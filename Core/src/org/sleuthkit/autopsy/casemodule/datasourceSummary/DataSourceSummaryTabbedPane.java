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
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.util.Map;
import javax.swing.JTabbedPane;
import org.sleuthkit.autopsy.casemodule.IngestJobInfoPanel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tabbed pane showing the summary of a data source including tabs of:
 * DataSourceSummaryCountsPanel, DataSourceSummaryDetailsPanel, and 
 * IngestJobInfoPanel.
 */
public class DataSourceSummaryTabbedPane extends JTabbedPane {
    private final DataSourceSummaryCountsPanel countsPanel;
    private final DataSourceSummaryDetailsPanel detailsPanel;
    private final IngestJobInfoPanel ingestHistoryPanel;
    
    private DataSource dataSource = null;
        
    public DataSourceSummaryTabbedPane() {
        Map<Long, Long> fileCountsMap = DataSourceInfoUtilities.getCountsOfFiles();
        
        
        countsPanel = new DataSourceSummaryCountsPanel(fileCountsMap);
        detailsPanel = new DataSourceSummaryDetailsPanel();
        ingestHistoryPanel = new IngestJobInfoPanel();
        
        addTab(Bundle.DataSourceSummaryDialog_detailsTab_title(), detailsPanel);
        addTab(Bundle.DataSourceSummaryDialog_countsTab_title(), countsPanel);
        addTab(Bundle.DataSourceSummaryDialog_ingestHistoryTab_title(), ingestHistoryPanel);
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        
        
        countsPanel.updateCountsTableData(dataSource);
        detailsPanel.setDataSource(dataSource);
        ingestHistoryPanel.setDataSource(dataSource);
        // TODO trigger updates in child components
    }
}
