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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.util.ArrayList;
import java.util.List;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory that creates ingest modules that use interesting files set
 * definitions to identify files that may be of interest to the user.
 */
@ServiceProvider(service = IngestModuleFactory.class)
final public class InterestingItemsIngestModuleFactory extends IngestModuleFactoryAdapter {

    @Messages({
        "InterestingItemsIngestModuleFactory.defaultSettingsError=Error getting default interesting files settings from file."
    })

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    static String getModuleName() {
        return NbBundle.getMessage(InterestingItemsIngestModuleFactory.class, "InterestingItemsIdentifierIngestModule.moduleName");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(InterestingItemsIngestModuleFactory.class, "InterestingItemsIdentifierIngestModule.moduleDescription");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        FilesSetDefsPanel panel = new FilesSetDefsPanel(FilesSetDefsPanel.PANEL_TYPE.INTERESTING_FILE_SETS);
        panel.load();
        return panel;
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        // All interesting files set definitions are enabled by default. The
        // names of the set definitions are stored instead of the set 
        // definitions to make per ingest job enabling and disabling of the 
        // definitions independent of the rules that make up the defintions.
        // Doing so also keeps the serialization simple.
        List<String> enabledFilesSetNames = new ArrayList<>();
        try {
            for (String name : FilesSetsManager.getInstance().getInterestingFilesSets().keySet()) {
                enabledFilesSetNames.add(name);
            }
        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            MessageNotifyUtil.Message.error(Bundle.InterestingItemsIngestModuleFactory_defaultSettingsError());
        }
        return new FilesIdentifierIngestJobSettings(enabledFilesSetNames);
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof FilesIdentifierIngestJobSettings)) {
            throw new IllegalArgumentException("Settings not instanceof org.sleuthkit.autopsy.modules.interestingitems.InterestingItemsIngestJobSettings");
        }
        return FilesIdentifierIngestJobSettingsPanel.makePanel((FilesIdentifierIngestJobSettings) settings);
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof FilesIdentifierIngestJobSettings)) {
            throw new IllegalArgumentException("Settings not instanceof org.sleuthkit.autopsy.modules.interestingitems.InterestingItemsIngestJobSettings");
        }
        return new FilesIdentifierIngestModule((FilesIdentifierIngestJobSettings) settings);
    }
}
