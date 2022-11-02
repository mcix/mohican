package nl.bytesoflife;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class DeltaProtoDriver implements ErosController, Runnable {

    static {
        System.out.println(System.getProperty("java.library.path"));
        System.out.println("*** load library ***");
        System.loadLibrary("deltateknic");
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

    public DeltaProtoDriver(String port, EncoderListener encoderListenerX, EncoderListener encoderListenerY ) {
        this.port= port;
        this.encoderListenerX= encoderListenerX;
        this.encoderListenerY= encoderListenerY;
    }

    public void reInitialize() {
        int portNum= Integer.valueOf( port );
        System.out.println("TeknicController.reInitialize " + portNum);
        p= openPort( portNum );

        home(p);
        maxAcc= getMaxAcc(p);
        maxVel= getMaxVel(p);
        minAcc= getMinAcc(p);
        minVel= getMinVel(p);

        encoderListenerX.newPos( 0 );
        encoderListenerY.newPos( 0 );

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleWithFixedDelay(this, 0, 50, TimeUnit.MILLISECONDS);
    }

    public void closePorts() {
        closePort( p );
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

    public void setSpeedInPercentage( Integer value ) {
        if( 0 <= value && value <= 100 ) {
            this.speed= value;
        }
        else {
            System.out.println("INVALID SPEED VALUE");
        }
    }

    public void setAccelerationInPercentage( Integer value ) {
        if( 0 <= value && value <= 100 ) {
            this.acceleration= value;
        }
        else {
            System.out.println("INVALID SPEED VALUE");
        }
    }

    @Override
    public void run() {
        Integer x = getPosX();
        Integer y = getPosY();

        encoderListenerX.newPos( x );
        encoderListenerY.newPos( y );
    }
}