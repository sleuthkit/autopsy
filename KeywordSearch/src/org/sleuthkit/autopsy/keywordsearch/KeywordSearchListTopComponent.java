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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;

/**
 * Top component which displays something.
 */
@ConvertAsProperties(dtd = "-//org.sleuthkit.autopsy.keywordsearch//KeywordSearchList//EN",
autostore = false)
@TopComponent.Description(preferredID = "KeywordSearchListTopComponent",
//iconBase="SET/PATH/TO/ICON/HERE", 
persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "output", openAtStartup = false)
@ActionID(category = "Window", id = "org.sleuthkit.autopsy.keywordsearch.KeywordSearchListTopComponent")
@ActionReference(path = "Menu/Window" /*, position = 333 */)
@TopComponent.OpenActionRegistration(displayName = "#CTL_KeywordSearchListAction",
preferredID = "KeywordSearchListTopComponent")
public final class KeywordSearchListTopComponent extends TopComponent implements KeywordSearchTopComponentInterface {

    private static Logger logger = Logger.getLogger(KeywordSearchListTopComponent.class.getName());
    private KeywordTableModel tableModel;
    private String currentKeywordList;

    public KeywordSearchListTopComponent() {
        tableModel = new KeywordTableModel();
        initComponents();
        customizeComponents();
        setName(NbBundle.getMessage(KeywordSearchListTopComponent.class, "CTL_KeywordSearchListTopComponent"));
        setToolTipText(NbBundle.getMessage(KeywordSearchListTopComponent.class, "HINT_KeywordSearchListTopComponent"));

    }

    private void customizeComponents() {
        chLiteralWord.setToolTipText("Literal word (auto-escape special characters)");
        addWordButton.setToolTipText(("Add a new word to the keyword search list"));
        addWordField.setToolTipText("Enter a new word or regex to search");

        loadListButton.setToolTipText("Load a new keyword list from file or delete an existing list");
        saveListButton.setToolTipText("Save the current keyword list to a file");
        searchButton.setToolTipText("Execute the keyword list search using the current list");
        deleteWordButton.setToolTipText("Delete selected keyword(s) from the list");
        deleteAllWordsButton.setToolTipText("Delete all keywords from the list (clear it)");

        keywordTable.setAutoscrolls(true);
        keywordTable.setTableHeader(null);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);

        keywordTable.getParent().setBackground(keywordTable.getBackground());

        //customize column witdhs
        keywordTable.setSize(260, 200);
        final int width = keywordTable.getSize().width;
        TableColumn column = null;
        for (int i = 0; i < 2; i++) {
            column = keywordTable.getColumnModel().getColumn(i);
            if (i == 1) {
                column.setPreferredWidth(((int) (width * 0.2)));
                //column.setCellRenderer(new CellTooltipRenderer());
            } else {
                column.setCellRenderer(new CellTooltipRenderer());
                column.setPreferredWidth(((int) (width * 0.75)));
            }
        }
        keywordTable.setCellSelectionEnabled(false);

        loadDefaultKeywords();

        if (KeywordSearchListsXML.getInstance().getNumberLists() == 0) {
            loadListButton.setEnabled(false);
        }
    }

    private void loadDefaultKeywords() {
        //some hardcoded keywords for testing

        //phone number
        tableModel.addKeyword("\\d\\d\\d[\\.-]\\d\\d\\d[\\.-]\\d\\d\\d\\d");
        tableModel.addKeyword("\\d{8,10}");
        tableModel.addKeyword("phone|fax");
        //IP address
        tableModel.addKeyword("(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])");
        //email
        tableModel.addKeyword("[e\\-]{0,2}mail");
        tableModel.addKeyword("[A-Z0-9._%-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}");
        //URL
        tableModel.addKeyword("ftp|sftp|ssh|http|https|www");
        //escaped literal word \d\d\d
        tableModel.addKeyword("\\Q\\d\\d\\d\\E");
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        searchButton = new javax.swing.JButton();
        filesIndexedNameLabel = new javax.swing.JLabel();
        filesIndexedValLabel = new javax.swing.JLabel();
        titleLabel = new javax.swing.JLabel();
        listLabel = new javax.swing.JLabel();
        addWordField = new javax.swing.JTextField();
        addWordLabel = new javax.swing.JLabel();
        addWordButton = new javax.swing.JButton();
        loadListButton = new javax.swing.JButton();
        deleteWordButton = new javax.swing.JButton();
        deleteAllWordsButton = new javax.swing.JButton();
        saveListButton = new javax.swing.JButton();
        chLiteralWord = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        keywordTable = new javax.swing.JTable();

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedNameLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.filesIndexedNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(filesIndexedValLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.filesIndexedValLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(titleLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.titleLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(listLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.listLabel.text")); // NOI18N

        addWordField.setText(org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.addWordField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addWordLabel, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.addWordLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(addWordButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.addWordButton.text")); // NOI18N
        addWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addWordButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(loadListButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.loadListButton.text")); // NOI18N
        loadListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadListButtonActionPerformed(evt);
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

        org.openide.awt.Mnemonics.setLocalizedText(saveListButton, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.saveListButton.text")); // NOI18N
        saveListButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveListButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(chLiteralWord, org.openide.util.NbBundle.getMessage(KeywordSearchListTopComponent.class, "KeywordSearchListTopComponent.chLiteralWord.text")); // NOI18N
        chLiteralWord.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chLiteralWordActionPerformed(evt);
            }
        });

        keywordTable.setModel(tableModel);
        keywordTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        keywordTable.setShowHorizontalLines(false);
        keywordTable.setShowVerticalLines(false);
        jScrollPane1.setViewportView(keywordTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(chLiteralWord)
                    .addComponent(titleLabel)
                    .addComponent(loadListButton)
                    .addComponent(addWordLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(deleteWordButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteAllWordsButton)
                        .addGap(18, 18, 18)
                        .addComponent(saveListButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(31, 31, 31)
                        .addComponent(addWordButton))
                    .addComponent(listLabel)
                    .addComponent(searchButton)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(filesIndexedNameLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(filesIndexedValLabel))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 272, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(15, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(titleLabel)
                .addGap(18, 18, 18)
                .addComponent(loadListButton)
                .addGap(19, 19, 19)
                .addComponent(addWordLabel)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addWordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(addWordButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(chLiteralWord)
                .addGap(9, 9, 9)
                .addComponent(listLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 220, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(28, 28, 28)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteWordButton)
                    .addComponent(deleteAllWordsButton)
                    .addComponent(saveListButton))
                .addGap(29, 29, 29)
                .addComponent(searchButton)
                .addGap(38, 38, 38)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filesIndexedNameLabel)
                    .addComponent(filesIndexedValLabel))
                .addGap(46, 46, 46))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
    }//GEN-LAST:event_searchButtonActionPerformed

    private void addWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addWordButtonActionPerformed

        String newWord = addWordField.getText();
        String newWordEscaped = Pattern.quote(newWord);

        if (newWord.trim().equals("")) {
            return;
        } else if (keywordExists(newWord) || keywordExists(newWordEscaped)) {
            KeywordSearchUtil.displayDialog("New Keyword Entry", "Keyword already exists in the list.", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            return;
        }

        String toAdd = null;
        if (chLiteralWord.isSelected()) {
            toAdd = newWordEscaped;
        } else {
            toAdd = newWord;
        }

        //check if valid
        boolean valid = true;
        try {
            Pattern.compile(toAdd);
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
        chLiteralWord.setSelected(false);
        tableModel.addKeyword(toAdd);
        addWordField.setText("");

    }//GEN-LAST:event_addWordButtonActionPerformed

    private void saveListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveListButtonActionPerformed
        final String FEATURE_NAME = "Save Keyword List";
        KeywordSearchListsXML writer = KeywordSearchListsXML.getInstance();

        String listName = (String) JOptionPane.showInputDialog(
                null,
                "New keyword list name:",
                FEATURE_NAME,
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentKeywordList != null ? currentKeywordList : "");
        if (listName == null || listName.equals("")) {
            return;
        }

        List<String> keywords = tableModel.getAllKeywords();
        boolean shouldWrite = false;
        boolean written = false;
        if (writer.listExists(listName)) {
            boolean replace = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME, "Keyword List <" + listName + "> already exists, do you want to replace it?",
                    KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            if (replace) {
                shouldWrite = true;
            }

        } else {
            shouldWrite = true;
        }

        if (shouldWrite) {
            writer.addList(listName, keywords);
            written = writer.save();
        }

        if (written) {
            currentKeywordList = listName;
            KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> saved", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
            //enable load button if it was previously disabled, as lists now exist
            if (loadListButton.isEnabled() == false) {
                loadListButton.setEnabled(true);
            }
        }
    }//GEN-LAST:event_saveListButtonActionPerformed

    private void chLiteralWordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chLiteralWordActionPerformed
    }//GEN-LAST:event_chLiteralWordActionPerformed

    private void deleteWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteWordButtonActionPerformed
        tableModel.deleteSelected();
    }//GEN-LAST:event_deleteWordButtonActionPerformed

    private void deleteAllWordsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteAllWordsButtonActionPerformed
        tableModel.deleteAll();
    }//GEN-LAST:event_deleteAllWordsButtonActionPerformed

    private void loadListButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadListButtonActionPerformed

        final String FEATURE_NAME = "Load Keyword List";

        KeywordSearchListsXML loader = KeywordSearchListsXML.getInstance();

        final String listName = showLoadDeleteListDialog(FEATURE_NAME, loader.getListNames().toArray(), currentKeywordList, true);

        if (listName == null || listName.equals("")) {
            return;
        }

        KeywordSearchList list = loader.getList(listName);
        if (list != null) {
            List<String> keywords = list.getKeywords();

            //TODO clear/append option ?
            tableModel.deleteAll();
            tableModel.addKeywords(keywords);
            currentKeywordList = listName;
            KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> loaded", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
        }

    }//GEN-LAST:event_loadListButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addWordButton;
    private javax.swing.JTextField addWordField;
    private javax.swing.JLabel addWordLabel;
    private javax.swing.JCheckBox chLiteralWord;
    private javax.swing.JButton deleteAllWordsButton;
    private javax.swing.JButton deleteWordButton;
    private javax.swing.JLabel filesIndexedNameLabel;
    private javax.swing.JLabel filesIndexedValLabel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable keywordTable;
    private javax.swing.JLabel listLabel;
    private javax.swing.JButton loadListButton;
    private javax.swing.JButton saveListButton;
    private javax.swing.JButton searchButton;
    private javax.swing.JLabel titleLabel;
    // End of variables declaration//GEN-END:variables

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
            //loadPane.setValue(initialChoice);

            final JDialog loadDialog = loadPane.createDialog(null, title);
            final JPopupMenu rightClickMenu = new JPopupMenu();
            JMenuItem delItem = new JMenuItem("Delete List");

            delItem.addActionListener(new ActionListener() {
                JComboBox combo;
                //find the combo component
                private JComboBox getDialogComponent(Component component) {
                    if (component instanceof JComboBox) {
                        combo = (JComboBox)component;
                    } else if (component instanceof JPanel) {
                        for (Component c : ((JPanel) component).getComponents()) {
                            getDialogComponent(c);
                        }
                    } else if (component instanceof JOptionPane) {
                        for (Component c : ((JOptionPane) component).getComponents()) {
                            getDialogComponent(c);
                        }

                    }
                    return combo;
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    //there is no JOptionPane API to get current from combobox before OK is pressed
                    //workaround traversing the widgets
                    String selList = null;
                    combo = getDialogComponent(loadPane);
                    if (combo != null) {
                        selList = (String) combo.getSelectedItem();
                    }


                    if (selList != null && selList != JOptionPane.UNINITIALIZED_VALUE) {
                        KeywordSearchListsXML loader = KeywordSearchListsXML.getInstance();
                        boolean deleted = loader.deleteList(selList);
                        if (deleted) {
                            Object[] choices = loader.getListNames().toArray();
                            loadPane.setSelectionValues(choices);
                            if (choices.length > 0) {
                                loadPane.setInitialSelectionValue(choices[0]);
                            }
                            loadPane.selectInitialValue();
                            KeywordSearchUtil.displayDialog(title, "Keyword List <" + selList + "> deleted", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                        }

                    }

                    rightClickMenu.setVisible(false);
                }
            });

            rightClickMenu.add(delItem);

            loadPane.addMouseListener(new MouseListener() {

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
            });



            loadPane.selectInitialValue();
            loadDialog.show();
            loadDialog.dispose();

            return (String) loadPane.getInputValue();
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
    public Map<String, Boolean> getQueryList() {
        List<String> selected = getSelectedKeywords();
        //filter out blank just in case
        Map<String, Boolean> ret = new LinkedHashMap<String, Boolean>();
        for (String s : selected) {
            if (!s.trim().equals("")) {
                //use false for isLiteral because we are currently escaping
                //the keyword earlier as it is stored
                //might need to change and pass isLiteral 
                //if the query object needs to treat it specially
                ret.put(s, false);
            }
        }
        return ret;
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

    public List<String> getAllKeywords() {
        return tableModel.getAllKeywords();
    }

    public List<String> getSelectedKeywords() {
        return tableModel.getSelectedKeywords();
    }

    private boolean keywordExists(String keyword) {

        return tableModel.keywordExists(keyword);
    }

    static class KeywordTableModel extends AbstractTableModel {

        private static Logger logger = Logger.getLogger(KeywordTableModel.class.getName());
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
            return columnIndex == 1 ? true : false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 1) {
                TableEntry entry = null;
                //iterate until row
                Iterator<TableEntry> it = keywordData.iterator();
                for (int i = 0; i <= rowIndex; ++i) {
                    entry = it.next();
                }
                entry.isActive = (Boolean) aValue;
            }
        }

        @Override
        public Class getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }

        List<String> getAllKeywords() {
            List<String> ret = new ArrayList<String>();
            for (TableEntry e : keywordData) {
                ret.add(e.keyword);
            }
            return ret;
        }

        List<String> getSelectedKeywords() {
            List<String> ret = new ArrayList<String>();
            for (TableEntry e : keywordData) {
                if (e.isActive && !e.keyword.equals("")) {
                    ret.add(e.keyword);
                }
            }
            return ret;
        }

        boolean keywordExists(String keyword) {
            List<String> all = getAllKeywords();
            return all.contains(keyword);
        }

        void addKeyword(String keyword) {
            if (!keywordExists(keyword)) {
                keywordData.add(new TableEntry(keyword));
            }
            fireTableDataChanged();
        }

        void addKeywords(List<String> keywords) {
            for (String keyword : keywords) {
                if (!keywordExists(keyword)) {
                    keywordData.add(new TableEntry(keyword));
                }
            }
            fireTableDataChanged();
        }

        void deleteAll() {
            keywordData.clear();
            fireTableDataChanged();
        }

        void deleteSelected() {
            List<TableEntry> toDel = new ArrayList<TableEntry>();
            int i = 0;
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
            Boolean isActive;

            TableEntry(String keyword, Boolean isActive) {
                this.keyword = keyword;
                this.isActive = isActive;
            }

            TableEntry(String keyword) {
                this.keyword = keyword;
                this.isActive = false;
            }

            @Override
            public int compareTo(Object o) {
                return this.keyword.compareTo(((TableEntry) o).keyword);
            }
        }
    }

    /**
     * tooltips that show entire query string
     */
    public static class CellTooltipRenderer extends DefaultTableCellRenderer {

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
