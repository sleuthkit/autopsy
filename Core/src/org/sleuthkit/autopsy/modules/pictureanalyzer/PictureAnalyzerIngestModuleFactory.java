/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.pictureanalyzer;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * Factory for the Picture Analysis ingest module.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class PictureAnalyzerIngestModuleFactory extends IngestModuleFactoryAdapter {

    @NbBundle.Messages({
        "PictureAnalyzerIngestModuleFactory.module_name=Picture Analyzer"
    })
    public static String getModuleName() {
        return Bundle.PictureAnalyzerIngestModuleFactory_module_name();
    }
    
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    @NbBundle.Messages({
        "PictureAnalyzerIngestModuleFactory.module_description=Performs general"
                + " analysis on picture files, including extracting EXIF metadata"
                + " and converting between formats."
    })
    public String getModuleDescription() {
        return Bundle.PictureAnalyzerIngestModuleFactory_module_description();
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        return new PictureAnalyzerIngestModule();
    }
    
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

}
