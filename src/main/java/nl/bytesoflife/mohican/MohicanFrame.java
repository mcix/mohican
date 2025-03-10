package nl.bytesoflife.mohican;

import nl.bytesoflife.Configuration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class MohicanFrame extends JFrame {

    private Mohican mohican;

    {
        //setupOSX();
    }

    private JLabel titleLabel;
    private JLabel labelX;
    private JLabel labelY;
    private JLabel postionLabelX;
    private JLabel postionLabelY;

    private JSlider sliderX;
    private JSlider sliderY;
    private static Integer sliderMaxY = 34500;
    private static Integer sliderMaxX = 61600;
    private static Integer sliderDivider = 100;

    /*static void setupOSX() {
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
    }*/

    public MohicanFrame(Mohican m_mohican) {
        initUI();

        mohican = m_mohican;

        EventQueue.invokeLater(() -> {
            this.setVisible(true);
        });
    }

    private void setImage(boolean on) {
        Image img = getToolkit().getImage(getClass().getResource(on ? "/logo_on.png" : "/logo.png"));

        setIconImage(img);
    }

    private void initUI() {

        setImage(false);

        titleLabel = new JLabel("", SwingConstants.CENTER);

        postionLabelX = new JLabel();
        postionLabelX.setHorizontalAlignment(4);
        postionLabelX.setHorizontalTextPosition(0);
        postionLabelX.setText("0.0");
        //give the label a fixed width
        postionLabelX.setPreferredSize(new Dimension(45, 20));

        postionLabelY = new JLabel();
        postionLabelY.setHorizontalAlignment(4);
        postionLabelY.setHorizontalTextPosition(0);
        postionLabelY.setText("0.0");
        //give the label a fixed width
        postionLabelY.setPreferredSize(new Dimension(45, 20));

        labelX = new JLabel("X:");
        labelY = new JLabel("Y:");

        JPanel frame2 = new JPanel();

        frame2.add(labelX);
        frame2.add(postionLabelX);
        frame2.add(labelY);
        frame2.add(postionLabelY);
        frame2.setPreferredSize(new Dimension(200, 45));

        JButton quitButton = new JButton("Quit");
        quitButton.addActionListener((ActionEvent event) -> {
            System.exit(0);
        });

        JButton reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener((ActionEvent event) -> {
            //System.exit(0);
            mohican.reInitialize();
        });

        JPanel frame = new JPanel();
        frame.add(quitButton);
        frame.add(reconnectButton);
        frame.setPreferredSize(new Dimension(200, 45));

        createLayout(titleLabel, frame2, frame);

        if (Configuration.getInstance().getMotoroSimulate()) {
            DecimalFormat df = new DecimalFormat();
            sliderX = new JSlider(0, sliderMaxX, 0);
            sliderY = new JSlider(JSlider.VERTICAL, 0, sliderMaxY, 0);

            sliderX.addChangeListener(e -> {
                String val = df.format(new BigDecimal(sliderX.getValue()).divide(BigDecimal.valueOf(sliderDivider)));

                postionLabelX.setText(val);
                postionLabelX.paintImmediately(postionLabelX.getVisibleRect());
            });

            sliderY.addChangeListener(e -> {
                String val = df.format(new BigDecimal((sliderY.getValue())).divide(BigDecimal.valueOf(sliderDivider)));

                postionLabelY.setText(val);
                postionLabelY.paintImmediately(postionLabelY.getVisibleRect());
            });

            Container pane = getContentPane();
            pane.add(sliderY);
            pane.add(sliderX);
        }

        setTitle("Mohican");
        setSize(250, 130);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    private void createLayout(Container... arg) {

        Container pane = getContentPane();
        pane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        pane.add(arg[0], gbc);

        gbc.gridwidth = 2;
        gbc.gridy = 1;
        pane.add(arg[1], gbc);

        gbc.gridwidth = 2;
        gbc.gridy = 2;
        pane.add(arg[2], gbc);
    }

    public void setTitleLabel(String val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                titleLabel.setText(val);
                titleLabel.paintImmediately(titleLabel.getVisibleRect());
            }
        });
    }

    public void setXConnectionStatus(boolean val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                labelX.setForeground(val ? Color.GREEN : Color.RED);
                labelX.paintImmediately(labelX.getVisibleRect());
            }
        });
    }

    public void setYConnectionStatus(boolean val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                labelY.setForeground(val ? Color.GREEN : Color.RED);
                labelY.paintImmediately(labelY.getVisibleRect());
            }
        });
    }

    public void setPostionLabelX(String val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                postionLabelX.setText(val);
                postionLabelX.paintImmediately(postionLabelX.getVisibleRect());
            }
        });
    }

    public void setPostionLabelY(String val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                postionLabelY.setText(val);
                postionLabelY.paintImmediately(postionLabelY.getVisibleRect());
            }
        });
    }

    public BigDecimal getPostionLabelX() {
        return new BigDecimal(sliderX.getValue()).divide(BigDecimal.valueOf(sliderDivider));
    }

    public BigDecimal getPostionLabelY() {
        return new BigDecimal(sliderY.getValue()).divide(BigDecimal.valueOf(sliderDivider));
    }
}
