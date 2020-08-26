/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import javax.swing.JPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.SwingWorkerSequentialRunner;
import org.sleuthkit.datamodel.DataSource;

/**
 * Base class from which other tabs in data source summary derive.
 */
abstract class BaseDataSourceSummaryTab extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SwingWorkerSequentialRunner loader = new SwingWorkerSequentialRunner();
    private DataSource dataSource;

    /**
     * The datasource currently used as the model in this panel.
     *
     * @return The datasource currently being used as the model in this panel.
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets datasource to visualize in the panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    void setDataSource(DataSource dataSource) {
        DataSource oldDataSource = this.dataSource;
        this.dataSource = dataSource;
        if (this.dataSource != oldDataSource) {
            onNewDataSource(this.dataSource);
        }
    }

    /**
     * @return The sequential runner associated with this panel.
     */
    protected SwingWorkerSequentialRunner getLoader() {
        return loader;
    }

    /**
     * When a new dataSource is added, this method is called.
     *
     * @param dataSource The new dataSource.
     */
    protected abstract void onNewDataSource(DataSource dataSource);
}
