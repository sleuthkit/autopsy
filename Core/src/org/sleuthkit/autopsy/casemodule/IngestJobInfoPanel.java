/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.beans.PropertyChangeEvent;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle.Messages;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.IngestJobInfo;
import org.sleuthkit.datamodel.IngestModuleInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying ingest job history for a data source.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class IngestJobInfoPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(IngestJobInfoPanel.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.STARTED, IngestManager.IngestJobEvent.CANCELLED, IngestManager.IngestJobEvent.COMPLETED);
    private static final Set<Case.Events> CASE_EVENTS_OF_INTEREST = EnumSet.of(Case.Events.CURRENT_CASE);
    private static final int EXTRA_ROW_HEIGHT = 4;
    private final List<IngestJobInfo> ingestJobs = new ArrayList<>();
    private final List<IngestJobInfo> ingestJobsForSelectedDataSource = new ArrayList<>();
    private IngestJobTableModel ingestJobTableModel = new IngestJobTableModel();
    private IngestModuleTableModel ingestModuleTableModel = new IngestModuleTableModel(null);
    private final DateFormat datetimeFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private DataSource selectedDataSource;
    private static SwingWorker<Boolean, Void> refreshWorker = null;

    /**
     * Creates new form IngestJobInfoPanel
     */
    public IngestJobInfoPanel() {
        initComponents();
        customizeComponents();
    }

    @Messages({"IngestJobInfoPanel.loadIngestJob.error.text=Failed to load ingest jobs.",
        "IngestJobInfoPanel.loadIngestJob.error.title=Load Failure"})
    private void customizeComponents() {
        refresh();
        this.ingestJobTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            IngestJobInfo currJob = (ingestJobTable.getSelectedRow() < 0 ? null : this.ingestJobsForSelectedDataSource.get(ingestJobTable.getSelectedRow()));
            this.ingestModuleTableModel = new IngestModuleTableModel(currJob);
            this.ingestModuleTable.setModel(this.ingestModuleTableModel);
        });

        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, (PropertyChangeEvent evt) -> {
            if (evt.getPropertyName().equals(IngestManager.IngestJobEvent.STARTED.toString())
                    || evt.getPropertyName().equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                    || evt.getPropertyName().equals(IngestManager.IngestJobEvent.COMPLETED.toString())) {
                refresh();
            }
        });

        Case.addEventTypeSubscriber(CASE_EVENTS_OF_INTEREST, (PropertyChangeEvent evt) -> {
            if (!(evt instanceof AutopsyEvent) || (((AutopsyEvent) evt).getSourceType() != AutopsyEvent.SourceType.LOCAL)) {
                return;
            }

            // Check whether we have a case open or case close event.
            if ((CURRENT_CASE == Case.Events.valueOf(evt.getPropertyName()))) {
                if (evt.getNewValue() != null) {
                    // Case open
                    refresh();
                } else {
                    // Case close
                    reset();
                }
            }
        });
        ingestJobTable.setRowHeight(ingestJobTable.getRowHeight() + EXTRA_ROW_HEIGHT);
        ingestModuleTable.setRowHeight(ingestModuleTable.getRowHeight() + EXTRA_ROW_HEIGHT);

    }

    /**
     * Changes the data source for which ingest jobs are being displayed.
     *
     * @param selectedDataSource The data source.
     */
    public void setDataSource(DataSource selectedDataSource) {
        this.selectedDataSource = selectedDataSource;
        ingestJobsForSelectedDataSource.clear();
        if (selectedDataSource != null) {
            for (IngestJobInfo jobInfo : ingestJobs) {
                if (selectedDataSource.getId() == jobInfo.getObjectId()) {
                    ingestJobsForSelectedDataSource.add(jobInfo);
                }
            }
        }
        this.ingestJobTableModel = new IngestJobTableModel();

        SwingUtilities.invokeLater(() -> {
            this.ingestJobTable.setModel(ingestJobTableModel);
            //if there were ingest jobs select the first one by default
            if (!ingestJobsForSelectedDataSource.isEmpty()) {
                ingestJobTable.setRowSelectionInterval(0, 0);
            }
            this.repaint();
        });
    }

    /**
     * Get the updated complete list of ingest jobs.
     */
    private void refresh() {
        if (refreshWorker != null && !refreshWorker.isDone()) {
            refreshWorker.cancel(true);
        }
        refreshWorker = new SwingWorker<Boolean, Void>() {

            @Override
            protected Boolean doInBackground() throws Exception {
                ingestJobs.clear();
                try {
                    if (Case.isCaseOpen()) { // Note - this will generally return true when handling a case close event
                        SleuthkitCase skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
                        ingestJobs.addAll(skCase.getIngestJobs());
                        setDataSource(selectedDataSource);
                    } else {
                        setDataSource(null);
                    }
                    return true;
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Failed to load ingest jobs.", ex);
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (!get()) {
                        JOptionPane.showMessageDialog(IngestJobInfoPanel.this, Bundle.IngestJobInfoPanel_loadIngestJob_error_text(), Bundle.IngestJobInfoPanel_loadIngestJob_error_title(), JOptionPane.ERROR_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.WARNING, "Error getting results from Ingest Job Info Panel's refresh worker", ex);
                } catch (CancellationException ignored) {
                    logger.log(Level.INFO, "The refreshing of the IngestJobInfoPanel was cancelled");
                }
            }
        };
        refreshWorker.execute();
    }

    /**
     * Reset the panel.
     */
    private void reset() {
        if (refreshWorker != null) {
            refreshWorker.cancel(true);
        }
        this.ingestJobs.clear();
        setDataSource(null);
    }

    @Messages({"IngestJobInfoPanel.IngestJobTableModel.StartTime.header=Start Time",
        "IngestJobInfoPanel.IngestJobTableModel.EndTime.header=End Time",
        "IngestJobInfoPanel.IngestJobTableModel.IngestStatus.header=Ingest Status"})
    private class IngestJobTableModel extends AbstractTableModel {

        private final List<String> columnHeaders = new ArrayList<>();

        IngestJobTableModel() {
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_StartTime_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_EndTime_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestJobTableModel_IngestStatus_header());
        }

        @Override
        public int getRowCount() {
            return ingestJobsForSelectedDataSource.size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobInfo currIngestJob = ingestJobsForSelectedDataSource.get(rowIndex);
            if (columnIndex == 0) {
                return datetimeFormat.format(currIngestJob.getStartDateTime());
            } else if (columnIndex == 1) {
                Date endDate = currIngestJob.getEndDateTime();
                if (endDate.getTime() == 0) {
                    return "N/A";
                }
                return datetimeFormat.format(currIngestJob.getEndDateTime());
            } else if (columnIndex == 2) {
                return currIngestJob.getStatus().getDisplayName();
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

    }

    @Messages({"IngestJobInfoPanel.IngestModuleTableModel.ModuleName.header=Module Name",
        "IngestJobInfoPanel.IngestModuleTableModel.ModuleVersion.header=Module Version"})
    private class IngestModuleTableModel extends AbstractTableModel {

        private final List<String> columnHeaders = new ArrayList<>();
        private final IngestJobInfo currJob;

        IngestModuleTableModel(IngestJobInfo currJob) {
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestModuleTableModel_ModuleName_header());
            columnHeaders.add(Bundle.IngestJobInfoPanel_IngestModuleTableModel_ModuleVersion_header());
            this.currJob = currJob;
        }

        @Override
        public int getRowCount() {
            if (currJob == null) {
                return 0;
            }
            return currJob.getIngestModuleInfo().size();
        }

        @Override
        public int getColumnCount() {
            return columnHeaders.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (currJob != null) {
                IngestModuleInfo currIngestModule = currJob.getIngestModuleInfo().get(rowIndex);
                if (columnIndex == 0) {
                    return currIngestModule.getDisplayName();
                } else if (columnIndex == 1) {
                    return currIngestModule.getVersion();
                }
                return null;
            }
            return null;
        }

        @Override
        public String getColumnName(int column) {
            return columnHeaders.get(column);
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.JScrollPane mainScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel contentPanel = new javax.swing.JPanel();
        javax.swing.JScrollPane ingestJobsScrollPane = new javax.swing.JScrollPane();
        ingestJobTable = new javax.swing.JTable();
        javax.swing.JLabel jLabel1 = new javax.swing.JLabel();
        javax.swing.JLabel jLabel2 = new javax.swing.JLabel();
        javax.swing.JScrollPane ingestModulesScrollPane = new javax.swing.JScrollPane();
        ingestModuleTable = new javax.swing.JTable();

        setLayout(new java.awt.BorderLayout());

        contentPanel.setMinimumSize(new java.awt.Dimension(625, 150));
        contentPanel.setPreferredSize(new java.awt.Dimension(625, 150));
        contentPanel.setLayout(new java.awt.GridBagLayout());

        ingestJobsScrollPane.setBorder(null);
        ingestJobsScrollPane.setMinimumSize(new java.awt.Dimension(16, 16));

        ingestJobTable.setModel(ingestJobTableModel);
        ingestJobTable.setGridColor(javax.swing.UIManager.getDefaults().getColor("InternalFrame.borderColor"));
        ingestJobTable.setIntercellSpacing(new java.awt.Dimension(4, 2));
        ingestJobTable.getTableHeader().setReorderingAllowed(false);
        ingestJobsScrollPane.setViewportView(ingestJobTable);
        ingestJobTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 10, 10, 0);
        contentPanel.add(ingestJobsScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(IngestJobInfoPanel.class, "IngestJobInfoPanel.jLabel1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        contentPanel.add(jLabel1, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(IngestJobInfoPanel.class, "IngestJobInfoPanel.jLabel2.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        contentPanel.add(jLabel2, gridBagConstraints);

        ingestModulesScrollPane.setMaximumSize(new java.awt.Dimension(254, 32767));
        ingestModulesScrollPane.setMinimumSize(new java.awt.Dimension(254, 16));
        ingestModulesScrollPane.setPreferredSize(new java.awt.Dimension(254, 16));

        ingestModuleTable.setModel(ingestModuleTableModel);
        ingestModuleTable.setGridColor(javax.swing.UIManager.getDefaults().getColor("InternalFrame.borderColor"));
        ingestModuleTable.setIntercellSpacing(new java.awt.Dimension(4, 2));
        ingestModulesScrollPane.setViewportView(ingestModuleTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 8, 10, 10);
        contentPanel.add(ingestModulesScrollPane, gridBagConstraints);

        mainScrollPane.setViewportView(contentPanel);

        add(mainScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable ingestJobTable;
    private javax.swing.JTable ingestModuleTable;
    // End of variables declaration//GEN-END:variables
}
