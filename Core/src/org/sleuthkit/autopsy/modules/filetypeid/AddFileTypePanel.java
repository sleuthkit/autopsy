/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.filetypeid;

import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import static org.sleuthkit.autopsy.modules.filetypeid.AddFileTypePanel.EVENT.SIG_LIST_CHANGED;
import org.sleuthkit.autopsy.modules.filetypeid.AddFileTypeSignatureDialog.BUTTON_PRESSED;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;

/**
 * Panel for adding or editing file types.
 */
@Messages("AddFileTypePanel.mimeFormatLabel.text=Form of MIME type should be: media type/media subtype")
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class AddFileTypePanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private AddFileTypeSignatureDialog addSigDialog;
    private DefaultListModel<FileType.Signature> signaturesListModel;

    /**
     * Creates a panel for a new file type.
     */
    AddFileTypePanel() {
        initComponents();
        this.signaturesListModel = new DefaultListModel<>();
        this.signatureList.setModel(signaturesListModel);
        this.addTypeListSelectionListener();
        this.enableButtons();
    }

    enum EVENT {
        SIG_LIST_CHANGED
    }

    /**
     * Creates a panel for editing a file type.
     *
     * @param toEdit The file type to edit
     */
    AddFileTypePanel(FileType toEdit) {
        this();
        this.setComponentValues(toEdit);
    }

    /**
     * Sets the values of the UI components according to the file type given.
     *
     * @param toEdit The file type used to set the components.
     */
    private void setComponentValues(FileType toEdit) {
        this.mimeTypeTextField.setText(toEdit.getMimeType());
        for (Signature sig : toEdit.getSignatures()) {
            this.signaturesListModel.addElement(sig);
        }
        this.postHitCheckBox.setSelected(toEdit.shouldCreateInterestingFileHit());
        this.setNameTextField.setEnabled(toEdit.shouldCreateInterestingFileHit());
        this.setNameTextField.setText(toEdit.getInterestingFilesSetName());
            
    }

    /**
     * Gets the file type defined by this panel, and gives popups for invalid
     * file types.
     *
     * @return The file type defined by this panel, or null if an invalid file
     *         type is given.
     */
    @Messages({"AddMimeTypePanel.emptySigList.message=Must have at least one signature.",
        "AddMimeTypePanel.emptySigList.title=Invalid Signature List",
        "AddMimeTypePanel.emptySetName.message=Interesting files set name is required if alert is requested.",
        "AddMimeTypePanel.emptySetName.title=Missing Interesting Files Set Name",
        "# {0} - media subtype",
        "AddFileTypePanel.nonStandardMIMEType.message="
        + "MIME type must be of form: media type/media subtype. Custom/{0} has been suggested instead.",
        "# {0} - type name",
        "AddFileTypePanel.containsIllegalCharacter.message=Invalid character in MIME type, {0} has been suggested instead",
        "AddFileTypePanel.containsIllegalCharacter.title=Invalid Character in MIME Type",
        "AddFileTypePanel.nonStandardMIMEType.title=Non-standard MIME Type"})

    FileType getFileType() {
        String typeName = mimeTypeTextField.getText();

        //if typeName does not equal sanitized typeName display message saying this name will be used instead 
        if (typeName.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        //if we need to remove more characters could use matches instead of contains and regex "[^\\w\s\\-\\/] to remove everything that isnt a letter, number, underscore, whitespace, dash, or forward slash.
        if (typeName.contains("\'")) {  //remove single apostraphes as they are an easy way to accidently screw up PostgreSQL
            typeName = typeName.replaceAll("[\\']", "");
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.containsIllegalCharacter.message", typeName),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.containsIllegalCharacter.title"),
                    JOptionPane.WARNING_MESSAGE);
            mimeTypeTextField.setText(typeName);
            return null;
        }
        //if the MIME type is lacking two parts or the first part is empty ask if they want to use 'custom' as the first part
        //if the MIME type has more than 2 parts the first part will be used as a media type and the remainder of the string as the sub-type
        String[] splitName = typeName.split("/");
        if (splitName.length < 2 || splitName[0].isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.nonStandardMIMEType.message", typeName),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.nonStandardMIMEType.title"),
                    JOptionPane.WARNING_MESSAGE);
            mimeTypeTextField.setText("custom/" + typeName);
            return null;
        }
        //Make sure the mimetype will piece back together to be the same string it was entered 
        //trailing forward slashes will cause this mismatch to happen
        //suggests a mime_type that will be the same after it is split appart and rejoined
        if (!StringUtils.join(ArrayUtils.subarray(splitName, 0, splitName.length), "/").equals(typeName)) {
            String rejoinedMimeType = StringUtils.join(ArrayUtils.subarray(splitName, 0, splitName.length), "/");
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.nonStandardMIMEType.message", rejoinedMimeType),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "AddFileTypePanel.nonStandardMIMEType.title"),
                    JOptionPane.WARNING_MESSAGE);
            mimeTypeTextField.setText(rejoinedMimeType);
            return null;
        }
        if (this.signaturesListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Bundle.AddMimeTypePanel_emptySigList_message(),
                    Bundle.AddMimeTypePanel_emptySigList_title(),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        List<Signature> sigList = new ArrayList<>();
        for (int i = 0; i < this.signaturesListModel.getSize(); i++) {
            sigList.add(this.signaturesListModel.elementAt(i));
        }

        String setName = "";
        if (this.postHitCheckBox.isSelected()) {
            if (this.setNameTextField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        Bundle.AddMimeTypePanel_emptySetName_message(),
                        Bundle.AddMimeTypePanel_emptySetName_title(),
                        JOptionPane.ERROR_MESSAGE);

                return null;
            }
            setName = this.setNameTextField.getText();
        }
        return new FileType(typeName, sigList, this.postHitCheckBox.isSelected(), setName);
    }

    /**
     * Adds the selection listener to the list so that actions are taken based
     * on whether or not an item in the list of signatures is selected.
     */
    private void addTypeListSelectionListener() {
        this.signatureList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting() == false) {
                    enableButtons();

                }
            }
        });
    }

    /**
     * Enables or disables buttons based on whether or not an item in the list
     * of signatures is selected.
     */
    private void enableButtons() {
        if (signatureList.getSelectedIndex() == -1) {
            editSigButton.setEnabled(false);
            deleteSigButton.setEnabled(false);
        } else {
            editSigButton.setEnabled(true);
            deleteSigButton.setEnabled(true);
        }
    }

    boolean hasSignature() {
        return !this.signaturesListModel.isEmpty();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        editSigButton = new javax.swing.JButton();
        deleteSigButton = new javax.swing.JButton();
        mimeTypeLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        signatureList = new javax.swing.JList<>();
        mimeTypeTextField = new javax.swing.JTextField();
        addSigButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        postHitCheckBox = new javax.swing.JCheckBox();
        setNameLabel = new javax.swing.JLabel();
        setNameTextField = new javax.swing.JTextField();
        mimeFormatLabel = new javax.swing.JLabel();

        editSigButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editSigButton, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.editSigButton.text")); // NOI18N
        editSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSigButtonActionPerformed(evt);
            }
        });

        deleteSigButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteSigButton, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.deleteSigButton.text")); // NOI18N
        deleteSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSigButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(mimeTypeLabel, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.mimeTypeLabel.text")); // NOI18N

        signatureList.setModel(new javax.swing.AbstractListModel<FileType.Signature>() {
            Signature[] signatures = {};
            public int getSize() { return signatures.length; }
            public Signature getElementAt(int i) { return signatures[i]; }
        });
        jScrollPane1.setViewportView(signatureList);

        mimeTypeTextField.setText(org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.mimeTypeTextField.text")); // NOI18N

        addSigButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(addSigButton, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.addSigButton.text")); // NOI18N
        addSigButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addSigButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(postHitCheckBox, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.postHitCheckBox.text")); // NOI18N
        postHitCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                postHitCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(setNameLabel, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.setNameLabel.text")); // NOI18N
        setNameLabel.setEnabled(postHitCheckBox.isSelected());

        setNameTextField.setText(org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.setNameTextField.text")); // NOI18N
        setNameTextField.setEnabled(postHitCheckBox.isSelected());

        org.openide.awt.Mnemonics.setLocalizedText(mimeFormatLabel, org.openide.util.NbBundle.getMessage(AddFileTypePanel.class, "AddFileTypePanel.mimeFormatLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(mimeTypeLabel)
                        .addGap(18, 18, 18)
                        .addComponent(mimeTypeTextField))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(addSigButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(editSigButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(deleteSigButton))
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 393, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGroup(layout.createSequentialGroup()
                            .addGap(28, 28, 28)
                            .addComponent(setNameLabel)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                            .addComponent(setNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(postHitCheckBox))
                        .addGap(0, 0, 0))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(71, 71, 71)
                        .addComponent(mimeFormatLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mimeTypeLabel)
                    .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(mimeFormatLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addGap(1, 1, 1)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addSigButton)
                    .addComponent(editSigButton)
                    .addComponent(deleteSigButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(postHitCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(setNameLabel)
                    .addComponent(setNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void editSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSigButtonActionPerformed
        if (evt.getSource().equals(this.editSigButton) && this.signatureList.getSelectedValue() != null) {
            int selected = this.signatureList.getSelectedIndex();
            this.addSigDialog = new AddFileTypeSignatureDialog(this.signatureList.getSelectedValue());
            this.addSigDialog.display(false);
            if (addSigDialog.getResult() == BUTTON_PRESSED.OK) {
                signaturesListModel.removeElementAt(selected);
                this.signaturesListModel.add(selected, this.addSigDialog.getSignature());
            }
        }
    }//GEN-LAST:event_editSigButtonActionPerformed

    private void deleteSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSigButtonActionPerformed
        if (this.signatureList.getSelectedIndex() != -1) {
            signaturesListModel.removeElementAt(this.signatureList.getSelectedIndex());
            if (!this.signaturesListModel.isEmpty()) {
                signatureList.setSelectedIndex(0);
            }
            firePropertyChange(SIG_LIST_CHANGED.toString(), null, null);
        }
    }//GEN-LAST:event_deleteSigButtonActionPerformed

    private void addSigButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addSigButtonActionPerformed
        if (evt.getSource().equals(this.addSigButton)) {
            this.addSigDialog = new AddFileTypeSignatureDialog();
            this.addSigDialog.display(true);
            if (addSigDialog.getResult() == AddFileTypeSignatureDialog.BUTTON_PRESSED.OK) {
                signaturesListModel.addElement(this.addSigDialog.getSignature());
            }
            firePropertyChange(SIG_LIST_CHANGED.toString(), null, null);
        }
    }//GEN-LAST:event_addSigButtonActionPerformed

    private void postHitCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_postHitCheckBoxActionPerformed
        if (evt.getSource().equals(postHitCheckBox)) {
            this.setNameLabel.setEnabled(postHitCheckBox.isSelected());
            this.setNameTextField.setEnabled(postHitCheckBox.isSelected());
        }
    }//GEN-LAST:event_postHitCheckBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addSigButton;
    private javax.swing.JButton deleteSigButton;
    private javax.swing.JButton editSigButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel mimeFormatLabel;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JTextField mimeTypeTextField;
    private javax.swing.JCheckBox postHitCheckBox;
    private javax.swing.JLabel setNameLabel;
    private javax.swing.JTextField setNameTextField;
    private javax.swing.JList<FileType.Signature> signatureList;
    // End of variables declaration//GEN-END:variables
}
