package nl.bytesoflife.mohican;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ends a running legacy Mohican (the Swing application, main class
 * {@code nl.vanderheijde.stapper.Eros}) before this client opens the machine.
 * <p>
 * Only one process can hold the Teknic hub's COM port. The legacy application
 * does not close it on exit either, and when it finds the port busy its native
 * driver crashes outright — so when an operator switches to the web page while
 * the legacy window is still open, nothing works until somebody kills it by
 * hand. Windows only: the shop-floor boxes; elsewhere this is a no-op.
 * <p>
 * The processes are listed with PowerShell ({@code Get-CimInstance Win32_Process},
 * {@code wmic} is gone from current Windows), matched on their command line
 * against {@code legacy.kill.match}, and ended with {@code taskkill /F}. This
 * process itself is never a candidate, whatever the pattern says.
 */
public final class LegacyProcessKiller {

    private static final Logger logger = LoggerFactory.getLogger(LegacyProcessKiller.class);

    /** The legacy main class, its assembled jar, or a checkout named after it. */
    public static final String DEFAULT_MATCH = "(?i)nl\\.vanderheijde\\.stapper|mohican-\\d[\\w.-]*-jar-with-dependencies\\.jar|mohican-legacy";

    /** A java process and the command line it was started with. */
    static final class JavaProcess {
        final long pid;
        final String commandLine;

        JavaProcess(long pid, String commandLine) {
            this.pid = pid;
            this.commandLine = commandLine;
        }
    }

    private LegacyProcessKiller() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * Kill every legacy process; returns what was done, for the log and the
     * status label, or null when there was nothing to do.
     */
    public static String killLegacy(String match) {
        if (!isWindows()) {
            return null;
        }
        final Pattern pattern = compile(match);
        final List<JavaProcess> all;
        try {
            all = listJavaProcesses();
        } catch (Exception e) {
            logger.warn("Could not list the running java processes: {}", e.toString());
            return null;
        }
        final long self = ownPid();
        final List<JavaProcess> victims = new ArrayList<>();
        for (JavaProcess p : all) {
            if (p.pid != self && pattern.matcher(p.commandLine).find()) {
                victims.add(p);
            }
        }
        if (victims.isEmpty()) {
            logger.info("No legacy Mohican running ({} java processes seen)", all.size());
            return null;
        }
        final StringBuilder done = new StringBuilder();
        for (JavaProcess p : victims) {
            logger.warn("Ending the legacy Mohican, pid {}: {}", p.pid, abbreviate(p.commandLine));
            final boolean ok = taskkill(p.pid);
            if (done.length() > 0) {
                done.append(", ");
            }
            done.append("pid ").append(p.pid).append(ok ? "" : " (taskkill failed)");
        }
        // The port is only free once the process is gone; taskkill returns before that.
        waitUntilGone(victims, 5000);
        return "Closed the legacy Mohican (" + done + ")";
    }

    /** "pid command line", one process per line. */
    private static final Pattern PROCESS_LINE = Pattern.compile("^\\s*(\\d+)\\s+(.*)$");

    static List<JavaProcess> listJavaProcesses() throws Exception {
        // Passed base64-encoded: a -Command argument with quotes inside does not
        // survive the Windows command line quoting ProcessBuilder applies.
        final String script = "Get-CimInstance Win32_Process -Filter \"Name like 'java%'\" | ForEach-Object { \"$($_.ProcessId) $($_.CommandLine)\" }";
        final String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        final List<String> lines = run(new String[]{"powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded}, 15);
        return parse(lines);
    }

    static List<JavaProcess> parse(List<String> lines) {
        final List<JavaProcess> out = new ArrayList<>();
        for (String line : lines) {
            final Matcher m = PROCESS_LINE.matcher(line);
            if (m.matches()) {
                out.add(new JavaProcess(Long.parseLong(m.group(1)), m.group(2).trim()));
            }
        }
        return out;
    }

    private static boolean taskkill(long pid) {
        try {
            run(new String[]{"taskkill", "/PID", String.valueOf(pid), "/F"}, 10);
            return true;
        } catch (Exception e) {
            logger.warn("taskkill {} failed: {}", pid, e.toString());
            return false;
        }
    }

    private static void waitUntilGone(List<JavaProcess> victims, long maxMs) {
        final long deadline = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                final List<JavaProcess> now = listJavaProcesses();
                boolean alive = false;
                for (JavaProcess v : victims) {
                    for (JavaProcess p : now) {
                        if (p.pid == v.pid) {
                            alive = true;
                        }
                    }
                }
                if (!alive) {
                    return;
                }
                Thread.sleep(250);
            } catch (Exception e) {
                return;
            }
        }
        logger.warn("A legacy Mohican was still running after {} ms", maxMs);
    }

    /** Runs a command, returns its stdout lines; throws when it fails or takes too long. */
    private static List<String> run(String[] command, int timeoutSeconds) throws Exception {
        final ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        final Process process = pb.start();
        final List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(command[0] + " did not finish in " + timeoutSeconds + " s");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(command[0] + " exited with " + process.exitValue() + ": " + String.join(" ", lines));
        }
        return lines;
    }

    /** Java 8: the runtime name is "pid@host". */
    static long ownPid() {
        try {
            final String name = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(name.substring(0, name.indexOf('@')));
        } catch (Exception e) {
            return -1;
        }
    }

    private static Pattern compile(String match) {
        if (match == null || match.trim().isEmpty()) {
            return Pattern.compile(DEFAULT_MATCH);
        }
        try {
            return Pattern.compile(match.trim());
        } catch (Exception e) {
            logger.warn("legacy.kill.match '{}' is not a valid pattern, using the default", match);
            return Pattern.compile(DEFAULT_MATCH);
        }
    }

    private static String abbreviate(String s) {
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }
}
