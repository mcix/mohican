package nl.bytesoflife.mohican;

import java.awt.*;
import java.awt.event.ActionEvent;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;

import lombok.Data;
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
public class SwingApp extends JFrame implements ReduxEventListener, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(SwingApp.class);

    @Autowired
    private SimpMessagingTemplate websocket;

    @Autowired
    private WebSocketEventListener eventListener;

    public SwingApp() {

        initUI();
    }

    private void initUI() {

        JButton quitButton = new JButton("Quit");

        quitButton.addActionListener((ActionEvent event) -> {
            System.exit(0);
        });

        JButton messageButton = new JButton("Send test message");

        messageButton.addActionListener((ActionEvent event) -> {
            ReduxAction reduxAction= new ReduxAction();
            reduxAction.setType("RECEIVE_CHECK_BOM_LINE");
            reduxAction.setValue( "Test" );

            if( websocket != null ) {
                websocket.convertAndSend(WebSocketConfiguration.MESSAGE_PREFIX + "/mohican/", reduxAction);
            }
        });

        createLayout(quitButton, messageButton);

        setTitle("Mohican [DISCONNECTED]");
        setSize(300, 70);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);


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

    @Override
    public void onMessage(ReduxAction action) {
        logger.info("onMessage" + action.getType());
    }

    @Override
    public void onConnect() {
        logger.info("onConnect");
        setTitle("Mohican [CONNECTED]");
    }

    @Override
    public void onDisconnect() {
        logger.info("onDisconnect");
        setTitle("Mohican [DISCONNECTED]");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        eventListener.addReduxEventListener( this );
    }
}