/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;
import org.sleuthkit.autopsy.centralrepository.optionspanel.GlobalSettingsPanel;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.awt.Cursor;
import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.util.EnumSet;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coordinationservice.utils.CoordinationServiceUtils;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.events.MessageServiceException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Configuration panel for multi-user settings.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class MultiUserSettingsPanel extends javax.swing.JPanel {

    private static final String HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.toolTipText");
    private static final String PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.toolTipText");
    private static final String USER_NAME_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.toolTipText");
    private static final String PASSWORD_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.toolTipText");
    private static final String USER_NAME_PROMPT_OPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgUsername.toolTipText");
    private static final String PASSWORD_PROMPT_OPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPassword.toolTipText");
    private static final String INCOMPLETE_SETTINGS_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.incomplete");
    private static final String INVALID_DB_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidDatabasePort");
    private static final String INVALID_MESSAGE_SERVICE_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidMessageServicePort");
    private static final String INVALID_INDEXING_SERVER_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidIndexingServerPort");
    private static final String INVALID_SOLR4_SERVER_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidSolr4ServerPort");
    private static final String SOLR_SERVER_NOT_CONFIGURED_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.solrNotConfigured");
    private static final String INVALID_ZK_SERVER_HOST_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidZkServerHostName");
    private static final String INVALID_ZK_SERVER_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidZkServerPort");
    private static final String SOLR8_HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr8Hostname.toolTipText");
    private static final String SOLR8_PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr8Port.toolTipText");
    private static final String SOLR4_HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr4Hostname.toolTipText");
    private static final String SOLR4_PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr4Port.toolTipText");
    private static final String ZK_HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbZkHostname.toolTipText");
    private static final String ZK_PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbZkPort.toolTipText");

    private static final long serialVersionUID = 1L;
    private final MultiUserSettingsPanelController controller;
    private final Collection<JTextField> textBoxes = new ArrayList<>();
    private final TextBoxChangedListener textBoxChangedListener;
    private static final Logger logger = Logger.getLogger(MultiUserSettingsPanel.class.getName());
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    private static final boolean isWindowsOS = PlatformUtil.isWindowsOS();

    /**
     * Creates new form AutopsyMultiUserSettingsPanel
     *
     * @param theController Controller to notify of changes.
     */
    public MultiUserSettingsPanel(MultiUserSettingsPanelController theController) {
        initComponents();
        controller = theController;
        setSize(555, 600);

        /**
         * Add text prompts to all of the text fields.
         */
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbDbHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbDbPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbDbUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbDbPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbMsgHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbMsgPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT_OPT, tbMsgUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT_OPT, tbMsgPassword));
        textPrompts.add(new TextPrompt(SOLR8_HOST_NAME_OR_IP_PROMPT, tbSolr8Hostname));
        textPrompts.add(new TextPrompt(SOLR8_PORT_PROMPT, tbSolr8Port));
        textPrompts.add(new TextPrompt(SOLR4_HOST_NAME_OR_IP_PROMPT, tbSolr4Hostname));
        textPrompts.add(new TextPrompt(SOLR4_PORT_PROMPT, tbSolr4Port));
        textPrompts.add(new TextPrompt(ZK_HOST_NAME_OR_IP_PROMPT, tbZkHostname));
        textPrompts.add(new TextPrompt(ZK_PORT_PROMPT, tbZkPort));
        configureTextPrompts(textPrompts);

        /*
         * Set each textbox with a "statusIcon" property enabling the
         * DocumentListeners to know which icon to erase when changes are made
         */
        tbDbHostname.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPort.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbUsername.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPassword.getDocument().putProperty("statusIcon", lbTestDatabase);

        tbSolr8Hostname.getDocument().putProperty("statusIcon", lbTestSolr8);
        tbSolr8Port.getDocument().putProperty("statusIcon", lbTestSolr8);
        tbSolr4Hostname.getDocument().putProperty("statusIcon", lbTestSolr4);
        tbSolr4Port.getDocument().putProperty("statusIcon", lbTestSolr4);
        tbZkHostname.getDocument().putProperty("statusIcon", lbTestZK);
        tbZkPort.getDocument().putProperty("statusIcon", lbTestZK);

        tbMsgHostname.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPort.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgUsername.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPassword.getDocument().putProperty("statusIcon", lbTestMessageService);

        /// Register for notifications when the text boxes get updated.
        textBoxChangedListener = new TextBoxChangedListener();
        textBoxes.add(tbDbHostname);
        textBoxes.add(tbDbPort);
        textBoxes.add(tbDbUsername);
        textBoxes.add(tbDbPassword);
        textBoxes.add(tbMsgHostname);
        textBoxes.add(tbMsgPort);
        textBoxes.add(tbMsgUsername);
        textBoxes.add(tbMsgPassword);
        textBoxes.add(tbSolr8Hostname);
        textBoxes.add(tbSolr8Port);
        textBoxes.add(tbSolr4Hostname);
        textBoxes.add(tbSolr4Port);
        textBoxes.add(tbZkHostname);
        textBoxes.add(tbZkPort);

        // as the user enters Solr 8 settings, we fill in the ZK settings with the embedded Solr 8 ZK connection info.
        tbSolr8Hostname.getDocument().addDocumentListener(new MyDocumentListener());

        addDocumentListeners(textBoxes, textBoxChangedListener);
        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false));
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false));
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            //disable when case is open, enable when case is closed
            load(evt.getNewValue() != null);
        });
    }

    /**
     * Sets the foreground color and transparency of a collection of text
     * prompts.
     *
     * @param textPrompts The text prompts to configure.
     */
    private static void configureTextPrompts(Collection<TextPrompt> textPrompts) {
        float alpha = 0.9f; // Mostly opaque
        for (TextPrompt textPrompt : textPrompts) {
            textPrompt.setForeground(Color.LIGHT_GRAY);
            textPrompt.changeAlpha(alpha);
        }
    }

    /**
     * Adds a change listener to a collection of text fields.
     *
     * @param textFields The text fields.
     * @param listener   The change listener.
     */
    private static void addDocumentListeners(Collection<JTextField> textFields, TextBoxChangedListener listener) {
        for (JTextField textField : textFields) {
            textField.getDocument().addDocumentListener(listener);
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

        jScrollPane = new javax.swing.JScrollPane();
        pnOverallPanel = new javax.swing.JPanel();
        pnDatabaseSettings = new javax.swing.JPanel();
        tbDbHostname = new javax.swing.JTextField();
        tbDbPort = new javax.swing.JTextField();
        tbDbUsername = new javax.swing.JTextField();
        tbDbPassword = new javax.swing.JPasswordField();
        lbDatabaseSettings = new javax.swing.JLabel();
        bnTestDatabase = new javax.swing.JButton();
        lbTestDatabase = new javax.swing.JLabel();
        lbTestDbWarning = new javax.swing.JLabel();
        pnSolrSettings = new javax.swing.JPanel();
        lbSolr8Settings = new javax.swing.JLabel();
        tbSolr8Hostname = new javax.swing.JTextField();
        tbSolr8Port = new javax.swing.JTextField();
        bnTestSolr8 = new javax.swing.JButton();
        lbTestSolr8 = new javax.swing.JLabel();
        lbWarning = new javax.swing.JLabel();
        tbSolr4Hostname = new javax.swing.JTextField();
        tbSolr4Port = new javax.swing.JTextField();
        lbSolr4Settings = new javax.swing.JLabel();
        lbZkSettings = new javax.swing.JLabel();
        tbZkHostname = new javax.swing.JTextField();
        tbZkPort = new javax.swing.JTextField();
        lbSolrNote1 = new javax.swing.JLabel();
        lbSolrNote2 = new javax.swing.JLabel();
        bnTestSolr4 = new javax.swing.JButton();
        lbTestSolr4 = new javax.swing.JLabel();
        lbTestZK = new javax.swing.JLabel();
        bnTestZK = new javax.swing.JButton();
        pnMessagingSettings = new javax.swing.JPanel();
        lbMessageServiceSettings = new javax.swing.JLabel();
        tbMsgHostname = new javax.swing.JTextField();
        tbMsgUsername = new javax.swing.JTextField();
        tbMsgPort = new javax.swing.JTextField();
        tbMsgPassword = new javax.swing.JPasswordField();
        bnTestMessageService = new javax.swing.JButton();
        lbTestMessageService = new javax.swing.JLabel();
        lbTestMessageWarning = new javax.swing.JLabel();
        cbEnableMultiUser = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();

        pnDatabaseSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbDbHostname.setFont(tbDbHostname.getFont().deriveFont(tbDbHostname.getFont().getSize()+1f));
        tbDbHostname.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.text")); // NOI18N
        tbDbHostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.toolTipText")); // NOI18N

        tbDbPort.setFont(tbDbPort.getFont().deriveFont(tbDbPort.getFont().getSize()+1f));
        tbDbPort.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.text")); // NOI18N
        tbDbPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.toolTipText")); // NOI18N

        tbDbUsername.setFont(tbDbUsername.getFont().deriveFont(tbDbUsername.getFont().getSize()+1f));
        tbDbUsername.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.text")); // NOI18N
        tbDbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.toolTipText")); // NOI18N

        tbDbPassword.setFont(tbDbPassword.getFont().deriveFont(tbDbPassword.getFont().getSize()+1f));
        tbDbPassword.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.text")); // NOI18N
        tbDbPassword.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.toolTipText")); // NOI18N

        lbDatabaseSettings.setFont(lbDatabaseSettings.getFont().deriveFont(lbDatabaseSettings.getFont().getSize()+1f));
        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbDatabaseSettings.text")); // NOI18N
        lbDatabaseSettings.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        org.openide.awt.Mnemonics.setLocalizedText(bnTestDatabase, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestDatabase.text")); // NOI18N
        bnTestDatabase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestDatabaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestDatabase, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestDatabase.text")); // NOI18N
        lbTestDatabase.setAutoscrolls(true);

        lbTestDbWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestDbWarning, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestDbWarning.text")); // NOI18N

        javax.swing.GroupLayout pnDatabaseSettingsLayout = new javax.swing.GroupLayout(pnDatabaseSettings);
        pnDatabaseSettings.setLayout(pnDatabaseSettingsLayout);
        pnDatabaseSettingsLayout.setHorizontalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbDbHostname)
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbDatabaseSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 231, Short.MAX_VALUE)
                        .addComponent(bnTestDatabase)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbDbPort)
                    .addComponent(tbDbUsername)
                    .addComponent(tbDbPassword)
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestDbWarning)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnDatabaseSettingsLayout.setVerticalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bnTestDatabase)
                    .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabaseSettings, javax.swing.GroupLayout.Alignment.TRAILING))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbTestDbWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pnSolrSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbSolr8Settings.setFont(lbSolr8Settings.getFont().deriveFont(lbSolr8Settings.getFont().getSize()+1f));
        org.openide.awt.Mnemonics.setLocalizedText(lbSolr8Settings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolr8Settings.text")); // NOI18N

        tbSolr8Hostname.setFont(tbSolr8Hostname.getFont().deriveFont(tbSolr8Hostname.getFont().getSize()+1f));
        tbSolr8Hostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr8Hostname.toolTipText")); // NOI18N

        tbSolr8Port.setFont(tbSolr8Port.getFont().deriveFont(tbSolr8Port.getFont().getSize()+1f));
        tbSolr8Port.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr8Port.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestSolr8, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestSolr8.text")); // NOI18N
        bnTestSolr8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestSolr8ActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestSolr8, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestSolr8.text")); // NOI18N

        lbWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbWarning, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbWarning.text")); // NOI18N

        tbSolr4Hostname.setFont(tbSolr4Hostname.getFont().deriveFont(tbSolr4Hostname.getFont().getSize()+1f));
        tbSolr4Hostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr4Hostname.toolTipText")); // NOI18N

        tbSolr4Port.setFont(tbSolr4Port.getFont().deriveFont(tbSolr4Port.getFont().getSize()+1f));
        tbSolr4Port.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr4Port.toolTipText")); // NOI18N

        lbSolr4Settings.setFont(lbSolr4Settings.getFont().deriveFont(lbSolr4Settings.getFont().getSize()+1f));
        org.openide.awt.Mnemonics.setLocalizedText(lbSolr4Settings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolr4Settings.text")); // NOI18N

        lbZkSettings.setFont(lbZkSettings.getFont().deriveFont(lbZkSettings.getFont().getSize()+1f));
        org.openide.awt.Mnemonics.setLocalizedText(lbZkSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbZkSettings.text")); // NOI18N

        tbZkHostname.setFont(tbZkHostname.getFont().deriveFont(tbZkHostname.getFont().getSize()+1f));
        tbZkHostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbZkHostname.toolTipText")); // NOI18N

        tbZkPort.setFont(tbZkPort.getFont().deriveFont(tbZkPort.getFont().getSize()+1f));
        tbZkPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbZkPort.toolTipText")); // NOI18N

        lbSolrNote1.setFont(lbSolrNote1.getFont().deriveFont(lbSolrNote1.getFont().getSize()+1f));
        lbSolrNote1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbSolrNote1, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolrNote1.text")); // NOI18N

        lbSolrNote2.setFont(lbSolrNote2.getFont().deriveFont(lbSolrNote2.getFont().getSize()+1f));
        org.openide.awt.Mnemonics.setLocalizedText(lbSolrNote2, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolrNote2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestSolr4, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestSolr4.text")); // NOI18N
        bnTestSolr4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestSolr4ActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestSolr4, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestSolr4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbTestZK, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestZK.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestZK, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestZK.text")); // NOI18N
        bnTestZK.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestZKActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnSolrSettingsLayout = new javax.swing.GroupLayout(pnSolrSettings);
        pnSolrSettings.setLayout(pnSolrSettingsLayout);
        pnSolrSettingsLayout.setHorizontalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolr4Settings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestSolr4)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestSolr4, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbSolr8Hostname)
                    .addComponent(tbSolr8Port)
                    .addComponent(tbSolr4Hostname)
                    .addComponent(tbSolr4Port)
                    .addComponent(tbZkHostname)
                    .addComponent(tbZkPort)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbZkSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 164, Short.MAX_VALUE)
                        .addComponent(bnTestZK)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestZK, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolr8Settings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestSolr8)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestSolr8, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbSolrNote1)
                            .addComponent(lbSolrNote2)
                            .addComponent(lbWarning))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnSolrSettingsLayout.setVerticalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addComponent(lbSolrNote1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolrNote2)
                        .addGap(28, 28, 28)
                        .addComponent(lbSolr8Settings))
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestSolr8, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(5, 5, 5))
                    .addComponent(bnTestSolr8))
                .addGap(6, 6, 6)
                .addComponent(tbSolr8Hostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbSolr8Port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbSolr4Settings, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnTestSolr4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tbSolr4Hostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tbSolr4Port, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(lbZkSettings)
                                .addComponent(bnTestZK))
                            .addComponent(lbTestZK, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tbZkHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tbZkPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(lbWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestSolr4, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        tbSolr4Port.getAccessibleContext().setAccessibleDescription(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolr4Port.toolTipText")); // NOI18N

        pnMessagingSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbMessageServiceSettings.setFont(lbMessageServiceSettings.getFont().deriveFont((float)12));
        org.openide.awt.Mnemonics.setLocalizedText(lbMessageServiceSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbMessageServiceSettings.text")); // NOI18N

        tbMsgHostname.setFont(tbMsgHostname.getFont().deriveFont(tbMsgHostname.getFont().getSize()+1f));
        tbMsgHostname.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgHostname.text")); // NOI18N
        tbMsgHostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgHostname.toolTipText")); // NOI18N

        tbMsgUsername.setFont(tbMsgUsername.getFont().deriveFont(tbMsgUsername.getFont().getSize()+1f));
        tbMsgUsername.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgUsername.text")); // NOI18N
        tbMsgUsername.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgUsername.toolTipText")); // NOI18N

        tbMsgPort.setFont(tbMsgPort.getFont().deriveFont(tbMsgPort.getFont().getSize()+1f));
        tbMsgPort.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPort.text")); // NOI18N
        tbMsgPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPort.toolTipText")); // NOI18N

        tbMsgPassword.setFont(tbMsgPassword.getFont().deriveFont(tbMsgPassword.getFont().getSize()+1f));
        tbMsgPassword.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPassword.text")); // NOI18N
        tbMsgPassword.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPassword.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestMessageService, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestMessageService.text")); // NOI18N
        bnTestMessageService.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestMessageServiceActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestMessageService, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestMessageService.text")); // NOI18N

        lbTestMessageWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestMessageWarning, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestMessageWarning.text")); // NOI18N

        javax.swing.GroupLayout pnMessagingSettingsLayout = new javax.swing.GroupLayout(pnMessagingSettings);
        pnMessagingSettings.setLayout(pnMessagingSettingsLayout);
        pnMessagingSettingsLayout.setHorizontalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbMsgHostname)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbMessageServiceSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestMessageService)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbMsgPort)
                    .addComponent(tbMsgUsername)
                    .addComponent(tbMsgPassword)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbTestMessageWarning)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnMessagingSettingsLayout.setVerticalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bnTestMessageService)
                        .addComponent(lbMessageServiceSettings))
                    .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbTestMessageWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        org.openide.awt.Mnemonics.setLocalizedText(cbEnableMultiUser, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.cbEnableMultiUser.text")); // NOI18N
        cbEnableMultiUser.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableMultiUserItemStateChanged(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setFont(tbOops.getFont().deriveFont(tbOops.getFont().getStyle() | java.awt.Font.BOLD, tbOops.getFont().getSize()+1));
        tbOops.setForeground(new java.awt.Color(255, 0, 0));
        tbOops.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbOops.text")); // NOI18N
        tbOops.setBorder(null);

        javax.swing.GroupLayout pnOverallPanelLayout = new javax.swing.GroupLayout(pnOverallPanel);
        pnOverallPanel.setLayout(pnOverallPanelLayout);
        pnOverallPanelLayout.setHorizontalGroup(
            pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnOverallPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(pnOverallPanelLayout.createSequentialGroup()
                        .addComponent(cbEnableMultiUser)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(tbOops))
                    .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnMessagingSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnSolrSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(39, 39, 39))
        );
        pnOverallPanelLayout.setVerticalGroup(
            pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnOverallPanelLayout.createSequentialGroup()
                .addGroup(pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cbEnableMultiUser))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(pnOverallPanelLayout.createSequentialGroup()
                        .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(pnMessagingSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pnSolrSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(234, Short.MAX_VALUE))
        );

        jScrollPane.setViewportView(pnOverallPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 1250, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 695, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Enables/disables the multi-user settings, based upon input provided
     *
     * @param textFields The text fields to enable/disable.
     * @param enabled    True means enable, false means disable.
     */
    private static void enableMultiUserComponents(Collection<JTextField> textFields, boolean enabled) {
        for (JTextField textField : textFields) {
            textField.setEnabled(enabled);
        }
    }

    private void cbEnableMultiUserItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbEnableMultiUserItemStateChanged
        if (!cbEnableMultiUser.isSelected()) {
            tbOops.setText("");
            bnTestDatabase.setEnabled(false);
            lbTestDatabase.setIcon(null);
            bnTestSolr8.setEnabled(false);
            lbTestSolr8.setIcon(null);
            bnTestSolr4.setEnabled(false);
            lbTestSolr4.setIcon(null);
            bnTestZK.setEnabled(false);
            lbTestZK.setIcon(null);
            bnTestMessageService.setEnabled(false);
            lbTestMessageService.setIcon(null);
            lbTestDbWarning.setText("");
            lbWarning.setText("");
            lbTestMessageWarning.setText("");
        }
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        controller.changed();
    }//GEN-LAST:event_cbEnableMultiUserItemStateChanged

    private void bnTestDatabaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestDatabaseActionPerformed
        lbTestDatabase.setIcon(null);
        lbTestDatabase.paintImmediately(lbTestDatabase.getVisibleRect());
        lbTestDbWarning.setText("");
        lbTestDbWarning.paintImmediately(lbTestDbWarning.getVisibleRect());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            CaseDbConnectionInfo info = new CaseDbConnectionInfo(
                    this.tbDbHostname.getText().trim(),
                    this.tbDbPort.getText().trim(),
                    this.tbDbUsername.getText().trim(),
                    new String(this.tbDbPassword.getPassword()),
                    DbType.POSTGRESQL);

            SleuthkitCase.tryConnect(info);
            lbTestDatabase.setIcon(goodIcon);
            lbTestDbWarning.setText("");
        } catch (TskCoreException ex) {
            lbTestDatabase.setIcon(badIcon);
            lbTestDbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestDatabaseActionPerformed

    private void bnTestMessageServiceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestMessageServiceActionPerformed
        lbTestMessageService.setIcon(null);
        lbTestMessageService.paintImmediately(lbTestMessageService.getVisibleRect());
        lbTestMessageWarning.setText("");
        lbTestMessageWarning.paintImmediately(lbTestMessageWarning.getVisibleRect());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        int port;
        try {
            port = Integer.parseInt(this.tbMsgPort.getText().trim());
        } catch (NumberFormatException ex) {
            lbTestMessageService.setIcon(badIcon);
            lbTestMessageWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.InvalidPortNumber"));
            return;
        }

        MessageServiceConnectionInfo info = new MessageServiceConnectionInfo(
                this.tbMsgHostname.getText().trim(),
                port,
                this.tbMsgUsername.getText().trim(),
                new String(this.tbMsgPassword.getPassword()));
        try {
            info.tryConnect();
            lbTestMessageService.setIcon(goodIcon);
            lbTestMessageWarning.setText("");
        } catch (MessageServiceException ex) {
            lbTestMessageService.setIcon(badIcon);
            lbTestMessageWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestMessageServiceActionPerformed

    private void bnTestSolr8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestSolr8ActionPerformed
        lbTestSolr8.setIcon(null);
        lbTestSolr8.paintImmediately(lbTestSolr8.getVisibleRect());
        lbWarning.setText("");
        lbWarning.paintImmediately(lbWarning.getVisibleRect());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        try {
            if (kwsService != null) {
                // test Solr 8 connectivity
                if (tbSolr8Port.getText().trim().isEmpty() || tbSolr8Hostname.getText().trim().isEmpty()) {
                    lbTestSolr8.setIcon(badIcon);
                    lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.Solr8ConnectionInfoMissing.error"));
                    return;
                }
                int port = Integer.parseInt(tbSolr8Port.getText().trim());
                kwsService.tryConnect(tbSolr8Hostname.getText().trim(), port);
                lbTestSolr8.setIcon(goodIcon);
                lbWarning.setText("");
            } else {
                lbTestSolr8.setIcon(badIcon);
                lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.KeywordSearchNull")); 
            }
        } catch (NumberFormatException ex) {
            lbTestSolr8.setIcon(badIcon);
            lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.InvalidPortNumber"));
        } catch (KeywordSearchServiceException ex) {
            lbTestSolr8.setIcon(badIcon);
            lbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestSolr8ActionPerformed

    private void bnTestSolr4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestSolr4ActionPerformed
        lbTestSolr4.setIcon(null);
        lbTestSolr4.paintImmediately(lbTestSolr4.getVisibleRect());
        lbWarning.setText("");
        lbWarning.paintImmediately(lbWarning.getVisibleRect());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        KeywordSearchService kwsService = Lookup.getDefault().lookup(KeywordSearchService.class);
        try {
            if (kwsService != null) {
                // test Solr 4 conenctivity                
                if (tbSolr4Port.getText().trim().isEmpty() || tbSolr4Hostname.getText().trim().isEmpty()) {
                    lbTestSolr4.setIcon(badIcon);
                    lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.Solr4ConnectionInfoMissing.error"));
                    return;
                }
                int port = Integer.parseInt(tbSolr4Port.getText().trim());
                kwsService.tryConnect(tbSolr4Hostname.getText().trim(), port);
                lbTestSolr4.setIcon(goodIcon);
                lbWarning.setText("");
            } else {
                lbTestSolr4.setIcon(badIcon);
                lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.KeywordSearchNull"));
            }
        } catch (NumberFormatException ex) {
            lbTestSolr4.setIcon(badIcon);
            lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.InvalidPortNumber"));
        } catch (KeywordSearchServiceException ex) {
            lbTestSolr4.setIcon(badIcon);
            lbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestSolr4ActionPerformed

    private void bnTestZKActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestZKActionPerformed
        lbTestZK.setIcon(null);
        lbTestZK.paintImmediately(lbTestZK.getVisibleRect());
        lbWarning.setText("");
        lbWarning.paintImmediately(lbWarning.getVisibleRect());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            // test ZooKeeper connectivity (ZK settings are mandatory)
            if (tbZkPort.getText().trim().isEmpty() || tbZkHostname.getText().trim().isEmpty()) {
                lbTestZK.setIcon(badIcon);
                lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.UnableToConnectToZK"));
                return;
            }

            if (false == CoordinationServiceUtils.isZooKeeperAccessible(tbZkHostname.getText().trim(), tbZkPort.getText().trim())) {
                lbTestZK.setIcon(badIcon);
                lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.UnableToConnectToZK"));
                return;
            }

            lbTestZK.setIcon(goodIcon);
            lbWarning.setText("");
        } catch (NumberFormatException ex) {
            lbTestZK.setIcon(badIcon);
            lbWarning.setText(NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.InvalidPortNumber"));
        } catch (InterruptedException | IOException ex) {
            // ZK exceptions
            lbTestZK.setIcon(badIcon);
            lbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }//GEN-LAST:event_bnTestZKActionPerformed

    @Messages({
       "MultiUserSettingsPanel_Close_Case_To_Modify=Close case to modfy settings" 
    })
    
    void load(boolean caseOpen) {
        lbTestDatabase.setIcon(null);
        lbTestSolr8.setIcon(null);
        lbTestSolr4.setIcon(null);
        lbTestZK.setIcon(null);
        lbTestMessageService.setIcon(null);
        lbTestDbWarning.setText("");
        lbWarning.setText("");
        lbTestMessageWarning.setText("");

        try {
            CaseDbConnectionInfo dbInfo = UserPreferences.getDatabaseConnectionInfo();
            tbDbHostname.setText(dbInfo.getHost().trim());
            tbDbPort.setText(dbInfo.getPort().trim());
            tbDbUsername.setText(dbInfo.getUserName().trim());
            tbDbPassword.setText(dbInfo.getPassword());
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
        }

        try {
            MessageServiceConnectionInfo msgServiceInfo = UserPreferences.getMessageServiceConnectionInfo();
            tbMsgHostname.setText(msgServiceInfo.getHost().trim());
            tbMsgPort.setText(Integer.toString(msgServiceInfo.getPort()));
            tbMsgUsername.setText(msgServiceInfo.getUserName().trim());
            tbMsgPassword.setText(msgServiceInfo.getPassword());
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
        }

        populateSolrAndZkSettings();

        bnTestDatabase.setEnabled(false);
        bnTestSolr8.setEnabled(false);
        bnTestSolr4.setEnabled(false);
        bnTestZK.setEnabled(false);
        bnTestMessageService.setEnabled(false);

        cbEnableMultiUser.setSelected(UserPreferences.getIsMultiUserModeEnabled());
        
        // When a case is open, prevent the user from changing
        // multi-user settings.
        cbEnableMultiUser.setEnabled(UserPreferences.isMultiUserSupported() && !caseOpen);
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected() && !caseOpen);
               
        this.valid(caseOpen); // trigger validation to enable buttons based on current settings
    }

    private void populateSolrAndZkSettings() {

        String indexingServerHost = UserPreferences.getIndexingServerHost().trim();
        if (!indexingServerHost.isEmpty()) {
            tbSolr8Hostname.setText(indexingServerHost);
        }
        String indexingServerPort = UserPreferences.getIndexingServerPort().trim();
        if (portNumberIsValid(indexingServerPort)) {
            tbSolr8Port.setText(indexingServerPort);
        }

        String solr4ServerHost = UserPreferences.getSolr4ServerHost().trim();
        if (!solr4ServerHost.isEmpty()) {
            tbSolr4Hostname.setText(solr4ServerHost);
        }
        String solr4ServerPort = UserPreferences.getSolr4ServerPort().trim();
        if (portNumberIsValid(solr4ServerPort)) {
            tbSolr4Port.setText(solr4ServerPort);
        }

        // if there are existing valid ZK settings, use those
        String zkServerPort = UserPreferences.getZkServerPort().trim();
        if (portNumberIsValid(zkServerPort)) {
            tbZkPort.setText(zkServerPort); // gets default ZK port, which is Solr port number + 1000
        }
        String zkServerHost = UserPreferences.getZkServerHost().trim();
        if (!zkServerHost.isEmpty()) {
            tbZkHostname.setText(zkServerHost);
            return;
        }

        // If there are no previous Solr 4 settings, use Solr 8 settings
        // to fill in the ZK settings with the embedded Solr 8 ZK connection info.
        if (solr4ServerHost.isEmpty() && !indexingServerHost.isEmpty()) {
            tbZkHostname.setText(indexingServerHost);
            tbZkPort.setText(zkServerPort); // gets default ZK port, which is Solr port number + 1000
            return;
        }

        // If there are existing Solr 4 settings and no Solr 8 settings, 
        // pre-populate the ZK settings with the Solr 4 embedded ZK settings.
        if (!solr4ServerHost.isEmpty() && indexingServerHost.isEmpty()) {
            tbZkHostname.setText(solr4ServerHost);
            tbZkPort.setText(zkServerPort); // gets default ZK port, which is Solr port number + 1000
            return;
        }
    }

    /**
     * Tests whether or not values have been entered in all of the database
     * settings text fields.
     *
     * @return True or false.
     */
    private boolean databaseFieldsArePopulated() {
        return !tbDbHostname.getText().trim().isEmpty()
                && !tbDbPort.getText().trim().isEmpty()
                && !tbDbUsername.getText().trim().isEmpty()
                && tbDbPassword.getPassword().length != 0;
    }

    /**
     * Tests whether or not values have been entered in all of the mandatory
     * Solr settings text fields. 
     *
     * @return True or false.
     */
    private boolean solr4FieldsArePopulated() {

        // check if Solr 4 settings are set
        if (!tbSolr4Hostname.getText().trim().isEmpty()
                && !tbSolr4Port.getText().trim().isEmpty()) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Tests whether or not values have been entered in all of the mandatory
     * Solr settings text fields. 
     *
     * @return True or false.
     */
    private boolean solr8FieldsArePopulated() {

        // check if Solr 8 settings are set
        if (!tbSolr8Hostname.getText().trim().isEmpty()
                && !tbSolr8Port.getText().trim().isEmpty()) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Tests whether or not values have been entered in all of the mandatory
     * ZK settings text fields. 
     *
     * @return True or false.
     */
    private boolean ZooKeeperFieldsArePopulated() {
        // check if ZK settings are set
        return (!tbZkHostname.getText().trim().isEmpty()
                && !tbZkPort.getText().trim().isEmpty());
    }    

    /**
     * Tests whether or not values have been entered in all of the required
     * message service settings text fields.
     *
     * @return True or false.
     */
    private boolean messageServiceFieldsArePopulated() {

        if ((tbMsgHostname.getText().trim().isEmpty())
                || (tbMsgPort.getText().trim().isEmpty())) {
            return false;
        }

        // user name and pw are optional, but make sure they are both set or both empty
        boolean isUserSet = (tbMsgUsername.getText().trim().isEmpty() == false);
        boolean isPwSet = (tbMsgPassword.getPassword().length != 0);
        return (isUserSet == isPwSet);
    }

    void store() {
        boolean prevSelected = UserPreferences.getIsMultiUserModeEnabled();
        CaseDbConnectionInfo prevConn = null;
        try {
            prevConn = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "There was an error while fetching previous connection settings while trying to save", ex); //NON-NLS
        }

        boolean multiUserCasesEnabled = cbEnableMultiUser.isSelected();
        UserPreferences.setIsMultiUserModeEnabled(multiUserCasesEnabled);

        CaseDbConnectionInfo info = null;

        if (multiUserCasesEnabled == true) {

            // Check if aplication restart is required.
            boolean needsRestart = false;
            // don't check if entring multi user data for the first time
            if (prevSelected == true) {
                needsRestart = isRestartRequired();
            }

            /*
             * Currently only supporting multi-user cases with PostgreSQL case
             * databases.
             */
            DbType dbType = DbType.POSTGRESQL;
            info = new CaseDbConnectionInfo(
                    tbDbHostname.getText().trim(),
                    tbDbPort.getText().trim(),
                    tbDbUsername.getText().trim(),
                    new String(tbDbPassword.getPassword()),
                    dbType);
            try {
                UserPreferences.setDatabaseConnectionInfo(info);
            } catch (UserPreferencesException ex) {
                logger.log(Level.SEVERE, "Error saving case database connection info", ex); //NON-NLS
            }

            int msgServicePort = 0;
            try {
                msgServicePort = Integer.parseInt(this.tbMsgPort.getText().trim());
            } catch (NumberFormatException ex) {
                logger.log(Level.SEVERE, "Could not parse messaging service port setting", ex);
            }

            MessageServiceConnectionInfo msgServiceInfo = new MessageServiceConnectionInfo(
                    tbMsgHostname.getText().trim(),
                    msgServicePort,
                    tbMsgUsername.getText().trim(),
                    new String(tbMsgPassword.getPassword()));

            try {
                UserPreferences.setMessageServiceConnectionInfo(msgServiceInfo);
            } catch (UserPreferencesException ex) {
                logger.log(Level.SEVERE, "Error saving messaging service connection info", ex); //NON-NLS
            }

            UserPreferences.setIndexingServerHost(tbSolr8Hostname.getText().trim());
            String solr8port = tbSolr8Port.getText().trim();
            if (!solr8port.isEmpty()) {
                UserPreferences.setIndexingServerPort(Integer.parseInt(solr8port));
            }
            UserPreferences.setSolr4ServerHost(tbSolr4Hostname.getText().trim());
            UserPreferences.setSolr4ServerPort(tbSolr4Port.getText().trim());
            UserPreferences.setZkServerHost(tbZkHostname.getText().trim());
            UserPreferences.setZkServerPort(tbZkPort.getText().trim());

            if (needsRestart) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this,
                            NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.MustRestart"),
                            NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.restartRequiredLabel.text"),
                            JOptionPane.WARNING_MESSAGE);
                });
            }
        }

        // trigger changes to whether or not user can use multi user settings for central repository
        if (prevSelected != multiUserCasesEnabled || !areCaseDbConnectionEqual(prevConn, info)) {
            GlobalSettingsPanel.onMultiUserChange(this, prevSelected, multiUserCasesEnabled);
        }
    }

    private boolean isRestartRequired() {
        // if ZK was previously configured
        if (!UserPreferences.getZkServerHost().isEmpty()) {
            // Restart is required any time ZK info has changed
            if (!(tbZkHostname.getText().trim().equalsIgnoreCase(UserPreferences.getZkServerHost()))
                    || !(tbZkPort.getText().trim().equals(UserPreferences.getZkServerPort()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean arePropsEqual(Object a, Object b) {
        if (a == null || b == null) {
            return (a == null && b == null);
        } else {
            return a.equals(b);
        }
    }

    private static boolean areCaseDbConnectionEqual(CaseDbConnectionInfo a, CaseDbConnectionInfo b) {
        if (a == null || b == null) {
            return (a == null && b == null);
        }

        return arePropsEqual(a.getDbType(), b.getDbType())
                && arePropsEqual(a.getHost(), b.getHost())
                && arePropsEqual(a.getPassword(), b.getPassword())
                && arePropsEqual(a.getPort(), b.getPort())
                && arePropsEqual(a.getUserName(), b.getUserName());
    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    boolean valid(boolean caseOpen) {
        if(caseOpen) {
            tbOops.setText(Bundle.MultiUserSettingsPanel_Close_Case_To_Modify());
        } else {
            tbOops.setText("");
        }
        
        if (cbEnableMultiUser.isSelected()) {
            return checkFieldsAndEnableButtons(caseOpen)
                    && databaseSettingsAreValid()
                    && indexingServerSettingsAreValid()
                    && messageServiceSettingsAreValid();
        } else {
            return true;
        }
    }

    /**
     * Tests whether or not all of the settings components are populated and
     * sets the test buttons appropriately.
     *
     * @return True or false.
     */
    boolean checkFieldsAndEnableButtons(boolean caseOpen) {
        boolean result = true;

        boolean dbPopulated = databaseFieldsArePopulated();
        boolean solr4Populated = solr4FieldsArePopulated();
        boolean solr8Populated = solr8FieldsArePopulated();
        boolean zkPopulated = ZooKeeperFieldsArePopulated();
        boolean messageServicePopulated = messageServiceFieldsArePopulated();

        // PostgreSQL Database
        bnTestDatabase.setEnabled(dbPopulated && !caseOpen);

        // Solr Indexing
        bnTestSolr8.setEnabled(solr8Populated && !caseOpen);
        bnTestSolr4.setEnabled(solr4Populated && !caseOpen);
        bnTestZK.setEnabled(zkPopulated && !caseOpen);

        // ActiveMQ Messaging
        bnTestMessageService.setEnabled(messageServicePopulated && !caseOpen);

        if (dbPopulated && messageServicePopulated && zkPopulated && (solr8Populated || solr4Populated)) {
            result = true;
        } else {
            // We don't even have everything filled out
            result = false;
            tbOops.setText(INCOMPLETE_SETTINGS_MSG);
        }
        return result;
    }

    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    boolean databaseSettingsAreValid() {
        if (portNumberIsValid(tbDbPort.getText().trim())) {
            return true;
        } else {
            tbOops.setText(INVALID_DB_PORT_MSG);
            return false;
        }
    }

    /**
     * Tests whether or not the message service settings are valid.
     *
     * @return True or false.
     */
    boolean messageServiceSettingsAreValid() {
        if (!portNumberIsValid(tbMsgPort.getText().trim())) {
            tbOops.setText(INVALID_MESSAGE_SERVICE_PORT_MSG);
            return false;
        }

        return true;
    }

    /**
     * Tests whether or not the indexing server settings are valid.
     *
     * @return True or false.
     */
    boolean indexingServerSettingsAreValid() {

        String solr8Port = tbSolr8Port.getText().trim();
        if (!solr8Port.isEmpty() && !portNumberIsValid(solr8Port)) {
            // if the port is specified, it has to be valid
            tbOops.setText(INVALID_INDEXING_SERVER_PORT_MSG);
            return false;
        }

        String solr4Port = tbSolr4Port.getText().trim();
        if (!solr4Port.isEmpty() && !portNumberIsValid(solr4Port)) {
            // if the port is specified, it has to be valid
            tbOops.setText(INVALID_SOLR4_SERVER_PORT_MSG);
            return false;
        }

        // either Solr 8 or/and Solr 4 seetings must be specified
        boolean solrConfigured = false;

        // check if Solr 8 settings are set
        if (!tbSolr8Hostname.getText().trim().isEmpty()
                && !tbSolr8Port.getText().trim().isEmpty()) {
            solrConfigured = true;
        }

        // check if Solr 4 settings are set
        if (!tbSolr4Hostname.getText().trim().isEmpty()
                && !tbSolr4Port.getText().trim().isEmpty()) {
            solrConfigured = true;
        }

        if (!solrConfigured) {
            tbOops.setText(SOLR_SERVER_NOT_CONFIGURED_MSG);
            return false;
        }

        // ZK settings are mandatory    
        if (tbZkHostname.getText().trim().isEmpty()) {
            tbOops.setText(INVALID_ZK_SERVER_HOST_MSG);
            return false;
        }

        // ZK settings are mandatory  
        String zkPort = tbZkPort.getText().trim();
        if (zkPort.isEmpty() || !portNumberIsValid(zkPort)) {
            // if the port is specified, it has to be valid
            tbOops.setText(INVALID_ZK_SERVER_PORT_MSG);
            return false;
        }

        return true;
    }

    /**
     * Determines whether or not a port number is within the range of valid port
     * numbers.
     *
     * @param portNumber The port number as a string.
     *
     * @return True or false.
     */
    private static boolean portNumberIsValid(String portNumber) {
        try {
            int value = Integer.parseInt(portNumber);
            if (value < 0 || value > 65535) { // invalid port numbers
                return false;
            }
        } catch (NumberFormatException detailsNotImportant) {
            return false;
        }
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnTestDatabase;
    private javax.swing.JButton bnTestMessageService;
    private javax.swing.JButton bnTestSolr4;
    private javax.swing.JButton bnTestSolr8;
    private javax.swing.JButton bnTestZK;
    private javax.swing.JCheckBox cbEnableMultiUser;
    private javax.swing.JScrollPane jScrollPane;
    private javax.swing.JLabel lbDatabaseSettings;
    private javax.swing.JLabel lbMessageServiceSettings;
    private javax.swing.JLabel lbSolr4Settings;
    private javax.swing.JLabel lbSolr8Settings;
    private javax.swing.JLabel lbSolrNote1;
    private javax.swing.JLabel lbSolrNote2;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestDbWarning;
    private javax.swing.JLabel lbTestMessageService;
    private javax.swing.JLabel lbTestMessageWarning;
    private javax.swing.JLabel lbTestSolr4;
    private javax.swing.JLabel lbTestSolr8;
    private javax.swing.JLabel lbTestZK;
    private javax.swing.JLabel lbWarning;
    private javax.swing.JLabel lbZkSettings;
    private javax.swing.JPanel pnDatabaseSettings;
    private javax.swing.JPanel pnMessagingSettings;
    private javax.swing.JPanel pnOverallPanel;
    private javax.swing.JPanel pnSolrSettings;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JPasswordField tbDbPassword;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    private javax.swing.JTextField tbMsgHostname;
    private javax.swing.JPasswordField tbMsgPassword;
    private javax.swing.JTextField tbMsgPort;
    private javax.swing.JTextField tbMsgUsername;
    private javax.swing.JTextField tbOops;
    private javax.swing.JTextField tbSolr4Hostname;
    private javax.swing.JTextField tbSolr4Port;
    private javax.swing.JTextField tbSolr8Hostname;
    private javax.swing.JTextField tbSolr8Port;
    private javax.swing.JTextField tbZkHostname;
    private javax.swing.JTextField tbZkPort;
    // End of variables declaration//GEN-END:variables

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            controller.changed();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            controller.changed();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon");
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            controller.changed();
        }
    }

    private class MyDocumentListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            tbZkHostname.setText(tbSolr8Hostname.getText().trim());
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            tbZkHostname.setText(tbSolr8Hostname.getText().trim());
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            tbZkHostname.setText(tbSolr8Hostname.getText().trim());
        }
    };
}
