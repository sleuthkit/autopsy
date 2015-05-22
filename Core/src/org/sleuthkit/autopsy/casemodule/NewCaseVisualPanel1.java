/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import org.openide.util.NbBundle;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.WizardPathValidator;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * The wizard panel for the new case creation.
 *
 * @author jantonius
 */
final class NewCaseVisualPanel1 extends JPanel implements DocumentListener {

    private JFileChooser fc = new JFileChooser();
    private NewCaseWizardPanel1 wizPanel;
    java.util.List<WizardPathValidator> pathValidatorList = new ArrayList<>();
    private final Pattern driveLetterPattern = Pattern.compile("^[Cc]:.*$");    

    NewCaseVisualPanel1(NewCaseWizardPanel1 wizPanel) {
        initComponents();
        discoverWizardPathValidators();
        errorLabel.setVisible(false);
        lbBadMultiUserSettings.setText("");
        this.wizPanel = wizPanel;
        caseNameTextField.getDocument().addDocumentListener(this);
        caseParentDirTextField.getDocument().addDocumentListener(this);
        CaseDbConnectionInfo info = UserPreferences.getDatabaseConnectionInfo();
        if (info.getDbType() == DbType.UNKNOWN) {
            rbSingleUserCase.setSelected(true);
            rbSingleUserCase.setEnabled(false);
            rbMultiUserCase.setEnabled(false);
            lbBadMultiUserSettings.setForeground(new java.awt.Color(153, 153, 153)); // Gray
            lbBadMultiUserSettings.setText(NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.MultiUserDisabled.text"));
        } else {
            rbSingleUserCase.setEnabled(true);
            rbMultiUserCase.setEnabled(true);
            if (true == info.settingsValid()) {
                    rbMultiUserCase.setSelected(true); // default to multi-user if available
            } else {
                // if we cannot connect to the shared database, don't present the option
                lbBadMultiUserSettings.setForeground(new java.awt.Color(255, 0, 0)); // Red
                lbBadMultiUserSettings.setText(NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.badCredentials.text"));
                rbSingleUserCase.setSelected(true);
                rbSingleUserCase.setEnabled(false);
                rbMultiUserCase.setEnabled(false);
            }
        }
    }

    /**
     * Returns the name of the this panel. This name will be shown on the left
     * panel of the "New Case" wizard panel.
     *
     * @return name the name of this panel
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.getName.text");
    }

    /**
     * Gets the case name that the user types on the case name text field.
     *
     * @return caseName the case name from the case name text field
     */
    public String getCaseName() {
        return this.caseNameTextField.getText();
    }

    /**
     * Gets the base directory that the user typed on the base directory text
     * field. Will add file separator if it was not added.
     *
     * @return baseDirectory the base directory from the case dir text field
     */
    public String getCaseParentDir() {
        String parentDir = this.caseParentDirTextField.getText();

        if (parentDir.endsWith(File.separator) == false) {
            parentDir = parentDir + File.separator;
        }
        return parentDir;
    }

    public JTextField getCaseParentDirTextField() {
        return this.caseParentDirTextField;
    }

    /**
     * Gets the case type.
     *
     * @return CaseType as set via radio buttons
     */
    public CaseType getCaseType() {
        CaseType value = CaseType.SINGLE_USER_CASE;
        if (rbSingleUserCase.isSelected()) {
            value = CaseType.SINGLE_USER_CASE;
        } else if (rbMultiUserCase.isSelected()) {
            value = CaseType.MULTI_USER_CASE;
        }
        return value;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        caseTypeButtonGroup = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        caseNameLabel = new javax.swing.JLabel();
        caseDirLabel = new javax.swing.JLabel();
        caseNameTextField = new javax.swing.JTextField();
        caseParentDirTextField = new javax.swing.JTextField();
        caseDirBrowseButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        caseDirTextField = new javax.swing.JTextField();
        rbSingleUserCase = new javax.swing.JRadioButton();
        rbMultiUserCase = new javax.swing.JRadioButton();
        lbBadMultiUserSettings = new javax.swing.JLabel();
        errorLabel = new javax.swing.JLabel();

        jLabel1.setFont(new java.awt.Font("Tahoma", 1, 14)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.jLabel1.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(caseNameLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseNameLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(caseDirLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseDirLabel.text")); // NOI18N

        caseNameTextField.setText(org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseNameTextField.text_1")); // NOI18N

        caseParentDirTextField.setText(org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseParentDirTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(caseDirBrowseButton, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseDirBrowseButton.text")); // NOI18N
        caseDirBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                caseDirBrowseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.jLabel2.text_1")); // NOI18N

        caseDirTextField.setEditable(false);
        caseDirTextField.setText(org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseDirTextField.text_1")); // NOI18N

        caseTypeButtonGroup.add(rbSingleUserCase);
        org.openide.awt.Mnemonics.setLocalizedText(rbSingleUserCase, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.rbSingleUserCase.text")); // NOI18N
        rbSingleUserCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rbSingleUserCaseActionPerformed(evt);
            }
        });

        caseTypeButtonGroup.add(rbMultiUserCase);
        org.openide.awt.Mnemonics.setLocalizedText(rbMultiUserCase, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.rbMultiUserCase.text")); // NOI18N
        rbMultiUserCase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rbMultiUserCaseActionPerformed(evt);
            }
        });

        lbBadMultiUserSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        lbBadMultiUserSettings.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbBadMultiUserSettings, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.lbBadMultiUserSettings.text")); // NOI18N

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.errorLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(caseDirTextField, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(0, 58, Short.MAX_VALUE)
                                        .addComponent(lbBadMultiUserSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 372, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(jLabel1)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(caseDirLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(caseParentDirTextField))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(caseNameLabel)
                                        .addGap(26, 26, 26)
                                        .addComponent(caseNameTextField)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(caseDirBrowseButton)))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(rbSingleUserCase)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(rbMultiUserCase))
                            .addComponent(errorLabel))
                        .addGap(0, 0, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addComponent(jLabel1)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNameLabel)
                    .addComponent(caseNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseDirLabel)
                    .addComponent(caseParentDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseDirBrowseButton))
                .addGap(18, 18, 18)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(caseDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(rbSingleUserCase)
                    .addComponent(rbMultiUserCase))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addGap(1, 1, 1)
                .addComponent(lbBadMultiUserSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * The action when the Browse button is pressed. The browse button will pop
     * up the file chooser window to choose where the user wants to save the
     * case directory.
     *
     * @param evt the action event
     */
    private void caseDirBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_caseDirBrowseButtonActionPerformed
        // show the directory chooser where the case directory will be created
        fc.setDragEnabled(false);
        if (!caseParentDirTextField.getText().trim().equals("")) {
            fc.setCurrentDirectory(new File(caseParentDirTextField.getText()));
        }
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        //fc.setSelectedFile(new File("C:\\Program Files\\"));
        //disableTextField(fc); // disable all the text field on the file chooser

        int returnValue = fc.showDialog((Component) evt.getSource(), NbBundle.getMessage(this.getClass(),
                "NewCaseVisualPanel1.caseDirBrowse.selectButton.text"));
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            caseParentDirTextField.setText(path); // put the path to the textfield
        }
    }//GEN-LAST:event_caseDirBrowseButtonActionPerformed

    private void rbSingleUserCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rbSingleUserCaseActionPerformed
        this.wizPanel.fireChangeEvent();
        updateUI(null); // DocumentEvent is not used inside updateUI
    }//GEN-LAST:event_rbSingleUserCaseActionPerformed

    private void rbMultiUserCaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rbMultiUserCaseActionPerformed
        this.wizPanel.fireChangeEvent();
        updateUI(null); // DocumentEvent is not used inside updateUI
    }//GEN-LAST:event_rbMultiUserCaseActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton caseDirBrowseButton;
    private javax.swing.JLabel caseDirLabel;
    private javax.swing.JTextField caseDirTextField;
    private javax.swing.JLabel caseNameLabel;
    private javax.swing.JTextField caseNameTextField;
    private javax.swing.JTextField caseParentDirTextField;
    private javax.swing.ButtonGroup caseTypeButtonGroup;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel lbBadMultiUserSettings;
    private javax.swing.JRadioButton rbMultiUserCase;
    private javax.swing.JRadioButton rbSingleUserCase;
    // End of variables declaration//GEN-END:variables

    /**
     * Gives notification that there was an insert into the document. The range
     * given by the DocumentEvent bounds the freshly inserted region.
     *
     * @param e the document event
     */
    @Override
    public void insertUpdate(DocumentEvent e) {
        this.wizPanel.fireChangeEvent();
        updateUI(e);
    }

    /**
     * Gives notification that a portion of the document has been removed. The
     * range is given in terms of what the view last saw (that is, before
     * updating sticky positions).
     *
     * @param e the document event
     */
    @Override
    public void removeUpdate(DocumentEvent e) {
        this.wizPanel.fireChangeEvent();
        updateUI(e);
    }

    /**
     * Gives notification that an attribute or set of attributes changed.
     *
     * @param e the document event
     */
    @Override
    public void changedUpdate(DocumentEvent e) {
        this.wizPanel.fireChangeEvent();
        updateUI(e);
    }

    /**
     * The "listener" that listens when the fields in this form are updated.
     * This method is used to determine when to enable / disable the "Finish"
     * button.
     *
     * @param e the document event
     */
    public void updateUI(DocumentEvent e) {
        
        // Note: DocumentEvent e can be null when called from rbSingleUserCaseActionPerformed()
        // and rbMultiUserCaseActionPerformed().

        String caseName = getCaseName();
        String parentDir = getCaseParentDir();
        
        if (!isImagePathValid(parentDir)) {
            wizPanel.setIsFinish(false);
            return;
        }        

        if (!caseName.equals("") && !parentDir.equals("")) {
            caseDirTextField.setText(parentDir + caseName);
            wizPanel.setIsFinish(true);
        } else {
            caseDirTextField.setText("");
            wizPanel.setIsFinish(false);
        }
    }
    
    /**
     * Validates path to selected data source. Calls WizardPathValidator service provider
     * if one is available. Otherwise performs path validation locally.
     * @param path Absolute path to the selected data source
     * @return true if path is valid, false otherwise.
     */
    private boolean isImagePathValid(String path){
        
        errorLabel.setVisible(false);
        String errorString = "";
        
        if (path.isEmpty()) {
            return false;   // no need for error message as the module sets path to "" at startup
        }

        // check if the is a WizardPathValidator service provider
        if (!pathValidatorList.isEmpty()) {
            // call WizardPathValidator service provider
            errorString = pathValidatorList.get(0).validateDataSourcePath(path, getCaseType());
        } else {
            // validate locally            
            if (getCaseType() == Case.CaseType.MULTI_USER_CASE) {
                // check that path is not on "C:" drive
                if (pathOnCDrive(path)) {
                    errorString = NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.CaseFolderOnCDriveError.text");  //NON-NLS
                } 
            } else {
                // single user case - no validation needed
            }
        }
        
        // set error string
        if (!errorString.isEmpty()){
            errorLabel.setVisible(true);
            errorLabel.setText(errorString);
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks whether a file path contains drive letter defined by pattern.
     *
     * @param filePath Input file absolute path
     * @return true if path matches the pattern, false otherwise.
     */
    private boolean pathOnCDrive(String filePath) {
        Matcher m = driveLetterPattern.matcher(filePath);
        return m.find();
    }      
    
    /**
     * Discovers WizardPathValidator service providers
     */
    private void discoverWizardPathValidators() {
        for (WizardPathValidator pathValidator : Lookup.getDefault().lookupAll(WizardPathValidator.class)) {
            pathValidatorList.add(pathValidator);
        }
    }     
}
