package nl.bytesoflife.inspector.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A parsed {@code CAMERA_COMMAND} request.
 * <p>
 * Wire shape:
 * <pre>
 * { "type": "CAMERA_COMMAND",
 *   "value": { "id": "c-17", "op": "setSetting", "args": { "setting": "iso", "value": 72 } } }
 * </pre>
 * The value arrives as a {@code LinkedHashMap} (Jackson over HTTP, or the STOMP
 * JSON parser), so this parses defensively rather than relying on typed binding —
 * the same approach {@code CANON_SET_FOCUS_STACKING} already takes.
 */
public class CameraCommand {

    private final String id;
    private final String op;
    private final Map<String, Object> args;

    private CameraCommand(final String id, final String op, final Map<String, Object> args) {
        this.id = id;
        this.op = op;
        this.args = args;
    }

    public String getId() {
        return id;
    }

    public String getOp() {
        return op;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    /**
     * @throws IllegalArgumentException if the envelope is not a map or has no op
     */
    @SuppressWarnings("unchecked")
    public static CameraCommand parse(final Object rawValue) {
        if (!(rawValue instanceof Map)) {
            throw new IllegalArgumentException(
                    "CAMERA_COMMAND value must be an object with an 'op' field");
        }
        final Map<String, Object> map = (Map<String, Object>) rawValue;

        final Object op = map.get("op");
        if (op == null || String.valueOf(op).trim().isEmpty()) {
            throw new IllegalArgumentException("CAMERA_COMMAND is missing 'op'");
        }

        final Object rawArgs = map.get("args");
        final Map<String, Object> args = rawArgs instanceof Map
                ? (Map<String, Object>) rawArgs
                : Collections.<String, Object>emptyMap();

        final Object id = map.get("id");
        return new CameraCommand(id == null ? null : String.valueOf(id), String.valueOf(op).trim(),
                new LinkedHashMap<>(args));
    }

    // --- typed argument access -------------------------------------------

    public String getString(final String name) {
        final Object value = args.get(name);
        return value == null ? null : String.valueOf(value);
    }

    public String requireString(final String name) {
        final String value = getString(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("'" + op + "' requires argument '" + name + "'");
        }
        return value;
    }

    /**
     * JSON numbers reach us as Integer, Long or Double depending on the parser
     * and the literal, so normalise through {@link Number} rather than casting.
     */
    public Integer getInt(final String name) {
        final Object value = args.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "'" + op + "' argument '" + name + "' must be a number, got: " + value);
        }
    }

    public int requireInt(final String name) {
        final Integer value = getInt(name);
        if (value == null) {
            throw new IllegalArgumentException("'" + op + "' requires numeric argument '" + name + "'");
        }
        return value;
    }

    public int getInt(final String name, final int fallback) {
        final Integer value = getInt(name);
        return value == null ? fallback : value;
    }

    @Override
    public String toString() {
        return "CameraCommand{id=" + id + ", op=" + op + ", args=" + args + "}";
    }
}
