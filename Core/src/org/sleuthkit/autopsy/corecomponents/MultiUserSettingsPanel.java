/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.events.MessageServiceConnectionInfo;
import org.sleuthkit.autopsy.coreutils.Logger;

public final class MultiUserSettingsPanel extends javax.swing.JPanel {

    private static final String HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbHostnameOrIp.toolTipText");
    private static final String PORT_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPortNumber.toolTipText");
    private static final String USER_NAME_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbUsername.toolTipText");
    private static final String PASSWORD_PROMPT = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPassword.toolTipText");
    private static final String INCOMPLETE_SETTINGS_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.incomplete");
    private static final String INVALID_DB_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidDatabasePort");
    private static final String INVALID_MESSAGE_SERVICE_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidMessageServicePort");
    private static final String INVALID_INDEXING_SERVER_PORT_MSG = NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.validationErrMsg.invalidIndexingServerPort");
    private final MultiUserSettingsPanelController controller;
    private final Collection<JTextField> textBoxes = new ArrayList<>();
    private final TextBoxChangedListener textBoxChangedListener;
    private static final Logger logger = Logger.getLogger(MultiUserSettingsPanel.class.getName());

    /**
     * Creates new form AutopsyMultiUserSettingsPanel
     */
    public MultiUserSettingsPanel(MultiUserSettingsPanelController theController) {
        initComponents();
        controller = theController;

        /**
         * Add text prompts to all of the text fields.
         */
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbHostnameOrIp));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbPortNumber));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbPassword));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, msgHostTextField));
        textPrompts.add(new TextPrompt(PORT_PROMPT, msgPortTextField));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, msgUserNameTextField));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, msgPasswordField));
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbIndexingServerHost));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbIndexingServerPort));
        configureTextPrompts(textPrompts);

        /// Register for notifications when the text boxes get updated.
        textBoxChangedListener = new TextBoxChangedListener();
        textBoxes.add(tbHostnameOrIp);
        textBoxes.add(tbPortNumber);
        textBoxes.add(tbUsername);
        textBoxes.add(tbPassword);
        textBoxes.add(msgHostTextField);
        textBoxes.add(msgPortTextField);
        textBoxes.add(msgUserNameTextField);
        textBoxes.add(msgPasswordField);
        textBoxes.add(tbIndexingServerHost);
        textBoxes.add(tbIndexingServerPort);
        addDocumentListeners(textBoxes, textBoxChangedListener);

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
        tbHostnameOrIp = new javax.swing.JTextField();
        tbPortNumber = new javax.swing.JTextField();
        tbUsername = new javax.swing.JTextField();
        tbPassword = new javax.swing.JPasswordField();
        lbDatabaseSettings = new javax.swing.JLabel();
        pnSolrSettings = new javax.swing.JPanel();
        lbSolrSettings = new javax.swing.JLabel();
        tbIndexingServerHost = new javax.swing.JTextField();
        tbIndexingServerPort = new javax.swing.JTextField();
        pnMessagingSettings = new javax.swing.JPanel();
        lbMessagingSettings = new javax.swing.JLabel();
        msgHostTextField = new javax.swing.JTextField();
        msgUserNameTextField = new javax.swing.JTextField();
        msgPortTextField = new javax.swing.JTextField();
        msgPasswordField = new javax.swing.JPasswordField();
        cbEnableMultiUser = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();

        pnDatabaseSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbHostnameOrIp.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbHostnameOrIp.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbHostnameOrIp.text")); // NOI18N
        tbHostnameOrIp.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbHostnameOrIp.toolTipText")); // NOI18N

        tbPortNumber.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbPortNumber.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPortNumber.text")); // NOI18N
        tbPortNumber.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPortNumber.toolTipText")); // NOI18N

        tbUsername.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbUsername.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbUsername.text")); // NOI18N
        tbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbUsername.toolTipText")); // NOI18N

        tbPassword.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbPassword.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPassword.text")); // NOI18N
        tbPassword.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbPassword.toolTipText")); // NOI18N

        lbDatabaseSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbDatabaseSettings.text")); // NOI18N
        lbDatabaseSettings.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        javax.swing.GroupLayout pnDatabaseSettingsLayout = new javax.swing.GroupLayout(pnDatabaseSettings);
        pnDatabaseSettings.setLayout(pnDatabaseSettingsLayout);
        pnDatabaseSettingsLayout.setHorizontalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbHostnameOrIp)
                    .addComponent(tbPortNumber)
                    .addComponent(tbUsername)
                    .addComponent(tbPassword)
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbDatabaseSettings)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnDatabaseSettingsLayout.setVerticalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbDatabaseSettings)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 11, Short.MAX_VALUE)
                .addComponent(tbHostnameOrIp, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbPortNumber, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pnSolrSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbSolrSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbSolrSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbSolrSettings.text")); // NOI18N

        tbIndexingServerHost.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbIndexingServerHost.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbIndexingServerHost.toolTipText")); // NOI18N

        tbIndexingServerPort.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        tbIndexingServerPort.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.tbIndexingServerPort.toolTipText")); // NOI18N

        javax.swing.GroupLayout pnSolrSettingsLayout = new javax.swing.GroupLayout(pnSolrSettings);
        pnSolrSettings.setLayout(pnSolrSettingsLayout);
        pnSolrSettingsLayout.setHorizontalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                        .addComponent(lbSolrSettings)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(tbIndexingServerHost, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbIndexingServerPort, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        pnSolrSettingsLayout.setVerticalGroup(
            pnSolrSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSolrSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbSolrSettings)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(tbIndexingServerHost, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(tbIndexingServerPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(45, 45, 45))
        );

        pnMessagingSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        lbMessagingSettings.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbMessagingSettings, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.lbMessagingSettings.text")); // NOI18N

        msgHostTextField.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        msgHostTextField.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgHostTextField.text")); // NOI18N
        msgHostTextField.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgHostTextField.toolTipText")); // NOI18N

        msgUserNameTextField.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        msgUserNameTextField.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgUserNameTextField.text")); // NOI18N
        msgUserNameTextField.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgUserNameTextField.toolTipText")); // NOI18N

        msgPortTextField.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        msgPortTextField.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgPortTextField.text")); // NOI18N
        msgPortTextField.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgPortTextField.toolTipText")); // NOI18N

        msgPasswordField.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        msgPasswordField.setText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgPasswordField.text")); // NOI18N
        msgPasswordField.setToolTipText(org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.msgPasswordField.toolTipText")); // NOI18N

        javax.swing.GroupLayout pnMessagingSettingsLayout = new javax.swing.GroupLayout(pnMessagingSettings);
        pnMessagingSettings.setLayout(pnMessagingSettingsLayout);
        pnMessagingSettingsLayout.setHorizontalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                        .addComponent(lbMessagingSettings)
                        .addGap(0, 334, Short.MAX_VALUE))
                    .addComponent(msgHostTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(msgUserNameTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(msgPortTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(msgPasswordField))
                .addContainerGap())
        );
        pnMessagingSettingsLayout.setVerticalGroup(
            pnMessagingSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnMessagingSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbMessagingSettings)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(msgHostTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(msgPortTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(msgUserNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(msgPasswordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        org.openide.awt.Mnemonics.setLocalizedText(cbEnableMultiUser, org.openide.util.NbBundle.getMessage(MultiUserSettingsPanel.class, "MultiUserSettingsPanel.cbEnableMultiUser.text")); // NOI18N
        cbEnableMultiUser.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableMultiUserItemStateChanged(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setBackground(new java.awt.Color(240, 240, 240));
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
        }
        enableMultiUserComponents(textBoxes, cbEnableMultiUser.isSelected());
        controller.changed();
    }//GEN-LAST:event_cbEnableMultiUserItemStateChanged

    void load() {
        CaseDbConnectionInfo dbInfo = UserPreferences.getDatabaseConnectionInfo();
        tbHostnameOrIp.setText(dbInfo.getHost().trim());
        tbPortNumber.setText(dbInfo.getPort().trim());
        tbUsername.setText(dbInfo.getUserName().trim());
        tbPassword.setText(dbInfo.getPassword());

        MessageServiceConnectionInfo msgServiceInfo = UserPreferences.getMessageServiceConnectionInfo();
        msgHostTextField.setText(msgServiceInfo.getHost().trim());
        msgPortTextField.setText(msgServiceInfo.getPort().trim());
        msgUserNameTextField.setText(msgServiceInfo.getUserName().trim());
        msgPasswordField.setText(msgServiceInfo.getPassword());

        String indexingServerHost = UserPreferences.getIndexingServerHost().trim();
        if (!indexingServerHost.isEmpty()) {
            tbIndexingServerHost.setText(indexingServerHost);
        }
        String indexingServerPort = UserPreferences.getIndexingServerPort().trim();
        if (portNumberIsValid(indexingServerPort)) {
            tbIndexingServerPort.setText(indexingServerPort);
        }

        cbEnableMultiUser.setSelected(UserPreferences.getIsMultiUserModeEnabled());
    }

    /**
     * Tests whether or not values have been entered in all of the message
     * service settings text fields.
     *
     * @return True or false.
     */
    private boolean messageServiceFieldsArePopulated() {
        return !msgHostTextField.getText().isEmpty()
                && !msgPortTextField.getText().isEmpty()
                && !msgUserNameTextField.getText().isEmpty()
                && msgPasswordField.getPassword().length != 0;
    }

    void store() {

        DbType dbType = DbType.SQLITE;

        if (cbEnableMultiUser.isSelected()) {
            dbType = DbType.POSTGRESQL;
        }

        UserPreferences.setIsMultiUserModeEnabled(cbEnableMultiUser.isSelected());

        CaseDbConnectionInfo info = new CaseDbConnectionInfo(
                tbHostnameOrIp.getText().trim(),
                tbPortNumber.getText().trim(),
                tbUsername.getText().trim(),
                new String(tbPassword.getPassword()),
                dbType);

        UserPreferences.setDatabaseConnectionInfo(info);

        MessageServiceConnectionInfo msgServiceInfo = new MessageServiceConnectionInfo(
                msgUserNameTextField.getText().trim(),
                new String(msgPasswordField.getPassword()),
                msgHostTextField.getText().trim(),
                msgPortTextField.getText().trim());
        UserPreferences.setMessageServiceConnectionInfo(msgServiceInfo);

        UserPreferences.setIndexingServerHost(tbIndexingServerHost.getText().trim());
        UserPreferences.setIndexingServerPort(Integer.parseInt(tbIndexingServerPort.getText().trim()));

    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    boolean valid() {
        tbOops.setText("");
        if (cbEnableMultiUser.isSelected()) {
            return settingsAreComplete() && databaseSettingsAreValid() && indexingServerSettingsAreValid() && messageServiceSettingsAreValid();
        } else {
            return true;
        }
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    boolean settingsAreComplete() {
        boolean result = true;
        if (tbHostnameOrIp.getText().isEmpty()
                || tbPortNumber.getText().isEmpty()
                || tbUsername.getText().isEmpty()
                || tbPassword.getPassword().length == 0
                || !messageServiceFieldsArePopulated()) {
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
        if (portNumberIsValid(tbPortNumber.getText())) {
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
        if (!portNumberIsValid(msgPortTextField.getText())) {
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
        if (!portNumberIsValid(tbIndexingServerPort.getText())) {
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
    private javax.swing.JCheckBox cbEnableMultiUser;
    private javax.swing.JLabel lbDatabaseSettings;
    private javax.swing.JLabel lbMessagingSettings;
    private javax.swing.JLabel lbSolrSettings;
    private javax.swing.JTextField msgHostTextField;
    private javax.swing.JPasswordField msgPasswordField;
    private javax.swing.JTextField msgPortTextField;
    private javax.swing.JTextField msgUserNameTextField;
    private javax.swing.JPanel pnDatabaseSettings;
    private javax.swing.JPanel pnMessagingSettings;
    private javax.swing.JPanel pnOverallPanel;
    private javax.swing.JPanel pnSolrSettings;
    private javax.swing.JTextField tbHostnameOrIp;
    private javax.swing.JTextField tbIndexingServerHost;
    private javax.swing.JTextField tbIndexingServerPort;
    private javax.swing.JTextField tbOops;
    private javax.swing.JPasswordField tbPassword;
    private javax.swing.JTextField tbPortNumber;
    private javax.swing.JTextField tbUsername;
    // End of variables declaration//GEN-END:variables

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            controller.changed();
        }

        @Override

        public void removeUpdate(DocumentEvent e) {
            controller.changed();
        }
    }
}
