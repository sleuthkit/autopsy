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

import org.sleuthkit.datamodel.Content;

/**
 * Interface that must be implemented by all data source ingest modules.
 */
public interface DataSourceIngestModule extends IngestModule {
    
    /**
     * Process a data source.
     * @param dataSource The data source to process.
     * @param statusHelper A status helper to be used to report progress and 
     * detect task cancellation.
     * @return RJCTODO
     */
    ProcessResult process(Content dataSource, IngestDataSourceWorkerController statusHelper); // RJCTODO: Change name of IngestDataSourceWorkerController class, or better, get rid of it so all threads in ingest can be the same     
}