package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.inspector.CanonDriverWrapperInterface;
import nl.bytesoflife.inspector.CanonLiveViewSource;
import nl.bytesoflife.inspector.DelegatingCanonDriverWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Canon live view over HTTP, consumed by {@code CanonLiveView.jsx} in the
 * protofoto UI.
 * <p>
 * Two shapes of the same stream:
 * <ul>
 *   <li>{@code /api/camera/liveview.mjpg} — {@code multipart/x-mixed-replace},
 *       which browsers render natively in an {@code <img>} with no JS decode loop.</li>
 *   <li>{@code /api/camera/liveview.jpg} — one frame, for iOS Safari, which only
 *       ever paints the first frame of an MJPEG stream and therefore polls.</li>
 * </ul>
 * Both hold a ref-counted {@link CanonLiveViewSource} session, so several tabs
 * share one EDSDK live view and the camera only leaves EVF mode when the last
 * viewer disconnects.
 * <p>
 * CORS is already open for the frontend origins via
 * {@link SpringWebSecurity#corsConfigurer()}, which matters here because the
 * {@code <img>} is tagged {@code crossOrigin="anonymous"} so the component can
 * sample a pixel to detect a frozen feed.
 */
@RestController
@RequestMapping("/api/camera")
public class CameraLiveViewController {

    private static final Logger logger = LoggerFactory.getLogger(CameraLiveViewController.class);

    private static final String BOUNDARY = "frame";
    private static final MediaType MJPEG = MediaType.parseMediaType(
            "multipart/x-mixed-replace; boundary=" + BOUNDARY);

    /** ~15 fps. The camera's EVF runs faster; this keeps one browser tab off a whole CPU. */
    private static final long FRAME_INTERVAL_MS = 66;
    /**
     * Live view needs a moment after EVF output flips to PC before the first frame
     * exists, so a null frame is normal at first. Give up only if they never stop.
     */
    private static final int MAX_CONSECUTIVE_EMPTY_FRAMES = 150; // ~10s

    private final CanonDriverWrapperInterface canonDriverWrapper;

    @Autowired
    public CameraLiveViewController(final CanonDriverWrapperInterface canonDriverWrapper) {
        this.canonDriverWrapper = canonDriverWrapper;
    }

    @GetMapping(value = "/liveview.mjpg")
    public ResponseEntity<StreamingResponseBody> liveViewStream() {
        final CanonLiveViewSource source = liveViewSource();
        if (source == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            source.acquireLiveView();
        } catch (Exception e) {
            logger.warn("Could not start live view", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        final StreamingResponseBody body = outputStream -> {
            int emptyFrames = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    final byte[] frame = source.nextLiveViewFrame();
                    if (frame == null || frame.length == 0) {
                        if (++emptyFrames > MAX_CONSECUTIVE_EMPTY_FRAMES) {
                            logger.warn("Live view produced no frames, ending stream");
                            return;
                        }
                    } else {
                        emptyFrames = 0;
                        writePart(outputStream, frame);
                        // A client that closed the tab is only detectable on write,
                        // so flush every frame rather than letting Tomcat buffer.
                        outputStream.flush();
                    }
                    Thread.sleep(FRAME_INTERVAL_MS);
                }
            } catch (IOException e) {
                // Normal: the browser navigated away or cleared the <img> src.
                logger.debug("Live view client disconnected", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                source.releaseLiveView();
            }
        };

        return ResponseEntity.ok().contentType(MJPEG).body(body);
    }

    @GetMapping(value = "/liveview.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> liveViewFrame() {
        final CanonLiveViewSource source = liveViewSource();
        if (source == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            source.acquireLiveView();
        } catch (Exception e) {
            logger.warn("Could not start live view", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        try {
            final byte[] frame = source.nextLiveViewFrame();
            if (frame == null || frame.length == 0) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    // Polled at ~5 fps by the iOS fallback; never let a proxy cache it.
                    .header("Cache-Control", "no-store")
                    .body(frame);
        } finally {
            // Each poll is its own acquire/release pair, so the consumer count
            // legitimately hits zero between polls. The driver lingers before
            // actually stopping EVF, which is what keeps this from cycling the
            // camera in and out of live view at the poll rate.
            source.releaseLiveView();
        }
    }

    /**
     * Resolve the <b>concrete</b> live view source once, so a stream keeps talking
     * to the driver it started on.
     * <p>
     * Going through {@code DelegatingCanonDriverWrapper} for every call would
     * re-resolve the <i>currently active</i> driver each time, so acquire and
     * release could land on different drivers: tab 1 streams on driver A, the
     * operator switches to B, tab 2 streams on B, then tab 1 disconnects and its
     * release decrements <b>B</b> to zero — killing tab 2's feed. Binding the
     * instance for the life of the stream makes the counts balance per driver.
     */
    private CanonLiveViewSource liveViewSource() {
        final CanonDriverWrapperInterface active = canonDriverWrapper instanceof DelegatingCanonDriverWrapper
                ? ((DelegatingCanonDriverWrapper) canonDriverWrapper).activeDriverOrNull()
                : canonDriverWrapper;
        if (active instanceof CanonLiveViewSource) {
            return (CanonLiveViewSource) active;
        }
        logger.debug("Active Canon driver {} has no live view support",
                active == null ? "<none>" : active.getClass().getSimpleName());
        return null;
    }

    private static void writePart(final OutputStream out, final byte[] frame) throws IOException {
        final String header = "--" + BOUNDARY + "\r\n"
                + "Content-Type: image/jpeg\r\n"
                + "Content-Length: " + frame.length + "\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));
        out.write(frame);
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
    }
}
