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
import org.sleuthkit.autopsy.communications.relationships.SelectionInfo.SelectionSummary;
import org.sleuthkit.datamodel.Account;

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
        "SummaryViewer_CentralRepository_Message=<Enable Central Resposity to see Other Occurrences>",
        "SummaryViewer_Creation_Date_Title=Creation Date",
        "SummeryViewer_FileRef_Message=<Select one Accout to see File References>",
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
        fileReferencesPanel.hideOutlineView(Bundle.SummeryViewer_FileRef_Message());
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
            
            fileReferencesPanel.hideOutlineView(Bundle.SummeryViewer_FileRef_Message());
        } else {
            SelectionSummary summaryDetails = info.getSummary();

            attachmentsDataLabel.setText(Integer.toString(summaryDetails.getAttachmentCnt()));
            callLogsDataLabel.setText(Integer.toString(summaryDetails.getCallLogCnt()));
            contactsDataLabel.setText(Integer.toString(summaryDetails.getContactsCnt()));
            messagesDataLabel.setText(Integer.toString(summaryDetails.getMessagesCnt())+Integer.toString(summaryDetails.getEmailCnt()));
            
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
        attachmentsLabel.setEnabled(enabled);
        callLogsLabel.setEnabled(enabled);
        contactsLabel.setEnabled(enabled);
        messagesLabel.setEnabled(enabled);
        caseReferencesPanel.setEnabled(enabled);
        fileReferencesPanel.setEnabled(enabled);
        countsPanel.setEnabled(enabled);
    }

    /**
     * Clears the text fields and OutlookViews.
     */
    private void clearControls() {
        attachmentsDataLabel.setText("");
        callLogsDataLabel.setText("");
        contactsDataLabel.setText("");
        messagesDataLabel.setText("");

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

        countsPanel = new javax.swing.JPanel();
        contactsLabel = new javax.swing.JLabel();
        messagesLabel = new javax.swing.JLabel();
        callLogsLabel = new javax.swing.JLabel();
        attachmentsLabel = new javax.swing.JLabel();
        attachmentsDataLabel = new javax.swing.JLabel();
        messagesDataLabel = new javax.swing.JLabel();
        callLogsDataLabel = new javax.swing.JLabel();
        contactsDataLabel = new javax.swing.JLabel();
        fileReferencesPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();
        caseReferencesPanel = new org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel();

        setLayout(new java.awt.GridBagLayout());

        countsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.countsPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(contactsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.contactsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(messagesLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.messagesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(callLogsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.callLogsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(attachmentsLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.attachmentsLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(attachmentsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.attachmentsDataLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(messagesDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.messagesDataLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(callLogsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.callLogsDataLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(contactsDataLabel, org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.contactsDataLabel.text")); // NOI18N

        javax.swing.GroupLayout countsPanelLayout = new javax.swing.GroupLayout(countsPanel);
        countsPanel.setLayout(countsPanelLayout);
        countsPanelLayout.setHorizontalGroup(
            countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(countsPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(messagesLabel)
                    .addComponent(callLogsLabel)
                    .addComponent(contactsLabel)
                    .addComponent(attachmentsLabel))
                .addGap(18, 18, 18)
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(attachmentsDataLabel)
                    .addComponent(contactsDataLabel)
                    .addComponent(callLogsDataLabel)
                    .addComponent(messagesDataLabel))
                .addContainerGap(959, Short.MAX_VALUE))
        );
        countsPanelLayout.setVerticalGroup(
            countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(countsPanelLayout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(messagesLabel)
                    .addComponent(messagesDataLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(callLogsLabel)
                    .addComponent(callLogsDataLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(contactsLabel)
                    .addComponent(contactsDataLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(countsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(attachmentsLabel)
                    .addComponent(attachmentsDataLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        add(countsPanel, gridBagConstraints);

        fileReferencesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.fileReferencesPanel.border.title"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(fileReferencesPanel, gridBagConstraints);

        caseReferencesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(SummaryViewer.class, "SummaryViewer.caseReferencesPanel.border.title"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(caseReferencesPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel attachmentsDataLabel;
    private javax.swing.JLabel attachmentsLabel;
    private javax.swing.JLabel callLogsDataLabel;
    private javax.swing.JLabel callLogsLabel;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel caseReferencesPanel;
    private javax.swing.JLabel contactsDataLabel;
    private javax.swing.JLabel contactsLabel;
    private javax.swing.JPanel countsPanel;
    private org.sleuthkit.autopsy.communications.relationships.OutlineViewPanel fileReferencesPanel;
    private javax.swing.JLabel messagesDataLabel;
    private javax.swing.JLabel messagesLabel;
    // End of variables declaration//GEN-END:variables

}
