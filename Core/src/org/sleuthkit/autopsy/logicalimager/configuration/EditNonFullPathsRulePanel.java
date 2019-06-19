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
package org.sleuthkit.autopsy.logicalimager.configuration;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.strip;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.openide.util.NbBundle;

/**
 * Edit non-full paths rule panel
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class EditNonFullPathsRulePanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Color DISABLED_COLOR = new Color(240,240,240);
    private JButton okButton;
    private JButton cancelButton;
    private final javax.swing.JTextArea filenamesTextArea;
    private final javax.swing.JTextArea folderNamesTextArea;

    /**
     * Creates new form EditRulePanel
     */
    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.example=Example: "
    })
    EditNonFullPathsRulePanel(JButton okButton, JButton cancelButton, String ruleName, LogicalImagerRule rule, boolean editing) {
        initComponents();

        if (editing) {
            ruleNameTextField.setEnabled(!editing);
        }

        this.setRule(ruleName, rule);
        this.setButtons(okButton, cancelButton);

        setExtensions(rule.getExtensions());

        filenamesTextArea = new JTextArea();
        initTextArea(filenamesScrollPane, filenamesTextArea);
        setTextArea(filenamesTextArea, rule.getFilenames());

        if (rule.getExtensions() != null) {
            extensionsCheckbox.setSelected(true);
        } else if (rule.getFilenames() != null && !rule.getFilenames().isEmpty()) {
            fileNamesCheckbox.setSelected(true);
        }
        updateExclusiveConditions();
        folderNamesTextArea = new JTextArea();
        initTextArea(folderNamesScrollPane, folderNamesTextArea);
        setTextArea(folderNamesTextArea, rule.getPaths());

        setMinDays(rule.getMinDays());

        minSizeTextField.setText(rule.getMinFileSize() == null ? "" : rule.getMinFileSize().toString());
        maxSizeTextField.setText(rule.getMaxFileSize() == null ? "" : rule.getMaxFileSize().toString());
        ruleNameTextField.requestFocus();

        EditRulePanel.setTextFieldPrompts(extensionsTextField, Bundle.EditNonFullPathsRulePanel_example() + "gif,jpg,png"); // NON-NLS
        EditRulePanel.setTextFieldPrompts(filenamesTextArea, "<html>"
                + Bundle.EditNonFullPathsRulePanel_example()
                + "<br>filename.txt<br>readme.txt</html>"); // NON-NLS
        EditRulePanel.setTextFieldPrompts(folderNamesTextArea, "<html>"
                + Bundle.EditNonFullPathsRulePanel_example()
                + "<br>[USER_FOLDER]/My Documents/Downloads"
                + "<br>/Program Files/Common Files</html>"); // NON-NLS
        validate();
        repaint();
    }

    private void initTextArea(JScrollPane pane, JTextArea textArea) {
        textArea.setColumns(20);
        textArea.setRows(4);
        pane.setViewportView(textArea);
        textArea.setEnabled(false);
        textArea.setEditable(false);
        textArea.setBackground(DISABLED_COLOR);
        textArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_TAB) {
                    if (e.getModifiers() > 0) {
                        textArea.transferFocusBackward();
                    } else {
                        textArea.transferFocus();
                    }
                    e.consume();
                }
            }
        });
    }

    private void setMinDays(Integer minDays) {
        modifiedWithTextField.setText(minDays == null ? "" : minDays.toString());
    }

    private void setTextArea(JTextArea textArea, List<String> set) {
        String text = "";
        if (set != null) {
            text = set.stream().map((s) -> s + System.getProperty("line.separator")).reduce(text, String::concat); // NON-NLS
        }
        textArea.setText(text);
    }

    private void setExtensions(List<String> extensions) {
        extensionsTextField.setText("");
        String content = "";
        if (extensions != null) {
            boolean first = true;
            for (String ext : extensions) {
                content += (first ? "" : ",") + ext;
                first = false;
            }
        }
        extensionsTextField.setText(content);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        daysIncludedLabel = new javax.swing.JLabel();
        shouldSaveCheckBox = new javax.swing.JCheckBox();
        shouldAlertCheckBox = new javax.swing.JCheckBox();
        extensionsTextField = new javax.swing.JTextField();
        descriptionTextField = new javax.swing.JTextField();
        ruleNameLabel = new javax.swing.JLabel();
        ruleNameTextField = new javax.swing.JTextField();
        filenamesScrollPane = new javax.swing.JScrollPane();
        folderNamesScrollPane = new javax.swing.JScrollPane();
        minSizeTextField = new javax.swing.JFormattedTextField();
        maxSizeTextField = new javax.swing.JFormattedTextField();
        modifiedWithTextField = new javax.swing.JFormattedTextField();
        userFolderNote = new javax.swing.JLabel();
        minSizeCheckBox = new javax.swing.JCheckBox();
        maxSizeCheckBox = new javax.swing.JCheckBox();
        modifiedWithinCheckbox = new javax.swing.JCheckBox();
        folderNamesCheckbox = new javax.swing.JCheckBox();
        fileNamesCheckbox = new javax.swing.JCheckBox();
        extensionsCheckbox = new javax.swing.JCheckBox();
        descriptionCheckbox = new javax.swing.JCheckBox();

        org.openide.awt.Mnemonics.setLocalizedText(daysIncludedLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.daysIncludedLabel.text")); // NOI18N

        shouldSaveCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(shouldSaveCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldSaveCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(shouldAlertCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.text")); // NOI18N
        shouldAlertCheckBox.setActionCommand(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.actionCommand")); // NOI18N

        extensionsTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsTextField.text")); // NOI18N
        extensionsTextField.setEnabled(false);

        descriptionTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.descriptionTextField.text")); // NOI18N
        descriptionTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.ruleNameLabel.text")); // NOI18N

        ruleNameTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.ruleNameTextField.text")); // NOI18N

        filenamesScrollPane.setEnabled(false);

        folderNamesScrollPane.setEnabled(false);

        minSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,###; "))));
        minSizeTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.minSizeTextField.text")); // NOI18N
        minSizeTextField.setEnabled(false);

        maxSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,###; "))));
        maxSizeTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.maxSizeTextField.text")); // NOI18N
        maxSizeTextField.setEnabled(false);

        modifiedWithTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("####; "))));
        modifiedWithTextField.setEnabled(false);

        userFolderNote.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(userFolderNote, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.userFolderNote.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(minSizeCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.minSizeCheckBox.text")); // NOI18N
        minSizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minSizeCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(maxSizeCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.maxSizeCheckBox.text")); // NOI18N
        maxSizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                maxSizeCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(modifiedWithinCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.modifiedWithinCheckbox.text")); // NOI18N
        modifiedWithinCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modifiedWithinCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(folderNamesCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.folderNamesCheckbox.text")); // NOI18N
        folderNamesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderNamesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileNamesCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.fileNamesCheckbox.text")); // NOI18N
        fileNamesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileNamesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(extensionsCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsCheckbox.text")); // NOI18N
        extensionsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extensionsCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(descriptionCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.descriptionCheckbox.text")); // NOI18N
        descriptionCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                descriptionCheckboxActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                        .addComponent(folderNamesCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)
                                        .addComponent(extensionsCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(descriptionCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(ruleNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                        .addComponent(minSizeCheckBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(maxSizeCheckBox, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 101, Short.MAX_VALUE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(descriptionTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(extensionsTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(folderNamesScrollPane)
                                    .addComponent(filenamesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(userFolderNote))
                                        .addGap(0, 0, Short.MAX_VALUE))))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(fileNamesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(modifiedWithinCheckbox)
                                .addGap(10, 10, 10)
                                .addComponent(modifiedWithTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(daysIncludedLabel))
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(shouldAlertCheckBox)
                                .addComponent(shouldSaveCheckBox)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ruleNameLabel)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(descriptionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(descriptionCheckbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(extensionsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(extensionsCheckbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filenamesScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(fileNamesCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(folderNamesScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(folderNamesCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(userFolderNote)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(minSizeCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(maxSizeCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(daysIncludedLabel)
                    .addComponent(modifiedWithTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(modifiedWithinCheckbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(shouldSaveCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(shouldAlertCheckBox)
                .addGap(11, 11, 11))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void extensionsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extensionsCheckboxActionPerformed
        if (fileNamesCheckbox.isSelected() && extensionsCheckbox.isSelected()) {
            fileNamesCheckbox.setSelected(false);
        }
        updateExclusiveConditions();
    }//GEN-LAST:event_extensionsCheckboxActionPerformed

    private void fileNamesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileNamesCheckboxActionPerformed
        if (fileNamesCheckbox.isSelected() && extensionsCheckbox.isSelected()) {
            extensionsCheckbox.setSelected(false);
        }
        updateExclusiveConditions();
    }//GEN-LAST:event_fileNamesCheckboxActionPerformed

    private void descriptionCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_descriptionCheckboxActionPerformed
        descriptionTextField.setEnabled(descriptionCheckbox.isSelected());
    }//GEN-LAST:event_descriptionCheckboxActionPerformed

    private void folderNamesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderNamesCheckboxActionPerformed
        folderNamesScrollPane.setEnabled(folderNamesCheckbox.isSelected());
        folderNamesTextArea.setEditable(folderNamesCheckbox.isSelected());
        folderNamesTextArea.setEnabled(folderNamesCheckbox.isSelected());
        updateTextAreaBackgroundColor(folderNamesTextArea);
        
    }//GEN-LAST:event_folderNamesCheckboxActionPerformed

    private void minSizeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minSizeCheckBoxActionPerformed
        minSizeTextField.setEnabled(minSizeCheckBox.isSelected());
    }//GEN-LAST:event_minSizeCheckBoxActionPerformed

    private void maxSizeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_maxSizeCheckBoxActionPerformed
        maxSizeTextField.setEnabled(maxSizeCheckBox.isSelected());
    }//GEN-LAST:event_maxSizeCheckBoxActionPerformed

    private void modifiedWithinCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modifiedWithinCheckboxActionPerformed
        modifiedWithTextField.setEnabled(modifiedWithinCheckbox.isSelected());
    }//GEN-LAST:event_modifiedWithinCheckboxActionPerformed

    private static void updateTextAreaBackgroundColor(JTextArea textArea){
        if (textArea.isEnabled()){
            textArea.setBackground(Color.WHITE);
        } else {
            textArea.setBackground(DISABLED_COLOR);
        }
    }
    
    private void updateExclusiveConditions() {
        extensionsTextField.setEnabled(extensionsCheckbox.isSelected());
        filenamesScrollPane.setEnabled(fileNamesCheckbox.isSelected());
        filenamesTextArea.setEditable(fileNamesCheckbox.isSelected());
        filenamesTextArea.setEnabled(fileNamesCheckbox.isSelected());
        updateTextAreaBackgroundColor(filenamesTextArea);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel daysIncludedLabel;
    private javax.swing.JCheckBox descriptionCheckbox;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JCheckBox extensionsCheckbox;
    private javax.swing.JTextField extensionsTextField;
    private javax.swing.JCheckBox fileNamesCheckbox;
    private javax.swing.JScrollPane filenamesScrollPane;
    private javax.swing.JCheckBox folderNamesCheckbox;
    private javax.swing.JScrollPane folderNamesScrollPane;
    private javax.swing.JCheckBox maxSizeCheckBox;
    private javax.swing.JFormattedTextField maxSizeTextField;
    private javax.swing.JCheckBox minSizeCheckBox;
    private javax.swing.JFormattedTextField minSizeTextField;
    private javax.swing.JFormattedTextField modifiedWithTextField;
    private javax.swing.JCheckBox modifiedWithinCheckbox;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.JCheckBox shouldAlertCheckBox;
    private javax.swing.JCheckBox shouldSaveCheckBox;
    private javax.swing.JLabel userFolderNote;
    // End of variables declaration//GEN-END:variables

    private void setRule(String ruleName, LogicalImagerRule rule) {
        ruleNameTextField.setText(ruleName);
        descriptionTextField.setText(rule.getDescription());
        shouldAlertCheckBox.setSelected(rule.isShouldAlert());
        shouldSaveCheckBox.setSelected(rule.isShouldSave());
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

    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.modifiedDaysNotPositiveException=Modified days must be a positive",
        "# {0} - message",
        "EditNonFullPathsRulePanel.modifiedDaysMustBeNumberException=Modified days must be a number: {0}",
        "EditNonFullPathsRulePanel.minFileSizeNotPositiveException=Minimum file size must be a positive",
        "# {0} - message",
        "EditNonFullPathsRulePanel.minFileSizeMustBeNumberException=Minimum file size must be a number: {0}",
        "EditNonFullPathsRulePanel.maxFileSizeNotPositiveException=Maximum file size must be a positive",
        "# {0} - message",
        "EditNonFullPathsRulePanel.maxFileSizeMustBeNumberException=Maximum file size must be a number: {0}",
        "# {0} - maxFileSize",
        "# {1} - minFileSize",
        "EditNonFullPathsRulePanel.maxFileSizeSmallerThanMinException=Maximum file size: {0} must be bigger than minimum file size: {1}",
        "EditNonFullPathsRulePanel.fileNames=File names",
        "EditNonFullPathsRulePanel.folderNames=Folder names",})
    ImmutablePair<String, LogicalImagerRule> toRule() throws IOException {
        String ruleName = EditRulePanel.validRuleName(ruleNameTextField.getText());
        List<String> extensions = validateExtensions(extensionsTextField);
        List<String> filenames = EditRulePanel.validateTextList(filenamesTextArea, Bundle.EditNonFullPathsRulePanel_fileNames());
        List<String> folderNames = EditRulePanel.validateTextList(folderNamesTextArea, Bundle.EditNonFullPathsRulePanel_folderNames());

        LogicalImagerRule.Builder builder = new LogicalImagerRule.Builder();
        builder.getName(ruleName)
                .getDescription(descriptionTextField.getText())
                .getShouldAlert(shouldAlertCheckBox.isSelected())
                .getShouldSave(shouldSaveCheckBox.isSelected())
                .getPaths(folderNames);

        if (extensionsCheckbox.isSelected()) {
            builder.getExtensions(extensions);
        } else if (fileNamesCheckbox.isSelected()) {
            builder.getFilenames(filenames);
        }

        int minDays;
        if (!isBlank(modifiedWithTextField.getText())) {
            try {
                modifiedWithTextField.commitEdit();
                minDays = ((Number) modifiedWithTextField.getValue()).intValue();
                if (minDays < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_modifiedDaysNotPositiveException());
                }
                builder.getMinDays(minDays);
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_modifiedDaysMustBeNumberException(ex.getMessage()), ex);
            }
        }

        int minFileSize = 0;
        if (!isBlank(minSizeTextField.getText())) {
            try {
                minSizeTextField.commitEdit();
                minFileSize = ((Number) minSizeTextField.getValue()).intValue();
                if (minFileSize < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_minFileSizeNotPositiveException());
                }
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_minFileSizeMustBeNumberException(ex.getMessage()), ex);
            }
        }

        int maxFileSize = 0;
        if (!isBlank(maxSizeTextField.getText())) {
            try {
                maxSizeTextField.commitEdit();
                maxFileSize = ((Number) maxSizeTextField.getValue()).intValue();
                if (maxFileSize < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeNotPositiveException());
                }
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeMustBeNumberException(ex.getMessage()), ex);
            }
        }

        if (maxFileSize != 0 && (maxFileSize < minFileSize)) {
            throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeSmallerThanMinException(maxFileSize, minFileSize));
        }
        if (minFileSize != 0) {
            builder.getMinFileSize(minFileSize);
        }
        if (maxFileSize != 0) {
            builder.getMaxFileSize(maxFileSize);
        }

        LogicalImagerRule rule = builder.build();
        return new ImmutablePair<>(ruleName, rule);
    }

    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.emptyExtensionException=Extensions cannot have an empty entry",})
    private List<String> validateExtensions(JTextField textField) throws IOException {
        if (isBlank(textField.getText())) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        for (String extension : textField.getText().split(",")) {
            String strippedExtension = strip(extension);
            if (extension.isEmpty()) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_emptyExtensionException());
            }
            extensions.add(strippedExtension);
        }
        if (extensions.isEmpty()) {
            return null;
        }
        return extensions;
    }
}
