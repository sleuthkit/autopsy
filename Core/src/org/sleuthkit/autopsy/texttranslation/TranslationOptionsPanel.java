/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.texttranslation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.swing.JLabel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Options panel to display translation options
 */
final class TranslationOptionsPanel extends javax.swing.JPanel {

    private final static Logger logger = Logger.getLogger(TranslationOptionsPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final TranslationOptionsPanelController controller;
    private String currentSelection = "";

    /**
     * Creates new form TranslationOptionsPanel
     */
    @Messages({"TranslationOptionsPanel.translationDisabled.text=Translation disabled"})
    TranslationOptionsPanel(TranslationOptionsPanelController theController) {
        initComponents();
        controller = theController;
        translatorComboBox.addItem(Bundle.TranslationOptionsPanel_translationDisabled_text());
        TextTranslationService.getInstance().getTranslators().forEach((translator) -> {
            translatorComboBox.addItem(translator.getName());
        });
        translatorComboBox.setEnabled(translatorComboBox.getItemCount() > 1);
        load();
    }

    /**
     * Private helper method to update the panel if the selected TextTranslator
     * has changed
     */
    private void updatePanel() {
        if (!currentSelection.equals(translatorComboBox.getSelectedItem().toString())) {
            currentSelection = translatorComboBox.getSelectedItem().toString();
            loadSelectedPanelSettings();
            controller.changed();
        }
    }

    /**
     * Load the settings for the selected TextTranslator's panel
     */
    @Messages({"TranslationOptionsPanel.textTranslatorsUnavailable.text=Unable to get selected text translator, translation is disabled.",
        "TranslationOptionsPanel.noTextTranslatorSelected.text=No text translator selected, translation is disabled.",
        "TranslationOptionsPanel.noTextTranslators.text=No text translators exist, translation is disabled."})
    private void loadSelectedPanelSettings() {
        translationServicePanel.removeAll();
        if (translatorComboBox.getSelectedItem() != null && !translatorComboBox.getSelectedItem().toString().equals(Bundle.TranslationOptionsPanel_translationDisabled_text())) {
            try {
                Component panel = TextTranslationService.getInstance().getTranslatorByName(translatorComboBox.getSelectedItem().toString()).getSettingsPanel();
                panel.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        controller.changed();
                    }
                });
                translationServicePanel.add(panel, BorderLayout.PAGE_START);
            } catch (NoServiceProviderException ex) {
                logger.log(Level.WARNING, "Unable to get TextExtractor named: " + translatorComboBox.getSelectedItem().toString(), ex);
                JLabel label = new JLabel(Bundle.TranslationOptionsPanel_textTranslatorsUnavailable_text());
                label.setForeground(Color.RED);
                translationServicePanel.add(label, BorderLayout.PAGE_START);
            }
        } else {
            if (translatorComboBox.getItemCount() < 2) {
                translationServicePanel.add(new JLabel(Bundle.TranslationOptionsPanel_noTextTranslators_text()), BorderLayout.PAGE_START);
            } else {
                translationServicePanel.add(new JLabel(Bundle.TranslationOptionsPanel_noTextTranslatorSelected_text()), BorderLayout.PAGE_START);
            }
        }
        revalidate();
        repaint();
    }

    /**
     * Load settings from user preferences and update the UI
     */
    final void load() {
        currentSelection = UserPreferences.getTextTranslatorName();
        if (currentSelection == null) {
            currentSelection = Bundle.TranslationOptionsPanel_translationDisabled_text();
        }
        translatorComboBox.setSelectedItem(currentSelection);
        loadSelectedPanelSettings();
        enableOcrCheckBox.setSelected(UserPreferences.getUseOcrInTranslation());
    }

    /**
     * Save the current settings
     */
    void store() {
        //The current text translator name is saved to user preferences
        UserPreferences.setTextTranslatorName(currentSelection);
        //The TextTranslationService updates the TextTranslator in use from user preferences
        TextTranslationService.getInstance().updateSelectedTranslator();
        if (currentSelection != null && !currentSelection.equals(Bundle.TranslationOptionsPanel_translationDisabled_text())) {
            try {
                TextTranslationService.getInstance().getTranslatorByName(currentSelection).saveSettings();
            } catch (NoServiceProviderException | TranslationConfigException ex) {
                logger.log(Level.WARNING, "Unable to save settings for TextTranslator named: " + currentSelection, ex);
            }
        }
        // Save whether OCR is enabled in the content viewer
        UserPreferences.setUseOcrInTranslation(enableOcrCheckBox.isSelected());

    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        translatorComboBox = new javax.swing.JComboBox<>();
        translationServiceLabel = new javax.swing.JLabel();
        translationServicePanel = new javax.swing.JPanel();
        translationOptionsDescription = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        enableOcrCheckBox = new javax.swing.JCheckBox();

        translatorComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                translatorComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(translationServiceLabel, org.openide.util.NbBundle.getMessage(TranslationOptionsPanel.class, "TranslationOptionsPanel.translationServiceLabel.text")); // NOI18N

        translationServicePanel.setLayout(new java.awt.BorderLayout());

        org.openide.awt.Mnemonics.setLocalizedText(translationOptionsDescription, org.openide.util.NbBundle.getMessage(TranslationOptionsPanel.class, "TranslationOptionsPanel.translationOptionsDescription.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(enableOcrCheckBox, org.openide.util.NbBundle.getMessage(TranslationOptionsPanel.class, "TranslationOptionsPanel.enableOcrCheckBox.text")); // NOI18N
        enableOcrCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableOcrCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSeparator1)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(translationServicePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(translationOptionsDescription, javax.swing.GroupLayout.DEFAULT_SIZE, 462, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(translationServiceLabel)
                                .addGap(10, 10, 10)
                                .addComponent(translatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 214, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(enableOcrCheckBox))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(translationOptionsDescription, javax.swing.GroupLayout.PREFERRED_SIZE, 15, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(translatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(translationServiceLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(translationServicePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 10, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(enableOcrCheckBox)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void translatorComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_translatorComboBoxActionPerformed
        updatePanel();
    }//GEN-LAST:event_translatorComboBoxActionPerformed

    private void enableOcrCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableOcrCheckBoxActionPerformed
        controller.changed();
    }//GEN-LAST:event_enableOcrCheckBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox enableOcrCheckBox;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JLabel translationOptionsDescription;
    private javax.swing.JLabel translationServiceLabel;
    private javax.swing.JPanel translationServicePanel;
    private javax.swing.JComboBox<String> translatorComboBox;
    // End of variables declaration//GEN-END:variables

}