/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2014 Basis Technology Corp.
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
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;

// RJCTODO: Comment
final class IngestModuleLoader {
    private static IngestModuleLoader instance;
    private ArrayList<IngestModuleFactory> moduleFactories = new ArrayList<>();

    private IngestModuleLoader() {
    }

    synchronized static IngestModuleLoader getDefault() {
        if (instance == null) {
            Logger.getLogger(IngestModuleLoader.class.getName()).log(Level.INFO, "Creating ingest module loader instance");
            instance = new IngestModuleLoader();
            instance.init();
        }
        return instance;
    }

    private void init() {    
        // RJCTODO: Add code to listen to changes in the collections, possibly restore listener code...
        // RJCTODO: Since we were going to overwrite pipeline config every time and we are going to move the code modules
        // into this package, we can simply handle the module ordering here, possibly just directly instantiating the core 
        // modules.
        Logger logger = Logger.getLogger(IngestModuleLoader.class.getName());
        Collection<? extends IngestModuleFactory> factories = Lookup.getDefault().lookupAll(IngestModuleFactory.class);
        for (IngestModuleFactory factory : factories) {
            logger.log(Level.INFO, "Loaded ingest module factory: name = {0}, version = {1}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()});            
            moduleFactories.add(factory);
        }        
    }

    List<IngestModuleFactory> getIngestModuleFactories() {
        return moduleFactories;
    }    
}