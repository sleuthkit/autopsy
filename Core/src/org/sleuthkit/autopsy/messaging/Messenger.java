/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.messaging;

import org.sleuthkit.autopsy.events.Publisher;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import java.net.URISyntaxException;
import java.util.logging.Level;
import javax.annotation.concurrent.Immutable;
import javax.jms.Connection;
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
 * Uses a message service to send and receives event messages between Autopsy
 * nodes when a multi-user case is open.
 */
@Immutable
public final class Messenger {

    private static final Logger logger = Logger.getLogger(Messenger.class.getName());
    private static final String ALL_MESSAGE_SELECTOR = "All";
    private final Publisher eventPublisher;
    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;
    private final MessageReceiver receiver;

    /**
     * Creates and starts a messenger to send and receive event messages between
     * Autopsy nodes when a multi-user case is open.
     *
     * @param topicName The name of the topic for this messenger to use for
     * communication.
     * @param eventPublisher An event publisher that will be used to publish
     * remote events locally.
     * @param info Connection info for the message service.
     * @throws URISyntaxException if the URI in the connection info is
     * malformed.
     * @throws JMSException if the connection to the message service cannot be
     * made.
     */
    public Messenger(String topicName, Publisher eventPublisher, MessageServiceConnectionInfo info) throws URISyntaxException, JMSException {
        try {
            this.eventPublisher = eventPublisher;
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(info.getUserName(), info.getPassword(), info.getURI());
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            producer = session.createProducer(topic);
            MessageConsumer consumer = session.createConsumer(topic, "events = '" + ALL_MESSAGE_SELECTOR + "'", true);
            receiver = new MessageReceiver();
            consumer.setMessageListener(receiver);
        } catch (URISyntaxException | JMSException ex) {
            logger.log(Level.SEVERE, "Failed to start", ex);
            stop();
            throw ex;
        }
    }

    /**
     * Stops this messenger, causing it to disconnect from the message service.
     */
    synchronized public void stop() {
        try {
            if (null != session) {
                session.close();
            }
        } catch (JMSException ex) {
            logger.log(Level.SEVERE, "Failed to close message service session", ex);
        }
        try {
            if (null != connection) {
                connection.close();
            }
        } catch (JMSException ex) {
            logger.log(Level.SEVERE, "Failed to close connection to message service", ex);
        }
    }

    /**
     * Sends an event message via the message service.
     *
     * @param event The event to send.
     */
    synchronized public void send(AutopsyEvent event) throws JMSException {
        ObjectMessage message = session.createObjectMessage();
        message.setStringProperty("events", ALL_MESSAGE_SELECTOR);
        message.setObject(event);
        producer.send(message);
    }

    
    /**
     * Receives event messages via the message service and publishes them
     * locally.
     */
    private final class MessageReceiver implements MessageListener {

        /**
         * Receives an event message via the message service and publishes it
         * locally. Called by a JMS thread.
         *
         * @param message The message.
         */
        @Override
        public void onMessage(Message message) {
            try {
                if (message instanceof ObjectMessage) {
                    ObjectMessage objectMessage = (ObjectMessage) message;
                    Object object = objectMessage.getObject();
                    if (object instanceof AutopsyEvent) {
                        AutopsyEvent event = (AutopsyEvent) object;
                        eventPublisher.publish(event);
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error receiving message", ex);
            }
        }

    }

}
