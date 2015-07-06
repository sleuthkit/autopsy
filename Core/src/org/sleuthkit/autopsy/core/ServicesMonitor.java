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

    private static final Set<String> serviceNames = Stream.of(ServicesMonitor.Service.values())
            .map(ServicesMonitor.Service::toString)
            .collect(Collectors.toSet());

    /**
     * List of services that are being monitored.
     */
    public enum Service {

        /**
         * Property change event fired when ....TODO.... The old value of the
         * PropertyChangeEvent object is set to the ingest job id, and the new
         * value is set to null.
         */
        REMOTE_CASE_DATABASE,
        REMOTE_KEYWORD_SEARCH,
        MESSAGING
    };

    public enum ServiceStatus {

        UP,
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

        /**
         * Start periodic task that check the availability of key collaboration
         * services.
         */
        periodicTasksExecutor = new ScheduledThreadPoolExecutor(NUMBER_OF_PERIODIC_TASK_THREADS, new ThreadFactoryBuilder().setNameFormat(PERIODIC_TASK_THREAD_NAME).build());
        periodicTasksExecutor.scheduleAtFixedRate(new CrashDetectionTask(), CRASH_DETECTION_INTERVAL_MINUTES, CRASH_DETECTION_INTERVAL_MINUTES, TimeUnit.MINUTES);
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
     * Publish an event signifying change in remote database (e.g. PostgreSQL)
     * service status.
     *
     * @param status Updated status for the event.
     */
    private void publishRemoteDatabaseStatusChange(ServiceStatus status) {
        eventPublisher.publishLocally(new ServiceEvent(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), null, status.toString()));
    }

    /**
     * Publish an event signifying change in remote database (e.g. PostgreSQL)
     * service status.
     *
     * @param status Updated status for the event.
     */
    private void publishRemoteKeywordSearchStatusChange(ServiceStatus status) {
        eventPublisher.publishLocally(new ServiceEvent(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), null, status.toString()));
    }

    /**
     * Publish an event signifying change in remote database (e.g. PostgreSQL)
     * service status.
     *
     * @param status Updated status for the event.
     */
    private void publishMessagingStatusChange(ServiceStatus status) {
        eventPublisher.publishLocally(new ServiceEvent(ServicesMonitor.Service.REMOTE_CASE_DATABASE.toString(), null, status.toString()));
    }

    /**
     * Publish a custom event.
     *
     * @param service Name of the service.
     * @param status Updated status for the event.
     * @param details Details of the event.
     */
    public void publishServiceStatus(String service, String status, String details) {
        eventPublisher.publishLocally(new ServiceEvent(service, status, details));
    }

    /**
     * A Runnable task that periodically checks the availability of
     * collaboration resources (PostgreSQL server, Solr server, Active MQ
     * message broker) and reports status to the user in case of a gap in
     * service.
     */
    private final class CrashDetectionTask implements Runnable {

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
                        //MessageNotifyUtil.Notify.info(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredDbService.notify.msg"));
                        publishRemoteDatabaseStatusChange(ServiceStatus.UP);
                    }
                } else {
                    if (dbServerIsRunning) {
                        dbServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to PostgreSQL server"); //NON-NLS
                        //MessageNotifyUtil.Notify.error(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedDbService.notify.msg"));
                        publishRemoteDatabaseStatusChange(ServiceStatus.DOWN);
                    }
                }

                KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
                // TODO - do I need to check for kwsService == null?
                if (kwsService.canConnectToRemoteSolrServer()) {
                    if (!solrServerIsRunning) {
                        solrServerIsRunning = true;
                        logger.log(Level.INFO, "Connection to Solr server restored"); //NON-NLS
                        //MessageNotifyUtil.Notify.info(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredSolrService.notify.msg"));                    
                        publishRemoteKeywordSearchStatusChange(ServiceStatus.UP);
                    }
                } else {
                    if (solrServerIsRunning) {
                        solrServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to Solr server"); //NON-NLS
                        //MessageNotifyUtil.Notify.error(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedSolrService.notify.msg"));
                        publishRemoteKeywordSearchStatusChange(ServiceStatus.DOWN);
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
                        //MessageNotifyUtil.Notify.info(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.restoredMessageService.notify.msg"));
                        publishMessagingStatusChange(ServiceStatus.UP);
                    }
                } catch (URISyntaxException | JMSException ex) {
                    if (messageServerIsRunning) {
                        messageServerIsRunning = false;
                        logger.log(Level.SEVERE, "Failed to connect to ActiveMQ server", ex); //NON-NLS
                        //MessageNotifyUtil.Notify.error(NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedService.notify.title"), NbBundle.getMessage(CollaborationMonitor.class, "CollaborationMonitor.failedMessageService.notify.msg"));
                        publishMessagingStatusChange(ServiceStatus.DOWN);
                    }
                }
            }
        }

    }
}
