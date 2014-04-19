package com.digitalpetri.halcyon;

import java.util.Calendar;
import java.util.List;
import java.util.Optional;

import com.digitalpetri.halcyon.util.ManifestUtil;
import com.digitalpetri.opcua.server.api.OpcUaServerConfig;
import com.typesafe.config.Config;
import org.opcfoundation.ua.builtintypes.DateTime;
import org.opcfoundation.ua.core.BuildInfo;
import org.opcfoundation.ua.utils.EndpointUtil;

public class HalcyonConfig implements OpcUaServerConfig {

    private static final String BuildDateProperty = "X-Halcyon-Build-Date";
    private static final String BuildNumberProperty = "X-Halcyon-Build-Number";
    private static final String SoftwareVersionProperty = "X-Halcyon-Version";

    private final Config config;

    public HalcyonConfig(Config config) {
        this.config = config;
    }

    @Override
    public List<String> getBindAddresses() {
        return config.getStringList("halcyon.bind-address-list");
    }

    @Override
    public int getBindPort() {
        return config.getInt("halcyon.bind-port");
    }

    @Override
    public String getHostname() {
        return Optional.ofNullable(System.getProperty("hostname")).orElseGet(() -> {
            try {
                return EndpointUtil.getHostname();
            } catch (Throwable t) {
                return "localhost";
            }
        });
    }

    @Override
    public BuildInfo getBuildInfo() {
        String productUri = "http://www.digitalpetri.com/halcyon";
        String manufacturerName = "digitalpetri";
        String productName = "Halcyon OPC-UA Server";
        String softwareVersion = ManifestUtil.read(SoftwareVersionProperty).orElse("dev");
        String buildNumber = ManifestUtil.read(BuildNumberProperty).orElse("dev");
        DateTime buildDate = ManifestUtil.read(BuildDateProperty).map((ts) -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(Long.valueOf(ts));
            return new DateTime(c);
        }).orElse(new DateTime());

        return new BuildInfo(
                productUri,
                manufacturerName,
                productName,
                softwareVersion,
                buildNumber,
                buildDate
        );
    }

}
