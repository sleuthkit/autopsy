/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.nio.file.Paths;
import javax.swing.JFileChooser;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.openide.util.NbBundle;

/**
 * Dialog for unpackaging a portable case
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class UnpackagePortableCaseDialog extends javax.swing.JDialog {

    private final String[] PORTABLE_CASE_EXTENSIONS = new String[]{"zip", "001"}; //NON-NLS
    private final JFileChooser caseFileChooser;
    private final JFileChooser outputFolderChooser;
    
    /**
     * Creates new form UnpackagePortableCaseDialog
     */
    @NbBundle.Messages({
        "UnpackagePortableCaseDialog.UnpackagePortableCaseDialog.extensions=Portable case package (.zip, .zip.001)",
        "UnpackagePortableCaseDialog.title.text=Unpackage Portable Case",
    }) 
    UnpackagePortableCaseDialog(java.awt.Frame parent) {
        super(parent, Bundle.UnpackagePortableCaseDialog_title_text(), true);
        initComponents();
        
        FileNameExtensionFilter pkgFilter = new FileNameExtensionFilter(
                Bundle.UnpackagePortableCaseDialog_UnpackagePortableCaseDialog_extensions(), PORTABLE_CASE_EXTENSIONS);
        caseFileChooser = new JFileChooser();
        caseFileChooser.setFileFilter(pkgFilter);
        
        outputFolderChooser = new JFileChooser();
        outputFolderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
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
    
    /**
     * Check whether the input and output paths are valid.
     */
    @NbBundle.Messages({
        "UnpackagePortableCaseDialog.validatePaths.caseNotFound=File does not exist",
        "UnpackagePortableCaseDialog.validatePaths.caseIsNotFile=Selected path is not a file",
        "UnpackagePortableCaseDialog.validatePaths.folderNotFound=Folder does not exist",
        "UnpackagePortableCaseDialog.validatePaths.notAFolder=Output location is not a directory",
        "# {0} - case folder",
        "UnpackagePortableCaseDialog.validatePaths.caseFolderExists=Folder {0} already exists",
        "UnpackagePortableCaseDialog.validatePaths.badExtension=File extension must be .zip or .zip.001",
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
            }else {
                // Do a final check that if the exension is ".001" it is preceeded by ".zip"
                if (portableCasePackage.getAbsolutePath().endsWith(".001")) { // NON-NLS
                    if (portableCasePackage.getAbsolutePath().endsWith(".zip.001")) {
                        caseErrorLabel.setText(""); // NON-NLS
                    } else {
                        caseErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_badExtension());
                        isValid = false;
                    }
                } else {
                    caseErrorLabel.setText(""); // NON-NLS
                }
            }
        }
        
        // Now test the output folder
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
                // Check if the expected output folder exists if we are in a potentially valid state
                if (isValid) {
                    String caseFolderName = portableCasePackage.getName();
                    if (caseFolderName.endsWith(".zip.001")) { // NON-NLS
                        caseFolderName = caseFolderName.substring(0, caseFolderName.length() - 8);
                    } else if (caseFolderName.endsWith(".zip")) { // NON-NLS
                        caseFolderName = caseFolderName.substring(0, caseFolderName.length() - 4);
                    }
                    
                    File expectedCaseFolder = Paths.get(outputDir.toString(), caseFolderName).toFile();
                    if (expectedCaseFolder.exists()) {
                        String pathToDisplay = expectedCaseFolder.toString();
                        if (pathToDisplay.length() > 40) {
                            pathToDisplay = "\"..." + pathToDisplay.substring(pathToDisplay.length() - 38) + "\""; // NON-NLS
                        }
                        outputErrorLabel.setText(Bundle.UnpackagePortableCaseDialog_validatePaths_caseFolderExists(pathToDisplay));
                        isValid = false;
                    } else {
                         outputErrorLabel.setText(""); // NON-NLS
                    }
                }
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
        caseLabel = new javax.swing.JLabel();
        caseTextField = new javax.swing.JTextField();
        extractLabel = new javax.swing.JLabel();
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

        org.openide.awt.Mnemonics.setLocalizedText(caseLabel, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.caseLabel.text")); // NOI18N

        caseTextField.setText(org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.caseTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(extractLabel, org.openide.util.NbBundle.getMessage(UnpackagePortableCaseDialog.class, "UnpackagePortableCaseDialog.extractLabel.text")); // NOI18N

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
                        .addComponent(caseLabel)
                        .addGap(18, 18, 18)
                        .addComponent(caseErrorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(desc1Label)
                            .addComponent(desc2Label)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(extractLabel)
                                .addGap(18, 18, 18)
                                .addComponent(outputErrorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 361, javax.swing.GroupLayout.PREFERRED_SIZE)))
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
                    .addComponent(caseLabel)
                    .addComponent(caseErrorLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseSelectButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(extractLabel)
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
        if (! outputTextField.getText().isEmpty()) {
            outputFolderChooser.setCurrentDirectory(new File(outputTextField.getText()));
        }
        int returnVal = outputFolderChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            outputTextField.setText(outputFolderChooser.getSelectedFile().getAbsolutePath());
        }
    }//GEN-LAST:event_outputSelectButtonActionPerformed

    private void caseSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_caseSelectButtonActionPerformed
        if (! caseTextField.getText().isEmpty()) {
            caseFileChooser.setCurrentDirectory(new File(caseTextField.getText()));
        }
        int returnVal = caseFileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            caseTextField.setText(caseFileChooser.getSelectedFile().getAbsolutePath());
            
            // Auto-fill the output field with the case folder
            File tempCase = new File(caseTextField.getText());
            if (tempCase.exists()) {
                outputTextField.setText(tempCase.getParent());
            }
        }
    }//GEN-LAST:event_caseSelectButtonActionPerformed

    private void exitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitButtonActionPerformed
        dispose();
    }//GEN-LAST:event_exitButtonActionPerformed

    private void unpackageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unpackageButtonActionPerformed
        UnpackagePortableCaseProgressDialog dialog = new UnpackagePortableCaseProgressDialog();
        dialog.unpackageCase(caseTextField.getText(), outputTextField.getText());
        if (dialog.isSuccess()) {
            dispose();
        } else {
            validatePaths(); // The output folder now exists so we need to disable the unpackage button
        }
    }//GEN-LAST:event_unpackageButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caseErrorLabel;
    private javax.swing.JLabel caseLabel;
    private javax.swing.JButton caseSelectButton;
    private javax.swing.JTextField caseTextField;
    private javax.swing.JLabel desc1Label;
    private javax.swing.JLabel desc2Label;
    private javax.swing.JButton exitButton;
    private javax.swing.JLabel extractLabel;
    private javax.swing.JLabel outputErrorLabel;
    private javax.swing.JButton outputSelectButton;
    private javax.swing.JTextField outputTextField;
    private javax.swing.JButton unpackageButton;
    // End of variables declaration//GEN-END:variables
}
