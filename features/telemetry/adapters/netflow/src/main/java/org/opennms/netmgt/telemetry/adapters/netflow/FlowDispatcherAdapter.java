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
import java.util.Objects;

import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessage;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessageLog;
import org.opennms.netmgt.telemetry.adapters.netflow.ipfix.IpfixAdapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.Netflow5Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.proto.Utils;
import org.opennms.netmgt.telemetry.adapters.netflow.v9.Netflow9Adapter;
import org.opennms.netmgt.telemetry.config.api.Protocol;
import org.slf4j.LoggerFactory;

public class FlowDispatcherAdapter implements Adapter {

    private final Netflow9Adapter netflow9Adapter;
    private final Netflow5Adapter netflow5Adapter;
    private final IpfixAdapter ipfixAdapter;

    public FlowDispatcherAdapter(final Netflow5Adapter netflow5Adapter,
                                 final Netflow9Adapter netflow9Adapter,
                                 final IpfixAdapter ipfixAdapter) {
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
            LoggerFactory.getLogger(getClass()).debug("Received flows. Determine concrete adapter to dispatch");

            // get 1st message and peak
            final TelemetryMessage message = messageLog.getMessageList().get(0);
            final byte[] bytes = message.getByteArray();

            try {
                final Adapter adapter = getAdapter(bytes);
                LoggerFactory.getLogger(getClass()).debug("Dispatching flows to {}", adapter.getClass());
                adapter.handleMessageLog(messageLog);
            } catch (IllegalArgumentException ex) {
                LoggerFactory.getLogger(getClass()).error("Received flow with invalid version information. Cannot dispatch to concrete adapter. Dropping packet", ex);
            }
        } else {
            LoggerFactory.getLogger(getClass()).debug("Received empty flows. Nothing to do");
        }
    }

    protected Adapter getAdapter(int version) {
        LoggerFactory.getLogger(getClass()).debug("Determined concrete adapter for version: {}", version);
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
            LoggerFactory.getLogger(getClass()).debug("Received version is {}. Assuming sflow. Reading next 2 bytes to verify.", version);
            version = Utils.getInt(0, 1, byteBuffer, 1);
            if (version != 5) {
                throw new IllegalArgumentException("Invalid sflow version " + version);
            }
            throw new UnsupportedOperationException("The sflow adapter is not yet implemented");
        }
        return getAdapter(version);
    }
}
