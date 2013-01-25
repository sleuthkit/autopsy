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


package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *  KeywordSearchEditListPanel widget to manage keywords in lists
 */
class KeywordSearchEditListPanel extends javax.swing.JPanel implements ListSelectionListener, OptionsPanel {

    private static Logger logger = Logger.getLogger(KeywordSearchEditListPanel.class.getName());
    private KeywordTableModel tableModel;
    private KeywordSearchList currentKeywordList;

    
    private boolean ingestRunning;

    /** Creates new form KeywordSearchEditListPanel */
    KeywordSearchEditListPanel() {
        tableModel = new KeywordTableModel();
        initComponents();
        customizeComponents();
    }
    

    private void customizeComponents() {
        chRegex.setToolTipText("Keyword is a regular expression");
        addWordButton.setToolTipText(("Add a new word to the keyword search list"));
        addWordField.setToolTipText("Enter a new word or regex to search");
        exportButton.setToolTipText("Export the current keyword list to a file");
        saveListButton.setToolTipText("Save the current keyword list with a new name");
        deleteWordButton.setToolTipText("Remove selected keyword(s) from the list");

        //keywordTable.setAutoscrolls(true);
        //keywordTable.setTableHeader(null);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);

        keywordTable.getParent().setBackground(keywordTable.getBackground());

        //customize column witdhs
        final int width = jScrollPane1.getPreferredSize().width;
        keywordTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column = null;
        for (int i = 0; i < keywordTable.getColumnCount(); i++) {
            column = keywordTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.90)));
            } else {
                column.setPreferredWidth(((int) (width * 0.10)));
                //column.setCellRenderer(new CheckBoxRenderer());
            }
        }
        keywordTable.setCellSelectionEnabled(false);
        keywordTable.setRowSelectionAllowed(true);

        final ListSelectionModel lsm = keywordTable.getSelectionModel();
        lsm.addListSelectionListener(new ListSelectionListener() {
   
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (lsm.isSelectionEmpty() || currentKeywordList.isLocked()) {
                    deleteWordButton.setEnabled(false);
                    return;
                } else {
                    deleteWordButton.setEnabled(true);
                }
                
                //show selector if available
                DefaultListSelectionModel selModel = (DefaultListSelectionModel) e.getSource();
                if (!selModel.getValueIsAdjusting()) {
                    List<Keyword> keywords = currentKeywordList.getKeywords();
                    final int minIndex = selModel.getMinSelectionIndex();
                    final int maxIndex = selModel.getMaxSelectionIndex();
                    int selected = -1;
                    for (int i = minIndex; i <= maxIndex; i++) {
                        if (selModel.isSelectedIndex(i)) {
                            selected = i;
                            break;
                        }
                    }
                    if (selected > -1 && selected < keywords.size()) {
                        Keyword k = keywords.get(selected);
                        BlackboardAttribute.ATTRIBUTE_TYPE selType = k.getType();
                        if (selType != null) {
                            selectorsCombo.setSelectedIndex(selType.ordinal());
                        } else {
                            //set to none (last item)
                            selectorsCombo.setSelectedIndex(selectorsCombo.getItemCount() - 1);
                        }
                    }


                }
            }
        });

        //loadDefaultKeywords();
        

        initButtons();

        addWordField.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(cutMenuItem)) {
                    addWordField.cut();
                } else if (jmi.equals(copyMenuItem)) {
                    addWordField.copy();
                } else if (jmi.equals(pasteMenuItem)) {
                    addWordField.paste();
                } else if (jmi.equals(selectAllMenuItem)) {
                    addWordField.selectAll();
                }
            }
        };
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);



        if (IngestManager.getDefault().isModuleRunning(KeywordSearchIngestModule.getDefault())) {
            initIngest(0);
        } else {
            initIngest(1);
        }

        IngestManager.addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String changed = evt.getPropertyName();
                Object oldValue = evt.getOldValue();
                if (changed.equals(IngestModuleEvent.COMPLETED.toString() )
                        && ((String) oldValue).equals(KeywordSearchIngestModule.MODULE_NAME)) {
                    initIngest(1);
                } else if (changed.equals(IngestModuleEvent.STARTED.toString() )
                        && ((String) oldValue).equals(KeywordSearchIngestModule.MODULE_NAME)) {
                    initIngest(0);
                } else if (changed.equals(IngestModuleEvent.STOPPED.toString() )
                        && ((String) oldValue).equals(KeywordSearchIngestModule.MODULE_NAME)) {
                    initIngest(1);
                }
            }
        });

        //selectors
        selectorsCombo.setEnabled(false);
        for (BlackboardAttribute.ATTRIBUTE_TYPE type : BlackboardAttribute.ATTRIBUTE_TYPE.values()) {
            selectorsCombo.addItem(type.getDisplayName());
        }
        selectorsCombo.addItem("<none>");
        selectorsCombo.setSelectedIndex(selectorsCombo.getItemCount() - 1);

    }

    /** 
     * Initialize this panel depending on whether ingest is running
     * @param running 
     * case 0: ingest running
     * case 1: ingest not running
     */
    private void initIngest(int running) {
        switch (running) {
            case 0:
                ingestRunning = true;
                break;
            case 1:
                ingestRunning = false;
                break;
        }
        initButtons();
    }

    void initButtons() {
        //initialize buttons
        // Certain buttons will be disabled if no list is set
        boolean listSet = currentKeywordList != null;
        // Certain buttons will be disabled if ingest is ongoing
        boolean ingestOngoing = this.ingestRunning;
        // Certain buttons will be disabled if ingest is ongoing on this list
        boolean useForIngest = !listSet ? false : currentKeywordList.getUseForIngest();
        // Certain buttons will be disabled if the list shouldn't send ingest messages
        boolean sendIngestMessages = !listSet ? false : currentKeywordList.getIngestMessages();
        // Certain buttons will be disabled if the selected list is locked
        boolean isLocked = !listSet ? true : currentKeywordList.isLocked();
        // Certain buttons will be disabled if no keywords are set
        boolean noKeywords = !listSet ? true : currentKeywordList.getKeywords().isEmpty();

        // Certain buttons will be disabled if ingest is ongoing on this list
        List<String> ingestLists = new ArrayList<String>();
        if (ingestOngoing) {
            ingestLists = KeywordSearchIngestModule.getDefault().getKeywordLists();
        }
        boolean inIngest = !listSet ? false : ingestLists.contains(currentKeywordList.getName());

        addWordButton.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked);
        addWordField.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked);
        chRegex.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked);
        keywordOptionsLabel.setEnabled(addWordButton.isEnabled() || chRegex.isEnabled());
        keywordOptionsSeparator.setEnabled(addWordButton.isEnabled() || chRegex.isEnabled());
        selectorsCombo.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked && chRegex.isSelected());
        useForIngestCheckbox.setEnabled(listSet && (!ingestOngoing || !inIngest));
        useForIngestCheckbox.setSelected(useForIngest);
        ingestMessagesCheckbox.setEnabled(useForIngestCheckbox.isEnabled() && useForIngestCheckbox.isSelected());
        ingestMessagesCheckbox.setSelected(sendIngestMessages);
        listOptionsLabel.setEnabled(useForIngestCheckbox.isEnabled() || ingestMessagesCheckbox.isEnabled());
        listOptionsSeparator.setEnabled(useForIngestCheckbox.isEnabled() || ingestMessagesCheckbox.isEnabled());
        saveListButton.setEnabled(listSet);
        exportButton.setEnabled(listSet);
        deleteListButton.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked);
        deleteWordButton.setEnabled(listSet && (!ingestOngoing || !inIngest) && !isLocked);

        if (noKeywords) {
            saveListButton.setEnabled(false);
            exportButton.setEnabled(false);
            deleteWordButton.setEnabled(false);
        } else {
            saveListButton.setEnabled(true);
            exportButton.setEnabled(true);
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
        jScrollPane1 = new javax.swing.JScrollPane();
        keywordTable = new javax.swing.JTable();
        useForIngestCheckbox = new javax.swing.JCheckBox();
        addKeywordPanel = new javax.swing.JPanel();
        addWordButton = new javax.swing.JButton();
        addWordField = new javax.swing.JTextField();
        chRegex = new javax.swing.JCheckBox();
        selectorsCombo = new javax.swing.JComboBox();
        deleteWordButton = new javax.swing.JButton();
        ingestMessagesCheckbox = new javax.swing.JCheckBox();
        keywordsLabel = new javax.swing.JLabel();
        keywordOptionsLabel = new javax.swing.JLabel();
        listOptionsLabel = new javax.swing.JLabel();
        keywordOptionsSeparator = new javax.swing.JSeparator();
        listOptionsSeparator = new javax.swing.JSeparator();
        deleteListButton = new javax.swing.JButton();
        saveListButton = new javax.swing.JButton();
        exportButton = new javax.swing.JButton();

        cutMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        pasteMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setMinimumSize(new java.awt.Dimension(340, 300));
        setPreferredSize(new java.awt.Dimension(340, 420));

        jScrollPane1.setPreferredSize(new java.awt.Dimension(340, 300));

        keywordTable.setModel(tableModel);
        keywordTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);
        keywordTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(keywordTable);

        useForIngestCheckbox.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.useForIngestCheckbox.text")); // NOI18N
        useForIngestCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                useForIngestCheckboxActionPerformed(evt);
            }
        });

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

        chRegex.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.chRegex.text")); // NOI18N
        chRegex.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chRegexActionPerformed(evt);
            }
        });

        selectorsCombo.setToolTipText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.selectorsCombo.toolTipText")); // NOI18N

        deleteWordButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.deleteWordButton.text")); // NOI18N
        deleteWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteWordButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addKeywordPanelLayout = new javax.swing.GroupLayout(addKeywordPanel);
        addKeywordPanel.setLayout(addKeywordPanelLayout);
        addKeywordPanelLayout.setHorizontalGroup(
            addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addKeywordPanelLayout.createSequentialGroup()
                .addGroup(addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(addKeywordPanelLayout.createSequentialGroup()
                            .addComponent(addWordField)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(addWordButton))
                        .addComponent(deleteWordButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, addKeywordPanelLayout.createSequentialGroup()
                        .addComponent(chRegex)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(selectorsCombo, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        addKeywordPanelLayout.setVerticalGroup(
            addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addKeywordPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addWordButton))
                .addGap(7, 7, 7)
                .addGroup(addKeywordPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectorsCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chRegex))
                .addGap(7, 7, 7)
                .addComponent(deleteWordButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        ingestMessagesCheckbox.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.ingestMessagesCheckbox.text")); // NOI18N
        ingestMessagesCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.ingestMessagesCheckbox.toolTipText")); // NOI18N
        ingestMessagesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ingestMessagesCheckboxActionPerformed(evt);
            }
        });

        keywordsLabel.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.keywordsLabel.text")); // NOI18N

        keywordOptionsLabel.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.keywordOptionsLabel.text")); // NOI18N

        listOptionsLabel.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.listOptionsLabel.text")); // NOI18N

        deleteListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/delete16.png"))); // NOI18N
        deleteListButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.deleteListButton.text")); // NOI18N

        saveListButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/save16.png"))); // NOI18N
        saveListButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.saveListButton.text")); // NOI18N

        exportButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/export16.png"))); // NOI18N
        exportButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchEditListPanel.class, "KeywordSearchEditListPanel.exportButton.text")); // NOI18N
        exportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout listEditorPanelLayout = new javax.swing.GroupLayout(listEditorPanel);
        listEditorPanel.setLayout(listEditorPanelLayout);
        listEditorPanelLayout.setHorizontalGroup(
            listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(listEditorPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(addKeywordPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                .addComponent(listOptionsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(listOptionsSeparator))
                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                .addComponent(keywordOptionsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(keywordOptionsSeparator))
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(keywordsLabel)
                                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                                        .addGap(10, 10, 10)
                                        .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                                .addComponent(exportButton)
                                                .addGap(18, 18, 18)
                                                .addComponent(saveListButton)
                                                .addGap(18, 18, 18)
                                                .addComponent(deleteListButton))
                                            .addComponent(useForIngestCheckbox)
                                            .addComponent(ingestMessagesCheckbox))))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())))
        );
        listEditorPanelLayout.setVerticalGroup(
            listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, listEditorPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(keywordsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(keywordOptionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 7, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(keywordOptionsLabel))
                        .addGap(7, 7, 7)
                        .addComponent(addKeywordPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, 0)
                        .addComponent(listOptionsLabel))
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addGap(123, 123, 123)
                        .addComponent(listOptionsSeparator, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(7, 7, 7)
                .addComponent(useForIngestCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ingestMessagesCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exportButton)
                    .addComponent(saveListButton)
                    .addComponent(deleteListButton))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(listEditorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(listEditorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void addWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordButtonActionPerformed
        String newWord = addWordField.getText().trim();
        boolean isLiteral = !chRegex.isSelected();
        final Keyword keyword = new Keyword(newWord, isLiteral);
        if (!isLiteral) {
            //get selector
            int selI = this.selectorsCombo.getSelectedIndex();
            if (selI < this.selectorsCombo.getItemCount() - 1) {
                BlackboardAttribute.ATTRIBUTE_TYPE selector = BlackboardAttribute.ATTRIBUTE_TYPE.values()[selI];
                keyword.setType(selector);
            }
        }

        if (newWord.equals("")) {
            return;
        } else if (currentKeywordList.hasKeyword(keyword)) {
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
        KeywordSearchListsXML.getCurrent().addList(currentKeywordList);
        chRegex.setSelected(false);
        addWordField.setText("");

        initButtons();
    }//GEN-LAST:event_addWordButtonActionPerformed

    private void deleteWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteWordButtonActionPerformed
        if (KeywordSearchUtil.displayConfirmDialog("Removing a keyword"
                , "This will remove a keyword from the list globally (for all Cases). "
                + "Do you want to proceed? "
                , KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) ) {
        
        tableModel.deleteSelected(keywordTable.getSelectedRows());
        KeywordSearchListsXML.getCurrent().addList(currentKeywordList);
        initButtons();
        }
    }//GEN-LAST:event_deleteWordButtonActionPerformed

    private void addWordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordFieldActionPerformed
        addWordButtonActionPerformed(evt);
    }//GEN-LAST:event_addWordFieldActionPerformed

    private void exportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportButtonActionPerformed

        final String FEATURE_NAME = "Keyword List Export";

        JFileChooser chooser = new JFileChooser();
        final String EXTENSION = "xml";
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Keyword List XML file", EXTENSION);
        chooser.setFileFilter(filter);
        chooser.setSelectedFile(new File(currentKeywordList.getName()));
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
            toWrite.add(reader.getList(currentKeywordList.getName()));
            final KeywordSearchListsXML exporter = new KeywordSearchListsXML(fileAbs);
            boolean written = exporter.saveLists(toWrite);
            if (written) {
                KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword lists exported",
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            }
        }
    }//GEN-LAST:event_exportButtonActionPerformed

    private void chRegexActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chRegexActionPerformed
        selectorsCombo.setEnabled(chRegex.isEnabled() && chRegex.isSelected());
    }//GEN-LAST:event_chRegexActionPerformed

private void useForIngestCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_useForIngestCheckboxActionPerformed
    ingestMessagesCheckbox.setEnabled(useForIngestCheckbox.isSelected());
    currentKeywordList.setUseForIngest(useForIngestCheckbox.isSelected());
    KeywordSearchListsXML updater = KeywordSearchListsXML.getCurrent();
    updater.addList(currentKeywordList);
}//GEN-LAST:event_useForIngestCheckboxActionPerformed

    private void ingestMessagesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ingestMessagesCheckboxActionPerformed
        currentKeywordList.setIngestMessages(ingestMessagesCheckbox.isSelected());
        KeywordSearchListsXML updater = KeywordSearchListsXML.getCurrent();
        updater.addList(currentKeywordList);
    }//GEN-LAST:event_ingestMessagesCheckboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel addKeywordPanel;
    private javax.swing.JButton addWordButton;
    private javax.swing.JTextField addWordField;
    private javax.swing.JCheckBox chRegex;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JButton deleteListButton;
    private javax.swing.JButton deleteWordButton;
    private javax.swing.JButton exportButton;
    private javax.swing.JCheckBox ingestMessagesCheckbox;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel keywordOptionsLabel;
    private javax.swing.JSeparator keywordOptionsSeparator;
    private javax.swing.JTable keywordTable;
    private javax.swing.JLabel keywordsLabel;
    private javax.swing.JPanel listEditorPanel;
    private javax.swing.JLabel listOptionsLabel;
    private javax.swing.JSeparator listOptionsSeparator;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JButton saveListButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JComboBox selectorsCombo;
    private javax.swing.JCheckBox useForIngestCheckbox;
    // End of variables declaration//GEN-END:variables

    @Override
    public void valueChanged(ListSelectionEvent e) {
        //respond to list selection changes in KeywordSearchListManagementPanel
        ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
        if (!listSelectionModel.isSelectionEmpty()) {
            int index = listSelectionModel.getMinSelectionIndex();

            listSelectionModel.setSelectionInterval(index, index);
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

            currentKeywordList = loader.getListsL(false).get(index);
            tableModel.resync();
            initButtons();
        } else {
            currentKeywordList = null;
            tableModel.resync();
            initButtons();
        }
    }

    @Override
    public void store() {
        // Implemented by parent panel
    }

    @Override
    public void load() {
        // Implemented by parent panel
    }
    
    KeywordSearchList getCurrentKeywordList() {
        return currentKeywordList;
    }
    
    void setCurrentKeywordList(KeywordSearchList list) {
        currentKeywordList = list;
    }
    
    void addDeleteButtonActionPerformed(ActionListener l) {
        deleteListButton.addActionListener(l);
    }
    
    void addSaveButtonActionPerformed(ActionListener l) {
        saveListButton.addActionListener(l);
    }


    private class KeywordTableModel extends AbstractTableModel {
        //data

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return currentKeywordList == null ? 0 : currentKeywordList.getKeywords().size();
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
            if(currentKeywordList == null) {
                return "";
            }
            Keyword word = currentKeywordList.getKeywords().get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = (Object) word.getQuery();
                    break;
                case 1:
                    ret = (Object) !word.isLiteral();
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
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        void addKeyword(Keyword keyword) {
            if(!currentKeywordList.hasKeyword(keyword)) {
                currentKeywordList.getKeywords().add(keyword);
            }
            fireTableDataChanged();
        }

        void resync() {
            fireTableDataChanged();
        }

        //delete selected from handle, events are fired from the handle
        void deleteSelected(int[] selected) {
            List<Keyword> words = currentKeywordList.getKeywords();
            Arrays.sort(selected);
            for(int arrayi = selected.length-1; arrayi >= 0; arrayi--) {
                words.remove(selected[arrayi]);
            }
            resync();
        }

    }

    private class CheckBoxRenderer extends JCheckBox implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value,
                boolean isSelected, boolean hasFocus,
                int row, int column) {

            this.setHorizontalAlignment(JCheckBox.CENTER);
            this.setVerticalAlignment(JCheckBox.CENTER);

            Boolean selected = (Boolean) table.getModel().getValueAt(row, 1);
            setSelected(selected);
            if (isSelected) {
                setBackground(keywordTable.getSelectionBackground());
                setForeground(keywordTable.getSelectionForeground());
            } else {
                setBackground(keywordTable.getBackground());
                setForeground(keywordTable.getForeground());
            }
            setEnabled(false);

            return this;
        }
    }
}
