/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2017 Basis Technology Corp.
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
 * A factory for creating file level ingest module that extracts embedded files
 * from supported archive and document formats.
 */
@NbBundle.Messages({
    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.moduleName=Embedded File Extractor",
    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.moduleDesc.text=Extracts embedded files (doc, docx, ppt, pptx, xls, xlsx, zip, rar, arj, 7z, gzip, bzip2, tar), schedules them for ingestion, and populates the directory tree with them."
})
@ServiceProvider(service = IngestModuleFactory.class)
public class EmbeddedFileExtractorModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return Bundle.EmbeddedFileExtractorIngestModule_ArchiveExtractor_moduleName();
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }
    
    static String getOutputFolderName() {
        return "EFE";
    }

    @Override
    public String getModuleDescription() {
        return Bundle.EmbeddedFileExtractorIngestModule_ArchiveExtractor_moduleDesc_text();
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
