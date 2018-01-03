/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.Collections;
import java.util.List;

public class IngestJobStartResult {

    private final IngestJob job;
    private final IngestManager.IngestManagerException startupException;
    private final List<IngestModuleError> moduleErrors;

    IngestJobStartResult(IngestJob job, IngestManager.IngestManagerException startupException, List<IngestModuleError> moduleErrors) {
        this.job = job;
        this.startupException = startupException;
        if (moduleErrors == null) {
            this.moduleErrors = new ArrayList<>();
        } else {
            this.moduleErrors = moduleErrors;
        }
    }

    /**
     * @return the job, which may be null
     */
    public IngestJob getJob() {
        return job;
    }

    /**
     * @return the startupException, which may be null
     */
    public IngestManager.IngestManagerException getStartupException() {
        return startupException;
    }

    /**
     * @return the moduleErrors, which may be empty
     */
    public List<IngestModuleError> getModuleErrors() {
        return Collections.unmodifiableList(moduleErrors);
    }
}
