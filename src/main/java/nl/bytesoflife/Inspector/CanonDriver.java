package nl.bytesoflife.inspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;

import static java.lang.Thread.sleep;

public class CanonDriver {

    private static final Logger logger = LoggerFactory.getLogger(CanonDriver.class);

//     static {
//
//         logger.info("*** load library ***");
//         System.loadLibrary("InspectorCamera");
//         System.loadLibrary("EDSDK");
//         logger.info("*** load library complete ***");
//     }

//    @EventListener(ApplicationReadyEvent.class)
//    public void runAfterStartup() {
//        logger.info("*** runAfterStartup ***");
//
//        logger.info(System.getProperty("java.library.path"));
//        System.loadLibrary("InspectorCamera");
//        //System.loadLibrary("EDSDK");
//
//        init();
//        logger.info(String.valueOf(openSession()));
//        //logger.info(Arrays.toString(getAllImageInfo()));
//        //logger.info(String.valueOf(takePhoto()));
//    }

    public void loadLibrary() {
        logger.info("*** load library ***");
        System.loadLibrary("InspectorCamera");
//        System.loadLibrary("EDSDK");
        logger.info("*** load library complete ***");
    }

    public static class EdsFocusShiftSet {
        private int version;
        private int focusShiftFunction;
        private int shootingNumber;
        private int stepWidth;
        private int exposureSmoothing;
        private int focusStackingFunction;
        private int focusStackingTrimming;
        private int reserved;


        // Single setter to set all values
        public void setAll(int version, int focusShiftFunction, int shootingNumber, int stepWidth,
                           int exposureSmoothing, int focusStackingFunction, int focusStackingTrimming, int reserved) {
            this.version = version;
            this.focusShiftFunction = focusShiftFunction;
            this.shootingNumber = shootingNumber;
            this.stepWidth = stepWidth;
            this.exposureSmoothing = exposureSmoothing;
            this.focusStackingFunction = focusStackingFunction;
            this.focusStackingTrimming = focusStackingTrimming;
            this.reserved = reserved;
        }

        // Getter and Setter for version
        public int getVersion() { return version; }
        public void setVersion(int version) { this.version = version; }

        // Getter and Setter for focusShiftFunction
        public int getFocusShiftFunction() { return focusShiftFunction; }
        public void setFocusShiftFunction(int focusShiftFunction) { this.focusShiftFunction = focusShiftFunction; }

        // Getter and Setter for shootingNumber
        public int getShootingNumber() { return shootingNumber; }
        public void setShootingNumber(int shootingNumber) { this.shootingNumber = shootingNumber; }

        // Getter and Setter for stepWidth
        public int getStepWidth() { return stepWidth; }
        public void setStepWidth(int stepWidth) { this.stepWidth = stepWidth; }

        // Getter and Setter for exposureSmoothing
        public int getExposureSmoothing() { return exposureSmoothing; }
        public void setExposureSmoothing(int exposureSmoothing) { this.exposureSmoothing = exposureSmoothing; }

        // Getter and Setter for focusStackingFunction
        public int getFocusStackingFunction() { return focusStackingFunction; }
        public void setFocusStackingFunction(int focusStackingFunction) { this.focusStackingFunction = focusStackingFunction; }

        // Getter and Setter for focusStackingTrimming
        public int getFocusStackingTrimming() { return focusStackingTrimming; }
        public void setFocusStackingTrimming(int focusStackingTrimming) { this.focusStackingTrimming = focusStackingTrimming; }

        // Getter and Setter for reserved
        public int getReserved() { return reserved; }
        public void setReserved(int reserved) { this.reserved = reserved; }
    }

    public void reInitialize(){
        init();
//        terminate();
        closeSession();
//        logger.error(String.valueOf(init()));
        int err = initCamera();
        if(err != 0){
            logger.error("Canon Error initCamera: " + String.valueOf(err));
        }

        err = openSession();
        if(err != 0){
            logger.error("Canon Error openSession: " + String.valueOf(err));
        }
    }

    public String[] allImageInfo() {
        logger.info("Start allImageInfo");
        String[] res = getAllImageInfo();
        logger.info("allImageInfo: " + Arrays.toString(res));
        return res;
    }

    // Init and terminate
    public native int init();
    public native int terminate();

    // Camera utilities
    public native int initCamera();
    public native int takePhoto();
    public native int openSession();
    public native int closeSession();
    public native int setFocusBracketing(EdsFocusShiftSet item);
    public native String[] getAllImageInfo();
    public native int formatAll();
    public native byte[] getImage(String imageName);
    public native void DoAll();
    public native boolean findCamera();

    public native int setFocusBracketing();


    // ShutterSpeed class functions
    public native String[] getShutterSpeedOptions();
    public native String getCurrentShutterSpeed();
    public native String setShutterSpeed(int shutterSpeed);

    // WhiteBalance class functions
    public native String[]  getListOfWhiteBalanceOptions();
    public native String getWhiteBalanceSetting();
    public native int setWhiteBalance(int whiteBalance);

    // Iso class functions
    public native String[]  getListOfIsoOptions();
    public native String getIsoSetting();
    public native int setIso(int iso);

    // Aperture class functions
    public native String[] getListOfApertureOptions();
    public native String getApertureSetting();
    public native int setAperture(int aperture);

    // Image Quality controls
    public native String[]  getImageQualityOptions();
    public native String getCurrentImageQuality();
    public native int setImageQuality(int ImageQuality);


    public static void main(String[] args) throws InterruptedException {

        System.loadLibrary("InspectorCamera");

        CanonDriver canonDriver = new CanonDriver();

        System.out.println("start:");

        int initResult = canonDriver.init();
        System.out.println("Init result: " + initResult);
        if (initResult != 0) {
            System.err.println("Failed to initialize CanonDriver");
            return;
        }

        int openSessionResult = canonDriver.openSession();
        System.out.println("Open session result: " + openSessionResult);
        if (openSessionResult != 0) {
            System.err.println("Failed to open session");
            return;
        }

        //canonDriver.formatAll();

//        System.out.println(" sleep start ");
//        sleep(5000);
//        System.out.println(" sleep stop ");

        int takePhotoResult = canonDriver.takePhoto();
        System.out.println("Take photo result: " + takePhotoResult);
        if (takePhotoResult != 0) {
            System.err.println("Failed to take photo");
            return;
        }

        int setImageQualityResult = canonDriver.setImageQuality(4);
        System.out.println("Set image quality result: " + setImageQualityResult);
        if (setImageQualityResult != 0) {
            System.err.println("Failed to set image quality");
            return;
        }

        String[] imageInfo = canonDriver.getAllImageInfo();
        System.out.println("Image info: " + Arrays.toString(imageInfo));

        // Test ISO functions
        int setIsoResult = canonDriver.setIso(100);
        System.out.println("Set ISO result: " + setIsoResult);
        if (setIsoResult != 0) {
            System.err.println("Failed to set ISO");
            return;
        }
        String isoSetting = canonDriver.getIsoSetting();
        System.out.println("ISO setting: " + isoSetting);

        // Test Aperture functions
        int setApertureResult = canonDriver.setAperture(2);
        System.out.println("Set Aperture result: " + setApertureResult);
        if (setApertureResult != 0) {
            System.err.println("Failed to set Aperture");
            return;
        }
        String apertureSetting = canonDriver.getApertureSetting();
        System.out.println("Aperture setting: " + apertureSetting);

        System.out.println("set shutter speed: " + Arrays.toString(canonDriver.getShutterSpeedOptions()));
//        int setShutterSpeedResult = Integer.parseInt(canonDriver.setShutterSpeed(8));
        System.out.println("set shutter speed: " + canonDriver.setShutterSpeed(8) );
        String shutterSpeedSetting = canonDriver.getCurrentShutterSpeed();
        System.out.println("Shutter Speed setting: " + shutterSpeedSetting);
    }
}


