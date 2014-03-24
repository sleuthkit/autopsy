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

    public boolean isIngestJobCancelled() {
        return this.ingestJob.isCancelled();
    }

    public void addFilesToPipeline(List<AbstractFile> files) {
        for (AbstractFile file : files) {
            IngestManager.getDefault().scheduleFile(ingestJob.getId(), file);
        }
    }
}
