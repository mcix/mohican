package nl.bytesoflife.mohican;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Routes every incoming {@link ReduxAction} onto a worker, one queue per category.
 * <p>
 * <b>Why.</b> Commands used to run inline on whichever thread delivered them — the
 * Tomcat request thread for {@code POST /mohicanreq}, or the STOMP message thread.
 * A camera capture therefore pinned an HTTP thread for seconds, and a slow camera
 * could starve the pool that motor commands also arrive on.
 * <p>
 * <b>Why per category and not one pool.</b> Two properties have to hold at once:
 * <ul>
 *   <li><b>Order within a category.</b> Motion is a sequence — a GOTO followed by a
 *       BRAKE must execute in that order. Each category is therefore a
 *       <i>single</i> thread, never a pool: a pool would reorder them.</li>
 *   <li><b>Isolation between categories.</b> A camera capture takes seconds; a motor
 *       command must not wait behind it. Separate queues give that for free.</li>
 * </ul>
 * <p>
 * <b>Why queueing motion is safe.</b> {@code MotorController.goTo}/{@code enableBrake}
 * only append a string to the serial writer's own queue; they do not block until the
 * move completes. So a BRAKE queued behind a GOTO is not queued behind the
 * <i>movement</i> — the GOTO handler returns in microseconds. If a motion handler
 * ever becomes blocking, this assumption has to be revisited, because BRAKE is the
 * stop control.
 * <p>
 * Queues are unbounded so a command is never silently dropped; depth is logged
 * instead once it passes {@link #QUEUE_WARN_DEPTH}, which in practice means a
 * handler is wedged.
 */
public class MessageDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatcher.class);

    /** Depth at which a queue is almost certainly backed up behind a stuck handler. */
    private static final int QUEUE_WARN_DEPTH = 50;

    public enum Category {
        /** Motors, position, homing, braking. Order-sensitive and latency-sensitive. */
        MOTION("motion"),
        /** Everything that talks to the camera — both the v1 and v2 protocols. */
        CAMERA("camera"),
        /** Version queries and anything unclassified. */
        DEVICE("device");

        private final String threadName;

        Category(final String threadName) {
            this.threadName = threadName;
        }
    }

    private final ThreadPoolExecutor motion = single(Category.MOTION);
    private final ThreadPoolExecutor camera = single(Category.CAMERA);
    private final ThreadPoolExecutor device = single(Category.DEVICE);

    /** True on the worker thread of the category it belongs to. */
    private static final ThreadLocal<Category> CURRENT_CATEGORY = new ThreadLocal<>();

    /**
     * Whether the caller is already running on {@code category}'s worker.
     * <p>
     * Lets a handler that is itself on the worker run nested work inline instead of
     * re-queueing it, which would put the nested work <i>behind</i> commands that
     * arrived after it.
     */
    public static boolean isOn(final Category category) {
        return CURRENT_CATEGORY.get() == category;
    }

    private static ThreadPoolExecutor single(final Category category) {
        final ThreadFactory factory = r -> {
            final Thread t = new Thread(() -> {
                CURRENT_CATEGORY.set(category);
                r.run();
            }, "mohican-" + category.threadName);
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(), factory);
    }

    /**
     * Which worker handles a message type.
     * <p>
     * {@code CANON_*} is matched by prefix so new v1 commands land on the camera
     * queue without anyone remembering to update this list.
     */
    public static Category categoryOf(final String type) {
        if (type == null) {
            return Category.DEVICE;
        }
        if (type.startsWith("CANON_") || type.startsWith("CAMERA_") || "INIT_CAMERA".equals(type)) {
            return Category.CAMERA;
        }
        switch (type) {
            // MESSAGE carries raw device strings to the X/Y/MAIN controllers —
            // vacuum, LEDs, pumps — which BomMohican interleaves with GOTOs during
            // pick-and-place. Before per-category dispatch these all ran inline in
            // arrival order; putting MESSAGE on its own queue would let a "vacuum
            // on" and the move that depends on it reorder. Same hardware, same
            // queue.
            case "MESSAGE":
            case "POSITION_TIMEOUT":
            case "SET_POSITION":
            case "GOTO_PCB":
            case "GOTO":
            case "BRAKE":
            case "SET_PCB_POSITION":
            case "SET_PLANKJE_POSITION":
            case "SET_ACCELERATION":
            case "SET_SPEED":
            case "SET_CURRENT":
            case "SET_MIN_MAX_POSITION":
            case "ZERO":
            case "HOME":
                return Category.MOTION;
            default:
                return Category.DEVICE;
        }
    }

    /**
     * Hand {@code action} to its category's worker and return immediately.
     * <p>
     * {@code handler} must not throw past this point — anything it does throw is
     * logged and swallowed so one bad command cannot kill the worker and strand
     * every command queued behind it.
     */
    public void dispatch(final ReduxAction action, final Consumer<ReduxAction> handler) {
        final Category category = categoryOf(action == null ? null : action.getType());
        final ThreadPoolExecutor executor = executorFor(category);

        final int depth = executor.getQueue().size();
        if (depth >= QUEUE_WARN_DEPTH) {
            logger.warn("{} command queue is {} deep — a handler is probably stuck (next: {})",
                    category.threadName, depth, action == null ? "null" : action.getType());
        }

        executor.execute(() -> {
            try {
                handler.accept(action);
            } catch (Throwable t) {
                logger.error("Handler for {} failed",
                        action == null ? "null" : action.getType(), t);
            }
        });
    }

    /**
     * The camera worker, so the v2 command service can share this queue instead of
     * running its own. Sharing matters: v1 and v2 drive the same driver, and on
     * separate threads a {@code CANON_TAKE_PHOTO} could interleave with a v2
     * {@code getLastImage} mid-operation.
     */
    public Executor cameraExecutor() {
        return camera;
    }

    private ThreadPoolExecutor executorFor(final Category category) {
        switch (category) {
            case MOTION:
                return motion;
            case CAMERA:
                return camera;
            default:
                return device;
        }
    }

    /** Queue depths, for diagnostics. */
    public String describeQueues() {
        return "motion=" + motion.getQueue().size()
                + " camera=" + camera.getQueue().size()
                + " device=" + device.getQueue().size();
    }

    public void shutdown() {
        for (ExecutorService executor : new ExecutorService[]{motion, camera, device}) {
            executor.shutdownNow();
        }
    }
}
