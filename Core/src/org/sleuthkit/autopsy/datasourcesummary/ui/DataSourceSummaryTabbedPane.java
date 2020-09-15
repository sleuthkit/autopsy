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

import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JTabbedPane;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.IngestJobInfoPanel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tabbed pane showing the summary of a data source including tabs of:
 * DataSourceSummaryCountsPanel, ContainerPanel, and IngestJobInfoPanel.
 */
@Messages({
    "DataSourceSummaryTabbedPane_typesTab_title=Types",
    "DataSourceSummaryTabbedPane_detailsTab_title=Container",
    "DataSourceSummaryTabbedPane_userActivityTab_title=User Activity",
    "DataSourceSummaryTabbedPane_ingestHistoryTab_title=Ingest History",
    "DataSourceSummaryTabbedPane_recentFileTab_title=Recent Files",
    "DataSourceSummaryTabbedPane_pastCasesTab_title=Past Cases",
    "DataSourceSummaryTabbedPane_analysisTab_title=Analysis"
})
public class DataSourceSummaryTabbedPane extends JTabbedPane {

    /**
     * Records of tab information (i.e. title, component, function to call on
     * new data source).
     */
    private static class DataSourceTab {

        private final String tabTitle;
        private final Component component;
        private final Consumer<DataSource> onDataSource;
        private final Runnable onClose;

        /**
         * Main constructor.
         *
         * @param tabTitle     The title of the tab.
         * @param component    The component to be displayed.
         * @param onDataSource The function to be called on a new data source.
         * @param onClose      Called to cleanup resources when closing tabs.
         */
        DataSourceTab(String tabTitle, Component component, Consumer<DataSource> onDataSource, Runnable onClose) {
            this.tabTitle = tabTitle;
            this.component = component;
            this.onDataSource = onDataSource;
            this.onClose = onClose;
        }

        /**
         * Main constructor.
         *
         * @param tabTitle The title of the tab.
         * @param panel    The component to be displayed in the tab.
         */
        DataSourceTab(String tabTitle, BaseDataSourceSummaryPanel panel) {
            this.tabTitle = tabTitle;
            this.component = panel;
            this.onDataSource = panel::setDataSource;
            this.onClose = panel::close;
        }

        /**
         * @return The title for the tab.
         */
        String getTabTitle() {
            return tabTitle;
        }

        /**
         * @return The component to display in the tab.
         */
        Component getComponent() {
            return component;
        }

        /**
         * @return The function to be called on new data source.
         */
        Consumer<DataSource> getOnDataSource() {
            return onDataSource;
        }

        /**
         * @return The action for closing resources in the tab.
         */
        public Runnable getOnClose() {
            return onClose;
        }
    }

    private static final long serialVersionUID = 1L;

    private final IngestJobInfoPanel ingestHistoryPanel = new IngestJobInfoPanel();

    private final List<DataSourceTab> tabs = Arrays.asList(
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_typesTab_title(), new TypesPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_userActivityTab_title(), new UserActivityPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_analysisTab_title(), new AnalysisPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_recentFileTab_title(), new RecentFilesPanel()),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_pastCasesTab_title(), new PastCasesPanel()),
            // do nothing on closing 
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_ingestHistoryTab_title(), ingestHistoryPanel, ingestHistoryPanel::setDataSource, () -> {
            }),
            new DataSourceTab(Bundle.DataSourceSummaryTabbedPane_detailsTab_title(), new ContainerPanel())
    );

    private DataSource dataSource = null;

    /**
     * Constructs a tabbed pane showing the summary of a data source.
     */
    public DataSourceSummaryTabbedPane() {
        initComponent();
    }

    private void initComponent() {
        for (DataSourceTab tab : tabs) {
            addTab(tab.getTabTitle(), tab.getComponent());
        }
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

        for (DataSourceTab tab : tabs) {
            tab.getOnDataSource().accept(dataSource);
        }
    }

    /**
     * Handle close events on each tab.
     */
    public void close() {
        for (DataSourceTab tab : tabs) {
            tab.getOnClose().run();
        }
    }
}
