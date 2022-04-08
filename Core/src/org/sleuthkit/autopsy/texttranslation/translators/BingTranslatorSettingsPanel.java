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
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.Desktop;
import java.net.URISyntaxException;
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
                testResultValueLabel.setText("");
                firePropertyChange("SettingChanged", true, false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                testResultValueLabel.setText("");
                firePropertyChange("SettingChanged", true, false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                testResultValueLabel.setText("");
                firePropertyChange("SettingChanged", true, false);
            }

        });
        populateComboBox();
        selectLanguageByCode(code);
        targetLanguageCode = code;
        
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
     * Populate the target language combo box with available target languages
     */
    @Messages({"BingTranslatorSettingsPanel.warning.targetLanguageFailure=Unable to get list of target languages or parse the result that was received"})
    private void populateComboBox() {
        Request get_request = new Request.Builder()
                .url(GET_TARGET_LANGUAGES_URL).build();
        try {
            Response response = new OkHttpClient().newCall(get_request).execute();
            String responseBody = response.body().string();
            JsonElement elementBody = JsonParser.parseString(responseBody);
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
        java.awt.GridBagConstraints gridBagConstraints;

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
        instructionsTextArea = new javax.swing.JTextPane();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));

        setLayout(new java.awt.GridBagLayout());

        authenticationKeyField.setToolTipText(org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.authenticationKeyField.toolTipText")); // NOI18N
        authenticationKeyField.setMaximumSize(new java.awt.Dimension(800, 22));
        authenticationKeyField.setPreferredSize(new java.awt.Dimension(163, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 7;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(14, 5, 0, 12);
        add(authenticationKeyField, gridBagConstraints);

        warningLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(warningLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "GoogleTranslatorSettingsPanel.warningLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 10;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(7, 12, 6, 0);
        add(warningLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(testButton, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.testButton.text")); // NOI18N
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

        org.openide.awt.Mnemonics.setLocalizedText(targetLanguageLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.targetLanguageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 12, 0, 0);
        add(targetLanguageLabel, gridBagConstraints);

        targetLanguageComboBox.setEnabled(false);
        targetLanguageComboBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                targetLanguageComboBoxSelected(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(8, 5, 0, 0);
        add(targetLanguageComboBox, gridBagConstraints);

        testUntranslatedTextField.setText(DEFUALT_TEST_STRING);
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

        org.openide.awt.Mnemonics.setLocalizedText(untranslatedLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.untranslatedLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 0);
        add(untranslatedLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(resultLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.resultLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 6;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 0);
        add(resultLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(testResultValueLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.testResultValueLabel.text")); // NOI18N
        testResultValueLabel.setMaximumSize(new java.awt.Dimension(600, 22));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 7;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(4, 7, 0, 12);
        add(testResultValueLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(authenticationKeyLabel, org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.authenticationKeyLabel.text")); // NOI18N
        authenticationKeyLabel.setMaximumSize(new java.awt.Dimension(200, 16));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(16, 12, 0, 0);
        add(authenticationKeyLabel, gridBagConstraints);

        instructionsScrollPane.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        instructionsScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        instructionsScrollPane.setPreferredSize(new java.awt.Dimension(168, 80));

        instructionsTextArea.setEditable(false);
        instructionsTextArea.setBackground(new java.awt.Color(240, 240, 240));
        instructionsTextArea.setContentType("text/html"); // NOI18N
        instructionsTextArea.setText(org.openide.util.NbBundle.getMessage(BingTranslatorSettingsPanel.class, "BingTranslatorSettingsPanel.instructionsTextArea.text_1")); // NOI18N
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
            testResultValueLabel.setText("");
            firePropertyChange("SettingChanged", true, false);
        }
    }//GEN-LAST:event_targetLanguageComboBoxSelected

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField authenticationKeyField;
    private javax.swing.JLabel authenticationKeyLabel;
    private javax.swing.JScrollPane instructionsScrollPane;
    private javax.swing.JTextPane instructionsTextArea;
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
            JsonArray responses = JsonParser.parseString(response.body().string()).getAsJsonArray();
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
