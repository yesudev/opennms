//
// This file is part of the OpenNMS(R) Application.
//
// OpenNMS(R) is Copyright (C) 2006 The OpenNMS Group, Inc.  All rights reserved.
// OpenNMS(R) is a derivative work, containing both original code, included code and modified
// code that was published under the GNU General Public License. Copyrights for modified
// and included code are below.
//
// OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
//
// Original code base Copyright (C) 1999-2001 Oculan Corp.  All rights reserved.
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
//
// For more information contact:
//      OpenNMS Licensing       <license@opennms.org>
//      http://www.opennms.org/
//      http://www.opennms.com/
//
package org.opennms.web.svclayer.support;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Category;
import org.opennms.core.utils.ThreadCategory;
import org.opennms.netmgt.dao.ApplicationDao;
import org.opennms.netmgt.dao.LocationMonitorDao;
import org.opennms.netmgt.dao.MonitoredServiceDao;
import org.opennms.netmgt.model.OnmsApplication;
import org.opennms.netmgt.model.OnmsLocationMonitor;
import org.opennms.netmgt.model.OnmsLocationSpecificStatus;
import org.opennms.netmgt.model.OnmsMonitoredService;
import org.opennms.netmgt.model.OnmsMonitoringLocationDefinition;
import org.opennms.netmgt.model.OnmsNode;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.model.OnmsLocationMonitor.MonitorStatus;
import org.opennms.web.Util;
import org.opennms.web.command.DistributedStatusDetailsCommand;
import org.opennms.web.graph.RelativeTimePeriod;
import org.opennms.web.svclayer.DistributedStatusService;
import org.opennms.web.svclayer.SimpleWebTable;
import org.opennms.web.svclayer.SimpleWebTable.Cell;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;

public class DefaultDistributedStatusService implements DistributedStatusService, InitializingBean {
    private MonitoredServiceDao m_monitoredServiceDao;
    private LocationMonitorDao m_locationMonitorDao;
    private ApplicationDao m_applicationDao;
    private boolean m_layoutApplicationsVertically = false;

    public enum Severity {
        INDETERMINATE("Indeterminate"),
        NORMAL("Normal"),
        WARNING("Warning"),
        CRITICAL("Critical");
        
        private final String m_style;

        private Severity(String style) {
            m_style = style;
        }
        
        public String getStyle() {
            return m_style;
        }
    }
    
    /*
     * XXX No unit tests
     * XXX Not sorting by category
     * XXX not dealing with the case where a node has multiple categories
     */
    public SimpleWebTable createStatusTable(DistributedStatusDetailsCommand command, Errors errors) {
        SimpleWebTable table = new SimpleWebTable(); 
        table.setErrors(errors);
        
        // Already had some validation errors, so don't bother doing anything 
        if (table.getErrors().hasErrors()) {
            return table;
        }
        
        table.setTitle("Distributed poller view for " + command.getApplication() + " from " + command.getLocation() + " location");

        List<OnmsLocationSpecificStatus> status =
            findLocationSpecificStatus(command, table.getErrors());
        
        // No data was found, and an error was probably added, so just return
        if (status == null) {
            return table;
        }
        
        table.addColumn("Node", "");
        table.addColumn("Monitor", "");
        table.addColumn("Service", "");
        table.addColumn("Status", "");
        table.addColumn("Response", "");
        table.addColumn("Last Update", "");
        
        for (OnmsLocationSpecificStatus s : status) {
            OnmsNode node = s.getMonitoredService().getIpInterface().getNode();
            
            table.newRow();
            table.addCell(node.getLabel(), 
                          getStyleForPollResult(s.getPollResult()),
                          "element/node.jsp?node=" + node.getId());

            table.addCell(s.getLocationMonitor().getDefinitionName() + "-"
                          + s.getLocationMonitor().getId(),
                          "");
            table.addCell(s.getMonitoredService().getServiceName(), "",
                          "element/service.jsp?ifserviceid="
                          + s.getMonitoredService().getId());
            table.addCell(s.getPollResult().getStatusName(),
                          "bright");
            
            String responseValue;
            if (s.getPollResult().isAvailable()) {
                long responseTime = s.getPollResult().getResponseTime();
                if (responseTime >= 0) {
                    responseValue = responseTime + "ms"; 
                } else {
                    responseValue = "";
                }
            } else {
                responseValue = s.getPollResult().getReason(); 
            }
            
            table.addCell(responseValue, "");
            table.addCell(new Date(s.getPollResult().getTimestamp().getTime()), "");
        }
        
        return table;
    }
    
    private String getStyleForPollResult(PollStatus status) {
        if (status.isAvailable()) {
            return "Normal";
        } else if (status.isUnresponsive()) {
            return "Warning";
        } else {
            return "Critical";
        }
    }

    protected List<OnmsLocationSpecificStatus> findLocationSpecificStatus(DistributedStatusDetailsCommand command, Errors errors) {
        String locationName = command.getLocation();
        String applicationName = command.getApplication();

        if (locationName == null) {
            throw new IllegalArgumentException("location cannot be null");
        }
        
        if (applicationName == null) {
            throw new IllegalArgumentException("application cannot be null");
        }
        
        OnmsMonitoringLocationDefinition location = m_locationMonitorDao.findMonitoringLocationDefinition(locationName);
        if (location == null) {
            throw new IllegalArgumentException("Could not find location for "
                                               + "location name \""
                                               + locationName + "\"");
        }
        
        OnmsApplication application = m_applicationDao.findByName(applicationName);
        if (application == null) {
            throw new IllegalArgumentException("Could not find application "
                                               + "for application name \""
                                               + applicationName + "\"");
        }

        Collection<OnmsLocationMonitor> locationMonitors = m_locationMonitorDao.findByLocationDefinition(location);
        
        if (locationMonitors.size() == 0) {
            errors.reject("location.no-monitors",
                          new Object[] { applicationName, locationName },
                          "No location monitors have registered for this "
                          + "application and location");
            return null;
        }
        
        List<OnmsLocationMonitor> sortedLocationMonitors = new ArrayList<OnmsLocationMonitor>(locationMonitors);
        Collections.sort(sortedLocationMonitors);
        
        Collection<OnmsMonitoredService> services = m_monitoredServiceDao.findByApplication(application);
        
        List<OnmsMonitoredService> sortedServices = new ArrayList<OnmsMonitoredService>(services);
        Collections.sort(sortedServices);
                                                                     
        List<OnmsLocationSpecificStatus> status = new LinkedList<OnmsLocationSpecificStatus>();
        for (OnmsMonitoredService service : sortedServices) {
            for (OnmsLocationMonitor locationMonitor : sortedLocationMonitors) {
                OnmsLocationSpecificStatus currentStatus = m_locationMonitorDao.getMostRecentStatusChange(locationMonitor, service);
                if (currentStatus == null) {
                    status.add(new OnmsLocationSpecificStatus(locationMonitor, service, PollStatus.unknown("No status recorded for this service from this location")));
                } else {
                    status.add(currentStatus);
                }
            }
        }

        return status;
    }

    public void setMonitoredServiceDao(MonitoredServiceDao monitoredServiceDao) {
        m_monitoredServiceDao = monitoredServiceDao;
        
    }

    public void setLocationMonitorDao(LocationMonitorDao locationMonitorDao) {
        m_locationMonitorDao = locationMonitorDao;
        
    }
    
    public void setApplicationDao(ApplicationDao applicationDao) {
        m_applicationDao = applicationDao;
        
    }

    public SimpleWebTable createFacilityStatusTable(Date start, Date end) {
        Assert.notNull(start, "argument start cannot be null");
        Assert.notNull(end, "argument end cannot be null");
        if (!start.before(end)) {
            throw new IllegalArgumentException("start date (" + start + ") must be older than end date (" + end + ")");
        }
        
        SimpleWebTable table = new SimpleWebTable();
        
        List<OnmsMonitoringLocationDefinition> locationDefinitions = m_locationMonitorDao.findAllMonitoringLocationDefinitions();

        Collection<OnmsApplication> applications = m_applicationDao.findAll();
        if (applications.size() == 0) {
            throw new IllegalArgumentException("there are no applications");
        }
        
        List<OnmsApplication> sortedApplications = new ArrayList<OnmsApplication>(applications);
        Collections.sort(sortedApplications);
        
        Collection<OnmsLocationSpecificStatus> mostRecentStatuses = m_locationMonitorDao.getAllMostRecentStatusChanges();

        Collection<OnmsLocationSpecificStatus> statusesPeriod = new HashSet<OnmsLocationSpecificStatus>();
        statusesPeriod.addAll(m_locationMonitorDao.getAllStatusChangesAt(start));
        statusesPeriod.addAll(m_locationMonitorDao.getStatusChangesBetween(start, end));

        table.setTitle("Distributed Poller Status Summary");
        
        table.addColumn("Area", "");
        table.addColumn("Location", "");
        for (OnmsApplication application : sortedApplications) {
            table.addColumn(application.getName(), "");
        }
        
        for (OnmsMonitoringLocationDefinition locationDefinition : locationDefinitions) {
            Collection<OnmsLocationMonitor> monitors = m_locationMonitorDao.findByLocationDefinition(locationDefinition);
            
            table.newRow();
            table.addCell(locationDefinition.getArea(), "");
            table.addCell(locationDefinition.getName(), "");
            
            for (OnmsApplication application : sortedApplications) {
                Collection<OnmsMonitoredService> memberServices = m_monitoredServiceDao.findByApplication(application);
                Severity status = calculateCurrentStatus(monitors, memberServices, mostRecentStatuses);
            
                Set<OnmsLocationSpecificStatus> selectedStatuses = filterStatus(statusesPeriod, monitors, memberServices);
                
                if (selectedStatuses.size() > 0) {
                    String percentage = calculatePercentageUptime(memberServices, selectedStatuses, start, end);
                    table.addCell(percentage, status.getStyle(), createHistoryPageUrl(locationDefinition, application));
                } else {
                    table.addCell("No data", status.getStyle());
                }
            }
        }
        
        if (isLayoutApplicationsVertically()) {
            SimpleWebTable newTable = new SimpleWebTable();
            newTable.setErrors(table.getErrors());
            newTable.setTitle(table.getTitle());
            
            newTable.addColumn("Application");
            for (List<Cell> row : table.getRows()) {
                // The location is in the second row
                newTable.addColumn(row.get(1).getContent(), row.get(1).getStyleClass());
            }
            
            for (Cell columnHeader : table.getColumnHeaders().subList(2, table.getColumnHeaders().size())) {
                // This is the index into collumn list of the old table to get the data for the current application
                int rowColumnIndex = newTable.getRows().size() + 2;
                
                newTable.newRow();
                newTable.addCell(columnHeader.getContent(), columnHeader.getStyleClass());
                
                for (List<Cell> row : table.getRows()) {
                    newTable.addCell(row.get(rowColumnIndex).getContent(), row.get(rowColumnIndex).getStyleClass(), row.get(rowColumnIndex).getLink());
                }
            }
            
            return newTable;
        }
        
        return table;
    }
    
    private Category log() {
        return ThreadCategory.getInstance(getClass());
    }

    /**
     * Filter a collection of OnmsLocationSpecificStatus based on a
     * collection of monitors and a collection of monitored services.
     * A specific OnmsLocationSpecificStatus instance will only be
     * returned if its OnmsLocationMonitor is in the collection of
     * monitors and its OnmsMonitoredService is in the collection of
     * services.
     * 
     * @param statuses
     * @param monitors
     * @param services
     * @return filtered list
     */
    private Set<OnmsLocationSpecificStatus> filterStatus(Collection<OnmsLocationSpecificStatus> statuses,
                                                         Collection<OnmsLocationMonitor> monitors,
                                                         Collection<OnmsMonitoredService> services) {
        Set<OnmsLocationSpecificStatus> filteredStatuses = new HashSet<OnmsLocationSpecificStatus>();
        
        for (OnmsLocationSpecificStatus status : statuses) {
            if (!monitors.contains(status.getLocationMonitor())) {
                continue;
            }
        
            if (!services.contains(status.getMonitoredService())) {
                continue;
            }

            filteredStatuses.add(status);
        }

        return filteredStatuses;
    }

    public Severity calculateCurrentStatus(
            Collection<OnmsLocationMonitor> monitors,
            Collection<OnmsMonitoredService> applicationServices,
            Collection<OnmsLocationSpecificStatus> statuses) {
        int goodMonitors = 0;
        int badMonitors = 0;
        
        for (OnmsLocationMonitor monitor : monitors) {
            if (monitor == null || monitor.getStatus() != MonitorStatus.STARTED) {
                continue;
            }
            
            Severity status = calculateCurrentStatus(monitor, applicationServices, statuses);
            
            // FIXME: "Normal", etc. should be done with static variables
            if (status == Severity.NORMAL) {
                goodMonitors++;
            } else if (status != Severity.INDETERMINATE) {
                badMonitors++;
            }
        }
        
        if (goodMonitors == 0 && badMonitors == 0) {
            return Severity.INDETERMINATE; // No current responses
        } else if (goodMonitors != 0 && badMonitors == 0) {
            return Severity.NORMAL; // No bad responses
        } else if (goodMonitors == 0 && badMonitors != 0) {
            return Severity.CRITICAL; // All bad responses
        } else if (goodMonitors != 0 && badMonitors != 0){
            return Severity.WARNING; // Some bad responses
        } else {
            throw new IllegalStateException("Shouldn't have gotten here. "
                                            + "good monitors = "
                                            + goodMonitors
                                            + ", bad monitors = "
                                            + badMonitors);
        }
    }
    
    public Severity calculateCurrentStatus(OnmsLocationMonitor monitor,
            Collection<OnmsMonitoredService> applicationServices,
            Collection<OnmsLocationSpecificStatus> statuses) {
        Set<PollStatus> pollStatuses = new HashSet<PollStatus>();
        
        for (OnmsMonitoredService service : applicationServices) {
            boolean foundIt = false;
            for (OnmsLocationSpecificStatus status : statuses) {
                if (status.getMonitoredService().equals(service) && status.getLocationMonitor().equals(monitor)) {
                    pollStatuses.add(status.getPollResult());
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) {
                pollStatuses.add(PollStatus.unknown("No status found for this service"));
                if (log().isDebugEnabled()) {
                    log().debug("Did not find status for service " + service + " in application.  Setting status for it to unknown.");
                }
            }
        }
        
        return calculateStatus(pollStatuses);
    }       
    
    public Severity calculateStatus(Collection<PollStatus> pollStatuses) {
        /*
         * XXX We aren't doing anything for warning, because we don't
         * have a warning state available, right now.  Should unknown
         * be a warning state?
         */
        
        int goodStatuses = 0;
        int badStatuses = 0;
        
        for (PollStatus pollStatus : pollStatuses) {
            if (pollStatus.isAvailable()) {
                goodStatuses++;
            } else if (pollStatus.getStatusCode() != PollStatus.SERVICE_UNKNOWN) {
                badStatuses++;
            }
        }

        if (goodStatuses == 0 && badStatuses == 0) {
            return Severity.INDETERMINATE;
        } else if (goodStatuses > 0 && badStatuses == 0) {
            return Severity.NORMAL;
        } else {
            return Severity.CRITICAL;
        }
    }

    /**
     * Calculate the percentage of time that all services are up for this
     * application on this remote monitor.
     * 
     * @param applicationServices services to report on
     * @param statuses status entries to use for report
     * @param startDate start date.  The report starts on this date.
     * @param endDate end date.  The report ends the last millisecond prior
     * this date.
     * @return representation of the percentage uptime out to three decimal
     * places.  Null is returned if there is no data.
     */
    public String calculatePercentageUptime(
            Collection<OnmsMonitoredService> applicationServices,
            Collection<OnmsLocationSpecificStatus> statuses,
            Date startDate, Date endDate) {
        /*
         * The methodology is as such:
         * 1) Sort the status entries by their timestamp;
         * 2) Create a Map of each monitored service with a default
         *    PollStatus of unknown.
         * 3) Iterate through the sorted list of status entries until
         *    we hit a timestamp that is not within our time range or
         *    run out of entries.
         *    a) Along the way, update the status Map with the current
         *       entry's status, and calculate the current status.
         *    b) If the current timestamp is before the start time, store
         *       the current status so we can use it once we cross over
         *       into our time range and then continue.
         *    c) If the previous status is normal, then count up the number
         *       of milliseconds since the previous state change entry in
         *       the time range (or the beginning of the range if this is
         *       the first entry in within the time range), and add that
         *       a counter of "normal" millseconds.
         *    d) Finally, save the current date and status for later use.
         * 4) Perform the same computation in 3c, except count the number
         *    of milliseconds since the last state change entry (or the
         *    start time if there were no entries) and the end time, and add
         *    that to the counter of "normal" milliseconds.
         * 5) Divide the "normal" milliseconds counter by the total number
         *    of milliseconds in our time range and compute and return a
         *    percentage.
         */

        List<OnmsLocationSpecificStatus> sortedStatuses =
            new LinkedList<OnmsLocationSpecificStatus>(statuses);
        Collections.sort(sortedStatuses, new Comparator<OnmsLocationSpecificStatus>(){
            public int compare(OnmsLocationSpecificStatus o1, OnmsLocationSpecificStatus o2) {
                return o1.getPollResult().getTimestamp().compareTo(o2.getPollResult().getTimestamp());
            }
        });

        HashMap<OnmsMonitoredService,PollStatus> serviceStatus =
            new HashMap<OnmsMonitoredService,PollStatus>();
        for (OnmsMonitoredService service : applicationServices) {
            serviceStatus.put(service, PollStatus.unknown("No history for this service from this location"));
        }
        
        float normalMilliseconds = 0f;
        
        Date lastDate = startDate;
        Severity lastStatus = Severity.CRITICAL;
        
        for (OnmsLocationSpecificStatus status : sortedStatuses) {
            Date currentDate = status.getPollResult().getTimestamp();

            if (!currentDate.before(endDate)) {
                // We're at or past the end date, so we're done processing
                break;
            }
            
            serviceStatus.put(status.getMonitoredService(), status.getPollResult());
            Severity currentStatus = calculateStatus(serviceStatus.values());
            
            if (currentDate.before(startDate)) {
                /*
                 * We're not yet to a date that is inside our time period, so
                 * we don't need to check the status and adjust the
                 * normalMilliseconds variable, but we do need to save the
                 * status so we have an up-to-date status when we cross the
                 * start date.
                 */
                lastStatus = currentStatus;
                continue;
            }
            
            /*
             * Because we *just* had a state change, we want to look at the
             * value of the *last* status.
             */
            if (lastStatus == Severity.NORMAL) {
                long milliseconds = currentDate.getTime() - lastDate.getTime();
                normalMilliseconds += milliseconds;
            }
            
            lastDate = currentDate;
            lastStatus = currentStatus;
        }
        
        if (lastStatus == Severity.NORMAL) {
            long milliseconds = endDate.getTime() - lastDate.getTime();
            normalMilliseconds += milliseconds;
        }

        float percentage = normalMilliseconds /
            (endDate.getTime() - startDate.getTime()) * 100;
        return new DecimalFormat("0.000").format((double) percentage) + "%";
    }

    private String createHistoryPageUrl(
            OnmsMonitoringLocationDefinition locationDefinition,
            OnmsApplication application) {

        List<String> params = new ArrayList<String>(2);
        params.add("location=" + Util.encode(locationDefinition.getName()));
        params.add("application=" + Util.encode(application.getName()));
        
        return "distributedStatusHistory.htm"
            + "?"
            + StringUtils.collectionToDelimitedString(params, "&");
    }

    public DistributedStatusHistoryModel createHistoryModel(
            String locationName, String monitorId, String applicationName,
            String timeSpan, String previousLocationName) {
        List<String> errors = new LinkedList<String>();
        
        List<OnmsMonitoringLocationDefinition> locationDefinitions = m_locationMonitorDao.findAllMonitoringLocationDefinitions();

        List<RelativeTimePeriod> periods = Arrays.asList(RelativeTimePeriod.getDefaultPeriods());

        Collection<OnmsApplication> applications = m_applicationDao.findAll();
        List<OnmsApplication> sortedApplications = new ArrayList<OnmsApplication>(applications);
        Collections.sort(sortedApplications);

        OnmsMonitoringLocationDefinition location;
        if (locationName == null) {
            location = locationDefinitions.get(0);
        } else {
            location = m_locationMonitorDao.findMonitoringLocationDefinition(locationName);
            if (location == null) {
                errors.add("Could not find location definition '" + locationName + "'");
                location = locationDefinitions.get(0);
            }
        }
        
        int monitorIdInt = -1;
        
        if (monitorId != null && monitorId.length() > 0) {
            try {
                monitorIdInt = Integer.parseInt(monitorId);
            } catch (NumberFormatException e) {
                errors.add("Monitor ID '" + monitorId + "' is not an integer");
            }
        }

        OnmsApplication application;
        if (applicationName == null) {
            application = sortedApplications.get(0);
        } else {
            application = m_applicationDao.findByName(applicationName);
            if (application == null) {
                errors.add("Could not find application '" + applicationName + "'");
                application = sortedApplications.get(0);
            }
        }
        
        Collection<OnmsLocationMonitor> monitors = m_locationMonitorDao.findByLocationDefinition(location);
        List<OnmsLocationMonitor> sortedMonitors = new LinkedList<OnmsLocationMonitor>(monitors);
        Collections.sort(sortedMonitors);

        OnmsLocationMonitor monitor = null;
        if (monitorIdInt != -1 && location.getName().equals(previousLocationName)) {
            for (OnmsLocationMonitor m : sortedMonitors) {
                if (m.getId().equals(monitorIdInt)) {
                    monitor = m;
                    break;
                }
            }
            
            if (monitor == null) {
                // XXX should I do anything?
            }
        }
        
        if (monitor == null && sortedMonitors.size() > 0) {
            monitor = sortedMonitors.get(0);
        }
        
        RelativeTimePeriod period = RelativeTimePeriod.getPeriodByIdOrDefault(timeSpan);
        
        /*
         * Initialize the heirarchy under the service so that we don't get
         * a LazyInitializationException later when the JSP page is pulling
         * data out of the model object.
         */
        Collection<OnmsMonitoredService> memberServices = m_monitoredServiceDao.findByApplication(application);
        for (OnmsMonitoredService service : memberServices) {
            m_locationMonitorDao.initialize(service.getIpInterface());
            m_locationMonitorDao.initialize(service.getIpInterface().getNode());
        }

        Collection<OnmsMonitoredService> applicationMemberServices = m_monitoredServiceDao.findByApplication(application);
        return new DistributedStatusHistoryModel(locationDefinitions,
                                                 sortedApplications,
                                                 sortedMonitors,
                                                 periods,
                                                 location,
                                                 application,
                                                 applicationMemberServices,
                                                 monitor,
                                                 period,
                                                 errors);
    }

    public void setLayoutApplicationsVertically(boolean layoutApplicationsVertically) {
        m_layoutApplicationsVertically = layoutApplicationsVertically;
    }
    
    public boolean isLayoutApplicationsVertically() {
        return m_layoutApplicationsVertically;
    }

    public void afterPropertiesSet() throws Exception {
        Assert.state(m_monitoredServiceDao != null, "property monitoredServiceDao cannot be null");
        Assert.state(m_locationMonitorDao != null, "property locationMonitorDao cannot be null");
        Assert.state(m_applicationDao != null, "property applicationDao cannot be null");
    }
}
