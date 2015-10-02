/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.corecomponents;

import org.sleuthkit.autopsy.coreutils.SolrServiceConnectionTester;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.NbBundle;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.TskData.DbType;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.awt.Cursor;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;

public final class MultiUserSettingsPanel extends javax.swing.JPanel {

    private static final String HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.toolTipText");
    private static final String PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.toolTipText");
    private static final String USER_NAME_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.toolTipText");
    private static final String PASSWORD_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.toolTipText");
    private static final String INCOMPLETE_SETTINGS_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.incomplete");
    private static final String INVALID_DB_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidDatabasePort");
    private static final String INVALID_MESSAGE_SERVICE_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidMessageServicePort");
    private static final String INVALID_INDEXING_SERVER_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidIndexingServerPort");
    private static final long serialVersionUID = 1L;
    private final MultiUserSettingsPanelController controller;
    private final Collection<JTextField> textBoxes = new ArrayList<>();
    private final TextBoxChangedListener textBoxChangedListener;
    private static final Logger logger = Logger.getLogger(MultiUserSettingsPanel.class.getName());
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    // private final ImageIcon untestedIcon;

    /**
     * Creates new form AutopsyMultiUserSettingsPanel
     *
     * @param theController Controller to notify of changes.
     */
    public MultiUserSettingsPanel(MultiUserSettingsPanelController theController) {
        initComponents();
        controller = theController;

        /**
         * Add text prompts to all of the text fields.
         */
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbDbHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbDbPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbDbUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbDbPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbMsgHost));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbMsgPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbMsgUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbMsgPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbSolrHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbSolrPort));
        configureTextPrompts(textPrompts);

        /* Set each textbox with a "statusIcon" property enabling the 
        DocumentListeners to know which icon to erase when changes are made */
        
        tbDbHostname.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPort.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbUsername.getDocument().putProperty("statusIcon", lbTestDatabase);
        tbDbPassword.getDocument().putProperty("statusIcon", lbTestDatabase);
        
        tbSolrHostname.getDocument().putProperty("statusIcon", lbTestSolr);
        tbSolrPort.getDocument().putProperty("statusIcon", lbTestSolr);
        
        tbMsgHost.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPort.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgUsername.getDocument().putProperty("statusIcon", lbTestMessageService);
        tbMsgPassword.getDocument().putProperty("statusIcon", lbTestMessageService);
        
        /// Register for notifications when the text boxes get updated.
        textBoxChangedListener = new TextBoxChangedListener();
        textBoxes.add(tbDbHostname);
        textBoxes.add(tbDbPort);
        textBoxes.add(tbDbUsername);
        textBoxes.add(tbDbPassword);
        textBoxes.add(tbMsgHost);
        textBoxes.add(tbMsgPort);
        textBoxes.add(tbMsgUsername);
        textBoxes.add(tbMsgPassword);
        textBoxes.add(tbSolrHostname);
        textBoxes.add(tbSolrPort);
        
        addDocumentListeners(textBoxes, textBoxChangedListener);
        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false));
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false));
        lbTestDatabase.setIcon(null);
        lbTestSolr.setIcon(null);
        lbTestMessageService.setIcon(null);
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
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

        pnOverallPanel = new javax.swing.JPanel();
        pnDatabaseSettings = new javax.swing.JPanel();
        tbDbHostname = new javax.swing.JTextField();
        tbDbPort = new javax.swing.JTextField();
        tbDbUsername = new javax.swing.JTextField();
        tbDbPassword = new javax.swing.JPasswordField();
        lbDatabaseSettings = new javax.swing.JLabel();
        bnTestDatabase = new javax.swing.JButton();
        lbTestDatabase = new javax.swing.JLabel();
        pnSolrSettings = new javax.swing.JPanel();
        lbSolrSettings = new javax.swing.JLabel();
        tbSolrHostname = new javax.swing.JTextField();
        tbSolrPort = new javax.swing.JTextField();
        bnTestSolr = new javax.swing.JButton();
        lbTestSolr = new javax.swing.JLabel();
        pnMessagingSettings = new javax.swing.JPanel();
        lbMessageServiceSettings = new javax.swing.JLabel();
        tbMsgHost = new javax.swing.JTextField();
        tbMsgUsername = new javax.swing.JTextField();
        tbMsgPort = new javax.swing.JTextField();
        tbMsgPassword = new javax.swing.JPasswordField();
        bnTestMessageService = new javax.swing.JButton();
        lbTestMessageService = new javax.swing.JLabel();
        cbEnableMultiUser = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();

        pnDatabaseSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbDbHostname.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbHostname.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.text")); // NOI18N
        tbDbHostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbHostname.toolTipText")); // NOI18N

        tbDbPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbPort.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.text")); // NOI18N
        tbDbPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPort.toolTipText")); // NOI18N

        tbDbUsername.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbUsername.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.text")); // NOI18N
        tbDbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbUsername.toolTipText")); // NOI18N

        tbDbPassword.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbDbPassword.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.text")); // NOI18N
        tbDbPassword.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbDbPassword.toolTipText")); // NOI18N

        lbDatabaseSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
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
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestDatabase)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbDbPort)
                    .addComponent(tbDbUsername)
                    .addComponent(tbDbPassword))
                .addContainerGap())
        );
        pnDatabaseSettingsLayout.setVerticalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDatabaseSettings)
                    .addComponent(bnTestDatabase)
                    .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(13, 13, 13))
        );

        pnSolrSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbSolrSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbSolrSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolrSettings.text")); // NOI18N

        tbSolrHostname.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbSolrHostname.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolrHostname.toolTipText")); // NOI18N

        tbSolrPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbSolrPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbSolrPort.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestSolr, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestSolr.text")); // NOI18N
        bnTestSolr.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestSolrActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestSolr, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestSolr.text")); // NOI18N

        javax.swing.GroupLayout pnSolrSettingsLayout = new javax.swing.GroupLayout(pnSolrSettings);
        pnSolrSettings.setLayout(pnSolrSettingsLayout);
        pnSolrSettingsLayout.setHorizontalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbSolrHostname)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolrSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnTestSolr)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestSolr, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbSolrPort))
                .addContainerGap())
        );
        pnSolrSettingsLayout.setVerticalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbSolrSettings)
                        .addComponent(bnTestSolr))
                    .addComponent(lbTestSolr, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbSolrHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tbSolrPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(45, 45, 45))
        );

        pnMessagingSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbMessageServiceSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbMessageServiceSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbMessageServiceSettings.text")); // NOI18N

        tbMsgHost.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgHost.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgHost.text")); // NOI18N
        tbMsgHost.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgHost.toolTipText")); // NOI18N

        tbMsgUsername.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgUsername.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgUsername.text")); // NOI18N
        tbMsgUsername.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgUsername.toolTipText")); // NOI18N

        tbMsgPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgPort.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPort.text")); // NOI18N
        tbMsgPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPort.toolTipText")); // NOI18N

        tbMsgPassword.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbMsgPassword.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPassword.text")); // NOI18N
        tbMsgPassword.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbMsgPassword.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestMessageService, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.bnTestMessageService.text")); // NOI18N
        bnTestMessageService.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestMessageServiceActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestMessageService, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbTestMessageService.text")); // NOI18N

        javax.swing.GroupLayout pnMessagingSettingsLayout = new javax.swing.GroupLayout(pnMessagingSettings);
        pnMessagingSettings.setLayout(pnMessagingSettingsLayout);
        pnMessagingSettingsLayout.setHorizontalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbMsgHost)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbMessageServiceSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 229, Short.MAX_VALUE)
                        .addComponent(bnTestMessageService)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbMsgPort)
                    .addComponent(tbMsgUsername)
                    .addComponent(tbMsgPassword))
                .addContainerGap())
        );
        pnMessagingSettingsLayout.setVerticalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(lbMessageServiceSettings)
                        .addComponent(bnTestMessageService))
                    .addComponent(lbTestMessageService, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tbMsgHost, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbMsgPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(cbEnableMultiUser, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.cbEnableMultiUser.text")); // NOI18N
        cbEnableMultiUser.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableMultiUserItemStateChanged(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
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
                    .addComponent(pnSolrSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnMessagingSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        pnOverallPanelLayout.setVerticalGroup(
            pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnOverallPanelLayout.createSequentialGroup()
                .addGroup(pnOverallPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cbEnableMultiUser))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnSolrSettings, javax.swing.GroupLayout.PREFERRED_SIZE, 106, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnMessagingSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnOverallPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnOverallPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Enables/disables the multi-user settings, based upon input provided
     *
     * @param enabled true means enable, false means disable
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
            bnTestSolr.setEnabled(false);
            lbTestSolr.setIcon(null);
            bnTestMessageService.setEnabled(false);
            lbTestMessageService.setIcon(null);
        }
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        controller.changed();
    }//GEN-LAST:event_cbEnableMultiUserItemStateChanged

    private void bnTestDatabaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestDatabaseActionPerformed
        lbTestDatabase.setIcon(null);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        CaseDbConnectionInfo dbInfo = new CaseDbConnectionInfo(
                this.tbDbHostname.getText(),
                this.tbDbPort.getText(),
                this.tbDbUsername.getText(),
                new String(this.tbDbPassword.getPassword()),
                DbType.POSTGRESQL);
        if (dbInfo.canConnect()) {
            lbTestDatabase.setIcon(goodIcon);
        } else {
            lbTestDatabase.setIcon(badIcon);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnTestDatabaseActionPerformed

    private void bnTestMessageServiceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestMessageServiceActionPerformed
        lbTestMessageService.setIcon(null);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        MessageServiceConnectionInfo messagingConnectionInfo = new MessageServiceConnectionInfo(
                this.tbMsgUsername.getText(),
                new String(this.tbMsgPassword.getPassword()),
                this.tbMsgHost.getText(),
                this.tbMsgPort.getText());
        if (messagingConnectionInfo.canConnect()) {
            lbTestMessageService.setIcon(goodIcon);
        } else {
            lbTestMessageService.setIcon(badIcon);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnTestMessageServiceActionPerformed

    private void bnTestSolrActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestSolrActionPerformed
        lbTestSolr.setIcon(null);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        if (SolrServiceConnectionTester.canConnect(tbSolrHostname.getText(), tbSolrPort.getText())) {
            lbTestSolr.setIcon(goodIcon);
        } else {
            lbTestSolr.setIcon(badIcon);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnTestSolrActionPerformed

    void load() {
        CaseDbConnectionInfo dbInfo = UserPreferences.getDatabaseConnectionInfo();
        tbDbHostname.setText(dbInfo.getHost().trim());
        tbDbPort.setText(dbInfo.getPort().trim());
        tbDbUsername.setText(dbInfo.getUserName().trim());
        tbDbPassword.setText(dbInfo.getPassword());

        MessageServiceConnectionInfo msgServiceInfo = UserPreferences.getMessageServiceConnectionInfo();
        tbMsgHost.setText(msgServiceInfo.getHost().trim());
        tbMsgPort.setText(msgServiceInfo.getPort().trim());
        tbMsgUsername.setText(msgServiceInfo.getUserName().trim());
        tbMsgPassword.setText(msgServiceInfo.getPassword());

        String indexingServerHost = UserPreferences.getIndexingServerHost().trim();
        if (!indexingServerHost.isEmpty()) {
            tbSolrHostname.setText(indexingServerHost);
        }
        String indexingServerPort = UserPreferences.getIndexingServerPort().trim();
        if (portNumberIsValid(indexingServerPort)) {
            tbSolrPort.setText(indexingServerPort);
        }

        lbTestDatabase.setIcon(null);
        lbTestSolr.setIcon(null);
        lbTestMessageService.setIcon(null);

        bnTestDatabase.setEnabled(false);
        bnTestSolr.setEnabled(false);
        bnTestMessageService.setEnabled(false);

        cbEnableMultiUser.setSelected(UserPreferences.getIsMultiUserModeEnabled());
        this.valid(); // trigger validation to enable buttons based on current settings
    }

    /**
     * Tests whether or not values have been entered in all of the database
     * settings text fields.
     *
     * @return True or false.
     */
    private boolean databaseFieldsArePopulated() {
        return !tbDbHostname.getText().isEmpty()
                && !tbDbPort.getText().isEmpty()
                && !tbDbUsername.getText().isEmpty()
                && tbDbPassword.getPassword().length != 0;
    }

    /**
     * Tests whether or not values have been entered in all of the Solr settings
     * text fields.
     *
     * @return True or false.
     */
    private boolean solrFieldsArePopulated() {
        return !tbSolrHostname.getText().isEmpty()
                && !tbSolrPort.getText().isEmpty();
    }

    /**
     * Tests whether or not values have been entered in all of the message
     * service settings text fields.
     *
     * @return True or false.
     */
    private boolean messageServiceFieldsArePopulated() {
        return !tbMsgHost.getText().isEmpty()
                && !tbMsgPort.getText().isEmpty()
                && !tbMsgUsername.getText().isEmpty()
                && tbMsgPassword.getPassword().length != 0;
    }

    void store() {
        DbType dbType = DbType.SQLITE;

        if (cbEnableMultiUser.isSelected()) {
            dbType = DbType.POSTGRESQL;
        }

        UserPreferences.setIsMultiUserModeEnabled(cbEnableMultiUser.isSelected());

        CaseDbConnectionInfo info = new CaseDbConnectionInfo(
                tbDbHostname.getText().trim(),
                tbDbPort.getText().trim(),
                tbDbUsername.getText().trim(),
                new String(tbDbPassword.getPassword()),
                dbType);

        UserPreferences.setDatabaseConnectionInfo(info);

        MessageServiceConnectionInfo msgServiceInfo = new MessageServiceConnectionInfo(
                tbMsgUsername.getText().trim(),
                new String(tbMsgPassword.getPassword()),
                tbMsgHost.getText().trim(),
                tbMsgPort.getText().trim());
        UserPreferences.setMessageServiceConnectionInfo(msgServiceInfo);

        UserPreferences.setIndexingServerHost(tbSolrHostname.getText().trim());
        UserPreferences.setIndexingServerPort(Integer.parseInt(tbSolrPort.getText().trim()));

    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    boolean valid() {
        tbOops.setText("");

        if (cbEnableMultiUser.isSelected()) {
            return checkFieldsAndEnableButtons()
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
    boolean checkFieldsAndEnableButtons() {
        boolean result = true;

        boolean dbPopulated = databaseFieldsArePopulated();
        boolean solrPopulated = solrFieldsArePopulated();
        boolean messageServicePopulated = messageServiceFieldsArePopulated();

        // PostgreSQL Database
        bnTestDatabase.setEnabled(dbPopulated);

        // Solr Indexing
        bnTestSolr.setEnabled(solrPopulated);

        // ActiveMQ Messaging
        bnTestMessageService.setEnabled(messageServicePopulated);

        if (!dbPopulated || !solrPopulated || !messageServicePopulated) {
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
        if (portNumberIsValid(tbDbPort.getText())) {
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
        if (!portNumberIsValid(tbMsgPort.getText())) {
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
        if (!portNumberIsValid(tbSolrPort.getText())) {
            tbOops.setText(INVALID_INDEXING_SERVER_PORT_MSG);
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
    private javax.swing.JButton bnTestSolr;
    private javax.swing.JCheckBox cbEnableMultiUser;
    private javax.swing.JLabel lbDatabaseSettings;
    private javax.swing.JLabel lbMessageServiceSettings;
    private javax.swing.JLabel lbSolrSettings;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestMessageService;
    private javax.swing.JLabel lbTestSolr;
    private javax.swing.JPanel pnDatabaseSettings;
    private javax.swing.JPanel pnMessagingSettings;
    private javax.swing.JPanel pnOverallPanel;
    private javax.swing.JPanel pnSolrSettings;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JPasswordField tbDbPassword;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    private javax.swing.JTextField tbMsgHost;
    private javax.swing.JPasswordField tbMsgPassword;
    private javax.swing.JTextField tbMsgPort;
    private javax.swing.JTextField tbMsgUsername;
    private javax.swing.JTextField tbOops;
    private javax.swing.JTextField tbSolrHostname;
    private javax.swing.JTextField tbSolrPort;
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
}
