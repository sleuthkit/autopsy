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

public class TreeEventTimedCache<T> {
    private static final long DEFAULT_TIMEOUT_MILLIS = 2 * 60 * 1000;
    
    private final Object timeoutLock = new Object();
    private final Map<T, Long> eventTimeouts = new HashMap<>();

    private final long timeoutMillis;

    
    public TreeEventTimedCache() {
        this(DEFAULT_TIMEOUT_MILLIS);
    }
    
    public TreeEventTimedCache(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    private long getCurTime() {
        return System.currentTimeMillis();
    }

    private long getTimeoutTime() {
        return getCurTime() + timeoutMillis;
    }

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
    
    public Set<T> getEnqueued() {
        return new HashSet<>(eventTimeouts.keySet());
    }

    public Collection<T> getEventTimeouts() {
        long curTime = getCurTime();
        List<T> toUpdate;
        synchronized (this.timeoutLock) {
            toUpdate = new ArrayList<>(this.eventTimeouts.keySet());
            this.eventTimeouts.keySet().removeAll(toUpdate);
        }
        return toUpdate;
    }

    public Collection<T> flushEvents() {
        synchronized (this.timeoutLock) {
            List<T> toRet = new ArrayList<>(eventTimeouts.keySet());
            eventTimeouts.clear();
            return toRet;
        }
    }
}
