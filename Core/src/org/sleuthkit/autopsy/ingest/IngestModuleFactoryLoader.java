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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Looks up loaded ingest module factories using the NetBean global lookup.
 */
final class IngestModuleFactoryLoader {

    private static final Logger logger = Logger.getLogger(IngestModuleFactoryLoader.class.getName());
    private static IngestModuleFactoryLoader instance;

    private IngestModuleFactoryLoader() {
    }

    synchronized static IngestModuleFactoryLoader getInstance() {
        if (instance == null) {
            instance = new IngestModuleFactoryLoader();
        }
        return instance;
    }

    synchronized List<IngestModuleFactory> getIngestModuleFactories() {
        List<IngestModuleFactory> moduleFactories = new ArrayList<>();
        HashSet<String> moduleDisplayNames = new HashSet<>();
        Collection<? extends IngestModuleFactory> factories = Lookup.getDefault().lookupAll(IngestModuleFactory.class);
        for (IngestModuleFactory factory : factories) {
            logger.log(Level.INFO, "Found ingest module factory: name = {0}, version = {1}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()});
            if (!moduleDisplayNames.contains(factory.getModuleDisplayName())) {
                moduleFactories.add(factory);
                moduleDisplayNames.add(factory.getModuleDisplayName());
            } else {
                logger.log(Level.SEVERE, "Found duplicate ingest module display name, discarding ingest module factory (name = {0}", new Object[]{factory.getModuleDisplayName(), factory.getModuleVersionNumber()});
            }
        }
        return new ArrayList<>(moduleFactories);
    }
}