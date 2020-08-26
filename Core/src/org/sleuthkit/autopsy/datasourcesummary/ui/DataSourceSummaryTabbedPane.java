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

import java.util.Arrays;
import java.util.List;
import javax.swing.JTabbedPane;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.IngestJobInfoPanel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tabbed pane showing the summary of a data source including tabs of:
 * DataSourceSummaryCountsPanel, DataSourceSummaryDetailsPanel, and
 * IngestJobInfoPanel.
 */
@Messages({
    "DataSourceSummaryTabbedPane_countsTab_title=Counts",
    "DataSourceSummaryTabbedPane_detailsTab_title=Details",
    "DataSourceSummaryTabbedPane_userActivityTab_title=User Activity",
    "DataSourceSummaryTabbedPane_ingestHistoryTab_title=Ingest History"
})
public class DataSourceSummaryTabbedPane extends JTabbedPane {

    private static final long serialVersionUID = 1L;

    // A pair of the tab name and the corresponding BaseDataSourceSummaryTabs to be displayed.
    private final List<Pair<String, BaseDataSourceSummaryTab>> tabs = Arrays.asList(
            Pair.of(Bundle.DataSourceSummaryTabbedPane_detailsTab_title(), new DataSourceSummaryDetailsPanel()),
            Pair.of(Bundle.DataSourceSummaryTabbedPane_countsTab_title(), new DataSourceSummaryCountsPanel()),
            Pair.of(Bundle.DataSourceSummaryTabbedPane_detailsTab_title(), new DataSourceSummaryUserActivityPanel())
    );

    private final IngestJobInfoPanel ingestHistoryPanel = new IngestJobInfoPanel();

    private DataSource dataSource = null;

    /**
     * Constructs a tabbed pane showing the summary of a data source.
     */
    public DataSourceSummaryTabbedPane() {
        for (Pair<String, BaseDataSourceSummaryTab> tab : tabs) {
            addTab(tab.getKey(), tab.getValue());
        }

        addTab(Bundle.DataSourceSummaryTabbedPane_ingestHistoryTab_title(), ingestHistoryPanel);
    }

    /**
     * The datasource currently used as the model in this panel.
     *
     * @return The datasource currently being used as the model in this panel.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets datasource to visualize in the tabbed panel.
     *
     * @param dataSource The datasource to use in this panel.
     */
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;

        for (Pair<String, BaseDataSourceSummaryTab> tab : tabs) {
            tab.getValue().setDataSource(dataSource);
        }

        ingestHistoryPanel.setDataSource(dataSource);
    }
}
