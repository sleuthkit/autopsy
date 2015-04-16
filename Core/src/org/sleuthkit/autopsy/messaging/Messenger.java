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
package org.sleuthkit.autopsy.messaging;

import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import java.net.URISyntaxException;
import java.util.logging.Level;
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
public final class Messenger implements MessageListener {

    private static final Logger logger = Logger.getLogger(Messenger.class.getName());
    private static final String ALL_MESSAGE_SELECTOR = "All";
    private final String caseName;
    private final AutopsyEventPublisher eventPublisher;
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    /**
     * Creates a messenger to send and receive event messages between Autopsy
     * nodes when a multi-user case is open.
     *
     * @param caseName The name of the multi-user case.
     * @param eventPublisher An event publisher that will be used to publish
     * remote events locally.
     */
    public Messenger(String caseName, AutopsyEventPublisher eventPublisher) {
        this.caseName = caseName;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Starts this messenger, causing it to connect to the message service.
     *
     * @param info Connection info for the message service.
     * @throws URISyntaxException if the URI in the connection info is
     * malformed.
     * @throws JMSException if the connection to the message service cannot be
     * made.
     */
    public void start(MessageServiceConnectionInfo info) throws URISyntaxException, JMSException {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(info.getUserName(), info.getPassword(), info.getURI());
        connection = connectionFactory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = session.createTopic(caseName);
        producer = session.createProducer(topic);
        MessageConsumer consumer = session.createConsumer(topic, "events = '" + ALL_MESSAGE_SELECTOR + "'", true);
        consumer.setMessageListener(this);
    }

    /**
     * Stops this messenger, causing it to disconnect from the message service.
     */
    public void stop() {
        try {
            session.close();
            connection.close();
        } catch (JMSException ex) {
            logger.log(Level.SEVERE, "Failed to close connection to message service", ex);
        }
    }

    /**
     * Sends an event message via the message service.
     *
     * @param event The event to send.
     */
    public void send(AutopsyEvent event) throws JMSException {
        ObjectMessage message = session.createObjectMessage();
        message.setStringProperty("events", ALL_MESSAGE_SELECTOR);
        message.setObject(event);
        producer.send(message);
    }

    /**
     * Receives an event message via the message service and publishes it
     * locally.
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
