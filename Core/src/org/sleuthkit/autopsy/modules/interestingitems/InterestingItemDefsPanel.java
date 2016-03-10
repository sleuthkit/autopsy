/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import java.awt.EventQueue;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.apache.tika.mime.MediaType;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A panel that allows a user to make interesting item definitions.
 */
final class InterestingItemDefsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    @NbBundle.Messages({
        "InterestingItemDefsPanel.bytes=Bytes",
        "InterestingItemDefsPanel.kiloBytes=Kilobytes",
        "InterestingItemDefsPanel.megaBytes=Megabytes",
        "InterestingItemDefsPanel.gigaBytes=Gigabytes",
        "InterestingItemsDefsPanel.loadError=Error loading interesting files sets from file.",
        "InterestingItemsDefsPanel.saveError=Error saving interesting files sets to file."
    })

    private static final SortedSet<MediaType> mediaTypes = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes();
    private final DefaultListModel<FilesSet> setsListModel = new DefaultListModel<>();
    private final DefaultListModel<FilesSet.Rule> rulesListModel = new DefaultListModel<>();
    private final Logger logger = Logger.getLogger(InterestingItemDefsPanel.class.getName());
    private JButton okButton = new JButton("OK");
    private JButton cancelButton = new JButton("Cancel");

    // The following is a map of interesting files set names to interesting 
    // files set definitions. It is a snapshot of the files set definitions 
    // obtained from the interesting item definitions manager at the time the 
    // the panel is loaded. When the panel saves or stores its settings, these
    // definitions, possibly changed, are submitted back to the interesting item
    // definitions manager. Note that it is a tree map to aid in displaying
    // files sets in sorted order by name.
    private TreeMap<String, FilesSet> filesSets;

    /**
     * Constructs an interesting item definitions panel.
     */
    InterestingItemDefsPanel() {
        this.initComponents();
        this.customInit();
        this.setsList.setModel(setsListModel);
        this.setsList.addListSelectionListener(new InterestingItemDefsPanel.SetsListSelectionListener());
        this.rulesList.setModel(rulesListModel);
        this.rulesList.addListSelectionListener(new InterestingItemDefsPanel.RulesListSelectionListener());
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
        this.fileSizeUnitComboBox.setSelectedIndex(1);
        this.equalitySignComboBox.setSelectedIndex(2);
    }

    /**
     * @inheritDoc
     */
    @Override
    public void saveSettings() {
        try {
            InterestingItemDefsManager.getInstance().setInterestingFilesSets(this.filesSets);
        } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
            MessageNotifyUtil.Message.error(Bundle.InterestingItemsDefsPanel_saveError());
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void store() {
        this.saveSettings();
    }

    /**
     * @inheritDoc
     */
    @Override
    public void load() {
        this.resetComponents();

        try {
            // Get a working copy of the interesting files set definitions and sort
            // by set name.
            this.filesSets = new TreeMap<>(InterestingItemDefsManager.getInstance().getInterestingFilesSets());
        } catch (InterestingItemDefsManager.InterestingItemDefsManagerException ex) {
            MessageNotifyUtil.Message.error(Bundle.InterestingItemsDefsPanel_loadError());
            this.filesSets = new TreeMap<>();
        }

        // Populate the list model for the interesting files sets list 
        // component.
        for (FilesSet set : this.filesSets.values()) {
            this.setsListModel.addElement(set);
        }

        if (!this.filesSets.isEmpty()) {
            // Select the first files set by default. The list selections 
            // listeners will then populate the other components.
            EventQueue.invokeLater(() -> {
                InterestingItemDefsPanel.this.setsList.setSelectedIndex(0);
            });
        }
    }

    /**
     * Clears the list models and resets all of the components.
     */
    private void resetComponents() {
        this.resetRuleComponents();
        this.setsListModel.clear();
        this.setDescriptionTextArea.setText("");
        this.ignoreKnownFilesCheckbox.setSelected(true);
        this.newSetButton.setEnabled(true);
        this.editSetButton.setEnabled(false);
        this.deleteSetButton.setEnabled(false);
    }

    /**
     * Clears the rules list model and resets all of the rule-related
     * components.
     */
    private void resetRuleComponents() {
        this.fileNameTextField.setText("");
        this.fileNameRadioButton.setSelected(true);
        this.fileNameRegexCheckbox.setSelected(false);
        this.filesRadioButton.setSelected(true);
        this.rulePathConditionTextField.setText("");
        this.rulePathConditionRegexCheckBox.setSelected(false);
        this.newRuleButton.setEnabled(!this.setsListModel.isEmpty());
        this.editRuleButton.setEnabled(false);
        this.deleteRuleButton.setEnabled(false);
    }

    /**
     * A list events listener for the interesting files sets list component.
     */
    private final class SetsListSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }

            InterestingItemDefsPanel.this.rulesListModel.clear();
            InterestingItemDefsPanel.this.resetRuleComponents();

            // Get the selected interesting files set and populate the set
            // components.
            FilesSet selectedSet = InterestingItemDefsPanel.this.setsList.getSelectedValue();
            if (selectedSet != null) {
                // Populate the components that display the properties of the 
                // selected files set.
                InterestingItemDefsPanel.this.setDescriptionTextArea.setText(selectedSet.getDescription());
                InterestingItemDefsPanel.this.ignoreKnownFilesCheckbox.setSelected(selectedSet.ignoresKnownFiles());

                // Enable the new, edit and delete set buttons.
                InterestingItemDefsPanel.this.newSetButton.setEnabled(true);
                InterestingItemDefsPanel.this.editSetButton.setEnabled(true);
                InterestingItemDefsPanel.this.deleteSetButton.setEnabled(true);

                // Populate the rule definitions list, sorted by name.
                TreeMap<String, FilesSet.Rule> rules = new TreeMap<>(selectedSet.getRules());
                for (FilesSet.Rule rule : rules.values()) {
                    InterestingItemDefsPanel.this.rulesListModel.addElement(rule);
                }

                // Select the first rule by default.
                if (!InterestingItemDefsPanel.this.rulesListModel.isEmpty()) {
                    InterestingItemDefsPanel.this.rulesList.setSelectedIndex(0);
                }
            }
        }

    }

    /**
     * A list events listener for the interesting files set rules list
     * component.
     */
    private final class RulesListSelectionListener implements ListSelectionListener {

        @Override
        public void valueChanged(ListSelectionEvent e) {
            if (e.getValueIsAdjusting()) {
                return;
            }

            // Get the selected rule and populate the rule components.
            FilesSet.Rule rule = InterestingItemDefsPanel.this.rulesList.getSelectedValue();
            if (rule != null) {
                // Get the conditions that make up the rule.
                FilesSet.Rule.FileNameCondition nameCondition = rule.getFileNameCondition();
                FilesSet.Rule.MetaTypeCondition typeCondition = rule.getMetaTypeCondition();
                FilesSet.Rule.ParentPathCondition pathCondition = rule.getPathCondition();
                FilesSet.Rule.MimeTypeCondition mimeTypeCondition = rule.getMimeTypeCondition();
                FilesSet.Rule.FileSizeCondition fileSizeCondition = rule.getFileSizeCondition();

                // Populate the components that display the properties of the 
                // selected rule.
                if (nameCondition != null) {
                    InterestingItemDefsPanel.this.fileNameTextField.setText(nameCondition.getTextToMatch());
                    InterestingItemDefsPanel.this.fileNameRadioButton.setSelected(nameCondition instanceof FilesSet.Rule.FullNameCondition);
                    InterestingItemDefsPanel.this.fileNameExtensionRadioButton.setSelected(nameCondition instanceof FilesSet.Rule.ExtensionCondition);
                    InterestingItemDefsPanel.this.fileNameRegexCheckbox.setSelected(nameCondition.isRegex());
                } else {
                    InterestingItemDefsPanel.this.fileNameTextField.setText("");
                    InterestingItemDefsPanel.this.fileNameRadioButton.setSelected(true);
                    InterestingItemDefsPanel.this.fileNameExtensionRadioButton.setSelected(false);
                    InterestingItemDefsPanel.this.fileNameRegexCheckbox.setSelected(false);
                }
                switch (typeCondition.getMetaType()) {
                    case FILES:
                        InterestingItemDefsPanel.this.filesRadioButton.setSelected(true);
                        break;
                    case DIRECTORIES:
                        InterestingItemDefsPanel.this.dirsRadioButton.setSelected(true);
                        break;
                    case FILES_AND_DIRECTORIES:
                        InterestingItemDefsPanel.this.bothRadioButton.setSelected(true);
                        break;
                }
                if (pathCondition != null) {
                    InterestingItemDefsPanel.this.rulePathConditionTextField.setText(pathCondition.getTextToMatch());
                    InterestingItemDefsPanel.this.rulePathConditionRegexCheckBox.setSelected(pathCondition.isRegex());
                } else {
                    InterestingItemDefsPanel.this.rulePathConditionTextField.setText("");
                    InterestingItemDefsPanel.this.rulePathConditionRegexCheckBox.setSelected(false);
                }
                if (mimeTypeCondition != null) {
                    InterestingItemDefsPanel.this.mimeTypeComboBox.setSelectedItem(mimeTypeCondition.getMimeType());
                } else {
                    InterestingItemDefsPanel.this.mimeTypeComboBox.setSelectedIndex(0);
                }
                if (fileSizeCondition != null) {
                    InterestingItemDefsPanel.this.fileSizeUnitComboBox.setSelectedItem(fileSizeCondition.getUnit().getName());
                    InterestingItemDefsPanel.this.equalitySignComboBox.setSelectedItem(fileSizeCondition.getComparator().getSymbol());
                    InterestingItemDefsPanel.this.jSpinner1.setValue(fileSizeCondition.getSizeValue());
                } else {
                    InterestingItemDefsPanel.this.fileSizeUnitComboBox.setSelectedIndex(1);
                    InterestingItemDefsPanel.this.equalitySignComboBox.setSelectedIndex(2);
                    InterestingItemDefsPanel.this.jSpinner1.setValue(0);
                }

                // Enable the new, edit and delete rule buttons.
                InterestingItemDefsPanel.this.newRuleButton.setEnabled(true);
                InterestingItemDefsPanel.this.editRuleButton.setEnabled(true);
                InterestingItemDefsPanel.this.deleteRuleButton.setEnabled(true);
            } else {
                InterestingItemDefsPanel.this.resetRuleComponents();
            }
        }

    }

    /**
     * Display an interesting files set definition panel in a dialog box and
     * respond to user interactions with the dialog.
     *
     * @param selectedSet The currently selected files set, may be null to
     *                    indicate a new interesting files set definition is to
     *                    be created.
     */
    private void doFileSetsDialog(FilesSet selectedSet) {
        // Create a files set defintion panle.
        FilesSetPanel panel;
        if (selectedSet != null) {
            // Editing an existing set definition.
            panel = new FilesSetPanel(selectedSet);
        } else {
            // Creating a new set definition.
            panel = new FilesSetPanel();
        }

        // Do a dialog box with the files set panel until the user either enters 
        // a valid definition or cancels. Note that the panel gives the user
        // feedback when isValidDefinition() is called.
        int option = JOptionPane.OK_OPTION;
        do {
            option = JOptionPane.showConfirmDialog(null, panel, NbBundle.getMessage(FilesSetPanel.class, "FilesSetPanel.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        } while (option == JOptionPane.OK_OPTION && !panel.isValidDefinition());

        // While adding new ruleset(selectedSet == null), if rule set with same name already exists, do not add to the filesSets hashMap.
        // In case of editing an existing ruleset(selectedSet != null), following check is not performed.
        if (this.filesSets.containsKey(panel.getFilesSetName()) && selectedSet == null) {
            MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(),
                    "InterestingItemDefsPanel.doFileSetsDialog.duplicateRuleSet.text",
                    panel.getFilesSetName()));
            return;
        }

        if (option == JOptionPane.OK_OPTION) {
            Map<String, FilesSet.Rule> rules = new HashMap<>();
            if (selectedSet != null) {
                // Interesting file sets are immutable for thread safety,
                // so editing a files set definition is a replacement operation. 
                // Preserve the existing rules from the set being edited.
                rules.putAll(selectedSet.getRules());
            }
            this.replaceFilesSet(selectedSet, panel.getFilesSetName(), panel.getFilesSetDescription(), panel.getFileSetIgnoresKnownFiles(), rules);
        }
    }

    /**
     * Display an interesting files set membership rule definition panel in a
     * dialog box and respond to user interactions with the dialog.
     *
     * @param selectedRule The currently selected rule, may be null to indicate
     *                     a new rule definition is to be created.
     */
    private void doFilesSetRuleDialog(FilesSet.Rule selectedRule) {
        // Create a files set rule panel.
        FilesSetRulePanel panel;
        if (selectedRule != null) {
            // Editing an existing rule definition.
            panel = new FilesSetRulePanel(selectedRule, okButton, cancelButton);
        } else {
            // Creating a new rule definition.
            panel = new FilesSetRulePanel(okButton, cancelButton);
        }
        // Do a dialog box with the files set panel until the user either enters 
        // a valid definition or cancels. Note that the panel gives the user
        // feedback when isValidDefinition() is called.
        int option = JOptionPane.OK_OPTION;
        do {
            option = JOptionPane.showOptionDialog(null, panel, NbBundle.getMessage(FilesSetPanel.class, "FilesSetPanel.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{okButton, cancelButton}, okButton);
        } while (option == JOptionPane.OK_OPTION && !panel.isValidRuleDefinition());

        if (option == JOptionPane.OK_OPTION) {
            // Interesting file sets are immutable for thread safety,
            // so editing a files set rule definition is a replacement 
            // operation. Preserve the existing rules from the set being edited.
            FilesSet selectedSet = this.setsList.getSelectedValue();
            Map<String, FilesSet.Rule> rules = new HashMap<>(selectedSet.getRules());

            // Remove the "old" rule definition and add the new/edited 
            // definition.
            if (selectedRule != null) {
                rules.remove(selectedRule.getUuid());
            }
            FilesSet.Rule newRule = new FilesSet.Rule(panel.getRuleName(), panel.getFileNameCondition(), panel.getMetaTypeCondition(), panel.getPathCondition(), panel.getMimeTypeCondition(), panel.getFileSizeCondition());
            rules.put(newRule.getUuid(), newRule);

            // Add the new/edited files set definition, replacing any previous 
            // definition with the same name and refreshing the display.
            this.replaceFilesSet(selectedSet, selectedSet.getName(), selectedSet.getDescription(), selectedSet.ignoresKnownFiles(), rules);

            // Select the new/edited rule. Queue it up so it happens after the 
            // selection listeners react to the selection of the "new" files 
            // set.
            EventQueue.invokeLater(() -> {
                this.rulesList.setSelectedValue(newRule, true);
            });
        }
    }

    /**
     * Adds an interesting files set definition to the collection of definitions
     * owned by this panel. If there is a definition with the same name, it will
     * be replaced, so this is an add/edit operation.
     *
     * @param oldSet            A set to replace, null if the new set is not a
     *                          replacement.
     * @param name              The name of the files set.
     * @param description       The description of the files set.
     * @param ignoresKnownFiles Whether or not the files set ignores known
     *                          files.
     * @param rules             The set membership rules for the set.
     */
    void replaceFilesSet(FilesSet oldSet, String name, String description, boolean ignoresKnownFiles, Map<String, FilesSet.Rule> rules) {
        if (oldSet != null) {
            // Remove the set to be replaced from the working copy if the files
            // set definitions.
            this.filesSets.remove(oldSet.getName());
        }

        // Make the new/edited set definition and add it to the working copy of
        // the files set definitions.
        FilesSet newSet = new FilesSet(name, description, ignoresKnownFiles, rules);
        this.filesSets.put(newSet.getName(), newSet);

        // Redo the list model for the files set list component, which will make
        // everything stays sorted as in the working copy tree set.
        InterestingItemDefsPanel.this.setsListModel.clear();
        for (FilesSet set : this.filesSets.values()) {
            this.setsListModel.addElement(set);
        }

        // Select the new/edited files set definition in the set definitions 
        // list. This will cause the selection listeners to repopulate the 
        // subordinate components.
        this.setsList.setSelectedValue(newSet, true);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileNameButtonGroup = new javax.swing.ButtonGroup();
        typeButtonGroup = new javax.swing.ButtonGroup();
        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        newRuleButton = new javax.swing.JButton();
        filesRadioButton = new javax.swing.JRadioButton();
        editRuleButton = new javax.swing.JButton();
        rulesListLabel = new javax.swing.JLabel();
        rulesListScrollPane = new javax.swing.JScrollPane();
        rulesList = new javax.swing.JList<FilesSet.Rule>();
        setDescScrollPanel = new javax.swing.JScrollPane();
        setDescriptionTextArea = new javax.swing.JTextArea();
        editSetButton = new javax.swing.JButton();
        setsListScrollPane = new javax.swing.JScrollPane();
        setsList = new javax.swing.JList<FilesSet>();
        fileNameExtensionRadioButton = new javax.swing.JRadioButton();
        jLabel3 = new javax.swing.JLabel();
        fileNameTextField = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        fileNameRadioButton = new javax.swing.JRadioButton();
        rulePathConditionTextField = new javax.swing.JTextField();
        ignoreKnownFilesCheckbox = new javax.swing.JCheckBox();
        fileNameRegexCheckbox = new javax.swing.JCheckBox();
        separator = new javax.swing.JSeparator();
        setsListLabel = new javax.swing.JLabel();
        bothRadioButton = new javax.swing.JRadioButton();
        deleteSetButton = new javax.swing.JButton();
        deleteRuleButton = new javax.swing.JButton();
        newSetButton = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        dirsRadioButton = new javax.swing.JRadioButton();
        jLabel1 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        rulePathConditionRegexCheckBox = new javax.swing.JCheckBox();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextArea1 = new javax.swing.JTextArea();
        jLabel7 = new javax.swing.JLabel();
        mimeTypeComboBox = new javax.swing.JComboBox<String>();
        jLabel8 = new javax.swing.JLabel();
        equalitySignComboBox = new javax.swing.JComboBox<String>();
        jSpinner1 = new javax.swing.JSpinner();
        fileSizeUnitComboBox = new javax.swing.JComboBox<String>();

        setFont(getFont().deriveFont(getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jScrollPane1.setFont(jScrollPane1.getFont().deriveFont(jScrollPane1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jPanel1.setFont(jPanel1.getFont().deriveFont(jPanel1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jLabel6.setFont(jLabel6.getFont().deriveFont(jLabel6.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel6.text")); // NOI18N

        newRuleButton.setFont(newRuleButton.getFont().deriveFont(newRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        newRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newRuleButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.newRuleButton.text")); // NOI18N
        newRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newRuleButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(filesRadioButton);
        filesRadioButton.setFont(filesRadioButton.getFont().deriveFont(filesRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        filesRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(filesRadioButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.filesRadioButton.text")); // NOI18N
        filesRadioButton.setEnabled(false);

        editRuleButton.setFont(editRuleButton.getFont().deriveFont(editRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        editRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editRuleButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.editRuleButton.text")); // NOI18N
        editRuleButton.setEnabled(false);
        editRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRuleButtonActionPerformed(evt);
            }
        });

        rulesListLabel.setFont(rulesListLabel.getFont().deriveFont(rulesListLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(rulesListLabel, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.rulesListLabel.text")); // NOI18N

        rulesListScrollPane.setFont(rulesListScrollPane.getFont().deriveFont(rulesListScrollPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        rulesList.setFont(rulesList.getFont().deriveFont(rulesList.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        rulesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        rulesListScrollPane.setViewportView(rulesList);

        setDescScrollPanel.setFont(setDescScrollPanel.getFont().deriveFont(setDescScrollPanel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        setDescriptionTextArea.setEditable(false);
        setDescriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        setDescriptionTextArea.setColumns(20);
        setDescriptionTextArea.setFont(setDescriptionTextArea.getFont().deriveFont(setDescriptionTextArea.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        setDescriptionTextArea.setLineWrap(true);
        setDescriptionTextArea.setRows(2);
        setDescScrollPanel.setViewportView(setDescriptionTextArea);

        editSetButton.setFont(editSetButton.getFont().deriveFont(editSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        editSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editSetButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.editSetButton.text")); // NOI18N
        editSetButton.setEnabled(false);
        editSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSetButtonActionPerformed(evt);
            }
        });

        setsListScrollPane.setFont(setsListScrollPane.getFont().deriveFont(setsListScrollPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        setsList.setFont(setsList.getFont().deriveFont(setsList.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        setsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        setsListScrollPane.setViewportView(setsList);

        fileNameButtonGroup.add(fileNameExtensionRadioButton);
        fileNameExtensionRadioButton.setFont(fileNameExtensionRadioButton.getFont().deriveFont(fileNameExtensionRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(fileNameExtensionRadioButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.fileNameExtensionRadioButton.text")); // NOI18N
        fileNameExtensionRadioButton.setEnabled(false);

        jLabel3.setFont(jLabel3.getFont().deriveFont(jLabel3.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel3.text")); // NOI18N

        fileNameTextField.setEditable(false);
        fileNameTextField.setFont(fileNameTextField.getFont().deriveFont(fileNameTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        fileNameTextField.setText(org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.fileNameTextField.text")); // NOI18N
        fileNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileNameTextFieldActionPerformed(evt);
            }
        });

        jLabel5.setFont(jLabel5.getFont().deriveFont(jLabel5.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel5.text")); // NOI18N

        fileNameButtonGroup.add(fileNameRadioButton);
        fileNameRadioButton.setFont(fileNameRadioButton.getFont().deriveFont(fileNameRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(fileNameRadioButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.fileNameRadioButton.text")); // NOI18N
        fileNameRadioButton.setEnabled(false);

        rulePathConditionTextField.setEditable(false);
        rulePathConditionTextField.setFont(rulePathConditionTextField.getFont().deriveFont(rulePathConditionTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        rulePathConditionTextField.setText(org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.rulePathConditionTextField.text")); // NOI18N

        ignoreKnownFilesCheckbox.setFont(ignoreKnownFilesCheckbox.getFont().deriveFont(ignoreKnownFilesCheckbox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(ignoreKnownFilesCheckbox, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.ignoreKnownFilesCheckbox.text")); // NOI18N
        ignoreKnownFilesCheckbox.setEnabled(false);
        ignoreKnownFilesCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ignoreKnownFilesCheckboxActionPerformed(evt);
            }
        });

        fileNameRegexCheckbox.setFont(fileNameRegexCheckbox.getFont().deriveFont(fileNameRegexCheckbox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(fileNameRegexCheckbox, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.fileNameRegexCheckbox.text")); // NOI18N
        fileNameRegexCheckbox.setEnabled(false);

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);

        setsListLabel.setFont(setsListLabel.getFont().deriveFont(setsListLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(setsListLabel, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.setsListLabel.text")); // NOI18N

        typeButtonGroup.add(bothRadioButton);
        bothRadioButton.setFont(bothRadioButton.getFont().deriveFont(bothRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(bothRadioButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.bothRadioButton.text")); // NOI18N
        bothRadioButton.setEnabled(false);

        deleteSetButton.setFont(deleteSetButton.getFont().deriveFont(deleteSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        deleteSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteSetButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.deleteSetButton.text")); // NOI18N
        deleteSetButton.setEnabled(false);
        deleteSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSetButtonActionPerformed(evt);
            }
        });

        deleteRuleButton.setFont(deleteRuleButton.getFont().deriveFont(deleteRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        deleteRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteRuleButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.deleteRuleButton.text")); // NOI18N
        deleteRuleButton.setEnabled(false);
        deleteRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteRuleButtonActionPerformed(evt);
            }
        });

        newSetButton.setFont(newSetButton.getFont().deriveFont(newSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        newSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newSetButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.newSetButton.text")); // NOI18N
        newSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newSetButtonActionPerformed(evt);
            }
        });

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel2.text")); // NOI18N

        typeButtonGroup.add(dirsRadioButton);
        dirsRadioButton.setFont(dirsRadioButton.getFont().deriveFont(dirsRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(dirsRadioButton, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.dirsRadioButton.text")); // NOI18N
        dirsRadioButton.setEnabled(false);
        dirsRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dirsRadioButtonActionPerformed(evt);
            }
        });

        jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel1.text")); // NOI18N

        jLabel4.setFont(jLabel4.getFont().deriveFont(jLabel4.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel4.text")); // NOI18N

        rulePathConditionRegexCheckBox.setFont(rulePathConditionRegexCheckBox.getFont().deriveFont(rulePathConditionRegexCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(rulePathConditionRegexCheckBox, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.rulePathConditionRegexCheckBox.text")); // NOI18N
        rulePathConditionRegexCheckBox.setEnabled(false);

        jScrollPane2.setFont(jScrollPane2.getFont().deriveFont(jScrollPane2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jTextArea1.setEditable(false);
        jTextArea1.setBackground(new java.awt.Color(240, 240, 240));
        jTextArea1.setColumns(20);
        jTextArea1.setFont(jTextArea1.getFont().deriveFont(jTextArea1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(3);
        jTextArea1.setText(org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jTextArea1.text")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jScrollPane2.setViewportView(jTextArea1);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel7, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel7.text")); // NOI18N

        mimeTypeComboBox.setEditable(true);
        mimeTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] {""}));
        mimeTypeComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel8, org.openide.util.NbBundle.getMessage(InterestingItemDefsPanel.class, "InterestingItemDefsPanel.jLabel8.text")); // NOI18N

        equalitySignComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { "=", ">", "≥", "<", "≤" }));
        equalitySignComboBox.setEnabled(false);

        jSpinner1.setEnabled(false);

        fileSizeUnitComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.InterestingItemDefsPanel_bytes(), Bundle.InterestingItemDefsPanel_kiloBytes(), Bundle.InterestingItemDefsPanel_megaBytes(), Bundle.InterestingItemDefsPanel_gigaBytes() }));
        fileSizeUnitComboBox.setEnabled(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(setsListLabel)
                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(setsListScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 314, Short.MAX_VALUE)
                        .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(newSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(editSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, 89, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(deleteSetButton)))
                .addGap(18, 18, 18)
                .addComponent(separator, javax.swing.GroupLayout.PREFERRED_SIZE, 6, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(12, 12, 12)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addGap(27, 27, 27)
                        .addComponent(filesRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(dirsRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(bothRadioButton)
                        .addGap(27, 27, 27))))
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(380, 380, 380)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel3)
                                    .addComponent(jLabel7)
                                    .addComponent(jLabel8))
                                .addGap(6, 6, 6))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel4)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)))
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(rulePathConditionTextField, javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 83, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.Alignment.LEADING, 0, 245, Short.MAX_VALUE)
                            .addComponent(fileNameTextField)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(rulesListLabel))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(jLabel5))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(ignoreKnownFilesCheckbox))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(setDescScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 336, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(jLabel6))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(rulesListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 336, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(360, 360, 360)
                        .addComponent(newRuleButton)
                        .addGap(18, 18, 18)
                        .addComponent(editRuleButton)
                        .addGap(18, 18, 18)
                        .addComponent(deleteRuleButton))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(456, 456, 456)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(fileNameRadioButton)
                                .addGap(4, 4, 4)
                                .addComponent(fileNameExtensionRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(fileNameRegexCheckbox))
                            .addComponent(rulePathConditionRegexCheckBox))))
                .addGap(20, 20, 20))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(separator)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(setsListLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(setsListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 199, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(newSetButton)
                                    .addComponent(editSetButton)
                                    .addComponent(deleteSetButton)))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel6)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel5)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(setDescScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ignoreKnownFilesCheckbox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(rulesListLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rulesListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 64, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(newRuleButton)
                                    .addComponent(editRuleButton)
                                    .addComponent(deleteRuleButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel1)
                                .addGap(2, 2, 2)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel2)
                                    .addComponent(filesRadioButton)
                                    .addComponent(dirsRadioButton)
                                    .addComponent(bothRadioButton))
                                .addGap(16, 16, 16)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel3)
                                    .addComponent(fileNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(fileNameRadioButton)
                                    .addComponent(fileNameExtensionRadioButton)
                                    .addComponent(fileNameRegexCheckbox))))
                        .addGap(14, 14, 14)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(rulePathConditionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rulePathConditionRegexCheckBox)
                        .addGap(10, 10, 10)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel7)
                            .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(16, 16, 16)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel8)
                            .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jSpinner1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {deleteRuleButton, deleteSetButton, editRuleButton, editSetButton, newRuleButton, newSetButton});

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 728, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 34, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 503, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void dirsRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dirsRadioButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_dirsRadioButtonActionPerformed

    private void newSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSetButtonActionPerformed
        this.doFileSetsDialog(null);
    }//GEN-LAST:event_newSetButtonActionPerformed

    private void deleteRuleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteRuleButtonActionPerformed
        // Interesting file sets are immutable for thread safety,
        // so editing a files set rule definition is a replacement
        // operation. Preserve the existing rules from the set being
        // edited, except for the deleted rule.
        FilesSet oldSet = this.setsList.getSelectedValue();
        Map<String, FilesSet.Rule> rules = new HashMap<>(oldSet.getRules());
        FilesSet.Rule selectedRule = this.rulesList.getSelectedValue();
        rules.remove(selectedRule.getUuid());
        this.replaceFilesSet(oldSet, oldSet.getName(), oldSet.getDescription(), oldSet.ignoresKnownFiles(), rules);
    }//GEN-LAST:event_deleteRuleButtonActionPerformed

    private void deleteSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSetButtonActionPerformed
        FilesSet selectedSet = this.setsList.getSelectedValue();
        this.filesSets.remove(selectedSet.getName());
        this.setsListModel.removeElement(selectedSet);

        // Select the first of the remaining set definitions. This will cause
        // the selection listeners to repopulate the subordinate components.
        if (!this.filesSets.isEmpty()) {
            this.setsList.setSelectedIndex(0);
        } else {
            this.resetComponents();
        }
    }//GEN-LAST:event_deleteSetButtonActionPerformed

    private void ignoreKnownFilesCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ignoreKnownFilesCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ignoreKnownFilesCheckboxActionPerformed

    private void editSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSetButtonActionPerformed
        this.doFileSetsDialog(this.setsList.getSelectedValue());
    }//GEN-LAST:event_editSetButtonActionPerformed

    private void editRuleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editRuleButtonActionPerformed
        this.doFilesSetRuleDialog(this.rulesList.getSelectedValue());
    }//GEN-LAST:event_editRuleButtonActionPerformed

    private void newRuleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newRuleButtonActionPerformed
        this.doFilesSetRuleDialog(null);
    }//GEN-LAST:event_newRuleButtonActionPerformed

    private void fileNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileNameTextFieldActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileNameTextFieldActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton bothRadioButton;
    private javax.swing.JButton deleteRuleButton;
    private javax.swing.JButton deleteSetButton;
    private javax.swing.JRadioButton dirsRadioButton;
    private javax.swing.JButton editRuleButton;
    private javax.swing.JButton editSetButton;
    private javax.swing.JComboBox<String> equalitySignComboBox;
    private javax.swing.ButtonGroup fileNameButtonGroup;
    private javax.swing.JRadioButton fileNameExtensionRadioButton;
    private javax.swing.JRadioButton fileNameRadioButton;
    private javax.swing.JCheckBox fileNameRegexCheckbox;
    private javax.swing.JTextField fileNameTextField;
    private javax.swing.JComboBox<String> fileSizeUnitComboBox;
    private javax.swing.JRadioButton filesRadioButton;
    private javax.swing.JCheckBox ignoreKnownFilesCheckbox;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JSpinner jSpinner1;
    private javax.swing.JTextArea jTextArea1;
    private javax.swing.JComboBox<String> mimeTypeComboBox;
    private javax.swing.JButton newRuleButton;
    private javax.swing.JButton newSetButton;
    private javax.swing.JCheckBox rulePathConditionRegexCheckBox;
    private javax.swing.JTextField rulePathConditionTextField;
    private javax.swing.JList<FilesSet.Rule> rulesList;
    private javax.swing.JLabel rulesListLabel;
    private javax.swing.JScrollPane rulesListScrollPane;
    private javax.swing.JSeparator separator;
    private javax.swing.JScrollPane setDescScrollPanel;
    private javax.swing.JTextArea setDescriptionTextArea;
    private javax.swing.JList<FilesSet> setsList;
    private javax.swing.JLabel setsListLabel;
    private javax.swing.JScrollPane setsListScrollPane;
    private javax.swing.ButtonGroup typeButtonGroup;
    // End of variables declaration//GEN-END:variables

}
