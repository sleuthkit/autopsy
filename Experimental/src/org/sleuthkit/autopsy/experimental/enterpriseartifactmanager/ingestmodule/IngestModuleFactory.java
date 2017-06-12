/*
 * Enterprise Artifact Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.ingestmodule;

import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.optionspanel.EamGlobalSettingsPanel;

/**
 * Factory for enterprise artifact manager ingest modules
 */
@ServiceProvider(service = org.sleuthkit.autopsy.ingest.IngestModuleFactory.class)
public class IngestModuleFactory extends IngestModuleFactoryAdapter {

    private static final String VERSION_NUMBER = "0.8.0";

    static String getModuleName() {
        return java.util.ResourceBundle.getBundle("org/sleuthkit/autopsy/experimental/enterpriseartifactmanager/Bundle")
                .getString("OpenIDE-Module-Name");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return "";
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
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return new IngestModule();
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        EamGlobalSettingsPanel globalOptionsPanel = new EamGlobalSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }

}
