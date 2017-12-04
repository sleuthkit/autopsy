/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.jms.JMSException;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;


@ThreadSafe
public class DummyEventPublisher extends AutopsyEventPublisher {

    /**
     * Constructs an object for publishing events to registered subscribers on
     * both this Autopsy node and other Autopsy nodes. Communication with other
     * nodes is not turned on by default - call openRemoteEventChannel() after
     * construction.
     */
    public DummyEventPublisher() {;
    }

    /**
     * Opens an event channel used for publishing events to, and receiving
     * events from, other Autopsy nodes. Only one channel may be open at a time.
     *
     * @param channelName The name of the event channel.
     *
     * @throws AutopsyEventException if the channel was not opened.
     */
    public synchronized void openRemoteEventChannel(String channelName) throws AutopsyEventException {
    }

    /**
     * Closes the event channel used for publishing events to and receiving
     * events from other Autopsy nodes.
     */
    public synchronized void closeRemoteEventChannel() {
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(String eventName, PropertyChangeListener subscriber) {
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The events the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventName  The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(String eventName, PropertyChangeListener subscriber) {
    }

    /**
     * Publishes an event to this Autopsy node and other Autopsy nodes.
     *
     * @param event The event to publish.
     */
    public void publish(AutopsyEvent event) {
    }

    /**
     * Publishes an event to this Autopsy node only.
     *
     * @param event The event to publish.
     */
    public void publishLocally(AutopsyEvent event) {
    }

    /**
     * Publishes an event to other Autopsy nodes only.
     *
     * @param event The event to publish.
     */
    public synchronized void publishRemotely(AutopsyEvent event) {
    }

    /**
     * Stops the remote event publisher, but does not reset the current channel
     * name.
     */
    private synchronized void stopRemotePublisher() {
    }

}
