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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;

final class GlobalListSettingsPanel extends javax.swing.JPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;

    private final GlobalListsManagementPanel listsManagementPanel = new GlobalListsManagementPanel(this);
    private final GlobalEditListPanel editListPanel = new GlobalEditListPanel();

    GlobalListSettingsPanel() {
        initComponents();
        customizeComponents();
        setName(org.openide.util.NbBundle.getMessage(DropdownToolbar.class, "ListBundleConfig"));
    }

    private void customizeComponents() {
        listsManagementPanel.addListSelectionListener(editListPanel);
        editListPanel.addDeleteButtonActionPerformed(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (KeywordSearchUtil.displayConfirmDialog(NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.title"), NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.body"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN)) {
                    String toDelete = editListPanel.getCurrentKeywordList().getName();
                    editListPanel.setCurrentKeywordList(null);
                    editListPanel.setButtonStates();
                    XmlKeywordSearchList deleter = XmlKeywordSearchList.getCurrent();
                    deleter.deleteList(toDelete);
                    listsManagementPanel.resync();
                }
            }
        });

        editListPanel.addSaveButtonActionPerformed(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final String FEATURE_NAME = NbBundle.getMessage(this.getClass(),
                        "KeywordSearchGlobalListSettingsPanel.component.featureName.text");
                KeywordList currentKeywordList = editListPanel.getCurrentKeywordList();

                List<Keyword> keywords = currentKeywordList.getKeywords();
                if (keywords.isEmpty()) {
                    KeywordSearchUtil.displayDialog(FEATURE_NAME, NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.keywordListEmptyErr"),
                            KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                    return;
                }

                String listName = (String) JOptionPane.showInputDialog(
                        null,
                        NbBundle.getMessage(this.getClass(), "KeywordSearch.newKwListTitle"),
                        FEATURE_NAME,
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        null,
                        currentKeywordList.getName());
                if (listName == null || listName.trim().equals("")) {
                    return;
                }

                XmlKeywordSearchList writer = XmlKeywordSearchList.getCurrent();
                if (writer.listExists(listName) && writer.getList(listName).isEditable()) {
                    KeywordSearchUtil.displayDialog(FEATURE_NAME, NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.noOwDefaultMsg"), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
                    return;
                }
                boolean shouldAdd = false;
                if (writer.listExists(listName)) {
                    boolean replace = KeywordSearchUtil.displayConfirmDialog(FEATURE_NAME, NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.kwListExistMsg", listName),
                            KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
                    if (replace) {
                        shouldAdd = true;
                    }

                } else {
                    shouldAdd = true;
                }

                if (shouldAdd) {
                    writer.addList(listName, keywords);
                    KeywordSearchUtil.displayDialog(FEATURE_NAME, NbBundle.getMessage(this.getClass(), "KeywordSearchConfigurationPanel1.customizeComponents.kwListSavedMsg", listName), KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                }

                listsManagementPanel.resync();
            }
        });

        mainSplitPane.setLeftComponent(listsManagementPanel);
        mainSplitPane.setRightComponent(editListPanel);
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        listsManagementPanel.addPropertyChangeListener(l);
        editListPanel.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        listsManagementPanel.removePropertyChangeListener(l);
        editListPanel.removePropertyChangeListener(l);
    }

    @Override
    public void store() {
        XmlKeywordSearchList.getCurrent().save(false);
        //refresh the list viewer/searcher panel
        DropdownListSearchPanel.getDefault().resync();
    }

    @Override
    public void load() {
        listsManagementPanel.load();
    }

    /**
     * Set the keyboard focus to new keyword textbox.
     */
    void setFocusOnKeywordTextBox() {
        editListPanel.setFocusOnKeywordTextBox();
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
        mainSplitPane = new javax.swing.JSplitPane();
        leftPanel = new javax.swing.JPanel();
        rightPanel = new javax.swing.JPanel();

        mainSplitPane.setBorder(null);
        mainSplitPane.setDividerLocation(275);
        mainSplitPane.setDividerSize(1);

        javax.swing.GroupLayout leftPanelLayout = new javax.swing.GroupLayout(leftPanel);
        leftPanel.setLayout(leftPanelLayout);
        leftPanelLayout.setHorizontalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 275, Short.MAX_VALUE)
        );
        leftPanelLayout.setVerticalGroup(
            leftPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 327, Short.MAX_VALUE)
        );

        mainSplitPane.setLeftComponent(leftPanel);

        rightPanel.setPreferredSize(new java.awt.Dimension(360, 327));

        javax.swing.GroupLayout rightPanelLayout = new javax.swing.GroupLayout(rightPanel);
        rightPanel.setLayout(rightPanelLayout);
        rightPanelLayout.setHorizontalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 397, Short.MAX_VALUE)
        );
        rightPanelLayout.setVerticalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 327, Short.MAX_VALUE)
        );

        mainSplitPane.setRightComponent(rightPanel);

        jScrollPane1.setViewportView(mainSplitPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 675, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel leftPanel;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JPanel rightPanel;
    // End of variables declaration//GEN-END:variables
}
