/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.datamodel.FsContent;

/**
 * ingest service that acts on every FsContent in image
 * 
 */
public interface IngestServiceFsContent extends IngestServiceAbstract {

    public enum ProcessResult {
        UNKNOWN, //values unknown for the (service,last file)
        OK, //subsequent service continues processing the file
        STOP, //subsequent service stops processing the file unconditionally
        COND_STOP, //subsequent service decides whether to stop processing the file
        ERROR //error encountered processing the file, hint for the depending service to skip processing the file
    };
    
    /**
     * notification from manager to process file / directory.
     * Service may choose to perform an action or enqueue processing of a group of FsContents.
     * The service notifies viewers via IngestManager.postMessage()
     * and may also write results to the black-board as it is processing
     */
    public ProcessResult process(FsContent fsContent);
}
