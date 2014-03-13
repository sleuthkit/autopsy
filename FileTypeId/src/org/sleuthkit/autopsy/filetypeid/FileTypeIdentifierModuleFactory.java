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
public class FileTypeIdentifierModuleFactory extends IngestModuleFactoryAdapter {

    public final static String MODULE_NAME = "File Type Identification";
    public final static String MODULE_DESCRIPTION = "Matches file types based on binary signatures.";

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    static String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public String getModuleDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobOptions() {
        return new FileTypeIdentifierIngestJobOptions();
    }

    @Override
    public boolean providesIngestJobOptionsPanels() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobOptionsPanel(IngestModuleIngestJobSettings ingestJobOptions) {
        return new FileTypeIdSimpleConfigPanel((FileTypeIdentifierIngestJobOptions) ingestJobOptions);
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestJobOptions) {
        return new FileTypeIdIngestModule((FileTypeIdentifierIngestJobOptions) ingestJobOptions);
    }
}
