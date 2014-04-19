package com.digitalpetri.halcyon;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;

import com.codahale.metrics.MetricRegistry;
import com.digitalpetri.halcyon.api.Connection;
import com.digitalpetri.halcyon.api.ConnectionType;
import com.digitalpetri.halcyon.util.KeyUtil;
import com.digitalpetri.opcua.server.OpcUaServer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.opcfoundation.ua.application.Application;
import org.opcfoundation.ua.builtintypes.LocalizedText;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.common.ServiceResultException;
import org.opcfoundation.ua.transport.security.CertificateValidator;
import org.opcfoundation.ua.transport.security.KeyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HalcyonServer {

    private static final MetricRegistry MetricRegistry = new MetricRegistry();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final OpcUaServer server;

    private final HalcyonNamespace halcyonNamespace;
    private final ConnectionNamespace connectionNamespace;

    public HalcyonServer(Config config) {
        HalcyonConfig halcyonConfig = new HalcyonConfig(config);

        Application application = new Application();

        KeyPair certificate = KeyUtil.getApplicationInstanceCertificate(config)
                .orElseGet(() -> KeyUtil.createApplicationInstanceCertificate(
                        config, applicationUri(halcyonConfig), halcyonConfig.getHostname()));

        application.addApplicationInstanceCertificate(certificate);
        application.setApplicationName(LocalizedText.english("Halcyon OPC-UA Server"));
        application.setApplicationUri(certificate.getCertificate());
        application.getOpctcpSettings().setCertificateValidator(CertificateValidator.ALLOW_ALL);
        application.getOpctcpSettings().setCertificateValidator(c -> {
            if (c != null) logger.debug("Validating certificate from: {}", c.getCertificate().getSubjectDN());
            return null;
        });

        server = new OpcUaServer(application, halcyonConfig);

        halcyonNamespace = server.getNamespaceManager().registerAndAdd(
                application.getApplicationUri(),
                (namespaceIndex) -> new HalcyonNamespace(application.getApplicationUri()));

        connectionNamespace = server.getNamespaceManager().registerAndAdd(
                ConnectionNamespace.NAMESPACE_URI,
                (namespaceIndex) -> new ConnectionNamespace(this, namespaceIndex));

        loadConnections();
    }

    public void startup() throws ServiceResultException {
        server.startup();
    }

    public void shutdown() {
        server.shutdown();
    }

    public OpcUaServer getServer() {
        return server;
    }

    private void loadConnections() {
        File connectionsDirectory = new File("../connections/");

        if (!connectionsDirectory.exists() && !connectionsDirectory.mkdirs()) {
            logger.error("Could not create connections directory.");
            return;
        }

        loadDirectory(connectionsDirectory);
    }

    private void loadDirectory(File directory) {
        /*
		 * Load any .conf files in this directory.
		 */
        File[] configFiles = directory.listFiles(pathname -> pathname.getPath().endsWith(".conf"));
        Arrays.stream(configFiles).forEach(file -> {
            try {
                Connection connection = load(file);

                connectionNamespace.addConnection(connection);
            } catch (Exception e) {
                logger.error("Error loading connection.", e);
            }
        });

		/*
		 * Recursively descend into subdirectories loading .conf files as we go.
		 */
        File[] dirs = directory.listFiles(File::isDirectory);
        Arrays.stream(dirs).forEach(this::loadDirectory);
    }

    private Connection load(File file) throws Exception {
        Config config = ConfigFactory.parseFile(file);
        ConnectionType connectionType = ConnectionType.class.cast(
                Class.forName(config.getString("connection.connection-type-class")).newInstance());

        String name = config.getString("connection.connection-name");

        ConnectionContext context = new ConnectionContext(
                name,
                config,
                new NodeId(connectionNamespace.getNamespaceIndex(), String.format("[%s]", name)),
                server.getExecutorService(),
                MetricRegistry,
                connectionNamespace.getNamespaceIndex()
        );

        return connectionType.createConnection(context, config);
    }

    private static String applicationUri(HalcyonConfig config) {
        return String.format("urn:%s:halcyon:%s", config.getHostname(), UUID.randomUUID());
    }

}
