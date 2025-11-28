package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.mohican.OSValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Initializes SSL certificate before Spring Boot configures the web server.
 * This ensures the dynamically generated certificate is used for HTTPS.
 * Uses keytool for certificate generation to ensure compatibility with all Java versions.
 */
public class SslCertificateInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(SslCertificateInitializer.class);

    private static final String KEYSTORE_FILENAME = "mohican2.p12";
    private static final String CERT_FILENAME = "mohican2.crt";
    private static final String CERT_ALIAS = "mohican2";
    private static final String KEYSTORE_PASSWORD = "deltaproto";
    private static final String HOSTNAME = "localhost";
    private static final int VALIDITY_DAYS = 3650;

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            Path appDataDir = getAppDataDirectory();
            Files.createDirectories(appDataDir);

            Path keystorePath = appDataDir.resolve(KEYSTORE_FILENAME);
            Path certPath = appDataDir.resolve(CERT_FILENAME);

            if (!Files.exists(keystorePath)) {
                logger.info("Keystore not found at {}, generating new self-signed certificate...", keystorePath);
                generateCertificateWithKeytool(keystorePath, certPath);
                installCertificateInTrustStore(certPath);
            } else {
                logger.info("Using existing keystore at: {}", keystorePath);
            }

            // Set SSL properties to use the dynamic keystore
            // Use proper URI encoding for paths with spaces
            ConfigurableEnvironment environment = applicationContext.getEnvironment();
            Map<String, Object> sslProperties = new HashMap<>();
            String keystoreUri = keystorePath.toUri().toString();
            sslProperties.put("server.ssl.key-store", keystoreUri);
            sslProperties.put("server.ssl.key-store-type", "PKCS12");
            sslProperties.put("server.ssl.key-store-password", KEYSTORE_PASSWORD);
            sslProperties.put("server.ssl.key-alias", CERT_ALIAS);

            environment.getPropertySources().addFirst(new MapPropertySource("dynamicSslProperties", sslProperties));
            logger.info("SSL configured to use keystore at: {}", keystoreUri);

        } catch (Exception e) {
            logger.error("Failed to initialize SSL certificate, falling back to bundled keystore", e);
        }
    }

    private Path getAppDataDirectory() {
        String userHome = System.getProperty("user.home");
        if (OSValidator.isWindows()) {
            String appData = System.getenv("LOCALAPPDATA");
            if (appData == null) {
                appData = userHome + "\\AppData\\Local";
            }
            return Paths.get(appData, "Mohican", "ssl");
        } else if (OSValidator.isMac()) {
            return Paths.get(userHome, "Library", "Application Support", "Mohican", "ssl");
        } else {
            return Paths.get(userHome, ".mohican", "ssl");
        }
    }

    private void generateCertificateWithKeytool(Path keystorePath, Path certPath) throws Exception {
        // Find keytool executable
        String keytool = findKeytool();

        // Generate keystore with self-signed certificate using keytool
        // keytool -genkeypair -alias mohican2 -keyalg RSA -keysize 2048 -validity 3650
        //         -keystore mohican2.p12 -storetype PKCS12 -storepass deltaproto
        //         -dname "CN=localhost, O=Mohican, L=Local, C=NL"
        //         -ext "SAN=dns:localhost,ip:127.0.0.1"
        List<String> genKeyCmd = new ArrayList<>();
        genKeyCmd.add(keytool);
        genKeyCmd.add("-genkeypair");
        genKeyCmd.add("-alias");
        genKeyCmd.add(CERT_ALIAS);
        genKeyCmd.add("-keyalg");
        genKeyCmd.add("RSA");
        genKeyCmd.add("-keysize");
        genKeyCmd.add("2048");
        genKeyCmd.add("-validity");
        genKeyCmd.add(String.valueOf(VALIDITY_DAYS));
        genKeyCmd.add("-keystore");
        genKeyCmd.add(keystorePath.toString());
        genKeyCmd.add("-storetype");
        genKeyCmd.add("PKCS12");
        genKeyCmd.add("-storepass");
        genKeyCmd.add(KEYSTORE_PASSWORD);
        genKeyCmd.add("-keypass");
        genKeyCmd.add(KEYSTORE_PASSWORD);
        genKeyCmd.add("-dname");
        genKeyCmd.add("CN=" + HOSTNAME + ", O=Mohican, L=Local, C=NL");
        genKeyCmd.add("-ext");
        genKeyCmd.add("SAN=dns:" + HOSTNAME + ",ip:127.0.0.1");

        ProcessBuilder pb = new ProcessBuilder(genKeyCmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("keytool genkeypair failed: " + output);
        }
        logger.info("Keystore created at: {}", keystorePath);

        // Export certificate from keystore
        // keytool -exportcert -alias mohican2 -keystore mohican2.p12 -storepass deltaproto -file mohican2.crt
        List<String> exportCmd = new ArrayList<>();
        exportCmd.add(keytool);
        exportCmd.add("-exportcert");
        exportCmd.add("-alias");
        exportCmd.add(CERT_ALIAS);
        exportCmd.add("-keystore");
        exportCmd.add(keystorePath.toString());
        exportCmd.add("-storetype");
        exportCmd.add("PKCS12");
        exportCmd.add("-storepass");
        exportCmd.add(KEYSTORE_PASSWORD);
        exportCmd.add("-file");
        exportCmd.add(certPath.toString());
        exportCmd.add("-rfc"); // PEM format for better compatibility

        pb = new ProcessBuilder(exportCmd);
        pb.redirectErrorStream(true);
        process = pb.start();
        output = readProcessOutput(process);
        exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("keytool exportcert failed: " + output);
        }
        logger.info("Certificate exported to: {}", certPath);
    }

    private String findKeytool() {
        String javaHome = System.getProperty("java.home");
        String keytool;

        if (OSValidator.isWindows()) {
            keytool = javaHome + "\\bin\\keytool.exe";
        } else {
            keytool = javaHome + "/bin/keytool";
        }

        // Verify keytool exists
        if (Files.exists(Paths.get(keytool))) {
            return keytool;
        }

        // Fall back to PATH
        return "keytool";
    }

    private void installCertificateInTrustStore(Path certPath) {
        try {
            if (OSValidator.isWindows()) {
                installCertificateWindows(certPath);
            } else if (OSValidator.isMac()) {
                installCertificateMac(certPath);
            } else {
                logger.warn("Certificate installation not supported on this OS. Please manually trust: {}", certPath);
            }
        } catch (Exception e) {
            logger.error("Failed to install certificate in system trust store", e);
            logger.info("Please manually install the certificate from: {}", certPath);
        }
    }

    private void installCertificateWindows(Path certPath) throws Exception {
        logger.info("Installing certificate in Windows trust store...");

        ProcessBuilder pb = new ProcessBuilder(
                "certutil", "-addstore", "-user", "Root", certPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Certificate successfully installed in Windows user trust store");
        } else {
            logger.warn("Could not install certificate automatically. Output: {}", output);
            logger.info("To install manually: double-click {} and install to 'Trusted Root Certification Authorities'", certPath);
        }
    }

    private void installCertificateMac(Path certPath) throws Exception {
        logger.info("Installing certificate in macOS keychain...");

        ProcessBuilder pb = new ProcessBuilder(
                "security", "add-trusted-cert", "-r", "trustRoot",
                "-k", System.getProperty("user.home") + "/Library/Keychains/login.keychain-db",
                certPath.toString()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output = readProcessOutput(process);
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            logger.info("Certificate successfully installed in macOS keychain");
        } else {
            logger.warn("Could not install certificate automatically. Output: {}", output);
            logger.info("To install manually: double-click {} and add to login keychain with 'Always Trust'", certPath);
        }
    }

    private String readProcessOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output.toString();
    }
}
