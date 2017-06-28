/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifact;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalFileInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamGlobalSet;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Instances of this class allow a user to select an existing hash database and
 * add it to the set of hash databases used to classify files as unknown, known,
 * or known bad.
 */
final class ImportHashDatabaseDialog extends javax.swing.JDialog {
    private static final Logger LOGGER = Logger.getLogger(ImportHashDatabaseDialog.class.getName());

    private final JFileChooser fileChooser = new JFileChooser();
    private final static String LAST_FILE_PATH_KEY = "CentralRepositoryImport_Path"; // NON-NLS
    private EamOrganization selectedOrg = null;
    private List<EamOrganization> orgs = null;
    private final Collection<JTextField> textBoxes;
    private final TextBoxChangedListener textBoxChangedListener;


    /**
     * Displays a dialog that allows a user to select an existing hash database
     * and add it to the set of hash databases used to classify files as
     * unknown, known, or known bad.
     */
    @Messages({"ImportHashDatabaseDialog.importHashDbMsg=Import Hash Database"})
    ImportHashDatabaseDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                Bundle.ImportHashDatabaseDialog_importHashDbMsg(),
                true); // NON-NLS
        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();
        initFileChooser();
        initComponents();
        customizeComponents();
        display();
    }

    @Messages({"ImportHashDatabaseDialog.fileNameExtFilter.text=Hash Database File",})
    private void initFileChooser() {
        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[]{"idx"}; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                Bundle.ImportHashDatabaseDialog_fileNameExtFilter_text(), 
                EXTENSION); // NON-NLS
        fileChooser.setFileFilter(filter);
        fileChooser.setMultiSelectionEnabled(false);
    }

    private void customizeComponents() {
        populateCombobox();
        setTextBoxListeners();
        enableOkButton(false);        
    }

    /**
     * Register for notifications when the text boxes get updated.
     */
    private void setTextBoxListeners() {
        textBoxes.add(tfFilePath);
        textBoxes.add(tfDatabaseName);
        textBoxes.add(tfDatabaseVersion);
        addDocumentListeners(textBoxes, textBoxChangedListener);
    }

    private void populateCombobox() {
        comboboxSourceOrganization.removeAllItems();
        try {
            EamDb dbManager = EamDb.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                comboboxSourceOrganization.addItem(org.getName());
            });
            if (!orgs.isEmpty()) {
                selectedOrg = orgs.get(0);
            }
            valid();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failure populating combobox with organizations.", ex);
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
     * Tests whether or not values have been entered in all of the required
     * text fields.
     *
     * @return True or false.
     */
    private boolean textFieldsArePopulated() {
        return !tfDatabaseName.getText().trim().isEmpty()
                && !tfDatabaseVersion.getText().trim().isEmpty()
                && !tfFilePath.getText().trim().isEmpty();
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    @Messages({"ImportHashDatabaseDialog.validation.incompleteFields=Fill in all values"})
    private boolean checkFields() {
        boolean result = true;

        boolean allPopulated = textFieldsArePopulated();

        if (!allPopulated) {
            // We don't even have everything filled out
            result = false;
            lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_validation_incompleteFields());
        }
        return result;
    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    @Messages({"ImportHashDatabaseDialog.validation.notEnabled=Database not initialized."})
    public boolean valid() {
        lbWarningMsg.setText("");
        EamDb dbManager = EamDb.getInstance();        
        if (!EamDb.isEnabled()) {
            lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_validation_notEnabled());
            return false;
        }

        return enableOkButton(checkFields() && null != selectedOrg);
    }

    /**
     * Enables the "OK" button to create the Global File Set and insert the instances.
     *
     * @param enable
     *
     * @return True or False
     */
    private boolean enableOkButton(Boolean enable) {
        okButton.setEnabled(enable);
        return enable;
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
    
    private void display() {
        Dimension screenDimension = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screenDimension.width - getSize().width) / 2, (screenDimension.height - getSize().height) / 2);
        setVisible(true);
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
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();
        tfFilePath = new javax.swing.JTextField();
        openButton = new javax.swing.JButton();
        knownRadioButton = new javax.swing.JRadioButton();
        knownBadRadioButton = new javax.swing.JRadioButton();
        lbDatabaseType = new javax.swing.JLabel();
        lbDatabasePath = new javax.swing.JLabel();
        lbDatabaseAttribution = new javax.swing.JLabel();
        lbSourceOrganization = new javax.swing.JLabel();
        lbDatabaseName = new javax.swing.JLabel();
        lbDatabaseVersion = new javax.swing.JLabel();
        comboboxSourceOrganization = new javax.swing.JComboBox<>();
        tfDatabaseName = new javax.swing.JTextField();
        tfDatabaseVersion = new javax.swing.JTextField();
        bnNewOrganization = new javax.swing.JButton();
        lbWarningMsg = new javax.swing.JLabel();
        lbInstructions = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        tfFilePath.setText(org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.tfFilePath.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(openButton, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.openButton.text")); // NOI18N
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(knownRadioButton, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.knownRadioButton.text")); // NOI18N

        buttonGroup1.add(knownBadRadioButton);
        knownBadRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(knownBadRadioButton, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.knownBadRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseType, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbDatabaseType.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabasePath, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbFilePath.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseAttribution, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbDatabaseAttribution.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbSourceOrganization, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbSourceOrganization.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseName, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbDatabaseName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseVersion, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbDatabaseVersion.text")); // NOI18N

        comboboxSourceOrganization.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        comboboxSourceOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboboxSourceOrganizationActionPerformed(evt);
            }
        });

        tfDatabaseName.setToolTipText(org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.tfDatabaseName.tooltip")); // NOI18N

        tfDatabaseVersion.setToolTipText(org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.tfDatabaseVersion.tooltip.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
            }
        });

        lbWarningMsg.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lbWarningMsg.setForeground(new java.awt.Color(255, 0, 0));

        lbInstructions.setFont(new java.awt.Font("Tahoma", 0, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(lbInstructions, org.openide.util.NbBundle.getMessage(ImportHashDatabaseDialog.class, "ImportHashDatabaseDialog.lbInstructions.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbInstructions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(lbDatabasePath)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(tfFilePath))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGap(23, 23, 23)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbDatabaseVersion)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(tfDatabaseVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbSourceOrganization)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(bnNewOrganization))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbDatabaseName)
                                        .addGap(12, 12, 12)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(comboboxSourceOrganization, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(tfDatabaseName))))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(openButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbDatabaseType)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(19, 19, 19)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(knownRadioButton)
                                    .addComponent(knownBadRadioButton)))
                            .addComponent(lbDatabaseAttribution))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(lbWarningMsg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbInstructions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(openButton)
                    .addComponent(tfFilePath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabasePath))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbDatabaseType)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(knownRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(knownBadRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(lbDatabaseAttribution)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(bnNewOrganization)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(3, 3, 3)
                                .addComponent(lbSourceOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(comboboxSourceOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(tfDatabaseName)
                            .addComponent(lbDatabaseName, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(lbDatabaseVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(tfDatabaseVersion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lbWarningMsg, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(44, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(okButton)
                            .addComponent(cancelButton))
                        .addContainerGap())))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    @Messages({"ImportHashDatabaseDialog.failedToGetDbPathMsg=Failed to get the path of the selected database.",})
    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File databaseFile = fileChooser.getSelectedFile();
            try {
                tfFilePath.setText(databaseFile.getCanonicalPath());
                if (databaseFile.getName().toLowerCase().contains("nsrl")) { //NON-NLS
                    knownRadioButton.setSelected(true);
                }
                ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY, databaseFile.getParent());
            } catch (IOException ex) {
                Logger.getLogger(ImportHashDatabaseDialog.class.getName()).log(Level.SEVERE, "Failed to get path of selected database", ex); // NON-NLS
                lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_failedToGetDbPathMsg());
            }
        }
        valid();
    }//GEN-LAST:event_openButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    /**
     * Create the new global set and return the ID number
     * 
     * @return ID number of new global set
     * @throws EamDbException 
     */
    private int createGlobalSet() throws EamDbException {
        EamDb dbManager = EamDb.getInstance();
        EamGlobalSet eamGlobalSet = new EamGlobalSet(
            selectedOrg.getOrgID(),
            tfDatabaseName.getText().trim(),
            tfDatabaseVersion.getText().trim(),
            LocalDate.now());
        return dbManager.newGlobalSet(eamGlobalSet);
    }
    
    @Messages({"ImportHashDatabaseDialog.createGlobalSet.failedMsg.text=Failed to store attribution details.",
        "ImportHashDatabaseDialog.mustSelectHashDbFilePathMsg=Missing hash database file path.",
        "ImportHashDatabaseDialog.hashDbDoesNotExistMsg=The selected hash database does not exist.",
        "# {0} - selected file path", 
        "ImportHashDatabaseDialog.errorMessage.failedToOpenHashDbMsg=Failed to open hash database at {0}.",
})
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        // Note that the error handlers in this method call return without disposing of the 
        // dialog to allow the user to try again, if desired.
        String selectedFilePath = tfFilePath.getText();

        // have valid file path
        if (selectedFilePath.isEmpty()) {
            lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_mustSelectHashDbFilePathMsg());
            return;
        }
        File file = new File(selectedFilePath);
        if (!file.exists()) {
            lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_hashDbDoesNotExistMsg());
            return;
        }
        
        // create global set
        int globalSetID;
        try {
            globalSetID = createGlobalSet();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create global set.", ex);
            lbWarningMsg.setText(Bundle.ImportHashDatabaseDialog_createGlobalSet_failedMsg_text());
            return;
        }
        
        // insert hashes
        EamArtifactInstance.KnownStatus knownStatus = EamArtifactInstance.KnownStatus.UNKNOWN;
        if (knownRadioButton.isSelected()) {
            knownStatus = EamArtifactInstance.KnownStatus.KNOWN;
        } else if (knownBadRadioButton.isSelected()) {
            knownStatus = EamArtifactInstance.KnownStatus.BAD;
        }

        String errorMessage = Bundle.ImportHashDatabaseDialog_errorMessage_failedToOpenHashDbMsg(selectedFilePath);
        // Future, make UI handle more than the "FILES" type.
        EamArtifact.Type contentType = EamArtifact.getDefaultArtifactTypes().get(0); // get "FILES" type
        try {
            // run in the background and close dialog
            SwingUtilities.invokeLater(new ImportHashDatabaseWorker(selectedFilePath, knownStatus, globalSetID, contentType)::execute);
            dispose();
        } catch (EamDbException ex) {
            Logger.getLogger(ImportHashDatabaseDialog.class.getName()).log(Level.SEVERE, errorMessage, ex);
            lbWarningMsg.setText(ex.getMessage());
        }

    }//GEN-LAST:event_okButtonActionPerformed
    
    @SuppressWarnings({"unchecked"})
    private void bnNewOrganizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnNewOrganizationActionPerformed
        AddNewOrganizationDialog dialogO = new AddNewOrganizationDialog();
        // update the combobox options
        if (dialogO.isChanged()) {
            populateCombobox();
        }       
    }//GEN-LAST:event_bnNewOrganizationActionPerformed
    
    @SuppressWarnings({"unchecked"})
    private void comboboxSourceOrganizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboboxSourceOrganizationActionPerformed
        JComboBox<String> cb = (JComboBox<String>)evt.getSource();
        String orgName = (String)cb.getSelectedItem();
        if (null == orgName) return;
        
        for (EamOrganization org : orgs) {
            if (org.getName().equals(orgName)) {
                selectedOrg = org;
                return;
            }
        }
        valid();
    }//GEN-LAST:event_comboboxSourceOrganizationActionPerformed

    @NbBundle.Messages({"ImportHashDatabaseDialog.ImportHashDatabaseWorker.displayName=Importing Hash Database"})
    private class ImportHashDatabaseWorker extends SwingWorker<Void, Void> {

        private final File file;
        private final EamArtifactInstance.KnownStatus knownStatus;
        private final int globalSetID;
        private final ProgressHandle progress;
        private final EamArtifact.Type contentType;

        public ImportHashDatabaseWorker(String filename, EamArtifactInstance.KnownStatus knownStatus, int globalSetID, EamArtifact.Type contentType) throws EamDbException {
            this.file = new File(filename);
            this.knownStatus = knownStatus;
            this.globalSetID = globalSetID;
            this.contentType = contentType;
            this.progress = ProgressHandle.createHandle(Bundle.ImportHashDatabaseDialog_ImportHashDatabaseWorker_displayName());

            if (!EamDb.isEnabled()) {
                throw new EamDbException("Central Repository database is not enabled."); // NON-NLS
            }
        }

        @Override
        protected Void doInBackground() throws Exception {
            importHashDatabase();
            return null;
        }

        @Override
        @Messages({"ImportHashDatabaseDialog.ImportHashDatabaseWorker.error=Failed to import hash database."})
        protected void done() {
            progress.finish();
            try {
                get();
            } catch (InterruptedException | ExecutionException ex) {
                Logger.getLogger(ImportHashDatabaseDialog.class.getName()).log(Level.SEVERE, Bundle.ImportHashDatabaseDialog_ImportHashDatabaseWorker_error(), ex);
                MessageNotifyUtil.Notify.show(Bundle.ImportHashDatabaseDialog_ImportHashDatabaseWorker_error(),
                        ex.getMessage(),
                        MessageNotifyUtil.MessageType.ERROR);
            }
        }
        
        private long numberOfLinesInFile(File f) throws IOException {
            return Files.lines(f.toPath()).count();
        }

        @Messages({"# {0} - value content", 
            "ImportHashDatabaseDialog.ImportHashDatabaseWorker.duplicate=Duplicate value {0} found in import file."})
        private void importHashDatabase() throws EamDbException, IOException {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            EamDb dbManager = EamDb.getInstance();
            Set<EamGlobalFileInstance> globalInstances = new HashSet<>();

            long totalLines = numberOfLinesInFile(file);
            if (totalLines <= Integer.MAX_VALUE) {
                progress.start((int) totalLines);
            } else {
                progress.start();
            }

            int numLines = 0;
            while ((line = reader.readLine()) != null) {
                progress.progress(++numLines);

                String[] parts = line.split("\\|");

                // Header lines start with a 41 character dummy hash, 1 character longer than a SHA-1 hash
                if (parts.length != 2 || parts[0].length() == 41) {
                    continue;
                }

                EamGlobalFileInstance eamGlobalFileInstance = new EamGlobalFileInstance(
                        globalSetID, 
                        parts[0].toLowerCase(), 
                        knownStatus, 
                        "");

                if (!globalInstances.add(eamGlobalFileInstance)) {
                    throw new EamDbException(Bundle.ImportHashDatabaseDialog_ImportHashDatabaseWorker_duplicate(parts[0])); // NON-NLS
                }
            }

            dbManager.bulkInsertGlobalFileInstances(globalInstances, contentType);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnNewOrganization;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox<String> comboboxSourceOrganization;
    private javax.swing.JRadioButton knownBadRadioButton;
    private javax.swing.JRadioButton knownRadioButton;
    private javax.swing.JLabel lbDatabaseAttribution;
    private javax.swing.JLabel lbDatabaseName;
    private javax.swing.JLabel lbDatabasePath;
    private javax.swing.JLabel lbDatabaseType;
    private javax.swing.JLabel lbDatabaseVersion;
    private javax.swing.JLabel lbInstructions;
    private javax.swing.JLabel lbSourceOrganization;
    private javax.swing.JLabel lbWarningMsg;
    private javax.swing.JButton okButton;
    private javax.swing.JButton openButton;
    private javax.swing.JTextField tfDatabaseName;
    private javax.swing.JTextField tfDatabaseVersion;
    private javax.swing.JTextField tfFilePath;
    // End of variables declaration//GEN-END:variables
}
