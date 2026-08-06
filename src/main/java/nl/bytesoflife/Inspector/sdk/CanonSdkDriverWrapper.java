package nl.bytesoflife.inspector.sdk;

import com.sun.jna.Pointer;
import org.blackdread.camerabinding.jna.EdsFocusShiftSet;
import nl.bytesoflife.inspector.CanonDriver;
import nl.bytesoflife.inspector.CanonDriverListener;
import nl.bytesoflife.inspector.CanonDriverWrapperInterface;
import nl.bytesoflife.inspector.CanonAutoFocusSource;
import nl.bytesoflife.inspector.CanonLiveViewSource;
import org.blackdread.camerabinding.jna.EdsdkLibrary.EdsCameraRef;
import org.blackdread.camerabinding.jna.EdsPoint;
import org.blackdread.camerabinding.jna.EdsSize;
import org.blackdread.cameraframework.api.CallableCommand;
import org.blackdread.cameraframework.api.camera.CanonCamera;
import org.blackdread.cameraframework.api.command.CanonCommand;
import org.blackdread.cameraframework.api.command.TerminateSdkCommand;
import org.blackdread.cameraframework.api.command.builder.ShootOption;
import org.blackdread.cameraframework.api.command.builder.ShootOptionBuilder;
import org.blackdread.cameraframework.api.constant.EdsAv;
import org.blackdread.cameraframework.api.constant.EdsCameraCommand;
import org.blackdread.cameraframework.api.constant.EdsEvfAf;
import org.blackdread.cameraframework.api.constant.EdsEvfOutputDevice;
import org.blackdread.cameraframework.api.constant.EdsISOSpeed;
import org.blackdread.cameraframework.api.constant.EdsObjectEvent;
import org.blackdread.cameraframework.api.constant.EdsImageQuality;
import org.blackdread.cameraframework.api.constant.EdsPropertyID;
import org.blackdread.cameraframework.api.constant.EdsSaveTo;
import org.blackdread.cameraframework.api.constant.EdsStateEvent;
import org.blackdread.cameraframework.api.constant.EdsTv;
import org.blackdread.cameraframework.api.constant.EdsWhiteBalance;
import org.blackdread.cameraframework.api.constant.EdsdkError;
import org.blackdread.cameraframework.api.constant.NativeEnum;
import org.blackdread.cameraframework.api.helper.factory.CanonFactory;
import org.blackdread.cameraframework.api.helper.initialisation.FrameworkInitialisation;
import org.blackdread.cameraframework.api.helper.logic.LiveViewReference;
import org.blackdread.cameraframework.util.ErrorUtil;
import org.blackdread.cameraframework.api.helper.logic.event.CameraAddedListener;
import org.blackdread.cameraframework.api.helper.logic.event.CameraObjectListener;
import org.blackdread.cameraframework.api.helper.logic.event.CameraStateListener;
import org.blackdread.cameraframework.exception.error.EdsdkErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Canon driver backed by <a href="https://github.com/Blackdread/canon-sdk-java">canon-sdk-java</a>
 * (pure Java + JNA over EDSDK) instead of the hand-written {@code InspectorCamera.dll}
 * JNI layer.
 * <p>
 * Wire-compatible with {@link CanonDriverWrapperInterface} so the {@code CANON_*}
 * protocol in {@code Mohican} and the protofoto frontend need no changes. In
 * particular the option lists keep the historical {@code "<int>:<label>"} shape
 * that {@code CanonManager.js} parses with {@code parseInt(v.split(':')[0])} —
 * but the integer is now the real EDSDK property value (e.g. {@code 0x48} for ISO
 * 100) rather than an ordinal into a C++ lookup table, so it is stable across
 * bodies and lens combinations.
 * <p>
 * Threading: EDSDK requires all calls from one thread. canon-sdk-java enforces
 * that with its {@code CommandDispatcher}, so every SDK touch here goes through
 * {@link #submit} rather than a local executor.
 */
public class CanonSdkDriverWrapper implements CanonDriverWrapperInterface, CanonLiveViewSource, CanonAutoFocusSource {

    private static final Logger logger = LoggerFactory.getLogger(CanonSdkDriverWrapper.class);

    private static final int OK = EdsdkError.EDS_ERR_OK.value();
    /** Returned when we are asked to do something that needs an open session and there is none. */
    private static final int ERR_DEVICE_NOT_FOUND = EdsdkError.EDS_ERR_DEVICE_NOT_FOUND.value();

    /**
     * Save to the card, not the host. Focus bracketing is composited by the camera
     * and only the composite is written to the card, so a host-targeted shot would
     * hand us the individual frames instead of the stacked image.
     * <p>
     * {@code fetchEvents} and {@code waitForItemDownloadEvent} must be turned off
     * <b>together</b> — {@code ShootOptionBuilder.validate()} rejects
     * fetch-without-wait. Both are off here because the shot lands on the card:
     * there is no host download to wait for, and the framework's own event-fetcher
     * thread ({@code withEventFetcherLogic()} in {@link #init()}) already pumps
     * {@code EdsGetEvent}, so the shoot command must not also pump it.
     */
    private static ShootOption shootOption() {
        return new ShootOptionBuilder()
                .setSaveTo(EdsSaveTo.kEdsSaveTo_Camera)
                .setShootWithNoAF(true)
                .setShootAttemptCount(2)
                // We stop live view ourselves before shooting, so this would only
                // add an extra EVF probe at the moment the camera is busiest —
                // visible in the logs as "Live view seems off while testing status"
                // and a plausible contributor to the DEVICE_BUSY we then retry past.
                .setCheckLiveViewState(false)
                .setWaitForItemDownloadEvent(false)
                .setFetchEvents(false)
                .build();
    }

    private final List<CanonDriverListener> listeners = new ArrayList<>();
    private final AtomicInteger liveViewConsumers = new AtomicInteger(0);

    /** Set while a capture is in flight; counted down by the DirItemCreated event. */
    private final AtomicReference<CountDownLatch> pendingCapture = new AtomicReference<>();

    /** Set while an auto-focus is in flight; counted down by the AfResult event. */
    private final AtomicReference<CountDownLatch> pendingAf = new AtomicReference<>();
    private final AtomicReference<Long> lastAfResult = new AtomicReference<>();

    /** How long to wait for the lens to report back before releasing AF. */
    private static final long AF_RESULT_TIMEOUT_MS = 2500;

    /**
     * How long to let the camera write a shot before touching the card. Covers a
     * normal JPEG comfortably; an in-camera focus stack takes longer, but the
     * session refresh that follows is itself timeout-protected, so a slow write
     * degrades to a retryable failure rather than a wedge.
     */
    private static final long CAPTURE_SETTLE_MS = 2500;

    /** How long the camera needs after EVF goes down before it will accept a shot. */
    private static final long LIVE_VIEW_TO_SHOOT_SETTLE_MS = 2000;

    /** Retries for a busy camera: 1s, 2s, 4s, 8s — ~15s total, matching what the R8 needs. */
    private static final int SHOOT_BUSY_ATTEMPTS = 5;
    private static final long SHOOT_BUSY_RETRY_START_MS = 1000;

    /** Set by a capture; makes the next listing rebuild the session snapshot. */
    private volatile boolean cardDirty = false;
    /** Bumped on every acquire/release so a stale linger-stop can identify itself. */
    private final AtomicInteger liveViewGeneration = new AtomicInteger(0);

    /** How long EVF stays up after the last consumer leaves. Covers the ~200ms iOS poll gap. */
    private static final long LIVE_VIEW_LINGER_MS = 3000;

    private static final ScheduledExecutorService LINGER = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "camera-liveview-linger");
        t.setDaemon(true);
        return t;
    });

    private volatile CanonCamera camera;
    private volatile EdsCameraRef cameraRef;
    private volatile boolean sdkInitialised = false;
    private volatile boolean sessionOpen = false;
    private volatile boolean liveViewRunning = false;

    // Keep hard references: the framework holds these listeners weakly.
    private CameraAddedListener cameraAddedListener;
    private CameraObjectListener cameraObjectListener;
    private CameraStateListener cameraStateListener;

    @Override
    public void addListener(final CanonDriverListener listener) {
        listeners.add(listener);
    }

    /**
     * Set while {@link #refreshSession()} tears the session down and rebuilds it.
     * <p>
     * That refresh is an internal implementation detail of reading the card, not a
     * real disconnect — without this every image listing fired
     * disconnect/connect at the UI and pushed spurious CAMERA_EVENTs, twice per
     * {@code CANON_GET_LAST_IMAGE}.
     */
    private volatile boolean suppressConnectionEvents = false;

    /** Set by {@link #stop()} so in-flight reconnect attempts give up. */
    private volatile boolean stopped = false;

    /** Coalesces the several signals a single disconnect produces. */
    private final java.util.concurrent.atomic.AtomicBoolean reconnecting =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** Reconnect backoff: 1s doubling to 30s, then every 30s for as long as it takes. */
    private static final long RECONNECT_START_MS = 1000;
    private static final long RECONNECT_MAX_MS = 30_000;

    /** How often to probe a session nobody is using. Slow: this is a backstop, not the primary signal. */
    private static final long HEALTH_CHECK_SECONDS = 20;

    private java.util.concurrent.ScheduledFuture<?> healthCheck;

    private static final ScheduledExecutorService HEALTH = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "camera-health");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService RECONNECT = Executors.newCachedThreadPool(r -> {
        final Thread t = new Thread(r, "camera-reconnect");
        t.setDaemon(true);
        return t;
    });

    private void handleConnect() {
        if (suppressConnectionEvents) {
            return;
        }
        for (CanonDriverListener listener : listeners) {
            listener.onCameraConnect();
        }
    }

    private void handleDisconnect() {
        if (suppressConnectionEvents) {
            return;
        }
        for (CanonDriverListener listener : listeners) {
            listener.onCameraDisconnect();
        }
    }

    private void handlePictureTaken() {
        for (CanonDriverListener listener : listeners) {
            listener.onCameraPictureTaken();
        }
    }

    /**
     * Auto-focus on a point of the live view image.
     *
     * @param nx horizontal position, 0..1 across the live view frame
     * @param ny vertical position, 0..1 down the live view frame
     * @return what happened, for the caller to turn into UI
     */
    @Override
    public Map<String, Object> focusAt(final double nx, final double ny) throws Exception {
        if (!requireSession()) {
            throw new IllegalStateException("No camera session open");
        }
        if (!liveViewRunning) {
            // The AF frame is a live-view concept: without EVF running there is no
            // coordinate system to place it in and the camera ignores the command.
            throw new IllegalStateException("Live view must be running to focus on a point");
        }

        final double cx = Math.max(0.0, Math.min(1.0, nx));
        final double cy = Math.max(0.0, Math.min(1.0, ny));

        final CountDownLatch afDone = new CountDownLatch(1);
        lastAfResult.set(null);
        pendingAf.set(afDone);
        try {
            return submit(() -> {
                // The coordinate system lives on the EVF *image*, not the camera ref
                // — asking the camera ref returns nothing useful. It is also not the
                // same as the JPEG's pixel size, which is why the caller sends a
                // fraction and we scale here rather than trusting client pixels.
                final EdsSize coord;
                try (LiveViewReference lv =
                             CanonFactory.liveViewLogic().getLiveViewImageReference(cameraRef)) {
                    coord = CanonFactory.propertyGetShortcutLogic()
                            .getEvfCoordinateSystem(lv.getImageRef().getValue());
                }

                final EdsPoint.ByValue point = new EdsPoint.ByValue();
                point.x = (int) Math.round(cx * coord.width);
                point.y = (int) Math.round(cy * coord.height);

                final EdsdkError placed = ErrorUtil.toEdsdkError(
                        CanonFactory.edsdkLibrary().EdsSetFramePoint(cameraRef, point, false));
                if (placed != EdsdkError.EDS_ERR_OK) {
                    throw placed.getException();
                }

                final EdsdkError started = ErrorUtil.toEdsdkError(
                        CanonFactory.edsdkLibrary().EdsSendCommand(cameraRef,
                                EdsCameraCommand.kEdsCameraCommand_DoEvfAf.value(),
                                EdsEvfAf.kEdsCameraCommand_EvfAf_ON.value()));

                // Give the lens a moment, then release AF. Leaving DoEvfAf ON holds
                // the lens driving and the next shot then reports the camera busy.
                boolean signalled = false;
                try {
                    signalled = afDone.await(AF_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ErrorUtil.toEdsdkError(CanonFactory.edsdkLibrary().EdsSendCommand(cameraRef,
                        EdsCameraCommand.kEdsCameraCommand_DoEvfAf.value(),
                        EdsEvfAf.kEdsCameraCommand_EvfAf_OFF.value()));

                final Map<String, Object> result = new LinkedHashMap<>();
                // The command's return code is the only signal whose meaning is
                // documented, so it decides focused/not. kEdsStateEvent_AfResult is
                // reported raw alongside it: the API reference does not specify its
                // payload, so guessing a polarity would be worse than saying what we
                // saw. Once its meaning is confirmed on the R8 this can tighten.
                result.put("focused", started == EdsdkError.EDS_ERR_OK);
                result.put("code", started.value());
                result.put("reason", started == EdsdkError.EDS_ERR_OK ? null : started.name());
                result.put("afResultSignalled", signalled);
                result.put("afResultRaw", lastAfResult.get());
                result.put("x", cx);
                result.put("y", cy);
                result.put("coordinateSystem", coordMap(coord));
                result.put("point", pointMap(point));
                if (started != EdsdkError.EDS_ERR_OK) {
                    logger.info("Auto-focus at ({}, {}) did not lock: {}", cx, cy, started);
                }
                return result;
            });
        } finally {
            pendingAf.set(null);
        }
    }

    private static Map<String, Object> coordMap(final EdsSize size) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("width", size.width);
        m.put("height", size.height);
        return m;
    }

    private static Map<String, Object> pointMap(final EdsPoint point) {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", point.x);
        m.put("y", point.y);
        return m;
    }

    /**
     * Bring live view back after a capture, off the caller's thread and retrying
     * while the camera is busy.
     * <p>
     * The first attempt lands milliseconds after the shutter, when the camera is
     * still writing the file, so it reliably gets EDS_ERR_DEVICE_BUSY. The original
     * version tried once and logged the failure — which left liveViewRunning false
     * while the browser's MJPEG connection was still open, so the stream fed on
     * nulls until it hit its empty-frame limit and killed itself. One photo
     * permanently ended live view for that session.
     * <p>
     * Asynchronous on purpose: the operator's photo must not wait on the feed
     * coming back. takePhoto returns as soon as the image exists, and live view
     * reappears a moment later.
     */
    private void restoreLiveViewAsync() {
        RECONNECT.execute(() -> {
            long delayMs = SHOOT_BUSY_RETRY_START_MS;
            for (int attempt = 1; attempt <= SHOOT_BUSY_ATTEMPTS; attempt++) {
                if (stopped || !sessionOpen || liveViewConsumers.get() <= 0) {
                    return; // nobody left watching, or we are going down
                }
                try {
                    synchronized (this) {
                        if (liveViewRunning) {
                            return;
                        }
                        submit(() -> {
                            CanonFactory.liveViewLogic().beginLiveView(cameraRef,
                                    EdsEvfOutputDevice.kEdsEvfOutputDevice_PC);
                            return null;
                        });
                        liveViewRunning = true;
                    }
                    logger.info("Live view restored after the shot");
                    return;
                } catch (Exception e) {
                    if (codeOf(e) != EdsdkError.EDS_ERR_DEVICE_BUSY.value()
                            || attempt == SHOOT_BUSY_ATTEMPTS) {
                        logger.warn("Could not restore live view after the shot", e);
                        return;
                    }
                    logger.debug("Camera busy restoring live view {}/{}, retrying in {}ms",
                            attempt, SHOOT_BUSY_ATTEMPTS, delayMs);
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delayMs *= 2;
            }
        });
    }

    /**
     * Shoot, retrying on {@code EDS_ERR_DEVICE_BUSY} with a growing delay.
     * <p>
     * This is Canon's own documented remedy for 129 ("reissue the command after a
     * while"), and on an R8 it is needed in the normal case rather than an
     * exceptional one: coming out of live view the body stays busy for several
     * seconds, far longer than {@code ShootOption.shootAttemptCount} retries — which
     * fire back-to-back with no delay — can cover.
     */
    private void shootWithBusyRetry() throws Exception {
        final int busyCode = EdsdkError.EDS_ERR_DEVICE_BUSY.value();
        Exception last = null;
        long delayMs = SHOOT_BUSY_RETRY_START_MS;
        for (int attempt = 1; attempt <= SHOOT_BUSY_ATTEMPTS; attempt++) {
            try {
                camera.getShoot().shoot(shootOption());
                return;
            } catch (Exception e) {
                last = e;
                if (codeOf(e) != busyCode || attempt == SHOOT_BUSY_ATTEMPTS) {
                    throw e;
                }
                logger.info("Camera busy on shot attempt {}/{}, retrying in {}ms",
                        attempt, SHOOT_BUSY_ATTEMPTS, delayMs);
                Thread.sleep(delayMs);
                delayMs *= 2;
            }
        }
        throw last;
    }

    /** EDSDK code carried by an exception chain, or -1 if it is not an EDSDK failure. */
    private static int codeOf(final Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof EdsdkErrorException) {
                return ((EdsdkErrorException) cause).getEdsdkError().value();
            }
            cause = cause.getCause();
        }
        return -1;
    }

    /** Started explicitly by CameraDriverRegistry — see CanonDriverWrapperInterface#start. */
    @Override
    public void start() {
        stopped = false;
        startHealthCheck();
        try {
            final int initResult = init();
            if (initResult != OK) {
                logger.error("Canon SDK init failed: {}", initResult);
                return;
            }
            final int openResult = openSession();
            if (openResult != OK) {
                logger.error("Canon SDK openSession failed: {}", openResult);
            }
            // openSession() already notifies listeners on success.
        } catch (Throwable t) {
            // UnsatisfiedLinkError when EDSDK itself is missing (no EDSDK.dll on
            // Windows / no EDSDK.framework on macOS). Must not take mohican down.
            logger.error("Canon SDK startup failed", t);
        }
    }

    @Override
    public void stop() {
        // Before terminate(): otherwise the reconnect loop races the shutdown and
        // reopens the session we are trying to release, and the registry's switch
        // away from this driver never settles.
        stopped = true;
        stopHealthCheck();
        try {
            terminate();
        } catch (Throwable t) {
            logger.warn("Canon SDK shutdown failed", t);
        }
    }

    // --- health check -----------------------------------------------------

    /**
     * Backstop for a disconnect that produces no event at all — USB yanked, or the
     * camera switched off while idle. EDSDK does not always deliver
     * {@code kEdsStateEvent_Shutdown} in those cases, and without a poll the loss
     * would only surface when the operator next tried to shoot.
     * <p>
     * Deliberately cheap and non-intrusive: it only probes when the session is
     * believed open and nothing else is using the camera. Probing during a capture
     * or a live view stream would add USB traffic exactly when the camera is
     * busiest, which is what provokes DEVICE_BUSY.
     */
    private synchronized void startHealthCheck() {
        stopHealthCheck();
        healthCheck = HEALTH.scheduleWithFixedDelay(() -> {
            try {
                if (stopped || !sdkInitialised) {
                    return;
                }
                if (!sessionOpen) {
                    scheduleReconnect("health check found no session");
                    return;
                }
                if (liveViewRunning || pendingCapture.get() != null) {
                    return; // busy; a failing command would tell us anyway
                }
                final int connected = submit(() -> CanonFactory.cameraLogic().getCameraConnectedCount());
                if (connected <= 0) {
                    handleCameraLost("health check found no camera");
                }
            } catch (Exception e) {
                logger.debug("Camera health check failed", e);
                handleCameraLost("health check error: " + e.getMessage());
            }
        }, HEALTH_CHECK_SECONDS, HEALTH_CHECK_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void stopHealthCheck() {
        if (healthCheck != null) {
            healthCheck.cancel(false);
            healthCheck = null;
        }
    }

    @Override
    public boolean isReady() {
        return sdkInitialised && sessionOpen && cameraRef != null;
    }

    // --- lifecycle --------------------------------------------------------

    @Override
    public synchronized int init() {
        if (sdkInitialised) {
            return OK;
        }
        try {
            cameraAddedListener = event -> {
                // EDSDK saw a camera appear on the bus. Usually the operator just
                // plugged ours back in (or switched it on), so try to reclaim it.
                logger.info("Camera added: {}", event);
                scheduleReconnect("camera added");
            };
            cameraObjectListener = event -> {
                logger.debug("Object event: {}", event);
                if (event.getObjectEvent() == EdsObjectEvent.kEdsObjectEvent_DirItemCreated) {
                    // The camera has finished writing a file to the card. This is
                    // also what tells EDSDK's own directory tree about it, so a
                    // listing taken after this point will see the new image.
                    final CountDownLatch latch = pendingCapture.get();
                    if (latch != null) {
                        latch.countDown();
                    }
                }
            };
            cameraStateListener = event -> {
                logger.debug("State event: {}", event);
                if (event.getStateEvent() == EdsStateEvent.kEdsStateEvent_AfResult) {
                    // Undocumented payload ("Not specified in API Reference"), so we
                    // record it rather than interpret it — see focusAt().
                    lastAfResult.set(event.getEventData());
                    final CountDownLatch latch = pendingAf.get();
                    if (latch != null) {
                        latch.countDown();
                    }
                }
                if (event.getStateEvent() == EdsStateEvent.kEdsStateEvent_Shutdown) {
                    // The camera is telling us it is going away — powered off, USB
                    // pulled, or auto-power-off. This is the only prompt notice we
                    // get; ignoring it left a stale session that every later command
                    // failed against.
                    logger.warn("Camera reported shutdown/disconnect");
                    handleCameraLost("camera reported shutdown");
                }
            };

            new FrameworkInitialisation()
                    .withEventFetcherLogic()
                    .registerCameraAddedEvent()
                    .withCameraAddedListener(cameraAddedListener)
                    .initialize();

            camera = new CanonCamera();
            sdkInitialised = true;
            return OK;
        } catch (Exception e) {
            return errorOf(e, "init");
        }
    }

    @Override
    public synchronized int terminate() {
        if (!sdkInitialised) {
            return OK;
        }
        try {
            closeSession();
            CanonFactory.commandDispatcher().scheduleCommand(new TerminateSdkCommand());
            sdkInitialised = false;
            camera = null;
            cameraRef = null;
            return OK;
        } catch (Exception e) {
            return errorOf(e, "terminate");
        }
    }

    /**
     * The JNI driver used this to (re)acquire the camera handle. Here the handle
     * comes from {@link #openSession()}, so this only has to make sure the SDK is up.
     */
    @Override
    public synchronized int initCamera() {
        return init();
    }

    @Override
    public synchronized int openSession() {
        final int initResult = init();
        if (initResult != OK) {
            return initResult;
        }
        if (sessionOpen) {
            return OK;
        }
        try {
            cameraRef = await(camera.openSession());
            sessionOpen = true;

            await(camera.getEvent().registerObjectEventCommand());
            await(camera.getEvent().registerStateEventCommand());
            CanonFactory.cameraObjectEventLogic().addCameraObjectListener(cameraObjectListener);
            CanonFactory.cameraStateEventLogic().addCameraStateListener(cameraRef, cameraStateListener);

            handleConnect();
            return OK;
        } catch (Exception e) {
            sessionOpen = false;
            return errorOf(e, "openSession");
        }
    }

    @Override
    public synchronized int closeSession() {
        if (!sessionOpen) {
            return OK;
        }
        try {
            if (liveViewRunning) {
                stopLiveView();
            }
            await(camera.closeSession());
            return OK;
        } catch (Exception e) {
            return errorOf(e, "closeSession");
        } finally {
            sessionOpen = false;
            cameraRef = null;
            liveViewConsumers.set(0);
            handleDisconnect();
        }
    }

    @Override
    public boolean findCamera() {
        if (!sdkInitialised && init() != OK) {
            return false;
        }
        try {
            return submit(() -> CanonFactory.cameraLogic().getCameraConnectedCount()) > 0;
        } catch (Exception e) {
            logger.warn("findCamera failed", e);
            return false;
        }
    }

    // --- capture ----------------------------------------------------------

    @Override
    public int takePhoto() {
        if (!requireSession()) {
            return ERR_DEVICE_NOT_FOUND;
        }
        // Drop live view before shooting and let the camera settle.
        //
        // Measured on an R8: a shot taken while (or just after) live view is
        // running returns EDS_ERR_DEVICE_BUSY, and the same shot succeeds once EVF
        // has been down for a moment. ShootOption.checkLiveViewState pauses EVF but
        // does not wait long enough for this body. It matters because protofoto's
        // whole flow is "aim with live view, then capture", so the busy path is the
        // normal path, not an edge case.
        final int liveViewWatchers = liveViewConsumers.get();
        final boolean liveViewWasUp;
        synchronized (this) {
            liveViewWasUp = liveViewRunning;
            if (liveViewWasUp) {
                stopLiveView();
            }
        }
        if (liveViewWasUp) {
            try {
                Thread.sleep(LIVE_VIEW_TO_SHOOT_SETTLE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return errorOf(e, "takePhoto");
            }
        }

        // Arm before shooting: the event can arrive while shoot() is still returning.
        final CountDownLatch landed = new CountDownLatch(1);
        pendingCapture.set(landed);
        try {
            shootWithBusyRetry();

            // Let the camera finish writing before anything touches the card.
            // shoot() returns when the shutter command completes, well before the
            // file exists — and tearing the session down mid-write is what wedged
            // the R8 badly enough to need a power cycle.
            //
            // DirItemCreated is only a fast path here, not a requirement: measured
            // on an R8, a save-to-card shot never fires it (the object event
            // announces items offered to the *host*, and nothing is offered when
            // the image goes to the card). waitForItemDownloadEvent is unusable for
            // the same reason. So we wait for the event but fall back to a fixed
            // settle, and treat the timeout as normal rather than an error.
            landed.await(CAPTURE_SETTLE_MS, TimeUnit.MILLISECONDS);
            cardDirty = true;
            handlePictureTaken();
            return OK;
        } catch (Exception e) {
            return errorOf(e, "takePhoto");
        } finally {
            pendingCapture.set(null);
            // Put live view back for anyone still watching, so a capture does not
            // silently kill the feed the operator is aiming with.
            if (liveViewWasUp && liveViewWatchers > 0 && sessionOpen) {
                restoreLiveViewAsync();
            }
        }
    }

    @Override
    public String[] getAllImageInfo() {
        // EDSDK snapshots the card's directory tree when the session opens, so a
        // photo taken since then is invisible until the session is rebuilt. Verified
        // on an R8: after a shot the listing still ended at IMG_0466, and only a
        // fresh process saw IMG_0467.
        //
        // The rebuild is therefore necessary — but only after a capture, not on
        // every listing. Doing it unconditionally cost a session teardown per call
        // and, run immediately after the shutter, hung the camera hard. Gating on
        // cardDirty means a plain listing stays on the fast path (0.26s measured)
        // and the expensive rebuild happens once, after the settle in takePhoto.
        if (cardDirty) {
            cardDirty = false;
            refreshSession();
        }
        if (!requireSession()) {
            return new String[0];
        }
        try {
            final List<String> names = submit(() -> CanonSdkStorage.listImageNames(cameraRef));
            return names.toArray(new String[0]);
        } catch (Exception e) {
            logger.error("getAllImageInfo failed", e);
            return new String[0];
        }
    }

    /**
     * Close and re-open the session so the card listing is current, putting live
     * view back afterwards if anyone was watching. Without the restore, taking a
     * photo would silently kill the live view feed of every open protofoto tab.
     */
    private synchronized void refreshSession() {
        if (!sessionOpen) {
            return;
        }
        final int watchers = liveViewConsumers.get();
        suppressConnectionEvents = true;
        try {
            closeSession();
            if (openSession() != OK) {
                return;
            }
        } finally {
            suppressConnectionEvents = false;
        }
        if (watchers > 0) {
            liveViewConsumers.set(watchers);
            try {
                submit(() -> {
                    CanonFactory.liveViewLogic().beginLiveView(cameraRef,
                            EdsEvfOutputDevice.kEdsEvfOutputDevice_PC);
                    return null;
                });
                liveViewRunning = true;
            } catch (Exception e) {
                logger.warn("Could not restore live view after session refresh", e);
                liveViewConsumers.set(0);
            }
        }
    }

    @Override
    public byte[] getImage(final String imageName) {
        if (!requireSession()) {
            return new byte[0];
        }
        try {
            return submit(() -> CanonSdkStorage.download(cameraRef, imageName));
        } catch (Exception e) {
            logger.error("getImage({}) failed", imageName, e);
            return new byte[0];
        }
    }

    @Override
    public int formatAll() {
        if (!requireSession()) {
            return ERR_DEVICE_NOT_FOUND;
        }
        try {
            submit(() -> {
                CanonSdkStorage.formatAll(cameraRef);
                return null;
            });
            return OK;
        } catch (Exception e) {
            return errorOf(e, "formatAll");
        }
    }

    /** Legacy debug entry point from the JNI driver; nothing to do here. */
    @Override
    public void doAll() {
        logger.debug("doAll() is a no-op on the canon-sdk-java driver");
    }

    // --- focus bracketing -------------------------------------------------

    @Override
    public int setFocusBracketing(final CanonDriver.EdsFocusShiftSet item) {
        if (!requireSession()) {
            return ERR_DEVICE_NOT_FOUND;
        }
        try {
            return submit(() -> {
                final EdsFocusShiftSet struct = new EdsFocusShiftSet();
                struct.version = item.getVersion();
                struct.focusShiftFunction = item.getFocusShiftFunction();
                struct.shootingNumber = item.getShootingNumber();
                struct.stepWidth = item.getStepWidth();
                struct.exposureSmoothing = item.getExposureSmoothing();
                struct.focusStackingFunction = item.getFocusStackingFunction();
                struct.focusStackingTrimming = item.getFocusStackingTrimming();
                struct.flashInterval = item.getReserved();
                struct.write();

                final Pointer data = struct.getPointer();
                final EdsdkError error = org.blackdread.cameraframework.util.ErrorUtil.toEdsdkError(
                        CanonFactory.edsdkLibrary().EdsSetPropertyData(
                                cameraRef,
                                EdsPropertyID.kEdsPropID_FocusShiftSetting.value(),
                                0,
                                struct.size(),
                                data));
                if (error != EdsdkError.EDS_ERR_OK) {
                    logger.error("setFocusBracketing failed: {}", error);
                }
                return error.value();
            });
        } catch (Exception e) {
            return errorOf(e, "setFocusBracketing");
        }
    }

    /** No-arg variant kept for interface compatibility; the JNI driver had a hard-coded default. */
    @Override
    public int setFocusBracketing() {
        final CanonDriver.EdsFocusShiftSet defaults = new CanonDriver.EdsFocusShiftSet();
        defaults.setAll(3, 1, 5, 4, 1, 1, 1, 0);
        return setFocusBracketing(defaults);
    }

    // --- shutter speed ----------------------------------------------------

    @Override
    public String[] getShutterSpeedOptions() {
        return options(ref -> CanonFactory.propertyDescShortcutLogic().getTvDesc(ref));
    }

    @Override
    public String getCurrentShutterSpeed() {
        return current(ref -> CanonFactory.propertyGetShortcutLogic().getTv(ref));
    }

    @Override
    public String setShutterSpeed(final int shutterSpeed) {
        final int result = setProperty("shutterSpeed", shutterSpeed,
                value -> camera.getProperty().setShutterSpeedAsync(EdsTv.ofValue(value)));
        return result == OK ? getCurrentShutterSpeed() : "error: " + result;
    }

    // --- white balance ----------------------------------------------------

    @Override
    public String[] getListOfWhiteBalanceOptions() {
        return options(ref -> CanonFactory.propertyDescShortcutLogic().getWhiteBalanceDesc(ref));
    }

    @Override
    public String getWhiteBalanceSetting() {
        return current(ref -> CanonFactory.propertyGetShortcutLogic().getWhiteBalance(ref));
    }

    @Override
    public int setWhiteBalance(final int whiteBalance) {
        return setProperty("whiteBalance", whiteBalance,
                value -> camera.getProperty().setWhiteBalanceAsync(EdsWhiteBalance.ofValue(value)));
    }

    // --- ISO --------------------------------------------------------------

    @Override
    public String[] getListOfIsoOptions() {
        return options(ref -> CanonFactory.propertyDescShortcutLogic().getISOSpeedDesc(ref));
    }

    @Override
    public String getIsoSetting() {
        return current(ref -> CanonFactory.propertyGetShortcutLogic().getISOSpeed(ref));
    }

    @Override
    public int setIso(final int iso) {
        return setProperty("iso", iso,
                value -> camera.getProperty().setIsoSpeedAsync(EdsISOSpeed.ofValue(value)));
    }

    // --- aperture ---------------------------------------------------------

    @Override
    public String[] getListOfApertureOptions() {
        return options(ref -> CanonFactory.propertyDescShortcutLogic().getAvDesc(ref));
    }

    @Override
    public String getApertureSetting() {
        return current(ref -> CanonFactory.propertyGetShortcutLogic().getAv(ref));
    }

    @Override
    public int setAperture(final int aperture) {
        return setProperty("aperture", aperture,
                value -> camera.getProperty().setApertureAsync(EdsAv.ofValue(value)));
    }

    // --- image quality ----------------------------------------------------

    @Override
    public String[] getImageQualityOptions() {
        return options(ref -> CanonFactory.propertyDescShortcutLogic().getImageQualityDesc(ref));
    }

    @Override
    public String getCurrentImageQuality() {
        return current(ref -> CanonFactory.propertyGetShortcutLogic().getImageQuality(ref));
    }

    @Override
    public int setImageQuality(final int imageQuality) {
        return setProperty("imageQuality", imageQuality,
                value -> camera.getProperty().setImageQualityAsync(EdsImageQuality.ofValue(value)));
    }

    // --- live view --------------------------------------------------------

    @Override
    public void acquireLiveView() throws Exception {
        if (!requireSession()) {
            throw new IllegalStateException("No camera session open");
        }
        synchronized (this) {
            // Invalidates any pending linger-stop so a re-acquire keeps EVF up.
            liveViewGeneration.incrementAndGet();
            if (liveViewConsumers.incrementAndGet() == 1 && !liveViewRunning) {
                try {
                    submit(() -> {
                        CanonFactory.liveViewLogic().beginLiveView(cameraRef,
                                EdsEvfOutputDevice.kEdsEvfOutputDevice_PC);
                        return null;
                    });
                    liveViewRunning = true;
                    logger.info("Live view started");
                } catch (Exception e) {
                    liveViewConsumers.decrementAndGet();
                    throw e;
                }
            }
        }
    }

    @Override
    public void releaseLiveView() {
        synchronized (this) {
            // Guard against an unbalanced release dropping the count below zero and
            // then never reaching 0 again for the tabs that are genuinely streaming.
            if (liveViewConsumers.get() <= 0) {
                return;
            }
            if (liveViewConsumers.decrementAndGet() == 0) {
                scheduleLingerStop();
            }
        }
    }

    /**
     * Stop live view only if nobody re-acquires within {@link #LIVE_VIEW_LINGER_MS}.
     * <p>
     * The iOS fallback polls {@code /liveview.jpg} at ~5 Hz, and each poll is its
     * own acquire/release pair — so the consumer count legitimately hits zero
     * between polls. Stopping immediately made every poll flip the camera out of
     * and back into EVF mode, and since EDSDK needs ~2s to produce a first frame,
     * every poll returned 204 while physically cycling the camera at 5 Hz. The
     * linger keeps EVF up across the gap; a genuinely finished session still stops
     * one linger-interval later.
     */
    private void scheduleLingerStop() {
        final int generation = liveViewGeneration.incrementAndGet();
        LINGER.schedule(() -> {
            synchronized (CanonSdkDriverWrapper.this) {
                // Someone acquired (or released again) since we were scheduled.
                if (liveViewGeneration.get() != generation || liveViewConsumers.get() > 0) {
                    return;
                }
                stopLiveView();
            }
        }, LIVE_VIEW_LINGER_MS, TimeUnit.MILLISECONDS);
    }

    private void stopLiveView() {
        if (!liveViewRunning) {
            return;
        }
        try {
            submit(() -> {
                CanonFactory.liveViewLogic().endLiveView(cameraRef);
                return null;
            });
            logger.info("Live view stopped");
        } catch (Exception e) {
            logger.warn("Failed to stop live view cleanly", e);
        } finally {
            liveViewRunning = false;
        }
    }

    @Override
    public byte[] nextLiveViewFrame() {
        if (!liveViewRunning || !sessionOpen) {
            return null;
        }
        try {
            // EDSDK returns the EVF stream as a ready-made JPEG, so no decode/encode
            // round trip is needed to feed the browser's <img>.
            return submit(() -> CanonFactory.liveViewLogic().getLiveViewImageBuffer(cameraRef));
        } catch (Exception e) {
            // OBJECT_NOTREADY is the normal answer while the camera spins live view
            // up, and between frames if we poll faster than it produces them.
            logger.debug("Live view frame not ready", e);
            return null;
        }
    }

    // --- helpers ----------------------------------------------------------

    /**
     * How long any single EDSDK call may take before we give up on it.
     * <p>
     * Generous: a focus-stacked capture legitimately takes many seconds. The point
     * is not to be tight, it is to be finite — see {@link #await}.
     */
    private static final long CAMERA_CALL_TIMEOUT_SECONDS = 45;

    /**
     * Run {@code callable} on the EDSDK command thread and wait, bounded.
     */
    private <R> R submit(final CallableCommand<R> callable) throws Exception {
        return await(camera.sendGenericCommandAsync(callable));
    }

    /**
     * Wait for a canon-sdk-java command with a deadline.
     * <p>
     * Unbounded {@code get()} was a single point of total failure. canon-sdk-java's
     * dispatcher applies no timeout of its own, so one hung native call (a USB
     * flake — the classic EDSDK failure) wedged the shared camera worker forever,
     * and because {@code CameraDriverRegistry.activate} stops the outgoing driver
     * while holding its lock, the operator's escape hatch — switching drivers —
     * hung too. A deadline turns "mohican's camera is dead until restart" into a
     * failed command the operator can act on.
     */
    private <R> R await(final CanonCommand<R> command) throws Exception {
        final CompletableFuture<R> future = CompletableFuture.supplyAsync(() -> {
            try {
                return command.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (ExecutionException e) {
                throw new CompletionException(e.getCause() == null ? e : e.getCause());
            }
        }, TIMEOUT_WAITER);
        try {
            return future.get(CAMERA_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // The EDSDK thread is still stuck; we simply stop waiting on it. The
            // session is now of unknown validity, so force a re-open next time.
            sessionInvalidated();
            throw new IllegalStateException("Camera call timed out after "
                    + CAMERA_CALL_TIMEOUT_SECONDS + "s — the camera or USB link is unresponsive", e);
        } catch (ExecutionException e) {
            final Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException(cause);
        } finally {
            future.cancel(true);
        }
    }

    /**
     * Waiter pool for {@link #await}. Threads here only block on a command that is
     * already queued on EDSDK's own dispatcher, so they are cheap and short-lived
     * except when a call has genuinely hung — in which case one is deliberately
     * abandoned rather than blocking the camera worker.
     */
    private static final ExecutorService TIMEOUT_WAITER = Executors.newCachedThreadPool(r -> {
        final Thread t = new Thread(r, "camera-call-waiter");
        t.setDaemon(true);
        return t;
    });

    /**
     * Mark the session unusable after a timeout so the next command re-opens it
     * instead of piling onto a connection we no longer trust.
     */
    private void sessionInvalidated() {
        sessionOpen = false;
        liveViewRunning = false;
        liveViewConsumers.set(0);
    }

    private boolean requireSession() {
        if (sessionOpen && cameraRef != null) {
            return true;
        }
        return openSession() == OK;
    }

    // --- reconnection -----------------------------------------------------

    /**
     * Give up the current session and start trying to get it back.
     * <p>
     * Safe to call from anywhere, including the EDSDK event threads: it only flips
     * flags and hands the actual reopen to {@link #RECONNECT}. Reopening inline
     * would deadlock — {@code openSession()} is synchronized and blocks on the
     * EDSDK dispatcher, which is the very thread delivering the event.
     */
    private void handleCameraLost(final String reason) {
        final boolean wasOpen;
        synchronized (this) {
            wasOpen = sessionOpen;
            sessionInvalidated();
            cameraRef = null;
        }
        if (wasOpen) {
            logger.warn("Camera session lost ({}), will try to reconnect", reason);
            handleDisconnect();
        }
        scheduleReconnect(reason);
    }

    /**
     * Try to reopen the session, retrying with backoff until it works or the driver
     * is stopped.
     * <p>
     * Coalesced: a disconnect typically produces a state event, a camera-added
     * event and a failing command all at once, and they must not each start their
     * own retry loop.
     */
    private void scheduleReconnect(final String reason) {
        if (!sdkInitialised || stopped) {
            return;
        }
        if (!reconnecting.compareAndSet(false, true)) {
            return; // an attempt is already in flight
        }
        RECONNECT.execute(() -> {
            try {
                long delayMs = RECONNECT_START_MS;
                while (!stopped && sdkInitialised && !sessionOpen) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    if (stopped || sessionOpen) {
                        return;
                    }
                    logger.info("Attempting to reconnect to the camera ({})", reason);
                    // openSession() fires onCameraConnect on success, so the UI and
                    // the v2 CAMERA_EVENT stream recover on their own.
                    if (openSession() == OK) {
                        logger.info("Camera reconnected");
                        return;
                    }
                    delayMs = Math.min(delayMs * 2, RECONNECT_MAX_MS);
                }
            } finally {
                reconnecting.set(false);
            }
        });
    }

    /**
     * Format an option list the way the frontend expects: {@code "<edsdkValue>:<label>"}.
     * {@code CanonManager.js} shows the whole string and posts back the leading int.
     */
    private String[] options(final Function<EdsCameraRef, List<? extends NativeEnum<Integer>>> lookup) {
        if (!requireSession()) {
            return new String[0];
        }
        try {
            final List<? extends NativeEnum<Integer>> values = submit(() -> lookup.apply(cameraRef));
            final String[] result = new String[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = format(values.get(i));
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to read property options", e);
            return new String[0];
        }
    }

    /**
     * Current value in the same {@code "<edsdkValue>:<label>"} shape as the option
     * list, so the {@code <select>} in CanonManager matches an entry.
     * <p>
     * On failure returns a string starting with {@code "camera"} — {@code Mohican}'s
     * {@code CANON_GET_ALL_CURRENT_SETTINGS} treats that prefix as "no value" and
     * skips the push, which is the behaviour the JNI driver had.
     */
    private String current(final Function<EdsCameraRef, NativeEnum<Integer>> lookup) {
        if (!requireSession()) {
            return "camera: no session";
        }
        try {
            return format(submit(() -> lookup.apply(cameraRef)));
        } catch (Exception e) {
            logger.warn("Failed to read current property", e);
            return "camera: " + e.getMessage();
        }
    }

    private static String format(final NativeEnum<Integer> value) {
        return value.value() + ":" + value.description();
    }

    private int setProperty(final String name, final int value,
                            final Function<Integer, CanonCommand<Void>> setter) {
        if (!requireSession()) {
            return ERR_DEVICE_NOT_FOUND;
        }
        try {
            await(setter.apply(value));
            return OK;
        } catch (Exception e) {
            return errorOf(e, "set " + name + " to " + value);
        }
    }

    /**
     * Collapse any failure into the EDSDK error code the {@code CANON_*} protocol
     * expects. Non-EDSDK failures become a generic internal error rather than 0,
     * so callers never read a crash as success.
     */
    private int errorOf(final Exception e, final String what) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof EdsdkErrorException) {
                final EdsdkError error = ((EdsdkErrorException) cause).getEdsdkError();
                logger.error("{} failed: {}", what, error);
                // A comm/device-class error means the handle is dead, not that this
                // one call failed. Without invalidating here the fast-fail path
                // latched: sessionOpen stayed true, requireSession() kept returning
                // the stale ref, and every subsequent command failed the same way
                // until mohican was restarted.
                if (isConnectionLost(error)) {
                    handleCameraLost(what + " -> " + error);
                }
                return error.value();
            }
            cause = cause.getCause();
        }
        logger.error("{} failed", what, e);
        return EdsdkError.EDS_ERR_INTERNAL_ERROR.value();
    }

    /** Errors that mean the camera handle is gone, not that one call misfired. */
    private static boolean isConnectionLost(final EdsdkError error) {
        switch (error) {
            case EDS_ERR_COMM_DISCONNECTED:
            case EDS_ERR_DEVICE_NOT_FOUND:
            case EDS_ERR_DEVICE_INVALID:
            case EDS_ERR_DEVICE_NOT_INSTALLED:
                return true;
            default:
                return false;
        }
    }
}
