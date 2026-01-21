package nl.bytesoflife.mohican;

import java.awt.*;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Handles macOS-specific integration features like dock icon and quit handler.
 * Uses reflection to support both Java 8 (com.apple.eawt) and Java 9+ (java.awt.Taskbar/Desktop).
 * All methods are designed to fail silently to avoid crashing the application.
 */
public class MacOSIntegration {

    private static boolean initialized = false;

    private MacOSIntegration() {
        // Utility class, no instantiation
    }

    /**
     * Check if running on Java 9 or later.
     */
    private static boolean isJava9OrLater() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return false; // Java 8 or earlier (1.8.x, 1.7.x, etc.)
        }
        return true; // Java 9+ uses version like "9", "11", "17", "20", etc.
    }

    /**
     * Initialize macOS-specific UI settings.
     * Sets up menu bar properties, quit handler, and initial dock icon.
     * Fails silently if not on macOS or if any error occurs.
     */
    public static void initialize() {
        if (!OSValidator.isMac()) {
            return;
        }

        if (initialized) {
            return;
        }

        try {
            // Put the menu bar at the top of the screen
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            // Set the name in the menu
            System.setProperty("com.apple.mrj.application.apple.menu.about.name", "DeltaProto Mohican");
            // Set the application name for the dock
            System.setProperty("apple.awt.application.name", "DeltaProto Mohican");

            if (isJava9OrLater()) {
                setupModernQuitHandler();
            } else {
                setupLegacyQuitHandler();
            }

            // Set initial dock icon
            Image icon = Toolkit.getDefaultToolkit().getImage(MacOSIntegration.class.getResource("/logo.png"));
            setDockIcon(icon);

            initialized = true;
        } catch (Throwable t) {
            System.err.println("MacOSIntegration: Failed to initialize - " + t.getMessage());
        }
    }

    /**
     * Set the dock icon using the appropriate API for the current Java version.
     * Fails silently if not on macOS or if any error occurs.
     *
     * @param icon the image to set as dock icon
     */
    public static void setDockIcon(Image icon) {
        if (!OSValidator.isMac() || icon == null) {
            return;
        }

        try {
            if (isJava9OrLater()) {
                setDockIconModern(icon);
            } else {
                setDockIconLegacy(icon);
            }
        } catch (Throwable t) {
            System.err.println("MacOSIntegration: Failed to set dock icon - " + t.getMessage());
        }
    }

    /**
     * Setup quit handler using Java 9+ Desktop API via reflection.
     */
    private static void setupModernQuitHandler() throws Exception {
        Class<?> desktopClass = Class.forName("java.awt.Desktop");
        Method isDesktopSupported = desktopClass.getMethod("isDesktopSupported");
        if (!(Boolean) isDesktopSupported.invoke(null)) {
            return;
        }

        Method getDesktop = desktopClass.getMethod("getDesktop");
        Object desktop = getDesktop.invoke(null);

        // Check if APP_QUIT_HANDLER is supported
        Class<?> actionClass = Class.forName("java.awt.Desktop$Action");
        @SuppressWarnings("unchecked")
        Object quitHandlerAction = Enum.valueOf((Class<Enum>) actionClass, "APP_QUIT_HANDLER");
        Method isSupported = desktopClass.getMethod("isSupported", actionClass);
        if (!(Boolean) isSupported.invoke(desktop, quitHandlerAction)) {
            return;
        }

        // Create quit handler proxy
        Class<?> quitHandlerClass = Class.forName("java.awt.desktop.QuitHandler");
        Object quitHandler = Proxy.newProxyInstance(
                quitHandlerClass.getClassLoader(),
                new Class<?>[]{quitHandlerClass},
                (proxy, method, args) -> {
                    if ("handleQuitRequestWith".equals(method.getName())) {
                        System.exit(0);
                    }
                    return null;
                }
        );
        Method setQuitHandler = desktopClass.getMethod("setQuitHandler", quitHandlerClass);
        setQuitHandler.invoke(desktop, quitHandler);
    }

    /**
     * Setup quit handler using Java 8 com.apple.eawt.Application via reflection.
     */
    private static void setupLegacyQuitHandler() throws Exception {
        Class<?> appClass = Class.forName("com.apple.eawt.Application");
        Method getApplication = appClass.getMethod("getApplication");
        Object app = getApplication.invoke(null);

        if (app == null) {
            return;
        }

        // Set quit handler
        Class<?> quitHandlerClass = Class.forName("com.apple.eawt.QuitHandler");
        Object quitHandler = Proxy.newProxyInstance(
                quitHandlerClass.getClassLoader(),
                new Class<?>[]{quitHandlerClass},
                (proxy, method, args) -> {
                    if ("handleQuitRequestWith".equals(method.getName())) {
                        System.exit(0);
                    }
                    return null;
                }
        );
        Method setQuitHandler = appClass.getMethod("setQuitHandler", quitHandlerClass);
        setQuitHandler.invoke(app, quitHandler);
    }

    /**
     * Set dock icon using Java 9+ Taskbar API via reflection.
     */
    private static void setDockIconModern(Image icon) throws Exception {
        Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
        Method isTaskbarSupported = taskbarClass.getMethod("isTaskbarSupported");
        if (!(Boolean) isTaskbarSupported.invoke(null)) {
            return;
        }

        Method getTaskbar = taskbarClass.getMethod("getTaskbar");
        Object taskbar = getTaskbar.invoke(null);

        Class<?> featureClass = Class.forName("java.awt.Taskbar$Feature");
        @SuppressWarnings("unchecked")
        Object iconImageFeature = Enum.valueOf((Class<Enum>) featureClass, "ICON_IMAGE");
        Method isSupported = taskbarClass.getMethod("isSupported", featureClass);
        if (!(Boolean) isSupported.invoke(taskbar, iconImageFeature)) {
            return;
        }

        Method setIconImage = taskbarClass.getMethod("setIconImage", Image.class);
        setIconImage.invoke(taskbar, icon);
    }

    /**
     * Set dock icon using Java 8 com.apple.eawt.Application via reflection.
     */
    private static void setDockIconLegacy(Image icon) throws Exception {
        Class<?> appClass = Class.forName("com.apple.eawt.Application");
        Method getApplication = appClass.getMethod("getApplication");
        Object app = getApplication.invoke(null);

        if (app == null) {
            return;
        }

        Method setDockIconImage = appClass.getMethod("setDockIconImage", Image.class);
        setDockIconImage.invoke(app, icon);
    }
}
