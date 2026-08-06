package nl.bytesoflife.inspector.sdk;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import org.blackdread.camerabinding.jna.EdsDirectoryItemInfo;
import org.blackdread.camerabinding.jna.EdsdkLibrary.EdsBaseRef;
import org.blackdread.camerabinding.jna.EdsdkLibrary.EdsCameraRef;
import org.blackdread.camerabinding.jna.EdsdkLibrary.EdsDirectoryItemRef;
import org.blackdread.camerabinding.jna.EdsdkLibrary.EdsVolumeRef;
import org.blackdread.cameraframework.api.constant.EdsdkError;
import org.blackdread.cameraframework.api.helper.factory.CanonFactory;
import org.blackdread.cameraframework.util.ReleaseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.blackdread.cameraframework.util.ErrorUtil.toEdsdkError;

/**
 * Camera-card browsing: list, download and erase the images the camera has
 * written to its own storage.
 * <p>
 * canon-sdk-java has no framework helper for this — {@code FileLogic} can only
 * download a directory item you already hold a ref to, and refs only arrive via
 * object events after a host-targeted shot. Our capture flow is card-targeted on
 * purpose: focus bracketing is composited <em>in the camera</em>, and only the
 * finished composite lands on the card. So we walk the volume tree ourselves
 * with the raw JNA bindings, mirroring what the old InspectorCamera.dll did.
 * <p>
 * All methods must be called on the EDSDK command thread — see
 * {@link CanonSdkDriverWrapper#submit}.
 */
final class CanonSdkStorage {

    private static final Logger logger = LoggerFactory.getLogger(CanonSdkStorage.class);

    /** Depth guard: DCIM/100CANON/IMG.JPG is 3; anything deeper is a malformed card. */
    private static final int MAX_DEPTH = 6;

    private CanonSdkStorage() {
    }

    /**
     * Every image file across every mounted volume, in camera order (so the last
     * element is the most recent shot — {@code CANON_GET_LAST_IMAGE} relies on that).
     */
    static List<String> listImageNames(final EdsCameraRef camera) {
        final List<String> names = new ArrayList<>();
        forEachVolume(camera, volume -> collectNames(volume, names, 0));
        return names;
    }

    /**
     * Download one image off the card by file name.
     *
     * @return the file bytes, or an empty array when no such file exists
     */
    static byte[] download(final EdsCameraRef camera, final String fileName) {
        final byte[][] result = new byte[][]{new byte[0]};
        forEachVolume(camera, volume -> {
            if (result[0].length == 0) {
                findAndDownload(volume, fileName, 0, result);
            }
        });
        return result[0];
    }

    /**
     * Format every mounted volume. Destructive — this is what {@code CANON_FORMAT}
     * has always done.
     */
    static void formatAll(final EdsCameraRef camera) {
        forEachVolume(camera, volume -> {
            final EdsdkError error = toEdsdkError(CanonFactory.edsdkLibrary().EdsFormatVolume(volume));
            if (error != EdsdkError.EDS_ERR_OK) {
                throw error.getException();
            }
        });
    }

    // --- tree walking -----------------------------------------------------

    private interface VolumeVisitor {
        void visit(EdsVolumeRef volume);
    }

    private static void forEachVolume(final EdsCameraRef camera, final VolumeVisitor visitor) {
        final int volumeCount = childCount(camera);
        for (int i = 0; i < volumeCount; i++) {
            final EdsVolumeRef.ByReference volumeRef = new EdsVolumeRef.ByReference();
            final EdsdkError error = toEdsdkError(
                    CanonFactory.edsdkLibrary().EdsGetChildAtIndex(camera, i, volumeRef));
            if (error != EdsdkError.EDS_ERR_OK) {
                logger.warn("Skipping volume {}: {}", i, error);
                continue;
            }
            try {
                visitor.visit(volumeRef.getValue());
            } finally {
                ReleaseUtil.release(volumeRef);
            }
        }
    }

    private static void collectNames(final EdsBaseRef parent, final List<String> names, final int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        final int count = childCount(parent);
        for (int i = 0; i < count; i++) {
            final EdsDirectoryItemRef.ByReference itemRef = new EdsDirectoryItemRef.ByReference();
            final EdsdkError error = toEdsdkError(
                    CanonFactory.edsdkLibrary().EdsGetChildAtIndex(parent, i, itemRef));
            if (error != EdsdkError.EDS_ERR_OK) {
                logger.warn("Skipping directory item {} at depth {}: {}", i, depth, error);
                continue;
            }
            try {
                final EdsDirectoryItemRef item = itemRef.getValue();
                final EdsDirectoryItemInfo info = CanonFactory.fileLogic().getDirectoryItemInfo(item);
                if (info.isFolder != 0) {
                    collectNames(item, names, depth + 1);
                } else {
                    names.add(fileNameOf(info));
                }
            } finally {
                ReleaseUtil.release(itemRef);
            }
        }
    }

    /**
     * Depth-first search for {@code fileName}; writes the downloaded bytes into
     * {@code result[0]} and stops as soon as a match is found.
     */
    private static void findAndDownload(final EdsBaseRef parent, final String fileName,
                                        final int depth, final byte[][] result) {
        if (depth > MAX_DEPTH) {
            return;
        }
        final int count = childCount(parent);
        for (int i = 0; i < count && result[0].length == 0; i++) {
            final EdsDirectoryItemRef.ByReference itemRef = new EdsDirectoryItemRef.ByReference();
            final EdsdkError error = toEdsdkError(
                    CanonFactory.edsdkLibrary().EdsGetChildAtIndex(parent, i, itemRef));
            if (error != EdsdkError.EDS_ERR_OK) {
                logger.warn("Skipping directory item {} at depth {}: {}", i, depth, error);
                continue;
            }
            try {
                final EdsDirectoryItemRef item = itemRef.getValue();
                final EdsDirectoryItemInfo info = CanonFactory.fileLogic().getDirectoryItemInfo(item);
                if (info.isFolder != 0) {
                    findAndDownload(item, fileName, depth + 1, result);
                } else if (fileNameOf(info).equals(fileName)) {
                    result[0] = readAndDelete(item);
                }
            } finally {
                ReleaseUtil.release(itemRef);
            }
        }
    }

    /**
     * EDSDK downloads to a file stream, so we stage into the system temp dir and
     * read it back. The caller wants bytes to Base64 straight onto the WebSocket.
     */
    private static byte[] readAndDelete(final EdsDirectoryItemRef item) {
        final File staged = CanonFactory.fileLogic().download(item);
        try {
            return Files.readAllBytes(staged.toPath());
        } catch (IOException e) {
            logger.error("Downloaded {} but could not read it back", staged, e);
            return new byte[0];
        } finally {
            if (!staged.delete()) {
                staged.deleteOnExit();
            }
        }
    }

    private static int childCount(final EdsBaseRef ref) {
        final IntByReference count = new IntByReference();
        final EdsdkError error = toEdsdkError(CanonFactory.edsdkLibrary().EdsGetChildCount(ref, count));
        if (error != EdsdkError.EDS_ERR_OK) {
            throw error.getException();
        }
        return count.getValue();
    }

    private static String fileNameOf(final EdsDirectoryItemInfo info) {
        return Native.toString(info.szFileName);
    }
}
