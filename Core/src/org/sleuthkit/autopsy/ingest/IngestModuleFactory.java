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
 * ingest module factory will be used to create instances of a type of data
 * source ingest module, a type of file ingest module, or both.
 * <P>
 * IMPORTANT: The factory should be stateless to support context-sensitive use
 * of the factory. The ingest framework is responsible for managing context
 * switching and the persistence of resource configurations and per ingest job
 * options.
 * <p>
 * IMPORTANT: The ingest framework will create one or more instances of each
 * module type for each ingest job it performs. The ingest framework may use
 * multiple threads to complete an ingest job, but it is guaranteed that there
 * will be no more than one module instance per thread. However, if these
 * instances must share resources, the modules are responsible for synchronizing
 * access to the shared resources and doing reference counting as required to
 * release those resources correctly.
 * <p>
 * IMPORTANT: To be discovered at runtime by the ingest framework,
 * IngestModuleFactory implementations must be marked with the following
 * NetBeans Service provider annotation:
 *
 * @ServiceProvider(service=IngestModuleFactory.class)
 */
public interface IngestModuleFactory {

    /**
     * Gets the display name that identifies the family of ingest modules the
     * factory creates.
     *
     * @return The module family display name.
     */
    String getModuleDisplayName();

    /**
     * Gets a brief, user-friendly description of the family of ingest modules
     * the factory creates.
     *
     * @return The module family description.
     */
    String getModuleDescription();

    /**
     * Gets the version number of the family of ingest modules the factory
     * creates.
     *
     * @return The module family version number.
     */
    String getModuleVersionNumber();

    /**
     * Queries the factory to determine if it provides user interface panels to
     * configure resources to be used by instances of the family of ingest
     * modules the factory creates. For example, the core hash lookup ingest
     * module factory provides resource configuration panels to import and
     * create hash databases. The hash databases are then enabled or disabled
     * per ingest job using ingest job options panels. If the module family does
     * not have a resources configuration, the factory should extend
     * IngestModuleFactoryAdapter to get an implementation of this method that
     * returns false.
     *
     * @return True if the factory provides resource configuration panels.
     */
    boolean providesGlobalSettingsPanel();

    /**
     * Gets a user interface panel that can be used to configure resources for
     * instances of the family of ingest modules the factory creates. For
     * example, the core hash lookup ingest module factory provides a resource
     * configuration panel to import and create hash databases. The imported
     * hash databases are then enabled or disabled per ingest job using ingest
     * options panels. If the module family does not have a resources
     * configuration, the factory should extend IngestModuleFactoryAdapter to
     * get an implementation of this method that throws an
     * UnsupportedOperationException.
     * <p>
     * IMPORTANT: The ingest framework assumes that ingest module factories are
     * stateless to support context-sensitive use of the factory, with the
     * ingest framework managing context switching and the persistence of
     * resource configurations and per ingest job options. A factory should not
     * retain references to the resources configuration panels it creates.
     *
     * @param resourcesConfig A resources configuration with which to initialize
     * the panel.
     * @return A user interface panel for configuring ingest module resources.
     */
    IngestModuleGlobalSetttingsPanel getGlobalSettingsPanel();

    /**
     * Gets the default per ingest job options for instances of the family of
     * ingest modules the factory creates. For example, the core hash lookup
     * ingest modules family has a resources configuration consisting of hash
     * databases, all of which are enabled by default for an ingest job. If the
     * module family does not have per ingest job options, the factory should
     * extend IngestModuleFactoryAdapter to get an implementation of this method
     * that returns an instance of the NoIngestJobOptions class.
     *
     * @return The ingest options.
     */
    IngestModuleSettings getDefaultModuleSettings();

    /**
     * Queries the factory to determine if it provides user interface panels to
     * set per ingest job options for instances of the family of ingest modules
     * the factory creates. For example, the core hash lookup ingest module
     * factory provides ingest options panels to enable or disable hash
     * databases per ingest job. If the module family does not have per ingest
     * job options, the factory should extend IngestModuleFactoryAdapter to get
     * an implementation of this method that returns false.
     *
     * @return True if the factory provides ingest job options panels.
     */
    boolean providesModuleSettingsPanel();

    /**
     * Gets a user interface panel that can be used to set per ingest job
     * options for instances of the family of ingest modules the factory
     * creates. For example, the core hash lookup ingest module factory provides
     * ingest options panels to enable or disable hash databases per ingest job.
     * If the module family does not have ingest job options, the factory should
     * extend IngestModuleFactoryAdapter to get an implementation of this method
     * that throws an UnsupportedOperationException.
     * <p>
     * IMPORTANT: The ingest framework assumes that ingest module factories are
     * stateless to support context-sensitive use of the factory. The ingest
     * framework is responsible for managing context switching and the
     * persistence of resource configurations and per ingest job options. A
     * factory should not retain references to the ingest job options panels it
     * creates.
     *
     * @param resourcesConfig
     * @param ingestOptions Per ingest job options to initialize the panel.
     * @return A user interface panel.
     */
    IngestModuleJobSettingsPanel getModuleSettingsPanel(IngestModuleSettings ingestOptions);

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * modules.
     *
     * @return True if the factory can create file ingest modules.
     */
    boolean isDataSourceIngestModuleFactory();

    /**
     * Creates a data source ingest module instance.
     * <p>
     * IMPORTANT: The factory should be stateless to support context-sensitive
     * use of the factory. The ingest framework is responsible for managing
     * context switching and the persistence of resource configurations and per
     * ingest job options. A factory should not retain references to the data
     * source ingest module instances it creates.
     * <p>
     * IMPORTANT: The ingest framework will create one or more data source
     * ingest module instances for each ingest job it performs. The ingest
     * framework may use multiple threads to complete an ingest job, but it is
     * guaranteed that there will be no more than one module instance per
     * thread. However, if these instances must share resources, the modules are
     * responsible for synchronizing access to the shared resources and doing
     * reference counting as required to release those resources correctly.
     *
     * @param ingestOptions The ingest options for the module instance.
     * @return A data source ingest module instance.
     */
    DataSourceIngestModule createDataSourceIngestModule(IngestModuleSettings ingestOptions);

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * module instances.
     *
     * @return True if the factory can create file ingest module instances.
     */
    boolean isFileIngestModuleFactory();

    /**
     * Creates a file ingest module instance.
     * <p>
     * IMPORTANT: The factory should be stateless to support context-sensitive
     * use of the factory. The ingest framework is responsible for managing
     * context switching and the persistence of resource configurations and per
     * ingest job options. A factory should not retain references to the file
     * ingest module instances it creates.
     * <p>
     * IMPORTANT: The ingest framework will create one or more file ingest
     * module instances for each ingest job it performs. The ingest framework
     * may use multiple threads to complete an ingest job, but it is guaranteed
     * that there will be no more than one module instance per thread. However,
     * if these instances must share resources, the modules are responsible for
     * synchronizing access to the shared resources and doing reference counting
     * as required to release those resources correctly.
     *
     * @param ingestOptions The ingest options for the module instance.
     * @return A file ingest module instance.
     */
    FileIngestModule createFileIngestModule(IngestModuleSettings ingestOptions);
}
