package nl.bytesoflife.inspector.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Reply to one {@code CAMERA_COMMAND}, pushed back as {@code CAMERA_RESULT}.
 * <p>
 * The v1 {@code CANON_*} protocol had no way to tell which request a push
 * belonged to — the frontend had to infer it from a state machine and guard every
 * step with a watchdog. {@link #id} echoes the caller's request id so a v2 client
 * can simply resolve a promise.
 */
@Data
@Builder
public class CameraResult {

    /** Echo of the request id. Null only if the request had no parseable id. */
    private String id;

    /** Echo of the requested operation, so logs and clients can read a result standalone. */
    private String op;

    private boolean ok;

    /** Operation payload on success; null on failure. */
    private Object data;

    /** Machine-readable failure reason; null on success. */
    private String errorCode;

    /** Human-readable failure reason; null on success. */
    private String errorMessage;

    /** Driver that served the command, e.g. {@code sdk}. */
    private String driver;

    public static CameraResult ok(final String id, final String op, final String driver, final Object data) {
        return CameraResult.builder().id(id).op(op).driver(driver).ok(true).data(data).build();
    }

    public static CameraResult error(final String id, final String op, final String driver,
                                     final String code, final String message) {
        return CameraResult.builder()
                .id(id).op(op).driver(driver).ok(false)
                .errorCode(code).errorMessage(message)
                .build();
    }

    /** Convenience for ops whose payload is a single named value. */
    public static Object value(final String key, final Object value) {
        final Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
