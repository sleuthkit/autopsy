/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.encryptiondetection;

import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory that creates file ingest modules that detect encryption and
 * password protection.
 */
@ServiceProvider(service = IngestModuleFactory.class)
@Messages({
    "EncryptionDetectionFileIngestModule.moduleName.text=Encryption Detection",
    "EncryptionDetectionFileIngestModule.getDesc.text=Looks for files with the specified minimum entropy.",
    "EncryptionDetectionFileIngestModule.artifactComment.password=Password protection detected.",
})

public class EncryptionDetectionModuleFactory implements IngestModuleFactory {

    public static final String PASSWORD_PROTECT_MESSAGE = Bundle.EncryptionDetectionFileIngestModule_artifactComment_password();

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
        return NbBundle.getMessage(EncryptionDetectionFileIngestModule.class, "EncryptionDetectionFileIngestModule.moduleName.text");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(EncryptionDetectionFileIngestModule.class, "EncryptionDetectionFileIngestModule.getDesc.text");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof EncryptionDetectionIngestJobSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of EncryptionDetectionIngestJobSettings.");
        }
        return new EncryptionDetectionFileIngestModule((EncryptionDetectionIngestJobSettings) settings);
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return false;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new EncryptionDetectionIngestJobSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof EncryptionDetectionIngestJobSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of EncryptionDetectionIngestJobSettings");
        }
        return new EncryptionDetectionIngestJobSettingsPanel((EncryptionDetectionIngestJobSettings) settings);
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof EncryptionDetectionIngestJobSettings)) {
            throw new IllegalArgumentException("Expected settings argument to be an instance of EncryptionDetectionIngestJobSettings.");
        }
        return new EncryptionDetectionDataSourceIngestModule((EncryptionDetectionIngestJobSettings) settings);
    }
}
