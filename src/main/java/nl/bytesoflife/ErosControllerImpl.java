package nl.bytesoflife;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static java.lang.Thread.sleep;

public class ErosControllerImpl implements ErosController {
    private EncoderListener encoderListenerX= null;
    private EncoderListener encoderListenerY= null;

    private SimpleSerialPort portX;
    private SimpleSerialPort portY;

    private MotorController motorX;
    private MotorController motorY;

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
        closePorts();

        portX= new SimpleSerialPort( Configuration.getInstance().getPortX(), 115200 );
        portY= new SimpleSerialPort( Configuration.getInstance().getPortY(), 115200 );

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

        if( encoderListenerX != null )
        {
            motorX.addListener( encoderListenerX );
        }

        if( encoderListenerY != null )
        {
            motorY.addListener( encoderListenerY );
        }

        motorX.start();
        motorY.start();
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

    }

    @Override
    public void setSpeedInPercentage(Integer value) {

    }
}
