/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.framework;

import java.nio.file.Path;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;

/**
 * Interface implemented by DataSourceProcessors in order to be supported by
 * automated ingest capability.
 *
 * @author elivis
 */
public interface AutoIngestDataSourceProcessor extends DataSourceProcessor {

    /**
     * Indicates whether the DataSourceProcessor is capable of processing the
     * data source. Returns a confidence value. Method can throw an exception
     * for a system level problem. The exception should not be thrown for an issue
     * related to bad input data.
     *
     * @param dataSourcePath Path to the data source.
     *
     * @return Confidence value. Values between 0 and 100 are recommended. Zero
     *         or less means the data source is not supported by the
     *         DataSourceProcessor. Value of 100 indicates high certainty in
     *         being able to process the data source.
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutomatedIngestDataSourceProcessor.AutomatedIngestDataSourceProcessorException
     */
    int canProcess(Path dataSourcePath) throws AutoIngestDataSourceProcessorException;

    /**
     * Adds a data source to the case database using a background task in a
     * separate thread by calling DataSourceProcessor.run() method. Returns as
     * soon as the background task is started. The background task uses a
     * callback object to signal task completion and return results. Method can
     * throw an exception for a system level problem. The exception should not
     * be thrown for an issue related to bad input data.
     *
     * @param deviceId        An ASCII-printable identifier for the device
     *                        associated with the data source that is intended
     *                        to be unique across multiple cases (e.g., a UUID).
     * @param dataSourcePath  Path to the data source.
     * @param progressMonitor Progress monitor that will be used by the
     *                        background task to report progress.
     * @param callBack        Callback that will be used by the background task
     *                        to return results.
     *
     * @throws
     * org.sleuthkit.autopsy.corecomponentinterfaces.AutomatedIngestDataSourceProcessor.AutomatedIngestDataSourceProcessorException
     */
    void process(String deviceId, Path dataSourcePath, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callBack) throws AutoIngestDataSourceProcessorException;

    /**
     * A custom exception for the use of AutomatedIngestDataSourceProcessor.
     */
    public class AutoIngestDataSourceProcessorException extends Exception {

        private static final long serialVersionUID = 1L;

        public AutoIngestDataSourceProcessorException(String message) {
            super(message);
        }

        public AutoIngestDataSourceProcessorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
