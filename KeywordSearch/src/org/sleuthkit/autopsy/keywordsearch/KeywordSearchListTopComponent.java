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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Top component for Keyword List search
 */

//@ConvertAsProperties(dtd = "-//org.sleuthkit.autopsy.keywordsearch//KeywordSearchList//EN",
//autostore = false)
//@TopComponent.Description(preferredID = "KeywordSearchListTopComponent",
////iconBase="SET/PATH/TO/ICON/HERE", 
//persistenceType = TopComponent.PERSISTENCE_NEVER)
//@TopComponent.Registration(mode = "explorer", openAtStartup = false)
////@ActionID(category = "Window", id = "org.sleuthkit.autopsy.keywordsearch.KeywordSearchListTopComponent")
////@ActionReference(path = "Menu/Window" /*, position = 333 */)
////@TopComponent.OpenActionRegistration(displayName = "#CTL_KeywordSearchListAction",
////preferredID = "KeywordSearchListTopComponent")
public final class KeywordSearchListTopComponent extends TopComponent implements KeywordSearchTopComponentInterface {

    private static Logger logger = Logger.getLogger(KeywordSearchListTopComponent.class.getName());
    private KeywordTableModel tableModel;
    private String currentKeywordList;
    
    public static final String PREFERRED_ID = "KeywordSearchListTopComponent";
    
    private static KeywordSearchListTopComponent instance = null;

    private KeywordSearchListTopComponent() {
        tableModel = new KeywordTableModel();
        initComponents();
        customizeComponents();
        setName(NbBundle.getMessage(KeywordSearchListTopComponent.class, "CTL_KeywordSearchListTopComponent"));
        setToolTipText(NbBundle.getMessage(KeywordSearchListTopComponent.class, "HINT_KeywordSearchListTopComponent"));

    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }
    
    
    
    public static synchronized KeywordSearchListTopComponent getDefault() {
        if (instance == null) {
            instance = new KeywordSearchListTopComponent();
        }
        return instance;
    }
    
     public static synchronized KeywordSearchListTopComponent findInstance() {
        TopComponent win = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (win == null) {
            return getDefault();
        }
        if (win instanceof KeywordSearchListTopComponent) {
            return (KeywordSearchListTopComponent) win;
        }
       
        return getDefault();
    }

    private void customizeComponents() {
        chRegex.setToolTipText("Keyword is a regular expression");
        addWordButton.setToolTipText(("Add a new word to the keyword search list"));
        addWordField.setToolTipText("Enter a new word or regex to search");

        loadListButton.setToolTipText("Load a new keyword list from file or delete an existing list");
        importButton.setToolTipText("Import list(s) of keywords from an external file.");
        saveListButton.setToolTipText("Save the current keyword list to a file");
        searchButton.setToolTipText("Execute the keyword list search using the current list");
        deleteWordButton.setToolTipText("Remove selected keyword(s) from the list");
        deleteAllWordsButton.setToolTipText("Remove all keywords from the list (clear it)");
        
        searchButton.setEnabled(false);

        //keywordTable.setAutoscrolls(true);
        //keywordTable.setTableHeader(null);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);

        keywordTable.getParent().setBackground(keywordTable.getBackground());

        //customize column witdhs
        keywordTable.setSize(260, 200);
        final int width = keywordTable.getSize().width;
        TableColumn column = null;
        for (int i = 0; i < 3; i++) {
            column = keywordTable.getColumnModel().getColumn(i);
            if (i > 0) {
                column.setPreferredWidth(((int) (width * 0.15)));
                //column.setCellRenderer(new CellTooltipRenderer());
            }
            else {
                column.setCellRenderer(new CellTooltipRenderer());
                column.setPreferredWidth(((int) (width * 0.68)));
            }
        }
        keywordTable.setCellSelectionEnabled(false);

        //loadDefaultKeywords();

        KeywordSearchListsXML.getCurrent().addPropertyChangeListener(new PropertyChangeListener() {

            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(KeywordSearchListsXML.ListsEvt.LIST_DELETED.toString())) {
                    //still keep keywords from deleted list in widgetm just disassociate the name
                    currentKeywordList = null;
                    curListValLabel.setText("-");
                    if (Integer.valueOf((Integer) evt.getNewValue()) == 0) {
                        loadListButton.setEnabled(false);
                    }
                } else if (evt.getPropertyName().equals(KeywordSearchListsXML.ListsEvt.LIST_ADDED.toString())) {
                    if (Integer.valueOf((Integer) evt.getOldValue()) == 0) {
                        loadListButton.setEnabled(true);
                    }
                }
            }
        });

        if (KeywordSearchListsXML.getCurrent().getNumberLists() == 0) {
            loadListButton.setEnabled(false);
        }

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
        //initialize remove buttons
        if (getSelectedKeywords().isEmpty()) {
            deleteWordButton.setEnabled(false);
        } else {
            deleteWordButton.setEnabled(true);
        }

        if (getAllKeywords().isEmpty()) {
            deleteAllWordsButton.setEnabled(false);
            saveListButton.setEnabled(false);
        } else {
            deleteAllWordsButton.setEnabled(true);
            saveListButton.setEnabled(true);
        }
    }

    private void loadDefaultKeywords() {
        //some hardcoded keywords for testing

        //phone number
        tableModel.addKeyword(new Keyword("\\d\\d\\d[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d", false));
        tableModel.addKeyword(new Keyword("\\d{8,10}", false));
        tableModel.addKeyword(new Keyword("phone|fax", false));
        //IP address
        tableModel.addKeyword(new Keyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])", false));
        //email
        tableModel.addKeyword(new Keyword("[e\\-]{0,2}mail", false));
        tableModel.addKeyword(new Keyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", false));
        //URL
        tableModel.addKeyword(new Keyword("ftp|sftp|ssh|http|https|www", false));
        //escaped literal word \d\d\d
        tableModel.addKeyword(new Keyword("\\Q\\d\\d\\d\\E", false));
        tableModel.addKeyword(new Keyword("\\d\\d\\d\\d", true));
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainScrollPane = new javax.swing.JScrollPane();
        mainPanel = new javax.swing.JPanel();
        rightClickMenu = new javax.swing.JPopupMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        filesIndexedNameLabel = new javax.swing.JLabel();
        filesIndexedValLabel = new javax.swing.JLabel();
        titleLabel = new javax.swing.JLabel();
        curListNameLabel = new javax.swing.JLabel();
        loadListButton = new javax.swing.JButton();
        tablePanel = new javax.swing.JPanel();
        saveListButton = new javax.swing.JButton();
        deleteWordButton = new javax.swing.JButton();
        deleteAllWordsButton = new javax.swing.JButton();
        chRegex = new javax.swing.JCheckBox();
        addWordButton = new javax.swing.JButton();
        addWordField = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        keywordTable = new javax.swing.JTable();
        searchButton = new javax.swing.JButton();
        curListValLabel = new javax.swing.JLabel();
        importButton = new javax.swing.JButton();

        setPreferredSize(new java.awt.Dimension(345, 534));
        org.openide.awt.Mnemonics.setLocalizedText(cutMenuItem, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(copyMenuItem, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(pasteMenuItem, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(400, 612));

        mainScrollPane.setPreferredSize(new java.awt.Dimension(345, 534));

        mainPanel.setPreferredSize(new java.awt.Dimension(345, 534));

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedNameLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.filesIndexedNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedValLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.filesIndexedValLabel.text")); // NOI18N

        titleLabel.setFont(new java.awt.Font("Tahoma", 0, 12));
        org.openide.awt.Mnemonics.setLocalizedText(titleLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.titleLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(curListNameLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.curListNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(loadListButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.loadListButton.text")); // NOI18N
        loadListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadListButtonActionPerformed(evt);
            }
        });

        tablePanel.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));

        org.openide.awt.Mnemonics.setLocalizedText(saveListButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.saveListButton.text")); // NOI18N
        saveListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveListButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteWordButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.deleteWordButton.text")); // NOI18N
        deleteWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteWordButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteAllWordsButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.deleteAllWordsButton.text")); // NOI18N
        deleteAllWordsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteAllWordsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(chRegex, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.chRegex.text")); // NOI18N
        chRegex.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chRegexActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(addWordButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.addWordButton.text")); // NOI18N
        addWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addWordButtonActionPerformed(evt);
            }
        });

        addWordField.setText(org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.addWordField.text")); // NOI18N
        addWordField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addWordFieldActionPerformed(evt);
            }
        });

        keywordTable.setModel(tableModel);
        keywordTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);
        keywordTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(keywordTable);

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout tablePanelLayout = new javax.swing.GroupLayout(tablePanel);
        tablePanel.setLayout(tablePanelLayout);
        tablePanelLayout.setHorizontalGroup(
            tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tablePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tablePanelLayout.createSequentialGroup()
                        .addComponent(deleteWordButton)
                        .addGap(18, 18, 18)
                        .addComponent(deleteAllWordsButton)
                        .addGap(18, 18, 18)
                        .addComponent(saveListButton))
                    .addGroup(tablePanelLayout.createSequentialGroup()
                        .addGap(35, 35, 35)
                        .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(tablePanelLayout.createSequentialGroup()
                                .addGap(10, 10, 10)
                                .addComponent(chRegex)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(addWordButton))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(searchButton))
                .addContainerGap(21, Short.MAX_VALUE))
        );
        tablePanelLayout.setVerticalGroup(
            tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(tablePanelLayout.createSequentialGroup()
                .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tablePanelLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(addWordButton)
                            .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(34, 34, 34))
                    .addGroup(tablePanelLayout.createSequentialGroup()
                        .addContainerGap(31, Short.MAX_VALUE)
                        .addComponent(chRegex)
                        .addGap(14, 14, 14)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 210, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(tablePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(deleteWordButton)
                        .addComponent(deleteAllWordsButton))
                    .addComponent(saveListButton))
                .addGap(18, 18, 18)
                .addComponent(searchButton)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(curListValLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.curListValLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(importButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.importButton.text")); // NOI18N
        importButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(titleLabel)
                        .addGroup(mainPanelLayout.createSequentialGroup()
                            .addGap(58, 58, 58)
                            .addComponent(loadListButton)
                            .addGap(27, 27, 27)
                            .addComponent(importButton))
                        .addGroup(mainPanelLayout.createSequentialGroup()
                            .addGap(11, 11, 11)
                            .addComponent(curListNameLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(curListValLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 242, Short.MAX_VALUE))
                        .addComponent(tablePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(filesIndexedNameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filesIndexedValLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 204, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 38, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(54, 54, 54))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(mainPanelLayout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addComponent(titleLabel)
                .addGap(18, 18, 18)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(importButton)
                    .addComponent(loadListButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tablePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(curListNameLabel)
                    .addComponent(curListValLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filesIndexedNameLabel)
                    .addComponent(filesIndexedValLabel))
                .addContainerGap(60, Short.MAX_VALUE))
        );

        mainScrollPane.setViewportView(mainPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 386, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 568, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    }//GEN-LAST:event_searchButtonActionPerformed

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

        if (deleteWordButton.isEnabled() == false) {
            if (!getSelectedKeywords().isEmpty()) {
                deleteWordButton.setEnabled(true);
            }
        }

        if (!getAllKeywords().isEmpty()) {
            deleteAllWordsButton.setEnabled(true);
            saveListButton.setEnabled(true);
        }


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
        curListValLabel.setText(listName);
        KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> saved", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);


    }//GEN-LAST:event_saveListButtonActionPerformed

    private void chRegexActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chRegexActionPerformed
    }//GEN-LAST:event_chRegexActionPerformed

    private void deleteWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteWordButtonActionPerformed
        tableModel.deleteSelected();

        if (getSelectedKeywords().isEmpty()) {
            deleteWordButton.setEnabled(false);
        }

        if (getAllKeywords().isEmpty()) {
            deleteAllWordsButton.setEnabled(false);
            saveListButton.setEnabled(false);
        }

    }//GEN-LAST:event_deleteWordButtonActionPerformed

    private void deleteAllWordsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllWordsButtonActionPerformed
        tableModel.deleteAll();

        deleteWordButton.setEnabled(false);
        deleteAllWordsButton.setEnabled(false);
        saveListButton.setEnabled(false);
    }//GEN-LAST:event_deleteAllWordsButtonActionPerformed

    private void loadListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadListButtonActionPerformed

        final String FEATURE_NAME = "Load Keyword List";

        KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

        final String listName = showLoadDeleteListDialog(FEATURE_NAME, loader.getListNames().toArray(), currentKeywordList, true);

        if (listName == null || listName.equals("")) {
            return;
        }
        currentKeywordList = listName;
        tableModel.resync(currentKeywordList);
        curListValLabel.setText(listName);
        initButtons();
        KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> loaded", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);


    }//GEN-LAST:event_loadListButtonActionPerformed

    private void importButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importButtonActionPerformed
        //delegate to lists component
        KeywordSearchListImportExportTopComponent lists = new KeywordSearchListImportExportTopComponent();
        if (lists != null) {
            lists.importButtonAction(evt);
        }

    }//GEN-LAST:event_importButtonActionPerformed

    private void addWordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordFieldActionPerformed
        addWordButtonActionPerformed(evt);
    }//GEN-LAST:event_addWordFieldActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addWordButton;
    private javax.swing.JTextField addWordField;
    private javax.swing.JCheckBox chRegex;
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JLabel curListNameLabel;
    private javax.swing.JLabel curListValLabel;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JButton deleteAllWordsButton;
    private javax.swing.JButton deleteWordButton;
    private javax.swing.JLabel filesIndexedNameLabel;
    private javax.swing.JLabel filesIndexedValLabel;
    private javax.swing.JButton importButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable keywordTable;
    private javax.swing.JButton loadListButton;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JScrollPane mainScrollPane;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JButton saveListButton;
    private javax.swing.JButton searchButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JPanel tablePanel;
    private javax.swing.JLabel titleLabel;
    // End of variables declaration//GEN-END:variables
    private JComboBox loadListCombo;
    
    private JComboBox findDialogComponent(Component component) {
        if (component instanceof JComboBox) {
            loadListCombo = (JComboBox) component;
        } else if (component instanceof JPanel) {
            for (Component c : ((JPanel) component).getComponents()) {
                findDialogComponent(c);
            }
        } else if (component instanceof JOptionPane) {
            for (Component c : ((JOptionPane) component).getComponents()) {
                findDialogComponent(c);
            }

        }
        return loadListCombo;
    }

    private String showLoadDeleteListDialog(final String title, Object[] choices, Object initialChoice, boolean deleteOption) {
        if (deleteOption) {
            //custom JOptionPane with right click to delete list
            //TODO custom component might be better, than customizing a prefab component
            final JOptionPane loadPane = new JOptionPane("Keyword list to load (right-click to delete):", JOptionPane.PLAIN_MESSAGE,
                    JOptionPane.OK_CANCEL_OPTION, null,
                    null, null);

            loadPane.setWantsInput(true);
            loadPane.setSelectionValues(choices);
            loadPane.setInitialSelectionValue(initialChoice);

            final JDialog loadDialog = loadPane.createDialog(null, title);
            final JPopupMenu rightClickMenu = new JPopupMenu();

            final MouseListener rightClickListener = new MouseListener() {

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON3) {
                        rightClickMenu.show(loadPane, e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                }

                @Override
                public void mouseExited(MouseEvent e) {
                }

                @Override
                public void mousePressed(MouseEvent e) {
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    rightClickMenu.setVisible(false);
                }
            };
            JMenuItem delItem = new JMenuItem("Delete List");

            delItem.addActionListener(new ActionListener() {

                JComboBox combo;

                @Override
                public void actionPerformed(ActionEvent e) {

                    String selList = null;
                    //there is no JOptionPane API to get current from combobox before OK is pressed
                    //workaround traversing the widgets
                    combo = findDialogComponent(loadPane);

                    if (combo != null) {
                        selList = (String) combo.getSelectedItem();
                    }

                    if (selList != null && selList != JOptionPane.UNINITIALIZED_VALUE) {
                        KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();
                        boolean deleted = loader.deleteList(selList);
                        if (deleted) {
                            Object[] choices = loader.getListNames().toArray();
                            loadPane.setSelectionValues(choices);
                            if (choices.length > 0) {
                                loadPane.setInitialSelectionValue(choices[0]);
                            }
                            loadPane.selectInitialValue();
                            combo = findDialogComponent(loadPane);
                            combo.addMouseListener(rightClickListener);
                            KeywordSearchUtil.displayDialog(title, "Keyword List <" + selList + "> deleted", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                        }
                    }
                    rightClickMenu.setVisible(false);
                }
            });

            rightClickMenu.add(delItem);

            JComboBox combo = findDialogComponent(loadPane);
            combo.addMouseListener(rightClickListener);

            loadPane.selectInitialValue();
            loadDialog.setVisible(true);
            loadDialog.dispose();
            String retString = (String) loadPane.getInputValue();
            if (retString == JOptionPane.UNINITIALIZED_VALUE) //no choice was made
            {
                retString = null;
            }

            return retString;
        } else {
            return (String) JOptionPane.showInputDialog(
                    null,
                    "Keyword list to load:",
                    title,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    choices,
                    initialChoice);

        }
    }

    @Override
    public void componentOpened() {
    }

    @Override
    public void componentClosed() {
    }

    void writeProperties(java.util.Properties p) {
        p.setProperty("version", "1.0");
    }

    void readProperties(java.util.Properties p) {
    }

    @Override
    public boolean isMultiwordQuery() {
        return true;
    }

    @Override
    public void addSearchButtonListener(ActionListener l) {
        searchButton.addActionListener(l);
    }

    @Override
    public String getQueryText() {
        return null;
    }

    @Override
    public List<Keyword> getQueryList() {
        return getAllKeywords();
    }

    @Override
    public boolean isLuceneQuerySelected() {
        return false;
    }

    @Override
    public boolean isRegexQuerySelected() {
        return true;
    }

    @Override
    public void setFilesIndexed(int filesIndexed) {
        filesIndexedValLabel.setText(Integer.toString(filesIndexed));
        if (filesIndexed == 0) {
            searchButton.setEnabled(false);
        } else {
            searchButton.setEnabled(true);
        }
    }

    List<Keyword> getAllKeywords() {
        return tableModel.getAllKeywords();
    }

    List<Keyword> getSelectedKeywords() {
        return tableModel.getSelectedKeywords();
    }

    private boolean keywordExists(Keyword keyword) {
        return tableModel.keywordExists(keyword);
    }

    private class KeywordTableModel extends AbstractTableModel {
        //data

        private Set<TableEntry> keywordData = new TreeSet<TableEntry>();

        @Override
        public int getColumnCount() {
            return 3;
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
                    colName = "RegEx.";
                    break;
                case 2:
                    colName = "Sel.";
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
                case 2:
                    ret = (Object) entry.isActive;
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: " + columnIndex);
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2 ? true : false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 2) {
                TableEntry entry = null;
                //iterate until row
                Iterator<TableEntry> it = keywordData.iterator();
                for (int i = 0; i <= rowIndex && it.hasNext(); ++i) {
                    entry = it.next();
                }
                if (entry != null)
                    entry.isActive = (Boolean) aValue;
                if (getSelectedKeywords().isEmpty()) {
                    deleteWordButton.setEnabled(false);
                } else {
                    deleteWordButton.setEnabled(true);
                }
            }
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

        List<Keyword> getSelectedKeywords() {
            List<Keyword> ret = new ArrayList<Keyword>();
            for (TableEntry e : keywordData) {
                if (e.isActive && !e.keyword.equals("")) {
                    ret.add(new Keyword(e.keyword, e.isLiteral));
                }
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

        void deleteSelected() {
            List<TableEntry> toDel = new ArrayList<TableEntry>();

            for (TableEntry e : keywordData) {
                if (e.isActive && !e.keyword.equals("")) {
                    toDel.add(e);
                }
            }
            for (TableEntry del : toDel) {
                keywordData.remove(del);
            }
            fireTableDataChanged();

        }

        class TableEntry implements Comparable {

            String keyword;
            Boolean isLiteral;
            Boolean isActive;

            TableEntry(Keyword keyword, Boolean isActive) {
                this.keyword = keyword.getQuery();
                this.isLiteral = keyword.isLiteral();
                this.isActive = isActive;
            }

            TableEntry(Keyword keyword) {
                this.keyword = keyword.getQuery();
                this.isLiteral = keyword.isLiteral();
                this.isActive = false;
            }
            
            TableEntry(String keyword, Boolean isLiteral) {
                this.keyword = keyword;
                this.isLiteral = isLiteral;
                this.isActive = false;
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
