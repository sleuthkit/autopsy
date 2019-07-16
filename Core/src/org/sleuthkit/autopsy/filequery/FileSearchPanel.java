/*
 * Autopsy Forensic Browser
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
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.filequery.FileSearch.GroupingAttributeType;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileSize;
import org.sleuthkit.autopsy.filequery.FileSearchData.Frequency;
import org.sleuthkit.autopsy.filequery.FileSearchFiltering.ParentSearchTerm;
import org.sleuthkit.autopsy.filequery.FileSorter.SortingMethod;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.DataSource;

/**
 * Dialog to allow the user to choose filtering and grouping options.
 */
final class FileSearchPanel extends javax.swing.JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;
    private final static Logger logger = Logger.getLogger(FileSearchPanel.class.getName());

    private DefaultListModel<FileSearchFiltering.ParentSearchTerm> parentListModel;
    private final SleuthkitCase caseDb;
    private final EamDb centralRepoDb;

    /**
     * Creates new form FileSearchDialog
     */
    @NbBundle.Messages({
        "FileSearchPanel.dialogTitle.text=Test file search",})
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

        // Set up the grouping attributes
        for (FileSearch.GroupingAttributeType type : FileSearch.GroupingAttributeType.values()) {
            groupByCombobox.addItem(type);
        }

        // Set up the file order list
        for (FileSorter.SortingMethod method : FileSorter.SortingMethod.values()) {
            orderByCombobox.addItem(method);
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

            // TODO - create case DB query
            List<BlackboardArtifact> arts = caseDb.getBlackboardArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            List<String> setNames = new ArrayList<>();
            for (BlackboardArtifact art : arts) {
                for (BlackboardAttribute attr : art.getAttributes()) {
                    if (attr.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()) {
                        String setName = attr.getValueString();
                        if (!setNames.contains(setName)) {
                            setNames.add(setName);
                        }
                    }
                }
            }
            Collections.sort(setNames);
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
     * Initialize the parent path filter
     */
    private void setUpParentPathFilter() {
        parentButtonGroup.add(fullRadioButton);
        parentButtonGroup.add(substringRadioButton);
        fullRadioButton.setSelected(true);
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

        parentButtonGroup = new javax.swing.ButtonGroup();
        orderGroupsByButtonGroup = new javax.swing.ButtonGroup();
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

        setMinimumSize(new java.awt.Dimension(408, 0));

        filtersScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.filtersScrollPane.border.title"))); // NOI18N
        filtersScrollPane.setPreferredSize(new java.awt.Dimension(300, 483));

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
        gridBagConstraints.gridy = 4;
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

        parentButtonGroup.add(fullRadioButton);
        fullRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(fullRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.fullRadioButton.text")); // NOI18N
        fullRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(fullRadioButton, gridBagConstraints);

        parentButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.substringRadioButton.text")); // NOI18N
        substringRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(substringRadioButton, gridBagConstraints);

        parentTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
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
        gridBagConstraints.gridy = 7;
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
        gridBagConstraints.gridy = 6;
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
        keywordList.setVisibleRowCount(5);
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
        gridBagConstraints.gridy = 5;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(parentLabel, gridBagConstraints);

        parentList.setModel(new DefaultListModel<ParentSearchTerm>());
        parentList.setEnabled(false);
        parentList.setVisibleRowCount(5);
        parentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                parentListValueChanged(evt);
            }
        });
        parentScrollPane.setViewportView(parentList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(parentScrollPane, gridBagConstraints);

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
                            .addComponent(groupByCombobox, 0, 260, Short.MAX_VALUE)
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(4, 4, 4)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(fileTypeLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(fileTypeComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(sortingPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(filtersScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(4, 4, 4))
        );
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
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(errorLabel))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        searchButton.setEnabled(false);
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
        SearchWorker searchWorker = new SearchWorker(centralRepoDb, searchButton, searchType, filters, groupingAttr, groupSortAlgorithm, fileSort);
        searchWorker.execute();
    }//GEN-LAST:event_searchButtonActionPerformed


    private void parentCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parentCheckboxActionPerformed
        parentList.setEnabled(parentCheckbox.isSelected());
        fullRadioButton.setEnabled(parentCheckbox.isSelected());
        substringRadioButton.setEnabled(parentCheckbox.isSelected());
        parentTextField.setEnabled(parentCheckbox.isSelected());
        addButton.setEnabled(parentCheckbox.isSelected());
        deleteButton.setEnabled(parentCheckbox.isSelected());
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
            if (fullRadioButton.isSelected()) {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), true);
            } else {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), false);
            }
            parentListModel.add(parentListModel.size(), searchTerm);
        }
    }//GEN-LAST:event_addButtonActionPerformed

    private void deleteButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteButtonActionPerformed
        int index = parentList.getSelectedIndex();
        parentListModel.remove(index);
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


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addButton;
    private javax.swing.JRadioButton attributeRadioButton;
    private javax.swing.JCheckBox crFrequencyCheckbox;
    private javax.swing.JList<Frequency> crFrequencyList;
    private javax.swing.JScrollPane crFrequencyScrollPane;
    private javax.swing.JCheckBox dataSourceCheckbox;
    private javax.swing.JList<DataSourceItem> dataSourceList;
    private javax.swing.JScrollPane dataSourceScrollPane;
    private javax.swing.JButton deleteButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JComboBox<FileType> fileTypeComboBox;
    private javax.swing.JLabel fileTypeLabel;
    private javax.swing.JPanel filtersPanel;
    private javax.swing.JScrollPane filtersScrollPane;
    private javax.swing.JRadioButton fullRadioButton;
    private javax.swing.JComboBox<GroupingAttributeType> groupByCombobox;
    private javax.swing.JLabel groupByLabel;
    private javax.swing.JRadioButton groupSizeRadioButton;
    private javax.swing.JCheckBox keywordCheckbox;
    private javax.swing.JList<String> keywordList;
    private javax.swing.JScrollPane keywordScrollPane;
    private javax.swing.JComboBox<SortingMethod> orderByCombobox;
    private javax.swing.JLabel orderByLabel;
    private javax.swing.ButtonGroup orderGroupsByButtonGroup;
    private javax.swing.JLabel orderGroupsByLabel;
    private javax.swing.ButtonGroup parentButtonGroup;
    private javax.swing.JCheckBox parentCheckbox;
    private javax.swing.JLabel parentLabel;
    private javax.swing.JList<ParentSearchTerm> parentList;
    private javax.swing.JScrollPane parentScrollPane;
    private javax.swing.JTextField parentTextField;
    private javax.swing.JButton searchButton;
    private javax.swing.JCheckBox sizeCheckbox;
    private javax.swing.JList<FileSize> sizeList;
    private javax.swing.JScrollPane sizeScrollPane;
    private javax.swing.JPanel sortingPanel;
    private javax.swing.JRadioButton substringRadioButton;
    // End of variables declaration//GEN-END:variables

}
