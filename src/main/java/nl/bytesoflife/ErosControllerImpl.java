package nl.bytesoflife;

import jssc.SerialNativeInterface;
import jssc.SerialPortException;
import jssc.SerialPortList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;

public class ErosControllerImpl implements ErosController {
    private EncoderListener encoderListenerX= null;
    private EncoderListener encoderListenerY= null;

    List<MotorController> connectedDevices= new ArrayList<>();

    private SimpleSerialPort portX;
    private SimpleSerialPort portY;

    private MotorController motorX;
    private MotorController motorY;
    private MotorController mainPcb;

    final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public ErosControllerImpl(  )
    {
    }

    public ErosControllerImpl(EncoderListener encoderListenerX, EncoderListener encoderListenerY )
    {
        this.encoderListenerX= encoderListenerX;
        this.encoderListenerY= encoderListenerY;
    }

    public void reInitialize()
    {
        for (MotorController c : connectedDevices) {
            c.terminate();
        }

        List<String> portNames;
        if( SerialNativeInterface.getOsType() != 1 ) { //unix

            Pattern p = Pattern.compile("(tty)\\..*");

            portNames = Arrays.stream(SerialPortList.getPortNames(p)).distinct().collect(Collectors.toList());
        } else {
            portNames = Arrays.stream(SerialPortList.getPortNames()).distinct().collect(Collectors.toList());
        }

        portNames = portNames.stream().filter(s -> !s.toLowerCase().contains("bluetooth")).collect(Collectors.toList());

        ArrayList<Object> ports = new ArrayList<>();
        for (String port : portNames) {

            //if( !port.toLowerCase().contains("21401") ) continue;

            SimpleSerialPort simpleSerialPort = null;
            try {
                simpleSerialPort = new SimpleSerialPort( port, 115200 );
                ports.add( simpleSerialPort );

                MotorController motor = new MotorController(simpleSerialPort, Configuration.getInstance().getMotorXinverted(),
                        Configuration.getInstance().getMotorSpeed(),
                        Configuration.getInstance().getMotorAcceleration(),
                        Configuration.getInstance().getMotorDeceleration(),
                        true);

                connectedDevices.add(motor);

                motor.getAxis(new MotorController.AxisListener() {

                    @Override
                    public void receiveAxis(String axis, MotorController motorController) {
                        if( axis.equalsIgnoreCase("X") ) {
                            motorX = motorController;
                            if (encoderListenerX != null) {
                                motorX.addListener(encoderListenerX);
                            }
                        } else if( axis.equalsIgnoreCase("Y") ) {
                            motorY = motorController;
                            if (encoderListenerY != null) {
                                motorY.addListener(encoderListenerY);
                            }
                        } else if( axis.equalsIgnoreCase("MAIN") ) {
                            mainPcb = motorController;

                            //mainPcb.sendMessage("L038A700");
                            //mainPcb.sendMessage("L138A700");
                            //mainPcb.sendMessage("L238A700");
                            //mainPcb.sendMessage("L338A700");
                            //mainPcb.sendMessage("L438A700");
                        }

                        if( motorX != null && motorY != null && mainPcb != null ) {
                            //disconnectOtherPorts();
                        }
                    }
                });

                motor.start();
            } catch (SerialPortException e) {

            }

        }


        //try {
            portX= null;//new SimpleSerialPort( Configuration.getInstance().getPortX(), 115200 );
            portY= null;//new SimpleSerialPort( Configuration.getInstance().getPortY(), 115200 );
        //} catch (SerialPortException e) {
        //    throw new RuntimeException(e);
        //}




        if( portX != null ) {
            motorX = new MotorController(portX, Configuration.getInstance().getMotorXinverted(),
                    Configuration.getInstance().getMotorSpeed(),
                    Configuration.getInstance().getMotorAcceleration(),
                    Configuration.getInstance().getMotorDeceleration(),
                    true);
        }

        if( portY != null ) {
            motorY = new MotorController(portY, Configuration.getInstance().getMotorYinverted(),
                    Configuration.getInstance().getMotorSpeed(),
                    Configuration.getInstance().getMotorAcceleration(),
                    Configuration.getInstance().getMotorDeceleration(),
                    true);
        }

        if( motorX != null ) {
            if (encoderListenerX != null) {
                motorX.addListener(encoderListenerX);
            }
            //motorX.start();
        }

        if( motorY != null ) {
            if (encoderListenerY != null) {
                motorY.addListener(encoderListenerY);
            }
            //motorY.start();
        }



    }

    private void disconnectOtherPorts() {
        for (MotorController c : connectedDevices) {
            if( c.getAxis() == null || c.getAxis().isEmpty() ) {
                c.terminate();
                //connectedDevices.remove( c );
            }
        }


    }

    public void closePorts()
    {
        if( motorX != null )
            motorX.terminate();
        if( motorY != null )
            motorY.terminate();

        if( portX != null )
            portX.close();
        if( portY != null )
            portY.close();
    }

    public void setZero()
    {
        motorX.setToZero();
        motorY.setToZero();
    }

    @Override
    public void home(Integer current) {
        motorX.home( current );
        motorY.home( current );
    }

    public void setPosition( Integer posX, Integer posY )
    {
        motorX.setToPosition( posX );
        motorY.setToPosition( posY );
    }

    public void goTo( Integer posX, Integer posY )
    {
        motorX.goTo(posX);
        motorY.goTo(posY);
    }

    public void goToPcb(Integer posX, Integer posY) {
        motorX.goTo(posX);
        motorY.goTo(posY);
    }

    public void goTo(final Integer posX, final Integer posY, final Integer delayX, final Integer delayY, boolean release )
    {
        Thread x= new Thread( new Runnable()
        {
            public void run()
            {
                try
                {
                    sleep( delayX );
                    motorX.goTo(posX);
                }
                catch( InterruptedException e )
                {
                    e.printStackTrace();
                }
            }
        } );

        Thread y= new Thread( new Runnable()
        {
            public void run()
            {
                try
                {
                    sleep( delayY );
                    motorY.goTo(posY);
                }
                catch( InterruptedException e )
                {
                    e.printStackTrace();
                }
            }
        } );

        x.start();
        y.start();
    }

    public void disableBrake()
    {
        motorX.disableBrake();
        motorY.disableBrake();
    }

    public void moveY(int i)
    {
        motorY.steps(i);
    }

    public void moveX(int i)
    {
        motorX.steps( i );
    }

    public void setUpdateSpeed(int updateSpeed)
    {
        motorX.updateDelay( updateSpeed );
        motorY.updateDelay( updateSpeed );
    }

    @Override
    public void setAccelerationInPercentage(Integer value) {
        motorX.setAcceleration(value);
        motorY.setAcceleration(value);
    }

    @Override
    public void setSpeedInPercentage(Integer value) {
        motorX.setSpeed(value);
        motorY.setSpeed(value);
    }

    @Override
    public void setCurrent(int current) {
        motorX.setCurrent(current);
        motorY.setCurrent(current);
    }

    public void message(String device, String value) {
        switch (device) {
            case "X":
                motorX.sendMessage(value);
                break;
            case "Y":
                motorY.sendMessage(value);
                break;
            case "MAIN":
                mainPcb.sendMessage(value);
                break;
        }
    }
}
