/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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
 * This class periodically checks availability of collaboration resources -
 * remote database, remote keyword search server, messaging service - and
 * reports status updates to the user in case of a gap in service.
 */
public class ServicesMonitor {

    private AutopsyEventPublisher eventPublisher;
    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    private static final String PERIODIC_TASK_THREAD_NAME = "services-monitor-periodic-task-%d"; //NON-NLS
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 15;

    private static final Set<String> servicesList = Stream.of(ServicesMonitor.Service.values())
            .map(Service::toString)
            .collect(Collectors.toSet());

    /**
     * The service monitor maintains a mapping of each service to it's last
     * status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;

    /**
     * Call constructor on start-up so that the first check of services is done
     * as soon as possible.
     */
    private static ServicesMonitor instance = new ServicesMonitor();

    /**
     * List of services that are being monitored. The service names should be
     * representative of the service functionality and readable as they get
     * logged when service outage occurs.
     */
    public enum Service {

        /**
         * Property change event fired when remote case database service status
         * changes. New value is set to updated ServiceStatus, old value is
         * null.
         */
        REMOTE_CASE_DATABASE(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.remoteCaseDatabase.displayName.text")),
        /**
         * Property change event fired when remote keyword search service status
         * changes. New value is set to updated ServiceStatus, old value is
         * null.
         */
        REMOTE_KEYWORD_SEARCH(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.remoteKeywordSearch.displayName.text")),
        /**
         * Property change event fired when messaging service status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */
        MESSAGING(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.messaging.displayName.text"));

        private final String displayName;

        private Service(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    };

    /**
     * List of possible service statuses.
     */
    public enum ServiceStatus {

        /**
         * Service is currently up.
         */
        UP,
        /**
         * Service is currently down.
         */
        DOWN
    };

    public synchronized static ServicesMonitor getInstance() {
        if (instance == null) {
            instance = new ServicesMonitor();
        }
        return instance;
    }

    private ServicesMonitor() {

        this.eventPublisher = new AutopsyEventPublisher();
        this.statusByService = new ConcurrentHashMap<>();

        // First check is triggered immediately on current thread.
        checkAllServices();

        /**
         * Start periodic task that check the availability of key collaboration
         * services.
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleWithFixedDelay(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Updates service status and publishes the service status update if it is
     * different from previous status. Event is published locally. Logs status
     * changes.
     *
     * @param service Name of the service.
     * @param status  Updated status for the service.
     * @param details Details of the event.
     *
     */
    public void setServiceStatus(String service, String status, String details) {
        // if the status update is for an existing service who's status hasn't changed - do nothing.       
        if (statusByService.containsKey(service) && status.equals(statusByService.get(service))) {
            return;
        }

        // new service or status has changed - identify service's display name
        String serviceDisplayName;
        try {
            serviceDisplayName = ServicesMonitor.Service.valueOf(service).getDisplayName();
        } catch (IllegalArgumentException ignore) {
            // custom service that is not listed in ServicesMonitor.Service enum. Use service name as display name.
            serviceDisplayName = service;
        }

        if (status.equals(ServiceStatus.UP.toString())) {
            logger.log(Level.INFO, "Connection to {0} is up", serviceDisplayName); //NON-NLS
            MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.msg", serviceDisplayName));
        } else if (status.equals(ServiceStatus.DOWN.toString())) {
            logger.log(Level.SEVERE, "Failed to connect to {0}. Reason: {1}", new Object[]{serviceDisplayName, details}); //NON-NLS
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.msg", serviceDisplayName));
        } else {
            logger.log(Level.INFO, "Status for {0} is {1}", new Object[]{serviceDisplayName, status}); //NON-NLS
            MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.msg", new Object[]{serviceDisplayName, status}));
        }

        // update and publish new status
        statusByService.put(service, status);
        eventPublisher.publishLocally(new ServiceEvent(service, status, details));
    }

    /**
     * Get last status update for a service.
     *
     * @param service Name of the service.
     *
     * @return ServiceStatus Status for the service.
     *
     * @throws ServicesMonitorException If service name is null or service
     *                                  doesn't exist.
     */
    public String getServiceStatus(String service) throws ServicesMonitorException {

        if (service == null) {
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.nullServiceName.excepton.txt"));
        }

        // if request is for one of our "core" services - perform an on demand check
        // to make sure we have the latest status.
        if (servicesList.contains(service)) {
            checkServiceStatus(service);
        }

        String status = statusByService.get(service);
        if (status == null) {
            // no such service
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.unknownServiceName.excepton.txt", service));
        }
        return status;
    }

    /**
     * Performs service availability status check.
     *
     * @param service Name of the service.
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
     * Performs case database service availability status check.
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
     * Performs keyword search service availability status check.
     */
    private void checkKeywordSearchServerConnectionStatus() {
        KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        try {
            if (kwsService != null) {
                int port = Integer.parseUnsignedInt(UserPreferences.getIndexingServerPort());
                kwsService.tryConnect(UserPreferences.getIndexingServerHost(), port);
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.UP.toString(), "");
            } else {
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString(),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.KeywordSearchNull"));
            }
        } catch (NumberFormatException ex) {
            String rootCause = NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.InvalidPortNumber");
            logger.log(Level.SEVERE, "Unable to connect to messaging server: " + rootCause, ex); //NON-NLS
            setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString(), rootCause);
        } catch (KeywordSearchServiceException ex) {
            String rootCause = ex.getMessage();
            logger.log(Level.SEVERE, "Unable to connect to messaging server: " + rootCause, ex); //NON-NLS
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

    /**
     * Adds an event subscriber to this publisher. Subscriber will be subscribed
     * to all events from this publisher.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(servicesList, subscriber);
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventNames, subscriber);
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventName, subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The events the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventNames, subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventName  The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventName, subscriber);
    }

    /**
     * Removes an event subscriber to this publisher. Subscriber will be removed
     * from all event notifications from this publisher.
     *
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(servicesList, subscriber);
    }

    /**
     * Verifies connectivity to all services.
     */
    private void checkAllServices() {
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            return;
        }

        for (String service : servicesList) {
            checkServiceStatus(service);
        }
    }

    /**
     * A Runnable task that periodically checks the availability of
     * collaboration resources (remote database, remote keyword search service,
     * message broker) and reports status to the user in case of a gap in
     * service.
     */
    private final class CrashDetectionTask implements Runnable {

        /**
         * Monitor the availability of collaboration resources
         */
        @Override
        public void run() {
            try {
                checkAllServices();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unexpected exception in CrashDetectionTask", ex); //NON-NLS
            }
        }
    }

    /**
     * Exception thrown when service status query results in an error.
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
}
