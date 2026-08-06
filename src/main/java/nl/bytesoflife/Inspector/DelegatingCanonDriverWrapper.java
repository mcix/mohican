package nl.bytesoflife.inspector;

import java.util.concurrent.ExecutionException;

/**
 * The {@code CanonDriverWrapperInterface} bean everything injects. Forwards every
 * call to whichever driver {@link CameraDriverRegistry} currently has active.
 * <p>
 * This is what lets the driver be swapped at runtime without touching a single
 * caller: {@code Mohican}'s {@code CANON_*} switch, the v2 command service and
 * the live view controller all hold this one stable reference.
 * <p>
 * Also implements {@link CanonLiveViewSource} and forwards only when the active
 * driver actually supports live view — the JNI driver does not, and asking it
 * must fail loudly rather than silently return nothing.
 */
public class DelegatingCanonDriverWrapper implements CanonDriverWrapperInterface, CanonLiveViewSource, CanonAutoFocusSource {

    private final CameraDriverRegistry registry;

    public DelegatingCanonDriverWrapper(final CameraDriverRegistry registry) {
        this.registry = registry;
    }

    private CanonDriverWrapperInterface target() {
        return registry.getActiveDriver();
    }

    /**
     * The concrete active driver, or null if none.
     * <p>
     * For callers that must pin one driver instance for the life of an operation
     * rather than re-resolving per call — the live view endpoints, whose acquire
     * and release must balance against the <i>same</i> driver even if the
     * operator switches drivers mid-stream.
     */
    public CanonDriverWrapperInterface activeDriverOrNull() {
        if (registry.getActive() == null) {
            return null;
        }
        try {
            return registry.getActiveDriver();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    // --- lifecycle --------------------------------------------------------

    /**
     * Listeners are held by the registry, not by the driver, so they survive a
     * switch. Registering here would bind them to today's driver only.
     */
    @Override
    public void addListener(final CanonDriverListener listener) {
        registry.addListener(listener);
    }

    /**
     * Startup and shutdown are the registry's job — it decides which single
     * driver may hold the camera. Delegating these would let a caller start a
     * driver behind the registry's back.
     */
    @Override
    public void start() {
        throw new UnsupportedOperationException("Use CameraDriverRegistry.activate(...)");
    }

    @Override
    public void stop() {
        throw new UnsupportedOperationException("Use CameraDriverRegistry.activate(...)");
    }

    @Override
    public boolean isReady() {
        final CameraDriver active = registry.getActive();
        if (active == null) {
            return false;
        }
        try {
            return target().isReady();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    // --- session ----------------------------------------------------------

    @Override
    public int init() throws ExecutionException, InterruptedException {
        return target().init();
    }

    @Override
    public int terminate() throws ExecutionException, InterruptedException {
        return target().terminate();
    }

    @Override
    public int initCamera() throws ExecutionException, InterruptedException {
        return target().initCamera();
    }

    @Override
    public int openSession() throws ExecutionException, InterruptedException {
        return target().openSession();
    }

    @Override
    public int closeSession() throws ExecutionException, InterruptedException {
        return target().closeSession();
    }

    @Override
    public boolean findCamera() throws ExecutionException, InterruptedException {
        return target().findCamera();
    }

    // --- capture ----------------------------------------------------------

    @Override
    public int takePhoto() throws ExecutionException, InterruptedException {
        return target().takePhoto();
    }

    @Override
    public String[] getAllImageInfo() throws ExecutionException, InterruptedException {
        return target().getAllImageInfo();
    }

    @Override
    public byte[] getImage(final String imageName) throws ExecutionException, InterruptedException {
        return target().getImage(imageName);
    }

    @Override
    public int formatAll() throws ExecutionException, InterruptedException {
        return target().formatAll();
    }

    @Override
    public void doAll() throws ExecutionException, InterruptedException {
        target().doAll();
    }

    // --- focus bracketing -------------------------------------------------

    @Override
    public int setFocusBracketing(final CanonDriver.EdsFocusShiftSet item)
            throws ExecutionException, InterruptedException {
        return target().setFocusBracketing(item);
    }

    @Override
    public int setFocusBracketing() throws ExecutionException, InterruptedException {
        return target().setFocusBracketing();
    }

    // --- properties -------------------------------------------------------

    @Override
    public String[] getShutterSpeedOptions() throws ExecutionException, InterruptedException {
        return target().getShutterSpeedOptions();
    }

    @Override
    public String getCurrentShutterSpeed() throws ExecutionException, InterruptedException {
        return target().getCurrentShutterSpeed();
    }

    @Override
    public String setShutterSpeed(final int shutterSpeed) throws ExecutionException, InterruptedException {
        return target().setShutterSpeed(shutterSpeed);
    }

    @Override
    public String[] getListOfWhiteBalanceOptions() throws ExecutionException, InterruptedException {
        return target().getListOfWhiteBalanceOptions();
    }

    @Override
    public String getWhiteBalanceSetting() throws ExecutionException, InterruptedException {
        return target().getWhiteBalanceSetting();
    }

    @Override
    public int setWhiteBalance(final int whiteBalance) throws ExecutionException, InterruptedException {
        return target().setWhiteBalance(whiteBalance);
    }

    @Override
    public String[] getListOfIsoOptions() throws ExecutionException, InterruptedException {
        return target().getListOfIsoOptions();
    }

    @Override
    public String getIsoSetting() throws ExecutionException, InterruptedException {
        return target().getIsoSetting();
    }

    @Override
    public int setIso(final int iso) throws ExecutionException, InterruptedException {
        return target().setIso(iso);
    }

    @Override
    public String[] getListOfApertureOptions() throws ExecutionException, InterruptedException {
        return target().getListOfApertureOptions();
    }

    @Override
    public String getApertureSetting() throws ExecutionException, InterruptedException {
        return target().getApertureSetting();
    }

    @Override
    public int setAperture(final int aperture) throws ExecutionException, InterruptedException {
        return target().setAperture(aperture);
    }

    @Override
    public String[] getImageQualityOptions() throws ExecutionException, InterruptedException {
        return target().getImageQualityOptions();
    }

    @Override
    public String getCurrentImageQuality() throws ExecutionException, InterruptedException {
        return target().getCurrentImageQuality();
    }

    @Override
    public int setImageQuality(final int imageQuality) throws ExecutionException, InterruptedException {
        return target().setImageQuality(imageQuality);
    }

    // --- live view --------------------------------------------------------

    /** True when the active driver can serve live view (the JNI driver cannot). */
    public boolean isLiveViewSupported() {
        final CameraDriver active = registry.getActive();
        if (active == null) {
            return false;
        }
        try {
            return target() instanceof CanonLiveViewSource;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private CanonLiveViewSource liveViewTarget() {
        final CanonDriverWrapperInterface driver = target();
        if (!(driver instanceof CanonLiveViewSource)) {
            throw new UnsupportedOperationException(
                    "Camera driver " + registry.getActive().getId() + " does not support live view");
        }
        return (CanonLiveViewSource) driver;
    }

    @Override
    public void acquireLiveView() throws Exception {
        liveViewTarget().acquireLiveView();
    }

    @Override
    public void releaseLiveView() {
        try {
            liveViewTarget().releaseLiveView();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            // Driver switched or went away while a stream was closing down.
        }
    }

    // --- auto focus -------------------------------------------------------

    @Override
    public java.util.Map<String, Object> focusAt(final double nx, final double ny) throws Exception {
        final CanonDriverWrapperInterface driver = target();
        if (!(driver instanceof CanonAutoFocusSource)) {
            throw new UnsupportedOperationException(
                    "Camera driver " + registry.getActive().getId() + " cannot auto-focus on a point");
        }
        return ((CanonAutoFocusSource) driver).focusAt(nx, ny);
    }

    @Override
    public byte[] nextLiveViewFrame() {
        try {
            return liveViewTarget().nextLiveViewFrame();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return null;
        }
    }
}
