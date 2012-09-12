/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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
 *
 * Context passed to a module at initialization time.
 * It may contain module configuration required to initialize some modules.
 */
public class IngestModuleInit {
    
    private String moduleArgs;

    /**
     * Get module arguments
     * @return module args string, used by some modules
     */
    public String getModuleArgs() {
        return moduleArgs;
    }

    /**
     * Sets module args. string (only used by module pipeline)
     * @param moduleArgs arguments to set for the module
     */
    void setModuleArgs(String moduleArgs) {
        this.moduleArgs = moduleArgs;
    }
    
    
    
    
}
