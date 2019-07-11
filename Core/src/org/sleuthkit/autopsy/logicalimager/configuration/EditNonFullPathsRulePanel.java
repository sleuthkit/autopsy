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
import java.util.logging.Level;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.commons.lang.StringUtils;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.strip;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Edit non-full paths rule panel
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class EditNonFullPathsRulePanel extends javax.swing.JPanel {

    private static final Logger logger = Logger.getLogger(EditNonFullPathsRulePanel.class.getName());
    private static final long serialVersionUID = 1L;
    private static final Color DISABLED_COLOR = new Color(240, 240, 240);
    private static final int BYTE_UNIT_CONVERSION = 1000;
    private JButton okButton;
    private JButton cancelButton;
    private final javax.swing.JTextArea fileNamesTextArea;
    private final javax.swing.JTextArea folderNamesTextArea;

    /**
     * Creates new form EditRulePanel
     */
    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.example=Example: ",
        "EditNonFullPathsRulePanel.units.bytes=Bytes",
        "EditNonFullPathsRulePanel.units.kilobytes=Kilobytes",
        "EditNonFullPathsRulePanel.units.megabytes=Megabytes",
        "EditNonFullPathsRulePanel.units.gigabytes=Gigabytes"
    })
    EditNonFullPathsRulePanel(JButton okButton, JButton cancelButton, String ruleName, LogicalImagerRule rule, boolean editing) {
        initComponents();

        this.setRule(ruleName, rule);
        this.setButtons(okButton, cancelButton);

        setExtensions(rule.getExtensions());
        fileNamesTextArea = new JTextArea();
        initTextArea(filenamesScrollPane, fileNamesTextArea);
        setTextArea(fileNamesTextArea, rule.getFilenames());
        if (rule.getExtensions() != null && !rule.getExtensions().isEmpty()) {
            extensionsCheckbox.setSelected(true);
            extensionsTextField.setEnabled(extensionsCheckbox.isSelected());
        } else if (rule.getFilenames() != null && !rule.getFilenames().isEmpty()) {
            fileNamesCheckbox.setSelected(true);
            fileNamesTextArea.setEnabled(fileNamesCheckbox.isSelected());
        }
        updateExclusiveConditions();
        folderNamesTextArea = new JTextArea();
        initTextArea(folderNamesScrollPane, folderNamesTextArea);
        setTextArea(folderNamesTextArea, rule.getPaths());
        folderNamesCheckbox.setSelected(!StringUtils.isBlank(folderNamesTextArea.getText()));
        folderNamesTextArea.setEnabled(folderNamesCheckbox.isSelected());
        updateTextAreaBackgroundColor(folderNamesTextArea);
        setModifiedWithin(rule.getMinDays());

        setupMinMaxSizeOptions(rule);
        ruleNameTextField.requestFocus();

        EditRulePanel.setTextFieldPrompts(extensionsTextField, Bundle.EditNonFullPathsRulePanel_example() + "gif,jpg,png"); // NON-NLS
        EditRulePanel.setTextFieldPrompts(fileNamesTextArea, "<html>"
                + Bundle.EditNonFullPathsRulePanel_example()
                + "<br>filename.txt<br>readme.txt</html>"); // NON-NLS
        EditRulePanel.setTextFieldPrompts(folderNamesTextArea, "<html>"
                + Bundle.EditNonFullPathsRulePanel_example()
                + "<br>[USER_FOLDER]/My Documents/Downloads"
                + "<br>/Program Files/Common Files</html>"); // NON-NLS
        validate();
        repaint();
        addDocumentListeners();
    }

    /**
     * Set the min and max size options
     *
     * @param rule the rule the min and max size options should reflect
     */
    private void setupMinMaxSizeOptions(LogicalImagerRule rule) {
        String savedMinSize = rule.getMinFileSize() == null ? "" : rule.getMinFileSize().toString();
        setSizeAndUnits(minSizeTextField, minSizeUnitsCombobox, savedMinSize);
        minSizeCheckbox.setSelected(!StringUtils.isBlank(minSizeTextField.getText()));
        minSizeTextField.setEnabled(minSizeCheckbox.isSelected());
        minSizeUnitsCombobox.setEnabled(minSizeCheckbox.isSelected());

        String savedMaxSize = rule.getMaxFileSize() == null ? "" : rule.getMaxFileSize().toString();
        setSizeAndUnits(maxSizeTextField, maxSizeUnitsCombobox, savedMaxSize);
        maxSizeCheckbox.setSelected(!StringUtils.isBlank(maxSizeTextField.getText()));
        maxSizeTextField.setEnabled(maxSizeCheckbox.isSelected());
        maxSizeUnitsCombobox.setEnabled(maxSizeCheckbox.isSelected());
    }

    /**
     * Update the OK button when contents of a field change
     */
    private void addDocumentListeners() {
        SwingUtilities.invokeLater(() -> {
            setOkButton();  //ensure initial state before listeners added is correct
        });
        DocumentListener docListener;
        docListener = new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                setOkButton();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                setOkButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                setOkButton();
            }
        };
        ruleNameTextField.getDocument().addDocumentListener(docListener);
        extensionsTextField.getDocument().addDocumentListener(docListener);
        fileNamesTextArea.getDocument().addDocumentListener(docListener);
        folderNamesTextArea.getDocument().addDocumentListener(docListener);
        minSizeTextField.getDocument().addDocumentListener(docListener);
        maxSizeTextField.getDocument().addDocumentListener(docListener);
        modifiedWithinTextField.getDocument().addDocumentListener(docListener);
    }

    /**
     * Initialize the text area and the scroll pane viewing it
     *
     * @param pane     the JScrollPane which will be viewing the JTextArea
     * @param textArea the JTextArea being initialized
     */
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

    /**
     * Convert a long value and a string representing its units to Bytes
     *
     * @param value the numerical value to convert
     * @param units the name of the units to convert to Kilobytes, Megabytes, or
     *              Gigabytes
     *
     * @return
     */
    private long convertToBytes(long value, String units) {
        long convertedValue = value;
        if (units.equals(Bundle.EditNonFullPathsRulePanel_units_gigabytes())) {
            convertedValue = convertedValue * BYTE_UNIT_CONVERSION * BYTE_UNIT_CONVERSION * BYTE_UNIT_CONVERSION;
        } else if (units.equals(Bundle.EditNonFullPathsRulePanel_units_megabytes())) {
            convertedValue = convertedValue * BYTE_UNIT_CONVERSION * BYTE_UNIT_CONVERSION;
        } else if (units.equals(Bundle.EditNonFullPathsRulePanel_units_kilobytes())) {
            convertedValue *= BYTE_UNIT_CONVERSION;
        }
        return convertedValue;
    }

    /**
     * Set the size and units for a specified sizeField and unitsComboBox
     *
     * @param sizeField     the size field to set
     * @param unitsComboBox the units combo box to set
     * @param value         the value as a string representation of the number
     *                      of Bytes
     */
    private void setSizeAndUnits(JTextField sizeField, JComboBox<String> unitsComboBox, String value) {
        if (StringUtils.isBlank(value)) {
            unitsComboBox.setSelectedItem(Bundle.EditNonFullPathsRulePanel_units_bytes());
            sizeField.setText("");
            return;
        }
        long longValue = Long.valueOf(value);
        if (longValue % BYTE_UNIT_CONVERSION != 0) {
            unitsComboBox.setSelectedItem(Bundle.EditNonFullPathsRulePanel_units_bytes());
            sizeField.setText(value);  //value stored in bytes is correct value to display
            return;
        }
        longValue /= BYTE_UNIT_CONVERSION;
        if (longValue % BYTE_UNIT_CONVERSION != 0) {
            unitsComboBox.setSelectedItem(Bundle.EditNonFullPathsRulePanel_units_kilobytes());
            sizeField.setText(String.valueOf(longValue));
            return;
        }
        longValue /= BYTE_UNIT_CONVERSION;
        if (longValue % BYTE_UNIT_CONVERSION != 0) {
            unitsComboBox.setSelectedItem(Bundle.EditNonFullPathsRulePanel_units_megabytes());
            sizeField.setText(String.valueOf(longValue));
            return;
        }
        longValue /= BYTE_UNIT_CONVERSION;
        unitsComboBox.setSelectedItem(Bundle.EditNonFullPathsRulePanel_units_gigabytes());
        sizeField.setText(String.valueOf(longValue));

    }

    /**
     * Set the modified within X days field
     *
     * @param minDays the number of days to include
     */
    private void setModifiedWithin(Integer minDays) {
        modifiedWithinTextField.setText(minDays == null ? "" : minDays.toString());
        modifiedWithinCheckbox.setSelected(!StringUtils.isBlank(modifiedWithinTextField.getText()));
        modifiedWithinTextField.setEnabled(modifiedWithinCheckbox.isSelected());
    }

    /**
     * Set the contents of a text area to display a list of Strings
     *
     * @param textArea the text area to set the text of
     * @param list     the list of Strings to display in the text area
     */
    private void setTextArea(JTextArea textArea, List<String> list) {
        String text = "";
        if (list != null) {
            text = list.stream().map((s) -> s + System.getProperty("line.separator")).reduce(text, String::concat); // NON-NLS
        }
        textArea.setText(text);
    }

    /**
     * Set the extensions textField to display a list of extensions
     *
     * @param extensions the list of extensions to display
     */
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
        extensionsCheckbox.setSelected(!StringUtils.isBlank(content));
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
        modifiedWithinTextField = new javax.swing.JFormattedTextField();
        userFolderNote = new javax.swing.JLabel();
        minSizeCheckbox = new javax.swing.JCheckBox();
        maxSizeCheckbox = new javax.swing.JCheckBox();
        modifiedWithinCheckbox = new javax.swing.JCheckBox();
        folderNamesCheckbox = new javax.swing.JCheckBox();
        fileNamesCheckbox = new javax.swing.JCheckBox();
        extensionsCheckbox = new javax.swing.JCheckBox();
        minSizeUnitsCombobox = new javax.swing.JComboBox<>();
        maxSizeUnitsCombobox = new javax.swing.JComboBox<>();
        jSeparator1 = new javax.swing.JSeparator();
        jSeparator2 = new javax.swing.JSeparator();
        descriptionLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        extensionsInfoLabel = new javax.swing.JLabel();
        fileNamesInfoLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(daysIncludedLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.daysIncludedLabel.text")); // NOI18N

        shouldSaveCheckBox.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(shouldSaveCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldSaveCheckBox.text")); // NOI18N
        shouldSaveCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shouldSaveCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(shouldAlertCheckBox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.text")); // NOI18N
        shouldAlertCheckBox.setActionCommand(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.shouldAlertCheckBox.actionCommand")); // NOI18N
        shouldAlertCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                shouldAlertCheckBoxActionPerformed(evt);
            }
        });

        extensionsTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ruleNameLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.ruleNameLabel.text")); // NOI18N
        ruleNameLabel.setPreferredSize(new java.awt.Dimension(112, 14));

        filenamesScrollPane.setToolTipText(org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.filenamesScrollPane.toolTipText")); // NOI18N
        filenamesScrollPane.setEnabled(false);

        folderNamesScrollPane.setEnabled(false);

        minSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new DefaultToEmptyNumberFormatter(new java.text.DecimalFormat("#,###; "))));
        minSizeTextField.setEnabled(false);

        maxSizeTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new DefaultToEmptyNumberFormatter(new java.text.DecimalFormat("#,###; "))));
        maxSizeTextField.setEnabled(false);

        modifiedWithinTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new DefaultToEmptyNumberFormatter(new java.text.DecimalFormat("#,###; "))));
        modifiedWithinTextField.setEnabled(false);

        userFolderNote.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(userFolderNote, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.userFolderNote.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(minSizeCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.minSizeCheckbox.text")); // NOI18N
        minSizeCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        minSizeCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                minSizeCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(maxSizeCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.maxSizeCheckbox.text")); // NOI18N
        maxSizeCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        maxSizeCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                maxSizeCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(modifiedWithinCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.modifiedWithinCheckbox.text")); // NOI18N
        modifiedWithinCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        modifiedWithinCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                modifiedWithinCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(folderNamesCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.folderNamesCheckbox.text")); // NOI18N
        folderNamesCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        folderNamesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                folderNamesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileNamesCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.fileNamesCheckbox.text")); // NOI18N
        fileNamesCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        fileNamesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileNamesCheckboxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(extensionsCheckbox, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsCheckbox.text")); // NOI18N
        extensionsCheckbox.setPreferredSize(new java.awt.Dimension(112, 23));
        extensionsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                extensionsCheckboxActionPerformed(evt);
            }
        });

        minSizeUnitsCombobox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.EditNonFullPathsRulePanel_units_bytes(), Bundle.EditNonFullPathsRulePanel_units_kilobytes(), Bundle.EditNonFullPathsRulePanel_units_megabytes(), Bundle.EditNonFullPathsRulePanel_units_gigabytes()}));
        minSizeUnitsCombobox.setEnabled(false);

        maxSizeUnitsCombobox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.EditNonFullPathsRulePanel_units_bytes(), Bundle.EditNonFullPathsRulePanel_units_kilobytes(), Bundle.EditNonFullPathsRulePanel_units_megabytes(), Bundle.EditNonFullPathsRulePanel_units_gigabytes()}));
        maxSizeUnitsCombobox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.descriptionLabel.text")); // NOI18N
        descriptionLabel.setPreferredSize(new java.awt.Dimension(112, 14));

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.jLabel1.text")); // NOI18N

        extensionsInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(extensionsInfoLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.extensionsInfoLabel.text")); // NOI18N

        fileNamesInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/info-icon-16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(fileNamesInfoLabel, org.openide.util.NbBundle.getMessage(EditNonFullPathsRulePanel.class, "EditNonFullPathsRulePanel.fileNamesInfoLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSeparator2)
                    .addComponent(jSeparator1)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(shouldAlertCheckBox)
                            .addComponent(shouldSaveCheckBox)
                            .addComponent(fileNamesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 112, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                .addComponent(modifiedWithinCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addComponent(maxSizeCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addComponent(jLabel1, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(extensionsCheckbox, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(ruleNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(minSizeCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(folderNamesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(0, 0, 0)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ruleNameTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(descriptionTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(extensionsTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(folderNamesScrollPane)
                            .addComponent(filenamesScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(fileNamesInfoLabel)
                                    .addComponent(extensionsInfoLabel)
                                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                        .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 522, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(userFolderNote)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(minSizeUnitsCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addGroup(layout.createSequentialGroup()
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                                    .addComponent(modifiedWithinTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                    .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 63, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                    .addComponent(maxSizeUnitsCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, 110, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                    .addComponent(daysIncludedLabel))))))
                                .addGap(0, 11, Short.MAX_VALUE)))))
                .addContainerGap())
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {maxSizeTextField, minSizeTextField, modifiedWithinTextField});

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {descriptionLabel, extensionsCheckbox, fileNamesCheckbox, folderNamesCheckbox, maxSizeCheckbox, minSizeCheckbox, modifiedWithinCheckbox, ruleNameLabel});

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {maxSizeUnitsCombobox, minSizeUnitsCombobox});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(ruleNameLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ruleNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(descriptionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(descriptionLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(extensionsTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(extensionsCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(extensionsInfoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filenamesScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(fileNamesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fileNamesInfoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(folderNamesScrollPane)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(folderNamesCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(userFolderNote, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(minSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(minSizeCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(minSizeUnitsCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(maxSizeTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(maxSizeCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(maxSizeUnitsCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(daysIncludedLabel)
                    .addComponent(modifiedWithinTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(modifiedWithinCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(shouldSaveCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(shouldAlertCheckBox)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void extensionsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_extensionsCheckboxActionPerformed
        if (fileNamesCheckbox.isSelected() && extensionsCheckbox.isSelected()) {
            fileNamesCheckbox.setSelected(false);
        }
        updateExclusiveConditions();
        setOkButton();
    }//GEN-LAST:event_extensionsCheckboxActionPerformed

    private void fileNamesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileNamesCheckboxActionPerformed
        if (fileNamesCheckbox.isSelected() && extensionsCheckbox.isSelected()) {
            extensionsCheckbox.setSelected(false);
        }
        updateExclusiveConditions();
        setOkButton();
    }//GEN-LAST:event_fileNamesCheckboxActionPerformed

    private void folderNamesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_folderNamesCheckboxActionPerformed
        folderNamesScrollPane.setEnabled(folderNamesCheckbox.isSelected());
        folderNamesTextArea.setEditable(folderNamesCheckbox.isSelected());
        folderNamesTextArea.setEnabled(folderNamesCheckbox.isSelected());
        updateTextAreaBackgroundColor(folderNamesTextArea);
        setOkButton();

    }//GEN-LAST:event_folderNamesCheckboxActionPerformed

    private void minSizeCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_minSizeCheckboxActionPerformed
        minSizeTextField.setEnabled(minSizeCheckbox.isSelected());
        minSizeUnitsCombobox.setEnabled(minSizeCheckbox.isSelected());
        setOkButton();
    }//GEN-LAST:event_minSizeCheckboxActionPerformed

    private void maxSizeCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_maxSizeCheckboxActionPerformed
        maxSizeTextField.setEnabled(maxSizeCheckbox.isSelected());
        maxSizeUnitsCombobox.setEnabled(maxSizeCheckbox.isSelected());
        setOkButton();
    }//GEN-LAST:event_maxSizeCheckboxActionPerformed

    private void modifiedWithinCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_modifiedWithinCheckboxActionPerformed
        modifiedWithinTextField.setEnabled(modifiedWithinCheckbox.isSelected());
        setOkButton();
    }//GEN-LAST:event_modifiedWithinCheckboxActionPerformed

    private void shouldSaveCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shouldSaveCheckBoxActionPerformed
        setOkButton();
    }//GEN-LAST:event_shouldSaveCheckBoxActionPerformed

    private void shouldAlertCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_shouldAlertCheckBoxActionPerformed
        setOkButton();
    }//GEN-LAST:event_shouldAlertCheckBoxActionPerformed

    /**
     * Update the background area of a JTextArea to reflect whether it is
     * enabled or not
     *
     * @param textArea the textArea to update the background color of
     */
    private static void updateTextAreaBackgroundColor(JTextArea textArea) {
        if (textArea.isEnabled()) {
            textArea.setBackground(Color.WHITE);
        } else {
            textArea.setBackground(DISABLED_COLOR);
        }
    }

    /**
     * Update the enabled status of conditions which are exclusive of each other
     * when either one is changed
     */
    private void updateExclusiveConditions() {
        extensionsTextField.setEnabled(extensionsCheckbox.isSelected());
        filenamesScrollPane.setEnabled(fileNamesCheckbox.isSelected());
        fileNamesTextArea.setEditable(fileNamesCheckbox.isSelected());
        fileNamesTextArea.setEnabled(fileNamesCheckbox.isSelected());
        updateTextAreaBackgroundColor(fileNamesTextArea);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel daysIncludedLabel;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JTextField descriptionTextField;
    private javax.swing.JCheckBox extensionsCheckbox;
    private javax.swing.JLabel extensionsInfoLabel;
    private javax.swing.JTextField extensionsTextField;
    private javax.swing.JCheckBox fileNamesCheckbox;
    private javax.swing.JLabel fileNamesInfoLabel;
    private javax.swing.JScrollPane filenamesScrollPane;
    private javax.swing.JCheckBox folderNamesCheckbox;
    private javax.swing.JScrollPane folderNamesScrollPane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JCheckBox maxSizeCheckbox;
    private javax.swing.JFormattedTextField maxSizeTextField;
    private javax.swing.JComboBox<String> maxSizeUnitsCombobox;
    private javax.swing.JCheckBox minSizeCheckbox;
    private javax.swing.JFormattedTextField minSizeTextField;
    private javax.swing.JComboBox<String> minSizeUnitsCombobox;
    private javax.swing.JCheckBox modifiedWithinCheckbox;
    private javax.swing.JFormattedTextField modifiedWithinTextField;
    private javax.swing.JLabel ruleNameLabel;
    private javax.swing.JTextField ruleNameTextField;
    private javax.swing.JCheckBox shouldAlertCheckBox;
    private javax.swing.JCheckBox shouldSaveCheckBox;
    private javax.swing.JLabel userFolderNote;
    // End of variables declaration//GEN-END:variables

    /**
     * Set the name description and should alert / should save checkboxes
     *
     * @param ruleName the name of the rule
     * @param rule     the LogicalImagerRule
     */
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
    void setOkButton() {
        if (this.okButton != null) {
            this.okButton.setEnabled(!StringUtils.isBlank(ruleNameTextField.getText()) && atLeastOneConditionSet() && (shouldAlertCheckBox.isSelected() || shouldSaveCheckBox.isSelected()));
        }
    }

    /**
     * Checks that at least one condition has been selected and set to have a
     * value
     *
     * @return true if at least one condition is set, false otherwise
     */
    private boolean atLeastOneConditionSet() {
        try {
            return (extensionsCheckbox.isSelected() && !StringUtils.isBlank(extensionsTextField.getText()) && !validateExtensions(extensionsTextField).isEmpty())
                    || (fileNamesCheckbox.isSelected() && !StringUtils.isBlank(fileNamesTextArea.getText()))
                    || (folderNamesCheckbox.isSelected() && !StringUtils.isBlank(folderNamesTextArea.getText()))
                    || (minSizeCheckbox.isSelected() && !StringUtils.isBlank(minSizeTextField.getText()) && isNonZeroLong(minSizeTextField.getValue()))
                    || (maxSizeCheckbox.isSelected() && !StringUtils.isBlank(maxSizeTextField.getText()) && isNonZeroLong(maxSizeTextField.getValue()))
                    || (modifiedWithinCheckbox.isSelected() && !StringUtils.isBlank(modifiedWithinTextField.getText()) && isNonZeroLong(modifiedWithinTextField.getValue()));
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Invalid contents of extensionsTextField", ex);
            return false;
        }
    }

    /**
     * Check that value could be a non zero long
     *
     * @param numberObject the object to check
     *
     * @return true if the value is a non-zero long
     */
    private boolean isNonZeroLong(Object numberObject) {
        Long value = 0L;
        try {
            if (numberObject instanceof Number) {
                value = ((Number) numberObject).longValue();
            }
        } catch (NumberFormatException ignored) {
            //The string was not a number, this method will return false becaue the value is still 0L
        }
        return (value != 0);
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

    /**
     * Convert the contents of this panel to a rule and return it as well as its
     * name.
     *
     * @return an ImmutablePair containing the name of the rule and the rule
     *
     * @throws IOException
     */
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
        "EditNonFullPathsRulePanel.maxFileSizeSmallerThanMinException=Maximum file size: {0} bytes must be bigger than minimum file size: {1} bytes",
        "EditNonFullPathsRulePanel.fileNames=File names",
        "EditNonFullPathsRulePanel.folderNames=Folder names",})
    ImmutablePair<String, LogicalImagerRule> toRule() throws IOException {
        String ruleName = EditRulePanel.validRuleName(ruleNameTextField.getText());
        List<String> folderNames = folderNamesCheckbox.isSelected() ? EditRulePanel.validateTextList(folderNamesTextArea, Bundle.EditNonFullPathsRulePanel_folderNames()) : null;

        LogicalImagerRule.Builder builder = new LogicalImagerRule.Builder();
        builder.getName(ruleName)
                .getDescription(descriptionTextField.getText())
                .getShouldAlert(shouldAlertCheckBox.isSelected())
                .getShouldSave(shouldSaveCheckBox.isSelected())
                .getPaths(folderNames);

        if (extensionsCheckbox.isSelected()) {
            builder.getExtensions(validateExtensions(extensionsTextField));
        } else if (fileNamesCheckbox.isSelected()) {
            builder.getFilenames(EditRulePanel.validateTextList(fileNamesTextArea, Bundle.EditNonFullPathsRulePanel_fileNames()));
        }

        int minDays;
        if (modifiedWithinCheckbox.isSelected() && !isBlank(modifiedWithinTextField.getText())) {
            try {
                modifiedWithinTextField.commitEdit();
                minDays = ((Number) modifiedWithinTextField.getValue()).intValue();
                if (minDays < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_modifiedDaysNotPositiveException());
                }
                builder.getMinDays(minDays);
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_modifiedDaysMustBeNumberException(ex.getMessage()), ex);
            }
        }

        long minFileSize = 0;
        if (minSizeCheckbox.isSelected() && !isBlank(minSizeTextField.getText())) {
            try {
                minSizeTextField.commitEdit();
                minFileSize = ((Number) minSizeTextField.getValue()).longValue();
                if (minFileSize < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_minFileSizeNotPositiveException());
                }
                minFileSize = convertToBytes(minFileSize, minSizeUnitsCombobox.getItemAt(minSizeUnitsCombobox.getSelectedIndex()));
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_minFileSizeMustBeNumberException(ex.getMessage()), ex);
            }
        }

        long maxFileSize = 0;
        if (maxSizeCheckbox.isSelected() && !isBlank(maxSizeTextField.getText())) {
            try {
                maxSizeTextField.commitEdit();
                maxFileSize = ((Number) maxSizeTextField.getValue()).longValue();
                if (maxFileSize < 0) {
                    throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeNotPositiveException());
                }
                maxFileSize = convertToBytes(maxFileSize, maxSizeUnitsCombobox.getItemAt(maxSizeUnitsCombobox.getSelectedIndex()));
            } catch (NumberFormatException | ParseException ex) {
                throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeMustBeNumberException(ex.getMessage()), ex);
            }
        }

        if (maxFileSize != 0 && (maxFileSize < minFileSize)) {
            throw new IOException(Bundle.EditNonFullPathsRulePanel_maxFileSizeSmallerThanMinException(maxFileSize, minFileSize));
        }
        if (minSizeCheckbox.isSelected() && minFileSize != 0) {
            builder.getMinFileSize(minFileSize);
        }
        if (maxSizeCheckbox.isSelected() && maxFileSize != 0) {
            builder.getMaxFileSize(maxFileSize);
        }

        LogicalImagerRule rule = builder.build();
        return new ImmutablePair<>(ruleName, rule);
    }

    /**
     * Validate the contents of the extensions textField contain a list of comma
     * seperated strings which could be file extensions
     *
     * @param textField the JTextField which contains the extensions
     *
     * @return a List containing a string for each possible extension specified.
     *
     * @throws IOException
     */
    @NbBundle.Messages({
        "EditNonFullPathsRulePanel.emptyExtensionException=Extensions cannot have an empty entry",})
    private List<String> validateExtensions(JTextField textField) throws IOException {
        if (isBlank(textField.getText())) {
            return null;
        }
        List<String> extensions = new ArrayList<>();
        for (String extension : textField.getText().split(",")) {
            String strippedExtension = strip(extension);
            if (strippedExtension.isEmpty()) {
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
