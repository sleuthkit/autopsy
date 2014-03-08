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
package org.sleuthkit.autopsy.hashdatabase;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleOptions;
import org.sleuthkit.autopsy.ingest.IngestModuleOptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleResourcesConfigPanel;

/**
 * A factory that creates file ingest modules that do hash database lookups.
 */
@ServiceProvider(service = IngestModuleFactory.class)
public class HashLookupModuleFactory extends IngestModuleFactoryAdapter {

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    static String getModuleName() {
        return NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleName");
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleDescription");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public IngestModuleOptions getDefaultIngestOptions() {
        return new HashLookupOptions();
    }

    @Override
    public boolean providesIngestOptionsPanels() {
        return true;
    }

    @Override
    public IngestModuleOptionsPanel getIngestOptionsPanel(IngestModuleOptions ingestOptions) {
        HashDbSimpleConfigPanel ingestOptionsPanel = new HashDbSimpleConfigPanel();
        ingestOptionsPanel.load();
        return ingestOptionsPanel;
    }

    @Override
    public boolean providesResourcesConfigPanels() {
        return true;
    }

    @Override
    public IngestModuleResourcesConfigPanel getResourcesConfigPanel() {
        HashDbConfigPanel resourcesConfigPanel = new HashDbConfigPanel();
        resourcesConfigPanel.load();
        return resourcesConfigPanel;
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException {
        return new HashDbIngestModule();
    }
}
