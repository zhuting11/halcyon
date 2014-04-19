package com.digitalpetri.halcyon;

import static com.digitalpetri.opcua.server.util.FutureUtils.sequence;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import com.digitalpetri.halcyon.api.Connection;
import com.digitalpetri.halcyon.api.ConnectionContext;
import com.digitalpetri.opcua.server.api.MonitoredItem;
import com.digitalpetri.opcua.server.api.Namespace;
import com.digitalpetri.opcua.server.api.Reference;
import com.digitalpetri.opcua.server.api.nodes.Node;
import com.digitalpetri.opcua.server.api.nodes.UaNode;
import com.digitalpetri.opcua.server.api.nodes.UaObjectNode;
import com.digitalpetri.opcua.server.util.Pending;
import com.digitalpetri.opcua.server.util.PendingRead;
import com.digitalpetri.opcua.server.util.PendingWrite;
import com.digitalpetri.opcua.server.util.SubscriptionModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.ExpandedNodeId;
import org.opcfoundation.ua.builtintypes.LocalizedText;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;
import org.opcfoundation.ua.builtintypes.StatusCode;
import org.opcfoundation.ua.common.ServerTable;
import org.opcfoundation.ua.common.ServiceResultException;
import org.opcfoundation.ua.core.Identifiers;
import org.opcfoundation.ua.core.NodeClass;
import org.opcfoundation.ua.core.ReadValueId;
import org.opcfoundation.ua.core.StatusCodes;
import org.opcfoundation.ua.core.TimestampsToReturn;
import org.opcfoundation.ua.core.WriteValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionNamespace implements Namespace {

    public static final String NAMESPACE_URI = "urn:digitalpetri:halcyon:connections";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();
    private final Map<String, Connection> connections = Maps.newConcurrentMap();

    private final SubscriptionModel subscriptionModel;
    private final NodeId connectionsNodeId;

    private final HalcyonServer server;
    private final int namespaceIndex;

    public ConnectionNamespace(HalcyonServer server, int namespaceIndex) {
        this.server = server;
        this.namespaceIndex = namespaceIndex;

        subscriptionModel = new SubscriptionModel(this, server.getServer().getExecutorService());

        connectionsNodeId = new NodeId(namespaceIndex, "Connections");

        UaNode folderNode = UaObjectNode.builder()
                .setNodeId(connectionsNodeId)
                .setBrowseName(new QualifiedName(namespaceIndex, "Connections"))
                .setDisplayName(LocalizedText.english("Connections"))
                .setTypeDefinition(Identifiers.FolderType)
                .build();

        nodes.put(folderNode.getNodeId(), folderNode);

        try {
            server.getServer().getUaNamespace().addReference(
                    Identifiers.ObjectsFolder,
                    Identifiers.Organizes,
                    true, ServerTable.DEFAULT.getUri(0),
                    new ExpandedNodeId(connectionsNodeId), NodeClass.Object);
        } catch (ServiceResultException e) {
            logger.error("Error adding reference to Connections folder.", e);
        }
    }

    @Override
    public int getNamespaceIndex() {
        return namespaceIndex;
    }

    @Override
    public String getNamespaceUri() {
        return NAMESPACE_URI;
    }

    @Override
    public boolean containsNodeId(NodeId nodeId) {
        return getNode(nodeId).isPresent();
    }

    @Override
    public Optional<Node> getNode(NodeId nodeId) {
        UaNode node = nodes.get(nodeId);

        if (node != null) {
            return Optional.of(node);
        } else {
            return connection(nodeId).flatMap(c -> c.getNode(nodeId));
        }
    }

    @Override
    public Optional<List<Reference>> getReferences(NodeId nodeId) {
        UaNode node = nodes.get(nodeId);

        if (node != null) {
            return Optional.of(node.getReferences());
        } else {
            return connection(nodeId).flatMap(c -> c.getReferences(nodeId));
        }
    }

    @Override
    public void read(List<ReadValueId> readValueIds,
                     Double maxAge,
                     TimestampsToReturn timestamps,
                     CompletableFuture<List<DataValue>> future) {

        List<PendingRead> pendingReads = readValueIds.stream()
                .map(PendingRead::new)
                .collect(Collectors.toList());

        Map<Optional<Connection>, List<PendingRead>> byConnection = pendingReads.stream()
                .collect(Collectors.groupingBy(p -> connection(p.getInput().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<PendingRead> pending = byConnection.get(connection);

            List<ReadValueId> ids = pending.stream()
                    .map(PendingRead::getInput)
                    .collect(Collectors.toList());

            CompletableFuture<List<DataValue>> callback = Pending.callback(pending);

            if (connection.isPresent()) {
                server.getServer().getExecutorService().execute(
                        () -> connection.get().read(ids, maxAge, timestamps, callback));
            } else {
                callback.complete(read(ids));
            }
        });

		/*
         * When all PendingReads have been completed complete the future we received with the values.
		 */

        List<CompletableFuture<DataValue>> futures = pendingReads.stream()
                .map(PendingRead::getFuture)
                .collect(Collectors.toList());

        sequence(futures).thenAcceptAsync(future::complete, server.getServer().getExecutorService());
    }

    private List<DataValue> read(List<ReadValueId> readValueIds) {
        return readValueIds.stream().map(id -> {
            NodeId nodeId = id.getNodeId();
            UaNode node = nodes.get(nodeId);

            if (node != null) {
                return node.readAttribute(id.getAttributeId());
            } else {
                return new DataValue(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
            }
        }).collect(Collectors.toList());
    }

    @Override
    public void write(List<WriteValue> writeValues, CompletableFuture<List<StatusCode>> future) {
        List<PendingWrite> pendingWrites = writeValues.stream()
                .map(PendingWrite::new)
                .collect(Collectors.toList());

        Map<Optional<Connection>, List<PendingWrite>> byConnection = pendingWrites.stream()
                .collect(Collectors.groupingBy(p -> connection(p.getInput().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<PendingWrite> pending = byConnection.get(connection);

            List<WriteValue> values = pending.stream()
                    .map(PendingWrite::getInput)
                    .collect(Collectors.toList());

            CompletableFuture<List<StatusCode>> callback = Pending.callback(pending);

            if (connection.isPresent()) {
                server.getServer().getExecutorService().execute(
                        () -> connection.get().write(values, callback));
            } else {
                callback.complete(write(values));
            }
        });

        List<CompletableFuture<StatusCode>> futures = pendingWrites.stream()
                .map(PendingWrite::getFuture)
                .collect(Collectors.toList());

        sequence(futures).thenAcceptAsync(future::complete, server.getServer().getExecutorService());
    }

    private List<StatusCode> write(List<WriteValue> values) {
        return Collections.nCopies(values.size(), new StatusCode(StatusCodes.Bad_NotWritable));
    }

    @Override
    public void onMonitoredItemsCreated(List<MonitoredItem> monitoredItems) {
        Map<Optional<Connection>, List<MonitoredItem>> byConnection = monitoredItems.stream()
                .collect(Collectors.groupingBy(item -> connection(item.getReadValueId().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<MonitoredItem> items = byConnection.get(connection);

            if (connection.isPresent()) {
                connection.get().onMonitoredItemsCreated(items);
            } else {
                subscriptionModel.onMonitoredItemsCreated(items);
            }
        });
    }

    @Override
    public void onMonitoredItemsModified(List<MonitoredItem> monitoredItems) {
        Map<Optional<Connection>, List<MonitoredItem>> byConnection = monitoredItems.stream()
                .collect(Collectors.groupingBy(item -> connection(item.getReadValueId().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<MonitoredItem> items = byConnection.get(connection);

            if (connection.isPresent()) {
                connection.get().onMonitoredItemsModified(items);
            } else {
                subscriptionModel.onMonitoredItemsModified(items);
            }
        });
    }

    @Override
    public void onMonitoredItemsDeleted(List<MonitoredItem> monitoredItems) {
        Map<Optional<Connection>, List<MonitoredItem>> byConnection = monitoredItems.stream()
                .collect(Collectors.groupingBy(item -> connection(item.getReadValueId().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<MonitoredItem> items = byConnection.get(connection);

            if (connection.isPresent()) {
                connection.get().onMonitoredItemsDeleted(items);
            } else {
                subscriptionModel.onMonitoredItemsDeleted(items);
            }
        });
    }

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
        Map<Optional<Connection>, List<MonitoredItem>> byConnection = monitoredItems.stream()
                .collect(Collectors.groupingBy(item -> connection(item.getReadValueId().getNodeId())));

        byConnection.keySet().forEach(connection -> {
            List<MonitoredItem> items = byConnection.get(connection);

            if (connection.isPresent()) {
                connection.get().onMonitoringModeChanged(items);
            } else {
                subscriptionModel.onMonitoringModeChanged(items);
            }
        });
    }

    private Optional<Connection> connection(NodeId nodeId) {
        String id = nodeId.getValue().toString();
        Matcher matcher = ConnectionContext.CONNECTION_PREFIX_PATTERN.matcher(id);
        boolean matches = matcher.matches();

        return matches ?
                Optional.ofNullable(connections.get(matcher.group(1))) :
                Optional.empty();
    }

    public void addConnection(Connection connection) {
        // Build the browse path nodes...
        List<String> browsePath = connection.getContext().getConfig().getStringList("connection.browse-path");
        List<UaNode> browsePathNodes = createNodes(browsePath, Lists.newArrayList(), Lists.newArrayList());

        browsePathNodes.stream().forEach(node -> nodes.putIfAbsent(node.getNodeId(), node));

        // Create references for all the nodes we built...
        UaNode connectionsFolder = nodes.get(connectionsNodeId);
        List<UaNode> ns = Lists.newArrayList(connectionsFolder);
        ns.addAll(browsePathNodes);
        List<Reference> references = createReferences(connection, ns, Lists.newArrayList());

        references.stream().forEach(reference -> {
            UaNode node = nodes.get(reference.getSourceNodeId());
            node.addReference(reference);
        });

        connections.put(connection.getContext().getName(), connection);
    }

    private List<UaNode> createNodes(List<String> browsePath, List<String> currentPath, List<UaNode> nodes) {
        if (browsePath.isEmpty()) {
            return nodes;
        } else {
            String element = browsePath.get(0);
            String nodeId = String.join("/", currentPath) + "/" + element;

            UaNode node = UaObjectNode.builder()
                    .setNodeId(new NodeId(getNamespaceIndex(), nodeId))
                    .setBrowseName(new QualifiedName(getNamespaceIndex(), element))
                    .setDisplayName(LocalizedText.english(element))
                    .setTypeDefinition(Identifiers.FolderType)
                    .build();

            browsePath.remove(0);
            currentPath.add(element);
            nodes.add(node);

            return createNodes(browsePath, currentPath, nodes);
        }
    }

    private List<Reference> createReferences(Connection connection, List<UaNode> nodes, List<Reference> references) {
        if (nodes.isEmpty()) {
            return references;
        } else if (nodes.size() == 1) {
            // The termination/special case: create a reference to the connection's root folder.
            Reference reference = new Reference(
                    nodes.get(0).getNodeId(),
                    Identifiers.Organizes,
                    new ExpandedNodeId(connection.getContext().getRootNodeId()),
                    NodeClass.Object,
                    true
            );

            references.add(reference);

            return references;
        } else {
            Node n1 = nodes.get(0);
            Node n2 = nodes.get(1);

            Reference reference = new Reference(
                    n1.getNodeId(),
                    Identifiers.Organizes,
                    new ExpandedNodeId(n2.getNodeId()),
                    n2.getNodeClass(),
                    true
            );

            references.add(reference);
            nodes.remove(0);

            return createReferences(connection, nodes, references);
        }
    }

}
