/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.dataSourceIntegrity;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.autopsy.ingest.NoIngestModuleIngestJobSettings;

/**
 * An factory that creates data source ingest modules that verify the integrity
 * of Expert Witness Format (EWF), i.e., .e01 files .
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class DataSourceIntegrityModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return NbBundle.getMessage(DataSourceIntegrityIngestModule.class,
                "DataSourceIntegrityModuleFactory.moduleName.text");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(DataSourceIntegrityIngestModule.class,
                "DataSourceIntegrityModuleFactory.moduleDesc.text");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        if (settings instanceof DataSourceIntegrityIngestSettings) {
            return new DataSourceIntegrityIngestModule((DataSourceIntegrityIngestSettings) settings);
        }
        /*
         * Compatibility check for older versions.
         */
        if (settings instanceof NoIngestModuleIngestJobSettings) {
            return new DataSourceIntegrityIngestModule(new DataSourceIntegrityIngestSettings());
        }
        
        throw new IllegalArgumentException("Expected settings argument to be an instance of IngestSettings");
    }
    
    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new DataSourceIntegrityIngestSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (settings instanceof DataSourceIntegrityIngestSettings) {
            return new DataSourceIntegrityIngestSettingsPanel((DataSourceIntegrityIngestSettings) settings);
        }
        /*
         * Compatibility check for older versions.
         */
        if (settings instanceof NoIngestModuleIngestJobSettings) {
            return new DataSourceIntegrityIngestSettingsPanel(new DataSourceIntegrityIngestSettings());
        }
        
        throw new IllegalArgumentException("Expected settings argument to be an instance of IngestSettings");
    }    
}
