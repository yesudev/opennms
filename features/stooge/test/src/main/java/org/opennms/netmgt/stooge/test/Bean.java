/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2017-2017 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2017 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.stooge.test;

import org.opennms.core.ipc.sink.api.MessageConsumer;
import org.opennms.core.ipc.sink.api.MessageConsumerManager;
import org.opennms.core.ipc.sink.api.SinkModule;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.stooge.api.TransactionOperations;
import org.opennms.netmgt.telemetry.config.model.Protocol;
import org.opennms.netmgt.telemetry.ipc.TelemetryProtos;
import org.opennms.netmgt.telemetry.ipc.TelemetrySinkModule;
import org.opennms.netmgt.telemetry.listeners.api.TelemetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Bean implements MessageConsumer<TelemetryMessage, TelemetryProtos.TelemetryMessageLog> {
    private static final Logger LOG = LoggerFactory.getLogger(Bean.class);

    private NodeDao nodeDao;
    private DistPollerDao distPollerDao;
    private TransactionOperations transactionOperations;
    private MessageConsumerManager messageConsumerManager;
    private TelemetrySinkModule sinkModule;

    public void init() throws Exception {
        LOG.info("init");
        transactionOperations.doInTransactionWithoutResult(() -> {
            final List<OnmsNode> nodes = nodeDao.findAll();
            if (nodes.isEmpty()) {
                LOG.warn("No nodes found.");
            }
            for (OnmsNode node : nodes) {
                LOG.info("Found node: {}", node);
            }
        });

        Protocol protocol = new Protocol();
        protocol.setName("TEST");
        sinkModule = new TelemetrySinkModule(protocol);
        sinkModule.setDistPollerDao(distPollerDao);
        messageConsumerManager.registerConsumer(this);

        LOG.info("Started consumer for: {}", sinkModule);
    }

    public void destroy() {
        LOG.info("destroy");
    }

    public NodeDao getNodeDao() {
        return nodeDao;
    }

    public void setNodeDao(NodeDao nodeDao) {
        this.nodeDao = nodeDao;
    }

    public DistPollerDao getDistPollerDao() {
        return distPollerDao;
    }

    public void setDistPollerDao(DistPollerDao distPollerDao) {
        this.distPollerDao = distPollerDao;
    }

    public TransactionOperations getTransactionOperations() {
        return transactionOperations;
    }

    public void setTransactionOperations(TransactionOperations transactionOperations) {
        this.transactionOperations = transactionOperations;
    }

    public MessageConsumerManager getMessageConsumerManager() {
        return messageConsumerManager;
    }

    public void setMessageConsumerManager(MessageConsumerManager messageConsumerManager) {
        this.messageConsumerManager = messageConsumerManager;
    }

    @Override
    public SinkModule<TelemetryMessage, TelemetryProtos.TelemetryMessageLog> getModule() {
        return sinkModule;
    }

    @Override
    public void handleMessage(TelemetryProtos.TelemetryMessageLog messageLog) {
        LOG.warn("Got message: {}", messageLog);
    }

}
