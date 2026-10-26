package nl.bytesoflife;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * The motor controller in place while no machine is connected.
 * <p>
 * Until a Teknic hub or the microPlacer's serial motors are found, every motion
 * command lands here and is logged and dropped. The web client keeps its
 * connection and its camera; only the head does not move. This replaces a null
 * {@code erosController}, which made every command fail with a
 * NullPointerException swallowed by the catch-all in Mohican.
 */
public class NoMachineController implements ErosController {

    private static final Logger logger = LoggerFactory.getLogger(NoMachineController.class);

    private final String reason;

    public NoMachineController(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    private void ignored(String command) {
        logger.warn("No machine connected ({}): {} ignored", reason, command);
    }

    @Override
    public void reInitialize() {
    }

    @Override
    public void closePorts() {
    }

    @Override
    public void setZero() {
        ignored("ZERO");
    }

    @Override
    public void home(Integer current, Integer directionX, Integer directionY) {
        ignored("HOME");
    }

    @Override
    public void setPosition(Integer posX, Integer posY) {
        ignored("SET_POSITION");
    }

    @Override
    public void goTo(Integer posX, Integer posY) {
        ignored("GOTO");
    }

    @Override
    public void goToPcb(Integer posX, Integer posY) {
        ignored("GOTO_PCB");
    }

    @Override
    public void disableBrake() {
        ignored("BRAKE");
    }

    @Override
    public void enableBrake() {
        ignored("BRAKE");
    }

    @Override
    public void moveY(int i) {
        ignored("moveY");
    }

    @Override
    public void moveX(int i) {
        ignored("moveX");
    }

    @Override
    public void setUpdateSpeed(int updateSpeed) {
    }

    @Override
    public void setMinMaxPosition(int minX, int maxX, int minY, int maxY) {
    }

    @Override
    public void setAccelerationInPercentage(Integer value) {
    }

    @Override
    public void setSpeedInPercentage(Integer value) {
    }

    @Override
    public void setCurrent(int current) {
    }

    @Override
    public void message(String device, String value) {
    }

    @Override
    public Map<String, String> getVersion() {
        final Map<String, String> version = new HashMap<>();
        version.put("driver", "none");
        return version;
    }
}
