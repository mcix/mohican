package nl.bytesoflife;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;
import lombok.Getter;
import nl.bytesoflife.mohican.Mohican;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import static java.lang.Math.PI;

public class MotorController extends Thread
{
    private static final Logger logger = LoggerFactory.getLogger(MotorController.class);

    private SimpleSerialPort2 port= null;
    private boolean inverted;
    private boolean validate;

    private int defaultSpeed= 500;
    private int defaultAcceleration= 500;
    private int defaultDeceleration= 500;

    private boolean running= true;

    @Getter
    private String axis = "";
    private String version = "?";
    private int tryForAxisInformation = 0;

    ArrayList<EncoderListener> listeners= new ArrayList<EncoderListener>();
    ArrayList<AxisListener> axisListeners= new ArrayList<AxisListener>();

    private LinkedBlockingQueue<String> messages= new LinkedBlockingQueue<String>();

    public MotorController( SimpleSerialPort2 port, Boolean inverted, int defaultSpeed, int defaultAcceleration, int defaultDeceleration )
    {
        this.port= port;
        this.inverted= inverted;
        this.defaultSpeed= defaultSpeed;
        this.defaultAcceleration= defaultAcceleration;
        this.defaultDeceleration= defaultDeceleration;
    }

    public MotorController( SimpleSerialPort2 port, Boolean inverted, int defaultSpeed, int defaultAcceleration, int defaultDeceleration, Boolean validateSteps )
    {
        this.port= port;
        this.inverted= inverted;
        this.defaultSpeed= defaultSpeed;
        this.defaultAcceleration= defaultAcceleration;
        this.defaultDeceleration= defaultDeceleration;
        this.validate= validateSteps;
    }

    @Override
    public void run()
    {
        boolean hasMessage;
        port.flush();

        //messages.add("z");

        while(running)
        {

            try {
                if (messages.peek() != null) {
                    try {
                        String mes = messages.take();

                        port.writeString(mes + "\n");

                    } catch (InterruptedException e) {
                        logger.error(e.getMessage());
                    }
                }

                hasMessage = true;
                String value;

                if( axis.equals("") || axis.equals("MAIN") ) {
                    value = port.readStringTimout();
                } else {
                    value = port.readString();
                }

                if( value != null && !value.isEmpty() ) {
                    try {

                        String cleanedValue = value.replace("\r", "").replace("\n", "");
                        if (cleanedValue.contains("run:")) {
                             int runIndex = cleanedValue.indexOf(" run: ");

                            String pos = cleanedValue.substring(0, runIndex).trim();
                            String runValueStr = cleanedValue.substring(runIndex + 5).trim(); // "+5" to skip past "run: "
                            boolean runValue = runValueStr.equals("1");
                            int intPos = Integer.parseInt(pos);

//                            System.out.println(intPos);
//                            System.out.println(pos);
//                            System.out.println(runValue);

                            fireNewPosition(intPos, runValue);
                            hasMessage = false;

                            // Perform actions based on the value of run
                            if (runValue == false) {
//                                System.out.println("Run is 0. Performing action for run = 0.");
                            } else if (runValue == true) {
//                                System.out.println("Run is 1. Performing action for run = 1.");
                            } else {
                                System.out.println("Unexpected run value: " + runValue);
                            }

                        } else {


                            int pos = Integer.parseInt(cleanedValue);

                            fireNewPosition(pos);

                            hasMessage = false;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println("Not a number: " + value);
                    } catch (Exception e) {
                        System.out.println("Error processing value: " + value);
                    }
                }

                if( hasMessage ) {
                    if (value == null || value.isEmpty()) {
                        sleep(100);
                    } else if( value.startsWith("invalid command") ) {
                        //this is the main print

                        for (AxisListener axisListener : axisListeners) {
                            axis = "MAIN";
                            axisListener.receiveAxis("MAIN", this);
                        }

                    } else {
                        if (value.startsWith("AXIS=")) {
                            axis = value.replace("AXIS=", "").replace("\r", "").replace("\n", "");
                            for (AxisListener axisListener : axisListeners) {
                                axisListener.receiveAxis(axis, this);
                            }

                        } else if( value.startsWith("VERSION=") ) {
                            version = value.replace("VERSION=", "").replace("\r", "").replace("\n", "");
                        }
                    }
                }

                //port.writeString("P\n");
                /*byte[] res = port.readData(6);

                if( res.length != 6 )
                {
                    System.out.println("strange length! " + res );
                }

                else if (res != null && res.length > 0)
                {
                    byte[] data= new byte[ res.length - 2 ];
                    int crc= res[ res.length - 2 ];
                    ArrayUtil.arraycopy( res, 0, data, 0, res.length - 2);

                    byte calcCrc= (byte) CRC8.calculateCRC8( data );

                    if( crc == calcCrc )
                    {

                        ByteArrayInputStream bain = new ByteArrayInputStream(res);
                        DataInputStream in = new DataInputStream(bain);

                        int pos = in.readInt();

                        fireNewPosition(pos);
                    }
                }*/

            } catch (Exception e ) {

                if( axis.equals("") ) {
                    tryForAxisInformation++;
                    if (tryForAxisInformation > 20) {
                        running = false;
                        break;
                    }
                }

            }
        }

        if( port != null && port.serialPort != null ) {
            try {
                port.serialPort.closePort();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        //super.run();
    }

    private void fireNewPosition(int value)
    {
        //System.out.println("MotorController.fireNewPosition " + value);

        for (EncoderListener listener : listeners)
        {
            listener.newPos( inverted ? -value: value );
        }
    }

    private void fireNewPosition(int value, boolean running)
    {
        //System.out.println("MotorController.fireNewPosition " + value);

        for (EncoderListener listener : listeners)
        {
            listener.newPos( inverted ? -value: value, running );
        }
    }

    private static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public void steps(int steps)
    {
        try
        {
            move( steps, defaultAcceleration, defaultDeceleration, defaultSpeed);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void move( int steps, int acc, int dec, int speed ) throws IOException
    {
        steps= inverted ? -steps : steps;
        //String value = "move " + steps + " " + acc + " " + dec + " " + speed + "\n";
        String value = "m " + steps + "\n";

        messages.add( value );
    }

    public void goTo(Integer pos)
    {
        pos= inverted ? -pos : pos;
        String value = "m " + pos+ "\n";
        messages.add(value);
    }

    private boolean validate( String value )
    {
        int n = JOptionPane.showConfirmDialog(
                null,
                value,
                "Step validation",
                JOptionPane.YES_NO_OPTION);

        if( n == JOptionPane.YES_OPTION )
        {
            return true;
        }
        else
        {
            return false;
        }
    }

    public void addListener( EncoderListener listener )
    {
        listeners.add(listener);
    }

    public void removeListener( EncoderListener listener )
    {
        listeners.remove(listener);
    }

    public void setToZero()
    {
        String value = "z" + "\n";

        messages.add(value);
    }


    public void enableBrake()
    {
        messages.add("b1\n");
    }
    public void disableBrake()
    {
        messages.add("b0\n");
    }
    public void sendMessage(String value)
    {
        messages.add( value );
    }

    public void terminate()
    {
        running= false;
    }

    public void updateDelay(int delay)
    {
        String value = "p " + delay + "\n";

        messages.add(value);
    }

    public void setToPosition(Integer steps)
    {
        steps= inverted ? -steps : steps;
        String value = "sp " + steps + "\n";

        messages.add(value);
    }

    public void getAxis( AxisListener listener )
    {
        messages.add("a");

        //listener.receiveAxis("X", this);

        axisListeners.add(listener);
    }

    public void home( Integer current ) {
        if( current != null && current != 0 ) {
            messages.add("q " + current);
        } else {
            messages.add("q");
        }
    }

    public void setCurrent(int current) {
        messages.add("sc"+current);
    }

    public void setSpeed(int speed) {
        messages.add("ss"+speed);
    }

    public void setAcceleration(int acceleration) {
        messages.add("sa"+acceleration);
    }

    public String getVersion() {
        return version;
    }

    public static interface AxisListener {
        void receiveAxis(String axis, MotorController motorController);
    }


}