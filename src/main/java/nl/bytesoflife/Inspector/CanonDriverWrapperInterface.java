package nl.bytesoflife.inspector;

import java.util.concurrent.ExecutionException;

public interface CanonDriverWrapperInterface {
    
    void addListener(CanonDriverListener listener);
    
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