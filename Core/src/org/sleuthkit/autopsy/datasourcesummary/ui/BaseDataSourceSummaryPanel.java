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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.SwingWorkerSequentialExecutor;
import org.sleuthkit.datamodel.DataSource;

/**
 * Base class from which other tabs in data source summary derive.
 */
abstract class BaseDataSourceSummaryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SwingWorkerSequentialExecutor executor = new SwingWorkerSequentialExecutor();

    private List<DataFetchComponents<DataSource, ?>> dataFetchComponents = Collections.emptyList();
    private List<LoadableComponent> loadableComponents = Collections.emptyList();
    private DataSource dataSource;

    /**
     * Sets the means for obtaining workers to load data for this panel.
     *
     * @param dataFetchComponents The data fetch components to trigger when
     *                            loading data.
     */
    protected final void setDataFetchComponents(List<? extends DataFetchComponents<DataSource, ?>> dataFetchComponents) {
        this.dataFetchComponents = dataFetchComponents == null ? Collections.emptyList() : new ArrayList<>(dataFetchComponents);
    }

    /**
     * Sets the components that will need to be loaded. In particular, set them
     * to loading when workers are submitted for run.
     *
     * @param loadableComponents The loadable components.
     */
    protected final void setLoadableComponents(List<? extends LoadableComponent> loadableComponents) {
        this.loadableComponents = loadableComponents == null ? Collections.emptyList() : new ArrayList<>(loadableComponents);
    }

    /**
     * Sets datasource to visualize in the panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    synchronized void setDataSource(DataSource dataSource) {
        if (dataSource != this.dataSource) {
            this.dataSource = dataSource;
            this.executor.cancelRunning();
            onNewDataSource(this.dataSource);
        }
    }

    /**
     * Submits the following swing workers for execution in sequential order. If
     * there are any previous workers, those workers are cancelled.
     *
     * @param workers The workers to submit for execution.
     */
    protected void submit(List<? extends SwingWorker<?, ?>> workers) {
        executor.submit(workers);
    }

    /**
     * When a data source is updated this function is triggered.
     *
     * @param dataSource
     */
    synchronized void onRefresh() {
        // don't update the data source if it is already trying to load
        if (!executor.isRunning()) {
            // trigger on new data source with the current data source
            onNewDataSource(this.dataSource);
        }
    }

    /**
     * When a new dataSource is added, this method is called.
     *
     * @param dataSource The new dataSource.
     */
    protected void onNewDataSource(DataSource dataSource) {
        // if no data source is present or the case is not open,
        // set results for tables to null.
        if (dataSource == null || !Case.isCaseOpen()) {
            this.dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataFetchResult.getSuccessResult(null)));

        } else {
            // set tables to display loading screen
            this.loadableComponents.forEach((table) -> table.showDefaultLoadingMessage());

            // create swing workers to run for each loadable item
            List<DataFetchWorker<?, ?>> workers = dataFetchComponents
                    .stream()
                    .map((components) -> new DataFetchWorker<>(components, dataSource))
                    .collect(Collectors.toList());

            // submit swing workers to run
            if (workers.size() > 0) {
                submit(workers);
            }
        }
    }
}
