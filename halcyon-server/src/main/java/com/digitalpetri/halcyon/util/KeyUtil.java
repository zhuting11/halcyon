package com.digitalpetri.halcyon.util;

import java.io.File;
import java.util.Optional;

import com.typesafe.config.Config;
import org.opcfoundation.ua.transport.security.KeyPair;
import org.opcfoundation.ua.utils.CertificateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyUtil {

    private static final Logger logger = LoggerFactory.getLogger(KeyUtil.class);

    /**
     * Get the application instance certificate and private key from the keystore file identified by the
     * `halcyon.security.keystore-file` property under the alias identified by `halcyon.security.keystore-ai-alias`.
     * <p>
     * The password to the keystore is the value of `halcyon.security.keystore-password` and the password for the alias
     * is the value of `halcyon.security.keystore-ai-password`.
     *
     * @return The {@link KeyPair} containing the application instance certificate and private key.
     */
    public static Optional<KeyPair> getApplicationInstanceCertificate(Config config) {
        try {
            String keystoreFile = config.getString("halcyon.security.keystore-file");
            String keystorePassword = config.getString("halcyon.security.keystore-password");
            String aiAliasName = config.getString("halcyon.security.keystore-ai-alias");
            String aiAliasPassword = config.getString("halcyon.security.keystore-ai-password");

            KeyPair keyPair = CertificateUtils.loadKeyPairFromProtectedStore(
                    keystoreFile, aiAliasName, keystorePassword, aiAliasPassword);

            return Optional.of(keyPair);
        } catch (Exception ex) {
            logger.warn("Certificate could not be loaded: " + ex.getMessage());

            return Optional.empty();
        }
    }

    /**
     * Create an application instance certificate and private key in the keystore file identified by the
     * `halcyon.security.keystore-file` property under the alias identified by `halcyon.security.keystore-ai-alias`.
     * <p>
     * The password to the keystore is the value of `halcyon.security.keystore-password` and the password for the alias
     * is the value of `halcyon.security.keystore-ai-password`.
     *
     * @param applicationUri The application URI to use in the certificate.
     * @param hostname       The hostname to use in the certificate.
     * @return The newly created {@link KeyPair} containing the application instance certificate and private key.
     */
    public static KeyPair createApplicationInstanceCertificate(Config config, String applicationUri, String hostname) {
        try {
            KeyPair keys = CertificateUtils.createApplicationInstanceCertificate(
                    "halcyon", null, applicationUri, 3650, hostname
            );

            String keystoreFile = config.getString("halcyon.security.keystore-file");
            String keystorePassword = config.getString("halcyon.security.keystore-password");
            String aiAliasName = config.getString("halcyon.security.keystore-ai-alias");
            String aiAliasPassword = config.getString("halcyon.security.keystore-ai-password");

            // Create any parent directories leading to the file...
            File f = new File(keystoreFile).getParentFile();
            if (f != null && !f.exists() && !f.mkdirs()) {
                throw new Exception("unable to access path: " + f.getPath());
            }

            CertificateUtils.saveKeyPairToProtectedStore(
                    keys, keystoreFile, aiAliasName, keystorePassword, aiAliasPassword
            );

            return keys;
        } catch (Exception ex) {
            logger.error("Error saving KeyPair.", ex);

            throw new RuntimeException(ex);
        }
    }

}
