package nl.bytesoflife.inspector;

import java.util.concurrent.ExecutionException;

public interface CanonDriverWrapperInterface {

    void addListener(CanonDriverListener listener);

    /**
     * Bring the driver up: load natives, initialise the SDK and open a session.
     * <p>
     * Drivers used to do this from an {@code @EventListener(ApplicationReadyEvent)}
     * of their own, which meant every driver Spring knew about would grab the
     * camera at boot. Startup is now driven explicitly by
     * {@code CameraDriverRegistry} so exactly one driver is live at a time —
     * EDSDK only permits a single session per body.
     * <p>
     * Implementations must be safe to call when no camera is attached: log and
     * return rather than throwing.
     */
    void start();

    /**
     * Release the camera: close the session and terminate the SDK. Must be safe
     * to call when the driver was never started, and must not throw.
     */
    void stop();

    /**
     * @return true once the driver has an open session and can serve commands
     */
    boolean isReady();

    int init() throws ExecutionException, InterruptedException;
    
    int terminate() throws ExecutionException, InterruptedException;
    
    int initCamera() throws ExecutionException, InterruptedException;
    
    int takePhoto() throws ExecutionException, InterruptedException;
    
    int openSession() throws ExecutionException, InterruptedException;
    
    int closeSession() throws ExecutionException, InterruptedException;
    
    int setFocusBracketing(CanonDriver.EdsFocusShiftSet item) throws ExecutionException, InterruptedException;
    
    String[] getAllImageInfo() throws ExecutionException, InterruptedException;
    
    int formatAll() throws ExecutionException, InterruptedException;
    
    byte[] getImage(String imageName) throws ExecutionException, InterruptedException;
    
    void doAll() throws ExecutionException, InterruptedException;
    
    boolean findCamera() throws ExecutionException, InterruptedException;
    
    int setFocusBracketing() throws ExecutionException, InterruptedException;
    
    String[] getShutterSpeedOptions() throws ExecutionException, InterruptedException;
    
    String getCurrentShutterSpeed() throws ExecutionException, InterruptedException;
    
    String setShutterSpeed(int shutterSpeed) throws ExecutionException, InterruptedException;
    
    String[] getListOfWhiteBalanceOptions() throws ExecutionException, InterruptedException;
    
    String getWhiteBalanceSetting() throws ExecutionException, InterruptedException;
    
    int setWhiteBalance(int whiteBalance) throws ExecutionException, InterruptedException;
    
    String[] getListOfIsoOptions() throws ExecutionException, InterruptedException;
    
    String getIsoSetting() throws ExecutionException, InterruptedException;
    
    int setIso(int iso) throws ExecutionException, InterruptedException;
    
    String[] getListOfApertureOptions() throws ExecutionException, InterruptedException;
    
    String getApertureSetting() throws ExecutionException, InterruptedException;
    
    int setAperture(int aperture) throws ExecutionException, InterruptedException;
    
    String[] getImageQualityOptions() throws ExecutionException, InterruptedException;
    
    String getCurrentImageQuality() throws ExecutionException, InterruptedException;
    
    int setImageQuality(int imageQuality) throws ExecutionException, InterruptedException;
} 