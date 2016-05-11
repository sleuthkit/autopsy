/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.awt.Color;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
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
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

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
                    addExtButton.setEnabled(true);
                    //initButtons();
                } else {
                    selectedMime = "";
                    currentExtensions = null;
                    removeTypeButton.setEnabled(false);
                    addExtButton.setEnabled(false);
                    extTableModel.resync();
                }

                clearErrLabels();
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
        addExtButton.setEnabled(false);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private void clearErrLabels() {
        mimeErrLabel.setText(" ");
        extErrorLabel.setText(" ");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        jSplitPane1 = new javax.swing.JSplitPane();
        mimePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        mimeTable = new javax.swing.JTable();
        userTypeTextField = new javax.swing.JTextField();
        addTypeButton = new javax.swing.JButton();
        removeTypeButton = new javax.swing.JButton();
        mimeErrLabel = new javax.swing.JLabel();
        extensionPanel = new javax.swing.JPanel();
        userExtTextField = new javax.swing.JTextField();
        addExtButton = new javax.swing.JButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        extTable = new javax.swing.JTable();
        removeExtButton = new javax.swing.JButton();
        extHeaderLabel = new javax.swing.JLabel();
        extErrorLabel = new javax.swing.JLabel();

        jScrollPane1.setBorder(null);

        jPanel1.setPreferredSize(new java.awt.Dimension(687, 450));

        jSplitPane1.setDividerLocation(430);

        jLabel1.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.jLabel1.text")); // NOI18N

        mimeTable.setModel(mimeTableModel);
        jScrollPane2.setViewportView(mimeTable);

        userTypeTextField.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.userTypeTextField.text")); // NOI18N
        userTypeTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                userTypeTextFieldFocusGained(evt);
            }
        });

        addTypeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        addTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.addTypeButton.text")); // NOI18N
        addTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addTypeButtonActionPerformed(evt);
            }
        });

        removeTypeButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        removeTypeButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.removeTypeButton.text")); // NOI18N
        removeTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeTypeButtonActionPerformed(evt);
            }
        });

        mimeErrLabel.setForeground(new java.awt.Color(255, 0, 0));
        mimeErrLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.mimeErrLabel.text")); // NOI18N

        javax.swing.GroupLayout mimePanelLayout = new javax.swing.GroupLayout(mimePanel);
        mimePanel.setLayout(mimePanelLayout);
        mimePanelLayout.setHorizontalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(mimeErrLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, mimePanelLayout.createSequentialGroup()
                        .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addGroup(mimePanelLayout.createSequentialGroup()
                                .addComponent(userTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 128, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(addTypeButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeTypeButton)))
                        .addGap(0, 7, Short.MAX_VALUE)))
                .addContainerGap())
        );
        mimePanelLayout.setVerticalGroup(
            mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mimePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 396, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(removeTypeButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 25, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(mimePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(userTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(addTypeButton)))
                .addGap(4, 4, 4)
                .addComponent(mimeErrLabel))
        );

        jSplitPane1.setLeftComponent(mimePanel);

        userExtTextField.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.userExtTextField.text")); // NOI18N
        userExtTextField.setMinimumSize(new java.awt.Dimension(20, 20));
        userExtTextField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                userExtTextFieldFocusGained(evt);
            }
        });

        addExtButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        addExtButton.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.addExtButton.text")); // NOI18N
        addExtButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addExtButtonActionPerformed(evt);
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

        extErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        extErrorLabel.setText(org.openide.util.NbBundle.getMessage(FileExtMismatchSettingsPanel.class, "FileExtMismatchSettingsPanel.extErrorLabel.text")); // NOI18N

        javax.swing.GroupLayout extensionPanelLayout = new javax.swing.GroupLayout(extensionPanel);
        extensionPanel.setLayout(extensionPanelLayout);
        extensionPanelLayout.setHorizontalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(extensionPanelLayout.createSequentialGroup()
                        .addComponent(extHeaderLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 324, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, extensionPanelLayout.createSequentialGroup()
                        .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(extErrorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(extensionPanelLayout.createSequentialGroup()
                                .addComponent(userExtTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 152, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(addExtButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(removeExtButton)))
                        .addGap(0, 0, 0))))
        );
        extensionPanelLayout.setVerticalGroup(
            extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, extensionPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(extHeaderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 396, Short.MAX_VALUE)
                .addGap(5, 5, 5)
                .addGroup(extensionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(removeExtButton)
                    .addComponent(addExtButton)
                    .addComponent(userExtTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addComponent(extErrorLabel))
        );

        jSplitPane1.setRightComponent(extensionPanel);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jSplitPane1)
                .addContainerGap())
        );

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 838, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Add a user-provided filename extension string to the selecte mimetype
    private void addExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addExtButtonActionPerformed
        String newExt = userExtTextField.getText();
        if (newExt.isEmpty()) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.empty"));
            return;
        }

        if (selectedMime.isEmpty()) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.noMimeType"));
            return;
        }

        if (currentExtensions.contains(newExt)) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addExtButton.errLabel.extExists"));
            return;
        }

        Set<String> editedExtensions = editableMap.get(selectedMime);
        editedExtensions.add(newExt);

        // Old array will be replaced by new array for this key
        editableMap.put(selectedMime, editedExtensions);

        // Refresh table
        updateExtList();
        extTableModel.resync();
        this.userExtTextField.setText("");
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_addExtButtonActionPerformed

    private void addTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addTypeButtonActionPerformed
        String newMime = userTypeTextField.getText();
        if (newMime.isEmpty()) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addTypeButton.empty"));
            return;
        }
        if (newMime.equals("application/octet-stream")) { //NON-NLS
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchConfigPanel.addTypeButton.mimeTypeNotSupported"));
            return;
        }
        if (mimeList.contains(newMime)) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.addTypeButton.mimeTypeExists"));
            return;
        }

        boolean mimeTypeDetectable = (null != fileTypeDetector) ? fileTypeDetector.isDetectable(newMime) : false;
        if (!mimeTypeDetectable) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchConfigPanel.addTypeButton.mimeTypeNotDetectable"));
            return;
        }

        editableMap.put(newMime, new HashSet<String>());

        // Refresh table
        updateMimeList();
        mimeTableModel.resync();
        userTypeTextField.setText("");
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_addTypeButtonActionPerformed

    private void userExtTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_userExtTextFieldFocusGained
        extErrorLabel.setText(" "); //space so Swing doesn't mess up vertical spacing
    }//GEN-LAST:event_userExtTextFieldFocusGained

    private void userTypeTextFieldFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_userTypeTextFieldFocusGained
        mimeErrLabel.setText(" "); //space so Swing doesn't mess up vertical spacing
    }//GEN-LAST:event_userTypeTextFieldFocusGained

    private void removeTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeTypeButtonActionPerformed
        if (selectedMime.isEmpty()) {
            mimeErrLabel.setForeground(Color.red);
            mimeErrLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.removeTypeButton.noneSelected"));
            return;
        }

        editableMap.remove(selectedMime);
        String deadMime = selectedMime;

        // Refresh table
        updateMimeList();
        mimeTableModel.resync();
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_removeTypeButtonActionPerformed

    private void removeExtButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeExtButtonActionPerformed
        if (selectedExt.isEmpty()) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(
                    NbBundle.getMessage(this.getClass(), "FileExtMismatchConfigPanel.removeExtButton.noneSelected"));
            return;
        }

        if (selectedMime.isEmpty()) {
            extErrorLabel.setForeground(Color.red);
            extErrorLabel.setText(NbBundle.getMessage(this.getClass(),
                    "FileExtMismatchConfigPanel.removeExtButton.noMimeTypeSelected"));
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
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_removeExtButtonActionPerformed

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
            mimeErrLabel.setText(" ");
            extErrorLabel.setText(" ");
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
        clearErrLabels();
        load(); // The next time this panel is opened, we want it to be fresh
    }

    public void ok() {
        store();
        clearErrLabels();
        load(); // The next time this panel is opened, we want it to be fresh
    }

    boolean valid() {
        return true;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addExtButton;
    private javax.swing.JButton addTypeButton;
    private javax.swing.JLabel extErrorLabel;
    private javax.swing.JLabel extHeaderLabel;
    private javax.swing.JTable extTable;
    private javax.swing.JPanel extensionPanel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JLabel mimeErrLabel;
    private javax.swing.JPanel mimePanel;
    private javax.swing.JTable mimeTable;
    private javax.swing.JButton removeExtButton;
    private javax.swing.JButton removeTypeButton;
    private javax.swing.JTextField userExtTextField;
    private javax.swing.JTextField userTypeTextField;
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
