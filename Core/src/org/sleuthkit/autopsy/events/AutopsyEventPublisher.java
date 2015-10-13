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

import java.beans.PropertyChangeListener;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.logging.Level;
import javax.jms.JMSException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides thread-safe support for publishing events to registered subscribers
 * on both this Autopsy node and other Autopsy nodes. Subscribers are
 * constrained to be PropertyChangeListeners to integrate with the legacy use of
 * JavaBeans PropertyChangeEvents and PropertyChangeListeners as an application
 * event system.
 */
public final class AutopsyEventPublisher {

    /**
     * Composed of thread-safe objects.
     */
    private static final Logger logger = Logger.getLogger(AutopsyEventPublisher.class.getName());
    private final LocalEventPublisher localPublisher;
    private RemoteEventPublisher remotePublisher;

    /**
     * Constructs an object for publishing events to registered subscribers on
     * both this Autopsy node and other Autopsy nodes. Communication with other
     * nodes is not turned on by default - call openRemoteEventChannel() after
     * construction.
     */
    public AutopsyEventPublisher() {
        localPublisher = new LocalEventPublisher();
    }

    /**
     * Opens the event channel used for publishing events to and receiving
     * events from other Autopsy nodes. Only one channel may be open at a time.
     *
     * @param channelName The name of the event channel.
     *
     * @throws AutopsyEventException if the channel was not opened.
     */
    public void openRemoteEventChannel(String channelName) throws AutopsyEventException {
        if (null != remotePublisher) {
            closeRemoteEventChannel();
        }
        try {
            remotePublisher = new RemoteEventPublisher(channelName, localPublisher, UserPreferences.getMessageServiceConnectionInfo());
        } catch (URISyntaxException | JMSException ex) {
            String message = "Failed to open remote event channel"; //NON-NLS
            logger.log(Level.SEVERE, message, ex);
            throw new AutopsyEventException(message, ex);
        }
    }

    /**
     * Closes the event channel used for publishing events to and receiving
     * events from other Autopsy nodes.
     */
    public void closeRemoteEventChannel() {
        if (null != remotePublisher) {
            try {
                remotePublisher.stop();
            } catch (JMSException ex) {
                logger.log(Level.SEVERE, "Error closing remote event channel", ex); //NON-NLS
            }
            remotePublisher = null;
        }
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        localPublisher.addSubscriber(eventNames, subscriber);
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public void addSubscriber(String eventName, PropertyChangeListener subscriber) {
        localPublisher.addSubscriber(eventName, subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The events the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        localPublisher.removeSubscriber(eventNames, subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventName  The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    public void removeSubscriber(String eventName, PropertyChangeListener subscriber) {
        localPublisher.removeSubscriber(eventName, subscriber);
    }

    /**
     * Publishes an event to this Autopsy node and other Autopsy nodes.
     *
     * @param event The event to publish.
     */
    public void publish(AutopsyEvent event) {
        publishLocally(event);
        publishRemotely(event);
    }

    /**
     * Publishes an event to this Autopsy node only.
     *
     * @param event The event to publish.
     */
    public void publishLocally(AutopsyEvent event) {
        localPublisher.publish(event);
    }

    /**
     * Publishes an event to other Autopsy nodes only.
     *
     * @param event The event to publish.
     */
    public void publishRemotely(AutopsyEvent event) {
        if (null != remotePublisher) {
            try {
                remotePublisher.publish(event);
            } catch (JMSException ex) {
                logger.log(Level.SEVERE, String.format("Failed to publish %s event remotely", event.getPropertyName()), ex); //NON-NLS
            }
        }
    }

}
