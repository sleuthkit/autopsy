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

/**
 * This class periodically checks availability of collaboration resources -
 * remote database, remote keyword search server, messaging service - and
 * reports status updates to the user in case of a gap in service.
 */
public class ServicesMonitor {

    private AutopsyEventPublisher eventPublisher;
    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    private static final String PERIODIC_TASK_THREAD_NAME = "services-monitor-periodic-task-%d";
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 2;

    private static final Set<Service> servicesList = Stream.of(ServicesMonitor.Service.values())
            .collect(Collectors.toSet());
    
    /**
     * The service monitor maintains a mapping of each service to it's last
     * status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;

    /**
     * Call constructor on start-up so that the first check of services is 
     * done as soon as possible.
     */
    private static ServicesMonitor instance = new ServicesMonitor();
    
    /**
     * List of services that are being monitored. The service names should be
     * representative of the service functionality and readable as they get
     * logged when service outage occurs.
     */
    public enum Service {

        /**
         * Property change event fired when remote case database status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */
        REMOTE_CASE_DATABASE("Remote Case Database"),
        /**
         * Property change event fired when remote keyword search service status
         * changes. New value is set to updated ServiceStatus, old value is
         * null.
         */
        REMOTE_KEYWORD_SEARCH("Remote Keyword Search"),
        /**
         * Property change event fired when messaging service status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */
        MESSAGING("Messaging Service");
        
        private final String serviceName;

        private Service(String displayName) {
            this.serviceName = displayName;
        }

        public String getName() {
            return serviceName;
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
        periodicTasksExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Updates service status and publishes the service status update if it is
     * different from previous status. Event is published locally. Logs status changes.
     *
     * @param service Name of the service.
     * @param status Updated status for the service.
     * @param details Details of the event.
     */
    private void setServiceStatus(String service, String status, String details) {
        
        // if the status update is for an existing service who's status hasn't changed - do nothing.
        if (status.equals(statusByService.get(service))) {
            return;
        }

        // new service or status has changed
        if (status.equals(ServiceStatus.UP.toString())) {
            logger.log(Level.INFO, "Connection to {0} restored", service); //NON-NLS
            MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.msg", service));
        } else if (status.equals(ServiceStatus.DOWN.toString())) {
            logger.log(Level.SEVERE, "Failed to connect to {0}", service); //NON-NLS
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"),
                    NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.msg", service));
        }

        // update and publish new status
        statusByService.put(service, status);
        eventPublisher.publishLocally(new ServiceEvent(service, status, details));
    }

    /**
     * Get last status update for a service.
     *
     * @param service Name of the service.
     * @return ServiceStatus Status for the service.
     * @throws org.sleuthkit.autopsy.core.ServicesMonitor.UnknownServiceException Thrown if service name is null or service doesn't exist.
     */
    public String getServiceStatus(String service) throws UnknownServiceException {

        if (service == null) {
            throw new UnknownServiceException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.nullServiceName.excepton.txt"));
        }

        String status = statusByService.get(service);
        if (status == null) {
            // no such service
            throw new UnknownServiceException(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.unknownServiceName.excepton.txt", service));
        }
        return status;
    }

    /**
     * Performs service availability status check.
     *
     * @param service Name of the service.
     */
    private void checkServiceStatus(String service) {
        if (service.equals(Service.REMOTE_CASE_DATABASE.getName())) {
            if (UserPreferences.getDatabaseConnectionInfo().canConnect()) {
                setServiceStatus(Service.REMOTE_CASE_DATABASE.getName(), ServiceStatus.UP.toString(), "");
                return;
            } else {
                setServiceStatus(Service.REMOTE_CASE_DATABASE.getName(), ServiceStatus.DOWN.toString(), "");
                return;
            }
        } else if (service.equals(Service.REMOTE_KEYWORD_SEARCH.getName())) {
            KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
            if (kwsService != null && kwsService.canConnectToRemoteSolrServer()) {
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.getName(), ServiceStatus.UP.toString(), "");
                return;
            } else {
                setServiceStatus(Service.REMOTE_KEYWORD_SEARCH.getName(), ServiceStatus.DOWN.toString(), "");
                return;
            }
        } else if (service.equals(Service.MESSAGING.getName())) {
            if (UserPreferences.getMessageServiceConnectionInfo().canConnect()) {
                setServiceStatus(Service.MESSAGING.getName(), ServiceStatus.UP.toString(), "");
                return;
            } else {
                setServiceStatus(Service.MESSAGING.getName(), ServiceStatus.DOWN.toString(), "");
                return;
            }
        }

        // no method to check any other services
        logger.log(Level.SEVERE, "No method exists to check status of service {0}", service); //NON-NLS 
        setServiceStatus(service, ServiceStatus.DOWN.toString(), "");
    }

    /**
     * Adds an event subscriber to this publisher. Subscriber will be subscribed
     * to all events from this publisher.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        for (Service service : servicesList) {
            eventPublisher.addSubscriber(service.getName(), subscriber);
        }
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
     * @param eventName The event the subscriber is interested in.
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
     * @param eventName The event the subscriber is no longer interested in.
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
        for (Service service : servicesList) {
            eventPublisher.removeSubscriber(service.getName(), subscriber);
        }
    }
    
    /**
     * Verifies connectivity to all services.
     */
    private void checkAllServices() {
        for (Service service : servicesList) {
            checkServiceStatus(service.getName());
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
            checkAllServices();
        }
    }

    /**
     * Exception thrown when service status query results in an error.
     */
    public class UnknownServiceException extends Exception {

        public UnknownServiceException(String message) {
            super(message);
        }

        public UnknownServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
