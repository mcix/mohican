package nl.bytesoflife.mohican;

import lombok.Builder;
import lombok.Data;
import nl.bytesoflife.*;
import nl.bytesoflife.Inspector.CanonDriver;
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
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.List;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Component
@SpringBootApplication
public class Mohican implements ReduxEventListener, WebsocketProviderListener, InitializingBean, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Mohican.class);

    @Value("${info.app.version:unknown}")
    String version;
    int posX, posY;
    BigDecimal toMMx;
    BigDecimal toMMy;
    ScheduledExecutorService executor = null;
    @Autowired
    private SimpMessagingTemplate websocket;
    @Autowired
    private WebSocketEventListener eventListener;
    private MohicanFrame mohicanFrame;
    private String positionX;
    private String positionY;
    private ErosController erosController;
    private CanonDriver canonDriver;
    private boolean sessionOpen = false;
    private int canonError;

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

        if (!headless) {
            try {
                mohicanFrame = new MohicanFrame(this);
            } catch (HeadlessException e) {
                logger.error("Mohican HeadlessException: Run the application with -h or -headless to run without a UI");
                logger.error("Mohican HeadlessException: Start de applicatie met -h of -headless om zonder userinterface te draaien");
                throw e;
            }
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

    public void canonSendAll() {
        sendMessage("CANON_APERTURE_OPTIONS", canonDriver.getListOfApertureOptions());
        sendMessage("CANON_ISO_OPTIONS", canonDriver.getListOfIsoOptions());
        sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriver.getShutterSpeedOptions());
        sendMessage("CANON_GET_IMAGE_INFO", canonDriver.getAllImageInfo());
    }

    private void intiDeltaProtoDriver() {
        //if (Configuration.getInstance().getTeknicPort() != null) {

        DecimalFormatSymbols otherSymbols = new DecimalFormatSymbols();
        otherSymbols.setDecimalSeparator('.');
        otherSymbols.setGroupingSeparator(',');

        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(otherSymbols);
        df.setGroupingUsed(false);
        toMMx = Configuration.getInstance().getposToMMx();
        toMMy = Configuration.getInstance().getposToMMy();

        EncoderListener encoderListenerX = new EncoderListener() {
            public void newPos(int value) {
                posX = (value);
                String val = df.format(toMMx.multiply(new BigDecimal(posX)));
                positionX = val;

                //System.out.println(posX);

                if (mohicanFrame != null) {
                    mohicanFrame.setPostionLabelX(val);
                }

                if (positionX != null && positionY != null) {
                    sendMessage("MOHICAN_POSITION", Position.builder().x(new BigDecimal(positionX)).y(new BigDecimal(positionY)).build());
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
                posY = (value);
                String val = df.format(toMMy.multiply(new BigDecimal(posY)));
                positionY = val;

                //System.out.println(posY);

                if (mohicanFrame != null) {
                    mohicanFrame.setPostionLabelY(val);
                }

                //sendMessage("MOHICAN_POSITION", Position.builder().x(new BigDecimal(positionX)).y(new BigDecimal(positionY)).build());

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

        canonDriver = new CanonDriver();
        canonError = canonDriver.init();
        if (canonError != 0) {
            logger.error("CanonDriver init error: " + canonError);
        } else {
            logger.info("CanonDriver init success");
        }

        if (Configuration.getInstance().getTeknicPort() != null) {


            toMMx = Configuration.getInstance().getposToMMx();
            toMMy = Configuration.getInstance().getposToMMy();

            erosController = new DeltaProtoDriver(Configuration.getInstance().getTeknicPort(), encoderListenerX, encoderListenerY);

            erosController.reInitialize();
        } else if (canonDriver.findCamera()) {

//            canonDriver.findCamera();
//            long camera = canonDriver.initCamera();
//            canonDriver.openSession();

            logger.info("Mode: Inspector");

            erosController = new ErosControllerImpl(encoderListenerX, encoderListenerY);
            erosController.reInitialize();
        } else {//if( Configuration.getInstance().getPortX() != null ) {

            erosController = new ErosControllerImpl(encoderListenerX, encoderListenerY);
            erosController.reInitialize();

        }

    }

    private BigDecimal getNumber(Object value) {
        if (value instanceof String) {

            return BigDecimal.valueOf(Double.parseDouble((String) value));

        } else if (value instanceof Integer) {

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
                    Integer value = Integer.valueOf((String) action.getValue());
                    erosController.setAccelerationInPercentage(value);
                    break;
                }
                case "SET_SPEED": {
                    Integer value = Integer.valueOf((String) action.getValue());
                    erosController.setSpeedInPercentage(value);
                    break;
                }
                case "SET_CURRENT": {
                    Integer value = Integer.valueOf((String) action.getValue());
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
                case "MESSAGE": {
                    if (action.getValue() != null) {
                        LinkedHashMap deviceMessage = (LinkedHashMap) action.getValue();
                        erosController.message((String) deviceMessage.get("devicwe"), (String) deviceMessage.get("value"));
                    }
                }
                case "GET_VERSION": {
                    String javaVersion = System.getProperty("java.version");
                    sendMessage("MOHICAN_CLIENT_VERSION", VersionMessage.builder().javaVersion(javaVersion).version(version).versions(erosController.getVersion()).build());
                }

                case "CANON_REINITIALIZE": {
                    canonDriver.reInitialize();
                    break;
                }

                case "CANON_FORMAT": {
                    logger.error(String.valueOf(canonDriver.formatAll()));
                    break;
                }

                case "CANON_GET_ALL_SETTINGS": {
                    sendMessage("CANON_APERTURE_OPTIONS", canonDriver.getListOfApertureOptions());
                    sendMessage("CANON_ISO_OPTIONS", canonDriver.getListOfIsoOptions());
                    sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriver.getShutterSpeedOptions());
                    sendMessage("CANON_QUALITY_ALL", canonDriver.getImageQualityOptions());
                    canonDriver.closeSession();
                    canonDriver.openSession();
                    sendMessage("CANON_GET_IMAGE_INFO", canonDriver.getAllImageInfo());
                }

                case "INIT_CAMERA": {
                    canonDriver.initCamera();
                    break;
                }

                case "CANON_OPEN_SESSION": {
                    logger.warn(String.valueOf(canonDriver.openSession()));
                    break;
                }
                case "CANON_CLOSE_SESSION": {
                    canonDriver.closeSession();
                    break;
                }
                case "CANON_TAKE_PICTURE": {
                    logger.warn(String.valueOf(canonDriver.takePhoto()));
//                    if (err == 0) {
//                        sendMessage("CANON_PHOTO_STATUS", "ok");
//                    } else {
//                        sendMessage("CANON_PHOTO_STATUS", "err: " + err);
//                    }
                    break;
                }

                case "CANON_GET_CURRENT_QUALITY": {
                    sendMessage("CANON_CURRENT_QUALITY", canonDriver.getCurrentImageQuality());
                    break;
                }

                case "CANON_SET_QUALITY": {
                    canonDriver.setImageQuality((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_QUALITY": {
                    sendMessage("CANON_ALL_QUALITY", canonDriver.getImageQualityOptions());
                    break;
                }

                case "CANON_GET_CURRENT_APERTURE": {
                    sendMessage("CANON_APERTURE_CURRENT", canonDriver.getApertureSetting());
                    break;
                }

                case "CANON_SET_APERTURE": {
                    int err = canonDriver.setAperture((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_APERTURE": {
                    sendMessage("CANON_APERTURE_OPTIONS", canonDriver.getListOfApertureOptions());
                    break;
                }

                // iso settings
                case "CANON_GET_CURRENT_ISO": {
                    sendMessage("CANON_ISO_CURRENT", canonDriver.getIsoSetting());
                    break;
                }
                case "CANON_SET_ISO": {
                    int err = canonDriver.setIso((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_ISO": {
                    sendMessage("CANON_ISO_OPTIONS", canonDriver.getListOfIsoOptions());
                    break;
                }

                // shutterspeed settings
                case "CANON_GET_CURRENT_SHUTTERSPEED": {
                    sendMessage("CANON_SHUTTERSPEED_CURRENT", canonDriver.getCurrentShutterSpeed());
                    break;
                }
                case "CANON_GET_ALL_SHUTTERSPEED": {
                    sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriver.getShutterSpeedOptions());
                    break;
                }
                case "CANON_SET_SHUTTERSPEED": {
                    canonDriver.setShutterSpeed((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_IMAGE_INFO": {
                    canonDriver.closeSession();
                    canonDriver.openSession();
                    sendMessage("CANON_GET_IMAGE_INFO", canonDriver.getAllImageInfo());
                    break;
                }

                case "CANON_GET_IMAGE": {
                    sendMessage("CANON_GET_IMAGE", Base64.getEncoder().encodeToString(canonDriver.getImage((String) action.getValue())));
                    break;
                }

            }

        } catch (Exception e) {
            logger.warn("Failed to parse message " + action.getType() + " " + action.getValue());
        }
    }

    @Override
    public void onConnect() {
        logger.info("onConnect");
        if (canonDriver.findCamera()) {
            canonDriver.reInitialize();
            sendMessage("CANON_APERTURE_OPTIONS", canonDriver.getListOfApertureOptions());
            sendMessage("CANON_ISO_OPTIONS", canonDriver.getListOfIsoOptions());
            sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriver.getShutterSpeedOptions());
            canonDriver.closeSession();
            canonDriver.openSession();
            sendMessage("CANON_GET_IMAGE_INFO", canonDriver.getAllImageInfo());
        }
        //setTitle("Mohican [CONNECTED]");

        //setImage(true);

        //messageButton.setEnabled( true );
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");
        //setTitle("Mohican [DISCONNECTED]");

        //setImage(false);

        if (executor != null) {
            executor.shutdown();
        }
        //messageButton.setEnabled( false );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventListener.addReduxEventListener(this);
        eventListener.addWebsocketProviderListener(this);
    }

    void reInitialize() {
        if (canonDriver.findCamera()) {
            canonDriver.reInitialize();
        }
        erosController.reInitialize();
    }

    private void setPositionTimeOut(Integer ms) {

        logger.info("setPositionTimeOut " + ms);

        if (executor != null) {
            executor.shutdown();
        }

        if (ms != null && ms != 0) {
            executor = Executors.newScheduledThreadPool(1);

            executor.scheduleWithFixedDelay(this, 0, ms, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public Position getPosition() {
        BigDecimal x = BigDecimal.valueOf(0);
        BigDecimal y = BigDecimal.valueOf(0);

        if (erosController != null) {
            if (positionX != null && positionY != null) {
                String xi = positionX.replace(",", ".");
                String yi = positionY.replace(",", ".");

                x = new BigDecimal(xi);
                y = new BigDecimal(yi);
            }
        } else if (mohicanFrame != null) {
            x = mohicanFrame.getPostionLabelX();
            y = mohicanFrame.getPostionLabelY();
        }
        return Position.builder().x(x).y(y).build();
    }

    void sendPosition() {
        //sendPosition("MOHICAN_POSITION");
    }

    void sendPosition(String type) {

        Position position = getPosition();

        ReduxAction reduxAction = new ReduxAction();
        reduxAction.setType(type);
        reduxAction.setValue(position);

        if (websocket != null) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    void sendMessage(String type, Object value) {

        ReduxAction reduxAction = new ReduxAction();
        reduxAction.setType(type);
        reduxAction.setValue(value);

        if (websocket != null) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    @Override
    public void run() {
        sendPosition();
    }

    @Data
    @Builder
    public static class VersionMessage {
        String version;
        String javaVersion;

        Map<String, String> versions = new HashMap<>();
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
}