/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import java.awt.Dialog;
import java.util.logging.Level;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoOrganization;
import org.sleuthkit.autopsy.centralrepository.optionspanel.ManageOrganizationsDialog;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Panel which allows for editing and setting of the case details which are
 * optional or otherwise able to be edited.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class OptionalCasePropertiesPanel extends javax.swing.JPanel {

    private final static Logger logger = Logger.getLogger(OptionalCasePropertiesPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private CentralRepoOrganization selectedOrg = null;
    private java.util.List<CentralRepoOrganization> orgs = null;

    /**
     * Creates new form OptionalCasePropertiesPanel
     */
    OptionalCasePropertiesPanel() {
        initComponents();
        caseDisplayNameLabel.setVisible(false);
        caseDisplayNameTextField.setVisible(false);
        lbPointOfContactNameLabel.setVisible(false);
        lbPointOfContactNameText.setVisible(false);
        lbPointOfContactPhoneLabel.setVisible(false);
        lbPointOfContactPhoneText.setVisible(false);
        lbPointOfContactEmailLabel.setVisible(false);
        lbPointOfContactEmailText.setVisible(false);
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
            setUpCaseDetailsFields();
            setUpOrganizationData();
        } else {
            caseDisplayNameLabel.setVisible(false);
            caseDisplayNameTextField.setVisible(false);
            lbPointOfContactNameLabel.setVisible(false);
            lbPointOfContactNameText.setVisible(false);
            lbPointOfContactPhoneLabel.setVisible(false);
            lbPointOfContactPhoneText.setVisible(false);
            lbPointOfContactEmailLabel.setVisible(false);
            lbPointOfContactEmailText.setVisible(false);
            setUpCaseDetailsFields();
        }

    }

    private void setUpOrganizationData() {
        if (CentralRepository.isEnabled()) {
            try {
                Case currentCase = Case.getCurrentCaseThrows();
                if (currentCase != null) {
                    CentralRepository dbManager = CentralRepository.getInstance();
                    selectedOrg = dbManager.getCase(currentCase).getOrg();
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Unable to get Organization associated with the case from Central Repo", ex);
            } catch (NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            }
            
            if (selectedOrg != null) {
                setCurrentlySelectedOrganization(selectedOrg.getName());
            }
            else {
                setCurrentlySelectedOrganization(CentralRepoDbUtil.getDefaultOrgName());
            }
        }
    }

    void setUpCaseDetailsFields() {
        boolean cREnabled = CentralRepository.isEnabled();
        comboBoxOrgName.setEnabled(cREnabled);
        bnNewOrganization.setEnabled(cREnabled);
        lbPointOfContactNameText.setEnabled(cREnabled);
        lbPointOfContactEmailText.setEnabled(cREnabled);
        lbPointOfContactPhoneText.setEnabled(cREnabled);
        lbOrganizationNameLabel.setEnabled(cREnabled);
        lbPointOfContactNameLabel.setEnabled(cREnabled);
        lbPointOfContactEmailLabel.setEnabled(cREnabled);
        lbPointOfContactPhoneLabel.setEnabled(cREnabled);
        orgainizationPanel.setEnabled(cREnabled);
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
            CentralRepository dbManager = CentralRepository.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                comboBoxOrgName.addItem(org.getName());
            });
            comboBoxOrgName.setSelectedItem(selectedBeforeLoad);
        } catch (CentralRepoException ex) {
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
            return CentralRepoDbUtil.getDefaultOrgName();
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

    @Messages({"OptionalCasePropertiesPanel.caseDisplayNameLabel.text=Name:",
        "OptionalCasePropertiesPanel.lbPointOfContactEmailLabel.text=Email:",
        "OptionalCasePropertiesPanel.lbOrganizationNameLabel.text=Organization analysis is being done for:",
        "OptionalCasePropertiesPanel.bnNewOrganization.text=Manage Organizations",
        "OptionalCasePropertiesPanel.lbPointOfContactNameLabel.text=Point of Contact:",
        "OptionalCasePropertiesPanel.lbPointOfContactPhoneLabel.text=Phone:",
        "OptionalCasePropertiesPanel.orgainizationPanel.border.title=Organization",
        "OptionalCasePropertiesPanel.lbNotesLabel.text=Notes:",
        "OptionalCasePropertiesPanel.examinerLabel.text=Name:",
        "OptionalCasePropertiesPanel.lbExaminerEmailLabel.text=Email:",
        "OptionalCasePropertiesPanel.lbExaminerPhoneLabel.text=Phone:",
        "OptionalCasePropertiesPanel.examinerPanel.border.title=Examiner",
        "OptionalCasePropertiesPanel.caseNumberLabel.text=Number:",
        "OptionalCasePropertiesPanel.casePanel.border.title=Case"
    })
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        casePanel = new javax.swing.JPanel();
        caseNumberLabel = new javax.swing.JLabel();
        caseNumberTextField = new javax.swing.JTextField();
        caseDisplayNameLabel = new javax.swing.JLabel();
        caseDisplayNameTextField = new javax.swing.JTextField();
        examinerPanel = new javax.swing.JPanel();
        tfExaminerPhoneText = new javax.swing.JTextField();
        lbExaminerPhoneLabel = new javax.swing.JLabel();
        caseNotesScrollPane = new javax.swing.JScrollPane();
        taNotesText = new javax.swing.JTextArea();
        tfExaminerEmailText = new javax.swing.JTextField();
        examinerTextField = new javax.swing.JTextField();
        lbExaminerEmailLabel = new javax.swing.JLabel();
        examinerLabel = new javax.swing.JLabel();
        lbNotesLabel = new javax.swing.JLabel();
        orgainizationPanel = new javax.swing.JPanel();
        lbPointOfContactPhoneLabel = new javax.swing.JLabel();
        comboBoxOrgName = new javax.swing.JComboBox<>();
        lbPointOfContactNameLabel = new javax.swing.JLabel();
        bnNewOrganization = new javax.swing.JButton();
        lbPointOfContactEmailText = new javax.swing.JLabel();
        lbPointOfContactNameText = new javax.swing.JLabel();
        lbOrganizationNameLabel = new javax.swing.JLabel();
        lbPointOfContactEmailLabel = new javax.swing.JLabel();
        lbPointOfContactPhoneText = new javax.swing.JLabel();

        casePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.casePanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(caseNumberLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseNumberLabel.text")); // NOI18N
        caseNumberLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        caseNumberLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        caseNumberLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(caseDisplayNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseDisplayNameLabel.text")); // NOI18N
        caseDisplayNameLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setPreferredSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setVerifyInputWhenFocusTarget(false);

        javax.swing.GroupLayout casePanelLayout = new javax.swing.GroupLayout(casePanel);
        casePanel.setLayout(casePanelLayout);
        casePanelLayout.setHorizontalGroup(
            casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(caseNumberLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 60, Short.MAX_VALUE)
                    .addComponent(caseDisplayNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseNumberTextField)
                    .addComponent(caseDisplayNameTextField))
                .addContainerGap())
        );
        casePanelLayout.setVerticalGroup(
            casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseDisplayNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseDisplayNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(caseNumberLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseNumberTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        examinerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.examinerPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerPhoneLabel.text")); // NOI18N
        lbExaminerPhoneLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        caseNotesScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        taNotesText.setColumns(20);
        taNotesText.setLineWrap(true);
        taNotesText.setRows(2);
        taNotesText.setWrapStyleWord(true);
        taNotesText.setBorder(null);
        caseNotesScrollPane.setViewportView(taNotesText);

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerEmailLabel.text")); // NOI18N
        lbExaminerEmailLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(examinerLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.examinerLabel.text")); // NOI18N
        examinerLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        examinerLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        examinerLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbNotesLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbNotesLabel.text")); // NOI18N
        lbNotesLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbNotesLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbNotesLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        javax.swing.GroupLayout examinerPanelLayout = new javax.swing.GroupLayout(examinerPanel);
        examinerPanel.setLayout(examinerPanelLayout);
        examinerPanelLayout.setHorizontalGroup(
            examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(examinerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(examinerPanelLayout.createSequentialGroup()
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(caseNotesScrollPane)
                            .addComponent(tfExaminerPhoneText)))
                    .addGroup(examinerPanelLayout.createSequentialGroup()
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(examinerLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(examinerTextField)
                            .addComponent(tfExaminerEmailText))))
                .addGap(11, 11, 11))
        );

        examinerPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {examinerLabel, lbExaminerEmailLabel, lbExaminerPhoneLabel, lbNotesLabel});

        examinerPanelLayout.setVerticalGroup(
            examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(examinerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(examinerLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(examinerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseNotesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        orgainizationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.orgainizationPanel.border.title"))); // NOI18N
        orgainizationPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactPhoneLabel.text")); // NOI18N
        lbPointOfContactPhoneLabel.setMaximumSize(new java.awt.Dimension(182, 14));
        lbPointOfContactPhoneLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactPhoneLabel.setPreferredSize(new java.awt.Dimension(120, 14));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(7, 12, 0, 0);
        orgainizationPanel.add(lbPointOfContactPhoneLabel, gridBagConstraints);

        comboBoxOrgName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxOrgNameActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 18, 0, 0);
        orgainizationPanel.add(comboBoxOrgName, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactNameLabel.text")); // NOI18N
        lbPointOfContactNameLabel.setMaximumSize(new java.awt.Dimension(182, 14));
        lbPointOfContactNameLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactNameLabel.setPreferredSize(new java.awt.Dimension(120, 14));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(13, 12, 0, 5);
        orgainizationPanel.add(lbPointOfContactNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.setMargin(new java.awt.Insets(2, 6, 2, 6));
        bnNewOrganization.setMaximumSize(new java.awt.Dimension(160, 23));
        bnNewOrganization.setMinimumSize(new java.awt.Dimension(123, 23));
        bnNewOrganization.setPreferredSize(new java.awt.Dimension(140, 23));
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 12, 0, 18);
        orgainizationPanel.add(bnNewOrganization, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(7, 4, 13, 18);
        orgainizationPanel.add(lbPointOfContactEmailText, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(13, 4, 0, 18);
        orgainizationPanel.add(lbPointOfContactNameText, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbOrganizationNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbOrganizationNameLabel.text")); // NOI18N
        lbOrganizationNameLabel.setMaximumSize(new java.awt.Dimension(300, 14));
        lbOrganizationNameLabel.setMinimumSize(new java.awt.Dimension(189, 14));
        lbOrganizationNameLabel.setPreferredSize(new java.awt.Dimension(220, 14));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
        orgainizationPanel.add(lbOrganizationNameLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactEmailLabel.text")); // NOI18N
        lbPointOfContactEmailLabel.setMaximumSize(new java.awt.Dimension(182, 14));
        lbPointOfContactEmailLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactEmailLabel.setPreferredSize(new java.awt.Dimension(120, 14));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(7, 12, 13, 0);
        orgainizationPanel.add(lbPointOfContactEmailLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(7, 4, 0, 18);
        orgainizationPanel.add(lbPointOfContactPhoneText, gridBagConstraints);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(casePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(examinerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(orgainizationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(6, 6, 6))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(casePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(examinerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(orgainizationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0))
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
        for (CentralRepoOrganization org : orgs) {
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
        ManageOrganizationsDialog dialog = new ManageOrganizationsDialog((Dialog) SwingUtilities.getWindowAncestor(this));
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
        comboBoxOrgName.setSelectedItem(orgName == null ? CentralRepoDbUtil.getDefaultOrgName() : orgName);
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
                logger.log(Level.WARNING, "Error updating case details", ex);
            }
        }
    }

    /**
     * Save changed value from text fields and text areas into the EamCase
     * object.
     */
    private void updateCorrelationCase() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (CentralRepository.isEnabled()) {
            try {
                CentralRepository dbManager = CentralRepository.getInstance();
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
            } catch (CentralRepoException ex) {
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
    private javax.swing.JPanel casePanel;
    private javax.swing.JComboBox<String> comboBoxOrgName;
    private javax.swing.JLabel examinerLabel;
    private javax.swing.JPanel examinerPanel;
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
    private javax.swing.JPanel orgainizationPanel;
    private javax.swing.JTextArea taNotesText;
    private javax.swing.JTextField tfExaminerEmailText;
    private javax.swing.JTextField tfExaminerPhoneText;
    // End of variables declaration//GEN-END:variables
}