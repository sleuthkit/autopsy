/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2021 Basis Technology Corp.
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
 * An interface that must be implemented by all providers of Autopsy ingest
 * modules. An ingest module factory is used to create instances of a type of
 * data source ingest module, a type of file ingest module, or both.
 * <p>
 * Autopsy will generally use the factory to create several instances of each
 * type of module for each ingest job it performs. Completing an ingest job
 * entails processing a single data source (e.g., a disk image) and all of the
 * files from the data source, including files extracted from archives and any
 * unallocated space (made to look like a series of files). The data source is
 * passed through one or more pipelines of data source ingest modules. The files
 * are passed through one or more pipelines of file ingest modules.
 * <p>
 * Autopsy may use multiple threads to complete an ingest job, but it is
 * guaranteed that there will be no more than one module instance per thread.
 * However, if the module instances must share resources, the modules are
 * responsible for synchronizing access to the shared resources and doing
 * reference counting as required to release those resources correctly. Also,
 * more than one ingest job may be in progress at any given time. This must also
 * be taken into consideration when sharing resources between module instances.
 * <p>
 * An ingest module factory may provide global and per ingest job settings user
 * interface panels. The global settings should apply to all module instances.
 * The per ingest job settings should apply to all module instances working on a
 * particular ingest job. Autopsy supports context-sensitive and persistent per
 * ingest job settings, so per ingest job settings must be serializable.
 * <p>
 * To be discovered at runtime by the ingest framework, IngestModuleFactory
 * implementations must be marked with the following NetBeans Service provider
 * annotation:
 *
 * <pre>\@ServiceProvider(service=IngestModuleFactory.class)</pre>
 * <p>
 * IMPORTANT TIP: If an implementation of IngestModuleFactory does not need to
 * provide implementations of all of the IngestModuleFactory methods, it can
 * extend the abstract class IngestModuleFactoryAdapter to get default
 * implementations of most of the IngestModuleFactory methods.
 */
public interface IngestModuleFactory {

    /**
     * Gets the display name that identifies the family of ingest modules the
     * factory creates. Autopsy uses this string to identify the module in user
     * interface components and log messages. The module name must be unique. so
     * a brief but distinctive name is recommended.
     *
     * @return The module family display name.
     */
    String getModuleDisplayName();

    /**
     * Gets a brief, user-friendly description of the family of ingest modules
     * the factory creates. Autopsy uses this string to describe the module in
     * user interface components.
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
     * Queries the factory to determine if it provides a user interface panel to
     * allow a user to change settings that are used by all instances of the
     * family of ingest modules the factory creates. For example, the Autopsy
     * core hash lookup ingest module factory provides a global settings panel
     * to import and create hash databases. The hash databases are then enabled
     * or disabled per ingest job using an ingest job settings panel.
     *
     * @return True if the factory provides a global settings panel.
     */
    default boolean hasGlobalSettingsPanel() {
        return false;
    }

    /**
     * Gets a user interface panel that allows a user to change settings that
     * are used by all instances of the family of ingest modules the factory
     * creates. For example, the Autopsy core hash lookup ingest module factory
     * provides a global settings panel to import and create hash databases. The
     * imported hash databases are then enabled or disabled per ingest job using
     * ingest an ingest job settings panel.
     *
     * @return A global settings panel.
     */
    default IngestModuleGlobalSettingsPanel getGlobalSettingsPanel() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the default per ingest job settings for instances of the family of
     * ingest modules the factory creates. For example, the Autopsy core hash
     * lookup ingest modules family uses hash databases imported or created
     * using its global settings panel. All of the hash databases are enabled by
     * default for an ingest job.
     *
     * @return The default ingest job settings.
     */
    default IngestModuleIngestJobSettings getDefaultIngestJobSettings() {
        return new NoIngestModuleIngestJobSettings();
    }

    /**
     * Queries the factory to determine if it provides user a interface panel to
     * allow a user to make per ingest job settings for instances of the family
     * of ingest modules the factory creates. For example, the Autopsy core hash
     * lookup ingest module factory provides an ingest job settings panels to
     * enable or disable hash databases per ingest job.
     *
     * @return True if the factory provides ingest job settings panels.
     */
    default boolean hasIngestJobSettingsPanel() {
        return false;
    }

    /**
     * Gets a user interface panel that can be used to set per ingest job
     * settings for instances of the family of ingest modules the factory
     * creates. For example, the core hash lookup ingest module factory provides
     * an ingest job settings panel to enable or disable hash databases per
     * ingest job.
     *
     * @param settings Per ingest job settings to initialize the panel.
     *
     * @return An ingest job settings panel.
     */
    default IngestModuleIngestJobSettingsPanel getIngestJobSettingsPanel(IngestModuleIngestJobSettings settings) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the factory to determine if it is capable of creating data source
     * ingest modules.
     *
     * @return True if the factory can create data source ingest modules.
     */
    default boolean isDataSourceIngestModuleFactory() {
        return false;
    }

    /**
     * Creates a data source ingest module instance.
     * <p>
     * Autopsy will generally use the factory to several instances of each type
     * of module for each ingest job it performs. Completing an ingest job
     * entails processing a single data source (e.g., a disk image) and all of
     * the files from the data source, including files extracted from archives
     * and any unallocated space (made to look like a series of files). The data
     * source is passed through one or more pipelines of data source ingest
     * modules. The files are passed through one or more pipelines of file
     * ingest modules.
     * <p>
     * The ingest framework may use multiple threads to complete an ingest job,
     * but it is guaranteed that there will be no more than one module instance
     * per thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances. modules.
     *
     * @param ingestOptions The settings for the ingest job.
     *
     * @return A data source ingest module instance.
     */
    default DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * modules.
     *
     * @return True if the factory can create file ingest modules.
     */
    default boolean isFileIngestModuleFactory() {
        return false;
    }

    /**
     * Creates a file ingest module instance.
     * <p>
     * Autopsy will generally use the factory to several instances of each type
     * of module for each ingest job it performs. Completing an ingest job
     * entails processing a single data source (e.g., a disk image) and all of
     * the files from the data source, including files extracted from archives
     * and any unallocated space (made to look like a series of files). The data
     * source is passed through one or more pipelines of data source ingest
     * modules. The files are passed through one or more pipelines of file
     * ingest modules.
     * <p>
     * The ingest framework may use multiple threads to complete an ingest job,
     * but it is guaranteed that there will be no more than one module instance
     * per thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances. modules.
     *
     * @param settings The settings for the ingest job.
     *
     * @return A file ingest module instance.
     */
    default FileIngestModule createFileIngestModule(IngestModuleIngestJobSettings ingestOptions) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the factory to determine if it is capable of creating data
     * artifact ingest modules.
     *
     * @return True or false.
     */
    default boolean isDataArtifactIngestModuleFactory() {
        return false;
    }

    /**
     * Creates a data artifact ingest module instance.
     * <p>
     * Autopsy will generally use the factory to several instances of each type
     * of module for each ingest job it performs. Completing an ingest job
     * entails processing a single data source (e.g., a disk image) and all of
     * the files from the data source, including files extracted from archives
     * and any unallocated space (made to look like a series of files). The data
     * source is passed through one or more pipelines of data source ingest
     * modules. The files are passed through one or more pipelines of file
     * ingest modules.
     * <p>
     * The ingest framework may use multiple threads to complete an ingest job,
     * but it is guaranteed that there will be no more than one module instance
     * per thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances. modules.
     *
     * @param settings The settings for the ingest job.
     *
     * @return A file ingest module instance.
     */
    default DataArtifactIngestModule createDataArtifactIngestModule(IngestModuleIngestJobSettings settings) {
        throw new UnsupportedOperationException();
    }

    /**
     * Queries the factory to determine if it is capable of creating analysis
     * result ingest modules.
     *
     * @return True or false.
     */
    default boolean isAnalysisResultIngestModuleFactory() {
        return false;
    }

    /**
     * Creates an analysis result ingest module instance.
     * <p>
     * Autopsy will generally use the factory to several instances of each type
     * of module for each ingest job it performs. Completing an ingest job
     * entails processing a single data source (e.g., a disk image) and all of
     * the files from the data source, including files extracted from archives
     * and any unallocated space (made to look like a series of files). The data
     * source is passed through one or more pipelines of data source ingest
     * modules. The files are passed through one or more pipelines of file
     * ingest modules.
     * <p>
     * The ingest framework may use multiple threads to complete an ingest job,
     * but it is guaranteed that there will be no more than one module instance
     * per thread. However, if the module instances must share resources, the
     * modules are responsible for synchronizing access to the shared resources
     * and doing reference counting as required to release those resources
     * correctly. Also, more than one ingest job may be in progress at any given
     * time. This must also be taken into consideration when sharing resources
     * between module instances. modules.
     *
     * @param settings The settings for the ingest job.
     *
     * @return A file ingest module instance.
     */
    default AnalysisResultIngestModule createAnalysisResultIngestModule(IngestModuleIngestJobSettings settings) {
        throw new UnsupportedOperationException();
    }

}
