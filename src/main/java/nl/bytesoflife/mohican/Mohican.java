package nl.bytesoflife.mohican;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.*;

import lombok.Builder;
import lombok.Data;
import nl.bytesoflife.*;
import nl.bytesoflife.mohican.spring.WebSocketConfiguration;
import nl.bytesoflife.mohican.spring.WebSocketEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@SpringBootApplication
public class Mohican extends JFrame implements ReduxEventListener, InitializingBean, Runnable {

    {
        setupOSX();
    }

    static void setupOSX() {
        if( !OSValidator.isMac() ) {
            return;
        }
        //log.debug("Setting up OSX UI Elements");
        try {
            // Put the menu bar at the top of the screen
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // Set the name in the menu
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Mohican");

            // This line must come AFTER the above properties are set, otherwise
            // the name will not appear
            final com.apple.eawt.Application osxApp = com.apple.eawt.Application.getApplication();
            if (osxApp == null) {
                // setup.
                throw new NullPointerException("com.apple.eawt.Application.getApplication() returned NULL. " + "Aborting OSX UI Setup.");
            }
            // Set handlers
            osxApp.setQuitHandler((quitEvent, quitResponse) -> {
                System.exit(0);
            });
            //osxApp.setAboutHandler(ah);
            //osxApp.setPreferencesHandler(ph);
            // Set the dock icon to the largest icon
            //final Image dockIcon = Toolkit.getDefaultToolkit().getImage(SwingStartup.class.getResource(ICON_RSRC));
            //osxApp.setDockIconImage(dockIcon);

            final Image dockIcon = Toolkit.getDefaultToolkit().getImage(Mohican.class.getResource("/logo.png"));

            osxApp.setDockIconImage(dockIcon);
            //osxApp.setDockIconBadge("Mohican");

        } catch (final Throwable t) {
            //log.warn("Error setting up OSX UI:", t);
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(Mohican.class);

    @Autowired
    private SimpMessagingTemplate websocket;

    @Autowired
    private WebSocketEventListener eventListener;

    int posX, posY;
    private JButton messageButton;
    private JLabel postionLabelX;
    private JLabel postionLabelY;

    private JSlider sliderX;
    private JSlider sliderY;
    private static Integer sliderMaxY = 34500;
    private static Integer sliderMaxX = 61600;
    private static Integer sliderDivider = 100;

    private ErosController erosController;

    public Mohican() {
        initUI();
        intiDeltaProtoDriver();
    }

    private void setImage(boolean on) {
        Image img = getToolkit().getImage(getClass().getResource(on ? "/logo_on.png" : "/logo.png"));

        setIconImage(img);
    }

    private void initUI() {

        setImage(false);

        JButton quitButton = new JButton("Quit");

        quitButton.addActionListener((ActionEvent event) -> {
            System.exit(0);
        });

        messageButton = new JButton("Send position");
        messageButton.setEnabled( false );
        messageButton.addActionListener((ActionEvent event) -> {
            sendPosition();
        });

        postionLabelX = new JLabel();
        postionLabelX.setHorizontalAlignment(4);
        postionLabelX.setHorizontalTextPosition(0);
        postionLabelX.setText("0.0");

        postionLabelY = new JLabel();
        postionLabelY.setHorizontalAlignment(4);
        postionLabelY.setHorizontalTextPosition(0);
        postionLabelY.setText("0.0");

        //createLayout(quitButton, messageButton);
        createLayout(quitButton);
        createLayout(postionLabelX, postionLabelY);

        if (Configuration.getInstance().getTeknicPort() == null && Configuration.getInstance().getPortX() == null) {
            DecimalFormat df = new DecimalFormat();
            sliderX = new JSlider( 0, sliderMaxX, 0);
            sliderY = new JSlider(JSlider.VERTICAL, 0, sliderMaxY, 0);

            sliderX.addChangeListener(e -> {
                String val = df.format(new BigDecimal(sliderX.getValue()).divide(BigDecimal.valueOf(sliderDivider)));

                postionLabelX.setText("X " + val);
                postionLabelX.paintImmediately(postionLabelX.getVisibleRect());
            });

            sliderY.addChangeListener(e -> {
                String val = df.format(new BigDecimal((sliderY.getValue())).divide(BigDecimal.valueOf(sliderDivider)));

                postionLabelY.setText("Y " + val);
                postionLabelY.paintImmediately(postionLabelY.getVisibleRect());
            });

            Container pane = getContentPane();
            pane.add(sliderY);
            pane.add(sliderX);

        }

        setTitle("Mohican [DISCONNECTED]");
        setSize(300, 90);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
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

                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            String val = df.format(toMMx.multiply(new BigDecimal(posX)));

                            postionLabelX.setText(val);
                            postionLabelX.paintImmediately(postionLabelX.getVisibleRect());
                        }
                    });
                }
            };

            EncoderListener encoderListenerY = new EncoderListener() {
                public void newPos(final int value) {
                    posY = new Integer(value);
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            String val = df.format(toMMy.multiply(new BigDecimal(posY)));

                            postionLabelY.setText(val);
                            postionLabelY.paintImmediately(postionLabelY.getVisibleRect());
                        }
                    });
                }
            };
        //}

        if (Configuration.getInstance().getTeknicPort() != null) {


            toMMx = Configuration.getInstance().getposToMMx();
            toMMy = Configuration.getInstance().getposToMMy();

            erosController = new DeltaProtoDriver(Configuration.getInstance().getTeknicPort(), encoderListenerX, encoderListenerY);

            erosController.reInitialize();
        } else if( Configuration.getInstance().getPortX() != null ) {

            erosController = new ErosControllerImpl(encoderListenerX, encoderListenerY);
            erosController.reInitialize();

        }

    }

    private void createLayout(JComponent... arg) {

        Container pane = getContentPane();

        FlowLayout layout = new FlowLayout();

        pane.setLayout( layout );

        for (JComponent jComponent : arg) {
            pane.add( jComponent );
        }

    }

    public static void main(String[] args) {

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Mohican.class)
                .headless(false).run(args);

        EventQueue.invokeLater(() -> {
            Mohican ex = ctx.getBean(Mohican.class);
            ex.setVisible(true);
        });
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
                case "ZERO": {
                    erosController.setZero();
                    break;
                }

            }

        }
        catch (Exception e) {
            logger.warn("Failed to parse message " + action.getType() + " " + action.getValue());
        }
    }

    @Override
    public void onConnect() {
        logger.info("onConnect");
        setTitle("Mohican [CONNECTED]");

        setImage(true);

        messageButton.setEnabled( true );
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");
        setTitle("Mohican [DISCONNECTED]");

        setImage(false);

        if( executor != null ) {
            executor.shutdown();
        }
        messageButton.setEnabled( false );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventListener.addReduxEventListener( this );
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

    @Data
    @Builder
    static class Position {
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

        BigDecimal x = BigDecimal.valueOf(0);
        BigDecimal y = BigDecimal.valueOf(0);

        if( erosController != null ) {
            //int xi = ((DeltaProtoDriver) erosController).getPosX();
            //int yi = ((DeltaProtoDriver) erosController).getPosY();

            //todo: test this
            String xi = postionLabelX.getText().replace(",", ".");
            String yi = postionLabelY.getText().replace(",", ".");

            x = new BigDecimal(xi);
            y = new BigDecimal(yi);
        } else {
            x = new BigDecimal(sliderX.getValue()).divide(BigDecimal.valueOf(sliderDivider));
            y = new BigDecimal((sliderY.getValue())).divide(BigDecimal.valueOf(sliderDivider));
        }

        ReduxAction reduxAction= new ReduxAction();
        reduxAction.setType(type);
        reduxAction.setValue( Position.builder().x(x).y(y).build() );

        if( websocket != null ) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    @Override
    public void run() {
        sendPosition();
    }
}