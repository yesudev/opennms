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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.gemini.blueprint.context.support.OsgiBundleXmlApplicationContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

public class Activator implements BundleActivator {
    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private final Map<Long, OsgiBundleXmlApplicationContext> applicationContextMap = new HashMap<>();

    @Override
    public void start(BundleContext context) throws Exception {
        LOG.info("Start.");

        context.addBundleListener(event -> {
            // TODO MVR this should be done in a separate thread according to the gemini-blueprint/osgi documentation
            switch (event.getType()) {
                case BundleEvent.STARTED:
                    startApplicationContext(event.getBundle());
                    break;
                case BundleEvent.UPDATED:
                    restartApplicationContext(event.getBundle());
                    break;
                case BundleEvent.STOPPED:
                    stopApplicationContext(event.getBundle());
                    break;
            }
        });

        LOG.info("Done starting.");
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        LOG.info("Stop.");
        applicationContextMap.values().forEach(applicationContext -> applicationContext.close());
        LOG.info("Done stopping.");
    }

    private void startApplicationContext(Bundle bundle) {
        // We use "X-Spring-Context" to not interfere with default behaviour of Gemini-blueprint, which expects "Spring-Context".
        // Otherwise the application context would be initialized twice.
        final String springContext = bundle.getHeaders().get("X-Spring-Context");
        if (!Strings.isNullOrEmpty(springContext)) {
            final List<String> springContextes = StreamSupport.stream(Arrays.spliterator(springContext.split(",")), false)
                    .map(context -> context.trim())
                    .filter(context -> context.length() > 0).collect(Collectors.toList());
            if (!springContextes.isEmpty()) {
                // Load the application context using Gemini
                // We can't use the standard ClasspathXmlApplicationContext since it fails
                // class-loader related issues and Hibernate cannot perform the package scanning properly,
                // also due to class-loader issue.
                final OsgiBundleXmlApplicationContext applicationContext = new OsgiBundleXmlApplicationContext(springContextes.toArray(new String[springContextes.size()]));
                applicationContext.setBundleContext(bundle.getBundleContext());
                applicationContext.refresh();
                applicationContext.start();
                applicationContextMap.put(bundle.getBundleId(), applicationContext);
            }
        }
    }

    private void stopApplicationContext(Bundle bundle) {
        LOG.info("Stopping application context for bundle {} (id: {})", bundle.getSymbolicName(), bundle.getBundleId());
        final OsgiBundleXmlApplicationContext applicationContext = applicationContextMap.get(bundle.getBundleId());
        if (applicationContext != null) {
            applicationContext.close();
            applicationContextMap.remove(bundle.getBundleId());
        }
        LOG.info("Stopped application context for bundle {} (id: {})", bundle.getSymbolicName(), bundle.getBundleId());
    }

    private void restartApplicationContext(Bundle bundle) {
        stopApplicationContext(bundle);
        startApplicationContext(bundle);
    }

}
