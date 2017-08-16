/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.configuration;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import java.awt.Cursor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.openide.util.ImageUtilities;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.StatusDatabaseLogger;

/**
 *
 */
public class NodeStatusLogPanel extends javax.swing.JPanel {

    private static final String HOST_NAME_OR_IP_PROMPT = NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbHostname.toolTipText");
    private static final String PORT_PROMPT = NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPort.toolTipText");
    private static final String USER_NAME_PROMPT = NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbUsername.toolTipText");
    private static final String PASSWORD_PROMPT = NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPassword.toolTipText");
    private static final String DATABASE_NAME_PROMPT = NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbName.toolTipText");
    
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    
    JDialog jDialog;
    private static final Logger logger = Logger.getLogger(NodeStatusLogPanel.class.getName());
    
    /**
     * Creates new form DatabaseLogPanell
     */
    public NodeStatusLogPanel(JDialog jDialog) {
        initComponents();
        load();
        validateSettings();
        this.jDialog = jDialog;
        
        tbDbHostname.getDocument().addDocumentListener(new MyDocumentListener());
        tbDbPort.getDocument().addDocumentListener(new MyDocumentListener());
        tbDbPassword.getDocument().addDocumentListener(new MyDocumentListener());
        tbDbUsername.getDocument().addDocumentListener(new MyDocumentListener());
        tbDbName.getDocument().addDocumentListener(new MyDocumentListener());
        
        /**
         * Add text prompts to all of the text fields.
         */
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(HOST_NAME_OR_IP_PROMPT, tbDbHostname));
        textPrompts.add(new TextPrompt(PORT_PROMPT, tbDbPort));
        textPrompts.add(new TextPrompt(USER_NAME_PROMPT, tbDbUsername));
        textPrompts.add(new TextPrompt(PASSWORD_PROMPT, tbDbPassword));
        textPrompts.add(new TextPrompt(DATABASE_NAME_PROMPT, tbDbName));
        configureTextPrompts(textPrompts);
        
        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false));
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false));
    }
    
    final void validateSettings(){
        if(valid()){
            bnOk.setEnabled(true);
            if(cbEnableLogging.isSelected()){
                bnTestDatabase.setEnabled(true);
            } else {
                bnTestDatabase.setEnabled(false);
            }
        } else {
            bnOk.setEnabled(false);
            bnTestDatabase.setEnabled(false);
        }
    }
    
    private boolean valid(){
        if(cbEnableLogging.isSelected()){
            if(tbDbHostname.getText().isEmpty()
                || tbDbPort.getText().isEmpty()
                || tbDbUsername.getText().isEmpty()
                || (tbDbPassword.getPassword().length == 0)
                || tbDbName.getText().isEmpty()){
                    return false;
            }
        }
        return true;
    }
    
    private void enableFields(boolean enable){
        tbDbHostname.setEnabled(enable);
        tbDbPort.setEnabled(enable);
        tbDbUsername.setEnabled(enable);
        tbDbPassword.setEnabled(enable);
        tbDbName.setEnabled(enable);
    }
    
    final void load(){
        
        try{
            cbEnableLogging.setSelected(AutoIngestUserPreferences.getStatusDatabaseLoggingEnabled());
            tbDbHostname.setText(AutoIngestUserPreferences.getLoggingDatabaseHostnameOrIP());
            tbDbPort.setText(AutoIngestUserPreferences.getLoggingPort());
            tbDbUsername.setText(AutoIngestUserPreferences.getLoggingUsername());
            tbDbPassword.setText(AutoIngestUserPreferences.getLoggingPassword());
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing status database connection info", ex); //NON-NLS
        }
        tbDbName.setText(AutoIngestUserPreferences.getLoggingDatabaseName());
    }
    
    void store(){
        AutoIngestUserPreferences.setStatusDatabaseLoggingEnabled(cbEnableLogging.isSelected());
        if(cbEnableLogging.isSelected()){
            try{
                AutoIngestUserPreferences.setLoggingDatabaseHostnameOrIP(tbDbHostname.getText().trim());
                AutoIngestUserPreferences.setLoggingPort(tbDbPort.getText().trim());
                AutoIngestUserPreferences.setLoggingUsername(tbDbUsername.getText().trim());
                AutoIngestUserPreferences.setLoggingPassword(new String(tbDbPassword.getPassword()));
                AutoIngestUserPreferences.setLoggingDatabaseName(tbDbName.getText().trim());
            }  catch (UserPreferencesException ex) {
                logger.log(Level.SEVERE, "Error saving database connection info", ex); //NON-NLS
            }
           
        }
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
    
    private void testDatabase(){
        String host = tbDbHostname.getText();
        String port = tbDbPort.getText();
        String username = tbDbUsername.getText();
        String password = new String(tbDbPassword.getPassword());
        String dbName = tbDbName.getText();
        
        lbTestDatabase.setIcon(null);
        lbTestDbWarning.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        // First test whether we can connect to the database
        try{
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ex){
            // Continue on even if this fails
        }
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + dbName,
                username, password);
                Statement statement = connection.createStatement();) {
            // Now make sure the database is set up for logging
            try{
                StatusDatabaseLogger.logToStatusDatabase(statement, "Testing configuration", false);
                lbTestDatabase.setIcon(goodIcon);
                lbTestDbWarning.setText("");
            } catch (SQLException ex){
                lbTestDatabase.setIcon(badIcon);
                lbTestDbWarning.setText("Database is not correctly initialized - " + ex.getMessage());
            }    
        } catch (SQLException ex) {
            lbTestDatabase.setIcon(badIcon);
            lbTestDbWarning.setText(ex.getMessage());
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

        pnDatabaseSettings = new javax.swing.JPanel();
        tbDbHostname = new javax.swing.JTextField();
        tbDbPort = new javax.swing.JTextField();
        tbDbUsername = new javax.swing.JTextField();
        tbDbPassword = new javax.swing.JPasswordField();
        lbDatabaseSettings = new javax.swing.JLabel();
        bnTestDatabase = new javax.swing.JButton();
        lbTestDatabase = new javax.swing.JLabel();
        lbTestDbWarning = new javax.swing.JLabel();
        tbDbName = new javax.swing.JTextField();
        cbEnableLogging = new javax.swing.JCheckBox();
        bnOk = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();

        pnDatabaseSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        tbDbHostname.setFont(tbDbHostname.getFont().deriveFont(tbDbHostname.getFont().getStyle() & ~java.awt.Font.BOLD, 12));
        tbDbHostname.setText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbHostname.text")); // NOI18N
        tbDbHostname.setToolTipText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbHostname.toolTipText")); // NOI18N

        tbDbPort.setFont(tbDbPort.getFont().deriveFont(tbDbPort.getFont().getStyle() & ~java.awt.Font.BOLD, 12));
        tbDbPort.setText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPort.text")); // NOI18N
        tbDbPort.setToolTipText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPort.toolTipText")); // NOI18N

        tbDbUsername.setFont(tbDbUsername.getFont().deriveFont(tbDbUsername.getFont().getStyle() & ~java.awt.Font.BOLD, 12));
        tbDbUsername.setText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbUsername.text")); // NOI18N
        tbDbUsername.setToolTipText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbUsername.toolTipText")); // NOI18N

        tbDbPassword.setFont(tbDbPassword.getFont().deriveFont(tbDbPassword.getFont().getStyle() & ~java.awt.Font.BOLD, 12));
        tbDbPassword.setText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPassword.text")); // NOI18N
        tbDbPassword.setToolTipText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbPassword.toolTipText")); // NOI18N

        lbDatabaseSettings.setFont(lbDatabaseSettings.getFont().deriveFont(lbDatabaseSettings.getFont().getStyle() & ~java.awt.Font.BOLD, 12));
        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseSettings, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.lbDatabaseSettings.text")); // NOI18N
        lbDatabaseSettings.setVerticalAlignment(javax.swing.SwingConstants.TOP);

        bnTestDatabase.setFont(bnTestDatabase.getFont().deriveFont(bnTestDatabase.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(bnTestDatabase, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.bnTestDatabase.text")); // NOI18N
        bnTestDatabase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestDatabaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestDatabase, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.lbTestDatabase.text")); // NOI18N
        lbTestDatabase.setAutoscrolls(true);

        lbTestDbWarning.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestDbWarning, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.lbTestDbWarning.text")); // NOI18N

        tbDbName.setText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbName.text")); // NOI18N
        tbDbName.setToolTipText(org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.tbDbName.toolTipText_1")); // NOI18N

        javax.swing.GroupLayout pnDatabaseSettingsLayout = new javax.swing.GroupLayout(pnDatabaseSettings);
        pnDatabaseSettings.setLayout(pnDatabaseSettingsLayout);
        pnDatabaseSettingsLayout.setHorizontalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnDatabaseSettingsLayout.createSequentialGroup()
                        .addComponent(lbDatabaseSettings)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 344, Short.MAX_VALUE)
                        .addComponent(bnTestDatabase)
                        .addGap(18, 18, 18)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(tbDbHostname)
                    .addComponent(tbDbPort)
                    .addComponent(tbDbUsername)
                    .addComponent(tbDbPassword)
                    .addComponent(tbDbName)
                    .addComponent(lbTestDbWarning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        pnDatabaseSettingsLayout.setVerticalGroup(
            pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnDatabaseSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bnTestDatabase)
                    .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabaseSettings))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbDbName, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(lbTestDbWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(24, 24, 24))
        );

        cbEnableLogging.setFont(cbEnableLogging.getFont().deriveFont(cbEnableLogging.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(cbEnableLogging, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.cbEnableLogging.text")); // NOI18N
        cbEnableLogging.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableLoggingItemStateChanged(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOk, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.bnOk.text")); // NOI18N
        bnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOkActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(NodeStatusLogPanel.class, "NodeStatusLogPanel.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGap(232, 232, 232)
                .addComponent(bnOk)
                .addGap(18, 18, 18)
                .addComponent(bnCancel)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(cbEnableLogging)
                    .addContainerGap(421, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(79, 79, 79)
                .addComponent(pnDatabaseSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnOk)
                    .addComponent(bnCancel))
                .addContainerGap(69, Short.MAX_VALUE))
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addGap(48, 48, 48)
                    .addComponent(cbEnableLogging)
                    .addContainerGap(324, Short.MAX_VALUE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void cbEnableLoggingItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbEnableLoggingItemStateChanged
        enableFields(cbEnableLogging.isSelected());
        validateSettings();
    }//GEN-LAST:event_cbEnableLoggingItemStateChanged

    private void bnTestDatabaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestDatabaseActionPerformed
        testDatabase();
    }//GEN-LAST:event_bnTestDatabaseActionPerformed

    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        store();
        jDialog.dispose();
    }//GEN-LAST:event_bnOkActionPerformed

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        jDialog.dispose();
    }//GEN-LAST:event_bnCancelActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnOk;
    private javax.swing.JButton bnTestDatabase;
    private javax.swing.JCheckBox cbEnableLogging;
    private javax.swing.JLabel lbDatabaseSettings;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestDbWarning;
    private javax.swing.JPanel pnDatabaseSettings;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JTextField tbDbName;
    private javax.swing.JPasswordField tbDbPassword;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    // End of variables declaration//GEN-END:variables

    private class MyDocumentListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            validateSettings();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            validateSettings();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            validateSettings();
        }
    };
}
