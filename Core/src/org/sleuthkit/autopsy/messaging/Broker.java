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
import org.apache.activemq.broker.BrokerService;
import org.openide.modules.OnStart;
import org.sleuthkit.autopsy.coreutils.Logger;

//@OnStart
public class Broker implements Runnable {

    private static final Logger logger = Logger.getLogger(Publisher.class.getName());

    @Override
    public void run() {
//        try {
//            BrokerService broker = new BrokerService();
//            broker.addConnector("vm://localhost");
//            broker.setPersistent(false);
//            broker.start();
//        } catch (Exception ex) {
//            logger.log(Level.SEVERE, "Error sending message", ex);
//        }
//        
//        new Thread(new Publisher()).start();
//        new Thread(new Subscriber()).start();
    }

}
