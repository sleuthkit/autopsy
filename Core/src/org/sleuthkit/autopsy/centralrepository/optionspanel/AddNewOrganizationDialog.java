/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;

/**
 * Dialog to add a new organization to the Central Repository database
 */
public class AddNewOrganizationDialog extends javax.swing.JDialog {

    private static final Logger LOGGER = Logger.getLogger(AddNewOrganizationDialog.class.getName());
    private static final long serialVersionUID = 1L;

    private final Collection<JTextField> textBoxes;
    private final TextBoxChangedListener textBoxChangedListener;
    private boolean hasChanged;
    private EamOrganization newOrg;
    private final EamOrganization organizationToEdit;

    /**
     * Creates new form AddNewOrganizationDialog
     */
    @Messages({"AddNewOrganizationDialog.addNewOrg.msg=Add New Organization"})
    public AddNewOrganizationDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.AddNewOrganizationDialog_addNewOrg_msg(),
                true); // NON-NLS
        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();
        hasChanged = false;
        newOrg = null;
        initComponents();
        customizeComponents();
        organizationToEdit = null;
        display();
    }

    public AddNewOrganizationDialog(EamOrganization orgToEdit) {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.AddNewOrganizationDialog_addNewOrg_msg(),
                true); // NON-NLS
        organizationToEdit = orgToEdit;
        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();
        hasChanged = false;
        newOrg = null;
        initComponents();
        customizeComponents();
        tfOrganizationName.setText(orgToEdit.getName());
        tfPocName.setText(orgToEdit.getPocName());
        tfPocEmail.setText(orgToEdit.getPocEmail());
        tfPocPhone.setText(orgToEdit.getPocPhone());
        display();
    }

    private void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    private void customizeComponents() {
        setTextBoxListeners();
        enableOkButton(false);
    }

    /**
     * Register for notifications when the text boxes get updated.
     */
    private void setTextBoxListeners() {
        textBoxes.add(tfOrganizationName);
        textBoxes.add(tfPocEmail);
        textBoxes.add(tfPocPhone);
        textBoxes.add(tfPocName);
        addDocumentListeners(textBoxes, textBoxChangedListener);
    }

    /**
     * Adds a change listener to a collection of text fields.
     *
     * @param textFields The text fields.
     * @param listener   The change listener.
     */
    private static void addDocumentListeners(Collection<JTextField> textFields, TextBoxChangedListener listener) {
        textFields.forEach((textField) -> {
            textField.getDocument().addDocumentListener(listener);
        });
    }

    /**
     * Tests whether or not values have been entered in all of the database
     * settings text fields.
     *
     * @return True or false.
     */
    private boolean requiredFieldsArePopulated() {
        return !tfOrganizationName.getText().trim().isEmpty();
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    @Messages({"AddNewOrganizationDialog.validation.incompleteFields=Organization Name is required."})
    private boolean checkFields() {
        boolean result = true;

        boolean isPopulated = requiredFieldsArePopulated();

        if (!isPopulated) {
            // We don't even have everything filled out
            result = false;
            lbWarningMsg.setText(Bundle.AddNewOrganizationDialog_validation_incompleteFields());
        }
        return result;
    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    public boolean valid() {
        lbWarningMsg.setText("");

        return enableOkButton(checkFields());
    }

    /**
     * Enables the "OK" button to save the new organization.
     *
     * @param enable
     *
     * @return True or False
     */
    private boolean enableOkButton(Boolean enable) {
        bnOK.setEnabled(enable);
        return enable;
    }

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    private class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }
    }

    public boolean isChanged() {
        return hasChanged;
    }

    public EamOrganization getNewOrg() {
        return newOrg;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bnOK = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();
        lbOrganizationName = new javax.swing.JLabel();
        lbPocHeading = new javax.swing.JLabel();
        lbPocName = new javax.swing.JLabel();
        lbPocEmail = new javax.swing.JLabel();
        lbPocPhone = new javax.swing.JLabel();
        tfPocName = new javax.swing.JTextField();
        tfPocEmail = new javax.swing.JTextField();
        tfPocPhone = new javax.swing.JTextField();
        tfOrganizationName = new javax.swing.JTextField();
        lbWarningMsg = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(bnOK, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.bnOK.text")); // NOI18N
        bnOK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOKActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbOrganizationName, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.lbOrganizationName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPocHeading, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.lbPocHeading.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPocName, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.lbPocName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPocEmail, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.lbPocEmail.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPocPhone, org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.lbPocPhone.text")); // NOI18N

        tfPocName.setToolTipText(org.openide.util.NbBundle.getMessage(AddNewOrganizationDialog.class, "AddNewOrganizationDialog.tfName.tooltip")); // NOI18N

        lbWarningMsg.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lbWarningMsg.setForeground(new java.awt.Color(255, 0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(258, Short.MAX_VALUE)
                .addComponent(bnOK)
                .addGap(18, 18, 18)
                .addComponent(bnCancel)
                .addGap(12, 12, 12))
            .addGroup(layout.createSequentialGroup()
                .addGap(39, 39, 39)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbPocEmail)
                    .addComponent(lbPocName)
                    .addComponent(lbPocPhone))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tfPocPhone)
                    .addComponent(tfPocName)
                    .addComponent(tfPocEmail))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lbOrganizationName)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tfOrganizationName))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lbPocHeading)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(lbWarningMsg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbOrganizationName)
                    .addComponent(tfOrganizationName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbPocHeading)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbPocName)
                    .addComponent(tfPocName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbPocEmail)
                    .addComponent(tfPocEmail, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbPocPhone)
                    .addComponent(tfPocPhone, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbWarningMsg, javax.swing.GroupLayout.DEFAULT_SIZE, 22, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnOK)
                    .addComponent(bnCancel))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        dispose();
    }//GEN-LAST:event_bnCancelActionPerformed

    @Messages({"AddNewOrganizationDialog.bnOk.addFailed.text=Failed to add new organization."})
    private void bnOKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOKActionPerformed

        try {
            EamDb dbManager = EamDb.getInstance();
            if (organizationToEdit != null) {
                //check if new name exists with ID other than the one in use here
                newOrg = new EamOrganization(organizationToEdit.getOrgID(),
                        tfOrganizationName.getText(),
                        tfPocName.getText(),
                        tfPocEmail.getText(),
                        tfPocPhone.getText());
                dbManager.updateOrganization(newOrg);
            } else {
                newOrg = new EamOrganization(
                        tfOrganizationName.getText(),
                        tfPocName.getText(),
                        tfPocEmail.getText(),
                        tfPocPhone.getText());
                newOrg.setOrgID((int)dbManager.newOrganization(newOrg));
            }
            hasChanged = true;
            dispose();
        } catch (EamDbException ex) {
            lbWarningMsg.setText(Bundle.AddNewOrganizationDialog_bnOk_addFailed_text());
            LOGGER.log(Level.SEVERE, "Failed adding new organization.", ex);
        }
    }//GEN-LAST:event_bnOKActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnOK;
    private javax.swing.JLabel lbOrganizationName;
    private javax.swing.JLabel lbPocEmail;
    private javax.swing.JLabel lbPocHeading;
    private javax.swing.JLabel lbPocName;
    private javax.swing.JLabel lbPocPhone;
    private javax.swing.JLabel lbWarningMsg;
    private javax.swing.JTextField tfOrganizationName;
    private javax.swing.JTextField tfPocEmail;
    private javax.swing.JTextField tfPocName;
    private javax.swing.JTextField tfPocPhone;
    // End of variables declaration//GEN-END:variables
}
