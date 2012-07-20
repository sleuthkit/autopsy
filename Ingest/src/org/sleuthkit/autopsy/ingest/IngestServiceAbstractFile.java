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
 * Ingest service interface that will be called for every file in the image
 */
public interface IngestServiceAbstractFile extends IngestServiceAbstract {

    /**
     * Return value resulting from processing AbstractFile
     * Can be used by IngestManager to stop processing the file, or by subsequent module
     * in the pipeline as a hint to stop processing the file
     */
    public enum ProcessResult {
        OK, ///<  Indicates that processing was successful (including if the file was largely ignored by the module)
        COND_STOP, ///< Indicates that the module thinks that the pipeline could stop processing, but it is up to the IngestManager to decide. Use this, for example, if a hash lookup detects that a file is known to be good and can be ignored.  
        STOP, ///< Indicates that the module thinks that the pipeline processing should be stopped unconditionally for the current file (this should be used sparingly for critical system errors and could be removed in future version)
        ERROR, ///< Indicates that an error was encountered while processing the file, hint for later modules that depend on this module to skip processing the file due to error condition (such as file could not be read)
        UNKNOWN ///< Indicates that a return value for the module is not known.  This should not be returned directly by modules, but is used when modules want to learn about a return value from a previously run module.  
    };
    
    /**
     * Entry point to process file / directory by the service.  See \ref ingestmodule_making for details
     * on what modules are responsible for doing. 
     * 
     * @param abstractFile file to process
     * @return ProcessResult result of the processing that can be used in the pipeline as a hint whether to further process this file
     */
    public ProcessResult process(AbstractFile abstractFile);
}
