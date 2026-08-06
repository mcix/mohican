package nl.bytesoflife.inspector.protocol;

import nl.bytesoflife.inspector.CameraDriver;
import nl.bytesoflife.inspector.CameraDriverRegistry;
import nl.bytesoflife.inspector.CanonDriver;
import nl.bytesoflife.inspector.CanonDriverWrapperInterface;
import nl.bytesoflife.inspector.CanonLiveViewSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

/**
 * The v2 camera protocol: one {@code CAMERA_COMMAND} in, one {@code CAMERA_RESULT}
 * out, correlated by request id.
 * <p>
 * <b>Why a second protocol.</b> v1 ({@code CANON_*}) is 25 separate message types
 * with no request/response correlation and no error channel — a client has to run
 * a state machine over unsolicited Redux pushes and guess which push answered
 * which request. v2 keeps the same transport but gives every command an id, a
 * structured payload and a real error, so a client can await a promise.
 * <p>
 * <b>Why it runs off the caller's thread.</b> v1 executes inside
 * {@code Mohican.onMessage}, which runs on the Tomcat request thread handling
 * {@code POST /mohicanreq}. A {@code CANON_TAKE_PHOTO} therefore pins a request
 * thread for however long the camera takes — seconds, and longer with focus
 * bracketing. Every command here is queued to a single worker instead; the POST
 * returns immediately and the answer arrives over the WebSocket. The worker is
 * single-threaded on purpose: EDSDK serialises anyway, and a queue makes the
 * ordering explicit rather than emergent.
 */
public class CameraCommandService {

    private static final Logger logger = LoggerFactory.getLogger(CameraCommandService.class);

    /** Pushes a Redux action to connected clients. Implemented by {@code Mohican}. */
    public interface MessageSink {
        void send(String type, Object value);
    }

    public static final String COMMAND_TYPE = "CAMERA_COMMAND";
    public static final String RESULT_TYPE = "CAMERA_RESULT";
    public static final String EVENT_TYPE = "CAMERA_EVENT";

    private final CameraDriverRegistry registry;
    private final CanonDriverWrapperInterface driver;
    private final MessageSink sink;

    /**
     * The shared camera worker (see {@code MessageDispatcher}). Deliberately not a
     * private executor: v1 {@code CANON_*} and v2 {@code CAMERA_COMMAND} drive the
     * same driver, and on two threads a v1 capture could interleave with a v2
     * {@code getLastImage} mid-operation. One queue makes them strictly ordered.
     */
    private final Executor worker;

    /** True when the calling thread is already {@link #worker}. */
    private final BooleanSupplier onWorkerThread;

    public CameraCommandService(final CameraDriverRegistry registry,
                                final CanonDriverWrapperInterface driver,
                                final MessageSink sink,
                                final Executor worker,
                                final BooleanSupplier onWorkerThread) {
        this.registry = registry;
        this.driver = driver;
        this.sink = sink;
        this.worker = worker;
        this.onWorkerThread = onWorkerThread;
        registry.addChangeListener(this::onDriverChanged);
    }

    /**
     * Queue a command. Returns immediately — the result is pushed as
     * {@code CAMERA_RESULT}.
     */
    public void submit(final Object rawValue) {
        final CameraCommand command;
        try {
            command = CameraCommand.parse(rawValue);
        } catch (IllegalArgumentException e) {
            // Malformed envelope: we may not even have an id to correlate with,
            // but the client still needs to see the failure rather than time out.
            logger.warn("Rejected CAMERA_COMMAND: {}", e.getMessage());
            sink.send(RESULT_TYPE, CameraResult.error(null, null, activeDriverId(),
                    "BAD_REQUEST", e.getMessage()));
            return;
        }

        // Run inline when already on the camera worker. submit() is normally called
        // from Mohican.handleMessage, which the dispatcher has already placed on
        // that worker — re-queueing there would append this command to the TAIL of
        // the queue, so a v1 CANON_* that arrived later would execute first. That
        // is exactly the v1/v2 interleaving the shared queue is meant to prevent.
        final Runnable task = () -> {
            CameraResult result;
            try {
                result = execute(command);
            } catch (IllegalArgumentException e) {
                result = CameraResult.error(command.getId(), command.getOp(), activeDriverId(),
                        "BAD_REQUEST", e.getMessage());
            } catch (UnsupportedOperationException e) {
                result = CameraResult.error(command.getId(), command.getOp(), activeDriverId(),
                        "UNSUPPORTED", e.getMessage());
            } catch (Throwable t) {
                logger.error("CAMERA_COMMAND {} failed", command, t);
                result = CameraResult.error(command.getId(), command.getOp(), activeDriverId(),
                        "CAMERA_ERROR", String.valueOf(t.getMessage()));
            }
            sink.send(RESULT_TYPE, result);
        };

        if (onWorkerThread.getAsBoolean()) {
            task.run();
        } else {
            worker.execute(task);
        }
    }

    /** Push an unsolicited event (connect, disconnect, picture taken, driver change). */
    public void publishEvent(final String event, final Map<String, Object> extra) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", event);
        payload.put("driver", activeDriverId());
        if (extra != null) {
            payload.putAll(extra);
        }
        sink.send(EVENT_TYPE, payload);
    }

    /** The worker is owned by MessageDispatcher, so there is nothing to shut down here. */
    public void shutdown() {
        // no-op
    }

    // --- dispatch ---------------------------------------------------------

    private CameraResult execute(final CameraCommand c) throws Exception {
        final String id = c.getId();
        final String op = c.getOp();

        switch (op) {
            // --- introspection & driver selection ---
            case "status":
                return CameraResult.ok(id, op, activeDriverId(), status());

            case "listDrivers":
                return CameraResult.ok(id, op, activeDriverId(), CameraResult.value("drivers", drivers()));

            case "selectDriver": {
                final String wanted = c.requireString("driver");
                final CameraDriver target = CameraDriver.fromId(wanted);
                if (target == null) {
                    throw new IllegalArgumentException("Unknown driver '" + wanted
                            + "'. Known: sdk, jni, simulate");
                }
                final boolean ready = registry.activate(target);
                final Map<String, Object> data = status();
                data.put("started", ready);
                return CameraResult.ok(id, op, activeDriverId(), data);
            }

            // --- session ---
            case "openSession":
                return intResult(id, op, driver.openSession());
            case "closeSession":
                return intResult(id, op, driver.closeSession());
            case "reinitialize": {
                driver.closeSession();
                return intResult(id, op, driver.openSession());
            }
            case "findCamera":
                return CameraResult.ok(id, op, activeDriverId(),
                        CameraResult.value("found", driver.findCamera()));

            // --- capture ---
            case "takePhoto":
                return intResult(id, op, driver.takePhoto());

            case "listImages":
                return CameraResult.ok(id, op, activeDriverId(),
                        CameraResult.value("images", driver.getAllImageInfo()));

            case "getImage":
                return imageResult(id, op, c.requireString("name"));

            case "getLastImage": {
                final String[] images = driver.getAllImageInfo();
                if (images == null || images.length == 0) {
                    return CameraResult.error(id, op, activeDriverId(),
                            "NO_IMAGES", "The camera card holds no images");
                }
                return imageResult(id, op, images[images.length - 1]);
            }

            case "formatCard":
                return intResult(id, op, driver.formatAll());

            // --- settings ---
            case "getSettings":
                return CameraResult.ok(id, op, activeDriverId(), settings());

            case "setSetting": {
                final String setting = c.requireString("setting");
                final int value = c.requireInt("value");
                return CameraResult.ok(id, op, activeDriverId(), setSetting(setting, value));
            }

            case "setFocusStacking": {
                final CanonDriver.EdsFocusShiftSet set = new CanonDriver.EdsFocusShiftSet();
                set.setAll(
                        // version 3 is required for the camera to composite the
                        // bracket itself; anything lower shoots but does not stack.
                        c.getInt("version", 3),
                        c.getInt("focusShiftFunction", 1),
                        c.getInt("shootingNumber", 5),
                        c.getInt("stepWidth", 4),
                        c.getInt("exposureSmoothing", 1),
                        c.getInt("focusStackingFunction", 1),
                        c.getInt("focusStackingTrimming", 1),
                        c.getInt("reserved", 0));
                return intResult(id, op, driver.setFocusBracketing(set));
            }

            // --- live view ---
            case "liveViewStatus":
                return CameraResult.ok(id, op, activeDriverId(), liveViewStatus());

            default:
                throw new IllegalArgumentException("Unknown op '" + op + "'");
        }
    }

    // --- op helpers -------------------------------------------------------

    /**
     * EDSDK returns 0 for success and an error code otherwise. Surface a non-zero
     * code as a failed result rather than a successful one carrying an error
     * number, which is the trap v1 fell into with {@code CANON_PHOTO_STATUS}.
     */
    private CameraResult intResult(final String id, final String op, final int code) {
        if (code == 0) {
            return CameraResult.ok(id, op, activeDriverId(), CameraResult.value("code", 0));
        }
        return CameraResult.error(id, op, activeDriverId(), "EDSDK_" + code,
                "Camera returned error code " + code);
    }

    private CameraResult imageResult(final String id, final String op, final String name) throws Exception {
        final byte[] bytes = driver.getImage(name);
        if (bytes == null || bytes.length == 0) {
            return CameraResult.error(id, op, activeDriverId(),
                    "IMAGE_UNAVAILABLE", "Could not read '" + name + "' from the camera");
        }
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", name);
        data.put("bytes", bytes.length);
        data.put("data", Base64.getEncoder().encodeToString(bytes));
        return CameraResult.ok(id, op, activeDriverId(), data);
    }

    private Map<String, Object> status() {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("driver", activeDriverId());
        data.put("drivers", drivers());
        data.put("ready", safeReady());
        data.putAll(liveViewStatus());
        return data;
    }

    private List<Map<String, Object>> drivers() {
        final List<Map<String, Object>> out = new ArrayList<>();
        for (CameraDriver d : registry.getAll()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", d.getId());
            entry.put("label", d.getLabel());
            entry.put("active", d == registry.getActive());
            entry.put("liveView", d != CameraDriver.JNI);
            out.add(entry);
        }
        return out;
    }

    private Map<String, Object> liveViewStatus() {
        final Map<String, Object> data = new LinkedHashMap<>();
        // Must be false when nothing is active: `getActive() != JNI` is also true
        // for null, which reported live view as available with no driver at all.
        final CameraDriver active = registry.getActive();
        final boolean supported = active != null
                && active != CameraDriver.JNI
                && driver instanceof CanonLiveViewSource;
        data.put("liveViewSupported", supported);
        // Live view is consumed over HTTP, not this protocol — a JSON channel
        // cannot carry 15fps of JPEG without wrecking the WebSocket.
        data.put("liveViewMjpeg", "/api/camera/liveview.mjpg");
        data.put("liveViewJpeg", "/api/camera/liveview.jpg");
        return data;
    }

    private Map<String, Object> settings() throws Exception {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("iso", setting(driver.getIsoSetting(), driver.getListOfIsoOptions()));
        data.put("aperture", setting(driver.getApertureSetting(), driver.getListOfApertureOptions()));
        data.put("shutterSpeed", setting(driver.getCurrentShutterSpeed(), driver.getShutterSpeedOptions()));
        data.put("quality", setting(driver.getCurrentImageQuality(), driver.getImageQualityOptions()));
        data.put("whiteBalance", setting(driver.getWhiteBalanceSetting(), driver.getListOfWhiteBalanceOptions()));
        return data;
    }

    /**
     * Both current value and options use the historical {@code "<value>:<label>"}
     * encoding. v2 splits them so clients never have to parse strings.
     */
    private Map<String, Object> setting(final String current, final String[] options) {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", decode(current));
        final List<Map<String, Object>> decoded = new ArrayList<>();
        if (options != null) {
            for (String option : options) {
                decoded.add(decode(option));
            }
        }
        data.put("options", decoded);
        return data;
    }

    private Map<String, Object> decode(final String encoded) {
        final Map<String, Object> data = new LinkedHashMap<>();
        if (encoded == null) {
            return data;
        }
        final int colon = encoded.indexOf(':');
        if (colon <= 0) {
            // Driver could not read the property (v1 signals this with a string
            // starting "camera") — pass the raw text through with no value.
            data.put("value", null);
            data.put("label", encoded);
            return data;
        }
        try {
            data.put("value", Integer.valueOf(encoded.substring(0, colon).trim()));
        } catch (NumberFormatException e) {
            data.put("value", null);
        }
        data.put("label", encoded.substring(colon + 1).trim());
        return data;
    }

    private Map<String, Object> setSetting(final String setting, final int value) throws Exception {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("setting", setting);
        switch (setting) {
            case "iso":
                driver.setIso(value);
                data.put("current", decode(driver.getIsoSetting()));
                break;
            case "aperture":
                driver.setAperture(value);
                data.put("current", decode(driver.getApertureSetting()));
                break;
            case "shutterSpeed":
                driver.setShutterSpeed(value);
                data.put("current", decode(driver.getCurrentShutterSpeed()));
                break;
            case "quality":
                driver.setImageQuality(value);
                data.put("current", decode(driver.getCurrentImageQuality()));
                break;
            case "whiteBalance":
                driver.setWhiteBalance(value);
                data.put("current", decode(driver.getWhiteBalanceSetting()));
                break;
            default:
                throw new IllegalArgumentException("Unknown setting '" + setting
                        + "'. Known: iso, aperture, shutterSpeed, quality, whiteBalance");
        }
        return data;
    }

    private boolean safeReady() {
        try {
            return driver.isReady();
        } catch (Throwable t) {
            return false;
        }
    }

    private String activeDriverId() {
        final CameraDriver current = registry.getActive();
        return current == null ? null : current.getId();
    }

    private void onDriverChanged(final CameraDriver driver) {
        final Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("ready", safeReady());
        publishEvent("driverChanged", extra);
    }
}
