package nl.bytesoflife;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.poi.util.ArrayUtil;

import java.util.ArrayList;

public class SimpleSerialPort2 {
    SerialPort serialPort;

    public SimpleSerialPort2(String port, int baudrate) {
        serialPort= SerialPort.getCommPort(port);
        serialPort.openPort();//Open serial port
        serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);
        //serialPort.purgePort(0);
        //serialPort.setParams(baudrate, 8, 1, 0);//Set params.
    }

    public void close()
    {
        if( serialPort != null )
        {
            serialPort.closePort();//Close serial port
        }
    }

    public static ArrayList<String> getPorts()
    {
        /*String[] portNames = SerialPortList.getPortNames();

        ArrayList<String> ports= new ArrayList<String>();

        for (String portName : portNames)
        {
            ports.add( portName );
        }

        return ports;*/

        return new ArrayList<>();
    }

    public Byte readByte( ) {
        byte[] readBuffer = new byte[1024];
        int x = serialPort.readBytes(readBuffer, 1);
        if( x > 0 ) {
            return readBuffer[0];
        }
        return null;
    }

    public Byte readByteTimeout( ) {
        byte[] readBuffer = new byte[1024];
        int x = serialPort.readBytes(readBuffer, 1);
        if( x > 0 ) {
            return readBuffer[0];
        }
        return null;
    }

    public void writeByte( byte value )
    {
        //serialPort.writeByte(value);
    }

    public void writeBytes( byte[] value )
    {
        serialPort.writeBytes(value, value.length);
    }

    public void writeString(String value)
    {
        byte[] data= value.getBytes();

        serialPort.writeBytes(data, data.length);
    }

    public String readStringTimout() {
        Byte value= null;
        int i=0;
        byte[] data= new byte[200];

        do
        {
            value= readByteTimeout();
            if( value != null ) {
                data[i++] = value;
            }
        }
        while( value != -1 && value != '\n');

        String res= new String( data );

        if( res.contains("\n") )
            return res.substring(0, res.indexOf('\n'));
        else
            return "";
    }

    public String readString() {
        Byte value= null;
        int i=0;
        byte[] data= new byte[200];

        do
        {
            value= readByte();
            if( value != null ) {
                data[i++] = value;
            }
        }
        while( value != -1 && value != '\n');

        String res= new String( data );

        if( res.contains("\n") )
            return res.substring(0, res.indexOf('\n'));
        else
            return "";
    }


    public byte[] readData() {
        byte value= -1;
        int i=0;
        byte[] data= new byte[200];

        do
        {
            value= readByte();
            if( value != -1 )
                data[i++]= value;
        }
        while( /*value != -1 &&*/ value != '\n');

        byte[] res= new byte[i];
        ArrayUtil.arraycopy(data, 0, res, 0, i);

        return res;
    }

    public void flush()
    {
        //serialPort.purgePort()
    }

    public byte[] readData(int i)
    {
        byte[] readBuffer = new byte[1024];
        int x = serialPort.readBytes(readBuffer, 6);
        return readBuffer;
    }
}
