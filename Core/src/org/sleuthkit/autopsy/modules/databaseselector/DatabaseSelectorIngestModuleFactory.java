/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.databaseselector;

import org.sleuthkit.autopsy.coreutils.Version;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory that creates a DatabaseSelectorModule to parse database files and 
 * mark them as having interesting records (emails, phone numbers, mac addresses, gps coordinates).
 */
@NbBundle.Messages({
    "DatabaseSelectorIngestModuleFactory.FlagDatabases.moduleName=Database Selector",
    "DatabaseSelectorIngestModuleFactory.FlagDatabases.moduleDesc.text=Flag databases with interesting items (emails, phone numbers, gps coordinates, ip/mac addresses)"
})
@ServiceProvider(service = IngestModuleFactory.class)
public class DatabaseSelectorIngestModuleFactory extends IngestModuleFactoryAdapter {

    static String getModuleName() {
        return Bundle.DatabaseSelectorIngestModuleFactory_FlagDatabases_moduleName();
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }
    
    @Override
    public String getModuleDescription() {
        return Bundle.DatabaseSelectorIngestModuleFactory_FlagDatabases_moduleDesc_text();
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
        return new DatabaseSelectorIngestModule();
    }
}