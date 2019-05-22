/*
 * Autopsy
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.texttranslation.translators;

import com.google.auth.Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.translate.Language;
import com.google.cloud.translate.TranslateOptions;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;

/**
 * Settings panel for the GoogleTranslator
 */
public class GoogleTranslatorSettingsPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(GoogleTranslatorSettingsPanel.class.getName());
    private static final String JSON_EXTENSION = "json";
    private static final long serialVersionUID = 1L;
    private final ItemListener listener = new ComboBoxSelectionListener();
    private String targetLanguageCode = "";

    /**
     * Creates new form GoogleTranslatorSettingsPanel
     */
    public GoogleTranslatorSettingsPanel(String credentialsPath, String languageCode) {
        initComponents();
        targetLanguageCode = languageCode;
        credentialsPathField.setText(credentialsPath);
        populateTargetLanguageComboBox();
    }

    /**
     * Private method to make a temporary translation service given the current
     * settings and use it to retrieve the available target languages for
     * population of combobox with target language with unsaved settings.
     *
     * @return A list of Languages
     */
    @Messages({"GoogleTranslatorSettingsPanel.errorMessage.fileNotFound=Credentials file not found, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unableToReadCredentials=Unable to read credentials from credentials file, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unableToMakeCredentials=Unable to construct credentials object from credentials file, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unknownFailureGetting=Failure getting list of supported languages with current credentials file.",})
    private List<Language> getListOfTargetLanguages() {
        //This method also has the side effect of more or less validating the JSON file which was selected as it is necessary to get the list of target languages
        try {
            InputStream credentialStream;
            Credentials creds;
            try {
                credentialStream = new FileInputStream(credentialsPathField.getText());
            } catch (FileNotFoundException ignored) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_fileNotFound());
                return new ArrayList<>();
            }
            try {
                creds = ServiceAccountCredentials.fromStream(credentialStream);
            } catch (IOException ignored) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unableToMakeCredentials());
                return new ArrayList<>();
            }
            if (creds == null) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unableToReadCredentials());
                logger.log(Level.WARNING, "Credentials were not successfully made, no translations will be available from the GoogleTranslator");
                return new ArrayList<>();
            } else {
                TranslateOptions.Builder builder = TranslateOptions.newBuilder();
                builder.setCredentials(creds);
                builder.setTargetLanguage(targetLanguageCode); //localize the list to the currently selected target language
                warningLabel.setText("");  //clear any previous warning text
                return builder.build().getService().listSupportedLanguages();
            }
        } catch (Throwable throwable) {
            warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unknownFailureGetting());
            logger.log(Level.WARNING, "Throwable caught while getting list of supported languages", throwable);
            return new ArrayList<>();
        }
    }

    /**
     * Populate the target language selection combo box
     */
    @Messages({"GoogleTranslatorSettingsPanel.errorMessage.noFileSelected=A JSON file must be selected to provide your credentials for Google Translate.",
        "GoogleTranslatorSettingsPanel.errorMessage.unknownFailurePopulating=Failure populating list of supported languages with current credentials file."})
    private void populateTargetLanguageComboBox() {
        targetLanguageComboBox.removeItemListener(listener);
        try {
            if (!StringUtils.isBlank(credentialsPathField.getText())) {
                List<Language> listSupportedLanguages = getListOfTargetLanguages();
                targetLanguageComboBox.removeAllItems();
                if (!listSupportedLanguages.isEmpty()) {
                    listSupportedLanguages.forEach((lang) -> {
                        targetLanguageComboBox.addItem(new GoogleLanguageWrapper(lang));
                    });
                    selectLanguageByCode(targetLanguageCode);
                    targetLanguageComboBox.addItemListener(listener);
                    targetLanguageComboBox.setEnabled(true);
                } else {
                    targetLanguageComboBox.setEnabled(false);
                }
            } else {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_noFileSelected());
                targetLanguageComboBox.setEnabled(false);
            }
        } catch (Throwable throwable) {
            warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unknownFailurePopulating());
            logger.log(Level.WARNING, "Throwable caught while populating list of supported languages", throwable);
            targetLanguageComboBox.setEnabled(false);
        }
    }

    /**
     * Given a language code select the corresponding language in the combo box
     * if it is present
     *
     * @param code language code such as "en" for English
     */
    private void selectLanguageByCode(String code) {
        for (int i = 0; i < targetLanguageComboBox.getModel().getSize(); i++) {
            if (targetLanguageComboBox.getItemAt(i).getLanguage().getCode().equals(code)) {
                targetLanguageComboBox.setSelectedIndex(i);
                return;
            }
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

        credentialsLabel = new javax.swing.JLabel();
        credentialsPathField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        targetLanguageComboBox = new javax.swing.JComboBox<>();
        jLabel1 = new javax.swing.JLabel();
        warningLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(credentialsLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.credentialsLabel.text")); // NOI18N

        credentialsPathField.setEditable(false);

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        targetLanguageComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.jLabel1.text")); // NOI18N

        warningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.warningLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(warningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 551, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(credentialsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(credentialsPathField, javax.swing.GroupLayout.DEFAULT_SIZE, 443, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(browseButton)
                                .addGap(14, 14, 14))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(targetLanguageComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 317, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE))))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(credentialsLabel)
                    .addComponent(credentialsPathField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(targetLanguageComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(warningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(23, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    @Messages({"GoogleTranslatorSettingsPanel.json.description=JSON Files",
        "GoogleTranslatorSettingsPanel.fileChooser.confirmButton=Select"})
    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDragEnabled(false);
        //if they previously had a path set, start navigation there
        if (!StringUtils.isBlank(credentialsPathField.getText())) {
            fileChooser.setCurrentDirectory(new File(credentialsPathField.getText()).getParentFile());
        }
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileFilter(new FileNameExtensionFilter(Bundle.GoogleTranslatorSettingsPanel_json_description(), JSON_EXTENSION));
        int dialogResult = fileChooser.showDialog(this, Bundle.GoogleTranslatorSettingsPanel_fileChooser_confirmButton());
        if (dialogResult == JFileChooser.APPROVE_OPTION) {
            credentialsPathField.setText(fileChooser.getSelectedFile().getPath());
            populateTargetLanguageComboBox();
            firePropertyChange("SettingChanged", true, false);
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel credentialsLabel;
    private javax.swing.JTextField credentialsPathField;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JComboBox<org.sleuthkit.autopsy.texttranslation.translators.GoogleLanguageWrapper> targetLanguageComboBox;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Get the currently selected target language code
     *
     * @return the target language code of the language selected in the combobox
     */
    String getTargetLanguageCode() {
        return targetLanguageCode;
    }

    /**
     * Get the currently set path to the JSON credentials file
     *
     * @return the path to the credentials file specified in the textarea
     */
    String getCredentialsPath() {
        return credentialsPathField.getText();
    }

    /**
     * Listener to identfy when a combo box item has been selected and update
     * the combo box to reflect that
     */
    private class ComboBoxSelectionListener implements ItemListener {

        @Override
        public void itemStateChanged(java.awt.event.ItemEvent evt) {
            String selectedCode = ((GoogleLanguageWrapper) targetLanguageComboBox.getSelectedItem()).getLanguage().getCode();
            if (!StringUtils.isBlank(selectedCode) && !selectedCode.equals(targetLanguageCode)) {
                targetLanguageCode = selectedCode;
                populateTargetLanguageComboBox();
                firePropertyChange("SettingChanged", true, false);
            }
        }
    }
}
