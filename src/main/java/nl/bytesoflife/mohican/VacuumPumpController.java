package nl.bytesoflife.mohican;

import com.fazecast.jSerialComm.SerialPort;
import nl.bytesoflife.TeknicHubFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Keeps the vacuum up on the machines whose pump is not under the controller's
 * own command.
 * <p>
 * Two kinds of machine exist. On the newer ones the motor controller carries
 * the vacuum sensor and the pump switch itself and nothing is needed here. On
 * the older ones the sensor sits on the "HP3 main" board, reached over its own
 * serial port, and the pump hangs off an FTDI relay board: the relay follows
 * the RTS line of that FTDI's serial port. The legacy Mohican ran a thread for
 * this when {@code vacuum.port} was set ({@code Hp3mainController}), and that
 * is the whole difference between the two configurations: a machine with the
 * integrated controller has no {@code vacuum.port} line.
 * <p>
 * The loop is the legacy one. Every {@code vacuum.interval.ms} the board is
 * asked {@code VACUUM\r} and answers {@code vacuum:NNNN}; the number falls as
 * the vacuum builds. Below {@code vacuum.top} (enough vacuum) the pump goes
 * off, above {@code vacuum.bottom} (vacuum lost) it goes on, in between it
 * keeps doing what it did. The relay board is found by its port name
 * ({@code vacuum.relay.port}, or {@code auto}: the first port matching
 * {@code vacuum.relay.match} that is not the sensor, a motor port or the Teknic
 * hub); the legacy application took "the first FTDI device" through the D2XX
 * driver, which needed a native library of its own and happened to be right on
 * the machines it ran on.
 * <p>
 * Unlike the legacy loop, this one never leaves the pump running blind: when
 * the sensor gives no reading {@code vacuum.missed.max} rounds in a row, or its
 * port is gone, the pump is switched off until a reading comes back. A port
 * that is missing or stops answering is tried again on the next round, and the
 * pump is switched off on the way out as well.
 */
public final class VacuumPumpController implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(VacuumPumpController.class);

    /** How the FTDI relay board announces itself on Windows. */
    public static final String DEFAULT_RELAY_MATCH = "(?i)FTDI|USB Serial Port";

    /** What the operator sees: the last reading and what the pump is doing. */
    public static final class State {
        /** The last value read, or null when the sensor did not answer. */
        public final Integer value;
        /** Whether the relay is switched on; null while no relay is driven. */
        public final Boolean pumpOn;
        /** True when the sensor answers and a relay is driven. */
        public final boolean ok;
        /** One line for the window, the log and the web page. */
        public final String status;

        State(Integer value, Boolean pumpOn, boolean ok, String status) {
            this.value = value;
            this.pumpOn = pumpOn;
            this.ok = ok;
            this.status = status;
        }
    }

    private final String sensorPortName;
    private final String relayPortName;
    private final Pattern relayMatch;
    private final List<String> reservedPorts;
    private final int top;
    private final int bottom;
    private final long intervalMs;
    private final int missedMax;
    private final Consumer<State> listener;

    private final Thread thread;
    private volatile boolean running = true;
    private SerialPort sensor;
    private SerialPort relay;
    private Boolean pumpOn;
    /** Rounds in a row without a reading. */
    private int missed = 0;
    private volatile State state = new State(null, null, false, "vacuum: starting…");

    /**
     * @param sensorPortName the HP3 main board's port ({@code vacuum.port})
     * @param relayPortName  the relay board's port, or {@code auto}
     * @param relayMatch     how the relay board's port is recognised when auto
     * @param reservedPorts  ports that are never the relay (motor ports, the hub)
     * @param top            below this reading the pump goes off
     * @param bottom         above this reading the pump goes on
     * @param intervalMs     the time between readings
     * @param missedMax      readings missed in a row before the pump is switched off
     * @param listener       told about every reading, off the caller's thread
     */
    public VacuumPumpController(String sensorPortName, String relayPortName, String relayMatch, List<String> reservedPorts,
                                int top, int bottom, long intervalMs, int missedMax, Consumer<State> listener) {
        this.sensorPortName = sensorPortName;
        this.relayPortName = relayPortName == null || relayPortName.trim().isEmpty() ? "auto" : relayPortName.trim();
        this.relayMatch = compile(relayMatch);
        this.reservedPorts = reservedPorts == null ? new ArrayList<String>() : new ArrayList<>(reservedPorts);
        this.top = top;
        this.bottom = bottom;
        this.intervalMs = Math.max(200, intervalMs);
        this.missedMax = Math.max(1, missedMax);
        this.listener = listener;
        this.thread = new Thread(this, "vacuum-pump");
        this.thread.setDaemon(true);
    }

    public void start() {
        logger.info("Vacuum control: sensor on {}, relay {}, pump off below {}, on above {}", sensorPortName, relayPortName, top, bottom);
        thread.start();
    }

    public State getState() {
        return state;
    }

    /** Pump off, ports closed. Waits briefly for the loop to let go of them. */
    public void close() {
        running = false;
        thread.interrupt();
        try {
            thread.join(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            releasePorts(true);
        }
    }

    @Override
    public void run() {
        while (running) {
            synchronized (this) {
                if (running) {
                    round();
                }
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void round() {
        if (sensor == null && !openSensor()) {
            noReading("vacuum sensor " + sensorPortName + " not found");
            return;
        }
        if (relay == null && !openRelay()) {
            // Keep reading so the operator sees the level; nobody drives the pump.
            final Integer value = read();
            report(value, null, false, value == null
                    ? "vacuum sensor " + sensorPortName + " not answering, no relay"
                    : "vacuum " + value + ", no pump relay found");
            return;
        }
        final Integer value = read();
        if (value == null) {
            noReading("vacuum sensor " + sensorPortName + " not answering");
            return;
        }
        missed = 0;
        Boolean want = pumpOn;
        if (value < top) {
            want = Boolean.FALSE;
        } else if (value > bottom) {
            want = Boolean.TRUE;
        }
        if (want != null && !want.equals(pumpOn)) {
            logger.info("Vacuum {}: pump {}", value, want ? "ON" : "OFF");
        }
        if (want != null && !setPump(want)) {
            report(value, pumpOn, false, "vacuum " + value + ", relay lost");
            return;
        }
        report(value, pumpOn, true, "vacuum " + value + ", pump " + (pumpOn == null ? "untouched" : pumpOn ? "on" : "off"));
    }

    /**
     * The safeguard: a pump nobody can see the result of must not keep running.
     * After {@code missedMax} rounds without a reading the relay is opened, and
     * stays open until the sensor answers again.
     */
    private void noReading(String why) {
        missed++;
        if (relay != null && missed >= missedMax && !Boolean.FALSE.equals(pumpOn)) {
            logger.warn("{} for {} rounds: pump OFF", why, missed);
            setPump(false);
        }
        report(null, pumpOn, false, why + (Boolean.FALSE.equals(pumpOn) && missed >= missedMax ? ", pump off" : ""));
    }

    private boolean openSensor() {
        try {
            final SerialPort port = SerialPort.getCommPort(sensorPortName);
            port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0);
            if (!port.openPort()) {
                logger.warn("Vacuum sensor port {} could not be opened", sensorPortName);
                return false;
            }
            sensor = port;
            logger.info("Vacuum sensor port {} open", sensorPortName);
            return true;
        } catch (Throwable t) {
            logger.warn("Vacuum sensor port {}: {}", sensorPortName, t.toString());
            return false;
        }
    }

    private boolean openRelay() {
        final SerialPort port = findRelay();
        if (port == null) {
            return false;
        }
        try {
            // jSerialComm raises RTS when it opens a port; the relay would click
            // the pump on for a moment. Ask for it low before the open.
            port.clearRTS();
            port.clearDTR();
            port.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            if (!port.openPort()) {
                logger.warn("Vacuum relay port {} could not be opened", port.getSystemPortName());
                return false;
            }
            relay = port;
            pumpOn = null;
            logger.info("Vacuum relay on {} ({})", port.getSystemPortName(), port.getPortDescription());
            return true;
        } catch (Throwable t) {
            logger.warn("Vacuum relay port {}: {}", port.getSystemPortName(), t.toString());
            return false;
        }
    }

    private SerialPort findRelay() {
        final List<SerialPort> ports = TeknicHubFinder.ports();
        if (!"auto".equalsIgnoreCase(relayPortName)) {
            for (SerialPort port : ports) {
                if (relayPortName.equalsIgnoreCase(port.getSystemPortName())) {
                    return port;
                }
            }
            try {
                return SerialPort.getCommPort(relayPortName);
            } catch (Throwable t) {
                logger.warn("Vacuum relay port {} is not a serial port: {}", relayPortName, t.toString());
                return null;
            }
        }
        final List<SerialPort> candidates = new ArrayList<>();
        for (SerialPort port : ports) {
            final String name = port.getSystemPortName();
            if (name.equalsIgnoreCase(sensorPortName) || isReserved(name)) {
                continue;
            }
            final String described = port.getPortDescription() + " / " + port.getDescriptivePortName();
            if (relayMatch.matcher(described).find()) {
                candidates.add(port);
            }
        }
        if (candidates.isEmpty()) {
            logger.warn("No vacuum relay among the serial ports: {}", TeknicHubFinder.describe(ports));
            return null;
        }
        if (candidates.size() > 1) {
            logger.warn("Several ports could be the vacuum relay, taking the first; set vacuum.relay.port to choose: {}",
                    TeknicHubFinder.describe(candidates));
        }
        return candidates.get(0);
    }

    private boolean isReserved(String name) {
        for (String reserved : reservedPorts) {
            if (reserved != null && reserved.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** One reading, or null when the board gives no usable answer. */
    private Integer read() {
        try {
            sensor.flushIOBuffers();
            final byte[] ask = "VACUUM\r".getBytes(StandardCharsets.US_ASCII);
            if (sensor.writeBytes(ask, ask.length) != ask.length) {
                dropSensor("write failed");
                return null;
            }
            final String line = readLine(sensor, 1000);
            if (line == null) {
                return null;
            }
            return parse(line);
        } catch (Throwable t) {
            dropSensor(t.toString());
            return null;
        }
    }

    /** Up to the newline, within {@code budgetMs}; null on timeout or a dead port. */
    private String readLine(SerialPort port, long budgetMs) {
        final StringBuilder sb = new StringBuilder();
        final byte[] one = new byte[1];
        final long deadline = System.currentTimeMillis() + budgetMs;
        while (System.currentTimeMillis() < deadline) {
            final int n = port.readBytes(one, 1);
            if (n < 0) {
                dropSensor("read failed");
                return null;
            }
            if (n == 0) {
                continue;
            }
            if (one[0] == '\n') {
                return sb.toString();
            }
            if (sb.length() < 200) {
                sb.append((char) one[0]);
            }
        }
        return null;
    }

    /** "vacuum:1850\r" → 1850; null for anything else. */
    static Integer parse(String line) {
        if (line == null) {
            return null;
        }
        final String s = line.trim();
        if (!s.toLowerCase().startsWith("vacuum:")) {
            return null;
        }
        try {
            return Integer.valueOf(s.substring("vacuum:".length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean setPump(boolean on) {
        try {
            final boolean done = on ? relay.setRTS() : relay.clearRTS();
            if (!done) {
                dropRelay("could not set RTS");
                return false;
            }
            pumpOn = on;
            return true;
        } catch (Throwable t) {
            dropRelay(t.toString());
            return false;
        }
    }

    private void dropSensor(String why) {
        logger.warn("Vacuum sensor {}: {}; reopening on the next round", sensorPortName, why);
        if (sensor != null) {
            try {
                sensor.closePort();
            } catch (Throwable ignored) {
            }
        }
        sensor = null;
    }

    private void dropRelay(String why) {
        logger.warn("Vacuum relay {}: {}; looking again on the next round", relay == null ? "?" : relay.getSystemPortName(), why);
        if (relay != null) {
            try {
                relay.closePort();
            } catch (Throwable ignored) {
            }
        }
        relay = null;
        pumpOn = null;
    }

    private void releasePorts(boolean pumpOff) {
        if (relay != null) {
            try {
                if (pumpOff) {
                    relay.clearRTS();
                }
                relay.closePort();
            } catch (Throwable ignored) {
            }
            relay = null;
        }
        if (sensor != null) {
            try {
                sensor.closePort();
            } catch (Throwable ignored) {
            }
            sensor = null;
        }
        pumpOn = null;
        logger.info("Vacuum ports closed");
    }

    private void report(Integer value, Boolean pump, boolean ok, String status) {
        final State previous = state;
        final State next = new State(value, pump, ok, status);
        state = next;
        if (!ok && (previous.ok || !status.equals(previous.status))) {
            logger.warn(status);
        }
        if (listener != null) {
            try {
                listener.accept(next);
            } catch (Throwable t) {
                logger.warn("Vacuum listener failed", t);
            }
        }
    }

    private static Pattern compile(String match) {
        if (match == null || match.trim().isEmpty()) {
            return Pattern.compile(DEFAULT_RELAY_MATCH);
        }
        try {
            return Pattern.compile(match.trim());
        } catch (Exception e) {
            logger.warn("vacuum.relay.match '{}' is not a valid pattern, using the default", match);
            return Pattern.compile(DEFAULT_RELAY_MATCH);
        }
    }
}
