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
 * KeywordSearchConfigurationPanel1.java
 *
 * Created on Feb 28, 2012, 4:12:47 PM
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import javax.swing.JOptionPane;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * Panel containing all other Keyword search Options panels.
 */
public class KeywordSearchConfigurationPanel1 extends javax.swing.JPanel implements OptionsPanel {

    KeywordSearchListsManagementPanel listsManagementPanel;
    KeywordSearchEditListPanel editListPanel;
    private static final Logger logger = Logger.getLogger(KeywordSearchConfigurationPanel1.class.getName());
    private static final String KEYWORD_CONFIG_NAME = org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "ListBundleConfig");
    
    /** Creates new form KeywordSearchConfigurationPanel1 */
    KeywordSearchConfigurationPanel1() {
        
        initComponents();
        customizeComponents();
        setName(KEYWORD_CONFIG_NAME);
    }

    private void customizeComponents() {
        listsManagementPanel = new KeywordSearchListsManagementPanel();
        editListPanel = new KeywordSearchEditListPanel();

        listsManagementPanel.addListSelectionListener(editListPanel);
        editListPanel.addDeleteButtonActionPerformed(new ActionListener() {
 
            @Override
            public void actionPerformed(ActionEvent e) {
                if (KeywordSearchUtil.displayConfirmDialog("Delete a keyword list"
                        , "This will delete the keyword list globally (for all Cases). "
                        + "Do you want to proceed with the deletion? "
                        , KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN) ) {

                    KeywordSearchListsXML deleter = KeywordSearchListsXML.getCurrent();
                    String toDelete = editListPanel.getCurrentKeywordList().getName();
                    editListPanel.setCurrentKeywordList(null);
                    editListPanel.initButtons();
                    deleter.deleteList(toDelete);
                    listsManagementPanel.resync();
                }
            }
        });
        
        editListPanel.addSaveButtonActionPerformed(new ActionListener() {
           
            @Override
            public void actionPerformed(ActionEvent e) {
                final String FEATURE_NAME = "Save Keyword List";
                KeywordSearchListsXML writer = KeywordSearchListsXML.getCurrent();
                KeywordSearchList currentKeywordList = editListPanel.getCurrentKeywordList();

                List<Keyword> keywords = currentKeywordList.getKeywords();
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
                        currentKeywordList != null ? currentKeywordList.getName() : "");
                if (listName == null || listName.trim().equals("")) {
                    return;
                }

                if (writer.listExists(listName) && writer.getList(listName).isLocked()) {
                    KeywordSearchUtil.displayDialog(FEATURE_NAME, "Cannot overwrite default list", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
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
                    KeywordSearchUtil.displayDialog(FEATURE_NAME, "Keyword List <" + listName + "> saved", KeywordSearchUtil.DIALOG_MESSAGE_TYPE.INFO);
                }

                //currentKeywordList = writer.getList(listName);
                
                listsManagementPanel.resync();
            }
        });
        
        mainSplitPane.setLeftComponent(listsManagementPanel);
        mainSplitPane.setRightComponent(editListPanel);
        mainSplitPane.revalidate();
        mainSplitPane.repaint();
    }
    
    @Override
    public void store() {
        KeywordSearchListsXML.getCurrent().save(false);
        //refresh the list viewer/searcher panel
        KeywordSearchListsViewerPanel.getDefault().resync();
    }
    
    @Override
    public void load() {
        listsManagementPanel.load();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mainSplitPane = new javax.swing.JSplitPane();
        leftPanel = new javax.swing.JPanel();
        rightPanel = new javax.swing.JPanel();

        mainSplitPane.setBorder(null);
        mainSplitPane.setDividerLocation(275);

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

        javax.swing.GroupLayout rightPanelLayout = new javax.swing.GroupLayout(rightPanel);
        rightPanel.setLayout(rightPanelLayout);
        rightPanelLayout.setHorizontalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 318, Short.MAX_VALUE)
        );
        rightPanelLayout.setVerticalGroup(
            rightPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 327, Short.MAX_VALUE)
        );

        mainSplitPane.setRightComponent(rightPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainSplitPane)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainSplitPane)
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel leftPanel;
    private javax.swing.JSplitPane mainSplitPane;
    private javax.swing.JPanel rightPanel;
    // End of variables declaration//GEN-END:variables
    
}
