/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this class allow a user to select an existing hash database and
 * add it to the set of hash databases used to classify files as unknown, known,
 * or notable.
 */
final class ManageTagsDialog extends javax.swing.JDialog {

    private static final Logger LOGGER = Logger.getLogger(ManageTagsDialog.class.getName());

    /**
     * Displays a dialog that allows a user to select an existing hash database
     * and add it to the set of hash databases used to classify files as
     * unknown, known, or notable.
     */
    @Messages({"ManageTagDialog.title=Manage Tags",
        "ManageTagDialog.tagInfo.text=Select the tags that cause files and results to be recorded in the central repository. Additional tags can be created in the Tags options panel."})
    ManageTagsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.ManageTagDialog_title(),
                true); // NON-NLS
        initComponents();
        customizeComponents();
        setupHelpTextArea();
        display();
    }


    @Messages({"ManageTagsDialog.init.failedConnection.msg=Cannot connect to central cepository.",
        "ManageTagsDialog.init.failedGettingTags.msg=Unable to retrieve list of tags.",
        "ManageTagsDialog.tagColumn.header.text=Tags",
        "ManageTagsDialog.notableColumn.header.text=Notable"})
    private void setupHelpTextArea() {
        helpTextArea.setText(Bundle.ManageTagDialog_tagInfo_text());
    }

    private void customizeComponents() {
        lbWarnings.setText("");
        EamDb dbManager;
        try {
            dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to connect to central repository database.");
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedConnection_msg());
            return;
        }
        List<String> badTags = TagsManager.getNotableTagDisplayNames();

        List<String> tagNames = new ArrayList<>();
        try {
            tagNames.addAll(TagsManager.getTagDisplayNames());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Could not get list of tags in case", ex);
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedGettingTags_msg());
        }

        Collections.sort(tagNames);

        DefaultTableModel model = (DefaultTableModel) tblTagNames.getModel();
        model.setColumnIdentifiers(new String[] {Bundle.ManageTagsDialog_tagColumn_header_text(), Bundle.ManageTagsDialog_notableColumn_header_text()});
        for (String tagName : tagNames) {
            boolean enabled = badTags.contains(tagName);
            model.addRow(new Object[]{tagName, enabled});
        }
        CheckBoxModelListener listener = new CheckBoxModelListener(this);
        model.addTableModelListener(listener);
    }

    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        tagScrollArea = new javax.swing.JScrollPane();
        tblTagNames = new javax.swing.JTable();
        lbWarnings = new javax.swing.JLabel();
        helpScrollPane = new javax.swing.JScrollPane();
        helpTextArea = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(ManageTagsDialog.class, "ManageTagsDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(ManageTagsDialog.class, "ManageTagsDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        tblTagNames.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "", ""
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Object.class, java.lang.Boolean.class
            };
            boolean[] canEdit = new boolean [] {
                false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        tagScrollArea.setViewportView(tblTagNames);

        helpScrollPane.setBorder(null);

        helpTextArea.setEditable(false);
        helpTextArea.setBackground(new java.awt.Color(240, 240, 240));
        helpTextArea.setColumns(20);
        helpTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        helpTextArea.setLineWrap(true);
        helpTextArea.setRows(3);
        helpTextArea.setWrapStyleWord(true);
        helpTextArea.setBorder(null);
        helpTextArea.setFocusable(false);
        helpScrollPane.setViewportView(helpTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(helpScrollPane, javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tagScrollArea, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 328, Short.MAX_VALUE)
                            .addComponent(lbWarnings, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(2, 2, 2)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(helpScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(tagScrollArea, javax.swing.GroupLayout.DEFAULT_SIZE, 341, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbWarnings, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(okButton)
                    .addComponent(cancelButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed


    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        if (setBadTags()) {
            dispose();
        }
    }//GEN-LAST:event_okButtonActionPerformed

    private boolean setBadTags() {
        List<String> badTags = new ArrayList<>();

        DefaultTableModel model = (DefaultTableModel) tblTagNames.getModel();
        for (int i = 0; i < model.getRowCount(); ++i) {
            String tagName = (String) model.getValueAt(i, 0);
            boolean enabled = (boolean) model.getValueAt(i, 1);

            if (enabled) {
                badTags.add(tagName);
            }
        }
        try {
            EamDb dbManager = EamDb.getInstance();
            dbManager.saveSettings();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to connect to central repository database."); // NON-NLS
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedConnection_msg());
            return false;
        }
        return true;
    }
    
    /**
     * If the user sets a tag to "Notable", give them the option to update
     * any existing tagged items (in the current case only) in the central repo.
     */
    public class CheckBoxModelListener implements TableModelListener {
        @Messages({"ManageTagsDialog.updateCurrentCase.msg=Mark as notable any files/results in the current case that have this tag?",
                    "ManageTagsDialog.updateCurrentCase.title=Update current case?",
                    "ManageTagsDialog.updateCurrentCase.error=Error updating existing central repository entries"})
        
        javax.swing.JDialog dialog;
        public CheckBoxModelListener(javax.swing.JDialog dialog){
            this.dialog = dialog;
        }
        
        @Override
        public void tableChanged(TableModelEvent e) {
            int row = e.getFirstRow();
            int column = e.getColumn();
            if (column == 1) {
                DefaultTableModel model = (DefaultTableModel) e.getSource();
                String tagName = (String) model.getValueAt(row, 0);
                Boolean checked = (Boolean) model.getValueAt(row, column);
                if (checked) {
                    
                    // Don't do anything if there's no case open
                    if(Case.isCaseOpen()){
                        int dialogButton = JOptionPane.YES_NO_OPTION;
                        int dialogResult = JOptionPane.showConfirmDialog (
                                null, 
                                Bundle.ManageTagsDialog_updateCurrentCase_msg(),
                                Bundle.ManageTagsDialog_updateCurrentCase_title(),
                                dialogButton);
                        if(dialogResult == JOptionPane.YES_OPTION){
                            try{
                                dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                setArtifactsKnownBadByTag(tagName, Case.getCurrentCase());
                            } catch (EamDbException ex) {
                                LOGGER.log(Level.SEVERE, "Failed to apply notable status to artifacts in current case", ex);
                                JOptionPane.showMessageDialog(null, Bundle.ManageTagsDialog_updateCurrentCase_error());
                            } finally {
                                dialog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Set knownBad status for all files/artifacts in the given case that
     * are tagged with the given tag name. 
     * Files/artifacts that are not already in the database will be added.
     * @param tagName The name of the tag to search for
     * @param curCase The case to search in
     */
    public void setArtifactsKnownBadByTag(String tagNameString, Case curCase) throws EamDbException{
        try{
            TagName tagName = curCase.getServices().getTagsManager().getDisplayNamesToTagNamesMap().get(tagNameString);
            
            // First find any matching artifacts
            List<BlackboardArtifactTag> artifactTags = curCase.getSleuthkitCase().getBlackboardArtifactTagsByTagName(tagName);                  
            
            for(BlackboardArtifactTag bbTag:artifactTags){
                List<CorrelationAttribute> convertedArtifacts = EamArtifactUtil.getCorrelationAttributeFromBlackboardArtifact(bbTag.getArtifact(), true, true);
                for (CorrelationAttribute eamArtifact : convertedArtifacts) {
                    EamDb.getInstance().setArtifactInstanceKnownStatus(eamArtifact,TskData.FileKnown.BAD);
                }
            }

            // Now search for files
            List<ContentTag> fileTags = curCase.getSleuthkitCase().getContentTagsByTagName(tagName);
            for(ContentTag contentTag:fileTags){
                final CorrelationAttribute eamArtifact = EamArtifactUtil.getEamArtifactFromContent(contentTag.getContent(), 
                            TskData.FileKnown.BAD, "");
                if(eamArtifact != null){
                    EamDb.getInstance().setArtifactInstanceKnownStatus(eamArtifact, TskData.FileKnown.BAD);
                }
            }
        } catch (TskCoreException ex){
            throw new EamDbException("Error updating artifacts", ex);
        }
        
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane helpScrollPane;
    private javax.swing.JTextArea helpTextArea;
    private javax.swing.JLabel lbWarnings;
    private javax.swing.JButton okButton;
    private javax.swing.JScrollPane tagScrollArea;
    private javax.swing.JTable tblTagNames;
    // End of variables declaration//GEN-END:variables
}
