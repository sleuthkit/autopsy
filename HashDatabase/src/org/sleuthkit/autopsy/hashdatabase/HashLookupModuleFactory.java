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

import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSetttingsPanel;

/**
 * A factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class HashLookupModuleFactory extends IngestModuleFactoryAdapter {

    private HashLookupModuleSettingsPanel moduleSettingsPanel = null;

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    static String getModuleName() {
        return NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleName");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleDescription");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleSettings getDefaultModuleSettings() {
        HashDbManager hashDbManager = HashDbManager.getInstance();
        List<String> enabledHashSets = new ArrayList<>();
        List<HashDbManager.HashDb> knownFileHashSets = hashDbManager.getKnownFileHashSets();
        for (HashDbManager.HashDb db : knownFileHashSets) {
            if (db.getSearchDuringIngest()) {
                enabledHashSets.add(db.getHashSetName());
            }
        }
        List<HashDbManager.HashDb> knownBadFileHashSets = hashDbManager.getKnownBadFileHashSets();
        for (HashDbManager.HashDb db : knownBadFileHashSets) {
            if (db.getSearchDuringIngest()) {
                enabledHashSets.add(db.getHashSetName());
            }
        }
        return new HashLookupModuleSettings(hashDbManager.getAlwaysCalculateHashes(), enabledHashSets);
    }

    @Override
    public boolean hasModuleSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleSettingsPanel getModuleSettingsPanel(IngestModuleSettings settings) {
        if (moduleSettingsPanel == null) {
            moduleSettingsPanel = new HashLookupModuleSettingsPanel();
        }
        moduleSettingsPanel.load(); // RJCTODO: Fix this
        return moduleSettingsPanel;
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSetttingsPanel getGlobalSettingsPanel() {
        HashLookupSettingsPanel globalSettingsPanel = new HashLookupSettingsPanel();
        globalSettingsPanel.load();
        return globalSettingsPanel;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleSettings settings) {
        assert settings instanceof HashLookupModuleSettings;
        if (!(settings instanceof HashLookupModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be instanceof HashLookupModuleSettings");
        }
        return new HashDbIngestModule((HashLookupModuleSettings) settings);
    }
}
