package nl.bytesoflife.inspector;

import java.util.Map;

/**
 * A camera driver that can auto-focus on a chosen point of the live view.
 * <p>
 * Separate from {@link CanonDriverWrapperInterface} for the same reason as
 * {@link CanonLiveViewSource}: the legacy JNI driver has no EVF support at all,
 * so folding this in would force a stub onto it. The v2 {@code autoFocus} op
 * checks for this interface and reports UNSUPPORTED otherwise.
 */
public interface CanonAutoFocusSource {

    /**
     * Focus on a point of the live view frame.
     *
     * @param nx horizontal position, 0..1 across the frame
     * @param ny vertical position, 0..1 down the frame
     * @return outcome, including whether focus was achieved and where the frame
     *         was actually placed in the camera's coordinate system
     * @throws Exception if live view is not running, or the camera rejects it
     */
    Map<String, Object> focusAt(double nx, double ny) throws Exception;
}
