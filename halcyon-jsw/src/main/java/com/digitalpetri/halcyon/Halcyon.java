package com.digitalpetri.halcyon;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.slf4j.LoggerFactory;

public class Halcyon {

    public static void main(String[] args) throws Exception {
        new Halcyon();
    }

    private final HalcyonServer server;

    public Halcyon() throws Exception {
        createDirectories();

        configureLog4j();
        configureLogback();

        Config config = ConfigFactory
                .parseFile(new File("../config/halcyon.conf"))
                .withFallback(ConfigFactory.load());

        server = new HalcyonServer(config);
        server.startup();

        shutdownFuture().get();
    }

    private CompletableFuture<Void> shutdownFuture() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            future.complete(null);
        }));

        return future;
    }

    private void createDirectories() throws Exception {
        File configDir = new File("../config/");
        if (!configDir.exists() && !configDir.mkdirs()) {
            throw new Exception("Could not create config directory.");
        }

        File logsDir = new File("../logs/");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            throw new Exception("Could not create logs directory.");
        }

        File connectionsDir = new File("../connections/");
        if (!connectionsDir.exists() && !connectionsDir.mkdirs()) {
            throw new Exception("Could not create connections directory.");
        }

        File securityDir = new File("../security/");
        if (!securityDir.exists() && !securityDir.mkdirs()) {
            throw new Exception("Could not create security directory.");
        }
    }

    private void configureLog4j() {
        // TODO This won't be necessary once the UA stack uses slf4j.
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.INFO);
    }

    private void configureLogback() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset();

            System.out.println(System.getProperty("user.dir"));

            File logbackXml = new File("../config/logback.xml");

            if (!logbackXml.exists()) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("config/logback.xml");
                Files.copy(is, logbackXml.toPath());
            }

            configurator.doConfigure(logbackXml);
        } catch (Exception e) {
            System.err.println("Error configuring logback." + e);
        }

        StatusPrinter.printInCaseOfErrorsOrWarnings(context);
    }

}
