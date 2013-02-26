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
 * Ingest module interface that will be called for every file in the image
 */
public interface IngestModuleAbstractFile extends IngestModuleAbstract {

    /**
     * Return value resulting from processing AbstractFile
     * If ERROR, can be used subsequent module
     * in the pipeline as a hint to stop processing the file
     */
    public enum ProcessResult {
        OK, ///<  Indicates that processing was successful (including if the file was largely ignored by the module)
        ERROR, ///< Indicates that an error was encountered while processing the file, hint for later modules that depend on this module to skip processing the file due to error condition (such as file could not be read)
        UNKNOWN ///< Indicates that a return value for the module is not known.  This should not be returned directly by modules, but is used to indicate the module has not set its return value (e.g. it never ran)
    };
    
    /**
     * Entry point to process file / directory by the module.  See \ref ingestmodule_making for details
     * on what modules are responsible for doing. 
     * 
     * @param pipelineContext the context in which the ingest runs (with its own settings, modules, etc)
     * @param abstractFile file to process
     * @return ProcessResult result of the processing that can be used in the pipeline as a hint whether to further process this file
     */
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile>pipelineContext, AbstractFile abstractFile);
}
