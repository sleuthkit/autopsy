/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2015 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents a single file analysis task, which is defined by a file to analyze
 * and the InjestJob/Pipeline to run it on.
 */
final class FileIngestTask extends IngestTask {
    
    private final long fileId;
    private AbstractFile file = null;

    FileIngestTask(IngestJobPipeline ingestJobPipeline, AbstractFile file) {
        super(ingestJobPipeline);
        this.file = file;
        fileId = file.getId();
    }

    FileIngestTask(IngestJobPipeline ingestJobPipeline, long fileId) {
        super(ingestJobPipeline);
        this.fileId = fileId;
    }

    long getFileId() {
        return fileId;
    }

    synchronized AbstractFile getFile() throws TskCoreException {
        if (file == null) {
            file = Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(fileId);
        }
        return file;
    }

    @Override
    void execute(long threadId) throws InterruptedException {
        super.setThreadId(threadId);
        getIngestJobPipeline().process(this);
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
        IngestJobPipeline thisPipeline = getIngestJobPipeline();
        IngestJobPipeline otherPipeline = other.getIngestJobPipeline();
        if (thisPipeline != otherPipeline && (thisPipeline == null || !thisPipeline.equals(otherPipeline))) {
            return false;
        }
        return (this.fileId == other.fileId);
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(getIngestJobPipeline());
        hash = 47 * hash + Objects.hashCode(this.fileId);
        return hash;
    }
}
