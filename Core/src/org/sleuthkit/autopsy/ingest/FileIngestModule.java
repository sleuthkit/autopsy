/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
     * IMPORTANT: In addition to returning ProcessResult.OK or
     * ProcessResult.ERROR, modules should log all errors using methods provided
     * by the org.sleuthkit.autopsy.coreutils.Logger class. Log messages should
     * include the name and object ID of the data being processed and any other
     * information that would be useful for debugging. If an exception has been
     * caught by the module, the exception should be sent to the logger along
     * with the log message so that a stack trace will appear in the application
     * log.
     *
     * @param file The file to analyze.
     *
     * @return A result code indicating success or failure of the processing.
     */
    ProcessResult process(AbstractFile file);

}
