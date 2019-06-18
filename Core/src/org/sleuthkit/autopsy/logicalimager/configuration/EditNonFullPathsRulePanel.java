/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
class EditNonFullPathsRulePanel extends javax.swing.JPanel {

    private JButton okButton;
    private JButton cancelButton;
    private final javax.swing.JTextArea filenamesTextArea;
    private final javax.swing.JTextArea folderNamesTextArea;
    
    /**
     * Creates new form EditRulePanel
     */
    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.example=Example: ",
        "EditNonFullPathsRulePanel.note=NOTE: A special [USER_FOLDER] token at the the start of a folder name to allow matches of all user folders in the file system."
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

        if (rule.getExtensions() == null) {
            extensionsRadioButton.setSelected(false);
            filenamesRadioButton.setSelected(true);
        } else {
            extensionsRadioButton.setSelected(true);
            filenamesRadioButton.setSelected(false);
        }
        
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
                + "<br>/Program Files/Common Files"
                + "<br>"
                + Bundle.EditNonFullPathsRulePanel_note()
                + "</html>"); // NON-NLS
        validate();
        repaint();
    }
    
    private void initTextArea(JScrollPane pane, JTextArea textArea) {
        textArea.setColumns(20);
        textArea.setRows(5);
        pane.setViewportView(textArea);
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
        minDaysTextField.setText(minDays == null ? "" : minDays.toString());
    }

    private void setTextArea(JTextArea textArea, List<String> set) {
        String text = "";
        if (set != null) {
            for (String s : set) {
                text += s + System.getProperty("line.separator"); // NON-NLS
            }
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

        buttonGroup = new javax.swing.ButtonGroup();
        modifiedDateLabel = new javax.swing.JLabel();
        daysIncludedLabel = new javax.swing.JLabel();
        shouldSaveCheckBox = new javax.swing.JCheckBox();
        shouldAlertCheckBox = new javax.swing.JCheckBox();
        extensionsLabel = new javax.swing.JLabel();
        extensionsTextField = new javax.swing.JTextField();
        filenamesLabel = new javax.swing.JLabel();
        folderNamesLabel = new javax.swing.JLabel();
        fileSizeLabel = new javax.swing.JLabel();
        descriptionTextField = new javax.swing.JTextField();
        descriptionLabel = new javax.swing.JLabel();
        ruleNameLabel = new javax.swing.JLabel();
        ruleNameTextField = new javax.swing.JTextField();
        filenamesScrollPane = new javax.swing.JScrollPane();
        folderNamesScrollPane = new javax.swing.JScrollPane();
        minSizeLabel = new javax.swing.JLabel();
        minSizeTextField = new javax.swing.JFormattedTextField();
        maxSizeLabel = new javax.swing.JLabel();
        maxSizeTextField = new javax.swing.JFormattedTextField();
        minDaysTextField = new javax.swing.JFormattedTextField();
        extensionsRadioButton = new javax.swing.JRadioButton();
        filenamesRadioButton = new javax.swing.JRadioButton();

        org.openide.awt.Mnemonics.setLocalizedText(modifiedDateLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.modifiedDateLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(daysIncludedLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.daysIncludedLabel.text")); // NOI18N

        shouldSaveCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(shouldSaveCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldSaveCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(shouldAlertCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.text")); // NOI18N
        shouldAlertCheckBox.setActionCommand(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.actionCommand")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(extensionsLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsLabel.text")); // NOI18N

        extensionsTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(filenamesLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.filenamesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(folderNamesLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.folderNamesLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(fileSizeLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.fileSizeLabel.text")); // NOI18N

        descriptionTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.descriptionTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.descriptionLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.ruleNameLabel.text")); // NOI18N

        ruleNameTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.ruleNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(minSizeLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.minSizeLabel.text")); // NOI18N

        minSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,###; "))));
        minSizeTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.minSizeTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(maxSizeLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.maxSizeLabel.text")); // NOI18N

        maxSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#,###; "))));
        maxSizeTextField.setText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.maxSizeTextField.text")); // NOI18N

        minDaysTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("####; "))));

        buttonGroup.add(extensionsRadioButton);
        extensionsRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(extensionsRadioButton, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsRadioButton.text")); // NOI18N
        extensionsRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsRadioButton.toolTipText")); // NOI18N
        extensionsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extensionsRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup.add(filenamesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(filenamesRadioButton, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.filenamesRadioButton.text")); // NOI18N
        filenamesRadioButton.setToolTipText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.filenamesRadioButton.toolTipText")); // NOI18N
        filenamesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filenamesRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(extensionsRadioButton)
                            .addComponent(filenamesRadioButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ruleNameLabel)
                            .addComponent(descriptionLabel)
                            .addComponent(extensionsLabel)
                            .addComponent(filenamesLabel)
                            .addComponent(folderNamesLabel)
                            .addComponent(fileSizeLabel)
                            .addComponent(modifiedDateLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(folderNamesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(filenamesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(extensionsTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(descriptionTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ruleNameTextField, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(23, 23, 23)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(shouldSaveCheckBox)
                                    .addComponent(shouldAlertCheckBox)))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(108, 108, 108)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(minDaysTextField)
                                    .addComponent(minSizeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGap(18, 18, 18)
                                        .addComponent(maxSizeLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(daysIncludedLabel))))
                        .addGap(0, 236, Short.MAX_VALUE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ruleNameLabel)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(descriptionLabel)
                    .addComponent(descriptionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(extensionsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(extensionsLabel)
                    .addComponent(extensionsRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filenamesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 70, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addComponent(filenamesLabel)
                            .addComponent(filenamesRadioButton))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(folderNamesLabel)
                    .addComponent(folderNamesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(fileSizeLabel)
                    .addComponent(minSizeLabel)
                    .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(maxSizeLabel)
                    .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(modifiedDateLabel)
                    .addComponent(daysIncludedLabel)
                    .addComponent(minDaysTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(14, 14, 14)
                .addComponent(shouldAlertCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(shouldSaveCheckBox)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void extensionsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extensionsRadioButtonActionPerformed
        filenamesTextArea.setEnabled(false);
        filenamesTextArea.setForeground(Color.LIGHT_GRAY);
        extensionsTextField.setEnabled(true);
        extensionsTextField.setForeground(Color.BLACK);
    }//GEN-LAST:event_extensionsRadioButtonActionPerformed

    private void filenamesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filenamesRadioButtonActionPerformed
        filenamesTextArea.setEnabled(true);
        filenamesTextArea.setForeground(Color.BLACK);
        extensionsTextField.setEnabled(false);
        extensionsTextField.setForeground(Color.LIGHT_GRAY);
    }//GEN-LAST:event_filenamesRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.ButtonGroup buttonGroup;
    private javax.swing.JLabel daysIncludedLabel;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JLabel extensionsLabel;
    private javax.swing.JRadioButton extensionsRadioButton;
    private javax.swing.JTextField extensionsTextField;
    private javax.swing.JLabel fileSizeLabel;
    private javax.swing.JLabel filenamesLabel;
    private javax.swing.JRadioButton filenamesRadioButton;
    private javax.swing.JScrollPane filenamesScrollPane;
    private javax.swing.JLabel folderNamesLabel;
    private javax.swing.JScrollPane folderNamesScrollPane;
    private javax.swing.JLabel maxSizeLabel;
    private javax.swing.JFormattedTextField maxSizeTextField;
    private javax.swing.JFormattedTextField minDaysTextField;
    private javax.swing.JLabel minSizeLabel;
    private javax.swing.JFormattedTextField minSizeTextField;
    private javax.swing.JLabel modifiedDateLabel;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.JCheckBox shouldAlertCheckBox;
    private javax.swing.JCheckBox shouldSaveCheckBox;
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
        "EditNonFullPathsRulePanel.folderNames=Folder names",
    })
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
        
        if (extensionsRadioButton.isSelected()) {
            builder.getExtensions(extensions);
        } else {
            builder.getFilenames(filenames);
        }
        
        int minDays;
        if (!isBlank(minDaysTextField.getText())) {
            try {
                minDaysTextField.commitEdit();
                minDays = ((Number)minDaysTextField.getValue()).intValue();
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
                minFileSize = ((Number)minSizeTextField.getValue()).intValue();
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
                maxFileSize = ((Number)maxSizeTextField.getValue()).intValue();
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
        "EditNonFullPathsRulePanel.emptyExtensionException=Extensions cannot have an empty entry",
    })
    private List<String> validateExtensions(JTextField textField) throws IOException {
        if (isBlank(textField.getText())) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        for (String extension : textField.getText().split(",")) {
            extension = strip(extension);
            if (extension.isEmpty()) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_emptyExtensionException());
            }
            extensions.add(extension);
        }
        if (extensions.isEmpty()) {
            return null;
        }
        return extensions;
    }
}
