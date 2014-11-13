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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * An ExecUtil process terminator for data source ingest modules that checks for
 * ingest job cancellation.
 */
public class IngestModuleTimedProcessTerminator implements ExecUtil.ProcessTerminator {

    public final IngestJobContext context;
    private static final Logger logger = Logger.getLogger(IngestModuleTimedProcessTerminator.class.getName());
    private static long creationTime_sec;   // time when TimedProcessTerminator was constructed
    private static long timeout_sec;        // time out value (seconds)
    private static final long DEFAULT_TIMEOUT_SEC = 172800;   // 48 hours
    
    /**
     * Constructs a process terminator for an ingest module. 
     * Uses default process execution timeout value.
     *
     * @param context The ingest job context for the ingest module.
     */
    public IngestModuleTimedProcessTerminator(IngestJobContext context) {
        this.context = context;
        
        IngestModuleTimedProcessTerminator.creationTime_sec = (new Date().getTime())/1000;
        IngestModuleTimedProcessTerminator.timeout_sec = DEFAULT_TIMEOUT_SEC;
    }
    
    /**
     * Constructs a process terminator for an ingest module. 
     *
     * @param context The ingest job context for the ingest module.
     * @param timeout_sec Process execution timeout value (seconds)
     */
    public IngestModuleTimedProcessTerminator(IngestJobContext context, long timeout_sec) {
        this.context = context;
        
        IngestModuleTimedProcessTerminator.creationTime_sec = (new Date().getTime())/1000;
        
        if (timeout_sec > 0)
            IngestModuleTimedProcessTerminator.timeout_sec = timeout_sec;
        else {
            IngestModuleTimedProcessTerminator.logger.log(Level.WARNING, "Process time out value specified must be greater than zero"); // NON-NLS
            IngestModuleTimedProcessTerminator.timeout_sec = DEFAULT_TIMEOUT_SEC;
        }
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
        if (currentTime_sec - IngestModuleTimedProcessTerminator.creationTime_sec > IngestModuleTimedProcessTerminator.timeout_sec)
            return true;
        
        return false;
    }    
}
