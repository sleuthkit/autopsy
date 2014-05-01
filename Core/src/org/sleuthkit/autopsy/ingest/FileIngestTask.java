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
import org.sleuthkit.datamodel.AbstractFile;

final class FileIngestTask implements IngestTask {

    private final IngestJob ingestJob;
    private final AbstractFile file;

    FileIngestTask(IngestJob task, AbstractFile file) {
        this.ingestJob = task;
        this.file = file;
    }

    public IngestJob getIngestJob() { // RJCTODO: Maybe add to interface
        return ingestJob;
    }

    public AbstractFile getFile() {
        return file;
    }

    @Override
    public void execute() throws InterruptedException {
        ingestJob.process(file);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FileIngestTask other = (FileIngestTask) obj;
        if (this.ingestJob != other.ingestJob && (this.ingestJob == null || !this.ingestJob.equals(other.ingestJob))) {
            return false;
        }
        if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(this.ingestJob);
        hash = 47 * hash + Objects.hashCode(this.file);
        return hash;
    }
}
