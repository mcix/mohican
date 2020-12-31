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
import nl.bytesoflife.Configuration;
import nl.bytesoflife.DeltaProtoDriver;
import nl.bytesoflife.EncoderListener;
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
public class SwingApp extends JFrame implements ReduxEventListener, InitializingBean, Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SwingApp.class);

    @Autowired
    private SimpMessagingTemplate websocket;

    @Autowired
    private WebSocketEventListener eventListener;

    int posX, posY;
    private JButton messageButton;
    private JLabel postionLabelX;
    private JLabel postionLabelY;

    private DeltaProtoDriver erosController;

    public SwingApp() {

        initUI();

        intiDeltaProtoDriver();
    }

    private void initUI() {

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
        postionLabelX.setText("11.0");

        postionLabelY = new JLabel();
        postionLabelY.setHorizontalAlignment(4);
        postionLabelY.setHorizontalTextPosition(0);
        postionLabelY.setText("13,1");

        createLayout(quitButton, messageButton);
        createLayout(postionLabelX, postionLabelY);

        setTitle("Mohican [DISCONNECTED]");
        setSize(300, 70);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void intiDeltaProtoDriver() {
        if (Configuration.getInstance().getTeknicPort() != null) {

            DecimalFormat df = new DecimalFormat();
            BigDecimal toMMx = Configuration.getInstance().getposToMMx();
            BigDecimal toMMy = Configuration.getInstance().getposToMMy();

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

            erosController = new DeltaProtoDriver(Configuration.getInstance().getTeknicPort(), encoderListenerX, encoderListenerY);
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

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(SwingApp.class)
                .headless(false).run(args);

        EventQueue.invokeLater(() -> {
            SwingApp ex = ctx.getBean(SwingApp.class);
            ex.setVisible(true);
        });
    }

    private Position parsePosition(Object object) {
        LinkedHashMap<String, Double> val = (LinkedHashMap<String, Double>) object.getValue();
        return Position.builder().x(new BigDecimal(val.get("x"))).y(new BigDecimal(val.get("y"))).build();
    }

    @Override
    public void onMessage(ReduxAction action) {
        logger.info("onMessage" + action.getType());

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

        messageButton.setEnabled( true );
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");
        setTitle("Mohican [DISCONNECTED]");

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
        logger.info("Send position via websocket");

        BigDecimal currentX = new BigDecimal(postionLabelX.getText().replace(',', '.'));
        BigDecimal currentY = new BigDecimal(postionLabelY.getText().replace(',', '.'));

        ReduxAction reduxAction= new ReduxAction();
        reduxAction.setType("MOHICAN_POSITION");
        reduxAction.setValue( Position.builder().x(currentX).y(currentY).build() );

        if( websocket != null ) {
            websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
        }
    }

    @Override
    public void run() {
        sendPosition();
    }
}