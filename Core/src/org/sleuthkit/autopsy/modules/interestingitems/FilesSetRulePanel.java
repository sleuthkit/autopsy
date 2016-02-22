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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * A panel that allows a user to create and edit interesting files set
 * membership rules.
 */
final class FilesSetRulePanel extends javax.swing.JPanel {

    @Messages({
        "FilesSetRulePanel.bytes=Bytes",
        "FilesSetRulePanel.kiloBytes=Kilobytes",
        "FilesSetRulePanel.megaBytes=Megabytes",
        "FilesSetRulePanel.gigaBytes=Gigabytes"
    })

    private static final SortedSet<MediaType> mediaTypes = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes();
    private static final Logger logger = Logger.getLogger(FilesSetRulePanel.class.getName());
    private static final String SLEUTHKIT_PATH_SEPARATOR = "/"; // NON-NLS
    private static final List<String> ILLEGAL_FILE_NAME_CHARS = InterestingItemDefsManager.getIllegalFileNameChars();
    private static final List<String> ILLEGAL_FILE_PATH_CHARS = InterestingItemDefsManager.getIllegalFilePathChars();
    private JButton okButton;
    private JButton cancelButton;

    /**
     * Constructs a files set rule panel in create rule mode.
     */
    FilesSetRulePanel() {
        initComponents();
        populateComponentsWithDefaultValues();
        customInit();
    }

    /**
     * Constructs a files set rule panel in edit rule mode.
     *
     * @param rule The files set rule to be edited.
     */
    FilesSetRulePanel(FilesSet.Rule rule) {
        initComponents();
        populateRuleNameComponent(rule);
        populateTypeConditionComponents(rule);
        populateNameConditionComponents(rule);
        populatePathConditionComponents(rule);
        customInit();
    }

    /**
     * Populates the UI components with default values.
     */
    private void populateComponentsWithDefaultValues() {
        this.filesRadio.setSelected(true);
        this.fullNameRadioButton.setSelected(true);
        this.equalitySymbolComboBox.setSelectedIndex(2);
        this.fileSizeComboBox.setSelectedIndex(1);
    }

    private void customInit() {
        Set<String> fileTypesCollated = new HashSet<>();
        for (MediaType mediaType : mediaTypes) {
            fileTypesCollated.add(mediaType.toString());
        }

        FileTypeDetector fileTypeDetector;
        try {
            fileTypeDetector = new FileTypeDetector();
            List<String> userDefinedFileTypes = fileTypeDetector.getUserDefinedTypes();
            fileTypesCollated.addAll(userDefinedFileTypes);

        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Unable to get user defined file types", ex);
        }

        List<String> toSort = new ArrayList<>(fileTypesCollated);
        toSort.sort((String string1, String string2) -> {
            int result = String.CASE_INSENSITIVE_ORDER.compare(string1, string2);
            if (result == 0) {
                result = string1.compareTo(string2);
            }
            return result;
        });

        for (String file : toSort) {
            mimeTypeComboBox.addItem(file);
        }
        this.setOkButton();
    }

    /**
     * Populates the UI component that displays the rule name.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateRuleNameComponent(FilesSet.Rule rule) {
        this.ruleNameTextField.setText(rule.getName());
    }

    private void setOkButton() {
        if (this.okButton != null) {
            if (!(this.fileSizeCheck.isSelected() || this.mimeCheck.isSelected()
                    || this.nameCheck.isSelected() || this.pathCheck.isSelected())) {
                this.okButton.setEnabled(false);
            }
            else {
                this.okButton.setEnabled(true);
            }
        }
    }

    private JOptionPane getOptionPane(JComponent parent) {
        JOptionPane pane = null;
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
     * @param ok The ok button
     * @param cancel The cancel button
     */
    public void setButtons(JButton ok, JButton cancel) {
        this.okButton = ok;
        this.cancelButton = cancel;
        okButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane(okButton);
                pane.setValue(okButton);
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane pane = getOptionPane(cancelButton);
                pane.setValue(cancelButton);
            }
        });
        this.setOkButton();
    }

    /**
     * Populates the UI components that display the meta-type condition for a
     * rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateTypeConditionComponents(FilesSet.Rule rule) {
        FilesSet.Rule.MetaTypeCondition typeCondition = rule.getMetaTypeCondition();
        switch (typeCondition.getMetaType()) {
            case FILES:
                this.filesRadio.setSelected(true);
                break;
            case DIRECTORIES:
                this.dirsRadio.setSelected(true);
                break;
            case FILES_AND_DIRECTORIES:
                this.filesAndDirsRadio.setSelected(true);
                break;
        }
    }

    /**
     * Populates the UI components that display the name condition for a rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populateNameConditionComponents(FilesSet.Rule rule) {
        FilesSet.Rule.FileNameCondition nameCondition = rule.getFileNameCondition();
        this.nameTextField.setText(nameCondition.getTextToMatch());
        this.nameRegexCheckbox.setSelected(nameCondition.isRegex());
        if (nameCondition instanceof FilesSet.Rule.FullNameCondition) {
            this.fullNameRadioButton.setSelected(true);
        } else {
            this.extensionRadioButton.setSelected(true);
        }
    }

    /**
     * Populates the UI components that display the optional path condition for
     * a rule.
     *
     * @param rule The files set rule to be edited.
     */
    private void populatePathConditionComponents(FilesSet.Rule rule) {
        FilesSet.Rule.ParentPathCondition pathCondition = rule.getPathCondition();
        if (pathCondition != null) {
            this.pathTextField.setText(pathCondition.getTextToMatch());
            this.pathRegexCheckBox.setSelected(pathCondition.isRegex());
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

        // The rule must have name condition text.
        if (this.nameTextField.getText().isEmpty()) {
            NotifyDescriptor notifyDesc = new NotifyDescriptor.Message(
                    NbBundle.getMessage(FilesSetPanel.class, "FilesSetRulePanel.messages.emptyNameCondition"),
                    NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notify(notifyDesc);
            return false;
        }

        // The name condition must either be a regular expression that compiles or
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

        // The path condition, if specified, must either be a regular expression 
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
     * Gets the name condition for the rule that was created or edited. Should
     * only be called if isValidDefintion() returns true.
     *
     * @return A name condition.
     *
     * @throws IllegalStateException if the specified name condition is not
     * valid.
     */
    FilesSet.Rule.FileNameCondition getFileNameCondition() throws IllegalStateException {
        FilesSet.Rule.FileNameCondition condition = null;
        if (!this.nameTextField.getText().isEmpty()) {
            if (this.nameRegexCheckbox.isSelected()) {
                try {
                    Pattern pattern = Pattern.compile(this.nameTextField.getText());
                    if (this.fullNameRadioButton.isSelected()) {
                        condition = new FilesSet.Rule.FullNameCondition(pattern);
                    } else {
                        condition = new FilesSet.Rule.ExtensionCondition(pattern);
                    }
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Attempt to get regex name condition that does not compile", ex); // NON-NLS
                    throw new IllegalStateException("The files set rule panel name condition is not in a valid state"); // NON-NLS
                }
            } else {
                if (FilesSetRulePanel.containsOnlyLegalChars(this.nameTextField.getText(), FilesSetRulePanel.ILLEGAL_FILE_NAME_CHARS)) {
                    if (this.fullNameRadioButton.isSelected()) {
                        condition = new FilesSet.Rule.FullNameCondition(this.nameTextField.getText());
                    } else {
                        condition = new FilesSet.Rule.ExtensionCondition(this.nameTextField.getText());
                    }
                } else {
                    logger.log(Level.SEVERE, "Attempt to get name condition with illegal chars"); // NON-NLS
                    throw new IllegalStateException("The files set rule panel name condition is not in a valid state"); // NON-NLS                    
                }
            }
        }
        return condition;
    }

    /**
     * Gets the mime type condition based upon the panel input
     *
     * @return the mime type condition, null if no condition is specified
     */
    FilesSet.Rule.MimeTypeCondition getMimeTypeCondition() {
        FilesSet.Rule.MimeTypeCondition condition = null;
        if (!this.mimeTypeComboBox.getSelectedItem().equals("")) {
            condition = new FilesSet.Rule.MimeTypeCondition((String) this.mimeTypeComboBox.getSelectedItem());
        }
        return condition;
    }

    /**
     * Gets the file size condition created based upon the panel input
     *
     * @return the file size condition, null if no condition is specified
     */
    FilesSet.Rule.FileSizeCondition getFileSizeCondition() {
        FilesSet.Rule.FileSizeCondition condition = null;
        if ((Integer) this.fileSizeSpinner.getValue() != 0) {
            try {
                FilesSet.Rule.FileSizeCondition.COMPARATOR comparator = FilesSet.Rule.FileSizeCondition.COMPARATOR.fromSymbol((String) this.equalitySymbolComboBox.getSelectedItem());
                FilesSet.Rule.FileSizeCondition.SIZE_UNIT unit = FilesSet.Rule.FileSizeCondition.SIZE_UNIT.fromName((String) this.fileSizeComboBox.getSelectedItem());
                int fileSizeValue = (Integer) this.fileSizeSpinner.getValue();
                condition = new FilesSet.Rule.FileSizeCondition(comparator, unit, fileSizeValue);
            } catch (IllegalArgumentException ex) {
                //Swallowing up exception because if invalid data is given, this should return null
            }
        }
        return condition;
    }

    /**
     * Gets the file meta-type condition for the rule that was created or
     * edited.
     *
     * @return A type condition.
     */
    FilesSet.Rule.MetaTypeCondition getMetaTypeCondition() {
        if (this.filesRadio.isSelected()) {
            return new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES);
        } else if (this.dirsRadio.isSelected()) {
            return new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.DIRECTORIES);
        } else {
            return new FilesSet.Rule.MetaTypeCondition(FilesSet.Rule.MetaTypeCondition.Type.FILES_AND_DIRECTORIES);
        }
    }

    /**
     * Gets the optional path condition for the rule that was created or edited.
     * Should only be called if isValidDefintion() returns true.
     *
     * @return A path condition or null if no path condition was specified.
     *
     * @throws IllegalStateException if the specified path condition is not
     * valid.
     */
    FilesSet.Rule.ParentPathCondition getPathCondition() throws IllegalStateException {
        FilesSet.Rule.ParentPathCondition condition = null;
        if (!this.pathTextField.getText().isEmpty()) {
            if (this.pathRegexCheckBox.isSelected()) {
                try {
                    condition = new FilesSet.Rule.ParentPathCondition(Pattern.compile(this.pathTextField.getText()));
                } catch (PatternSyntaxException ex) {
                    logger.log(Level.SEVERE, "Attempt to get malformed path condition", ex); // NON-NLS
                    throw new IllegalStateException("The files set rule panel path condition is not in a valid state"); // NON-NLS
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
                    condition = new FilesSet.Rule.ParentPathCondition(path);
                } else {
                    logger.log(Level.SEVERE, "Attempt to get path condition with illegal chars"); // NON-NLS
                    throw new IllegalStateException("The files set rule panel path condition is not in a valid state"); // NON-NLS                    
                }
            }
        }
        return condition;
    }

    /**
     * Checks an input string for the use of illegal characters.
     *
     * @param toBeChecked The input string.
     * @param illegalChars The characters deemed to be illegal.
     *
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
     * Sets the state of the name condition UI components consistent with the
     * state of the UI components in the type button group.
     */
    private void setComponentsForSearchType() {
        if (this.dirsRadio.isSelected()) {
            this.fullNameRadioButton.setSelected(true);
            this.extensionRadioButton.setEnabled(false);
            this.mimeTypeComboBox.setEnabled(false);
            this.mimeTypeComboBox.setSelectedIndex(0);
            this.equalitySymbolComboBox.setEnabled(false);
            this.fileSizeComboBox.setEnabled(false);
            this.fileSizeSpinner.setEnabled(false);
            this.fileSizeSpinner.setValue(0);
            this.fileSizeCheck.setEnabled(false);
            this.fileSizeCheck.setSelected(false);
            this.mimeCheck.setEnabled(false);
            this.mimeCheck.setSelected(false);

        } else {
            this.extensionRadioButton.setEnabled(true);
            this.fileSizeCheck.setEnabled(true);
            this.mimeCheck.setEnabled(true);
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
        bindingGroup = new org.jdesktop.beansbinding.BindingGroup();

        nameButtonGroup = new javax.swing.ButtonGroup();
        typeButtonGroup = new javax.swing.ButtonGroup();
        ruleNameLabel = new javax.swing.JLabel();
        ruleNameTextField = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        nameTextField = new javax.swing.JTextField();
        fullNameRadioButton = new javax.swing.JRadioButton();
        extensionRadioButton = new javax.swing.JRadioButton();
        nameRegexCheckbox = new javax.swing.JCheckBox();
        pathTextField = new javax.swing.JTextField();
        pathRegexCheckBox = new javax.swing.JCheckBox();
        pathSeparatorInfoLabel = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        mimeTypeComboBox = new javax.swing.JComboBox<String>();
        equalitySymbolComboBox = new javax.swing.JComboBox<String>();
        fileSizeComboBox = new javax.swing.JComboBox<String>();
        fileSizeSpinner = new javax.swing.JSpinner();
        nameCheck = new javax.swing.JCheckBox();
        pathCheck = new javax.swing.JCheckBox();
        mimeCheck = new javax.swing.JCheckBox();
        fileSizeCheck = new javax.swing.JCheckBox();
        filesRadio = new javax.swing.JRadioButton();
        dirsRadio = new javax.swing.JRadioButton();
        filesAndDirsRadio = new javax.swing.JRadioButton();

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.ruleNameLabel.text")); // NOI18N

        ruleNameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.ruleNameTextField.text")); // NOI18N
        ruleNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ruleNameTextFieldActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.jLabel1.text")); // NOI18N

        nameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.nameTextField.text")); // NOI18N
        nameTextField.setEnabled(false);

        nameButtonGroup.add(fullNameRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(fullNameRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.fullNameRadioButton.text")); // NOI18N
        fullNameRadioButton.setEnabled(false);

        nameButtonGroup.add(extensionRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(extensionRadioButton, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.extensionRadioButton.text")); // NOI18N
        extensionRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(nameRegexCheckbox, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.nameRegexCheckbox.text")); // NOI18N
        nameRegexCheckbox.setEnabled(false);

        pathTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathTextField.text")); // NOI18N
        pathTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(pathRegexCheckBox, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathRegexCheckBox.text")); // NOI18N
        pathRegexCheckBox.setEnabled(false);

        pathSeparatorInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(pathSeparatorInfoLabel, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathSeparatorInfoLabel.text")); // NOI18N
        pathSeparatorInfoLabel.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.jLabel5.text")); // NOI18N

        mimeTypeComboBox.setEditable(true);
        mimeTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] {""}));
        mimeTypeComboBox.setEnabled(false);
        mimeTypeComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mimeTypeComboBoxActionPerformed(evt);
            }
        });

        equalitySymbolComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "=", ">", "≥", "<", "≤" }));
        equalitySymbolComboBox.setEnabled(false);

        fileSizeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { Bundle.FilesSetRulePanel_bytes(), Bundle.FilesSetRulePanel_kiloBytes(), Bundle.FilesSetRulePanel_megaBytes(), Bundle.FilesSetRulePanel_gigaBytes() }));
        fileSizeComboBox.setEnabled(false);

        fileSizeSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(0), Integer.valueOf(0), null, Integer.valueOf(1)));
        fileSizeSpinner.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(nameCheck, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.nameCheck.text")); // NOI18N
        nameCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nameCheckActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(pathCheck, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.pathCheck.text")); // NOI18N
        pathCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                pathCheckActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(mimeCheck, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.mimeCheck.text")); // NOI18N
        mimeCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mimeCheckActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileSizeCheck, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.fileSizeCheck.text")); // NOI18N
        fileSizeCheck.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileSizeCheckActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(filesRadio, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.filesRadio.text")); // NOI18N

        org.jdesktop.beansbinding.Binding binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, typeButtonGroup, org.jdesktop.beansbinding.ObjectProperty.create(), filesRadio, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        org.openide.awt.Mnemonics.setLocalizedText(dirsRadio, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.dirsRadio.text")); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, typeButtonGroup, org.jdesktop.beansbinding.ObjectProperty.create(), dirsRadio, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        org.openide.awt.Mnemonics.setLocalizedText(filesAndDirsRadio, org.openide.util.NbBundle.getMessage(FilesSetRulePanel.class, "FilesSetRulePanel.filesAndDirsRadio.text")); // NOI18N

        binding = org.jdesktop.beansbinding.Bindings.createAutoBinding(org.jdesktop.beansbinding.AutoBinding.UpdateStrategy.READ_WRITE, typeButtonGroup, org.jdesktop.beansbinding.ObjectProperty.create(), filesAndDirsRadio, org.jdesktop.beansbinding.BeanProperty.create("selected"));
        bindingGroup.addBinding(binding);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(8, 8, 8)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(ruleNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 234, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel5)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addGap(65, 65, 65)
                                .addComponent(filesRadio)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(dirsRadio)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(filesAndDirsRadio))))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(nameCheck)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 249, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(pathCheck)
                        .addGap(4, 4, 4)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(pathRegexCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(pathSeparatorInfoLabel))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(fullNameRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(extensionRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 114, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(nameRegexCheckbox)
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(mimeCheck)
                            .addComponent(fileSizeCheck))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(equalitySymbolComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 36, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, 94, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(fileSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(filesRadio)
                    .addComponent(dirsRadio)
                    .addComponent(filesAndDirsRadio))
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(nameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(nameCheck))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fullNameRadioButton)
                    .addComponent(extensionRadioButton)
                    .addComponent(nameRegexCheckbox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(pathCheck))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pathRegexCheckBox)
                    .addComponent(pathSeparatorInfoLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 8, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(mimeCheck))
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(equalitySymbolComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fileSizeCheck))
                .addGap(15, 15, 15)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ruleNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        bindingGroup.bind();
    }// </editor-fold>//GEN-END:initComponents

    private void ruleNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ruleNameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ruleNameTextFieldActionPerformed

    private void nameCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nameCheckActionPerformed
        if (!this.nameCheck.isSelected()) {
            this.nameTextField.setEnabled(false);
            this.nameTextField.setText("");
            this.fullNameRadioButton.setEnabled(false);
            this.extensionRadioButton.setEnabled(false);
            this.nameRegexCheckbox.setEnabled(false);
        } else {
            this.nameTextField.setEnabled(true);
            this.fullNameRadioButton.setEnabled(true);
            this.extensionRadioButton.setEnabled(true);
            this.nameRegexCheckbox.setEnabled(true);
        }
        this.setOkButton();
    }//GEN-LAST:event_nameCheckActionPerformed

    private void pathCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_pathCheckActionPerformed
        if (!this.pathCheck.isSelected()) {
            this.pathTextField.setEnabled(false);
            this.pathTextField.setText("");
            this.pathRegexCheckBox.setEnabled(false);
            this.pathSeparatorInfoLabel.setEnabled(false);
        } else {
            this.pathTextField.setEnabled(true);
            this.pathRegexCheckBox.setEnabled(true);
            this.pathSeparatorInfoLabel.setEnabled(true);
        }
        this.setOkButton();
    }//GEN-LAST:event_pathCheckActionPerformed

    private void mimeCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mimeCheckActionPerformed
        if (!this.mimeCheck.isSelected()) {
            this.mimeTypeComboBox.setEnabled(false);
            this.mimeTypeComboBox.setSelectedIndex(0);
        } else {
            this.mimeTypeComboBox.setEnabled(true);
        }
        this.setOkButton();
    }//GEN-LAST:event_mimeCheckActionPerformed

    private void fileSizeCheckActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileSizeCheckActionPerformed
        if (!this.fileSizeCheck.isSelected()) {
            this.fileSizeComboBox.setEnabled(false);
            this.fileSizeSpinner.setEnabled(false);
            this.fileSizeSpinner.setValue(0);
            this.equalitySymbolComboBox.setEnabled(false);
        } else {
            this.fileSizeComboBox.setEnabled(true);
            this.fileSizeSpinner.setEnabled(true);
            this.equalitySymbolComboBox.setEnabled(true);
        }
        this.setOkButton();
    }//GEN-LAST:event_fileSizeCheckActionPerformed

    private void mimeTypeComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mimeTypeComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_mimeTypeComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton dirsRadio;
    private javax.swing.JComboBox equalitySymbolComboBox;
    private javax.swing.JRadioButton extensionRadioButton;
    private javax.swing.JCheckBox fileSizeCheck;
    private javax.swing.JComboBox fileSizeComboBox;
    private javax.swing.JSpinner fileSizeSpinner;
    private javax.swing.JRadioButton filesAndDirsRadio;
    private javax.swing.JRadioButton filesRadio;
    private javax.swing.JRadioButton fullNameRadioButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JCheckBox mimeCheck;
    private javax.swing.JComboBox<String> mimeTypeComboBox;
    private javax.swing.ButtonGroup nameButtonGroup;
    private javax.swing.JCheckBox nameCheck;
    private javax.swing.JCheckBox nameRegexCheckbox;
    private javax.swing.JTextField nameTextField;
    private javax.swing.JCheckBox pathCheck;
    private javax.swing.JCheckBox pathRegexCheckBox;
    private javax.swing.JLabel pathSeparatorInfoLabel;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.ButtonGroup typeButtonGroup;
    private org.jdesktop.beansbinding.BindingGroup bindingGroup;
    // End of variables declaration//GEN-END:variables
}
