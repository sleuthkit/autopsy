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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory for creating archive extractor file ingest modules and the user
 * interface panels used to configure the settings for instances of the modules.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class EmbeddedFileExtractorModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return NbBundle.getMessage(EmbeddedFileExtractorIngestModule.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.moduleName");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(EmbeddedFileExtractorIngestModule.class,
                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.moduleDesc.text");
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
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return new EmbeddedFileExtractorIngestModule();
    }
}