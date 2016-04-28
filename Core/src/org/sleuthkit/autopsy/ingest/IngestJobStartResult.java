/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2015 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.List;

public class IngestJobStartResult {

    private IngestJob job; // may be null
    private IngestManagerException startupException; // may be null
    private List<IngestModuleError> moduleErrors; // may be empty

    public IngestJobStartResult() {
        job = null;
        startupException = null;
        moduleErrors = new ArrayList<>();
    }

    /**
     * @return the job
     */
    public IngestJob getJob() {
        return job;
    }

    /**
     * @param job the job to set
     */
    public void setJob(IngestJob job) {
        this.job = job;
    }

    /**
     * @return the startupException
     */
    public IngestManagerException getStartupException() {
        return startupException;
    }

    /**
     * @param startupException the startupException to set
     */
    public void setStartupException(IngestManagerException startupException) {
        this.startupException = startupException;
    }

    /**
     * @return the moduleErrors
     */
    public List<IngestModuleError> getModuleErrors() {
        return moduleErrors;
    }

    /**
     * @param moduleErrors the moduleErrors to set
     */
    public void setModuleErrors(List<IngestModuleError> moduleErrors) {
        this.moduleErrors = moduleErrors;
    }
}
