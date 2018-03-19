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

import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.LocalDisk;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

@NbBundle.Messages({
    "LocalDiskSelectionDialog.title.text=Select Local Disk",
    "LocalDiskSelectionDialog.refreshTablebutton.text=Refresh Local Disks",
    "LocalDiskSelectionDialog.listener.getOpenCase.errTitle=No open case available",
    "LocalDiskSelectionDialog.listener.getOpenCase.errMsg=LocalDiskSelectionDialog listener couldn't get the open case.",
    "LocalDiskSelectionDialog.localDiskModel.loading.msg=Loading local disks...",
    "LocalDiskSelectionDialog.localDiskModel.nodrives.msg=No Accessible Drives",
    "LocalDiskSelectionDialog.moduleErr=Module Error",
    "LocalDiskSelectionDialog.moduleErr.msg=A module caused an error listening to LocalDiskPanel updates. See log to determine which module. Some data could be incomplete.",
    "LocalDiskSelectionDialog.errLabel.disksNotDetected.text=Disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\").",
    "LocalDiskSelectionDialog.errLabel.disksNotDetected.toolTipText=Disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\").",
    "LocalDiskSelectionDialog.errLabel.drivesNotDetected.text=Local drives were not detected. Auto-detection not supported on this OS  or admin privileges required",
    "LocalDiskSelectionDialog.errLabel.drivesNotDetected.toolTipText=Local drives were not detected. Auto-detection not supported on this OS  or admin privileges required",
    "LocalDiskSelectionDialog.errLabel.someDisksNotDetected.text=Some disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\").",
    "LocalDiskSelectionDialog.errLabel.someDisksNotDetected.toolTipText=Some disks were not detected. On some systems it requires admin privileges (or \"Run as administrator\")."
})
/**
 * ImageTypePanel for adding a local disk or partition such as PhysicalDrive0 or
 * C:.
 */
final class LocalDiskSelectionDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(LocalDiskSelectionDialog.class.getName());
    private static final long serialVersionUID = 1L;
    private List<LocalDisk> disks;
    private final LocalDiskModel model;

    /**
     * Creates new form LocalDiskSelectionDialog
     */
    LocalDiskSelectionDialog() {
        super((Window) LocalDiskPanel.getDefault().getTopLevelAncestor(), NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.title.text"), ModalityType.MODELESS);
        
        this.model = new LocalDiskModel();
        this.disks = new ArrayList<>();
        
        initComponents();
        customInit();
        refreshTable();
        
        localDiskTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                int selectedRow = localDiskTable.getSelectedRow();
                okButton.setEnabled(selectedRow >= 0 && selectedRow < disks.size());
                
                /*try {
                    firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "LocalDiskSelectionDialog listener threw exception", e); //NON-NLS
                    MessageNotifyUtil.Notify.show(NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.moduleErr"),
                            NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.moduleErr.msg"),
                            MessageNotifyUtil.MessageType.ERROR);
                }*/
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void customInit() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
        localDiskTable.setEnabled(false);
    }
    
    /**
     * Display the dialog.
     */
    void display() {
        setModal(true);
        setSize(getPreferredSize());
        setLocationRelativeTo(this.getParent());
        setAlwaysOnTop(false);
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
        setAlwaysOnTop(true);
        setResizable(false);

        selectLocalDiskLabel.setFont(selectLocalDiskLabel.getFont().deriveFont(selectLocalDiskLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(selectLocalDiskLabel, org.openide.util.NbBundle.getMessage(LocalDiskSelectionDialog.class, "LocalDiskSelectionDialog.selectLocalDiskLabel.text")); // NOI18N

        errorLabel.setFont(errorLabel.getFont().deriveFont(errorLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
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
        int selectedRow = localDiskTable.getSelectedRow();
        LocalDisk localDisk = disks.get(selectedRow);
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

    private void fireUpdateEvent() {
        try {
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "LocalDiskSelectionDialog listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.moduleErr"),
                    NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.moduleErr.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Return the currently selected disk path.
     *
     * @return String selected disk path
     */
    String getContentPaths() {
        if (disks.size() > 0) {
            int selectedRow = localDiskTable.getSelectedRow();
            LocalDisk selected = disks.get(selectedRow);
            return selected.getPath();
        } else {
            return "";
        }
    }

    /**
     * Refreshes the list of disks in the table.
     */
    public void refreshTable() {
        model.loadDisks();
        localDiskTable.clearSelection();
        //okButton.setEnabled(false);
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
     * Table model for displaing information from LocalDisk Objects in a table.
     */
    private class LocalDiskModel implements TableModel {

        private LocalDiskThread worker = null;
        private boolean ready = false;
        private volatile boolean loadingDisks = false;

        private final String LOADING = NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.localDiskModel.loading.msg");
        private final String NO_DRIVES = NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.localDiskModel.nodrives.msg");

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
            return 2;

        }

        @NbBundle.Messages({"LocalDiskSelectionDialog.diskTable.column1.title=Disk Name",
            "LocalDiskSelectionDialog.diskTable.column2.title=Disk Size"
        })

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.diskTable.column1.title");
                case 1:
                    return NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.diskTable.column2.title");
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
                    return NO_DRIVES;
                }
                switch (columnIndex) {
                    case 0:
                        return disks.get(rowIndex).getName();
                    case 1:
                        return disks.get(rowIndex).getReadableSize();
                    default:
                        return disks.get(rowIndex).getPath();
                }
            } else {
                return LOADING;
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
                        errorLabel.setText(
                                NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.errLabel.disksNotDetected.text"));
                        errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                                "LocalDiskSelectionDialog.errLabel.disksNotDetected.toolTipText"));
                    } else {
                        errorLabel.setText(
                                NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.errLabel.drivesNotDetected.text"));
                        errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                                "LocalDiskSelectionDialog.errLabel.drivesNotDetected.toolTipText"));
                    }
                    errorLabel.setVisible(true);
                    localDiskTable.setEnabled(false);
                } else if (physicalDrives.isEmpty()) {
                    errorLabel.setText(
                            NbBundle.getMessage(this.getClass(), "LocalDiskSelectionDialog.errLabel.someDisksNotDetected.text"));
                    errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                            "LocalDiskSelectionDialog.errLabel.someDisksNotDetected.toolTipText"));
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
