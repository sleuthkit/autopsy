/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.DefaultListModel;
import org.sleuthkit.autopsy.guicomponentutils.AbstractCheckboxListItem;
import org.sleuthkit.autopsy.guiutils.CheckBoxJList;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettingsPanel;
import org.sleuthkit.autopsy.modules.yara.YaraIngestJobSettings;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSet;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSetManager;

/**
 * Yara Ingest settings panel.
 */
public class YaraIngestSettingsPanel extends IngestModuleIngestJobSettingsPanel {

    private static final long serialVersionUID = 1L;

    private final CheckBoxJList<RuleSetListItem> checkboxList;
    private final DefaultListModel<RuleSetListItem> listModel;

    /**
     * Creates new form YaraIngestSettingsPanel
     */
    YaraIngestSettingsPanel() {
        initComponents();
        listModel = new DefaultListModel<>();
        checkboxList = new CheckBoxJList<>();
        scrollPane.setViewportView(checkboxList);
    }

    /**
     * Constructs a new panel with the given JobSetting objects.
     * 
     * @param settings Ingest job settings.
     */
    public YaraIngestSettingsPanel(YaraIngestJobSettings settings) {
        this();

        List<String> setNames = settings.getSelectedRuleSetNames();

        checkboxList.setModel(listModel);
        checkboxList.setOpaque(false);
        List<RuleSet> ruleSetList = RuleSetManager.getInstance().getRuleSetList();
        for (RuleSet set : ruleSetList) {
            RuleSetListItem item = new RuleSetListItem(set);
            item.setChecked(setNames.contains(set.getName()));
            listModel.addElement(item);
        }

        allFilesButton.setSelected(!settings.onlyExecutableFiles());
        executableFilesButton.setSelected(settings.onlyExecutableFiles());

        RuleSetManager.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                switch (evt.getPropertyName()) {
                    case RuleSetManager.RULE_SET_ADDED:
                        handleRuleSetAdded((RuleSet) evt.getNewValue());
                        break;
                    case RuleSetManager.RULE_SET_DELETED:
                        handleRuleSetDeleted((RuleSet) evt.getOldValue());
                        break;
                }
            }
        });
    }

    @Override
    public IngestModuleIngestJobSettings getSettings() {
        List<RuleSet> selectedRules = new ArrayList<>();

        Enumeration<RuleSetListItem> enumeration = listModel.elements();
        while (enumeration.hasMoreElements()) {
            RuleSetListItem item = enumeration.nextElement();
            if (item.isChecked()) {
                selectedRules.add(item.getRuleSet());
            }
        }

        return new YaraIngestJobSettings(selectedRules, executableFilesButton.isSelected());
    }

    /**
     * Handle the addition of a new Rule Set.
     * 
     * @param ruleSet 
     */
    private void handleRuleSetAdded(RuleSet ruleSet) {
        if (ruleSet == null) {
            return;
        }

        RuleSetListItem item = new RuleSetListItem(ruleSet);
        listModel.addElement(item);
    }

    /**
     * Handle the removal of the rule set.
     * 
     * @param ruleSet 
     */
    private void handleRuleSetDeleted(RuleSet ruleSet) {
        Enumeration<RuleSetListItem> enumeration = listModel.elements();
        while (enumeration.hasMoreElements()) {
            RuleSetListItem item = enumeration.nextElement();
            if (item.getDisplayName().equals(ruleSet.getName())) {
                listModel.removeElement(item);
                return;
            }
        }
    }

    /**
     * RuleSet wrapper class for Checkbox JList model.
     */
    private final class RuleSetListItem extends AbstractCheckboxListItem {

        private final RuleSet ruleSet;

        /**
         * RuleSetListItem constructor.
         *
         * @param set RuleSet object to display in list.
         */
        RuleSetListItem(RuleSet set) {
            this.ruleSet = set;
        }

        /**
         * Returns the RuleSet.
         *
         * @return
         */
        RuleSet getRuleSet() {
            return ruleSet;
        }

        @Override
        public String getDisplayName() {
            return ruleSet.getName();
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

        buttonGroup = new javax.swing.ButtonGroup();
        scrollPane = new javax.swing.JScrollPane();
        buttonPanel = new javax.swing.JPanel();
        allFilesButton = new javax.swing.JRadioButton();
        executableFilesButton = new javax.swing.JRadioButton();

        setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(YaraIngestSettingsPanel.class, "YaraIngestSettingsPanel.border.title"))); // NOI18N
        setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        add(scrollPane, gridBagConstraints);

        buttonPanel.setLayout(new java.awt.GridBagLayout());

        buttonGroup.add(allFilesButton);
        org.openide.awt.Mnemonics.setLocalizedText(allFilesButton, org.openide.util.NbBundle.getMessage(YaraIngestSettingsPanel.class, "YaraIngestSettingsPanel.allFilesButton.text")); // NOI18N
        allFilesButton.setToolTipText(org.openide.util.NbBundle.getMessage(YaraIngestSettingsPanel.class, "YaraIngestSettingsPanel.allFilesButton.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        buttonPanel.add(allFilesButton, gridBagConstraints);

        buttonGroup.add(executableFilesButton);
        executableFilesButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(executableFilesButton, org.openide.util.NbBundle.getMessage(YaraIngestSettingsPanel.class, "YaraIngestSettingsPanel.executableFilesButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        buttonPanel.add(executableFilesButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        add(buttonPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allFilesButton;
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JRadioButton executableFilesButton;
    private javax.swing.JScrollPane scrollPane;
    // End of variables declaration//GEN-END:variables
}
