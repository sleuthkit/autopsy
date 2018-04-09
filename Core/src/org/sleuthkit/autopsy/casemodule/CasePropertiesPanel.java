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

import java.nio.file.Paths;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A panel that allows the user to view various properties of a case and change
 * the display name of the case.
 */
final class CasePropertiesPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(CasePropertiesPanel.class.getName());
    private Case theCase;

    /**
     * Constructs a panel that allows the user to view various properties of the
     * current case and change the display name of the case.
     *
     * @param aCase A case.
     */
    CasePropertiesPanel(Case caseInfo) {
        initComponents();
        updateCaseInfo();
    }

    void updateCaseInfo() {
        try {
            theCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) { 
            LOGGER.log(Level.SEVERE, "Exception while getting open case.", ex);
            return;
        }
        lbCaseNameText.setText(theCase.getDisplayName());
        lbCaseNumberText.setText(theCase.getNumber());
        lbExaminerNameText.setText(theCase.getExaminer());
        lbExaminerPhoneText.setText(theCase.getExaminerPhone());
        lbExaminerEmailText.setText(theCase.getExaminerEmail());
        taNotesText.setText(theCase.getCaseNotes());
        crDateField.setText(theCase.getCreatedDate());
        caseDirField.setText(theCase.getCaseDirectory());
        if (Case.CaseType.SINGLE_USER_CASE == theCase.getCaseType()) {
            dbNameField.setText(Paths.get(theCase.getCaseDirectory(), theCase.getMetadata().getCaseDatabaseName()).toString());
        } else {
            dbNameField.setText(theCase.getMetadata().getCaseDatabaseName());
        }
        boolean cREnabled = EamDb.isEnabled();
        lbOrganizationNameLabel.setEnabled(cREnabled);
        lbOrganizationNameText.setEnabled(cREnabled);
        lbPointOfContactEmailLabel.setEnabled(cREnabled);
        lbPointOfContactEmailText.setEnabled(cREnabled);
        lbPointOfContactNameLabel.setEnabled(cREnabled);
        lbPointOfContactNameText.setEnabled(cREnabled);
        lbPointOfContactPhoneLabel.setEnabled(cREnabled);
        lbPointOfContactPhoneText.setEnabled(cREnabled);
        pnOrganization.setEnabled(cREnabled);
        EamOrganization currentOrg = null;
        if (cREnabled) {
            try {
                EamDb dbManager = EamDb.getInstance();
                if (dbManager != null) {
                    CorrelationCase correlationCase = dbManager.getCase(theCase);
                    if (null == correlationCase) {
                        correlationCase = dbManager.newCase(theCase);
                    }
                    currentOrg = correlationCase.getOrg();
                }
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Unable to access Correlation Case when Central Repo is enabled", ex);
            }
        }
        if (currentOrg != null) {
            lbOrganizationNameText.setText(currentOrg.getName());
            lbPointOfContactNameText.setText(currentOrg.getPocName());
            lbPointOfContactPhoneText.setText(currentOrg.getPocPhone());
            lbPointOfContactEmailText.setText(currentOrg.getPocEmail());
        } else {
            lbOrganizationNameText.setText("");
            lbPointOfContactNameText.setText("");
            lbPointOfContactPhoneText.setText("");
            lbPointOfContactEmailText.setText("");
        }
        Case.CaseType caseType = theCase.getCaseType();
        caseTypeField.setText(caseType.getLocalizedDisplayName());
        lbCaseUIDText.setText(theCase.getName());
        validate();
        repaint();
    }

    @Messages({"CasePropertiesPanel.casePanel.border.title=Case",
        "CasePropertiesPanel.lbCaseUUIDLabel.text=Case UUID:",
        "CasePropertiesPanel.examinerPanel.border.title=Examiner",
        "CasePropertiesPanel.examinerLabel.text=Name:",
        "CasePropertiesPanel.lbExaminerPhoneLabel.text=Phone:",
        "CasePropertiesPanel.lbExaminerEmailLabel.text=Email:",
        "CasePropertiesPanel.lbNotesLabel.text=Notes:",
        "CasePropertiesPanel.pnOrganization.border.title=Organization",
        "CasePropertiesPanel.lbOrganizationNameLabel.text=Name:",
        "CasePropertiesPanel.lbPointOfContactNameLabel.text=Point of Contact:",
        "CasePropertiesPanel.lbPointOfContactPhoneLabel.text=Phone:",
        "CasePropertiesPanel.lbPointOfContactEmailLabel.text=Email:"})

    /**
     * In this generated code below, there are 2 strings "Path" and "Remove"
     * that are table column headers in the DefaultTableModel. When this model
     * is generated, it puts the hard coded English strings into the generated
     * code. And then about 15 lines later, it separately internationalizes them
     * using: imagesTable.getColumnModel().getColumn(0).setHeaderValue(). There
     * is no way to prevent the GUI designer from putting the hard coded English
     * strings into the generated code. So, they remain, and are not used.
     */
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        detailsPanel = new javax.swing.JPanel();
        casePanel = new javax.swing.JPanel();
        caseNameLabel = new javax.swing.JLabel();
        lbDbType = new javax.swing.JLabel();
        lbCaseUUIDLabel = new javax.swing.JLabel();
        caseTypeField = new javax.swing.JLabel();
        dbNameField = new javax.swing.JLabel();
        lbDbName = new javax.swing.JLabel();
        caseNumberLabel = new javax.swing.JLabel();
        caseDirLabel = new javax.swing.JLabel();
        caseDirField = new javax.swing.JLabel();
        crDateLabel = new javax.swing.JLabel();
        crDateField = new javax.swing.JLabel();
        lbCaseUIDText = new javax.swing.JLabel();
        lbCaseNameText = new javax.swing.JLabel();
        lbCaseNumberText = new javax.swing.JLabel();
        examinerPanel = new javax.swing.JPanel();
        lbExaminerNameText = new javax.swing.JLabel();
        lbNotesLabel = new javax.swing.JLabel();
        examinerLabel = new javax.swing.JLabel();
        caseNotesScrollPane = new javax.swing.JScrollPane();
        taNotesText = new javax.swing.JTextArea();
        lbExaminerEmailLabel = new javax.swing.JLabel();
        lbExaminerPhoneLabel = new javax.swing.JLabel();
        lbExaminerPhoneText = new javax.swing.JLabel();
        lbExaminerEmailText = new javax.swing.JLabel();
        pnOrganization = new javax.swing.JPanel();
        lbOrganizationNameLabel = new javax.swing.JLabel();
        lbPointOfContactNameLabel = new javax.swing.JLabel();
        lbPointOfContactEmailLabel = new javax.swing.JLabel();
        lbPointOfContactPhoneLabel = new javax.swing.JLabel();
        lbPointOfContactNameText = new javax.swing.JLabel();
        lbPointOfContactEmailText = new javax.swing.JLabel();
        lbPointOfContactPhoneText = new javax.swing.JLabel();
        lbOrganizationNameText = new javax.swing.JLabel();

        jTextArea1.setColumns(20);
        jTextArea1.setRows(5);
        jScrollPane1.setViewportView(jTextArea1);

        casePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.casePanel.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        caseNameLabel.setFont(caseNameLabel.getFont().deriveFont(caseNameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        caseNameLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.caseNameLabel.text")); // NOI18N
        caseNameLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        caseNameLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        caseNameLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        lbDbType.setFont(lbDbType.getFont().deriveFont(lbDbType.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        lbDbType.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbDbType.text")); // NOI18N
        lbDbType.setMaximumSize(new java.awt.Dimension(82, 14));
        lbDbType.setMinimumSize(new java.awt.Dimension(82, 14));
        lbDbType.setPreferredSize(new java.awt.Dimension(82, 14));

        lbCaseUUIDLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbCaseUUIDLabel.text")); // NOI18N
        lbCaseUUIDLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbCaseUUIDLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbCaseUUIDLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        caseTypeField.setMaximumSize(new java.awt.Dimension(1, 0));

        dbNameField.setMinimumSize(new java.awt.Dimension(25, 14));

        lbDbName.setFont(lbDbName.getFont().deriveFont(lbDbName.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        lbDbName.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbDbName.text")); // NOI18N
        lbDbName.setMaximumSize(new java.awt.Dimension(82, 14));
        lbDbName.setMinimumSize(new java.awt.Dimension(82, 14));
        lbDbName.setPreferredSize(new java.awt.Dimension(82, 14));

        caseNumberLabel.setFont(caseNumberLabel.getFont().deriveFont(caseNumberLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        caseNumberLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.caseNumberLabel.text")); // NOI18N

        caseDirLabel.setFont(caseDirLabel.getFont().deriveFont(caseDirLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        caseDirLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.caseDirLabel.text")); // NOI18N
        caseDirLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        caseDirLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        caseDirLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        caseDirField.setMinimumSize(new java.awt.Dimension(25, 14));

        crDateLabel.setFont(crDateLabel.getFont().deriveFont(crDateLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        crDateLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.crDateLabel.text")); // NOI18N
        crDateLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        crDateLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        crDateLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        lbCaseNameText.setMinimumSize(new java.awt.Dimension(25, 14));

        lbCaseNumberText.setMinimumSize(new java.awt.Dimension(25, 14));

        javax.swing.GroupLayout casePanelLayout = new javax.swing.GroupLayout(casePanel);
        casePanel.setLayout(casePanelLayout);
        casePanelLayout.setHorizontalGroup(
            casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(casePanelLayout.createSequentialGroup()
                        .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(caseNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(caseNumberLabel))
                        .addGap(6, 6, 6)
                        .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbCaseNumberText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbCaseNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(casePanelLayout.createSequentialGroup()
                        .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(lbCaseUUIDLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(lbDbType, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(caseDirLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(crDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbDbName, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(crDateField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(caseDirField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(caseTypeField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(dbNameField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbCaseUIDText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );

        casePanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {caseDirLabel, caseNameLabel, caseNumberLabel, crDateLabel, lbCaseUUIDLabel, lbDbName, lbDbType});

        casePanelLayout.setVerticalGroup(
            casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseNumberLabel, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(lbCaseNumberText, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(crDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(crDateField, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(caseDirLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(caseDirField, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(caseTypeField, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDbType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDbName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(dbNameField, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(casePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbCaseUUIDLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbCaseUIDText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        examinerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.examinerPanel.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        lbNotesLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbNotesLabel.text")); // NOI18N
        lbNotesLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbNotesLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbNotesLabel.setPreferredSize(new java.awt.Dimension(82, 14));
        lbNotesLabel.setRequestFocusEnabled(false);

        examinerLabel.setFont(examinerLabel.getFont().deriveFont(examinerLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        examinerLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.examinerLabel.text")); // NOI18N
        examinerLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        examinerLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        examinerLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        caseNotesScrollPane.setBorder(null);

        taNotesText.setEditable(false);
        taNotesText.setBackground(new java.awt.Color(240, 240, 240));
        taNotesText.setColumns(20);
        taNotesText.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        taNotesText.setLineWrap(true);
        taNotesText.setRows(2);
        taNotesText.setWrapStyleWord(true);
        taNotesText.setBorder(null);
        taNotesText.setFocusable(false);
        caseNotesScrollPane.setViewportView(taNotesText);

        lbExaminerEmailLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbExaminerEmailLabel.text")); // NOI18N
        lbExaminerEmailLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbExaminerEmailLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbExaminerEmailLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        lbExaminerPhoneLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbExaminerPhoneLabel.text")); // NOI18N
        lbExaminerPhoneLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbExaminerPhoneLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbExaminerPhoneLabel.setPreferredSize(new java.awt.Dimension(82, 14));

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
                            .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbExaminerPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(caseNotesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 704, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, examinerPanelLayout.createSequentialGroup()
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(examinerLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbExaminerNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbExaminerEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );

        examinerPanelLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {examinerLabel, lbExaminerEmailLabel, lbExaminerPhoneLabel, lbNotesLabel});

        examinerPanelLayout.setVerticalGroup(
            examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(examinerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(examinerLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbExaminerPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbExaminerEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(examinerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(caseNotesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        pnOrganization.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.pnOrganization.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        lbOrganizationNameLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbOrganizationNameLabel.text")); // NOI18N
        lbOrganizationNameLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbOrganizationNameLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbOrganizationNameLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        lbPointOfContactNameLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbPointOfContactNameLabel.text")); // NOI18N

        lbPointOfContactEmailLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbPointOfContactEmailLabel.text")); // NOI18N
        lbPointOfContactEmailLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactEmailLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactEmailLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        lbPointOfContactPhoneLabel.setText(org.openide.util.NbBundle.getMessage(CasePropertiesPanel.class, "CasePropertiesPanel.lbPointOfContactPhoneLabel.text")); // NOI18N
        lbPointOfContactPhoneLabel.setMaximumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactPhoneLabel.setMinimumSize(new java.awt.Dimension(82, 14));
        lbPointOfContactPhoneLabel.setPreferredSize(new java.awt.Dimension(82, 14));

        javax.swing.GroupLayout pnOrganizationLayout = new javax.swing.GroupLayout(pnOrganization);
        pnOrganization.setLayout(pnOrganizationLayout);
        pnOrganizationLayout.setHorizontalGroup(
            pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnOrganizationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnOrganizationLayout.createSequentialGroup()
                        .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 115, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6)
                        .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.DEFAULT_SIZE, 704, Short.MAX_VALUE)
                            .addComponent(lbOrganizationNameText, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(pnOrganizationLayout.createSequentialGroup()
                        .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );

        pnOrganizationLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {lbOrganizationNameLabel, lbPointOfContactEmailLabel, lbPointOfContactNameLabel, lbPointOfContactPhoneLabel});

        pnOrganizationLayout.setVerticalGroup(
            pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnOrganizationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbOrganizationNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6))
        );

        javax.swing.GroupLayout detailsPanelLayout = new javax.swing.GroupLayout(detailsPanel);
        detailsPanel.setLayout(detailsPanelLayout);
        detailsPanelLayout.setHorizontalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(detailsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(casePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(examinerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnOrganization, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        detailsPanelLayout.setVerticalGroup(
            detailsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, detailsPanelLayout.createSequentialGroup()
                .addComponent(casePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(examinerPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(pnOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(detailsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(detailsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel caseDirField;
    private javax.swing.JLabel caseDirLabel;
    private javax.swing.JLabel caseNameLabel;
    private javax.swing.JScrollPane caseNotesScrollPane;
    private javax.swing.JLabel caseNumberLabel;
    private javax.swing.JPanel casePanel;
    private javax.swing.JLabel caseTypeField;
    private javax.swing.JLabel crDateField;
    private javax.swing.JLabel crDateLabel;
    private javax.swing.JLabel dbNameField;
    private javax.swing.JPanel detailsPanel;
    private javax.swing.JLabel examinerLabel;
    private javax.swing.JPanel examinerPanel;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JLabel lbCaseNameText;
    private javax.swing.JLabel lbCaseNumberText;
    private javax.swing.JLabel lbCaseUIDText;
    private javax.swing.JLabel lbCaseUUIDLabel;
    private javax.swing.JLabel lbDbName;
    private javax.swing.JLabel lbDbType;
    private javax.swing.JLabel lbExaminerEmailLabel;
    private javax.swing.JLabel lbExaminerEmailText;
    private javax.swing.JLabel lbExaminerNameText;
    private javax.swing.JLabel lbExaminerPhoneLabel;
    private javax.swing.JLabel lbExaminerPhoneText;
    private javax.swing.JLabel lbNotesLabel;
    private javax.swing.JLabel lbOrganizationNameLabel;
    private javax.swing.JLabel lbOrganizationNameText;
    private javax.swing.JLabel lbPointOfContactEmailLabel;
    private javax.swing.JLabel lbPointOfContactEmailText;
    private javax.swing.JLabel lbPointOfContactNameLabel;
    private javax.swing.JLabel lbPointOfContactNameText;
    private javax.swing.JLabel lbPointOfContactPhoneLabel;
    private javax.swing.JLabel lbPointOfContactPhoneText;
    private javax.swing.JPanel pnOrganization;
    private javax.swing.JTextArea taNotesText;
    // End of variables declaration//GEN-END:variables

}
