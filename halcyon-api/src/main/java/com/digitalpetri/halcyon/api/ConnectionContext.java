/*
 * Halcyon OPC-UA Server
 *
 * Copyright (C) 2014 Kevin Herron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.digitalpetri.halcyon.api;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

import com.codahale.metrics.MetricRegistry;
import com.typesafe.config.Config;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import org.opcfoundation.ua.builtintypes.NodeId;
import org.opcfoundation.ua.builtintypes.QualifiedName;

public interface ConnectionContext {

    /**
     * @return The name assigned to this connection.
     */
    String getName();

    /**
     * @return The {@link com.typesafe.config.Config} for this connection.
     */
    Config getConfig();

    /**
     * @return The {@link NodeId} to use as the root folder of this connection.
     */
    NodeId getRootNodeId();

    /**
     * Returns a shared {@link EventLoopGroup}.
     * <p>
     * If your connector isn't using netty for networking... well... you should consider it.
     *
     * @return The shared {@link EventLoopGroup}.
     */
    EventLoopGroup getEventLoop();

    /**
     * @return a shared {@link ExecutorService}.
     */
    ExecutorService getExecutorService();

    /**
     * Get a shared {@link ScheduledExecutorService}. Should only be used for tasks that <b>must</b> be scheduled.
     * <p>
     * For task execution that does not require scheduling see {@link #getExecutorService()}.
     *
     * @return a shared {@link ScheduledExecutorService}.
     */
    ScheduledExecutorService getScheduledExecutorService();

    /**
     * @return The shared {@link MetricRegistry}.
     */
    MetricRegistry getMetricRegistry();

    /**
     * Returns a shared {@link HashedWheelTimer}.
     * <p>
     * If your connector isn't using netty for networking... well... you should consider it.
     *
     * @return The shared {@link HashedWheelTimer}.
     */
    HashedWheelTimer getWheelTimer();

    /**
     * A convenience method for creating {@link NodeId}s that belong to this
     * connection.
     * <p>
     * The {@link NodeId} is built by prefixing the supplied value with
     * "[{@link #getName()}]".
     *
     * @param value The value of the {@link NodeId}, before the prefix is applied.
     * @return A {@link NodeId} suitable for use as representing a node belonging to
     * this connection.
     * @see {@link ConnectionContext#CONNECTION_PREFIX_PATTERN}
     */
    NodeId nodeId(Object value);

    /**
     * A convenience method for creating {@link QualifiedName}s that belong to the connection namespace.
     *
     * @param s The String to create the {@link QualifiedName} with.
     * @return A {@link QualifiedName} belonging to the connection namespace.
     */
    QualifiedName qualifiedName(String s);

    /**
     * @return The namespace index assigned to all connections.
     */
    int getNamespaceIndex();

    /**
     * A {@link Pattern} that will match on Strings starting with a connection name surrounded by square brackets.
     * <p>
     * Intended for use matching and extracting connection names from {@link NodeId} values.
     * <p>
     * Group 1 contains the connection name, group 2 contains the rest of the NodeId without the square brackets prefix.
     */
    public static final Pattern CONNECTION_PREFIX_PATTERN = Pattern.compile("^\\[(.+?)\\](.*)");

}
