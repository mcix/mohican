package nl.bytesoflife.inspector;

/**
 * A camera driver that can stream live view frames to the browser.
 * <p>
 * Deliberately kept separate from {@link CanonDriverWrapperInterface}: the legacy
 * JNI driver ({@code InspectorCamera.dll}) has no live view natives at all, so
 * folding these methods into the main interface would force a stub onto it. The
 * live view HTTP endpoints check for this interface and answer 503 when the
 * active driver does not implement it.
 * <p>
 * Sessions are ref-counted so several browser tabs share one EDSDK live view: the
 * first {@link #acquireLiveView()} starts it on the camera, the last matching
 * {@link #releaseLiveView()} stops it again.
 */
public interface CanonLiveViewSource {

    /**
     * Start (or join) the live view session. Balanced by {@link #releaseLiveView()}.
     *
     * @throws Exception if live view could not be started on the camera
     */
    void acquireLiveView() throws Exception;

    /**
     * Leave the live view session. Stops live view on the camera once the last
     * consumer has left. Safe to call more times than acquire.
     */
    void releaseLiveView();

    /**
     * The next live view frame as JPEG bytes.
     *
     * @return JPEG bytes, or {@code null} when the camera has no frame ready yet
     *         (normal for the first ~2s after acquiring, and between frames)
     */
    byte[] nextLiveViewFrame();
}
