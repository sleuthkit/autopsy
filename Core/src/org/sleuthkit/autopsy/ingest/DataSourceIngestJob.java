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
import java.util.logging.Logger;
import org.sleuthkit.datamodel.Content;

/**
 * RJCTODO
 */
class DataSourceIngestJob {

    private final long id;
    private final Content dataSource;
    private final IngestPipelines ingestPipelines;
    private final boolean processUnallocatedSpace;

    DataSourceIngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
        this.id = id;
        this.dataSource = dataSource;
        this.ingestPipelines = new IngestPipelines(id, ingestModuleTemplates);
        this.processUnallocatedSpace = processUnallocatedSpace;
    }

    long getId() {
        return id;
    }

    Content getDataSource() {
        return dataSource;
    }

    IngestPipelines getIngestPipelines() {
        return ingestPipelines;
    }

    boolean getProcessUnallocatedSpace() {
        return processUnallocatedSpace;
    }

    @Override
    public String toString() {
        return "ScheduledTask{ id=" + id + ", dataSource=" + dataSource + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        final DataSourceIngestJob other = (DataSourceIngestJob) obj;
        if (this.dataSource != other.dataSource && (this.dataSource == null || !this.dataSource.equals(other.dataSource))) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 61 * hash + (int) (this.id ^ (this.id >>> 32));
        hash = 61 * hash + Objects.hashCode(this.dataSource);
        hash = 61 * hash + Objects.hashCode(this.ingestPipelines);
        hash = 61 * hash + (this.processUnallocatedSpace ? 1 : 0);
        return hash;
    }
}
