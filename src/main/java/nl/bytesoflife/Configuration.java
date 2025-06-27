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

    public String[] getAllowedOrigins() {
        return props.getProperty("allowed.origins", "http://localhost:3000|https://deltaproto.com|https://www.deltaproto.com").split("\\|");
    }

    public boolean getCameraSimulate() {
        return Boolean.valueOf(props.getProperty("camera.simulate", "false"));
    }
}
