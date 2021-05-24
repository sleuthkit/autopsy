/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.artifactviewers;

import org.sleuthkit.autopsy.datamodel.AttachmentNode;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JScrollPane;
import javax.swing.text.JTextComponent;
import org.apache.commons.lang3.StringUtils;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.contentviewers.TranslatablePanel;
import org.sleuthkit.autopsy.contentviewers.TranslatablePanel.TranslatablePanelException;
import org.sleuthkit.autopsy.contentviewers.Utilities;
import org.sleuthkit.autopsy.contentviewers.artifactviewers.MessageArtifactWorker.MesssageArtifactData;
import org.sleuthkit.autopsy.corecomponents.AutoWrappingJTextPane;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_RCVD;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CC;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_HTML;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_PLAIN;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_CONTENT_RTF;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_HEADERS;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SUBJECT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TEXT;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.attributes.MessageAttachments.Attachment;

/**
 * Shows SMS/MMS/EMail messages
 */
@ServiceProvider(service = ArtifactContentViewer.class)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class MessageArtifactViewer extends javax.swing.JPanel implements ArtifactContentViewer {

    /**
     * This is a text component viewer to be a child component to be placed in a
     * {@link TranslatablePanel TranslatablePanel}.
     */
    class TextComponent implements TranslatablePanel.ContentComponent {

        private final Component rootComponent;
        private final AutoWrappingJTextPane childTextComponent;

        TextComponent() {
            childTextComponent = new AutoWrappingJTextPane();
            childTextComponent.setEditable(false);

            JScrollPane parentComponent = new JScrollPane();
            parentComponent.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            parentComponent.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
            parentComponent.setViewportView(childTextComponent);
            rootComponent = parentComponent;
        }

        @Override
        public Component getRootComponent() {
            return rootComponent;
        }

        @Override
        public void setContent(String content, ComponentOrientation orientation) throws TranslatablePanelException {
            childTextComponent.setText(content == null ? "" : content);
            childTextComponent.setComponentOrientation(orientation);
            childTextComponent.setCaretPosition(0);
        }
    }

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MessageArtifactViewer.class.getName());
    private static final BlackboardAttribute.Type TSK_ASSOCIATED_TYPE = new BlackboardAttribute.Type(TSK_ASSOCIATED_ARTIFACT);

    private static final int HDR_TAB_INDEX = 0;
    private static final int TEXT_TAB_INDEX = 1;
    private static final int HTML_TAB_INDEX = 2;
    private static final int RTF_TAB_INDEX = 3;
    private static final int ATTM_TAB_INDEX = 4;
    private static final int ACCT_TAB_INDEX = 5;

    private final List<JTextComponent> textAreas;
    private final org.sleuthkit.autopsy.contentviewers.HtmlPanel htmlPanel = new org.sleuthkit.autopsy.contentviewers.HtmlPanel();
    private final TranslatablePanel textPanel = new TranslatablePanel(new TextComponent());
    /**
     * Artifact currently being displayed
     */
    private BlackboardArtifact artifact;
    private final DataResultPanel drp;
    private ExplorerManager drpExplorerManager;
    
    private MessageAccountPanel accountsPanel;
    
    private MessageArtifactWorker worker;

    public MessageArtifactViewer(List<JTextComponent> textAreas, DataResultPanel drp) {
        this.textAreas = textAreas;
        this.drp = drp;
    }

    /**
     * Creates new MessageContentViewer
     */
    @NbBundle.Messages("MessageArtifactViewer.AttachmentPanel.title=Attachments")
    public MessageArtifactViewer() {
        initComponents();
        accountsPanel = new MessageAccountPanel();
        
        htmlPane.add(htmlPanel);
        envelopePanel.setBackground(new Color(0, 0, 0, 38));
        drp = DataResultPanel.createInstanceUninitialized(Bundle.MessageArtifactViewer_AttachmentPanel_title(), "", new TableFilterNode(Node.EMPTY, false), 0, null);
        attachmentsScrollPane.setViewportView(drp);

        msgbodyTabbedPane.insertTab(NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.textbodyScrollPane.TabConstraints.tabTitle"),
                null,
                textPanel,
                null,
                TEXT_TAB_INDEX);

        msgbodyTabbedPane.setEnabledAt(ATTM_TAB_INDEX, true);
        
        accountScrollPane.setViewportView(accountsPanel);
        msgbodyTabbedPane.setEnabledAt(ACCT_TAB_INDEX, CentralRepository.isEnabled());

        /*
         * HTML tab uses the HtmlPanel instead of an internal text pane, so we
         * use 'null' for that index.
         */
        textAreas = Arrays.asList(headersTextArea, null, null, rtfbodyTextPane);

        Utilities.configureTextPaneAsRtf(rtfbodyTextPane);
        resetComponent();

    }

    @Override
    public void addNotify() {
        super.addNotify(); //To change body of generated methods, choose Tools | Templates.

        drp.open();
        drpExplorerManager = drp.getExplorerManager();
        drpExplorerManager.addPropertyChangeListener(evt
                -> viewInNewWindowButton.setEnabled(drpExplorerManager.getSelectedNodes().length == 1));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        envelopePanel = new javax.swing.JPanel();
        fromLabel = new javax.swing.JLabel();
        datetimeText = new javax.swing.JLabel();
        fromText = new javax.swing.JLabel();
        toLabel = new javax.swing.JLabel();
        toText = new javax.swing.JLabel();
        ccLabel = new javax.swing.JLabel();
        ccText = new javax.swing.JLabel();
        subjectLabel = new javax.swing.JLabel();
        subjectText = new javax.swing.JLabel();
        directionText = new javax.swing.JLabel();
        msgbodyTabbedPane = new javax.swing.JTabbedPane();
        headersScrollPane = new javax.swing.JScrollPane();
        headersTextArea = new javax.swing.JTextArea();
        htmlPane = new javax.swing.JPanel();
        rtfbodyScrollPane = new javax.swing.JScrollPane();
        rtfbodyTextPane = new javax.swing.JTextPane();
        attachmentsPanel = new javax.swing.JPanel();
        viewInNewWindowButton = new javax.swing.JButton();
        attachmentsScrollPane = new javax.swing.JScrollPane();
        accountsTab = new javax.swing.JPanel();
        accountScrollPane = new javax.swing.JScrollPane();

        envelopePanel.setBackground(new java.awt.Color(204, 204, 204));

        org.openide.awt.Mnemonics.setLocalizedText(fromLabel, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.fromLabel.text")); // NOI18N

        datetimeText.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        org.openide.awt.Mnemonics.setLocalizedText(datetimeText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.datetimeText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fromText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.fromText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(toLabel, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.toLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(toText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.toText.text")); // NOI18N
        toText.setAutoscrolls(true);
        toText.setMinimumSize(new java.awt.Dimension(27, 14));

        org.openide.awt.Mnemonics.setLocalizedText(ccLabel, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.ccLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ccText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.ccText.text")); // NOI18N
        ccText.setMinimumSize(new java.awt.Dimension(27, 14));

        org.openide.awt.Mnemonics.setLocalizedText(subjectLabel, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.subjectLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(subjectText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.subjectText.text")); // NOI18N
        subjectText.setMinimumSize(new java.awt.Dimension(26, 14));

        directionText.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        org.openide.awt.Mnemonics.setLocalizedText(directionText, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.directionText.text")); // NOI18N

        javax.swing.GroupLayout envelopePanelLayout = new javax.swing.GroupLayout(envelopePanel);
        envelopePanel.setLayout(envelopePanelLayout);
        envelopePanelLayout.setHorizontalGroup(
            envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(envelopePanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(envelopePanelLayout.createSequentialGroup()
                        .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fromLabel)
                            .addComponent(toLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(envelopePanelLayout.createSequentialGroup()
                                .addComponent(toText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(directionText, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(envelopePanelLayout.createSequentialGroup()
                                .addComponent(fromText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(datetimeText, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(envelopePanelLayout.createSequentialGroup()
                        .addComponent(ccLabel)
                        .addGap(26, 26, 26)
                        .addComponent(ccText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(envelopePanelLayout.createSequentialGroup()
                        .addComponent(subjectLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(subjectText, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGap(5, 5, 5))
        );
        envelopePanelLayout.setVerticalGroup(
            envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(envelopePanelLayout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fromLabel)
                    .addComponent(datetimeText)
                    .addComponent(fromText))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(toLabel)
                    .addComponent(toText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(directionText))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ccLabel)
                    .addComponent(ccText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(envelopePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(subjectLabel)
                    .addComponent(subjectText, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5))
        );

        headersScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        headersScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        headersTextArea.setEditable(false);
        headersTextArea.setColumns(20);
        headersTextArea.setLineWrap(true);
        headersTextArea.setRows(5);
        headersTextArea.setWrapStyleWord(true);
        headersScrollPane.setViewportView(headersTextArea);

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.headersScrollPane.TabConstraints.tabTitle"), headersScrollPane); // NOI18N

        htmlPane.setLayout(new java.awt.BorderLayout());
        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.htmlPane.TabConstraints.tabTitle"), htmlPane); // NOI18N

        rtfbodyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        rtfbodyTextPane.setEditable(false);
        rtfbodyScrollPane.setViewportView(rtfbodyTextPane);

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.rtfbodyScrollPane.TabConstraints.tabTitle"), rtfbodyScrollPane); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(viewInNewWindowButton, org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.viewInNewWindowButton.text")); // NOI18N
        viewInNewWindowButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewInNewWindowButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout attachmentsPanelLayout = new javax.swing.GroupLayout(attachmentsPanel);
        attachmentsPanel.setLayout(attachmentsPanelLayout);
        attachmentsPanelLayout.setHorizontalGroup(
            attachmentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(attachmentsPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(attachmentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, attachmentsPanelLayout.createSequentialGroup()
                        .addComponent(viewInNewWindowButton)
                        .addGap(3, 3, 3))
                    .addComponent(attachmentsScrollPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 647, Short.MAX_VALUE)))
        );
        attachmentsPanelLayout.setVerticalGroup(
            attachmentsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(attachmentsPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(viewInNewWindowButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(attachmentsScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 333, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.attachmentsPanel.TabConstraints.tabTitle"), attachmentsPanel); // NOI18N

        accountsTab.setLayout(new java.awt.BorderLayout());
        accountsTab.add(accountScrollPane, java.awt.BorderLayout.CENTER);

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageArtifactViewer.class, "MessageArtifactViewer.accountsTab.TabConstraints.tabTitle"), accountsTab); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(msgbodyTabbedPane)
                    .addComponent(envelopePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(5, 5, 5))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addComponent(envelopePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(msgbodyTabbedPane)
                .addGap(5, 5, 5))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void viewInNewWindowButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewInNewWindowButtonActionPerformed
        new NewWindowViewAction("View in new window", drpExplorerManager.getSelectedNodes()[0]).actionPerformed(evt);
    }//GEN-LAST:event_viewInNewWindowButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane accountScrollPane;
    private javax.swing.JPanel accountsTab;
    private javax.swing.JPanel attachmentsPanel;
    private javax.swing.JScrollPane attachmentsScrollPane;
    private javax.swing.JLabel ccLabel;
    private javax.swing.JLabel ccText;
    private javax.swing.JLabel datetimeText;
    private javax.swing.JLabel directionText;
    private javax.swing.JPanel envelopePanel;
    private javax.swing.JLabel fromLabel;
    private javax.swing.JLabel fromText;
    private javax.swing.JScrollPane headersScrollPane;
    private javax.swing.JTextArea headersTextArea;
    private javax.swing.JPanel htmlPane;
    private javax.swing.JTabbedPane msgbodyTabbedPane;
    private javax.swing.JScrollPane rtfbodyScrollPane;
    private javax.swing.JTextPane rtfbodyTextPane;
    private javax.swing.JLabel subjectLabel;
    private javax.swing.JLabel subjectText;
    private javax.swing.JLabel toLabel;
    private javax.swing.JLabel toText;
    private javax.swing.JButton viewInNewWindowButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setArtifact(BlackboardArtifact artifact) {
        resetComponent();

        if (worker != null) {
            worker.cancel(true);
            worker = null;
        }

        if (artifact == null) {
            return;
        }

        worker = new MessageArtifactWorker(artifact) {
            @Override
            public void done() {
                if (isCancelled()) {
                    return;
                }

                try {
                    MesssageArtifactData data = get();
                    MessageArtifactViewer.this.artifact = data.getArtifact();
                    if (data.getArtifact().getArtifactTypeID() == TSK_MESSAGE.getTypeID()) {
                        displayMsg(data);
                    } else if (data.getArtifact().getArtifactTypeID() == TSK_EMAIL_MSG.getTypeID()) {
                        displayEmailMsg(data);
                    } else {
                        resetComponent();
                    }

                    msgbodyTabbedPane.setEnabledAt(ACCT_TAB_INDEX, true);
                    accountsPanel.setArtifact(data.getArtifact());
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to update message viewer for artifact (%d)", artifact.getId(), ex));
                }
            }
        };

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        worker.execute();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    public void resetComponent() {
        // reset all fields
        fromText.setText("");
        fromLabel.setEnabled(false);
        toText.setText("");
        toLabel.setEnabled(false);
        ccText.setText("");
        ccLabel.setEnabled(false);
        subjectText.setText("");
        subjectLabel.setEnabled(false);
        datetimeText.setText("");
        datetimeText.setEnabled(false);
        directionText.setText("");
        directionText.setEnabled(false);

        headersTextArea.setText("");
        rtfbodyTextPane.setText("");
        htmlPanel.reset();
        textPanel.reset();
        msgbodyTabbedPane.setEnabled(false);
        drp.setNode(null);
    }

    @Override
    public boolean isSupported(BlackboardArtifact artifact) {
        if (artifact == null) {
            return false;
        }
        //if the artifact is a keyword hit, check if its associated artifact is a message or email.
        if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()) {
            try {
                if (MessageArtifactWorker.getAssociatedArtifact(artifact).map(MessageArtifactViewer::isMessageArtifact).orElse(false)) {
                    return true;
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "error getting associated artifact", ex);
            }
        }
        return isMessageArtifact(artifact);
    }

    /**
     * Is the given artifact one that can be shown in this viewer?
     *
     * @param nodeArtifact An artifact that might be a message. Must not be
     *                     null.
     *
     * @return True if the given artifact can be shown as a message in this
     *         viewer.
     */
    public static boolean isMessageArtifact(BlackboardArtifact nodeArtifact) {
        final int artifactTypeID = nodeArtifact.getArtifactTypeID();
        return artifactTypeID == TSK_EMAIL_MSG.getTypeID()
                || artifactTypeID == TSK_MESSAGE.getTypeID();
    }

    /**
     * Configure the text area at the given index to show the content of the
     * given type.
     *
     * @param text text to show in the indexed tab.
     * @param index The index of the text area to configure.
     *
     * @throws TskCoreException
     */
    private void configureTextArea(String text, int index) {
        if (index == HTML_TAB_INDEX && StringUtils.isNotBlank(text)) {
            htmlPanel.setHtmlText(text);
        } else if (index == TEXT_TAB_INDEX && StringUtils.isNotBlank(text)) {
            textPanel.setContent(text, artifact.toString());
        } else {
            JTextComponent textComponent = textAreas.get(index);
            if (textComponent != null) {
                textComponent.setText(text);
                textComponent.setCaretPosition(0); //make sure we start at the top
            }
        }

        final boolean hasText = text.length() > 0;

        msgbodyTabbedPane.setEnabledAt(index, hasText);
        if (hasText) {
            msgbodyTabbedPane.setSelectedIndex(index);
        }
    }

    private void enableCommonFields() {
        msgbodyTabbedPane.setEnabled(true);
        fromLabel.setEnabled(true);
        toLabel.setEnabled(true);
        subjectLabel.setEnabled(true);
        datetimeText.setEnabled(true);
    }

    private void configureAttachments(Set<Attachment> attachments) {
        int numberOfAttachments = attachments.size();

        msgbodyTabbedPane.setEnabledAt(ATTM_TAB_INDEX, numberOfAttachments > 0);
        msgbodyTabbedPane.setTitleAt(ATTM_TAB_INDEX, "Attachments (" + numberOfAttachments + ")");
        drp.setNode(new TableFilterNode(new DataResultFilterNode(new AbstractNode(
                new AttachmentsChildren(attachments))), true));
    }

    private void displayEmailMsg(MesssageArtifactData artifactData) {
        enableCommonFields();

        directionText.setEnabled(false);
        ccLabel.setEnabled(true);

        this.fromText.setText(artifactData.getAttributeDisplayString( TSK_EMAIL_FROM));
        this.fromText.setToolTipText(artifactData.getAttributeDisplayString(TSK_EMAIL_FROM));
        this.toText.setText(artifactData.getAttributeDisplayString(TSK_EMAIL_TO));
        this.toText.setToolTipText(artifactData.getAttributeDisplayString(TSK_EMAIL_TO));
        this.directionText.setText("");
        this.ccText.setText(artifactData.getAttributeDisplayString(TSK_EMAIL_CC));
        this.ccText.setToolTipText(artifactData.getAttributeDisplayString(TSK_EMAIL_CC));
        this.subjectText.setText(artifactData.getAttributeDisplayString(TSK_SUBJECT));
        this.datetimeText.setText(artifactData.getAttributeDisplayString(TSK_DATETIME_RCVD));

        configureTextArea(artifactData.getAttributeDisplayString(TSK_HEADERS), HDR_TAB_INDEX);
        configureTextArea(artifactData.getAttributeDisplayString(TSK_EMAIL_CONTENT_PLAIN), TEXT_TAB_INDEX);
        configureTextArea(artifactData.getAttributeDisplayString(TSK_EMAIL_CONTENT_HTML), HTML_TAB_INDEX);
        configureTextArea(artifactData.getAttributeDisplayString(TSK_EMAIL_CONTENT_RTF), RTF_TAB_INDEX);
        configureAttachments(artifactData.getAttachements());  
    }

    private void displayMsg(MesssageArtifactData artifactData) {
        enableCommonFields();

        directionText.setEnabled(true);
        ccLabel.setEnabled(false);

        this.fromText.setText(artifactData.getAttributeDisplayString(TSK_PHONE_NUMBER_FROM));
        this.toText.setText(artifactData.getAttributeDisplayString(TSK_PHONE_NUMBER_TO));
        this.directionText.setText(artifactData.getAttributeDisplayString(TSK_DIRECTION));
        this.ccText.setText("");
        this.subjectText.setText(artifactData.getAttributeDisplayString(TSK_SUBJECT));
        this.datetimeText.setText(artifactData.getAttributeDisplayString(TSK_DATETIME));

        msgbodyTabbedPane.setEnabledAt(HTML_TAB_INDEX, false);
        msgbodyTabbedPane.setEnabledAt(RTF_TAB_INDEX, false);
        msgbodyTabbedPane.setEnabledAt(HDR_TAB_INDEX, false);
        msgbodyTabbedPane.setEnabledAt(HDR_TAB_INDEX, false);
        configureTextArea(artifactData.getAttributeDisplayString(TSK_TEXT), TEXT_TAB_INDEX);
        configureAttachments(artifactData.getAttachements());
    }
    
    /**
     * Creates child nodes for message attachments.
     */
    private static class AttachmentsChildren extends Children.Keys<Attachment> {

        private final Set<Attachment> attachments;

        AttachmentsChildren(Set<Attachment> attachments) {
            this.attachments = attachments;
        }

        @Override
        protected Node[] createNodes(Attachment t) {
            return new Node[]{new AttachmentNode(t)};
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            setKeys(attachments);
        }
    }
}
