/*
 * Autopsy
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import java.awt.Desktop;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JFileChooser;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;

/**
 * Settings panel for the GoogleTranslator
 */
public class GoogleTranslatorSettingsPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(GoogleTranslatorSettingsPanel.class.getName());
    private static final String JSON_EXTENSION = "json";
    private static final String DEFUALT_TEST_STRING = "traducción exitoso";  //spanish which should translate to something along the lines of "successful translation"
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
        
        instructionsTextArea.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                if(e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    // Try to display the URL in the user's browswer. 
                    if(Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (IOException | URISyntaxException ex) {
                            logger.log(Level.WARNING, "Failed to display URL in external viewer", ex);
                        }
                    }

                }
            }
        });
    }

    /**
     * Private method to make a temporary translation service given the current
     * settings with unsaved settings.
     *
     * @return A Translate object which is the translation service
     */
    @Messages({"GoogleTranslatorSettingsPanel.errorMessage.fileNotFound=Credentials file not found, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unableToReadCredentials=Unable to read credentials from credentials file, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unableToMakeCredentials=Unable to construct credentials object from credentials file, please set the location to be a valid JSON credentials file.",
        "GoogleTranslatorSettingsPanel.errorMessage.unknownFailureGetting=Failure getting list of supported languages with current credentials file.",})
    private Translate getTemporaryTranslationService() {
        //This method also has the side effect of more or less validating the JSON file which was selected as it is necessary to get the list of target languages
        try {
            InputStream credentialStream;
            try {
                credentialStream = new FileInputStream(credentialsPathField.getText());
            } catch (FileNotFoundException ignored) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_fileNotFound());
                return null;
            }
            Credentials creds;
            try {
                creds = ServiceAccountCredentials.fromStream(credentialStream);
            } catch (IOException ignored) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unableToMakeCredentials());
                return null;
            }
            if (creds == null) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unableToReadCredentials());
                logger.log(Level.WARNING, "Credentials were not successfully made, no translations will be available from the GoogleTranslator");
                return null;
            } else {
                TranslateOptions.Builder builder = TranslateOptions.newBuilder();
                builder.setCredentials(creds);
                builder.setTargetLanguage(targetLanguageCode); //localize the list to the currently selected target language
                warningLabel.setText("");  //clear any previous warning text
                return builder.build().getService();
            }
        } catch (Throwable throwable) {
            //Catching throwables because some of this Google Translate code throws throwables
            warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unknownFailureGetting());
            logger.log(Level.WARNING, "Throwable caught while getting list of supported languages", throwable);
            return null;
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
                List<Language> listSupportedLanguages;
                Translate tempService = getTemporaryTranslationService();
                if (tempService != null) {
                    listSupportedLanguages = tempService.listSupportedLanguages();
                } else {
                    listSupportedLanguages = new ArrayList<>();
                }
                targetLanguageComboBox.removeAllItems();
                if (!listSupportedLanguages.isEmpty()) {
                    listSupportedLanguages.forEach((lang) -> {
                        targetLanguageComboBox.addItem(new LanguageWrapper(lang));
                    });
                    selectLanguageByCode(targetLanguageCode);
                    targetLanguageComboBox.addItemListener(listener);
                    enableControls(true);

                } else {
                    enableControls(false);
                }
            } else {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_noFileSelected());
                enableControls(false);
            }
        } catch (Throwable throwable) {
            warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_unknownFailurePopulating());
            logger.log(Level.WARNING, "Throwable caught while populating list of supported languages", throwable);
            enableControls(false);
        }
    }

    /**
     * Helper method to enable/disable all controls which are dependent on valid
     * credentials having been provided
     *
     * @param enabled true to enable the controls, false to disable them
     */
    private void enableControls(boolean enabled) {
        targetLanguageComboBox.setEnabled(enabled);
        testButton.setEnabled(enabled);
        testResultValueLabel.setEnabled(enabled);
        testUntranslatedTextField.setEnabled(enabled);
        untranslatedLabel.setEnabled(enabled);
        resultLabel.setEnabled(enabled);
    }

    /**
     * Given a language code select the corresponding language in the combo box
     * if it is present
     *
     * @param code language code such as "en" for English
     */
    private void selectLanguageByCode(String code) {
        for (int i = 0; i < targetLanguageComboBox.getModel().getSize(); i++) {
            if (targetLanguageComboBox.getItemAt(i).getLanguageCode().equals(code)) {
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
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.JLabel credentialsLabel = new javax.swing.JLabel();
        credentialsPathField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        targetLanguageComboBox = new javax.swing.JComboBox<>();
        javax.swing.JLabel targetLanguageLabel = new javax.swing.JLabel();
        warningLabel = new javax.swing.JLabel();
        testResultValueLabel = new javax.swing.JLabel();
        resultLabel = new javax.swing.JLabel();
        untranslatedLabel = new javax.swing.JLabel();
        testUntranslatedTextField = new javax.swing.JTextField();
        testButton = new javax.swing.JButton();
        instructionsScrollPane = new javax.swing.JScrollPane();
        instructionsTextArea = new javax.swing.JTextPane();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(credentialsLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.credentialsLabel.text")); // NOI18N
        credentialsLabel.setMaximumSize(new java.awt.Dimension(200, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(17, 12, 0, 0);
        add(credentialsLabel, gridBagConstraints);

        credentialsPathField.setEditable(false);
        credentialsPathField.setMaximumSize(new java.awt.Dimension(700, 22));
        credentialsPathField.setPreferredSize(new java.awt.Dimension(100, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 6;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(14, 7, 0, 0);
        add(credentialsPathField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.browseButton.text")); // NOI18N
        browseButton.setMaximumSize(new java.awt.Dimension(100, 25));
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 9;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(13, 7, 0, 0);
        add(browseButton, gridBagConstraints);

        targetLanguageComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(13, 7, 0, 0);
        add(targetLanguageComboBox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(targetLanguageLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.targetLanguageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(16, 12, 0, 0);
        add(targetLanguageLabel, gridBagConstraints);

        warningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.warningLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(13, 12, 6, 0);
        add(warningLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(testResultValueLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.testResultValueLabel.text")); // NOI18N
        testResultValueLabel.setMaximumSize(new java.awt.Dimension(600, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 7;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 7, 0, 0);
        add(testResultValueLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(resultLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.resultLabel.text")); // NOI18N
        resultLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 0);
        add(resultLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(untranslatedLabel, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.untranslatedLabel.text")); // NOI18N
        untranslatedLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 7, 0, 0);
        add(untranslatedLabel, gridBagConstraints);

        testUntranslatedTextField.setText(DEFUALT_TEST_STRING);
        testUntranslatedTextField.setEnabled(false);
        testUntranslatedTextField.setMinimumSize(new java.awt.Dimension(160, 22));
        testUntranslatedTextField.setPreferredSize(new java.awt.Dimension(160, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 4;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 5, 0, 0);
        add(testUntranslatedTextField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(testButton, org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.testButton.text")); // NOI18N
        testButton.setEnabled(false);
        testButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(6, 12, 0, 0);
        add(testButton, gridBagConstraints);

        instructionsScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        instructionsScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        instructionsTextArea.setEditable(false);
        instructionsTextArea.setBackground(new java.awt.Color(240, 240, 240));
        instructionsTextArea.setContentType("text/html"); // NOI18N
        instructionsTextArea.setText(org.openide.util.NbBundle.getMessage(GoogleTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.instructionsTextArea.text")); // NOI18N
        instructionsTextArea.setMaximumSize(new java.awt.Dimension(1000, 200));
        instructionsTextArea.setPreferredSize(new java.awt.Dimension(164, 78));
        instructionsScrollPane.setViewportView(instructionsTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(13, 12, 0, 0);
        add(instructionsScrollPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 10;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridheight = 5;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 0.6;
        add(filler1, gridBagConstraints);
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
            testResultValueLabel.setText("");
            firePropertyChange("SettingChanged", true, false);
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    @Messages({"GoogleTranslatorSettingsPanel.errorMessage.translationFailure=Translation failure with specified credentials"})
    private void testButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testButtonActionPerformed
        testResultValueLabel.setText("");
        Translate tempTranslate = getTemporaryTranslationService();
        if (tempTranslate != null) {
            try {
                Translation translation = tempTranslate.translate(testUntranslatedTextField.getText());
                testResultValueLabel.setText(translation.getTranslatedText());
                warningLabel.setText("");
            } catch (Exception ex) {
                warningLabel.setText(Bundle.GoogleTranslatorSettingsPanel_errorMessage_translationFailure());
                logger.log(Level.WARNING, Bundle.GoogleTranslatorSettingsPanel_errorMessage_translationFailure(), ex);
            }
        }
    }//GEN-LAST:event_testButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JTextField credentialsPathField;
    private javax.swing.JScrollPane instructionsScrollPane;
    private javax.swing.JTextPane instructionsTextArea;
    private javax.swing.JLabel resultLabel;
    private javax.swing.JComboBox<org.sleuthkit.autopsy.texttranslation.translators.LanguageWrapper> targetLanguageComboBox;
    private javax.swing.JButton testButton;
    private javax.swing.JLabel testResultValueLabel;
    private javax.swing.JTextField testUntranslatedTextField;
    private javax.swing.JLabel untranslatedLabel;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Get the currently selected target language code.
     *
     * @return The target language code of the language selected in the combo
     *         box.
     */
    String getTargetLanguageCode() {
        return targetLanguageCode;
    }

    /**
     * Get the currently set path to the JSON credentials file.
     *
     * @return The path to the credentials file specified in the credentials
     *         field.
     */
    String getCredentialsPath() {
        return credentialsPathField.getText();
    }

    /**
     * Listener to identify when a combo box item has been selected and update
     * the combo box to reflect that selection.
     */
    private class ComboBoxSelectionListener implements ItemListener {

        @Override
        public void itemStateChanged(java.awt.event.ItemEvent evt) {
            String selectedCode = ((LanguageWrapper) targetLanguageComboBox.getSelectedItem()).getLanguageCode();
            if (!StringUtils.isBlank(selectedCode) && !selectedCode.equals(targetLanguageCode)) {
                targetLanguageCode = selectedCode;
                populateTargetLanguageComboBox();
                testResultValueLabel.setText("");
                firePropertyChange("SettingChanged", true, false);
            }
        }
    }
}
