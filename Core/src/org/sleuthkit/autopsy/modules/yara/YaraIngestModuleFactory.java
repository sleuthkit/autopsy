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
package org.sleuthkit.autopsy.modules.yara;

import java.util.ArrayList;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.autopsy.modules.yara.ui.YaraGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.yara.ui.YaraIngestSettingsPanel;

/**
 * A factory that creates ingest modules that use the Yara rule set definitions
 * to identify files that may be of interest to the user.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class YaraIngestModuleFactory extends IngestModuleFactoryAdapter {

    @Messages({
        "Yara_Module_Name=YARA Analyzer",
        "Yara_Module_Description=The YARA Analyzer uses YARA to search files for textual or binary patterns."
    })

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return Bundle.Yara_Module_Description();
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        return new YaraIngestSettingsPanel((YaraIngestJobSettings) settings);
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new YaraIngestJobSettings(new ArrayList<>(), true);
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new YaraIngestModule((YaraIngestJobSettings) settings);
    }

    /**
     * Return the name of the ingest module.
     *
     * @return Ingest module name.
     */
    static String getModuleName() {
        return Bundle.Yara_Module_Name();
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        YaraGlobalSettingsPanel globalOptionsPanel = new YaraGlobalSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }
}
