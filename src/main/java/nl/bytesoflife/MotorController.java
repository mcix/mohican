package nl.bytesoflife;

import jssc.SerialPortException;
import jssc.SerialPortTimeoutException;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class MotorController extends Thread
{
    private SimpleSerialPort port= null;
    private boolean inverted;
    private boolean validate;

    private int defaultSpeed= 500;
    private int defaultAcceleration= 500;
    private int defaultDeceleration= 500;

    private boolean running= true;

    private String axis = "";

    ArrayList<EncoderListener> listeners= new ArrayList<EncoderListener>();
    ArrayList<AxisListener> axisListeners= new ArrayList<AxisListener>();

    private LinkedBlockingQueue<String> messages= new LinkedBlockingQueue<String>();

    public MotorController( SimpleSerialPort port, Boolean inverted, int defaultSpeed, int defaultAcceleration, int defaultDeceleration )
    {
        this.port= port;
        this.inverted= inverted;
        this.defaultSpeed= defaultSpeed;
        this.defaultAcceleration= defaultAcceleration;
        this.defaultDeceleration= defaultDeceleration;
    }

    public MotorController( SimpleSerialPort port, Boolean inverted, int defaultSpeed, int defaultAcceleration, int defaultDeceleration, Boolean validateSteps )
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

        port.flush();

        //messages.add("z");

        while(running)
        {

            try {
                String value = port.readString();

                //System.out.println(value);

                if (isNumeric(value)) {
                    try {
                        Integer pos = Integer.parseInt(value.replace("\r", ""));

                        fireNewPosition(pos);

                    } catch (Exception e) {
                    }
                } else {
                    System.out.println(value);
                    if (value.startsWith("AXIS=")) {
                        axis = value.replace("AXIS=", "").replace("\r", "").replace("\n", "");
                        for (AxisListener axisListener : axisListeners) {
                            axisListener.receiveAxis(axis, this);
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

                if (messages.peek() != null) {
                    try {
                        String mes = messages.take();

                        port.writeString(mes + "\r\n");

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            } catch (SerialPortTimeoutException e ) {

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        if( port != null && port.serialPort != null ) {
            try {
                port.serialPort.closePort();
            } catch (SerialPortException e) {
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

        if( pos > 0 && pos <= 6000 ) {

            //String value = "moveto " + pos + " " + defaultAcceleration + " " + defaultDeceleration + " " + defaultSpeed + "\n";
            String value = "m " + pos+ "\n";
            messages.add(value);
        }
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


    public void disableBrake()
    {
        String value = "f1" + "\n";

        messages.add(value);
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

    public String getAxis() {
        return axis;
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

    public static interface AxisListener {
        void receiveAxis(String axis, MotorController motorController);
    }


}
