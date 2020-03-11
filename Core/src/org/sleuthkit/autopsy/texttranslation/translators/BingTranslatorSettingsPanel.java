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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;

/**
 * Settings panel for the BingTranslator
 */
public class BingTranslatorSettingsPanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(BingTranslatorSettingsPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private static final String GET_TARGET_LANGUAGES_URL = "https://api.cognitive.microsofttranslator.com/languages?api-version=3.0&scope=translation";
    private static final String DEFUALT_TEST_STRING = "traducción exitoso";  //spanish which should translate to something along the lines of "successful translation"
    private String targetLanguageCode = "";

    /**
     * Creates new form BingTranslatorSettingsPanel
     */
    public BingTranslatorSettingsPanel(String authenticationKey, String code) {
        initComponents();
        authenticationKeyField.setText(authenticationKey);
        authenticationKeyField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                firePropertyChange("SettingChanged", true, false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                firePropertyChange("SettingChanged", true, false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                firePropertyChange("SettingChanged", true, false);
            }

        });
        populateComboBox();
        selectLanguageByCode(code);
        targetLanguageCode = code;
    }

    /**
     * Populate the target language combo box with available target languages
     */
    @Messages({"BingTranslatorSettingsPanel.warning.targetLanguageFailure=Unable to get list of target languages or parse the result that was received"})
    private void populateComboBox() {
        Request get_request = new Request.Builder()
                .url(GET_TARGET_LANGUAGES_URL).build();
        try {
            Response response = new OkHttpClient().newCall(get_request).execute();
            JsonParser parser = new JsonParser();
            String responseBody = response.body().string();
            JsonElement elementBody = parser.parse(responseBody);
            JsonObject asObject = elementBody.getAsJsonObject();
            JsonElement translationElement = asObject.get("translation");
            JsonObject responses = translationElement.getAsJsonObject();
            responses.entrySet().forEach((entry) -> {
                targetLanguageComboBox.addItem(new LanguageWrapper(entry.getKey(), entry.getValue().getAsJsonObject().get("name").getAsString()));
            });
            targetLanguageComboBox.setEnabled(true);
        } catch (IOException | IllegalStateException | ClassCastException | NullPointerException | IndexOutOfBoundsException ex) {
            logger.log(Level.SEVERE, Bundle.BingTranslatorSettingsPanel_warning_targetLanguageFailure(), ex);
            warningLabel.setText(Bundle.BingTranslatorSettingsPanel_warning_targetLanguageFailure());
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
            if (targetLanguageComboBox.getModel().getElementAt(i).getLanguageCode().equals(code)) {
                targetLanguageComboBox.setSelectedIndex(i);
                break;
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

        authenticationKeyField = new javax.swing.JTextField();
        warningLabel = new javax.swing.JLabel();
        testButton = new javax.swing.JButton();
        targetLanguageLabel = new javax.swing.JLabel();
        targetLanguageComboBox = new javax.swing.JComboBox<>();
        testUntranslatedTextField = new javax.swing.JTextField();
        untranslatedLabel = new javax.swing.JLabel();
        resultLabel = new javax.swing.JLabel();
        testResultValueLabel = new javax.swing.JLabel();
        authenticationKeyLabel = new javax.swing.JLabel();
        instructionsScrollPane = new javax.swing.JScrollPane();
        instructionsTextArea = new javax.swing.JTextArea();

        authenticationKeyField.setToolTipText(org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.authenticationKeyField.toolTipText")); // NOI18N

        warningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.warningLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(testButton, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.testButton.text")); // NOI18N
        testButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                testButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(targetLanguageLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.targetLanguageLabel.text")); // NOI18N

        targetLanguageComboBox.setEnabled(false);
        targetLanguageComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                targetLanguageComboBoxSelected(evt);
            }
        });

        testUntranslatedTextField.setText(DEFUALT_TEST_STRING);

        org.openide.awt.Mnemonics.setLocalizedText(untranslatedLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.untranslatedLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(resultLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.resultLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(testResultValueLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.testResultValueLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(authenticationKeyLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.authenticationKeyLabel.text")); // NOI18N

        instructionsScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        instructionsScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        instructionsTextArea.setEditable(false);
        instructionsTextArea.setBackground(new java.awt.Color(240, 240, 240));
        instructionsTextArea.setColumns(20);
        instructionsTextArea.setLineWrap(true);
        instructionsTextArea.setRows(2);
        instructionsTextArea.setText(org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.instructionsTextArea.text")); // NOI18N
        instructionsTextArea.setWrapStyleWord(true);
        instructionsScrollPane.setViewportView(instructionsTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(instructionsScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(authenticationKeyLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(authenticationKeyField, javax.swing.GroupLayout.PREFERRED_SIZE, 486, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(warningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 551, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(targetLanguageLabel)
                                    .addComponent(testButton, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(untranslatedLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(testUntranslatedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(resultLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(testResultValueLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(targetLanguageComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGap(276, 276, 276)))))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(instructionsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(authenticationKeyField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(authenticationKeyLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(targetLanguageLabel)
                    .addComponent(targetLanguageComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(testButton)
                    .addComponent(testUntranslatedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(untranslatedLabel)
                    .addComponent(resultLabel)
                    .addComponent(testResultValueLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(warningLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 18, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    @Messages({"BingTranslatorSettingsPanel.warning.invalidKey=Invalid translation authentication key"})
    private void testButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_testButtonActionPerformed
        if (testTranslationSetup()) {
            warningLabel.setText("");
        } else {
            warningLabel.setText(Bundle.BingTranslatorSettingsPanel_warning_invalidKey());
        }
    }//GEN-LAST:event_testButtonActionPerformed

    private void targetLanguageComboBoxSelected(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_targetLanguageComboBoxSelected
        String selectedCode = ((LanguageWrapper) targetLanguageComboBox.getSelectedItem()).getLanguageCode();
        if (!StringUtils.isBlank(selectedCode) && !selectedCode.equals(targetLanguageCode)) {
            targetLanguageCode = selectedCode;
            firePropertyChange("SettingChanged", true, false);
        }
    }//GEN-LAST:event_targetLanguageComboBoxSelected

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField authenticationKeyField;
    private javax.swing.JLabel authenticationKeyLabel;
    private javax.swing.JScrollPane instructionsScrollPane;
    private javax.swing.JTextArea instructionsTextArea;
    private javax.swing.JLabel resultLabel;
    private javax.swing.JComboBox<LanguageWrapper> targetLanguageComboBox;
    private javax.swing.JLabel targetLanguageLabel;
    private javax.swing.JButton testButton;
    private javax.swing.JLabel testResultValueLabel;
    private javax.swing.JTextField testUntranslatedTextField;
    private javax.swing.JLabel untranslatedLabel;
    private javax.swing.JLabel warningLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Attempts to translate the text specified in the Untranslated field using
     * the settings currently specified but not necessarily saved
     *
     * @return true if the translation was able to be performed, false otherwise
     *
     */
    private boolean testTranslationSetup() {
        testResultValueLabel.setText("");
        MediaType mediaType = MediaType.parse("application/json");
        JsonArray jsonArray = new JsonArray();
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("Text", testUntranslatedTextField.getText());
        jsonArray.add(jsonObject);
        String bodyString = jsonArray.toString();

        RequestBody body = RequestBody.create(mediaType,
                bodyString);
        Request request = new Request.Builder()
                .url(BingTranslator.getTranlatorUrl(targetLanguageCode)).post(body)
                .addHeader("Ocp-Apim-Subscription-Key", authenticationKeyField.getText())
                .addHeader("Content-type", "application/json").build();
        try {
            Response response = new OkHttpClient().newCall(request).execute();
            JsonParser parser = new JsonParser();
            JsonArray responses = parser.parse(response.body().string()).getAsJsonArray();
            //As far as I know, there's always exactly one item in the array.
            JsonObject response0 = responses.get(0).getAsJsonObject();
            JsonArray translations = response0.getAsJsonArray("translations");
            JsonObject translation0 = translations.get(0).getAsJsonObject();
            testResultValueLabel.setText(translation0.get("text").getAsString());
            return true;
        } catch (IOException | IllegalStateException | ClassCastException | NullPointerException | IndexOutOfBoundsException e) {
            logger.log(Level.WARNING, "Test of Bing Translator failed due to exception", e);
            return false;
        }
    }

    /**
     * Get the currently set authentication key to be used for the Microsoft
     * translation service
     *
     * @return the authentication key specified in the textarea
     */
    String getAuthenticationKey() {
        return authenticationKeyField.getText();
    }

    /**
     * Get the currently selected target language code
     *
     * @return the target language code of the language selected in the combobox
     */
    String getTargetLanguageCode() {
        return targetLanguageCode;
    }
}
