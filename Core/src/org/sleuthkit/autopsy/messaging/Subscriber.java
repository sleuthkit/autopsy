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

import java.util.logging.Level;
import javax.jms.Connection;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 *
 */
public class Subscriber implements Runnable, ExceptionListener {

    private static final Logger logger = Logger.getLogger(Publisher.class.getName());

    @Override
    public void run() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost");
            Connection connection = connectionFactory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic("test");
            MessageConsumer consumer = session.createConsumer(topic);
            Message message = consumer.receive(1000);
            if (message instanceof TextMessage) {
                TextMessage textMessage = (TextMessage) message;
                String text = textMessage.getText();
                logger.log(Level.INFO, "Received: {0}", text);
            } else {
                logger.log(Level.INFO, "Received: {0}", message);
            }
            consumer.close();
            session.close();
            connection.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Subscribing error", ex);
        }
    }

    @Override
    public void onException(JMSException jmse) {
        System.out.println("JMS Exception occured.  Shutting down client.");
    }

}
