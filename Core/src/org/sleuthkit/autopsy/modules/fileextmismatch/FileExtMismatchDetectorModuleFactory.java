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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * An factory that creates file ingest modules that detect mismatches between
 * the types of files and their extensions.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class FileExtMismatchDetectorModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return NbBundle.getMessage(FileExtMismatchIngestModule.class,
                "FileExtMismatchIngestModule.moduleName");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(FileExtMismatchIngestModule.class,
                "FileExtMismatchIngestModule.moduleDesc.text");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new FileExtMismatchDetectorModuleSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        assert settings instanceof FileExtMismatchDetectorModuleSettings;
        if (!(settings instanceof FileExtMismatchDetectorModuleSettings)) {
            throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchDetectorModuleFactory.getIngestJobSettingsPanel.exception.msg"));
        }
        FileExtMismatchModuleSettingsPanel settingsPanel = new FileExtMismatchModuleSettingsPanel((FileExtMismatchDetectorModuleSettings) settings);
        return settingsPanel;
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        FileExtMismatchSettingsPanel globalOptionsPanel = new FileExtMismatchSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        assert settings instanceof FileExtMismatchDetectorModuleSettings;
        if (!(settings instanceof FileExtMismatchDetectorModuleSettings)) {
            throw new IllegalArgumentException(NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchDetectorModuleFactory.getIngestJobSettingsPanel.exception.msg"));
        }
        return new FileExtMismatchIngestModule((FileExtMismatchDetectorModuleSettings) settings);
    }
}
