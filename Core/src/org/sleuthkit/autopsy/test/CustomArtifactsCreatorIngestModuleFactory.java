/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.test;

//import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
//import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 * A factory for data source and file ingest modules that associate custom
 * artifacts and attributes with content for test purposes. Uncomment the
 * service provider annotation and required imports to activate this test
 * fixture.
 */
// @ServiceProvider(service = IngestModuleFactory.class)
public final class CustomArtifactsCreatorIngestModuleFactory extends IngestModuleFactoryAdapter {

    /**
     * Gets the module display name.
     *
     * @return The module display name.
     */
    static String getModuleName() {
        return "Custom Artifacts Creator";
    }

    /**
     * Gets the display name that identifies the family of ingest modules the
     * factory creates.
     *
     * @return The module family display name.
     */
    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    /**
     * Gets a brief, user-friendly description of the family of ingest modules
     * the factory creates.
     *
     * @return The module family description.
     */
    @Override
    public String getModuleDescription() {
        return "Associates custom artifacts and attributes with files for test purposes.";
    }

    /**
     * Gets the version number of the family of ingest modules the factory
     * creates.
     *
     * @return The module family version number.
     */
    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    /**
     * Queries the factory to determine if it is capable of creating data source
     * ingest modules.
     *
     * @return True.
     */
    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    /**
     * Creates a data source ingest module instance.
     *
     * @param settings The settings for the ingest job.
     *
     * @return A data source ingest module instance.
     */
    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        return new CustomArtifactsCreatorDataSourceIngestModule();
    }

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * modules.
     *
     * @return True.
     */
    @Override
    public boolean isFileIngestModuleFactory() {
        return true;
    }

    /**
     * Creates a file ingest module instance.
     *
     * @param settings The settings for the ingest job.
     *
     * @return A file ingest module instance.
     */
    @Override
    public FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings settings) {
        return new CustomArtifactsCreatorFileIngestModule();
    }

}
