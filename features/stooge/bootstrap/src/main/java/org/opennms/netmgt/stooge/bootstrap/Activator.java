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

package org.opennms.netmgt.stooge.bootstrap;

import com.google.common.collect.Maps;
import org.eclipse.gemini.blueprint.context.support.AbstractOsgiBundleApplicationContext;
import org.eclipse.gemini.blueprint.context.support.OsgiBundleXmlApplicationContext;
import org.opennms.core.soa.Registration;
import org.opennms.core.soa.RegistrationHook;
import org.opennms.core.soa.ServiceRegistry;
import org.opennms.netmgt.dao.api.NodeDao;
import org.opennms.netmgt.dao.hibernate.NodeDaoHibernate;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.rrd.NullRrdStrategy;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.opennms.netmgt.stooge.api.TransactionOperations;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class Activator implements BundleActivator, RegistrationHook {
    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private BundleContext context;
    private final Map<Registration, ServiceRegistration<?>> onmsRegistration2osgiRegistrationMap = new ConcurrentHashMap<Registration, ServiceRegistration<?>>();

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.info("Start.");
        this.context = context;

        System.setProperty("opennms.home", "/home/jesse/git/opennms/target/opennms");
        System.setProperty("org.opennms.timeseries.strategy", "rrd-disabled");

        // Load the application context using Gemini
        // We can't use the standard ClasspathXmlApplicationContext since it fails
        // class-loader related issues and Hibernate cannot perform the package scanning properly,
        // also due to class-loader issue.
        final OsgiBundleXmlApplicationContext applicationContext = new OsgiBundleXmlApplicationContext(new String[]{
                    "/META-INF/opennms/applicationContext-soa.xml",
                    "/META-INF/opennms/applicationContext-commonConfigs.xml",
                    "/META-INF/opennms/applicationContext-dao.xml"});
        applicationContext.setBundleContext(context);
        applicationContext.refresh();
        applicationContext.start();

        // Expose the services in the ONMSGi registry
        final ServiceRegistry serviceRegistry = applicationContext.getBean(ServiceRegistry.class);
        serviceRegistry.addRegistrationHook(this, true);

        // Expose the trans ops
        org.springframework.transaction.support.TransactionOperations transactionOperations = applicationContext.getBean(org.springframework.transaction.support.TransactionOperations.class);
        TransactionOperationsWrapper wrapper = new TransactionOperationsWrapper(transactionOperations);
        context.registerService(TransactionOperations.class, wrapper, new Hashtable<>());

        LOG.info("Done starting.");
    }

    private static class TransactionOperationsWrapper implements TransactionOperations {
        private final org.springframework.transaction.support.TransactionOperations transactionOperations;

        public TransactionOperationsWrapper(org.springframework.transaction.support.TransactionOperations transactionOperations) {
            this.transactionOperations = transactionOperations;
        }

        @Override
        public void doInTransactionWithoutResult(Runnable runnable) {
            transactionOperations.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    runnable.run();
                }
            });
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.info("Stop.");
    }

    @Override
    public void registrationAdded(Registration registration) {
        final String[] providedInterfaceNames = getProvidedInterfaceNames(registration);
        final Dictionary<String, ?> properties;
        if (registration.getProperties() == null) {
            properties = new Hashtable<>();
        } else {
            properties = new Hashtable<>(registration.getProperties());
        }
        final ServiceRegistration<?> osgiRegistration = context.registerService(providedInterfaceNames, registration.getProvider(), properties);
        onmsRegistration2osgiRegistrationMap.put(registration, osgiRegistration);
    }

    @Override
    public void registrationRemoved(Registration registration) {
        final ServiceRegistration<?> osgiRegistration = onmsRegistration2osgiRegistrationMap.get(registration);
        if (osgiRegistration == null) {
            return;
        }
        osgiRegistration.unregister();
    }

    private static String[] getProvidedInterfaceNames(Registration registration) {
        return Arrays.stream(registration.getProvidedInterfaces())
                .map(c -> c.getCanonicalName())
                .collect(Collectors.toList())
                .toArray(new String[registration.getProvidedInterfaces().length]);
    }
}
