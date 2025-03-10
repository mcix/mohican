package nl.bytesoflife;

/**
 * Created by Arnoud on 9-10-2015.
 */
public interface EncoderListener
{
    void newPos( int value );
    void newPos(int value, boolean running);
    void homingFinished();
}
