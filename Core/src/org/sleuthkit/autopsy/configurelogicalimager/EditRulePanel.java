/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.configurelogicalimager;

import java.awt.event.ActionEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 *
 * @author jkho
 */
public class EditRulePanel extends javax.swing.JPanel {

    private JButton okButton;
    private JButton cancelButton;
    private JPanel panel;

    /**
     * Creates new form EditRulePanel
     */
    public EditRulePanel(JButton okButton, JButton cancelButton, String ruleName, LogicalImagerRule rule) {
        //initComponents();
        if (rule.getFullPaths() != null && rule.getFullPaths().size() > 0) {
            panel = new EditFullPathsRulePanel(okButton, cancelButton, ruleName, rule);
        } else {
            panel = new EditNonFullPathsRulePanel(okButton, cancelButton, ruleName, rule);
        }
    }

    static public JPanel NewFullPathsRulePanel(JButton okButton, JButton cancelButton) {
        LogicalImagerRule rule = new LogicalImagerRule();
        return new EditFullPathsRulePanel(okButton, cancelButton, "", rule);
    }
    
    static public JPanel NewNonFullPathsRulePanel(JButton okButton, JButton cancelButton) {
        LogicalImagerRule rule = new LogicalImagerRule();
        return new EditNonFullPathsRulePanel(okButton, cancelButton, "", rule);
    }
    
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileSizeSpinner = new javax.swing.JSpinner();
        modifiedDateLabel = new javax.swing.JLabel();
        daysIncludedTextField = new javax.swing.JTextField();
        daysIncludedLabel = new javax.swing.JLabel();
        fullPathsLabel = new javax.swing.JLabel();
        shouldSaveCheckBox = new javax.swing.JCheckBox();
        shouldAlertCheckBox = new javax.swing.JCheckBox();
        extensionsLabel = new javax.swing.JLabel();
        extensionsTextField = new javax.swing.JTextField();
        filenamesLabel = new javax.swing.JLabel();
        folderNamesLabel = new javax.swing.JLabel();
        fileSizeLabel = new javax.swing.JLabel();
        equalitySignComboBox = new javax.swing.JComboBox<String>();
        extensionsCheckBox = new javax.swing.JCheckBox();
        filenamesCheckBox = new javax.swing.JCheckBox();
        folderNamesCheckBox = new javax.swing.JCheckBox();
        fullPathsCheckBox = new javax.swing.JCheckBox();
        fileSizeCheckBox = new javax.swing.JCheckBox();
        fileSizeUnitComboBox = new javax.swing.JComboBox<String>();
        descriptionTextField = new javax.swing.JTextField();
        descriptionLabel = new javax.swing.JLabel();
        ruleNameLabel = new javax.swing.JLabel();
        ruleNameTextField = new javax.swing.JTextField();
        minDaysCheckBox = new javax.swing.JCheckBox();
        filenamesScrollPane = new javax.swing.JScrollPane();
        folderNamesScrollPane = new javax.swing.JScrollPane();
        fullPathsScrollPane = new javax.swing.JScrollPane();

        fileSizeSpinner.setEnabled(false);
        fileSizeSpinner.setMinimumSize(new java.awt.Dimension(2, 20));

        org.openide.awt.Mnemonics.setLocalizedText(modifiedDateLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.modifiedDateLabel.text")); // NOI18N

        daysIncludedTextField.setEditable(false);
        daysIncludedTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        daysIncludedTextField.setText(org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.daysIncludedTextField.text")); // NOI18N
        daysIncludedTextField.setMinimumSize(new java.awt.Dimension(60, 20));
        daysIncludedTextField.setPreferredSize(new java.awt.Dimension(60, 20));

        org.openide.awt.Mnemonics.setLocalizedText(daysIncludedLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.daysIncludedLabel.text")); // NOI18N
        daysIncludedLabel.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(fullPathsLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.fullPathsLabel.text")); // NOI18N

        shouldSaveCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(shouldSaveCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.shouldSaveCheckBox.text")); // NOI18N

        shouldAlertCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(shouldAlertCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.shouldAlertCheckBox.text")); // NOI18N
        shouldAlertCheckBox.setActionCommand(org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.shouldAlertCheckBox.actionCommand")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(extensionsLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.extensionsLabel.text")); // NOI18N

        extensionsTextField.setText(org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.extensionsTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(filenamesLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.filenamesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(folderNamesLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.folderNamesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fileSizeLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.fileSizeLabel.text")); // NOI18N

        equalitySignComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { "=", ">", "≥", "<", "≤" }));
        equalitySignComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(extensionsCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.extensionsCheckBox.text")); // NOI18N
        extensionsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extensionsCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(filenamesCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.filenamesCheckBox.text")); // NOI18N
        filenamesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filenamesCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(folderNamesCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.folderNamesCheckBox.text")); // NOI18N
        folderNamesCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderNamesCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fullPathsCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.fullPathsCheckBox.text")); // NOI18N
        fullPathsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fullPathsCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileSizeCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.fileSizeCheckBox.text")); // NOI18N
        fileSizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileSizeCheckBoxActionPerformed(evt);
            }
        });

        fileSizeUnitComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.FilesSetDefsPanel_bytes(), Bundle.FilesSetDefsPanel_kiloBytes(), Bundle.FilesSetDefsPanel_megaBytes(), Bundle.FilesSetDefsPanel_gigaBytes() }));
        fileSizeUnitComboBox.setEnabled(false);

        descriptionTextField.setText(org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.descriptionTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.descriptionLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.ruleNameLabel.text")); // NOI18N

        ruleNameTextField.setText(org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.ruleNameTextField.text")); // NOI18N
        ruleNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ruleNameTextFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(minDaysCheckBox, org.openide.util.NbBundle.getMessage(EditRulePanel.class, "EditRulePanel.minDaysCheckBox.text")); // NOI18N
        minDaysCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minDaysCheckBoxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(extensionsCheckBox)
                        .addComponent(minDaysCheckBox)
                        .addComponent(filenamesCheckBox, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(fileSizeCheckBox)
                    .addComponent(folderNamesCheckBox)
                    .addComponent(fullPathsCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ruleNameLabel)
                    .addComponent(descriptionLabel)
                    .addComponent(extensionsLabel)
                    .addComponent(filenamesLabel)
                    .addComponent(folderNamesLabel)
                    .addComponent(fullPathsLabel)
                    .addComponent(fileSizeLabel)
                    .addComponent(modifiedDateLabel))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ruleNameTextField)
                            .addComponent(extensionsTextField)
                            .addComponent(descriptionTextField)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(daysIncludedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(1, 1, 1)
                                        .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(daysIncludedLabel)
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(75, 75, 75)
                        .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 288, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(filenamesScrollPane))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(folderNamesScrollPane))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fullPathsScrollPane)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addGap(23, 23, 23)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(shouldSaveCheckBox)
                    .addComponent(shouldAlertCheckBox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ruleNameLabel)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(descriptionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(descriptionLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(extensionsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(extensionsCheckBox)
                    .addComponent(extensionsLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filenamesLabel)
                    .addComponent(filenamesCheckBox)
                    .addComponent(filenamesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(folderNamesLabel)
                    .addComponent(folderNamesCheckBox)
                    .addComponent(folderNamesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fullPathsLabel)
                            .addComponent(fullPathsCheckBox)))
                    .addGroup(layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fullPathsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(fileSizeCheckBox)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(fileSizeLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(modifiedDateLabel)
                            .addComponent(daysIncludedLabel)
                            .addComponent(minDaysCheckBox)
                            .addComponent(daysIncludedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 17, Short.MAX_VALUE)
                .addComponent(shouldAlertCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(shouldSaveCheckBox)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void extensionsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extensionsCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_extensionsCheckBoxActionPerformed

    private void filenamesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filenamesCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_filenamesCheckBoxActionPerformed

    private void folderNamesCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderNamesCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_folderNamesCheckBoxActionPerformed

    private void fullPathsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fullPathsCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fullPathsCheckBoxActionPerformed

    private void fileSizeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileSizeCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileSizeCheckBoxActionPerformed

    private void ruleNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ruleNameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ruleNameTextFieldActionPerformed

    private void minDaysCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minDaysCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_minDaysCheckBoxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel daysIncludedLabel;
    private javax.swing.JTextField daysIncludedTextField;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JComboBox<String> equalitySignComboBox;
    private javax.swing.JCheckBox extensionsCheckBox;
    private javax.swing.JLabel extensionsLabel;
    private javax.swing.JTextField extensionsTextField;
    private javax.swing.JCheckBox fileSizeCheckBox;
    private javax.swing.JLabel fileSizeLabel;
    private javax.swing.JSpinner fileSizeSpinner;
    private javax.swing.JComboBox<String> fileSizeUnitComboBox;
    private javax.swing.JCheckBox filenamesCheckBox;
    private javax.swing.JLabel filenamesLabel;
    private javax.swing.JScrollPane filenamesScrollPane;
    private javax.swing.JCheckBox folderNamesCheckBox;
    private javax.swing.JLabel folderNamesLabel;
    private javax.swing.JScrollPane folderNamesScrollPane;
    private javax.swing.JCheckBox fullPathsCheckBox;
    private javax.swing.JLabel fullPathsLabel;
    private javax.swing.JScrollPane fullPathsScrollPane;
    private javax.swing.JCheckBox minDaysCheckBox;
    private javax.swing.JLabel modifiedDateLabel;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.JCheckBox shouldAlertCheckBox;
    private javax.swing.JCheckBox shouldSaveCheckBox;
    // End of variables declaration//GEN-END:variables

    void setRule(LogicalImagerRule rule) {
        
    }

    /**
     * Sets whether or not the OK button should be enabled based upon other UI
     * elements
     */
    private void setOkButton() {
        if (this.okButton != null) {
            this.okButton.setEnabled(true);
        }
    }

    /**
     * Gets the JOptionPane that is used to contain this panel if there is one
     *
     * @param parent
     *
     * @return
     */
    private JOptionPane getOptionPane(JComponent parent) {
        JOptionPane pane;
        if (!(parent instanceof JOptionPane)) {
            pane = getOptionPane((JComponent) parent.getParent());
        } else {
            pane = (JOptionPane) parent;
        }
        return pane;
    }

    /**
     * Sets the buttons for ending the panel
     *
     * @param ok     The ok button
     * @param cancel The cancel button
     */
    private void setButtons(JButton ok, JButton cancel) {
        this.okButton = ok;
        this.cancelButton = cancel;
        okButton.addActionListener((ActionEvent e) -> {
            JOptionPane pane = getOptionPane(okButton);
            pane.setValue(okButton);
        });
        cancelButton.addActionListener((ActionEvent e) -> {
            JOptionPane pane = getOptionPane(cancelButton);
            pane.setValue(cancelButton);
        });
        this.setOkButton();
    }

    JPanel getPanel() {
        return panel;
    }
}
