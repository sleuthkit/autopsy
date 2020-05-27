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
package org.sleuthkit.autopsy.casemodule.services;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.datamodel.TskData;

@Messages({"TagNameDialog.descriptionLabel.text=Description:",
    "TagNameDialog.notableCheckbox.text=Tag indicates item is notable."})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class TagNameDialog extends javax.swing.JDialog {

    private static final long serialVersionUID = 1L;
    private String userTagDisplayName;
    private String userTagDescription;
    private boolean userTagIsNotable;
    private BUTTON_PRESSED result;

    enum BUTTON_PRESSED {
        OK, CANCEL;
    }

    /**
     * Creates a new NewUserTagNameDialog dialog.
     */
    TagNameDialog() {
        super(new JFrame(NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.title.text")),
                NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.title.text"), true);
        initComponents();
        this.display();
    }

    @Messages({"TagNameDialog.editTitle.text=Edit Tag"})
    TagNameDialog(TagNameDefinition tagNameToEdit) {
        super(new JFrame(NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.editTitle.text")),
                NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.editTitle.text"), true);
        initComponents();
        tagNameTextField.setText(tagNameToEdit.getDisplayName());
        descriptionTextArea.setText(tagNameToEdit.getDescription());
        notableCheckbox.setSelected(tagNameToEdit.getKnownStatus() == TskData.FileKnown.BAD);
        tagNameTextField.setEnabled(false);
        this.display();
    }

    /**
     * Sets display settings for the dialog and adds appropriate listeners.
     */
    private void display() {
        setLayout(new BorderLayout());

        /*
         * Center the dialog
         */
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());

        /*
         * Add a handler for when the dialog window is closed directly.
         */
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doButtonAction(false);
            }
        });

        /*
         * Add a listener to enable the OK button when the text field changes.
         */
        tagNameTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                fire();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fire();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                fire();
            }

            private void fire() {
                enableOkButton();
            }
        });

        enableOkButton();

        /*
         * Used to show the dialog.
         */
        setResizable(false);
        setVisible(true);
    }

    /**
     * Called when a button is pressed or when the dialog is closed.
     *
     * @param okPressed whether the OK button was pressed.
     */
    @Messages({"TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.message=Tag descriptions may not contain commas (,) or semicolons (;)",
        "TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.title=Invalid character in tag description"})
    private void doButtonAction(boolean okPressed) {
        if (okPressed) {
            String newTagDisplayName = tagNameTextField.getText().trim();
            String descriptionText = descriptionTextArea.getText();
            if (newTagDisplayName.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagNameEmpty.message"),
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagNameEmpty.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            //if a tag name contains illegal characters and is not the name of one of the standard tags
            if (TagsManager.containsIllegalCharacters(newTagDisplayName) && !TagNameDefinition.getStandardTagNames().contains(newTagDisplayName)) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.message"),
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            } else if (descriptionText.contains(",")
                    || descriptionText.contains(";")) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.message"),
                        NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.JOptionPane.tagDescriptionIllegalCharacters.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            userTagDescription = descriptionTextArea.getText();
            userTagDisplayName = newTagDisplayName;
            userTagIsNotable = notableCheckbox.isSelected();
            result = BUTTON_PRESSED.OK;
        } else {
            result = BUTTON_PRESSED.CANCEL;
        }
        setVisible(false);
    }

    /**
     * Returns the tag name entered by the user.
     *
     * @return a new user tag name
     */
    String getTagName() {
        return userTagDisplayName;
    }

    String getTagDesciption() {
        return userTagDescription;
    }

    boolean isTagNotable() {
        return userTagIsNotable;
    }

    /**
     * Returns information about which button was pressed.
     *
     * @return BUTTON_PRESSED (OK, CANCEL)
     */
    BUTTON_PRESSED getResult() {
        return result;
    }

    /**
     * Enable the OK button if the tag name text field is not empty. Sets the
     * enter button as default, so user can press enter to activate an okButton
     * press and add the tag name.
     */
    private void enableOkButton() {
        okButton.setEnabled(!tagNameTextField.getText().isEmpty());
        getRootPane().setDefaultButton(okButton);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        newTagNameLabel = new javax.swing.JLabel();
        tagNameTextField = new javax.swing.JTextField();
        cancelButton = new javax.swing.JButton();
        okButton = new javax.swing.JButton();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextArea = new javax.swing.JTextArea();
        descriptionLabel = new javax.swing.JLabel();
        notableCheckbox = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(newTagNameLabel, org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.newTagNameLabel.text")); // NOI18N

        tagNameTextField.setText(org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.tagNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        descriptionTextArea.setColumns(20);
        descriptionTextArea.setRows(3);
        descriptionScrollPane.setViewportView(descriptionTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.descriptionLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(notableCheckbox, org.openide.util.NbBundle.getMessage(TagNameDialog.class, "TagNameDialog.notableCheckbox.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tagNameTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 284, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addComponent(newTagNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(descriptionScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(notableCheckbox)
                            .addComponent(descriptionLabel))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(newTagNameLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tagNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(notableCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 42, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(okButton)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        doButtonAction(true);
    }//GEN-LAST:event_okButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        doButtonAction(false);
    }//GEN-LAST:event_cancelButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextArea descriptionTextArea;
    private javax.swing.JLabel newTagNameLabel;
    private javax.swing.JCheckBox notableCheckbox;
    private javax.swing.JButton okButton;
    private javax.swing.JTextField tagNameTextField;
    // End of variables declaration//GEN-END:variables
}
