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
package org.sleuthkit.autopsy.texttranslation.ui;

import java.awt.Color;
import java.awt.Component;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.logging.Level;
import javax.swing.JLabel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.texttranslation.NoServiceProviderException;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.autopsy.coreutils.Logger;

public class TranslationOptionsPanel extends javax.swing.JPanel {

    private final static Logger logger = Logger.getLogger(TranslationOptionsPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private final TranslationOptionsPanelController controller;
    private String currentSelection = "";

    /**
     * Creates new form TranslationOptionsPanel
     */
    @Messages({"TranslationOptionsPanel.translationDisabled.text=Disable translation"})
    public TranslationOptionsPanel(TranslationOptionsPanelController theController) {
        initComponents();
        controller = theController;
        translatorComboBox.addItem(Bundle.TranslationOptionsPanel_translationDisabled_text());
        TextTranslationService.getInstance().getTranslators().forEach((translator) -> {
            translatorComboBox.addItem(translator.getName());
        });
        translatorComboBox.setEnabled(translatorComboBox.getItemCount() > 1);
        load();
    }

    private void updatePanel() {
        if (!currentSelection.equals(translatorComboBox.getSelectedItem().toString())) {
            loadSelectedPanelSettings();
            controller.changed();
        }
    }

    @Messages({"TranslationOptionsPanel.textTranslatorsUnavailable.text=Unable to get selected text translator, translation is disabled.",
        "TranslationOptionsPanel.noTextTranslatorSelected.text=No text translator selected, translation is disabled."})
    private void loadSelectedPanelSettings() {
        translationServicePanel.removeAll();
        if (translatorComboBox.getSelectedItem() != null && !translatorComboBox.getSelectedItem().toString().equals(Bundle.TranslationOptionsPanel_translationDisabled_text())) {
            try {
                TextTranslationService.getInstance().setSelectedTranslator(translatorComboBox.getSelectedItem().toString());
                Component panel = TextTranslationService.getInstance().getSelectedTranslator().getComponent();
                panel.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        controller.changed();
                    }
                });
                translationServicePanel.add(panel);
                currentSelection = translatorComboBox.getSelectedItem().toString();
            } catch (NoServiceProviderException ex) {
                logger.log(Level.WARNING, "Unable to get TextExtractor named: " + translatorComboBox.getSelectedItem().toString(), ex);
                JLabel label = new JLabel(Bundle.TranslationOptionsPanel_textTranslatorsUnavailable_text());
                label.setForeground(Color.RED);
                translationServicePanel.add(label);
            }
        } else {
            translationServicePanel.add(new JLabel(Bundle.TranslationOptionsPanel_noTextTranslatorSelected_text()));
        }
        revalidate();
        repaint();
    }

    final void load() {
        currentSelection = UserPreferences.getTextTranslatorName();
        if (currentSelection == null) {
            currentSelection = Bundle.TranslationOptionsPanel_translationDisabled_text();
        }
        loadSelectedPanelSettings();
    }

    void store() {
        UserPreferences.setTextTranslatorName(currentSelection);
        try {
            TextTranslationService.getInstance().getSelectedTranslator().saveSettings();
        } catch (NoServiceProviderException ex) {
            logger.log(Level.WARNING, "Unable to save selected translators settings", ex);
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

        translatorComboBox = new javax.swing.JComboBox<>();
        translationServiceLabel = new javax.swing.JLabel();
        translationServicePanel = new javax.swing.JPanel();

        translatorComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                translatorComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(translationServiceLabel, org.openide.util.NbBundle.getMessage(TranslationOptionsPanel.class, "TranslationOptionsPanel.translationServiceLabel.text")); // NOI18N

        javax.swing.GroupLayout translationServicePanelLayout = new javax.swing.GroupLayout(translationServicePanel);
        translationServicePanel.setLayout(translationServicePanelLayout);
        translationServicePanelLayout.setHorizontalGroup(
            translationServicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 462, Short.MAX_VALUE)
        );
        translationServicePanelLayout.setVerticalGroup(
            translationServicePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 222, Short.MAX_VALUE)
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(translationServiceLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(translatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 205, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 152, Short.MAX_VALUE))
                    .addComponent(translationServicePanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(translatorComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(translationServiceLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(translationServicePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void translatorComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_translatorComboBoxActionPerformed
        updatePanel();
    }//GEN-LAST:event_translatorComboBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel translationServiceLabel;
    private javax.swing.JPanel translationServicePanel;
    private javax.swing.JComboBox<String> translatorComboBox;
    // End of variables declaration//GEN-END:variables

}
