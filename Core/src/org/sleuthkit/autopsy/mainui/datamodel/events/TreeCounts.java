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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class is in charge of tracking tree events. When an autopsy event comes
 * in that affects a tree node, the sub DAO's enqueue the event in this class
 * along with the timeout (current time + timeoutMillis). If another autopsy
 * event comes in affecting the same tree category, the timeout is reset. Events
 * are not removed from tracking until getEventTimeouts is flushEvents are
 * called. The MainDAO has a periodically running task to see if any tree events
 * have timed out, and broadcasts those events that have reached timeout.
 */
public class TreeCounts<T> {

    private static final long DEFAULT_TIMEOUT_MILLIS = 2 * 60 * 1000;

    private final Object timeoutLock = new Object();
    private final Map<T, Long> eventTimeouts = new HashMap<>();

    private final long timeoutMillis;

    /**
     * Constructor that uses default timeout duration.
     */
    public TreeCounts() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Main constructor.
     *
     * @param timeoutMillis How long to track an event before it reaches a
     *                      timeout (in milliseconds).
     */
    public TreeCounts(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Returns the current time in milliseconds.
     *
     * @return The current time in milliseconds.
     */
    private long getCurTime() {
        return System.currentTimeMillis();
    }

    /**
     * Returns the timeout time based on the current time.
     *
     * @return The current time.
     */
    private long getTimeoutTime() {
        return getCurTime() + timeoutMillis;
    }

    /**
     * Adds events to be tracked until they reach timeout.
     *
     * @param events The events to be tracked.
     *
     * @return The subset of events that were not already being tracked.
     */
    public Collection<T> enqueueAll(Collection<T> events) {
        Collection<T> updateToIndeterminate = new ArrayList<>();

        synchronized (this.timeoutLock) {
            for (T event : events) {
                this.eventTimeouts.compute(event, (k, v) -> {
                    if (v == null) {
                        updateToIndeterminate.add(event);
                    }
                    return getTimeoutTime();
                });
            }
        }

        return updateToIndeterminate;
    }

    /**
     * Returns the set of events that are currently being tracked for timeout.
     *
     * @return The events that are being tracked for timeout.
     */
    public Set<T> getEnqueued() {
        synchronized (this.timeoutLock) {
            return new HashSet<>(eventTimeouts.keySet());
        }
    }

    /**
     * Returns the events that have reached timeout based on the current time
     * stamp and removes them from tracking.
     *
     * @return The
     */
    public Collection<T> getEventTimeouts() {
        long curTime = getCurTime();
        List<T> toUpdate;
        synchronized (this.timeoutLock) {
            toUpdate = this.eventTimeouts.entrySet().stream()
                    .filter(e -> e.getValue() < curTime)
                    .map(e -> e.getKey())
                    .collect(Collectors.toList());

            this.eventTimeouts.keySet().removeAll(toUpdate);
        }
        return toUpdate;
    }

    /**
     * Returns all currently tracked events despite timeout. This method removes
     * all events from tracking.
     *
     * @return All currently tracked events.
     */
    public Collection<T> flushEvents() {
        synchronized (this.timeoutLock) {
            List<T> toRet = new ArrayList<>(eventTimeouts.keySet());
            eventTimeouts.clear();
            return toRet;
        }
    }
}
