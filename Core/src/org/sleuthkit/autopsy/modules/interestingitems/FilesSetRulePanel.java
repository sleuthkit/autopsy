/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.interestingitems;

import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A panel that allows a user to create and edit interesting files set
 * membership rules.
 */
final class FilesSetRulePanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(FilesSetRulePanel.class.getName());
    private static final String SLEUTHKIT_PATH_SEPARATOR = "/"; // NON-NLS
    private static final List<String> ILLEGAL_FILE_NAME_CHARS = InterestingItemDefsManager.getIllegalFileNameChars();
    private static final List<String> ILLEGAL_FILE_PATH_CHARS = InterestingItemDefsManager.getIllegalFilePathChars();

    /**
     * Constructs a files set rule panel in create rule mode.
     */
    FilesSetRulePanel() {
        initComponents();
        populateComponentsWithDefaultValues();
    }

    /**
     * Constructs a files set rule panel in edit rule mode.
     *
     * @param rule The files set rule to be edited.
     */
    FilesSetRulePanel(FilesSet.Rule rule) {
        initComponents();
        populateRuleNameComponent(rule);
        populateTypeFilterComponents(rule);
        populateNameFilterComponents(rule);
        populatePathFilterComponents(rule);
    }

    /**
     * Populates the UI components with default values.
     */
    private void populateComponentsWithDefaultValues() {
        this.filesRadioButton.setSelected(true);
        this.fullNameRadioButton.setSelected(true);
    }

    /**
     * Populates the UI component that displays the rule name.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateRuleNameComponent(FilesSet.Rule rule) {
        this.ruleNameTextField.setText(rule.getName());
    }

    /**
     * Populates the UI components that display the meta-type filter for a rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateTypeFilterComponents(FilesSet.Rule rule) {
        FilesSet.Rule.MetaTypeFilter typeFilter = rule.getMetaTypeFilter();
        switch (typeFilter.getMetaType()) {
            case FILES:
                this.filesRadioButton.setSelected(true);
                break;
            case DIRECTORIES:
                this.dirsRadioButton.setSelected(true);
                break;
            case FILES_AND_DIRECTORIES:
                this.filesAndDirsRadioButton.setSelected(true);
                break;
        }
    }

    /**
     * Populates the UI components that display the name filter for a rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateNameFilterComponents(FilesSet.Rule rule) {
        FilesSet.Rule.FileNameFilter nameFilter = rule.getFileNameFilter();
        this.nameTextField.setText(nameFilter.getTextToMatch());
        this.nameRegexCheckbox.setSelected(nameFilter.isRegex());
        if (nameFilter instanceof FilesSet.Rule.FullNameFilter) {
            this.fullNameRadioButton.setSelected(true);
        } else {
            this.extensionRadioButton.setSelected(true);
        }
    }

    /**
     * Populates the UI components that display the optional path filter for a
     * rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populatePathFilterComponents(FilesSet.Rule rule) {
        FilesSet.Rule.ParentPathFilter pathFilter = rule.getPathFilter();
        if (pathFilter != null) {
            this.pathTextField.setText(pathFilter.getTextToMatch());
            this.pathRegexCheckBox.setSelected(pathFilter.isRegex());
        }
    }

    /**
     * Returns whether or not the data entered in the panel constitutes a valid
     * interesting files set membership rule definition, displaying a dialog
     * explaining the deficiency if the definition is invalid.
     *
     * @return True if the definition is valid, false otherwise.
     */
    boolean isValidRuleDefinition() {
        // The rule must have a name.
        if (this.ruleNameTextField.getText().isEmpty()) {
            NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                    NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.filesSetRulesMustBeNamed"),
                    NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notify(notifyDesc);
            return false;
        }

        // The rule must have name filter text.
        if (this.nameTextField.getText().isEmpty()) {
            NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                    NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.emptyNameFilter"),
                    NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notify(notifyDesc);
            return false;
        }

        // The name filter must either be a regular expression that compiles or
        // a string without illegal file name chars. 
        if (this.nameRegexCheckbox.isSelected()) {
            try {
                Pattern.compile(this.nameTextField.getText());
            } catch (PatternSyntaxException ex) {
                NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                        NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.invalidNameRegex", ex.getLocalizedMessage()),
                        NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(notifyDesc);
                return false;
            }
        } else {
            if (!FilesSetRulePanel.containsOnlyLegalChars(this.nameTextField.getText(), FilesSetRulePanel.ILLEGAL_FILE_NAME_CHARS)) {
                NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                        NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.invalidCharInName"),
                        NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(notifyDesc);
                return false;
            }
        }

        // The path filter, if specified, must either be a regular expression 
        // that compiles or a string without illegal file path chars. 
        if (!this.pathTextField.getText().isEmpty()) {
            if (this.pathRegexCheckBox.isSelected()) {
                try {
                    Pattern.compile(this.pathTextField.getText());
                } catch (PatternSyntaxException ex) {
                    NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                            NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.invalidPathRegex", ex.getLocalizedMessage()),
                            NotifyDescriptor.WARNING_MESSAGE);
                    DialogDisplayer.getDefault().notify(notifyDesc);
                    return false;
                }
            } else {
                if (!FilesSetRulePanel.containsOnlyLegalChars(this.pathTextField.getText(), FilesSetRulePanel.ILLEGAL_FILE_PATH_CHARS)) {
                    NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                            NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.invalidCharInPath"),
                            NotifyDescriptor.WARNING_MESSAGE);
                    DialogDisplayer.getDefault().notify(notifyDesc);
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Gets the name of the files set rule created or edited.
     *
     * @return A name string.
     */
    String getRuleName() {
        return this.ruleNameTextField.getText();
    }

    /**
     * Gets the name filter for the rule that was created or edited. Should only
     * be called if isValidDefintion() returns true.
     *
     * @return A name filter.
     * @throws IllegalStateException if the specified name filter is not valid.
     */
    FilesSet.Rule.FileNameFilter getFileNameFilter() throws IllegalStateException {
        FilesSet.Rule.FileNameFilter filter = null;
        if (!this.nameTextField.getText().isEmpty()) {
            if (this.nameRegexCheckbox.isSelected()) {
                try {
                    Pattern pattern = Pattern.compile(this.nameTextField.getText());
                    if (this.fullNameRadioButton.isSelected()) {
                        filter = new FilesSet.Rule.FullNameFilter(pattern);
                    } else {
                        filter = new FilesSet.Rule.ExtensionFilter(pattern);
                    }
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Attempt to get regex name filter that does not compile", ex); // NON-NLS
                    throw new IllegalStateException("The files set rule panel name filter is not in a valid state"); // NON-NLS
                }
            } else {
                if (FilesSetRulePanel.containsOnlyLegalChars(this.nameTextField.getText(), FilesSetRulePanel.ILLEGAL_FILE_NAME_CHARS)) {
                    if (this.fullNameRadioButton.isSelected()) {
                        filter = new FilesSet.Rule.FullNameFilter(this.nameTextField.getText());
                    } else {
                        filter = new FilesSet.Rule.ExtensionFilter(this.nameTextField.getText());
                    }
                } else {
                    logger.log(Level.SEVERE, "Attempt to get name filter with illegal chars"); // NON-NLS
                    throw new IllegalStateException("The files set rule panel name filter is not in a valid state"); // NON-NLS                    
                }
            }
        }
        return filter;
    }

    /**
     * Gets the file meta-type filter for the rule that was created or edited.
     *
     * @return A type filter.
     */
    FilesSet.Rule.MetaTypeFilter getMetaTypeFilter() {
        if (this.filesRadioButton.isSelected()) {
            return new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES);
        } else if (this.dirsRadioButton.isSelected()) {
            return new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.DIRECTORIES);
        } else {
            return new FilesSet.Rule.MetaTypeFilter(FilesSet.Rule.MetaTypeFilter.Type.FILES_AND_DIRECTORIES);
        }
    }

    /**
     * Gets the optional path filter for the rule that was created or edited.
     * Should only be called if isValidDefintion() returns true.
     *
     * @return A path filter or null if no path filter was specified.
     * @throws IllegalStateException if the specified path filter is not valid.
     */
    FilesSet.Rule.ParentPathFilter getPathFilter() throws IllegalStateException {
        FilesSet.Rule.ParentPathFilter filter = null;
        if (!this.pathTextField.getText().isEmpty()) {
            if (this.pathRegexCheckBox.isSelected()) {
                try {
                    filter = new FilesSet.Rule.ParentPathFilter(Pattern.compile(this.pathTextField.getText()));
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Attempt to get malformed path filter", ex); // NON-NLS
                    throw new IllegalStateException("The files set rule panel path filter is not in a valid state"); // NON-NLS
                }
            } else {
                String path = this.pathTextField.getText();
                if (FilesSetRulePanel.containsOnlyLegalChars(path, FilesSetRulePanel.ILLEGAL_FILE_PATH_CHARS)) {
                    // Add a leading path separator if omitted.
                    if (!path.startsWith(FilesSetRulePanel.SLEUTHKIT_PATH_SEPARATOR)) {
                        path = FilesSetRulePanel.SLEUTHKIT_PATH_SEPARATOR + path;
                    }
                    // Add a trailing path separator if omitted.
                    if (!path.endsWith(FilesSetRulePanel.SLEUTHKIT_PATH_SEPARATOR)) {
                        path += FilesSetRulePanel.SLEUTHKIT_PATH_SEPARATOR;
                    }
                    filter = new FilesSet.Rule.ParentPathFilter(path);
                } else {
                    logger.log(Level.SEVERE, "Attempt to get path filter with illegal chars"); // NON-NLS
                    throw new IllegalStateException("The files set rule panel path filter is not in a valid state"); // NON-NLS                    
                }
            }
        }
        return filter;
    }

    /**
     * Checks an input string for the use of illegal characters.
     *
     * @param toBeChecked The input string.
     * @param illegalChars The characters deemed to be illegal.
     * @return True if the string does not contain illegal characters, false
     * otherwise.
     */
    private static boolean containsOnlyLegalChars(String toBeChecked, List<String> illegalChars) {
        for (String illegalChar : illegalChars) {
            if (toBeChecked.contains(illegalChar)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the state of the name filter UI components consistent with the state
     * of the UI components in the type button group.
     */
    private void setComponentsForSearchType() {
        if (!this.filesRadioButton.isSelected()) {
            this.fullNameRadioButton.setSelected(true);
            this.extensionRadioButton.setEnabled(false);
        } else {
            this.extensionRadioButton.setEnabled(true);
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

        nameButtonGroup = new javax.swing.ButtonGroup();
        typeButtonGroup = new javax.swing.ButtonGroup();
        ruleNameLabel = new javax.swing.JLabel();
        ruleNameTextField = new javax.swing.JTextField();
        namePanel = new javax.swing.JPanel();
        nameRegexCheckbox = new javax.swing.JCheckBox();
        extensionRadioButton = new javax.swing.JRadioButton();
        nameTextField = new javax.swing.JTextField();
        fullNameRadioButton = new javax.swing.JRadioButton();
        typePanel = new javax.swing.JPanel();
        filesRadioButton = new javax.swing.JRadioButton();
        dirsRadioButton = new javax.swing.JRadioButton();
        filesAndDirsRadioButton = new javax.swing.JRadioButton();
        pathPanel = new javax.swing.JPanel();
        pathRegexCheckBox = new javax.swing.JCheckBox();
        pathTextField = new javax.swing.JTextField();
        pathSeparatorInfoLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.ruleNameLabel.text")); // NOI18N

        ruleNameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.ruleNameTextField.text")); // NOI18N

        namePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.namePanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(nameRegexCheckbox, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.nameRegexCheckbox.text")); // NOI18N

        nameButtonGroup.add(extensionRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(extensionRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.extensionRadioButton.text")); // NOI18N

        nameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.nameTextField.text")); // NOI18N

        nameButtonGroup.add(fullNameRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(fullNameRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.fullNameRadioButton.text")); // NOI18N

        javax.swing.GroupLayout namePanelLayout = new javax.swing.GroupLayout(namePanel);
        namePanel.setLayout(namePanelLayout);
        namePanelLayout.setHorizontalGroup(
            namePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(namePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(namePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(namePanelLayout.createSequentialGroup()
                        .addComponent(fullNameRadioButton)
                        .addGap(10, 10, 10)
                        .addComponent(extensionRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(nameRegexCheckbox)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(nameTextField))
                .addContainerGap())
        );
        namePanelLayout.setVerticalGroup(
            namePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(namePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(namePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fullNameRadioButton)
                    .addComponent(extensionRadioButton)
                    .addComponent(nameRegexCheckbox))
                .addContainerGap())
        );

        typePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.typePanel.border.title"))); // NOI18N

        typeButtonGroup.add(filesRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(filesRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.filesRadioButton.text")); // NOI18N
        filesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filesRadioButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(dirsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(dirsRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.dirsRadioButton.text")); // NOI18N
        dirsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dirsRadioButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(filesAndDirsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(filesAndDirsRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.filesAndDirsRadioButton.text")); // NOI18N
        filesAndDirsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                filesAndDirsRadioButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout typePanelLayout = new javax.swing.GroupLayout(typePanel);
        typePanel.setLayout(typePanelLayout);
        typePanelLayout.setHorizontalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(typePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(filesRadioButton)
                .addGap(18, 18, 18)
                .addComponent(dirsRadioButton)
                .addGap(18, 18, 18)
                .addComponent(filesAndDirsRadioButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        typePanelLayout.setVerticalGroup(
            typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(typePanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(typePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filesRadioButton)
                    .addComponent(dirsRadioButton)
                    .addComponent(filesAndDirsRadioButton))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pathPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(pathRegexCheckBox, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathRegexCheckBox.text")); // NOI18N

        pathTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathTextField.text")); // NOI18N

        pathSeparatorInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(pathSeparatorInfoLabel, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathSeparatorInfoLabel.text")); // NOI18N

        javax.swing.GroupLayout pathPanelLayout = new javax.swing.GroupLayout(pathPanel);
        pathPanel.setLayout(pathPanelLayout);
        pathPanelLayout.setHorizontalGroup(
            pathPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pathPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pathPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(pathPanelLayout.createSequentialGroup()
                        .addComponent(pathRegexCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(pathSeparatorInfoLabel))
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 283, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pathPanelLayout.setVerticalGroup(
            pathPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pathPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(pathPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pathRegexCheckBox)
                    .addComponent(pathSeparatorInfoLabel))
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(typePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(namePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(ruleNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 59, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 256, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(pathPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ruleNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addComponent(typePanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(namePanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pathPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        namePanel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.namePanel.AccessibleContext.accessibleName")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void filesAndDirsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filesAndDirsRadioButtonActionPerformed
        setComponentsForSearchType();
    }//GEN-LAST:event_filesAndDirsRadioButtonActionPerformed

    private void dirsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dirsRadioButtonActionPerformed
        setComponentsForSearchType();
    }//GEN-LAST:event_dirsRadioButtonActionPerformed

    private void filesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_filesRadioButtonActionPerformed
        setComponentsForSearchType();
    }//GEN-LAST:event_filesRadioButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton dirsRadioButton;
    private javax.swing.JRadioButton extensionRadioButton;
    private javax.swing.JRadioButton filesAndDirsRadioButton;
    private javax.swing.JRadioButton filesRadioButton;
    private javax.swing.JRadioButton fullNameRadioButton;
    private javax.swing.ButtonGroup nameButtonGroup;
    private javax.swing.JPanel namePanel;
    private javax.swing.JCheckBox nameRegexCheckbox;
    private javax.swing.JTextField nameTextField;
    private javax.swing.JPanel pathPanel;
    private javax.swing.JCheckBox pathRegexCheckBox;
    private javax.swing.JLabel pathSeparatorInfoLabel;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.ButtonGroup typeButtonGroup;
    private javax.swing.JPanel typePanel;
    // End of variables declaration//GEN-END:variables
}
