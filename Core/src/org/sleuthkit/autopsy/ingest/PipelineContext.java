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

import org.openide.util.NbBundle;

import java.util.Objects;

/**
 * Stores information about a given pipeline, which is a series of modules. 
 * This is passed into modules for their reference. 
 * 
 * @param T type of the ingest associated with the context (file or data source Content)
 * 
 */
public class PipelineContext <T extends IngestModuleAbstract> {
    private final DataSourceTask<T> task;
    
    PipelineContext(DataSourceTask<T> task) {
        this.task = task;
    }
    
    /**
     * Returns the currently scheduled task.
     * @return 
     */
    DataSourceTask<T> getDataSourceTask() {
        return task;
    }


    @Override
    public String toString() {
        return NbBundle.getMessage(this.getClass(), "PipelineContext.toString.text", task);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.task);
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
        @SuppressWarnings("unchecked")
        final PipelineContext<T> other = (PipelineContext<T>) obj;

        if (!Objects.equals(this.task, other.task)) {
            return false;
        }
        
        return true;
    }    
}
