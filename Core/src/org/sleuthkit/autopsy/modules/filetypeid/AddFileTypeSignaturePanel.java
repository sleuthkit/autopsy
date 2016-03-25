/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.filetypeid;

import java.nio.charset.Charset;
import javax.swing.JOptionPane;
import javax.xml.bind.DatatypeConverter;
import org.openide.util.NbBundle;

/**
 *
 * @author oliver
 */
class AddFileTypeSignaturePanel extends javax.swing.JPanel {

    private static final String RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM = NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.signatureComboBox.rawItem");

    /**
     * Creates new form AddFileTypeSignaturePanel
     */
    AddFileTypeSignaturePanel() {
        initComponents();
    }
    
    public Signature getSignature() {
        /**
         * Get the MIME type.
         */
        String typeName = mimeTypeTextField.getText();
        if (typeName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidMIMEType.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        /**
         * Get the signature type.
         */
        FileType.Signature.Type sigType = signatureTypeComboBox.getSelectedItem() == FileTypeIdGlobalSettingsPanel.RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM ? FileType.Signature.Type.RAW : FileType.Signature.Type.ASCII;

        /**
         * Get the signature bytes.
         */
        String sigString = signatureTextField.getText();
        if (sigString.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignature.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignature.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }
        byte[] signatureBytes;
        if (FileType.Signature.Type.RAW == sigType) {
            try {
                sigString = sigString.replaceAll("\\s", ""); //NON-NLS
                signatureBytes = DatatypeConverter.parseHexBinary(sigString);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidRawSignatureBytes.message"),
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidSignatureBytes.title"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            signatureBytes = sigString.getBytes(Charset.forName("UTF-8"));
        }

        /**
         * Get the offset.
         */
        long offset;
        boolean isRelativeToStart = offsetRelativeToComboBox.getSelectedItem() == FileTypeIdGlobalSettingsPanel.START_OFFSET_RELATIVE_COMBO_BOX_ITEM;
        try {
                offset = Long.parseUnsignedLong(offsetTextField.getText());
                if(!isRelativeToStart && signatureBytes.length > offset+1) {
                    JOptionPane.showMessageDialog(null,
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.length"),
                        NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.title"),
                        JOptionPane.ERROR_MESSAGE);
                    return null;
                }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidOffset.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        /**
         * Get the interesting files set details.
         */
        String filesSetName = "";
        if (postHitCheckBox.isSelected()) {
            filesSetName = filesSetNameTextField.getText();
        }
        if (postHitCheckBox.isSelected() && filesSetName.isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidInterestingFilesSetName.message"),
                    NbBundle.getMessage(FileTypeIdGlobalSettingsPanel.class, "FileTypeIdGlobalSettingsPanel.JOptionPane.invalidInterestingFilesSetName.title"),
                    JOptionPane.ERROR_MESSAGE);
            return null;
        }

        /**
         * Put it all together and reset the file types list component.
         */
        FileType.Signature signature = new FileType.Signature(signatureBytes, offset, sigType, isRelativeToStart);
        FileType fileType = new FileType(typeName, signature, filesSetName, postHitCheckBox.isSelected());
        FileType selected = typesList.getSelectedValue();
        if (selected != null) {
            fileTypes.remove(selected);
        }
        fileTypes.add(fileType);
        updateFileTypesListModel();
        typesList.setSelectedValue(fileType, true);
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        offsetLabel = new javax.swing.JLabel();
        offsetTextField = new javax.swing.JTextField();
        offsetRelativeToComboBox = new javax.swing.JComboBox<String>();
        offsetRelativeToLabel = new javax.swing.JLabel();
        hexPrefixLabel = new javax.swing.JLabel();
        signatureTypeComboBox = new javax.swing.JComboBox<String>();
        signatureLabel = new javax.swing.JLabel();
        signatureTypeLabel = new javax.swing.JLabel();
        signatureTextField = new javax.swing.JTextField();

        offsetLabel.setFont(offsetLabel.getFont().deriveFont(offsetLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(offsetLabel, org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.offsetLabel.text")); // NOI18N

        offsetTextField.setFont(offsetTextField.getFont().deriveFont(offsetTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        offsetTextField.setText(org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.offsetTextField.text")); // NOI18N

        offsetRelativeToComboBox.setFont(offsetRelativeToComboBox.getFont().deriveFont(offsetRelativeToComboBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        offsetRelativeToLabel.setFont(offsetRelativeToLabel.getFont().deriveFont(offsetRelativeToLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(offsetRelativeToLabel, org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.offsetRelativeToLabel.text")); // NOI18N

        hexPrefixLabel.setFont(hexPrefixLabel.getFont().deriveFont(hexPrefixLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(hexPrefixLabel, org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.hexPrefixLabel.text")); // NOI18N

        signatureTypeComboBox.setFont(signatureTypeComboBox.getFont().deriveFont(signatureTypeComboBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        signatureTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signatureTypeComboBoxActionPerformed(evt);
            }
        });

        signatureLabel.setFont(signatureLabel.getFont().deriveFont(signatureLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(signatureLabel, org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.signatureLabel.text")); // NOI18N

        signatureTypeLabel.setFont(signatureTypeLabel.getFont().deriveFont(signatureTypeLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(signatureTypeLabel, org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.signatureTypeLabel.text")); // NOI18N

        signatureTextField.setFont(signatureTextField.getFont().deriveFont(signatureTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        signatureTextField.setText(org.openide.util.NbBundle.getMessage(AddFileTypeSignaturePanel.class, "AddFileTypeSignaturePanel.signatureTextField.text")); // NOI18N
        signatureTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                signatureTextFieldActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(signatureTypeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 176, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(signatureLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 73, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(hexPrefixLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(offsetLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 71, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(offsetRelativeToLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(offsetRelativeToComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(26, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(signatureTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(signatureTypeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(hexPrefixLabel)
                        .addComponent(signatureTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(signatureLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(offsetLabel)
                    .addComponent(offsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(offsetRelativeToComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(offsetRelativeToLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void signatureTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signatureTypeComboBoxActionPerformed
        if (signatureTypeComboBox.getSelectedItem() == RAW_SIGNATURE_TYPE_COMBO_BOX_ITEM) {
            hexPrefixLabel.setVisible(true);
            signatureTextField.setText("0000");
        } else {
            hexPrefixLabel.setVisible(false);
            signatureTextField.setText("");
        }
    }//GEN-LAST:event_signatureTypeComboBoxActionPerformed

    private void signatureTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_signatureTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_signatureTextFieldActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel hexPrefixLabel;
    private javax.swing.JLabel offsetLabel;
    private javax.swing.JComboBox<String> offsetRelativeToComboBox;
    private javax.swing.JLabel offsetRelativeToLabel;
    private javax.swing.JTextField offsetTextField;
    private javax.swing.JLabel signatureLabel;
    private javax.swing.JTextField signatureTextField;
    private javax.swing.JComboBox<String> signatureTypeComboBox;
    private javax.swing.JLabel signatureTypeLabel;
    // End of variables declaration//GEN-END:variables
}
