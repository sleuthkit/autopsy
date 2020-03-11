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
package org.sleuthkit.autopsy.casemodule;

import org.sleuthkit.autopsy.featureaccess.FeatureAccessUtils;
import java.awt.Component;
import org.openide.util.NbBundle;

import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.PathValidator;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * The JPanel for the first page of the new case wizard.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class NewCaseVisualPanel1 extends JPanel implements DocumentListener {

    private final JFileChooser fileChooser = new JFileChooser();
    private final NewCaseWizardPanel1 wizPanel;

    /**
     * Constructs the JPanel for the first page of the new case wizard.
     *
     * @param wizPanel The wizard panmel that owns this panel.
     */
    NewCaseVisualPanel1(NewCaseWizardPanel1 wizPanel) {
        this.wizPanel = wizPanel;
        initComponents();
        TextFieldListener listener = new TextFieldListener();
        caseNameTextField.getDocument().addDocumentListener(listener);
        caseParentDirTextField.getDocument().addDocumentListener(listener);
        caseParentDirWarningLabel.setVisible(false);
    }

    /**
     * Should be called by the readSettings() of the wizard panel that owns this
     * UI panel so that this panel can read settings for each invocation of the
     * wizard as well.
     */
    void readSettings() {
        caseNameTextField.setText("");
        if (FeatureAccessUtils.canCreateMultiUserCases()) {
            multiUserCaseRadioButton.setEnabled(true);
            multiUserCaseRadioButton.setSelected(true);
        } else {
            multiUserCaseRadioButton.setEnabled(false);
            singleUserCaseRadioButton.setSelected(true);
        }
        validateSettings();
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
    String getCaseName() {
        return this.caseNameTextField.getText().trim();
    }

    /**
     * Allows the the wizard panel that owns this UI panel to set the base case
     * directory to a persisted vlaue.
     *
     * @param caseParentDir The persisted path to the base case directory.
     */
    void setCaseParentDir(String caseParentDir) {
        caseParentDirTextField.setText(caseParentDir);
        validateSettings();
    }

    /**
     * Gets the base directory that the user typed on the base directory text
     * field. Will add file separator if it was not added.
     *
     * @return baseDirectory the base directory from the case dir text field
     */
    String getCaseParentDir() {
        String parentDir = this.caseParentDirTextField.getText().trim();
        if (parentDir.endsWith(File.separator) == false) {
            parentDir = parentDir + File.separator;
        }
        return parentDir;
    }

    /**
     * Gets the case type.
     *
     * @return CaseType as set via radio buttons
     */
    CaseType getCaseType() {
        CaseType value = CaseType.SINGLE_USER_CASE;
        if (singleUserCaseRadioButton.isSelected()) {
            value = CaseType.SINGLE_USER_CASE;
        } else if (multiUserCaseRadioButton.isSelected()) {
            value = CaseType.MULTI_USER_CASE;
        }
        return value;
    }

    /**
     * Called when the user interacts with a child UI component of this panel,
     * this method notifies the wizard panel that owns this panel and then
     * validates the user's settings.
     */
    private void handleUpdate() {
        wizPanel.fireChangeEvent();
        validateSettings();
    }

    /**
     * Does validation of the current settings and enables or disables the
     * "Next" button of the wizard panel that owns this panel.
     */
    private void validateSettings() {
        /**
         * Check the base case directory for the selected case type and show a
         * warning if it is a dubious choice.
         */
        caseParentDirWarningLabel.setVisible(false);
        String parentDir = getCaseParentDir();
        if (!PathValidator.isValidForMultiUserCase(parentDir, getCaseType())) {
            caseParentDirWarningLabel.setVisible(true);
            caseParentDirWarningLabel.setText(NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.CaseFolderOnCDriveError.text"));
        }
        
        /**
         * Check the base case directory if it can persist data and show a 
         * warning if it is a wrong choice
         */
        if(!PathValidator.isValidForRunningOnTarget(parentDir)){
            caseParentDirWarningLabel.setVisible(true);
            if(PlatformUtil.isWindowsOS()){
                caseParentDirWarningLabel.setText(NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.CaseFolderOnInternalDriveWindowsError.text" ));
            } else if(System.getProperty("os.name").toLowerCase().contains("nux")) {
                caseParentDirWarningLabel.setText(NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel1.CaseFolderOnInternalDriveLinuxError.text"));
            }
        }

        /**
         * Enable the "Next" button for the wizard if there is text entered for
         * the case name and base case directory. Also make sure that multi-user
         * cases are enabled if the multi-user case radio button is selected.
         */
        String caseName = getCaseName();
        if (!caseName.equals("") && !parentDir.equals("")) {
            caseDirTextField.setText(parentDir + caseName);
            wizPanel.setIsFinish(true);
        } else {
            caseDirTextField.setText("");
            wizPanel.setIsFinish(false);
        }
    }

    /**
     * Handles validation when the user provides input to text field components
     * of this panel.
     */
    private class TextFieldListener implements DocumentListener {

        @Override
        public void insertUpdate(DocumentEvent e) {
            handleUpdate();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            handleUpdate();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            handleUpdate();
        }

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        caseTypeButtonGroup = new javax.swing.ButtonGroup();
        caseNameLabel = new javax.swing.JLabel();
        caseDirLabel = new javax.swing.JLabel();
        caseNameTextField = new javax.swing.JTextField();
        caseParentDirTextField = new javax.swing.JTextField();
        caseDirBrowseButton = new javax.swing.JButton();
        caseDataStoredLabel = new javax.swing.JLabel();
        caseDirTextField = new javax.swing.JTextField();
        singleUserCaseRadioButton = new javax.swing.JRadioButton();
        multiUserCaseRadioButton = new javax.swing.JRadioButton();
        caseParentDirWarningLabel = new javax.swing.JLabel();
        caseTypeLabel = new javax.swing.JLabel();

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

        org.openide.awt.Mnemonics.setLocalizedText(caseDataStoredLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseDataStoredLabel.text_1")); // NOI18N

        caseDirTextField.setEditable(false);
        caseDirTextField.setText(org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseDirTextField.text_1")); // NOI18N

        caseTypeButtonGroup.add(singleUserCaseRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(singleUserCaseRadioButton, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.singleUserCaseRadioButton.text")); // NOI18N
        singleUserCaseRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                singleUserCaseRadioButtonActionPerformed(evt);
            }
        });

        caseTypeButtonGroup.add(multiUserCaseRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(multiUserCaseRadioButton, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.multiUserCaseRadioButton.text")); // NOI18N
        multiUserCaseRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                multiUserCaseRadioButtonActionPerformed(evt);
            }
        });

        caseParentDirWarningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(caseParentDirWarningLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseParentDirWarningLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(caseTypeLabel, org.openide.util.NbBundle.getMessage(NewCaseVisualPanel1.class, "NewCaseVisualPanel1.caseTypeLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(caseDataStoredLabel)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(caseDirTextField, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(caseNameLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(caseNameTextField))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(caseDirLabel)
                                            .addComponent(caseTypeLabel))
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(singleUserCaseRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(multiUserCaseRadioButton)
                                                .addGap(0, 192, Short.MAX_VALUE))
                                            .addComponent(caseParentDirTextField))))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(caseDirBrowseButton)))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(caseParentDirWarningLabel)
                        .addGap(0, 0, Short.MAX_VALUE))))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {caseDirLabel, caseNameLabel, caseTypeLabel});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNameLabel)
                    .addComponent(caseNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseDirLabel)
                    .addComponent(caseParentDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseDirBrowseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(singleUserCaseRadioButton)
                    .addComponent(multiUserCaseRadioButton)
                    .addComponent(caseTypeLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(caseDataStoredLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(caseDirTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(27, 27, 27)
                .addComponent(caseParentDirWarningLabel)
                .addContainerGap(115, Short.MAX_VALUE))
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
        fileChooser.setDragEnabled(false);
        if (!caseParentDirTextField.getText().trim().equals("")) {
            fileChooser.setCurrentDirectory(new File(caseParentDirTextField.getText()));
        }
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int choice = fileChooser.showDialog((Component) evt.getSource(), NbBundle.getMessage(this.getClass(),
                "NewCaseVisualPanel1.caseDirBrowse.selectButton.text"));
        if (JFileChooser.APPROVE_OPTION == choice) {
            String path = fileChooser.getSelectedFile().getPath();
            caseParentDirTextField.setText(path);
        }
    }//GEN-LAST:event_caseDirBrowseButtonActionPerformed

    private void singleUserCaseRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_singleUserCaseRadioButtonActionPerformed
        handleUpdate();
    }//GEN-LAST:event_singleUserCaseRadioButtonActionPerformed

    private void multiUserCaseRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_multiUserCaseRadioButtonActionPerformed
        handleUpdate();
    }//GEN-LAST:event_multiUserCaseRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caseDataStoredLabel;
    private javax.swing.JButton caseDirBrowseButton;
    private javax.swing.JLabel caseDirLabel;
    private javax.swing.JTextField caseDirTextField;
    private javax.swing.JLabel caseNameLabel;
    private javax.swing.JTextField caseNameTextField;
    private javax.swing.JTextField caseParentDirTextField;
    private javax.swing.JLabel caseParentDirWarningLabel;
    private javax.swing.ButtonGroup caseTypeButtonGroup;
    private javax.swing.JLabel caseTypeLabel;
    private javax.swing.JRadioButton multiUserCaseRadioButton;
    private javax.swing.JRadioButton singleUserCaseRadioButton;
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

        String caseName = getCaseName();
        String parentDir = getCaseParentDir();

        if (!caseName.equals("") && !parentDir.equals("")) {
            caseDirTextField.setText(parentDir + caseName);
            wizPanel.setIsFinish(true);
        } else {
            caseDirTextField.setText("");
            wizPanel.setIsFinish(false);
        }
    }
}
