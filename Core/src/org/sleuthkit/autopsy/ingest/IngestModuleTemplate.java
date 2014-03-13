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
 * Combines an ingest module factory with ingest options and an enabled flag to
 * create a template for creating fully configured ingest modules.
 */
final class IngestModuleTemplate {

    private final IngestModuleFactory moduleFactory;
    private IngestModuleSettings ingestOptions = null;
    boolean enabled = true;

    IngestModuleTemplate(IngestModuleFactory moduleFactory, IngestModuleSettings ingestOptions) {
        this.moduleFactory = moduleFactory;
        this.ingestOptions = ingestOptions;
    }

    IngestModuleFactory getIngestModuleFactory() {
        return moduleFactory;
    }

    IngestModuleSettings getIngestOptions() {
        return ingestOptions;
    }

    void setIngestOptions(IngestModuleSettings ingestOptions) {
        this.ingestOptions = ingestOptions;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled() {
        return enabled;
    }
}
