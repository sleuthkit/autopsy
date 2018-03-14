/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import com.github.lgooddatepicker.components.DateTimePicker;
import com.github.lgooddatepicker.optionalusertools.PickerUtilities;
import com.github.lgooddatepicker.components.TimePickerSettings;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.sleuthkit.autopsy.corecomponents.TextPrompt;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule.FileMIMETypeCondition;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule.FileSizeCondition;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule.FileSizeCondition.SizeUnit;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule.RelationalOp;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import java.util.logging.Level;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.ImageUtilities;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.experimental.autoingest.FileExportRuleSet.Rule.ArtifactCondition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.swing.DefaultListModel;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;

/**
 * Global settings panel for data-source-level ingest modules that export and
 * catalog files based on user-defined export rules.
 */
public final class FileExporterSettingsPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final JFileChooser rootDirectoryChooser = new JFileChooser();
    private final JFileChooser reportDirectoryChooser = new JFileChooser();
    private static final Logger logger = Logger.getLogger(FileExporterSettingsPanel.class.getName());
    private FileExportRuleSet exportRuleSet = new FileExportRuleSet("DefaultRuleSet"); //NON-NLS
    private Rule localRule = null; // this is the rule to compare against to see if things have changed
    private static final SortedSet<MediaType> mediaTypes = MimeTypes.getDefaultMimeTypes().getMediaTypeRegistry().getTypes();
    private static final String ROOTNODE = "Rules"; //NON-NLS
    private final JDialog jDialog;
    private DefaultMutableTreeNode rootNode;
    private final TreeSelectionModel treeSelectionModel;
    private final DefaultTreeModel defaultTreeModel;
    private final DefaultListModel<String> attributeListModel;
    private List<TreePath> expandedNodes = null;
    private Map<String, BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE> attributeTypeMap = new HashMap<>();
    private static final String UNSET = "Unset"; //NON-NLS
    private final javax.swing.event.ListSelectionListener listSelectionListener;
    private final javax.swing.event.TreeSelectionListener treeSelectionListener;
    private TimePickerSettings timeSettings = new TimePickerSettings();

    private enum ItemType {

        RULE_SET,
        RULE,
        MIME_TYPE_CLAUSE,
        SIZE_CLAUSE,
        ARTIFACT_CLAUSE
    }

    private class Item {

        private String name;
        private String parentRuleName;
        private ItemType itemType;

        Item(String name, String parentRuleName, ItemType itemType) {
            this.name = name;
            this.parentRuleName = parentRuleName;
            this.itemType = itemType;
        }

        /**
         * @return the name of the rule containing this Item
         */
        String getRuleName() {
            if (this.itemType == ItemType.RULE) {
                return this.name;
            } else {
                return this.parentRuleName;
            }
        }

        /**
         * @return the name
         */
        String getName() {
            return name;
        }

        /**
         * @param name the name to set
         */
        void setName(String name) {
            this.name = name;
        }

        /**
         * @return the parentRUleName
         */
        String getParentName() {
            return parentRuleName;
        }

        /**
         * @param parentName the parentRUleName to set
         */
        void setParentName(String parentName) {
            this.parentRuleName = parentName;
        }

        /**
         * @return the itemType
         */
        ItemType getItemType() {
            return itemType;
        }

        /**
         * @param itemType the itemType to set
         */
        void setItemType(ItemType itemType) {
            this.itemType = itemType;
        }

        @Override
        public String toString() {
            return this.getName();
        }
    }

    public FileExporterSettingsPanel(JDialog jDialog) {
        timeSettings.setFormatForDisplayTime(PickerUtilities.createFormatterFromPatternString("HH:mm:ss", timeSettings.getLocale()));
        timeSettings.setFormatForMenuTimes(PickerUtilities.createFormatterFromPatternString("HH:mm", timeSettings.getLocale()));

        initComponents();
        rootNode = new DefaultMutableTreeNode(new Item(ROOTNODE, ROOTNODE, ItemType.RULE_SET));
        trRuleList.setModel(new DefaultTreeModel(rootNode));
        this.jDialog = jDialog;

        attributeListModel = (DefaultListModel<String>) lsAttributeList.getModel();

        rootDirectoryChooser.setCurrentDirectory(rootDirectoryChooser.getFileSystemView().getParentDirectory(new File("C:\\"))); //NON-NLS
        rootDirectoryChooser.setAcceptAllFileFilterUsed(false);
        rootDirectoryChooser.setDialogTitle(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChooseRootDirectory"));
        rootDirectoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        reportDirectoryChooser.setCurrentDirectory(reportDirectoryChooser.getFileSystemView().getParentDirectory(new File("C:\\"))); //NON-NLS
        reportDirectoryChooser.setAcceptAllFileFilterUsed(false);
        reportDirectoryChooser.setDialogTitle(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChooseReportDirectory"));
        reportDirectoryChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        // Add text prompt to the text box fields
        TextPrompt textPromptRuleName = new TextPrompt(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.RuleName"), tbRuleName);
        textPromptRuleName.setForeground(Color.LIGHT_GRAY);
        textPromptRuleName.changeAlpha(0.9f);

        TextPrompt textPromptRootDirectory = new TextPrompt(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.RootDirectory"), tbRootDirectory);
        textPromptRootDirectory.setForeground(Color.LIGHT_GRAY);
        textPromptRootDirectory.changeAlpha(0.9f);

        TextPrompt textPromptReportDirectory = new TextPrompt(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ReportDirectory"), tbReportDirectory);
        textPromptReportDirectory.setForeground(Color.LIGHT_GRAY);
        textPromptReportDirectory.changeAlpha(0.9f);

        TextPrompt textPromptReportAttributeValue = new TextPrompt(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.AttributeValue"), tbAttributeValue);
        textPromptReportAttributeValue.setForeground(Color.LIGHT_GRAY);
        textPromptReportAttributeValue.changeAlpha(0.9f);

        for (SizeUnit item : SizeUnit.values()) {
            comboBoxFileSizeUnits.addItem(item.toString());
        }

        for (RelationalOp item : RelationalOp.values()) {
            comboBoxFileSizeComparison.addItem(item.getSymbol());
            comboBoxAttributeComparison.addItem(item.getSymbol());
        }

        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.INTEGER.getLabel());
        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.LONG.getLabel());
        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DOUBLE.getLabel());
        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.STRING.getLabel());
        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME.getLabel());
        comboBoxValueType.addItem(BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE.getLabel());
        comboBoxValueType.addItem(UNSET);

        load();
        trRuleList.setCellRenderer(new DefaultTreeCellRenderer() {
            private static final long serialVersionUID = 1L;
            private final ImageIcon ruleSetIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/ruleset-icon.png", false));
            private final ImageIcon ruleIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/extracted_content.png", false));
            private final ImageIcon sizeIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/file-size-16.png", false));
            private final ImageIcon artifactIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/artifact-icon.png", false));
            private final ImageIcon mimetypeIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/mime-icon.png", false));
            private final ImageIcon otherIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/knownbad-icon.png", false));

            @Override
            public Component getTreeCellRendererComponent(javax.swing.JTree tree,
                    Object value, boolean selected, boolean expanded,
                    boolean isLeaf, int row, boolean focused) {
                Component component = super.getTreeCellRendererComponent(tree, value,
                        selected, expanded, isLeaf, row, focused);
                Icon icon;
                switch (((Item) ((DefaultMutableTreeNode) value).getUserObject()).getItemType()) {
                    case ARTIFACT_CLAUSE:
                        icon = artifactIcon;
                        break;
                    case MIME_TYPE_CLAUSE:
                        icon = mimetypeIcon;
                        break;
                    case RULE:
                        icon = ruleIcon;
                        break;
                    case SIZE_CLAUSE:
                        icon = sizeIcon;
                        break;
                    case RULE_SET:
                        icon = ruleSetIcon;
                        break;
                    default:
                        icon = otherIcon;
                        break;
                }
                setIcon(icon);
                return component;
            }
        });
        populateMimeTypes();
        populateArtifacts();
        populateAttributes();
        populateRuleTree();

        tbRuleName.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                setSaveButton();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                setSaveButton();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                setSaveButton();
            }
        });

        comboBoxMimeValue.getEditor().getEditorComponent().addFocusListener(new java.awt.event.FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                comboBoxMimeValue.showPopup();
                comboBoxMimeValue.getEditor().selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // do nothing
            }
        });

        comboBoxArtifactName.getEditor().getEditorComponent().addFocusListener(new java.awt.event.FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                comboBoxArtifactName.showPopup();
                comboBoxArtifactName.getEditor().selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // do nothing
            }
        });

        comboBoxAttributeName.getEditor().getEditorComponent().addFocusListener(new java.awt.event.FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                comboBoxAttributeName.showPopup();
                comboBoxAttributeName.getEditor().selectAll();
            }

            @Override
            public void focusLost(FocusEvent e) {
                // do nothing
            }
        });

        comboBoxMimeTypeComparison.addItem(RelationalOp.Equals.getSymbol());
        comboBoxMimeTypeComparison.addItem(RelationalOp.NotEquals.getSymbol());
        treeSelectionModel = trRuleList.getSelectionModel();
        defaultTreeModel = (DefaultTreeModel) trRuleList.getModel();
        bnDeleteRule.setEnabled(false);
        String selectedAttribute = comboBoxAttributeName.getSelectedItem().toString();
        comboBoxValueType.setSelectedItem(selectedAttribute);
        localRule = makeRuleFromUserInput();

        listSelectionListener = this::lsAttributeListValueChanged;
        lsAttributeList.addListSelectionListener(listSelectionListener);

        treeSelectionListener = this::trRuleListValueChanged;
        trRuleList.addTreeSelectionListener(treeSelectionListener);
        setDeleteAttributeButton();
        setSaveButton();
    }

    /**
     * Save the settings, issuing warnings if needed.
     *
     * @param tryValidation attempt to validate if set to true, otherwise do not
     *
     * @return true if form ready to close, false otherwise
     */
    boolean saveAndValidateSettings(boolean tryValidation) {
        if (tryValidation) {
            try {
                validateRootDirectory();
            } catch (FolderDidNotValidateException ex) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadRootFolder") + " " + ex.getMessage(),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadFolderForInterestingFileExport"),
                        JOptionPane.OK_OPTION);
                return false;
            }

            try {
                validateReportDirectory();
            } catch (FolderDidNotValidateException ex) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadReportFolder") + " " + ex.getMessage(),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadFolderForInterestingFileExport"),
                        JOptionPane.OK_OPTION);
                return false;
            }

            if (hasRuleChanged()) {
                // if rule has changed without saving ask if we should save or discard
                if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChangesWillBeLost") + " "
                        + NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.DoYouWantToSave"),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChangesWillBeLost"),
                        JOptionPane.YES_NO_OPTION)) {
                    saveOrUpdateRule();
                }
            }
        }
        store();
        return true;
    }

    private void setValueField() {
        if (comboBoxValueType.getSelectedItem().toString().compareTo("DateTime") == 0) {
            dateTimePicker.setVisible(true);
            tbAttributeValue.setVisible(false);
        } else {
            dateTimePicker.setVisible(false);
            tbAttributeValue.setVisible(true);
        }
    }

    private void setTypeColor() {
        if (cbAttributeType.isSelected() && comboBoxValueType.getSelectedItem().toString().compareTo(UNSET) == 0) {
            comboBoxValueType.setForeground(Color.RED);
        } else {
            comboBoxValueType.setForeground(Color.BLACK);
        }
    }

    /**
     * Enable save button if we have a rule name and at least one condition in
     * use.
     */
    private void setSaveButton() {
        String ruleName = tbRuleName.getText();
        String selectedString = comboBoxValueType.getSelectedItem().toString();
        if ((!cbEnableFileExport.isSelected()) || (cbAttributeType.isSelected() && selectedString.compareTo(UNSET) == 0)) {
            bnSaveRule.setEnabled(false);
            lbSaveRuleHelper.setVisible(true);
            return;
        }
        if (ruleName != null && !ruleName.isEmpty()) {
            boolean result = cbFileSize.isSelected() || cbMimeType.isSelected() || cbAttributeType.isSelected();
            bnSaveRule.setEnabled(result);
            lbSaveRuleHelper.setVisible(!result);
        } else {
            bnSaveRule.setEnabled(false);
            lbSaveRuleHelper.setVisible(true);
        }
    }

    /**
     * Go through the ruleSet and populate the JList
     */
    void populateRuleTree() {
        populateRuleTree(null);
    }

    /**
     * Go through the ruleSet and populate the JList
     *
     * @param ruleToBeSelected The path to the rule that should be selected
     *                         after populating the tree
     */
    void populateRuleTree(String ruleToBeSelected) {
        TreePath ttt = new TreePath(rootNode);
        Enumeration<TreePath> expandedDescendants = trRuleList.getExpandedDescendants(ttt);
        expandedNodes = (expandedDescendants == null ? new ArrayList<>() : Collections.list(expandedDescendants));
        if (rootNode != null) {
            rootNode.removeAllChildren();
        }

        for (Rule rule : exportRuleSet.getRules().values()) {
            String ruleName = rule.getName();
            DefaultMutableTreeNode ruleNode = new DefaultMutableTreeNode(new Item(ruleName, ROOTNODE, ItemType.RULE));
            rootNode.add(ruleNode);

            FileMIMETypeCondition fileMIMETypeCondition = rule.getFileMIMETypeCondition();
            if (fileMIMETypeCondition != null) {
                ruleNode.add(new DefaultMutableTreeNode(new Item("MIME Type", ruleName, ItemType.MIME_TYPE_CLAUSE)));
            }

            List<FileSizeCondition> fileSizeConditions = rule.getFileSizeConditions();
            for (FileSizeCondition fsc : fileSizeConditions) {
                ruleNode.add(new DefaultMutableTreeNode(new Item("File Size", ruleName, ItemType.SIZE_CLAUSE)));
            }

            for (Rule.ArtifactCondition artifact : rule.getArtifactConditions()) {
                DefaultMutableTreeNode clauseNode = new DefaultMutableTreeNode(
                        new Item(artifact.getTreeDisplayName(), ruleName, ItemType.ARTIFACT_CLAUSE));
                ruleNode.add(clauseNode);
            }
        }
        ((DefaultTreeModel) trRuleList.getModel()).reload();

        // Re-expand any rules that were open previously and that still exist
        for (TreePath e : expandedNodes) {
            TreePath treePath = findTreePathByRuleName(e.getLastPathComponent().toString());
            trRuleList.expandPath(treePath);
        }
        expandedNodes.clear();

        // select the rule to leave the cursor in a logical place
        if (ruleToBeSelected != null) {
            TreePath treePath = findTreePathByRuleName(ruleToBeSelected);
            treeSelectionModel.setSelectionPath(treePath);
            trRuleList.expandPath(treePath);
        }
    }

    /**
     * Populate the MIME types in the combo box.
     */
    void populateMimeTypes() {
        try {
            SortedSet<String> detectableMimeTypes = FileTypeDetector.getDetectedTypes();
            detectableMimeTypes.addAll(scanRulesForMimetypes());
            detectableMimeTypes.forEach((type) -> {
                comboBoxMimeValue.addItem(type);
            });
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            logger.log(Level.SEVERE, "Unable to get detectable file types", ex);
        }
    }

    /**
     * Populate the Artifact types in the combo box.
     */
    void populateArtifacts() {
        Set<String> artifactTypes = scanRulesForArtifacts();
        try {
            SleuthkitCase currentCase = Case.getOpenCase().getSleuthkitCase();
            for (BlackboardArtifact.Type type : currentCase.getArtifactTypes()) {
                artifactTypes.add(type.getTypeName());
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            // Unable to find and open case or cannot read the database. Use enum.
            for (BlackboardArtifact.ARTIFACT_TYPE artifact : BlackboardArtifact.ARTIFACT_TYPE.values()) {
                artifactTypes.add(artifact.toString());
            }
        }
        List<String> sorted = new ArrayList<>(artifactTypes);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String artifact : sorted) {
            comboBoxArtifactName.addItem(artifact);
        }
    }

    /**
     * Find all MIME types from existing rules
     *
     * @return a Set of Strings with existing rule MIME types
     */
    Set<String> scanRulesForMimetypes() {
        Set<String> mimeTypes = new HashSet<>();
        NavigableMap<String, Rule> nmp = exportRuleSet.getRules();
        for (Rule rule : nmp.values()) {
            if (rule.getFileMIMETypeCondition() != null) {
                mimeTypes.add(rule.getFileMIMETypeCondition().getMIMEType());
            }
        }
        return mimeTypes;
    }

    /**
     * Find all artifact types from existing rules
     *
     * @return a Set of Strings with existing rule artifact types
     */
    Set<String> scanRulesForArtifacts() {
        Set<String> artifacts = new HashSet<>();

        NavigableMap<String, Rule> nmp = exportRuleSet.getRules();
        for (Rule rule : nmp.values()) {
            for (ArtifactCondition ac : rule.getArtifactConditions()) {
                artifacts.add(ac.getArtifactTypeName());
            }
        }
        return artifacts;
    }

    /**
     * Find all attribute types from existing rules
     *
     * @return a Set of Strings with existing rule attribute types
     */
    Set<String> scanRulesForAttributes() {
        Set<String> attributes = new HashSet<>();
        NavigableMap<String, Rule> nmp = exportRuleSet.getRules();
        for (Rule rule : nmp.values()) {
            for (ArtifactCondition ac : rule.getArtifactConditions()) {
                attributes.add(ac.getAttributeTypeName());
                attributeTypeMap.put(ac.getAttributeTypeName(), ac.getAttributeValueType());
            }
        }
        return attributes;
    }

    /**
     * Populate the Attribute types in the combo box.
     */
    void populateAttributes() {
        Set<String> attributeTypes = scanRulesForAttributes();

        try {
            SleuthkitCase currentCase = Case.getOpenCase().getSleuthkitCase();
            for (BlackboardAttribute.Type type : currentCase.getAttributeTypes()) {
                attributeTypes.add(type.getTypeName());
                attributeTypeMap.put(type.getTypeName(), type.getValueType());
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            // Unable to find and open case or cannot read the database. Use enum.
            for (BlackboardAttribute.ATTRIBUTE_TYPE type : BlackboardAttribute.ATTRIBUTE_TYPE.values()) {
                attributeTypes.add(type.getLabel());
                attributeTypeMap.put(type.getLabel(), type.getValueType());
            }
        }

        List<String> sorted = new ArrayList<>(attributeTypes);
        Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);
        for (String attribute : sorted) {
            comboBoxAttributeName.addItem(attribute);
        }
    }

    private void populateArtifactEditor(ArtifactCondition artifactConditionToPopulateWith) {
        cbAttributeType.setSelected(true);
        comboBoxArtifactName.setSelectedItem(artifactConditionToPopulateWith.getArtifactTypeName());
        comboBoxAttributeComparison.setSelectedItem(artifactConditionToPopulateWith.getRelationalOperator().getSymbol());
        comboBoxAttributeName.setSelectedItem(artifactConditionToPopulateWith.getAttributeTypeName());
        BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE valueType = artifactConditionToPopulateWith.getAttributeValueType();
        comboBoxValueType.setSelectedItem(valueType.getLabel());
        comboBoxValueType.setEnabled(null == attributeTypeMap.get(artifactConditionToPopulateWith.getAttributeTypeName()));
        if (valueType == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
            Instant instant = Instant.ofEpochMilli(artifactConditionToPopulateWith.getDateTimeValue().toDate().getTime());
            dateTimePicker.setDateTimeStrict(LocalDateTime.ofInstant(instant, ZoneId.systemDefault()));
        } else {
            tbAttributeValue.setText(artifactConditionToPopulateWith.getStringRepresentationOfValue());
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

        mainPanel = new javax.swing.JPanel();
        tbRootDirectory = new javax.swing.JTextField();
        pnEditRule = new javax.swing.JPanel();
        comboBoxMimeValue = new javax.swing.JComboBox<>();
        cbMimeType = new javax.swing.JCheckBox();
        spFileSizeValue = new javax.swing.JSpinner();
        comboBoxFileSizeUnits = new javax.swing.JComboBox<>();
        cbFileSize = new javax.swing.JCheckBox();
        comboBoxFileSizeComparison = new javax.swing.JComboBox<>();
        comboBoxMimeTypeComparison = new javax.swing.JComboBox<>();
        tbRuleName = new javax.swing.JTextField();
        bnSaveRule = new javax.swing.JButton();
        comboBoxArtifactName = new javax.swing.JComboBox<>();
        comboBoxAttributeName = new javax.swing.JComboBox<>();
        comboBoxAttributeComparison = new javax.swing.JComboBox<>();
        tbAttributeValue = new javax.swing.JTextField();
        bnAddAttribute = new javax.swing.JButton();
        comboBoxValueType = new javax.swing.JComboBox<>();
        cbAttributeType = new javax.swing.JCheckBox();
        lbArtifact = new javax.swing.JLabel();
        lbAttribute = new javax.swing.JLabel();
        bnDeleteAttribute = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        lsAttributeList = new javax.swing.JList<>();
        lbRuleName = new javax.swing.JLabel();
        lbSaveRuleHelper = new javax.swing.JLabel();
        dateTimePicker = new DateTimePicker(null, timeSettings);
        bnBrowseReportDirectory = new javax.swing.JButton();
        tbReportDirectory = new javax.swing.JTextField();
        ruleListScrollPane = new javax.swing.JScrollPane();
        trRuleList = new javax.swing.JTree();
        lbFiles = new javax.swing.JLabel();
        lbReports = new javax.swing.JLabel();
        bnBrowseRootDirectory = new javax.swing.JButton();
        cbEnableFileExport = new javax.swing.JCheckBox();
        bnNewRule = new javax.swing.JButton();
        bnDeleteRule = new javax.swing.JButton();
        bnClose = new javax.swing.JButton();
        lbExplanation = new javax.swing.JLabel();

        setBorder(javax.swing.BorderFactory.createEtchedBorder());
        setName(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.Title")); // NOI18N

        mainPanel.setPreferredSize(new java.awt.Dimension(657, 425));
        mainPanel.setAutoscrolls(true);

        tbRootDirectory.setMaximumSize(new java.awt.Dimension(2000, 2000));
        tbRootDirectory.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.RuleOutputTooltip_1")); // NOI18N

        pnEditRule.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        pnEditRule.setAutoscrolls(true);

        comboBoxMimeValue.setEditable(true);
        comboBoxMimeValue.setMaximumRowCount(30);
        comboBoxMimeValue.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.MimetypeTooltip_1")); // NOI18N
        comboBoxMimeValue.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxMimeValueActionPerformed(evt);
            }
        });

        cbMimeType.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.MimetypeText")); // NOI18N
        cbMimeType.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.MimetypeCheckboxTooltip_1")); // NOI18N
        cbMimeType.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                checkButtonItemStateChanged(evt);
            }
        });

        spFileSizeValue.setModel(new javax.swing.SpinnerNumberModel(1024, 0, null, 1));
        spFileSizeValue.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.FileSizeValueToolTip_1")); // NOI18N

        comboBoxFileSizeUnits.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.FileSizeUnitToolTip_1")); // NOI18N

        cbFileSize.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.FileSize")); // NOI18N
        cbFileSize.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.FileSize_1")); // NOI18N
        cbFileSize.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                checkButtonItemStateChanged(evt);
            }
        });

        comboBoxFileSizeComparison.setMinimumSize(new java.awt.Dimension(32, 20));
        comboBoxFileSizeComparison.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.FileSizeComparisonTooltip_1")); // NOI18N

        comboBoxMimeTypeComparison.setMinimumSize(new java.awt.Dimension(32, 20));
        comboBoxMimeTypeComparison.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.MimeTypeComparisonTooltip_1")); // NOI18N

        tbRuleName.setMaximumSize(new java.awt.Dimension(10, 1000));
        tbRuleName.setPreferredSize(new java.awt.Dimension(733, 20));
        tbRuleName.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.CurrentlySelectedRuleNameTooltip_1")); // NOI18N
        tbRuleName.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyTyped(java.awt.event.KeyEvent evt) {
                tbRuleNameKeyTyped(evt);
            }
        });

        bnSaveRule.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/experimental/images/save-icon.png"))); // NOI18N
        bnSaveRule.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.SaveText")); // NOI18N
        bnSaveRule.setEnabled(false);
        bnSaveRule.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.SaveTooltip_1")); // NOI18N
        bnSaveRule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnSaveRuleActionPerformed(evt);
            }
        });

        comboBoxArtifactName.setEditable(true);
        comboBoxArtifactName.setMaximumRowCount(30);
        comboBoxArtifactName.setToolTipText("The Artifact to match");
        comboBoxArtifactName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxArtifactNameActionPerformed(evt);
            }
        });

        comboBoxAttributeName.setEditable(true);
        comboBoxAttributeName.setMaximumRowCount(30);
        comboBoxAttributeName.setToolTipText("The attribute of the artifact to match");
        comboBoxAttributeName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxAttributeNameActionPerformed(evt);
            }
        });

        comboBoxAttributeComparison.setMinimumSize(new java.awt.Dimension(32, 23));
        comboBoxAttributeComparison.setToolTipText("Select the conditional operator");

        tbAttributeValue.setMinimumSize(new java.awt.Dimension(6, 23));
        tbAttributeValue.setPreferredSize(new java.awt.Dimension(6, 23));
        tbAttributeValue.setToolTipText("Type a value here");

        bnAddAttribute.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/experimental/images/left-arrow-16-icon.png"))); // NOI18N
        bnAddAttribute.setText("Add Attribute");
        bnAddAttribute.setEnabled(false);
        bnAddAttribute.setToolTipText("Click to add an attribute to the current rule");
        bnAddAttribute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnAddAttributeActionPerformed(evt);
            }
        });

        comboBoxValueType.setToolTipText("Select the data type of attribute. This will affect how values are compared.");
        comboBoxValueType.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboBoxValueTypeActionPerformed(evt);
            }
        });

        cbAttributeType.setText("Attributes");
        cbAttributeType.setToolTipText("Select to include artifact/attribute pairs in the rule");
        cbAttributeType.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbAttributeTypeItemStateChanged(evt);
            }
        });

        lbArtifact.setText("Artifact");

        lbAttribute.setText("Attribute");

        bnDeleteAttribute.setText("Delete Attribute");
        bnDeleteAttribute.setEnabled(false);
        bnDeleteAttribute.setToolTipText("Click to remove the selected attribute");
        bnDeleteAttribute.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeleteAttributeActionPerformed(evt);
            }
        });

        lsAttributeList.setModel(new DefaultListModel<String>());
        lsAttributeList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        lsAttributeList.setToolTipText("The attributes for the selected rule");
        jScrollPane1.setViewportView(lsAttributeList);

        lbRuleName.setText("Rule Name");

        lbSaveRuleHelper.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lbSaveRuleHelper.setText("To save, a rule must have a name and at least one condition.");

        dateTimePicker.setToolTipText("Choose a date and time");

        javax.swing.GroupLayout pnEditRuleLayout = new javax.swing.GroupLayout(pnEditRule);
        pnEditRule.setLayout(pnEditRuleLayout);
        pnEditRuleLayout.setHorizontalGroup(
            pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnEditRuleLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 198, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(10, 10, 10)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnEditRuleLayout.createSequentialGroup()
                                .addComponent(lbSaveRuleHelper, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnSaveRule, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(pnEditRuleLayout.createSequentialGroup()
                                .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(comboBoxArtifactName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                            .addComponent(lbArtifact))
                                        .addGap(18, 18, 18)
                                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addGroup(pnEditRuleLayout.createSequentialGroup()
                                                .addComponent(comboBoxAttributeName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(comboBoxValueType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                            .addComponent(lbAttribute)))
                                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                                        .addComponent(bnAddAttribute, javax.swing.GroupLayout.PREFERRED_SIZE, 117, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(bnDeleteAttribute))
                                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                                        .addComponent(comboBoxAttributeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(dateTimePicker, javax.swing.GroupLayout.PREFERRED_SIZE, 306, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(tbAttributeValue, javax.swing.GroupLayout.DEFAULT_SIZE, 158, Short.MAX_VALUE)))
                                .addGap(0, 0, Short.MAX_VALUE))))
                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cbMimeType)
                            .addComponent(cbFileSize)
                            .addComponent(cbAttributeType))
                        .addGap(89, 89, 89)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(pnEditRuleLayout.createSequentialGroup()
                                .addComponent(comboBoxMimeTypeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(comboBoxMimeValue, javax.swing.GroupLayout.PREFERRED_SIZE, 240, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(pnEditRuleLayout.createSequentialGroup()
                                .addComponent(comboBoxFileSizeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(spFileSizeValue, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(comboBoxFileSizeUnits, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                        .addComponent(lbRuleName)
                        .addGap(18, 18, 18)
                        .addComponent(tbRuleName, javax.swing.GroupLayout.PREFERRED_SIZE, 1, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnEditRuleLayout.setVerticalGroup(
            pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnEditRuleLayout.createSequentialGroup()
                .addGap(14, 14, 14)
                .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbRuleName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbRuleName))
                .addGap(26, 26, 26)
                .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbMimeType)
                    .addComponent(comboBoxMimeTypeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(comboBoxMimeValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                        .addGap(78, 78, 78)
                        .addComponent(lbAttribute)
                        .addGap(8, 8, 8)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(comboBoxAttributeName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxArtifactName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxValueType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(tbAttributeValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxAttributeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(dateTimePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnAddAttribute)
                            .addComponent(bnDeleteAttribute))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(bnSaveRule)
                            .addComponent(lbSaveRuleHelper)))
                    .addGroup(pnEditRuleLayout.createSequentialGroup()
                        .addGap(26, 26, 26)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cbFileSize)
                            .addComponent(spFileSizeValue, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxFileSizeUnits, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(comboBoxFileSizeComparison, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(26, 26, 26)
                        .addGroup(pnEditRuleLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cbAttributeType)
                            .addComponent(lbArtifact))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 187, Short.MAX_VALUE)))
                .addContainerGap())
        );

        bnBrowseReportDirectory.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BrowseText")); // NOI18N
        bnBrowseReportDirectory.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BrowseReportTooltip_1")); // NOI18N
        bnBrowseReportDirectory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnBrowseReportDirectoryActionPerformed(evt);
            }
        });

        tbReportDirectory.setMaximumSize(new java.awt.Dimension(2000, 2000));
        tbReportDirectory.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ReportOutputFolderTooltip_1")); // NOI18N

        javax.swing.tree.DefaultMutableTreeNode treeNode1 = new javax.swing.tree.DefaultMutableTreeNode("root");
        trRuleList.setModel(new javax.swing.tree.DefaultTreeModel(treeNode1));
        trRuleList.setName("trRuleList"); // NOI18N
        trRuleList.setShowsRootHandles(true);
        trRuleList.setToolTipText("This tree shows the rules to collect files for automatic file export");
        ruleListScrollPane.setViewportView(trRuleList);
        trRuleList.getAccessibleContext().setAccessibleParent(ruleListScrollPane);

        lbFiles.setText("Files Folder");

        lbReports.setText("Reports Folder");

        bnBrowseRootDirectory.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BrowseText")); // NOI18N
        bnBrowseRootDirectory.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BrowseRootOutputFolder_1")); // NOI18N
        bnBrowseRootDirectory.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnBrowseRootDirectoryActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout mainPanelLayout = new javax.swing.GroupLayout(mainPanel);
        mainPanel.setLayout(mainPanelLayout);
        mainPanelLayout.setHorizontalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbFiles)
                            .addComponent(lbReports))
                        .addGap(18, 18, 18)
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tbRootDirectory, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(tbReportDirectory, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(bnBrowseReportDirectory, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(bnBrowseRootDirectory, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 93, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(mainPanelLayout.createSequentialGroup()
                        .addComponent(ruleListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 278, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(pnEditRule, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        mainPanelLayout.setVerticalGroup(
            mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, mainPanelLayout.createSequentialGroup()
                .addGap(1, 1, 1)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(tbRootDirectory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbFiles)
                    .addComponent(bnBrowseRootDirectory))
                .addGap(6, 6, 6)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnBrowseReportDirectory)
                    .addComponent(tbReportDirectory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbReports))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(mainPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ruleListScrollPane)
                    .addComponent(pnEditRule, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        cbEnableFileExport.setText("Enable File Export");
        cbEnableFileExport.setToolTipText("Select to enable File Export");
        cbEnableFileExport.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cbEnableFileExportItemStateChanged(evt);
            }
        });

        bnNewRule.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/experimental/images/plus-icon.png"))); // NOI18N
        bnNewRule.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.NewText")); // NOI18N
        bnNewRule.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.NewRuleTooltip_1")); // NOI18N
        bnNewRule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnNewRuleActionPerformed(evt);
            }
        });

        bnDeleteRule.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/experimental/images/minus-icon.png"))); // NOI18N
        bnDeleteRule.setText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.DeleteText")); // NOI18N
        bnDeleteRule.setEnabled(false);
        bnDeleteRule.setToolTipText(org.openide.util.NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.DeleteTooltip_1")); // NOI18N
        bnDeleteRule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDeleteRuleActionPerformed(evt);
            }
        });

        bnClose.setText("Close");
        bnClose.setToolTipText("Close the settings panel");
        bnClose.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCloseActionPerformed(evt);
            }
        });

        lbExplanation.setText("File Export occurs after ingest has completed, automatically exporting files matching the rules specified below. Individual components of the rule are ANDed together.");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(cbEnableFileExport)
                .addGap(39, 39, 39)
                .addComponent(lbExplanation)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addGap(36, 36, 36)
                .addComponent(bnNewRule, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(bnDeleteRule)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(bnClose, javax.swing.GroupLayout.PREFERRED_SIZE, 101, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(21, 21, 21))
            .addGroup(layout.createSequentialGroup()
                .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 1051, Short.MAX_VALUE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbEnableFileExport)
                    .addComponent(lbExplanation))
                .addGap(7, 7, 7)
                .addComponent(mainPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 455, Short.MAX_VALUE)
                .addGap(1, 1, 1)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(bnDeleteRule)
                    .addComponent(bnNewRule)
                    .addComponent(bnClose))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles clicking the "New Rule" button.
     *
     * @param evt The event which caused this call.
     */
    private void bnNewRuleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnNewRuleActionPerformed
        if (hasRuleChanged()) {
            // if rule has changed without saving
            if (JOptionPane.showConfirmDialog(this,
                    NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.UnsavedChangesLost"),
                    NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChangesWillBeLost"),
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.CANCEL_OPTION) {
                // they were not quite ready to navigate away yet, so clear the selection
                treeSelectionModel.clearSelection();
                return;
            }
        }
        clearRuleEditor();
        localRule = makeRuleFromUserInput();
    }//GEN-LAST:event_bnNewRuleActionPerformed

    /**
     * Handles clicking the "Delete Rule" button.
     *
     * @param evt The event which caused this call.
     */
    private void bnDeleteRuleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeleteRuleActionPerformed
        Item item = (Item) ((DefaultMutableTreeNode) trRuleList.getLastSelectedPathComponent()).getUserObject();
        if (item != null) {
            if (item.getItemType() == ItemType.RULE) {
                String ruleName = item.getName();
                if (JOptionPane.showConfirmDialog(this, NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ReallyDeleteRule")
                        + " " + ruleName + NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.QuestionMark"),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ConfirmRuleDeletion"),
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
                    exportRuleSet.removeRule(ruleName);
                    clearRuleEditor();
                    localRule = makeRuleFromUserInput();
                    populateRuleTree(ruleName);
                }
            }
        } else {
            logger.log(Level.WARNING, "Nothing selected to delete");
        }
    }//GEN-LAST:event_bnDeleteRuleActionPerformed

    /**
     * Deconflict artifact names in the tree view.
     *
     * @param initalName The base name to start with
     * @param rule       The rule we are deconflicting
     *
     * @return The new name, or throw IllegalArgumentException
     */
    String getArtifactClauseNameAndNumber(String initialName, Rule rule) {
        int number = 1;

        if (rule != null) {
            for (ArtifactCondition ac : rule.getArtifactConditions()) {
                int location = ac.getTreeDisplayName().lastIndexOf('_');
                if (ac.getTreeDisplayName().startsWith(initialName)) {
                    int temp = Integer.parseInt(ac.getTreeDisplayName().substring(location + 1));
                    if (temp >= number) {
                        number = temp + 1;
                    }
                }
            }
            if (number == Integer.MAX_VALUE) {
                // It never became unique, so give up.
                throw new IllegalArgumentException("Too many attributes named " + initialName); //NON-NLS
            }
        }
        return (initialName + "_" + Integer.toString(number));
    }

    /**
     * Creates a rule from the rule editor components on the right side of the
     * panel.
     *
     * @return the rule created from the user's inputs.
     */
    private Rule makeRuleFromUserInput() {
        return makeRuleFromUserInput(false);
    }

    /**
     * Creates a rule from the rule editor components on the right side of the
     * panel.
     *
     * @param showFailures If there is a failure, shows the user a dialog box
     *                     with an explanation of why it failed.
     *
     * @return the rule created from the user's inputs.
     */
    private Rule makeRuleFromUserInput(boolean showFailures) {
        String ruleName = tbRuleName.getText();
        Rule rule = new Rule(ruleName);

        FileSizeCondition fileSizeCondition = null;
        if (cbFileSize.isSelected()) {
            try {
                spFileSizeValue.commitEdit();
                fileSizeCondition = new Rule.FileSizeCondition((Integer) spFileSizeValue.getValue(),
                        SizeUnit.valueOf(comboBoxFileSizeUnits.getSelectedItem().toString()),
                        RelationalOp.fromSymbol(comboBoxFileSizeComparison.getSelectedItem().toString()));
            } catch (ParseException ex) {
                fileSizeCondition = null;
                logger.log(Level.WARNING, "Could not create size condition for rule %s: " + ruleName, ex); //NON-NLS
            }
        }

        FileMIMETypeCondition fileMIMETypeCondition = null;
        if (cbMimeType.isSelected()) {
            fileMIMETypeCondition = new Rule.FileMIMETypeCondition(comboBoxMimeValue.getSelectedItem().toString(),
                    RelationalOp.fromSymbol(comboBoxMimeTypeComparison.getSelectedItem().toString()));
        }

        try {
            ArtifactCondition artifactCondition = getArtifactConditionFromInput(rule);

            if (fileSizeCondition != null) {
                rule.addFileSizeCondition(fileSizeCondition);
            }

            if (fileMIMETypeCondition != null) {
                rule.addFileMIMETypeCondition(fileMIMETypeCondition);
            }

            if (artifactCondition != null) {
                rule.addArtfactCondition(artifactCondition);
            }

            return rule;

        } catch (IllegalArgumentException ex) {
            String message = "Attribute value '" + tbAttributeValue.getText() + "' is not of type " + comboBoxValueType.getSelectedItem().toString() + ". Ignoring invalid Attribute Type clause.";
            logger.log(Level.INFO, message);
            if (showFailures) {
                JOptionPane.showMessageDialog(this, message, "Invalid Type Conversion", JOptionPane.OK_OPTION);
            }
            cbAttributeType.setSelected(false);
            return null;
        }
    }

    /**
     * Get the artifact condition from the user's input
     *
     * @return the ArtifactCondition, or null if there isn't one.
     */
    ArtifactCondition getArtifactConditionFromInput(Rule rule) throws IllegalArgumentException {
        ArtifactCondition artifactCondition = null;
        if (cbAttributeType.isSelected()) {
            String selectedAttribute = comboBoxAttributeName.getSelectedItem().toString();
            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE typeFromComboBox = BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.fromLabel(comboBoxValueType.getSelectedItem().toString());
            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE intrinsicType = attributeTypeMap.get(comboBoxAttributeName.getSelectedItem().toString());

            // if we don't have a type in the map, but they have set the combobox, put it in the map
            if (intrinsicType == null && typeFromComboBox != null) {
                intrinsicType = typeFromComboBox;
                attributeTypeMap.put(selectedAttribute, typeFromComboBox);
            }

            if (intrinsicType == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                LocalDateTime localDateTime = dateTimePicker.getDateTimeStrict();
                if (localDateTime == null) {
                    throw new IllegalArgumentException("Bad date/time combination");
                }
                Instant instant = localDateTime.atZone(ZoneId.systemDefault()).toInstant();
                String stringValue = Long.toString(Date.from(instant).getTime());
                artifactCondition = new Rule.ArtifactCondition(
                        comboBoxArtifactName.getSelectedItem().toString(),
                        comboBoxAttributeName.getSelectedItem().toString(),
                        stringValue,
                        intrinsicType,
                        RelationalOp.fromSymbol(comboBoxAttributeComparison.getSelectedItem().toString()));
            } else if (intrinsicType == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.BYTE) {
                try {
                    String stringValue = tbAttributeValue.getText();
                    byte[] hexValue = Hex.decodeHex(stringValue.toCharArray());
                    String finalValue = new String(Hex.encodeHex(hexValue));
                    artifactCondition = new Rule.ArtifactCondition(
                            comboBoxArtifactName.getSelectedItem().toString(),
                            comboBoxAttributeName.getSelectedItem().toString(),
                            finalValue,
                            intrinsicType,
                            RelationalOp.fromSymbol(comboBoxAttributeComparison.getSelectedItem().toString()));
                } catch (DecoderException ex) {
                    throw new IllegalArgumentException(ex);
                }
            } else if (intrinsicType != null) {
                artifactCondition = new Rule.ArtifactCondition(
                        comboBoxArtifactName.getSelectedItem().toString(),
                        comboBoxAttributeName.getSelectedItem().toString(),
                        tbAttributeValue.getText(),
                        intrinsicType,
                        RelationalOp.fromSymbol(comboBoxAttributeComparison.getSelectedItem().toString()));
            } else {
                throw new IllegalArgumentException();
            }
        }
        return artifactCondition;
    }

    /**
     * Handles clicking the "Save Rule" button.
     *
     * @param evt The event which caused this call.
     */
    private void bnSaveRuleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnSaveRuleActionPerformed
        saveOrUpdateRule();
    }//GEN-LAST:event_bnSaveRuleActionPerformed

    /**
     * Get the TreePath to this Artifact clause given a String rule and clause
     * name
     *
     * @param ruleName   the rule name to find
     * @param clauseName the clause name to find
     *
     * @return
     */
    private TreePath findTreePathByRuleAndArtifactClauseName(String ruleName, String clauseName) {
        @SuppressWarnings("unchecked")
        Enumeration<DefaultMutableTreeNode> enumeration = rootNode.preorderEnumeration();
        boolean insideRule = false;
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = enumeration.nextElement();
            Item item = (Item) node.getUserObject();
            if (item.getItemType() == ItemType.RULE) {
                insideRule = node.toString().equalsIgnoreCase(ruleName);
            }

            if ((insideRule == true)
                    && (item.getItemType() == ItemType.ARTIFACT_CLAUSE)
                    && (item.getName().compareTo(clauseName) == 0)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    /**
     * Get the TreePath to this rule, given a String rule name.
     *
     * @param ruleName the name of the rule to find
     *
     * @return the TreePath to this rule, given a String rule name.
     */
    private TreePath findTreePathByRuleName(String ruleName) {
        @SuppressWarnings("unchecked")
        Enumeration<DefaultMutableTreeNode> enumeration = rootNode.depthFirstEnumeration();
        while (enumeration.hasMoreElements()) {
            DefaultMutableTreeNode node = enumeration.nextElement();
            if (node.toString().equalsIgnoreCase(ruleName)) {
                return new TreePath(node.getPath());
            }
        }
        return null;
    }

    /**
     * This method saves the rule from the editor to the tree, if the rule is
     * not malformed. This means the rule must have a name, and at least one
     * condition is selected. It updates any existing rule in the tree with the
     * same name.
     *
     */
    void saveOrUpdateRule() {
        String ruleName = tbRuleName.getText();
        Rule userInputRule = makeRuleFromUserInput(true);
        if (userInputRule == null) { // we had bad input. do not continue.
            return;
        }
        if ((ruleName != null && !ruleName.isEmpty())
                && (cbFileSize.isSelected() || cbMimeType.isSelected()
                || cbAttributeType.isSelected())) {
            localRule = userInputRule;
            Rule existingRule = exportRuleSet.getRule(ruleName);
            if (existingRule == null) {
                // This is a new rule. Store it in the list and put it in the tree.
                List<ArtifactCondition> userRuleArtifactConditions = userInputRule.getArtifactConditions();
                for (ArtifactCondition artifactCondition : userRuleArtifactConditions) {
                    String displayName = artifactCondition.getTreeDisplayName();
                    artifactCondition.setTreeDisplayName(getArtifactClauseNameAndNumber(displayName, null));
                }
                exportRuleSet.addRule(userInputRule);
            } else {
                // Update an existing rule.
                exportRuleSet.removeRule(existingRule); // remove rule if it exists already, does nothing if it does not exist

                if (cbMimeType.isSelected()) {
                    FileMIMETypeCondition fileMIMETypeCondition = userInputRule.getFileMIMETypeCondition();
                    if (fileMIMETypeCondition != null) {
                        existingRule.addFileMIMETypeCondition(fileMIMETypeCondition);
                    }
                } else {
                    existingRule.removeFileMIMETypeCondition();
                }

                if (cbFileSize.isSelected()) {
                    List<FileSizeCondition> fileSizeConditions = userInputRule.getFileSizeConditions();
                    for (FileSizeCondition fileSizeCondition : fileSizeConditions) {
                        // Do not need to de-dupliate, as currently implmented in FileExportRuleSet
                        existingRule.addFileSizeCondition(fileSizeCondition);
                    }
                } else {
                    existingRule.removeFileSizeCondition();
                }

                if (cbAttributeType.isSelected()) {
                    // for every artifact condition in the new rule, disambiguate the name
                    List<ArtifactCondition> userRuleArtifactConditions = userInputRule.getArtifactConditions();
                    for (ArtifactCondition artifactCondition : userRuleArtifactConditions) {
                        String displayName = artifactCondition.getTreeDisplayName();
                        String newName = getArtifactClauseNameAndNumber(displayName, existingRule);
                        artifactCondition.setTreeDisplayName(newName);
                        existingRule.addArtfactCondition(artifactCondition);
                    }
                    exportRuleSet.addRule(existingRule);

                } else {
                    existingRule.removeArtifactConditions();
                }
                exportRuleSet.addRule(existingRule);
            }
            populateRuleTree(ruleName);
        } else {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.RuleNotSaved"),
                    NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.MalformedRule"),
                    JOptionPane.OK_OPTION);
        }
    }

    /**
     * Handles clicking the "Browse Root Directory" button.
     *
     * @param evt The event which caused this call.
     */
    private void bnBrowseRootDirectoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnBrowseRootDirectoryActionPerformed
        int returnVal = rootDirectoryChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                validateAndSanitizeBrowsedDirectory(rootDirectoryChooser, tbRootDirectory);
            } catch (FolderDidNotValidateException ex) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadRootFolder") + " " + ex.getMessage(),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadFolderForInterestingFileExport"),
                        JOptionPane.OK_OPTION);
            }
        }
    }//GEN-LAST:event_bnBrowseRootDirectoryActionPerformed

    /**
     * Handles when any 'condition' check boxes change.
     *
     * @param evt The event which caused this call.
     */
    private void checkButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_checkButtonItemStateChanged
        setSaveButton();
    }//GEN-LAST:event_checkButtonItemStateChanged

    /**
     * Handles clicking the "Browse Report Directory" button.
     *
     * @param evt The event which caused this call.
     */
    private void bnBrowseReportDirectoryActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnBrowseReportDirectoryActionPerformed
        int returnVal = reportDirectoryChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            try {
                validateAndSanitizeBrowsedDirectory(reportDirectoryChooser, tbReportDirectory);
            } catch (FolderDidNotValidateException ex) {
                JOptionPane.showMessageDialog(this,
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadReportFolder") + " " + ex.getMessage(),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.BadFolderForInterestingFileExport"),
                        JOptionPane.OK_OPTION);
            }
        }
    }//GEN-LAST:event_bnBrowseReportDirectoryActionPerformed

    /**
     * Handles when the MIME value combo box changes
     *
     * @param evt
     */
    private void comboBoxMimeValueActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxMimeValueActionPerformed
        if (-1 == comboBoxMimeValue.getSelectedIndex()) {
            String item = comboBoxMimeValue.getSelectedItem().toString().trim();
            if (-1 == ((DefaultComboBoxModel) comboBoxMimeValue.getModel()).getIndexOf(item)) {
                comboBoxMimeValue.addItem(item);
            }
        }
    }//GEN-LAST:event_comboBoxMimeValueActionPerformed

    /**
     * Set the state of the dialog based upon the input
     *
     * @param state the input state
     */
    private void setEnabledState(boolean state) {
        bnBrowseReportDirectory.setEnabled(state);
        bnBrowseRootDirectory.setEnabled(state);
        bnDeleteRule.setEnabled(state);
        bnSaveRule.setEnabled(state);
        lbSaveRuleHelper.setVisible(!state);
        cbFileSize.setEnabled(state);
        cbMimeType.setEnabled(state);
        comboBoxFileSizeComparison.setEnabled(state);
        comboBoxFileSizeUnits.setEnabled(state);
        comboBoxMimeTypeComparison.setEnabled(state);
        comboBoxMimeValue.setEnabled(state);
        mainPanel.setEnabled(state);
        pnEditRule.setEnabled(state);
        spFileSizeValue.setEnabled(state);
        tbReportDirectory.setEnabled(state);
        tbRootDirectory.setEnabled(state);
        tbRuleName.setEnabled(state);
        tbAttributeValue.setEnabled(state);
        cbAttributeType.setEnabled(state);
        comboBoxArtifactName.setEnabled(state);
        comboBoxAttributeComparison.setEnabled(state);
        comboBoxAttributeName.setEnabled(state);
        comboBoxValueType.setEnabled(state);
        trRuleList.setEnabled(state);
        bnAddAttribute.setEnabled(state);
        dateTimePicker.setEnabled(state);
        bnNewRule.setEnabled(state);
        lbArtifact.setEnabled(state);
        lbAttribute.setEnabled(state);
        lbExplanation.setEnabled(state);
        lbFiles.setEnabled(state);
        lbReports.setEnabled(state);
        lsAttributeList.setEnabled(state);
        lbRuleName.setEnabled(state);
        lbSaveRuleHelper.setEnabled(state);
    }

    private void cbEnableFileExportItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbEnableFileExportItemStateChanged
        setEnabledState(cbEnableFileExport.isSelected());
        setDeleteAttributeButton();
        setSaveButton();
    }//GEN-LAST:event_cbEnableFileExportItemStateChanged

    private void bnCloseActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCloseActionPerformed
        if (saveAndValidateSettings(cbEnableFileExport.isSelected())) {
            jDialog.dispose();
        }
    }//GEN-LAST:event_bnCloseActionPerformed

    private void cbAttributeTypeItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cbAttributeTypeItemStateChanged
        setSaveButton();
        setAddAttributeButton();
        setDeleteAttributeButton();
        setTypeColor();
    }//GEN-LAST:event_cbAttributeTypeItemStateChanged

    private void bnAddAttributeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnAddAttributeActionPerformed
        saveOrUpdateRule();
    }//GEN-LAST:event_bnAddAttributeActionPerformed

    private void tbRuleNameKeyTyped(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_tbRuleNameKeyTyped
        setAddAttributeButton();
    }//GEN-LAST:event_tbRuleNameKeyTyped

    private void comboBoxArtifactNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxArtifactNameActionPerformed
        if (-1 == comboBoxArtifactName.getSelectedIndex()) {
            // if the selected item is not in the drop down list
            String item = comboBoxArtifactName.getSelectedItem().toString().trim();
            if (-1 == ((DefaultComboBoxModel) comboBoxArtifactName.getModel()).getIndexOf(item)) {
                comboBoxArtifactName.addItem(item);
            }
        }
        setSaveButton();
        setAddAttributeButton();
    }//GEN-LAST:event_comboBoxArtifactNameActionPerformed

    private void comboBoxAttributeNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxAttributeNameActionPerformed
        if (-1 == comboBoxAttributeName.getSelectedIndex()) {
            // if the selected item is not in the drop down list
            String item = comboBoxAttributeName.getSelectedItem().toString().trim();
            if (-1 == ((DefaultComboBoxModel) comboBoxAttributeName.getModel()).getIndexOf(item)) {
                comboBoxAttributeName.addItem(item);
                comboBoxValueType.setSelectedItem(UNSET);
                comboBoxValueType.setEnabled(true);
            }
        } else {
            BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE typeFromMap = attributeTypeMap.get(comboBoxAttributeName.getSelectedItem().toString());
            if (typeFromMap != null) {
                comboBoxValueType.setSelectedItem(typeFromMap.getLabel());
                comboBoxValueType.setEnabled(false);
            } else {
                comboBoxValueType.setSelectedItem(UNSET);
                comboBoxValueType.setEnabled(true);
            }
        }

        setSaveButton();
        setAddAttributeButton();
        setTypeColor();
    }//GEN-LAST:event_comboBoxAttributeNameActionPerformed

    private void comboBoxValueTypeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboBoxValueTypeActionPerformed
        setSaveButton();
        setAddAttributeButton();
        setTypeColor();
        setValueField();
    }//GEN-LAST:event_comboBoxValueTypeActionPerformed

    private void bnDeleteAttributeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDeleteAttributeActionPerformed
        String selection = lsAttributeList.getSelectedValue();
        if (selection != null && !selection.isEmpty()) {
            Item item = (Item) ((DefaultMutableTreeNode) trRuleList.getLastSelectedPathComponent()).getUserObject();
            if (item != null) {
                if (JOptionPane.showConfirmDialog(this, NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ReallyDeleteCondition")
                        + " " + selection + NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.QuestionMark"),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ConfirmClauseDeletion"),
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {

                    String ruleName = item.getRuleName();

                    // get the rule
                    Rule rule = exportRuleSet.getRule(ruleName);

                    // find the clause and remove it
                    for (ArtifactCondition ac : rule.getArtifactConditions()) {
                        if (selection.compareTo(ac.getTreeDisplayName()) == 0) {
                            rule.removeArtifactCondition(ac);
                            break;
                        }
                    }
                    if (isRuleEmpty(rule)) {
                        exportRuleSet.removeRule(rule);
                    }
                    populateRuleTree(ruleName);
                }
            }
        }
    }//GEN-LAST:event_bnDeleteAttributeActionPerformed

    private void lsAttributeListValueChanged(javax.swing.event.ListSelectionEvent evt) {
        if (evt.getValueIsAdjusting() == false) {
            // if this is the final iteration through the value changed handler
            if (lsAttributeList.getSelectedIndex() >= 0) {
                // and we have a selected entry
                bnDeleteAttribute.setEnabled(true);

                DefaultMutableTreeNode node = (DefaultMutableTreeNode) trRuleList.getLastSelectedPathComponent();
                if (node == null) { // Nothing is selected
                    return;
                }
                Item ruleInfo = (Item) node.getUserObject();
                if (ruleInfo.getItemType() == ItemType.RULE_SET) {
                    return;
                }

                Object listItem = lsAttributeList.getSelectedValue();
                if (listItem != null) {
                    Rule rule = exportRuleSet.getRules().get(ruleInfo.getRuleName());
                    if (rule != null) {
                        // find the attribute to select
                        for (ArtifactCondition ac : rule.getArtifactConditions()) {
                            if (ac.getTreeDisplayName().compareTo(listItem.toString()) == 0) {
                                // set selected and expand it
                                TreePath shortPath = findTreePathByRuleName(rule.getName());
                                TreePath treePath = findTreePathByRuleAndArtifactClauseName(rule.getName(), listItem.toString());
                                trRuleList.expandPath(shortPath);

                                // Don't let treeSelectionListener respond
                                trRuleList.removeTreeSelectionListener(treeSelectionListener);
                                treeSelectionModel.setSelectionPath(treePath);
                                populateArtifactEditor(ac);
                                setDeleteAttributeButton();
                                trRuleList.addTreeSelectionListener(treeSelectionListener);
                                localRule = makeRuleFromUserInput();
                                break;
                            }
                        }
                    }
                } else {
                    bnDeleteAttribute.setEnabled(false);
                }
            }
        }
    }

    private void trRuleListValueChanged(javax.swing.event.TreeSelectionEvent evt) {
        int selectionCount = treeSelectionModel.getSelectionCount();
        lsAttributeList.removeAll();
        attributeListModel.removeAllElements();

        if (selectionCount > 0) {
            if (hasRuleChanged()) {
                // and the rule has changed without saving
                if (JOptionPane.showConfirmDialog(this, NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.UnsavedChangesLost"),
                        NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.ChangesWillBeLost"),
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.CANCEL_OPTION) {
                    // they were not quite ready to navigate away yet, so clear the selection
                    trRuleList.clearSelection();
                    return;
                }
            }

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) trRuleList.getLastSelectedPathComponent();
            if (node == null) { // Nothing is selected
                return;
            }

            Item nodeInfo = (Item) node.getUserObject();
            bnDeleteRule.setEnabled(nodeInfo.getItemType() == ItemType.RULE);

            if (nodeInfo.getItemType() == ItemType.RULE_SET) {
                tbRuleName.setText(null);
                clearSizeCondition();
                clearMIMECondition();
                clearArtifactCondition();
                localRule = makeRuleFromUserInput();
                return;
            }

            String selectedRuleName = nodeInfo.getRuleName();
            Rule rule = exportRuleSet.getRules().get(selectedRuleName);
            if (rule != null) {
                // Read values for this rule and display them in the UI
                tbRuleName.setText(rule.getName());
                FileMIMETypeCondition fileMIMETypeCondition = rule.getFileMIMETypeCondition();
                if (fileMIMETypeCondition != null) {
                    // if there is a MIME type condition
                    cbMimeType.setSelected(true);
                    comboBoxMimeTypeComparison.setSelectedItem(fileMIMETypeCondition.getRelationalOp().getSymbol());
                    comboBoxMimeValue.setSelectedItem(fileMIMETypeCondition.getMIMEType());
                } else { // Clear the selection
                    clearMIMECondition();
                }

                List<FileSizeCondition> fileSizeCondition = rule.getFileSizeConditions();
                if (fileSizeCondition != null && !fileSizeCondition.isEmpty()) {
                    // if there is a file size condition                            
                    FileSizeCondition condition = fileSizeCondition.get(0);
                    cbFileSize.setSelected(true);
                    spFileSizeValue.setValue(condition.getSize());
                    comboBoxFileSizeUnits.setSelectedItem(condition.getUnit().toString());
                    comboBoxFileSizeComparison.setSelectedItem(condition.getRelationalOperator().getSymbol());
                } else {
                    // Clear the selection
                    clearSizeCondition();
                }

                ArtifactCondition artifactConditionToPopulateWith = null;
                List<ArtifactCondition> artifactConditions = rule.getArtifactConditions();
                if (nodeInfo.getItemType() != ItemType.ARTIFACT_CLAUSE) {
                    // if there are any attribute clauses, populate the first one, otherwise clear artifact editor
                    if (artifactConditions.isEmpty()) {
                        clearArtifactCondition();
                    } else {
                        artifactConditionToPopulateWith = artifactConditions.get(0);
                    }
                } else { // an artifact clause is selected. populate it.
                    for (ArtifactCondition artifact : artifactConditions) {
                        if (artifact.getTreeDisplayName().compareTo(nodeInfo.getName()) == 0) {
                            artifactConditionToPopulateWith = artifact;
                            break;
                        }
                    }
                }
                if (artifactConditionToPopulateWith != null) {
                    for (ArtifactCondition artifact : artifactConditions) {
                        attributeListModel.addElement(artifact.getTreeDisplayName());
                    }
                    // Don't let listSelectionListener respond
                    lsAttributeList.removeListSelectionListener(listSelectionListener);
                    lsAttributeList.setSelectedValue(artifactConditionToPopulateWith.getTreeDisplayName(), true);
                    populateArtifactEditor(artifactConditionToPopulateWith);
                    setDeleteAttributeButton();
                    lsAttributeList.addListSelectionListener(listSelectionListener);
                }
            }
            localRule = makeRuleFromUserInput();
        } else {
            bnDeleteRule.setEnabled(false);
        }
    }

    void setAddAttributeButton() {
        if (!tbRuleName.getText().isEmpty()) {
            bnAddAttribute.setEnabled((cbAttributeType.isSelected())
                    && (comboBoxValueType.getSelectedItem().toString().compareTo(UNSET) != 0));
        } else {
            bnAddAttribute.setEnabled(false);
        }
    }

    void setDeleteAttributeButton() {
        String selection = lsAttributeList.getSelectedValue();
        bnDeleteAttribute.setEnabled(selection != null && !selection.isEmpty() && cbEnableFileExport.isSelected() != false);
    }

    /**
     * Clears out the fields of the file size condition, reseting them to the
     * default value.
     */
    private void clearSizeCondition() {
        cbFileSize.setSelected(false);
        spFileSizeValue.setValue(1024);
        comboBoxFileSizeUnits.setSelectedIndex(0);
        comboBoxFileSizeComparison.setSelectedIndex(0);
    }

    /**
     * Clears out the fields of the MIME Type condition, reseting them to the
     * default value.
     */
    private void clearMIMECondition() {
        cbMimeType.setSelected(false);
        comboBoxMimeValue.setSelectedIndex(0);
        comboBoxMimeTypeComparison.setSelectedIndex(0);
    }

    /**
     * Clears out the fields of the Artifact condition, reseting them to the
     * default value.
     */
    private void clearArtifactCondition() {
        tbAttributeValue.setText("");
        cbAttributeType.setSelected(false);
        comboBoxArtifactName.setSelectedIndex(0);
        comboBoxAttributeComparison.setSelectedIndex(0);
        comboBoxAttributeName.setSelectedIndex(0);
        comboBoxValueType.setSelectedIndex(0);
        dateTimePicker.clear();
    }

    /**
     * Clears out the fields of the entire rule editor, reseting them to the
     * default value and clearing the selection in the rule list on the left.
     */
    private void clearRuleEditor() {
        tbRuleName.setText(null);
        clearSizeCondition();
        clearMIMECondition();
        clearArtifactCondition();
        trRuleList.clearSelection();
    }

    /**
     * Checks if a rule has changed since the last snapshot stored in localRule.
     *
     * @return True if the rule has changed, False otherwise
     */
    boolean hasRuleChanged() {
        return (!localRule.equals(makeRuleFromUserInput()));
    }

    /**
     * Check if this rule has no clauses left.
     *
     * @param rule The rule to check
     *
     * @return True if the rule is empty, false otherwise.
     */
    boolean isRuleEmpty(Rule rule) {

        FileMIMETypeCondition mtc = rule.getFileMIMETypeCondition();
        List<FileSizeCondition> fsc = rule.getFileSizeConditions();
        List<ArtifactCondition> arc = rule.getArtifactConditions();

        // if there are no clauses in the rule, it's empty.
        return (mtc == null) && (fsc == null || fsc.isEmpty()) && (arc == null || arc.isEmpty());
    }

    /**
     * Validates that the selected folder exists, and sets the passed-in
     * textField to empty if it does not exist. Then checks if the sanitized
     * folder is readable and writable with the current user's credentials,
     * returning True if so and False if not. This has the desirable effect that
     * a folder can show up in the text box (as chosen by the user while
     * browsing), and still return false if the permissions are incorrect. This
     * way the user can see the folder chosen and take appropriate action to fix
     * the permissions. Use this only for files chosen by the file chooser.
     *
     * @param fileChooser The fileChooser we want to use.
     * @param textField   The text field to store the sanitized value in.
     *
     * Throws FolderDidNotValidateException
     */
    private void validateAndSanitizeBrowsedDirectory(JFileChooser fileChooser, JTextField textField) throws FolderDidNotValidateException {
        File selectedFile = fileChooser.getSelectedFile();
        if (selectedFile == null || !selectedFile.exists()) {
            textField.setText(""); //NON-NLS

            throw new FolderDidNotValidateException(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.UnableToFindDirectory"));

        } else {
            textField.setText(selectedFile.toString());
            File file = new File(selectedFile.toString());
            fileChooser.setCurrentDirectory(file);

            if (!FileUtil.hasReadWriteAccess(file.toPath())) {
                throw new FolderDidNotValidateException(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.PermissionsInvalid"));
            }
        }
    }

    /**
     * Validates that the selected folder exists, then checks if the sanitized
     * folder is readable and writable with the current user's credentials.
     * Throws and exception with explanatory text if the folder is not valid.
     * This has none of the side effects of the above method such as setting the
     * JFileChooser and JTextFields. This method only checks if the passed-in
     * String is a valid directory for our purposes.
     *
     * @param selectedString The String to validate
     *
     * @return Returns the validated String, if possible
     *
     * throws FolderDidNotValidateException
     */
    private String validateDirectory(String selectedString) throws FolderDidNotValidateException {
        File selectedFile = new File(selectedString);

        if (!selectedFile.exists()) {
            throw new FolderDidNotValidateException(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.UnableToFindDirectory"));
        } else {
            File file = new File(selectedFile.toString());

            if (!FileUtil.hasReadWriteAccess(file.toPath())) {
                throw new FolderDidNotValidateException(NbBundle.getMessage(FileExporterSettingsPanel.class, "FileExporterSettingsPanel.PermissionsInvalid"));
            }
        }
        return selectedString;
    }

    /**
     * Allows Options Panel to tell if the root directory is valid. Throws an
     * exception with explanatory text if it is not valid.
     *
     * throws FolderDidNotValidateException
     */
    void validateRootDirectory() throws FolderDidNotValidateException {
        tbRootDirectory.setText(validateDirectory(tbRootDirectory.getText()));
    }

    /**
     * Allows Options Panel to tell if the report directory is valid. Throws an
     * exception with explanatory text if it is not valid.
     *
     * throws FolderDidNotValidateException
     */
    void validateReportDirectory() throws FolderDidNotValidateException {
        tbReportDirectory.setText(validateDirectory(tbReportDirectory.getText()));
    }

    /**
     * Store the settings to disk.
     */
    public void store() {
        FileExportSettings settings = new FileExportSettings();
        settings.setFilesRootDirectory(Paths.get(tbRootDirectory.getText()));
        settings.setReportsRootDirectory(Paths.get(tbReportDirectory.getText()));
        settings.setFileExportEnabledState(cbEnableFileExport.isSelected());
        TreeMap<String, FileExportRuleSet> treeMap = new TreeMap<>();
        treeMap.put(exportRuleSet.getName(), exportRuleSet);
        settings.setRuleSets(treeMap);
        try {
            FileExportSettings.store(settings);
        } catch (FileExportSettings.PersistenceException ex) {
            logger.log(Level.SEVERE, "Unable to save rules: ", ex); //NON-NLS
        }
    }

    /**
     * Read the settings from disk.
     */
    public void load() {
        try {
            FileExportSettings settings = FileExportSettings.load();
            if (settings != null) {
                Path path = settings.getFilesRootDirectory();
                if (path != null) {
                    tbRootDirectory.setText(path.toString());
                }
                path = settings.getReportsRootDirectory();
                if (path != null) {
                    tbReportDirectory.setText(path.toString());
                }
                TreeMap<String, FileExportRuleSet> treeMap = settings.getRuleSets();
                if (treeMap != null && !treeMap.isEmpty()) {
                    exportRuleSet = treeMap.firstEntry().getValue();
                }
                boolean answer = settings.getFileExportEnabledState();
                setEnabledState(answer);
                cbEnableFileExport.setSelected(answer);
            }
            return;
        } catch (FileExportSettings.PersistenceException ex) {
            logger.log(Level.INFO, "Unable to load rule settings: {0}", ex.getMessage()); //NON-NLS
        }
        setEnabledState(false);
        cbEnableFileExport.setSelected(false);
    }

    class FolderDidNotValidateException extends Exception {

        private static final long serialVersionUID = 1L;

        FolderDidNotValidateException(String message) {
            super(message);
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnAddAttribute;
    private javax.swing.JButton bnBrowseReportDirectory;
    private javax.swing.JButton bnBrowseRootDirectory;
    private javax.swing.JButton bnClose;
    private javax.swing.JButton bnDeleteAttribute;
    private javax.swing.JButton bnDeleteRule;
    private javax.swing.JButton bnNewRule;
    private javax.swing.JButton bnSaveRule;
    private javax.swing.JCheckBox cbAttributeType;
    private javax.swing.JCheckBox cbEnableFileExport;
    private javax.swing.JCheckBox cbFileSize;
    private javax.swing.JCheckBox cbMimeType;
    private javax.swing.JComboBox<String> comboBoxArtifactName;
    private javax.swing.JComboBox<String> comboBoxAttributeComparison;
    private javax.swing.JComboBox<String> comboBoxAttributeName;
    private javax.swing.JComboBox<String> comboBoxFileSizeComparison;
    private javax.swing.JComboBox<String> comboBoxFileSizeUnits;
    private javax.swing.JComboBox<String> comboBoxMimeTypeComparison;
    private javax.swing.JComboBox<String> comboBoxMimeValue;
    private javax.swing.JComboBox<String> comboBoxValueType;
    private com.github.lgooddatepicker.components.DateTimePicker dateTimePicker;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lbArtifact;
    private javax.swing.JLabel lbAttribute;
    private javax.swing.JLabel lbExplanation;
    private javax.swing.JLabel lbFiles;
    private javax.swing.JLabel lbReports;
    private javax.swing.JLabel lbRuleName;
    private javax.swing.JLabel lbSaveRuleHelper;
    private javax.swing.JList<String> lsAttributeList;
    private javax.swing.JPanel mainPanel;
    private javax.swing.JPanel pnEditRule;
    private javax.swing.JScrollPane ruleListScrollPane;
    private javax.swing.JSpinner spFileSizeValue;
    private javax.swing.JTextField tbAttributeValue;
    private javax.swing.JTextField tbReportDirectory;
    private javax.swing.JTextField tbRootDirectory;
    private javax.swing.JTextField tbRuleName;
    private javax.swing.JTree trRuleList;
    // End of variables declaration//GEN-END:variables
}
