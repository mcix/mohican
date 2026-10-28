package nl.bytesoflife;

import java.io.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Created by Arnoud on 9-10-2015.
 */
public class Configuration
{
    private static Configuration INSTANCE = new Configuration();
    private Properties props;

    public Configuration()
    {
        try
        {
            FileInputStream input = new FileInputStream("configuration.properties");

            props= new Properties();
            props.load(input);
        }
        catch (FileNotFoundException e)
        {

            try {
                InputStream inputStream = Configuration.class.getResourceAsStream("/configuration.properties");
                props= new Properties();
                props.load(inputStream);
            } catch (FileNotFoundException e2) {
                e2.printStackTrace();
            } catch (IOException e2) {
                e2.printStackTrace();
            }

            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void saveAndReload()
    {
        try
        {
            FileOutputStream output = new FileOutputStream("configuration.properties");

            props.store(output, null);

            INSTANCE= null;
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static synchronized Configuration getInstance()
    {
        if( INSTANCE == null )
        {
            INSTANCE= new Configuration();
        }
        return INSTANCE;
    }

    /**             **/

    public String getPortX()
    {
        return props.getProperty("motor.port.x");
    }

    public String getPortY()
    {
        return props.getProperty("motor.port.y");
    }

    public Boolean getMotoroSimulate()
    {
        return Boolean.valueOf(props.getProperty("motor.simulate", "false"));
    }

    public boolean getMotorXinverted()
    {
        return Boolean.valueOf(props.getProperty("motor.invert.x", "false"));
    }

    public boolean getMotorYinverted()
    {
        return Boolean.valueOf(props.getProperty("motor.invert.y", "false"));
    }

    public BigDecimal getMotorMinX()
    {
        return new BigDecimal(props.getProperty("motor.min.x", "0"));
    }

    public BigDecimal getMotorMaxX()
    {
        return new BigDecimal(props.getProperty("motor.max.x", "100"));
    }

    public BigDecimal getMotorMinY()
    {
        return new BigDecimal(props.getProperty("motor.min.y", "0"));
    }

    public BigDecimal getMotorMaxY()
    {
        return new BigDecimal(props.getProperty("motor.max.y", "100"));
    }

    public int getMotorSpeed()
    {
        return Integer.valueOf(props.getProperty("motor.speed"));
    }

    public int getMotorAcceleration()
    {
        return Integer.valueOf(props.getProperty("motor.accelerate"));
    }

    public int getMotorDeceleration()
    {
        return Integer.valueOf(props.getProperty("motor.decelerate"));
    }

    public String getPortXenc()
    {
        return props.getProperty("encoder.port.x");
    }

    public String getPortYenc()
    {
        return props.getProperty("encoder.port.y");
    }

    public boolean getEncXinverted()
    {
        return Boolean.valueOf(props.getProperty("encoder.invert.x", "false"));
    }

    public boolean getEncYinverted()
    {
        return Boolean.valueOf(props.getProperty("encoder.invert.y", "false"));
    }

    public int getEncoderInterval()
    {
        return Integer.valueOf(props.getProperty("encoder.interval", "500"));
    }

    public void setEncoderInterval(int value)
    {
        props.setProperty("encoder.interval", String.valueOf(value));
    }

    public BigDecimal getposToMM()
    {
        return new BigDecimal( props.getProperty("encoder.to.mm", "1.00") );
    }

    public BigDecimal getposToMMx()
    {
        return new BigDecimal( props.getProperty("encoder.to.mm.x", "0.01") );
    }

    public BigDecimal getposToMMy()
    {
        return new BigDecimal( props.getProperty("encoder.to.mm.y", "0.01") );
    }

    public BigDecimal getPickupNextSpacing()
    {
        return new BigDecimal( props.getProperty("component.reel.spacing", "4.00") );
    }

    public File getDefaultFileOpenLocation()
    {
        return new File( props.getProperty("file.open.default", ""));
    }

    public BigDecimal getManualMoveDistance()
    {
        return new BigDecimal( props.getProperty( "manual.move.mm", "0.05" ) );
    }

    public BigDecimal getPlankjeDelta()
    {
        return new BigDecimal( props.getProperty( "plankje.pitch.x.mm", "8.50" ) );
    }

    public boolean getMoveStraightOut()
    {
        return Boolean.valueOf( props.getProperty( "plankje.move.out.enabled", "false" ) );
    }

    public Integer getMoveStraightOutDelay()
    {
        return Integer.valueOf( props.getProperty( "plankje.move.out.delay", "2000" ) );
    }

    public Boolean isWidthIsSprocketToPocket()
    {
        return Boolean.valueOf( props.getProperty( "width.is.sprockettopocket", "true" ) );
    }

    public String getTjardoUrl()
    {
        return props.getProperty( "tjardo.deltaproto.url", "http://www.deltaproto.com/intern/machine.php" );
    }

    public String getTeknicPort() {
        return props.getProperty( "teknic.port", null );
    }

    public BigDecimal plankjeX() {
        return new BigDecimal(props.getProperty( "teknic.plankje.x", "0.00" ));
    }

    public BigDecimal plankjeY() {
        return new BigDecimal(props.getProperty( "teknic.plankje.y", "0.00" ));
    }

    public BigDecimal pixelToCMX() {
        return new BigDecimal(props.getProperty( "pixel.to.cm.x", "167" ));
    }

    public BigDecimal pixelToCMY() {
        return new BigDecimal(props.getProperty( "pixel.to.cm.y", "167" ));
    }

    public String getVacuumPort() {
        return props.getProperty( "vacuum.port", null );
    }

    public Integer getVacuumTop()
    {
        return Integer.valueOf( props.getProperty( "vacuum.top", "1830" ) );
    }

    public Integer getVacuumBottom()
    {
        return Integer.valueOf( props.getProperty( "vacuum.bottom", "2000" ) );
    }

    /** The FTDI relay board's serial port, or {@code auto} (default) to find it by name. */
    public String getVacuumRelayPort() {
        return props.getProperty("vacuum.relay.port", "auto");
    }

    /** Regular expression that recognises the relay board by its serial port name. */
    public String getVacuumRelayMatch() {
        return props.getProperty("vacuum.relay.match", nl.bytesoflife.mohican.VacuumPumpController.DEFAULT_RELAY_MATCH);
    }

    /** Milliseconds between two vacuum readings. */
    public long getVacuumIntervalMs() {
        return Long.parseLong(props.getProperty("vacuum.interval.ms", "2000"));
    }

    /** Readings missed in a row before the pump is switched off. */
    public int getVacuumMissedMax() {
        return Integer.parseInt(props.getProperty("vacuum.missed.max", "2"));
    }

    /**
     * Origins the browser may drive this client from (CORS and the websocket).
     * <p>
     * Patterns, not plain origins: {@code https://*.deltaproto.com} covers the
     * operator page on mohican.deltaproto.com and the blue/green hosts, and
     * {@code localhost:[*]} any dev port. Override with {@code allowed.origins},
     * {@code |}-separated.
     */
    public String[] getAllowedOrigins() {
        return props.getProperty("allowed.origins",
                "http://localhost:[*]|https://localhost:[*]|https://deltaproto.com|https://www.deltaproto.com|https://*.deltaproto.com")
                .split("\\|");
    }

    /**
     * Which motor driver to run: {@code auto} (default), {@code teknic} or
     * {@code serial}.
     * <p>
     * {@code auto} looks for a Teknic ClearPath-SC hub among the serial ports
     * and uses the Teknic driver when one is there, the serial microPlacer
     * driver otherwise. A configured {@code teknic.port} still forces the Teknic
     * driver, so existing hybrid-placer configs keep working unchanged.
     */
    public String getMotorDriver() {
        final String configured = props.getProperty("motor.driver");
        if (configured != null && !configured.trim().isEmpty()) {
            return configured.trim().toLowerCase();
        }
        return getTeknicPort() != null ? "teknic" : "auto";
    }

    /** Regular expression that recognises the Teknic hub by its serial port name. */
    public String getTeknicHubMatch() {
        return props.getProperty("teknic.hub.match", TeknicHubFinder.DEFAULT_MATCH);
    }

    /** How long to wait before looking for the machine again when none was found. */
    public int getMotorRetrySeconds() {
        return Integer.valueOf(props.getProperty("motor.retry.seconds", "5"));
    }

    /** End a running legacy Mohican at startup, so this client can open the machine (Windows). */
    public boolean getLegacyKill() {
        return Boolean.parseBoolean(props.getProperty("legacy.kill", "true"));
    }

    /** Which java command lines count as the legacy Mohican. */
    public String getLegacyKillMatch() {
        return props.getProperty("legacy.kill.match", nl.bytesoflife.mohican.LegacyProcessKiller.DEFAULT_MATCH);
    }

    public boolean getCameraSimulate() {
        return Boolean.valueOf(props.getProperty("camera.simulate", "false"));
    }

    /**
     * Which Canon driver to use: {@code jni} (legacy InspectorCamera.dll),
     * {@code sdk} (canon-sdk-java / JNA, the only one that can serve live view)
     * or {@code simulate}.
     * <p>
     * Defaults to {@code jni} <b>deliberately</b>. The shop-floor machines have no
     * {@code camera.driver} key, so any other default would silently move
     * production capture onto the newer driver the moment this is deployed. Set
     * {@code camera.driver=sdk} explicitly once it has been proven against the
     * camera — or switch at runtime from the mohican window, which does not
     * persist and so cannot surprise the next restart.
     * <p>
     * {@code camera.simulate=true} still wins, so existing configs keep working.
     */
    public String getCameraDriver() {
        if (getCameraSimulate()) {
            return "simulate";
        }
        return props.getProperty("camera.driver", "jni").trim().toLowerCase();
    }
}
