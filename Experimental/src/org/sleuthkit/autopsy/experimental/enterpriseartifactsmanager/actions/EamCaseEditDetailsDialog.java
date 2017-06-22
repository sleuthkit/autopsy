/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.actions;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JComboBox;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamCase;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamOrganization;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.optionspanel.AddNewOrganizationDialog;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDb;

/**
 * Handle editing details of cases within the Enterprise Artifacts Manager
 */
public class EamCaseEditDetailsDialog extends JDialog {

    private final static Logger LOGGER = Logger.getLogger(EamCaseEditDetailsDialog.class.getName());
    private EamCase eamCase;
    private EamDb dbManager;
    private Boolean contentChanged = false;
    private final Collection<JTextField> textBoxes = new ArrayList<>();
    private final Collection<JTextArea> textAreas = new ArrayList<>();
    private final TextBoxChangedListener textBoxChangedListener = new TextBoxChangedListener();
    private EamOrganization selectedOrg = null;
    private List<EamOrganization> orgs = null;
    private boolean comboboxOrganizationActionListenerActive;

    /**
     * Creates new form EnterpriseArtifactsManagerCasedEditDetailsForm
     */
    @Messages({"EnterpriseArtifactsManagerCaseEditDetails.window.title=Edit Case Details"})
    public EamCaseEditDetailsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.EnterpriseArtifactsManagerCaseEditDetails_window_title(),
                true); // NON-NLS

        try {
            this.dbManager = EamDb.getInstance();
            this.eamCase = this.dbManager.getCaseDetails(Case.getCurrentCase().getName());
            initComponents();
            loadData();
            customizeComponents();
            display();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting current case.", ex);
        }
    }

    private void customizeComponents() {
        setTextBoxListeners();
        setTextAreaListeners();

    }

    private void setTextBoxListeners() {
        // Register for notifications when the text boxes get updated.
        textBoxes.add(tfExaminerNameText);
        textBoxes.add(tfExaminerEmailText);
        textBoxes.add(tfExaminerPhoneText);
        addTextFieldDocumentListeners(textBoxes, textBoxChangedListener);
    }

    private void setTextAreaListeners() {
        // Register for notifications when the text areas get updated.
        textAreas.add(taNotesText);
        addTextAreaDocumentListeners(textAreas, textBoxChangedListener);
    }

    /**
     * Adds a change listener to a collection of text fields.
     *
     * @param textFields The text fields.
     * @param listener   The change listener.
     */
    private static void addTextFieldDocumentListeners(Collection<JTextField> textFields, TextBoxChangedListener listener) {
        textFields.forEach((textField) -> {
            textField.getDocument().addDocumentListener(listener);
        });
    }

    /**
     * Adds a change listener to a collection of text areas.
     *
     * @param textAreas The text areas.
     * @param listener  The change listener.
     */
    private static void addTextAreaDocumentListeners(Collection<JTextArea> textAreas, TextBoxChangedListener listener) {
        textAreas.forEach((textArea) -> {
            textArea.getDocument().addDocumentListener(listener);
        });
    }

    private void display() {
        pack();
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bnClose = new javax.swing.JButton();
        bnOk = new javax.swing.JButton();
        pnCaseMetadata = new javax.swing.JPanel();
        lbCaseNameLabel = new javax.swing.JLabel();
        lbCreationDateLabel = new javax.swing.JLabel();
        lbCaseNumberLabel = new javax.swing.JLabel();
        lbCaseUUIDLabel = new javax.swing.JLabel();
        lbCaseUUIDText = new javax.swing.JLabel();
        lbCaseNameText = new javax.swing.JLabel();
        lbCeationDateText = new javax.swing.JLabel();
        lbCaseNumberText = new javax.swing.JLabel();
        pnOrganization = new javax.swing.JPanel();
        lbOrganizationNameLabel = new javax.swing.JLabel();
        comboBoxOrgName = new javax.swing.JComboBox<>();
        lbPointOfContactGroupLabel = new javax.swing.JLabel();
        lbPointOfContactNameLabel = new javax.swing.JLabel();
        lbPointOfContactEmailLabel = new javax.swing.JLabel();
        lbPointOfContactPhoneLabel = new javax.swing.JLabel();
        lbPointOfContactNameText = new javax.swing.JLabel();
        lbPointOfContactEmailText = new javax.swing.JLabel();
        lbPointOfContactPhoneText = new javax.swing.JLabel();
        bnNewOrganization = new javax.swing.JButton();
        pnExaminer = new javax.swing.JPanel();
        lbExaminerNameLabel = new javax.swing.JLabel();
        tfExaminerNameText = new javax.swing.JTextField();
        lbExaminerEmailLabel = new javax.swing.JLabel();
        tfExaminerEmailText = new javax.swing.JTextField();
        lbExaminerPhoneLabel = new javax.swing.JLabel();
        tfExaminerPhoneText = new javax.swing.JTextField();
        lbNotesLabel = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        taNotesText = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(bnClose, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.bnClose.text")); // NOI18N
        bnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCloseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOk, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.bnOk.text")); // NOI18N
        bnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOkActionPerformed(evt);
            }
        });

        pnCaseMetadata.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.pnCaseMetadata.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbCaseNameLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbCaseNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbCreationDateLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbCreationDateLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbCaseNumberLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbCaseNumberLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbCaseUUIDLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbCaseUUIDLabel.text")); // NOI18N

        javax.swing.GroupLayout pnCaseMetadataLayout = new javax.swing.GroupLayout(pnCaseMetadata);
        pnCaseMetadata.setLayout(pnCaseMetadataLayout);
        pnCaseMetadataLayout.setHorizontalGroup(
            pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnCaseMetadataLayout.createSequentialGroup()
                .addGap(25, 25, 25)
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(lbCaseNumberLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCreationDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseUUIDLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbCaseNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbCeationDateText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbCaseNumberText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbCaseUUIDText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnCaseMetadataLayout.setVerticalGroup(
            pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnCaseMetadataLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbCaseUUIDLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbCaseUUIDText, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbCaseNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseNameText, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbCreationDateLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCeationDateText, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(pnCaseMetadataLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbCaseNumberLabel)
                    .addComponent(lbCaseNumberText, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26))
        );

        pnOrganization.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.pnOrganization.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbOrganizationNameLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbOrganizationNameLabel.text")); // NOI18N

        comboBoxOrgName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxOrgNameActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactGroupLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbPointOfContactGroupLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactNameLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbPointOfContactNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactEmailLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbPointOfContactEmailLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPointOfContactPhoneLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbPointOfContactPhoneLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnOrganizationLayout = new javax.swing.GroupLayout(pnOrganization);
        pnOrganization.setLayout(pnOrganizationLayout);
        pnOrganizationLayout.setHorizontalGroup(
            pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnOrganizationLayout.createSequentialGroup()
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnOrganizationLayout.createSequentialGroup()
                        .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, pnOrganizationLayout.createSequentialGroup()
                                .addGap(25, 25, 25)
                                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(lbPointOfContactGroupLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbOrganizationNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addGroup(pnOrganizationLayout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                        .addGap(18, 18, 18)
                        .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(comboBoxOrgName, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnOrganizationLayout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(bnNewOrganization)))
                .addContainerGap())
        );
        pnOrganizationLayout.setVerticalGroup(
            pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnOrganizationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbOrganizationNameLabel)
                    .addComponent(comboBoxOrgName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbPointOfContactGroupLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactNameText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactEmailLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactEmailText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOrganizationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbPointOfContactPhoneLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbPointOfContactPhoneText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnNewOrganization)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnExaminer.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.pnExaminer.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerNameLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbExaminerNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerEmailLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbExaminerEmailLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbExaminerPhoneLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbExaminerPhoneLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbNotesLabel, org.openide.util.NbBundle.getMessage(EamCaseEditDetailsDialog.class, "EamCaseEditDetailsDialog.lbNotesLabel.text")); // NOI18N

        taNotesText.setColumns(20);
        taNotesText.setRows(5);
        jScrollPane2.setViewportView(taNotesText);

        javax.swing.GroupLayout pnExaminerLayout = new javax.swing.GroupLayout(pnExaminer);
        pnExaminer.setLayout(pnExaminerLayout);
        pnExaminerLayout.setHorizontalGroup(
            pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnExaminerLayout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbExaminerEmailLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerPhoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbNotesLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 87, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(25, 25, 25)
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tfExaminerEmailText)
                    .addComponent(tfExaminerPhoneText)
                    .addComponent(tfExaminerNameText)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 274, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnExaminerLayout.setVerticalGroup(
            pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnExaminerLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbExaminerNameLabel)
                    .addComponent(tfExaminerNameText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerEmailText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerEmailLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tfExaminerPhoneText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbExaminerPhoneLabel))
                .addGap(24, 24, 24)
                .addGroup(pnExaminerLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbNotesLabel)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 178, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(bnOk, javax.swing.GroupLayout.PREFERRED_SIZE, 59, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bnClose))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(pnCaseMetadata, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pnOrganization, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pnExaminer, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addComponent(pnCaseMetadata, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnExaminer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnOk)
                    .addComponent(bnClose))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        if (contentChanged) {
            updateEnterpriseArtifactsManagerCase();
            updateDb();
        }
        dispose();
    }//GEN-LAST:event_bnOkActionPerformed

    private void bnNewOrganizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnNewOrganizationActionPerformed
        AddNewOrganizationDialog dialogO = new AddNewOrganizationDialog();
        // update the combobox options and org data fields
        if (dialogO.isChanged()) {
            loadOrganizationData();
        }
    }//GEN-LAST:event_bnNewOrganizationActionPerformed

    @SuppressWarnings({"unchecked"})
    private void comboBoxOrgNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxOrgNameActionPerformed
        JComboBox<String> cb = (JComboBox<String>) evt.getSource();
        String orgName = (String) cb.getSelectedItem();
        if (null == orgName || false == comboboxOrganizationActionListenerActive) {
            return;
        }

        if ("".equals(orgName)) {
            selectedOrg = null;
            lbPointOfContactNameText.setText("");
            lbPointOfContactEmailText.setText("");
            lbPointOfContactPhoneText.setText("");
            contentChanged = true;
            return;            
        }

        for (EamOrganization org : orgs) {
            if (org.getName().equals(orgName)) {
                selectedOrg = org;
                lbPointOfContactNameText.setText(selectedOrg.getPocName());
                lbPointOfContactEmailText.setText(selectedOrg.getPocEmail());
                lbPointOfContactPhoneText.setText(selectedOrg.getPocPhone());
                contentChanged = true;
                return;
            }
        }
    }//GEN-LAST:event_comboBoxOrgNameActionPerformed

    private void bnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCloseActionPerformed
        dispose();
    }//GEN-LAST:event_bnCloseActionPerformed

    private void loadCaseMetaData() {
        lbCaseUUIDText.setText(eamCase.getCaseUUID());
        lbCaseNameText.setText(eamCase.getDisplayName());
        lbCeationDateText.setText(eamCase.getCreationDate());
        lbCaseNumberText.setText(eamCase.getCaseNumber());
    }

    private void loadExaminerData() {
        tfExaminerNameText.setText(eamCase.getExaminerName());
        tfExaminerEmailText.setText(eamCase.getExaminerEmail());
        tfExaminerPhoneText.setText(eamCase.getExaminerPhone());
        taNotesText.setText(eamCase.getNotes());
    }

    private void loadOrganizationData() {
        comboboxOrganizationActionListenerActive = false; // don't fire action listener while loading combobox content
        comboBoxOrgName.removeAllItems();
        try {
            orgs = dbManager.getOrganizations();
            comboBoxOrgName.addItem(""); // for when a case has a null Org
            orgs.forEach((org) -> {
                comboBoxOrgName.addItem(org.getName());
            });
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failure populating combobox with organizations.", ex);
        }
        comboboxOrganizationActionListenerActive = true;

        if (!orgs.isEmpty() && null != eamCase.getOrg()) {                
            selectedOrg = eamCase.getOrg();
            comboBoxOrgName.setSelectedItem(selectedOrg.getName());
            lbPointOfContactNameText.setText(selectedOrg.getPocName());
            lbPointOfContactEmailText.setText(selectedOrg.getPocEmail());
            lbPointOfContactPhoneText.setText(selectedOrg.getPocPhone());
        } else {
            comboBoxOrgName.setSelectedItem("");
            lbPointOfContactNameText.setText("");
            lbPointOfContactEmailText.setText("");
            lbPointOfContactPhoneText.setText("");
        }
    }

    private void loadData() {
        loadCaseMetaData();
        loadExaminerData();
        loadOrganizationData();
    }

    /**
     * Save changed value from text fields and text areas into the EamCase
     * object.
     */
    private void updateEnterpriseArtifactsManagerCase() {
        eamCase.setOrg(selectedOrg);
        eamCase.setExaminerName(tfExaminerNameText.getText());
        eamCase.setExaminerEmail(tfExaminerEmailText.getText());
        eamCase.setExaminerPhone(tfExaminerPhoneText.getText());
        eamCase.setNotes(taNotesText.getText());
    }

    private void updateDb() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (!EamDb.isEnabled()) {
            LOGGER.log(Level.SEVERE, "Enteprise artifacts manager database not enabled"); // NON-NLS
            return;
        }

        try {
            dbManager.updateCase(eamCase);
        } catch (IllegalArgumentException | EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error connecting to enterprise artifacts manager database", ex); // NON-NLS
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    /**
     * Used to listen for changes in text areas/boxes. Let the panel know text
     * content has changed.
     */
    private class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            setChanged();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            setChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            setChanged();
        }

        private void setChanged() {
            contentChanged = true;
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnClose;
    private javax.swing.JButton bnNewOrganization;
    private javax.swing.JButton bnOk;
    private javax.swing.JComboBox<String> comboBoxOrgName;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lbCaseNameLabel;
    private javax.swing.JLabel lbCaseNameText;
    private javax.swing.JLabel lbCaseNumberLabel;
    private javax.swing.JLabel lbCaseNumberText;
    private javax.swing.JLabel lbCaseUUIDLabel;
    private javax.swing.JLabel lbCaseUUIDText;
    private javax.swing.JLabel lbCeationDateText;
    private javax.swing.JLabel lbCreationDateLabel;
    private javax.swing.JLabel lbExaminerEmailLabel;
    private javax.swing.JLabel lbExaminerNameLabel;
    private javax.swing.JLabel lbExaminerPhoneLabel;
    private javax.swing.JLabel lbNotesLabel;
    private javax.swing.JLabel lbOrganizationNameLabel;
    private javax.swing.JLabel lbPointOfContactEmailLabel;
    private javax.swing.JLabel lbPointOfContactEmailText;
    private javax.swing.JLabel lbPointOfContactGroupLabel;
    private javax.swing.JLabel lbPointOfContactNameLabel;
    private javax.swing.JLabel lbPointOfContactNameText;
    private javax.swing.JLabel lbPointOfContactPhoneLabel;
    private javax.swing.JLabel lbPointOfContactPhoneText;
    private javax.swing.JPanel pnCaseMetadata;
    private javax.swing.JPanel pnExaminer;
    private javax.swing.JPanel pnOrganization;
    private javax.swing.JTextArea taNotesText;
    private javax.swing.JTextField tfExaminerEmailText;
    private javax.swing.JTextField tfExaminerNameText;
    private javax.swing.JTextField tfExaminerPhoneText;
    // End of variables declaration//GEN-END:variables
}
