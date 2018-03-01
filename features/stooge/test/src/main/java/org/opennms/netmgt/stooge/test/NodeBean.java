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

package org.opennms.netmgt.stooge.test;

import java.util.List;

import org.opennms.netmgt.dao.api.AlarmDao;
import org.opennms.netmgt.dao.api.DistPollerDao;
import org.opennms.netmgt.dao.api.MonitoringLocationDao;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.events.api.EventConstants;
import org.opennms.netmgt.model.OnmsAlarm;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.OnmsSeverity;
import org.opennms.netmgt.model.monitoringLocations.OnmsMonitoringLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

public class NodeBean {

    private static final Logger LOG = LoggerFactory.getLogger(NodeBean.class);
    private final NodeDao nodeDao;
    private final AlarmDao alarmDao;
    private final DistPollerDao distPollerDao;
    private final TransactionOperations transactionOperations;

    public NodeBean(NodeDao nodeDao, AlarmDao alarmDao, DistPollerDao distPollerDao, TransactionOperations transactionOperations) {
        this.nodeDao = nodeDao;
        this.transactionOperations = transactionOperations;
        this.alarmDao = alarmDao;
        this.distPollerDao = distPollerDao;
    }

    public void init() throws Exception {
        LOG.info("init");

        // Create a node
        LOG.info("Creating a new node");
        final int nodeId = transactionOperations.execute(status -> {
            OnmsNode node = new OnmsNode();
            node.setLocation(new OnmsMonitoringLocation(MonitoringLocationDao.DEFAULT_MONITORING_LOCATION_ID, MonitoringLocationDao.DEFAULT_MONITORING_LOCATION_ID));
            node.setLabel("dummy");
            return nodeDao.save(node);
        });
        LOG.info("New node created");

        // Query nodes
        LOG.info("Query nodes");
        transactionOperations.execute((status) -> {
            final List<OnmsNode> nodes = nodeDao.findAll();
            if (nodes.isEmpty()) {
                LOG.warn("No nodes found.");
            }
            for (OnmsNode node : nodes) {
                LOG.info("Found node: {}", node);
            }
            return null;
        });

        // Delete node
        LOG.info("Delete node with id {}", nodeId);
        transactionOperations.execute(status -> {
            nodeDao.delete(nodeId);
            return null;
        });
        LOG.info("Node deleted");

        // Create alarm
        int alarmId = transactionOperations.execute(status -> {
            OnmsAlarm alarm = new OnmsAlarm();
            alarm.setUei(EventConstants.NODE_LOST_SERVICE_EVENT_UEI);
            alarm.setSeverity(OnmsSeverity.CRITICAL);
            alarm.setAlarmType(OnmsAlarm.PROBLEM_TYPE);
            alarm.setCounter(1);
            alarm.setDistPoller(distPollerDao.whoami());
            alarm.setReductionKey(String.format("%s::1:192.168.1.1:ICMP", EventConstants.NODE_LOST_SERVICE_EVENT_UEI));
            return alarmDao.save(alarm);
        });

        // Query alarms
        transactionOperations.execute(status -> {
            final List<OnmsAlarm> all = alarmDao.findAll();
            if (all.isEmpty()) {
                LOG.warn("No alarms found.");
            }
            for (OnmsAlarm alarm : all) {
                LOG.info("Found alarm: {}", alarm);
            }
            return null;
        });

        // Delete alarm
        LOG.info("Delete alarm with id {}", alarmId);
        transactionOperations.execute(status -> {
            alarmDao.delete(alarmId);
            return null;
        });
        LOG.info("Alarm deleted");
    }

    public void destroy() {
        LOG.info("destroy");
    }
}
