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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.apache.commons.io.FilenameUtils;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalSet;
import org.sleuthkit.autopsy.centralrepository.optionspanel.ManageOrganizationsDialog;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb.KnownFilesType;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDbManagerException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this class allow a user to create a new hash database and add it
 * to the set of hash databases used to classify files as unknown, known or
 * notable.
 */
final class HashDbCreateDatabaseDialog extends javax.swing.JDialog {

    private static final String DEFAULT_FILE_NAME = NbBundle
            .getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.defaultFileName");
    private JFileChooser fileChooser = null;
    private HashDb newHashDb = null;
    private final static String LAST_FILE_PATH_KEY = "HashDbCreate_Path";
    private EamOrganization selectedOrg = null;
    private List<EamOrganization> orgs = null;

    /**
     * Displays a dialog that allows a user to create a new hash database and
     * add it to the set of hash databases used to classify files as unknown,
     * known or notable.
     */
    HashDbCreateDatabaseDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(), NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.createHashDbMsg"), true);
        initFileChooser();
        initComponents();
        enableComponents();
        display();
    }

    /**
     * Get the hash database created by the user, if any.
     *
     * @return A HashDb object or null.
     */
    HashDb getHashDatabase() {
        return newHashDb;
    }

    private void initFileChooser() {
        fileChooser = new JFileChooser() {
            @Override
            public void approveSelection() {
                File selectedFile = getSelectedFile();
                if (!FilenameUtils.getExtension(selectedFile.getName()).equalsIgnoreCase(HashDbManager.getHashDatabaseFileExtension())) {
                    if (JOptionPane.showConfirmDialog(this,
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbCreateDatabaseDialog.hashDbMustHaveFileExtensionMsg",
                                    HashDbManager.getHashDatabaseFileExtension()),
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbCreateDatabaseDialog.fileNameErr"),
                            JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) {
                        cancelSelection();
                    }
                    return;
                }
                if (selectedFile.exists()) {
                    if (JOptionPane.showConfirmDialog(this,
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbCreateDatabaseDialog.fileNameAlreadyExistsMsg"),
                            NbBundle.getMessage(this.getClass(),
                                    "HashDbCreateDatabaseDialog.fileExistsErr"),
                            JOptionPane.OK_CANCEL_OPTION) == JOptionPane.CANCEL_OPTION) {
                        cancelSelection();
                    }
                    return;
                }
                super.approveSelection();
            }
        };
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setDragEnabled(false);
        fileChooser.setMultiSelectionEnabled(false);
    }

    private void display() {
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }
    
    private void enableComponents(){
                
        if(! EamDb.isEnabled()){
            centralRepoRadioButton.setEnabled(false);
            fileTypeRadioButton.setSelected(true);
        } else {
            populateCombobox();
        }
        
        boolean isFileType = fileTypeRadioButton.isSelected();

        // Type type only
        databasePathLabel.setEnabled(isFileType);
        databasePathTextField.setEnabled(isFileType);
        saveAsButton.setEnabled(isFileType);
        
        // Central repo only
        lbOrg.setEnabled(! isFileType);
        orgButton.setEnabled(! isFileType);
        orgComboBox.setEnabled(! isFileType);
    }
    
    @NbBundle.Messages({"HashDbCreateDatabaseDialog.populateOrgsError.message=Failure loading organizations."})
    private void populateCombobox() {
        orgComboBox.removeAllItems();
        try {
            EamDb dbManager = EamDb.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                orgComboBox.addItem(org.getName());
                if(EamDbUtil.isDefaultOrg(org)){
                    orgComboBox.setSelectedItem(org.getName());
                    selectedOrg = org;
                }
            });
            if ((selectedOrg == null) && (!orgs.isEmpty())) {
                selectedOrg = orgs.get(0);
            }
        } catch (EamDbException ex) {
            JOptionPane.showMessageDialog(this, Bundle.HashDbCreateDatabaseDialog_populateOrgsError_message());
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
        saveAsButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        knownRadioButton = new javax.swing.JRadioButton();
        knownBadRadioButton = new javax.swing.JRadioButton();
        databasePathLabel = new javax.swing.JLabel();
        hashSetNameTextField = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        sendIngestMessagesCheckbox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        databasePathTextField = new javax.swing.JTextField();
        okButton = new javax.swing.JButton();
        jLabel4 = new javax.swing.JLabel();
        fileTypeRadioButton = new javax.swing.JRadioButton();
        centralRepoRadioButton = new javax.swing.JRadioButton();
        lbOrg = new javax.swing.JLabel();
        orgComboBox = new javax.swing.JComboBox<>();
        orgButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(saveAsButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.saveAsButton.text")); // NOI18N
        saveAsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveAsButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(knownRadioButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.knownRadioButton.text")); // NOI18N
        knownRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                knownRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownBadRadioButton);
        knownBadRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(knownBadRadioButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.knownBadRadioButton.text")); // NOI18N
        knownBadRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                knownBadRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(databasePathLabel, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.databasePathLabel.text")); // NOI18N

        hashSetNameTextField.setText(org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.hashSetNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.jLabel2.text")); // NOI18N

        sendIngestMessagesCheckbox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(sendIngestMessagesCheckbox, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.sendIngestMessagesCheckbox.text")); // NOI18N
        sendIngestMessagesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendIngestMessagesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.jLabel3.text")); // NOI18N

        databasePathTextField.setEditable(false);
        databasePathTextField.setText(org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.databasePathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.jLabel4.text")); // NOI18N

        storageTypeButtonGroup.add(fileTypeRadioButton);
        fileTypeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(fileTypeRadioButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.fileTypeRadioButton.text")); // NOI18N
        fileTypeRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileTypeRadioButtonActionPerformed(evt);
            }
        });

        storageTypeButtonGroup.add(centralRepoRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(centralRepoRadioButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.centralRepoRadioButton.text")); // NOI18N
        centralRepoRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                centralRepoRadioButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(lbOrg, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.lbOrg.text")); // NOI18N

        orgComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                orgComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(orgButton, org.openide.util.NbBundle.getMessage(HashDbCreateDatabaseDialog.class, "HashDbCreateDatabaseDialog.orgButton.text")); // NOI18N
        orgButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                orgButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addComponent(lbOrg)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(orgComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(orgButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel3)
                                    .addComponent(jLabel4)
                                    .addComponent(databasePathLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(fileTypeRadioButton)
                                        .addGap(22, 22, 22)
                                        .addComponent(centralRepoRadioButton))
                                    .addComponent(hashSetNameTextField)
                                    .addComponent(databasePathTextField))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(saveAsButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addGap(88, 88, 88))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(32, 32, 32)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(knownRadioButton)
                            .addComponent(knownBadRadioButton)))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(jLabel2))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(12, 12, 12)
                        .addComponent(sendIngestMessagesCheckbox)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(fileTypeRadioButton)
                    .addComponent(centralRepoRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(hashSetNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(databasePathLabel)
                    .addComponent(databasePathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(saveAsButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbOrg)
                    .addComponent(orgComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orgButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownBadRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(sendIngestMessagesCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cancelButton)
                            .addComponent(okButton))))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void knownRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_knownRadioButtonActionPerformed
        sendIngestMessagesCheckbox.setSelected(false);
        sendIngestMessagesCheckbox.setEnabled(false);
    }//GEN-LAST:event_knownRadioButtonActionPerformed

    private void knownBadRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_knownBadRadioButtonActionPerformed
        sendIngestMessagesCheckbox.setSelected(true);
        sendIngestMessagesCheckbox.setEnabled(true);
    }//GEN-LAST:event_knownBadRadioButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void saveAsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveAsButtonActionPerformed
        try {
            String lastBaseDirectory = Paths.get(PlatformUtil.getUserConfigDirectory(), "HashDatabases").toString();
            if (ModuleSettings.settingExists(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY)) {
                lastBaseDirectory = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY);
            }
            StringBuilder path = new StringBuilder();
            path.append(lastBaseDirectory);
            File hashDbFolder = new File(path.toString());
            // create the folder if it doesn't exist
            if (!hashDbFolder.exists()){
                hashDbFolder.mkdir();
            }
            if (!hashSetNameTextField.getText().isEmpty()) {
                path.append(File.separator).append(hashSetNameTextField.getText());
            } else {
                path.append(File.separator).append(DEFAULT_FILE_NAME);
            }
            path.append(".").append(HashDbManager.getHashDatabaseFileExtension());
            fileChooser.setSelectedFile(new File(path.toString()));
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File databaseFile = fileChooser.getSelectedFile();
                databasePathTextField.setText(databaseFile.getCanonicalPath());
                ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY, databaseFile.getParent());
            }
        } catch (IOException ex) {
            Logger.getLogger(HashDbCreateDatabaseDialog.class.getName()).log(Level.WARNING, "Couldn't get selected file path.", ex); //NON-NLS
        }
    }//GEN-LAST:event_saveAsButtonActionPerformed

    @NbBundle.Messages({"HashDbCreateDatabaseDialog.missingOrg=An organization must be selected",
        "HashDbCreateDatabaseDialog.duplicateName=A hashset with this name already exists",
        "HashDbCreateDatabaseDialog.databaseLookupError=Error accessing central repository",
        "HashDbCreateDatabaseDialog.databaseCreationError=Error creating new hash set"
    })
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        // Note that the error handlers in this method call return without disposing of the 
        // dialog to allow the user to try again, if desired.

        if (hashSetNameTextField.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "HashDbCreateDatabaseDialog.mustEnterHashSetNameMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbCreateDatabaseDialog.createHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if(fileTypeRadioButton.isSelected()){
            if (databasePathTextField.getText().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.mustEnterHashDbPathMsg"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            if(selectedOrg == null){
                JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "HashDbCreateDatabaseDialog.missingOrg"),
                    NbBundle.getMessage(this.getClass(),
                            "HashDbCreateDatabaseDialog.createHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        KnownFilesType type;
        TskData.FileKnown fileKnown;
        if (knownRadioButton.isSelected()) {
            type = KnownFilesType.KNOWN;
            fileKnown = TskData.FileKnown.KNOWN;
        } else {
            type = KnownFilesType.KNOWN_BAD;
            fileKnown = TskData.FileKnown.BAD;
        }

        String errorMessage = NbBundle
                .getMessage(this.getClass(), "HashDbCreateDatabaseDialog.errMsg.hashDbCreationErr");

        if(fileTypeRadioButton.isSelected()){
            try {
                newHashDb = HashDbManager.getInstance().addNewHashDatabaseNoSave(hashSetNameTextField.getText(), fileChooser.getSelectedFile().getCanonicalPath(), true, sendIngestMessagesCheckbox.isSelected(), type);
            } catch (IOException ex) {
                Logger.getLogger(HashDbCreateDatabaseDialog.class.getName()).log(Level.WARNING, errorMessage, ex);
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.cannotCreateFileAtLocMsg"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            } catch (HashDbManagerException ex) {
                Logger.getLogger(HashDbCreateDatabaseDialog.class.getName()).log(Level.WARNING, errorMessage, ex);
                JOptionPane.showMessageDialog(this,
                        ex.getMessage(),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            // Check if a hash set with the same name/version already exists
            try{
                if(EamDb.getInstance().referenceSetExists(hashSetNameTextField.getText(), "")){
                    JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.duplicateName"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (EamDbException ex){
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.SEVERE, "Error looking up reference set", ex);
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.databaseLookupError"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;                
            }
            
            try{
                int referenceSetID = EamDb.getInstance().newReferenceSet(new EamGlobalSet(selectedOrg.getOrgID(), hashSetNameTextField.getText(),  
                        "", fileKnown, false, EamDb.getInstance().getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID)));
                newHashDb = HashDbManager.getInstance().addExistingCentralRepoHashSet(hashSetNameTextField.getText(), 
                        "", referenceSetID, 
                        true, sendIngestMessagesCheckbox.isSelected(), type, false);
            } catch (EamDbException | TskCoreException ex){
                Logger.getLogger(HashDbImportDatabaseDialog.class.getName()).log(Level.SEVERE, "Error creating new reference set", ex);
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.databaseCreationError"),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbCreateDatabaseDialog.createHashDbErr"),
                        JOptionPane.ERROR_MESSAGE);
                return;  
            } 
        }

        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private void sendIngestMessagesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendIngestMessagesCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_sendIngestMessagesCheckboxActionPerformed

    private void orgButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_orgButtonActionPerformed
        ManageOrganizationsDialog dialog = new ManageOrganizationsDialog();
        // update the combobox options
        if (dialog.isChanged()) {
            populateCombobox();
        } 
    }//GEN-LAST:event_orgButtonActionPerformed

    private void orgComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_orgComboBoxActionPerformed
        if (null == orgComboBox.getSelectedItem()) return;
        String orgName = this.orgComboBox.getSelectedItem().toString();
        for (EamOrganization org : orgs) {
            if (org.getName().equals(orgName)) {
                selectedOrg = org;
                return;
            }
        }
    }//GEN-LAST:event_orgComboBoxActionPerformed

    private void fileTypeRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileTypeRadioButtonActionPerformed
        enableComponents();
    }//GEN-LAST:event_fileTypeRadioButtonActionPerformed

    private void centralRepoRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_centralRepoRadioButtonActionPerformed
        enableComponents();
    }//GEN-LAST:event_centralRepoRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancelButton;
    private javax.swing.JRadioButton centralRepoRadioButton;
    private javax.swing.JLabel databasePathLabel;
    private javax.swing.JTextField databasePathTextField;
    private javax.swing.JRadioButton fileTypeRadioButton;
    private javax.swing.JTextField hashSetNameTextField;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JRadioButton knownBadRadioButton;
    private javax.swing.JRadioButton knownRadioButton;
    private javax.swing.JLabel lbOrg;
    private javax.swing.JButton okButton;
    private javax.swing.JButton orgButton;
    private javax.swing.JComboBox<String> orgComboBox;
    private javax.swing.JButton saveAsButton;
    private javax.swing.JCheckBox sendIngestMessagesCheckbox;
    private javax.swing.ButtonGroup storageTypeButtonGroup;
    // End of variables declaration//GEN-END:variables
}
