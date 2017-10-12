/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import org.sleuthkit.autopsy.centralrepository.actions.EamCaseEditDetailsDialog;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.centralrepository.optionspanel.ManageOrganizationsDialog;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 *
 * @author wschaefer
 */
class OptionalCasePropertiesPanel extends javax.swing.JPanel {

    private final static Logger LOGGER = Logger.getLogger(EamCaseEditDetailsDialog.class.getName());
    private static final long serialVersionUID = 1L;
    private EamOrganization selectedOrg = null;
    private java.util.List<EamOrganization> orgs = null;
    private EamDb dbManager;

    /**
     * Creates new form OptionalCasePropertiesPanel
     */
    OptionalCasePropertiesPanel() {
        initComponents();
        caseDisplayNameLabel.setVisible(false);
        caseDisplayNameTextField.setVisible(false);
    }

    OptionalCasePropertiesPanel(boolean editCurrentCase) {
        initComponents();
        if (editCurrentCase) {
            caseDisplayNameTextField.setText(Case.getCurrentCase().getDisplayName());
            caseNumberTextField.setText(Case.getCurrentCase().getNumber());
            examinerTextField.setText(Case.getCurrentCase().getExaminer());
            tfExaminerEmailText.setText(Case.getCurrentCase().getExaminerEmail());
            tfExaminerPhoneText.setText(Case.getCurrentCase().getExaminerPhone());
            taNotesText.setText(Case.getCurrentCase().getExaminerNotes());
            try {
                this.dbManager = EamDb.getInstance();
                if (dbManager != null) {
                    selectedOrg = dbManager.getCaseByUUID(Case.getCurrentCase().getName()).getOrg();
                }
            } catch (EamDbException ex) {
                dbManager = null;
            }

        }

    }

    void setUpCentralRepoFields() {
        try {
            this.dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            dbManager = null;
        }
        boolean cREnabled = (dbManager != null);
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

        if (cREnabled) {
            loadOrganizationData();
        } else {
            selectedOrg = null;
            clearOrganization();
        }
    }

    private void loadOrganizationData() {

        comboBoxOrgName.removeAllItems();
        try {
            orgs = dbManager.getOrganizations();
            comboBoxOrgName.addItem(""); // for when a case has a null Org
            orgs.forEach((org) -> {
                comboBoxOrgName.addItem(org.getName());
            });
        } catch (EamDbException ex) {
            selectedOrg = null;
        }

        if (null != selectedOrg) {
            comboBoxOrgName.setSelectedItem(selectedOrg.getName());
            lbPointOfContactNameText.setText(selectedOrg.getPocName());
            lbPointOfContactEmailText.setText(selectedOrg.getPocEmail());
            lbPointOfContactPhoneText.setText(selectedOrg.getPocPhone());
        } else {
            clearOrganization();
        }
    }

    private void clearOrganization() {
        comboBoxOrgName.setSelectedItem("");
        lbPointOfContactNameText.setText("");
        lbPointOfContactEmailText.setText("");
        lbPointOfContactPhoneText.setText("");
    }

    String getCaseNumber() {
        return caseNumberTextField.getText();
    }

    Examiner getExaminer() {
        return new Examiner(examinerTextField.getText(), tfExaminerPhoneText.getText(), tfExaminerEmailText.getText(), taNotesText.getText());
    }

    String getOrganization() {
        if (selectedOrg != null) {
            return selectedOrg.getName();
        } else {
            return "";
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        casePanel = new javax.swing.JPanel();
        caseNumberLabel = new javax.swing.JLabel();
        caseNumberTextField = new javax.swing.JTextField();
        caseDisplayNameLabel = new javax.swing.JLabel();
        caseDisplayNameTextField = new javax.swing.JTextField();
        examinerPanel = new javax.swing.JPanel();
        tfExaminerPhoneText = new javax.swing.JTextField();
        lbExaminerPhoneLabel = new javax.swing.JLabel();
        examinerNotesScrollPane = new javax.swing.JScrollPane();
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

        caseNumberLabel.setFont(caseNumberLabel.getFont().deriveFont(caseNumberLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(caseNumberLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseNumberLabel.text")); // NOI18N

        caseNumberTextField.setFont(caseNumberTextField.getFont().deriveFont(caseNumberTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        caseNumberTextField.setText(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseNumberTextField.text")); // NOI18N

        caseDisplayNameLabel.setFont(caseDisplayNameLabel.getFont().deriveFont(caseDisplayNameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(caseDisplayNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.caseDisplayNameLabel.text")); // NOI18N
        caseDisplayNameLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        caseDisplayNameLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        caseDisplayNameTextField.setFont(caseDisplayNameTextField.getFont().deriveFont(caseDisplayNameTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        javax.swing.GroupLayout casePanelLayout = new javax.swing.GroupLayout(casePanel);
        casePanel.setLayout(casePanelLayout);
        casePanelLayout.setHorizontalGroup(
            casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseNumberLabel)
                    .addComponent(caseDisplayNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseDisplayNameTextField)
                    .addComponent(caseNumberTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 458, Short.MAX_VALUE))
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
                    .addComponent(caseNumberLabel)
                    .addComponent(caseNumberTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        examinerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.examinerPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerPhoneLabel.text")); // NOI18N
        lbExaminerPhoneLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerPhoneLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        examinerNotesScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        taNotesText.setColumns(20);
        taNotesText.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        taNotesText.setLineWrap(true);
        taNotesText.setRows(2);
        taNotesText.setWrapStyleWord(true);
        taNotesText.setBorder(null);
        examinerNotesScrollPane.setViewportView(taNotesText);

        examinerTextField.setFont(examinerTextField.getFont().deriveFont(examinerTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        examinerTextField.setText(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.examinerTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbExaminerEmailLabel.text")); // NOI18N
        lbExaminerEmailLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbExaminerEmailLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        examinerLabel.setFont(examinerLabel.getFont().deriveFont(examinerLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
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
                        .addGap(10, 10, 10)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tfExaminerPhoneText)
                            .addComponent(examinerNotesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 458, Short.MAX_VALUE)))
                    .addGroup(examinerPanelLayout.createSequentialGroup()
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(examinerLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(examinerTextField)
                            .addComponent(tfExaminerEmailText))))
                .addContainerGap())
        );
        examinerPanelLayout.setVerticalGroup(
            examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(examinerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(examinerLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(examinerTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(examinerNotesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        orgainizationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.orgainizationPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactPhoneLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactPhoneLabel.text")); // NOI18N

        comboBoxOrgName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxOrgNameActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.setMargin(new java.awt.Insets(2, 6, 2, 6));
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbOrganizationNameLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbOrganizationNameLabel.text")); // NOI18N
        lbOrganizationNameLabel.setMaximumSize(new java.awt.Dimension(41, 14));
        lbOrganizationNameLabel.setMinimumSize(new java.awt.Dimension(41, 14));
        lbOrganizationNameLabel.setPreferredSize(new java.awt.Dimension(41, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactEmailLabel, org.openide.util.NbBundle.getMessage(OptionalCasePropertiesPanel.class, "OptionalCasePropertiesPanel.lbPointOfContactEmailLabel.text")); // NOI18N

        javax.swing.GroupLayout orgainizationPanelLayout = new javax.swing.GroupLayout(orgainizationPanel);
        orgainizationPanel.setLayout(orgainizationPanelLayout);
        orgainizationPanelLayout.setHorizontalGroup(
            orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(orgainizationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(orgainizationPanelLayout.createSequentialGroup()
                        .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPointOfContactPhoneLabel)
                            .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbPointOfContactNameLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(orgainizationPanelLayout.createSequentialGroup()
                        .addComponent(comboBoxOrgName, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnNewOrganization)))
                .addContainerGap())
        );
        orgainizationPanelLayout.setVerticalGroup(
            orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(orgainizationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(comboBoxOrgName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bnNewOrganization))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(orgainizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 561, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(orgainizationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(examinerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(casePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addContainerGap()))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 413, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(casePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(examinerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                    .addComponent(orgainizationPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addContainerGap()))
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
        if (dialog.isChanged()) {
            selectedOrg = dialog.getNewOrg();
            loadOrganizationData();
            validate();
            repaint();
        }
    }//GEN-LAST:event_bnNewOrganizationActionPerformed

    private void updateCaseNumber() {
        try {
            Case.getCurrentCase().updateCaseNumber(caseNumberTextField.getText());
        } catch (CaseActionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    void saveUpdatedCaseDetails() {
        updateCaseName();
        updateCaseNumber();
        updateExaminer();
        updateCorrelationCase();
    }

    private void updateCaseName() {
        if (caseDisplayNameTextField.isVisible()) {
            try {
                Case.getCurrentCase().updateDisplayName(caseDisplayNameTextField.getText());
            } catch (CaseActionException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }

    private void updateExaminer() {
        try {
            Case.getCurrentCase().updateExaminer(new Examiner(examinerTextField.getText(), tfExaminerPhoneText.getText(), tfExaminerEmailText.getText(), taNotesText.getText()));
        } catch (CaseActionException ex) {
            MessageNotifyUtil.Message.error(ex.getLocalizedMessage());
            LOGGER.log(Level.SEVERE, "Failed to update case display name", ex); //NON-NLS
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
                CorrelationCase correlationCase = dbManager.getCaseByUUID(Case.getCurrentCase().getName());
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
                LOGGER.log(Level.SEVERE, "Error connecting to central repository database", ex); // NON-NLS
            } finally {
                setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnNewOrganization;
    private javax.swing.JLabel caseDisplayNameLabel;
    private javax.swing.JTextField caseDisplayNameTextField;
    private javax.swing.JLabel caseNumberLabel;
    private javax.swing.JTextField caseNumberTextField;
    private javax.swing.JPanel casePanel;
    private javax.swing.JComboBox<String> comboBoxOrgName;
    private javax.swing.JLabel examinerLabel;
    private javax.swing.JScrollPane examinerNotesScrollPane;
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
