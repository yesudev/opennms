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

import java.util.Map;
import java.util.Objects;

import org.opennms.netmgt.telemetry.adapters.api.Adapter;
import org.opennms.netmgt.telemetry.adapters.api.AdapterFactory;
import org.opennms.netmgt.telemetry.adapters.netflow.ipfix.IpfixAdapter;
import org.opennms.netmgt.telemetry.adapters.netflow.ipfix.IpfixAdapterFactory;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.Netflow5Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v5.Netflow5AdapterFactory;
import org.opennms.netmgt.telemetry.adapters.netflow.v9.Netflow9Adapter;
import org.opennms.netmgt.telemetry.adapters.netflow.v9.Netflow9AdapterFactory;
import org.opennms.netmgt.telemetry.config.api.Protocol;

public class FlowDispatcherAdapterFactory implements AdapterFactory {

    private Netflow9AdapterFactory netflow9AdapterFactory;
    private Netflow5AdapterFactory netflow5AdapterFactory;
    private IpfixAdapterFactory ipfixAdapterFactory;

    public void setNetflow9AdapterFactory(Netflow9AdapterFactory netflow9AdapterFactory) {
        this.netflow9AdapterFactory = netflow9AdapterFactory;
    }

    public void setNetflow5AdapterFactory(Netflow5AdapterFactory netflow5AdapterFactory) {
        this.netflow5AdapterFactory = netflow5AdapterFactory;
    }

    public void setIpfixAdapterFactory(IpfixAdapterFactory ipfixAdapterFactory) {
        this.ipfixAdapterFactory = ipfixAdapterFactory;
    }

    @Override
    public Class<? extends Adapter> getAdapterClass() {
        return FlowDispatcherAdapter.class;
    }

    @Override
    public Adapter createAdapter(Protocol protocol, Map<String, String> properties) {
        Objects.requireNonNull(netflow5AdapterFactory);
        Objects.requireNonNull(netflow9AdapterFactory);
        Objects.requireNonNull(ipfixAdapterFactory);

        return new FlowDispatcherAdapter(
                (Netflow5Adapter) netflow5AdapterFactory.createAdapter(protocol, properties),
                (Netflow9Adapter) netflow9AdapterFactory.createAdapter(protocol, properties),
                (IpfixAdapter) ipfixAdapterFactory.createAdapter(protocol, properties));
    }
}
