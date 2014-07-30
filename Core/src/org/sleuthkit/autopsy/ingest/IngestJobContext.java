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

import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Provides an instance of an ingest module with services specific to the ingest
 * job and the ingest pipeline of which the module is a part.
 */
public final class IngestJobContext {

    private final IngestJob ingestJob;

    IngestJobContext(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    /**
     * Gets the identifier of the ingest job associated with this context.
     *
     * @return The ingest job identifier.
     */
    public long getJobId() {
        return this.ingestJob.getId();
    }

    /**
     * Determines whether the ingest job associated with the current context has
     * been canceled.
     *
     * @return True if the job has been canceled, false otherwise.
     */
    public boolean isJobCancelled() {
        return this.ingestJob.isCancelled();
    }

    /**
     * Adds one or more files to the files to be passed through the file ingest
     * pipeline of the ingest job associated with the current context.
     *
     * @param files The files to be processed by the file ingest pipeline.
     */
    public void scheduleFiles(List<AbstractFile> files) {
        for (AbstractFile file : files) {
            try {
                IngestScheduler.getInstance().scheduleAdditionalFileIngestTask(ingestJob, file);
            } catch (InterruptedException ex) {
                // Ultimately, this method is called by ingest task execution
                // threads running ingest module code. Handle the unexpected
                // interrupt here rather
                Thread.currentThread().interrupt();
                Logger.getLogger(IngestJobContext.class.getName()).log(Level.SEVERE, "File task scheduling unexpectedly interrupted", ex); //NON-NLS
            }
        }
    }
}
