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
package org.sleuthkit.autopsy.actions;

import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.table.AbstractTableModel;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

public class GetTagNameDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String TAG_ICON_PATH = "org/sleuthkit/autopsy/images/tag-folder-blue-icon-16.png"; //NON-NLS
    private final Map<String, TagName> tagNamesMap = new TreeMap<>();
    private TagName tagName = null;

    /**
     * Show the Tag Name Dialog and return the TagName selected by the user. The
     * dialog will be centered with the main autopsy window as its owner. To set
     * another window as the owner use doDialog(Window) instead.
     *
     * @return a TagName instance selected by the user, or null if the user
     *         canceled the dialog.
     */
    public static TagName doDialog() {
        return doDialog(WindowManager.getDefault().getMainWindow());
    }

    /**
     * Show the Tag Name Dialog and return the TagName selected by the user.
     *
     * @param owner the window that will be the owner of the dialog. The dialog
     *              will be centered over this window and will block the rest of
     *              the application.
     *
     * @return a TagName instance selected by the user, or null if the user
     *         canceled the dialog.
     */
    public static TagName doDialog(Window owner) {
        GetTagNameDialog dialog = new GetTagNameDialog(owner);
        dialog.display();
        return dialog.tagName;
    }

    private GetTagNameDialog(Window owner) {
        super(owner, 
                NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.createTag"),
                ModalityType.APPLICATION_MODAL);
    }

    private void display() {
        setIconImage(ImageUtilities.loadImage(TAG_ICON_PATH));
        initComponents();

        // Set up the dialog to close when Esc is pressed.
        String cancelName = NbBundle.getMessage(this.getClass(), "GetTagNameDialog.cancelName");
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);
        ActionMap actionMap = getRootPane().getActionMap();
        actionMap.put(cancelName, new AbstractAction() {
            private static final long serialVersionUID = 1L;
            
            @Override
            public void actionPerformed(ActionEvent e) {
                cancelButtonActionPerformed(e);
            }
        });

        // Get the current set of tag names and hash them for a speedy lookup in
        // case the user chooses an existing tag name from the tag names table.
        TagsManager tagsManager = Case.getCurrentCase().getServices().getTagsManager();
        try {
            tagNamesMap.putAll(tagsManager.getDisplayNamesToTagNamesMap());
        } catch (TskCoreException ex) {
            Logger.getLogger(GetTagNameDialog.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
        }

        // Populate the tag names table.
        tagsTable.setModel(new TagsTableModel(new ArrayList<>(tagNamesMap.keySet())));
        tagsTable.setTableHeader(null);
        tagsTable.setCellSelectionEnabled(false);
        tagsTable.setFocusable(false);
        tagsTable.setRowHeight(tagsTable.getRowHeight() + 5);

        // Center and show the dialog box. 
        this.setLocationRelativeTo(this.getOwner());
        setVisible(true);        
    }
    
    private class TagsTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private final ArrayList<String> tagDisplayNames = new ArrayList<>();

        TagsTableModel(List<String> tagDisplayNames) {
            for (String tagDisplayName : tagDisplayNames) {
                this.tagDisplayNames.add(tagDisplayName);
            }
        }

        @Override
        public int getRowCount() {
            return tagDisplayNames.size();
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public String getValueAt(int rowIndex, int columnIndex) {
            return tagDisplayNames.get(rowIndex);
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tagsTable = new javax.swing.JTable();
        preexistingLabel = new javax.swing.JLabel();
        newTagPanel = new javax.swing.JPanel();
        tagNameLabel = new javax.swing.JLabel();
        tagNameField = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        jScrollPane1.setBackground(new java.awt.Color(255, 255, 255));

        tagsTable.setBackground(new java.awt.Color(240, 240, 240));
        tagsTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        tagsTable.setShowHorizontalLines(false);
        tagsTable.setShowVerticalLines(false);
        tagsTable.setTableHeader(null);
        jScrollPane1.setViewportView(tagsTable);

        org.openide.awt.Mnemonics.setLocalizedText(preexistingLabel, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.preexistingLabel.text")); // NOI18N

        newTagPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.newTagPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(tagNameLabel, org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.tagNameLabel.text")); // NOI18N

        tagNameField.setText(org.openide.util.NbBundle.getMessage(GetTagNameDialog.class, "GetTagNameDialog.tagNameField.text")); // NOI18N
        tagNameField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                tagNameFieldKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout newTagPanelLayout = new javax.swing.GroupLayout(newTagPanel);
        newTagPanel.setLayout(newTagPanelLayout);
        newTagPanelLayout.setHorizontalGroup(
            newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newTagPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tagNameLabel)
                .addGap(36, 36, 36)
                .addComponent(tagNameField, javax.swing.GroupLayout.DEFAULT_SIZE, 235, Short.MAX_VALUE)
                .addContainerGap())
        );
        newTagPanelLayout.setVerticalGroup(
            newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(newTagPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(newTagPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tagNameLabel)
                    .addComponent(tagNameField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(164, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(preexistingLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 233, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cancelButton))
                    .addComponent(newTagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(preexistingLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                    .addComponent(newTagPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        tagName = null;
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        String tagDisplayName = tagNameField.getText();
        if (tagDisplayName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(this.getClass(),
                            "GetTagNameDialog.mustSupplyTtagName.msg"),
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.tagNameErr"),
                    JOptionPane.ERROR_MESSAGE);
        } else if (TagsManager.containsIllegalCharacters(tagDisplayName)) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.illegalChars.msg"),
                    NbBundle.getMessage(this.getClass(), "GetTagNameDialog.illegalCharsErr"),
                    JOptionPane.ERROR_MESSAGE);
        } else {
            tagName = tagNamesMap.get(tagDisplayName);
            if (tagName == null) {
                try {
                    tagName = Case.getCurrentCase().getServices().getTagsManager().addTagName(tagDisplayName);
                    dispose();
                } catch (TskCoreException ex) {
                    Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, "Error adding " + tagDisplayName + " tag name", ex); //NON-NLS
                    JOptionPane.showMessageDialog(null,
                            NbBundle.getMessage(this.getClass(),
                                    "GetTagNameDialog.unableToAddTagNameToCase.msg",
                                    tagDisplayName),
                            NbBundle.getMessage(this.getClass(), "GetTagNameDialog.taggingErr"),
                            JOptionPane.ERROR_MESSAGE);
                    tagName = null;
                } catch (TagsManager.TagNameAlreadyExistsException ex) {
                    try {
                        tagName = Case.getCurrentCase().getServices().getTagsManager().getDisplayNamesToTagNamesMap().get(tagDisplayName);
                    } catch (TskCoreException ex1) {
                        Logger.getLogger(AddTagAction.class.getName()).log(Level.SEVERE, tagDisplayName + " exists in database but an error occurred in retrieving it.", ex1); //NON-NLS
                        JOptionPane.showMessageDialog(null,
                                NbBundle.getMessage(this.getClass(),
                                        "GetTagNameDialog.tagNameExistsTskCore.msg",
                                        tagDisplayName),
                                NbBundle.getMessage(this.getClass(), "GetTagNameDialog.dupTagErr"),
                                JOptionPane.ERROR_MESSAGE);
                        tagName = null;
                    }
                }
            } else {
                dispose();
            }
        }
    }//GEN-LAST:event_okButtonActionPerformed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            okButtonActionPerformed(null);
        }
    }//GEN-LAST:event_formKeyReleased

    private void tagNameFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_tagNameFieldKeyReleased
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            okButtonActionPerformed(null);
        }
    }//GEN-LAST:event_tagNameFieldKeyReleased

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JPanel newTagPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JLabel preexistingLabel;
    private javax.swing.JTextField tagNameField;
    private javax.swing.JLabel tagNameLabel;
    private javax.swing.JTable tagsTable;
    // End of variables declaration//GEN-END:variables

}
