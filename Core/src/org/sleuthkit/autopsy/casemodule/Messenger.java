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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.sleuthkit.autopsy.coreutils.Logger;

class Messenger implements PropertyChangeListener, MessageListener {

    private static final String BROKER_URL = "tcp://10.1.8.234:61616";
    private static final Logger logger = Logger.getLogger(Messenger.class.getName());
    private final String caseName;
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    Messenger(String caseName) {
        this.caseName = caseName;
    }

    void start() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(BROKER_URL);
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(caseName);
            producer = session.createProducer(topic);

            MessageConsumer consumer = session.createConsumer(topic, "event = '" + Case.Events.DATA_SOURCE_ADDED.toString() + "'", false);
            consumer.setMessageListener(this);

            Case.addPropertyChangeListener(this);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Startup error", ex);
        }
    }

    void stop() {
        Case.removePropertyChangeListener(this);
        try {
            session.close();
            connection.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Shutdown error", ex);
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        switch (Case.Events.valueOf(event.getPropertyName())) {
            case DATA_SOURCE_ADDED:
                send();
                break;
        }
    }

    private void send() {
        try {
            TextMessage message = session.createTextMessage();
            message.setStringProperty("event", Case.Events.DATA_SOURCE_ADDED.toString());
            producer.send(message);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Publishing error", ex);
        }
    }

    @Override
    public void onMessage(Message message) {
        try {
            Case.getCurrentCase().notifyNewDataSource(null);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Publishing error", ex);
        }

    }

}
