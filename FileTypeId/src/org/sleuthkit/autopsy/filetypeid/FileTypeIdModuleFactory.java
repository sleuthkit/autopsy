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
package org.sleuthkit.autopsy.filetypeid;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * An factory that creates file ingest modules that determine the types of
 * files.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class FileTypeIdModuleFactory extends IngestModuleFactoryAdapter {

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    static String getModuleName() {
        return NbBundle.getMessage(FileTypeIdIngestModule.class,
                "FileTypeIdIngestModule.moduleName.text");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(FileTypeIdIngestModule.class,
                "FileTypeIdIngestModule.moduleDesc.text");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new FileTypeIdModuleSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        assert settings instanceof FileTypeIdModuleSettings;
        if (!(settings instanceof FileTypeIdModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be instanceof FileTypeIdModuleSettings");
        }        
        return new FileTypeIdModuleSettingsPanel((FileTypeIdModuleSettings) settings);
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        assert settings instanceof FileTypeIdModuleSettings;
        if (!(settings instanceof FileTypeIdModuleSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be instanceof FileTypeIdModuleSettings");
        }        
        return new FileTypeIdIngestModule((FileTypeIdModuleSettings) settings);
    }
}
