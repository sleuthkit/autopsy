/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2018 Basis Technology Corp.
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.JDialog;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.openide.util.NbBundle;

/**
 * A panel that displays ingest task progress snapshots.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class IngestProgressSnapshotPanel extends javax.swing.JPanel {

    private final JDialog parent;
    private final IngestProgressSnapshotProvider snapshotProvider;
    private final IngestThreadActivitySnapshotsTableModel threadActivityTableModel;
    private final IngestJobTableModel jobTableModel;
    private final ModuleTableModel moduleTableModel;

    IngestProgressSnapshotPanel(JDialog parent, IngestProgressSnapshotProvider snapshotProvider) {
        this.parent = parent;
        this.snapshotProvider = snapshotProvider;
        threadActivityTableModel = new IngestThreadActivitySnapshotsTableModel();
        jobTableModel = new IngestJobTableModel();
        moduleTableModel = new ModuleTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        threadActivitySnapshotsTable.setModel(threadActivityTableModel);
        jobTable.setModel(jobTableModel);
        moduleTable.setModel(moduleTableModel);

        int width = snapshotsScrollPane.getPreferredSize().width;
        for (int i = 0; i < threadActivitySnapshotsTable.getColumnCount(); ++i) {
            TableColumn column = threadActivitySnapshotsTable.getColumnModel().getColumn(i);
            switch (i) {
                case 0:
                    column.setPreferredWidth(((int) (width * 0.02)));
                    break;
                case 1:
                    column.setPreferredWidth(((int) (width * 0.20)));
                    break;
                case 2:
                    column.setPreferredWidth(((int) (width * 0.15)));
                    break;
                case 3:
                    column.setPreferredWidth(((int) (width * 0.35)));
                    break;
                case 4:
                    column.setPreferredWidth(((int) (width * 0.18)));
                    break;
                case 5:
                    column.setPreferredWidth(((int) (width * 0.10)));
                    break;
            }
        }

        threadActivitySnapshotsTable.setFillsViewportHeight(true);
    }

    private class IngestThreadActivitySnapshotsTableModel extends AbstractTableModel {

        private final String[] columnNames = {NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.threadID"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.activity"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.dataSource"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.file"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.startTime"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.elapsedTime"),
            NbBundle.getMessage(this.getClass(),
            "IngestProgressSnapshotPanel.SnapshotsTableModel.colNames.jobID")};
        private List<IngestManager.IngestThreadActivitySnapshot> snapshots;

        private IngestThreadActivitySnapshotsTableModel() {
            refresh();
        }

        private void refresh() {
            snapshots = snapshotProvider.getIngestThreadActivitySnapshots();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return snapshots.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestManager.IngestThreadActivitySnapshot snapshot = snapshots.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = snapshot.getThreadId();
                    break;
                case 1:
                    cellValue = snapshot.getModuleDisplayName();
                    break;
                case 2:
                    cellValue = snapshot.getDataSourceName();
                    break;
                case 3:
                    cellValue = snapshot.getFileName();
                    break;
                case 4:
                    cellValue = snapshot.getStartTime();
                    break;
                case 5:
                    Date now = new Date();
                    long elapsedTime = now.getTime() - snapshot.getStartTime().getTime();
                    cellValue = DurationFormatUtils.formatDurationHMS(elapsedTime);
                    break;
                case 6:
                    cellValue = snapshot.getIngestJobId();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
        }
    }

    private class IngestJobTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] columnNames = {
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.jobID"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.dataSource"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.start"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.tier"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.numProcessed"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.filesPerSec"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.inProgress"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.filesQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.dirQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.rootQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.streamingQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.dsQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.artifactsQueued"),
            NbBundle.getMessage(this.getClass(), "IngestJobTableModel.colName.resultsQueued")};

        private List<IngestJobProgressSnapshot> jobSnapshots;

        private IngestJobTableModel() {
            refresh();
        }

        private void refresh() {
            jobSnapshots = snapshotProvider.getIngestJobSnapshots();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return jobSnapshots.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            IngestJobProgressSnapshot snapShot = jobSnapshots.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = snapShot.getJobId();
                    break;
                case 1:
                    cellValue = snapShot.getDataSource();
                    break;
                case 2:
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    cellValue = dateFormat.format(new Date(snapShot.getJobStartTime()));
                    break;
                case 3:
                    cellValue = snapShot.getCurrentIngestModuleTier();
                    break;
                case 4:
                    cellValue = snapShot.getFilesProcessed();
                    break;
                case 5:
                    cellValue = snapShot.getFilesProcessedPerSec();
                    break;
                case 6:
                    cellValue = snapShot.getRunningListSize();
                    break;
                case 7:
                    cellValue = snapShot.getFileQueueSize();
                    break;
                case 8:
                    cellValue = snapShot.getDirQueueSize();
                    break;
                case 9:
                    cellValue = snapShot.getRootQueueSize();
                    break;
                case 10:
                    cellValue = snapShot.getStreamingQueueSize();
                    break;
                case 11:
                    cellValue = snapShot.getDsQueueSize();
                    break;
                case 12:
                    cellValue = snapShot.getDataArtifactTasksQueueSize();
                    break;
                case 13:
                    cellValue = snapShot.getAnalysisResultTasksQueueSize();
                    break;
                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
        }
    }

    private class ModuleTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private class ModuleStats implements Comparable<ModuleStats> {

            private final String name;
            private final long duration;

            ModuleStats(String name, long duration) {
                this.name = name;
                this.duration = duration;
            }

            /**
             * @return the name
             */
            protected String getName() {
                return name;
            }

            /**
             * @return the duration
             */
            protected long getDuration() {
                return duration;
            }

            @Override
            public int compareTo(ModuleStats o) {
                if (duration > o.getDuration()) {
                    return -1;
                } else if (duration == o.getDuration()) {
                    return 0;
                } else {
                    return 1;
                }
            }

        }
        private final String[] columnNames = {NbBundle.getMessage(this.getClass(), "ModuleTableModel.colName.module"),
            NbBundle.getMessage(this.getClass(),
            "ModuleTableModel.colName.duration")};
        private final List<ModuleStats> moduleStats = new ArrayList<>();
        private long totalTime;

        private ModuleTableModel() {
            refresh();
        }

        private void refresh() {
            Map<String, Long> moduleStatMap = snapshotProvider.getModuleRunTimes();
            moduleStats.clear();
            totalTime = 0;
            for (String k : moduleStatMap.keySet()) {
                moduleStats.add(new ModuleStats(k, moduleStatMap.get(k)));
                totalTime += moduleStatMap.get(k);
            }
            Collections.sort(moduleStats);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return moduleStats.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int col) {
            return columnNames[col];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ModuleStats moduleStat = moduleStats.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = moduleStat.getName();
                    break;
                case 1:
                    cellValue = DurationFormatUtils.formatDurationHMS(moduleStat.getDuration()) + " (" + (moduleStat.getDuration() * 100) / totalTime + "%)";
                    break;

                default:
                    cellValue = null;
                    break;
            }
            return cellValue;
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

        snapshotsScrollPane = new javax.swing.JScrollPane();
        threadActivitySnapshotsTable = new javax.swing.JTable();
        jobScrollPane = new javax.swing.JScrollPane();
        jobTable = new javax.swing.JTable();
        refreshButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();
        moduleScrollPane = new javax.swing.JScrollPane();
        moduleTable = new javax.swing.JTable();
        jPanel1 = new javax.swing.JPanel();

        setMinimumSize(new java.awt.Dimension(500, 500));
        setPreferredSize(new java.awt.Dimension(1500, 700));
        setLayout(new java.awt.GridBagLayout());

        threadActivitySnapshotsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        snapshotsScrollPane.setViewportView(threadActivitySnapshotsTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(11, 10, 0, 10);
        add(snapshotsScrollPane, gridBagConstraints);

        jobTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        jobScrollPane.setViewportView(jobTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 10);
        add(jobScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.refreshButton.text")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        add(refreshButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 11, 10);
        add(closeButton, gridBagConstraints);

        moduleTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        moduleScrollPane.setViewportView(moduleTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 10);
        add(moduleScrollPane, gridBagConstraints);
        add(jPanel1, new java.awt.GridBagConstraints());
    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        parent.dispose();
    }//GEN-LAST:event_closeButtonActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        threadActivityTableModel.refresh();
        jobTableModel.refresh();
        moduleTableModel.refresh();
    }//GEN-LAST:event_refreshButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jobScrollPane;
    private javax.swing.JTable jobTable;
    private javax.swing.JScrollPane moduleScrollPane;
    private javax.swing.JTable moduleTable;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane snapshotsScrollPane;
    private javax.swing.JTable threadActivitySnapshotsTable;
    // End of variables declaration//GEN-END:variables
}
