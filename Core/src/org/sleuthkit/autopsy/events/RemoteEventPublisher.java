/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.events;

import java.net.URISyntaxException;
import java.util.logging.Level;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides thread-safe support for publishing events to registered subscribers
 * on other Autopsy nodes and for publishing events from other Autopsy nodes.
 * Subscribers on this node are constrained to be PropertyChangeListeners to
 * integrate with the legacy use of JavaBeans PropertyChangeEvents and
 * PropertyChangeListeners as an application event system.
 */
final class RemoteEventPublisher {

    private static final Logger logger = Logger.getLogger(RemoteEventPublisher.class.getName());
    private static final String ALL_MESSAGE_SELECTOR = "All";
    private final LocalEventPublisher localPublisher;
    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final MessageConsumer consumer;
    private final MessageReceiver receiver;

    /**
     * Constructs an object for publishing events to registered subscribers on
     * other Autopsy nodes and for publishing events from other Autopsy nodes.
     *
     * @param eventChannelName The name of the event channel to be used for
     *                         communication with other Autopsy nodes.
     * @param localPublisher   An event publisher that will be used to publish
     *                         events from other Autopsy nodes on this node.
     * @param info             Connection info for the message service.
     *
     * @throws URISyntaxException if the URI in the connection info is
     *                            malformed.
     * @throws JMSException       if the connection to the message service
     *                            cannot be made.
     */
    RemoteEventPublisher(String eventChannelName, LocalEventPublisher localPublisher, MessageServiceConnectionInfo info) throws URISyntaxException, JMSException {
        try {
            this.localPublisher = localPublisher;
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(info.getUserName(), info.getPassword(), info.getURI());
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(eventChannelName);
            producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
            consumer = session.createConsumer(topic, "events = '" + ALL_MESSAGE_SELECTOR + "'", true);
            receiver = new MessageReceiver();
            consumer.setMessageListener(receiver);
        } catch (URISyntaxException | JMSException ex) {
            logger.log(Level.SEVERE, "Failed to connect to event channel", ex);
            try {
                stop();
            } catch (JMSException ignored) {
                /**
                 * Not surprising if there is some error here, but it was worth
                 * trying to clean up.
                 */
            }
            throw ex;
        }
    }

    /**
     * Stops this publisher, causing it to disconnect from the message service.
     *
     * @throws JMSException if there is a problem closing the session or the
     *                      connection.
     */
    synchronized void stop() throws JMSException {
        if (null != producer) {
            producer.close();
        }
        if (null != consumer) {
            consumer.close();
        }
        if (null != session) {
            session.close();
        }
        if (null != connection) {
            connection.close();
        }
    }

    /**
     * Sends an event message to the message service.
     *
     * @param event The event to publish.
     */
    synchronized void publish(AutopsyEvent event) throws JMSException {
        ObjectMessage message = session.createObjectMessage();
        message.setStringProperty("events", ALL_MESSAGE_SELECTOR);
        message.setObject(event);
        producer.send(message);
    }

    /**
     * Receives event messages from the message service and publishes them
     * locally.
     */
    private final class MessageReceiver implements MessageListener {

        /**
         * Receives an event message from the message service and publishes it
         * locally. Called by a JMS thread.
         *
         * @param message The message.
         */
        @Override
        public void onMessage(Message message) {

            /**
             * TODO! Applications not running interactively also need to receive
             * remote events. We need to figure out how to handle case closing
             * issue differently.
             *
             * if (IngestManager.getInstance().isRunningInteractively()) {
             *
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, Case.currentCase is
             * set to null before Case.Event.CURRENT_CASE is published. That
             * means that clients of this class have not had the chance to close
             * their remote event channels and remote events may be received for
             * a case that is already closed.
             */
            /**
             * TODO! Remote events may not be related to current case. It can be
             * a status/priority/UI update. try { Case.getCurrentCase(); } catch
             * (IllegalStateException notUsed) { // Case is closed, do not
             * publish the event. return; } *
             */
            try {
                if (message instanceof ObjectMessage) {
                    ObjectMessage objectMessage = (ObjectMessage) message;
                    Object object = objectMessage.getObject();
                    if (object instanceof AutopsyEvent) {
                        AutopsyEvent event = (AutopsyEvent) object;
                        event.setSourceType(AutopsyEvent.SourceType.REMOTE);
                        localPublisher.publish(event);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error receiving message", ex);
            }
        }
    }
}
