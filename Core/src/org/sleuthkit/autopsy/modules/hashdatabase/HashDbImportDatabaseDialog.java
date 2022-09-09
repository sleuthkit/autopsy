/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoOrganization;
import org.sleuthkit.autopsy.centralrepository.optionspanel.ManageOrganizationsDialog;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb.KnownFilesType;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDbManagerException;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.featureaccess.FeatureAccessUtils;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;

/**
 * Instances of this class allow a user to select an existing hash database and
 * add it to the set of hash databases used to classify files as unknown, known,
 * or notable.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class HashDbImportDatabaseDialog extends javax.swing.JDialog {

    private JFileChooser fileChooser;
    private String selectedFilePath = "";
    private HashDb selectedHashDb = null;
    private final static String LAST_FILE_PATH_KEY = "HashDbImport_Path";
    private CentralRepoOrganization selectedOrg = null;
    private List<CentralRepoOrganization> orgs = null;
    private final JFileChooserFactory chooserHelper;

    /**
     * Displays a dialog that allows a user to select an existing hash database
     * and add it to the set of hash databases used to classify files as
     * unknown, known, or notable.
     */
    HashDbImportDatabaseDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.importHashDbMsg"),
                true);
        chooserHelper = new JFileChooserFactory();
        initComponents();
        enableComponents();
        display();
    }

    /**
     * Get the hash database imported by the user, if any.
     *
     * @return A HashDb object or null.
     */
    HashDb getHashDatabase() {
        return selectedHashDb;
    }

    private void display() {
        setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        setVisible(true);
    }

    private static String shortenPath(String path) {
        String shortenedPath = path;
        if (shortenedPath.length() > 50) {
            shortenedPath = shortenedPath.substring(0, 10 + shortenedPath.substring(10).indexOf(File.separator) + 1) + "..." + shortenedPath.substring((shortenedPath.length() - 20) + shortenedPath.substring(shortenedPath.length() - 20).indexOf(File.separator));
        }
        return shortenedPath;
    }

    private void enableComponents() {

        if (!CentralRepository.isEnabled() || !FeatureAccessUtils.canAddHashSetsToCentralRepo()) {
            centralRepoRadioButton.setEnabled(false);
            fileTypeRadioButton.setSelected(true);
        } else {
            populateCombobox();
        }

        boolean isFileType = fileTypeRadioButton.isSelected();

        // Central repo only
        lbVersion.setEnabled((!isFileType) && (readOnlyCheckbox.isSelected()));
        versionTextField.setEnabled((!isFileType) && (readOnlyCheckbox.isSelected()));

        lbOrg.setEnabled(!isFileType);
        orgButton.setEnabled(!isFileType);
        orgComboBox.setEnabled(!isFileType);
        readOnlyCheckbox.setEnabled(!isFileType);
    }

    @NbBundle.Messages({"HashDbImportDatabaseDialog.populateOrgsError.message=Failure loading organizations."})
    private void populateCombobox() {
        orgComboBox.removeAllItems();
        try {
            CentralRepository dbManager = CentralRepository.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                orgComboBox.addItem(org.getName());
                if (CentralRepoDbUtil.isDefaultOrg(org)) {
                    orgComboBox.setSelectedItem(org.getName());
                    selectedOrg = org;
                }
            });
            if ((selectedOrg == null) && (!orgs.isEmpty())) {
                selectedOrg = orgs.get(0);
            }
        } catch (CentralRepoException ex) {
            JOptionPane.showMessageDialog(this, Bundle.HashDbImportDatabaseDialog_populateOrgsError_message());
            Logger.getLogger(ImportCentralRepoDbProgressDialog.class.getName()).log(Level.SEVERE, "Failure loading organizations", ex);
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

        buttonGroup1 = new javax.swing.ButtonGroup();
        storageTypeButtonGroup = new javax.swing.ButtonGroup();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        databasePathTextField = new javax.swing.JTextField();
        openButton = new javax.swing.JButton();
        knownRadioButton = new javax.swing.JRadioButton();
        knownBadRadioButton = new javax.swing.JRadioButton();
        jLabel1 = new javax.swing.JLabel();
        hashSetNameTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        sendIngestMessagesCheckbox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        lbVersion = new javax.swing.JLabel();
        versionTextField = new javax.swing.JTextField();
        lbOrg = new javax.swing.JLabel();
        orgComboBox = new javax.swing.JComboBox<>();
        orgButton = new javax.swing.JButton();
        readOnlyCheckbox = new javax.swing.JCheckBox();
        fileTypeRadioButton = new javax.swing.JRadioButton();
        centralRepoRadioButton = new javax.swing.JRadioButton();
        jLabel4 = new javax.swing.JLabel();
        saveInUserConfigFolderCheckbox = new javax.swing.JCheckBox();
        noChangeRadioButton = new javax.swing.JRadioButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        databasePathTextField.setEditable(false);
        databasePathTextField.setText(org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.databasePathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(openButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.openButton.text")); // NOI18N
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(knownRadioButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.knownRadioButton.text")); // NOI18N
        knownRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                knownRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownBadRadioButton);
        knownBadRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(knownBadRadioButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.knownBadRadioButton.text")); // NOI18N
        knownBadRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                knownBadRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.jLabel1.text")); // NOI18N

        hashSetNameTextField.setText(org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.hashSetNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.jLabel2.text")); // NOI18N

        sendIngestMessagesCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(sendIngestMessagesCheckbox, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.sendIngestMessagesCheckbox.text")); // NOI18N
        sendIngestMessagesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendIngestMessagesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbVersion, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.lbVersion.text")); // NOI18N

        versionTextField.setText(org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.versionTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbOrg, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.lbOrg.text")); // NOI18N

        orgComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                orgComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(orgButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.orgButton.text")); // NOI18N
        orgButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                orgButtonActionPerformed(evt);
            }
        });

        readOnlyCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(readOnlyCheckbox, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.readOnlyCheckbox.text")); // NOI18N
        readOnlyCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                readOnlyCheckboxActionPerformed(evt);
            }
        });

        storageTypeButtonGroup.add(fileTypeRadioButton);
        fileTypeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(fileTypeRadioButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.fileTypeRadioButton.text")); // NOI18N
        fileTypeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileTypeRadioButtonActionPerformed(evt);
            }
        });

        storageTypeButtonGroup.add(centralRepoRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(centralRepoRadioButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.centralRepoRadioButton.text")); // NOI18N
        centralRepoRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centralRepoRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(saveInUserConfigFolderCheckbox, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.saveInUserConfigFolderCheckbox.text")); // NOI18N
        saveInUserConfigFolderCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.saveInUserConfigFolderCheckbox.toolTipText")); // NOI18N

        buttonGroup1.add(noChangeRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(noChangeRadioButton, org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.noChangeRadioButton.text")); // NOI18N
        noChangeRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(HashDbImportDatabaseDialog.class, "HashDbImportDatabaseDialog.noChangeRadioButton.toolTipText")); // NOI18N
        noChangeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                noChangeRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(fileTypeRadioButton)
                                .addGap(26, 26, 26)
                                .addComponent(centralRepoRadioButton)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(databasePathTextField)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(openButton))))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(lbOrg)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(orgComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 134, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(orgButton))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel1)
                                    .addComponent(lbVersion))
                                .addGap(40, 40, 40)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(versionTextField)
                                    .addComponent(hashSetNameTextField))))
                        .addGap(81, 81, 81))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(sendIngestMessagesCheckbox)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(okButton)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel2)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(19, 19, 19)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(knownRadioButton)
                                    .addComponent(knownBadRadioButton)
                                    .addComponent(noChangeRadioButton)))
                            .addComponent(saveInUserConfigFolderCheckbox)
                            .addComponent(readOnlyCheckbox))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(databasePathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel3)
                    .addComponent(openButton))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fileTypeRadioButton)
                    .addComponent(centralRepoRadioButton)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(hashSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbVersion)
                    .addComponent(versionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(orgButton)
                    .addComponent(orgComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbOrg))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownBadRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(noChangeRadioButton)
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(readOnlyCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(sendIngestMessagesCheckbox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(saveInUserConfigFolderCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(81, 81, 81)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cancelButton)
                            .addComponent(okButton))))
                .addGap(18, 18, 18))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        if(fileChooser == null) {
            fileChooser = chooserHelper.getChooser();
            fileChooser.setDragEnabled(false);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            String[] EXTENSION = new String[]{"txt", "kdb", "idx", "hash", "Hash", "hsh"}; //NON-NLS
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    NbBundle.getMessage(this.getClass(), "HashDbImportDatabaseDialog.fileNameExtFilter.text"), EXTENSION);
            fileChooser.setFileFilter(filter);
            fileChooser.setMultiSelectionEnabled(false);
        }
        
        String lastBaseDirectory = HashLookupSettings.getDefaultDbPath();
        if (ModuleSettings.settingExists(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY)) {
            lastBaseDirectory = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY);
        }
        File hashDbFolder = new File(lastBaseDirectory);
        // create the folder if it doesn't exist
        if (!hashDbFolder.exists()) {
            hashDbFolder.mkdirs();
        }
        fileChooser.setCurrentDirectory(hashDbFolder);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File databaseFile = fileChooser.getSelectedFile();
            try {
                selectedFilePath = databaseFile.getCanonicalPath();
                databasePathTextField.setText(shortenPath(selectedFilePath));
                hashSetNameTextField.setText(FilenameUtils.removeExtension(databaseFile.getName()));
                if (hashSetNameTextField.getText().toLowerCase().contains("nsrl")) { //NON-NLS
                    knownRadioButton.setSelected(true);
                    knownRadioButtonActionPerformed(null);
                }
                ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY, databaseFile.getParent());
            } catch (IOException ex) {
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.SEVERE, "Failed to get path of selected hash set", ex); //NON-NLS
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.failedToGetDbPathMsg"));
            }
        }
    }//GEN-LAST:event_openButtonActionPerformed

    private void knownRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_knownRadioButtonActionPerformed
        sendIngestMessagesCheckbox.setSelected(KnownFilesType.KNOWN.isDefaultInboxMessages());
        sendIngestMessagesCheckbox.setEnabled(KnownFilesType.KNOWN.isInboxMessagesAllowed());
    }//GEN-LAST:event_knownRadioButtonActionPerformed

    private void knownBadRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_knownBadRadioButtonActionPerformed
        sendIngestMessagesCheckbox.setSelected(KnownFilesType.KNOWN_BAD.isDefaultInboxMessages());
        sendIngestMessagesCheckbox.setEnabled(KnownFilesType.KNOWN_BAD.isInboxMessagesAllowed());
    }//GEN-LAST:event_knownBadRadioButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        this.dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    @NbBundle.Messages({"HashDbImportDatabaseDialog.missingVersion=A version must be entered",
        "HashDbImportDatabaseDialog.missingOrg=An organization must be selected",
        "HashDbImportDatabaseDialog.duplicateName=A hashset with this name and version already exists",
        "HashDbImportDatabaseDialog.databaseLookupError=Error accessing central repository",
        "HashDbImportDatabaseDialog.mustEnterHashSetNameMsg=A hash set name must be entered."
    })
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        // Note that the error handlers in this method call return without disposing of the 
        // dialog to allow the user to try again, if desired.

        if (hashSetNameTextField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.mustEnterHashSetNameMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (centralRepoRadioButton.isSelected()) {
            if (readOnlyCheckbox.isSelected() && versionTextField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.missingVersion"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.importHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (selectedOrg == null) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.missingOrg"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.importHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        if (selectedFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.mustSelectHashDbFilePathMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        File file = new File(selectedFilePath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.hashDbDoesNotExistMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (saveInUserConfigFolderCheckbox.isSelected()) {
            // copy the hash database to user configuration directory and use that path instead (JIRA-4177)
            String locationInUserConfigDir = Paths.get(HashLookupSettings.getDefaultDbPath(), hashSetNameTextField.getText(), file.getName()).toString();
            try {
                File newFileLoc = new File(locationInUserConfigDir);
                newFileLoc.getParentFile().mkdirs();
                FileUtils.copyFile(file, newFileLoc);
                // update the hash database location
                selectedFilePath = locationInUserConfigDir;
            } catch (IOException ex) {
                String errorMessage = NbBundle.getMessage(this.getClass(), "HashDbImportDatabaseDialog.unableToCopyToUserDirMsg", locationInUserConfigDir);
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.SEVERE, errorMessage, ex);
                JOptionPane.showMessageDialog(this, errorMessage, NbBundle.getMessage(this.getClass(), "HashDbImportDatabaseDialog.importHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        KnownFilesType type;
        if (knownRadioButton.isSelected()) {
            type = KnownFilesType.KNOWN;
        } else if (noChangeRadioButton.isSelected()) {
            type = KnownFilesType.NO_CHANGE;
        } else {
            type = KnownFilesType.KNOWN_BAD;
        }

        String errorMessage = NbBundle.getMessage(this.getClass(),
                "HashDbImportDatabaseDialog.errorMessage.failedToOpenHashDbMsg",
                selectedFilePath);
        if (fileTypeRadioButton.isSelected()) {

            try {
                selectedHashDb = HashDbManager.getInstance().addExistingHashDatabaseNoSave(hashSetNameTextField.getText(), selectedFilePath, true, sendIngestMessagesCheckbox.isSelected(), type);
            } catch (HashDbManagerException ex) {
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.WARNING, errorMessage, ex);
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.importHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {

            // Check if a hash set with the same name/version already exists
            try {
                if (CentralRepository.getInstance().referenceSetExists(hashSetNameTextField.getText(), versionTextField.getText())) {
                    JOptionPane.showMessageDialog(this,
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbImportDatabaseDialog.duplicateName"),
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbImportDatabaseDialog.importHashDbErr"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (CentralRepoException ex) {
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.SEVERE, "Error looking up reference set", ex);
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.databaseLookupError"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbImportDatabaseDialog.importHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            String version;
            if (readOnlyCheckbox.isSelected()) {
                version = versionTextField.getText();
            } else {
                // Editable databases don't have a version
                version = "";
            }
            ImportCentralRepoDbProgressDialog progressDialog = new ImportCentralRepoDbProgressDialog();
            progressDialog.importFile(hashSetNameTextField.getText(), version,
                    selectedOrg.getOrgID(), true, sendIngestMessagesCheckbox.isSelected(), type,
                    readOnlyCheckbox.isSelected(), selectedFilePath);
            selectedHashDb = progressDialog.getDatabase();
        }

        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void sendIngestMessagesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendIngestMessagesCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sendIngestMessagesCheckboxActionPerformed

    private void fileTypeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileTypeRadioButtonActionPerformed
        enableComponents();
    }//GEN-LAST:event_fileTypeRadioButtonActionPerformed

    private void centralRepoRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centralRepoRadioButtonActionPerformed
        enableComponents();
    }//GEN-LAST:event_centralRepoRadioButtonActionPerformed

    private void orgButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_orgButtonActionPerformed
        ManageOrganizationsDialog dialog = new ManageOrganizationsDialog();
        // update the combobox options
        if (dialog.isChanged()) {
            populateCombobox();
        }
    }//GEN-LAST:event_orgButtonActionPerformed

    private void orgComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_orgComboBoxActionPerformed
        if (null == orgComboBox.getSelectedItem()) {
            return;
        }
        String orgName = this.orgComboBox.getSelectedItem().toString();
        for (CentralRepoOrganization org : orgs) {
            if (org.getName().equals(orgName)) {
                selectedOrg = org;
                return;
            }
        }
    }//GEN-LAST:event_orgComboBoxActionPerformed

    private void readOnlyCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_readOnlyCheckboxActionPerformed
        enableComponents();
    }//GEN-LAST:event_readOnlyCheckboxActionPerformed

    private void noChangeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_noChangeRadioButtonActionPerformed
        sendIngestMessagesCheckbox.setSelected(KnownFilesType.NO_CHANGE.isDefaultInboxMessages());
        sendIngestMessagesCheckbox.setEnabled(KnownFilesType.NO_CHANGE.isInboxMessagesAllowed());
    }//GEN-LAST:event_noChangeRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancelButton;
    private javax.swing.JRadioButton centralRepoRadioButton;
    private javax.swing.JTextField databasePathTextField;
    private javax.swing.JRadioButton fileTypeRadioButton;
    private javax.swing.JTextField hashSetNameTextField;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JRadioButton knownBadRadioButton;
    private javax.swing.JRadioButton knownRadioButton;
    private javax.swing.JLabel lbOrg;
    private javax.swing.JLabel lbVersion;
    private javax.swing.JRadioButton noChangeRadioButton;
    private javax.swing.JButton okButton;
    private javax.swing.JButton openButton;
    private javax.swing.JButton orgButton;
    private javax.swing.JComboBox<String> orgComboBox;
    private javax.swing.JCheckBox readOnlyCheckbox;
    private javax.swing.JCheckBox saveInUserConfigFolderCheckbox;
    private javax.swing.JCheckBox sendIngestMessagesCheckbox;
    private javax.swing.ButtonGroup storageTypeButtonGroup;
    private javax.swing.JTextField versionTextField;
    // End of variables declaration//GEN-END:variables
}
