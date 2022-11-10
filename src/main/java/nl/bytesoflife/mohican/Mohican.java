package nl.bytesoflife.mohican;

import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.Builder;
import lombok.Data;
import nl.bytesoflife.*;
import nl.bytesoflife.mohican.spring.WebSocketConfiguration;
import nl.bytesoflife.mohican.spring.WebSocketEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootApplication
public class Mohican implements ReduxEventListener, WebsocketProviderListener, InitializingBean, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Mohican.class);

    @Value("${info.app.version:unknown}") String version;

    @Autowired
    private SimpMessagingTemplate websocket;

    @Autowired
    private WebSocketEventListener eventListener;

    private MohicanFrame mohicanFrame;

    int posX, posY;

    private String positionX;
    private String positionY;

    private ErosController erosController;

    public Mohican() {
        intiDeltaProtoDriver();
    }

    @Autowired
    public Mohican(ApplicationArguments args) {

        boolean headless = false;

        for (String sourceArg : args.getSourceArgs()) {
            switch (sourceArg) {
                case "-h":
                case "-headless":
                    headless = true;
                    break;
                default:
                    //stub
                    break;
            }
        }

        intiDeltaProtoDriver();

        if( !headless ) {
            try {
                mohicanFrame = new MohicanFrame(this);
            } catch (HeadlessException e) {
                logger.error("Mohican HeadlessException: Run the application with -h or -headless to run without a UI");
                logger.error("Mohican HeadlessException: Start de applicatie met -h of -headless om zonder userinterface te draaien");
                throw e;
            }
        }
    }

    BigDecimal toMMx;
    BigDecimal toMMy;

    private void intiDeltaProtoDriver() {
        //if (Configuration.getInstance().getTeknicPort() != null) {

            DecimalFormat df = new DecimalFormat();
            toMMx = Configuration.getInstance().getposToMMx();
            toMMy = Configuration.getInstance().getposToMMy();

            EncoderListener encoderListenerX = new EncoderListener() {
                public void newPos(int value) {
                    posX = new Integer(value);
                    String val = df.format(toMMx.multiply(new BigDecimal(posX)));
                    positionX = val;

                    if( mohicanFrame != null ) {
                        mohicanFrame.setPostionLabelX( val );
                    }

                    /*if( postionLabelX != null ) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                String val = df.format(toMMx.multiply(new BigDecimal(posX)));

                                postionLabelX.setText(val);
                                postionLabelX.paintImmediately(postionLabelX.getVisibleRect());
                                positionX = val;
                            }
                        });
                    }*/
                }
            };

            EncoderListener encoderListenerY = new EncoderListener() {
                public void newPos(final int value) {
                    posY = new Integer(value);
                    String val = df.format(toMMy.multiply(new BigDecimal(posY)));
                    positionY = val;

                    if( mohicanFrame != null ) {
                        mohicanFrame.setPostionLabelY( val );
                    }

                    /*if( postionLabelY != null ) {
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                String val = df.format(toMMy.multiply(new BigDecimal(posY)));

                                postionLabelY.setText(val);
                                postionLabelY.paintImmediately(postionLabelY.getVisibleRect());
                            }
                        });
                    }*/
                }
            };
        //}

        if (Configuration.getInstance().getTeknicPort() != null) {


            toMMx = Configuration.getInstance().getposToMMx();
            toMMy = Configuration.getInstance().getposToMMy();

            erosController = new DeltaProtoDriver(Configuration.getInstance().getTeknicPort(), encoderListenerX, encoderListenerY);

            erosController.reInitialize();
        } else {//if( Configuration.getInstance().getPortX() != null ) {

            erosController = new ErosControllerImpl(encoderListenerX, encoderListenerY);
            erosController.reInitialize();

        }

    }



    public static void main(String[] args) {

        boolean headless = false;

        for (String arg : args) {
            switch (arg) {
                case "-h":
                case "-headless":
                    headless = true;
                    break;
                default:
                    //stub
                    break;
            }
        }

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Mohican.class)
                .headless(headless).addCommandLineProperties(true).run(args);
    }

    private BigDecimal getNumber(Object value) {
        if( value instanceof String ) {

            return BigDecimal.valueOf(Double.parseDouble((String) value));

        } else if( value instanceof Integer ) {

            return BigDecimal.valueOf(Double.parseDouble(String.valueOf((Integer) value)));

        } else {
            return BigDecimal.valueOf((Double) value);
        }
    }

    private Position parsePosition(Object object) {
        LinkedHashMap<String, Object> val = (LinkedHashMap<String, Object>) object;

        return Position.builder().x(getNumber(val.get("x"))).y(getNumber(val.get("y"))).build();
    }

    private Integer parseIntValue(Object object) {
        LinkedHashMap<String, Integer> val = (LinkedHashMap<String, Integer>) object;
        return val.get("value");
    }

    @Override
    public void onMessage(ReduxAction action) {
        logger.info("onMessage " + action.getType());

        try {

            switch (action.getType()) {
                case "POSITION_TIMEOUT": {
                    Integer delayMs = (Integer) action.getValue();
                    setPositionTimeOut(delayMs);
                    break;
                }
                case "SET_POSITION": {
                    Position pos = parsePosition(action.getValue());
                    IntPosition intPosition = pos.getInt();
                    erosController.setPosition(intPosition.getX(), intPosition.getY());
                    break;
                }
                case "GOTO_PCB": {
                    Position pos = parsePosition(action.getValue());
                    IntPosition intPosition = pos.getInt();
                    erosController.goToPcb(intPosition.getX(), intPosition.getY());
                    break;
                }
                case "GOTO": {
                    Position pos = parsePosition(action.getValue());
                    IntPosition intPosition = pos.getInt();
                    erosController.goTo(intPosition.getX(), intPosition.getY());
                    //erosController.disableBrake();
                    break;
                }
                case "SET_PCB_POSITION": {
                    sendPosition("MOHICAN_PCB_POSITION");
                    break;
                }
                case "SET_PLANKJE_POSITION": {
                    sendPosition("MOHICAN_PLANKJE_POSITION");
                    break;
                }
                case "SET_ACCELERATION": {
                    Integer value = parseIntValue(action.getValue());
                    erosController.setAccelerationInPercentage(value);
                    break;
                }
                case "SET_SPEED": {
                    Integer value = parseIntValue(action.getValue());
                    erosController.setSpeedInPercentage(value);
                    break;
                }
                case "SET_CURRENT": {
                    Integer value = parseIntValue(action.getValue());
                    erosController.setCurrent(value);
                    break;
                }
                case "ZERO": {
                    erosController.setZero();
                    break;
                }
                case "HOME": {
                    if (action.getValue() != null) {
                        Integer value = (Integer) action.getValue();
                        erosController.home(value);
                    } else {
                        erosController.home(null);
                    }
                    break;
                }
                case "GET_VERSION": {
                    String javaVersion = System.getProperty("java.version");
                    sendMessage("MOHICAN_CLIENT_VERSION", VersionMessage.builder().javaVersion(javaVersion).version(version).build());
                }

            }

        }
        catch (Exception e) {
            logger.warn("Failed to parse message " + action.getType() + " " + action.getValue());
        }
    }

    @Data
    @Builder
    public static class VersionMessage {
        String version;
        String javaVersion;
    }

    @Override
    public void onConnect() {
        logger.info("onConnect");
        //setTitle("Mohican [CONNECTED]");

        //setImage(true);

        //messageButton.setEnabled( true );
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");
        //setTitle("Mohican [DISCONNECTED]");

        //setImage(false);

        if( executor != null ) {
            executor.shutdown();
        }
        //messageButton.setEnabled( false );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventListener.addReduxEventListener( this );
        eventListener.addWebsocketProviderListener( this );
    }

    void reInitialize() {
        erosController.reInitialize();
    }

    ScheduledExecutorService executor = null;

    private void setPositionTimeOut(Integer ms) {

        logger.info("setPositionTimeOut " + ms);

        if( executor != null ) {
            executor.shutdown();
        }

        if( ms != null && ms != 0 ) {
            executor = Executors.newScheduledThreadPool(1);

            executor.scheduleWithFixedDelay(this, 0, ms, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public Position getPosition() {
        BigDecimal x = BigDecimal.valueOf(0);
        BigDecimal y = BigDecimal.valueOf(0);

        if( erosController != null ) {
            if( positionX != null && positionY != null ) {
                String xi = positionX.replace(",", ".");
                String yi = positionY.replace(",", ".");

                x = new BigDecimal(xi);
                y = new BigDecimal(yi);
            }
        } else if( mohicanFrame != null ) {
            x =  mohicanFrame.getPostionLabelX();
            y =  mohicanFrame.getPostionLabelY();
        }
        return Position.builder().x(x).y(y).build();
    }

    @Data
    @Builder
    public static class Position {
        BigDecimal x;
        BigDecimal y;

        IntPosition getInt() {
            BigDecimal toMMx = Configuration.getInstance().getposToMMx();
            BigDecimal toMMy = Configuration.getInstance().getposToMMy();

            BigDecimal x = getX().divide(toMMx, BigDecimal.ROUND_HALF_UP);
            BigDecimal y = getY().divide(toMMy, BigDecimal.ROUND_HALF_UP);
            int xi = x.intValue();
            int yi = y.intValue();

            return IntPosition.builder().x(xi).y(yi).build();
        }
    }

    @Data
    @Builder
    static class IntPosition {
        int x;
        int y;
    }

    void sendPosition() {
        sendPosition("MOHICAN_POSITION");
    }

    void sendPosition(String type) {

        Position position = getPosition();

        ReduxAction reduxAction= new ReduxAction();
        reduxAction.setType(type);
        reduxAction.setValue( position );

        if( websocket != null ) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    void sendMessage(String type, Object value) {

        ReduxAction reduxAction= new ReduxAction();
        reduxAction.setType(type);
        reduxAction.setValue( value );

        if( websocket != null ) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    @Override
    public void run() {
        sendPosition();
    }
}