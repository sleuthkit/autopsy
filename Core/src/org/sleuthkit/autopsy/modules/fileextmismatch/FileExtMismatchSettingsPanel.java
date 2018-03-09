/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.fileextmismatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * Container panel for File Extension Mismatch Ingest Module advanced
 * configuration options
 */
final class FileExtMismatchSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final Logger logger = Logger.getLogger(FileExtMismatchSettingsPanel.class.getName());
    private HashMap<String, Set<String>> editableMap = new HashMap<>();
    private ArrayList<String> mimeList = null;
    private ArrayList<String> currentExtensions = null;
    private MimeTableModel mimeTableModel;
    private ExtTableModel extTableModel;
    private final String EXT_HEADER_LABEL = NbBundle.getMessage(FileExtMismatchSettingsPanel.class,
            "AddFileExtensionAction.extHeaderLbl.text");
    private String selectedMime = "";
    private String selectedExt = "";
    ListSelectionModel lsm = null;
    private FileTypeDetector fileTypeDetector;

    public FileExtMismatchSettingsPanel() {
        mimeTableModel = new MimeTableModel();
        extTableModel = new ExtTableModel();

        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Failed to create file type detector", ex); //NON-NLS
            fileTypeDetector = null;
        }

        initComponents();
        customizeComponents();
    }

    @NbBundle.Messages({"FileExtMismatchSettingsPanel.Title=Global File Extension Mismatch Identification Settings"})
    private void customizeComponents() {
        setName(Bundle.FileExtMismatchSettingsPanel_Title());

        // Handle selections on the left table
        lsm = mimeTable.getSelectionModel();
        lsm.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    listSelectionModel.setSelectionInterval(index, index);

                    selectedMime = mimeList.get(index);
                    String labelStr = EXT_HEADER_LABEL + selectedMime + ":";
                    if (labelStr.length() > 80) {
                        labelStr = labelStr.substring(0, 80);
                    }
                    extHeaderLabel.setText(labelStr);
                    updateExtList();

                    extTableModel.resync();
                    removeTypeButton.setEnabled(true);
                    newExtButton.setEnabled(true);
                    //initButtons();
                } else {
                    selectedMime = "";
                    currentExtensions = null;
                    removeTypeButton.setEnabled(false);
                    newExtButton.setEnabled(false);
                    extTableModel.resync();
                }
            }
        });

        // Handle selections on the right table
        ListSelectionModel extLsm = extTable.getSelectionModel();
        extLsm.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
                if (!listSelectionModel.isSelectionEmpty()) {
                    int index = listSelectionModel.getMinSelectionIndex();
                    listSelectionModel.setSelectionInterval(index, index);

                    selectedExt = currentExtensions.get(index);
                    removeExtButton.setEnabled(true);
                } else {
                    selectedExt = "";
                    removeExtButton.setEnabled(false);
                }

            }
        });
        removeExtButton.setEnabled(false);
        removeTypeButton.setEnabled(false);
        newExtButton.setEnabled(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jSplitPane1 = new javax.swing.JSplitPane();
        mimePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        mimeTable = new javax.swing.JTable();
        newTypeButton = new javax.swing.JButton();
        removeTypeButton = new javax.swing.JButton();
        extensionPanel = new javax.swing.JPanel();
        newExtButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        extTable = new javax.swing.JTable();
        removeExtButton = new javax.swing.JButton();
        extHeaderLabel = new javax.swing.JLabel();

        setPreferredSize(new java.awt.Dimension(718, 430));

        jPanel1.setPreferredSize(new java.awt.Dimension(718, 430));

        jScrollPane1.setRequestFocusEnabled(false);

        jSplitPane1.setDividerLocation(365);
        jSplitPane1.setDividerSize(1);

        mimePanel.setPreferredSize(new java.awt.Dimension(369, 424));
        mimePanel.setRequestFocusEnabled(false);

        jLabel1.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.jLabel1.text")); // NOI18N

        mimeTable.setModel(mimeTableModel);
        jScrollPane2.setViewportView(mimeTable);

        newTypeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        newTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newTypeButton.text")); // NOI18N
        newTypeButton.setMaximumSize(new java.awt.Dimension(111, 25));
        newTypeButton.setMinimumSize(new java.awt.Dimension(111, 25));
        newTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTypeButtonActionPerformed(evt);
            }
        });

        removeTypeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        removeTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeTypeButton.text")); // NOI18N
        removeTypeButton.setToolTipText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeTypeButton.toolTipText")); // NOI18N
        removeTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeTypeButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mimePanelLayout = new javax.swing.GroupLayout(mimePanel);
        mimePanel.setLayout(mimePanelLayout);
        mimePanelLayout.setHorizontalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mimePanelLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(jLabel1)
                        .addGap(286, 286, 286))
                    .addGroup(mimePanelLayout.createSequentialGroup()
                        .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 349, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(mimePanelLayout.createSequentialGroup()
                                .addComponent(newTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(10, 10, 10)
                                .addComponent(removeTypeButton)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        mimePanelLayout.setVerticalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 341, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(newTypeButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(removeTypeButton))
                .addContainerGap())
        );

        jSplitPane1.setLeftComponent(mimePanel);

        extensionPanel.setPreferredSize(new java.awt.Dimension(344, 424));

        newExtButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        newExtButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtButton.text")); // NOI18N
        newExtButton.setMaximumSize(new java.awt.Dimension(111, 25));
        newExtButton.setMinimumSize(new java.awt.Dimension(111, 25));
        newExtButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newExtButtonActionPerformed(evt);
            }
        });

        extTable.setModel(extTableModel);
        jScrollPane3.setViewportView(extTable);

        removeExtButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        removeExtButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeExtButton.text")); // NOI18N
        removeExtButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeExtButtonActionPerformed(evt);
            }
        });

        extHeaderLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.extHeaderLabel.text")); // NOI18N

        javax.swing.GroupLayout extensionPanelLayout = new javax.swing.GroupLayout(extensionPanel);
        extensionPanel.setLayout(extensionPanelLayout);
        extensionPanelLayout.setHorizontalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(extensionPanelLayout.createSequentialGroup()
                        .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(extHeaderLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(extensionPanelLayout.createSequentialGroup()
                                .addComponent(newExtButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeExtButton)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        extensionPanelLayout.setVerticalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(extHeaderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 348, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newExtButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(removeExtButton))
                .addContainerGap())
        );

        jSplitPane1.setRightComponent(extensionPanel);

        jScrollPane1.setViewportView(jSplitPane1);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1)
                .addGap(0, 0, 0))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jScrollPane1)
                .addGap(0, 0, 0))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Add a user-provided filename extension string to the selecte mimetype
    private void newExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newExtButtonActionPerformed
        String newExt = (String) JOptionPane.showInputDialog(this, NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.message"),
                NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.title"), JOptionPane.PLAIN_MESSAGE, null, null, "");

        if (newExt == null) {
            return;
        }
        if (newExt.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.empty.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.empty.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (selectedMime.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.noMimeType.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.noMimeType.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (currentExtensions.contains(newExt)) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.extExists.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newExtPrompt.extExists.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Set<String> editedExtensions = editableMap.get(selectedMime);
        editedExtensions.add(newExt);

        // Old array will be replaced by new array for this key
        editableMap.put(selectedMime, editedExtensions);

        // Refresh table
        updateExtList();
        extTableModel.resync();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_newExtButtonActionPerformed

    private void removeExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeExtButtonActionPerformed
        if (selectedExt.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeExtButton.noneSelected.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeExtButton.noneSelected.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (selectedMime.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeExtButton.noMimeTypeSelected.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeExtButton.noMimeTypeSelected.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        Set<String> editedExtensions = editableMap.get(selectedMime);
        editedExtensions.remove(selectedExt);
        String deadExt = selectedExt;
        
        // Old array will be replaced by new array for this key
        editableMap.put(selectedMime, editedExtensions);

        // Refresh tables        
        updateExtList();
        extTableModel.resync();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_removeExtButtonActionPerformed

    private void removeTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeTypeButtonActionPerformed
        if (selectedMime.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeTypeButton.noneSelected.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeTypeButton.noneSelected.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        editableMap.remove(selectedMime);

        // Refresh table
        updateMimeList();
        mimeTableModel.resync();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_removeTypeButtonActionPerformed

    private void newTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTypeButtonActionPerformed
        String newMime = (String) JOptionPane.showInputDialog(this, NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.message"),
                NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.title"), JOptionPane.PLAIN_MESSAGE, null, null, "");

        if (newMime == null) {
            return;
        }
        if (newMime.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.emptyMime.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.emptyMime.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (newMime.equals("application/octet-stream")) { //NON-NLS
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeNotSupported.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeNotSupported.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (mimeList.contains(newMime)) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeExists.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeExists.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        FileTypeDetector detector;
        try {
            detector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.WARNING, "Couldn't create file type detector for file ext mismatch settings.", ex);
            return;
        }
        boolean mimeTypeDetectable = (null != detector) ? detector.isDetectable(newMime) : false;
        if (!mimeTypeDetectable) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeNotDetectable.message"),
                    NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.newMimePrompt.mimeTypeNotDetectable.title"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        editableMap.put(newMime, new HashSet<String>());
        
        // Refresh table
        updateMimeList();
        mimeTableModel.resync();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_newTypeButtonActionPerformed

    private void updateMimeList() {
        mimeList = new ArrayList<>(editableMap.keySet());
        if (mimeList.size() > 0) {
            Collections.sort(mimeList);
        }
    }

    private void updateExtList() {
        Set<String> temp = editableMap.get(selectedMime);
        if (temp != null) {
            currentExtensions = new ArrayList<>(temp);
            if (temp.size() > 0) {
                Collections.sort(currentExtensions);
            }
        } else {
            currentExtensions = null;
        }
    }

    @Override
    public void saveSettings() {
        try {
            FileExtMismatchSettings.writeSettings(new FileExtMismatchSettings(editableMap));
        } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
            //error
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "FileExtMismatchConfigPanel.store.msgDlg.msg"),
                    NbBundle.getMessage(this.getClass(),
                            "FileExtMismatchConfigPanel.save.msgDlg.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    public void load() {
        try {
            // Load the configuration into a buffer that the user can modify. They can choose
            // to save it back to the file after making changes.
            editableMap = FileExtMismatchSettings.readSettings().getMimeTypeToExtsMap();

        } catch (FileExtMismatchSettings.FileExtMismatchSettingsException ex) {
            //error
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "AddFileExtensionAction.msgDlg.msg2"),
                    NbBundle.getMessage(this.getClass(),
                            "FileExtMismatchConfigPanel.save.msgDlg.title"),
                    JOptionPane.ERROR_MESSAGE);
        }
        updateMimeList();
        updateExtList();
    }

    @Override
    public void store() {
        saveSettings();
    }

    public void cancel() {
        load(); // The next time this panel is opened, we want it to be fresh
    }

    public void ok() {
        store();
        load(); // The next time this panel is opened, we want it to be fresh
    }

    boolean valid() {
        return true;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel extHeaderLabel;
    private javax.swing.JTable extTable;
    private javax.swing.JPanel extensionPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JPanel mimePanel;
    private javax.swing.JTable mimeTable;
    private javax.swing.JButton newExtButton;
    private javax.swing.JButton newTypeButton;
    private javax.swing.JButton removeExtButton;
    private javax.swing.JButton removeTypeButton;
    // End of variables declaration//GEN-END:variables

    private class MimeTableModel extends AbstractTableModel {

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return editableMap == null ? 0 : editableMap.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            if ((mimeList == null) || (rowIndex > mimeList.size())) {
                return "";
            }
            String word = mimeList.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = (Object) word;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: {0}", columnIndex); //NON-NLS
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void resync() {
            fireTableDataChanged();
        }
    }

    private class ExtTableModel extends AbstractTableModel {

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return currentExtensions == null ? 0 : currentExtensions.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;

            if ((currentExtensions == null) || (currentExtensions.isEmpty()) || (rowIndex > currentExtensions.size())) {
                return "";
            }
            String word = currentExtensions.get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = (Object) word;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: {0}", columnIndex); //NON-NLS
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void resync() {
            fireTableDataChanged();
        }
    }
}
