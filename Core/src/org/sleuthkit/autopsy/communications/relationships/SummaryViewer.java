/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obt ain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.communications.relationships;

import java.util.Set;
import javax.swing.JPanel;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.autopsy.coreutils.PhoneNumUtil;

/**
 * Account Summary View Panel. This panel shows a list of various counts related
 * to the currently selected account. As well has a panel showing a list of
 * cases and files that reference the account.
 *
 */
public class SummaryViewer extends javax.swing.JPanel implements RelationshipsViewer {

    private final Lookup lookup;

    @Messages({
        "SummaryViewer_TabTitle=Summary",
        "SummaryViewer_FileRefNameColumn_Title=Path",
        "SummaryViewer_CaseRefNameColumn_Title=Case Name",
        "SummaryViewer_CentralRepository_Message=<Enable Central Respository to see Other Occurrences>",
        "SummaryViewer_Creation_Date_Title=Creation Date",
        "SummaryViewer_FileRef_Message=<Select a single account to see File References>",
        "SummaryViewer_Device_Account_Description=This account was referenced by a device in the case.",
        "SummaryViewer_Account_Description=This account represents a device in the case.",
        "SummaryViewer_Account_Description_MuliSelect=Summary information is not available when multiple accounts are selected.",
        "SummaryViewer_Country_Code=Country: "
    })

    /**
     * Creates new form SummaryViewer
     */
    public SummaryViewer() {
        lookup = Lookup.getDefault();
        initComponents();

        OutlineView outlineView = fileReferencesPanel.getOutlineView();
        Outline outline = outlineView.getOutline();

        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.SummaryViewer_FileRefNameColumn_Title());

        outlineView = caseReferencesPanel.getOutlineView();
        outline = outlineView.getOutline();
        outlineView.setPropertyColumns("creationDate", Bundle.SummaryViewer_Creation_Date_Title()); //NON-NLS

        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.SummaryViewer_CaseRefNameColumn_Title());

        clearControls();

        caseReferencesPanel.hideOutlineView(Bundle.SummaryViewer_CentralRepository_Message());
        fileReferencesPanel.hideOutlineView(Bundle.SummaryViewer_FileRef_Message());
    }

    @Override
    public String getDisplayName() {
        return Bundle.SummaryViewer_TabTitle();
    }

    @Override
    public JPanel getPanel() {
        return this;
    }

    @Override
    public void setSelectionInfo(SelectionInfo info) {

        if (!EamDb.isEnabled()) {
            caseReferencesPanel.hideOutlineView(Bundle.SummaryViewer_CentralRepository_Message());
        } else {
            caseReferencesPanel.showOutlineView();
        }

        // Request is that the SummaryViewer only show information if one
        // account is selected
        if (info.getAccounts().size() != 1) {
            setEnabled(false);
            clearControls();
            
            accoutDescriptionLabel.setText(Bundle.SummaryViewer_Account_Description_MuliSelect());

            fileReferencesPanel.hideOutlineView(Bundle.SummaryViewer_FileRef_Message());
        } else {
            Account[] accountArray = info.getAccounts().toArray(new Account[1]);
            Account account = accountArray[0];
            
            if (account.getAccountType().getTypeName().contains("PHONE")) {
                String countryCode = PhoneNumUtil.getCountryCode(account.getTypeSpecificID());
                accountLabel.setText(PhoneNumUtil.convertToInternational(account.getTypeSpecificID()));
                accountCountry.setText(Bundle.SummaryViewer_Country_Code() + countryCode);
                accountCountry.setEnabled(true);
            } else {
                accountLabel.setText(account.getTypeSpecificID());
                accountCountry.setText("");
                accountCountry.setEnabled(false);
            }
            
            if (account.getAccountType().equals(Account.Type.DEVICE)) {
                accoutDescriptionLabel.setText(Bundle.SummaryViewer_Account_Description());
            } else {
                accoutDescriptionLabel.setText(Bundle.SummaryViewer_Device_Account_Description());
            }
            
            AccountSummary summaryDetails = new AccountSummary(account, info.getArtifacts());

            thumbnailsDataLabel.setText(Integer.toString(summaryDetails.getThumbnailCnt()));
            callLogsDataLabel.setText(Integer.toString(summaryDetails.getCallLogCnt()));
            contactsDataLabel.setText(Integer.toString(summaryDetails.getContactsCnt()));
            messagesDataLabel.setText(Integer.toString(summaryDetails.getMessagesCnt() + summaryDetails.getEmailCnt()));
            attachmentDataLabel.setText(Integer.toString(summaryDetails.getAttachmentCnt()));
            referencesDataLabel.setText(Integer.toString(summaryDetails.getReferenceCnt()));
            contactsDataLabel.setText(Integer.toString(summaryDetails.getContactsCnt()));

            fileReferencesPanel.showOutlineView();

            fileReferencesPanel.setNode(new AbstractNode(Children.create(new AccountSourceContentChildNodeFactory(info.getAccounts()), true)));
            caseReferencesPanel.setNode(new AbstractNode(Children.create(new CorrelationCaseChildNodeFactory(info.getAccounts()), true)));

            setEnabled(true);
        }
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    /**
     * Sets whether or not the text fields are enabled.
     *
     * @param enabled true if this component should be enabled, false otherwise
     */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        thumbnailCntLabel.setEnabled(enabled);
        callLogsLabel.setEnabled(enabled);
        contactsLabel.setEnabled(enabled);
        messagesLabel.setEnabled(enabled);
        caseReferencesPanel.setEnabled(enabled);
        fileReferencesPanel.setEnabled(enabled);
        countsPanel.setEnabled(enabled);
        attachmentsLabel.setEnabled(enabled);
        referencesLabel.setEnabled(enabled);
    }

    /**
     * Clears the text fields and OutlookViews.
     */
    private void clearControls() {
        thumbnailsDataLabel.setText("");
        callLogsDataLabel.setText("");
        contactsDataLabel.setText("");
        messagesDataLabel.setText("");
        attachmentDataLabel.setText("");
        accountLabel.setText("");
        accoutDescriptionLabel.setText("");
        referencesDataLabel.setText("");
        accountCountry.setText("");
        
        fileReferencesPanel.setNode(new AbstractNode(Children.LEAF));
        caseReferencesPanel.setNode(new AbstractNode(Children.LEAF));
    }

    /**
     * For the given accounts create a comma separated string of all of the
     * names (TypeSpecificID).
     *
     * @param accounts Set of selected accounts
     *
     * @return String listing the account names
     */
    private String createAccountLabel(Set<Account> accounts) {
        StringBuilder buffer = new StringBuilder();
        accounts.stream().map((account) -> {
            buffer.append(account.getTypeSpecificID());
            return account;
        }).forEachOrdered((_item) -> {
            buffer.append(", ");
        });

        return buffer.toString().substring(0, buffer.length() - 2);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        summaryPanel = new javax.swing.JPanel();
        accountLabel = new javax.swing.JLabel();
        accountCountry = new javax.swing.JLabel();
        accoutDescriptionLabel = new javax.swing.JLabel();
        countsPanel = new javax.swing.JPanel();
        messagesLabel = new javax.swing.JLabel();
        callLogsLabel = new javax.swing.JLabel();
        thumbnailCntLabel = new javax.swing.JLabel();
        thumbnailsDataLabel = new javax.swing.JLabel();
        messagesDataLabel = new javax.swing.JLabel();
        callLogsDataLabel = new javax.swing.JLabel();
        attachmentsLabel = new javax.swing.JLabel();
        attachmentDataLabel = new javax.swing.JLabel();
        contanctsPanel = new javax.swing.JPanel();
        contactsLabel = new javax.swing.JLabel();
        contactsDataLabel = new javax.swing.JLabel();
        referencesLabel = new javax.swing.JLabel();
        referencesDataLabel = new javax.swing.JLabel();
        fileReferencesPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();
        caseReferencesPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();

        setLayout(new java.awt.GridBagLayout());

        summaryPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(accountLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.accountLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(15, 9, 0, 9);
        summaryPanel.add(accountLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(accountCountry, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.accountCountry.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 9);
        summaryPanel.add(accountCountry, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(accoutDescriptionLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.accoutDescriptionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 9, 15, 9);
        summaryPanel.add(accoutDescriptionLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        add(summaryPanel, gridBagConstraints);

        countsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.countsPanel.border.title"))); // NOI18N
        countsPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(messagesLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.messagesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 9, 15);
        countsPanel.add(messagesLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(callLogsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.callLogsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 9, 15);
        countsPanel.add(callLogsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(thumbnailCntLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.thumbnailCntLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 9, 15);
        countsPanel.add(thumbnailCntLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(thumbnailsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.thumbnailsDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 15);
        countsPanel.add(thumbnailsDataLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(messagesDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.messagesDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 9, 15);
        countsPanel.add(messagesDataLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(callLogsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.callLogsDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 15);
        countsPanel.add(callLogsDataLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(attachmentsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.attachmentsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 9, 15);
        countsPanel.add(attachmentsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(attachmentDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.attachmentDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 15);
        countsPanel.add(attachmentDataLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        add(countsPanel, gridBagConstraints);

        contanctsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.contanctsPanel.border.title"))); // NOI18N
        contanctsPanel.setLayout(new java.awt.GridBagLayout());

        contactsLabel.setLabelFor(contactsDataLabel);
        org.openide.awt.Mnemonics.setLocalizedText(contactsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.contactsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 9, 15);
        contanctsPanel.add(contactsLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(contactsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.contactsDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 9, 9, 15);
        contanctsPanel.add(contactsDataLabel, gridBagConstraints);

        referencesLabel.setLabelFor(referencesDataLabel);
        org.openide.awt.Mnemonics.setLocalizedText(referencesLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.referencesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 9, 0);
        contanctsPanel.add(referencesLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(referencesDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.referencesDataLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 0);
        contanctsPanel.add(referencesDataLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        add(contanctsPanel, gridBagConstraints);

        fileReferencesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.fileReferencesPanel.border.title"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        add(fileReferencesPanel, gridBagConstraints);

        caseReferencesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.caseReferencesPanel.border.title"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        add(caseReferencesPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel accountCountry;
    private javax.swing.JLabel accountLabel;
    private javax.swing.JLabel accoutDescriptionLabel;
    private javax.swing.JLabel attachmentDataLabel;
    private javax.swing.JLabel attachmentsLabel;
    private javax.swing.JLabel callLogsDataLabel;
    private javax.swing.JLabel callLogsLabel;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel caseReferencesPanel;
    private javax.swing.JLabel contactsDataLabel;
    private javax.swing.JLabel contactsLabel;
    private javax.swing.JPanel contanctsPanel;
    private javax.swing.JPanel countsPanel;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel fileReferencesPanel;
    private javax.swing.JLabel messagesDataLabel;
    private javax.swing.JLabel messagesLabel;
    private javax.swing.JLabel referencesDataLabel;
    private javax.swing.JLabel referencesLabel;
    private javax.swing.JPanel summaryPanel;
    private javax.swing.JLabel thumbnailCntLabel;
    private javax.swing.JLabel thumbnailsDataLabel;
    // End of variables declaration//GEN-END:variables

}
