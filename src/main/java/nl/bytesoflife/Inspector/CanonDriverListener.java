package nl.bytesoflife.inspector;

public interface CanonDriverListener {
    void onCameraConnect();
    void onCameraDisconnect();
    void onCameraPictureTaken();
}
