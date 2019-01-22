/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.datasourceSummary;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CaseDbAccessManager.CaseDbAccessQueryCallback;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;

final class DataSourceSummaryDialog extends javax.swing.JDialog implements Observer{

    private static final long serialVersionUID = 1L;
    private final List<IngestJobInfo> allIngestJobs = new ArrayList<>();
    private List<IngestJobInfo> ingestJobs = new ArrayList<>();
    private IngestJobTableModel ingestJobTableModel = new IngestJobTableModel();
    private DataSourceSummaryFilesPanel filesPanel = new DataSourceSummaryFilesPanel();
    private DataSourceSummaryDetailsPanel detailsPanel = new DataSourceSummaryDetailsPanel();
    private DataSourceBrowser dataSourcesPanel = new DataSourceBrowser();
    private final DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private static final Logger logger = Logger.getLogger(DataSourceSummaryDialog.class.getName());

    /**
     * Creates new form DataSourceSummaryPanel for displaying a summary of the
     * data sources for the fcurrent case and the contents found for each
     * datasource.
     */
    @Messages({"DataSourceSummaryPanel.window.title=Data Sources Summary","DataSourceSummaryPanel.getDataSources.error.text=Failed to get the list of datasources for the current case.",
        "DataSourceSummaryPanel.getDataSources.error.title=Load Failure"})
    DataSourceSummaryDialog(Frame owner) {
        super(owner, Bundle.DataSourceSummaryPanel_window_title(), true);
        initComponents();
        dataSourceSummarySplitPane.setLeftComponent(dataSourcesPanel);
        dataSourceTabbedPane.add("Files",filesPanel);
        dataSourceTabbedPane.addTab("Details", detailsPanel);
//        ingestJobsTable.getTableHeader().setReorderingAllowed(false);
//        fileCountsTable.getTableHeader().setReorderingAllowed(false);
//        dataSourcesTable.getTableHeader().setReorderingAllowed(false);
//        try {
//            SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
//            allIngestJobs.addAll(skCase.getIngestJobs());
            //if for some reason multiple OS_INFO_ARTIFACTS were created with the same parent object id this will only return one OSInfo object for them
//        } catch (TskCoreException | NoCurrentCaseException ex) {
//            logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
//            JOptionPane.showMessageDialog(this, Bundle.DataSourceSummaryPanel_getDataSources_error_text(), Bundle.DataSourceSummaryPanel_getDataSources_error_title(), JOptionPane.ERROR_MESSAGE);
//        }
        dataSourcesPanel.addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                DataSource selectedDataSource = dataSourcesPanel.getSelectedDataSource();
                updateIngestJobs(selectedDataSource);
                filesPanel.updateFilesTableData(selectedDataSource);
                detailsPanel.updateDetailsPanelData(selectedDataSource);
                this.repaint();
            }
        });
        dataSourcesPanel.addObserver(this);
        this.pack();
    }

    @Override
    public void update(Observable o, Object arg) {
        this.dispose();
    }

    /**
     * Update the ingestJobs list with the ingest jobs for the
     * selectedDataSource
     *
     * @param selectedDataSource the datasource to find the ingest jobs for
     */
    @Messages({"DataSourceSummaryPanel.loadIngestJob.error.text=Failed to load ingest jobs.",
        "DataSourceSummaryPanel.loadIngestJob.error.title=Load Failure"})
    private void updateIngestJobs(DataSource selectedDataSource) {
        ingestJobs.clear();
        if (selectedDataSource != null) {
            for (IngestJobInfo ingestJob : allIngestJobs) {
                if (ingestJob.getObjectId() == selectedDataSource.getId()) {
                    ingestJobs.add(ingestJob);
                }
            }
        }
        ingestJobTableModel = new IngestJobTableModel();
//        ingestJobsTable.setModel(ingestJobTableModel);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        closeButton = new javax.swing.JButton();
        dataSourceSummarySplitPane = new javax.swing.JSplitPane();
        dataSourceTabbedPane = new javax.swing.JTabbedPane();

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(DataSourceSummaryDialog.class, "DataSourceSummaryDialog.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        dataSourceSummarySplitPane.setDividerLocation(100);
        dataSourceSummarySplitPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        dataSourceSummarySplitPane.setRightComponent(dataSourceTabbedPane);

        dataSourceSummarySplitPane.setLeftComponent(dataSourcesPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(dataSourceSummarySplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 630, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(closeButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(dataSourceSummarySplitPane, javax.swing.GroupLayout.DEFAULT_SIZE, 292, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(closeButton)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    /**
     * Select the data source with the specicied data source id. If no data
     * source matches the dataSourceID it will select the first datasource.
     *
     * @param dataSourceID the ID of the datasource to select, null will cause
     *                     the first datasource to be selected
     */
    void selectDataSource(Long dataSourceID) {
//        if (dataSourceID != null) {
//            for (int i = 0; i < dataSources.size(); i++) {
//                if (dataSources.get(i).getId() == dataSourceID) {
//                    dataSourcesTable.setRowSelectionInterval(i, i);
//                    //scroll down from top of table to where selected datasource is
//                    dataSourcesTable.scrollRectToVisible(new Rectangle(dataSourcesTable.getCellRect(i, 0, true)));
//                    return;
//                }
//            }
//        }
//        //if there are data sources in the list and none were found that matched the specied dataSourceID select the first one
//        if (!dataSources.isEmpty()) {
//            dataSourcesTable.setRowSelectionInterval(0, 0);
//        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JSplitPane dataSourceSummarySplitPane;
    private javax.swing.JTabbedPane dataSourceTabbedPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Table model for the Ingest Job table to display ingest jobs for the
     * selected datasource.
     */
    @Messages({"DataSourceSummaryPanel.IngestJobTableModel.StartTime.header=Start Time",
        "DataSourceSummaryPanel.IngestJobTableModel.EndTime.header=End Time",
        "DataSourceSummaryPanel.IngestJobTableModel.IngestStatus.header=Ingest Status"})
    private class IngestJobTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final List<String> columnHeaders = new ArrayList<>();

        /**
         * Create a new IngestJobTableModel
         */
        IngestJobTableModel() {
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_StartTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_EndTime_header());
            columnHeaders.add(Bundle.DataSourceSummaryPanel_IngestJobTableModel_IngestStatus_header());
        }

        @Override
        public int getRowCount() {
            return ingestJobs.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobInfo currIngestJob = ingestJobs.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return datetimeFormat.format(currIngestJob.getStartDateTime());
                case 1:
                    Date endDate = currIngestJob.getEndDateTime();
                    if (endDate.getTime() == 0) {
                        return "N/A";
                    }
                    return datetimeFormat.format(currIngestJob.getEndDateTime());
                case 2:
                    return currIngestJob.getStatus().getDisplayName();
                default:
                    break;
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }
    }
}
