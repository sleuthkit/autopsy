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
 *
 * Context associated with ingest module instance.
 * Context may contain details in which the module runs, such as data, 
 * configuration, and the encompassing ingest task.
 * 
 * Context contains the task ingest was scheduled with.
 * 
 * @param T type of the ingest associated with the context (file or image)
 * 
 */
public class PipelineContext <T extends IngestModuleAbstract> {
    private final ScheduledImageTask<T> task;
    private final boolean processUnalloc;
    
    PipelineContext(ScheduledImageTask<T> task, boolean processUnalloc) {
        this.task = task;
        this.processUnalloc = processUnalloc;
    }
    
    

    ScheduledImageTask<T> getScheduledTask() {
        return task;
    }


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
