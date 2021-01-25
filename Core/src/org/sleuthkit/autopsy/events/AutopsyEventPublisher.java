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

/**
 * Provides thread-safe support for publishing events to registered subscribers
 * on both this Autopsy node and other Autopsy nodes. Subscribers are required
 * to be PropertyChangeListeners to integrate with the legacy use of JavaBeans
 * PropertyChangeEvents and PropertyChangeListeners as an application event
 * system.
 */
@ThreadSafe
public final class AutopsyEventPublisher {

    private static final Logger logger = Logger.getLogger(AutopsyEventPublisher.class.getName());
    private static final int MAX_REMOTE_EVENT_PUBLISH_TRIES = 1;
    private final LocalEventPublisher localPublisher; // LocalEventPublisher is thread-safe
    @GuardedBy("this")
    private RemoteEventPublisher remotePublisher;
    @GuardedBy("this")
    private String currentChannelName;

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
     * Opens an event channel used for publishing events to, and receiving
     * events from, other Autopsy nodes. Only one channel may be open at a time.
     *
     * @param channelName The name of the event channel.
     *
     * @throws AutopsyEventException if the channel was not opened.
     */
    public synchronized void openRemoteEventChannel(String channelName) throws AutopsyEventException {
        currentChannelName = channelName;
        if (null != remotePublisher) {
            closeRemoteEventChannel();
        }
        try {
            remotePublisher = new RemoteEventPublisher(channelName, localPublisher, UserPreferences.getMessageServiceConnectionInfo());
        } catch (URISyntaxException | JMSException ex) {
            String message = "Failed to open remote event channel"; //NON-NLS
            logger.log(Level.SEVERE, message, ex);
            throw new AutopsyEventException(message, ex);
        } catch (UserPreferencesException ex) {
            String message = "Error accessing messaging service connection info"; //NON-NLS
            logger.log(Level.SEVERE, message, ex);
            throw new AutopsyEventException(message, ex);
        }
    }

    /**
     * Closes the event channel used for publishing events to and receiving
     * events from other Autopsy nodes.
     */
    public synchronized void closeRemoteEventChannel() {
        stopRemotePublisher();
        currentChannelName = null;
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
    public synchronized void publishRemotely(AutopsyEvent event) {
        if (null != currentChannelName) {
            boolean published = false;
            int tryCount = 1;

            while (false == published && tryCount <= MAX_REMOTE_EVENT_PUBLISH_TRIES) {
                try {
                    if (null == remotePublisher) {
                        openRemoteEventChannel(currentChannelName);
                    }
                    remotePublisher.publish(event);
                    published = true;
                } catch (AutopsyEventException | JMSException ex) {
                    logger.log(Level.SEVERE, String.format("Failed to publish %s using channel %s (tryCount = %s)", event.getPropertyName(), currentChannelName, tryCount), ex); //NON-NLS
                    stopRemotePublisher();
                    ++tryCount;
                }
            }
        }
    }

    /**
     * Stops the remote event publisher, but does not reset the current channel
     * name.
     */
    private synchronized void stopRemotePublisher() {
        if (null != remotePublisher) {
            try {
                remotePublisher.stop();
            } catch (JMSException ex) {
                logger.log(Level.SEVERE, String.format("Error closing remote event publisher for channel %s", currentChannelName), ex); //NON-NLS
            }
            remotePublisher = null;
        }
    }

}
