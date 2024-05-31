package nl.bytesoflife.Inspector;

import com.sun.javaws.IconUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Scanner;

import static java.lang.Thread.sleep;

public class CanonDriver {

    private static final Logger logger = LoggerFactory.getLogger(CanonDriver.class);

    static {

        logger.info(System.getProperty("java.library.path"));
        logger.info("*** load library ***");
        System.loadLibrary("InspectorCamera");
        System.loadLibrary("EDSDK");
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

    public class ImageItem {
        private String name;
        private long itemRef;

        // Getter and Setter for name
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        // Getter and Setter for itemRef
        public long getItemRef() { return itemRef; }
        public void setItemRef(long itemRef) { this.itemRef = itemRef; }
    }

    // Init and terminate
    public native int init();
    public native int terminateCamera();

    // Camera utilities
    public native int initCamera();
    public native int takePhoto();
    public native int openSession();
    public native int closeSession();
    public native int setFocusBracketing(EdsFocusShiftSet item);
    public native ImageItem[] getAllImageInfo();
    public native int formatAll();
    public native byte[] getImage(long directoryItem);
    public native void DoAll();
    public native boolean findCamera();


    // ShutterSpeed class functions
    public native String[] getShutterSpeedOptions();
    public native String getCurrentShutterSpeed();
    public native int setShutterSpeed(int shutterSpeed);

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

    }
