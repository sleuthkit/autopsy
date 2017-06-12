/*
 * Enterprise Artifact Manager
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.optionspanel;

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
import java.util.List;
import java.util.logging.Level;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamArtifactInstance;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamGlobalFileInstance;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamGlobalSet;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamOrganization;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDb;

/**
 * Instances of this class allow a user to select an existing hash database and
 * add it to the set of hash databases used to classify files as unknown, known,
 * or known bad.
 */
final class EamImportDatabaseDialog extends javax.swing.JDialog {
    private static final Logger LOGGER = Logger.getLogger(EamImportDatabaseDialog.class.getName());

    private final JFileChooser fileChooser = new JFileChooser();
    private final static String LAST_FILE_PATH_KEY = "EnterpriseArtifactManagerImport_Path"; // NON-NLS
    private EamOrganization selectedOrg = null;
    private List<EamOrganization> orgs = null;
    private final Collection<JTextField> textBoxes;
    private final TextBoxChangedListener textBoxChangedListener;


    /**
     * Displays a dialog that allows a user to select an existing hash database
     * and add it to the set of hash databases used to classify files as
     * unknown, known, or known bad.
     */
    EamImportDatabaseDialog() {
        super((JFrame) WindowManager.getDefault().getMainWindow(),
                NbBundle.getMessage(EamImportDatabaseDialog.class, "EnterpriseArtifactManagerImportDatabaseDialog.importHashDbMsg"),
                true); // NON-NLS
        textBoxes = new ArrayList<>();
        textBoxChangedListener = new TextBoxChangedListener();
        initFileChooser();
        initComponents();
        customizeComponents();
        display();
    }

    private void initFileChooser() {
        fileChooser.setDragEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        String[] EXTENSION = new String[]{"idx"}; //NON-NLS
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "EnterpriseArtifactManagerImportDatabaseDialog.fileNameExtFilter.text"), EXTENSION); // NON-NLS
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
        textBoxes.add(tfDatasetName);
        textBoxes.add(tfDatasetVersion);
        addDocumentListeners(textBoxes, textBoxChangedListener);
    }

    private void populateCombobox() {
        comboboxSourceOrganization.removeAllItems();
        try {
            EamDb dbManager = EamDb.getInstance();
            orgs = dbManager.getOrganizations();
            orgs.forEach((org) -> {
                comboboxSourceOrganization.addItem(org.getName());
                selectedOrg = orgs.get(0);
            });
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
        return !tfDatasetName.getText().trim().isEmpty()
                && !tfDatasetVersion.getText().trim().isEmpty()
                && !databasePathTextField.getText().trim().isEmpty();
    }

    /**
     * Tests whether or not all of the settings components are populated.
     *
     * @return True or false.
     */
    @Messages({"EnterpriseArtifactManagerImportDatabaseDialog.validation.incompleteFields=Fill in all values"})
    private boolean checkFields() {
        boolean result = true;

        boolean allPopulated = textFieldsArePopulated();

        if (!allPopulated) {
            // We don't even have everything filled out
            result = false;
            lbWarningMsg.setText(Bundle.EnterpriseArtifactManagerImportDatabaseDialog_validation_incompleteFields());
        }
        return result;
    }

    /**
     * Validates that the form is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    @Messages({"EnterpriseArtifactManagerImportDatabaseDialog.validation.notEnabled=Database not initialized. Restart Autopsy."})
    public boolean valid() {
        lbWarningMsg.setText("");
        EamDb dbManager = EamDb.getInstance();        
        if (!dbManager.isEnabled()) {
            lbWarningMsg.setText(Bundle.EnterpriseArtifactManagerImportDatabaseDialog_validation_notEnabled());
            return false;
        }

        return enableOkButton(checkFields());
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
        databasePathTextField = new javax.swing.JTextField();
        openButton = new javax.swing.JButton();
        knownRadioButton = new javax.swing.JRadioButton();
        knownBadRadioButton = new javax.swing.JRadioButton();
        lbDatabaseType = new javax.swing.JLabel();
        lbDatabasePath = new javax.swing.JLabel();
        lbDatasetAttribution = new javax.swing.JLabel();
        lbSourceOrganization = new javax.swing.JLabel();
        lbDatasetName = new javax.swing.JLabel();
        lbDatasetVersion = new javax.swing.JLabel();
        comboboxSourceOrganization = new javax.swing.JComboBox<>();
        tfDatasetName = new javax.swing.JTextField();
        tfDatasetVersion = new javax.swing.JTextField();
        bnNewOrganization = new javax.swing.JButton();
        lbWarningMsg = new javax.swing.JLabel();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(okButton, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        databasePathTextField.setText(org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.databasePathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(openButton, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.openButton.text")); // NOI18N
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(knownRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(knownRadioButton, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.knownRadioButton.text")); // NOI18N

        buttonGroup1.add(knownBadRadioButton);
        knownBadRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(knownBadRadioButton, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.knownBadRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabaseType, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbDatabaseType.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatabasePath, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbDatabasePath.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatasetAttribution, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbDatasetAttribution.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbSourceOrganization, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbSourceOrganization.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatasetName, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbDatasetName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDatasetVersion, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.lbDatasetVersion.text")); // NOI18N

        comboboxSourceOrganization.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
        comboboxSourceOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboboxSourceOrganizationActionPerformed(evt);
            }
        });

        tfDatasetName.setToolTipText(org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EnterpriseArtifactManagerImportDatabaseDialog.tfDatasetName.tooltip")); // NOI18N

        tfDatasetVersion.setToolTipText(org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EnterpriseArtifactManagerImportDatabaseDialog.tfDatasetVersion.tooltip.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnNewOrganization, org.openide.util.NbBundle.getMessage(EamImportDatabaseDialog.class, "EamImportDatabaseDialog.bnNewOrganization.text")); // NOI18N
        bnNewOrganization.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewOrganizationActionPerformed(evt);
            }
        });

        lbWarningMsg.setFont(new java.awt.Font("Tahoma", 1, 12)); // NOI18N
        lbWarningMsg.setForeground(new java.awt.Color(255, 0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(lbWarningMsg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(okButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbDatabaseType)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(19, 19, 19)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(knownRadioButton)
                                    .addComponent(knownBadRadioButton)))
                            .addComponent(lbDatasetAttribution))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(lbDatabasePath)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(databasePathTextField))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGap(23, 23, 23)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbDatasetVersion)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(tfDatasetVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 154, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(0, 0, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbSourceOrganization)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(bnNewOrganization))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(lbDatasetName)
                                        .addGap(12, 12, 12)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(comboboxSourceOrganization, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                            .addComponent(tfDatasetName))))))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(openButton)))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, okButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(openButton)
                    .addComponent(databasePathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbDatabasePath))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lbDatabaseType)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(knownBadRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(lbDatasetAttribution)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(bnNewOrganization)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(lbSourceOrganization, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(comboboxSourceOrganization, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(tfDatasetName)
                    .addComponent(lbDatasetName, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDatasetVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tfDatasetVersion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lbWarningMsg, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(okButton)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File databaseFile = fileChooser.getSelectedFile();
            try {
                databasePathTextField.setText(databaseFile.getCanonicalPath());
                if (databaseFile.getName().toLowerCase().contains("nsrl")) { //NON-NLS
                    knownRadioButton.setSelected(true);
                }
                ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_FILE_PATH_KEY, databaseFile.getParent());
            } catch (IOException ex) {
                Logger.getLogger(EamImportDatabaseDialog.class.getName()).log(Level.SEVERE, "Failed to get path of selected database", ex); // NON-NLS
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(),
                                "EnterpriseArtifactManagerImportDatabaseDialog.failedToGetDbPathMsg")); // NON-NLS
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
            tfDatasetName.getText().trim(),
            tfDatasetVersion.getText().trim(),
            LocalDate.now());
        return dbManager.newGlobalSet(eamGlobalSet);
    }
    
    @Messages({"EnterpriseArtifactManagerImportDatabaseDialog.createGlobalSet.failedMsg.text=Failed to store attribution details.",
        "EnterpriseArtifactManagerImportDatabaseDialog.createGlobalSet.failedTitle.text=Import hashdb error."})
    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        // Note that the error handlers in this method call return without disposing of the 
        // dialog to allow the user to try again, if desired.
        String selectedFilePath = databasePathTextField.getText();

        // have valid file path
        if (selectedFilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "EnterpriseArtifactManagerImportDatabaseDialog.mustSelectHashDbFilePathMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "EnterpriseArtifactManagerImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE); // NON-NLS
            return;
        }
        File file = new File(selectedFilePath);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "EnterpriseArtifactManagerImportDatabaseDialog.hashDbDoesNotExistMsg"),
                    NbBundle.getMessage(this.getClass(),
                            "EnterpriseArtifactManagerImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE); // NON-NLS
            return;
        }
        
        // create global set
        int globalSetID = -1;
        try {
            globalSetID = createGlobalSet();
        } catch (EamDbException ex) {
            JOptionPane.showMessageDialog(this,
                    Bundle.EnterpriseArtifactManagerImportDatabaseDialog_createGlobalSet_failedMsg_text(),
                    Bundle.EnterpriseArtifactManagerImportDatabaseDialog_createGlobalSet_failedTitle_text(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // insert hashes
        EamArtifactInstance.KnownStatus knownStatus = EamArtifactInstance.KnownStatus.UNKNOWN;
        if (knownRadioButton.isSelected()) {
            knownStatus = EamArtifactInstance.KnownStatus.KNOWN;
        } else if (knownBadRadioButton.isSelected()) {
            knownStatus = EamArtifactInstance.KnownStatus.BAD;
        }

        String errorMessage = NbBundle.getMessage(this.getClass(),
                "EnterpriseArtifactManagerImportDatabaseDialog.errorMessage.failedToOpenHashDbMsg",
                selectedFilePath); // NON-NLS
        try {
            new ImportHashDatabaseWorker(selectedFilePath, knownStatus, globalSetID).execute();
        } catch (Throwable ex) {
            Logger.getLogger(EamImportDatabaseDialog.class.getName()).log(Level.WARNING, errorMessage, ex);
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    NbBundle.getMessage(this.getClass(),
                            "EnterpriseArtifactManagerImportDatabaseDialog.importHashDbErr"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        dispose();
    }//GEN-LAST:event_okButtonActionPerformed
    
    @SuppressWarnings({"unchecked"})
    private void bnNewOrganizationActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnNewOrganizationActionPerformed
        EamAddNewOrganizationDialog dialogO = new EamAddNewOrganizationDialog();
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
    }//GEN-LAST:event_comboboxSourceOrganizationActionPerformed

    @NbBundle.Messages({"EnterpriseArtifactManagerImportDatabaseDialog.ImportHashDatabaseWorker.displayName=Importing Hash Database"})
    private class ImportHashDatabaseWorker extends SwingWorker<Object, Void> {

        private final EamDb dbManager;
        private final File file;
        private final EamArtifactInstance.KnownStatus knownStatus;
        private final int globalSetID;

        public ImportHashDatabaseWorker(String filename, EamArtifactInstance.KnownStatus knownStatus, int globalSetID) throws EamDbException, UnknownHostException {
            this.dbManager = EamDb.getInstance();
            this.file = new File(filename);
            this.knownStatus = knownStatus;
            this.globalSetID = globalSetID;

            if (!dbManager.isEnabled()) {
                throw new EamDbException("Enterprise artifact manager database settings were not properly initialized"); // NON-NLS
            }
        }

        @Override
        protected Object doInBackground() throws Exception {
            ProgressHandle progress = ProgressHandle.createHandle(Bundle.EnterpriseArtifactManagerImportDatabaseDialog_ImportHashDatabaseWorker_displayName());
            importHashDatabase(progress);
            return null;
        }

        private long numberOfLinesInFile(File f) throws IOException {
            return Files.lines(f.toPath()).count();
        }

        private void importHashDatabase(ProgressHandle progress) throws EamDbException, IOException {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;

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

                dbManager.prepareGlobalFileInstance(eamGlobalFileInstance);
            }

            dbManager.bulkInsertGlobalFileInstances();
            progress.finish();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnNewOrganization;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JButton cancelButton;
    private javax.swing.JComboBox<String> comboboxSourceOrganization;
    private javax.swing.JTextField databasePathTextField;
    private javax.swing.JRadioButton knownBadRadioButton;
    private javax.swing.JRadioButton knownRadioButton;
    private javax.swing.JLabel lbDatabasePath;
    private javax.swing.JLabel lbDatabaseType;
    private javax.swing.JLabel lbDatasetAttribution;
    private javax.swing.JLabel lbDatasetName;
    private javax.swing.JLabel lbDatasetVersion;
    private javax.swing.JLabel lbSourceOrganization;
    private javax.swing.JLabel lbWarningMsg;
    private javax.swing.JButton okButton;
    private javax.swing.JButton openButton;
    private javax.swing.JTextField tfDatasetName;
    private javax.swing.JTextField tfDatasetVersion;
    // End of variables declaration//GEN-END:variables
}
