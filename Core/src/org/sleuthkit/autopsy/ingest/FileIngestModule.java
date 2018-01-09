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

import org.sleuthkit.datamodel.AbstractFile;

/**
 * Interface that must be implemented by all file ingest modules. See
 * description of IngestModule for more details on interface behavior.
 */
public interface FileIngestModule extends IngestModule {

    /**
     * Processes a file. Called between calls to startUp() and shutDown(). Will
     * be called for each file in a data source.
     *
     * @param file The file to analyze.
     *
     * @return A result code indicating success or failure of the processing.
     */
    ProcessResult process(AbstractFile file);

    /**
     * Invoked by Autopsy when an ingest job is completed (either because the
     * data has been analyzed or because the job was canceled - check
     * IngestJobContext.fileIngestIsCancelled()), before the ingest module
     * instance is discarded. The module should respond by doing things like
     * releasing private resources, submitting final results, and posting a
     * final ingest message.
     */
    void shutDown();
}
