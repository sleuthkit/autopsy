/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.apache.commons.lang3.tuple.Pair;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.guiutils.SimpleListCellRenderer;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.ingest.IngestProfiles;
import org.sleuthkit.autopsy.ingest.IngestProfiles.IngestProfile;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;

/**
 * A panel that allows a user to make interesting item definitions.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class FilesSetDefsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;

    @NbBundle.Messages({"# {0} - filter name",
        "# {1} - profile name",
        "FilesSetDefsPanel.ingest.fileFilterInUseError=The selected file filter, {0}, is being used by a profile, {1}, and cannot be deleted while any profile uses it.",
        "FilesSetDefsPanel.bytes=Bytes",
        "FilesSetDefsPanel.kiloBytes=Kilobytes",
        "FilesSetDefsPanel.megaBytes=Megabytes",
        "FilesSetDefsPanel.gigaBytes=Gigabytes",
        "FilesSetDefsPanel.loadError=Error loading interesting files sets from file.",
        "FilesSetDefsPanel.saveError=Error saving interesting files sets to file.",
        "FilesSetDefsPanel.interesting.copySetButton.text=Copy Set",
        "FilesSetDefsPanel.interesting.importSetButton.text=Import Set",
        "FilesSetDefsPanel.interesting.exportSetButton.text=Export Set"
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
    private final String filterDialogTitle;
    private final String ruleDialogTitle;
    private boolean canBeEnabled = true;
    
    private static final String XML_EXTENSION = "xml";
    
    private final JFileChooser importFileChooser;
    private static final String LAST_IMPORT_PATH_KEY = "InterestingFilesRuleSetLastImport";
    
    private final JFileChooser exportFileChooser;
    private static final String LAST_EXPORT_PATH_KEY = "InterestingFilesRuleSetLastExport";

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
            this.copySetButton.setVisible(false);
            this.importSetButton.setVisible(false);
            this.exportSetButton.setVisible(false);
            this.mimeTypeComboBox.setVisible(false);
            this.mimeTypeLabel.setVisible(false);
            this.filterDialogTitle = "FilesSetPanel.filter.title";
            this.ruleDialogTitle = "FilesSetPanel.rule.title";
            this.ignoreKnownFilesCheckbox.setVisible(false);
            this.fileTypeLabel.setVisible(false);
            this.filesRadioButton.setVisible(false);
            this.dirsRadioButton.setVisible(false);
            this.allRadioButton.setVisible(false);
            this.descriptionTextArea.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.jTextArea1.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(setsListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.setsListLabel.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(editSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.editSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(newSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.newSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(deleteSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.deleteSetButton.text")); // NOI18N
            org.openide.awt.Mnemonics.setLocalizedText(setDetailsLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingest.jLabel6.text")); // NOI18N
        } else {
            this.filterDialogTitle = "FilesSetPanel.interesting.title";
            this.ruleDialogTitle = "FilesSetPanel.interesting.title";
            this.ingoreUnallocCheckbox.setVisible(false);
        }

        IngestManager.getInstance().addIngestJobEventListener((ignored) -> {
            canBeEnabled
                    = !IngestManager.getInstance().isIngestRunning();
            enableButtons();
        });
        canBeEnabled = !IngestManager.getInstance().isIngestRunning();
        
        this.importFileChooser = new JFileChooser();
        this.exportFileChooser = new JFileChooser();
        configureFileChooser(importFileChooser);
        configureFileChooser(exportFileChooser);
    }
    
    /**
     * Configure the file chooser for rule set imports and exports.
     */
    private void configureFileChooser(JFileChooser fileChooser) {
        FileNameExtensionFilter autopsyFilter = new FileNameExtensionFilter(
                NbBundle.getMessage(this.getClass(), "FilesSetDefsPanel.interesting.fileExtensionFilterLbl"), XML_EXTENSION);
        fileChooser.addChoosableFileFilter(autopsyFilter);
        fileChooser.setAcceptAllFileFilterUsed(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    }

    @NbBundle.Messages({"FilesSetDefsPanel.Interesting.Title=Global Interesting Items Settings",
        "FilesSetDefsPanel.Ingest.Title=File Filter Settings"})
    private void customInit() {
        if (panelType == PANEL_TYPE.FILE_INGEST_FILTERS) {
            setName(Bundle.FilesSetDefsPanel_Ingest_Title());
        } else {
            setName(Bundle.FilesSetDefsPanel_Interesting_Title());
        }

        try {
            SortedSet<String> detectableMimeTypes = FileTypeDetector.getDetectedTypes();
            detectableMimeTypes.forEach((type) -> {
                mimeTypeComboBox.addItem(type);
            });
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Unable to get detectable file types", ex);
        }

        this.fileSizeUnitComboBox.setSelectedIndex(1);
        this.equalitySignComboBox.setSelectedIndex(0);
    }

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
            logger.log(Level.WARNING, Bundle.FilesSetDefsPanel_saveError(), ex);
        }
    }

    public void enableButtons() {
        FilesSet selectedFilesSet = this.setsList.getSelectedValue();
        boolean setSelected = (selectedFilesSet != null);
        boolean isStandardSet = (selectedFilesSet != null && selectedFilesSet.isStandardSet());

        boolean ruleSelected = (FilesSetDefsPanel.this.rulesList.getSelectedValue() != null);

        newRuleButton.setEnabled(canBeEnabled && setSelected && !isStandardSet);
        copySetButton.setEnabled(canBeEnabled && setSelected);
        newSetButton.setEnabled(canBeEnabled);
        editRuleButton.setEnabled(canBeEnabled && ruleSelected && !isStandardSet);
        editSetButton.setEnabled(canBeEnabled && setSelected && !isStandardSet);
        exportSetButton.setEnabled(setSelected);
        importSetButton.setEnabled(canBeEnabled);
        deleteRuleButton.setEnabled(canBeEnabled && ruleSelected && !isStandardSet);
        deleteSetButton.setEnabled(canBeEnabled && setSelected && !isStandardSet);
        ingestWarningLabel.setVisible(!canBeEnabled);
    }

    @Override
    public void store() {
        this.saveSettings();
    }

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
            logger.log(Level.WARNING, Bundle.FilesSetDefsPanel_loadError(), ex);
            this.filesSets = new TreeMap<>();
        }

        // Populate the list model for the interesting files sets list
        // component.
        this.filesSets.values().forEach((set) -> {
            this.setsListModel.addElement(set);
        });

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
        this.setsListModel.clear();
        this.setDescriptionTextArea.setText("");
        this.ignoreKnownFilesCheckbox.setSelected(true);
        this.ingoreUnallocCheckbox.setSelected(true);
        this.resetRuleComponents();
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
        this.daysIncludedTextField.setText("");
        this.rulePathConditionRegexCheckBox.setSelected(false);
        this.mimeTypeComboBox.setSelectedIndex(0);
        this.equalitySignComboBox.setSelectedIndex(0);
        this.fileSizeUnitComboBox.setSelectedIndex(1);
        this.fileSizeSpinner.setValue(0);
        enableButtons();
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
                // Populate the rule definitions list, sorted by name.
                List<FilesSet.Rule> rules = new ArrayList<>(selectedSet.getRules().values());
                Collections.sort(rules, new Comparator<FilesSet.Rule>() {
                    @Override
                    public int compare(FilesSet.Rule rule1, FilesSet.Rule rule2) {
                        return rule1.toString().compareTo(rule2.toString());
                    }
                });
                rules.forEach((rule) -> {
                    FilesSetDefsPanel.this.rulesListModel.addElement(rule);
                });
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
                FilesSet.Rule.DateCondition dateCondition = rule.getDateCondition();
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
                    FilesSetDefsPanel.this.equalitySignComboBox.setSelectedIndex(0);
                    FilesSetDefsPanel.this.fileSizeSpinner.setValue(0);
                }
                if (dateCondition != null) {
                    FilesSetDefsPanel.this.daysIncludedTextField.setText(Integer.toString(dateCondition.getDaysIncluded()));
                } else {
                    FilesSetDefsPanel.this.daysIncludedTextField.setText("");
                }
                enableButtons();
            } else {
                resetRuleComponents();
            }
        }

    }

    /**
     * Display an interesting files set definition panel in a dialog box and
     * respond to user interactions with the dialog.
     *
     * @param selectedSet     The currently selected files set, may be null to
     *                        indicate a new interesting files set definition is
     *                        to be created.
     * @param shouldCreateNew Wether this should be creating a new set or
     *                        replacing the selectedSet. False for edit, true
     *                        for copy or new.
     */
    private void doFileSetsDialog(FilesSet selectedSet, boolean shouldCreateNew) {
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
            option = JOptionPane.showConfirmDialog(this, panel, NbBundle.getMessage(FilesSetPanel.class, filterDialogTitle), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        } while (option == JOptionPane.OK_OPTION && !panel.isValidDefinition());

        if (option == JOptionPane.OK_OPTION) {
            Map<String, FilesSet.Rule> rules = new HashMap<>();
            if (selectedSet != null) {
                // Interesting file sets are immutable for thread safety,
                // so editing a files set definition is a replacement operation.
                // Preserve the existing rules from the set being edited.
                rules.putAll(selectedSet.getRules());
            }

            FilesSet filesSet = new FilesSet(
                    panel.getFilesSetName(),
                    panel.getFilesSetDescription(),
                    panel.getFileSetIgnoresKnownFiles(),
                    panel.getFileSetIgnoresUnallocatedSpace(),
                    rules
            );

            Pair<FilesSet, Integer> result = handleConflict(filesSet, false);
            option = result.getRight();
            FilesSet toAddOrUpdate = result.getLeft();

            if (result.getRight() == JOptionPane.OK_OPTION) {
                if (shouldCreateNew) {
                    this.replaceFilesSet(null, toAddOrUpdate, null);
                } else {
                    this.replaceFilesSet(selectedSet, toAddOrUpdate, null);
                }
            }
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
            option = JOptionPane.showOptionDialog(this, panel, NbBundle.getMessage(FilesSetPanel.class, ruleDialogTitle), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{okButton, cancelButton}, okButton);

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
            FilesSet.Rule newRule = new FilesSet.Rule(panel.getRuleName(), 
                    panel.getFileNameCondition(), panel.getMetaTypeCondition(), 
                    panel.getPathCondition(), panel.getMimeTypeCondition(), 
                    panel.getFileSizeCondition(), panel.getDateCondition(),
                    panel.isExclusive());
            rules.put(newRule.getUuid(), newRule);

            // Add the new/edited files set definition, replacing any previous
            // definition with the same name and refreshing the display.
            this.replaceFilesSet(selectedSet, selectedSet, rules);

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
     * @param newSet            The new set of rules.
     * @param rules             The set membership rules for the set. If null,
     *                          the rules in the new set will be used.
     */
    private void replaceFilesSet(FilesSet oldSet, FilesSet newSet, Map<String, FilesSet.Rule> rules) {
        if (oldSet != null) {
            // Remove the set to be replaced from the working copy if the files
            // set definitions.
            this.filesSets.remove(oldSet.getName());
        }

        FilesSet setToAdd = newSet;

        // Make the new/edited set definition and add it to the working copy of
        // the files set definitions.
        if (rules != null) {
            setToAdd = new FilesSet(
                    newSet.getName(),
                    newSet.getDescription(),
                    newSet.ignoresKnownFiles(),
                    newSet.ingoresUnallocatedSpace(),
                    rules,
                    newSet.isStandardSet(),
                    newSet.getVersionNumber()
            );
        }

        this.filesSets.put(setToAdd.getName(), setToAdd);

        // Redo the list model for the files set list component, which will make
        // everything stays sorted as in the working copy tree set.
        FilesSetDefsPanel.this.setsListModel.clear();
        this.filesSets.values().forEach((set) -> {
            this.setsListModel.addElement(set);
        });

        // Select the new/edited files set definition in the set definitions
        // list. This will cause the selection listeners to repopulate the
        // subordinate components.
        this.setsList.setSelectedValue(setToAdd, true);
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
        setDetailsLabel = new javax.swing.JLabel();
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
        nameLabel = new javax.swing.JLabel();
        fileNameTextField = new javax.swing.JTextField();
        descriptionLabel = new javax.swing.JLabel();
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
        fileTypeLabel = new javax.swing.JLabel();
        dirsRadioButton = new javax.swing.JRadioButton();
        ruleLabel = new javax.swing.JLabel();
        pathLabel = new javax.swing.JLabel();
        rulePathConditionRegexCheckBox = new javax.swing.JCheckBox();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextArea = new javax.swing.JTextArea();
        mimeTypeLabel = new javax.swing.JLabel();
        mimeTypeComboBox = new javax.swing.JComboBox<>();
        fileSizeLabel = new javax.swing.JLabel();
        equalitySignComboBox = new javax.swing.JComboBox<String>();
        fileSizeSpinner = new javax.swing.JSpinner();
        fileSizeUnitComboBox = new javax.swing.JComboBox<String>();
        ingoreUnallocCheckbox = new javax.swing.JCheckBox();
        ingestWarningLabel = new javax.swing.JLabel();
        copySetButton = new javax.swing.JButton();
        importSetButton = new javax.swing.JButton();
        exportSetButton = new javax.swing.JButton();
        modifiedDateLabel = new javax.swing.JLabel();
        daysIncludedTextField = new javax.swing.JTextField();
        daysIncludedLabel = new javax.swing.JLabel();

        org.openide.awt.Mnemonics.setLocalizedText(setDetailsLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.jLabel6.text")); // NOI18N

        newRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.newRuleButton.text")); // NOI18N
        newRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newRuleButtonActionPerformed(evt);
            }
        });

        typeButtonGroup.add(filesRadioButton);
        filesRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(filesRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.filesRadioButton.text")); // NOI18N
        filesRadioButton.setEnabled(false);

        editRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.editRuleButton.text")); // NOI18N
        editRuleButton.setEnabled(false);
        editRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editRuleButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(rulesListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulesListLabel.text")); // NOI18N

        rulesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        rulesListScrollPane.setViewportView(rulesList);
        rulesList.setCellRenderer(new SimpleListCellRenderer());

        setDescScrollPanel.setMinimumSize(new java.awt.Dimension(10, 22));
        setDescScrollPanel.setPreferredSize(new java.awt.Dimension(14, 40));

        setDescriptionTextArea.setEditable(false);
        setDescriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        setDescriptionTextArea.setColumns(20);
        setDescriptionTextArea.setLineWrap(true);
        setDescriptionTextArea.setRows(6);
        setDescriptionTextArea.setMinimumSize(new java.awt.Dimension(10, 22));
        setDescriptionTextArea.setOpaque(false);
        setDescScrollPanel.setViewportView(setDescriptionTextArea);

        editSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/edit16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(editSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.editSetButton.text")); // NOI18N
        editSetButton.setEnabled(false);
        editSetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        editSetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        editSetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        editSetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        editSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                editSetButtonActionPerformed(evt);
            }
        });

        setsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        setsListScrollPane.setViewportView(setsList);
        setsList.setCellRenderer(new SimpleListCellRenderer());

        fileNameButtonGroup.add(fileNameExtensionRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(fileNameExtensionRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameExtensionRadioButton.text")); // NOI18N
        fileNameExtensionRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(nameLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.nameLabel.text")); // NOI18N

        fileNameTextField.setEditable(false);
        fileNameTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(descriptionLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.descriptionLabel.text")); // NOI18N

        fileNameButtonGroup.add(fileNameRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(fileNameRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameRadioButton.text")); // NOI18N
        fileNameRadioButton.setEnabled(false);

        rulePathConditionTextField.setEditable(false);
        rulePathConditionTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulePathConditionTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(ignoreKnownFilesCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ignoreKnownFilesCheckbox.text")); // NOI18N
        ignoreKnownFilesCheckbox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(fileNameRegexCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileNameRegexCheckbox.text")); // NOI18N
        fileNameRegexCheckbox.setEnabled(false);
        fileNameRegexCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                fileNameRegexCheckboxActionPerformed(evt);
            }
        });

        separator.setOrientation(javax.swing.SwingConstants.VERTICAL);

        org.openide.awt.Mnemonics.setLocalizedText(setsListLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.setsListLabel.text")); // NOI18N

        typeButtonGroup.add(allRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(allRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.allRadioButton.text")); // NOI18N
        allRadioButton.setEnabled(false);

        deleteSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.deleteSetButton.text")); // NOI18N
        deleteSetButton.setEnabled(false);
        deleteSetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        deleteSetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        deleteSetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        deleteSetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        deleteSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteSetButtonActionPerformed(evt);
            }
        });

        deleteRuleButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/delete16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(deleteRuleButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.deleteRuleButton.text")); // NOI18N
        deleteRuleButton.setEnabled(false);
        deleteRuleButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteRuleButtonActionPerformed(evt);
            }
        });

        newSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/add16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(newSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.newSetButton.text")); // NOI18N
        newSetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        newSetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        newSetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        newSetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        newSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newSetButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(fileTypeLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileTypeLabel.text")); // NOI18N

        typeButtonGroup.add(dirsRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(dirsRadioButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.dirsRadioButton.text")); // NOI18N
        dirsRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ruleLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ruleLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.pathLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(rulePathConditionRegexCheckBox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.rulePathConditionRegexCheckBox.text")); // NOI18N
        rulePathConditionRegexCheckBox.setEnabled(false);

        descriptionTextArea.setEditable(false);
        descriptionTextArea.setBackground(new java.awt.Color(240, 240, 240));
        descriptionTextArea.setColumns(20);
        descriptionTextArea.setLineWrap(true);
        descriptionTextArea.setRows(3);
        descriptionTextArea.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.jTextArea1.text")); // NOI18N
        descriptionTextArea.setWrapStyleWord(true);
        descriptionTextArea.setOpaque(false);
        descriptionScrollPane.setViewportView(descriptionTextArea);

        org.openide.awt.Mnemonics.setLocalizedText(mimeTypeLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.mimeTypeLabel.text")); // NOI18N

        mimeTypeComboBox.setBackground(new java.awt.Color(240, 240, 240));
        mimeTypeComboBox.setEditable(true);
        mimeTypeComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] {""}));
        mimeTypeComboBox.setEnabled(false);
        mimeTypeComboBox.setMinimumSize(new java.awt.Dimension(0, 20));
        mimeTypeComboBox.setPreferredSize(new java.awt.Dimension(12, 20));

        org.openide.awt.Mnemonics.setLocalizedText(fileSizeLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.fileSizeLabel.text")); // NOI18N

        equalitySignComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { ">", "<" }));
        equalitySignComboBox.setEnabled(false);

        fileSizeSpinner.setEnabled(false);
        fileSizeSpinner.setMinimumSize(new java.awt.Dimension(2, 20));

        fileSizeUnitComboBox.setModel(new javax.swing.DefaultComboBoxModel<String>(new String[] { Bundle.FilesSetDefsPanel_bytes(), Bundle.FilesSetDefsPanel_kiloBytes(), Bundle.FilesSetDefsPanel_megaBytes(), Bundle.FilesSetDefsPanel_gigaBytes() }));
        fileSizeUnitComboBox.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(ingoreUnallocCheckbox, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingoreUnallocCheckbox.text")); // NOI18N
        ingoreUnallocCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingoreUnallocCheckbox.toolTipText")); // NOI18N
        ingoreUnallocCheckbox.setEnabled(false);

        ingestWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/hashdatabase/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestWarningLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.ingestWarningLabel.text")); // NOI18N

        copySetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/new16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(copySetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.copySetButton.text")); // NOI18N
        copySetButton.setEnabled(false);
        copySetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        copySetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        copySetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        copySetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        copySetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copySetButtonActionPerformed(evt);
            }
        });

        importSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/import16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(importSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.importSetButton.text")); // NOI18N
        importSetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        importSetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        importSetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        importSetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        importSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importSetButtonActionPerformed(evt);
            }
        });

        exportSetButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/export16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(exportSetButton, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.interesting.exportSetButton.text")); // NOI18N
        exportSetButton.setEnabled(false);
        exportSetButton.setMargin(new java.awt.Insets(2, 6, 2, 6));
        exportSetButton.setMaximumSize(new java.awt.Dimension(111, 25));
        exportSetButton.setMinimumSize(new java.awt.Dimension(111, 25));
        exportSetButton.setPreferredSize(new java.awt.Dimension(111, 25));
        exportSetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportSetButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(modifiedDateLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.modifiedDateLabel.text")); // NOI18N

        daysIncludedTextField.setEditable(false);
        daysIncludedTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        daysIncludedTextField.setText(org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.daysIncludedTextField.text")); // NOI18N
        daysIncludedTextField.setMinimumSize(new java.awt.Dimension(60, 20));
        daysIncludedTextField.setPreferredSize(new java.awt.Dimension(60, 20));

        org.openide.awt.Mnemonics.setLocalizedText(daysIncludedLabel, org.openide.util.NbBundle.getMessage(FilesSetDefsPanel.class, "FilesSetDefsPanel.daysIncludedLabel.text")); // NOI18N
        daysIncludedLabel.setEnabled(false);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(copySetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(importSetButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel1Layout.createSequentialGroup()
                                .addComponent(newSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(editSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(exportSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(deleteSetButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(setsListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 346, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 346, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(setsListLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(separator, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(rulesListScrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(setDescScrollPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(16, 16, 16)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(mimeTypeLabel)
                                    .addComponent(fileSizeLabel)
                                    .addComponent(fileTypeLabel)
                                    .addComponent(pathLabel)
                                    .addComponent(modifiedDateLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(nameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(rulePathConditionTextField)
                                    .addComponent(fileNameTextField, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(mimeTypeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(fileSizeSpinner, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(jPanel1Layout.createSequentialGroup()
                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(rulePathConditionRegexCheckBox)
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(daysIncludedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(daysIncludedLabel))
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(filesRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(dirsRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(allRadioButton))
                                            .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(fileNameRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(fileNameExtensionRadioButton)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(fileNameRegexCheckbox)))
                                        .addGap(0, 0, Short.MAX_VALUE)))))
                        .addGap(8, 8, 8))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(rulesListLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(ignoreKnownFilesCheckbox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ingoreUnallocCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 158, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(descriptionLabel)
                                    .addComponent(setDetailsLabel))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ingestWarningLabel))
                            .addComponent(ruleLabel)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(newRuleButton)
                                .addGap(18, 18, 18)
                                .addComponent(editRuleButton)
                                .addGap(18, 18, 18)
                                .addComponent(deleteRuleButton)))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {copySetButton, deleteSetButton, editSetButton, exportSetButton, importSetButton, newSetButton});

        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(separator)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(setDetailsLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(descriptionLabel)
                                .addGap(1, 1, 1))
                            .addComponent(ingestWarningLabel, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(setDescScrollPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 69, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(ignoreKnownFilesCheckbox)
                            .addComponent(ingoreUnallocCheckbox, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(rulesListLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rulesListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 61, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newRuleButton)
                            .addComponent(editRuleButton)
                            .addComponent(deleteRuleButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ruleLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(fileTypeLabel)
                            .addComponent(filesRadioButton)
                            .addComponent(dirsRadioButton)
                            .addComponent(allRadioButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(nameLabel)
                            .addComponent(fileNameTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(fileNameRadioButton)
                            .addComponent(fileNameExtensionRadioButton)
                            .addComponent(fileNameRegexCheckbox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(pathLabel)
                            .addComponent(rulePathConditionTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 20, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(rulePathConditionRegexCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(mimeTypeLabel)
                            .addComponent(mimeTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(fileSizeLabel)
                            .addComponent(equalitySignComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(fileSizeSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(fileSizeUnitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(modifiedDateLabel)
                            .addComponent(daysIncludedTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(daysIncludedLabel))
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(descriptionScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(setsListLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(setsListScrollPane)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(newSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(editSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(deleteSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(copySetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(importSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(exportSetButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(6, 6, 6))))
        );

        jPanel1Layout.linkSize(javax.swing.SwingConstants.VERTICAL, new java.awt.Component[] {copySetButton, deleteRuleButton, deleteSetButton, editRuleButton, editSetButton, exportSetButton, importSetButton, newRuleButton, newSetButton});

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 800, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void newSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newSetButtonActionPerformed
        this.doFileSetsDialog(null, true);
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
        this.replaceFilesSet(oldSet, oldSet, rules);
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
            for (IngestProfile profile : IngestProfiles.getIngestProfiles()) {
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
        this.doFileSetsDialog(this.setsList.getSelectedValue(), false);
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

    private void copySetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copySetButtonActionPerformed
        this.doFileSetsDialog(this.setsList.getSelectedValue(), true);
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_copySetButtonActionPerformed

    @NbBundle.Messages({       
        "FilesSetDefsPanel.interesting.failImportMsg=Interesting files set not imported",
        "FilesSetDefsPanel.interesting.fileExtensionFilterLbl=Autopsy Interesting File Set File (xml)",
        "FilesSetDefsPanel.interesting.importButtonAction.featureName=Interesting Files Set Import",
        "FilesSetDefsPanel.importSetButtonActionPerformed.noFilesSelected=No files sets were selected.",
        "FilesSetDefsPanel.importSetButtonActionPerformed.noFiles=No files sets were found in the selected files.",
        "# {0} - fileName",
        "# {1} - errorMessage",
        "FilesSetDefsPanel.importSetButtonActionPerformed.importError=The rules file \"{0}\" could not be read:\n{1}.",})
    private void importSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importSetButtonActionPerformed
        //save currently selected value as default value to select
        FilesSet selectedSet = this.setsList.getSelectedValue();
        
        File lastFolder = getLastUsedDirectory(LAST_IMPORT_PATH_KEY);
        importFileChooser.setCurrentDirectory(lastFolder);
        
        int returnVal = importFileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File selFile = importFileChooser.getSelectedFile();
            if (selFile == null) {
                JOptionPane.showMessageDialog(this,
                        Bundle.FilesSetDefsPanel_importSetButtonActionPerformed_noFilesSelected(),
                        Bundle.FilesSetDefsPanel_interesting_importButtonAction_featureName(),
                        JOptionPane.WARNING_MESSAGE);
                logger.warning("Selected file was null, when trying to import interesting files set definitions");
                return;
            }
            
            ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_IMPORT_PATH_KEY, selFile.getParent());
            
            Collection<FilesSet> importedSets;
            try {
                importedSets = InterestingItemsFilesSetSettings.readDefinitionsXML(selFile).values(); //read the xml from that path
                if (importedSets.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            Bundle.FilesSetDefsPanel_importSetButtonActionPerformed_noFiles(),
                            Bundle.FilesSetDefsPanel_interesting_importButtonAction_featureName(),
                            JOptionPane.WARNING_MESSAGE);
                    logger.log(Level.WARNING, "No Interesting files set definitions were read from the selected file");
                    return;
                }
            } catch (FilesSetsManager.FilesSetsManagerException ex) {
                JOptionPane.showMessageDialog(this,
                        Bundle.FilesSetDefsPanel_importSetButtonActionPerformed_importError(selFile.getName(), ex.getMessage()),
                        Bundle.FilesSetDefsPanel_interesting_importButtonAction_featureName(),
                        JOptionPane.WARNING_MESSAGE);
                logger.log(Level.WARNING, "No Interesting files set definitions were read from the selected file, exception", ex);
                return;
            }

            importedSets = importedSets
                    .stream()
                    .map((filesSet) -> StandardInterestingFilesSetsLoader.getAsStandardFilesSet(filesSet, false))
                    .collect(Collectors.toList());

            FilesSet newSelected = determineFilesToImport(importedSets);

            // Redo the list model for the files set list component
            FilesSetDefsPanel.this.setsListModel.clear();
            this.filesSets.values().forEach((set) -> {
                this.setsListModel.addElement(set);
            });
            // Select the new/edited files set definition in the set definitions
            // list. This will cause the selection listeners to repopulate the
            // subordinate components.
            this.setsList.setSelectedValue(newSelected == null ? selectedSet : newSelected, true);

            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }

    }//GEN-LAST:event_importSetButtonActionPerformed

    /**
     * Get the last used directory from ModuleSettings, using the value 
     * associated with the input key as the directory path.
     *
     * @param key The input key to search in module settings.
     * @return A directory instance if a value was found and the path is still
     * valid, or null otherwise.
     */
    private File getLastUsedDirectory(String key) {
        File lastFolder = null;
        if (ModuleSettings.settingExists(ModuleSettings.MAIN_SETTINGS, key)) {
            final String lastDirectory = ModuleSettings.getConfigSetting(ModuleSettings.MAIN_SETTINGS, key);
            File lastDirectoryFile = new File(lastDirectory);
            // Only select it if it exists.
            if (lastDirectoryFile.exists()) {
                lastFolder = lastDirectoryFile;
            }
        }
        return lastFolder;
    }
    
    /**
     * From the files sets that can be imported, this method rectifies any
     * conflicts that may occur.
     *
     * @param importedSets The sets to be imported.
     *
     * @return The files set to be selected or null if no items imported.
     */
    private FilesSet determineFilesToImport(Collection<FilesSet> importedSets) {
        FilesSet selectedSet = null;

        for (FilesSet set : importedSets) {
            Pair<FilesSet, Integer> conflictResult = handleConflict(set, true);
            int choice = conflictResult.getRight();
            FilesSet resultingFilesSet = conflictResult.getLeft();

            if (choice == JOptionPane.OK_OPTION) {
                selectedSet = resultingFilesSet;
                this.filesSets.put(resultingFilesSet.getName(), resultingFilesSet);
            } else if (choice == JOptionPane.CANCEL_OPTION) {
                break;
            }
        }

        return selectedSet;
    }

    /**
     * Handles any possible conflicts that may arise from importing a files set.
     *
     * @param set      The set to potentially import.
     * @param isImport The set with which to handle the conflict is being
     *                 imported, otherwise this is a set to be added from the
     *                 "New Set" button.
     *
     * @return A pair of the files set to be imported (or null if none) and the
     *         integer corresponding to the JOptionPane choice of the
     *         JOptionPane.YES_NO_CANCEL option.
     */
    private Pair<FilesSet, Integer> handleConflict(FilesSet set, boolean isImport) {
        FilesSet conflict = this.filesSets.get(set.getName());
        // if no conflict, return the files set as is with the option to proceed
        if (conflict == null) {
            return Pair.of(set, JOptionPane.OK_OPTION);
        }

        if (isImport) {
            if (conflict.isStandardSet()) {
                return onImportStandardSetConflict(set);
            } else {
                return onImportConflict(set);
            }
        } else {
            if (conflict.isStandardSet()) {
                return onNewEditSetStandardSetConflict(set);
            } else {
                return onNewEditSetConflict(set);
            }
        }

    }

    /**
     * When a user imports a files set and the files set name collides with a
     * pre-existing files set (not a standard files set), the user is prompted
     * for how they would like that handled (overwrite, skip, or cancel whole
     * operation)
     *
     * @param set The set to be imported.
     *
     * @return a pair of the files set and the JOptionPane.YES_NO_CANCEL option
     */
    @Messages({
        "FilesSetDefsPanel.yesOwMsg=Yes, overwrite",
        "FilesSetDefsPanel.noSkipMsg=No, skip",
        "FilesSetDefsPanel.cancelImportMsg=Cancel import",
        "# {0} - FilesSet name",
        "FilesSetDefsPanel.interesting.overwriteSetPrompt=Interesting files set \"{0}\" already exists locally, overwrite?",
        "FilesSetDefsPanel.interesting.importOwConflict=Import Interesting files set conflict",})
    private Pair<FilesSet, Integer> onImportConflict(FilesSet set) {
        // if there is a conflict, see if it is okay to overwrite.
        Object[] options = {
            Bundle.FilesSetDefsPanel_yesOwMsg(),
            Bundle.FilesSetDefsPanel_noSkipMsg(),
            Bundle.FilesSetDefsPanel_cancelImportMsg()
        };
        int conflictChoice = JOptionPane.showOptionDialog(this,
                Bundle.FilesSetDefsPanel_interesting_overwriteSetPrompt(set.getName()),
                Bundle.FilesSetDefsPanel_interesting_importOwConflict(),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (conflictChoice == JOptionPane.OK_OPTION) {
            // if so, just return the files set to be placed in the map overwriting what is currently present.
            return Pair.of(set, conflictChoice);
        }

        return Pair.of(null, conflictChoice);
    }

    /**
     * When a user imports a files set and the files set name collides with a
     * pre-existing standard files set, the user is prompted for how they would
     * like that handled (create files set with a " custom" suffix, skip, or
     * cancel whole operation)
     *
     * @param set The set to be imported.
     *
     * @return a pair of the files set and the JOptionPane.YES_NO_CANCEL option
     */
    @Messages({
        "FilesSetDefsPanel.yesStandardFileConflictCreate=Yes, create",
        "# {0} - FilesSet name",
        "# {1} - New FilesSet name",
        "FilesSetDefsPanel.interesting.standardFileConflict=A standard interesting file set already exists with the name \"{0}.\"  Would you like to rename your set to \"{1}?\"",})
    private Pair<FilesSet, Integer> onImportStandardSetConflict(FilesSet set) {
        // if there is a conflict and the conflicting files set is a standard files set,
        // see if allowing a custom files set is okay.
        Object[] options = {
            Bundle.FilesSetDefsPanel_yesStandardFileConflictCreate(),
            Bundle.FilesSetDefsPanel_noSkipMsg(),
            Bundle.FilesSetDefsPanel_cancelImportMsg()
        };
        
        String setName = set.getName();
        String customSetName = Bundle.StandardInterestingFileSetsLoader_customSuffixed(set.getName());
        
        int conflictChoice = JOptionPane.showOptionDialog(this,
                Bundle.FilesSetDefsPanel_interesting_standardFileConflict(setName, customSetName),
                Bundle.FilesSetDefsPanel_interesting_importOwConflict(),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        // if it is okay to create with custom prefix, try again to see if there is a conflict.
        if (conflictChoice == JOptionPane.OK_OPTION) {
            return handleConflict(StandardInterestingFilesSetsLoader.getAsCustomFileSet(set), true);
        }

        return Pair.of(null, conflictChoice);
    }

    /**
     * When a user creates a files set or edits a files set and the files set
     * name collides with a pre-existing files set (not a standard files set),
     * the user is prompted for how they would like that handled (overwrite or
     * cancel whole operation)
     *
     * @param set The set to be added.
     *
     * @return a pair of the files set and the JOptionPane.YES_NO_CANCEL option
     */
    @Messages({
        "FilesSetDefsPanel.cancelNewSetMsg=Cancel",
        "FilesSetDefsPanel.interesting.newOwConflict=Interesting files set conflict",})
    private Pair<FilesSet, Integer> onNewEditSetConflict(FilesSet set) {
        // if there is a conflict, see if it is okay to overwrite.
        Object[] options = {
            Bundle.FilesSetDefsPanel_yesOwMsg(),
            Bundle.FilesSetDefsPanel_cancelNewSetMsg()
        };
        int conflictChoice = JOptionPane.showOptionDialog(this,
                Bundle.FilesSetDefsPanel_interesting_overwriteSetPrompt(set.getName()),
                Bundle.FilesSetDefsPanel_interesting_newOwConflict(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (conflictChoice == JOptionPane.OK_OPTION) {
            // if so, just return the files set to be placed in the map overwriting what is currently present.
            return Pair.of(set, conflictChoice);
        }

        return Pair.of(null, conflictChoice);
    }

    /**
     * When a user creates a files set and the files set name collides with a
     * pre-existing standard files set, the user is prompted for how they would
     * like that handled (create files set with a " custom" suffix or cancel
     * whole operation)
     *
     * @param set The set to be adedd.
     *
     * @return a pair of the files set and the JOptionPane.YES_NO_CANCEL option
     */
    private Pair<FilesSet, Integer> onNewEditSetStandardSetConflict(FilesSet set) {
        // if there is a conflict and the conflicting files set is a standard files set,
        // see if allowing a custom files set is okay.
        Object[] options = {
            Bundle.FilesSetDefsPanel_yesStandardFileConflictCreate(),
            Bundle.FilesSetDefsPanel_cancelNewSetMsg()
        };
        
        String setName = set.getName();
        String customSetName = Bundle.StandardInterestingFileSetsLoader_customSuffixed(set.getName());
        
        int conflictChoice = JOptionPane.showOptionDialog(this,
                Bundle.FilesSetDefsPanel_interesting_standardFileConflict(setName, customSetName),
                Bundle.FilesSetDefsPanel_interesting_newOwConflict(),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        // if it is okay to create with custom prefix, try again to see if there is a conflict.
        if (conflictChoice == JOptionPane.OK_OPTION) {
            return handleConflict(StandardInterestingFilesSetsLoader.getAsCustomFileSet(set), false);
        }

        return Pair.of(null, conflictChoice);
    }

    @NbBundle.Messages({"FilesSetDefsPanel.interesting.exportButtonAction.featureName=Interesting Files Set Export",
        "# {0} - file name",
        "FilesSetDefsPanel.exportButtonActionPerformed.fileExistPrompt=File {0} exists, overwrite?",
        "FilesSetDefsPanel.interesting.ExportedMsg=Interesting files set exported",
        "FilesSetDefsPanel.interesting.failExportMsg=Export of interesting files set failed"})
    private void exportSetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportSetButtonActionPerformed
        //display warning that existing filessets with duplicate names will be overwritten
        //create file chooser to get xml filefinal String FEATURE_NAME = NbBundle.getMessage(this.getClass(),
        exportFileChooser.setSelectedFile(new File(this.setsList.getSelectedValue().getName()));
        
        final File lastDirectory = getLastUsedDirectory(LAST_EXPORT_PATH_KEY);
        exportFileChooser.setCurrentDirectory(lastDirectory);
        
        int returnVal = exportFileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final String FEATURE_NAME = NbBundle.getMessage(this.getClass(),
                    "FilesSetDefsPanel.interesting.exportButtonAction.featureName");
            File selFile = exportFileChooser.getSelectedFile();
            if (selFile == null) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(this.getClass(), "FilesSetDefsPanel.interesting.failExportMsg"),
                        FEATURE_NAME,
                        JOptionPane.WARNING_MESSAGE);
                logger.warning("Selected file was null, when trying to export interesting files set definitions");
                return;
            }
            
            ModuleSettings.setConfigSetting(ModuleSettings.MAIN_SETTINGS, LAST_EXPORT_PATH_KEY, selFile.getParent());
            
            //force append extension if not given
            String fileAbs = selFile.getAbsolutePath();
            if (!fileAbs.endsWith("." + XML_EXTENSION)) {
                fileAbs = fileAbs + "." + XML_EXTENSION;
                selFile = new File(fileAbs);
            }
            if (selFile.exists()) {
                //if the file already exists ask the user how to proceed
                final String FILE_EXISTS_MESSAGE = NbBundle.getMessage(this.getClass(),
                        "FilesSetDefsPanel.exportButtonActionPerformed.fileExistPrompt", selFile.getName());
                boolean shouldWrite = JOptionPane.showConfirmDialog(this, FILE_EXISTS_MESSAGE, FEATURE_NAME, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
                if (!shouldWrite) {
                    return;
                }
            }
            List<FilesSet> exportSets;
            exportSets = new ArrayList<>();
            //currently only exports selectedValue
            exportSets.add(this.setsList.getSelectedValue());
            boolean written = InterestingItemsFilesSetSettings.exportXmlDefinitionsFile(selFile, exportSets);
            if (written) {
                JOptionPane.showMessageDialog(
                        WindowManager.getDefault().getMainWindow(),
                        NbBundle.getMessage(this.getClass(), "FilesSetDefsPanel.interesting.ExportedMsg"),
                        FEATURE_NAME,
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        NbBundle.getMessage(this.getClass(), "FilesSetDefsPanel.interesting.failExportMsg"),
                        FEATURE_NAME,
                        JOptionPane.WARNING_MESSAGE);
                logger.warning("Export of interesting files set failed unable to write definitions xml file");
            }
        }
    }//GEN-LAST:event_exportSetButtonActionPerformed

    private void fileNameRegexCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_fileNameRegexCheckboxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_fileNameRegexCheckboxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allRadioButton;
    private javax.swing.JButton copySetButton;
    private javax.swing.JLabel daysIncludedLabel;
    private javax.swing.JTextField daysIncludedTextField;
    private javax.swing.JButton deleteRuleButton;
    private javax.swing.JButton deleteSetButton;
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextArea descriptionTextArea;
    private javax.swing.JRadioButton dirsRadioButton;
    private javax.swing.JButton editRuleButton;
    private javax.swing.JButton editSetButton;
    private javax.swing.JComboBox<String> equalitySignComboBox;
    private javax.swing.JButton exportSetButton;
    private javax.swing.ButtonGroup fileNameButtonGroup;
    private javax.swing.JRadioButton fileNameExtensionRadioButton;
    private javax.swing.JRadioButton fileNameRadioButton;
    private javax.swing.JCheckBox fileNameRegexCheckbox;
    private javax.swing.JTextField fileNameTextField;
    private javax.swing.JLabel fileSizeLabel;
    private javax.swing.JSpinner fileSizeSpinner;
    private javax.swing.JComboBox<String> fileSizeUnitComboBox;
    private javax.swing.JLabel fileTypeLabel;
    private javax.swing.JRadioButton filesRadioButton;
    private javax.swing.JCheckBox ignoreKnownFilesCheckbox;
    private javax.swing.JButton importSetButton;
    private javax.swing.JLabel ingestWarningLabel;
    private javax.swing.JCheckBox ingoreUnallocCheckbox;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JComboBox<String> mimeTypeComboBox;
    private javax.swing.JLabel mimeTypeLabel;
    private javax.swing.JLabel modifiedDateLabel;
    private javax.swing.JLabel nameLabel;
    private javax.swing.JButton newRuleButton;
    private javax.swing.JButton newSetButton;
    private javax.swing.JLabel pathLabel;
    private javax.swing.JLabel ruleLabel;
    private javax.swing.JCheckBox rulePathConditionRegexCheckBox;
    private javax.swing.JTextField rulePathConditionTextField;
    private javax.swing.JList<FilesSet.Rule> rulesList;
    private javax.swing.JLabel rulesListLabel;
    private javax.swing.JScrollPane rulesListScrollPane;
    private javax.swing.JSeparator separator;
    private javax.swing.JScrollPane setDescScrollPanel;
    private javax.swing.JTextArea setDescriptionTextArea;
    private javax.swing.JLabel setDetailsLabel;
    private javax.swing.JList<FilesSet> setsList;
    private javax.swing.JLabel setsListLabel;
    private javax.swing.JScrollPane setsListScrollPane;
    private javax.swing.ButtonGroup typeButtonGroup;
    // End of variables declaration//GEN-END:variables

}
