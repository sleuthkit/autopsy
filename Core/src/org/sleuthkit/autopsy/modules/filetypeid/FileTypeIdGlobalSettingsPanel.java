/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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

import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.filetypeid.FileType.Signature;

/**
 * A panel to allow a user to make custom file type definitions.
 */
final class FileTypeIdGlobalSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final String RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM = "Bytes (Hex)"; // RJCTODO: Bundle
    private static final String ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM = "String (ASCII)"; // RJCTODO: Bundle

    /**
     * This mapping of file type names to file types is used to hold the types
     * displayed in the file types list component.
     */
    private Map<String, FileType> fileTypes;

    /**
     * Creates a panel to allow a user to make custom file type definitions.
     */
    FileTypeIdGlobalSettingsPanel() {
        initComponents();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void saveSettings() {
        this.store();
    }

    /**
     * Populates the child components.
     */
    @Override
    public void load() {
        /**
         * Get the user-defined file types and set up a list model for the file
         * types list component.
         */
        this.fileTypes = UserDefinedFileTypesManager.getInstance().getUserDefinedFileTypes();
        DefaultListModel<String> fileTypesListModel = new DefaultListModel<>();
        for (FileType fileType : this.fileTypes.values()) {
            fileTypesListModel.addElement(fileType.getTypeName());
        }
        this.typesList.setModel(fileTypesListModel);

        /**
         * Add a selection listener to populate the file type details
         * display/edit components.
         */
        this.typesList.addListSelectionListener(new TypesListSelectionListener());

        /**
         * If there is at least one user-defined file type, select it the file
         * types list component.
         */
        if (!fileTypesListModel.isEmpty()) {
            this.typesList.setSelectedIndex(0);
        }

        /**
         * Make a model for the signature type combo box component.
         */
        DefaultComboBoxModel<String> sigTypeComboBoxModel = new DefaultComboBoxModel<>();
        sigTypeComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM);
        sigTypeComboBoxModel.addElement(FileTypeIdGlobalSettingsPanel.ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM);
        this.signatureTypeComboBox.setModel(sigTypeComboBoxModel);
    }

    /**
     * Stores any changes to the user-defined types.
     */
    @Override
    public void store() {
        try {
            UserDefinedFileTypesManager.getInstance().setUserDefinedFileTypes(this.fileTypes);
        } catch (UserDefinedFileTypesManager.UserDefinedFileTypesException ex) {
            // RJCTODO            
        }
    }

    private class TypesListSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting() == false) {
                if (FileTypeIdGlobalSettingsPanel.this.typesList.getSelectedIndex() == -1) {
                    FileTypeIdGlobalSettingsPanel.this.deleteTypeButton.setEnabled(false);
                } else {
                    String typeName = FileTypeIdGlobalSettingsPanel.this.typesList.getSelectedValue();
                    FileType fileType = FileTypeIdGlobalSettingsPanel.this.fileTypes.get(typeName);
                    Signature signature = fileType.getSignature();
                    FileTypeIdGlobalSettingsPanel.this.mimeTypeTextField.setText(typeName);
                    FileType.Signature.Type sigType = fileType.getSignature().getType();
                    FileTypeIdGlobalSettingsPanel.this.signatureTypeComboBox.setSelectedItem(sigType == FileType.Signature.Type.RAW ? FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM : FileTypeIdGlobalSettingsPanel.ASCII_SIGNATURE_TYPE_COMBO_BOX_ITEM);
                    FileTypeIdGlobalSettingsPanel.this.offsetTextField.setText(Long.toString(signature.getOffset()));
                    FileTypeIdGlobalSettingsPanel.this.postHitCheckBox.setSelected(fileType.alertOnMatch());
                    FileTypeIdGlobalSettingsPanel.this.deleteTypeButton.setEnabled(true);
                }
            }
        }
    }

    /**
     * RJCTODO
     */
    private void clearTypeDetailsComponents() {
        this.typesList.setSelectedIndex(-1);
        this.mimeTypeTextField.setText(""); //NON-NLS // RJCTODO: Default name?
        this.signatureTypeComboBox.setSelectedItem(FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM);
        this.signatureTextField.setText(""); //NON-NLS
        this.offsetTextField.setText(""); //NON-NLS
        this.postHitCheckBox.setSelected(false);
    }

    // RJCTODO: Consider fixing OptionsPanel interface or writing story
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        typesScrollPane = new javax.swing.JScrollPane();
        typesList = new javax.swing.JList<String>();
        jSeparator1 = new javax.swing.JSeparator();
        mimeTypeLabel = new javax.swing.JLabel();
        mimeTypeTextField = new javax.swing.JTextField();
        signatureTypeLabel = new javax.swing.JLabel();
        signatureTextField = new javax.swing.JTextField();
        offsetLabel = new javax.swing.JLabel();
        offsetTextField = new javax.swing.JTextField();
        newTypeButton = new javax.swing.JButton();
        deleteTypeButton = new javax.swing.JButton();
        saveTypeButton = new javax.swing.JButton();
        hexPrefixLabel = new javax.swing.JLabel();
        signatureTypeComboBox = new javax.swing.JComboBox<String>();
        signatureLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        postHitCheckBox = new javax.swing.JCheckBox();

        typesScrollPane.setViewportView(typesList);

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        org.openide.awt.Mnemonics.setLocalizedText(mimeTypeLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeLabel.text")); // NOI18N

        mimeTypeTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.mimeTypeTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(signatureTypeLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureTypeLabel.text")); // NOI18N

        signatureTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(offsetLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetLabel.text")); // NOI18N

        offsetTextField.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.offsetTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(newTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.newTypeButton.text")); // NOI18N
        newTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.deleteTypeButton.text")); // NOI18N
        deleteTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(saveTypeButton, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.saveTypeButton.text")); // NOI18N
        saveTypeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveTypeButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(hexPrefixLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.hexPrefixLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(signatureLabel, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureLabel.text")); // NOI18N

        jTextArea1.setEditable(false);
        jTextArea1.setColumns(20);
        jTextArea1.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(5);
        jTextArea1.setText(org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.jTextArea1.text")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jScrollPane2.setViewportView(jTextArea1);

        org.openide.awt.Mnemonics.setLocalizedText(postHitCheckBox, org.openide.util.NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.postHitCheckBox.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(26, 26, 26)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(newTypeButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteTypeButton))
                    .addComponent(typesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addComponent(saveTypeButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 13, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(signatureTypeLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(signatureLabel)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(hexPrefixLabel)
                                                .addGap(2, 2, 2))
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(offsetLabel)
                                                .addGap(44, 44, 44)))
                                        .addGap(5, 5, 5)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addGap(2, 2, 2)
                                        .addComponent(mimeTypeLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 181, javax.swing.GroupLayout.PREFERRED_SIZE))))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(postHitCheckBox)))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(typesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 219, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(mimeTypeLabel)
                                    .addComponent(mimeTypeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(signatureTypeLabel)
                                    .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(hexPrefixLabel)
                                    .addComponent(signatureLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(offsetLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(postHitCheckBox)))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(newTypeButton)
                                    .addComponent(deleteTypeButton)))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(saveTypeButton))))
                    .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 260, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void newTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newTypeButtonActionPerformed
        this.clearTypeDetailsComponents();
        // RJCTODO: Default name?
    }//GEN-LAST:event_newTypeButtonActionPerformed

    private void deleteTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteTypeButtonActionPerformed
        String typeName = this.typesList.getSelectedValue();
        this.fileTypes.remove(typeName);
        this.clearTypeDetailsComponents();
        this.typesList.setSelectedIndex(-1);
    }//GEN-LAST:event_deleteTypeButtonActionPerformed

    private void saveTypeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveTypeButtonActionPerformed
        try {
            String typeName = this.mimeTypeTextField.getText();
            if (typeName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "MIME type name cannot be empty.", "Missing MIME Type", JOptionPane.ERROR_MESSAGE); // RJCTODO: Bundle
                return;
            }

            String sigString = this.signatureTextField.getText();
            if (sigString.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Signature cannot be empty.", "Missing Signature", JOptionPane.ERROR_MESSAGE); // RJCTODO: Bundle
                return;
            }

            long offset = Long.parseUnsignedLong(this.offsetTextField.getText());

            FileType.Signature.Type sigType = this.signatureTypeComboBox.getSelectedItem() == FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM ? FileType.Signature.Type.RAW : FileType.Signature.Type.ASCII;
            FileType.Signature signature = new FileType.Signature(new byte[1], offset, sigType); // RJCTODO:
            FileType fileType = new FileType(typeName, signature, this.postHitCheckBox.isSelected());
            this.fileTypes.put(typeName, fileType);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null, "Offset is not a positive integer.", "Invalid Offset", JOptionPane.ERROR_MESSAGE); // RJCTODO: Bundle
        }
    }//GEN-LAST:event_saveTypeButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton deleteTypeButton;
    private javax.swing.JLabel hexPrefixLabel;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JTextField mimeTypeTextField;
    private javax.swing.JButton newTypeButton;
    private javax.swing.JLabel offsetLabel;
    private javax.swing.JTextField offsetTextField;
    private javax.swing.JCheckBox postHitCheckBox;
    private javax.swing.JButton saveTypeButton;
    private javax.swing.JLabel signatureLabel;
    private javax.swing.JTextField signatureTextField;
    private javax.swing.JComboBox<String> signatureTypeComboBox;
    private javax.swing.JLabel signatureTypeLabel;
    private javax.swing.JList<String> typesList;
    private javax.swing.JScrollPane typesScrollPane;
    // End of variables declaration//GEN-END:variables

}
