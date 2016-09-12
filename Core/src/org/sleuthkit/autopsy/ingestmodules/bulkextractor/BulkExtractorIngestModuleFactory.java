/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingestmodules.bulkextractor;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory for creating instances of data source and file ingest modules that
 * use Bulk Extractor to scan either an entire disk image or unallocated space
 * files, respectively.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class BulkExtractorIngestModuleFactory extends IngestModuleFactoryAdapter {

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDisplayName() {
        return Utilities.getModuleName();
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(BulkExtractorIngestModuleFactory.class, "BulkExtractorIngestModuleFactory.moduleDescription");
    }

    /**
     * @inheritDoc
     */
    @Override
    public String getModuleVersionNumber() {
        return Utilities.getVersion();
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        // Process the entire image by default.
        return new BulkExtractorIngestJobSettings(false);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof BulkExtractorIngestJobSettings)) {
            throw new IllegalArgumentException("Settings not instanceof src.org.sleuthkit.autopsy.bulkextractor.BulkExtractorIngestJobSettings"); // NON_NLS
        }
        return new BulkExtractorIngestJobSettingsPanel((BulkExtractorIngestJobSettings) settings);
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    /**
     * @inheritDoc
     */
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        if (!(settings instanceof BulkExtractorIngestJobSettings)) {
            throw new IllegalArgumentException("Settings not instanceof src.org.sleuthkit.autopsy.bulkextractor.BulkExtractorIngestJobSettings"); // NON_NLS
        }
        return new DiskImageIngestModule((BulkExtractorIngestJobSettings) settings);
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
        if (!(settings instanceof BulkExtractorIngestJobSettings)) {
            throw new IllegalArgumentException("Settings not instanceof src.org.sleuthkit.autopsy.bulkextractor.BulkExtractorIngestJobSettings"); // NON_NLS
        }
        return new UnallocatedSpaceIngestModule((BulkExtractorIngestJobSettings) settings);
    }

}
