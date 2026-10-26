package nl.bytesoflife;

/**
 * Thrown by the deltateknic native driver when sFoundation reports an error
 * (an mnErr: the hub port would not open, a node refused to enable, a move
 * timed out, ...) or when the driver's own checks fail (no hub found, an axis
 * not homed, acceleration out of range).
 * <p>
 * The native side looks this class up by name (nl/bytesoflife/TeknicException)
 * and falls back to RuntimeException when it is missing, so an older jar still
 * works with a newer DLL. Unchecked on purpose: the native method declarations
 * in {@link DeltaProtoDriver} stay as they are, and the caller decides whether a
 * failure means "fall back to another driver" (opening) or "the command did
 * not happen" (a move).
 */
public class TeknicException extends RuntimeException {

    /** sFoundation's cnErrCode, 0 for the driver's own checks. */
    private final int errorCode;
    /** The node address the error came from, -1 if none. */
    private final int address;

    public TeknicException(String message, int errorCode, int address) {
        super(message);
        this.errorCode = errorCode;
        this.address = address;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public int getAddress() {
        return address;
    }
}
