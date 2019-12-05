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
package org.sleuthkit.autopsy.modules.exif;

import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.openide.util.NbBundle;

/**
 * An factory that creates file ingest modules that parse EXIF meta data
 * associated with media files (e.g., JPEG format files).
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class ExifParserModuleFactory extends IngestModuleFactoryAdapter {

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    public static String getModuleName() {
        return NbBundle.getMessage(ExifParserFileIngestModule.class,
                "ExifParserFileIngestModule.moduleName.text");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(ExifParserFileIngestModule.class,
                "ExifParserFileIngestModule.getDesc.text");
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
        return new ExifParserFileIngestModule();
    }
}
