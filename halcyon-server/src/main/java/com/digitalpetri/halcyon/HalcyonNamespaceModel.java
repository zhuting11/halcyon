package com.digitalpetri.halcyon;

import org.opcfoundation.ua.builtintypes.DataValue;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.Variant;

public class HalcyonNamespaceModel {

    public DataValue readValue(NodeId nodeId) {
        return new DataValue(Variant.NULL);
    }

}
