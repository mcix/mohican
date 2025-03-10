package nl.bytesoflife;

import org.apache.commons.collections4.KeyValue;

import java.util.Map;

/**
 * Created by avanderheijde on 03/03/2017.
 */
public interface ErosController {

    void reInitialize();
    void closePorts();
    void setZero();
    void home( Integer current, Integer directionX, Integer directionY);

    void setPosition(Integer posX, Integer posY );
    void goTo( Integer posX, Integer posY );
    void goToPcb( Integer posX, Integer posY );
    //void goToPcb( Integer posX, Integer posY, boolean release );
    //void goTo( final Integer posX, final Integer posY, final Integer delayX, final Integer delayY, boolean release );
    void disableBrake();
    void enableBrake();
    void moveY(int i);
    void moveX(int i);
    void setUpdateSpeed(int updateSpeed);
    void setMinMaxPosition(int minX, int maxX, int minY, int maxY);

    //Used for Teknic Motor controller
    void setAccelerationInPercentage(Integer value);
    void setSpeedInPercentage(Integer value);
    void setCurrent(int current);

    void message(String device, String value);

    Map<String, String> getVersion();
}
