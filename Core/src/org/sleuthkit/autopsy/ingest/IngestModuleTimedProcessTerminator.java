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

import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.sleuthkit.autopsy.coreutils.ExecUtil;

/**
 * An ExecUtil process terminator for data source ingest modules that checks for
 * ingest job cancellation.
 */
public class IngestModuleTimedProcessTerminator implements ExecUtil.ProcessTerminator {

    public final IngestJobContext context;
    public static long creationTime_sec;    // time when TimedProcessTerminator was constructed
    public static final long DEFAULT_TIMEOUT = 172800;   // 48 hours
    public static final TimeUnit DEFAULT_TIMEOUT_UNITS = TimeUnit.SECONDS;
    
    /**
     * Constructs a process terminator for a data source ingest module.
     *
     * @param context The ingest job context for the ingest module.
     */
    public IngestModuleTimedProcessTerminator(IngestJobContext context) {
        this.context = context;
        
        IngestModuleTimedProcessTerminator.creationTime_sec = (new Date().getTime())/1000;
    }

    /**
     * @return true if process should be terminated, false otherwise
     */
    @Override
    public boolean shouldTerminateProcess() {
        
        // check if maximum execution time elapsed    
        if (didProcessTimeOut())
            return true;
        
        return this.context.dataSourceIngestIsCancelled();
    }

    /**
     * @return true if process should be terminated, false otherwise
     */
    public boolean didProcessTimeOut() {
        
        // check if maximum execution time elapsed
        long currentTime_sec = (new Date().getTime())/1000;        
        if (currentTime_sec - IngestModuleTimedProcessTerminator.creationTime_sec > DEFAULT_TIMEOUT)
            return true;
        
        return false;
    }    
}
