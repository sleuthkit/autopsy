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
package org.sleuthkit.autopsy.modules.filetypeid;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory that creates file ingest modules that determine the types of files.
 */
// TODO: This class does not need to be public.
@ServiceProvider(service = IngestModuleFactory.class)
public class FileTypeIdModuleFactory extends IngestModuleFactoryAdapter {

    FileTypeIdGlobalSettingsPanel globalSettingsPanel;

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    /**
     * Gets the module display name.
     *
     * @return The name string.
     */
    static String getModuleName() {
        return NbBundle.getMessage(FileTypeIdIngestModule.class,
                "FileTypeIdIngestModule.moduleName.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(FileTypeIdIngestModule.class,
                "FileTypeIdIngestModule.moduleDesc.text");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean hasGlobalSettingsPanel() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        if (null == globalSettingsPanel) {
            globalSettingsPanel = new FileTypeIdGlobalSettingsPanel();
        }
        globalSettingsPanel.load();
        return globalSettingsPanel;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new FileTypeIdIngestModule();
    }
}