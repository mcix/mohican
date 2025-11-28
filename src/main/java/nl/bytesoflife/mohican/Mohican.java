package nl.bytesoflife.mohican;

import lombok.Builder;
import lombok.Data;
import nl.bytesoflife.*;
import nl.bytesoflife.inspector.CanonDriverListener;
import nl.bytesoflife.inspector.CanonDriverWrapperInterface;
import nl.bytesoflife.inspector.CanonDriver;
import nl.bytesoflife.inspector.CanonDriverConfiguration;
import nl.bytesoflife.mohican.spring.SslCertificateInitializer;
import nl.bytesoflife.mohican.spring.WebSocketConfiguration;
import nl.bytesoflife.mohican.spring.WebEventListener;
import nl.bytesoflife.mohican.spring.WebSocketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;

import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


@Component
@SpringBootApplication
public class Mohican implements ReduxEventListener, WebsocketProviderListener, InitializingBean, Runnable, CanonDriverListener {

    private static final Logger logger = LoggerFactory.getLogger(Mohican.class);
    public Boolean runningX;
    public Boolean runningY;
    @Value("${info.app.version:unknown}")
    String version;
    int posX, posY;
    BigDecimal toMMx;
    BigDecimal toMMy;
    ScheduledExecutorService executor = null;
    private MohicanFrame mohicanFrame;
    private String positionX;
    private String positionY;
    private ErosController erosController;
    private boolean sessionOpen = false;
    private int canonError;
    

    @Autowired
    private CanonDriverWrapperInterface canonDriverWrapper;

    //private CanonDriver canonDriver;

    @Autowired
    private WebEventListener eventListener;

    @Autowired
    private WebSocketController webSocketController;

    @Autowired
    private SimpMessagingTemplate websocket;

    public Mohican() throws ExecutionException, InterruptedException {

    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventListener.addReduxEventListener(this);
        eventListener.addWebsocketProviderListener(this);

        webSocketController.addReduxEventListener(this);

        canonDriverWrapper.addListener(this);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void runAfterStartup() {


        try {
            intiDeltaProtoDriver();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Autowired
    public Mohican(ApplicationArguments args) throws ExecutionException, InterruptedException {

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

        //intiDeltaProtoDriver();

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
                .headless(headless)
                .addCommandLineProperties(true)
                .initializers(new SslCertificateInitializer())
                .sources(Mohican.class, CanonDriverConfiguration.class)
                .run(args);
    }

    public void canonSendAll() throws ExecutionException, InterruptedException {
        sendMessage("CANON_APERTURE_OPTIONS", canonDriverWrapper.getListOfApertureOptions());
        sendMessage("CANON_ISO_OPTIONS", canonDriverWrapper.getListOfIsoOptions());
        sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriverWrapper.getShutterSpeedOptions());
        sendMessage("CANON_GET_IMAGE_INFO", canonDriverWrapper.getAllImageInfo());
    }

    private void intiDeltaProtoDriver() throws ExecutionException, InterruptedException {
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
                    //sendMessage("MOHICAN_POSITION", Position.builder().x(new BigDecimal(positionX)).y(new BigDecimal(positionY)).xRun(runningX).yRun(runningY).build());

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

            @Override
            public void newPos(int value, boolean running) {
                posX = (value);
                String val = df.format(toMMx.multiply(new BigDecimal(posX)));
                positionX = val;
                runningX = running;


                if (mohicanFrame != null) {
                    mohicanFrame.setPostionLabelX(val);
                }

                if (positionX != null && positionY != null) {
                    //sendMessage("MOHICAN_POSITION", Position.builder().x(new BigDecimal(positionX)).y(new BigDecimal(positionY)).xRun(runningX).yRun(runningY).build());
                }
            }

            @Override
            public void homingFinished() {
                mohicanFrame.setTitleLabel("Homing X axis finished");
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

            @Override
            public void newPos(int value, boolean running) {
                posY = (value);
                String val = df.format(toMMy.multiply(new BigDecimal(posY)));
                positionY = val;
                runningY = running;

                //System.out.println(posY);

                if (mohicanFrame != null) {
                    mohicanFrame.setPostionLabelY(val);
                }
            }

            @Override
            public void homingFinished() {
                mohicanFrame.setTitleLabel("Homing Y axis finished");
            }
        };

        // canonDriverWrapper = new canonDriverWrapper();
        // canonError = canonDriverWrapper.init();
        // if (canonError != 0) {
        //     logger.error("canonDriverWrapper init error: " + canonError);
        // } else {
        //     logger.info("canonDriverWrapper init success");
        // }

        if (Configuration.getInstance().getTeknicPort() != null) {


            toMMx = Configuration.getInstance().getposToMMx();
            toMMy = Configuration.getInstance().getposToMMy();

            erosController = new DeltaProtoDriver(Configuration.getInstance().getTeknicPort(), encoderListenerX, encoderListenerY);

            erosController.reInitialize();
        } else {

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
                    logger.info(String.valueOf(intPosition));
                    erosController.goTo(intPosition.getX(), intPosition.getY());
//                    erosController.disableBrake();
                    break;
                }
                case "BRAKE": {
                    boolean status = (boolean) action.getValue();
                    if (status) {
                        erosController.enableBrake();
//                        erosController.message("X", "b1");
//                        erosController.message("Y", "b1");
                    } else {
                        erosController.disableBrake();
//                        erosController.message("X", "b0");
//                        erosController.message("Y", "b0");
                    }
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
                case "SET_MIN_MAX_POSITION": {
                    String[] values = ((String) action.getValue()).split(" ");
                    Integer minX = Integer.valueOf(values[0]);
                    Integer maxX = Integer.valueOf(values[1]);
                    Integer minY = Integer.valueOf(values[2]);
                    Integer maxY = Integer.valueOf(values[3]);

                    Position minPosition = Position.builder().x(BigDecimal.valueOf(minX)).y(BigDecimal.valueOf(minY)).build();
                    Position maxPosition = Position.builder().x(BigDecimal.valueOf(maxX)).y(BigDecimal.valueOf(maxY)).build();
                    IntPosition minIntPosition = minPosition.getInt();
                    IntPosition maxIntPosition = maxPosition.getInt();
                    erosController.setMinMaxPosition(minIntPosition.getX(), maxIntPosition.getX(), minIntPosition.getY(), maxIntPosition.getY());
                    break;
                }
                case "ZERO": {
                    erosController.setZero();
                    break;
                }
                case "HOME": {
                    /**
                     * Home the motors
                     * <p>
                     * current: 400
                     * directionX: 0
                     * directionY: 1
                     *
                     * directions will flip if you change the direction from 0 to 1 or vice versa
                     * <p>
                     * string format: "current directionX directionY"
                     * example: "400 0 1"
                     *
                     * spaces in between are mandatory
                     */

                    if (action.getValue() != null) {
                        Integer[] parsedString = Arrays.stream(((String) action.getValue()).split(" ", 3))
                                                       .map(Integer::parseInt)
                                                       .toArray(Integer[]::new);
                        Integer current = parsedString[0];
                        Integer directionX = parsedString[1];
                        Integer directionY = parsedString[2];
                        erosController.home(current, directionX, directionY);
                    } else {
                        erosController.home(null, 0, 0);
                    }
                    mohicanFrame.setTitleLabel("Homing...");
                    break;
                }
                case "MESSAGE": {
                    if (action.getValue() != null && action.getValue() instanceof LinkedHashMap) {
                        try {
                            LinkedHashMap<String, String> hashMap = (LinkedHashMap<String, String>) action.getValue();
                            DeviceMessage deviceMessage = DeviceMessage.fromLinkedHashMap(hashMap);
                            erosController.message(deviceMessage.getDevice(), deviceMessage.getMessage());
                        } catch (Exception e) {
                            logger.error("Error casting action value: " + e.getMessage(), e);
                        }
                    }
                }
                case "GET_VERSION": {
                    String javaVersion = System.getProperty("java.version");
                    sendMessage("MOHICAN_CLIENT_VERSION", VersionMessage.builder().javaVersion(javaVersion).version(version).versions(erosController.getVersion()).build());
                }

                case "CANON_REINITIALIZE": {
                    canonDriverWrapper.closeSession();
                    canonDriverWrapper.openSession();
                    break;
                }

                case "CANON_FORMAT": {
                    logger.error(String.valueOf(canonDriverWrapper.formatAll()));
                    break;
                }

                case "CANON_GET_ALL_SETTINGS": {
                    sendMessage("CANON_APERTURE_OPTIONS", canonDriverWrapper.getListOfApertureOptions());
                    sendMessage("CANON_ISO_OPTIONS", canonDriverWrapper.getListOfIsoOptions());
                    sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriverWrapper.getShutterSpeedOptions());
                    sendMessage("CANON_QUALITY_OPTIONS", canonDriverWrapper.getImageQualityOptions());
                }

                case "CANON_GET_ALL_CURRENT_SETTINGS": {
                    String apertureSetting = canonDriverWrapper.getApertureSetting();
                    String isoSetting = canonDriverWrapper.getIsoSetting();
                    String shutterSpeed = canonDriverWrapper.getCurrentShutterSpeed();
                    String quality = canonDriverWrapper.getCurrentImageQuality();

                    // Send messages only if they don't start with "camera"
                    if (!apertureSetting.startsWith("camera")) {
                        sendMessage("CANON_APERTURE_CURRENT", apertureSetting);
                    }

                    if (!isoSetting.startsWith("camera")) {
                        sendMessage("CANON_ISO_CURRENT", isoSetting);
                    }

                    if (!shutterSpeed.startsWith("camera")) {
                        sendMessage("CANON_SHUTTERSPEED_CURRENT", shutterSpeed);
                    }

                    if (!quality.startsWith("camera")) {
                        sendMessage("CANON_QUALITY_CURRENT", quality);
                    }
                    break;

                }


                case "INIT_CAMERA": {
                    canonDriverWrapper.initCamera();
                    break;
                }

                case "CANON_OPEN_SESSION": {
                    logger.warn(String.valueOf(canonDriverWrapper.openSession()));
                    break;
                }
                case "CANON_CLOSE_SESSION": {
                    canonDriverWrapper.closeSession();
                    break;
                }
                case "CANON_TAKE_PHOTO": {
                    int err = canonDriverWrapper.takePhoto();
                    if (err == 0) {
                        logger.info("CANON_PHOTO_STATUS: OK");
                        sendMessage("CANON_PHOTO_STATUS", "OK");
                    } else {
                        logger.error("CANON_PHOTO_STATUS: " + err);
                        sendMessage("CANON_PHOTO_STATUS", "ERROR: " + err);
                        err = canonDriverWrapper.init();
                        err = canonDriverWrapper.takePhoto();
                        if (err == 0) {
                            logger.info("CANON_PHOTO_STATUS: OK");
                            sendMessage("CANON_PHOTO_STATUS", "OK");
                        } else {
                            logger.error("CANON_PHOTO_STATUS: "+ err);
                            sendMessage("CANON_PHOTO_STATUS", "ERROR: " + err);}

                    }
                    break;
                }

                case "CANON_GET_CURRENT_QUALITY": {
                    sendMessage("CANON_QUALITY_CURRENT", canonDriverWrapper.getCurrentImageQuality());
                    break;
                }

                case "CANON_SET_QUALITY": {
                    canonDriverWrapper.setImageQuality((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_QUALITY": {
                    sendMessage("CANON_QUALITY_OPTIONS", canonDriverWrapper.getImageQualityOptions());
                    break;
                }

                case "CANON_GET_CURRENT_APERTURE": {
                    sendMessage("CANON_APERTURE_CURRENT", canonDriverWrapper.getApertureSetting());
                    break;
                }

                case "CANON_SET_APERTURE": {
                    int err = canonDriverWrapper.setAperture((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_APERTURE": {
                    sendMessage("CANON_APERTURE_OPTIONS", canonDriverWrapper.getListOfApertureOptions());
                    break;
                }

                // iso settings
                case "CANON_GET_CURRENT_ISO": {
                    sendMessage("CANON_ISO_CURRENT", canonDriverWrapper.getIsoSetting());
                    break;
                }
                case "CANON_SET_ISO": {
                    int err = canonDriverWrapper.setIso((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_ALL_ISO": {
                    sendMessage("CANON_ISO_OPTIONS", canonDriverWrapper.getListOfIsoOptions());
                    break;
                }

                // shutterspeed settings
                case "CANON_GET_CURRENT_SHUTTERSPEED": {
                    sendMessage("CANON_SHUTTERSPEED_CURRENT", canonDriverWrapper.getCurrentShutterSpeed());
                    break;
                }
                case "CANON_GET_ALL_SHUTTERSPEED": {
                    sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriverWrapper.getShutterSpeedOptions());
                    break;
                }
                case "CANON_SET_SHUTTERSPEED": {
                    canonDriverWrapper.setShutterSpeed((Integer) action.getValue());
                    break;
                }
                case "CANON_GET_IMAGE_INFO": {
                    canonDriverWrapper.closeSession();
                    canonDriverWrapper.openSession();
                    String[] allImageInfo = canonDriverWrapper.getAllImageInfo();
                    sendMessage("CANON_GET_IMAGE_INFO", allImageInfo);
                    break;
                }

                case "CANON_GET_IMAGE": {
                    // Get the image data from canonDriverWrapper based on the action value
                    String imageName = (String) action.getValue();
                    String[] imageInfoArray = canonDriverWrapper.getAllImageInfo();

                    boolean imageFound = false;

                    for (String info : imageInfoArray) {
                        if (info.equals(imageName)) {
                            imageFound = true;
                            break;
                        }
                    }
                    if (imageFound) {
                        byte[] imageData = canonDriverWrapper.getImage(imageName);
                        logger.info("length: " + String.valueOf(imageData.length));

                        // Check if imageData is not null and has content
                        if (imageData.length != 0) {


                            // Send the Base64 encoded image as a message
                            CanonImage canonImage = CanonImage.builder().name(imageName).data(Base64.getEncoder().encodeToString(imageData)).build();
                            sendMessage("CANON_GET_IMAGE", canonImage);
                        } else {
                            logger.error("Image length: " + String.valueOf(imageData.length) + ", trying again...");
                            imageData = canonDriverWrapper.getImage(imageName);
                            if (imageData.length > 1) {
                                CanonImage canonImage = CanonImage.builder().name(imageName).data(Base64.getEncoder().encodeToString(imageData)).build();
                                sendMessage("CANON_GET_IMAGE", canonImage);
                            } else {
                                logger.error("failed to get image: " + imageName);
                                // Handle the case where no image data is returned
                                sendMessage("CANON_GET_IMAGE_ERROR", "No image data found.");
                            }
                        }
                    } else {
                        sendMessage("CANON_GET_IMAGE_ERROR", "Image with " + imageName + " not found.");
                        sendMessage("CANON_GET_IMAGE_INFO", canonDriverWrapper.getAllImageInfo());
                    }
                    break;
                }
                case "CANON_GET_LAST_IMAGE": {
                    // Get all image info from canonDriverWrapper
                    String[] imageInfoArray = canonDriverWrapper.getAllImageInfo();

                    // Ensure imageInfoArray is not empty before proceeding
                    if (imageInfoArray.length == 0) {
                        sendMessage("CANON_GET_IMAGE_ERROR", "No images found.");
                        break;
                    }

                    // Get the last image in the array
                    String lastImageName = imageInfoArray[imageInfoArray.length - 1];
                    byte[] imageData = canonDriverWrapper.getImage(lastImageName);

                    logger.info("length: " + imageData.length);

                    // Check if imageData is not null and has content
                    if (imageData.length != 0) {
                        // Encode the image data to Base64
                        String base64Image = Base64.getEncoder().encodeToString(imageData);

                        // Send the Base64 encoded image as a message
                        CanonImage canonImage = CanonImage.builder().name(lastImageName).data(base64Image).build();
                        sendMessage("CANON_GET_IMAGE", canonImage);
                    } else {
                        logger.error("Image length: " + imageData.length + ", trying again...");
                        imageData = canonDriverWrapper.getImage(lastImageName);
                        if (imageData.length > 1) {
                            // Encode the image data to Base64
                            String base64Image = Base64.getEncoder().encodeToString(imageData);
                            // Send the Base64 encoded image as a message
                            CanonImage canonImage = CanonImage.builder().name(lastImageName).data(base64Image).build();
                            sendMessage("CANON_GET_IMAGE", canonImage);
                        } else {
                            logger.error("Failed to get image: " + lastImageName);
                            // Handle the case where no image data is returned
                            sendMessage("CANON_GET_IMAGE_ERROR", "No image data found.");
                        }
                    }
                    break;
                }

                case "CANON_SET_FOCUS_STACKING": {
                    try {
                        // Extract settings from action value
                        LinkedHashMap<String, Object> settings = (LinkedHashMap<String, Object>) action.getValue();

                        // Create and populate EdsFocusShiftSet
                        CanonDriver.EdsFocusShiftSet edsFocusShiftSet = new CanonDriver.EdsFocusShiftSet();
                        edsFocusShiftSet.setAll(
                                (Integer) settings.get("version"),
                                (Integer) settings.get("focusShiftFunction"),
                                (Integer) settings.get("shootingNumber"),
                                (Integer) settings.get("stepWidth"),
                                (Integer) settings.get("exposureSmoothing"),
                                (Integer) settings.get("focusStackingFunction"),
                                (Integer) settings.get("focusStackingTrimming"),
                                (Integer) settings.get("reserved")
                        );

                        logger.error(String.valueOf(edsFocusShiftSet.getFocusStackingFunction()));

                        // Call the native method to set focus bracketing
                        int result = canonDriverWrapper.setFocusBracketing(edsFocusShiftSet);
                        if (result != 0) {
                            logger.error("Error setting focus bracketing: " + result);
                        } else {
                            logger.info("Focus bracketing set successfully.");
                        }
                    } catch (ClassCastException e) {
                        logger.error("Error casting action value: " + e.getMessage(), e);
                    } catch (Exception e) {
                        logger.error("Unexpected error: " + e.getMessage(), e);
                    }
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
//        if (canonDriverWrapper.findCamera()) {
//            canonDriverWrapper.reInitialize();
//            canonDriverWrapper.openSession();
//            sendMessage("CANON_GET_IMAGE_INFO", canonDriverWrapper.getAllImageInfo());
//            sendMessage("CANON_APERTURE_OPTIONS", canonDriverWrapper.getListOfApertureOptions());
//            sendMessage("CANON_ISO_OPTIONS", canonDriverWrapper.getListOfIsoOptions());
//            sendMessage("CANON_SHUTTERSPEED_OPTIONS", canonDriverWrapper.getShutterSpeedOptions());
//
//        }

        mohicanFrame.setWebsocketStatus(true);
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");

        if (executor != null) {
            executor.shutdown();
        }

        mohicanFrame.setWebsocketStatus(false);
    }

    @Override
    public void onCameraConnect() {
        logger.info("onCameraConnect");
        mohicanFrame.setTitleLabel("Camera connected");
    }

    @Override
    public void onCameraDisconnect() {
        logger.info("onCameraDisconnect");
    }

    @Override
    public void onCameraPictureTaken() {
        logger.info("onCameraPictureTaken");
    }

    void reInitialize() {
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
        return Position.builder().x(x).y(y).xRun(runningX).yRun(runningY).build();
    }

    void sendPosition() {
        sendPosition("MOHICAN_POSITION");
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

    public void sendMessage(String type, Object value) {

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
        Boolean xRun;
        Boolean yRun;

        IntPosition getInt() {
            BigDecimal toMMx = Configuration.getInstance().getposToMMx();
            BigDecimal toMMy = Configuration.getInstance().getposToMMy();

            BigDecimal x = getX().divide(toMMx, BigDecimal.ROUND_HALF_UP);
            BigDecimal y = getY().divide(toMMy, BigDecimal.ROUND_HALF_UP);
            int xi = x.intValue();
            int yi = y.intValue();
            Boolean runx = xRun;
            Boolean runy = yRun;

            return IntPosition.builder().x(xi).y(yi).xRunning(runx).yRunning(runy).build();
        }
    }

    @Data
    @Builder
    static class IntPosition {
        int x;
        int y;
        Boolean xRunning;
        Boolean yRunning;

    }

    @Data
    @Builder
    static class CanonImage {
        String name;
        String data;
    }
}