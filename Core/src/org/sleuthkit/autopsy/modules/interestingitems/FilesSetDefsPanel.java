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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.autopsy.ingest.IngestProfiles.IngestProfile;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * A panel that allows a user to make interesting item definitions.
 */
public final class FilesSetDefsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    @NbBundle.Messages({"# {0} - filter name",
        "# {1} - profile name",
        "FilesSetDefsPanel.ingest.fileFilterInUseError=The selected file filter, {0}, is being used by a profile, {1}, and cannot be deleted until after the profile.",
        "FilesSetDefsPanel.bytes=Bytes",
        "FilesSetDefsPanel.kiloBytes=Kilobytes",
        "FilesSetDefsPanel.megaBytes=Megabytes",
        "FilesSetDefsPanel.gigaBytes=Gigabytes",
        "FilesSetDefsPanel.loadError=Error loading interesting files sets from file.",
        "FilesSetDefsPanel.saveError=Error saving interesting files sets to file."
    })
    public static enum PANEL_TYPE {
        FILE_INGEST_FILTERS,
        INTERESTING_FILE_SETS

    }
    private final DefaultListModel<FilesSet> setsListModel = new DefaultListModel<>();
    private final DefaultListModel<FilesSet.Rule> rulesListModel = new DefaultListModel<>();
    private final Logger logger = Logger.getLogger(FilesSetDefsPanel.class.getName());
    private final JButton okButton = new JButton("OK");
    private final JButton cancelButton = new JButton("Cancel");
    private final PANEL_TYPE panelType;
    private final String ruleDialogTitle;
    private boolean canBeEnabled = true;

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
    public FilesSetDefsPanel(PANEL_TYPE panelType) {
        this.panelType = panelType;
        this.initComponents();
        this.customInit();
        this.setsList.setModel(setsListModel);
        this.setsList.addListSelectionListener(new FilesSetDefsPanel.SetsListSelectionListener());
        this.rulesList.setModel(rulesListModel);
        this.rulesList.addListSelectionListener(new FilesSetDefsPanel.RulesListSelectionListener());
        this.ingestWarningLabel.setVisible(false);
        if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {  //Hide the mimetype settings when this is displaying FileSet rules instead of interesting item rules
            this.mimeTypeComboBox.setVisible(false);
            this.jLabel7.setVisible(false);
            this.fileSizeUnitComboBox.setVisible(false);
            this.fileSizeSpinner.setVisible(false);
            this.ruleDialogTitle = "FilesSetPanel.ingest.title";

            this.jLabel8.setVisible(false);
            this.equalitySignComboBox.setVisible(false);
            this.ignoreKnownFilesCheckbox.setVisible(false);
            this.jLabel2.setVisible(false);
            this.filesRadioButton.setVisible(false);
            this.dirsRadioButton.setVisible(false);
            this.allRadioButton.setVisible(false);
            this.jTextArea1.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.jTextArea1.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(setsListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.setsListLabel.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(editSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.editSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(newSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.newSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(deleteSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.deleteSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.jLabel6.text")); // NOI18N
        } else {
            this.ruleDialogTitle = "FilesSetPanel.interesting.title";
            this.ingoreUnallocCheckbox.setVisible(false);

        }
    }

    @NbBundle.Messages({"FilesSetDefsPanel.Interesting.Title=Global Interesting Items Settings",
        "FilesSetDefsPanel.Ingest.Title=File Ingest Filter Settings"})
    private void customInit() {
        if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {
            setName(Bundle.FilesSetDefsPanel_Ingest_Title());
        } else {
            setName(Bundle.FilesSetDefsPanel_Interesting_Title());
        }

        Set<String> fileTypesCollated = new HashSet<>();
        for (String mediaType : FileTypeDetector.getStandardDetectedTypes()) {
            fileTypesCollated.add(mediaType);
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
            if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {
                FilesSetsManager.getInstance().setCustomFileIngestFilters(this.filesSets);
            } else {
                FilesSetsManager.getInstance().setInterestingFilesSets(this.filesSets);
            }

        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            MessageNotifyUtil.Message.error(Bundle.FilesSetDefsPanel_saveError());
        }
    }

    public void enableButtons(boolean isEnabled) {
        canBeEnabled = isEnabled;
        newRuleButton.setEnabled(isEnabled);
        newSetButton.setEnabled(isEnabled);
        editRuleButton.setEnabled(isEnabled);
        editSetButton.setEnabled(isEnabled);
        deleteRuleButton.setEnabled(isEnabled);
        deleteSetButton.setEnabled(isEnabled);
        ingestWarningLabel.setVisible(!isEnabled);
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
            if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {
                this.filesSets = new TreeMap<>(FilesSetsManager.getInstance().getCustomFileIngestFilters());
            } else {
                this.filesSets = new TreeMap<>(FilesSetsManager.getInstance().getInterestingFilesSets());
            }

        } catch (FilesSetsManager.FilesSetsManagerException ex) {
            MessageNotifyUtil.Message.error(Bundle.FilesSetDefsPanel_loadError());
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
                FilesSetDefsPanel.this.setsList.setSelectedIndex(0);
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
        this.ingoreUnallocCheckbox.setSelected(true);
        this.newSetButton.setEnabled(true && canBeEnabled);
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
        this.mimeTypeComboBox.setSelectedIndex(0);
        this.equalitySignComboBox.setSelectedIndex(2);
        this.fileSizeUnitComboBox.setSelectedIndex(1);
        this.fileSizeSpinner.setValue(0);
        this.newRuleButton.setEnabled(!this.setsListModel.isEmpty() && canBeEnabled);
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

            FilesSetDefsPanel.this.rulesListModel.clear();
            FilesSetDefsPanel.this.resetRuleComponents();

            // Get the selected interesting files set and populate the set
            // components.
            FilesSet selectedSet = FilesSetDefsPanel.this.setsList.getSelectedValue();
            if (selectedSet != null) {
                // Populate the components that display the properties of the
                // selected files set.
                FilesSetDefsPanel.this.setDescriptionTextArea.setText(selectedSet.getDescription());
                FilesSetDefsPanel.this.ignoreKnownFilesCheckbox.setSelected(selectedSet.ignoresKnownFiles());
                FilesSetDefsPanel.this.ingoreUnallocCheckbox.setSelected(selectedSet.ingoresUnallocatedSpace());
                // Enable the new, edit and delete set buttons.
                FilesSetDefsPanel.this.newSetButton.setEnabled(true && canBeEnabled);
                FilesSetDefsPanel.this.editSetButton.setEnabled(true && canBeEnabled);
                FilesSetDefsPanel.this.deleteSetButton.setEnabled(true && canBeEnabled);

                // Populate the rule definitions list, sorted by name.
                TreeMap<String, FilesSet.Rule> rules = new TreeMap<>(selectedSet.getRules());
                for (FilesSet.Rule rule : rules.values()) {
                    FilesSetDefsPanel.this.rulesListModel.addElement(rule);
                }

                // Select the first rule by default.
                if (!FilesSetDefsPanel.this.rulesListModel.isEmpty()) {
                    FilesSetDefsPanel.this.rulesList.setSelectedIndex(0);
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
            FilesSet.Rule rule = FilesSetDefsPanel.this.rulesList.getSelectedValue();
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
                    FilesSetDefsPanel.this.fileNameTextField.setText(nameCondition.getTextToMatch());
                    FilesSetDefsPanel.this.fileNameRadioButton.setSelected(nameCondition instanceof FilesSet.Rule.FullNameCondition);
                    FilesSetDefsPanel.this.fileNameExtensionRadioButton.setSelected(nameCondition instanceof FilesSet.Rule.ExtensionCondition);
                    FilesSetDefsPanel.this.fileNameRegexCheckbox.setSelected(nameCondition.isRegex());
                } else {
                    FilesSetDefsPanel.this.fileNameTextField.setText("");
                    FilesSetDefsPanel.this.fileNameRadioButton.setSelected(true);
                    FilesSetDefsPanel.this.fileNameExtensionRadioButton.setSelected(false);
                    FilesSetDefsPanel.this.fileNameRegexCheckbox.setSelected(false);
                }
                switch (typeCondition.getMetaType()) {
                    case FILES:
                        FilesSetDefsPanel.this.filesRadioButton.setSelected(true);
                        break;
                    case DIRECTORIES:
                        FilesSetDefsPanel.this.dirsRadioButton.setSelected(true);
                        break;
                    case FILES_AND_DIRECTORIES:
                        FilesSetDefsPanel.this.allRadioButton.setSelected(true);
                        break;
                }
                if (pathCondition != null) {
                    FilesSetDefsPanel.this.rulePathConditionTextField.setText(pathCondition.getTextToMatch());
                    FilesSetDefsPanel.this.rulePathConditionRegexCheckBox.setSelected(pathCondition.isRegex());
                } else {
                    FilesSetDefsPanel.this.rulePathConditionTextField.setText("");
                    FilesSetDefsPanel.this.rulePathConditionRegexCheckBox.setSelected(false);
                }
                if (mimeTypeCondition != null) {
                    FilesSetDefsPanel.this.mimeTypeComboBox.setSelectedItem(mimeTypeCondition.getMimeType());
                } else {
                    FilesSetDefsPanel.this.mimeTypeComboBox.setSelectedIndex(0);
                }
                if (fileSizeCondition != null) {
                    FilesSetDefsPanel.this.fileSizeUnitComboBox.setSelectedItem(fileSizeCondition.getUnit().getName());
                    FilesSetDefsPanel.this.equalitySignComboBox.setSelectedItem(fileSizeCondition.getComparator().getSymbol());
                    FilesSetDefsPanel.this.fileSizeSpinner.setValue(fileSizeCondition.getSizeValue());
                } else {
                    FilesSetDefsPanel.this.fileSizeUnitComboBox.setSelectedIndex(1);
                    FilesSetDefsPanel.this.equalitySignComboBox.setSelectedIndex(2);
                    FilesSetDefsPanel.this.fileSizeSpinner.setValue(0);
                }

                // Enable the new, edit and delete rule buttons.
                FilesSetDefsPanel.this.newRuleButton.setEnabled(true && canBeEnabled);
                FilesSetDefsPanel.this.editRuleButton.setEnabled(true && canBeEnabled);
                FilesSetDefsPanel.this.deleteRuleButton.setEnabled(true && canBeEnabled);
            } else {
                FilesSetDefsPanel.this.resetRuleComponents();
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
            panel = new FilesSetPanel(selectedSet, panelType);
        } else {
            // Creating a new set definition.
            panel = new FilesSetPanel(panelType);
        }

        // Do a dialog box with the files set panel until the user either enters
        // a valid definition or cancels. Note that the panel gives the user
        // feedback when isValidDefinition() is called.
        int option = JOptionPane.OK_OPTION;
        do {
            option = JOptionPane.showConfirmDialog(null, panel, NbBundle.getMessage(FilesSetPanel.class, ruleDialogTitle), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        } while (option == JOptionPane.OK_OPTION && !panel.isValidDefinition());

        // While adding new ruleset(selectedSet == null), if rule set with same name already exists, do not add to the filesSets hashMap.
        // In case of editing an existing ruleset(selectedSet != null), following check is not performed.
        if (this.filesSets.containsKey(panel.getFilesSetName()) && selectedSet == null) {
            MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(),
                    "FilesSetDefsPanel.doFileSetsDialog.duplicateRuleSet.text",
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
            this.replaceFilesSet(selectedSet, panel.getFilesSetName(), panel.getFilesSetDescription(), panel.getFileSetIgnoresKnownFiles(), panel.getFileSetIgnoresUnallocatedSpace(), rules);
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
            panel = new FilesSetRulePanel(selectedRule, okButton, cancelButton, panelType);
        } else {
            // Creating a new rule definition.
            panel = new FilesSetRulePanel(okButton, cancelButton, panelType);
        }
        // Do a dialog box with the files set panel until the user either enters
        // a valid definition or cancels. Note that the panel gives the user
        // feedback when isValidDefinition() is called.
        int option = JOptionPane.OK_OPTION;
        do {
            option = JOptionPane.showOptionDialog(null, panel, NbBundle.getMessage(FilesSetPanel.class, ruleDialogTitle), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{okButton, cancelButton}, okButton);

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
            this.replaceFilesSet(selectedSet, selectedSet.getName(), selectedSet.getDescription(), selectedSet.ignoresKnownFiles(), selectedSet.ingoresUnallocatedSpace(), rules);

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
     * @param oldSet                    A set to replace, null if the new set is
     *                                  not a replacement.
     * @param name                      The name of the files set.
     * @param description               The description of the files set.
     * @param ignoresKnownFiles         Whether or not the files set ignores
     *                                  known files.
     * @param rules                     The set membership rules for the set.
     * @param processesUnallocatedSpace Whether or not this set of rules
     *                                  processes unallocated space
     */
    void replaceFilesSet(FilesSet oldSet, String name, String description, boolean ignoresKnownFiles, boolean ignoresUnallocatedSpace, Map<String, FilesSet.Rule> rules) {
        if (oldSet != null) {
            // Remove the set to be replaced from the working copy if the files
            // set definitions.
            this.filesSets.remove(oldSet.getName());
        }

        // Make the new/edited set definition and add it to the working copy of
        // the files set definitions.
        FilesSet newSet = new FilesSet(name, description, ignoresKnownFiles, ignoresUnallocatedSpace, rules);
        this.filesSets.put(newSet.getName(), newSet);

        // Redo the list model for the files set list component, which will make
        // everything stays sorted as in the working copy tree set.
        FilesSetDefsPanel.this.setsListModel.clear();
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
        rulesList = new javax.swing.JList<>();
        setDescScrollPanel = new javax.swing.JScrollPane();
        setDescriptionTextArea = new javax.swing.JTextArea();
        editSetButton = new javax.swing.JButton();
        setsListScrollPane = new javax.swing.JScrollPane();
        setsList = new javax.swing.JList<>();
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
        allRadioButton = new javax.swing.JRadioButton();
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
        mimeTypeComboBox = new javax.swing.JComboBox<>();
        jLabel8 = new javax.swing.JLabel();
        equalitySignComboBox = new javax.swing.JComboBox<String>();
        fileSizeSpinner = new javax.swing.JSpinner();
        fileSizeUnitComboBox = new javax.swing.JComboBox<String>();
        ingoreUnallocCheckbox = new javax.swing.JCheckBox();
        ingestWarningLabel = new javax.swing.JLabel();

        setFont(getFont().deriveFont(getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jScrollPane1.setFont(jScrollPane1.getFont().deriveFont(jScrollPane1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jPanel1.setFont(jPanel1.getFont().deriveFont(jPanel1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jLabel6.setFont(jLabel6.getFont().deriveFont(jLabel6.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel6, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.jLabel6.text")); // NOI18N

        newRuleButton.setFont(newRuleButton.getFont().deriveFont(newRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        newRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.newRuleButton.text")); // NOI18N
        newRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newRuleButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(filesRadioButton);
        filesRadioButton.setFont(filesRadioButton.getFont().deriveFont(filesRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        filesRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(filesRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.filesRadioButton.text")); // NOI18N
        filesRadioButton.setEnabled(false);

        editRuleButton.setFont(editRuleButton.getFont().deriveFont(editRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        editRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.editRuleButton.text")); // NOI18N
        editRuleButton.setEnabled(false);
        editRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRuleButtonActionPerformed(evt);
            }
        });

        rulesListLabel.setFont(rulesListLabel.getFont().deriveFont(rulesListLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(rulesListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulesListLabel.text")); // NOI18N

        rulesListScrollPane.setFont(rulesListScrollPane.getFont().deriveFont(rulesListScrollPane.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        rulesList.setFont(rulesList.getFont().deriveFont(rulesList.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        rulesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        rulesListScrollPane.setViewportView(rulesList);

        setDescScrollPanel.setFont(setDescScrollPanel.getFont().deriveFont(setDescScrollPanel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        setDescScrollPanel.setMinimumSize(new java.awt.Dimension(10, 22));
        setDescScrollPanel.setPreferredSize(new java.awt.Dimension(14, 40));

        setDescriptionTextArea.setEditable(false);
        setDescriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        setDescriptionTextArea.setColumns(20);
        setDescriptionTextArea.setFont(setDescriptionTextArea.getFont().deriveFont(setDescriptionTextArea.getFont().getStyle() & ~java.awt.Font.BOLD, 13));
        setDescriptionTextArea.setLineWrap(true);
        setDescriptionTextArea.setRows(2);
        setDescriptionTextArea.setMinimumSize(new java.awt.Dimension(10, 22));
        setDescriptionTextArea.setPreferredSize(new java.awt.Dimension(14, 40));
        setDescScrollPanel.setViewportView(setDescriptionTextArea);

        editSetButton.setFont(editSetButton.getFont().deriveFont(editSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        editSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.editSetButton.text")); // NOI18N
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
        org.openide.awt.Mnemonics.setLocalizedText(fileNameExtensionRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameExtensionRadioButton.text")); // NOI18N
        fileNameExtensionRadioButton.setEnabled(false);

        jLabel3.setFont(jLabel3.getFont().deriveFont(jLabel3.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel3.text")); // NOI18N

        fileNameTextField.setEditable(false);
        fileNameTextField.setFont(fileNameTextField.getFont().deriveFont(fileNameTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        fileNameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameTextField.text")); // NOI18N

        jLabel5.setFont(jLabel5.getFont().deriveFont(jLabel5.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel5.text")); // NOI18N

        fileNameButtonGroup.add(fileNameRadioButton);
        fileNameRadioButton.setFont(fileNameRadioButton.getFont().deriveFont(fileNameRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(fileNameRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameRadioButton.text")); // NOI18N
        fileNameRadioButton.setEnabled(false);

        rulePathConditionTextField.setEditable(false);
        rulePathConditionTextField.setFont(rulePathConditionTextField.getFont().deriveFont(rulePathConditionTextField.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        rulePathConditionTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulePathConditionTextField.text")); // NOI18N

        ignoreKnownFilesCheckbox.setFont(ignoreKnownFilesCheckbox.getFont().deriveFont(ignoreKnownFilesCheckbox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(ignoreKnownFilesCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ignoreKnownFilesCheckbox.text")); // NOI18N
        ignoreKnownFilesCheckbox.setEnabled(false);

        fileNameRegexCheckbox.setFont(fileNameRegexCheckbox.getFont().deriveFont(fileNameRegexCheckbox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(fileNameRegexCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameRegexCheckbox.text")); // NOI18N
        fileNameRegexCheckbox.setEnabled(false);

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);

        setsListLabel.setFont(setsListLabel.getFont().deriveFont(setsListLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(setsListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.setsListLabel.text")); // NOI18N

        typeButtonGroup.add(allRadioButton);
        allRadioButton.setFont(allRadioButton.getFont().deriveFont(allRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(allRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.allRadioButton.text")); // NOI18N
        allRadioButton.setEnabled(false);

        deleteSetButton.setFont(deleteSetButton.getFont().deriveFont(deleteSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        deleteSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.deleteSetButton.text")); // NOI18N
        deleteSetButton.setEnabled(false);
        deleteSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSetButtonActionPerformed(evt);
            }
        });

        deleteRuleButton.setFont(deleteRuleButton.getFont().deriveFont(deleteRuleButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        deleteRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.deleteRuleButton.text")); // NOI18N
        deleteRuleButton.setEnabled(false);
        deleteRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteRuleButtonActionPerformed(evt);
            }
        });

        newSetButton.setFont(newSetButton.getFont().deriveFont(newSetButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        newSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.newSetButton.text")); // NOI18N
        newSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newSetButtonActionPerformed(evt);
            }
        });

        jLabel2.setFont(jLabel2.getFont().deriveFont(jLabel2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel2.text")); // NOI18N

        typeButtonGroup.add(dirsRadioButton);
        dirsRadioButton.setFont(dirsRadioButton.getFont().deriveFont(dirsRadioButton.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(dirsRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.dirsRadioButton.text")); // NOI18N
        dirsRadioButton.setEnabled(false);

        jLabel1.setFont(jLabel1.getFont().deriveFont(jLabel1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel1.text")); // NOI18N

        jLabel4.setFont(jLabel4.getFont().deriveFont(jLabel4.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel4.text")); // NOI18N

        rulePathConditionRegexCheckBox.setFont(rulePathConditionRegexCheckBox.getFont().deriveFont(rulePathConditionRegexCheckBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(rulePathConditionRegexCheckBox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulePathConditionRegexCheckBox.text")); // NOI18N
        rulePathConditionRegexCheckBox.setEnabled(false);

        jScrollPane2.setFont(jScrollPane2.getFont().deriveFont(jScrollPane2.getFont().getStyle() & ~java.awt.Font.BOLD, 11));

        jTextArea1.setEditable(false);
        jTextArea1.setBackground(new java.awt.Color(240, 240, 240));
        jTextArea1.setColumns(20);
        jTextArea1.setFont(jTextArea1.getFont().deriveFont(jTextArea1.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        jTextArea1.setLineWrap(true);
        jTextArea1.setRows(3);
        jTextArea1.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.jTextArea1.text")); // NOI18N
        jTextArea1.setWrapStyleWord(true);
        jScrollPane2.setViewportView(jTextArea1);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel7, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel7.text")); // NOI18N

        mimeTypeComboBox.setBackground(new java.awt.Color(240, 240, 240));
        mimeTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] {""}));
        mimeTypeComboBox.setEnabled(false);
        mimeTypeComboBox.setMinimumSize(new java.awt.Dimension(0, 20));
        mimeTypeComboBox.setPreferredSize(new java.awt.Dimension(12, 20));

        org.openide.awt.Mnemonics.setLocalizedText(jLabel8, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.jLabel8.text")); // NOI18N

        equalitySignComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { "=", ">", "≥", "<", "≤" }));
        equalitySignComboBox.setEnabled(false);

        fileSizeSpinner.setEnabled(false);
        fileSizeSpinner.setMinimumSize(new java.awt.Dimension(2, 20));

        fileSizeUnitComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.FilesSetDefsPanel_bytes(), Bundle.FilesSetDefsPanel_kiloBytes(), Bundle.FilesSetDefsPanel_megaBytes(), Bundle.FilesSetDefsPanel_gigaBytes() }));
        fileSizeUnitComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ingoreUnallocCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingoreUnallocCheckbox.text")); // NOI18N
        ingoreUnallocCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingoreUnallocCheckbox.toolTipText")); // NOI18N
        ingoreUnallocCheckbox.setEnabled(false);

        ingestWarningLabel.setFont(ingestWarningLabel.getFont().deriveFont(ingestWarningLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingestWarningLabel.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(setsListLabel)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 314, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(newSetButton)
                        .addGap(9, 9, 9)
                        .addComponent(editSetButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(deleteSetButton))
                    .addComponent(setsListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 344, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(separator, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(101, 101, 101)
                        .addComponent(filesRadioButton, javax.swing.GroupLayout.PREFERRED_SIZE, 47, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(dirsRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(allRadioButton))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(105, 105, 105)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(fileNameRadioButton)
                                .addGap(4, 4, 4)
                                .addComponent(fileNameExtensionRadioButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(fileNameRegexCheckbox))
                            .addComponent(rulePathConditionRegexCheckBox)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(rulesListLabel)
                            .addComponent(jLabel1)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(newRuleButton)
                                .addGap(18, 18, 18)
                                .addComponent(editRuleButton)
                                .addGap(18, 18, 18)
                                .addComponent(deleteRuleButton))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(ignoreKnownFilesCheckbox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ingoreUnallocCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 158, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel5)
                                    .addComponent(jLabel6))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ingestWarningLabel))))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGap(29, 29, 29)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel7)
                                    .addComponent(jLabel8))
                                .addGap(18, 18, 18))
                            .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(jPanel1Layout.createSequentialGroup()
                                    .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jLabel3)
                                        .addComponent(jLabel2))
                                    .addGap(6, 6, 6))
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                    .addComponent(jLabel4)
                                    .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED))))
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(mimeTypeComboBox, 0, 313, Short.MAX_VALUE)
                            .addComponent(rulePathConditionTextField)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 44, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileSizeSpinner, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(fileNameTextField)))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(setDescScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(rulesListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 414, Short.MAX_VALUE))))
                .addGap(18, 18, 18))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {deleteSetButton, editSetButton, newSetButton});

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
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(setsListLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(setsListScrollPane)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(newSetButton)
                                    .addComponent(editSetButton)
                                    .addComponent(deleteSetButton)))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(jLabel6)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel5)
                                        .addGap(1, 1, 1))
                                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                        .addComponent(ingestWarningLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)))
                                .addComponent(setDescScrollPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 45, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(6, 6, 6)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(ignoreKnownFilesCheckbox)
                                    .addComponent(ingoreUnallocCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(rulesListLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(rulesListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 64, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(newRuleButton)
                                    .addComponent(editRuleButton)
                                    .addComponent(deleteRuleButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel1)
                                .addGap(8, 8, 8)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel2)
                                    .addComponent(filesRadioButton)
                                    .addComponent(dirsRadioButton)
                                    .addComponent(allRadioButton))
                                .addGap(16, 16, 16)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel3)
                                    .addComponent(fileNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(fileNameRadioButton)
                                    .addComponent(fileNameExtensionRadioButton)
                                    .addComponent(fileNameRegexCheckbox))
                                .addGap(16, 16, 16)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel4)
                                    .addComponent(rulePathConditionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(7, 7, 7)
                                .addComponent(rulePathConditionRegexCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel7)
                                    .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                    .addComponent(jLabel8)
                                    .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGap(5, 5, 5)))
                        .addGap(5, 5, 5))))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {deleteRuleButton, deleteSetButton, editRuleButton, editSetButton, newRuleButton, newSetButton});

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void newSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSetButtonActionPerformed
        this.doFileSetsDialog(null);
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
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
        this.replaceFilesSet(oldSet, oldSet.getName(), oldSet.getDescription(), oldSet.ignoresKnownFiles(), oldSet.ingoresUnallocatedSpace(), rules);
        if (!this.rulesListModel.isEmpty()) {
            this.rulesList.setSelectedIndex(0);
        } else {
            this.resetRuleComponents();
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_deleteRuleButtonActionPerformed

    private void deleteSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteSetButtonActionPerformed
        FilesSet selectedSet = this.setsList.getSelectedValue();
        if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {
            for (IngestProfile profile : new IngestProfiles().getIngestProfiles()) {
                if (profile.getFileIngestFilter().equals(selectedSet.getName())) {
                    MessageNotifyUtil.Message.error(NbBundle.getMessage(this.getClass(),
                            "FilesSetDefsPanel.ingest.fileFilterInUseError",
                            selectedSet.getName(), profile.toString()));
                    return;
                }
            }

        }
        this.filesSets.remove(selectedSet.getName());
        this.setsListModel.removeElement(selectedSet);
        // Select the first of the remaining set definitions. This will cause
        // the selection listeners to repopulate the subordinate components.
        if (!this.filesSets.isEmpty()) {
            this.setsList.setSelectedIndex(0);
        } else {
            this.resetComponents();
        }
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_deleteSetButtonActionPerformed

    private void editSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editSetButtonActionPerformed
        this.doFileSetsDialog(this.setsList.getSelectedValue());
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_editSetButtonActionPerformed

    private void editRuleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editRuleButtonActionPerformed
        this.doFilesSetRuleDialog(this.rulesList.getSelectedValue());
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_editRuleButtonActionPerformed

    private void newRuleButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newRuleButtonActionPerformed
        this.doFilesSetRuleDialog(null);
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_newRuleButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allRadioButton;
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
    private javax.swing.JSpinner fileSizeSpinner;
    private javax.swing.JComboBox<String> fileSizeUnitComboBox;
    private javax.swing.JRadioButton filesRadioButton;
    private javax.swing.JCheckBox ignoreKnownFilesCheckbox;
    private javax.swing.JLabel ingestWarningLabel;
    private javax.swing.JCheckBox ingoreUnallocCheckbox;
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
