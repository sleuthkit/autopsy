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
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.jms.Connection;
import javax.jms.JMSException;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;

/**
 * This class periodically checks availability of collaboration resources -
 * remote database, remote keyword search server, messaging service - and
 * reports status updates to the user in case of a gap in service.
 */
public class ServicesMonitor {

    private AutopsyEventPublisher eventPublisher;
    private static final Logger logger = Logger.getLogger(ServicesMonitor.class.getName());
    private static ServicesMonitor instance;
    private final ScheduledThreadPoolExecutor periodicTasksExecutor;

    private static final String PERIODIC_TASK_THREAD_NAME = "services-monitor-periodic-task-%d";
    private static final int NUMBER_OF_PERIODIC_TASK_THREADS = 1;
    private static final long CRASH_DETECTION_INTERVAL_MINUTES = 2;

    private static final Set<String> serviceNames = Stream.of(ServicesMonitor.ServiceName.values())
            .map(ServicesMonitor.ServiceName::toString)
            .collect(Collectors.toSet());
    
    /**
     * The service monitor maintains a mapping of each service to it's last status update.
     */
    private final ConcurrentHashMap<String, String> statusByService;    

    /**
     * List of services that are being monitored. The service names should be 
     * representative of the service functionality and readable as they get 
     * logged when service outage occurs.
     */
    public enum ServiceName {

        /**
         * Property change event fired when remote case database status changes. 
         * New value is set to updated ServiceStatus, old value is null.
         */
        REMOTE_CASE_DATABASE,
        
        /**
         * Property change event fired when remote keyword search service status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */        
        REMOTE_KEYWORD_SEARCH,
        
        /**
         * Property change event fired when messaging service status changes.
         * New value is set to updated ServiceStatus, old value is null.
         */        
        MESSAGING
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
        
        // Services are assumed to be "UP" by default. See CrashDetectionTask class for more info.
        for (String serviceName : serviceNames) {
            this.statusByService.put(serviceName, ServiceStatus.UP.toString());
        }

        /**
         * Start periodic task that check the availability of key collaboration
         * services.
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    /**
     * Store and publish service status update. 
     * 
     * @param service Name of the service.
     * @param status Updated status for the service.
     */
    private void setServiceStatus(String service, String status){
        this.statusByService.put(service, status);
        publishServiceStatusUpdate(service, status);
    }
    
    /**
     * Get last status update for a service.
     * 
     * @param service Name of the service.
     * @return ServiceStatus Status for the service.
     * @throws org.sleuthkit.autopsy.core.UnknownServiceException
     */
    public String getServiceStatus(String service) throws UnknownServiceException {
        
        if (service == null){
            // TODO NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.existNotDir"));
            throw new UnknownServiceException("Requested service name is null");
        }
        
        String status = this.statusByService.get(service);
        if (status == null){
            // no such service
            throw new UnknownServiceException("Requested service name " + service + " is unknown");
        }
        return status;
    }
    
    /**
     * Publish an event signifying change in service status. Event is published locally.
     *
     * @param service Name of the service.
     * @param status Updated status for the event.
     */
    private void publishServiceStatusUpdate(String service, String status) {
        eventPublisher.publishLocally(new ServiceEvent(service, status, ""));
    }

    /**
     * Publish a custom event. Event is published locally.
     *
     * @param service Name of the service.
     * @param status Updated status for the event.
     * @param details Details of the event.
     */
    public void publishCustomServiceStatus(String service, String status, String details) {
        eventPublisher.publishLocally(new ServiceEvent(service, status, details));
    }    

    /**
     * Adds an event subscriber to this publisher. Subscriber will be subscribed
     * to all events from this publisher.
     *
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(serviceNames, subscriber);
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
        eventPublisher.removeSubscriber(serviceNames, subscriber);
    }

    /**
     * A Runnable task that periodically checks the availability of
     * collaboration resources (PostgreSQL server, Solr server, Active MQ
     * message broker) and reports status to the user in case of a gap in
     * service.
     */
    private final class CrashDetectionTask implements Runnable {

        // Services are assumed to be "UP" by default. Change default value in ServicesMonitor() 
        // constructor if this assumption changes.
        private boolean dbServerIsRunning = true;
        private boolean solrServerIsRunning = true;
        private boolean messageServerIsRunning = true;
        
        private final Object lock = new Object();

        /**
         * Monitor the availability of collaboration resources
         */
        @Override
        public void run() {
            synchronized (lock) {
                CaseDbConnectionInfo dbInfo = UserPreferences.getDatabaseConnectionInfo();
                if (dbInfo.canConnect()) {
                    if (!dbServerIsRunning) {
                        dbServerIsRunning = true;
                        logger.log(Level.INFO, "Connection to PostgreSQL server restored"); //NON-NLS
                        MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredDbService.notify.msg"));
                        setServiceStatus(ServiceName.REMOTE_CASE_DATABASE.toString(), ServiceStatus.UP.toString());
                    }
                } else {
                    if (dbServerIsRunning) {
                        dbServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to PostgreSQL server"); //NON-NLS
                        MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedDbService.notify.msg"));
                        setServiceStatus(ServiceName.REMOTE_CASE_DATABASE.toString(), ServiceStatus.DOWN.toString());
                    }
                }

                KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
                // TODO - do I need to check for kwsService == null?
                if (kwsService.canConnectToRemoteSolrServer()) {
                    if (!solrServerIsRunning) {
                        solrServerIsRunning = true;
                        logger.log(Level.INFO, "Connection to Solr server restored"); //NON-NLS
                        MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredSolrService.notify.msg"));                    
                        setServiceStatus(ServiceName.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.UP.toString());
                    }
                } else {
                    if (solrServerIsRunning) {
                        solrServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to Solr server"); //NON-NLS
                        MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedSolrService.notify.msg"));
                        setServiceStatus(ServiceName.REMOTE_KEYWORD_SEARCH.toString(), ServiceStatus.DOWN.toString());
                    }
                }

                MessageServiceConnectionInfo msgInfo = UserPreferences.getMessageServiceConnectionInfo();
                try {
                    ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(msgInfo.getUserName(), msgInfo.getPassword(), msgInfo.getURI());
                    Connection connection = connectionFactory.createConnection();
                    connection.start();
                    connection.close();
                    if (!messageServerIsRunning) {
                        messageServerIsRunning = true;
                        logger.log(Level.INFO, "Connection to ActiveMQ server restored"); //NON-NLS
                        MessageNotifyUtil.Notify.info(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.restoredMessageService.notify.msg"));
                        setServiceStatus(ServiceName.MESSAGING.toString(), ServiceStatus.UP.toString());
                    }
                } catch (URISyntaxException | JMSException ex) {
                    if (messageServerIsRunning) {
                        messageServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to ActiveMQ server", ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedService.notify.title"), NbBundle.getMessage(ServicesMonitor.class, "ServicesMonitor.failedMessageService.notify.msg"));
                        setServiceStatus(ServiceName.MESSAGING.toString(), ServiceStatus.DOWN.toString());
                    }
                }
            }
        }
    }
}
