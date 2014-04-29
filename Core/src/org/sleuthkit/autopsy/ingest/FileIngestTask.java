/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2014 Basis Technology Corp.
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
import java.util.logging.Level;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

final class FileIngestTask {
    final AbstractFile file;
    private final IngestJob ingestJob;

    FileIngestTask(AbstractFile file, IngestJob task) {
        this.file = file;
        this.ingestJob = task;
    }

    public IngestJob getIngestJob() {
        return ingestJob;
    }

    public AbstractFile getFile() {
        return file;
    }
    
    void execute(long threadId) {
        ingestJob.process(file);
    }

    @Override
    public String toString() { //RJCTODO: May not keep this
        try {
            return "ProcessTask{" + "file=" + file.getId() + ": " + file.getUniquePath() + "}"; // + ", dataSourceTask=" + dataSourceTask + '}';
        } catch (TskCoreException ex) {
            // RJCTODO
//            FileIngestTaskScheduler.logger.log(Level.SEVERE, "Cound not get unique path of file in queue, ", ex); //NON-NLS
        }
        return "ProcessTask{" + "file=" + file.getId() + ": " + file.getName() + '}';
    }

    /**
     * two process tasks are equal when the file/dir and modules are the
     * same this enables are not to queue up the same file/dir, modules
     * tuples into the root dir set
     *
     * @param obj
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileIngestTask other = (FileIngestTask) obj;
        if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
            return false;
        }
        IngestJob thisTask = this.getIngestJob();
        IngestJob otherTask = other.getIngestJob();
        if (thisTask != otherTask && (thisTask == null || !thisTask.equals(otherTask))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(this.file);
        hash = 47 * hash + Objects.hashCode(this.ingestJob);
        return hash;
    }    
}
