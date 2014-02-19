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

package org.sleuthkit.autopsy.hashdatabase;

import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.AbstractIngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;

/**
 * An factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service=IngestModuleFactory.class)
public class HashLookupModuleFactory extends AbstractIngestModuleFactory {
    private final static String MODULE_NAME = "Hash Lookup";
    private final static String MODULE_DESCRIPTION = "Identifies known and notables files using supplied hash databases, such as a standard NSRL database.";
    
    @Override
    public String getModuleDisplayName() {
        return MODULE_NAME;
    }
    
    @Override
    public String getModuleDescription() {
        return MODULE_DESCRIPTION;        
    }
    
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();        
    }
}
