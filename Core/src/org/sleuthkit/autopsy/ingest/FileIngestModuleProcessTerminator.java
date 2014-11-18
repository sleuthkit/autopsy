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

import org.sleuthkit.autopsy.coreutils.ExecUtil;

/**
 * A timed process terminator for data source ingest modules. Checks for
 * ingest job cancellation as well.
 */
public final class FileIngestModuleProcessTerminator extends ExecUtil.TimedProcessTerminator {

    public final IngestJobContext context;
    
    /**
     * Constructs a process terminator for a file ingest module.
     * Uses default process execution timeout value.
     *
     * @param context The ingest job context for the ingest module.
     */
    public FileIngestModuleProcessTerminator(IngestJobContext context) {
        super();
        this.context = context;
    }
    
    /**
     * Constructs a process terminator for a file ingest module. 
     *
     * @param context The ingest job context for the ingest module.
     * @param timeoutSec Process execution timeout value (seconds)
     */
    public FileIngestModuleProcessTerminator(IngestJobContext context, long timeoutSec) {
        super(timeoutSec);
        this.context = context;
    }      

    /**
     * @return true if process should be terminated, false otherwise
     */
    @Override
    public boolean shouldTerminateProcess() {

        if (this.context.fileIngestIsCancelled())
            return true;
        
        return super.shouldTerminateProcess();
    }

}
