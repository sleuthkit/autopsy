/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.ingestmodule;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.centralrepository.optionspanel.GlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * Factory for Central Repository ingest modules
 */
@ServiceProvider(service = org.sleuthkit.autopsy.ingest.IngestModuleFactory.class)
@NbBundle.Messages({"IngestModuleFactory.ingestmodule.name=Correlation Engine",
                    "IngestModuleFactory.ingestmodule.desc=Saves properties to the central repository for later correlation"})
public class IngestModuleFactory extends IngestModuleFactoryAdapter {

    private static final String VERSION_NUMBER = "0.9.0";

    /**
     * Get the name of the module.
     *
     * @return The module name.
     */
    static String getModuleName() {
        return Bundle.IngestModuleFactory_ingestmodule_name();
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return Bundle.IngestModuleFactory_ingestmodule_desc();
    }

    @Override
    public String getModuleVersionNumber() {
        return VERSION_NUMBER;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new IngestModule((IngestSettings) settings);
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        GlobalSettingsPanel globalOptionsPanel = new GlobalSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }
    
    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new IngestSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof IngestSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of IngestSettings");
        }
        return new IngestSettingsPanel((IngestSettings) settings);
    }

}
