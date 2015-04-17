/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Provides thread-safe support for publishing events to registered subscribers.
 */
public class AutopsyEventPublisher {

    private static final Logger logger = Logger.getLogger(AutopsyEventPublisher.class.getName());
    private final Map<String, Set<AutopsyEventSubscriber>> subscribersByEvent;

    /**
     * Constructs an object for publishing events to registered subscribers.
     */
    public AutopsyEventPublisher() {
        subscribersByEvent = new HashMap<>();
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    synchronized public void addSubscriber(Collection<String> eventNames, AutopsyEventSubscriber subscriber) {
        for (String eventName : eventNames) {
            addSubscriber(eventName, subscriber);
        }
    }

    /**
     * Adds an event subscriber to this publisher.
     *
     * @param eventName The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    synchronized public void addSubscriber(String eventName, AutopsyEventSubscriber subscriber) {
        subscribersByEvent.putIfAbsent(eventName, new HashSet<>());
        Set<AutopsyEventSubscriber> subscribers = subscribersByEvent.get(eventName);
        subscribers.add(subscriber);
    }

    /**
     * Removes an event subscriber from this publisher.
     *
     * @param eventNames The events the subscriber is no longer interested in.
     * @param subscriber The subscriber to remove.
     */
    synchronized public void removeSubscriber(Collection<String> eventNames, AutopsyEventSubscriber subscriber) {
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
    synchronized public void removeSubscriber(String eventName, AutopsyEventSubscriber subscriber) {
        Set<AutopsyEventSubscriber> subscribers = subscribersByEvent.getOrDefault(eventName, null);
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
    synchronized public void publish(AutopsyEvent event) {
        Set<AutopsyEventSubscriber> subscribers = subscribersByEvent.getOrDefault(event.getPropertyName(), null);
        if (null != subscribers) {
            for (AutopsyEventSubscriber subscriber : subscribers) {
                try {
                    subscriber.receiveEvent(event);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Exception thrown by subscriber", ex);
                }
            }
        }
    }

}
