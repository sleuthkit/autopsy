/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.plaso;

import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;

/**
 * A factory that creates data source ingest modules that run Plaso against an
 * image and saves the storage file to module output.
 */
@ServiceProvider(service = IngestModuleFactory.class)
@NbBundle.Messages({"PlasoModuleFactory.ingestJobSettings.exception.msg=Expected settings argument to be instanceof PlasoModuleSettings"})
public class PlasoModuleFactory implements IngestModuleFactory {

    @NbBundle.Messages({"PlasoModuleFactory_moduleName=Plaso"})
    static String getModuleName() {
        return Bundle.PlasoModuleFactory_moduleName();
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @NbBundle.Messages({"PlasoModuleFactory_moduleDesc=Runs Plaso against a Data Source."})
    @Override
    public String getModuleDescription() {
        return Bundle.PlasoModuleFactory_moduleDesc();
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        assert settings instanceof PlasoModuleSettings;
        if (settings instanceof PlasoModuleSettings) {
            return new PlasoIngestModule((PlasoModuleSettings) settings);
        }
        throw new IllegalArgumentException(Bundle.PlasoModuleFactory_ingestJobSettings_exception_msg());
    }

    @Override
    public boolean hasGlobalSettingsPanel() {
        return false;
    }

    @Override
    public IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new PlasoModuleSettings();
    }

    @Override
    public boolean hasIngestJobSettingsPanel() {
        return true;
    }

    @Override
    public IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        assert settings instanceof PlasoModuleSettings;
        if (settings instanceof PlasoModuleSettings) {
            return new PlasoModuleSettingsPanel((PlasoModuleSettings) settings);
        }
        throw new IllegalArgumentException(Bundle.PlasoModuleFactory_ingestJobSettings_exception_msg());
    }

    @Override
    public boolean isFileIngestModuleFactory() {
        return false;
    }

    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        throw new UnsupportedOperationException();
    }
}
