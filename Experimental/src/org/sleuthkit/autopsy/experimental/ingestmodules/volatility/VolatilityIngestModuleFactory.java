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
package org.sleuthkit.autopsy.experimental.ingestmodules.volatility;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory for creating instances of data source modules that run Volatility
 * against hiberfil.sys files.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class VolatilityIngestModuleFactory extends IngestModuleFactoryAdapter {

    /**
     * Gets the ingest module name for use within this package.
     *
     * @return A name string.
     */
    static String getModuleName() {
        return NbBundle.getMessage(VolatilityIngestModuleFactory.class, "moduleDisplayName.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDisplayName() {
        return VolatilityIngestModuleFactory.getModuleName();
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(VolatilityIngestModuleFactory.class, "moduleDescription.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleVersionNumber() {
        return VolatilityIngestModule.getVersion();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        return new VolatilityIngestModule();
    }

}
