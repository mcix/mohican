package nl.bytesoflife.inspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class SimulatedCanonDriverWrapper implements CanonDriverWrapperInterface, CanonLiveViewSource, CanonAutoFocusSource {

    private static final Logger logger = LoggerFactory.getLogger(SimulatedCanonDriverWrapper.class);

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private List<CanonDriverListener> listeners = new ArrayList<>();
    
    // Simulated camera state
    private boolean isInitialized = false;
    private boolean isSessionOpen = false;
    private int currentAperture = 2;   // index into APERTURES
    private int currentIso = 0;        // index into ISO_SPEEDS
    private int currentShutterSpeed = 3; // index into SHUTTER_SPEEDS
    private int currentImageQuality = 2; // index into IMAGE_QUALITIES
    private int currentWhiteBalance = 0;  // index into WHITE_BALANCES
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

    /** Started explicitly by CameraDriverRegistry — see CanonDriverWrapperInterface#start. */
    public synchronized void start() {
        executorService.submit(() -> {
            logger.info("Starting simulated camera");
            isInitialized = true;
            isSessionOpen = true;
            handleConnect();
        });
    }

    public synchronized void stop() {
        executorService.submit(() -> {
            logger.info("Stopping simulated camera");
            isSessionOpen = false;
            isInitialized = false;
            liveViewConsumers.set(0);
            handleDisconnect();
        });
    }

    public boolean isReady() {
        return isInitialized && isSessionOpen;
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

    // Option lists use the "<index>:<label>" contract that CanonManager.js parses
    // with parseInt(v.split(':')[0]) and posts back as CANON_SET_*. Emitting a bare
    // label made that parse NaN, so simulated settings never actually changed.

    private static final String[] SHUTTER_SPEEDS =
            {"1/30", "1/60", "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000"};
    private static final String[] WHITE_BALANCES =
            {"Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent"};
    private static final String[] ISO_SPEEDS =
            {"100", "200", "400", "800", "1600", "3200", "6400"};
    private static final String[] APERTURES =
            {"f/1.4", "f/2.0", "f/2.8", "f/4.0", "f/5.6", "f/8.0", "f/11", "f/16"};
    private static final String[] IMAGE_QUALITIES =
            {"RAW", "RAW+JPEG", "JPEG Fine", "JPEG Normal", "JPEG Basic"};

    private static String[] optionsOf(String[] labels) {
        String[] result = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            result[i] = i + ":" + labels[i];
        }
        return result;
    }

    private static String optionAt(String[] labels, int index) {
        int clamped = Math.max(0, Math.min(index, labels.length - 1));
        return clamped + ":" + labels[clamped];
    }

    public synchronized String[] getShutterSpeedOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getShutterSpeedOptions");
            return optionsOf(SHUTTER_SPEEDS);
        }).get();
    }

    public synchronized String getCurrentShutterSpeed() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getCurrentShutterSpeed");
            return optionAt(SHUTTER_SPEEDS, currentShutterSpeed);
        }).get();
    }

    public synchronized String setShutterSpeed(int shutterSpeed) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setShutterSpeed: " + shutterSpeed);
            currentShutterSpeed = shutterSpeed;
            return optionAt(SHUTTER_SPEEDS, currentShutterSpeed);
        }).get();
    }

    public synchronized String[] getListOfWhiteBalanceOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getListOfWhiteBalanceOptions");
            return optionsOf(WHITE_BALANCES);
        }).get();
    }

    public synchronized String getWhiteBalanceSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getWhiteBalanceSetting");
            return optionAt(WHITE_BALANCES, currentWhiteBalance);
        }).get();
    }

    public synchronized int setWhiteBalance(int whiteBalance) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setWhiteBalance: " + whiteBalance);
            currentWhiteBalance = whiteBalance;
            return 0; // Success
        }).get();
    }

    public synchronized String[] getListOfIsoOptions() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getListOfIsoOptions");
            return optionsOf(ISO_SPEEDS);
        }).get();
    }

    public synchronized String getIsoSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getIsoSetting");
            return optionAt(ISO_SPEEDS, currentIso);
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
            return optionsOf(APERTURES);
        }).get();
    }

    public synchronized String getApertureSetting() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getApertureSetting");
            return optionAt(APERTURES, currentAperture);
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
            return optionsOf(IMAGE_QUALITIES);
        }).get();
    }

    public synchronized String getCurrentImageQuality() throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera getCurrentImageQuality");
            return optionAt(IMAGE_QUALITIES, currentImageQuality);
        }).get();
    }

    public synchronized int setImageQuality(int imageQuality) throws ExecutionException, InterruptedException {
        return executorService.submit(() -> {
            logger.info("Simulated camera setImageQuality: " + imageQuality);
            currentImageQuality = imageQuality;
            return 0; // Success
        }).get();
    }

    // --- simulated live view ---------------------------------------------
    // Exists so the protofoto live view UI can be driven end-to-end on a machine
    // with no camera attached. Frames are synthetic but they move, which is what
    // CanonLiveView.jsx's pixel-sampling staleness detector looks for.

    private final AtomicInteger liveViewConsumers = new AtomicInteger(0);
    private final AtomicInteger frameCounter = new AtomicInteger(0);

    @Override
    public void acquireLiveView() {
        if (liveViewConsumers.incrementAndGet() == 1) {
            logger.info("Simulated live view started");
        }
    }

    @Override
    public void releaseLiveView() {
        if (liveViewConsumers.get() <= 0) {
            return;
        }
        if (liveViewConsumers.decrementAndGet() == 0) {
            logger.info("Simulated live view stopped");
        }
    }

    /**
     * Pretend to focus.
     * <p>
     * Reports failure near the frame edges so the UI's red state is reachable
     * without a camera — real bodies do struggle at the extremes, and a simulator
     * that only ever succeeds leaves half the interface untested.
     */
    @Override
    public java.util.Map<String, Object> focusAt(double nx, double ny) throws InterruptedException {
        if (liveViewConsumers.get() <= 0) {
            throw new IllegalStateException("Live view must be running to focus on a point");
        }
        Thread.sleep(400); // lens travel, roughly
        boolean focused = nx >= 0.1 && nx <= 0.9 && ny >= 0.1 && ny <= 0.9;
        logger.info("Simulated auto-focus at ({}, {}) -> {}", nx, ny, focused ? "OK" : "failed");
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("focused", focused);
        result.put("code", 0);
        result.put("reason", focused ? null : "SIMULATED_AF_NG");
        result.put("x", nx);
        result.put("y", ny);
        return result;
    }

    @Override
    public byte[] nextLiveViewFrame() {
        if (liveViewConsumers.get() <= 0) {
            return null;
        }
        final int frame = frameCounter.getAndIncrement();
        final BufferedImage image = new BufferedImage(640, 426, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(24, 32, 40));
            g.fillRect(0, 0, 640, 426);

            // Sweeping bar: guarantees the sampled pixel changes between frames.
            final int x = (int) (320 + 260 * Math.sin(frame / 12.0));
            g.setColor(new Color(80, 200, 120));
            g.fillRect(x - 20, 0, 40, 426);

            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
            g.drawString("SIMULATED LIVE VIEW", 24, 40);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
            g.drawString("frame " + frame, 24, 68);

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpeg", out);
            return out.toByteArray();
        } catch (IOException e) {
            logger.warn("Failed to render simulated live view frame", e);
            return null;
        } finally {
            g.dispose();
        }
    }
}
