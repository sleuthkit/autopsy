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
 * Interface that must be implemented by all ingest modules.
 */
public interface IngestModule {
    
    public enum ProcessResult { // RJCTODO: Refactor to something like ProcessingResult or probably just Result or ResultCode
        OK,
        ERROR, // RJCTODO: Consider replacing comments that existed when this was specific to file ingest modules
        UNKNOWN // RJCTODO: This apperars to b e specifci
    };
        
    /**
     * Invoked to obtain a display name for the module, i.e., a name that is
     * suitable for presentation to a user in a user interface component or a
     * log message.
     * 
     * @return The display name of the module
     */
    String getDisplayName();
    
    /**
     * Invoked to allow an ingest module to set up internal data structures and
     * acquire any private resources it will need during ingest of a single 
     * data source. There will usually be more than one instance of a module 
     * executing, but it is guaranteed that there will be no more than one 
     * instance of the module per thread. If these instances must share 
     * resources, the modules are responsible for synchronizing access to the 
     * shared resources and doing reference counting as required to release 
     * the resources correctly.
     * <p>
     * A module that uses the scheduling service to schedule additional 
     * processing needs to supply the task ID passed to this method to the 
     * scheduler. For example, a module that extracts files from an archive 
     * discovered in a data source may schedule ingest of those files using 
     * the task ID.
     * 
     * @param taskId  To be used to schedule ingest of derived files
     */
    void init(long taskId);
    
    /**
     * RJCTODO
     * @return 
     */
    boolean hasBackgroundTasksRunning();    
    
    /**
     * Invoked when a single ingest of a particular data source is completed.  
     * The module should tear down internal data sources, release private 
     * resources, submit final results, and post a final ingest message. The
     * module will be discarded when this method returns.
     */
    void complete();

    /**
     * Invoked when a single ingest of a particular data source is canceled. 
     * The module should tear down internal data sources and release private 
     * resources, discard unrecorded results, and post a final ingest message. 
     *  The module will be discarded when this method returns.
     */
    void stop();    
}
