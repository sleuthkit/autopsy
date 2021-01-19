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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSet;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSetException;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSetManager;

/**
 *
 * Yara Rule Set Option Panel.
 */
public class YaraRuleSetOptionPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(YaraRuleSetOptionPanel.class.getName());

    /**
     * Creates new form YaraRuleSetOptionPanel
     */
    public YaraRuleSetOptionPanel() {
        initComponents();

        ruleSetPanel.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                handleSelectionChange();
            }
        });

        ruleSetPanel.addDeleteRuleListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleDeleteRuleSet();
            }
        });

        ruleSetPanel.addNewRuleListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleNewRuleSet();
            }
        });
    }

    /**
     * Update the panel with the current rule set.
     */
    void updatePanel() {
        ruleSetPanel.addSetList(RuleSetManager.getInstance().getRuleSetList());
    }
    
    @Messages({
        "# {0} - rule set name",
        "YaraRuleSetOptionPanel_RuleSet_Missing=The folder for the selected YARA rule set, {0}, no longer exists.",
        "YaraRuleSetOptionPanel_RuleSet_Missing_title=Folder removed",
    })

    /**
     * Handle the change in rule set selection. Update the detail panel with the
     * selected rule.
     */
    private void handleSelectionChange() {
        RuleSet ruleSet = ruleSetPanel.getSelectedRule();

        if(ruleSet != null && !ruleSet.getPath().toFile().exists()) {
            ruleSetDetailsPanel.setRuleSet(null);
            ruleSetPanel.removeRuleSet(ruleSet);
            JOptionPane.showMessageDialog(this,
                    Bundle.YaraRuleSetOptionPanel_RuleSet_Missing(ruleSet.getName()),
                    Bundle.YaraRuleSetOptionPanel_RuleSet_Missing_title(),
                    JOptionPane.ERROR_MESSAGE);
        } else {
            ruleSetDetailsPanel.setRuleSet(ruleSet);
        }
    }

    @Messages({
        "YaraRuleSetOptionPanel_new_rule_set_name_msg=Supply a new unique rule set name:",
        "YaraRuleSetOptionPanel_new_rule_set_name_title=Rule Set Name",
        "# {0} - rule set name",
        "YaraRuleSetOptionPanel_badName_msg=Rule set name {0} already exists.\nRule set names must be unique.",
        "YaraRuleSetOptionPanel_badName_title=Create Rule Set",
        "YaraRuleSetOptionPanel_badName2_msg=Rule set is invalid.\nRule set names must be non-empty string and unique.",})
    /**
     * Handle the new rule set action. Prompt the user for a rule set name,
     * create the new set and update the rule set list.
     */
    private void handleNewRuleSet() {
        String value = JOptionPane.showInputDialog(this,
                Bundle.YaraRuleSetOptionPanel_new_rule_set_name_msg(),
                Bundle.YaraRuleSetOptionPanel_new_rule_set_name_title());
        
        // User hit cancel.
        if(value == null) {
            return;
        }

        if (value.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    Bundle.YaraRuleSetOptionPanel_badName2_msg(),
                    Bundle.YaraRuleSetOptionPanel_badName_title(),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            ruleSetPanel.addRuleSet(RuleSetManager.getInstance().createRuleSet(value));
        } catch (RuleSetException ex) {
            JOptionPane.showMessageDialog(this,
                    Bundle.YaraRuleSetOptionPanel_badName_msg(value),
                    Bundle.YaraRuleSetOptionPanel_badName_title(),
                    JOptionPane.ERROR_MESSAGE);
            logger.log(Level.WARNING, "Failed to create new rule set, user provided existing name.", ex);
        }
    }

    @Messages({
        "# {0} - rule set name",
        "YaraRuleSetOptionPanel_rule_set_delete=Unable to delete the selected YARA rule set {0}.\nRule set may have already been removed."
    })

    /**
     * Handle the delete rule action. Delete the rule set and update the the
     * rule set list.
     */
    private void handleDeleteRuleSet() {
        RuleSet ruleSet = ruleSetPanel.getSelectedRule();
        if (ruleSet != null) {
            try {
                RuleSetManager.getInstance().deleteRuleSet(ruleSet);
            } catch (RuleSetException ex) {
                JOptionPane.showMessageDialog(this,
                        Bundle.YaraRuleSetOptionPanel_rule_set_delete(ruleSet.getName()),
                        Bundle.YaraRuleSetOptionPanel_badName_title(),
                        JOptionPane.ERROR_MESSAGE);
                logger.log(Level.WARNING, String.format("Failed to delete YARA rule set %s", ruleSet.getName()), ex);
            }
            ruleSetPanel.removeRuleSet(ruleSet);
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

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel viewportPanel = new javax.swing.JPanel();
        javax.swing.JSeparator separator = new javax.swing.JSeparator();
        ruleSetDetailsPanel = new org.sleuthkit.autopsy.modules.yara.ui.RuleSetDetailsPanel();
        ruleSetPanel = new org.sleuthkit.autopsy.modules.yara.ui.RuleSetPanel();

        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        setLayout(new java.awt.BorderLayout());

        scrollPane.setBorder(null);

        viewportPanel.setMinimumSize(new java.awt.Dimension(1000, 127));
        viewportPanel.setPreferredSize(new java.awt.Dimension(1020, 400));
        viewportPanel.setLayout(new java.awt.GridBagLayout());

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        viewportPanel.add(separator, gridBagConstraints);

        ruleSetDetailsPanel.setMinimumSize(new java.awt.Dimension(500, 97));
        ruleSetDetailsPanel.setPreferredSize(new java.awt.Dimension(500, 204));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
        viewportPanel.add(ruleSetDetailsPanel, gridBagConstraints);

        ruleSetPanel.setMaximumSize(new java.awt.Dimension(400, 2147483647));
        ruleSetPanel.setMinimumSize(new java.awt.Dimension(400, 107));
        ruleSetPanel.setPreferredSize(new java.awt.Dimension(400, 214));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 10, 10);
        viewportPanel.add(ruleSetPanel, gridBagConstraints);

        scrollPane.setViewportView(viewportPanel);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.modules.yara.ui.RuleSetDetailsPanel ruleSetDetailsPanel;
    private org.sleuthkit.autopsy.modules.yara.ui.RuleSetPanel ruleSetPanel;
    // End of variables declaration//GEN-END:variables
}
