/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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

import java.util.Objects;


/**
 * Stores information about a given pipeline, which is a series of modules. 
 * This is passed into modules for their reference. 
 * 
 * @param T type of the ingest associated with the context (file or data source Content)
 * 
 */
public class PipelineContext <T extends IngestModuleAbstract> {
    private final ScheduledTask<T> task;
    private final boolean processUnalloc;
    
    PipelineContext(ScheduledTask<T> task, boolean processUnalloc) {
        this.task = task;
        this.processUnalloc = processUnalloc;
    }
    
    

    /**
     * Returns the currently scheduled task.
     * @return 
     */
    ScheduledTask<T> getScheduledTask() {
        return task;
    }


    /**
     * Returns value of if unallocated space is going to be scheduled.
     * @return True if pipeline is processing unallocated space. 
     */
    boolean isProcessUnalloc() {
        return processUnalloc;
    }

    @Override
    public String toString() {
        return "pipelineContext{" + "task=" + task + ", processUnalloc=" + processUnalloc + '}';
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.task);
        hash = 53 * hash + (this.processUnalloc ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PipelineContext<T> other = (PipelineContext<T>) obj;

        if (!Objects.equals(this.task, other.task)) {
            return false;
        }
        if (this.processUnalloc != other.processUnalloc) {
            return false;
        }
        return true;
    }
    
  
    
}
