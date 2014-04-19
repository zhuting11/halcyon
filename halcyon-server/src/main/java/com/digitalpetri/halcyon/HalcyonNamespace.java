package com.digitalpetri.halcyon;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.digitalpetri.opcua.server.api.MonitoredItem;
import com.digitalpetri.opcua.server.api.Namespace;
import com.digitalpetri.opcua.server.api.Reference;
import com.digitalpetri.opcua.server.api.nodes.Node;
import com.digitalpetri.opcua.server.api.nodes.UaNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.StatusCode;
import org.opcfoundation.ua.core.Attributes;
import org.opcfoundation.ua.core.ReadValueId;
import org.opcfoundation.ua.core.StatusCodes;
import org.opcfoundation.ua.core.TimestampsToReturn;
import org.opcfoundation.ua.core.WriteValue;

public class HalcyonNamespace implements Namespace {

    private final HalcyonNamespaceModel model = new HalcyonNamespaceModel();
    private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();

    private final String namespaceUri;

    public HalcyonNamespace(String namespaceUri) {
        this.namespaceUri = namespaceUri;
    }

    @Override
    public int getNamespaceIndex() {
        return 1;
    }

    @Override
    public String getNamespaceUri() {
        return namespaceUri;
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
        UaNode node = nodes.get(nodeId);

        if (node != null) {
            return Optional.of(node.getReferences());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public void read(List<ReadValueId> readValueIds,
                     Double maxAge, TimestampsToReturn timestamps,
                     CompletableFuture<List<DataValue>> future) {

        List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

        for (ReadValueId id : readValueIds) {
            if (id.getAttributeId().equals(Attributes.Value)) {
                results.add(model.readValue(id.getNodeId()));
            } else {
                UaNode node = nodes.get(id.getNodeId());

                DataValue value = (node != null) ?
                        node.readAttribute(id.getAttributeId()) :
                        new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown));

                results.add(value);
            }
        }

        future.complete(results);
    }

    @Override
    public void write(List<WriteValue> writeValues, CompletableFuture<List<StatusCode>> future) {

    }

    @Override
    public void onMonitoredItemsCreated(List<MonitoredItem> monitoredItems) {

    }

    @Override
    public void onMonitoredItemsModified(List<MonitoredItem> monitoredItems) {

    }

    @Override
    public void onMonitoredItemsDeleted(List<MonitoredItem> monitoredItems) {

    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {

    }

}
