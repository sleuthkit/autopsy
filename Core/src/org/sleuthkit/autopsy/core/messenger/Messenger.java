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
package org.sleuthkit.autopsy.core.messenger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.sleuthkit.autopsy.coreutils.Logger;

public final class Messenger implements MessageListener {

    private static final String ALL_MESSAGE_SELECTOR = "All";
    private static final Logger logger = Logger.getLogger(Messenger.class.getName());
    private final String caseName;
    private final HashMap<String, List<AutopsyEventMessageListener>> listeners;
    private Connection connection;
    private Session session;
    private MessageProducer producer;

    Messenger(String caseName) {
        this.caseName = caseName;
        listeners = new HashMap<>();
    }

    void start(MessageServiceConnectionInfo info) {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(info.getUserName(), info.getPassword(), info.getURI());
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(caseName);
            producer = session.createProducer(topic);
            MessageConsumer consumer = session.createConsumer(topic, "events = '" + ALL_MESSAGE_SELECTOR + "'", true);
            consumer.setMessageListener(this);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Startup error", ex);
        }
    }

    void stop() {
        try {
            session.close();
            connection.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Shutdown error", ex);
        }
    }

    public void send(AutopsyEvent event) {
        try {
            ObjectMessage message = session.createObjectMessage();
            message.setStringProperty("events", ALL_MESSAGE_SELECTOR);
            message.setObject(event);
            producer.send(message);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Publishing error", ex);
        }
    }

    public void addEventMessageListener(String eventName, AutopsyEventMessageListener listener) {
        // RJCTODO: For Case, we will maintain a static listing of listeners, to be added and removed 
        // as each new Case messenger comes on line. This will allow IngestManager to register once, statically.
        // Case can also expose a send method(). Essnetially, this makes the Case class in control,
        // without requiring this code to be stuffed into that package.
        if (!listeners.containsKey(eventName)) {
            listeners.put(eventName, new ArrayList<>());
        }
        List<AutopsyEventMessageListener> eventListeners = listeners.get(eventName);
        eventListeners.add(listener);
    }

    public void removeEventMessageListener(String eventName, AutopsyEventMessageListener listener) {
        // RJCTODO   
    }

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage) message;
                Object object = objectMessage.getObject();
                if (object instanceof AutopsyEvent) {
                    AutopsyEvent event = (AutopsyEvent) object;
                    List<AutopsyEventMessageListener> eventListeners = listeners.getOrDefault(event.getPropertyName(), null);
                    if (null != eventListeners) {
                        for (AutopsyEventMessageListener listener : eventListeners) {
                            try {
                                listener.receive(event);
                            } catch (Exception ex) {
                                logger.log(Level.SEVERE, "Exception thrown by AutopsyEventMessageListener", ex);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Publishing error", ex);
        }

    }

}
