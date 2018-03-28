/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018-2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.telemetry.adapters.netflow;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessage;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessageLog;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.proto.Utils;
import org.opennms.netmgt.telemetry.config.api.Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlowDispatcherAdapter implements Adapter {

    public static class FlowVersionSpecificMessageLog implements TelemetryMessageLog {

        private final TelemetryMessageLog delegate;
        private final List<TelemetryMessage> messages = new ArrayList<>();

        public FlowVersionSpecificMessageLog(TelemetryMessageLog log) {
            this.delegate = log;
        }

        @Override
        public String getLocation() {
            return delegate.getLocation();
        }

        @Override
        public String getSystemId() {
            return delegate.getSystemId();
        }

        @Override
        public int getSourcePort() {
            return delegate.getSourcePort();
        }

        @Override
        public String getSourceAddress() {
            return delegate.getSourceAddress();
        }

        @Override
        public List<? extends TelemetryMessage> getMessageList() {
            return messages;
        }

        public void addMessage(TelemetryMessage message) {
            messages.add(message);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Adapter netflow9Adapter;
    private final Adapter netflow5Adapter;
    private final Adapter ipfixAdapter;

    public FlowDispatcherAdapter(final Adapter netflow5Adapter,
                                 final Adapter netflow9Adapter,
                                 final Adapter ipfixAdapter) {
        this.netflow5Adapter = Objects.requireNonNull(netflow5Adapter);
        this.netflow9Adapter = Objects.requireNonNull(netflow9Adapter);
        this.ipfixAdapter = Objects.requireNonNull(ipfixAdapter);
    }

    @Override
    public void setProtocol(Protocol protocol) {
        // we do not need the protocol
    }

    @Override
    public void handleMessageLog(TelemetryMessageLog messageLog) {
        if (!messageLog.getMessageList().isEmpty()) {
            logger.debug("Received flows. Determine concrete adapters to dispatch");

            // Assign messages from log to adapters to dispatch to
            final Map<Adapter, TelemetryMessageLog> dispatcherMap = aggregate(messageLog);

            // now dispatch
            for (Adapter adapter : dispatcherMap.keySet()) {
                final TelemetryMessageLog versionSpecificMessageLog = dispatcherMap.get(adapter);
                logger.debug("{}: dispatching {}/{} messages", adapter.getClass(), versionSpecificMessageLog.getMessageList().size(), messageLog.getMessageList());
                adapter.handleMessageLog(versionSpecificMessageLog);
            }
        } else {
            logger.debug("Received empty flows. Nothing to do");
        }
    }

    protected Map<Adapter, TelemetryMessageLog> aggregate(final TelemetryMessageLog messageLog) {
        final Map<Adapter, TelemetryMessageLog> dispatcherMap = new HashMap<>();
        for (TelemetryMessage message : messageLog.getMessageList()) {
            try {
                final byte[] bytes = message.getByteArray();
                final Adapter adapter = getAdapter(bytes);
                logger.debug("Dispatching flows to {}", adapter.getClass());

                // Ensure mapping exists
                if (!dispatcherMap.containsKey(adapter)) {
                    dispatcherMap.put(adapter, new FlowVersionSpecificMessageLog(messageLog));
                }

                // Add new message to log
                ((FlowVersionSpecificMessageLog) dispatcherMap.get(adapter)).addMessage(message);
            } catch (IllegalArgumentException ex) {
                logger.error("Received flow with invalid version information. Cannot dispatch to concrete adapter. Dropping packet", ex);
            }
        }
        return dispatcherMap;
    }

    protected Adapter getAdapter(int version) {
        logger.debug("Determined concrete adapter for version: {}", version);
        // determine where to dispatch to
        switch(version) {
            case 5:  return netflow5Adapter;
            case 9:  return netflow9Adapter;
            case 10: return ipfixAdapter;
            default:
                throw new IllegalArgumentException("Invalid netflow version " + version);
        }

    }

    protected Adapter getAdapter(byte[] bytes) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        int version = Utils.getInt(0, 1, byteBuffer, 0);
        if (version == 0) {
            logger.debug("Received version is {}. Assuming sflow. Reading next 2 bytes to verify.", version);
            version = Utils.getInt(0, 1, byteBuffer, 1);
            if (version != 5) {
                throw new IllegalArgumentException("Invalid sflow version " + version);
            }
            throw new UnsupportedOperationException("The sflow adapter is not yet implemented");
        }
        return getAdapter(version);
    }
}
