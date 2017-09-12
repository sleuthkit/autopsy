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

import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JFrame;
import javax.swing.table.DefaultTableModel;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class allow a user to select an existing hash database and
 * add it to the set of hash databases used to classify files as unknown, known,
 * or known bad.
 */
final class ManageTagsDialog extends javax.swing.JDialog {

    private static final Logger LOGGER = Logger.getLogger(ManageTagsDialog.class.getName());

    /**
     * Displays a dialog that allows a user to select an existing hash database
     * and add it to the set of hash databases used to classify files as
     * unknown, known, or known bad.
     */
    @Messages({"ManageTagDialog.title=Manage Tags",
        "ManageTagDialog.tagInfo.text1=-Additional tags can be created in the tags options panel.",
        "ManageTagDialog.tagInfo.text2=-Checking 'Implies Known Bad' for a tag name will give you the option of marking everyting with that tag in the current case as bad in the central repository.",
        "ManageTagDialog.tagInfo.text3=-Un-checking 'Implies Known Bad' for a tag name will not have an effect on the central repository.",
        "ManageTagDialog.tagInfo.text4=-Tagging an item with a tag which has 'Implies Known Bad' selected will flag the item as bad in the central repository.",
        "ManageTagDialog.tagInfo.text5=-Untagging an item will remove the bad status for that item from the central repository if there are no other known bad tags on that item."})
    ManageTagsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.ManageTagDialog_title(),
                true); // NON-NLS
        initComponents();
        customizeComponents();
        setupHelpTextArea();
        display();
    }

    private void setupHelpTextArea() {
        helpTextArea.setText(Bundle.ManageTagDialog_tagInfo_text1());
        helpTextArea.append("\n");
        helpTextArea.append(Bundle.ManageTagDialog_tagInfo_text2());
        helpTextArea.append("\n");
        helpTextArea.append(Bundle.ManageTagDialog_tagInfo_text3());
        helpTextArea.append("\n");
        helpTextArea.append(Bundle.ManageTagDialog_tagInfo_text4());
        helpTextArea.append("\n");
        helpTextArea.append(Bundle.ManageTagDialog_tagInfo_text5());
    }

    @Messages({"ManageTagsDialog.init.failedConnection.msg=Cannot connect to Central Repository.",
        "ManageTagsDialog.init.failedGettingTags.msg=Unable to retrieve list of tags."})
    private void customizeComponents() {
        lbWarnings.setText("");
        EamDb dbManager;
        try {
            dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database.");
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedConnection_msg());
            return;
        }
        List<String> badTags = dbManager.getBadTags();

        List<String> tagNames = new ArrayList<>(badTags);
        try {
            tagNames.addAll(
                    TagsManager.getTagDisplayNames()
                    .stream()
                    .filter(tagName -> !badTags.contains(tagName))
                    .collect(Collectors.toList()));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Could not get list of tags in case", ex);
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedGettingTags_msg());
        }

        Collections.sort(tagNames);

        DefaultTableModel model = (DefaultTableModel) tblTagNames.getModel();
        for (String tagName : tagNames) {
            boolean enabled = badTags.contains(tagName);
            model.addRow(new Object[]{tagName, enabled});
        }
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
                "Tag", "Implies Known Bad"
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

        helpTextArea.setEditable(false);
        helpTextArea.setBackground(new java.awt.Color(240, 240, 240));
        helpTextArea.setColumns(20);
        helpTextArea.setLineWrap(true);
        helpTextArea.setRows(5);
        helpTextArea.setWrapStyleWord(true);
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
                .addComponent(helpScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 166, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tagScrollArea, javax.swing.GroupLayout.DEFAULT_SIZE, 233, Short.MAX_VALUE)
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
            dbManager.setBadTags(badTags);
            dbManager.saveSettings();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to connect to Central Repository database."); // NON-NLS
            lbWarnings.setText(Bundle.ManageTagsDialog_init_failedConnection_msg());
            return false;
        }
        return true;
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
