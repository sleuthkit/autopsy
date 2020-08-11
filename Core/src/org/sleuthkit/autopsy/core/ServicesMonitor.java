/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.core;

import org.sleuthkit.autopsy.core.events.ServiceEvent;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import java.util.Objects;
import java.util.Map;
import org.sleuthkit.autopsy.casemodule.multiusercases.services.PostgreSqlServer;
import org.sleuthkit.autopsy.casemodule.multiusercases.services.ActiveMqMessageBroker;
import org.sleuthkit.autopsy.casemodule.multiusercases.services.SolrServer;
import org.sleuthkit.autopsy.casemodule.multiusercases.services.ZooKeeperDatabase;

/**
 * Monitors the status of application services in a collaborative, multi-user
 * case environment and publishes events and user notifications when the status
 * of a service changes.
 *
 * A service that wants to have its current status periodically polled by the
 * services monitor should implement the MonitoredService interface.
 *
 * A service that wants to push its current status to the services monitor
 * should call the setServiceStatus() method when it starts up and whenever its
 * status changes.
 *
 * A service can implement both strategies, if desired.
 */
public class ServicesMonitor {

    /**
     * An enumeration of the core services in a collaborative, multi-user case
     * environment.
     *
     * The service display names provided here can be used to identify the
     * service status events published for these services and to directly query
     * the ServicesMonitor for the current status of these services.
     */
    @NbBundle.Messages({
        "ServicesMonitor.dbServer.displayName=PostgreSQL Server",
        "ServicesMonitor.solrServer.displayName=Solr Server",
        "ServicesMonitor.messageBroker.displayName=ActiveMQ Message Broker",
        "ServicesMonitor.coordinationSvc.displayName=ZooKeeper Service"
    })
    public enum Service {
        DATABASE_SERVER(Bundle.ServicesMonitor_dbServer_displayName()),
        KEYWORD_SEARCH_SERVICE(Bundle.ServicesMonitor_solrServer_displayName()),
        MESSAGING(Bundle.ServicesMonitor_messageBroker_displayName()),
        COORDINATION_SERVICE(Bundle.ServicesMonitor_coordinationSvc_displayName()),
        /*
         * @deprecated
         */
        @Deprecated
        REMOTE_CASE_DATABASE(Bundle.ServicesMonitor_dbServer_displayName()),
        /*
         * @deprecated
         */
        @Deprecated
        REMOTE_KEYWORD_SEARCH(Bundle.ServicesMonitor_solrServer_displayName());

        private final String displayName;

        Service(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    };

    /**
     * An enumeration of the standard service statuses. If a service reports a
     * status of OTHER, its reported status will be its details string.
     */
    @NbBundle.Messages({
        "ServicesMonitor.serviceStatus.up=up",
        "ServicesMonitor.serviceStatus.down=down"
    })
    public enum ServiceStatus {
        UP(Bundle.ServicesMonitor_serviceStatus_up()),
        DOWN(Bundle.ServicesMonitor_serviceStatus_down());

        private final String displayName;

        ServiceStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

    };

    /**
     * A service status report.
     */
    public static class ServiceStatusReport {

        private final String serviceName;
        private final String status;
        private final String details;

        /**
         * Constructs an instance of a service status report.
         *
         * @param service The service.
         * @param status  The service status.
         * @param details Additional details regarding the service status, may
         *                be the empty string.
         */
        public ServiceStatusReport(Service service, ServiceStatus status, String details) {
            this(service.getDisplayName(), status.getDisplayName(), details);
        }

        /**
         * Constructs an instance of a service status report.
         *
         * @param serviceName The service name.
         * @param status      The service status.
         * @param details     Additional details regarding the service status,
         *                    may be the empty string.
         */
        public ServiceStatusReport(String serviceName, ServiceStatus status, String details) {
            this(serviceName, status.getDisplayName(), details);
        }

        /**
         * Constructs an instance of a service status report.
         *
         * @param serviceName The service name.
         * @param status      The service status.
         * @param details     Additional details regarding the service status,
         *                    may be the empty string.
         */
        public ServiceStatusReport(String serviceName, String status, String details) {
            /*
             * Support the deprecated members of the Services enum.
             */
            String svcName = replaceDeprecatedServiceName(serviceName);

            this.serviceName = svcName;
            this.status = status;
            this.details = details;
        }

        /**
         * Gets the display name of the service.
         *
         * @return The service display name.
         */
        public String getServiceName() {
            return serviceName;
        }

        /**
         * Gets the status of the service.
         *
         * @return The service status.
         */
        public String getStatus() {
            return status;
        }

        /**
         * Gets any additional details regarding the status of a service.
         *
         * @return The additional service status details, may be empty.
         */
        public String getDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "ServiceStatus{" + "serviceName=" + serviceName + ", status=" + status + ", details=" + details + '}';
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + Objects.hashCode(this.serviceName);
            hash = 23 * hash + Objects.hashCode(this.status);
            hash = 23 * hash + Objects.hashCode(this.details);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final ServiceStatusReport other = (ServiceStatusReport) obj;
            if (!Objects.equals(this.serviceName, other.getServiceName())) {
                return false;
            }
            if (!Objects.equals(this.status, other.getStatus())) {
                return false;
            }
            return Objects.equals(this.details, other.getDetails());
        }

    }

    /**
     * A service that wants to have its current status periodically polled by
     * the services monitor must implement the MonitoredService interface and
     * must be marked with the MonitoredService ServiceProvider annotation.
     */
    public interface MonitoredService {

        /**
         * Gets the current status of the service.
         *
         * @return The status of the service.
         */
        ServiceStatusReport getStatus();

    }

    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private static final String PERIODIC_TASK_THREAD_NAME = "services-monitor-periodic-task-%d"; //NON-NLS
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 15;
    private static ServicesMonitor servicesMonitor = new ServicesMonitor();
    private final Map<String, MonitoredService> coreServicesByName;
    private final Map<String, ServiceStatusReport> statusByService;
    private final AutopsyEventPublisher eventPublisher;
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    /**
     * Gets the services monitor that monitors the status of services and
     * publishes events and user notifications when the status of a service
     * changes.
     *
     * @return The services monitor singleton.
     */
    public synchronized static ServicesMonitor getInstance() {
        if (servicesMonitor == null) {
            servicesMonitor = new ServicesMonitor();
        }
        return servicesMonitor;
    }

    /**
     * Constructs a services monitor that monitors the status of services and
     * publishes events and user notifications when the status of a service
     * changes.
     */
    private ServicesMonitor() {
        coreServicesByName = new ConcurrentHashMap<>();
        coreServicesByName.put(Service.DATABASE_SERVER.toString(), new PostgreSqlServer());
        coreServicesByName.put(Service.KEYWORD_SEARCH_SERVICE.toString(), new SolrServer());
        coreServicesByName.put(Service.COORDINATION_SERVICE.toString(), new ZooKeeperDatabase());
        coreServicesByName.put(Service.MESSAGING.toString(), new ActiveMqMessageBroker());
        statusByService = new ConcurrentHashMap<>();
        eventPublisher = new AutopsyEventPublisher();

        /*
         * The first service statuses check is performed immediately in the
         * current thread.
         */
        checkAllServices();

        /**
         * Start a periodic task to do ongoing service status checks.
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleWithFixedDelay(new ServicesMonitoringTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Pushes a service status report to the services monitor.
     *
     * @param statusReport The service status report.
     */
    public void setServiceStatus(ServiceStatusReport statusReport) {
        String serviceName = statusReport.getServiceName();
        ServiceStatusReport previousStatusReport = statusByService.get(serviceName);
        if (statusIsChanged(previousStatusReport, statusReport)) {
            reportStatus(statusReport);
        }
        statusByService.put(statusReport.getServiceName(), statusReport);
    }

    /**
     * Gets a service status report for a specified service.
     *
     * @param service The service.
     *
     * @return The status report or null if there is no status report available
     *         for the specifed service.
     */
    public ServiceStatusReport getServiceStatusReport(Service service) {
        return getServiceStatusReport(service.toString());
    }

    /**
     * Gets a service status report for a specified service.
     *
     * @param serviceName The service name.
     *
     * @return The status report or null if there is no status report available
     *         for the specifed service.
     */
    public ServiceStatusReport getServiceStatusReport(String serviceName) {
        /*
         * Support the deprecated members of the Services enum.
         */
        String svcName = replaceDeprecatedServiceName(serviceName);

        ServiceStatusReport statusReport;
        if (coreServicesByName.containsKey(svcName)) {
            /*
             * If the request is for a core service, perform an "on demand"
             * check to get the current status.
             */
            MonitoredService service = coreServicesByName.get(svcName);
            statusReport = service.getStatus();
            setServiceStatus(statusReport);
        } else {
            statusReport = statusByService.get(svcName);
        }
        return statusReport;
    }

    /**
     * Adds a subscriber to service status events for the core services.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(coreServicesByName.keySet(), subscriber);
    }

    /**
     * Adds a subscriber to service status events for a set of services.
     *
     * @param serviceNames The names of the services the subscriber is
     *                     interested in.
     * @param subscriber   The subscriber to add.
     */
    public void addSubscriber(Set<String> serviceNames, PropertyChangeListener subscriber) {
        /*
         * Support the deprecated members of the Services enum.
         */
        Set<String> svcNames = replaceDeprecatedServiceNames(serviceNames);

        eventPublisher.addSubscriber(svcNames, subscriber);
    }

    /**
     * Adds a subscriber to service status events for a service.
     *
     * @param serviceName The service the subscriber is interested in.
     * @param subscriber  The subscriber to add.
     */
    public void addSubscriber(String serviceName, PropertyChangeListener subscriber) {
        /*
         * Support the deprecated members of the Services enum.
         */
        String svcName = replaceDeprecatedServiceName(serviceName);

        eventPublisher.addSubscriber(svcName, subscriber);
    }

    /**
     * Removes a subscriber to service status events for the core services.
     *
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(coreServicesByName.keySet(), subscriber);
    }

    /**
     * Removes a subscriber to service status events for a set of services.
     *
     * @param serviceNames The names of the services the subscriber is no longer
     *                     interested in.
     * @param subscriber   The subscriber to remove.
     */
    public void removeSubscriber(Set<String> serviceNames, PropertyChangeListener subscriber) {
        /*
         * Support the deprecated members of the Services enum.
         */
        Set<String> svcNames = replaceDeprecatedServiceNames(serviceNames);

        eventPublisher.removeSubscriber(svcNames, subscriber);
    }

    /**
     * Adds a subscriber to service status events for a service.
     *
     * @param serviceName The name of the service the subscriber is no longer
     *                    interested in.
     * @param subscriber  The subscriber to remove.
     */
    public void removeSubscriber(String serviceName, PropertyChangeListener subscriber) {
        /*
         * Support the deprecated members of the Services enum.
         */
        String svcName = replaceDeprecatedServiceName(serviceName);

        eventPublisher.removeSubscriber(svcName, subscriber);
    }

    /**
     * Checks the status of all monitored services in a collaborative,
     * multi-user case environment and publishes a service event and a user
     * notification if the status of a service has changed since the last check.
     */
    private void checkAllServices() {
        if (UserPreferences.getIsMultiUserModeEnabled()) {
            for (MonitoredService service : Lookup.getDefault().lookupAll(MonitoredService.class)) {
                setServiceStatus(service.getStatus());
            }
        }
    }

    /**
     * Compares a previous status report for a service to a new report to
     * determine if the status of the service has changed.
     *
     * @param previousStatusReport The previous report, may be null.
     * @param newStatusReport      The new report.
     *
     * @return
     */
    private boolean statusIsChanged(ServiceStatusReport previousStatusReport, ServiceStatusReport newStatusReport) {
        return (previousStatusReport == null || !newStatusReport.equals(previousStatusReport));
    }

    /**
     * Reports a service status change by logging the change, doing a user
     * notification if running in the GUI and publishing a service status event.
     *
     * @param newStatusReport The service status report to publish.
     */
    @NbBundle.Messages({
        "ServicesMonitor.notification.title=Service Status",
        "# {0} - service name", "# {1} - service status", "ServicesMonitor.notification.message.status={0} is {1}",
        "# {0} - service name", "# {1} - service status", "# {2} - status details", "ServicesMonitor.notification.message.detailedStatus={0} is {1}.\nDetails: {2}",})
    private void reportStatus(ServiceStatusReport newStatusReport) {
        /*
         * Log the status.
         */
        String serviceName = newStatusReport.getServiceName();
        String status = newStatusReport.getStatus();
        String details = newStatusReport.getDetails();
        logger.log(Level.INFO, "Status of {0} is {1}, details: {2}", new Object[]{serviceName, status, details}); //NON-NLS

        /*
         * Notify the user.
         */
        if (RuntimeProperties.runningWithGUI()) {
            if (details.isEmpty()) {
                MessageNotifyUtil.Notify.info(Bundle.ServicesMonitor_notification_title(), Bundle.ServicesMonitor_notification_message_status(serviceName, status));
            } else {
                MessageNotifyUtil.Notify.info(Bundle.ServicesMonitor_notification_title(), Bundle.ServicesMonitor_notification_message_detailedStatus(serviceName, status, details));
            }
        }

        /*
         * Publish a status event to subscribers in this application instance
         * only.
         */
        eventPublisher.publishLocally(new ServiceEvent(serviceName, status, details));
    }

    /**
     * Handles mapping deprecated service names from the Service enum to the
     * correct service names. Names of services that do not appear in the enum
     * or that are not deprecated are unchanged.
     *
     * @param serviceName The service name.
     *
     * @return The mapped service name.
     */
    private static String replaceDeprecatedServiceName(String serviceName) {
        String svcName;
        if (serviceName.equals(Service.REMOTE_CASE_DATABASE.getDisplayName())) {
            svcName = Service.DATABASE_SERVER.getDisplayName();
        } else if (serviceName.equals(Service.REMOTE_KEYWORD_SEARCH.getDisplayName())) {
            svcName = Service.KEYWORD_SEARCH_SERVICE.getDisplayName();
        } else {
            svcName = serviceName;
        }
        return svcName;
    }

    /**
     * Handles mapping deprecated service names from the Service enum to the
     * correct service names. Names of services that do not appear in the enum
     * or that are not deprecated are unchanged.
     *
     * @param serviceNames A set of service names.
     *
     * @return A set of of mapped service names.
     */
    private static Set<String> replaceDeprecatedServiceNames(Set<String> serviceNames) {
        Set<String> svcNames = new HashSet<>();
        for (String serviceName : serviceNames) {
            svcNames.add(replaceDeprecatedServiceName(serviceName));
        }
        return svcNames;
    }

    /**
     * A task that checks the status of the core services in a collaborative,
     * multi-user case environment: the database server, the keyword search
     * server and the messaging service. Publishes a service event and user
     * notification if the status of a service has changed since the last check.
     */
    private final class ServicesMonitoringTask implements Runnable {

        @Override
        public void run() {
            try {
                checkAllServices();
            } catch (Exception ex) { // Exception firewall
                logger.log(Level.SEVERE, "An error occurred during services monitoring", ex); //NON-NLS
            }
        }

    }

    /**
     * Exception thrown if an error occurs during a service status query.
     */
    public class ServicesMonitorException extends Exception {

        private static final long serialVersionUID = 1L;

        public ServicesMonitorException(String message) {
            super(message);
        }

        public ServicesMonitorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Records the status of a service and publishes a service status event if
     * the current status is different from the previously reported status.
     *
     * @param service Name of the service.
     * @param status  Current status of the service.
     * @param details Additional status details.
     *
     * @deprecated Call setServiceStatus(ServiceStatusReport newStatus) instead.
     */
    @Deprecated
    public void setServiceStatus(String service, String status, String details) {
        ServiceStatusReport statusReport = new ServiceStatusReport(service, status, details);
        setServiceStatus(statusReport);
    }

    /**
     * Get last recorded status for a service.
     *
     * @param serviceName Name of the service.
     *
     * @return ServiceStatus Status for the service.
     *
     * @throws ServicesMonitorException If the service name is unknown to the
     *                                  services monitor.
     * @deprecated Call ServiceStatusReport getServiceStatusReport(String
     * serviceName) instead.
     */
    @Deprecated
    public String getServiceStatus(String serviceName) throws ServicesMonitorException {
        ServiceStatusReport statusReport = getServiceStatusReport(serviceName);
        if (statusReport != null) {
            return statusReport.getStatus();
        } else {
            throw new ServicesMonitorException(String.format("No status available for %s", serviceName));
        }
    }

}
