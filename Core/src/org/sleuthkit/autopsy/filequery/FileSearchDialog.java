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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
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
import org.sleuthkit.datamodel.TagName;

/**
 * Dialog to allow the user to choose filtering and grouping options.
 */
public class FileSearchDialog extends javax.swing.JDialog implements ActionListener {

    private final static Logger logger = Logger.getLogger(FileSearchDialog.class.getName());
    
    private DefaultListModel<ParentSearchTerm> parentListModel;
    private boolean runAnotherSearch = false;
    private final SleuthkitCase caseDb;
    private final EamDb centralRepoDb;
    
    /**
     * Creates new form FileSearchDialog
     */
    @NbBundle.Messages({
        "FileSearchDialog.dialogTitle.text=Test file search",
    }) 
    public FileSearchDialog(java.awt.Frame parent, boolean modal, SleuthkitCase caseDb, EamDb centralRepoDb) {
        super((JFrame) WindowManager.getDefault().getMainWindow(), Bundle.FileSearchDialog_dialogTitle_text(), modal);
        this.caseDb = caseDb;
        this.centralRepoDb = centralRepoDb;
        initComponents();
        customizeComponents();
    }
    
    /**
     * Show the dialog
     */
    void display() {
        this.setLocationRelativeTo(WindowManager.getDefault().getMainWindow());
        runAnotherSearch = false;
        setVisible(true);
    }
    
    /**
     * Set up all the UI components
     */
    private void customizeComponents() {
        
        errorLabel.setVisible(false);
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
        
        // Set up the grouping attributes
        for (GroupingAttributeType type : GroupingAttributeType.values()) {
            groupComboBox.addItem(type);
        }
        
        // Set up the group order buttons
        orderButtonGroup.add(orderAttrRadioButton);
        orderButtonGroup.add(orderSizeRadioButton);
        orderAttrRadioButton.setSelected(true);
        
        // Set up the file order list
        for (SortingMethod method : SortingMethod.values()) {
            fileOrderComboBox.addItem(method);
        }
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
        int count = 0;
        DefaultListModel<FileType> fileTypeListModel = (DefaultListModel<FileType>)fileTypeList.getModel();
        for (FileType type : FileType.getOptionsForFiltering()) {
            fileTypeListModel.add(count, type);
            count++;
        }
        addListeners(null, fileTypeList);
    }
    
    /**
     * Initialize the data source filter
     */
    private void setUpDataSourceFilter() {
        int count = 0;
        try {
            DefaultListModel<DataSourceItem> dsListModel = (DefaultListModel<DataSourceItem>)dsList.getModel();
            for(DataSource ds : caseDb.getDataSources()) {
                dsListModel.add(count, new DataSourceItem(ds));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading data sources", ex);
            dsCheckBox.setEnabled(false);
            dsList.setEnabled(false);
        }
        addListeners(dsCheckBox, dsList);
    }
    
    /**
     * Initialize the frequency filter
     */
    private void setUpFrequencyFilter() {
        if (centralRepoDb == null) {
            freqList.setEnabled(false);
            freqCheckBox.setEnabled(false);
        } else {
            int count = 0;
            DefaultListModel<Frequency> frequencyListModel = (DefaultListModel<Frequency>)freqList.getModel();
            for (Frequency freq : Frequency.getOptionsForFiltering()) {
                frequencyListModel.add(count, freq);
            }
        }
        addListeners(freqCheckBox, freqList);
    }
    
    /**
     * Initialize the file size filter
     */
    private void setUpSizeFilter() {
        int count = 0;
        DefaultListModel<FileSize> sizeListModel = (DefaultListModel<FileSize>)sizeList.getModel();
        for (FileSize size : FileSize.values()) {
            sizeListModel.add(count, size);
        }
        addListeners(sizeCheckBox, sizeList);
    }
    
    /**
     * Initialize the keyword list names filter
     */
    private void setUpKWFilter() {
        int count = 0;
        try {
            DefaultListModel<String> kwListModel = (DefaultListModel<String>)kwList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for(String name : setNames) {
                kwListModel.add(count, name);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading keyword list names", ex);
            kwCheckBox.setEnabled(false);
            kwList.setEnabled(false);
        } 
        addListeners(kwCheckBox, kwList);
    }
    
    private void setUpHashFilter() {
        int count = 0;
        try {
            DefaultListModel<String> hashListModel = (DefaultListModel<String>)hashList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for(String name : setNames) {
                hashListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading hash set names", ex);
            hashCheckBox.setEnabled(false);
            hashList.setEnabled(false);
        } 
        addListeners(hashCheckBox, hashList);
    }
    
    private void setUpInterestingItemsFilter() {
        int count = 0;
        try {
            DefaultListModel<String> intListModel = (DefaultListModel<String>)intList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT,
                    BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME);
            for(String name : setNames) {
                intListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading interesting file set names", ex);
            intCheckBox.setEnabled(false);
            intList.setEnabled(false);
        } 
        addListeners(intCheckBox, intList);
    }
    
    private void setUpTagsFilter() {
        int count = 0;
        try {
            DefaultListModel<TagName> tagsListModel = (DefaultListModel<TagName>)tagsList.getModel();
            
            List<TagName> tagNames = caseDb.getTagNamesInUse();
            for(TagName name : tagNames) {
                tagsListModel.add(count, name);
                count++;
            }
            tagsList.setCellRenderer(new TagsListCellRenderer());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading tag names", ex);
            tagsCheckBox.setEnabled(false);
            tagsList.setEnabled(false);
        } 
        addListeners(tagsCheckBox, tagsList);
    }
    
    private class TagsListCellRenderer extends DefaultListCellRenderer {

        @Override
        public java.awt.Component getListCellRendererComponent(
                                       JList<?> list,
                                       Object value,
                                       int index,
                                       boolean isSelected,
                                       boolean cellHasFocus) {
            if (value instanceof TagName) {
                value = ((TagName)value).getDisplayName();
            }
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            return this;
        }
    }
    
    private void setUpObjectFilter() {
        int count = 0;
        try {
            DefaultListModel<String> objListModel = (DefaultListModel<String>)objList.getModel();
            
            List<String> setNames = getSetNames(BlackboardArtifact.ARTIFACT_TYPE.TSK_OBJECT_DETECTED, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION);
            for(String name : setNames) {
                objListModel.add(count, name);
                count++;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error loading object detected set names", ex);
            objCheckBox.setEnabled(false);
            objList.setEnabled(false);
        } 
        addListeners(objCheckBox, objList);
    }
    
    private List<String> getSetNames(BlackboardArtifact.ARTIFACT_TYPE artifactType, BlackboardAttribute.ATTRIBUTE_TYPE setNameAttribute) throws TskCoreException {
        List<BlackboardArtifact> arts = caseDb.getBlackboardArtifacts(artifactType);
        List<String> setNames = new ArrayList<>();
        for (BlackboardArtifact art : arts) {
            for (BlackboardAttribute attr : art.getAttributes()) {
                if (attr.getAttributeType().getTypeID() == setNameAttribute.getTypeID()) {
                    String setName = attr.getValueString();
                    if ( ! setNames.contains(setName)) {
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
        parentButtonGroup.add(parentFullRadioButton);
        parentButtonGroup.add(parentSubstringRadioButton);
        parentFullRadioButton.setSelected(true);
        parentListModel = (DefaultListModel<ParentSearchTerm>)parentList.getModel();
        
        addListeners(parentCheckBox, parentList);
    }
    
    /**
     * Get a list of all filters selected by the user.
     * 
     * @return the list of filters
     */
    List<FileSearchFiltering.FileFilter> getFilters() {
        List<FileSearchFiltering.FileFilter> filters = new ArrayList<>();
        
        // There will always be a file type selected
        filters.add(new FileSearchFiltering.FileTypeFilter(fileTypeList.getSelectedValuesList()));
        
        if (parentCheckBox.isSelected()) {
            // For the parent paths, everything in the box is used (not just the selected entries)
            filters.add(new FileSearchFiltering.ParentFilter(getParentPaths()));
        }
        
        if (dsCheckBox.isSelected()) {
            List<DataSource> dataSources = dsList.getSelectedValuesList().stream().map(t -> t.ds).collect(Collectors.toList());
            filters.add(new FileSearchFiltering.DataSourceFilter(dataSources));
        }
        
        if (freqCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.FrequencyFilter(freqList.getSelectedValuesList()));
        }
        
        if (sizeCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.SizeFilter(sizeList.getSelectedValuesList()));
        }
        
        if (kwCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.KeywordListFilter(kwList.getSelectedValuesList()));
        }
        
        if (hashCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.HashSetFilter(hashList.getSelectedValuesList()));
        }
        
        if (intCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.InterestingFileSetFilter(intList.getSelectedValuesList()));
        }
        
        if (objCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.ObjectDetectionFilter(objList.getSelectedValuesList()));
        }
        
        if (tagsCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.TagsFilter(tagsList.getSelectedValuesList()));
        }
        
        if (exifCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.ExifFilter());
        }
        
        if (notableCheckBox.isSelected()) {
            filters.add(new FileSearchFiltering.NotableFilter());
        }
        
        return filters;
    }
    
    /**
     * Utility method to get the parent path objects out of the JList.
     * 
     * @return The list of entered ParentSearchTerm objects
     */
    private List<ParentSearchTerm> getParentPaths() {
        List<ParentSearchTerm> results = new ArrayList<>();
        for (int i = 0;i < parentListModel.getSize();i++) {
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
        GroupingAttributeType groupingAttrType = (GroupingAttributeType)groupComboBox.getSelectedItem();
        return groupingAttrType.getAttributeType();
    }
    
    /**
     * Get the sorting method for groups.
     * 
     * @return the selected sorting method 
     */
    FileGroup.GroupSortingAlgorithm getGroupSortingMethod() {
        if (orderAttrRadioButton.isSelected()) {
            return FileGroup.GroupSortingAlgorithm.BY_GROUP_KEY;
        }
        return FileGroup.GroupSortingAlgorithm.BY_GROUP_SIZE;
    }
    
    /**
     * Get the sorting method for files.
     * 
     * @return the selected sorting method 
     */
    SortingMethod getFileSortingMethod() {
        return (SortingMethod)fileOrderComboBox.getSelectedItem();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        validateFields();
    }
    
    /**
     * Utility class to allow us to display the data source ID along with the name
     */
    private class DataSourceItem {
        private DataSource ds;
        
        DataSourceItem(DataSource ds) {
            this.ds = ds;
        }
        
        @Override
        public String toString() {
            return ds.getName() + " (ID: " + ds.getId() + ")";
        }
    }
    
    /**
     * Check whether the user chose to run the search or cancel
     * 
     * @return true if the search was cancelled, false otherwise
     */
    boolean searchCancelled() {
        return (! runAnotherSearch);
    }
    
    /**
     * Validate the form.
     * If we use any of this in the final dialog we should use bundle messages.
     */
    private void validateFields() {
        
        // There must be at least one file type selected
        if (fileTypeList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one file type must be selected");
            return;
        }
        
        // For most enabled filters, there should be something selected
        if (dsCheckBox.isSelected() && dsList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one data source must be selected");
            return;
        }
        if (freqCheckBox.isSelected() && freqList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one CR frequency must be selected");
            return;
        }
        if (sizeCheckBox.isSelected() && sizeList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one size must be selected");
            return;
        }
        if (kwCheckBox.isSelected() && kwList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one keyword list name must be selected");
            return;
        }
        
        // Parent uses everything in the box
        if (parentCheckBox.isSelected() && getParentPaths().isEmpty()) {
            setInvalid("At least one parent path must be entered");
            return;
        }
        
        if (hashCheckBox.isSelected() && hashList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one hash set name must be selected");
            return;
        }     
        
        if (intCheckBox.isSelected() && intList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one interesting file set name must be selected");
            return;
        } 
        
        if (objCheckBox.isSelected() && objList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one object type name must be selected");
            return;
        } 
        
        if (tagsCheckBox.isSelected() && tagsList.getSelectedValuesList().isEmpty()) {
            setInvalid("At least one tag name must be selected");
            return;
        } 
        
        setValid();
        
    }
    
    /**
     * The settings are valid so enable the Search button
     */
    private void setValid() {
        errorLabel.setVisible(false);
        searchButton.setEnabled(true);
    }
    
    /**
     * The settings are not valid so disable the search button and
     * display the given error message.
     * 
     * @param error 
     */
    private void setInvalid(String error) {
        errorLabel.setText(error);
        errorLabel.setVisible(true);
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

        parentButtonGroup = new javax.swing.ButtonGroup();
        orderButtonGroup = new javax.swing.ButtonGroup();
        jLabel1 = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();
        dsCheckBox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        fileTypeList = new javax.swing.JList<>();
        jScrollPane2 = new javax.swing.JScrollPane();
        dsList = new javax.swing.JList<>();
        freqCheckBox = new javax.swing.JCheckBox();
        jScrollPane3 = new javax.swing.JScrollPane();
        freqList = new javax.swing.JList<>();
        jScrollPane4 = new javax.swing.JScrollPane();
        sizeList = new javax.swing.JList<>();
        sizeCheckBox = new javax.swing.JCheckBox();
        jScrollPane5 = new javax.swing.JScrollPane();
        kwList = new javax.swing.JList<>();
        kwCheckBox = new javax.swing.JCheckBox();
        jScrollPane6 = new javax.swing.JScrollPane();
        parentList = new javax.swing.JList<>();
        parentCheckBox = new javax.swing.JCheckBox();
        deleteParentButton = new javax.swing.JButton();
        addParentButton = new javax.swing.JButton();
        parentTextField = new javax.swing.JTextField();
        parentFullRadioButton = new javax.swing.JRadioButton();
        parentSubstringRadioButton = new javax.swing.JRadioButton();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        groupComboBox = new javax.swing.JComboBox<>();
        jLabel4 = new javax.swing.JLabel();
        orderAttrRadioButton = new javax.swing.JRadioButton();
        orderSizeRadioButton = new javax.swing.JRadioButton();
        jLabel5 = new javax.swing.JLabel();
        fileOrderComboBox = new javax.swing.JComboBox<>();
        searchButton = new javax.swing.JButton();
        errorLabel = new javax.swing.JLabel();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(8, 7), new java.awt.Dimension(8, 7), new java.awt.Dimension(8, 7));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(265, 23), new java.awt.Dimension(265, 23), new java.awt.Dimension(265, 23));
        hashCheckBox = new javax.swing.JCheckBox();
        jScrollPane7 = new javax.swing.JScrollPane();
        hashList = new javax.swing.JList<>();
        intCheckBox = new javax.swing.JCheckBox();
        jScrollPane8 = new javax.swing.JScrollPane();
        intList = new javax.swing.JList<>();
        jScrollPane9 = new javax.swing.JScrollPane();
        tagsList = new javax.swing.JList<>();
        tagsCheckBox = new javax.swing.JCheckBox();
        jScrollPane10 = new javax.swing.JScrollPane();
        objList = new javax.swing.JList<>();
        objCheckBox = new javax.swing.JCheckBox();
        exifCheckBox = new javax.swing.JCheckBox();
        notableCheckBox = new javax.swing.JCheckBox();
        jCheckBox3 = new javax.swing.JCheckBox();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(dsCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.dsCheckBox.text")); // NOI18N
        dsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dsCheckBoxActionPerformed(evt);
            }
        });

        fileTypeList.setModel(new DefaultListModel<FileType>());
        jScrollPane1.setViewportView(fileTypeList);

        dsList.setModel(new DefaultListModel<DataSourceItem>());
        dsList.setEnabled(false);
        jScrollPane2.setViewportView(dsList);

        org.openide.awt.Mnemonics.setLocalizedText(freqCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.freqCheckBox.text")); // NOI18N
        freqCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                freqCheckBoxActionPerformed(evt);
            }
        });

        freqList.setModel(new DefaultListModel<Frequency>());
        freqList.setEnabled(false);
        jScrollPane3.setViewportView(freqList);

        sizeList.setModel(new DefaultListModel<FileSize>());
        sizeList.setEnabled(false);
        jScrollPane4.setViewportView(sizeList);

        org.openide.awt.Mnemonics.setLocalizedText(sizeCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.sizeCheckBox.text")); // NOI18N
        sizeCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sizeCheckBoxActionPerformed(evt);
            }
        });

        kwList.setModel(new DefaultListModel<String>());
        kwList.setEnabled(false);
        jScrollPane5.setViewportView(kwList);

        org.openide.awt.Mnemonics.setLocalizedText(kwCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.kwCheckBox.text")); // NOI18N
        kwCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                kwCheckBoxActionPerformed(evt);
            }
        });

        parentList.setModel(new DefaultListModel<ParentSearchTerm>());
        parentList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        parentList.setEnabled(false);
        parentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                parentListValueChanged(evt);
            }
        });
        jScrollPane6.setViewportView(parentList);

        org.openide.awt.Mnemonics.setLocalizedText(parentCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentCheckBox.text")); // NOI18N
        parentCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                parentCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(deleteParentButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.deleteParentButton.text")); // NOI18N
        deleteParentButton.setEnabled(false);
        deleteParentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteParentButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(addParentButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.addParentButton.text")); // NOI18N
        addParentButton.setEnabled(false);
        addParentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addParentButtonActionPerformed(evt);
            }
        });

        parentTextField.setText(org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentTextField.text")); // NOI18N
        parentTextField.setEnabled(false);
        parentTextField.setMaximumSize(new java.awt.Dimension(6, 20));

        org.openide.awt.Mnemonics.setLocalizedText(parentFullRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentFullRadioButton.text")); // NOI18N
        parentFullRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(parentSubstringRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.parentSubstringRadioButton.text")); // NOI18N
        parentSubstringRadioButton.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(jLabel2, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel2.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel3, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel3.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel4, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel4.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderAttrRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.orderAttrRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderSizeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.orderSizeRadioButton.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(jLabel5, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jLabel5.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.errorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(hashCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.hashCheckBox.text")); // NOI18N
        hashCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                hashCheckBoxActionPerformed(evt);
            }
        });

        hashList.setModel(new DefaultListModel<String>());
        hashList.setEnabled(false);
        jScrollPane7.setViewportView(hashList);

        org.openide.awt.Mnemonics.setLocalizedText(intCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.intCheckBox.text")); // NOI18N
        intCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                intCheckBoxActionPerformed(evt);
            }
        });

        intList.setModel(new DefaultListModel<String>());
        intList.setEnabled(false);
        jScrollPane8.setViewportView(intList);

        tagsList.setModel(new DefaultListModel<TagName>());
        tagsList.setEnabled(false);
        jScrollPane9.setViewportView(tagsList);

        org.openide.awt.Mnemonics.setLocalizedText(tagsCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.tagsCheckBox.text")); // NOI18N
        tagsCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tagsCheckBoxActionPerformed(evt);
            }
        });

        objList.setModel(new DefaultListModel<String>());
        objList.setEnabled(false);
        jScrollPane10.setViewportView(objList);

        org.openide.awt.Mnemonics.setLocalizedText(objCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.objCheckBox.text")); // NOI18N
        objCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                objCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(exifCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.exifCheckBox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(notableCheckBox, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.notableCheckBox.text")); // NOI18N
        notableCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                notableCheckBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jCheckBox3, org.openide.util.NbBundle.getMessage(FileSearchDialog.class, "FileSearchDialog.jCheckBox3.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dsCheckBox)
                    .addComponent(jLabel1)
                    .addComponent(freqCheckBox)
                    .addComponent(sizeCheckBox)
                    .addComponent(kwCheckBox)
                    .addComponent(parentCheckBox)
                    .addComponent(jLabel2))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(parentTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(parentFullRadioButton)
                                .addGap(18, 18, 18)
                                .addComponent(parentSubstringRadioButton)
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(deleteParentButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(addParentButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane5)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, 265, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(searchButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGap(344, 344, 344))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(hashCheckBox)
                                .addGap(18, 18, 18)
                                .addComponent(jScrollPane7))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(intCheckBox)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jScrollPane8))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(tagsCheckBox)
                                .addGap(18, 18, 18)
                                .addComponent(jScrollPane9))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(objCheckBox)
                                .addGap(18, 18, 18)
                                .addComponent(jScrollPane10)))
                        .addGap(35, 35, 35)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel3)
                            .addComponent(jLabel4)
                            .addComponent(jLabel5))
                        .addGap(29, 29, 29)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(orderAttrRadioButton)
                            .addComponent(groupComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(orderSizeRadioButton)
                            .addComponent(fileOrderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 144, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap())
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(filler2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(exifCheckBox)
                            .addComponent(notableCheckBox)
                            .addComponent(jCheckBox3))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(filler1, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(filler2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel1)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 82, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane7, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(hashCheckBox))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dsCheckBox)
                            .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 56, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jScrollPane10, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(objCheckBox)))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel3)
                            .addComponent(groupComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(5, 5, 5)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel4)
                            .addComponent(orderAttrRadioButton))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(orderSizeRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel5)
                            .addComponent(fileOrderComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(11, 11, 11)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(freqCheckBox)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 54, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jScrollPane9, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(tagsCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane4, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(sizeCheckBox)
                    .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(intCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 49, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(kwCheckBox)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(exifCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(notableCheckBox)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(parentCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel2))
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jCheckBox3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(deleteParentButton)
                    .addComponent(parentFullRadioButton)
                    .addComponent(parentSubstringRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(addParentButton)
                    .addComponent(parentTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(errorLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(searchButton)
                    .addComponent(cancelButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        setVisible(false);
        dispose();
        runAnotherSearch = false;
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void dsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dsCheckBoxActionPerformed
        dsList.setEnabled(dsCheckBox.isSelected());
    }//GEN-LAST:event_dsCheckBoxActionPerformed

    private void parentListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_parentListValueChanged
        if (parentList.getSelectedValuesList().isEmpty()) {
            deleteParentButton.setEnabled(false);
        } else {
            deleteParentButton.setEnabled(true);
        }
    }//GEN-LAST:event_parentListValueChanged

    private void deleteParentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteParentButtonActionPerformed
        int index = parentList.getSelectedIndex();
        parentListModel.remove(index);
    }//GEN-LAST:event_deleteParentButtonActionPerformed

    private void addParentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addParentButtonActionPerformed
        if ( ! parentTextField.getText().isEmpty()) {
            ParentSearchTerm searchTerm;
            if (parentFullRadioButton.isSelected()) {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), true);
            } else {
                searchTerm = new ParentSearchTerm(parentTextField.getText(), false);
            }
            parentListModel.add(parentListModel.size(), searchTerm);
        }
    }//GEN-LAST:event_addParentButtonActionPerformed

    private void freqCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_freqCheckBoxActionPerformed
        freqList.setEnabled(freqCheckBox.isSelected());
    }//GEN-LAST:event_freqCheckBoxActionPerformed

    private void sizeCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sizeCheckBoxActionPerformed
        sizeList.setEnabled(sizeCheckBox.isSelected());
    }//GEN-LAST:event_sizeCheckBoxActionPerformed

    private void kwCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_kwCheckBoxActionPerformed
        kwList.setEnabled(kwCheckBox.isSelected());
    }//GEN-LAST:event_kwCheckBoxActionPerformed

    private void parentCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parentCheckBoxActionPerformed
        parentList.setEnabled(parentCheckBox.isSelected());
        parentFullRadioButton.setEnabled(parentCheckBox.isSelected());
        parentSubstringRadioButton.setEnabled(parentCheckBox.isSelected());
        parentTextField.setEnabled(parentCheckBox.isSelected());
        addParentButton.setEnabled(parentCheckBox.isSelected());
        deleteParentButton.setEnabled(parentCheckBox.isSelected());
    }//GEN-LAST:event_parentCheckBoxActionPerformed

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        runAnotherSearch = true;
        setVisible(false);
    }//GEN-LAST:event_searchButtonActionPerformed

    private void hashCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_hashCheckBoxActionPerformed
        hashList.setEnabled(hashCheckBox.isSelected());
    }//GEN-LAST:event_hashCheckBoxActionPerformed

    private void intCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_intCheckBoxActionPerformed
        intList.setEnabled(intCheckBox.isSelected());
    }//GEN-LAST:event_intCheckBoxActionPerformed

    private void tagsCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tagsCheckBoxActionPerformed
        tagsList.setEnabled(tagsCheckBox.isSelected());
    }//GEN-LAST:event_tagsCheckBoxActionPerformed

    private void objCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_objCheckBoxActionPerformed
        objList.setEnabled(objCheckBox.isSelected());
    }//GEN-LAST:event_objCheckBoxActionPerformed

    private void notableCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_notableCheckBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_notableCheckBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addParentButton;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton deleteParentButton;
    private javax.swing.JCheckBox dsCheckBox;
    private javax.swing.JList<DataSourceItem> dsList;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JCheckBox exifCheckBox;
    private javax.swing.JComboBox<SortingMethod> fileOrderComboBox;
    private javax.swing.JList<FileSearchData.FileType> fileTypeList;
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JCheckBox freqCheckBox;
    private javax.swing.JList<Frequency> freqList;
    private javax.swing.JComboBox<GroupingAttributeType> groupComboBox;
    private javax.swing.JCheckBox hashCheckBox;
    private javax.swing.JList<String> hashList;
    private javax.swing.JCheckBox intCheckBox;
    private javax.swing.JList<String> intList;
    private javax.swing.JCheckBox jCheckBox3;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JCheckBox kwCheckBox;
    private javax.swing.JList<String> kwList;
    private javax.swing.JCheckBox notableCheckBox;
    private javax.swing.JCheckBox objCheckBox;
    private javax.swing.JList<String> objList;
    private javax.swing.JRadioButton orderAttrRadioButton;
    private javax.swing.ButtonGroup orderButtonGroup;
    private javax.swing.JRadioButton orderSizeRadioButton;
    private javax.swing.ButtonGroup parentButtonGroup;
    private javax.swing.JCheckBox parentCheckBox;
    private javax.swing.JRadioButton parentFullRadioButton;
    private javax.swing.JList<ParentSearchTerm> parentList;
    private javax.swing.JRadioButton parentSubstringRadioButton;
    private javax.swing.JTextField parentTextField;
    private javax.swing.JButton searchButton;
    private javax.swing.JCheckBox sizeCheckBox;
    private javax.swing.JList<FileSize> sizeList;
    private javax.swing.JCheckBox tagsCheckBox;
    private javax.swing.JList<TagName> tagsList;
    // End of variables declaration//GEN-END:variables
}
