package nl.bytesoflife.mohican.spring;

import nl.bytesoflife.mohican.OSValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import sun.security.x509.*;

/**
 * Initializes SSL certificate before Spring Boot configures the web server.
 * This ensures the dynamically generated certificate is used for HTTPS.
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
                generateCertificate(keystorePath, certPath);
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

    private void generateCertificate(Path keystorePath, Path certPath) throws Exception {
        // Generate key pair
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048, new SecureRandom());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        // Generate self-signed certificate
        X509Certificate certificate = generateSelfSignedCertificate(keyPair, HOSTNAME, VALIDITY_DAYS);

        // Create keystore and store the key pair and certificate
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry(CERT_ALIAS, keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
                new Certificate[]{certificate});

        // Save keystore to file
        try (FileOutputStream fos = new FileOutputStream(keystorePath.toFile())) {
            keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        }
        logger.info("Keystore created at: {}", keystorePath);

        // Export certificate to file for installation
        try (FileOutputStream fos = new FileOutputStream(certPath.toFile())) {
            fos.write(certificate.getEncoded());
        }
        logger.info("Certificate exported to: {}", certPath);
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String hostname, int validityDays) throws Exception {
        PrivateKey privateKey = keyPair.getPrivate();
        X509CertInfo info = new X509CertInfo();

        Date from = new Date();
        Date to = new Date(from.getTime() + validityDays * 86400000L);

        CertificateValidity interval = new CertificateValidity(from, to);
        BigInteger serialNumber = new BigInteger(64, new SecureRandom());
        X500Name owner = new X500Name("CN=" + hostname + ", O=Mohican, L=Local, C=NL");

        info.set(X509CertInfo.VALIDITY, interval);
        info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(serialNumber));
        info.set(X509CertInfo.SUBJECT, owner);
        info.set(X509CertInfo.ISSUER, owner);
        info.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
        info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));

        AlgorithmId algo = AlgorithmId.get("SHA256withRSA");
        info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(algo));

        // Add Subject Alternative Names (SAN) for localhost
        GeneralNames generalNames = new GeneralNames();
        generalNames.add(new GeneralName(new DNSName(hostname)));
        generalNames.add(new GeneralName(new DNSName("127.0.0.1")));
        generalNames.add(new GeneralName(new IPAddressName("127.0.0.1")));

        SubjectAlternativeNameExtension sanExtension = new SubjectAlternativeNameExtension(generalNames);
        CertificateExtensions extensions = new CertificateExtensions();
        extensions.set(SubjectAlternativeNameExtension.NAME, sanExtension);

        // Add Basic Constraints extension (CA:FALSE for end-entity certificate)
        BasicConstraintsExtension basicConstraints = new BasicConstraintsExtension(false, false, 0);
        extensions.set(BasicConstraintsExtension.NAME, basicConstraints);

        info.set(X509CertInfo.EXTENSIONS, extensions);

        // Sign the certificate
        X509CertImpl cert = new X509CertImpl(info);
        cert.sign(privateKey, "SHA256withRSA");

        // Update algorithm info after signing
        algo = (AlgorithmId) cert.get(X509CertImpl.SIG_ALG);
        info.set(CertificateAlgorithmId.NAME + "." + CertificateAlgorithmId.ALGORITHM, algo);
        cert = new X509CertImpl(info);
        cert.sign(privateKey, "SHA256withRSA");

        return cert;
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
