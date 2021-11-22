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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class TreeEventTimer<T> {

    public interface TreeEventHandler<T> {

        void handleEvents(Collection<T> events, boolean determinate);
    }

    private final Map<T, Long> eventTimeouts = new HashMap<>();
    private final Object timeoutLock = new Object();
    private ScheduledFuture<?> cancellableFuture;

    private final TreeEventHandler<T> eventsHandler;
    private final long timeoutMillis;
    private final long watchResolutionMillis;

    private final ScheduledThreadPoolExecutor timeoutExecutor
            = new ScheduledThreadPoolExecutor(1,
                    new ThreadFactoryBuilder().setNameFormat(DAOEventBatcher.class.getName()).build());

    public TreeEventTimer(TreeEventHandler<T> eventsHandler, long timeoutMillis, long checkResolutionMillis) {
        this.eventsHandler = eventsHandler;
        this.timeoutMillis = timeoutMillis;
        this.watchResolutionMillis = checkResolutionMillis;
    }

    private long getCurTime() {
        return System.currentTimeMillis();
    }

    private long getTimeoutTime() {
        return getCurTime() + timeoutMillis;
    }

    public void enqueueAll(List<T> events) {
        List<T> updateToIndeterminate = new ArrayList<>();

        synchronized (this.timeoutLock) {
            boolean needsWatch = this.eventTimeouts.isEmpty();
            for (T event : events) {
                // GVDTODO do we need to update all?
                this.eventTimeouts.compute(event, (k, v) -> {
                    if (v == null) {
                        updateToIndeterminate.add(event);
                    }
                    return getTimeoutTime();
                });
            }

            if (needsWatch) {
                this.cancellableFuture = this.timeoutExecutor.scheduleAtFixedRate(
                        () -> handleEventTimeouts(),
                        this.watchResolutionMillis,
                        this.watchResolutionMillis,
                        TimeUnit.MILLISECONDS);
            }
        }

        if (!updateToIndeterminate.isEmpty()) {
            this.eventsHandler.handleEvents(updateToIndeterminate, false);
        }
    }

    private void handleEventTimeouts() {
        long curTime = getCurTime();
        List<T> toUpdate = new ArrayList<>();
        synchronized (this.timeoutLock) {
            if (Thread.interrupted()) {
                return;
            }

            this.eventTimeouts.forEach((k, v) -> {
                if (v >= curTime) {
                    toUpdate.add(k);
                    this.eventTimeouts.remove(k);
                }
            });

            if (this.eventTimeouts.isEmpty()) {
                this.cancellableFuture.cancel(true);
            }
        }

        this.eventsHandler.handleEvents(toUpdate, true);
    }
}
