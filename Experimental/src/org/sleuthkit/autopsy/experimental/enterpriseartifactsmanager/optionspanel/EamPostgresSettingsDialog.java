/*
 * Enterprise Artifacts Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.optionspanel;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.Collection;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.PostgresEamDbSettings;

/**
 * Settings panel for the postgres-specific options
 */
public class EamPostgresSettingsDialog extends javax.swing.JDialog {

    private static final Logger LOGGER = Logger.getLogger(EamSqliteSettingsDialog.class.getName());
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    private final Collection<JTextField> textBoxes;
    private final TextBoxChangedListener textBoxChangedListener;

    private final PostgresEamDbSettings dbSettings;
    private Boolean hasChanged;

    /**
     * Creates new form EnterpriseArtifactsManagerPostgresSettingsDialog
     */
    @NbBundle.Messages({"EnterpriseArtifactsManagerPostgresSettingsDialog.postgresSettingsMessage.text=PostgreSQL Database Settings"})
    public EamPostgresSettingsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_postgresSettingsMessage_text(),
                true); // NON-NLS

        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();
        this.dbSettings = new PostgresEamDbSettings();
        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false)); // NON-NLS
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false)); // NON-NLS

        initComponents();
        customizeComponents();
        display();
    }

    /**
     * Let calling object determine if this dialog made any changes.
     *
     * @return true or false
     */
    public Boolean isChanged() {
        return hasChanged;
    }

    private void customizeComponents() {
        hasChanged = false;
        lbTestDatabaseWarning.setText("");
        setTextPrompts();
        setTextBoxStatusIcons();
        setTextBoxListeners();
        tbDbHostname.setText(dbSettings.getHost());
        tbDbPort.setText(Integer.toString(dbSettings.getPort()));
        tbDbName.setText(dbSettings.getDbName());
        tbDbUsername.setText(dbSettings.getUserName());
        tbDbPassword.setText(dbSettings.getPassword());

        enableTestDatabaseButton(false);
        enableSaveButton(false);
        this.valid();
    }

    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
    }

    /**
     * Add text prompts to all of the text fields.
     */
    @Messages({"EnterpriseArtifactsManagerPostgresSettingsDialog.textPrompt.hostnameOrIP=Hostname or IP Address",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.textPrompt.port=Port Number",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.textPrompt.dbName=Database Name",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.textPrompt.user=Database User",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.textPrompt.password=Database User's Password"})
    private void setTextPrompts() {
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_textPrompt_hostnameOrIP(), tbDbHostname));
        textPrompts.add(new TextPrompt(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_textPrompt_port(), tbDbPort));
        textPrompts.add(new TextPrompt(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_textPrompt_dbName(), tbDbName));
        textPrompts.add(new TextPrompt(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_textPrompt_user(), tbDbUsername));
        textPrompts.add(new TextPrompt(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_textPrompt_password(), tbDbPassword));
        configureTextPrompts(textPrompts);
    }

    /**
     * Set each textbox with a "statusIcon" property enabling the
     * DocumentListeners to know which icon to erase when changes are made
     */
    private void setTextBoxStatusIcons() {
        tbDbHostname.getDocument().putProperty("statusIcon", lbTestDatabase); // NON-NLS
        tbDbPort.getDocument().putProperty("statusIcon", lbTestDatabase); // NON-NLS
        tbDbName.getDocument().putProperty("statusIcon", lbTestDatabase); // NON-NLS
        tbDbUsername.getDocument().putProperty("statusIcon", lbTestDatabase); // NON-NLS
        tbDbPassword.getDocument().putProperty("statusIcon", lbTestDatabase); // NON-NLS
    }

    /**
     * Register for notifications when the text boxes get updated.
     */
    private void setTextBoxListeners() {
        textBoxes.add(tbDbHostname);
        textBoxes.add(tbDbPort);
        textBoxes.add(tbDbName);
        textBoxes.add(tbDbUsername);
        textBoxes.add(tbDbPassword);
        addDocumentListeners(textBoxes, textBoxChangedListener);
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
    private boolean databaseFieldsArePopulated() {
        return !tbDbHostname.getText().trim().isEmpty()
                && !tbDbPort.getText().trim().isEmpty()
                && !tbDbName.getText().trim().isEmpty()
                && !tbDbUsername.getText().trim().isEmpty()
                && !tbDbPassword.getText().trim().isEmpty();
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    @Messages({"EnterpriseArtifactsManagerPostgresSettingsDialog.validation.incompleteFields=Fill in all values"})
    private boolean checkFields() {
        boolean result = true;

        boolean dbPopulated = databaseFieldsArePopulated();

        if (!dbPopulated) {
            // We don't even have everything filled out
            result = false;
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_incompleteFields());
        }
        return result;
    }

    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    @Messages({"EnterpriseArtifactsManagerPostgresSettingsDialog.validation.invalidHost=Invalid database hostname.",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.validation.invalidPort=Invalid database port number.",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.validation.invalidDbName=Invalid database name.",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.validation.invalidDbUser=Invalid database username.",
        "EnterpriseArtifactsManagerPostgresSettingsDialog.validation.invalidDbPassword=Invalid database password.",})
    private boolean databaseSettingsAreValid() {

        try {
            dbSettings.setHost(tbDbHostname.getText().trim());
        } catch (EamDbException ex) {
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_invalidHost());
            return false;
        }

        try {
            dbSettings.setPort(Integer.valueOf(tbDbPort.getText().trim()));
        } catch (NumberFormatException | EamDbException ex) {
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_invalidPort());
            return false;
        }

        try {
            dbSettings.setDbName(tbDbName.getText().trim());
        } catch (EamDbException ex) {
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_invalidDbName());
            return false;
        }

        try {
            dbSettings.setUserName(tbDbUsername.getText().trim());
        } catch (EamDbException ex) {
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_invalidDbUser());
            return false;
        }

        try {
            dbSettings.setPassword(tbDbPassword.getText().trim());
        } catch (EamDbException ex) {
            lbTestDatabaseWarning.setText(Bundle.EnterpriseArtifactsManagerPostgresSettingsDialog_validation_invalidDbPassword());
            return false;
        }

        return true;
    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    public boolean valid() {
        lbTestDatabaseWarning.setText("");

        return checkFields()
                && enableTestDatabaseButton(databaseSettingsAreValid())
                && enableSaveButton(databaseSettingsAreValid());
    }

    /**
     * Enables the "Test Connection" button to test the database settings.
     *
     * @param enable
     *
     * @return True or False
     */
    private boolean enableTestDatabaseButton(Boolean enable) {
        bnTestConnection.setEnabled(enable);
        return enable;
    }

    /**
     * Enables the "Save" button to save the database settings.
     *
     * @param enable
     *
     * @return True or False
     */
    private boolean enableSaveButton(Boolean enable) {
        bnSave.setEnabled(enable);
        return enable;
    }

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    private class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon"); // NON-NLS
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon"); // NON-NLS
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            Object statusIcon = e.getDocument().getProperty("statusIcon"); // NON-NLS
            if (statusIcon != null) {
                ((javax.swing.JLabel) statusIcon).setIcon(null);
            }
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
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

        pnOuter = new javax.swing.JPanel();
        jScrollPane = new javax.swing.JScrollPane();
        pnContent = new javax.swing.JPanel();
        lbHostName = new javax.swing.JLabel();
        lbPort = new javax.swing.JLabel();
        lbUserName = new javax.swing.JLabel();
        lbUserPassword = new javax.swing.JLabel();
        lbDatabaseName = new javax.swing.JLabel();
        tbDbHostname = new javax.swing.JTextField();
        tbDbPort = new javax.swing.JTextField();
        tbDbName = new javax.swing.JTextField();
        tbDbUsername = new javax.swing.JTextField();
        tbDbPassword = new javax.swing.JTextField();
        bnTestConnection = new javax.swing.JButton();
        bnSave = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();
        lbTestDatabaseWarning = new javax.swing.JLabel();
        lbTestDatabase = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(lbHostName, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.lbHostName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbPort, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.lbPort.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbUserName, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.lbUserName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbUserPassword, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.lbUserPassword.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseName, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.lbDatabaseName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestConnection, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.bnTestConnection.text")); // NOI18N
        bnTestConnection.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestConnectionActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnSave, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.bnSave.text")); // NOI18N
        bnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnSaveActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(EamPostgresSettingsDialog.class, "EamPostgresSettingsDialog.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        lbTestDatabaseWarning.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lbTestDatabaseWarning.setForeground(new java.awt.Color(255, 0, 0));

        javax.swing.GroupLayout pnContentLayout = new javax.swing.GroupLayout(pnContent);
        pnContent.setLayout(pnContentLayout);
        pnContentLayout.setHorizontalGroup(
            pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnContentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnContentLayout.createSequentialGroup()
                        .addComponent(bnTestConnection)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 19, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(153, 153, 153)
                        .addComponent(bnSave)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(bnCancel))
                    .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(lbTestDatabaseWarning, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(javax.swing.GroupLayout.Alignment.LEADING, pnContentLayout.createSequentialGroup()
                            .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(lbHostName)
                                .addComponent(lbPort)
                                .addComponent(lbDatabaseName)
                                .addComponent(lbUserName)
                                .addComponent(lbUserPassword))
                            .addGap(18, 18, 18)
                            .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                .addComponent(tbDbPassword, javax.swing.GroupLayout.DEFAULT_SIZE, 322, Short.MAX_VALUE)
                                .addComponent(tbDbUsername)
                                .addComponent(tbDbName)
                                .addComponent(tbDbHostname)
                                .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, 67, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(0, 11, Short.MAX_VALUE))
        );
        pnContentLayout.setVerticalGroup(
            pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnContentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbHostName, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tbDbPort)
                    .addComponent(lbPort, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tbDbName)
                    .addComponent(lbDatabaseName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tbDbUsername)
                    .addComponent(lbUserName, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbUserPassword, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(37, 37, 37)
                .addComponent(lbTestDatabaseWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 21, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnTestConnection)
                    .addComponent(bnSave)
                    .addComponent(bnCancel)
                    .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jScrollPane.setViewportView(pnContent);

        javax.swing.GroupLayout pnOuterLayout = new javax.swing.GroupLayout(pnOuter);
        pnOuter.setLayout(pnOuterLayout);
        pnOuterLayout.setHorizontalGroup(
            pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 448, Short.MAX_VALUE)
            .addGroup(pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(pnOuterLayout.createSequentialGroup()
                    .addComponent(jScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 448, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );
        pnOuterLayout.setVerticalGroup(
            pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 249, Short.MAX_VALUE)
            .addGroup(pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(pnOuterLayout.createSequentialGroup()
                    .addComponent(jScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 249, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnOuter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnOuter, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void bnTestConnectionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestConnectionActionPerformed
        lbTestDatabase.setIcon(null);
        lbTestDatabase.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (dbSettings.testSettings()) {
            lbTestDatabase.setIcon(goodIcon);
        } else {
            lbTestDatabase.setIcon(badIcon);
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

    }//GEN-LAST:event_bnTestConnectionActionPerformed

    private void bnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnSaveActionPerformed
        hasChanged = true;
        dbSettings.setEnabled(true);
        dbSettings.saveSettings();
        dispose();
    }//GEN-LAST:event_bnSaveActionPerformed

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        dispose();
    }//GEN-LAST:event_bnCancelActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnSave;
    private javax.swing.JButton bnTestConnection;
    private javax.swing.JScrollPane jScrollPane;
    private javax.swing.JLabel lbDatabaseName;
    private javax.swing.JLabel lbHostName;
    private javax.swing.JLabel lbPort;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestDatabaseWarning;
    private javax.swing.JLabel lbUserName;
    private javax.swing.JLabel lbUserPassword;
    private javax.swing.JPanel pnContent;
    private javax.swing.JPanel pnOuter;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JTextField tbDbName;
    private javax.swing.JTextField tbDbPassword;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    // End of variables declaration//GEN-END:variables
}
