/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.Color;
import java.awt.Cursor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.DatabaseTestResult;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteCentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.RdbmsCentralRepoFactory;

/**
 * Configuration dialog for Central Repository database settings.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class EamDbSettingsDialog extends JDialog {

    private static final Logger logger = Logger.getLogger(EamDbSettingsDialog.class.getName());
    
    private static final long serialVersionUID = 1L;
    private final Collection<JTextField> textBoxes;
    private final TextBoxChangedListener textBoxChangedListener;
    private final CentralRepoDbManager manager = new CentralRepoDbManager();


    /**
     * Creates new form EamDbSettingsDialog
     */
    @Messages({"EamDbSettingsDialog.title.text=Central Repository Database Configuration",
        "EamDbSettingsDialog.lbSingleUserSqLite.text=SQLite should only be used by one examiner at a time.",
        "EamDbSettingsDialog.lbDatabaseType.text=Database Type :",
        "EamDbSettingsDialog.fcDatabasePath.title=Select location for central_repository.db"})
    public EamDbSettingsDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.EamDbSettingsDialog_title_text(),
                true);

        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();

        initComponents();
        fcDatabasePath.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fcDatabasePath.setAcceptAllFileFilterUsed(false);
        fcDatabasePath.setDialogTitle(Bundle.EamDbSettingsDialog_fcDatabasePath_title());
        fcDatabasePath.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    return true;
                }
                return  pathname.getName().equalsIgnoreCase(SqliteCentralRepoSettings.DEFAULT_DBNAME);
            }

            @Override
            public String getDescription() {
                return "Directories and Central Repository databases";
            }
        });
        cbDatabaseType.setSelectedItem(manager.getSelectedPlatform());
        customizeComponents();
        valid();
        display();

    }
    
    
    
     /**
     * prompts user based on testing status (i.e. failure to connect, invalid schema, db does not exist, etc.)
     * @return whether or not the ultimate status after prompts is okay to continue
     */
    @NbBundle.Messages({"EamDbSettingsDialog.okButton.corruptDatabaseExists.title=Error Loading Database",
        "EamDbSettingsDialog.okButton.corruptDatabaseExists.message=Database exists but is not the right format. Manually delete it or choose a different path (if applicable).",
        "EamDbSettingsDialog.okButton.createDbDialog.title=Database Does Not Exist",
        "EamDbSettingsDialog.okButton.createDbDialog.message=Database does not exist, would you like to create it?",
        "EamDbSettingsDialog.okButton.databaseConnectionFailed.title=Database Connection Failed",
        "EamDbSettingsDialog.okButton.databaseConnectionFailed.message=Unable to connect to database please check your settings and try again.",
        "EamDbSettingsDialog.okButton.createSQLiteDbError.message=Unable to create SQLite Database, please ensure location exists and you have write permissions and try again.",
        "EamDbSettingsDialog.okButton.createPostgresDbError.message=Unable to create Postgres Database, please ensure address, port, and login credentials are correct for Postgres server and try again.",
        "EamDbSettingsDialog.okButton.createDbError.title=Unable to Create Database"})
    private boolean promptTestStatusWarnings() {
        if (manager.getStatus() == DatabaseTestResult.CONNECTION_FAILED) {
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_databaseConnectionFailed_message(),
                    Bundle.EamDbSettingsDialog_okButton_databaseConnectionFailed_title(),
                    JOptionPane.WARNING_MESSAGE);
        } else if (manager.getStatus() == DatabaseTestResult.SCHEMA_INVALID) {
            // There's an existing database or file, but it's not in our format. 
            JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_corruptDatabaseExists_message(),
                    Bundle.EamDbSettingsDialog_okButton_corruptDatabaseExists_title(),
                    JOptionPane.WARNING_MESSAGE);
        } else if (manager.getStatus() == DatabaseTestResult.DB_DOES_NOT_EXIST) {
            //database doesn't exist. do you want to create?
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                    Bundle.EamDbSettingsDialog_okButton_createDbDialog_message(),
                    Bundle.EamDbSettingsDialog_okButton_createDbDialog_title(),
                    JOptionPane.YES_NO_OPTION)) {
                try {
                    manager.createDb();
                }
                catch (CentralRepoException e) {
                    // in the event that there is a failure to connect, notify user with corresponding message
                    String errorMessage;
                    switch (manager.getSelectedPlatform()) {
                        case POSTGRESQL:
                            errorMessage = Bundle.EamDbSettingsDialog_okButton_createPostgresDbError_message();
                            break;
                        case SQLITE:
                            errorMessage = Bundle.EamDbSettingsDialog_okButton_createSQLiteDbError_message();
                            break;
                        default:
                            errorMessage = "";
                            break;
                    }
                    
                    JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        errorMessage,
                        Bundle.EamDbSettingsDialog_okButton_createDbError_title(),
                        JOptionPane.WARNING_MESSAGE);
                }
                
                valid();
            }
        }

        return (manager.getStatus() == DatabaseTestResult.TESTEDOK);
    }   
    

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        bnGrpDatabasePlatforms = new javax.swing.ButtonGroup();
        fcDatabasePath = new javax.swing.JFileChooser();
        pnButtons = new javax.swing.JPanel();
        bnCancel = new javax.swing.JButton();
        bnOk = new javax.swing.JButton();
        pnSQLiteSettings = new javax.swing.JPanel();
        lbDatabasePath = new javax.swing.JLabel();
        tfDatabasePath = new javax.swing.JTextField();
        bnDatabasePathFileOpen = new javax.swing.JButton();
        lbHostName = new javax.swing.JLabel();
        tbDbHostname = new javax.swing.JTextField();
        lbPort = new javax.swing.JLabel();
        tbDbPort = new javax.swing.JTextField();
        lbUserName = new javax.swing.JLabel();
        tbDbUsername = new javax.swing.JTextField();
        lbUserPassword = new javax.swing.JLabel();
        jpDbPassword = new javax.swing.JPasswordField();
        cbDatabaseType = new javax.swing.JComboBox<>();
        lbSingleUserSqLite = new javax.swing.JLabel();
        lbDatabaseType = new javax.swing.JLabel();
        lbDatabaseDesc = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));
        dataBaseFileScrollPane = new javax.swing.JScrollPane();
        dataBaseFileTextArea = new javax.swing.JTextArea();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        org.openide.awt.Mnemonics.setLocalizedText(bnCancel, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.bnCancel.text")); // NOI18N
        bnCancel.setMaximumSize(new java.awt.Dimension(79, 23));
        bnCancel.setMinimumSize(new java.awt.Dimension(79, 23));
        bnCancel.setPreferredSize(new java.awt.Dimension(79, 23));
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnOk, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.bnOk.text")); // NOI18N
        bnOk.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnOkActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnButtonsLayout = new javax.swing.GroupLayout(pnButtons);
        pnButtons.setLayout(pnButtonsLayout);
        pnButtonsLayout.setHorizontalGroup(
            pnButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnButtonsLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bnOk)
                .addGap(11, 11, 11)
                .addComponent(bnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pnButtonsLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {bnCancel, bnOk});

        pnButtonsLayout.setVerticalGroup(
            pnButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnButtonsLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(pnButtonsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnOk)
                    .addComponent(bnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0))
        );

        pnSQLiteSettings.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabasePath, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbDatabasePath.text")); // NOI18N
        lbDatabasePath.setPreferredSize(new java.awt.Dimension(80, 14));

        tfDatabasePath.setText(org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.tfDatabasePath.text")); // NOI18N
        tfDatabasePath.setToolTipText(org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.tfDatabasePath.toolTipText")); // NOI18N
        tfDatabasePath.setPreferredSize(new java.awt.Dimension(420, 23));

        org.openide.awt.Mnemonics.setLocalizedText(bnDatabasePathFileOpen, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.bnDatabasePathFileOpen.text")); // NOI18N
        bnDatabasePathFileOpen.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDatabasePathFileOpenActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbHostName, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbHostName.text")); // NOI18N
        lbHostName.setPreferredSize(new java.awt.Dimension(80, 14));

        tbDbHostname.setPreferredSize(new java.awt.Dimension(509, 20));

        org.openide.awt.Mnemonics.setLocalizedText(lbPort, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbPort.text")); // NOI18N
        lbPort.setPreferredSize(new java.awt.Dimension(80, 14));

        tbDbPort.setPreferredSize(new java.awt.Dimension(509, 20));

        org.openide.awt.Mnemonics.setLocalizedText(lbUserName, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbUserName.text")); // NOI18N
        lbUserName.setPreferredSize(new java.awt.Dimension(80, 14));

        tbDbUsername.setPreferredSize(new java.awt.Dimension(509, 20));

        org.openide.awt.Mnemonics.setLocalizedText(lbUserPassword, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbUserPassword.text")); // NOI18N
        lbUserPassword.setPreferredSize(new java.awt.Dimension(80, 14));

        jpDbPassword.setPreferredSize(new java.awt.Dimension(509, 20));

        cbDatabaseType.setModel(new javax.swing.DefaultComboBoxModel<>(new CentralRepoPlatforms[]{CentralRepoPlatforms.POSTGRESQL, CentralRepoPlatforms.SQLITE}));
        cbDatabaseType.setPreferredSize(new java.awt.Dimension(120, 20));
        cbDatabaseType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbDatabaseTypeActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbSingleUserSqLite, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbSingleUserSqLite.text")); // NOI18N
        lbSingleUserSqLite.setPreferredSize(new java.awt.Dimension(381, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseType, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbDatabaseType.text")); // NOI18N
        lbDatabaseType.setMaximumSize(new java.awt.Dimension(80, 14));
        lbDatabaseType.setMinimumSize(new java.awt.Dimension(80, 14));
        lbDatabaseType.setPreferredSize(new java.awt.Dimension(80, 14));

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseDesc, org.openide.util.NbBundle.getMessage(EamDbSettingsDialog.class, "EamDbSettingsDialog.lbDatabaseDesc.text")); // NOI18N
        lbDatabaseDesc.setPreferredSize(new java.awt.Dimension(80, 14));

        dataBaseFileScrollPane.setBorder(null);

        dataBaseFileTextArea.setEditable(false);
        dataBaseFileTextArea.setBackground(new java.awt.Color(240, 240, 240));
        dataBaseFileTextArea.setColumns(20);
        dataBaseFileTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        dataBaseFileTextArea.setLineWrap(true);
        dataBaseFileTextArea.setRows(3);
        dataBaseFileScrollPane.setViewportView(dataBaseFileTextArea);

        javax.swing.GroupLayout pnSQLiteSettingsLayout = new javax.swing.GroupLayout(pnSQLiteSettings);
        pnSQLiteSettings.setLayout(pnSQLiteSettingsLayout);
        pnSQLiteSettingsLayout.setHorizontalGroup(
            pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbHostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabaseType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbUserName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(lbDatabaseDesc, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(lbUserPassword, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGap(10, 10, 10)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                        .addComponent(tfDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(bnDatabasePathFileOpen))
                    .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                        .addComponent(cbDatabaseType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(lbSingleUserSqLite, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jpDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(dataBaseFileScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 509, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
            .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                .addGap(55, 55, 55)
                .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        pnSQLiteSettingsLayout.setVerticalGroup(
            pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cbDatabaseType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(lbSingleUserSqLite, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(lbDatabaseType, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tfDatabasePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(bnDatabasePathFileOpen))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbDbHostname, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbHostName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbDbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbDbUsername, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbUserName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jpDbPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbUserPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnSQLiteSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnSQLiteSettingsLayout.createSequentialGroup()
                        .addComponent(lbDatabaseDesc, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(dataBaseFileScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnButtons, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnSQLiteSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pnSQLiteSettings, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(pnButtons, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void customizeComponents() {
        setTextPrompts();
        setTextBoxListeners();
        manager.clearStatus();
        if (manager.getSelectedPlatform() == CentralRepoPlatforms.SQLITE) {
            updatePostgresFields(false);
            updateSqliteFields(true);
        }
        else {
            updatePostgresFields(true);
            updateSqliteFields(false);
        }
        displayDatabaseSettings(CentralRepoPlatforms.POSTGRESQL.equals(manager.getSelectedPlatform()));
    }

    private void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    @Messages({"EamDbSettingsDialog.chooserPath.failedToGetDbPathMsg=Selected database path is invalid. Try again."})
    private void bnDatabasePathFileOpenActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDatabasePathFileOpenActionPerformed
        fcDatabasePath.setSelectedFile(new File(manager.getDbSettingsSqlite().getDbDirectory()));
        if (fcDatabasePath.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File databaseFile = fcDatabasePath.getSelectedFile();
            if (databaseFile.isFile()) {
                databaseFile = fcDatabasePath.getCurrentDirectory();
            }
            try {
                tfDatabasePath.setText(databaseFile.getCanonicalPath());
                tfDatabasePath.setCaretPosition(tfDatabasePath.getText().length());
                valid();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Failed to get path of selected database file", ex); // NON-NLS
            }
        }
    }//GEN-LAST:event_bnDatabasePathFileOpenActionPerformed

    @NbBundle.Messages({"EamDbSettingsDialog.okButton.errorTitle.text=Restart Required.",
        "EamDbSettingsDialog.okButton.errorMsg.text=Please restart Autopsy to begin using the new database platform.",
        "EamDbSettingsDialog.okButton.connectionErrorMsg.text=Failed to connect to central repository database."})
    private void bnOkActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnOkActionPerformed
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        manager.testStatus();
        valid();
        
        boolean testedOk = promptTestStatusWarnings();
        if (!testedOk) {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            return;
        }
        
        try{
            manager.saveNewCentralRepo();
        }
        catch (CentralRepoException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(this,
                    Bundle.EamDbSettingsDialog_okButton_errorMsg_text(),
                    Bundle.EamDbSettingsDialog_okButton_errorTitle_text(),
                    JOptionPane.WARNING_MESSAGE);
            });
        }
        

        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        dispose();
    }//GEN-LAST:event_bnOkActionPerformed

    
    /**
     * Returns if changes to the central repository configuration were
     * successfully applied
     *
     * @return true if the database configuration was successfully changed false
     * if it was not
     */
    public boolean wasConfigurationChanged() {
        return manager.wasConfigurationChanged();
    }

    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        dispose();
    }//GEN-LAST:event_bnCancelActionPerformed


    private void cbDatabaseTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbDatabaseTypeActionPerformed
        manager.setSelectedPlatform((CentralRepoPlatforms) cbDatabaseType.getSelectedItem());
        customizeComponents();
    }//GEN-LAST:event_cbDatabaseTypeActionPerformed

    private void updateFullDbPath() {
        dataBaseFileTextArea.setText(tfDatabasePath.getText() + File.separator + SqliteCentralRepoSettings.DEFAULT_DBNAME);
        dataBaseFileTextArea.setCaretPosition(dataBaseFileTextArea.getText().length());
    }

    private void displayDatabaseSettings(boolean isPostgres) {
        lbDatabasePath.setVisible(!isPostgres);
        tfDatabasePath.setVisible(!isPostgres);
        lbDatabaseDesc.setVisible(!isPostgres);
        dataBaseFileTextArea.setVisible(!isPostgres);
        lbSingleUserSqLite.setVisible(!isPostgres);
        bnDatabasePathFileOpen.setVisible(!isPostgres);
        lbHostName.setVisible(isPostgres);
        tbDbHostname.setVisible(isPostgres);
        lbPort.setVisible(isPostgres);
        tbDbPort.setVisible(isPostgres);
        lbUserName.setVisible(isPostgres);
        tbDbUsername.setVisible(isPostgres);
        lbUserPassword.setVisible(isPostgres);
        jpDbPassword.setVisible(isPostgres);
    }

    /**
     * Add text prompts to all of the text fields.
     */
    @Messages({"EamDbSettingsDialog.textPrompt.hostnameOrIP=Hostname or IP Address",
        "EamDbSettingsDialog.textPrompt.port=Port Number",
        "EamDbSettingsDialog.textPrompt.dbName=Database Name",
        "EamDbSettingsDialog.textPrompt.user=Database User",
        "EamDbSettingsDialog.textPrompt.password=Database User's Password"})
    private void setTextPrompts() {
        Collection<TextPrompt> textPrompts = new ArrayList<>();
        textPrompts.add(new TextPrompt(Bundle.EamDbSettingsDialog_textPrompt_hostnameOrIP(), tbDbHostname));
        textPrompts.add(new TextPrompt(Bundle.EamDbSettingsDialog_textPrompt_port(), tbDbPort));
        textPrompts.add(new TextPrompt(Bundle.EamDbSettingsDialog_textPrompt_user(), tbDbUsername));
        configureTextPrompts(textPrompts);
    }

    private void updatePostgresFields(boolean enabled) {
        tbDbHostname.setText(enabled ? manager.getDbSettingsPostgres().getHost() : "");
        tbDbHostname.setEnabled(enabled);
        tbDbPort.setText(enabled ? Integer.toString(manager.getDbSettingsPostgres().getPort()) : "");
        tbDbPort.setEnabled(enabled);
        tbDbUsername.setText(enabled ? manager.getDbSettingsPostgres().getUserName() : "");
        tbDbUsername.setEnabled(enabled);
        jpDbPassword.setText(enabled ? manager.getDbSettingsPostgres().getPassword() : "");
        jpDbPassword.setEnabled(enabled);
    }

    /**
     * Update the fields for the SQLite platform depending on whether the SQLite
     * radioButton is enabled.
     *
     * @param enabled
     */
    private void updateSqliteFields(boolean enabled) {
        tfDatabasePath.setText(enabled ? manager.getDbSettingsSqlite().getDbDirectory() : "");
        tfDatabasePath.setEnabled(enabled);
        bnDatabasePathFileOpen.setEnabled(enabled);
    }

    /**
     * Register for notifications when the text boxes get updated.
     */
    private void setTextBoxListeners() {
        textBoxes.add(tfDatabasePath);
        textBoxes.add(tbDbHostname);
        textBoxes.add(tbDbPort);
        //     textBoxes.add(tbDbName);
        textBoxes.add(tbDbUsername);
        textBoxes.add(jpDbPassword);
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
     * @param listener The change listener.
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
    @Messages({"EamDbSettingsDialog.validation.incompleteFields=Fill in all values for the selected database."})
    private boolean databaseFieldsArePopulated() {
        boolean result = true;
        if (manager.getSelectedPlatform() == CentralRepoPlatforms.POSTGRESQL) {
            result = !tbDbHostname.getText().trim().isEmpty()
                    && !tbDbPort.getText().trim().isEmpty()
                    //   && !tbDbName.getText().trim().isEmpty()
                    && !tbDbUsername.getText().trim().isEmpty()
                    && 0 < jpDbPassword.getPassword().length;
        }
        else if (manager.getSelectedPlatform() == CentralRepoPlatforms.SQLITE) {
            result = !tfDatabasePath.getText().trim().isEmpty();
        }

        return result;
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    private boolean checkFields() {
        return databaseFieldsArePopulated()
                && databaseSettingsAreValid();
    }


    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    private boolean valid() {
        return enableOkButton(checkFields());
    }

    /**
     * Enable the "OK" button if the db test passed. Disabled defaults to db
     * test passed.
     *
     * @return true
     */
    @Messages({"EamDbSettingsDialog.validation.finished=Click OK to save your database settings and return to the Options. Or select a different database type."})
    private boolean enableOkButton(boolean isValidInput) {
        if (isValidInput) {
            bnOk.setEnabled(true);
        } else {
            bnOk.setEnabled(false);
        }
        return true;

    }
    
    
    
    /**
     * Tests whether or not the database settings are valid.
     *
     * @return True or false.
     */
    private boolean databaseSettingsAreValid() {
        try {
            manager.testDatabaseSettingsAreValid(
                    tbDbHostname.getText().trim(), 
                    tbDbPort.getText().trim(), 
                    tbDbUsername.getText().trim(), 
                    tfDatabasePath.getText().trim(), 
                    new String(jpDbPassword.getPassword()));
        }
        catch (CentralRepoException | NumberFormatException | IllegalStateException e) {
            return false;
        }
        
        return true;
    }

    /**
     * Used to listen for changes in text boxes. It lets the panel know things
     * have been updated and that validation needs to happen.
     */
    private class TextBoxChangedListener implements DocumentListener {

        @Override
        public void changedUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            manager.clearStatus();
            updateFullDbPath();
            valid();
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            manager.clearStatus();
            updateFullDbPath();
            valid();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
            manager.clearStatus();
            updateFullDbPath();
            valid();

        }
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnDatabasePathFileOpen;
    private javax.swing.ButtonGroup bnGrpDatabasePlatforms;
    private javax.swing.JButton bnOk;
    private javax.swing.JComboBox<CentralRepoPlatforms> cbDatabaseType;
    private javax.swing.JScrollPane dataBaseFileScrollPane;
    private javax.swing.JTextArea dataBaseFileTextArea;
    private javax.swing.JFileChooser fcDatabasePath;
    private javax.swing.Box.Filler filler1;
    private javax.swing.JPasswordField jpDbPassword;
    private javax.swing.JLabel lbDatabaseDesc;
    private javax.swing.JLabel lbDatabasePath;
    private javax.swing.JLabel lbDatabaseType;
    private javax.swing.JLabel lbHostName;
    private javax.swing.JLabel lbPort;
    private javax.swing.JLabel lbSingleUserSqLite;
    private javax.swing.JLabel lbUserName;
    private javax.swing.JLabel lbUserPassword;
    private javax.swing.JPanel pnButtons;
    private javax.swing.JPanel pnSQLiteSettings;
    private javax.swing.JTextField tbDbHostname;
    private javax.swing.JTextField tbDbPort;
    private javax.swing.JTextField tbDbUsername;
    private javax.swing.JTextField tfDatabasePath;
    // End of variables declaration//GEN-END:variables
}
