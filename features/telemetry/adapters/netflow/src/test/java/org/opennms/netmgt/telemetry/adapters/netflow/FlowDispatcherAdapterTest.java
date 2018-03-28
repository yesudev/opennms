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

import org.easymock.EasyMock;
import org.junit.Test;
import org.opennms.netmgt.flows.api.FlowRepository;
import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.ipfix.IpfixAdapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.Netflow5Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v9.Netflow9Adapter;

import com.codahale.metrics.MetricRegistry;
import com.google.common.io.ByteStreams;

public class FlowDispatcherAdapterTest {

    @Test
    public void verifyDispatching() throws IOException {
        final FlowRepository flowRepository = EasyMock.niceMock(FlowRepository.class);
        final FlowDispatcherAdapter flowDispatcherAdapter = new FlowDispatcherAdapter(new Netflow5Adapter(new MetricRegistry(), flowRepository),
                new Netflow9Adapter(new MetricRegistry(), flowRepository),
                new IpfixAdapter(new MetricRegistry(), flowRepository));

        final Object[][] data = new Object[][]{
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
}