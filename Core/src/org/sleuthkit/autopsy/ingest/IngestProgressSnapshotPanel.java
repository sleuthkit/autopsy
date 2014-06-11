/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2014 Basis Technology Corp.
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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.sleuthkit.datamodel.AbstractFile;
import org.apache.commons.lang3.time.DurationFormatUtils;

public class IngestProgressSnapshotPanel extends javax.swing.JPanel {

    private final JDialog parent;
    private final SnapshotsTableModel tableModel;

    IngestProgressSnapshotPanel(JDialog parent) {
        this.parent = parent;
        tableModel = new SnapshotsTableModel();
        initComponents();
        customizeComponents();
        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
             @Override
             public void propertyChange(PropertyChangeEvent event) {
                 refreshButton.setEnabled(IngestManager.getInstance().isIngestRunning() == true);
             }
         });        
    }

    private void customizeComponents() {
        snapshotsTable.setModel(tableModel);

        int width = snapshotsScrollPane.getPreferredSize().width;
        for (int i = 0; i < snapshotsTable.getColumnCount(); ++i) {
            TableColumn column = snapshotsTable.getColumnModel().getColumn(i);
            switch (i) {
                case 0:
                    column.setPreferredWidth(((int) (width * 0.05)));
                    break;
                case 1:
                    column.setPreferredWidth(((int) (width * 0.15)));
                    break;
                case 2:
                    column.setPreferredWidth(((int) (width * 0.25)));
                    break;
                case 3:
                    column.setPreferredWidth(((int) (width * 0.35)));
                    break;
                case 4:
                    column.setPreferredWidth(((int) (width * 0.10)));
                    break;
                case 5:
                    column.setPreferredWidth(((int) (width * 0.10)));
                    break;
            }
        }
        
        snapshotsTable.setFillsViewportHeight(true);
    }

    private class SnapshotsTableModel extends AbstractTableModel {

        private final String[] columnNames = {"Thread ID", "Data Source", "Ingest Module", "File", "Start Time", "Elapsed Time (H:M:S)"};
        private List<IngestTask.ProgressSnapshot> snapshots;

        private SnapshotsTableModel() {
            refresh();
        }

        private void refresh() {
            snapshots = IngestManager.getInstance().getIngestTaskProgressSnapshots();
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
            IngestTask.ProgressSnapshot snapshot = snapshots.get(rowIndex);
            Object cellValue;
            switch (columnIndex) {
                case 0:
                    cellValue = snapshot.getThreadId();
                    break;
                case 1:
                    cellValue = snapshot.getDataSource().getName();
                    break;
                case 2:
                    cellValue = snapshot.getModuleDisplayName();
                    break;
                case 3:
                    AbstractFile file = snapshot.getFile();
                    if (file != null) {
                        cellValue = file.getName();
                    } else {
                        cellValue = "";
                    }
                    break;
                case 4:
                    cellValue = snapshot.getStartTime();
                    break;
                case 5:
                    long elapsedTime = Duration.between(snapshot.getStartTime(), LocalTime.now()).toMillis();
                    cellValue = DurationFormatUtils.formatDurationHMS(elapsedTime);
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

        snapshotsScrollPane = new javax.swing.JScrollPane();
        snapshotsTable = new javax.swing.JTable();
        refreshButton = new javax.swing.JButton();
        closeButton = new javax.swing.JButton();

        snapshotsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        snapshotsScrollPane.setViewportView(snapshotsTable);

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.refreshButton.text")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(closeButton, org.openide.util.NbBundle.getMessage(IngestProgressSnapshotPanel.class, "IngestProgressSnapshotPanel.closeButton.text")); // NOI18N
        closeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(snapshotsScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 733, Short.MAX_VALUE)
                        .addComponent(refreshButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(closeButton)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {closeButton, refreshButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(snapshotsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshButton)
                    .addComponent(closeButton))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {closeButton, refreshButton});

    }// </editor-fold>//GEN-END:initComponents

    private void closeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeButtonActionPerformed
        parent.setVisible(false);
    }//GEN-LAST:event_closeButtonActionPerformed

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        if (IngestManager.getInstance().isIngestRunning() == true) {
            tableModel.refresh();
        }
    }//GEN-LAST:event_refreshButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton closeButton;
    private javax.swing.JButton refreshButton;
    private javax.swing.JScrollPane snapshotsScrollPane;
    private javax.swing.JTable snapshotsTable;
    // End of variables declaration//GEN-END:variables
}
