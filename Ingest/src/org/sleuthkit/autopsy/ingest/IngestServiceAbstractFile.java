/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.sleuthkit.datamodel.AbstractFile;

/**
 * Ingest service interface that acts on every AbstractFile in the image
 */
public interface IngestServiceAbstractFile extends IngestServiceAbstract {

    /**
     * Return value resulting from processing AbstractFile
     * Can be used by manager to stop processing the file, or by subsequent service
     * in the pipeline as a hint to stop processing the file
     */
    public enum ProcessResult {
        UNKNOWN, //values unknown for the (service,last file)
        OK, //subsequent service continues processing the file
        STOP, //subsequent service stops processing the file unconditionally
        COND_STOP, //subsequent service decides whether to stop processing the file
        ERROR //error encountered processing the file, hint for the depending service to skip processing the file
    };
    
    /**
     * Entry point to process file / directory by the service.
     * 
     * Service does all the processing work in this method.
     * It may choose to skip the file if the file is not of interest to the service.
     * Results of processing, such as extracted data or analysis results should be posted to the blackboard.
     * 
     * In a more advanced module, the module can enqueue the file 
     * and postpone processing until more files of interest are available.
     * 
     * The service notifies the ingest inbox of interesting events (data, errors, warnings, infos) 
     * by posting ingest messages
     * The service notifies data viewers by firing events using IngestManager.fireServiceDataEvent
     * 
     * @param abstractFile file to process
     * @return ProcessResult result of the processing that can be used in the pipeline as a hint whether to further process this file
     */
    public ProcessResult process(AbstractFile abstractFile);
}
