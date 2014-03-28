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
 * <p>
 * Autopsy will generally use several instances of an ingest module for each
 * ingest job it performs. Completing an ingest job entails processing a single
 * data source (e.g., a disk image) and all of the files from the data source,
 * including files extracted from archives and any unallocated space (made to
 * look like a series of files). The data source is passed through one or more
 * pipelines of data source ingest modules. The files are passed through one or
 * more pipelines of file ingest modules.
 * <p>
 * Autopsy may use multiple threads to complete an ingest job, but it is
 * guaranteed that there will be no more than one module instance per thread.
 * However, if the module instances must share resources, the modules are
 * responsible for synchronizing access to the shared resources and doing
 * reference counting as required to release those resources correctly. Also,
 * more than one ingest job may be in progress at any given time. This must also
 * be taken into consideration when sharing resources between module instances.
 * <p>
 * TIP: An ingest module that does not require initialization or clean up may
 * extend the abstract IngestModuleAdapter class to get a default "do nothing"
 * implementation of this interface.
 *
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

        public IngestModuleException() {
        }

        public IngestModuleException(String message) {
            super(message);
        }
    }

    /**
     * Invoked by Autopsy to allow an ingest module instance to set up any
     * internal data structures and acquire any private resources it will need
     * during an ingest job.
     * <p>
     * Autopsy will generally use several instances of an ingest module for each
     * ingest job it performs. Completing an ingest job entails processing a
     * single data source (e.g., a disk image) and all of the files from the
     * data source, including files extracted from archives and any unallocated
     * space (made to look like a series of files). The data source is passed
     * through one or more pipelines of data source ingest modules. The files
     * are passed through one or more pipelines of file ingest modules.
     * <p>
     * Autopsy may use multiple threads to complete an ingest job, but it is
     * guaranteed that there will be no more than one module instance per
     * thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances.
     * <p>
     * An ingest module that does not require initialization may extend the
     * abstract IngestModuleAdapter class to get a default "do nothing"
     * implementation of this method.
     *
     * @param context Provides data and services specific to the ingest job and
     * the ingest pipeline of which the module is a part.
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    void startUp(IngestJobContext context) throws IngestModuleException;

    /**
     * Invoked by Autopsy when an ingest job is completed, before the ingest
     * module instance is discarded. The module should respond by doing things
     * like releasing private resources, submitting final results, and posting a
     * final ingest message.
     * <p>
     * Autopsy will generally use several instances of an ingest module for each
     * ingest job it performs. Completing an ingest job entails processing a
     * single data source (e.g., a disk image) and all of the files from the
     * data source, including files extracted from archives and any unallocated
     * space (made to look like a series of files). The data source is passed
     * through one or more pipelines of data source ingest modules. The files
     * are passed through one or more pipelines of file ingest modules.
     * <p>
     * Autopsy may use multiple threads to complete an ingest job, but it is
     * guaranteed that there will be no more than one module instance per
     * thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances.
     * <p>
     * An ingest module that does not require initialization may extend the
     * abstract IngestModuleAdapter class to get a default "do nothing"
     * implementation of this method.
     */
    void shutDown(boolean ingestJobWasCancelled);
}
