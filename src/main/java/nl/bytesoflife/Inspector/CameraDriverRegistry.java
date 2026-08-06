package nl.bytesoflife.inspector;

import nl.bytesoflife.Configuration;
import nl.bytesoflife.inspector.sdk.CanonSdkDriverWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Owns every Canon driver mohican can run and decides which one holds the camera.
 * <p>
 * Only one driver is live at a time: EDSDK permits a single session per body, so
 * the JNI driver and the canon-sdk-java driver cannot both be connected. Drivers
 * are therefore built <b>lazily on first activation</b> — constructing the JNI one
 * loads {@code InspectorCamera.dll} and constructing the SDK one initialises
 * EDSDK, neither of which should happen for a driver nobody selected.
 * <p>
 * Listener registrations survive switching: {@link Mohican}-style listeners are
 * held here and re-attached to whichever driver becomes active, so consumers
 * register once at boot and keep receiving connect/disconnect/picture events
 * across a switch.
 */
public class CameraDriverRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CameraDriverRegistry.class);

    private final Map<CameraDriver, CanonDriverWrapperInterface> instances = new EnumMap<>(CameraDriver.class);
    private final List<CanonDriverListener> listeners = new ArrayList<>();
    private final List<Consumer<CameraDriver>> changeListeners = new ArrayList<>();
    /** How many of {@link #listeners} each driver instance already has. */
    private final Map<CameraDriver, Integer> attachedListenerCount = new EnumMap<>(CameraDriver.class);

    private volatile CameraDriver active;

    /** How long to let an asynchronously-started driver settle before reporting readiness. */
    private static final long READY_SETTLE_MS = 750;

    /** How long a driver gets to release the camera before we abandon it. */
    private static final long STOP_TIMEOUT_SECONDS = 10;

    private static final ExecutorService STOPPER = Executors.newCachedThreadPool(r -> {
        final Thread t = new Thread(r, "camera-driver-stop");
        t.setDaemon(true);
        return t;
    });

    /**
     * Register a camera-event listener. It is attached to the active driver now
     * and re-attached to every driver activated later.
     */
    public synchronized void addListener(final CanonDriverListener listener) {
        listeners.add(listener);
        final CanonDriverWrapperInterface current = instances.get(active);
        if (current != null) {
            current.addListener(listener);
            attachedListenerCount.put(active, listeners.size());
        }
    }

    /** Notified whenever the active driver changes — used by the mohican UI. */
    public synchronized void addChangeListener(final Consumer<CameraDriver> listener) {
        changeListeners.add(listener);
    }

    public CameraDriver getActive() {
        return active;
    }

    /**
     * The driver currently holding the camera.
     *
     * @throws IllegalStateException if no driver has been activated yet
     */
    public CanonDriverWrapperInterface getActiveDriver() {
        final CameraDriver current = active;
        if (current == null) {
            throw new IllegalStateException("No camera driver is active");
        }
        final CanonDriverWrapperInterface driver = instances.get(current);
        if (driver == null) {
            throw new IllegalStateException("Camera driver " + current.getId() + " is not instantiated");
        }
        return driver;
    }

    /** All drivers, whether or not they can run on this machine. */
    public List<CameraDriver> getAll() {
        return Arrays.asList(CameraDriver.values());
    }

    /**
     * Activate the default driver from configuration. Called once at startup.
     */
    public synchronized void activateConfigured() {
        final String configured = Configuration.getInstance().getCameraDriver();
        CameraDriver driver = CameraDriver.fromId(configured);
        if (driver == null) {
            logger.warn("Unknown camera.driver '{}', falling back to '{}'",
                    configured, CameraDriver.JNI.getId());
            driver = CameraDriver.JNI;
        }
        activate(driver);
    }

    /**
     * Switch the camera to {@code target}, stopping whatever held it before.
     * <p>
     * Idempotent: activating the already-active driver is a no-op, so the UI
     * toggle can fire freely.
     *
     * @return true if {@code target} came up ready, false if it failed to start
     *         (the registry still considers it active so the operator can see the
     *         failure and switch away deliberately)
     */
    public synchronized boolean activate(final CameraDriver target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (target == active) {
            final CanonDriverWrapperInterface current = instances.get(target);
            if (current != null && current.isReady()) {
                logger.debug("Camera driver {} is already active and ready", target.getId());
                return true;
            }
            // Active but not ready — the driver failed to come up (DLL missing,
            // camera unplugged). Re-selecting it in the UI is the operator asking
            // to try again, so retry rather than reporting the stale failure. The
            // JNI driver in particular cannot self-heal: without this it stays dead
            // until you switch to another driver and back.
            logger.info("Camera driver {} is active but not ready — retrying start", target.getId());
            if (current != null) {
                try {
                    current.start();
                } catch (Throwable t) {
                    logger.error("Camera driver {} failed to start on retry", target.getId(), t);
                    return false;
                }
                notifyChanged(target);
                return current.isReady();
            }
        }

        final CanonDriverWrapperInterface previous = active == null ? null : instances.get(active);
        if (previous != null) {
            logger.info("Stopping camera driver {}", active.getId());
            stopWithDeadline(active, previous);
        }

        final CanonDriverWrapperInterface next;
        try {
            next = instantiate(target);
        } catch (Throwable t) {
            logger.error("Could not construct camera driver {}", target.getId(), t);
            active = null;
            notifyChanged(null);
            return false;
        }

        instances.put(target, next);
        attachNewListeners(target, next);

        active = target;
        logger.info("Activating camera driver {}", target.getId());
        try {
            next.start();
        } catch (Throwable t) {
            logger.error("Camera driver {} failed to start", target.getId(), t);
            notifyChanged(target);
            return false;
        }
        // The JNI and simulated drivers start asynchronously on their own executor,
        // so isReady() is briefly false even on a successful switch. Give them a
        // moment before reporting, otherwise a good switch shows "not ready" in the
        // UI and in the v2 selectDriver result until something else refreshes it.
        final boolean ready = awaitReady(next);
        notifyChanged(target);
        return ready;
    }

    /**
     * Poll {@code driver.isReady()} briefly. Returns as soon as it is ready, or
     * false once {@link #READY_SETTLE_MS} has elapsed. Short enough not to stall a
     * UI switch, long enough to cover an async start handing off to an executor.
     */
    private static boolean awaitReady(final CanonDriverWrapperInterface driver) {
        final long deadline = System.currentTimeMillis() + READY_SETTLE_MS;
        while (System.currentTimeMillis() < deadline) {
            if (driver.isReady()) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return driver.isReady();
            }
        }
        return driver.isReady();
    }

    /** Stop whichever driver is active. Called on shutdown. */
    public synchronized void shutdown() {
        final CanonDriverWrapperInterface current = active == null ? null : instances.get(active);
        if (current != null) {
            stopWithDeadline(active, current);
        }
        active = null;
        STOPPER.shutdownNow();
    }

    /**
     * Stop a driver, giving up after {@link #STOP_TIMEOUT_SECONDS}.
     * <p>
     * Switching away from a broken driver is the operator's escape hatch, so it
     * must not be blockable <em>by</em> that driver. Catching {@code Throwable}
     * only covered a driver that throws; one that <em>hangs</em> — the realistic
     * EDSDK/USB failure — would block here while {@code activate} holds this
     * object's monitor, wedging every future switch and leaving the UI selector
     * disabled forever. Abandoning the stop leaks a driver instance, which is a
     * far better outcome than an unrecoverable bridge.
     */
    private void stopWithDeadline(final CameraDriver driver, final CanonDriverWrapperInterface instance) {
        final Future<?> stop = STOPPER.submit(() -> {
            try {
                instance.stop();
            } catch (Throwable t) {
                logger.warn("Camera driver {} did not stop cleanly", driver.getId(), t);
            }
        });
        try {
            stop.get(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.error("Camera driver {} did not stop within {}s — abandoning it and "
                            + "switching anyway. It may still hold the camera.",
                    driver.getId(), STOP_TIMEOUT_SECONDS);
            stop.cancel(true);
            // Drop the reference so a later re-activation builds a fresh instance
            // rather than reusing the wedged one.
            instances.remove(driver);
            attachedListenerCount.remove(driver);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            logger.warn("Camera driver {} stop failed", driver.getId(), e.getCause());
        }
    }

    /**
     * Attach any listeners this driver instance has not seen yet.
     * <p>
     * Driver instances are cached and reused across switches, and their
     * {@code addListener} simply appends to a list — so blindly re-registering on
     * every activation makes each camera event fire once per time that driver had
     * been selected. (Symptom: a single {@code takePhoto} pushing two
     * {@code pictureTaken} events, which would drive dp-3's capture state machine
     * through the flow twice.) Counting per instance also covers listeners
     * registered after a driver was first built.
     */
    private void attachNewListeners(final CameraDriver target, final CanonDriverWrapperInterface driver) {
        final int alreadyAttached = attachedListenerCount.containsKey(target)
                ? attachedListenerCount.get(target) : 0;
        for (int i = alreadyAttached; i < listeners.size(); i++) {
            driver.addListener(listeners.get(i));
        }
        attachedListenerCount.put(target, listeners.size());
    }

    /**
     * Reuse an already-built driver where possible: re-instantiating the SDK
     * driver would initialise EDSDK a second time in the same JVM.
     */
    private CanonDriverWrapperInterface instantiate(final CameraDriver target) {
        final CanonDriverWrapperInterface existing = instances.get(target);
        if (existing != null) {
            return existing;
        }
        switch (target) {
            case SDK:
                return new CanonSdkDriverWrapper();
            case JNI:
                return new CanonDriverWrapper();
            case SIMULATE:
                return new SimulatedCanonDriverWrapper();
            default:
                throw new IllegalArgumentException("Unhandled camera driver: " + target);
        }
    }

    private void notifyChanged(final CameraDriver driver) {
        for (Consumer<CameraDriver> listener : changeListeners) {
            try {
                listener.accept(driver);
            } catch (Throwable t) {
                logger.warn("Camera driver change listener failed", t);
            }
        }
    }
}
