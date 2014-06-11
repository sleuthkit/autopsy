/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;

class IngestTask {
    
    private final IngestJob job;
    private final ProgressSnapshots snapshots;
    private long threadId;

    IngestTask(IngestJob job, ProgressSnapshots snapshots) {
        this.job = job;
        this.snapshots = snapshots;
    }

    IngestJob getIngestJob() {
        return job;
    }
    
    void updateProgressStatus(String ingestModuleDisplayName, AbstractFile file) {
        snapshots.update(new ProgressSnapshot(threadId, job.getDataSource(), ingestModuleDisplayName, file));
    }
        
    void execute(long threadId) throws InterruptedException {
        this.threadId = threadId;
    }
    
    public static final class ProgressSnapshot {
        private final long threadId;
        private final Content dataSource;
        private final String ingestModuleDisplayName;
        private final AbstractFile file;
        private final LocalTime startTime;
        
        private ProgressSnapshot(long threadId, Content dataSource, String ingestModuleDisplayName, AbstractFile file) {
            this.threadId = threadId;
            this.dataSource = dataSource;
            this.ingestModuleDisplayName = ingestModuleDisplayName;
            this.file = file;
            startTime = LocalTime.now();
        }
        
        long getThreadId() {
            return threadId;
        }
        
        Content getDataSource() {
            return dataSource;
        }
        
        String getModuleDisplayName() {
            return ingestModuleDisplayName;
        }
        
        AbstractFile getFile() {
            return file;
        }
        
        LocalTime getStartTime() {
            return startTime;
        }
    }
    
    static final class ProgressSnapshots {
        private final ConcurrentHashMap<Long, IngestTask.ProgressSnapshot> snapshots = new ConcurrentHashMap<>(); // Maps ingest thread ids to progress snapshots.    
    
        void update(ProgressSnapshot snapshot) {
            snapshots.put(snapshot.getThreadId(), snapshot);
        }
        
        List<ProgressSnapshot> getSnapshots() {
            return new ArrayList(snapshots.values());
        }
    }
}
