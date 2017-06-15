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

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.SqliteEamDbSettings;

/**
 * Settings panel for the sqlite-specific options
 */
public class EamSqliteSettingsDialog extends javax.swing.JDialog {

    private static final Logger LOGGER = Logger.getLogger(EamSqliteSettingsDialog.class.getName());
    private final ImageIcon goodIcon;
    private final ImageIcon badIcon;
    private final TextBoxChangedListener textBoxChangedListener;

    private final SqliteEamDbSettings dbSettings;
    private Boolean hasChanged;

    /**
     * Creates new form EnterpriseArtifactsManagerSQLiteSettingsDialog
     */
    @Messages({"EnterpriseArtifactsManagerSQLiteSettingsDialog.sqliteSettingsMessage.text=SQLite Database Settings"})
    public EamSqliteSettingsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.EnterpriseArtifactsManagerSQLiteSettingsDialog_sqliteSettingsMessage_text(),
                true); // NON-NLS

        this.dbSettings = new SqliteEamDbSettings();
        goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false)); // NON-NLS
        badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false)); // NON-NLS
        textBoxChangedListener = new TextBoxChangedListener();

        initComponents();
        customizeComponents();
        valid();
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
        customizeFileChooser();
        tfDatabasePath.setText(dbSettings.getFileNameWithPath());
        lbTestDatabaseWarning.setText("");
        hasChanged = false;
        tfDatabasePath.getDocument().addDocumentListener(textBoxChangedListener);
        bnOk.setEnabled(false);
        bnTestDatabase.setEnabled(false);
    }

    @Messages({"EnterpriseArtifactsManagerSQLiteSettingsDialog.fileNameExtFilter.text=SQLite Database File"})
    private void customizeFileChooser() {
        fcDatabasePath.setDragEnabled(false);
        fcDatabasePath.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[]{"db"}; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(Bundle.EnterpriseArtifactsManagerSQLiteSettingsDialog_fileNameExtFilter_text(), EXTENSION); // NON-NLS
        fcDatabasePath.setFileFilter(filter);
        fcDatabasePath.setMultiSelectionEnabled(false);
    }

    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
    }

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    private class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            valid();
        }
    }

    private boolean valid() {
        boolean result = false;
        if (tfDatabasePath.getText().trim().isEmpty()) {
            bnOk.setEnabled(false);
            bnTestDatabase.setEnabled(false);
        } else {
            storeDbNameAndDirectory();
            bnOk.setEnabled(true);
            bnTestDatabase.setEnabled(true);
            result = true;
        }
        return result;
    }

    /**
     * Get the db file name and directory path from the file chooser and store
     * in dbSettings.
     */
    @Messages({"EnterpriseArtifactsManagerSQLiteSettingsDialog.storeDbNameAndDirectory.failedToSetDbNamePathMsg=Database filename or directory path is missing. Try again."})
    private void storeDbNameAndDirectory() {
        File databasePath = new File(tfDatabasePath.getText());
        try {
            dbSettings.setDbName(databasePath.getName());
            dbSettings.setDbDirectory(databasePath.getParent());
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to set database name or directory path.", ex); // NON-NLS
            JOptionPane.showMessageDialog(this, Bundle.EnterpriseArtifactsManagerSQLiteSettingsDialog_storeDbNameAndDirectory_failedToSetDbNamePathMsg());
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

        fcDatabasePath = new javax.swing.JFileChooser();
        pnOuter = new javax.swing.JPanel();
        jScrollPane = new javax.swing.JScrollPane();
        pnContent = new javax.swing.JPanel();
        lbDatabasePath = new javax.swing.JLabel();
        tfDatabasePath = new javax.swing.JTextField();
        bnDatabasePathFileOpen = new javax.swing.JButton();
        lbTestDatabaseWarning = new javax.swing.JLabel();
        bnTestDatabase = new javax.swing.JButton();
        lbTestDatabase = new javax.swing.JLabel();
        bnCancel = new javax.swing.JButton();
        bnOk = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabasePath, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.lbDatabasePath.text")); // NOI18N

        tfDatabasePath.setText(org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.tfDatabasePath.text")); // NOI18N
        tfDatabasePath.setToolTipText(org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.tfDatabasePath.toolTipText")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnDatabasePathFileOpen, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.bnDatabasePathFileOpen.text")); // NOI18N
        bnDatabasePathFileOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDatabasePathFileOpenActionPerformed(evt);
            }
        });

        lbTestDatabaseWarning.setForeground(new java.awt.Color(255, 51, 51));
        org.openide.awt.Mnemonics.setLocalizedText(lbTestDatabaseWarning, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.lbTestDatabaseWarning.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnTestDatabase, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.bnTestDatabase.text")); // NOI18N
        bnTestDatabase.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestDatabaseActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbTestDatabase, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.lbTestDatabase.text")); // NOI18N
        lbTestDatabase.setName(""); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.bnCancel.text")); // NOI18N
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOk, org.openide.util.NbBundle.getMessage(EamSqliteSettingsDialog.class, "EamSqliteSettingsDialog.bnOk.text")); // NOI18N
        bnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOkActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnContentLayout = new javax.swing.GroupLayout(pnContent);
        pnContent.setLayout(pnContentLayout);
        pnContentLayout.setHorizontalGroup(
            pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnContentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnContentLayout.createSequentialGroup()
                        .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(lbTestDatabaseWarning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(pnContentLayout.createSequentialGroup()
                                .addComponent(lbDatabasePath)
                                .addGap(18, 18, 18)
                                .addComponent(tfDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, 343, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 57, Short.MAX_VALUE)
                        .addComponent(bnDatabasePathFileOpen)
                        .addGap(24, 24, 24))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnContentLayout.createSequentialGroup()
                        .addComponent(bnTestDatabase)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(bnOk)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(bnCancel)
                        .addContainerGap())))
        );
        pnContentLayout.setVerticalGroup(
            pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnContentLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tfDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bnDatabasePathFileOpen))
                .addGap(18, 18, 18)
                .addComponent(lbTestDatabaseWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnContentLayout.createSequentialGroup()
                        .addGap(19, 19, 19)
                        .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnCancel)
                            .addComponent(bnOk)))
                    .addGroup(pnContentLayout.createSequentialGroup()
                        .addGap(18, 18, 18)
                        .addGroup(pnContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbTestDatabase, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnTestDatabase))))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jScrollPane.setViewportView(pnContent);

        javax.swing.GroupLayout pnOuterLayout = new javax.swing.GroupLayout(pnOuter);
        pnOuter.setLayout(pnOuterLayout);
        pnOuterLayout.setHorizontalGroup(
            pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane)
        );
        pnOuterLayout.setVerticalGroup(
            pnOuterLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 603, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addComponent(pnOuter, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 129, Short.MAX_VALUE)
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addComponent(pnOuter, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGap(0, 0, Short.MAX_VALUE)))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    @Messages({"EnterpriseArtifactsManagerSQLiteSettingsDialog.chooserPath.failedToGetDbPathMsg=Selected database path is invalid. Try again."})
    private void bnDatabasePathFileOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDatabasePathFileOpenActionPerformed
        fcDatabasePath.setCurrentDirectory(new File(dbSettings.getDbDirectory()));
        fcDatabasePath.setSelectedFile(new File(dbSettings.getFileNameWithPath()));
        if (fcDatabasePath.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File databaseFile = fcDatabasePath.getSelectedFile();
            try {
                tfDatabasePath.setText(databaseFile.getCanonicalPath());
                valid();
                // TODO: create the db/schema if it doesn't exist.
                // TODO: set variable noting that we created a new db, so it can be removed if Cancel button is clicked.

            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get path of selected database file", ex); // NON-NLS
                JOptionPane.showMessageDialog(this, Bundle.EnterpriseArtifactsManagerSQLiteSettingsDialog_chooserPath_failedToGetDbPathMsg());
            }
        }
    }//GEN-LAST:event_bnDatabasePathFileOpenActionPerformed

    private void bnTestDatabaseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestDatabaseActionPerformed
        lbTestDatabase.setIcon(null);
        lbTestDatabaseWarning.setText("");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        if (dbSettings.testSettings()) {
            lbTestDatabase.setIcon(goodIcon);
        } else {
            lbTestDatabase.setIcon(badIcon);
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }//GEN-LAST:event_bnTestDatabaseActionPerformed

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        // TODO: if a new db was created, then delete it. update settings to disable this platform
        dispose();
    }//GEN-LAST:event_bnCancelActionPerformed

    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        hasChanged = true;
        dbSettings.setEnabled(true);
        dbSettings.saveSettings();
        dispose();
    }//GEN-LAST:event_bnOkActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnDatabasePathFileOpen;
    private javax.swing.JButton bnOk;
    private javax.swing.JButton bnTestDatabase;
    private javax.swing.JFileChooser fcDatabasePath;
    private javax.swing.JScrollPane jScrollPane;
    private javax.swing.JLabel lbDatabasePath;
    private javax.swing.JLabel lbTestDatabase;
    private javax.swing.JLabel lbTestDatabaseWarning;
    private javax.swing.JPanel pnContent;
    private javax.swing.JPanel pnOuter;
    private javax.swing.JTextField tfDatabasePath;
    // End of variables declaration//GEN-END:variables
}
