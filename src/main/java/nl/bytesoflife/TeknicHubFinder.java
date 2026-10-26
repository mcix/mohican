package nl.bytesoflife;

import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the Teknic ClearPath-SC hub among the serial ports of this machine.
 * <p>
 * The hub is an FTDI-based USB device that Windows exposes as a COM port. The
 * native driver ({@code deltateknic.dll}) locates it by itself through
 * sFoundation's {@code FindComHubPorts} once asked to open — but asking it with
 * no hub attached leaves the native side in a failed state it cannot report
 * back (its {@code openPort} returns an object either way). So before the native
 * code is touched at all, the Java side looks for the hub here, by the name the
 * operating system gives the port. That decides between the Teknic driver and
 * the serial microPlacer driver without any configuration.
 * <p>
 * The match is a regular expression on the port's description and friendly name
 * ({@code teknic.hub.match} in configuration.properties); every port seen is
 * logged so a hub that identifies itself differently can be recognised by
 * widening the pattern.
 */
public final class TeknicHubFinder {

    private static final Logger logger = LoggerFactory.getLogger(TeknicHubFinder.class);

    /** The USB names Teknic gives the hub, in the forms seen so far. */
    public static final String DEFAULT_MATCH = "(?i)teknic|sc-?hub|clearpath";

    private static final Pattern COM_NUMBER = Pattern.compile("(?i)COM(\\d+)");

    /** A serial port that looks like the hub. */
    public static final class HubPort {
        /** OS name, "COM11" on Windows. */
        public final String systemName;
        /** What the port calls itself, for the operator. */
        public final String description;
        /** The COM number, or -1 when the name carries none (macOS, Linux). */
        public final int comNumber;

        HubPort(String systemName, String description, int comNumber) {
            this.systemName = systemName;
            this.description = description;
            this.comNumber = comNumber;
        }

        @Override
        public String toString() {
            return systemName + " (" + description + ")";
        }
    }

    private static String lastDescribed = null;

    private TeknicHubFinder() {
    }

    /** Every serial port of this machine, or an empty list when enumeration itself fails. */
    public static List<SerialPort> ports() {
        try {
            final SerialPort[] found = SerialPort.getCommPorts();
            return found == null ? Collections.<SerialPort>emptyList() : new ArrayList<>(java.util.Arrays.asList(found));
        } catch (Throwable t) {
            // jSerialComm needs its native library; without it there is simply no list.
            logger.warn("Could not enumerate serial ports: {}", t.toString());
            return Collections.emptyList();
        }
    }

    /**
     * The first port whose name matches {@code match}, or null.
     *
     * @param match a regular expression; null or blank means {@link #DEFAULT_MATCH}
     */
    public static HubPort find(String match) {
        final Pattern pattern = compile(match);
        final List<SerialPort> ports = ports();
        final String described = describe(ports);
        // The search repeats while no machine answers; only a change is worth a line.
        if (!described.equals(lastDescribed)) {
            logger.info("Serial ports: {}", described);
            lastDescribed = described;
        } else {
            logger.debug("Serial ports: {}", described);
        }
        for (SerialPort port : ports) {
            final String name = nameOf(port);
            if (pattern.matcher(name).find()) {
                final HubPort hub = new HubPort(port.getSystemPortName(), name, comNumberOf(port.getSystemPortName()));
                logger.info("Teknic hub found on {}", hub);
                return hub;
            }
        }
        return null;
    }

    /** "COM3 (USB Serial Port), COM11 (Teknic SC-HUB)" — for the log and the status label. */
    public static String describe(List<SerialPort> ports) {
        if (ports.isEmpty()) {
            return "none";
        }
        final StringBuilder sb = new StringBuilder();
        for (SerialPort port : ports) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(port.getSystemPortName()).append(" (").append(nameOf(port)).append(')');
        }
        return sb.toString();
    }

    /** The description and the friendly name, joined, so one pattern covers both. */
    private static String nameOf(SerialPort port) {
        final String description = port.getPortDescription();
        final String friendly = port.getDescriptivePortName();
        if (description == null || description.isEmpty()) {
            return friendly == null ? "" : friendly;
        }
        if (friendly == null || friendly.isEmpty() || friendly.contains(description)) {
            return description;
        }
        return description + " / " + friendly;
    }

    static int comNumberOf(String systemName) {
        if (systemName == null) {
            return -1;
        }
        final Matcher m = COM_NUMBER.matcher(systemName);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static Pattern compile(String match) {
        if (match == null || match.trim().isEmpty()) {
            return Pattern.compile(DEFAULT_MATCH);
        }
        try {
            return Pattern.compile(match.trim());
        } catch (Exception e) {
            logger.warn("teknic.hub.match '{}' is not a valid pattern, using the default", match);
            return Pattern.compile(DEFAULT_MATCH);
        }
    }
}
