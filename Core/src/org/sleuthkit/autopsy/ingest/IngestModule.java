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
 * The interface that must be implemented by all ingest modules.
 */
public interface IngestModule {

    public enum ResultCode {

        OK,
        ERROR,
        @Deprecated
        NOT_SET 
    };

    /**
     * Invoked to obtain a display name for the module, i.e., a name that is
     * suitable for presentation to a user in a user interface component or a
     * log message.
     *
     * @return The display name of the module.
     */
    String getDisplayName();

    /**
     * Invoked to allow an ingest module to set up internal data structures and
     * acquire any private resources it will need during an ingest job. There
     * will usually be more than one instance of a module working on an ingest
     * job, but it is guaranteed that there will be no more than one instance of
     * the module per thread. If these instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release the resources
     * correctly.
     * <p>
     * A module that uses the scheduling service to schedule additional
     * processing needs to supply the ingest job ID passed to this method to the
     * scheduler. For example, a module that extracts files from an archive file
     * should schedule ingest of those files using the ingest job ID to ensure
     * that the files will be processed as part of the same ingest job.
     * <p>
     * An ingest module that does not require initialization should extend the
     * IngestModuleAdapter class to get a default implementation of this method
     * that saves the ingest job id.
     *
     * @param ingestJobId Identifier for the ingest job with which this module
     * instance is associated.
     */
    void init(long ingestJobId);

    /**
     * Invoked when an ingest job is completed, before the module instance is
     * discarded. The module should respond by doing things like releasing
     * private resources, submitting final results, and posting a final ingest
     * message.
     * <p>
     * An ingest module that does not need to do anything when the ingest job
     * completes should extend the IngestModuleAdapter class to get a default
     * implementation of this method that does nothing.
     */
    void jobCompleted();

    /**
     * Invoked when an ingest job is canceled or otherwise terminated early,
     * before the module instance is discarded. The module should respond by
     * doing things like releasing private resources, discarding partial
     * results, and posting a stopped ingest message.
     * <p>
     * An ingest module that does not need to do anything when the ingest job is
     * canceled should extend the IngestModuleAdapter class to get a default
     * implementation of this method that does nothing.
     */
    void jobCancelled();

    /**
     * Invoked after complete() or stop() is called to determine if the module
     * has finished responding to the termination request. The module instance
     * will be discarded when this method returns true.
     * <p>
     * An ingest module that does not need to do anything when the ingest job is
     * completed or canceled should extend the IngestModuleAdapter class to get
     * a default implementation of this method that returns true.
     *
     * @return True if the module is finished, false otherwise.
     */
    boolean isFinished();
}
