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
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.events.MessageServiceException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Monitors the status of services and publishes events and user notifications
 * when the status of a service changes. The database server, keyword search
 * server, and messaging service are considered to be core services in a
 * collaborative, multi-user case environment. Additional services can provide
 * current status by calling the setServiceStatus() method.
 */
public class ServicesMonitor {

    /**
     * An enumeration of the core services in a collaborative, multi-user case
     * environment. The display names provided here can be used to identify the
     * service status events published for these services and to directly query
     * the ServicesMonitor for the current status of these services.
     */
    public enum Service {
        REMOTE_CASE_DATABASE(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.remoteCaseDatabase.displayName.text")),
        REMOTE_KEYWORD_SEARCH(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.remoteKeywordSearch.displayName.text")),
        MESSAGING(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.messaging.displayName.text"));

        private final String displayName;

        private Service(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    };

    /**
     * An enumeration of the standard service statuses.
     */
    public enum ServiceStatus {
        UP,
        DOWN
    };

    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private static final String PERIODIC_TASK_THREAD_NAME = "services-monitor-periodic-task-%d"; //NON-NLS
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 15;
    private static final Set<String> coreServices = Stream.of(ServicesMonitor.Service.values()).map(Service::toString).collect(Collectors.toSet());
    private static ServicesMonitor servicesMonitor = new ServicesMonitor();
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;
    private final ConcurrentHashMap<String, String> statusByService;
    private final AutopsyEventPublisher eventPublisher;

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
        eventPublisher = new AutopsyEventPublisher();
        statusByService = new ConcurrentHashMap<>();

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
     * Records the status of a service and publishes a service status event if
     * the current status is different from the previously reported status.
     *
     * @param service Name of the service.
     * @param status  Current status of the service.
     * @param details Additional status details.
     *
     */
    public void setServiceStatus(String service, String status, String details) {
        if (statusByService.containsKey(service) && status.equals(statusByService.get(service))) {
            return;
        }

        statusByService.put(service, status);

        String serviceDisplayName;
        try {
            serviceDisplayName = ServicesMonitor.Service.valueOf(service).getDisplayName();
        } catch (IllegalArgumentException ignore) {
            serviceDisplayName = service;
        }

        if (status.equals(ServiceStatus.UP.toString())) {
            logger.log(Level.INFO, "Connection to {0} is up", serviceDisplayName); //NON-NLS
            MessageNotifyUtil.Notify.info(
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.msg", serviceDisplayName));
        } else if (status.equals(ServiceStatus.DOWN.toString())) {
            logger.log(Level.SEVERE, "Failed to connect to {0}. Reason: {1}", new Object[]{serviceDisplayName, details}); //NON-NLS
            MessageNotifyUtil.Notify.error(
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.msg", serviceDisplayName));
        } else {
            logger.log(Level.INFO, "Status for {0} is {1} ({2})", new Object[]{serviceDisplayName, status}); //NON-NLS
            MessageNotifyUtil.Notify.info(
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.msg", new Object[]{serviceDisplayName, status, details}));
        }

        eventPublisher.publishLocally(new ServiceEvent(service, status, details));
    }

    /**
     * Get last recorded status for a service.
     *
     * @param service Name of the service.
     *
     * @return ServiceStatus Status for the service.
     *
     * @throws ServicesMonitorException If the service name is unknown to the
     *                                  services monitor.
     */
    public String getServiceStatus(String service) throws ServicesMonitorException {

        if (service == null || service.isEmpty()) {
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.nullServiceName.excepton.txt"));
        }

        /*
         * If the request is for a core service, perform an "on demand" check to
         * get the current status.
         */
        if (coreServices.contains(service)) {
            checkServiceStatus(service);
        }

        String status = statusByService.get(service);
        if (status == null) {
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.unknownServiceName.excepton.txt", service));
        }
        return status;
    }

    /**
     * Adds a subscriber to service status events for the core services.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(coreServices, subscriber);
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
        eventPublisher.removeSubscriber(coreServices, subscriber);
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

        for (String service : coreServices) {
            checkServiceStatus(service);
        }
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
     * Performs a core service availability status check.
     *
     * @param service Name of the service to check.
     */
    private void checkServiceStatus(String service) {
        if (service.equals(Service.REMOTE_CASE_DATABASE.toString())) {
            checkDatabaseConnectionStatus();
        } else if (service.equals(Service.REMOTE_KEYWORD_SEARCH.toString())) {
            checkKeywordSearchServerConnectionStatus();
        } else if (service.equals(Service.MESSAGING.toString())) {
            checkMessagingServerConnectionStatus();
        }
    }

    /**
     * Performs a database server availability status check.
     */
    private void checkDatabaseConnectionStatus() {
        CaseDbConnectionInfo info;
        try {
            info = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
            setServiceStatus(Service.REMOTE_CASE_DATABASE.toString(), ServiceStatus.DOWN.toString(), NbBundle.getMessage(this.getClass(), "ServicesMonitor.databaseConnectionInfo.error.msg"));
            return;
        }
        try {
            SleuthkitCase.tryConnect(info);
            setServiceStatus(Service.REMOTE_CASE_DATABASE.toString(), ServiceStatus.UP.toString(), "");
        } catch (TskCoreException ex) {
            setServiceStatus(Service.REMOTE_CASE_DATABASE.toString(), ServiceStatus.DOWN.toString(), ex.getMessage());
        }
    }

    /**
     * Performs a keyword search service availability status check.
     */
    private void checkKeywordSearchServerConnectionStatus() {
        KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        try {
            if (kwsService != null) {
                ServiceStatus status = ServiceStatus.DOWN;
                // check Solr 8
                String kwsHostName = UserPreferences.getIndexingServerHost();
                if (!kwsHostName.isEmpty()) {
                    int port = Integer.parseUnsignedInt(UserPreferences.getIndexingServerPort());
                    kwsService.tryConnect(UserPreferences.getIndexingServerHost(), port);
                    status = ServiceStatus.UP;
                }
                
                // check Solr 4
                if (!UserPreferences.getSolr4ServerHost().trim().isEmpty()) {
                    int port = Integer.parseUnsignedInt(UserPreferences.getSolr4ServerPort().trim());
                    kwsService.tryConnect(UserPreferences.getSolr4ServerHost().trim(), port);
                    status = ServiceStatus.UP;
                }
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), status.toString(), "");
            } else {
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString(),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.KeywordSearchNull"));
            }
        } catch (NumberFormatException ex) {
            String rootCause = NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.InvalidPortNumber");
            logger.log(Level.SEVERE, "Unable to connect to Keyword Search server: " + rootCause, ex); //NON-NLS
            setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString(), rootCause);
        } catch (KeywordSearchServiceException ex) {
            String rootCause = ex.getMessage();
            logger.log(Level.SEVERE, "Unable to connect to Keyword Search server: " + rootCause, ex); //NON-NLS
            setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString(), rootCause);
        }
    }

    /**
     * Performs messaging service availability status check.
     */
    private void checkMessagingServerConnectionStatus() {
        MessageServiceConnectionInfo info;
        try {
            info = UserPreferences.getMessageServiceConnectionInfo();
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing messaging service connection info", ex); //NON-NLS
            setServiceStatus(Service.MESSAGING.toString(), ServiceStatus.DOWN.toString(), NbBundle.getMessage(this.getClass(), "ServicesMonitor.messagingService.connErr.text"));
            return;
        }

        try {
            info.tryConnect();
            setServiceStatus(Service.MESSAGING.toString(), ServiceStatus.UP.toString(), "");
        } catch (MessageServiceException ex) {
            String rootCause = ex.getMessage();
            logger.log(Level.SEVERE, "Unable to connect to messaging server: " + rootCause, ex); //NON-NLS
            setServiceStatus(Service.MESSAGING.toString(), ServiceStatus.DOWN.toString(), rootCause);
        }
    }

}
