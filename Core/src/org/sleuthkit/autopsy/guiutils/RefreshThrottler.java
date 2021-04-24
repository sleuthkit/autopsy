/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.guiutils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * Utility class that can be used by UI nodes to reduce the number of
 * potentially expensive UI refresh events when DATA_ADDED, CONTENT_CHANGED, and
 * FILE_DONE ingest manager events are received.
 */
public class RefreshThrottler {

    /**
     * The Refresher interface needs to be implemented by ChildFactory instances
     * that wish to take advantage of throttled refresh functionality.
     */
    public interface Refresher {

        /**
         * The RefreshThrottler calls this method when the RefreshTask runs.
         *
         */
        void refresh();

        /**
         * Determine whether the given event should result in a refresh.
         *
         * @param evt
         *
         * @return true if event should trigger a refresh, otherwise false.
         */
        boolean isRefreshRequired(PropertyChangeEvent evt);
    }

    static ScheduledThreadPoolExecutor refreshExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("Node Refresh Thread").build());
    // Keep a thread safe reference to the current refresh task (if any)
    private final AtomicReference<RefreshTask> refreshTaskRef;

    // The factory instance that will be called when a refresh is due.
    private final Refresher refresher;

    private static final long MIN_SECONDS_BETWEEN_REFRESH = 5;

    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(
            IngestManager.IngestModuleEvent.DATA_ADDED,
            IngestManager.IngestModuleEvent.CONTENT_CHANGED,
            IngestManager.IngestModuleEvent.FILE_DONE);

    /**
     * A RefreshTask is scheduled to run when an event arrives and there isn't
     * one already scheduled.
     */
    private final class RefreshTask implements Runnable {

        @Override
        public void run() {
            // Call refresh on the factory
            refresher.refresh();
            // Clear the refresh task reference
            refreshTaskRef.set(null);
        }
    }

    /**
     * PropertyChangeListener that reacts to DATA_ADDED and CONTENT_CHANGED
     * events and schedules a refresh task if one is not already scheduled.
     */
    private final PropertyChangeListener pcl;

    public RefreshThrottler(Refresher r) {
        this.refreshTaskRef = new AtomicReference<>(null);
        refresher = r;

        pcl = (PropertyChangeEvent evt) -> {
            String eventType = evt.getPropertyName();
            if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())
                    || eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())
                    || eventType.equals(IngestManager.IngestModuleEvent.FILE_DONE.toString())) {
                if (!refresher.isRefreshRequired(evt)) {
                    return;
                }

                RefreshTask task = new RefreshTask();
                if (refreshTaskRef.compareAndSet(null, task)) {
                    refreshExecutor.schedule(task, MIN_SECONDS_BETWEEN_REFRESH, TimeUnit.SECONDS);
                }
            }
        };
    }

    /**
     * Set up listener for ingest module events of interest.
     */
    public void registerForIngestModuleEvents() {
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, pcl);
    }

    /**
     * Remove ingest module event listener.
     */
    public void unregisterEventListener() {
        IngestManager.getInstance().removeIngestModuleEventListener(pcl);
    }
}
