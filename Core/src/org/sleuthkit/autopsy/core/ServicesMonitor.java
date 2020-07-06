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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        "serviceStatus.up=Up",
        "serviceStatus.down=Down",
        "serviceStatus.other=Other"
    })
    public enum ServiceStatus {
        UP(Bundle.serviceStatus_up()),
        DOWN(Bundle.serviceStatus_up()),
        OTHER(Bundle.serviceStatus_other());

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
        private final ServiceStatus status;
        private final String details;

        /**
         * Constructs an instance of a service status report.
         *
         * @param serviceName
         * @param status      The service status.
         * @param details     Additional details regarding the service status,
         *                    may be empty.
         */
        public ServiceStatusReport(String serviceName, ServiceStatus status, String details) {
            this.serviceName = serviceName;
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
        public ServiceStatus getStatus() {
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
     * must be marked woith the ServiceProvider annotation.
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
    private static final Set<String> coreServiceNames = Stream.of(ServicesMonitor.Service.values()).map(Service::toString).collect(Collectors.toSet());
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
        ServiceStatusReport statusReport;
        if (coreServicesByName.containsKey(serviceName)) {
            /*
             * If the request is for a core service, perform an "on demand"
             * check to get the current status.
             */
            MonitoredService service = coreServicesByName.get(serviceName);
            statusReport = service.getStatus();
            setServiceStatus(statusReport);
        } else {
            statusReport = statusByService.get(serviceName);
        }
        return statusReport;
    }

    /**
     * Adds a subscriber to service status events for the core services.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(coreServiceNames, subscriber);
    }

    /**
     * Adds a subscriber to service status events for a subset of the services
     * known to the services monitor.
     *
     * @param services   The services the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(Set<String> services, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(services, subscriber);
    }

    /**
     * Adds a subscriber to service status events for a specific service known
     * to the services monitor.
     *
     * @param service    The service the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(String service, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(service, subscriber);
    }

    /**
     * Removes a subscriber to service status events for the core services.
     *
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(coreServiceNames, subscriber);
    }

    /**
     * Removes a subscriber to service status events for a subset of the
     * services known to the services monitor.
     *
     * @param services   The services the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(Set<String> services, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(services, subscriber);
    }

    /**
     * Adds a subscriber to service status events for a specific service known
     * to the services monitor.
     *
     * @param service    The service the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(String service, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(service, subscriber);
    }

    /**
     * Checks the status of the core services in a collaborative, multi-user
     * case environment: the database server, the keyword search server and the
     * messaging service. Publishes a service event and user notification if the
     * status of a service has changed since the last check.
     */
    private void checkAllServices() {
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            return;
        }

        for (MonitoredService service : Lookup.getDefault().lookupAll(MonitoredService.class)) {
            setServiceStatus(service.getStatus());
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
        return ((previousStatusReport != null && newStatusReport != previousStatusReport) || (previousStatusReport == null));
    }

    /**
     * Reports a service status change by logging the change, doing a user
     * notification if running in the GUI, and firing a service status event.
     *
     * @param newStatusReport The service status report to publish.
     */
    private void reportStatus(ServiceStatusReport newStatusReport) {
        /*
         * Log the status.
         */
        String serviceName = newStatusReport.getServiceName();
        String status = newStatusReport.getStatus().toString();
        String details = newStatusReport.getDetails();
        logger.log(Level.INFO, "Status of {0} is {1} Details: {2}", new Object[]{serviceName, status, details}); //NON-NLS

        /*
         * Notify the user.
         */
        if (RuntimeProperties.runningWithGUI()) {
            if (status.equals(ServiceStatus.UP.toString())) {
                MessageNotifyUtil.Notify.info(
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.msg", serviceName));
            } else if (status.equals(ServiceStatus.DOWN.toString())) {
                MessageNotifyUtil.Notify.error(
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.msg", serviceName));
            }
        }

        /*
         * Fire a local status event.
         */
        eventPublisher.publishLocally(new ServiceEvent(serviceName, status, details));
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
        ServiceStatus serviceStatus;
        if (status.equals(ServiceStatus.UP.toString())) {
            serviceStatus = ServiceStatus.UP;
        } else if (status.equals(ServiceStatus.DOWN.toString())) {
            serviceStatus = ServiceStatus.DOWN;
        } else {
            serviceStatus = ServiceStatus.OTHER;
        }
        ServiceStatusReport statusReport = new ServiceStatusReport(service, serviceStatus, status + " : " + details);
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
            ServiceStatus status = statusReport.getStatus();
            if (status == ServiceStatus.OTHER) {
                return statusReport.getDetails();
            } else {
                return status.toString();
            }
        } else {
            throw new ServicesMonitorException(String.format("No status available for %s", serviceName));
        }
    }

}
