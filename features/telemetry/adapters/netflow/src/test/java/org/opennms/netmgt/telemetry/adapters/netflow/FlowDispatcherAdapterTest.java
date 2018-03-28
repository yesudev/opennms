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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessage;
import org.opennms.netmgt.telemetry.adapters.api.TelemetryMessageLog;
import org.opennms.netmgt.telemetry.adapters.netflow.ipfix.IpfixAdapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.Netflow5Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v9.Netflow9Adapter;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.ByteStreams;

public class FlowDispatcherAdapterTest {

    private FlowRepository flowRepository;
    private FlowDispatcherAdapter flowDispatcherAdapter;

    @Before
    public void setUp() {
        this.flowRepository = EasyMock.niceMock(FlowRepository.class);
        this.flowDispatcherAdapter = new FlowDispatcherAdapter(new Netflow5Adapter(new MetricRegistry(), flowRepository),
                new Netflow9Adapter(new MetricRegistry(), flowRepository),
                new IpfixAdapter(new MetricRegistry(), flowRepository));
    }


    @Test
    public void verifyGetAdapter() throws IOException {
        final Object[][] data = new Object[][] {
                {"/flows/netflow5.dat", Netflow5Adapter.class},
                {"/flows/netflow9_cisco_asr1001x_tpl259.dat", Netflow9Adapter.class},
                {"/flows/ipfix.dat", IpfixAdapter.class}
        };

        for (Object[] eachData : data) {
            try (final InputStream is = getClass().getResourceAsStream((String)eachData[0])) {
                byte[] bytes = ByteStreams.toByteArray(is);
                final Adapter adapter = flowDispatcherAdapter.getAdapter(bytes);
                assertEquals(adapter.getClass(), eachData[1]);
            }
        }
    }

    @Test
    public void verifyDispatching() {
        final TelemetryMessageLog messageLog = new TelemetryMessageLog() {
            @Override
            public String getLocation() {
                return "Default";
            }

            @Override
            public String getSystemId() {
                return UUID.randomUUID().toString();
            }

            @Override
            public int getSourcePort() {
                return 51234;
            }

            @Override
            public String getSourceAddress() {
                return InetAddressUtils.getLocalHostName();
            }

            @Override
            public List<? extends TelemetryMessage> getMessageList() {
                final List<TelemetryMessage> messageList = new ArrayList<>();
                messageList.add(createMessage("/flows/netflow5.dat"));
                messageList.add(createMessage("/flows/netflow9_cisco_asr1001x_tpl259.dat"));
                messageList.add(createMessage("/flows/ipfix.dat"));
                return messageList;
            }
        };
        final FlowRepository flowRepository = EasyMock.niceMock(FlowRepository.class);
        final FlowDispatcherAdapter flowDispatcherAdapter = new FlowDispatcherAdapter(new Netflow5Adapter(new MetricRegistry(), flowRepository),
                new Netflow9Adapter(new MetricRegistry(), flowRepository),
                new IpfixAdapter(new MetricRegistry(), flowRepository));
        final Map<Adapter, TelemetryMessageLog> aggregate = flowDispatcherAdapter.aggregate(messageLog);
        assertEquals(3, aggregate.size());

        // Verify that the message log is created properly
        for (TelemetryMessageLog eachLog : aggregate.values()) {
            assertEquals(1, eachLog.getMessageList().size());
            assertEquals(messageLog.getLocation(), eachLog.getLocation());
            assertEquals(messageLog.getSourcePort(), eachLog.getSourcePort());
            assertEquals(messageLog.getSourceAddress(), eachLog.getSourceAddress());
            assertEquals(messageLog.getSystemId(), eachLog.getSystemId());
        }
    }

    private static TelemetryMessage createMessage(String resource) {
        return new TelemetryMessage() {
            @Override
            public long getTimestamp() {
                return System.currentTimeMillis();
            }

            @Override
            public byte[] getByteArray() {
                try {
                    return ByteStreams.toByteArray(getClass().getResourceAsStream(resource));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}