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
package org.sleuthkit.autopsy.fileextmismatch;

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
    public IngestModuleSettings getDefaultModuleSettings() {
        return new FileExtMismatchDetectorModuleSettings();
    }

    @Override
    public boolean hasModuleSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleSettingsPanel getModuleSettingsPanel(IngestModuleSettings settings) {
        assert settings instanceof FileExtMismatchDetectorModuleSettings;
        if (!(settings instanceof FileExtMismatchDetectorModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be instanceof FileExtMismatchDetectorModuleSettings");
        }
        FileExtMismatchModuleSettingsPanel settingsPanel = new FileExtMismatchModuleSettingsPanel((FileExtMismatchDetectorModuleSettings) settings);
        return settingsPanel;
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleGlobalSetttingsPanel getGlobalSettingsPanel() {
        FileExtMismatchSettingsPanel globalOptionsPanel = new FileExtMismatchSettingsPanel();
        globalOptionsPanel.load();
        return globalOptionsPanel;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleSettings settings) {
        assert settings instanceof FileExtMismatchDetectorModuleSettings;
        if (!(settings instanceof FileExtMismatchDetectorModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be instanceof FileExtMismatchDetectorModuleSettings");
        }
        return new FileExtMismatchIngestModule((FileExtMismatchDetectorModuleSettings) settings);
    }
}
