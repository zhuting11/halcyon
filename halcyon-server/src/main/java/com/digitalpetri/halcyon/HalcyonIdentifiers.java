package com.digitalpetri.halcyon;

import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.UnsignedInteger;

public class HalcyonIdentifiers {

    public static final NodeId HeapInit = init("Heap/Init");
    public static final NodeId HeapUsed = init("Heap/Used");
    public static final NodeId HeapCommitted = init("Heap/Committed");
    public static final NodeId HeapMax = init("Heap/Max");

    static NodeId init(String value) {
        return new NodeId(1, value);
    }

    static NodeId init(int value) {
        return new NodeId(1, UnsignedInteger.getFromBits(value));
    }
}
