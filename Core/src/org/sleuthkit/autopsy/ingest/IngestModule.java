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
 *
 * Autopsy will generally use several instances of an ingest module for each
 * ingest job it performs (one for each thread that it is using).
 *
 * Autopsy will call startUp() before any data is processed, will pass any data
 * to be analyzed into the process() method (FileIngestModule.process() or
 * DataSourceIngestModule.process()), and call shutDown() after either all data
 * is analyzed or the user has canceled the job.
 *
 * Autopsy may use multiple threads to complete an ingest job, but it is
 * guaranteed that a module instance will always be called from a single thread.
 * Therefore, you can easily have thread-safe code by not using any static
 * member variables.
 *
 * If the module instances must share resources, the modules are responsible for
 * synchronizing access to the shared resources and doing reference counting as
 * required to release those resources correctly. Also, more than one ingest job
 * may be in progress at any given time. This must also be taken into
 * consideration when sharing resources between module instances.
 *
 * TIP: An ingest module that does not require initialization or clean up may
 * extend the abstract IngestModuleAdapter class to get a default "do nothing"
 * implementation of this interface.
 */
public interface IngestModule {

    /**
     * A return code for derived class process() methods.
     */
    public enum ProcessResult {

        OK,
        ERROR
    };

    /**
     * A custom exception for the use of ingest modules.
     */
    public class IngestModuleException extends Exception {

        private static final long serialVersionUID = 1L;

        @Deprecated
        public IngestModuleException() {
        }

        public IngestModuleException(String message) {
            super(message);
        }

        public IngestModuleException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Invoked by Autopsy to allow an ingest module instance to set up any
     * internal data structures and acquire any private resources it will need
     * during an ingest job. If the module depends on loading any resources, it
     * should do so in this method so that it can throw an exception in the case
     * of an error and alert the user. Exceptions that are thrown from process()
     * and shutDown() are logged, but do not stop processing of the data source.
     *
     * @param context Provides data and services specific to the ingest job and
     *                the ingest pipeline of which the module is a part.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    void startUp(IngestJobContext context) throws IngestModuleException;

    /**
     * TODO: The next time an API change is legal, add a cancel() method and
     * remove the "ingest job is canceled" queries from the IngestJobContext
     * class.
     */
}
