package nl.bytesoflife;

import jssc.SerialPortException;
import org.apache.poi.util.ArrayUtil;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
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

    ArrayList<EncoderListener> listeners= new ArrayList<EncoderListener>();

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

            try
            {
                String value = port.readString();

                System.out.println(value);

                try {
                    Integer pos = Integer.parseInt(value.replace("\r", ""));

                    fireNewPosition(pos);

                } catch (Exception e) {}

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

                if (messages.peek() != null)
                {
                    try
                    {
                        String mes = messages.take();

                        port.writeString(mes);

                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }

            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        //super.run();
    }

    private void fireNewPosition(int value)
    {
        System.out.println("MotorController.fireNewPosition " + value);

        for (EncoderListener listener : listeners)
        {
            listener.newPos( inverted ? -value: value );
        }
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

        if( pos > 0 && pos <= 5500 ) {

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

}
