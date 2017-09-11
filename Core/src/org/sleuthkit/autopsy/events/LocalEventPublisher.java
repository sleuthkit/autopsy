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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides thread-safe support for publishing events to registered subscribers
 * on this Autopsy node. Subscribers are constrained to be
 * PropertyChangeListeners to integrate with the legacy use of JavaBeans
 * PropertyChangeEvents and PropertyChangeListeners as an application event
 * system.
 */
@ThreadSafe
final class LocalEventPublisher {

    private static final Logger logger = Logger.getLogger(LocalEventPublisher.class.getName());
    private final Map<String, Set<PropertyChangeListener>> subscribersByEvent;

    /**
     * Constructs an object for publishing events to registered subscribers on
     * this Autopsy node.
     */
    LocalEventPublisher() {
        subscribersByEvent = new ConcurrentHashMap<>();
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    void addSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        for (String eventName : eventNames) {
            addSubscriber(eventName, subscriber);
        }
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    void addSubscriber(String eventName, PropertyChangeListener subscriber) {
        subscribersByEvent.putIfAbsent(eventName, ConcurrentHashMap.<PropertyChangeListener>newKeySet());
        Set<PropertyChangeListener> subscribers = subscribersByEvent.get(eventName);
        subscribers.add(subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The events the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    void removeSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        for (String eventName : eventNames) {
            removeSubscriber(eventName, subscriber);
        }
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    void removeSubscriber(String eventName, PropertyChangeListener subscriber) {
        Set<PropertyChangeListener> subscribers = subscribersByEvent.getOrDefault(eventName, null);
        if (null != subscribers) {
            subscribers.remove(subscriber);
        }
    }

    /**
     * Publishes an event to all registered subscribers, even if a subscriber
     * throws an exception.
     *
     * @param event The event to be published.
     */
    void publish(AutopsyEvent event) {
        Set<PropertyChangeListener> subscribers = subscribersByEvent.getOrDefault(event.getPropertyName(), null);
        if (null != subscribers) {
            subscribers.forEach((subscriber) -> {
                try {
                    subscriber.propertyChange(event);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Exception thrown by subscriber", ex); //NON-NLS
                }
            });
        }
    }

}
