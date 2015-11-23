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
import org.sleuthkit.autopsy.casemodule.Case;
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
 * The services monitor actively monitors multi-user case services and stores
 * status info for the multi-user services and any other services that choose to
 * report their status. Whenver the status of a registered service is reported,
 * it is published to any subscribers to events for the service. If the status
 * is change from the last reported status, the new status is logged and a user
 * notification message is created.
 */
public class ServicesMonitor {

    private AutopsyEventPublisher eventPublisher;
    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private final ScheduledThreadPoolExecutor multiuserServiceStatusChecksExecutor;

    private static final String MULTIUSER_SERVICES_STATUS_CHECK_THREAD_NAME = "services-monitor-multiuser-services-status-check-%d";
    private static final int NUMBER_OF_MULTIUSER_SERVICES_STATUS_CHECK_THREADS = 1;
    private static final long MULTIUSER_SERVICES_STATUS_CHECK_INTERVAL_MINUTES = 2;

    private static final Set<String> multiUserServicesList = Stream.of(ServicesMonitor.Service.values())
            .map(Service::toString)
            .collect(Collectors.toSet());

    /**
     * The service monitor maintains a mapping of each service to its last
     * status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;

    /**
     * Call constructor on start-up so that the first check of services is done
     * as soon as possible.
     */
    private static ServicesMonitor instance = new ServicesMonitor();

    /**
     *
     */
    public enum Service {

        /**
         * Property change event fired when remote case database service status
         * changes. New value is set to updated ServiceStatus, old value is
         * null.
         */
        REMOTE_CASE_DATABASE("Multi-user case database service"),
        /**
         * Property change event fired when remote keyword search service status
         * changes. New value is set to updated ServiceStatus, old value is
         * null.
         */
        REMOTE_KEYWORD_SEARCH("Multi-user keyword search service"),
        /**
         * Property change event fired when messaging service status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */
        MESSAGING("Messaging service");

        private final String displayName;

        private Service(String name) {
            this.displayName = name;
        }

        public String getDisplayName() {
            return displayName;
        }
    };

    /**
     * List of predefined service statuses.
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
        checkMultiUserServices();

        /*
         * Start a periodic task to check the availability of the multi-user
         * services.
         */
        multiuserServiceStatusChecksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_MULTIUSER_SERVICES_STATUS_CHECK_THREADS, new ThreadFactoryBuilder().setNameFormat(MULTIUSER_SERVICES_STATUS_CHECK_THREAD_NAME).build());
        multiuserServiceStatusChecksExecutor.scheduleAtFixedRate(new CrashDetectionTask(), MULTIUSER_SERVICES_STATUS_CHECK_INTERVAL_MINUTES, MULTIUSER_SERVICES_STATUS_CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Updates service status and publishes the service status. If the status is
     * changed from the last time it was reported, it is logged and a user
     * notification message is created.
     *
     * @param serviceName Name of the service.
     * @param status      Service status.
     * @param details     Service status details for publication.
     *
     */
    public void setServiceStatus(String serviceName, String status, String details) {

        /*
         * Select a display name for the service. If it is one of the multi-user
         * case services, use the service display name. Otherwise, use service
         * name as the display name.
         */
        String serviceDisplayName;
        if (multiUserServicesList.contains(serviceName)) {
            serviceDisplayName = ServicesMonitor.Service.valueOf(serviceName).getDisplayName();
        } else {
            serviceDisplayName = serviceName;
        }

        /*
         * If the status has changed, do a log message and create a user
         * notification message.
         */
        if ((!statusByService.containsKey(serviceName)) || (statusByService.containsKey(serviceName) && !status.equals(statusByService.get(serviceName)))) {
            if (status.equals(ServiceStatus.UP.toString())) {
                logger.log(Level.INFO, "{0} status is up", serviceDisplayName); //NON-NLS
                MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.msg", serviceDisplayName));
            } else if (status.equals(ServiceStatus.DOWN.toString())) {
                logger.log(Level.SEVERE, "{0} status is down", serviceDisplayName); //NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.msg", serviceDisplayName));
            } else {
                logger.log(Level.INFO, "{0} status is {1}", new Object[]{serviceDisplayName, status}); //NON-NLS
                MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.title"),
                        NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.statusChange.notify.msg", new Object[]{serviceDisplayName, status}));
            }
        }

        /*
         * Update the saved status and publish it.
         */
        statusByService.put(serviceName, status);
        eventPublisher.publishLocally(new ServiceEvent(serviceName, status, details));
    }

    /**
     * Get last reported status update for a service.
     *
     * @param serviceName Name of the service.
     *
     * @return ServiceStatus Status for the service.
     *
     * @throws ServicesMonitorException If the supplied service name is null or
     *                                  if the service is not registered.
     */
    public String getServiceStatus(String serviceName) throws ServicesMonitorException {

        if (serviceName == null) {
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.nullServiceName.exception.txt"));
        }

        // if request is for one of our "core" services - perform an on demand check
        // to make sure we have the latest status.
        if (multiUserServicesList.contains(serviceName)) {
            checkMultiUserServiceStatus(serviceName);
        }

        String status = statusByService.get(serviceName);
        if (status == null) {
            // no such service
            throw new ServicesMonitorException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.unknownServiceName.exception.txt", serviceName));
        }
        return status;
    }

    /**
     * Performs status check for one of the multi-user services.
     *
     * @param service Name of the service.
     */
    private void checkMultiUserServiceStatus(String service) {
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
            setServiceStatus(Service.REMOTE_CASE_DATABASE.toString(), ServiceStatus.DOWN.toString(), "Error accessing case database connection info");
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
            setServiceStatus(Service.MESSAGING.toString(), ServiceStatus.DOWN.toString(), "Error accessing messaging service connection info");
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
        eventPublisher.addSubscriber(multiUserServicesList, subscriber);
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
        eventPublisher.removeSubscriber(multiUserServicesList, subscriber);
    }

    /**
     * Verifies connectivity to multi-user services if multi-user cases are
     * enabled and there is no current case or the current case is a multi-user
     * case.
     */
    private void checkMultiUserServices() {
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            return;
        }

        if (Case.isCaseOpen()) {
            try {
                Case currentCase = Case.getCurrentCase();
                if (Case.CaseType.SINGLE_USER_CASE == currentCase.getCaseType()) {
                    return;
                }
            } catch (IllegalStateException ignored) {
                /*
                 * No current case, proceed to check services because multi-user
                 * cases are enabled.
                 */
            }
        }

        for (String service : multiUserServicesList) {
            checkMultiUserServiceStatus(service);
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
            checkMultiUserServices();
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
