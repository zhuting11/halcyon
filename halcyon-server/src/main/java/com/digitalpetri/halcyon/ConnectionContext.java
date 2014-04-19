package com.digitalpetri.halcyon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.HashedWheelTimer;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;

public class ConnectionContext implements com.digitalpetri.halcyon.api.ConnectionContext {

    private static final java.util.concurrent.ThreadFactory ThreadFactory = new ThreadFactory() {
        AtomicLong threadNumber = new AtomicLong(1L);

        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "halcyon-scheduler-thread-" + threadNumber.getAndIncrement());
        }
    };

    private static final int CorePoolSize = Runtime.getRuntime().availableProcessors() * 2;

    private static final ScheduledExecutorService ScheduledExecutor =
            Executors.newScheduledThreadPool(CorePoolSize, ThreadFactory);

    private static final NioEventLoopGroup EventLoop = new NioEventLoopGroup();
    private static final HashedWheelTimer WheelTimer = new HashedWheelTimer();


    private final String name;
    private final Config config;
    private final NodeId rootNodeId;
    private final ExecutorService executor;
    private final MetricRegistry metricRegistry;
    private final int namespaceIndex;

    public ConnectionContext(String name,
                             Config config,
                             NodeId rootNodeId,
                             ExecutorService executor,
                             MetricRegistry metricRegistry,
                             int namespaceIndex) {

        this.name = name;
        this.config = config;
        this.rootNodeId = rootNodeId;
        this.executor = executor;
        this.metricRegistry = metricRegistry;
        this.namespaceIndex = namespaceIndex;
    }

    @Override
    public NodeId nodeId(Object value) {
        return new NodeId(getNamespaceIndex(), String.format("[%s]%s", getName(), value));
    }

    @Override
    public QualifiedName qualifiedName(String s) {
        return new QualifiedName(namespaceIndex, s);
    }

    //region Getters
    @Override
    public String getName() {
        return name;
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public NodeId getRootNodeId() {
        return rootNodeId;
    }

    @Override
    public EventLoopGroup getEventLoop() {
        return EventLoop;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executor;
    }

    @Override
    public ScheduledExecutorService getScheduledExecutorService() {
        return ScheduledExecutor;
    }

    @Override
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    @Override
    public HashedWheelTimer getWheelTimer() {
        return WheelTimer;
    }

    @Override
    public int getNamespaceIndex() {
        return namespaceIndex;
    }
    //endregion

}
