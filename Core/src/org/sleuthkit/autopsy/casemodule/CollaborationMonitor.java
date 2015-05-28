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
package org.sleuthkit.autopsy.casemodule;

import org.sleuthkit.autopsy.events.AutopsyEventPublisher;

/**
 * RJCTODO: Comment this - When instantiated by a case, needs to send a here I
 * am message. - Needs to send heart beat and state change messages with state
 * in message. - When destroyed, needs to send a farewell message. - Needs to
 * listen for messages from other monitors. -- Start and finish progress bars as
 * other monitors notify of state changes. -- Keep track of other monitors and
 * states/progress bars. -- Periodic task to flush out stuff if heartbeats not
 * received.
 */
final class CollaborationMonitor {

    private static final String EVENT_CHANNEL_NAME = "%s-Collaboration-Monitor-Events";
    private final AutopsyEventPublisher eventPublisher;

    /**
     * RJCTODO
     */
    CollaborationMonitor() {
        this.eventPublisher = new AutopsyEventPublisher();
        try {
            this.eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, Case.getCurrentCase().getName()));
            this.eventPublisher.addSubscriber(EVENT_CHANNEL_NAME, null); // RJCTODO: Subscribe to monitor events
        } catch (Exception ex) {
            // RJCTODO:
        }
    }

}
