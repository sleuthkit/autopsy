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

import java.awt.Cursor;
import java.util.logging.Level;
import javax.swing.JComboBox;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.centralrepository.optionspanel.ManageOrganizationsDialog;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Panel which allows for editing and setting of the case details which are
 * optional or otherwise able to be edited.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class OptionalCasePropertiesPanel extends javax.swing.JPanel {

    private final static Logger logger = Logger.getLogger(OptionalCasePropertiesPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private EamOrganization selectedOrg = null;
    private java.util.List<EamOrganization> orgs = null;

    /**
     * Creates new form OptionalCasePropertiesPanel
     */
    OptionalCasePropertiesPanel() {
        initComponents();
        caseDisplayNameLabel.setVisible(false);
        caseDisplayNameTextField.setVisible(false);
        setUpCaseDetailsFields();
        setUpOrganizationData();
    }

    OptionalCasePropertiesPanel(boolean editCurrentCase) {
        initComponents();
        if (editCurrentCase) {
            Case openCase;
            try {
                openCase = Case.getCurrentCaseThrows();
            } catch (NoCurrentCaseException ex) { 
                logger.log(Level.SEVERE, "Exception while getting open case.", ex);
                return;
            }
            caseDisplayNameTextField.setText(openCase.getDisplayName());
            caseNumberTextField.setText(openCase.getNumber());
            examinerTextField.setText(openCase.getExaminer());
            tfExaminerEmailText.setText(openCase.getExaminerEmail());
            tfExaminerPhoneText.setText(openCase.getExaminerPhone());
            taNotesText.setText(openCase.getCaseNotes());
        } else {
            caseDisplayNameLabel.setVisible(false);
            caseDisplayNameTextField.setVisible(false);
        }
        
        setUpCaseDetailsFields();
        setUpOrganizationData();

    }

    private void setUpOrganizationData() {
        if (EamDb.isEnabled()) {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                if (currentCase != null) {
                    EamDb dbManager = EamDb.getInstance();
                    selectedOrg = dbManager.getCase(currentCase).getOrg();
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Unable to get Organization associated with the case from Central Repo", ex);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            }
            
            if (selectedOrg != null) {
                setCurrentlySelectedOrganization(selectedOrg.getName());
            }
            else {
                setCurrentlySelectedOrganization(EamDbUtil.getDefaultOrgName());
            }
        }
    }

    void setUpCaseDetailsFields() {
        boolean cREnabled = EamDb.isEnabled();
        comboBoxOrgName.setEnabled(cREnabled);
        bnNewOrganization.setEnabled(cREnabled);
        lbPointOfContactNameText.setEnabled(cREnabled);
        lbPointOfContactEmailText.setEnabled(cREnabled);
        lbPointOfContactPhoneText.setEnabled(cREnabled);
        lbOrganizationNameLabel.setEnabled(cREnabled);
        lbPointOfContactNameLabel.setEnabled(cREnabled);
        lbPointOfContactEmailLabel.setEnabled(cREnabled);
        lbPointOfContactPhoneLabel.setEnabled(cREnabled);
        if (!cREnabled) {
            clearOrganization();
        } else {
            loadOrganizationData();
        }

    }

    private void loadOrganizationData() {
        Object selectedBeforeLoad = comboBoxOrgName.getSelectedItem();
        comboBoxOrgName.removeAllItems();
        try {
            EamDb dbManager = EamDb.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                comboBoxOrgName.addItem(org.getName());
            });
            comboBoxOrgName.setSelectedItem(selectedBeforeLoad);
        } catch (EamDbException ex) {
            logger.log(Level.WARNING, "Unable to populate list of Organizations from Central Repo", ex);
        }
    }

    private void clearOrganization() {
        selectedOrg = null;
        lbPointOfContactNameText.setText("");
        lbPointOfContactEmailText.setText("");
        lbPointOfContactPhoneText.setText("");
    }

    String getCaseNumber() {
        return caseNumberTextField.getText();
    }

    String getExaminerName() {
        return examinerTextField.getText();
    }

    String getExaminerPhone() {
        return tfExaminerPhoneText.getText();
    }

    String getExaminerEmail() {
        return tfExaminerEmailText.getText();
    }

    String getCaseNotes() {
        return taNotesText.getText();
    }

    String getOrganization() {
        if (selectedOrg != null) {
            return selectedOrg.getName();
        } else {
            return EamDbUtil.getDefaultOrgName();
        }
    }

    void setCaseNumberField(String caseNumber) {
        caseNumberTextField.setText(caseNumber == null ? "" : caseNumber);
    }

    void setExaminerNameField(String examinerName) {
        examinerTextField.setText(examinerName == null ? "" : examinerName);
    }

    void setExaminerPhoneField(String examinerPhone) {
        tfExaminerPhoneText.setText(examinerPhone == null ? "" : examinerPhone);
    }

    void setExaminerEmailField(String examinerEmail) {
        tfExaminerEmailText.setText(examinerEmail == null ? "" : examinerEmail);
    }

    void setCaseNotesField(String caseNotes) {
        taNotesText.setText(caseNotes == null ? "" : caseNotes);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        caseDisplayNameTextField = new javax.swing.JTextField();
        caseNumberTextField = new javax.swing.JTextField();
        caseNotesScrollPane = new javax.swing.JScrollPane();
        taNotesText = new javax.swing.JTextArea();
        caseDisplayNameLabel = new javax.swing.JLabel();
        caseNumberLabel = new javax.swing.JLabel();
        lbNotesLabel = new javax.swing.JLabel();
        examinerTextField = new javax.swing.JTextField();
        tfExaminerPhoneText = new javax.swing.JTextField();
        tfExaminerEmailText = new javax.swing.JTextField();
        examinerLabel = new javax.swing.JLabel();
        lbExaminerPhoneLabel = new javax.swing.JLabel();
        lbExaminerEmailLabel = new javax.swing.JLabel();
        lbOrganizationNameLabel = new javax.swing.JLabel();
        comboBoxOrgName = new javax.swing.JComboBox<>();
        lbPointOfContactNameLabel = new javax.swing.JLabel();
        lbPointOfContactPhoneLabel = new javax.swing.JLabel();
        lbPointOfContactEmailLabel = new javax.swing.JLabel();
        bnNewOrganization = new javax.swing.JButton();
        lbPointOfContactNameText = new javax.swing.JLabel();
        lbPointOfContactPhoneText = new javax.swing.JLabel();
        lbPointOfContactEmailText = new javax.swing.JLabel();

        caseDisplayNameTextField.setFont(caseDisplayNameTextField.getFont().deriveFont(caseDisplayNameTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        caseNumberTextField.setFont(caseNumberTextField.getFont().deriveFont(caseNumberTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        caseNotesScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        taNotesText.setColumns(20);
        taNotesText.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        taNotesText.setLineWrap(true);
        taNotesText.setRows(2);
        taNotesText.setWrapStyleWord(true);
        taNotesText.setBorder(null);
        caseNotesScrollPane.setViewportView(taNotesText);

        caseDisplayNameLabel.setFont(caseDisplayNameLabel.getFont().deriveFont(caseDisplayNameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(caseDisplayNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseDisplayNameLabel.text")); // NOI18N
        caseDisplayNameLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setPreferredSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setVerifyInputWhenFocusTarget(false);

        caseNumberLabel.setFont(caseNumberLabel.getFont().deriveFont(caseNumberLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(caseNumberLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseNumberLabel.text_1")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbNotesLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbNotesLabel.text")); // NOI18N
        lbNotesLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbNotesLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbNotesLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        examinerTextField.setFont(examinerTextField.getFont().deriveFont(examinerTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        examinerLabel.setFont(examinerLabel.getFont().deriveFont(examinerLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(examinerLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.examinerLabel.text")); // NOI18N
        examinerLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        examinerLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        examinerLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerPhoneLabel.text")); // NOI18N
        lbExaminerPhoneLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerEmailLabel.text")); // NOI18N
        lbExaminerEmailLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbOrganizationNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbOrganizationNameLabel.text")); // NOI18N

        comboBoxOrgName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxOrgNameActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactPhoneLabel.text")); // NOI18N
        lbPointOfContactPhoneLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactPhoneLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactPhoneLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactEmailLabel.text")); // NOI18N
        lbPointOfContactEmailLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactEmailLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactEmailLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.setMargin(new java.awt.Insets(2, 6, 2, 6));
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
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
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                    .addComponent(caseNumberLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 109, Short.MAX_VALUE)
                                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(examinerLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(caseDisplayNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGap(6, 6, 6)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(caseNotesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 398, Short.MAX_VALUE)
                                    .addComponent(examinerTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(tfExaminerPhoneText, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(tfExaminerEmailText)
                                    .addComponent(caseDisplayNameTextField)
                                    .addComponent(caseNumberTextField)))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 185, Short.MAX_VALUE)
                                    .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(comboBoxOrgName, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 185, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(10, 10, 10))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(bnNewOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, 147, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(caseDisplayNameTextField)
                    .addComponent(caseDisplayNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNumberLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseNumberTextField))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseNotesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(examinerLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(examinerTextField))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(tfExaminerPhoneText))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tfExaminerEmailText)
                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(comboBoxOrgName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 20, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(bnNewOrganization)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void comboBoxOrgNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxOrgNameActionPerformed
        @SuppressWarnings("unchecked")
        JComboBox<String> cb = (JComboBox<String>) evt.getSource();
        String orgName = (String) cb.getSelectedItem();
        if (null == orgName) {
            return;
        }
        if ("".equals(orgName)) {
            clearOrganization();
            return;
        }
        for (EamOrganization org : orgs) {
            if (org.getName().equals(orgName)) {
                selectedOrg = org;
                lbPointOfContactNameText.setText(selectedOrg.getPocName());
                lbPointOfContactEmailText.setText(selectedOrg.getPocEmail());
                lbPointOfContactPhoneText.setText(selectedOrg.getPocPhone());
                return;
            }
        }
    }//GEN-LAST:event_comboBoxOrgNameActionPerformed

    private void bnNewOrganizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnNewOrganizationActionPerformed
        ManageOrganizationsDialog dialog = new ManageOrganizationsDialog();
        // update the combobox options and org data fields
        loadOrganizationData();
        if (dialog.isChanged()) {
            selectedOrg = dialog.getNewOrg();
            setCurrentlySelectedOrganization(dialog.getNewOrg().getName());
        }
        validate();
        repaint();
    }//GEN-LAST:event_bnNewOrganizationActionPerformed

    void setCurrentlySelectedOrganization(String orgName) {
        comboBoxOrgName.setSelectedItem(orgName == null ? EamDbUtil.getDefaultOrgName() : orgName);
    }

    @Messages({
        "OptionalCasePropertiesPanel.errorDialog.emptyCaseNameMessage=No case name entered.",
        "OptionalCasePropertiesPanel.errorDialog.invalidCaseNameMessage=Case names cannot include the following symbols: \\, /, :, *, ?, \", <, >, |",
        "OptionalCasePropertiesPanel.errorDialog.noOpenCase.errMsg=Exception while getting open case."
    })
    void saveUpdatedCaseDetails() {
        if (caseDisplayNameTextField.getText().trim().isEmpty()) {
            MessageNotifyUtil.Message.error(Bundle.OptionalCasePropertiesPanel_errorDialog_emptyCaseNameMessage());
            return;
        }
        if (!Case.isValidName(caseDisplayNameTextField.getText())) {
            MessageNotifyUtil.Message.error(Bundle.OptionalCasePropertiesPanel_errorDialog_invalidCaseNameMessage());
            return;
        }
        try {
            updateCaseDetails();
        } catch (NoCurrentCaseException ex) {
            MessageNotifyUtil.Message.error(Bundle.OptionalCasePropertiesPanel_errorDialog_noOpenCase_errMsg());
            return;
        }
        updateCorrelationCase();
    }

    private void updateCaseDetails() throws NoCurrentCaseException {
        if (caseDisplayNameTextField.isVisible()) {
            try {
                Case.getCurrentCaseThrows().updateCaseDetails(new CaseDetails(
                        caseDisplayNameTextField.getText(), caseNumberTextField.getText(),
                        examinerTextField.getText(), tfExaminerPhoneText.getText(),
                        tfExaminerEmailText.getText(), taNotesText.getText()));
            } catch (CaseActionException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    /**
     * Save changed value from text fields and text areas into the EamCase
     * object.
     */
    private void updateCorrelationCase() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (EamDb.isEnabled()) {
            try {
                EamDb dbManager = EamDb.getInstance();
                CorrelationCase correlationCase = dbManager.getCase(Case.getCurrentCaseThrows());
                if (caseDisplayNameTextField.isVisible()) {
                    correlationCase.setDisplayName(caseDisplayNameTextField.getText());
                }
                correlationCase.setOrg(selectedOrg);
                correlationCase.setCaseNumber(caseNumberTextField.getText());
                correlationCase.setExaminerName(examinerTextField.getText());
                correlationCase.setExaminerEmail(tfExaminerEmailText.getText());
                correlationCase.setExaminerPhone(tfExaminerPhoneText.getText());
                correlationCase.setNotes(taNotesText.getText());
                dbManager.updateCase(correlationCase);
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error connecting to central repository database", ex); // NON-NLS  
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex); // NON-NLS
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnNewOrganization;
    private javax.swing.JLabel caseDisplayNameLabel;
    private javax.swing.JTextField caseDisplayNameTextField;
    private javax.swing.JScrollPane caseNotesScrollPane;
    private javax.swing.JLabel caseNumberLabel;
    private javax.swing.JTextField caseNumberTextField;
    private javax.swing.JComboBox<String> comboBoxOrgName;
    private javax.swing.JLabel examinerLabel;
    private javax.swing.JTextField examinerTextField;
    private javax.swing.JLabel lbExaminerEmailLabel;
    private javax.swing.JLabel lbExaminerPhoneLabel;
    private javax.swing.JLabel lbNotesLabel;
    private javax.swing.JLabel lbOrganizationNameLabel;
    private javax.swing.JLabel lbPointOfContactEmailLabel;
    private javax.swing.JLabel lbPointOfContactEmailText;
    private javax.swing.JLabel lbPointOfContactNameLabel;
    private javax.swing.JLabel lbPointOfContactNameText;
    private javax.swing.JLabel lbPointOfContactPhoneLabel;
    private javax.swing.JLabel lbPointOfContactPhoneText;
    private javax.swing.JTextArea taNotesText;
    private javax.swing.JTextField tfExaminerEmailText;
    private javax.swing.JTextField tfExaminerPhoneText;
    // End of variables declaration//GEN-END:variables
}
