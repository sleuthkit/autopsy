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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * GlobalEditListPanel widget to manage keywords in lists
 */
class GlobalEditListPanel extends javax.swing.JPanel implements ListSelectionListener, OptionsPanel {

    private static final Logger logger = Logger.getLogger(GlobalEditListPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final KeywordTableModel tableModel;
    private KeywordList currentKeywordList;

    /**
     * Creates new form GlobalEditListPanel
     */
    GlobalEditListPanel() {
        tableModel = new KeywordTableModel();
        initComponents();
        customizeComponents();
    }

    private void customizeComponents() {
        newKeywordsButton.setToolTipText((NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.customizeComponents.addWordToolTip")));
        deleteWordButton.setToolTipText(NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.customizeComponents.removeSelectedMsg"));

        keywordTable.getParent().setBackground(keywordTable.getBackground());
        final int width = jScrollPane1.getPreferredSize().width;
        keywordTable.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column;
        for (int i = 0; i < keywordTable.getColumnCount(); i++) {
            column = keywordTable.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.90)));
            } else {
                column.setPreferredWidth(((int) (width * 0.10)));
            }
        }
        keywordTable.setCellSelectionEnabled(false);
        keywordTable.setRowSelectionAllowed(true);

        final ListSelectionModel lsm = keywordTable.getSelectionModel();
        lsm.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                boolean canDelete = !(lsm.isSelectionEmpty() || currentKeywordList.isEditable() || IngestManager.getInstance().isIngestRunning());
                boolean canEdit = canDelete && (lsm.getMaxSelectionIndex() == lsm.getMinSelectionIndex()); //edit only enabled with single selection
                deleteWordButton.setEnabled(canDelete);
                editWordButton.setEnabled(canEdit);
            }
        });

        setButtonStates();

        IngestManager.getInstance().addIngestJobEventListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                Object source = evt.getSource();
                if (source instanceof String && ((String) source).equals("LOCAL")) { //NON-NLS
                    EventQueue.invokeLater(() -> {
                        setButtonStates();
                    });
                }
            }
        });
    }

    /**
     * Enables and disables buttons on this panel based on the current state.
     */
    void setButtonStates() {
        boolean isIngestRunning = IngestManager.getInstance().isIngestRunning();
        boolean isListSelected = currentKeywordList != null;

        // items that only need a selected list
        boolean canEditList = isListSelected && !isIngestRunning;
        ingestMessagesCheckbox.setEnabled(canEditList);
        ingestMessagesCheckbox.setSelected(currentKeywordList != null && currentKeywordList.getIngestMessages());

        // items that need an unlocked list w/out ingest running
        boolean canAddWord = canEditList && !currentKeywordList.isEditable();
        newKeywordsButton.setEnabled(canAddWord);

        // items that need a non-empty list
        if ((currentKeywordList == null) || (currentKeywordList.getKeywords().isEmpty())) {
            deleteWordButton.setEnabled(false);
            editWordButton.setEnabled(false);
        }
    }

    @NbBundle.Messages("GlobalEditListPanel.editKeyword.title=Edit Keyword")
    /**
     * Adds keywords to a keyword list, returns true if at least one keyword was
     * successfully added and no duplicates were found.
     *
     * @return - true or false
     */
    private boolean addKeywordsAction(String existingKeywords, boolean isLiteral, boolean isWholeWord) {
        String keywordsToRedisplay = existingKeywords;
        AddKeywordsDialog dialog = new AddKeywordsDialog();

        int goodCount = 0;
        int dupeCount = 0;
        int badCount = 1;  // Default to 1 so we enter the loop the first time

        if (!existingKeywords.isEmpty()) {  //if there is an existing keyword then this action was called by the edit button
            dialog.setTitle(NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.editKeyword.title"));
        }
        while (badCount > 0) {
            dialog.setInitialKeywordList(keywordsToRedisplay, isLiteral, isWholeWord);
            dialog.display();

            goodCount = 0;
            dupeCount = 0;
            badCount = 0;
            keywordsToRedisplay = "";

            if (!dialog.getKeywords().isEmpty()) {

                for (String newWord : dialog.getKeywords()) {
                    if (newWord.isEmpty()) {
                        continue;
                    }

                    final Keyword keyword = new Keyword(newWord, !dialog.isKeywordRegex(), dialog.isKeywordExact(), currentKeywordList.getName(), newWord);
                    if (currentKeywordList.hasKeyword(keyword)) {
                        dupeCount++;
                        continue;
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

                        // Invalid keywords will reappear in the UI
                        keywordsToRedisplay += newWord + "\n";
                        badCount++;
                        continue;
                    }

                    // Add the new keyword
                    tableModel.addKeyword(keyword);
                    goodCount++;
                }
                XmlKeywordSearchList.getCurrent().addList(currentKeywordList);
                firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);

                if ((badCount > 0) || (dupeCount > 0)) {
                    // Display the error counts to the user
                    // The add keywords dialog will pop up again if any were invalid with any 
                    // invalid entries (valid entries and dupes will disappear)

                    String summary = "";
                    KeywordSearchUtil.DIALOG_MESSAGE_TYPE level = KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO;
                    if (goodCount > 0) {
                        if (goodCount > 1) {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordsAddedPlural.text", goodCount) + "\n";
                        } else {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordsAdded.text", goodCount) + "\n";
                        }
                    }
                    if (dupeCount > 0) {
                        if (dupeCount > 1) {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordDupesSkippedPlural.text", dupeCount) + "\n";
                        } else {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordDupesSkipped.text", dupeCount) + "\n";
                        }
                        level = KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN;
                    }
                    if (badCount > 0) {
                        if (badCount > 1) {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordErrorsPlural.text", badCount) + "\n";
                        } else {
                            summary += NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.keywordErrors.text", badCount) + "\n";
                        }
                        level = KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR;
                    }
                    KeywordSearchUtil.displayDialog(NbBundle.getMessage(this.getClass(), "GlobalEditListPanel.addKeywordResults.text"),
                            summary, level);
                }
            }
        }
        setFocusOnKeywordTextBox();
        setButtonStates();
        return (goodCount >= 1 && dupeCount == 0);
    }

    /**
     * Remove one or more keywords from a keyword list.
     *
     * @param selectedKeywords the indices of the keywords you would like to
     *                         delete
     */
    private void deleteKeywordAction(int[] selectedKeywords) {
        tableModel.deleteSelected(selectedKeywords);
        XmlKeywordSearchList.getCurrent().addList(currentKeywordList);
        setButtonStates();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        listEditorPanel = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        keywordTable = new javax.swing.JTable();
        ingestMessagesCheckbox = new javax.swing.JCheckBox();
        keywordsLabel = new javax.swing.JLabel();
        newKeywordsButton = new javax.swing.JButton();
        deleteWordButton = new javax.swing.JButton();
        editWordButton = new javax.swing.JButton();

        setMinimumSize(new java.awt.Dimension(0, 0));

        listEditorPanel.setMinimumSize(new java.awt.Dimension(0, 0));

        jScrollPane1.setPreferredSize(new java.awt.Dimension(340, 300));

        keywordTable.setModel(tableModel);
        keywordTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        keywordTable.setGridColor(new java.awt.Color(153, 153, 153));
        keywordTable.setMaximumSize(new java.awt.Dimension(30000, 30000));
        keywordTable.getTableHeader().setReorderingAllowed(false);
        jScrollPane1.setViewportView(keywordTable);

        ingestMessagesCheckbox.setSelected(true);
        ingestMessagesCheckbox.setText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "KeywordSearchEditListPanel.ingestMessagesCheckbox.text")); // NOI18N
        ingestMessagesCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "KeywordSearchEditListPanel.ingestMessagesCheckbox.toolTipText")); // NOI18N
        ingestMessagesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ingestMessagesCheckboxActionPerformed(evt);
            }
        });

        keywordsLabel.setText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "KeywordSearchEditListPanel.keywordsLabel.text")); // NOI18N

        newKeywordsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/add16.png"))); // NOI18N
        newKeywordsButton.setText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.newKeywordsButton.text")); // NOI18N
        newKeywordsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newKeywordsButtonActionPerformed(evt);
            }
        });

        deleteWordButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/delete16.png"))); // NOI18N
        deleteWordButton.setText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "KeywordSearchEditListPanel.deleteWordButton.text")); // NOI18N
        deleteWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteWordButtonActionPerformed(evt);
            }
        });

        editWordButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/edit16.png"))); // NOI18N
        editWordButton.setText(org.openide.util.NbBundle.getMessage(GlobalEditListPanel.class, "GlobalEditListPanel.editWordButton.text")); // NOI18N
        editWordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editWordButtonActionPerformed(evt);
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
                        .addComponent(keywordsLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(listEditorPanelLayout.createSequentialGroup()
                                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ingestMessagesCheckbox)
                                    .addGroup(listEditorPanelLayout.createSequentialGroup()
                                        .addComponent(newKeywordsButton)
                                        .addGap(14, 14, 14)
                                        .addComponent(editWordButton)
                                        .addGap(14, 14, 14)
                                        .addComponent(deleteWordButton)))
                                .addGap(0, 0, Short.MAX_VALUE)))))
                .addContainerGap())
        );

        listEditorPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deleteWordButton, editWordButton, newKeywordsButton});

        listEditorPanelLayout.setVerticalGroup(
            listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, listEditorPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(keywordsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 257, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(listEditorPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteWordButton)
                    .addComponent(newKeywordsButton)
                    .addComponent(editWordButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ingestMessagesCheckbox)
                .addGap(9, 9, 9))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(listEditorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(listEditorPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(5, 5, 5))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void deleteWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteWordButtonActionPerformed
        if (KeywordSearchUtil.displayConfirmDialog(NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.removeKwMsg"),
                NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.deleteWordButtonActionPerformed.delConfirmMsg"),
                KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN)) {
            deleteKeywordAction(keywordTable.getSelectedRows());
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_deleteWordButtonActionPerformed

    private void ingestMessagesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ingestMessagesCheckboxActionPerformed
        currentKeywordList.setIngestMessages(ingestMessagesCheckbox.isSelected());
        XmlKeywordSearchList updater = XmlKeywordSearchList.getCurrent();
        updater.addList(currentKeywordList);
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_ingestMessagesCheckboxActionPerformed

    private void newKeywordsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newKeywordsButtonActionPerformed
        addKeywordsAction("", true, true);
    }//GEN-LAST:event_newKeywordsButtonActionPerformed

    private void editWordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editWordButtonActionPerformed
        int[] selectedKeywords = keywordTable.getSelectedRows();
        if (selectedKeywords.length == 1) {
            Keyword currentKeyword = currentKeywordList.getKeywords().get(selectedKeywords[0]);
            if (addKeywordsAction(currentKeyword.getSearchTerm(), currentKeyword.searchTermIsLiteral(), currentKeyword.searchTermIsWholeWord())) {
                deleteKeywordAction(selectedKeywords);
            }
        }
    }//GEN-LAST:event_editWordButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton deleteWordButton;
    private javax.swing.JButton editWordButton;
    private javax.swing.JCheckBox ingestMessagesCheckbox;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable keywordTable;
    private javax.swing.JLabel keywordsLabel;
    private javax.swing.JPanel listEditorPanel;
    private javax.swing.JButton newKeywordsButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void valueChanged(ListSelectionEvent e) {
        //respond to list selection changes in KeywordSearchListManagementPanel
        ListSelectionModel listSelectionModel = (ListSelectionModel) e.getSource();
        currentKeywordList = null;
        if (!listSelectionModel.isSelectionEmpty()) {
            XmlKeywordSearchList loader = XmlKeywordSearchList.getCurrent();
            if (listSelectionModel.getMinSelectionIndex() == listSelectionModel.getMaxSelectionIndex()) {
                currentKeywordList = loader.getListsL(false).get(listSelectionModel.getMinSelectionIndex());
            }
        }
        tableModel.resync();
        setButtonStates();
    }

    @Override
    public void store() {
        // Implemented by parent panel
    }

    @Override
    public void load() {
        // Implemented by parent panel
    }

    KeywordList getCurrentKeywordList() {
        return currentKeywordList;
    }

    void setCurrentKeywordList(KeywordList list) {
        currentKeywordList = list;
    }

    private class KeywordTableModel extends AbstractTableModel {

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
                    colName = NbBundle.getMessage(this.getClass(), "KeywordSearchEditListPanel.kwColName");
                    break;
                case 1:
                    colName = NbBundle.getMessage(this.getClass(), "KeywordSearch.typeColLbl");
                    break;
                default:
                    ;
            }
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            if (currentKeywordList == null) {
                return "";
            }
            Keyword word = currentKeywordList.getKeywords().get(rowIndex);
            switch (columnIndex) {
                case 0:
                    ret = word.getSearchTerm();
                    break;
                case 1:
                    ret = word.getSearchTermType();
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

        void addKeyword(Keyword keyword) {
            if (!currentKeywordList.hasKeyword(keyword)) {
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
            for (int arrayi = selected.length - 1; arrayi >= 0; arrayi--) {
                words.remove(selected[arrayi]);
            }
            resync();
        }
    }

    /**
     * Set the keyboard focus to new keyword textbox.
     */
    void setFocusOnKeywordTextBox() {
        newKeywordsButton.requestFocus();
    }
}
