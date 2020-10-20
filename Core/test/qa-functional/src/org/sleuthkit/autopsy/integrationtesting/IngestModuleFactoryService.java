/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.integrationtesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.python.JythonModuleLoader;

/**
 * Class for obtaining ingest module factories since IngestModuleFactoryLoader
 * is not accessible.
 */
public class IngestModuleFactoryService {

    /**
     * Returns any found ingest modules from implementers of
     * IngestModuleFactory, IngestModuleFactoryAdapter, and Jython modules.
     *
     * @return The ingest module factories.
     */
    public List<IngestModuleFactory> getFactories() {
        // factories using lookup for IngestModuleFactory, IngestModuleFactoryAdapter and jython modules
        Stream<Collection<? extends IngestModuleFactory>> factoryCollections = Stream.of(
                Lookup.getDefault().lookupAll(IngestModuleFactory.class),
                Lookup.getDefault().lookupAll(IngestModuleFactoryAdapter.class),
                JythonModuleLoader.getIngestModuleFactories());

        // get unique by module display name
        Map<String, IngestModuleFactory> factories = factoryCollections
                .flatMap(coll -> coll.stream())
                .collect(Collectors.toMap(f -> f.getModuleDisplayName(), f -> f, (f1, f2) -> f1));

        // return list of values
        return new ArrayList<>(factories.values());
    }
}
