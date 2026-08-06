package nl.bytesoflife.inspector;

/**
 * The Canon driver implementations mohican can run.
 * <p>
 * Only one can hold the camera at a time — EDSDK permits a single session per
 * body — so these are alternatives, not peers running side by side.
 */
public enum CameraDriver {

    /**
     * canon-sdk-java (JNA) — our fork with macOS support and the EDSDK 13.20.10
     * bindings. Pure Java, no InspectorCamera.dll, and the only driver that can
     * serve live view.
     */
    SDK("sdk", "canon-sdk-java (JNA)"),

    /**
     * The original InspectorCamera.dll JNI driver. Windows only, no live view.
     * Kept as a fallback while the SDK driver is proven on the shop-floor machines.
     */
    JNI("jni", "InspectorCamera.dll (JNI)"),

    /** No hardware. Serves synthetic photos and a moving live view feed. */
    SIMULATE("simulate", "Simulated");

    private final String id;
    private final String label;

    CameraDriver(final String id, final String label) {
        this.id = id;
        this.label = label;
    }

    /** Stable identifier used on the wire and in configuration.properties. */
    public String getId() {
        return id;
    }

    /** Human-readable name for the mohican UI. */
    public String getLabel() {
        return label;
    }

    /**
     * @return the driver for {@code id}, or {@code null} if unknown
     */
    public static CameraDriver fromId(final String id) {
        if (id == null) {
            return null;
        }
        final String needle = id.trim().toLowerCase();
        for (CameraDriver driver : values()) {
            if (driver.id.equals(needle)) {
                return driver;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
