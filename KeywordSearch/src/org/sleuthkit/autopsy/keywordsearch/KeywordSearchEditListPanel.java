/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 Basis Technology Corp.
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

/*
 * KeywordSearchEditListPanel.java
 *
 * Created on Feb 10, 2012, 4:20:03 PM
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

/**
 *
 * @author dfickling
 */
public class KeywordSearchEditListPanel extends javax.swing.JPanel implements ListSelectionListener{

    private static Logger logger = Logger.getLogger(KeywordSearchEditListPanel.class.getName());
    private KeywordTableModel tableModel;
    private String currentKeywordList;
    
    private static KeywordSearchEditListPanel instance = null;
    
    /** Creates new form KeywordSearchEditListPanel */
    public KeywordSearchEditListPanel() {
        tableModel = new KeywordTableModel();
        initComponents();
        customizeComponents();
    }
    
    public static synchronized KeywordSearchEditListPanel getDefault() {
        if (instance == null) {
            instance = new KeywordSearchEditListPanel();
        }
        return instance;
    }
    
    private void customizeComponents() {
        chRegex.setToolTipText("Keyword is a regular expression");
        addWordButton.setToolTipText(("Add a new word to the keyword search list"));
        addWordField.setToolTipText("Enter a new word or regex to search");
        saveListButton.setToolTipText("Save the current keyword list to a file");
        deleteWordButton.setToolTipText("Remove selected keyword(s) from the list");
        
        //keywordTable.setAutoscrolls(true);
        //keywordTable.setTableHeader(null);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);

        keywordTable.getParent().setBackground(keywordTable.getBackground());

        //customize column witdhs
        keywordTable.setSize(260, 200);
        final int width = keywordTable.getSize().width;
        TableColumn column = null;
        for (int i = 0; i < 2; i++) {
            column = keywordTable.getColumnModel().getColumn(i);
            if (i > 0) {
                column.setPreferredWidth(((int) (width * 0.25)));
                //column.setCellRenderer(new CellTooltipRenderer());
            }
            else {
                //column.setCellRenderer(new CellTooltipRenderer());
                column.setPreferredWidth(((int) (width * 0.74)));
            }
        }
        keywordTable.setCellSelectionEnabled(false);
        keywordTable.setRowSelectionAllowed(true);

        //loadDefaultKeywords();

        initButtons();
        
        addWordField.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent e){
                JMenuItem jmi = (JMenuItem) e.getSource();
                if(jmi.equals(cutMenuItem))
                    addWordField.cut();
                else if(jmi.equals(copyMenuItem))
                    addWordField.copy();
                else if(jmi.equals(pasteMenuItem))
                    addWordField.paste();
                else if(jmi.equals(selectAllMenuItem))
                    addWordField.selectAll();
            }
        };
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
    }

    private void initButtons() {
        //initialize save buttons
        if (getAllKeywords().isEmpty()) {
            saveListButton.setEnabled(false);
        } else {
            saveListButton.setEnabled(true);
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickMenu = new javax.swing.JPopupMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        listEditorPanel = new javax.swing.JPanel();
        chRegex = new javax.swing.JCheckBox();
        deleteWordButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        keywordTable = new javax.swing.JTable();
        addWordButton = new javax.swing.JButton();
        addWordField = new javax.swing.JTextField();
        saveListButton = new javax.swing.JButton();
        importButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();
        deleteListButton = new javax.swing.JButton();

        cutMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        pasteMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(326, 400));

        listEditorPanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        chRegex.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.chRegex.text")); // NOI18N

        deleteWordButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.deleteWordButton.text")); // NOI18N
        deleteWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteWordButtonActionPerformed(evt);
            }
        });

        keywordTable.setModel(tableModel);
        keywordTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);
        keywordTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(keywordTable);

        addWordButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.addWordButton.text")); // NOI18N
        addWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addWordButtonActionPerformed(evt);
            }
        });

        addWordField.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.addWordField.text")); // NOI18N
        addWordField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addWordFieldActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout listEditorPanelLayout = new javax.swing.GroupLayout(listEditorPanel);
        listEditorPanel.setLayout(listEditorPanelLayout);
        listEditorPanelLayout.setHorizontalGroup(
            listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(listEditorPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, listEditorPanelLayout.createSequentialGroup()
                        .addGap(24, 24, 24)
                        .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(chRegex)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(addWordButton)
                        .addGap(35, 35, 35))
                    .addComponent(deleteWordButton, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE))
                .addContainerGap())
        );
        listEditorPanelLayout.setVerticalGroup(
            listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, listEditorPanelLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(addWordButton)
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(chRegex)))
                .addGap(7, 7, 7)
                .addComponent(deleteWordButton)
                .addContainerGap())
        );

        saveListButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.saveListButton.text")); // NOI18N
        saveListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveListButtonActionPerformed(evt);
            }
        });

        importButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.importButton.text")); // NOI18N
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        exportButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.exportButton.text")); // NOI18N
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        deleteListButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.deleteListButton.text")); // NOI18N
        deleteListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteListButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(importButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(exportButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(deleteListButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(saveListButton)
                .addContainerGap())
            .addComponent(listEditorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(listEditorPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 39, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(importButton)
                    .addComponent(exportButton)
                    .addComponent(deleteListButton)
                    .addComponent(saveListButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordButtonActionPerformed
        String newWord = addWordField.getText().trim();
        boolean isLiteral = !chRegex.isSelected();
        final Keyword keyword = new Keyword(newWord, isLiteral);
     
        if (newWord.equals("")) {
            return;
        } else if (keywordExists(keyword)) {
            KeywordSearchUtil.displayDialog("New Keyword Entry", "Keyword already exists in the list.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            return;
        }


        //check if valid
        boolean valid = true;
        try {
            Pattern.compile(newWord);
        } catch (PatternSyntaxException ex1) {
            valid = false;
        } catch (IllegalArgumentException ex2) {
            valid = false;
        }
        if (!valid) {
            KeywordSearchUtil.displayDialog("New Keyword Entry", "Invalid keyword pattern.  Use words or a correct regex pattern.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            return;
        }

        //add & reset checkbox
        tableModel.addKeyword(keyword);
        chRegex.setSelected(false);
        addWordField.setText("");

        initButtons();
    }//GEN-LAST:event_addWordButtonActionPerformed

    private void saveListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveListButtonActionPerformed
        final String FEATURE_NAME = "Save Keyword List";
        KeywordSearchListsXML writer = KeywordSearchListsXML.getCurrent();

        List<Keyword> keywords = tableModel.getAllKeywords();
        if (keywords.isEmpty()) {
            KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List is empty and cannot be saved", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            return;
        }

        String listName = (String) JOptionPane.showInputDialog(
                null,
                "New keyword list name:",
                FEATURE_NAME,
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentKeywordList != null ? currentKeywordList : "");
        if (listName == null || listName.trim().equals("")) {
            return;
        }

        boolean shouldAdd = false;
        if (writer.listExists(listName)) {
            boolean replace = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME, "Keyword List <" + listName + "> already exists, do you want to replace it?",
                    KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            if (replace) {
                shouldAdd = true;
            }

        } else {
            shouldAdd = true;
        }

        if (shouldAdd) {
            writer.addList(listName, keywords);
        }

        currentKeywordList = listName;
        KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> saved", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);

    }//GEN-LAST:event_saveListButtonActionPerformed

    private void deleteWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteWordButtonActionPerformed
         tableModel.deleteSelected(keywordTable.getSelectedRows());
    }//GEN-LAST:event_deleteWordButtonActionPerformed

    private void addWordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordFieldActionPerformed
        addWordButtonActionPerformed(evt);
    }//GEN-LAST:event_addWordFieldActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        final String FEATURE_NAME = "Keyword List Import";

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml";
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Keyword List XML file", EXTENSION);
        chooser.setFileFilter(filter);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int returnVal = chooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = chooser.getSelectedFile();
            if (selFile == null) {
                return;
            }

            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();

            final KeywordSearchListsXML reader = new KeywordSearchListsXML(fileAbs);
            if (!reader.load()) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Error importing keyword list from file " + fileAbs, KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
                return;
            }

            List<KeywordSearchList> toImport = reader.getListsL();
            List<KeywordSearchList> toImportConfirmed = new ArrayList<KeywordSearchList>();

            final KeywordSearchListsXML writer = KeywordSearchListsXML.getCurrent();

            for (KeywordSearchList list : toImport) {
                //check name collisions
                if (writer.listExists(list.getName())) {
                    Object[] options = {"Yes, overwrite",
                        "No, skip",
                        "Cancel import"};
                    int choice = JOptionPane.showOptionDialog(this,
                            "Keyword list <" + list.getName() + "> already exists locally, overwrite?",
                            "Import list conflict",
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

            if (writer.writeLists(toImportConfirmed)) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword list imported", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            }
            
            initButtons();
        }
    }//GEN-LAST:event_importButtonActionPerformed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed
        save();
        
        final String FEATURE_NAME = "Keyword List Export";

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml";
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Keyword List XML file", EXTENSION);
        chooser.setFileFilter(filter);
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
                shouldWrite = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME, "File " + selFile.getName() + " exists, overwrite?", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            }
            if (!shouldWrite) {
                return;
            }


            KeywordSearchListsXML reader = KeywordSearchListsXML.getCurrent();

            List<KeywordSearchList> toWrite = new ArrayList<KeywordSearchList>();
            toWrite.add(reader.getList(currentKeywordList));
            final KeywordSearchListsXML exporter = new KeywordSearchListsXML(fileAbs);
            boolean written = exporter.writeLists(toWrite);
            if (written) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword lists exported", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                return;
            }
        }
    }//GEN-LAST:event_exportButtonActionPerformed

    private void deleteListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteListButtonActionPerformed
        KeywordSearchListsXML deleter = KeywordSearchListsXML.getCurrent();
        String toDelete = currentKeywordList;
        currentKeywordList = null;
        tableModel.deleteAll();
        deleter.deleteList(toDelete);
    }//GEN-LAST:event_deleteListButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addWordButton;
    private javax.swing.JTextField addWordField;
    private javax.swing.JCheckBox chRegex;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JButton deleteListButton;
    private javax.swing.JButton deleteWordButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JButton importButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable keywordTable;
    private javax.swing.JPanel listEditorPanel;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JButton saveListButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    // End of variables declaration//GEN-END:variables

    @Override
    public void valueChanged(ListSelectionEvent e) {

        ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
        if (!listSelectionModel.isSelectionEmpty()) {
            int index = listSelectionModel.getMinSelectionIndex();
            KeywordSearchListsManagementPanel listsPanel = KeywordSearchListsManagementPanel.getDefault();
            
            save();
            listSelectionModel.setSelectionInterval(index, index);
            currentKeywordList = listsPanel.getAllLists().get(index);
            tableModel.resync(currentKeywordList);
            initButtons();
        }
    }

    List<Keyword> getAllKeywords() {
        return tableModel.getAllKeywords();
    }

    List<Keyword> getSelectedKeywords() {
        return tableModel.getSelectedKeywords(keywordTable.getSelectedRows());
    }

    private boolean keywordExists(Keyword keyword) {
        return tableModel.keywordExists(keyword);
    }

    void save() {
        if (currentKeywordList != null && !currentKeywordList.equals("")) {
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();
            KeywordSearchList oldList = loader.getList(currentKeywordList);
            List<Keyword> oldKeywords = oldList.getKeywords();
            List<Keyword> newKeywords = getAllKeywords();

            if (!oldKeywords.equals(newKeywords)) {
                /*boolean save = KeywordSearchUtil.displayConfirmDialog("Save List Changes",
                        "Do you want to save the changes you made to list " + currentKeywordList + "?",
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);*/
                boolean save = true;
                if (save) {
                    loader.addList(currentKeywordList, newKeywords);
                }
            }
        }
    }

    private class KeywordTableModel extends AbstractTableModel {
        //data

        private Set<TableEntry> keywordData = new TreeSet<TableEntry>();

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return keywordData.size();
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;
            
            switch (column) {
                case 0:
                    colName = "Keyword";
                    break;
                case 1:
                    colName = "RegEx";
                    break;
                default:
                    ;
                           
            }
            return colName;
        }
        
        

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            TableEntry entry = null;
            //iterate until row
            Iterator<TableEntry> it = keywordData.iterator();
            for (int i = 0; i <= rowIndex; ++i) {
                entry = it.next();
            }
            switch (columnIndex) {
                case 0:
                    ret = (Object) entry.keyword;
                    break;
                case 1:
                    ret = (Object) !entry.isLiteral;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: " + columnIndex);
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
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        List<Keyword> getAllKeywords() {
            List<Keyword> ret = new ArrayList<Keyword>();
            for (TableEntry e : keywordData) {
                ret.add(new Keyword(e.keyword, e.isLiteral));
            }
            return ret;
        }

        List<Keyword> getSelectedKeywords(int[] selected) {
            List<Keyword> ret = new ArrayList<Keyword>();
            for(int i = 0; i < selected.length; i++){
                Keyword word = new Keyword((String) getValueAt(0, selected[i]), !((Boolean) getValueAt(1, selected[i])));
                ret.add(word);
            }
            return ret;
        }

        boolean keywordExists(Keyword keyword) {
            List<Keyword> all = getAllKeywords();
            return all.contains(keyword);
        }

        void addKeyword(Keyword keyword) {
            if (!keywordExists(keyword)) {
                keywordData.add(new TableEntry(keyword));
            }
            fireTableDataChanged();
        }

        void addKeywords(List<Keyword> keywords) {
            for (Keyword keyword : keywords) {
                if (!keywordExists(keyword)) {
                    keywordData.add(new TableEntry(keyword));
                }
            }
            fireTableDataChanged();
        }

        void resync(String listName) {
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();
            KeywordSearchList list = loader.getList(listName);
            List<Keyword> keywords = list.getKeywords();

            deleteAll();
            addKeywords(keywords);
        }

        void deleteAll() {
            keywordData.clear();
            fireTableDataChanged();
        }
        
        //delete selected from handle, events are fired from the handle
        void deleteSelected(int[] selected) {
            List<TableEntry> toDel = new ArrayList<TableEntry>();
            for(int i = 0; i < selected.length; i++){
                Keyword word = new Keyword((String) getValueAt(selected[i], 0), !((Boolean) getValueAt(selected[i], 1)));
                toDel.add(new TableEntry(word));
            }
            for (TableEntry del : toDel) {
                keywordData.remove(del);
            }
            fireTableDataChanged();

        }

        class TableEntry implements Comparable {

            String keyword;
            Boolean isLiteral;

            TableEntry(Keyword keyword) {
                this.keyword = keyword.getQuery();
                this.isLiteral = keyword.isLiteral();
            }
            
            TableEntry(String keyword, Boolean isLiteral) {
                this.keyword = keyword;
                this.isLiteral = isLiteral;
            }

             @Override
            public int compareTo(Object o) {
                int keywords =  this.keyword.compareTo(((TableEntry) o).keyword);
                if (keywords != 0) 
                    return keywords;
                else return this.isLiteral.compareTo(((TableEntry) o).isLiteral);
            }
         
        }
    }

    /**
     * tooltips that show entire query string
     */
    private static class CellTooltipRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            if (column == 0) {
                String val = (String) table.getModel().getValueAt(row, column);
                setToolTipText(val);
                setText(val);
            }

            return this;
        }
    }
}
