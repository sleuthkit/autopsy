/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.LocalDisk;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

@NbBundle.Messages({
    "LocalDiskSelectionDialog.moduleErrorMessage.title=Module Error",
    "LocalDiskSelectionDialog.moduleErrorMessage.body=A module caused an error listening to LocalDiskPanel updates. See log to determine which module. Some data could be incomplete.",
    "LocalDiskSelectionDialog.errorMessage.disksNotDetected=Disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\").",
    "LocalDiskSelectionDialog.errorMessage.drivesNotDetected=Local drives were not detected. Auto-detection not supported on this OS  or admin privileges required",
    "LocalDiskSelectionDialog.errorMessage.someDisksNotDetected=Some disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\")."
})
/**
 * Local disk selection dialog for loading a disk into the LocalDiskPanel.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class LocalDiskSelectionDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(LocalDiskSelectionDialog.class.getName());
    private static final long serialVersionUID = 1L;
    private List<LocalDisk> disks;
    private final LocalDiskModel model;
    private final TooltipCellRenderer tooltipCellRenderer = new TooltipCellRenderer();
    
    /**
     * Creates a new LocalDiskSelectionDialog instance.
     */
    LocalDiskSelectionDialog() {
        super((Window) LocalDiskPanel.getDefault().getTopLevelAncestor(), ModalityType.MODELESS);
        
        this.model = new LocalDiskModel();
        this.disks = new ArrayList<>();
        
        initComponents();
        refreshTable();
        
        for (Enumeration<TableColumn> e = localDiskTable.getColumnModel().getColumns(); e.hasMoreElements();) {
            e.nextElement().setCellRenderer(tooltipCellRenderer);
        }
           
        localDiskTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int selectedRow = localDiskTable.getSelectedRow();
                okButton.setEnabled(selectedRow >= 0 && selectedRow < disks.size());
            }
        });
    }
    
    /**
     * Display the dialog.
     */
    void display() {
        setLocationRelativeTo(this.getParent());
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        selectLocalDiskLabel = new javax.swing.JLabel();
        errorLabel = new javax.swing.JLabel();
        localDiskScrollPane = new javax.swing.JScrollPane();
        localDiskTable = new javax.swing.JTable();
        refreshLocalDisksButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.title")); // NOI18N
        setAlwaysOnTop(true);
        setModal(true);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(selectLocalDiskLabel, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.selectLocalDiskLabel.text")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.errorLabel.text")); // NOI18N

        localDiskTable.setModel(model);
        localDiskTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        localDiskScrollPane.setViewportView(localDiskTable);

        org.openide.awt.Mnemonics.setLocalizedText(refreshLocalDisksButton, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.refreshLocalDisksButton.text")); // NOI18N
        refreshLocalDisksButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshLocalDisksButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.okButton.text")); // NOI18N
        okButton.setEnabled(false);
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(localDiskScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 560, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton, javax.swing.GroupLayout.PREFERRED_SIZE, 90, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(refreshLocalDisksButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(selectLocalDiskLabel)
                            .addComponent(errorLabel))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(selectLocalDiskLabel)
                .addGap(4, 4, 4)
                .addComponent(localDiskScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(refreshLocalDisksButton)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addContainerGap(27, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void refreshLocalDisksButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshLocalDisksButtonActionPerformed
        refreshTable();
    }//GEN-LAST:event_refreshLocalDisksButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        localDiskTable.clearSelection();
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JScrollPane localDiskScrollPane;
    private javax.swing.JTable localDiskTable;
    private javax.swing.JButton okButton;
    private javax.swing.JButton refreshLocalDisksButton;
    private javax.swing.JLabel selectLocalDiskLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Fire a property change event to update the UI.
     */
    private void fireUpdateEvent() {
        try {
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "LocalDiskSelectionDialog listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(Bundle.LocalDiskSelectionDialog_moduleErrorMessage_title(),
                    Bundle.LocalDiskSelectionDialog_moduleErrorMessage_body(),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Return the currently selected disk path.
     *
     * @return String selected disk path
     */
    String getContentPaths() {
        LocalDisk selected = getLocalDiskSelection();
        if (selected != null) {
            return selected.getPath();
        }
        return "";
    }

    /**
     * Refreshes the list of disks in the table.
     */
    private void refreshTable() {
        model.loadDisks();
        localDiskTable.clearSelection();
    }
    
    /**
     * Get the local disk selected from the table.
     * 
     * @return The LocalDisk object associated with the selection in the table.
     */
    LocalDisk getLocalDiskSelection() {
        if (disks.size() > 0) {
            int selectedRow = localDiskTable.getSelectedRow();
            if (selectedRow >= 0 && selectedRow < disks.size()) {
                return disks.get(selectedRow);
            }
        }
        return null;
    }
    
    /**
     * Shows tooltip for cell.
     */
    private class TooltipCellRenderer extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {
            JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String tooltip = value == null ? "" : value.toString();
            c.setToolTipText(tooltip);
            return c;
        }
    }

    @NbBundle.Messages({
        "LocalDiskSelectionDialog.tableMessage.loading=Loading local disks...",
        "LocalDiskSelectionDialog.tableMessage.noDrives=No Accessible Drives",
    })
    /**
     * Table model for displaing information from LocalDisk Objects in a table.
     */
    private class LocalDiskModel implements TableModel {

        private LocalDiskThread worker = null;
        private boolean ready = false;
        private volatile boolean loadingDisks = false;

        private void loadDisks() {

            // if there is a worker already building the lists, then cancel it first.
            if (loadingDisks && worker != null) {
                worker.cancel(false);
            }

            // Clear the lists
            errorLabel.setText("");
            localDiskTable.setEnabled(false);
            ready = false;
            loadingDisks = true;
            worker = new LocalDiskThread();
            worker.execute();
        }

        @Override
        public int getRowCount() {
            if (disks.isEmpty()) {
                return 0;
            }
            return disks.size();
        }

        @Override
        public int getColumnCount() {
            if (PlatformUtil.isLinuxOS()) {
                return 3;
            } else {
                return 2;
            }
        }

        @NbBundle.Messages({
            "LocalDiskSelectionDialog.columnName.diskName=Disk Name",
            "LocalDiskSelectionDialog.columnName.diskSize=Disk Size",
            "LocalDiskSelectionDialog.columnName.Details=Details"
        })

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return Bundle.LocalDiskSelectionDialog_columnName_diskName();
                case 1:
                    return Bundle.LocalDiskSelectionDialog_columnName_diskSize();
                case 2:
                    return Bundle.LocalDiskSelectionDialog_columnName_Details();
                default:
                    return "Unnamed"; //NON-NLS
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (ready) {
                if (disks.isEmpty()) {
                    return Bundle.LocalDiskSelectionDialog_tableMessage_noDrives();
                }
                switch (columnIndex) {
                    case 0:
                        return disks.get(rowIndex).getName();
                    case 1:
                        return disks.get(rowIndex).getReadableSize();
                    case 2:
                        return disks.get(rowIndex).getDetail();
                    default:
                        return disks.get(rowIndex).getPath();
                }
            } else {
                return Bundle.LocalDiskSelectionDialog_tableMessage_loading();
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            //setter does nothing they should not be able to modify table
        }

        @Override
        public void addTableModelListener(TableModelListener l) {

        }

        @Override
        public void removeTableModelListener(TableModelListener l) {

        }

        /**
         * Gets the lists of physical drives and partitions and combines them
         * into a list of disks.
         */
        class LocalDiskThread extends SwingWorker<Object, Void> {

            private final Logger logger = Logger.getLogger(LocalDiskThread.class.getName());
            private List<LocalDisk> physicalDrives = new ArrayList<>();
            private List<LocalDisk> partitions = new ArrayList<>();

            @Override
            protected Object doInBackground() throws Exception {
                // Populate the lists
                physicalDrives = new ArrayList<>();
                partitions = new ArrayList<>();
                physicalDrives = PlatformUtil.getPhysicalDrives();
                partitions = PlatformUtil.getPartitions();
                return null;
            }

            /**
             * Display any error messages that might of occurred when getting
             * the lists of physical drives or partitions.
             */
            private void displayErrors() {
                if (physicalDrives.isEmpty() && partitions.isEmpty()) {
                    if (PlatformUtil.isWindowsOS()) {
                        errorLabel.setText(Bundle.LocalDiskSelectionDialog_errorMessage_disksNotDetected());
                        errorLabel.setToolTipText(Bundle.LocalDiskSelectionDialog_errorMessage_disksNotDetected());
                    } else {
                        errorLabel.setText(Bundle.LocalDiskSelectionDialog_errorMessage_drivesNotDetected());
                        errorLabel.setToolTipText(Bundle.LocalDiskSelectionDialog_errorMessage_drivesNotDetected());
                    }
                    errorLabel.setVisible(true);
                    localDiskTable.setEnabled(false);
                } else if (physicalDrives.isEmpty()) {
                    errorLabel.setText(Bundle.LocalDiskSelectionDialog_errorMessage_someDisksNotDetected());
                    errorLabel.setToolTipText(Bundle.LocalDiskSelectionDialog_errorMessage_someDisksNotDetected());
                    errorLabel.setVisible(true);
                }
            }

            @Override
            protected void done() {
                try {
                    super.get(); //block and get all exceptions thrown while doInBackground()
                } catch (CancellationException ex) {
                    logger.log(Level.INFO, "Loading local disks was canceled."); //NON-NLS
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "Loading local disks was interrupted."); //NON-NLS
                } catch (ExecutionException ex) {
                    logger.log(Level.SEVERE, "Fatal error when loading local disks", ex); //NON-NLS
                } finally {
                    if (!this.isCancelled()) {
                        displayErrors();
                        worker = null;
                        loadingDisks = false;
                        disks = new ArrayList<>();
                        disks.addAll(physicalDrives);
                        disks.addAll(partitions);
                        if (disks.size() > 0) {
                            localDiskTable.setEnabled(true);
                            localDiskTable.clearSelection();
                            
                            // Remove the partition the application is running on.
                            String userConfigPath = PlatformUtil.getUserConfigDirectory();
                            for (Iterator<LocalDisk> iterator = disks.iterator(); iterator.hasNext();) {
                                LocalDisk disk = iterator.next();
                                String diskPath = disk.getPath();
                                if (diskPath.startsWith("\\\\.\\")) {
                                    // Strip out UNC prefix to get the drive letter.
                                    diskPath = diskPath.substring(4);
                                }
                                if (userConfigPath.startsWith(diskPath)) {
                                    iterator.remove();
                                }
                            }
                            
                            Collections.sort(disks, (LocalDisk disk1, LocalDisk disk2) -> disk1.getName().compareToIgnoreCase(disk2.getName()));
                        }
                        fireUpdateEvent();
                        ready = true;
                    }
                }
                localDiskTable.revalidate();
            }
        }
    }
}
