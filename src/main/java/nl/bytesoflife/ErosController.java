package nl.bytesoflife;

/**
 * Created by avanderheijde on 03/03/2017.
 */
public interface ErosController {

    void reInitialize();
    void closePorts();
    void setZero();
    void setPosition( Integer posX, Integer posY );
    void goTo( Integer posX, Integer posY );
    void goToPcb( Integer posX, Integer posY );
    //void goToPcb( Integer posX, Integer posY, boolean release );
    //void goTo( final Integer posX, final Integer posY, final Integer delayX, final Integer delayY, boolean release );
    void disableBrake();
    void moveY(int i);
    void moveX(int i);
    void setUpdateSpeed(int updateSpeed);
}
