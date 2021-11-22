/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.datamodel.events;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *
 * Handles refreshes in DAOs based on incoming events handling throttles
 */
public class DAOEventBatcher<T> {

    /**
     * The Refresher interface needs to be implemented by ChildFactory instances
     * that wish to take advantage of throttled refresh functionality.
     */
    public interface BatchedEventsHandler<T> {

        /**
         * Handles a list of aggregated events.
         *
         * @param events The events to handle.
         */
        void handle(Collection<T> events);
    }

    private final ScheduledThreadPoolExecutor refreshExecutor
            = new ScheduledThreadPoolExecutor(1,
                    new ThreadFactoryBuilder().setNameFormat(DAOEventBatcher.class.getName()).build());

    private Set<T> aggregateEvents = new HashSet<>();
    private Object eventListLock = new Object();
    private boolean isRunning = false;

    private final BatchedEventsHandler<T> eventsHandler;
    private final long batchMillis;

    public DAOEventBatcher(BatchedEventsHandler<T> eventsHandler, long batchMillis) {
        this.eventsHandler = eventsHandler;
        this.batchMillis = batchMillis;
    }

    /**
     * Queues an event to be fired as a part of a time-windowed batch.
     *
     * @param event The event.
     */
    public void queueEvent(T event) {
        synchronized (this.eventListLock) {
            this.aggregateEvents.add(event);
            verifyRunning();
        }
    }

    /**
     * Starts up throttled event runner if not currently running.
     */
    private void verifyRunning() {
        synchronized (this.eventListLock) {
            if (!this.isRunning) {
                refreshExecutor.schedule(() -> fireEvents(), this.batchMillis, TimeUnit.MILLISECONDS);
                this.isRunning = true;
            }
        }
    }

    /**
     * Queues an event to be fired as a part of a time-windowed batch.
     *
     * @param events The events.
     */
    public void enqueueAllEvents(Collection<T> events) {
        synchronized (this.eventListLock) {
            this.aggregateEvents.addAll(events);
            verifyRunning();
        }
    }

    /**
     * Fires all events and clears batch.
     */
    private void fireEvents() {
        Collection<T> evtsToFire;
        synchronized (this.eventListLock) {
            evtsToFire = this.aggregateEvents;
            this.aggregateEvents = new HashSet<>();
            this.isRunning = false;
        }

        this.eventsHandler.handle(evtsToFire);
    }
}
