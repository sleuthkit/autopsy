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
package org.sleuthkit.autopsy.ingest;

/**
 * An interface that must be implemented by all providers of ingest modules. An
 * IngestModuleFactory will be used as a stateless source of a type of data
 * source ingest module, a type of file ingest module, or both. The ingest
 * framework will create one or more instances of each module type for each
 * ingest job it performs, but it is guaranteed that there will be no more than
 * one module instance per thread. If these instances must share resources, the
 * modules are responsible for synchronizing access to the shared resources and
 * doing reference counting as required to release the resources correctly.
 * <p>
 * IngestModuleFactory implementations must be marked with the NetBeans Service
 * provider annotation:
 *
 * @ServiceProvider(service=IngestModuleFactory.class)
 * <p>
 * Default implementations of many of the methods in this interface are provided
 * by IngestModuleFactoryAdapter, an abstract base class.
 */
public interface IngestModuleFactory {

    class InvalidOptionsException extends Exception {

        public InvalidOptionsException() {
            super("Ingest options are not valid");
        }

        public InvalidOptionsException(String message) {
            super(message);
        }
    }

    /**
     * Gets the display name that identifies the family of ingest modules the
     * factory creates.
     *
     * @return The module display name as a string.
     */
    String getModuleDisplayName();

    /**
     * Gets a brief, user-friendly description of the family of ingest modules
     * the factory creates.
     *
     * @return The module description as a string.
     */
    String getModuleDescription();

    /**
     * Gets the version number of the family of ingest modules the factory
     * creates.
     *
     * @return The module version number as a string.
     */
    String getModuleVersionNumber();

    /**
     * Gets the default per ingest job options for instances of the family of
     * ingest modules the factory creates. If the module family does not have
     * per ingest job options, either this method should return an instance of
     * the NoIngestOptions class, or the factory should extend
     * IngestModuleFactoryAdapter.
     *
     * @return The ingest options.
     */
    IngestModuleOptions getDefaultIngestOptions();

    /**
     * Queries the factory to determine if it provides user interface panels to
     * configure resources to be used by instances of the family of ingest
     * modules the factory creates. For example, the core hash lookup and
     * keyword search ingest modules provide resource configuration panels to
     * import hash databases and keyword lists. The imported hash databases and
     * keyword lists are then enabled or disabled per ingest job using per
     * ingest job options panels.
     *
     * @return True if the factory provides per ingest job options panels, false
     * otherwise.
     */
    boolean providesIngestOptionsPanels();

    /**
     * Gets a user interface panel that can be used to specify per ingest job
     * options for instances of the family of ingest modules the factory
     * creates. If the module family does not have per ingest job options, this
     * method should either throw an UnsupportedOperationException or the
     * factory should extend IngestModuleFactoryAdapter.
     *
     * @param ingestOptions Per ingest job options to initialize the panel.
     * @return A user interface panel. The factory should be stateless and
     * should not hold a reference to the panel.
     * @throws
     * org.sleuthkit.autopsy.ingest.IngestModuleFactory.InvalidOptionsException
     */
    IngestModuleOptionsPanel getIngestOptionsPanel(IngestModuleOptions ingestOptions) throws InvalidOptionsException;

    /**
     * Queries the factory to determine if it provides user interface panels to
     * configure resources to be used by instances of the family of ingest
     * modules the factory creates. For example, the core hash lookup and
     * keyword search ingest modules provide resource configuration panels to
     * import hash databases and keyword lists. The imported hash databases and
     * keyword lists are then enabled or disabled per ingest job using per
     * ingest job options panels.
     *
     * @return True if the factory provides global options panels, false
     * otherwise.
     */
    boolean providesResourcesConfigPanels();

    /**
     * Gets a user interface panel that can be used to configure resources for
     * instances of the family of ingest modules the factory creates. For
     * example, the core hash lookup and keyword search ingest modules provide
     * resource configuration panels to import hash databases and keyword lists.
     * The imported hash databases and keyword lists are then enabled or
     * disabled per ingest job using per ingest job options panels. If the
     * module family does not have resources to configure, this method should
     * either throw an UnsupportedOperationException or the factory should
     * extend IngestModuleFactoryAdapter.
     *
     * @return A user interface panel. The factory should be stateless and
     * should not hold a reference to the panel.
     */
    IngestModuleResourcesConfigPanel getResourcesConfigPanel();

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * modules.
     *
     * @return True if the factory can create file ingest modules.
     */
    boolean isDataSourceIngestModuleFactory();

    /**
     * Creates a data source ingest module instance.
     *
     * @param ingestOptions The ingest options to use to configure the module.
     * @return A data source ingest module instance created using the provided
     * ingest options.
     */
    DataSourceIngestModule createDataSourceIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException;

    /**
     * Queries the factory to determine if it is capable of creating data source
     * ingest modules.
     *
     * @return True if the factory can create data source ingest modules.
     */
    boolean isFileIngestModuleFactory();

    /**
     * Creates a file ingest module instance.
     *
     * @param ingestOptions The ingest options to use to configure the module.
     * @return A file ingest module instance created using the provided ingest
     * options.
     */
    FileIngestModule createFileIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException;
}
