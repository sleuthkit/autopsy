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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.progress.ProgressHandle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

// RJCTODO: Update comment
/**
 * Represents a data source-level task to schedule and analyze. 
 * Children of the data will also be scheduled. 
 *
 * @param T type of Ingest Module / Pipeline (file or data source content) associated with this task
 */
class DataSourceIngestJob {
    private final long id;
    private final Content dataSource;
    private final IngestPipelines ingestPipelines;
    private final boolean processUnallocatedSpace;
    private final Logger logger = Logger.getLogger(IngestScheduler.class.getName());
    private long fileTasksCount = 0; // RJCTODO: Need additional counters
    private int filesToIngestEstimate = 0; // RJCTODO: Rename, change to long, may synchronize
    private int filesDequeued = 0;    // RJCTODO: Rename, change to long, synchronize
    private ProgressHandle progress;

    DataSourceIngestJob(long id, Content dataSource, List<IngestModuleTemplate> ingestModuleTemplates, boolean processUnallocatedSpace) {
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
        ++filesToIngestEstimate;
    }
    
    synchronized void fileTaskCompleted() {
        // RJCTODO: Implement the counters for fully, or do list scanning
        --fileTasksCount;
        if (0 == fileTasksCount) {
            // RJCTODO
        }
    }
    
    float getEstimatedPercentComplete() {
        if (filesToIngestEstimate == 0) {
            return 0;
        }
        return ((100.f) * filesDequeued) / filesToIngestEstimate;
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
        
        final DataSourceIngestJob other = (DataSourceIngestJob)obj;
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
    
    // RJCTODO: Fix comment 
    /**
     * Get counts of ingestable files/dirs for the content input source.
     *
     * Note, also includes counts of all unalloc children files (for the fs, image, volume) even
     * if ingest didn't ask for them
     */
    private class GetFilesCountVisitor extends ContentVisitor.Default<Long> {

        @Override
        protected Long defaultVisit(Content content) {
            // Treat content as a data source (e.g., image) file or volume
            // system. Look for child file system or layout files.
            //recursion stops at fs or unalloc file
            return visitChildren(content);
        }
                
        @Override
        public Long visit(FileSystem fs) {
            // Query the case database to get a count of the files in the 
            // file system.
            try {
                StringBuilder sqlWhereClause = new StringBuilder();
                sqlWhereClause.append("( (fs_obj_id = ").append(fs.getId());
                sqlWhereClause.append(") )");
                sqlWhereClause.append(" AND ( (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_REG.getValue());
                sqlWhereClause.append(") OR (meta_type = ").append(TskData.TSK_FS_META_TYPE_ENUM.TSK_FS_META_TYPE_DIR.getValue());
                sqlWhereClause.append(" AND (name != '.') AND (name != '..')");
                sqlWhereClause.append(") )");
                String query = sqlWhereClause.toString();
                SleuthkitCase caseDatabase = Case.getCurrentCase().getSleuthkitCase();
                return caseDatabase.countFilesWhere(query);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get count of all files in file system named " + fs.getName(), ex);
                return 0L;
            }
        }

        @Override
        public Long visit(LayoutFile lf) {
            // Layout files are not file system files. They are 
            // "virtual files" created from blocks of data such as unallocated
            // space. Count as single files. 
            return 1L;
        }

        private long visitChildren(Content content) {
            long count = 0;
            try {
                List<Content> children = content.getChildren();
                if (children.size() > 0) {
                    for (Content child : children) {
                        count += child.accept(this);
                    }
                } else {
                    count = 1;
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get children of content named " + content.getName(), ex);
            }
            return count;
        }
    }    
}
