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
    };
    
    // RJCTODO: Write header comment, make sure to mention "one module instance per thread"
    void startUp(IngestModuleContext context) throws Exception;

    // RJCTODO: Write header comment, make sure to mention "one module instance per thread"
    void shutDown(boolean ingestJobWasCancelled);
}
