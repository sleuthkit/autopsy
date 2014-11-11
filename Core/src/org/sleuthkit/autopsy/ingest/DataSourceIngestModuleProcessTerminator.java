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

/**
 * An ExecUtil process terminator for data source ingest modules that checks for
 * ingest job cancellation.
 */
public final class DataSourceIngestModuleProcessTerminator extends IngestModuleTimedProcessTerminator {

    /**
     * Constructs a process terminator for a data source ingest module.
     * Uses default process execution timeout value.
     *
     * @param context The ingest job context for the ingest module.
     */
    public DataSourceIngestModuleProcessTerminator(IngestJobContext context) {
        super(context);
    }

    /**
     * Constructs a process terminator for a data source ingest module. 
     *
     * @param context The ingest job context for the ingest module.
     * @param timeout_sec Process execution timeout value (seconds)
     */
    public DataSourceIngestModuleProcessTerminator(IngestJobContext context, long timeout_sec) {
        super(context, timeout_sec);
    }    
    
    /**
     * @return true if process should be terminated, false otherwise
     */
    @Override
    public boolean shouldTerminateProcess() {
        
        if (didProcessTimeOut())
            return true;
        
        return this.context.dataSourceIngestIsCancelled();
    }

}
