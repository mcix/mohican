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

public class SimulatedCanonDriverWrapper implements CanonDriverWrapperInterface {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedCanonDriverWrapper.class);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<CanonDriverListener> listeners = new ArrayList<>();
    
    // Simulated camera state
    private boolean isInitialized = false;
    private boolean isSessionOpen = false;
    private int currentAperture = 2;
    private int currentIso = 100;
    private int currentShutterSpeed = 8;
    private int currentImageQuality = 4;
    private int photoCounter = 0;

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
            logger.info("Running simulated camera after startup");
            isInitialized = true;
            isSessionOpen = true;
            handleConnect();
        });
    }

    public synchronized int init() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera init");
            isInitialized = true;
            return 0; // Success
        }).get();
    }

    public synchronized int terminate() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera terminate");
            isInitialized = false;
            isSessionOpen = false;
            return 0; // Success
        }).get();
    }

    public synchronized int initCamera() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera initCamera");
            return 0; // Success
        }).get();
    }

    public synchronized int takePhoto() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera takePhoto");
            photoCounter++;
            handlePictureTaken();
            return 0; // Success
        }).get();
    }

    public synchronized int openSession() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera openSession");
            isSessionOpen = true;
            return 0; // Success
        }).get();
    }

    public synchronized int closeSession() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera closeSession");
            isSessionOpen = false;
            return 0; // Success
        }).get();
    }

    public synchronized int setFocusBracketing(CanonDriver.EdsFocusShiftSet item) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setFocusBracketing");
            return 0; // Success
        }).get();
    }

    public synchronized String[] getAllImageInfo() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getAllImageInfo");
            // Return simulated image names
            String[] images = new String[photoCounter];
            for (int i = 0; i < photoCounter; i++) {
                images[i] = "IMG_" + String.format("%04d", i + 1) + ".JPG";
            }
            return images;
        }).get();
    }

    public synchronized int formatAll() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera formatAll");
            photoCounter = 0;
            return 0; // Success
        }).get();
    }

    public synchronized byte[] getImage(String imageName) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getImage: " + imageName);
            // Return a small simulated JPEG image (minimal valid JPEG data)
            return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, // JPEG header
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,     // JFIF marker
                0x01, 0x00, 0x01, 0x00, 0x01,                        // Basic image data
                (byte) 0xFF, (byte) 0xD9                             // JPEG end marker
            };
        }).get();
    }

    public synchronized void doAll() throws ExecutionException, InterruptedException {
        executorService.submit(() -> {
            logger.info("Simulated camera doAll");
            return null;
        }).get();
    }

    public synchronized boolean findCamera() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera findCamera");
            return true; // Always find camera in simulation
        }).get();
    }

    public synchronized int setFocusBracketing() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setFocusBracketing");
            return 0; // Success
        }).get();
    }

    public synchronized String[] getShutterSpeedOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getShutterSpeedOptions");
            return new String[]{"1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000"};
        }).get();
    }

    public synchronized String getCurrentShutterSpeed() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getCurrentShutterSpeed");
            String[] options = {"1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000"};
            return options[Math.min(currentShutterSpeed, options.length - 1)];
        }).get();
    }

    public synchronized String setShutterSpeed(int shutterSpeed) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setShutterSpeed: " + shutterSpeed);
            currentShutterSpeed = shutterSpeed;
            return getCurrentShutterSpeed();
        }).get();
    }

    public synchronized String[] getListOfWhiteBalanceOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getListOfWhiteBalanceOptions");
            return new String[]{"Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent"};
        }).get();
    }

    public synchronized String getWhiteBalanceSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getWhiteBalanceSetting");
            return "Auto";
        }).get();
    }

    public synchronized int setWhiteBalance(int whiteBalance) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setWhiteBalance: " + whiteBalance);
            return 0; // Success
        }).get();
    }

    public synchronized String[] getListOfIsoOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getListOfIsoOptions");
            return new String[]{"100", "200", "400", "800", "1600", "3200", "6400"};
        }).get();
    }

    public synchronized String getIsoSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getIsoSetting");
            String[] options = {"100", "200", "400", "800", "1600", "3200", "6400"};
            return options[Math.min(currentIso / 100 - 1, options.length - 1)];
        }).get();
    }

    public synchronized int setIso(int iso) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setIso: " + iso);
            currentIso = iso;
            return 0; // Success
        }).get();
    }

    public synchronized String[] getListOfApertureOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getListOfApertureOptions");
            return new String[]{"f/1.4", "f/2.0", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16"};
        }).get();
    }

    public synchronized String getApertureSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getApertureSetting");
            String[] options = {"f/1.4", "f/2.0", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16"};
            return options[Math.min(currentAperture - 1, options.length - 1)];
        }).get();
    }

    public synchronized int setAperture(int aperture) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setAperture: " + aperture);
            currentAperture = aperture;
            return 0; // Success
        }).get();
    }

    public synchronized String[] getImageQualityOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getImageQualityOptions");
            return new String[]{"RAW", "RAW+JPEG", "JPEG Fine", "JPEG Normal", "JPEG Basic"};
        }).get();
    }

    public synchronized String getCurrentImageQuality() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getCurrentImageQuality");
            String[] options = {"RAW", "RAW+JPEG", "JPEG Fine", "JPEG Normal", "JPEG Basic"};
            return options[Math.min(currentImageQuality, options.length - 1)];
        }).get();
    }

    public synchronized int setImageQuality(int imageQuality) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setImageQuality: " + imageQuality);
            currentImageQuality = imageQuality;
            return 0; // Success
        }).get();
    }
} 