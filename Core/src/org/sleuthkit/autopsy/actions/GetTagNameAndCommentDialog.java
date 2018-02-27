/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.HashSet;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.KeyStroke;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

public class GetTagNameAndCommentDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private final Set<TagName> tagNamesSet = new HashSet<>();
    private TagNameAndComment tagNameAndComment = null;
    
    public static class TagNameAndComment {

        private final TagName tagName;
        private final String comment;

        private TagNameAndComment(TagName tagName, String comment) {
            this.tagName = tagName;
            this.comment = comment;
        }

        public TagName getTagName() {
            return tagName;
        }

        public String getComment() {
            return comment;
        }
    }

    /**
     * Show the Tag Name and Comment Dialog and return the TagNameAndContent
     * chosen by the user. The dialog will be centered with the main autopsy
     * window as its owner.
     *
     * @return a TagNameAndComment instance containing the TagName selected by
     *         the user and the entered comment, or null if the user canceled
     *         the dialog.
     */
    public static TagNameAndComment doDialog() {
        return doDialog(WindowManager.getDefault().getMainWindow());
    }

    /**
     * Show the Tag Name and Comment Dialog and return the TagNameAndContent
     * chosen by the user.
     *
     * @param owner the window that will be the owner of the dialog. The dialog
     *              will be centered over this window and will block the rest of
     *              the application.
     *
     * @return a TagNameAndComment instance containg the TagName selected by the
     *         user and the entered comment, or null if the user canceled the
     *         dialog.
     */
    public static TagNameAndComment doDialog(Window owner) {
        GetTagNameAndCommentDialog dialog = new GetTagNameAndCommentDialog(owner);
        dialog.display();
        return dialog.tagNameAndComment;
    }

    private GetTagNameAndCommentDialog(Window owner) {
        super(owner,
                NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.createTag"),
                ModalityType.APPLICATION_MODAL);
    }

    
    private void display() {
        initComponents();
        tagCombo.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String status = ((TagName) value).getKnownStatus() == TskData.FileKnown.BAD ?TagsManager.getNotableTagLabel() : "";
                String newValue = ((TagName) value).getDisplayName() + status;
                return super.getListCellRendererComponent(list, newValue, index, isSelected, cellHasFocus);
            }
        });
        // Set up the dialog to close when Esc is pressed.
        String cancelName = NbBundle.getMessage(this.getClass(), "GetTagNameAndCommentDialog.cancelName");
        InputMap inputMap = getRootPane().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), cancelName);
        ActionMap actionMap = getRootPane().getActionMap();

        actionMap.put(cancelName, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        }
        );

        // Populate the combo box with the available tag names and save the 
        // tag name DTOs to be enable to return the one the user selects.
        // Tag name DTOs may be null (user tag names that have not been used do
        // not exist in the database).
        try {
            TagsManager tagsManager = Case.getOpenCase().getServices().getTagsManager();
            tagNamesSet.addAll(tagsManager.getAllTagNames());

        } catch (TskCoreException | NoCurrentCaseException ex) {
            Logger.getLogger(GetTagNameAndCommentDialog.class
                    .getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
        }
        for (TagName tag : tagNamesSet) {

            tagCombo.addItem(tag);
        }

        // Center and show the dialog box. 
        this.setLocationRelativeTo(this.getOwner());
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        tagCombo = new javax.swing.JComboBox<TagName>();
        tagLabel = new javax.swing.JLabel();
        commentLabel = new javax.swing.JLabel();
        commentText = new javax.swing.JTextField();
        newTagButton = new javax.swing.JButton();

        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                closeDialog(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        tagCombo.setToolTipText(org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.tagCombo.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(tagLabel, org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.tagLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(commentLabel, org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.commentLabel.text")); // NOI18N

        commentText.setText(org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.commentText.text")); // NOI18N
        commentText.setToolTipText(org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.commentText.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(newTagButton, org.openide.util.NbBundle.getMessage(GetTagNameAndCommentDialog.class, "GetTagNameAndCommentDialog.newTagButton.text")); // NOI18N
        newTagButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTagButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(newTagButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 165, Short.MAX_VALUE)
                        .addComponent(okButton, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(commentLabel)
                            .addComponent(tagLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(commentText)
                            .addComponent(tagCombo, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tagCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tagLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(commentLabel)
                    .addComponent(commentText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 37, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton)
                    .addComponent(newTagButton))
                .addContainerGap())
        );

        getRootPane().setDefaultButton(okButton);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        TagName tagNameFromCombo = (TagName) tagCombo.getSelectedItem();
        tagNameAndComment = new TagNameAndComment(tagNameFromCombo, commentText.getText());
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        tagNameAndComment = null;
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void closeDialog(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_closeDialog
        tagNameAndComment = null;
        dispose();
    }//GEN-LAST:event_closeDialog

    private void newTagButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTagButtonActionPerformed
        TagName newTagName = GetTagNameDialog.doDialog(this);
        if (newTagName != null) {
            tagNamesSet.add(newTagName);
            tagCombo.addItem(newTagName);
            tagCombo.setSelectedItem(newTagName);
        }
    }//GEN-LAST:event_newTagButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel commentLabel;
    private javax.swing.JTextField commentText;
    private javax.swing.JButton newTagButton;
    private javax.swing.JButton okButton;
    private javax.swing.JComboBox<TagName> tagCombo;
    private javax.swing.JLabel tagLabel;
    // End of variables declaration//GEN-END:variables
}
