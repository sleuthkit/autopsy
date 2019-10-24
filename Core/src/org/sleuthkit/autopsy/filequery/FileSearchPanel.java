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
package org.sleuthkit.autopsy.filequery;

import com.google.common.eventbus.Subscribe;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.filequery.FileSearch.GroupingAttributeType;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileSize;
import org.sleuthkit.autopsy.filequery.FileSearchData.Frequency;
import org.sleuthkit.autopsy.filequery.FileSearchData.Score;
import org.sleuthkit.autopsy.filequery.FileSearchFiltering.ParentSearchTerm;
import org.sleuthkit.autopsy.filequery.FileSorter.SortingMethod;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TagName;

/**
 * Dialog to allow the user to choose filtering and grouping options.
 */
final class FileSearchPanel extends javax.swing.JPanel implements ActionListener {
    
    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(FileSearchPanel.class.getName());
    
    private DefaultListModel<FileSearchFiltering.ParentSearchTerm> parentListModel;
    private final SleuthkitCase caseDb;
    private final EamDb centralRepoDb;
    private SearchWorker searchWorker = null;

    /**
     * Creates new form FileSearchDialog
     */
    @NbBundle.Messages({"FileSearchPanel.dialogTitle.text=Test file search"})
    FileSearchPanel(SleuthkitCase caseDb, EamDb centralRepoDb) {
        this.caseDb = caseDb;
        this.centralRepoDb = centralRepoDb;
        initComponents();
        customizeComponents();
    }

    /**
     * Set up all the UI components
     */
    private void customizeComponents() {
        
        searchButton.setEnabled(false);

        // Set up the filters
        setUpFileTypeFilter();
        setUpDataSourceFilter();
        setUpFrequencyFilter();
        setUpSizeFilter();
        setUpKWFilter();
        setUpParentPathFilter();
        setUpHashFilter();
        setUpInterestingItemsFilter();
        setUpTagsFilter();
        setUpObjectFilter();
        setUpScoreFilter();

        // Set up the grouping attributes
        for (FileSearch.GroupingAttributeType type : FileSearch.GroupingAttributeType.values()) {
            if (type != GroupingAttributeType.FREQUENCY || EamDb.isEnabled()) {
                groupByCombobox.addItem(type);
            }
        }

        // Set up the file order list
        for (FileSorter.SortingMethod method : FileSorter.SortingMethod.values()) {
            if (method != SortingMethod.BY_FREQUENCY || EamDb.isEnabled()) {
                orderByCombobox.addItem(method);
            }
        }
        validateFields();
    }

    /**
     * Add listeners to the checkbox/list set. Either can be null.
     *
     * @param checkBox
     * @param list
     */
    private void addListeners(JCheckBox checkBox, JList<?> list) {
        if (checkBox != null) {
            checkBox.addActionListener(this);
        }
        if (list != null) {
            list.addListSelectionListener(new ListSelectionListener() {
                @Override
                public void valueChanged(ListSelectionEvent evt) {
                    validateFields();
                }
            });
        }
    }

    /**
     * Initialize the file type filter
     */
    private void setUpFileTypeFilter() {
        DefaultComboBoxModel<FileType> fileTypeComboBoxModel = (DefaultComboBoxModel<FileType>) fileTypeComboBox.getModel();
        for (FileType type : FileType.getOptionsForFiltering()) {
            fileTypeComboBoxModel.addElement(type);
        }
        fileTypeComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                validateFields();
            }
        });
    }

    /**
     * Initialize the data source filter
     */
    private void setUpDataSourceFilter() {
        int count = 0;
        try {
            DefaultListModel<DataSourceItem> dsListModel = (DefaultListModel<DataSourceItem>) dataSourceList.getModel();
            for (DataSource ds : caseDb.getDataSources()) {
                dsListModel.add(count, new DataSourceItem(ds));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading data sources", ex);
            dataSourceCheckbox.setEnabled(false);
            dataSourceList.setEnabled(false);
        }
        addListeners(dataSourceCheckbox, dataSourceList);
    }

    /**
     * Initialize the frequency filter
     */
    private void setUpFrequencyFilter() {
        if (centralRepoDb == null) {
            crFrequencyList.setEnabled(false);
            crFrequencyCheckbox.setEnabled(false);
        } else {
            int count = 0;
            DefaultListModel<FileSearchData.Frequency> frequencyListModel = (DefaultListModel<FileSearchData.Frequency>) crFrequencyList.getModel();
            for (FileSearchData.Frequency freq : FileSearchData.Frequency.getOptionsForFiltering()) {
                frequencyListModel.add(count, freq);
            }
        }
        addListeners(crFrequencyCheckbox, crFrequencyList);
    }

    /**
     * Initialize the file size filter
     */
    private void setUpSizeFilter() {
        int count = 0;
        DefaultListModel<FileSearchData.FileSize> sizeListModel = (DefaultListModel<FileSearchData.FileSize>) sizeList.getModel();
        for (FileSearchData.FileSize size : FileSearchData.FileSize.values()) {
            sizeListModel.add(count, size);
        }
        addListeners(sizeCheckbox, sizeList);
    }

    /**
     * Initialize the keyword list names filter
     */
    private void setUpKWFilter() {
        int count = 0;
        try {
            DefaultListModel<String> kwListModel = (DefaultListModel<String>) keywordList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for (String name : setNames) {
                kwListModel.add(count, name);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading keyword list names", ex);
            keywordCheckbox.setEnabled(false);
            keywordList.setEnabled(false);
        }
        addListeners(keywordCheckbox, keywordList);
    }

    /**
     * Initialize the hash filter.
     */
    private void setUpHashFilter() {
        int count = 0;
        try {
            DefaultListModel<String> hashListModel = (DefaultListModel<String>) hashSetList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for (String name : setNames) {
                hashListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading hash set names", ex);
            hashSetCheckbox.setEnabled(false);
            hashSetList.setEnabled(false);
        }
        addListeners(hashSetCheckbox, hashSetList);
    }

    /**
     * Initialize the interesting items filter.
     */
    private void setUpInterestingItemsFilter() {
        int count = 0;
        try {
            DefaultListModel<String> intListModel = (DefaultListModel<String>) interestingItemsList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for (String name : setNames) {
                intListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading interesting file set names", ex);
            interestingItemsCheckbox.setEnabled(false);
            interestingItemsList.setEnabled(false);
        }
        addListeners(interestingItemsCheckbox, interestingItemsList);
    }

    /**
     * Initialize the tags filter.
     */
    private void setUpTagsFilter() {
        int count = 0;
        try {
            DefaultListModel<TagName> tagsListModel = (DefaultListModel<TagName>) tagsList.getModel();
            
            List<TagName> tagNames = caseDb.getTagNamesInUse();
            for (TagName name : tagNames) {
                tagsListModel.add(count, name);
                count++;
            }
            tagsList.setCellRenderer(new TagsListCellRenderer());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading tag names", ex);
            tagsCheckbox.setEnabled(false);
            tagsList.setEnabled(false);
        }
        addListeners(tagsCheckbox, tagsList);
    }

    /**
     * TagsListCellRenderer
     */
    private class TagsListCellRenderer extends DefaultListCellRenderer {
        
        private static final long serialVersionUID = 1L;
        
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            Object newValue = value;
            if (value instanceof TagName) {
                newValue = ((TagName) value).getDisplayName();
            }
            super.getListCellRendererComponent(list, newValue, index, isSelected, cellHasFocus);
            return this;
        }
    }

    /**
     * Initialize the object filter
     */
    private void setUpObjectFilter() {
        int count = 0;
        try {
            DefaultListModel<String> objListModel = (DefaultListModel<String>) objectsList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION);
            for (String name : setNames) {
                objListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading object detected set names", ex);
            objectsCheckbox.setEnabled(false);
            objectsList.setEnabled(false);
        }
        addListeners(objectsCheckbox, objectsList);
    }

    /**
     * Initialize the score filter
     */
    private void setUpScoreFilter() {
        
        int count = 0;
        DefaultListModel<Score> scoreListModel = (DefaultListModel<Score>) scoreList.getModel();
        for (Score score : Score.getOptionsForFiltering()) {
            scoreListModel.add(count, score);
        }
        addListeners(scoreCheckbox, scoreList);
    }

    /**
     * Get the names of the sets which exist in the case database for the
     * specified artifact and attribute types.
     *
     * @param artifactType     The artifact type to get the list of sets for.
     * @param setNameAttribute The attribute type which contains the set names.
     *
     * @return A list of set names which exist in the case for the specified
     *         artifact and attribute types.
     *
     * @throws TskCoreException
     */
    private List<String> getSetNames(BlackboardArtifact.ARTIFACT_TYPE artifactType, BlackboardAttribute.ATTRIBUTE_TYPE setNameAttribute) throws TskCoreException {
        List<BlackboardArtifact> arts = caseDb.getBlackboardArtifacts(artifactType);
        List<String> setNames = new ArrayList<>();
        for (BlackboardArtifact art : arts) {
            for (BlackboardAttribute attr : art.getAttributes()) {
                if (attr.getAttributeType().getTypeID() == setNameAttribute.getTypeID()) {
                    String setName = attr.getValueString();
                    if (!setNames.contains(setName)) {
                        setNames.add(setName);
                    }
                }
            }
        }
        Collections.sort(setNames);
        return setNames;
    }

    /**
     * Initialize the parent path filter
     */
    private void setUpParentPathFilter() {
        parentPathButtonGroup.add(fullRadioButton);
        parentPathButtonGroup.add(substringRadioButton);
        fullRadioButton.setSelected(true);
        includeRadioButton.setSelected(true);
        parentListModel = (DefaultListModel<FileSearchFiltering.ParentSearchTerm>) parentList.getModel();
        addListeners(parentCheckbox, parentList);
    }

    /**
     * Get a list of all filters selected by the user.
     *
     * @return the list of filters
     */
    List<FileSearchFiltering.FileFilter> getFilters() {
        List<FileSearchFiltering.FileFilter> filters = new ArrayList<>();

        // There will always be a file type selected
        filters.add(new FileSearchFiltering.FileTypeFilter(fileTypeComboBox.getItemAt(fileTypeComboBox.getSelectedIndex())));
        
        if (parentCheckbox.isSelected()) {
            // For the parent paths, everything in the box is used (not just the selected entries)
            filters.add(new FileSearchFiltering.ParentFilter(getParentPaths()));
        }
        
        if (dataSourceCheckbox.isSelected()) {
            List<DataSource> dataSources = dataSourceList.getSelectedValuesList().stream().map(t -> t.getDataSource()).collect(Collectors.toList());
            filters.add(new FileSearchFiltering.DataSourceFilter(dataSources));
        }
        
        if (crFrequencyCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.FrequencyFilter(crFrequencyList.getSelectedValuesList()));
        }
        
        if (sizeCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.SizeFilter(sizeList.getSelectedValuesList()));
        }
        
        if (keywordCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.KeywordListFilter(keywordList.getSelectedValuesList()));
        }
        
        if (hashSetCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.HashSetFilter(hashSetList.getSelectedValuesList()));
        }
        
        if (interestingItemsCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.InterestingFileSetFilter(interestingItemsList.getSelectedValuesList()));
        }
        
        if (objectsCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.ObjectDetectionFilter(objectsList.getSelectedValuesList()));
        }
        
        if (tagsCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.TagsFilter(tagsList.getSelectedValuesList()));
        }
        
        if (exifCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.ExifFilter());
        }
        
        if (notableCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.NotableFilter());
        }
        
        if (scoreCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.ScoreFilter(scoreList.getSelectedValuesList()));
        }
        
        return filters;
    }

    /**
     * Utility method to get the parent path objects out of the JList.
     *
     * @return The list of entered ParentSearchTerm objects
     */
    private List<FileSearchFiltering.ParentSearchTerm> getParentPaths() {
        List<FileSearchFiltering.ParentSearchTerm> results = new ArrayList<>();
        for (int i = 0; i < parentListModel.getSize(); i++) {
            results.add(parentListModel.get(i));
        }
        return results;
    }

    /**
     * Get the attribute to group by
     *
     * @return the grouping attribute
     */
    FileSearch.AttributeType getGroupingAttribute() {
        FileSearch.GroupingAttributeType groupingAttrType = (FileSearch.GroupingAttributeType) groupByCombobox.getSelectedItem();
        return groupingAttrType.getAttributeType();
    }

    /**
     * Get the sorting method for groups.
     *
     * @return the selected sorting method
     */
    FileGroup.GroupSortingAlgorithm getGroupSortingMethod() {
        if (attributeRadioButton.isSelected()) {
            return FileGroup.GroupSortingAlgorithm.BY_GROUP_KEY;
        }
        return FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE;
    }

    /**
     * Get the sorting method for files.
     *
     * @return the selected sorting method
     */
    FileSorter.SortingMethod getFileSortingMethod() {
        return (FileSorter.SortingMethod) orderByCombobox.getSelectedItem();
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        validateFields();
    }

    /**
     * Utility class to allow us to display the data source ID along with the
     * name
     */
    private class DataSourceItem {
        
        private final DataSource ds;
        
        DataSourceItem(DataSource ds) {
            this.ds = ds;
        }
        
        DataSource getDataSource() {
            return ds;
        }
        
        @Override
        public String toString() {
            return ds.getName() + " (ID: " + ds.getId() + ")";
        }
    }

    /**
     * Validate the form. If we use any of this in the final dialog we should
     * use bundle messages.
     */
    private void validateFields() {
        // There must be at least one file type selected
        if (fileTypeComboBox.getSelectedIndex() < 0) {
            setInvalid("At least one file type must be selected");
            return;
        }
        // For most enabled filters, there should be something selected
        if (dataSourceCheckbox.isSelected() && dataSourceList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one data source must be selected");
            return;
        }
        if (crFrequencyCheckbox.isSelected() && crFrequencyList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one CR frequency must be selected");
            return;
        }
        if (sizeCheckbox.isSelected() && sizeList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one size must be selected");
            return;
        }
        if (keywordCheckbox.isSelected() && keywordList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one keyword list name must be selected");
            return;
        }

        // Parent uses everything in the box
        if (parentCheckbox.isSelected() && getParentPaths().isEmpty()) {
            setInvalid("At least one parent path must be entered");
            return;
        }
        
        if (hashSetCheckbox.isSelected() && hashSetList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one hash set name must be selected");
            return;
        }
        
        if (interestingItemsCheckbox.isSelected() && interestingItemsList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one interesting file set name must be selected");
            return;
        }
        
        if (objectsCheckbox.isSelected() && objectsList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one object type name must be selected");
            return;
        }
        
        if (tagsCheckbox.isSelected() && tagsList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one tag name must be selected");
            return;
        }
        
        if (scoreCheckbox.isSelected() && scoreList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one score must be selected");
            return;
        }
        setValid();
    }

    /**
     * The settings are valid so enable the Search button
     */
    private void setValid() {
        errorLabel.setText("");
        searchButton.setEnabled(true);
    }

    /**
     * The settings are not valid so disable the search button and display the
     * given error message.
     *
     * @param error
     */
    private void setInvalid(String error) {
        errorLabel.setText(error);
        searchButton.setEnabled(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        parentPathButtonGroup = new javax.swing.ButtonGroup();
        orderGroupsByButtonGroup = new javax.swing.ButtonGroup();
        parentIncludeButtonGroup = new javax.swing.ButtonGroup();
        filtersScrollPane = new javax.swing.JScrollPane();
        filtersPanel = new javax.swing.JPanel();
        sizeCheckbox = new javax.swing.JCheckBox();
        dataSourceCheckbox = new javax.swing.JCheckBox();
        crFrequencyCheckbox = new javax.swing.JCheckBox();
        keywordCheckbox = new javax.swing.JCheckBox();
        parentCheckbox = new javax.swing.JCheckBox();
        dataSourceScrollPane = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        fullRadioButton = new javax.swing.JRadioButton();
        substringRadioButton = new javax.swing.JRadioButton();
        parentTextField = new javax.swing.JTextField();
        addButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        sizeScrollPane = new javax.swing.JScrollPane();
        sizeList = new javax.swing.JList<>();
        crFrequencyScrollPane = new javax.swing.JScrollPane();
        crFrequencyList = new javax.swing.JList<>();
        keywordScrollPane = new javax.swing.JScrollPane();
        keywordList = new javax.swing.JList<>();
        parentLabel = new javax.swing.JLabel();
        parentScrollPane = new javax.swing.JScrollPane();
        parentList = new javax.swing.JList<>();
        hashSetCheckbox = new javax.swing.JCheckBox();
        hashSetScrollPane = new javax.swing.JScrollPane();
        hashSetList = new javax.swing.JList<>();
        objectsCheckbox = new javax.swing.JCheckBox();
        tagsCheckbox = new javax.swing.JCheckBox();
        interestingItemsCheckbox = new javax.swing.JCheckBox();
        scoreCheckbox = new javax.swing.JCheckBox();
        exifCheckbox = new javax.swing.JCheckBox();
        notableCheckbox = new javax.swing.JCheckBox();
        objectsScrollPane = new javax.swing.JScrollPane();
        objectsList = new javax.swing.JList<>();
        tagsScrollPane = new javax.swing.JScrollPane();
        tagsList = new javax.swing.JList<>();
        interestingItemsScrollPane = new javax.swing.JScrollPane();
        interestingItemsList = new javax.swing.JList<>();
        scoreScrollPane = new javax.swing.JScrollPane();
        scoreList = new javax.swing.JList<>();
        includeRadioButton = new javax.swing.JRadioButton();
        excludeRadioButton = new javax.swing.JRadioButton();
        fileTypeLabel = new javax.swing.JLabel();
        searchButton = new javax.swing.JButton();
        sortingPanel = new javax.swing.JPanel();
        groupByCombobox = new javax.swing.JComboBox<>();
        orderByCombobox = new javax.swing.JComboBox<>();
        orderGroupsByLabel = new javax.swing.JLabel();
        attributeRadioButton = new javax.swing.JRadioButton();
        groupSizeRadioButton = new javax.swing.JRadioButton();
        orderByLabel = new javax.swing.JLabel();
        groupByLabel = new javax.swing.JLabel();
        fileTypeComboBox = new javax.swing.JComboBox<>();
        errorLabel = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();

        setMinimumSize(new java.awt.Dimension(424, 0));
        setPreferredSize(new java.awt.Dimension(424, 533));

        filtersScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.filtersScrollPane.border.title"))); // NOI18N
        filtersScrollPane.setPreferredSize(new java.awt.Dimension(416, 338));

        filtersPanel.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(sizeCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.sizeCheckbox.text")); // NOI18N
        sizeCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sizeCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(6, 6, 4, 0);
        filtersPanel.add(sizeCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(dataSourceCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.dataSourceCheckbox.text")); // NOI18N
        dataSourceCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourceCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(dataSourceCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(crFrequencyCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.crFrequencyCheckbox.text")); // NOI18N
        crFrequencyCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                crFrequencyCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(crFrequencyCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(keywordCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.keywordCheckbox.text")); // NOI18N
        keywordCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keywordCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(keywordCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(parentCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.parentCheckbox.text")); // NOI18N
        parentCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                parentCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(parentCheckbox, gridBagConstraints);

        dataSourceList.setModel(new DefaultListModel<DataSourceItem>());
        dataSourceList.setEnabled(false);
        dataSourceList.setVisibleRowCount(5);
        dataSourceScrollPane.setViewportView(dataSourceList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(dataSourceScrollPane, gridBagConstraints);

        parentPathButtonGroup.add(fullRadioButton);
        fullRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(fullRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.fullRadioButton.text")); // NOI18N
        fullRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(fullRadioButton, gridBagConstraints);

        parentPathButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.substringRadioButton.text")); // NOI18N
        substringRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(substringRadioButton, gridBagConstraints);

        parentTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 6, 0);
        filtersPanel.add(parentTextField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(addButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.addButton.text")); // NOI18N
        addButton.setEnabled(false);
        addButton.setMaximumSize(new java.awt.Dimension(70, 23));
        addButton.setMinimumSize(new java.awt.Dimension(70, 23));
        addButton.setPreferredSize(new java.awt.Dimension(70, 23));
        addButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 6, 6);
        filtersPanel.add(addButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(deleteButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.deleteButton.text")); // NOI18N
        deleteButton.setEnabled(false);
        deleteButton.setMaximumSize(new java.awt.Dimension(70, 23));
        deleteButton.setMinimumSize(new java.awt.Dimension(70, 23));
        deleteButton.setPreferredSize(new java.awt.Dimension(70, 23));
        deleteButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_END;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 4, 6);
        filtersPanel.add(deleteButton, gridBagConstraints);

        sizeList.setModel(new DefaultListModel<FileSize>());
        sizeList.setEnabled(false);
        sizeList.setVisibleRowCount(5);
        sizeScrollPane.setViewportView(sizeList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(6, 4, 4, 6);
        filtersPanel.add(sizeScrollPane, gridBagConstraints);

        crFrequencyList.setModel(new DefaultListModel<Frequency>());
        crFrequencyList.setEnabled(false);
        crFrequencyList.setVisibleRowCount(3);
        crFrequencyScrollPane.setViewportView(crFrequencyList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(crFrequencyScrollPane, gridBagConstraints);

        keywordList.setModel(new DefaultListModel<String>());
        keywordList.setEnabled(false);
        keywordList.setVisibleRowCount(3);
        keywordScrollPane.setViewportView(keywordList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(keywordScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(parentLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.parentLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(parentLabel, gridBagConstraints);

        parentList.setModel(new DefaultListModel<ParentSearchTerm>());
        parentList.setEnabled(false);
        parentList.setVisibleRowCount(3);
        parentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                parentListValueChanged(evt);
            }
        });
        parentScrollPane.setViewportView(parentList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(parentScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(hashSetCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.hashSetCheckbox.text")); // NOI18N
        hashSetCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hashSetCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(hashSetCheckbox, gridBagConstraints);

        hashSetList.setModel(new DefaultListModel<String>());
        hashSetList.setEnabled(false);
        hashSetList.setVisibleRowCount(3);
        hashSetScrollPane.setViewportView(hashSetList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(hashSetScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(objectsCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.objectsCheckbox.text")); // NOI18N
        objectsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                objectsCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(objectsCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(tagsCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.tagsCheckbox.text")); // NOI18N
        tagsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tagsCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(tagsCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(interestingItemsCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.interestingItemsCheckbox.text")); // NOI18N
        interestingItemsCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                interestingItemsCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(interestingItemsCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(scoreCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.scoreCheckbox.text")); // NOI18N
        scoreCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                scoreCheckboxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(scoreCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(exifCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.exifCheckbox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 6);
        filtersPanel.add(exifCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(notableCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.notableCheckbox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 6);
        filtersPanel.add(notableCheckbox, gridBagConstraints);

        objectsList.setModel(new DefaultListModel<String>());
        objectsList.setEnabled(false);
        objectsList.setVisibleRowCount(3);
        objectsScrollPane.setViewportView(objectsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(objectsScrollPane, gridBagConstraints);

        tagsList.setModel(new DefaultListModel<TagName>());
        tagsList.setEnabled(false);
        tagsList.setVisibleRowCount(3);
        tagsScrollPane.setViewportView(tagsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(tagsScrollPane, gridBagConstraints);

        interestingItemsList.setModel(new DefaultListModel<String>());
        interestingItemsList.setEnabled(false);
        interestingItemsList.setVisibleRowCount(3);
        interestingItemsScrollPane.setViewportView(interestingItemsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(interestingItemsScrollPane, gridBagConstraints);

        scoreList.setModel(new DefaultListModel<Score>());
        scoreList.setEnabled(false);
        scoreList.setVisibleRowCount(3);
        scoreScrollPane.setViewportView(scoreList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(scoreScrollPane, gridBagConstraints);

        parentIncludeButtonGroup.add(includeRadioButton);
        includeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(includeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.includeRadioButton.text")); // NOI18N
        includeRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(includeRadioButton, gridBagConstraints);

        parentIncludeButtonGroup.add(excludeRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(excludeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.excludeRadioButton.text")); // NOI18N
        excludeRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(excludeRadioButton, gridBagConstraints);

        filtersScrollPane.setViewportView(filtersPanel);

        org.openide.awt.Mnemonics.setLocalizedText(fileTypeLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.fileTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        sortingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.sortingPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderGroupsByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.orderGroupsByLabel.text")); // NOI18N

        orderGroupsByButtonGroup.add(attributeRadioButton);
        attributeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(attributeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.attributeRadioButton.text")); // NOI18N

        orderGroupsByButtonGroup.add(groupSizeRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(groupSizeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.groupSizeRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.orderByLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(groupByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.groupByLabel.text")); // NOI18N

        javax.swing.GroupLayout sortingPanelLayout = new javax.swing.GroupLayout(sortingPanel);
        sortingPanel.setLayout(sortingPanelLayout);
        sortingPanelLayout.setHorizontalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(sortingPanelLayout.createSequentialGroup()
                        .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(sortingPanelLayout.createSequentialGroup()
                                .addGap(47, 47, 47)
                                .addComponent(attributeRadioButton))
                            .addGroup(sortingPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(orderByLabel))
                            .addGroup(sortingPanelLayout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(groupByLabel)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(groupByCombobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(orderByCombobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(sortingPanelLayout.createSequentialGroup()
                        .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(sortingPanelLayout.createSequentialGroup()
                                .addGap(27, 27, 27)
                                .addComponent(orderGroupsByLabel))
                            .addGroup(sortingPanelLayout.createSequentialGroup()
                                .addGap(47, 47, 47)
                                .addComponent(groupSizeRadioButton)))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        sortingPanelLayout.setVerticalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addGap(8, 8, 8)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(orderByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orderByLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(groupByLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(orderGroupsByLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(attributeRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(groupSizeRadioButton)
                .addContainerGap())
        );

        fileTypeComboBox.setModel(new DefaultComboBoxModel<FileType>());

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.cancelButton.text")); // NOI18N
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(fileTypeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(fileTypeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton))
                    .addComponent(sortingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(filtersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(4, 4, 4))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, searchButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(fileTypeLabel)
                    .addComponent(fileTypeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sortingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filtersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 290, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cancelButton)
                        .addComponent(searchButton))
                    .addComponent(errorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 0, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        enableSearch(false);
        
        FileType searchType = fileTypeComboBox.getItemAt(fileTypeComboBox.getSelectedIndex());
        DiscoveryEvents.getDiscoveryEventBus().post(new DiscoveryEvents.SearchStartedEvent(searchType));
        // For testing, allow the user to run different searches in loop

        // Get the selected filters
        List<FileSearchFiltering.FileFilter> filters = getFilters();

        // Get the grouping attribute and group sorting method
        FileSearch.AttributeType groupingAttr = getGroupingAttribute();
        FileGroup.GroupSortingAlgorithm groupSortAlgorithm = getGroupSortingMethod();

        // Get the file sorting method
        FileSorter.SortingMethod fileSort = getFileSortingMethod();
        searchWorker = new SearchWorker(centralRepoDb, filters, groupingAttr, groupSortAlgorithm, fileSort);
        searchWorker.execute();
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Set the enabled status of the search controls.
     *
     * @param enabled Boolean which indicates if the search should be enabled.
     *                True if the search button and controls should be enabled,
     *                false otherwise.
     */
    private void enableSearch(boolean enabled) {
        if (enabled) {
            DiscoveryTopComponent.getTopComponent().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        } else {
            DiscoveryTopComponent.getTopComponent().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        searchButton.setEnabled(enabled);
        cancelButton.setEnabled(!enabled);
        fileTypeComboBox.setEnabled(enabled);
        orderByCombobox.setEnabled(enabled);
        groupByCombobox.setEnabled(enabled);
        attributeRadioButton.setEnabled(enabled);
        groupSizeRadioButton.setEnabled(enabled);
        sizeCheckbox.setEnabled(enabled);
        sizeScrollPane.setEnabled(enabled && sizeCheckbox.isSelected());
        sizeList.setEnabled(enabled && sizeCheckbox.isSelected());
        dataSourceCheckbox.setEnabled(enabled);
        dataSourceList.setEnabled(enabled && dataSourceCheckbox.isSelected());
        dataSourceScrollPane.setEnabled(enabled && dataSourceCheckbox.isSelected());
        crFrequencyCheckbox.setEnabled(enabled);
        crFrequencyScrollPane.setEnabled(enabled && crFrequencyCheckbox.isSelected());
        crFrequencyList.setEnabled(enabled && crFrequencyCheckbox.isSelected());
        keywordCheckbox.setEnabled(enabled);
        keywordList.setEnabled(enabled && keywordCheckbox.isSelected());
        keywordScrollPane.setEnabled(enabled && keywordCheckbox.isSelected());
        hashSetCheckbox.setEnabled(enabled);
        hashSetScrollPane.setEnabled(enabled && hashSetCheckbox.isSelected());
        hashSetList.setEnabled(enabled && hashSetCheckbox.isSelected());
        objectsCheckbox.setEnabled(enabled);
        objectsScrollPane.setEnabled(enabled && objectsCheckbox.isSelected());
        objectsList.setEnabled(enabled && objectsCheckbox.isSelected());
        tagsCheckbox.setEnabled(enabled);
        tagsScrollPane.setEnabled(enabled && tagsCheckbox.isSelected());
        tagsList.setEnabled(enabled && tagsCheckbox.isSelected());
        interestingItemsCheckbox.setEnabled(enabled);
        interestingItemsScrollPane.setEnabled(enabled && interestingItemsCheckbox.isSelected());
        interestingItemsList.setEnabled(enabled && interestingItemsCheckbox.isSelected());
        scoreCheckbox.setEnabled(enabled);
        scoreScrollPane.setEnabled(enabled && scoreCheckbox.isSelected());
        scoreList.setEnabled(enabled && scoreCheckbox.isSelected());
        exifCheckbox.setEnabled(enabled);
        notableCheckbox.setEnabled(enabled);
        parentCheckbox.setEnabled(enabled);
        parentScrollPane.setEnabled(enabled && parentCheckbox.isSelected());
        parentList.setEnabled(enabled && parentCheckbox.isSelected());
        parentTextField.setEnabled(enabled && parentCheckbox.isSelected());
        addButton.setEnabled(enabled && parentCheckbox.isSelected());
        deleteButton.setEnabled(enabled && parentCheckbox.isSelected() && !parentListModel.isEmpty());
        fullRadioButton.setEnabled(enabled && parentCheckbox.isSelected());
        substringRadioButton.setEnabled(enabled && parentCheckbox.isSelected());
        includeRadioButton.setEnabled(enabled && parentCheckbox.isSelected());
        excludeRadioButton.setEnabled(enabled && parentCheckbox.isSelected());
    }

    /**
     * Update the user interface when a search has been cancelled.
     *
     * @param searchCancelledEvent The SearchCancelledEvent which was received.
     */
    @Subscribe
    void handleSearchCancelledEvent(DiscoveryEvents.SearchCancelledEvent searchCancelledEvent) {
        SwingUtilities.invokeLater(() -> {
            enableSearch(true);
        });
    }

    /**
     * Update the user interface when a search has been successfully completed.
     *
     * @param searchCompleteEvent The SearchCompleteEvent which was received.
     */
    @Subscribe
    void handleSearchCompleteEvent(DiscoveryEvents.SearchCompleteEvent searchCompleteEvent) {
        SwingUtilities.invokeLater(() -> {
            enableSearch(true);
        });
    }

    private void parentCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parentCheckboxActionPerformed
        parentList.setEnabled(parentCheckbox.isSelected());
        fullRadioButton.setEnabled(parentCheckbox.isSelected());
        substringRadioButton.setEnabled(parentCheckbox.isSelected());
        includeRadioButton.setEnabled(parentCheckbox.isSelected());
        excludeRadioButton.setEnabled(parentCheckbox.isSelected());
        parentTextField.setEnabled(parentCheckbox.isSelected());
        addButton.setEnabled(parentCheckbox.isSelected());
        deleteButton.setEnabled(parentCheckbox.isSelected() && !parentListModel.isEmpty());
    }//GEN-LAST:event_parentCheckboxActionPerformed

    private void keywordCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keywordCheckboxActionPerformed
        keywordList.setEnabled(keywordCheckbox.isSelected());
    }//GEN-LAST:event_keywordCheckboxActionPerformed

    private void sizeCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sizeCheckboxActionPerformed
        sizeList.setEnabled(sizeCheckbox.isSelected());
    }//GEN-LAST:event_sizeCheckboxActionPerformed

    private void crFrequencyCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_crFrequencyCheckboxActionPerformed
        crFrequencyList.setEnabled(crFrequencyCheckbox.isSelected());
    }//GEN-LAST:event_crFrequencyCheckboxActionPerformed

    private void addButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButtonActionPerformed
        if (!parentTextField.getText().isEmpty()) {
            ParentSearchTerm searchTerm;
            searchTerm = new ParentSearchTerm(parentTextField.getText(), fullRadioButton.isSelected(), includeRadioButton.isSelected());
            parentListModel.add(parentListModel.size(), searchTerm);
            validateFields();
            parentTextField.setText("");
        }
    }//GEN-LAST:event_addButtonActionPerformed

    /**
     * Cancel the current search.
     */
    void cancelSearch() {
        if (searchWorker != null) {
            searchWorker.cancel(true);
        }
    }

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        int index = parentList.getSelectedIndex();
        if (index >= 0) {
            parentListModel.remove(index);
        }
        validateFields();
    }//GEN-LAST:event_deleteButtonActionPerformed

    private void parentListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_parentListValueChanged
        if (parentList.getSelectedValuesList().isEmpty()) {
            deleteButton.setEnabled(false);
        } else {
            deleteButton.setEnabled(true);
        }
    }//GEN-LAST:event_parentListValueChanged

    private void dataSourceCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourceCheckboxActionPerformed
        dataSourceList.setEnabled(dataSourceCheckbox.isSelected());
    }//GEN-LAST:event_dataSourceCheckboxActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        cancelSearch();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void hashSetCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hashSetCheckboxActionPerformed
        hashSetList.setEnabled(hashSetCheckbox.isSelected());
    }//GEN-LAST:event_hashSetCheckboxActionPerformed

    private void objectsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_objectsCheckboxActionPerformed
        objectsList.setEnabled(objectsCheckbox.isSelected());
    }//GEN-LAST:event_objectsCheckboxActionPerformed

    private void tagsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tagsCheckboxActionPerformed
        tagsList.setEnabled(tagsCheckbox.isSelected());
    }//GEN-LAST:event_tagsCheckboxActionPerformed

    private void interestingItemsCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_interestingItemsCheckboxActionPerformed
        interestingItemsList.setEnabled(interestingItemsCheckbox.isSelected());
    }//GEN-LAST:event_interestingItemsCheckboxActionPerformed

    private void scoreCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_scoreCheckboxActionPerformed
        scoreList.setEnabled(scoreCheckbox.isSelected());
    }//GEN-LAST:event_scoreCheckboxActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JRadioButton attributeRadioButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JCheckBox crFrequencyCheckbox;
    private javax.swing.JList<Frequency> crFrequencyList;
    private javax.swing.JScrollPane crFrequencyScrollPane;
    private javax.swing.JCheckBox dataSourceCheckbox;
    private javax.swing.JList<DataSourceItem> dataSourceList;
    private javax.swing.JScrollPane dataSourceScrollPane;
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JRadioButton excludeRadioButton;
    private javax.swing.JCheckBox exifCheckbox;
    private javax.swing.JComboBox<FileType> fileTypeComboBox;
    private javax.swing.JLabel fileTypeLabel;
    private javax.swing.JPanel filtersPanel;
    private javax.swing.JScrollPane filtersScrollPane;
    private javax.swing.JRadioButton fullRadioButton;
    private javax.swing.JComboBox<GroupingAttributeType> groupByCombobox;
    private javax.swing.JLabel groupByLabel;
    private javax.swing.JRadioButton groupSizeRadioButton;
    private javax.swing.JCheckBox hashSetCheckbox;
    private javax.swing.JList<String> hashSetList;
    private javax.swing.JScrollPane hashSetScrollPane;
    private javax.swing.JRadioButton includeRadioButton;
    private javax.swing.JCheckBox interestingItemsCheckbox;
    private javax.swing.JList<String> interestingItemsList;
    private javax.swing.JScrollPane interestingItemsScrollPane;
    private javax.swing.JCheckBox keywordCheckbox;
    private javax.swing.JList<String> keywordList;
    private javax.swing.JScrollPane keywordScrollPane;
    private javax.swing.JCheckBox notableCheckbox;
    private javax.swing.JCheckBox objectsCheckbox;
    private javax.swing.JList<String> objectsList;
    private javax.swing.JScrollPane objectsScrollPane;
    private javax.swing.JComboBox<SortingMethod> orderByCombobox;
    private javax.swing.JLabel orderByLabel;
    private javax.swing.ButtonGroup orderGroupsByButtonGroup;
    private javax.swing.JLabel orderGroupsByLabel;
    private javax.swing.JCheckBox parentCheckbox;
    private javax.swing.ButtonGroup parentIncludeButtonGroup;
    private javax.swing.JLabel parentLabel;
    private javax.swing.JList<ParentSearchTerm> parentList;
    private javax.swing.ButtonGroup parentPathButtonGroup;
    private javax.swing.JScrollPane parentScrollPane;
    private javax.swing.JTextField parentTextField;
    private javax.swing.JCheckBox scoreCheckbox;
    private javax.swing.JList<Score> scoreList;
    private javax.swing.JScrollPane scoreScrollPane;
    private javax.swing.JButton searchButton;
    private javax.swing.JCheckBox sizeCheckbox;
    private javax.swing.JList<FileSize> sizeList;
    private javax.swing.JScrollPane sizeScrollPane;
    private javax.swing.JPanel sortingPanel;
    private javax.swing.JRadioButton substringRadioButton;
    private javax.swing.JCheckBox tagsCheckbox;
    private javax.swing.JList<TagName> tagsList;
    private javax.swing.JScrollPane tagsScrollPane;
    // End of variables declaration//GEN-END:variables

}
