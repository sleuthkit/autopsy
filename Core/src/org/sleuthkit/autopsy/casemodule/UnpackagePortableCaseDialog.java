/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.NbBundle;

/**
 *
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class UnpackagePortableCaseDialog extends javax.swing.JDialog {

    private final String[] PORTABLE_CASE_EXTENSIONS = new String[]{"pkg", "001"}; //NON-NLS
    private final JFileChooser caseFileChooser;
    private final JFileChooser outputFileChooser;
    
    /**
     * Creates new form UnpackagePortableCaseDialog
     */
    @NbBundle.Messages({
        "UnpackagePortableCaseDialog.UnpackagePortableCaseDialog.extensions=Portable case package (.pkg, .pkg.001)",
    }) 
    UnpackagePortableCaseDialog(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();
        
        FileNameExtensionFilter pkgFilter = new FileNameExtensionFilter(
                Bundle.UnpackagePortableCaseDialog_UnpackagePortableCaseDialog_extensions(), PORTABLE_CASE_EXTENSIONS);
        caseFileChooser = new JFileChooser();
        caseFileChooser.setFileFilter(pkgFilter);
        
        outputFileChooser = new JFileChooser();
        outputFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        caseErrorLabel.setText(""); // NON-NLS
        outputErrorLabel.setText(""); // NON-NLS
        unpackageButton.setEnabled(false);
        
        /*
         * Create listenerer for the file paths
         */
        caseTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                validatePaths();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                validatePaths();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validatePaths();
            }
        });
        outputTextField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                validatePaths();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                validatePaths();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validatePaths();
            }
        });
        
    }
    
    @NbBundle.Messages({
        "UnpackagePortableCaseDialog.validatePaths.caseNotFound=File does not exist",
        "UnpackagePortableCaseDialog.validatePaths.caseIsNotFile=Selected path is not a file",
        "UnpackagePortableCaseDialog.validatePaths.folderNotFound=Folder does not exist",
        "UnpackagePortableCaseDialog.validatePaths.notAFolder=Output location is not a directory",
    })
    private void validatePaths() {
        boolean isValid = true;

        File portableCasePackage = new File(caseTextField.getText());
        File outputDir = new File(outputTextField.getText());
        
        // First test the case package
        if (caseTextField.getText().isEmpty()) {
            caseErrorLabel.setText(""); // NON-NLS
            isValid = false;
        } else {
            if (! portableCasePackage.exists()) {
                caseErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_caseNotFound());
                isValid = false;
            } else if (! portableCasePackage.isFile()) {
                caseErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_caseIsNotFile());
                isValid = false;
            } else {
                caseErrorLabel.setText(""); // NON-NLS
            }
        }
        
        // Now test the output folder
        // First test the case package
        if (outputTextField.getText().isEmpty()) {
            outputErrorLabel.setText(""); // NON-NLS
            isValid = false;
        } else {
            if (! outputDir.exists()) {
                outputErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_folderNotFound());
                isValid = false;
            } else if (! outputDir.isDirectory()) {
                outputErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_notAFolder());
                isValid = false;
            } else {
                outputErrorLabel.setText(""); // NON-NLS
            }
        }
        
        unpackageButton.setEnabled(isValid);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        desc1Label = new javax.swing.JLabel();
        desc2Label = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        caseTextField = new javax.swing.JTextField();
        jLabel8 = new javax.swing.JLabel();
        outputTextField = new javax.swing.JTextField();
        outputSelectButton = new javax.swing.JButton();
        caseSelectButton = new javax.swing.JButton();
        unpackageButton = new javax.swing.JButton();
        exitButton = new javax.swing.JButton();
        caseErrorLabel = new javax.swing.JLabel();
        outputErrorLabel = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(desc1Label, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.desc1Label.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(desc2Label, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.desc2Label.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel7, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.jLabel7.text")); // NOI18N

        caseTextField.setText(org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.caseTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel8, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.jLabel8.text")); // NOI18N

        outputTextField.setText(org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.outputTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(outputSelectButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.outputSelectButton.text")); // NOI18N
        outputSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                outputSelectButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(caseSelectButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.caseSelectButton.text")); // NOI18N
        caseSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                caseSelectButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(unpackageButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.unpackageButton.text")); // NOI18N
        unpackageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unpackageButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(exitButton, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.exitButton.text")); // NOI18N
        exitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitButtonActionPerformed(evt);
            }
        });

        caseErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(caseErrorLabel, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.caseErrorLabel.text")); // NOI18N

        outputErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(outputErrorLabel, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.outputErrorLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(caseTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(caseSelectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(desc1Label)
                            .addComponent(desc2Label)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel7)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(caseErrorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(jLabel8)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(outputErrorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                                .addGap(112, 112, 112)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(outputTextField)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(outputSelectButton, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(unpackageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(exitButton, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(desc1Label)
                .addGap(4, 4, 4)
                .addComponent(desc2Label)
                .addGap(13, 13, 13)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(caseErrorLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseSelectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(outputErrorLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(outputTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(outputSelectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exitButton)
                    .addComponent(unpackageButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void outputSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_outputSelectButtonActionPerformed
        int returnVal = outputFileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            outputTextField.setText(outputFileChooser.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_outputSelectButtonActionPerformed

    private void caseSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_caseSelectButtonActionPerformed
        int returnVal = caseFileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            caseTextField.setText(caseFileChooser.getSelectedFile().getAbsolutePath());
            
            // If the output folder field is empty, auto-fill it with the case folder
            if (outputTextField.getText().isEmpty() &&
                caseTextField.getText() != null && (! caseTextField.getText().isEmpty())) {
                
                File tempCase = new File(caseTextField.getText());
                if (tempCase.exists()) {
                    outputTextField.setText(tempCase.getParent());
                }
            }
        }
    }//GEN-LAST:event_caseSelectButtonActionPerformed

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitButtonActionPerformed
        dispose();
    }//GEN-LAST:event_exitButtonActionPerformed

    private void unpackageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unpackageButtonActionPerformed
        UnpackagePortableCaseProgressDialog dialog = new UnpackagePortableCaseProgressDialog();
        dialog.unpackageCase(caseTextField.getText(), outputTextField.getText());
    }//GEN-LAST:event_unpackageButtonActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(UnpackagePortableCaseDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(UnpackagePortableCaseDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(UnpackagePortableCaseDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(UnpackagePortableCaseDialog.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the dialog */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                UnpackagePortableCaseDialog dialog = new UnpackagePortableCaseDialog(new javax.swing.JFrame(), true);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosing(java.awt.event.WindowEvent e) {
                        System.exit(0);
                    }
                });
                dialog.setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caseErrorLabel;
    private javax.swing.JButton caseSelectButton;
    private javax.swing.JTextField caseTextField;
    private javax.swing.JLabel desc1Label;
    private javax.swing.JLabel desc2Label;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel outputErrorLabel;
    private javax.swing.JButton outputSelectButton;
    private javax.swing.JTextField outputTextField;
    private javax.swing.JButton unpackageButton;
    // End of variables declaration//GEN-END:variables
}
