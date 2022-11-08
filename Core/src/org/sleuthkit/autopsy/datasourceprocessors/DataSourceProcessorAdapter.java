/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import javax.swing.JPanel;

import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorCallback;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessorProgressMonitor;

import org.sleuthkit.datamodel.Host;

/**
 * An adapter that provides no-op implementations of various DataSourceProcessor
 * methods.
 */

public abstract class DataSourceProcessorAdapter implements DataSourceProcessor {
    @Override
    public abstract String getDataSourceType();

    @Override
    public JPanel getPanel() {
        return null;
    }

    @Override
    public boolean isPanelValid() {
        return false;
    }

    @Override
    public void run(Host host, DataSourceProcessorProgressMonitor progressMonitor, DataSourceProcessorCallback callback) {
    }
}
