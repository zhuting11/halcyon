package com.digitalpetri.halcyon.examples;

import com.digitalpetri.halcyon.api.Connection;
import com.digitalpetri.halcyon.api.ConnectionContext;
import com.digitalpetri.halcyon.api.ConnectionType;
import com.typesafe.config.Config;

public class ExampleConnectionType implements ConnectionType {

    @Override
    public Connection createConnection(ConnectionContext context, Config config) throws Exception {
        return new ExampleConnection(context, config);
    }

}
