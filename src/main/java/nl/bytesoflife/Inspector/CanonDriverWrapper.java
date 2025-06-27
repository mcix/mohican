package nl.bytesoflife.inspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class CanonDriverWrapper implements CanonDriverWrapperInterface {

    private static final Logger logger = LoggerFactory.getLogger(CanonDriverWrapper.class);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private CanonDriver canonDriver;

    private List<CanonDriverListener> listeners = new ArrayList<>();

    public void addListener(CanonDriverListener listener) {
        listeners.add(listener);
    }

    private void handleConnect() {
        for (CanonDriverListener listener : listeners) {
            listener.onCameraConnect();
        }
    }

    private void handleDisconnect() {
        for (CanonDriverListener listener : listeners) {
            listener.onCameraDisconnect();
        }
    }

    private void handlePictureTaken() {
        for (CanonDriverListener listener : listeners) {
            listener.onCameraPictureTaken();
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void runAfterStartup() {
        executorService.submit(() -> {
            logger.info("Running after startup");
            canonDriver = new CanonDriver();
            canonDriver.loadLibrary();
            int initRes = canonDriver.init();
            if( initRes != 0 ) {
                logger.error("Failed to initialize CanonDriver");
                return;
            }
            int openRes = canonDriver.openSession();
            if( openRes != 0 ) {
                logger.error("Failed to open session");
                return;
            }

            handleConnect();
        });
    }


    public synchronized int init() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.init()).get();
    }

    public synchronized int terminate() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.terminate()).get();
    }

    public synchronized int initCamera() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.initCamera()).get();
    }

    public synchronized int takePhoto() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.takePhoto()).get();
    }

    public synchronized int openSession() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.openSession()).get();
    }

    public synchronized int closeSession() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.closeSession()).get();
    }

    public synchronized int setFocusBracketing(CanonDriver.EdsFocusShiftSet item) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setFocusBracketing(item)).get();
    }

    public synchronized String[] getAllImageInfo() throws ExecutionException, InterruptedException {
        executorService.submit(() -> canonDriver.closeSession()).get();
        executorService.submit(() -> canonDriver.openSession()).get();
        return executorService.submit(() -> canonDriver.getAllImageInfo()).get();
    }

    public synchronized int formatAll() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.formatAll()).get();
    }

    public synchronized byte[] getImage(String imageName) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getImage(imageName)).get();
    }

    public synchronized void doAll() throws ExecutionException, InterruptedException {
        executorService.submit(() -> {
            canonDriver.DoAll();
            return null;
        }).get();
    }

    public synchronized boolean findCamera() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.findCamera()).get();
    }

    public synchronized int setFocusBracketing() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setFocusBracketing()).get();
    }

    public synchronized String[] getShutterSpeedOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getShutterSpeedOptions()).get();
    }

    public synchronized String getCurrentShutterSpeed() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getCurrentShutterSpeed()).get();
    }

    public synchronized String setShutterSpeed(int shutterSpeed) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setShutterSpeed(shutterSpeed)).get();
    }

    public synchronized String[] getListOfWhiteBalanceOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getListOfWhiteBalanceOptions()).get();
    }

    public synchronized String getWhiteBalanceSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getWhiteBalanceSetting()).get();
    }

    public synchronized int setWhiteBalance(int whiteBalance) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setWhiteBalance(whiteBalance)).get();
    }

    public synchronized String[] getListOfIsoOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getListOfIsoOptions()).get();
    }

    public synchronized String getIsoSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getIsoSetting()).get();
    }

    public synchronized int setIso(int iso) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setIso(iso)).get();
    }

    public synchronized String[] getListOfApertureOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getListOfApertureOptions()).get();
    }

    public synchronized String getApertureSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getApertureSetting()).get();
    }

    public synchronized int setAperture(int aperture) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setAperture(aperture)).get();
    }

    public synchronized String[] getImageQualityOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getImageQualityOptions()).get();
    }

    public synchronized String getCurrentImageQuality() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.getCurrentImageQuality()).get();
    }

    public synchronized int setImageQuality(int imageQuality) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> canonDriver.setImageQuality(imageQuality)).get();
    }

    // Add other methods to call CanonDriver functions in a similar way
}