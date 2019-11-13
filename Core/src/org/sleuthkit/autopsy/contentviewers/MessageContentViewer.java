/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import org.sleuthkit.autopsy.datamodel.AttachmentNode;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.text.JTextComponent;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.datamodel.AbstractFile;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.blackboardutils.FileAttachment;
import org.sleuthkit.datamodel.blackboardutils.MessageAttachments;
import org.sleuthkit.datamodel.blackboardutils.Attachment;
import org.sleuthkit.datamodel.blackboardutils.URLAttachment;

/**
 * Shows SMS/MMS/EMail messages
 */
@ServiceProvider(service = DataContentViewer.class, position = 5)
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class MessageContentViewer extends javax.swing.JPanel implements DataContentViewer {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(MessageContentViewer.class.getName());
    private static final BlackboardAttribute.Type TSK_ASSOCIATED_TYPE = new BlackboardAttribute.Type(TSK_ASSOCIATED_ARTIFACT);

    private static final int HDR_TAB_INDEX = 0;
    private static final int TEXT_TAB_INDEX = 1;
    private static final int HTML_TAB_INDEX = 2;
    private static final int RTF_TAB_INDEX = 3;
    private static final int ATTM_TAB_INDEX = 4;

    private final List<JTextComponent> textAreas;
    private final org.sleuthkit.autopsy.contentviewers.HtmlPanel htmlPanel = new org.sleuthkit.autopsy.contentviewers.HtmlPanel();
    /**
     * Artifact currently being displayed
     */
    private BlackboardArtifact artifact;
    private final DataResultPanel drp;
    private ExplorerManager drpExplorerManager;

    /**
     * Creates new MessageContentViewer
     */
    @NbBundle.Messages("MessageContentViewer.AtrachmentsPanel.title=Attachments")
    public MessageContentViewer() {
        initComponents();
        htmlPane.add(htmlPanel);
        envelopePanel.setBackground(new Color(0, 0, 0, 38));
        drp = DataResultPanel.createInstanceUninitialized(Bundle.MessageContentViewer_AtrachmentsPanel_title(), "", new TableFilterNode(Node.EMPTY, false), 0, null);
        attachmentsScrollPane.setViewportView(drp);
        msgbodyTabbedPane.setEnabledAt(ATTM_TAB_INDEX, true);

        /*
         * HTML tab uses the HtmlPanel instead of an internal text pane, so we
         * use 'null' for that index.
         */
        textAreas = Arrays.asList(headersTextArea, textbodyTextArea, null, rtfbodyTextPane);

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
        textbodyScrollPane = new javax.swing.JScrollPane();
        textbodyTextArea = new javax.swing.JTextArea();
        htmlPane = new javax.swing.JPanel();
        rtfbodyScrollPane = new javax.swing.JScrollPane();
        rtfbodyTextPane = new javax.swing.JTextPane();
        attachmentsPanel = new javax.swing.JPanel();
        viewInNewWindowButton = new javax.swing.JButton();
        attachmentsScrollPane = new javax.swing.JScrollPane();

        envelopePanel.setBackground(new java.awt.Color(204, 204, 204));

        org.openide.awt.Mnemonics.setLocalizedText(fromLabel, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.fromLabel.text")); // NOI18N

        datetimeText.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        org.openide.awt.Mnemonics.setLocalizedText(datetimeText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.datetimeText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fromText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.fromText.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(toLabel, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.toLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(toText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.toText.text")); // NOI18N
        toText.setAutoscrolls(true);
        toText.setMinimumSize(new java.awt.Dimension(27, 14));

        org.openide.awt.Mnemonics.setLocalizedText(ccLabel, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.ccLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ccText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.ccText.text")); // NOI18N
        ccText.setMinimumSize(new java.awt.Dimension(27, 14));

        org.openide.awt.Mnemonics.setLocalizedText(subjectLabel, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.subjectLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(subjectText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.subjectText.text")); // NOI18N
        subjectText.setMinimumSize(new java.awt.Dimension(26, 14));

        directionText.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        org.openide.awt.Mnemonics.setLocalizedText(directionText, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.directionText.text")); // NOI18N

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

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.headersScrollPane.TabConstraints.tabTitle"), headersScrollPane); // NOI18N

        textbodyScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        textbodyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        textbodyTextArea.setEditable(false);
        textbodyTextArea.setLineWrap(true);
        textbodyTextArea.setRows(5);
        textbodyTextArea.setWrapStyleWord(true);
        textbodyScrollPane.setViewportView(textbodyTextArea);

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.textbodyScrollPane.TabConstraints.tabTitle"), textbodyScrollPane); // NOI18N

        htmlPane.setLayout(new java.awt.BorderLayout());
        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.htmlPane.TabConstraints.tabTitle"), htmlPane); // NOI18N

        rtfbodyScrollPane.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        rtfbodyTextPane.setEditable(false);
        rtfbodyScrollPane.setViewportView(rtfbodyTextPane);

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.rtfbodyScrollPane.TabConstraints.tabTitle"), rtfbodyScrollPane); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(viewInNewWindowButton, org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.viewInNewWindowButton.text")); // NOI18N
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

        msgbodyTabbedPane.addTab(org.openide.util.NbBundle.getMessage(MessageContentViewer.class, "MessageContentViewer.attachmentsPanel.TabConstraints.tabTitle"), attachmentsPanel); // NOI18N

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
    private javax.swing.JScrollPane textbodyScrollPane;
    private javax.swing.JTextArea textbodyTextArea;
    private javax.swing.JLabel toLabel;
    private javax.swing.JLabel toText;
    private javax.swing.JButton viewInNewWindowButton;
    // End of variables declaration//GEN-END:variables

    @Override
    public void setNode(Node node) {
        if (node == null) {
            resetComponent();
            return;
        }

        artifact = node.getLookup().lookup(BlackboardArtifact.class);
        if (artifact == null) {
            resetComponent();
            return;
        }

        /*
         * If the artifact is a keyword hit, use the associated artifact as the
         * one to show in this viewer
         */
        if (artifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()) {
            try {
                getAssociatedArtifact(artifact).ifPresent(associatedArtifact -> {
                    artifact = associatedArtifact;
                });
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "error getting associated artifact", ex);
            }
        }

        if (artifact.getArtifactTypeID() == TSK_MESSAGE.getTypeID()) {
            displayMsg();
        } else if (artifact.getArtifactTypeID() == TSK_EMAIL_MSG.getTypeID()) {
            displayEmailMsg();
        } else {
            resetComponent();
        }
    }

    /**
     * Get the artifact associated with the given artifact, if there is one.
     *
     * @param artifact The artifact to get the associated artifact from. Must
     *                 not be null
     *
     * @throws TskCoreException If there is a critical error querying the DB.
     * @return An optional containing the artifact associated with the given
     *         artifact, if there is one.
     */
    private static Optional<BlackboardArtifact> getAssociatedArtifact(final BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute attribute = artifact.getAttribute(TSK_ASSOCIATED_TYPE);
        if (attribute != null) {
            return Optional.of(artifact.getSleuthkitCase().getArtifactByArtifactId(attribute.getValueLong()));
        }
        return Optional.empty();
    }

    @Override
    @NbBundle.Messages("MessageContentViewer.title=Message")
    public String getTitle() {
        return Bundle.MessageContentViewer_title();
    }

    @Override
    @NbBundle.Messages("MessageContentViewer.toolTip=Displays messages.")
    public String getToolTip() {
        return Bundle.MessageContentViewer_toolTip();
    }

    @Override
    public DataContentViewer createInstance() {
        return new MessageContentViewer();
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    final public void resetComponent() {
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
        textbodyTextArea.setText("");
        msgbodyTabbedPane.setEnabled(false);
        drp.setNode(null);
    }

    @Override
    public boolean isSupported(Node node) {
        BlackboardArtifact nodeArtifact = node.getLookup().lookup(BlackboardArtifact.class);

        if (nodeArtifact == null) {
            return false;
        }
        //if the artifact is a keyword hit, check if its associated artifact is a message or email.
        if (nodeArtifact.getArtifactTypeID() == TSK_KEYWORD_HIT.getTypeID()) {
            try {
                if (getAssociatedArtifact(nodeArtifact).map(MessageContentViewer::isMessageArtifact).orElse(false)) {
                    return true;
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "error getting associated artifact", ex);
            }
        }
        return isMessageArtifact(nodeArtifact);
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
    private static boolean isMessageArtifact(BlackboardArtifact nodeArtifact) {
        final int artifactTypeID = nodeArtifact.getArtifactTypeID();
        return artifactTypeID == TSK_EMAIL_MSG.getTypeID()
                || artifactTypeID == TSK_MESSAGE.getTypeID();
    }

    @Override
    public int isPreferred(Node node) {
        if (isSupported(node)) {
            return 7;
        }
        return 0;
    }

    /**
     * Configure the text area at the given index to show the content of the
     * given type.
     *
     * @param type  The ATTRIBUT_TYPE to show in the indexed tab.
     * @param index The index of the text area to configure.
     *
     * @throws TskCoreException
     */
    private void configureTextArea(BlackboardAttribute.ATTRIBUTE_TYPE type, int index) throws TskCoreException {
        String attributeText = getAttributeValueSafe(artifact, type);

        if (index == HTML_TAB_INDEX && StringUtils.isNotBlank(attributeText)) {
            htmlPanel.setHtmlText(attributeText);
        } else {
            JTextComponent textComponent = textAreas.get(index);
            if (textComponent != null) {
                textComponent.setText(attributeText);
                textComponent.setCaretPosition(0); //make sure we start at the top
            }
        }

        final boolean hasText = attributeText.length() > 0;

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

    private void configureAttachments() throws TskCoreException {
        
        final Set<Attachment> attachments;
        
        //  Attachments are specified in an attribute TSK_ATTACHMENTS as JSON attribute
        BlackboardAttribute attachmentsAttr = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ATTACHMENTS));
        if(attachmentsAttr != null) {
           
            attachments = new HashSet<>();
            String jsonVal = attachmentsAttr.getValueString();	                            
            MessageAttachments msgAttachments = new Gson().fromJson(jsonVal, MessageAttachments.class);
            
            Collection<FileAttachment> fileAttachments = msgAttachments.getFileAttachments();
            for (FileAttachment fileAttachment: fileAttachments) {
                attachments.add(fileAttachment);
            }
            Collection<URLAttachment> urlAttachments = msgAttachments.getUrlAttachments();
            for (URLAttachment urlAttachment: urlAttachments) {
                attachments.add(urlAttachment);
            }
        } else {    // For backward compatibility - email attachements are derived files and children of the email message artifact
                attachments = new HashSet<>();
                for (Content child: artifact.getChildren()) {
                    if (child instanceof AbstractFile) {
                        attachments.add(new FileAttachment((AbstractFile)child));
                    }
                }
        }
                
        final int numberOfAttachments = attachments.size();

        msgbodyTabbedPane.setEnabledAt(ATTM_TAB_INDEX, numberOfAttachments > 0);
        msgbodyTabbedPane.setTitleAt(ATTM_TAB_INDEX, "Attachments (" + numberOfAttachments + ")");
        drp.setNode(new TableFilterNode(new DataResultFilterNode(new AbstractNode(
                new AttachmentsChildren(attachments))), true));
    }

    private static String wrapInHtmlBody(String htmlText) {
        return "<html><body>" + htmlText + "</body></html>";
    }

    private void displayEmailMsg() {
        enableCommonFields();

        directionText.setEnabled(false);
        ccLabel.setEnabled(true);

        try {
            this.fromText.setText(getAttributeValueSafe(artifact, TSK_EMAIL_FROM));
            this.fromText.setToolTipText(getAttributeValueSafe(artifact, TSK_EMAIL_FROM));
            this.toText.setText(getAttributeValueSafe(artifact, TSK_EMAIL_TO));
            this.toText.setToolTipText(getAttributeValueSafe(artifact, TSK_EMAIL_TO));
            this.directionText.setText("");
            this.ccText.setText(getAttributeValueSafe(artifact, TSK_EMAIL_CC));
            this.ccText.setToolTipText(getAttributeValueSafe(artifact, TSK_EMAIL_CC));
            this.subjectText.setText(getAttributeValueSafe(artifact, TSK_SUBJECT));
            this.datetimeText.setText(getAttributeValueSafe(artifact, TSK_DATETIME_RCVD));

            configureTextArea(TSK_HEADERS, HDR_TAB_INDEX);
            configureTextArea(TSK_EMAIL_CONTENT_PLAIN, TEXT_TAB_INDEX);
            configureTextArea(TSK_EMAIL_CONTENT_HTML, HTML_TAB_INDEX);
            configureTextArea(TSK_EMAIL_CONTENT_RTF, RTF_TAB_INDEX);
            configureAttachments();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get attributes for email message.", ex); //NON-NLS
        }
    }

    private void displayMsg() {
        enableCommonFields();

        directionText.setEnabled(true);
        ccLabel.setEnabled(false);

        try {
            this.fromText.setText(getAttributeValueSafe(artifact, TSK_PHONE_NUMBER_FROM));
            this.toText.setText(getAttributeValueSafe(artifact, TSK_PHONE_NUMBER_TO));
            this.directionText.setText(getAttributeValueSafe(artifact, TSK_DIRECTION));
            this.ccText.setText("");
            this.subjectText.setText(getAttributeValueSafe(artifact, TSK_SUBJECT));
            this.datetimeText.setText(getAttributeValueSafe(artifact, TSK_DATETIME));

            msgbodyTabbedPane.setEnabledAt(HTML_TAB_INDEX, false);
            msgbodyTabbedPane.setEnabledAt(RTF_TAB_INDEX, false);
            msgbodyTabbedPane.setEnabledAt(HDR_TAB_INDEX, false);
            msgbodyTabbedPane.setEnabledAt(HDR_TAB_INDEX, false);
            configureTextArea(TSK_TEXT, TEXT_TAB_INDEX);
            configureAttachments();
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to get attributes for message.", ex); //NON-NLS
        }
    }

    private static String getAttributeValueSafe(BlackboardArtifact artifact, BlackboardAttribute.ATTRIBUTE_TYPE type) throws TskCoreException {
        return Optional.ofNullable(artifact.getAttribute(new BlackboardAttribute.Type(type)))
                .map(BlackboardAttribute::getDisplayString)
                .orElse("");
    }

    /**
     * Cleans out input HTML string
     *
     * @param htmlInString The HTML string to cleanse
     *
     * @return The cleansed HTML String
     */
    static private String cleanseHTML(String htmlInString) {

        Document doc = Jsoup.parse(htmlInString);

        //fix all img tags
        doc.select("img[src]").forEach(img -> img.attr("src", ""));

        return doc.html();
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
