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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;

/**
 * Utility class that can be used by UI nodes to reduce the number of
 * potentially expensive UI refresh events.
 */
class RefreshThrottler {

    interface Refresher {

        void refresh(PropertyChangeEvent evt);
    }

    static ScheduledThreadPoolExecutor refreshExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("Node Refresh Thread").build());
    private final AtomicReference refreshTaskRef = new AtomicReference<>(null);

    private final Refresher refresher;

    private static final long MIN_SECONDS_BETWEEN_RERFESH = 5;

    private final class RefreshTask implements Runnable {

        private final PropertyChangeEvent event;

        RefreshTask(PropertyChangeEvent event) {
            this.event = event;
        }

        @Override
        public void run() {
            refresher.refresh(event);
            refreshTaskRef.set(null);
        }
    }

    private final PropertyChangeListener pcl = (PropertyChangeEvent evt) -> {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())
                || eventType.equals(IngestManager.IngestModuleEvent.CONTENT_CHANGED.toString())) {
            RefreshTask task = new RefreshTask(evt);
            if (refreshTaskRef.compareAndSet(null, task)) {
                refreshExecutor.schedule(task, MIN_SECONDS_BETWEEN_RERFESH, TimeUnit.SECONDS);
            }
        }
    };

    RefreshThrottler(Refresher r) {
        refresher = r;
    }

    void registerForIngestEvents(Set<IngestJobEvent> jobEventsOfInterest, Set<IngestModuleEvent> moduleEventsOfInterest) {
        IngestManager.getInstance().addIngestJobEventListener(jobEventsOfInterest, pcl);
        IngestManager.getInstance().addIngestModuleEventListener(moduleEventsOfInterest, pcl);
    }
    
    void unregisterEventListener() {
        IngestManager.getInstance().removeIngestJobEventListener(pcl);
        IngestManager.getInstance().removeIngestModuleEventListener(pcl);
    }
}
