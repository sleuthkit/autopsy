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
package org.sleuthkit.autopsy.modules.hashdatabase;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;

/**
 * A factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service = IngestModuleFactory.class)
@NbBundle.Messages({
    "HashLookupModuleFactory.moduleName.text=Hash Lookup",
    "HashLookupModuleFactory.moduleDescription.text=Identifies known and notable files using supplied hash sets, such as a standard NSRL hash set."
})
public class HashLookupModuleFactory extends IngestModuleFactoryAdapter {

    private HashLookupModuleSettingsPanel moduleSettingsPanel = null;

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    /**
     * Get the name of the module.
     *
     * @return The module name.
     */
    static String getModuleName() {
        return Bundle.HashLookupModuleFactory_moduleName_text();
    }

    @Override
    public String getModuleDescription() {
        return Bundle.HashLookupModuleFactory_moduleDescription_text();
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        // All available hash sets are enabled and always calculate hashes is true by default.
        return new HashLookupModuleSettings(true, HashDbManager.getInstance().getAllHashSets());
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof HashLookupModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of HashLookupModuleSettings.");
        }
        if (moduleSettingsPanel == null) {
            moduleSettingsPanel = new HashLookupModuleSettingsPanel((HashLookupModuleSettings) settings);
        } else {
            moduleSettingsPanel.reset((HashLookupModuleSettings) settings);
        }
        return moduleSettingsPanel;
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        HashLookupSettingsPanel globalSettingsPanel = new HashLookupSettingsPanel();
        globalSettingsPanel.load();
        return globalSettingsPanel;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof HashLookupModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of HashLookupModuleSettings.");
        }
        return new HashDbIngestModule((HashLookupModuleSettings) settings);
    }
}
