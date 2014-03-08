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
 * Base class for ingest module resources configuration panels.
 */
public abstract class IngestModuleResourcesConfigPanel extends JPanel {

    /**
     * Initializes the resources configuration panel for an ingest module.
     *
     * @param ingestOptions The initial state of the resources configuration.
     */
    public abstract void initialize(IngestModuleResourcesConfig ingestOptions);

    /**
     * Gets the resources configuration for an ingest module.
     *
     * @return The resources configuration.
     */
    public abstract IngestModuleResourcesConfig getResourcesConfig();
}