package com.digitalpetri.halcyon.examples;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.digitalpetri.halcyon.api.Connection;
import com.digitalpetri.halcyon.api.ConnectionContext;
import com.digitalpetri.opcua.server.api.MonitoredItem;
import com.digitalpetri.opcua.server.api.Reference;
import com.digitalpetri.opcua.server.api.nodes.Node;
import com.digitalpetri.opcua.server.api.nodes.UaNode;
import com.digitalpetri.opcua.server.api.nodes.UaObjectNode;
import com.digitalpetri.opcua.server.api.nodes.UaVariableNode;
import com.digitalpetri.opcua.server.util.SubscriptionModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.typesafe.config.Config;
import org.opcfoundation.ua.builtintypes.*;
import org.opcfoundation.ua.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleConnection implements Connection {

    private static final NodeId[] DataTypes = new NodeId[]{
            Identifiers.Boolean,
            Identifiers.Int16,
            Identifiers.Int32,
            Identifiers.Int64,
            Identifiers.UInt16,
            Identifiers.UInt32,
            Identifiers.UInt64,
            Identifiers.Float,
            Identifiers.Double,
            Identifiers.String
    };

    private static final DataValue[] InitialValues = new DataValue[]{
            v(true),
            v(16),
            v(32),
            v(64),
            v(new UnsignedShort(16)),
            v(new UnsignedInteger(32)),
            v(new UnsignedLong(64)),
            v(3.14f),
            v(6.28d),
            v("Hello, world!")
    };

    private static DataValue v(Object o) {
        return new DataValue(new Variant(o), StatusCode.GOOD, DateTime.currentTime(), DateTime.currentTime());
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();

    private final SubscriptionModel subscriptionModel;
    private final ConnectionContext context;

    public ExampleConnection(ConnectionContext context, Config config) {
        this.context = context;

        subscriptionModel = new SubscriptionModel(this, context.getExecutorService());

        int myExampleProperty = config.getInt("example.my-example-property");
        logger.info("my-example-property={}", myExampleProperty);

        /*
         * Create and add the root folder.
         */

        UaObjectNode rootFolder = UaObjectNode.builder()
                .setNodeId(context.getRootNodeId())
                .setBrowseName(context.qualifiedName(context.getName()))
                .setDisplayName(LocalizedText.english(context.getName()))
                .setTypeDefinition(Identifiers.FolderType)
                .build();

        nodes.put(rootFolder.getNodeId(), rootFolder);

        /*
         * Create and add some example nodes, making sure to create references from root.
         */

        for (int i = 0; i < 10; i++) {
            String nodeId = "Node" + i;

            UaVariableNode node = UaVariableNode.builder()
                    .setNodeId(context.nodeId(nodeId))
                    .setBrowseName(context.qualifiedName(nodeId))
                    .setDisplayName(LocalizedText.english(nodeId))
                    .setDataType(DataTypes[i])
                    .setValue(InitialValues[i])
                    .setAccessLevel(AccessLevel.getMask(AccessLevel.READWRITE))
                    .setUserAccessLevel(AccessLevel.getMask(AccessLevel.READWRITE))
                    .setMinimumSamplingInterval(100.0)
                    .build();

            Reference reference = new Reference(
                    rootFolder.getNodeId(),
                    Identifiers.Organizes,
                    new ExpandedNodeId(node.getNodeId()),
                    node.getNodeClass(),
                    true
            );

            rootFolder.addReference(reference);

            nodes.put(node.getNodeId(), node);
        }
    }

    @Override
    public ConnectionContext getContext() {
        return context;
    }

    @Override
    public boolean containsNodeId(NodeId nodeId) {
        return nodes.containsKey(nodeId);
    }

    @Override
    public Optional<Node> getNode(NodeId nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<List<Reference>> getReferences(NodeId nodeId) {
        Optional<UaNode> node = Optional.ofNullable(nodes.get(nodeId));

        return node.map(n -> Optional.of(n.getReferences())).orElse(Optional.empty());
    }

    @Override
    public void read(List<ReadValueId> readValueIds,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     CompletableFuture<List<DataValue>> future) {

        List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

        for (ReadValueId id : readValueIds) {
            DataValue value = getNode(id.getNodeId())
                    .map(node -> node.readAttribute(id.getAttributeId()))
                    .orElse(new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown)));

            results.add(value);
        }

        logger.info("Read {} values.", readValueIds.size());
        future.complete(results);
    }

    @Override
    public void write(List<WriteValue> writeValues,
                      CompletableFuture<List<StatusCode>> future) {

        List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

        for (WriteValue value : writeValues) {
            Optional<UaNode> node = Optional.ofNullable(nodes.get(value.getNodeId()));

            StatusCode result = node.map(n -> {
                if (n instanceof UaVariableNode) {
                    ((UaVariableNode) n).setValue(value.getValue());
                    return StatusCode.GOOD;
                } else {
                    return new StatusCode(StatusCodes.Bad_NotWritable);
                }
            }).orElse(new StatusCode(StatusCodes.Bad_NodeIdUnknown));

            results.add(result);
        }

        logger.info("Wrote {} values.", writeValues.size());
        future.complete(results);
    }

    @Override
    public void onMonitoredItemsCreated(List<MonitoredItem> monitoredItems) {
        monitoredItems.stream().forEach(item -> {
            if (item.getSamplingInterval() < 100) item.setSamplingInterval(100.0);
        });

        logger.info("onMonitoredItemsCreated({} items)", monitoredItems.size());
        subscriptionModel.onMonitoredItemsCreated(monitoredItems);
    }

    @Override
    public void onMonitoredItemsModified(List<MonitoredItem> monitoredItems) {
        monitoredItems.stream().forEach(item -> {
            if (item.getSamplingInterval() < 100) item.setSamplingInterval(100.0);
        });

        logger.info("onMonitoredItemsModified({} items)", monitoredItems.size());
        subscriptionModel.onMonitoredItemsModified(monitoredItems);
    }

    @Override
    public void onMonitoredItemsDeleted(List<MonitoredItem> monitoredItems) {
        logger.info("onMonitoredItemsDeleted({} items)", monitoredItems.size());
        subscriptionModel.onMonitoredItemsDeleted(monitoredItems);
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        logger.info("onMonitoringModeChanged({} items)", monitoredItems.size());
        subscriptionModel.onMonitoringModeChanged(monitoredItems);
    }

}
