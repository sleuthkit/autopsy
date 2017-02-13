/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.EventQueue;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * A panel to manage all keyword lists created/imported in Autopsy.
 */
class GlobalListsManagementPanel extends javax.swing.JPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;

    private final Logger logger = Logger.getLogger(GlobalListsManagementPanel.class.getName());
    private final KeywordListTableModel tableModel;
    private final org.sleuthkit.autopsy.keywordsearch.GlobalListSettingsPanel globalListSettingsPanel;

    GlobalListsManagementPanel(org.sleuthkit.autopsy.keywordsearch.GlobalListSettingsPanel gsp) {
        this.globalListSettingsPanel = gsp;
        tableModel = new KeywordListTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        listsTable.setAutoscrolls(true);
        listsTable.setTableHeader(null);
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        exportButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.customizeComponents.exportToFile"));
        copyListButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.customizeComponents.saveCurrentWIthNewNameToolTip"));
        listsTable.getParent().setBackground(listsTable.getBackground());

        listsTable.setCellSelectionEnabled(false);
        listsTable.setRowSelectionAllowed(true);
        tableModel.resync();
        setButtonStates();

        listsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                globalListSettingsPanel.setFocusOnKeywordTextBox();
                setButtonStates();
            }
        });

        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object source = evt.getSource();
                if (source instanceof String && ((String) source).equals("LOCAL")) { //NON-NLS
                    EventQueue.invokeLater(() -> {
                        globalListSettingsPanel.setFocusOnKeywordTextBox();
                        setButtonStates();
                    });
                }
            }
        });
    }

    void addDeleteButtonActionPerformed(ActionListener l) {
        deleteListButton.addActionListener(l);
    }

    void addRenameButtonActionPerformed(ActionListener l) {
        renameListButton.addActionListener(l);
    }

    void addCopyButtonActionPerformed(ActionListener l) {
        copyListButton.addActionListener(l);
    }

    /**
     * Opens the dialogue for creating a new keyword list and adds it to the table.
     */
    private void newKeywordListAction() {
        XmlKeywordSearchList writer = XmlKeywordSearchList.getCurrent();
        String listName = "";
       

        listName = (String) JOptionPane.showInputDialog(null, NbBundle.getMessage(this.getClass(), "KeywordSearch.newKwListTitle"),
                NbBundle.getMessage(this.getClass(), "KeywordSearch.newKeywordListMsg"), JOptionPane.PLAIN_MESSAGE, null, null, listName);

        if (listName == null || listName.trim().equals("")) {
            return;
        }
        boolean shouldAdd = false;
        if (writer.listExists(listName)) {
            if (writer.getList(listName).isEditable()) {
                boolean replace = KeywordSearchUtil.displayConfirmDialog(
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.newKeywordListMsg"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.newKeywordListDescription", listName),
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
                if (replace) {
                    shouldAdd = true;
                }
            } else {
                boolean replace = KeywordSearchUtil.displayConfirmDialog(
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.newKeywordListMsg"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.newKeywordListDescription2", listName),
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
                if (replace) {
                    shouldAdd = true;
                }
            }
        } else {
            shouldAdd = true;
        }
        if (shouldAdd) {
            writer.addList(listName, new ArrayList<Keyword>());
        }

        tableModel.resync();

        //This loop selects the recently ADDED keywordslist in the JTable
        for (int i = 0; i < listsTable.getRowCount(); i++) {
            if (listsTable.getValueAt(i, 0).equals(listName)) {
                listsTable.getSelectionModel().addSelectionInterval(i, i);
            }
        }
    }

    /**
     * Enables and disables buttons on this panel based on the current state.
     */
    void setButtonStates() {
        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();
        boolean isListSelected = !listsTable.getSelectionModel().isSelectionEmpty();
        boolean canEditList = isListSelected && !isIngestRunning;
        // items that only need ingest to not be running
        importButton.setEnabled(!isIngestRunning);
        
        // items that need an unlocked list w/out ingest running 
        deleteListButton.setEnabled(canEditList);
        renameListButton.setEnabled(canEditList);
        importButton.setEnabled(canEditList);
        
        // items that only need a selected list
        copyListButton.setEnabled(isListSelected);
        exportButton.setEnabled(isListSelected);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        listsTable = new javax.swing.JTable();
        newListButton = new javax.swing.JButton();
        importButton = new javax.swing.JButton();
        keywordListsLabel = new javax.swing.JLabel();
        exportButton = new javax.swing.JButton();
        copyListButton = new javax.swing.JButton();
        deleteListButton = new javax.swing.JButton();
        renameListButton = new javax.swing.JButton();

        setMinimumSize(new java.awt.Dimension(250, 0));

        listsTable.setModel(tableModel);
        listsTable.setMaximumSize(new java.awt.Dimension(30000, 30000));
        listsTable.setShowHorizontalLines(false);
        listsTable.setShowVerticalLines(false);
        listsTable.getTableHeader().setReorderingAllowed(false);
        listsTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                listsTableKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(listsTable);

        newListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/new16.png"))); // NOI18N
        newListButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.newListButton.text")); // NOI18N
        newListButton.setIconTextGap(2);
        newListButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        newListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newListButtonActionPerformed(evt);
            }
        });

        importButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/import16.png"))); // NOI18N
        importButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.importButton.text")); // NOI18N
        importButton.setIconTextGap(2);
        importButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        keywordListsLabel.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.keywordListsLabel.text")); // NOI18N

        exportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/export16.png"))); // NOI18N
        exportButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.exportButton.text")); // NOI18N
        exportButton.setIconTextGap(2);
        exportButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        copyListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/save16.png"))); // NOI18N
        copyListButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.copyListButton.text")); // NOI18N
        copyListButton.setIconTextGap(2);
        copyListButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        copyListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyListButtonActionPerformed(evt);
            }
        });

        deleteListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/delete16.png"))); // NOI18N
        deleteListButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.deleteListButton.text")); // NOI18N
        deleteListButton.setIconTextGap(2);
        deleteListButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        deleteListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteListButtonActionPerformed(evt);
            }
        });

        renameListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/edit16.png"))); // NOI18N
        renameListButton.setText(org.openide.util.NbBundle.getMessage(GlobalListsManagementPanel.class, "GlobalListsManagementPanel.renameListButton.text")); // NOI18N
        renameListButton.setIconTextGap(2);
        renameListButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        renameListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                renameListButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(keywordListsLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(newListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(deleteListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE))
                        .addGap(12, 12, 12)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(renameListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 75, Short.MAX_VALUE)
                            .addComponent(importButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(12, 12, 12)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(copyListButton, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)
                                .addGap(0, 1, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(exportButton, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {copyListButton, deleteListButton, exportButton, importButton, newListButton, renameListButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(keywordListsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 316, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(newListButton)
                    .addComponent(renameListButton)
                    .addComponent(copyListButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton)
                    .addComponent(exportButton)
                    .addComponent(deleteListButton))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {copyListButton, deleteListButton, exportButton, importButton, newListButton, renameListButton});

    }// </editor-fold>//GEN-END:initComponents

    private void newListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newListButtonActionPerformed
        newKeywordListAction();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        globalListSettingsPanel.setFocusOnKeywordTextBox();
    }//GEN-LAST:event_newListButtonActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed

        JFileChooser chooser = new JFileChooser();
        final String[] AUTOPSY_EXTENSIONS = new String[]{"xml"}; //NON-NLS
        final String[] ENCASE_EXTENSIONS = new String[]{"txt"}; //NON-NLS
        FileNameExtensionFilter autopsyFilter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.fileExtensionFilterLbl"), AUTOPSY_EXTENSIONS);
        FileNameExtensionFilter encaseFilter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.fileExtensionFilterLb2"), ENCASE_EXTENSIONS);
        chooser.addChoosableFileFilter(autopsyFilter);
        chooser.addChoosableFileFilter(encaseFilter);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        String listName = null;
        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                return;
            }

            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();

            final KeywordSearchList reader;

            if (KeywordSearchUtil.isXMLList(fileAbs)) {
                reader = new XmlKeywordSearchList(fileAbs);
            } else {
                reader = new EnCaseKeywordSearchList(fileAbs);
            }

            if (!reader.load()) {
                KeywordSearchUtil.displayDialog(
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.listImportFeatureTitle"), NbBundle.getMessage(this.getClass(), "KeywordSearch.importListFileDialogMsg", fileAbs), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }

            List<KeywordList> toImport = reader.getListsL();
            List<KeywordList> toImportConfirmed = new ArrayList<KeywordList>();

            final XmlKeywordSearchList writer = XmlKeywordSearchList.getCurrent();

            for (KeywordList list : toImport) {
                //check name collisions
                listName = list.getName();
                if (writer.listExists(listName)) {
                    Object[] options = {NbBundle.getMessage(this.getClass(), "KeywordSearch.yesOwMsg"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.noSkipMsg"),
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.cancelImportMsg")};
                    int choice = JOptionPane.showOptionDialog(this,
                            NbBundle.getMessage(this.getClass(), "KeywordSearch.overwriteListPrompt", listName),
                            NbBundle.getMessage(this.getClass(), "KeywordSearch.importOwConflict"),
                            JOptionPane.YES_NO_CANCEL_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]);
                    if (choice == JOptionPane.OK_OPTION) {
                        toImportConfirmed.add(list);
                    } else if (choice == JOptionPane.CANCEL_OPTION) {
                        break;
                    }

                } else {
                    //no conflict
                    toImportConfirmed.add(list);
                }

            }

            if (toImportConfirmed.isEmpty()) {
                return;
            }

            if (!writer.writeLists(toImportConfirmed)) {
                KeywordSearchUtil.displayDialog(
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.listImportFeatureTitle"), NbBundle.getMessage(this.getClass(), "KeywordSearch.kwListFailImportMsg"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            }

        }
        tableModel.resync();

        //This loop selects the recently IMPORTED keywordslist in the JTable
        if (listName != null) {
            for (int i = 0; i < listsTable.getRowCount(); i++) {
                if (listsTable.getValueAt(i, 0).equals(listName)) {
                    listsTable.getSelectionModel().addSelectionInterval(i, i);
                }
            }
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_importButtonActionPerformed
    private void listsTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_listsTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE) {
            int[] selected = listsTable.getSelectedRows();
            if (selected.length == 0) {
                return;
            } else if (KeywordSearchUtil.displayConfirmDialog(NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.title"), NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.body"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN)) {
                String listName = (String) listsTable.getModel().getValueAt(selected[0], 0);
                XmlKeywordSearchList.getCurrent().deleteList(listName);
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            } else {
                return;
            }
        }
        tableModel.resync();
    }//GEN-LAST:event_listsTableKeyPressed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed

        final String FEATURE_NAME = NbBundle.getMessage(this.getClass(),
                "KeywordSearchEditListPanel.exportButtonAction.featureName.text");

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml"; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.exportButtonActionPerformed.fileFilterLabel"), EXTENSION);
        chooser.setFileFilter(filter);
        String listName = listsTable.getValueAt(listsTable.getSelectedRow(), 0).toString();

        chooser.setSelectedFile(new File(listName));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int returnVal = chooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                return;
            }

            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();
            if (!fileAbs.endsWith("." + EXTENSION)) {
                fileAbs = fileAbs + "." + EXTENSION;
                selFile = new File(fileAbs);
            }

            boolean shouldWrite = true;
            if (selFile.exists()) {
                shouldWrite = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME,
                        NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.exportButtonActionPerformed.fileExistPrompt",
                                selFile.getName()), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            }
            if (!shouldWrite) {
                return;
            }

            XmlKeywordSearchList reader = XmlKeywordSearchList.getCurrent();

            List<KeywordList> toWrite = new ArrayList<>();
            toWrite.add(reader.getList(listName));
            final XmlKeywordSearchList exporter = new XmlKeywordSearchList(fileAbs);
            boolean written = exporter.saveLists(toWrite);
            if (written) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME,
                        NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.exportButtonActionPerformed.kwListExportedMsg"),
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            }
        }
    }//GEN-LAST:event_exportButtonActionPerformed

    private void copyListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyListButtonActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_copyListButtonActionPerformed

    private void deleteListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteListButtonActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_deleteListButtonActionPerformed

    private void renameListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_renameListButtonActionPerformed
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_renameListButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton copyListButton;
    private javax.swing.JButton deleteListButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JButton importButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel keywordListsLabel;
    private javax.swing.JTable listsTable;
    private javax.swing.JButton newListButton;
    private javax.swing.JButton renameListButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void store() {
        // Implemented by parent panel
    }

    @Override
    public void load() {
        listsTable.clearSelection();
    }

    void resync() {
        tableModel.resync();
    }

    private class KeywordListTableModel extends AbstractTableModel {

        private final XmlKeywordSearchList listsHandle = XmlKeywordSearchList.getCurrent();

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return listsHandle.getNumberLists(false);
        }

        @Override
        public String getColumnName(int column) {
            return NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.getColName.text");
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return listsHandle.getListNames(false).get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "KeywordSearchListsManagementPanel.setValueAt.exception.msg"));
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        //delete selected from handle, events are fired from the handle
        void deleteSelected(int[] selected) {
            List<String> toDel = new ArrayList<>();
            for (int i = 0; i < selected.length; i++) {
                toDel.add((String) getValueAt(0, selected[i]));
            }
            for (String del : toDel) {
                listsHandle.deleteList(del);
            }
        }

        //resync model from handle, then update table
        void resync() {
            fireTableDataChanged();
        }
    }

    void addListSelectionListener(ListSelectionListener l) {
        listsTable.getSelectionModel().addListSelectionListener(l);
    }
}
