/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2014 Basis Technology Corp.
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

import java.util.List;
import java.util.Objects;
import org.sleuthkit.datamodel.Content;

// RJCTODO: Update comment
/**
 * Represents a data source-level task to schedule and analyze. 
 * Children of the data will also be scheduled. 
 *
 * @param T type of Ingest Module / Pipeline (file or data source content) associated with this task
 */
class DataSourceTask {
    private final long id;
    private final Content dataSource;
    private final IngestPipelines ingestPipelines;
    private final boolean processUnallocatedSpace;
    private long fileTasksCount = 0; // RJCTODO: Need additional counters

    DataSourceTask(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.ingestPipelines = new IngestPipelines(id, ingestModuleTemplates);
        this.processUnallocatedSpace = processUnallocatedSpace;        
    }    
    
    long getTaskId() {
        return id;
    }
    
    Content getDataSource() {
        return dataSource;
    }

    IngestPipelines getIngestPipelines() {
        return ingestPipelines;
    }
    
    /**
     * Returns value of if unallocated space should be analyzed (and scheduled)
     * @return True if pipeline should process unallocated space. 
     */
    boolean getProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    synchronized void fileTaskScheduled() {
        // RJCTODO: Implement the counters for fully, or do list scanning
        ++fileTasksCount;
    }
    
    synchronized void fileTaskCompleted() {
        // RJCTODO: Implement the counters for fully, or do list scanning
        --fileTasksCount;
        if (0 == fileTasksCount) {
            // RJCTODO
        }
    }
    
    @Override
    public String toString() {
        // RJCTODO: Improve? Is this useful?
//        return "ScheduledTask{" + "input=" + dataSource + ", modules=" + modules + '}';        
        return "ScheduledTask{ id=" + id + ", dataSource=" + dataSource + '}';
    }

    /**
     * Two scheduled tasks are equal when the content and modules are the same.
     * This enables us not to enqueue the equal schedules tasks twice into the
     * queue/set
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        // RJCTODO: Revisit this, probably don't need it
        if (obj == null) {
            return false;
        }
        
        if (getClass() != obj.getClass()) {
            return false;
        }
        
        final DataSourceTask other = (DataSourceTask)obj;
        if (this.dataSource != other.dataSource && (this.dataSource == null || !this.dataSource.equals(other.dataSource))) {
            return false;
        }
        
        return true;
    }

    @Override
    public int hashCode() {
        // RJCTODO: Probably don't need this
        int hash = 5;
        hash = 61 * hash + (int) (this.id ^ (this.id >>> 32));
        hash = 61 * hash + Objects.hashCode(this.dataSource);
        hash = 61 * hash + Objects.hashCode(this.ingestPipelines);
        hash = 61 * hash + (this.processUnallocatedSpace ? 1 : 0);
        return hash;
    }    
}
