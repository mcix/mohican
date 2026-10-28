package nl.bytesoflife;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

/**
 * The hybrid placer's motors: two Teknic ClearPath-SC nodes behind an SC hub,
 * driven through {@code deltateknic.dll} (JNI over sFoundation).
 * <p>
 * The native library is loaded on first use, not when the class loads. A static
 * initialiser that threw would poison the class for the rest of the JVM
 * (NoClassDefFoundError on every later touch), so a missing DLL — every Mac, and
 * a Windows box without the Teknic runtime — would have taken the whole motor
 * subsystem down instead of letting Mohican fall back to the serial driver.
 * <p>
 * The native {@code openPort} finds the hub by itself (sFoundation's
 * {@code FindComHubPorts}) and opens every hub it sees; the port number it is
 * given is only echoed in its log. It returns a handle whether or not a hub was
 * found, so a failed open only shows up as the first command throwing. That is
 * why {@link nl.bytesoflife.mohican.Mohican} looks for the hub on the Java side
 * first ({@link TeknicHubFinder}) and why every native call here is guarded.
 */
public class DeltaProtoDriver implements ErosController, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(DeltaProtoDriver.class);

    /** Native library name, {@code deltateknic.dll} next to the jar on Windows. */
    public static final String NATIVE_LIBRARY = "deltateknic";

    /** Position polls that may fail in a row before the hub is considered lost. */
    private static final int MAX_POLL_FAILURES = 20;

    private static Boolean nativeLoaded = null;
    private static Throwable nativeFailure = null;

    /**
     * Load the JNI library once; later calls return the cached outcome.
     *
     * @return true when the library is available on this machine
     */
    public static synchronized boolean loadNativeLibrary() {
        if (nativeLoaded != null) {
            return nativeLoaded;
        }
        logger.info("java.library.path = {}", System.getProperty("java.library.path"));
        logger.info("*** load library {} ***", NATIVE_LIBRARY);
        try {
            System.loadLibrary(NATIVE_LIBRARY);
            nativeLoaded = true;
        } catch (Throwable t) {
            // UnsatisfiedLinkError when the DLL (or sFoundation20.dll it needs) is
            // not there, or on a platform that has no build of it at all.
            nativeFailure = t;
            nativeLoaded = false;
            logger.warn("Teknic native library not available: {}", t.toString());
        }
        return nativeLoaded;
    }

    /** Why {@link #loadNativeLibrary()} failed, or null. */
    public static Throwable nativeFailure() {
        return nativeFailure;
    }

    //6400 pulses per rotation
    //80mm per rotation

    //0.0125 mm per pulse

    private static final int TRUE=1;
    private static final int FALSE=0;

    private native long openPort( int portNum );
    private native void home( long pointer );
    private native void closePort( long pointer );
    private native void moveTo( long pointer, int x, int y, int acc, int vel, int release);
    private native void release( long pointer, int release);
    private native double getPositionX( long pointer );
    private native double getPositionY( long pointer );
    private native int getMaxAcc( long pointer );
    private native int getMaxVel( long pointer );
    private native int getMinAcc( long pointer );
    private native int getMinVel( long pointer );

    private String port;
    private long p;

    private int minAcc;
    private int minVel;
    private int maxAcc;
    private int maxVel;

    private EncoderListener encoderListenerX= null;
    private EncoderListener encoderListenerY= null;

    private int pcbZeroX= 0;
    private int pcbZeroY= 0;

    private int speed= 50;
    private int acceleration= 50;

    private volatile boolean ready = false;
    private volatile int pollFailures = 0;
    private volatile Runnable onLost = null;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        final Thread t = new Thread(r, "teknic-position");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> poll = null;

    private int getAccByPercentage( int perc ) {
        Double x= ((double)(maxAcc - minAcc) / 100);
        x= x * perc;
        return x.intValue() + minAcc;
    }

    private int getVelByPercentage( int perc ) {
        Double x= ((double)(maxVel - minVel) / 100);
        x= x * perc;
        return x.intValue() + minVel;
    }

    /**
     * @param port the COM number of the hub ("11" for COM11). Only echoed by the
     *             native driver, which finds the hub itself; kept so the log and
     *             the status show which port was meant.
     */
    public DeltaProtoDriver(String port, EncoderListener encoderListenerX, EncoderListener encoderListenerY ) {
        this.port= port;
        this.encoderListenerX= encoderListenerX;
        this.encoderListenerY= encoderListenerY;
    }

    public String getPort() {
        return port;
    }

    /** True once the hub opened and the nodes homed; false again when polling it keeps failing. */
    public boolean isReady() {
        return ready;
    }

    /** Called (once) from the poll thread when the hub stops answering. */
    public void setOnLost(Runnable onLost) {
        this.onLost = onLost;
    }

    /**
     * Open the hub, home both axes and start polling the position.
     *
     * @throws IllegalStateException when the native library is missing or the hub
     *                               does not open; the driver is unusable then and
     *                               the caller should fall back to another one
     */
    public synchronized void reInitialize() {
        stopPolling();
        ready = false;
        pollFailures = 0;

        if (!loadNativeLibrary()) {
            throw new IllegalStateException("Teknic native library (" + NATIVE_LIBRARY + ") is not available: "
                    + (nativeFailure == null ? "unknown" : nativeFailure.getMessage()));
        }

        int portNum = 0;
        try {
            portNum = Integer.parseInt(String.valueOf(port).trim());
        } catch (NumberFormatException e) {
            logger.warn("teknic.port '{}' is not a number, passing 0 to the driver (it finds the hub itself)", port);
        }
        logger.info("TeknicController.reInitialize " + portNum);

        try {
            p= openPort( portNum );
            if (p == 0) {
                throw new IllegalStateException("Teknic hub did not open (null handle)");
            }
            home(p);
            maxAcc= getMaxAcc(p);
            maxVel= getMaxVel(p);
            minAcc= getMinAcc(p);
            minVel= getMinVel(p);
        } catch (IllegalStateException e) {
            closeQuietly();
            throw e;
        } catch (Throwable t) {
            // A C++ exception that the JNI layer turned into a Java one, or a
            // link error for a symbol the DLL on this machine does not have.
            closeQuietly();
            throw new IllegalStateException("Teknic hub failed to initialise: " + t, t);
        }
        logger.info("Teknic hub open: acc {}..{} vel {}..{}", minAcc, maxAcc, minVel, maxVel);

        encoderListenerX.newPos( 0 );
        encoderListenerY.newPos( 0 );

        ready = true;
        poll = executor.scheduleWithFixedDelay(this, 0, 50, TimeUnit.MILLISECONDS);
    }

    private void stopPolling() {
        if (poll != null) {
            poll.cancel(false);
            poll = null;
        }
    }

    private void closeQuietly() {
        if (p != 0) {
            try {
                closePort(p);
            } catch (Throwable t) {
                logger.warn("closePort failed", t);
            }
            p = 0;
        }
    }

    public synchronized void closePorts() {
        stopPolling();
        ready = false;
        closeQuietly();
    }

    private void setPcbPosition(int x, int y) {
        Double doublex= getPositionX(p);
        Double doubley= getPositionY(p);

        int currentx= doublex.intValue();
        int currenty= doubley.intValue();

        pcbZeroX= currentx - x;
        pcbZeroY= currenty - y;

        //System.out.println("DeltaProtoDriver.setPcbPosition x " +pcbZeroX);
        //System.out.println("DeltaProtoDriver.setPcbPosition y " +pcbZeroY);
    }

    public void setZero() {
        //System.out.println("DeltaProtoDriver.setZero");

        setPcbPosition(0, 0);
    }

    @Override
    public void home(Integer current, Integer directionX, Integer directionY ) {

    }

    public void setPosition(Integer posX, Integer posY) {
        //System.out.println("DeltaProtoDriver.setPosition " + posX + " " + posY);

        setPcbPosition( posX, posY);
    }

    public void goTo(Integer posX, Integer posY) {

        //encoderListenerX.newPos( posX - pcbZeroX );
        //encoderListenerY.newPos( posY - pcbZeroY );

        moveTo(p, posX, posY, getAccByPercentage(acceleration), getVelByPercentage(speed), TRUE);
    }

    public void goToPcb(Integer posX, Integer posY) {

        //encoderListenerX.newPos( posX );
        //encoderListenerY.newPos( posY );

        moveTo(p, pcbZeroX + posX, pcbZeroY + posY, getAccByPercentage(acceleration), getVelByPercentage(speed), TRUE);
    }

    public void goTo(final Integer posX, final Integer posY, final Integer delayX, final Integer delayY) {

        final Thread x= new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    if( delayX > delayY ) {
                        sleep(delayX);
                    }
                    else {
                        sleep(delayY);
                    }
                    moveTo(p, pcbZeroX + posX, pcbZeroY + posY, getAccByPercentage(acceleration), getVelByPercentage(speed), TRUE);
                }
                catch( InterruptedException e )
                {
                    e.printStackTrace();
                }
            }
        } );

        x.start();

        encoderListenerX.newPos( posX );
        encoderListenerY.newPos( posY );
    }

    public void disableBrake() {
        release(p, TRUE);
    }

    @Override
    public void enableBrake() {

    }

    public void moveY(int i) {
        //System.out.println("TeknicController.moveY " + i);

        Double doublex= getPositionX(p);
        Double doubley= getPositionY(p);

        int currentx= doublex.intValue();
        int currenty= doubley.intValue();

        encoderListenerX.newPos( currentx - pcbZeroX );
        encoderListenerY.newPos( currenty - pcbZeroY );

        moveTo(p, currentx, currenty + i, minAcc, minVel, TRUE);
    }

    public void moveX(int i) {
        //System.out.println("TeknicController.moveX " + i);

        Double doublex= getPositionX(p);
        Double doubley= getPositionY(p);

        int currentx= doublex.intValue();
        int currenty= doubley.intValue();

        encoderListenerX.newPos( currentx - pcbZeroX );
        encoderListenerY.newPos( currenty - pcbZeroY );

        moveTo(p, currentx + i, currenty, minAcc, minVel, TRUE);
    }

    public void setUpdateSpeed(int updateSpeed) {
    }

    @Override
    public void setMinMaxPosition(int minX, int maxX, int minY, int maxY) {
        //not used for teknic
    }

    public Integer getPosX() {
        Double d= getPositionX(p);

        encoderListenerX.newPos( d.intValue() - pcbZeroX );

        return d.intValue();
    }

    public Integer getPosY() {
        Double d= getPositionY(p);

        encoderListenerY.newPos( d.intValue() - pcbZeroY );

        return d.intValue();
    }

    /** The serial microPlacer's speed scale (the web page's slider), for pages that still send it. */
    private static final int SERIAL_SPEED_MAX = 800;
    /** The serial microPlacer's acceleration scale. */
    private static final int SERIAL_ACC_MAX = 500;

    /**
     * 0-100 % of the velocity limit, like the legacy Mohican's slider. A value
     * above 100 is taken to be on the serial boards' scale (the web page sent
     * 10-800 here before it knew which driver it talked to) and mapped onto the
     * percentage, so the slider's end is the legacy maximum; dropping it, as
     * before, silently left the head at the 50 % start value.
     */
    public void setSpeedInPercentage( Integer value ) {
        this.speed= percentage(value, SERIAL_SPEED_MAX, "speed");
    }

    private int percentage(Integer value, int serialMax, String what) {
        if (value == null) {
            logger.warn("No {} value", what);
            return 50;
        }
        if (value > 100) {
            final int mapped = Math.min(100, (int) Math.round(value * 100.0 / serialMax));
            logger.warn("{} {} is on the serial scale (max {}), using {} %", what, value, serialMax, mapped);
            return mapped;
        }
        if (value < 0) {
            logger.warn("{} {} is below zero, using 0 %", what, value);
            return 0;
        }
        return value;
    }

    @Override
    public void setCurrent(int current) {

    }



    @Override
    public void message(String device, String value) {

    }

    @Override
    public Map<String, String> getVersion() {
        final Map<String, String> version = new HashMap<>();
        version.put("driver", "teknic");
        version.put("port", "COM" + port);
        return version;
    }

    /** 0-100 % of the acceleration limit; see {@link #setSpeedInPercentage}. */
    public void setAccelerationInPercentage( Integer value ) {
        this.acceleration= percentage(value, SERIAL_ACC_MAX, "acceleration");
    }

    /**
     * The position poll. A throwing native call must not kill the scheduler
     * silently (a ScheduledExecutorService stops a task after its first
     * exception); it is counted, and after enough failures in a row the hub is
     * reported lost so Mohican can look for it again.
     */
    @Override
    public void run() {
        if (!ready) {
            return;
        }
        try {
            Integer x = getPosX();
            Integer y = getPosY();

            encoderListenerX.newPos( x );
            encoderListenerY.newPos( y );
            pollFailures = 0;
        } catch (Throwable t) {
            pollFailures++;
            if (pollFailures == 1) {
                logger.warn("Teknic position poll failed: {}", t.toString());
            }
            if (pollFailures >= MAX_POLL_FAILURES) {
                logger.error("Teknic hub stopped answering ({} polls failed), giving it up", pollFailures);
                ready = false;
                stopPolling();
                final Runnable lost = onLost;
                if (lost != null) {
                    lost.run();
                }
            }
        }
    }
}
