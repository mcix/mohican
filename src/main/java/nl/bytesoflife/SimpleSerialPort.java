package nl.bytesoflife;

import jssc.SerialPort;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPortTimeoutException;
import org.apache.poi.util.ArrayUtil;

import java.util.ArrayList;

public class SimpleSerialPort {
    SerialPort serialPort;

    public SimpleSerialPort(String port, int baudrate) throws SerialPortException {
        serialPort= new SerialPort(port);
        serialPort.openPort();//Open serial port
        serialPort.purgePort(0);
        serialPort.setParams(baudrate, 8, 1, 0);//Set params.
    }

    public void close()
    {
        if( serialPort != null )
        {
            try
            {
                serialPort.closePort();//Close serial port
            } catch (SerialPortException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static ArrayList<String> getPorts()
    {
        String[] portNames = SerialPortList.getPortNames();

        ArrayList<String> ports= new ArrayList<String>();

        for (String portName : portNames)
        {
            ports.add( portName );
        }

        return ports;
    }

    public byte readByte( ) throws SerialPortException, SerialPortTimeoutException {
        byte[] buffer = serialPort.readBytes(1, 1000);
        return buffer[0];
    }

    public void writeByte( byte value ) throws SerialPortException
    {
        serialPort.writeByte(value);
    }

    public void writeBytes( byte[] value ) throws SerialPortException
    {
        serialPort.writeBytes(value);
    }

    public void writeString(String value) throws SerialPortException
    {
        byte[] data= value.getBytes();

        serialPort.writeBytes(data);
    }

    public String readString() throws SerialPortException, SerialPortTimeoutException {
        byte value= -1;
        int i=0;
        byte[] data= new byte[200];

        do
        {
            value= readByte();
            data[i++]= value;
        }
        while( value != -1 && value != '\n');

        String res= new String( data );

        if( res.contains("\n") )
            return res.substring(0, res.indexOf('\n'));
        else
            return "";
    }


    public byte[] readData() throws SerialPortException, SerialPortTimeoutException {
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

    public byte[] readData(int i) throws SerialPortException
    {
        return serialPort.readBytes(6);
    }
}
