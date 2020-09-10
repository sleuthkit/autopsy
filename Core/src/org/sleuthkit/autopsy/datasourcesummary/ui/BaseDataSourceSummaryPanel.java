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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceSummaryDataModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.SwingWorkerSequentialExecutor;
import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.DataSource;

/**
 * Base class from which other tabs in data source summary derive.
 */
abstract class BaseDataSourceSummaryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SwingWorkerSequentialExecutor executor = new SwingWorkerSequentialExecutor();

    private final RefreshThrottler refreshThrottler = new RefreshThrottler(new RefreshThrottler.Refresher() {
        @Override
        public void refresh() {
            onRefresh();
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            String eventType = evt.getPropertyName();
            if (Case.isCaseOpen()) {
                if (IngestModuleEvent.DATA_ADDED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleDataEvent) {
                    ModuleDataEvent dataEvent = (ModuleDataEvent) evt.getOldValue();
                    return BaseDataSourceSummaryPanel.this.isRefreshRequired(dataEvent);
                } else if (IngestModuleEvent.CONTENT_CHANGED.toString().equals(eventType) && evt.getOldValue() instanceof ModuleContentEvent) {
                    ModuleContentEvent contentEvent = (ModuleContentEvent) evt.getOldValue();
                    return BaseDataSourceSummaryPanel.this.isRefreshRequired(contentEvent);
                }
            }
            return false;
        }
    });

    private final PropertyChangeListener caseEventsListener = (evt) -> {
        switch (Case.Events.valueOf(evt.getPropertyName())) {

            default:
                if (isRefreshRequiredForCaseEvent(evt)) {
                    onRefresh();
                }
                break;
        }
    };

    private final Set<Integer> artifactTypesForRefresh;
    private final boolean refreshOnNewContent;

    private DataSource dataSource;

    private static <I, O> Set<O> getUnionSet(Function<I, Collection<? extends O>> mapper, I... items) {
        return Stream.of(items)
                .flatMap((item) -> mapper.apply(item).stream())
                .collect(Collectors.toSet());
    }

    protected BaseDataSourceSummaryPanel(DataSourceSummaryDataModel...dataModels) {
        this(
                getUnionSet(DataSourceSummaryDataModel::getCaseEventUpdates, dataModels),
                getUnionSet(DataSourceSummaryDataModel::getArtifactIdUpdates, dataModels),
                Stream.of(dataModels).anyMatch(DataSourceSummaryDataModel::shouldRefreshOnNewContent)
        );
    }

    protected BaseDataSourceSummaryPanel(Set<Case.Events> caseEvents, Set<Integer> artifactTypesForRefresh, boolean refreshOnNewContent) {
        this.artifactTypesForRefresh = (artifactTypesForRefresh == null)
                ? new HashSet<>()
                : new HashSet<>(artifactTypesForRefresh);

        this.refreshOnNewContent = refreshOnNewContent;

        if (refreshOnNewContent || this.artifactTypesForRefresh.size() > 0) {
            refreshThrottler.registerForIngestModuleEvents();
        }

        if (caseEvents != null) {
            Case.addEventTypeSubscriber(caseEvents, caseEventsListener);
        }

    }

    protected boolean isRefreshRequired(ModuleDataEvent evt) {
        return artifactTypesForRefresh.contains(evt.getBlackboardArtifactType().getTypeID());
    }

    protected boolean isRefreshRequired(ModuleContentEvent evt) {
        return refreshOnNewContent;
    }

    protected boolean isRefreshRequiredForCaseEvent(PropertyChangeEvent evt) {
        return true;
    }

    /**
     * Sets datasource to visualize in the panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    synchronized void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        this.executor.cancelRunning();
        onNewDataSource(this.dataSource);
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
     * @param dataSource The data source.
     */
    synchronized void onRefresh() {
        // don't update the data source if it is already trying to load
        if (!executor.isRunning()) {
            // trigger on new data source with the current data source
            fetchInformation(this.dataSource);
        }
    }

    /**
     * Action that is called when information needs to be retrieved (on refresh
     * or on new data source).
     *
     * @param dataSource The datasource to fetch information about.
     */
    protected abstract void fetchInformation(DataSource dataSource);

    protected void fetchInformation(List<DataFetchComponents<DataSource, ?>> dataFetchComponents, DataSource dataSource) {
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

    /**
     * When a new dataSource is added, this method is called.
     *
     * @param dataSource The new dataSource.
     */
    protected abstract void onNewDataSource(DataSource dataSource);

    protected void onNewDataSource(
            List<DataFetchComponents<DataSource, ?>> dataFetchComponents, 
            List<? extends LoadableComponent<?>> loadableComponents, 
            DataSource dataSource) {
        // if no data source is present or the case is not open,
        // set results for tables to null.
        if (dataSource == null || !Case.isCaseOpen()) {
            dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataFetchResult.getSuccessResult(null)));

        } else {
            // set tables to display loading screen
            loadableComponents.forEach((table) -> table.showDefaultLoadingMessage());

            fetchInformation(dataSource);
        }
    }
}
