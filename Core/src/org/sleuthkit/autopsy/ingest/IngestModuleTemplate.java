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

import java.io.Serializable;

/**
 * RJCTODO
 */
public class IngestModuleTemplate {
    private final IngestModuleFactory moduleFactory;
    private Serializable ingestOptions = null;
    private boolean enabled = true;
    
    IngestModuleTemplate(IngestModuleFactory moduleFactory, Serializable ingestOptions, boolean enabled) {
        this.moduleFactory = moduleFactory;
        this.ingestOptions = ingestOptions;
        this.enabled = enabled;
    }
    
    String getModuleDisplayName() {
        return moduleFactory.getModuleDisplayName();
    }
    
    String getModuleDescription() {
        return moduleFactory.getModuleDescription();
    }

    Serializable getIngestOptions() {
        return ingestOptions;
    }
    
    void setIngestOptions(Serializable ingestOptions) {
        this.ingestOptions = ingestOptions;
    }
    
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    boolean isEnabled() {
        return enabled;
    }   
}
