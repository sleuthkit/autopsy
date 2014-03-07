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

import javax.swing.JPanel;

/**
 * An interface that must be implemented by all providers of ingest modules. An
 * IngestModuleFactory will be used as a stateless source of one or more
 * instances of a family of configurable ingest modules. IngestModuleFactory
 * implementations must be marked with the NetBeans Service provider annotation
 * as follows:
 *
 * @ServiceProvider(service=IngestModuleFactory.class)
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
     * Gets the default ingest options for instances of the family of ingest
     * modules the factory creates. Ingest options are serializable to support
     * the persistence of possibly different options for different module
     * execution contexts.
     *
     * @return The ingest options.
     */
    IngestModuleOptions getDefaultIngestOptions();

    /**
     * Queries the factory to determine if it provides user interface panels
     * that can be used to specify the ingest options for instances of the
     * family of ingest modules the factory creates.
     *
     * @return True if the factory provides ingest options panels.
     */
//    boolean providesIngestOptionsPanels();

    /**
     * Gets a user interface panel that can be used to specify the ingest
     * options for instances of the family of ingest modules the factory
     * creates.
     *
     * @param ingestOptions A set of ingest options to be used to initialize the
     * panel.
     * @return A user interface panel. It is assumed that the factory is
     * stateless and will not hold a reference to the panel.
     */
    IngestModuleOptionsPanel getIngestOptionsPanel(IngestModuleOptions ingestOptions) throws InvalidOptionsException;

    /**
     * Gets ingest options for instances of the family of ingest modules the
     * factory creates from an ingest options panel. Ingest options are
     * serializable to support the persistence of possibly different options for
     * different module execution contexts.
     *
     * @param ingestOptionsPanel The ingest options panel.
     * @return The ingest options from the panel.
     */
//    IngestModuleOptions getIngestOptionsFromPanel(JPanel ingestOptionsPanel); RJCTODO

    /**
     * Queries the factory to determine if it provides user interface panels
     * that can be used to specify global options for all instances of the
     * family of ingest modules the factory creates.
     *
     * @return True if the factory provides global options panels.
     */
    boolean providesGlobalOptionsPanels();

    /**
     * Gets a user interface panel that can be used to specify the global
     * options for all instances of the family of ingest modules the factory
     * creates. PLEASE TAKE NOTICE: The factory should initialize the panel from
     * its own persistence of global options to disk in the directory returned
     * by PlatformUtil.getUserConfigDirectory(). In the future, this method will
     * be deprecated and the factory will be expected to receive global options
     * in serializable form.
     *
     * @return A user interface panel. It is assumed that the factory is
     * stateless and will not hold a reference to the panel.
     */
    JPanel getGlobalOptionsPanel();

    /**
     * Get the global options for instances of the family of ingest modules the
     * factory creates from a global options panel and saves the options to
     * persistent storage on disk in the directory returned by
     * PlatformUtil.getUserConfigDirectory(). PLEASE TAKE NOTICE: In the future,
     * this method will be deprecated and the factory will be expected to supply
     * global options in serializable form in a getGlobalOptionsFromPanel()
     * method.
     *
     * @param globalOptionsPanel
     * @throws
     * org.sleuthkit.autopsy.ingest.IngestModuleFactory.InvalidOptionsException
     */
    void saveGlobalOptionsFromPanel(JPanel globalOptionsPanel) throws InvalidOptionsException;

    /**
     * Queries the factory to determine if it is capable of creating file ingest
     * modules.
     *
     * @return True if the factory can create file ingest modules.
     */
    boolean isDataSourceIngestModuleFactory();

    /**
     * Creates a data source ingest module.
     *
     * @param ingestOptions The ingest options to use to configure the module.
     * @return An instance of a data source ingest module created using the
     * provided ingest options.
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
     * Creates a data source ingest module.
     *
     * @param ingestOptions The ingest options to use to configure the module.
     * @return An instance of a data source ingest module created using the
     * provided ingest options.
     */
    FileIngestModule createFileIngestModule(IngestModuleOptions ingestOptions) throws InvalidOptionsException;
}
