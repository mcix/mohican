package nl.bytesoflife.mohican;

import nl.bytesoflife.Configuration;
import nl.bytesoflife.inspector.CameraDriver;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;

public class MohicanFrame extends JFrame {

    private Mohican mohican;

    {
        MacOSIntegration.initialize();
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

    private JComboBox<CameraDriver> driverSelector;
    private JLabel driverStatusLabel;
    private JLabel motorStatusLabel;
    /** Guards the selector's listener while we set it programmatically. */
    private volatile boolean suppressDriverEvents = false;

    public MohicanFrame(Mohican m_mohican) {
        // Assign before building the UI: the camera panel's listener calls back
        // into mohican, and initUI() used to run while the field was still null.
        mohican = m_mohican;

        initUI();

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

        createLayout(titleLabel, frame2, frame, buildMotorPanel(), buildCameraPanel());

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
        setSize(250, 270);
        setLocationRelativeTo(null);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
    }

    /**
     * Camera driver selector.
     * <p>
     * Only one driver can hold the camera — EDSDK allows a single session per body
     * — so this is a switch, not a set of independent toggles. Selecting an entry
     * stops the current driver and starts the chosen one; the label underneath
     * reports whether it actually came up, which is the point: on a Mac the JNI
     * driver cannot load InspectorCamera.dll, and without a camera attached the
     * SDK driver starts but never becomes ready.
     * <p>
     * Switching happens on a worker thread. Bringing a driver up talks to the
     * camera over USB and can block for seconds; doing that on the EDT would
     * freeze the window mid-switch.
     */
    private JPanel buildCameraPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Camera driver"));

        driverSelector = new JComboBox<>(CameraDriver.values());
        driverStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        driverStatusLabel.setFont(driverStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));

        driverSelector.addActionListener((ActionEvent event) -> {
            if (suppressDriverEvents) {
                return;
            }
            final CameraDriver selected = (CameraDriver) driverSelector.getSelectedItem();
            if (selected == null || mohican == null) {
                return;
            }
            driverSelector.setEnabled(false);
            driverStatusLabel.setText("switching to " + selected.getId() + "…");
            new Thread(() -> {
                boolean ready = false;
                String failure = null;
                try {
                    ready = mohican.selectCameraDriver(selected);
                } catch (Throwable t) {
                    // Never let a driver failure kill the UI thread that owns the
                    // selector — the operator needs it to switch back.
                    failure = t.getMessage();
                } finally {
                    final boolean up = ready;
                    final String failed = failure;
                    // All Swing touches on the EDT — this runs on the switch thread.
                    SwingUtilities.invokeLater(() -> {
                        driverSelector.setEnabled(true);
                        if (failed != null) {
                            suppressDriverEvents = true;
                            try {
                                driverSelector.setSelectedItem(selected);
                            } finally {
                                suppressDriverEvents = false;
                            }
                            driverStatusLabel.setText("failed: " + failed);
                        } else {
                            setCameraDriverStatus(selected, up);
                        }
                    });
                }
            }, "camera-driver-switch").start();
        });

        panel.add(driverSelector, BorderLayout.CENTER);
        panel.add(driverStatusLabel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(200, 70));
        return panel;
    }

    /**
     * Which machine drives the head, and whether it answers. Mohican finds the
     * machine by itself (Teknic hub, else the serial microPlacer) and keeps
     * looking while nothing is there; Reconnect starts that search over.
     */
    private JPanel buildMotorPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 2));
        panel.setBorder(BorderFactory.createTitledBorder("Motors"));
        motorStatusLabel = new JLabel("looking for the machine\u2026", SwingConstants.CENTER);
        motorStatusLabel.setFont(motorStatusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(motorStatusLabel, BorderLayout.CENTER);
        panel.setPreferredSize(new Dimension(200, 45));
        return panel;
    }

    public void setMotorStatus(String status, boolean ready) {
        SwingUtilities.invokeLater(() -> {
            if (motorStatusLabel == null) {
                return;
            }
            motorStatusLabel.setText(status);
            motorStatusLabel.setToolTipText(status);
            motorStatusLabel.setForeground(ready ? new Color(0x2e, 0x7d, 0x32) : UIManager.getColor("Label.foreground"));
        });
    }

    /** Reflect the active driver without re-triggering the switch listener. */
    public void setCameraDriverStatus(CameraDriver driver, boolean ready) {
        SwingUtilities.invokeLater(() -> {
            if (driverSelector == null) {
                return;
            }
            suppressDriverEvents = true;
            try {
                if (driver != null && driverSelector.getSelectedItem() != driver) {
                    driverSelector.setSelectedItem(driver);
                }
            } finally {
                suppressDriverEvents = false;
            }
            if (driver == null) {
                driverStatusLabel.setText("no driver active");
            } else {
                driverStatusLabel.setText(ready ? "● ready" : "○ not ready");
            }
        });
    }

    private void createLayout(Container... arg) {

        Container pane = getContentPane();
        pane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        for (int i = 0; i < arg.length; i++) {
            gbc.gridy = i;
            pane.add(arg[i], gbc);
        }
    }

    public void setTitleLabel(String val) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                titleLabel.setText(val);
                titleLabel.paintImmediately(titleLabel.getVisibleRect());
            }
        });
    }

    public void setWebsocketStatus(boolean val) {

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {

                setTitle("Mohican " + (val ? "⚡️" : ""));

                //set the macos icon to the logo_on.png if val is true, otherwise to the logo.png
                final Image dockIcon = Toolkit.getDefaultToolkit().getImage(Mohican.class.getResource(val ? "/logo_on.png" : "/logo.png"));
                MacOSIntegration.setDockIcon(dockIcon);
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
