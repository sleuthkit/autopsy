/*
 * Central Repository
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.persona;

import java.awt.Component;
import java.io.Serializable;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import org.apache.commons.lang.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.Persona;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.InvalidAccountIDException;

/**
 * Configuration dialog for adding an account to a persona.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class PersonaAccountDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(PersonaAccountDialog.class.getName());

    private static final long serialVersionUID = 1L;

    private final TypeChoiceRenderer TYPE_CHOICE_RENDERER = new TypeChoiceRenderer();
    private final PersonaDetailsPanel pdp;

    private PersonaDetailsPanel.PAccount currentAccount = null;

    /**
     * Creates new add account dialog
     */
    @Messages({"PersonaAccountDialog.title.text=Add Account",})
    public PersonaAccountDialog(PersonaDetailsPanel pdp) {
        super(SwingUtilities.windowForComponent(pdp),
                Bundle.PersonaAccountDialog_title_text(),
                ModalityType.APPLICATION_MODAL);
        this.pdp = pdp;

        initComponents();
        typeComboBox.setRenderer(TYPE_CHOICE_RENDERER);
        display();
    }

    PersonaAccountDialog(PersonaDetailsPanel pdp, PersonaDetailsPanel.PAccount acc) {
        super(SwingUtilities.windowForComponent(pdp),
                Bundle.PersonaAccountDialog_title_text(),
                ModalityType.APPLICATION_MODAL);
        this.pdp = pdp;

        initComponents();
        currentAccount = acc;
        confidenceComboBox.setSelectedItem(acc.confidence);
        justificationTextField.setText(acc.justification);
        typeComboBox.setSelectedItem(acc.account.getAccountType());
        identifierTextField.setText(acc.account.getIdentifier());

        typeComboBox.setEnabled(false);
        identifierTextField.setEnabled(false);

        typeComboBox.setRenderer(TYPE_CHOICE_RENDERER);
        display();
    }

    /**
     * This class handles displaying and rendering drop down menu for account
     * choices
     */
    private class TypeChoiceRenderer extends JLabel implements ListCellRenderer<CentralRepoAccountType>, Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(
                JList<? extends CentralRepoAccountType> list, CentralRepoAccountType value,
                int index, boolean isSelected, boolean cellHasFocus) {
            setText(value.getAcctType().getDisplayName());
            return this;
        }
    }

    @Messages({
        "PersonaAccountDialog_get_types_exception_Title=Central Repository failure",
        "PersonaAccountDialog_get_types_exception_msg=Failed to access central repository.",})
    private CentralRepoAccountType[] getAllAccountTypes() {
        Collection<CentralRepoAccountType> allAccountTypes;
        try {
            allAccountTypes = CentralRepository.getInstance().getAllAccountTypes();
        } catch (CentralRepoException e) {
            logger.log(Level.SEVERE, "Failed to access central repository", e);
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_get_types_exception_Title(),
                    Bundle.PersonaAccountDialog_get_types_exception_msg(),
                    JOptionPane.ERROR_MESSAGE);
            return new CentralRepoAccountType[0];
        }
        return allAccountTypes.toArray(new CentralRepoAccountType[0]);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        settingsPanel = new javax.swing.JPanel();
        identiferLbl = new javax.swing.JLabel();
        identifierTextField = new javax.swing.JTextField();
        typeLbl = new javax.swing.JLabel();
        typeComboBox = new javax.swing.JComboBox<>();
        confidenceLbl = new javax.swing.JLabel();
        confidenceComboBox = new javax.swing.JComboBox<>();
        justificationLbl = new javax.swing.JLabel();
        justificationTextField = new javax.swing.JTextField();
        cancelBtn = new javax.swing.JButton();
        okBtn = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        settingsPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        org.openide.awt.Mnemonics.setLocalizedText(identiferLbl, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.identiferLbl.text")); // NOI18N

        identifierTextField.setText(org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.identifierTextField.text")); // NOI18N
        identifierTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                identifierTextFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(typeLbl, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.typeLbl.text")); // NOI18N

        typeComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(getAllAccountTypes()));

        org.openide.awt.Mnemonics.setLocalizedText(confidenceLbl, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.confidenceLbl.text")); // NOI18N

        confidenceComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(org.sleuthkit.autopsy.centralrepository.datamodel.Persona.Confidence.values()));

        org.openide.awt.Mnemonics.setLocalizedText(justificationLbl, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.justificationLbl.text")); // NOI18N

        justificationTextField.setText(org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.justificationTextField.text")); // NOI18N

        javax.swing.GroupLayout settingsPanelLayout = new javax.swing.GroupLayout(settingsPanel);
        settingsPanel.setLayout(settingsPanelLayout);
        settingsPanelLayout.setHorizontalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addComponent(typeLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(typeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addComponent(identiferLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(identifierTextField))
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addComponent(confidenceLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(confidenceComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(settingsPanelLayout.createSequentialGroup()
                        .addComponent(justificationLbl)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(justificationTextField)))
                .addContainerGap())
        );
        settingsPanelLayout.setVerticalGroup(
            settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(settingsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(identiferLbl)
                    .addComponent(identifierTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(typeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(typeLbl, javax.swing.GroupLayout.PREFERRED_SIZE, 9, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(confidenceLbl)
                    .addComponent(confidenceComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(settingsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(justificationLbl)
                    .addComponent(justificationTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.openide.awt.Mnemonics.setLocalizedText(cancelBtn, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.cancelBtn.text")); // NOI18N
        cancelBtn.setMaximumSize(new java.awt.Dimension(79, 23));
        cancelBtn.setMinimumSize(new java.awt.Dimension(79, 23));
        cancelBtn.setPreferredSize(new java.awt.Dimension(79, 23));
        cancelBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelBtnActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(okBtn, org.openide.util.NbBundle.getMessage(PersonaAccountDialog.class, "PersonaAccountDialog.okBtn.text")); // NOI18N
        okBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okBtnActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap(194, Short.MAX_VALUE)
                .addComponent(okBtn)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cancelBtn, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addComponent(settingsPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelBtn, okBtn});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(settingsPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(okBtn, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(cancelBtn, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    @Messages({
        "PersonaAccountDialog_dup_Title=Account add failure",
        "PersonaAccountDialog_dup_msg=This account is already added to the persona.",
        "PersonaAccountDialog_identifier_empty_Title=Empty identifier",
        "PersonaAccountDialog_identifier_empty_msg=The identifier field cannot be empty.",
        "PersonaAccountDialog_search_failure_Title=Account add failure",
        "PersonaAccountDialog_search_failure_msg=Central Repository account search failed.",
        "PersonaAccountDialog_search_empty_Title=Account not found",
        "PersonaAccountDialog_search_empty_msg=Account not found for given identifier and type.",
        "PersonaAccountDialog_invalid_account_Title=Invalid account identifier",
        "PersonaAccountDialog_invalid_account_msg=Account identifier is not valid.",
    })
    private void okBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okBtnActionPerformed
        if (StringUtils.isBlank(identifierTextField.getText())) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_identifier_empty_msg(),
                    Bundle.PersonaAccountDialog_identifier_empty_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (StringUtils.isBlank(justificationTextField.getText())) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaDetailsPanel_empty_justification_msg(),
                    Bundle.PersonaDetailsPanel_empty_justification_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        Collection<CentralRepoAccount> candidates;
        try {
            candidates = CentralRepoAccount.getAccountsWithIdentifier(identifierTextField.getText());
        } catch (CentralRepoException e) {
            logger.log(Level.SEVERE, "Failed to access central repository", e);
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_search_failure_msg(),
                    Bundle.PersonaAccountDialog_search_failure_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        catch (InvalidAccountIDException e) {
            logger.log(Level.SEVERE, "Invalid account identifier", e);
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_invalid_account_msg(),
                    Bundle.PersonaAccountDialog_invalid_account_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_search_empty_msg(),
                    Bundle.PersonaAccountDialog_search_empty_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        CentralRepoAccount result = null;
        for (CentralRepoAccount cand : candidates) {
            if (cand.getAccountType().getAcctType().equals(
                    ((CentralRepoAccountType) typeComboBox.getSelectedItem()).getAcctType())) {
                result = cand;
                break;
            }
        }
        if (result == null) {
            JOptionPane.showMessageDialog(this,
                    Bundle.PersonaAccountDialog_search_empty_msg(),
                    Bundle.PersonaAccountDialog_search_empty_Title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Persona.Confidence confidence = (Persona.Confidence) confidenceComboBox.getSelectedItem();
        String justification = justificationTextField.getText();

        if (currentAccount != null) {
            currentAccount.confidence = confidence;
            currentAccount.justification = justification;
            dispose();
        } else {
            if (pdp.addAccount(result, justification, confidence)) {
                dispose();
            } else {
                JOptionPane.showMessageDialog(this,
                        Bundle.PersonaAccountDialog_dup_msg(),
                        Bundle.PersonaAccountDialog_dup_Title(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_okBtnActionPerformed

    private void cancelBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelBtnActionPerformed
        dispose();
    }//GEN-LAST:event_cancelBtnActionPerformed

    private void identifierTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_identifierTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_identifierTextFieldActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelBtn;
    private javax.swing.JComboBox<org.sleuthkit.autopsy.centralrepository.datamodel.Persona.Confidence> confidenceComboBox;
    private javax.swing.JLabel confidenceLbl;
    private javax.swing.JLabel identiferLbl;
    private javax.swing.JTextField identifierTextField;
    private javax.swing.JLabel justificationLbl;
    private javax.swing.JTextField justificationTextField;
    private javax.swing.JButton okBtn;
    private javax.swing.JPanel settingsPanel;
    private javax.swing.JComboBox<org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoAccount.CentralRepoAccountType> typeComboBox;
    private javax.swing.JLabel typeLbl;
    // End of variables declaration//GEN-END:variables
}
