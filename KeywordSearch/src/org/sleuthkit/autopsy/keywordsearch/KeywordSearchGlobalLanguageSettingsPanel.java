/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StringExtract;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.ingest.IngestManager;

/**
 * Child panel of the global settings panel (Languages tab).
 */
class KeywordSearchGlobalLanguageSettingsPanel extends javax.swing.JPanel implements OptionsPanel {

    private final Map<String, StringExtract.StringExtractUnicodeTable.SCRIPT> scripts = new HashMap<>();
    private ActionListener updateLanguagesAction;
    private List<SCRIPT> toUpdate;
    private static final Logger logger = Logger.getLogger(KeywordSearchGlobalLanguageSettingsPanel.class.getName());
    private KeywordSearchSettingsManager manager;
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    KeywordSearchGlobalLanguageSettingsPanel(KeywordSearchSettingsManager manager) {
        this.manager = manager;
        initComponents();
        customizeComponents();
        enableComponents();
    }

    private void customizeComponents() {
        updateLanguagesAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toUpdate = new ArrayList<>();
                final int components = checkPanel.getComponentCount();
                for (int i = 0; i < components; ++i) {
                    JCheckBox ch = (JCheckBox) checkPanel.getComponent(i);
                    if (ch.isSelected()) {
                        SCRIPT s = scripts.get(ch.getText());
                        toUpdate.add(s);
                    }
                }
                store();
            }
        };

        initScriptsCheckBoxes();
        if (manager != null) {
            reloadScriptsCheckBoxes();
        }
    }

    private void enableComponents() {
        boolean enable = manager != null;
        for (Component c : this.checkPanel.getComponents()) {
            c.setEnabled(enable);
        }
        this.enableUTF16Checkbox.setEnabled(enable);
        this.enableUTF8Checkbox.setEnabled(enable);
        this.langPanel.setEnabled(enable);
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    private void activateScriptsCheckboxes(boolean activate) {
        final int components = checkPanel.getComponentCount();
        for (int i = 0; i < components; ++i) {
            JCheckBox ch = (JCheckBox) checkPanel.getComponent(i);
            ch.setEnabled(activate);
        }
    }

    private static String getLangText(SCRIPT script) {
        StringBuilder sb = new StringBuilder();
        sb.append(script.toString()).append(" (");
        sb.append(script.getLanguages());
        sb.append(")");
        return sb.toString();
    }

    private void initScriptsCheckBoxes() {
        final List<StringExtract.StringExtractUnicodeTable.SCRIPT> supportedScripts = StringExtract.getSupportedScripts();
        checkPanel.setLayout(new GridLayout(0, 1));
        for (StringExtract.StringExtractUnicodeTable.SCRIPT s : supportedScripts) {
            String text = getLangText(s);
            JCheckBox ch = new JCheckBox(text);
            ch.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
                }
            });
            ch.addActionListener(updateLanguagesAction);
            checkPanel.add(ch);
            ch.setSelected(false);
            scripts.put(text, s);
        }
    }

    private void reloadScriptsCheckBoxes() {
        boolean utf16
                = Boolean.parseBoolean(manager.getStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString()));

        enableUTF16Checkbox.setSelected(utf16);

        boolean utf8
                = Boolean.parseBoolean(manager.getStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString()));
        enableUTF8Checkbox.setSelected(utf8);

        final List<SCRIPT> serviceScripts = manager.getStringExtractScripts();
        final int components = checkPanel.getComponentCount();

        for (int i = 0; i < components; ++i) {
            JCheckBox ch = (JCheckBox) checkPanel.getComponent(i);

            StringExtract.StringExtractUnicodeTable.SCRIPT script = scripts.get(ch.getText());

            ch.setSelected(serviceScripts.contains(script));
        }
    }

    @Messages({
        "KeywordSearchGlobalLanguageSettingsPanel.failedReadSettings.message=Couldn't read keyword search settings",
        "KeywordSearchGlobalLanguageSettingsPanel.failedReadSettings.title=Error Reading Settings"})
    private void activateWidgets() {
        reloadScriptsCheckBoxes();

        boolean utf16
                = Boolean.parseBoolean(manager.getStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString()));

        enableUTF16Checkbox.setSelected(utf16);

        boolean utf8
                = Boolean.parseBoolean(manager.getStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString()));
        enableUTF8Checkbox.setSelected(utf8);
        final boolean extractEnabled = utf16 || utf8;

        boolean ingestNotRunning = !IngestManager.getInstance().isIngestRunning() && !IngestManager.getInstance().isIngestRunning();
        //enable / disable checboxes
        activateScriptsCheckboxes(extractEnabled && ingestNotRunning);
        enableUTF16Checkbox.setEnabled(ingestNotRunning);
        enableUTF8Checkbox.setEnabled(ingestNotRunning);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        languagesLabel = new javax.swing.JLabel();
        langPanel = new javax.swing.JScrollPane();
        checkPanel = new javax.swing.JPanel();
        enableUTF8Checkbox = new javax.swing.JCheckBox();
        enableUTF16Checkbox = new javax.swing.JCheckBox();
        ingestSettingsLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(languagesLabel, org.openide.util.NbBundle.getMessage(KeywordSearchGlobalLanguageSettingsPanel.class, "KeywordSearchGlobalLanguageSettingsPanel.languagesLabel.text")); // NOI18N

        langPanel.setPreferredSize(new java.awt.Dimension(430, 361));

        checkPanel.setPreferredSize(new java.awt.Dimension(400, 361));

        javax.swing.GroupLayout checkPanelLayout = new javax.swing.GroupLayout(checkPanel);
        checkPanel.setLayout(checkPanelLayout);
        checkPanelLayout.setHorizontalGroup(
            checkPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 428, Short.MAX_VALUE)
        );
        checkPanelLayout.setVerticalGroup(
            checkPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 395, Short.MAX_VALUE)
        );

        langPanel.setViewportView(checkPanel);

        org.openide.awt.Mnemonics.setLocalizedText(enableUTF8Checkbox, org.openide.util.NbBundle.getMessage(KeywordSearchGlobalLanguageSettingsPanel.class, "KeywordSearchGlobalLanguageSettingsPanel.enableUTF8Checkbox.text")); // NOI18N
        enableUTF8Checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableUTF8CheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(enableUTF16Checkbox, org.openide.util.NbBundle.getMessage(KeywordSearchGlobalLanguageSettingsPanel.class, "KeywordSearchGlobalLanguageSettingsPanel.enableUTF16Checkbox.text")); // NOI18N
        enableUTF16Checkbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                enableUTF16CheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(ingestSettingsLabel, org.openide.util.NbBundle.getMessage(KeywordSearchGlobalLanguageSettingsPanel.class, "KeywordSearchGlobalLanguageSettingsPanel.ingestSettingsLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ingestSettingsLabel)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(enableUTF16Checkbox)
                            .addComponent(enableUTF8Checkbox)))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(languagesLabel, javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(langPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(255, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(ingestSettingsLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(enableUTF16Checkbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(enableUTF8Checkbox)
                .addGap(18, 18, 18)
                .addComponent(languagesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(langPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 397, Short.MAX_VALUE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void enableUTF8CheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableUTF8CheckboxActionPerformed

        boolean selected = this.enableUTF8Checkbox.isSelected();

        activateScriptsCheckboxes(selected || this.enableUTF16Checkbox.isSelected());
        store();
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);

    }//GEN-LAST:event_enableUTF8CheckboxActionPerformed

    private void enableUTF16CheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_enableUTF16CheckboxActionPerformed

        boolean selected = this.enableUTF16Checkbox.isSelected();

        activateScriptsCheckboxes(selected || this.enableUTF8Checkbox.isSelected());
        store();
        pcs.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_enableUTF16CheckboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel checkPanel;
    private javax.swing.JCheckBox enableUTF16Checkbox;
    private javax.swing.JCheckBox enableUTF8Checkbox;
    private javax.swing.JLabel ingestSettingsLabel;
    private javax.swing.JScrollPane langPanel;
    private javax.swing.JLabel languagesLabel;
    // End of variables declaration//GEN-END:variables

    @Override
    @Messages({
        "KeywordSearchGlobalLanguageSettingsPanel.failedWriteSettings.message=Couldn't write keyword search settings",
        "KeywordSearchGlobalLanguageSettingsPanel.failedWriteSettings.title=Error Writing Settings"})
    public void store() {
        if (manager != null) {
            try {
                manager.setStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF8.toString(),
                        Boolean.toString(enableUTF8Checkbox.isSelected()));

                manager.setStringExtractOption(TextExtractor.ExtractOptions.EXTRACT_UTF16.toString(),
                        Boolean.toString(enableUTF16Checkbox.isSelected()));

                if (toUpdate != null) {
                    manager.setStringExtractScripts(toUpdate);
                }
            } catch (KeywordSearchSettingsManager.KeywordSearchSettingsManagerException ex) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        JOptionPane.showMessageDialog(null, Bundle.KeywordSearchGlobalLanguageSettingsPanel_failedWriteSettings_message(),
                        Bundle.KeywordSearchGlobalLanguageSettingsPanel_failedWriteSettings_title(), JOptionPane.ERROR_MESSAGE);
                    }
                });
                
                this.reloadScriptsCheckBoxes();
                logger.log(Level.SEVERE, "Failed to write keyword search settings.", ex);
            }

            // This is a stop-gap way of notifying the job settings panel of potential changes.
            manager.fireLanguagesEvent(KeywordSearchSettingsManager.LanguagesEvent.LANGUAGES_CHANGED);
        }
    }

    @Override
    public void load() {
        if (manager != null) {
            activateWidgets();
        }
    }
}
