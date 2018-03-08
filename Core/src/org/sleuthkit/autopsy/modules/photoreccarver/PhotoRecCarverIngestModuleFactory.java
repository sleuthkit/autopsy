/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.photoreccarver;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory for creating instances of file ingest modules that carve
 * unallocated space
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class PhotoRecCarverIngestModuleFactory extends IngestModuleFactoryAdapter {

    private static final String VERSION = "7.0"; // Version should match the PhotoRec tool version.

    /**
     * Gets the ingest module name for use within this package.
     *
     * @return A name string.
     */
    static String getModuleName() {
        return NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "moduleDisplayName.text");
    }

    @Override
    public String getModuleDisplayName() {
        return PhotoRecCarverIngestModuleFactory.getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(PhotoRecCarverIngestModuleFactory.class, "moduleDescription.text");
    }

    @Override
    public String getModuleVersionNumber() {
        return VERSION;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new PhotoRecCarverFileIngestModule((PhotoRecCarverIngestJobSettings) settings);
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new PhotoRecCarverIngestJobSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        return new PhotoRecCarverIngestJobSettingsPanel((PhotoRecCarverIngestJobSettings) settings);
    }

}
