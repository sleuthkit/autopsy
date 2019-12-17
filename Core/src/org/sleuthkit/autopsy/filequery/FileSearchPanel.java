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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.filequery.FileGroup.GroupSortingAlgorithm;
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
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TagName;

/**
 * Dialog to allow the user to choose filtering and grouping options.
 */
final class FileSearchPanel extends javax.swing.JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final String[] DEFAULT_IGNORED_PATHS = {"/Windows/", "/Program Files/"}; //NON-NLS
    private final static Logger logger = Logger.getLogger(FileSearchPanel.class.getName());
    private FileType fileType = FileType.IMAGE;
    private DefaultListModel<FileSearchFiltering.ParentSearchTerm> parentListModel;
    private SearchWorker searchWorker = null;

    /**
     * Creates new form FileSearchDialog
     */
    @NbBundle.Messages({"FileSearchPanel.dialogTitle.text=Test file search"})
    FileSearchPanel() {
        initComponents();
        for (GroupSortingAlgorithm groupSortAlgorithm : GroupSortingAlgorithm.values()) {
            groupSortingComboBox.addItem(groupSortAlgorithm);
        }
        parentListModel = (DefaultListModel<FileSearchFiltering.ParentSearchTerm>) parentList.getModel();
        for (String ignorePath : DEFAULT_IGNORED_PATHS) {
            parentListModel.add(parentListModel.size(), new ParentSearchTerm(ignorePath, false, false));
        }
    }

    /**
     * Setup the data source filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void dataSourceFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        dataSourceCheckbox.setVisible(visible);
        dataSourceScrollPane.setVisible(visible);
        dataSourceList.setVisible(visible);
        dataSourceCheckbox.setEnabled(enabled);
        dataSourceCheckbox.setSelected(selected);
        if (dataSourceCheckbox.isEnabled() && dataSourceCheckbox.isSelected()) {
            dataSourceScrollPane.setEnabled(true);
            dataSourceList.setEnabled(true);
            if (indicesSelected != null) {
                dataSourceList.setSelectedIndices(indicesSelected);
            }
        } else {
            dataSourceScrollPane.setEnabled(false);
            dataSourceList.setEnabled(false);
        }
    }

    /**
     * Setup the file size filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void sizeFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        sizeCheckbox.setVisible(visible);
        sizeScrollPane.setVisible(visible);
        sizeList.setVisible(visible);
        sizeCheckbox.setEnabled(enabled);
        sizeCheckbox.setSelected(selected);
        if (sizeCheckbox.isEnabled() && sizeCheckbox.isSelected()) {
            sizeScrollPane.setEnabled(true);
            sizeList.setEnabled(true);
            if (indicesSelected != null) {
                sizeList.setSelectedIndices(indicesSelected);
            }
        } else {
            sizeScrollPane.setEnabled(false);
            sizeList.setEnabled(false);
        }
    }

    /**
     * Setup the central repository frequency filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void crFrequencyFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        crFrequencyCheckbox.setVisible(visible);
        crFrequencyScrollPane.setVisible(visible);
        crFrequencyList.setVisible(visible);
        crFrequencyCheckbox.setEnabled(enabled);
        crFrequencyCheckbox.setSelected(selected);
        if (crFrequencyCheckbox.isEnabled() && crFrequencyCheckbox.isSelected()) {
            crFrequencyScrollPane.setEnabled(true);
            crFrequencyList.setEnabled(true);
            if (indicesSelected != null) {
                crFrequencyList.setSelectedIndices(indicesSelected);
            }
        } else {
            crFrequencyScrollPane.setEnabled(false);
            crFrequencyList.setEnabled(false);
        }
    }

    /**
     * Setup the objects filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void objectsFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        objectsCheckbox.setVisible(visible);
        objectsScrollPane.setVisible(visible);
        objectsList.setVisible(visible);
        boolean hasObjects = objectsList.getModel().getSize() > 0;
        objectsCheckbox.setEnabled(enabled && hasObjects);
        objectsCheckbox.setSelected(selected && hasObjects);
        if (objectsCheckbox.isEnabled() && objectsCheckbox.isSelected()) {
            objectsScrollPane.setEnabled(true);
            objectsList.setEnabled(true);
            if (indicesSelected != null) {
                objectsList.setSelectedIndices(indicesSelected);
            }
        } else {
            objectsScrollPane.setEnabled(false);
            objectsList.setEnabled(false);
        }
    }

    /**
     * Setup the hash set filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void hashSetFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        hashSetCheckbox.setVisible(visible);
        hashSetScrollPane.setVisible(visible);
        hashSetList.setVisible(visible);
        boolean hasHashSets = hashSetList.getModel().getSize() > 0;
        hashSetCheckbox.setEnabled(enabled && hasHashSets);
        hashSetCheckbox.setSelected(selected && hasHashSets);
        if (hashSetCheckbox.isEnabled() && hashSetCheckbox.isSelected()) {
            hashSetScrollPane.setEnabled(true);
            hashSetList.setEnabled(true);
            if (indicesSelected != null) {
                hashSetList.setSelectedIndices(indicesSelected);
            }
        } else {
            hashSetScrollPane.setEnabled(false);
            hashSetList.setEnabled(false);
        }
    }

    /**
     * Setup the interesting items filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void interestingItemsFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        interestingItemsCheckbox.setVisible(visible);
        interestingItemsScrollPane.setVisible(visible);
        interestingItemsList.setVisible(visible);
        boolean hasInterestingItems = interestingItemsList.getModel().getSize() > 0;
        interestingItemsCheckbox.setEnabled(enabled && hasInterestingItems);
        interestingItemsCheckbox.setSelected(selected && hasInterestingItems);
        if (interestingItemsCheckbox.isEnabled() && interestingItemsCheckbox.isSelected()) {
            interestingItemsScrollPane.setEnabled(true);
            interestingItemsList.setEnabled(true);
            if (indicesSelected != null) {
                interestingItemsList.setSelectedIndices(indicesSelected);
            }
        } else {
            interestingItemsScrollPane.setEnabled(false);
            interestingItemsList.setEnabled(false);
        }
    }

    /**
     * Setup the score filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void scoreFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        scoreCheckbox.setVisible(visible);
        scoreScrollPane.setVisible(visible);
        scoreList.setVisible(visible);
        scoreCheckbox.setEnabled(enabled);
        scoreCheckbox.setSelected(selected);
        if (scoreCheckbox.isEnabled() && scoreCheckbox.isSelected()) {
            scoreScrollPane.setEnabled(true);
            scoreList.setEnabled(true);
            if (indicesSelected != null) {
                scoreList.setSelectedIndices(indicesSelected);
            }
        } else {
            scoreScrollPane.setEnabled(false);
            scoreList.setEnabled(false);
        }
    }

    /**
     * Setup the parent path filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void parentFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        parentCheckbox.setVisible(visible);
        parentScrollPane.setVisible(visible);
        parentList.setVisible(visible);
        parentCheckbox.setEnabled(enabled);
        parentCheckbox.setSelected(selected);
        if (parentCheckbox.isEnabled() && parentCheckbox.isSelected()) {
            parentScrollPane.setEnabled(true);
            includeRadioButton.setEnabled(true);
            excludeRadioButton.setEnabled(true);
            fullRadioButton.setEnabled(true);
            substringRadioButton.setEnabled(true);
            addButton.setEnabled(true);
            deleteButton.setEnabled(!parentListModel.isEmpty());
            parentList.setEnabled(true);
            parentTextField.setEnabled(true);
            if (indicesSelected != null) {
                parentList.setSelectedIndices(indicesSelected);
            }
        } else {
            parentScrollPane.setEnabled(false);
            parentList.setEnabled(false);
            includeRadioButton.setEnabled(false);
            excludeRadioButton.setEnabled(false);
            fullRadioButton.setEnabled(false);
            substringRadioButton.setEnabled(false);
            addButton.setEnabled(false);
            deleteButton.setEnabled(false);
            parentTextField.setEnabled(false);
        }
    }

    /**
     * Setup the tags filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void tagsFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        tagsCheckbox.setVisible(visible);
        tagsScrollPane.setVisible(visible);
        tagsList.setVisible(visible);
        tagsCheckbox.setEnabled(enabled);
        tagsCheckbox.setSelected(selected);
        if (tagsCheckbox.isEnabled() && tagsCheckbox.isSelected()) {
            tagsScrollPane.setEnabled(true);
            tagsList.setEnabled(true);
            if (indicesSelected != null) {
                tagsList.setSelectedIndices(indicesSelected);
            }
        } else {
            tagsScrollPane.setEnabled(false);
            tagsList.setEnabled(false);
        }
    }

    /**
     * Setup the keyword filter settings.
     *
     * @param visible         Boolean indicating if the filter should be
     *                        visible.
     * @param enabled         Boolean indicating if the filter should be
     *                        enabled.
     * @param selected        Boolean indicating if the filter should be
     *                        selected.
     * @param indicesSelected Array of integers indicating which list items are
     *                        selected, null to indicate leaving selected items
     *                        unchanged.
     */
    private void keywordFilterSettings(boolean visible, boolean enabled, boolean selected, int[] indicesSelected) {
        keywordCheckbox.setVisible(visible);
        keywordScrollPane.setVisible(visible);
        keywordList.setVisible(visible);
        keywordCheckbox.setEnabled(enabled);
        keywordCheckbox.setSelected(selected);
        if (keywordCheckbox.isEnabled() && keywordCheckbox.isSelected()) {
            keywordScrollPane.setEnabled(true);
            keywordList.setEnabled(true);
            if (indicesSelected != null) {
                keywordList.setSelectedIndices(indicesSelected);
            }
        } else {
            keywordScrollPane.setEnabled(false);
            keywordList.setEnabled(false);
        }
    }

    /**
     * Setup the exif filter settings.
     *
     * @param visible  Boolean indicating if the filter should be visible.
     * @param enabled  Boolean indicating if the filter should be enabled.
     * @param selected Boolean indicating if the filter should be selected.
     */
    private void exifFilterSettings(boolean visible, boolean enabled, boolean selected) {
        exifCheckbox.setVisible(visible);
        exifCheckbox.setEnabled(enabled);
        exifCheckbox.setSelected(selected);
    }

    /**
     * Setup the known status filter settings.
     *
     * @param visible  Boolean indicating if the filter should be visible.
     * @param enabled  Boolean indicating if the filter should be enabled.
     * @param selected Boolean indicating if the filter should be selected.
     */
    private void knownFilesFilterSettings(boolean visible, boolean enabled, boolean selected) {
        knownFilesCheckbox.setVisible(visible);
        knownFilesCheckbox.setEnabled(enabled);
        knownFilesCheckbox.setSelected(selected);
    }

    /**
     * Setup the notable filter settings.
     *
     * @param visible  Boolean indicating if the filter should be visible.
     * @param enabled  Boolean indicating if the filter should be enabled.
     * @param selected Boolean indicating if the filter should be selected.
     */
    private void notableFilterSettings(boolean visible, boolean enabled, boolean selected) {
        notableCheckbox.setVisible(visible);
        notableCheckbox.setEnabled(enabled);
        notableCheckbox.setSelected(selected);
    }

    /**
     * Set the UI elements available to be the set of UI elements available when
     * an Image search is being performed.
     *
     * @param enabled       Boolean indicating if the filters present for images
     *                      should be enabled.
     * @param resetSelected Boolean indicating if selection of the filters
     *                      present for images should be reset to their default
     *                      status.
     */
    private void imagesSelected(boolean enabled, boolean resetSelected) {
        dataSourceFilterSettings(true, enabled, !resetSelected && dataSourceCheckbox.isSelected(), null);
        int[] selectedSizeIndices = {1, 2, 3, 4, 5};
        sizeFilterSettings(true, enabled, resetSelected || sizeCheckbox.isSelected(), resetSelected == true ? selectedSizeIndices : null);
        int[] selectedFrequencyIndices;
        if (!EamDb.isEnabled()) {
            selectedFrequencyIndices = new int[]{0};
        } else {
            selectedFrequencyIndices = new int[]{1, 2, 3, 4, 5, 6, 7};
        }
        crFrequencyFilterSettings(true, enabled, resetSelected || crFrequencyCheckbox.isSelected(), resetSelected == true ? selectedFrequencyIndices : null);
        exifFilterSettings(true, enabled, !resetSelected && exifCheckbox.isSelected());
        objectsFilterSettings(true, enabled, !resetSelected && objectsCheckbox.isSelected(), null);
        hashSetFilterSettings(true, enabled, !resetSelected && hashSetCheckbox.isSelected(), null);
        interestingItemsFilterSettings(true, enabled, !resetSelected && interestingItemsCheckbox.isSelected(), null);
        parentFilterSettings(true, enabled, !resetSelected && parentCheckbox.isSelected(), null);
        scoreFilterSettings(false, false, false, null);
        tagsFilterSettings(false, false, false, null);
        keywordFilterSettings(false, false, false, null);
        knownFilesFilterSettings(false, false, false);
        notableFilterSettings(false, false, false);
    }

    /**
     * Set the UI elements available to be the set of UI elements available when
     * a Video search is being performed.
     *
     * @param enabled       Boolean indicating if the filters present for videos
     *                      should be enabled.
     * @param resetSelected Boolean indicating if selection of the filters
     *                      present for videos should be reset to their default
     *                      status.
     */
    private void videosSelected(boolean enabled, boolean resetSelected) {
        dataSourceFilterSettings(true, enabled, !resetSelected && dataSourceCheckbox.isSelected(), null);
        sizeFilterSettings(true, enabled, !resetSelected && sizeCheckbox.isSelected(), null);
        int[] selectedFrequencyIndices;
        if (!EamDb.isEnabled()) {
            selectedFrequencyIndices = new int[]{0};
        } else {
            selectedFrequencyIndices = new int[]{1, 2, 3, 4, 5, 6, 7};
        }
        crFrequencyFilterSettings(true, enabled, resetSelected || crFrequencyCheckbox.isSelected(), resetSelected == true ? selectedFrequencyIndices : null);
        exifFilterSettings(true, enabled, !resetSelected && exifCheckbox.isSelected());
        objectsFilterSettings(true, enabled, !resetSelected && objectsCheckbox.isSelected(), null);
        hashSetFilterSettings(true, enabled, !resetSelected && hashSetCheckbox.isSelected(), null);
        interestingItemsFilterSettings(true, enabled, !resetSelected && interestingItemsCheckbox.isSelected(), null);
        parentFilterSettings(true, enabled, !resetSelected && parentCheckbox.isSelected(), null);
        scoreFilterSettings(false, false, false, null);
        tagsFilterSettings(false, false, false, null);
        keywordFilterSettings(false, false, false, null);
        knownFilesFilterSettings(false, false, false);
        notableFilterSettings(false, false, false);
    }

    /**
     * Set the type of search to perform.
     *
     * @param type The type of File to be found by the search.
     */
    void setSelectedType(FileType type) {
        fileType = type;
        setUpSizeFilter();
        if (fileType == FileType.IMAGE) {
            imagesSelected(true, true);
        } else if (fileType == FileType.VIDEO) {
            videosSelected(true, true);
        }
        validateFields();
    }

    FileType getSelectedType() {
        return fileType;
    }

    /**
     * Reset the panel to its initial configuration.
     */
    void resetPanel() {

        searchButton.setEnabled(false);

        // Set up the filters
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

        groupByCombobox.removeAllItems();
        // Set up the grouping attributes
        for (FileSearch.GroupingAttributeType type : FileSearch.GroupingAttributeType.getOptionsForGrouping()) {
            if ((type != GroupingAttributeType.FREQUENCY || EamDb.isEnabled())
                    && (type != GroupingAttributeType.OBJECT_DETECTED || objectsList.getModel().getSize() > 0)
                    && (type != GroupingAttributeType.INTERESTING_ITEM_SET || interestingItemsList.getModel().getSize() > 0)
                    && (type != GroupingAttributeType.HASH_LIST_NAME || hashSetList.getModel().getSize() > 0)) {
                groupByCombobox.addItem(type);
            }
        }

        orderByCombobox.removeAllItems();
        // Set up the file order list
        for (FileSorter.SortingMethod method : FileSorter.SortingMethod.getOptionsForOrdering()) {
            if (method != SortingMethod.BY_FREQUENCY || EamDb.isEnabled()) {
                orderByCombobox.addItem(method);
            }
        }

        groupSortingComboBox.setSelectedIndex(0);
        setSelectedType(FileType.IMAGE);
        validateFields();
    }

    /**
     * Add listeners to the checkbox/list set if listeners have not already been
     * added. Either can be null.
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
                    if (!evt.getValueIsAdjusting()) {
                        validateFields();
                    }
                }
            });
        }
    }

    /**
     * Initialize the data source filter
     */
    private void setUpDataSourceFilter() {
        int count = 0;
        try {
            DefaultListModel<DataSourceItem> dsListModel = (DefaultListModel<DataSourceItem>) dataSourceList.getModel();
            dsListModel.removeAllElements();
            for (DataSource ds : Case.getCurrentCase().getSleuthkitCase().getDataSources()) {
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
        int count = 0;
        DefaultListModel<FileSearchData.Frequency> frequencyListModel = (DefaultListModel<FileSearchData.Frequency>) crFrequencyList.getModel();
        frequencyListModel.removeAllElements();
        if (!EamDb.isEnabled()) {
            for (FileSearchData.Frequency freq : FileSearchData.Frequency.getOptionsForFilteringWithoutCr()) {
                frequencyListModel.add(count, freq);
            }
        } else {
            for (FileSearchData.Frequency freq : FileSearchData.Frequency.getOptionsForFilteringWithCr()) {
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
        sizeListModel.removeAllElements();
        if (null == fileType) {
            for (FileSearchData.FileSize size : FileSearchData.FileSize.values()) {
                sizeListModel.add(count, size);
            }
        } else {
            List<FileSearchData.FileSize> sizes;
            switch (fileType) {
                case VIDEO:
                    sizes = FileSearchData.FileSize.getOptionsForVideos();
                    break;
                case IMAGE:
                    sizes = FileSearchData.FileSize.getOptionsForImages();
                    break;
                default:
                    sizes = new ArrayList<>();
                    break;
            }
            for (FileSearchData.FileSize size : sizes) {
                sizeListModel.add(count, size);
            }
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
            kwListModel.removeAllElements();
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
            hashListModel.removeAllElements();
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
            intListModel.removeAllElements();
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
            tagsListModel.removeAllElements();
            List<TagName> tagNames = Case.getCurrentCase().getSleuthkitCase().getTagNamesInUse();
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
            objListModel.removeAllElements();
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
        scoreListModel.removeAllElements();
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
        List<BlackboardArtifact> arts = Case.getCurrentCase().getSleuthkitCase().getBlackboardArtifacts(artifactType);
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
        filters.add(new FileSearchFiltering.FileTypeFilter(fileType));
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

        if (knownFilesCheckbox.isSelected()) {
            filters.add(new FileSearchFiltering.KnownFilter());
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
        return groupByCombobox.getItemAt(groupByCombobox.getSelectedIndex()).getAttributeType();
    }

    /**
     * Get the sorting method for groups.
     *
     * @return the selected sorting method
     */
    FileGroup.GroupSortingAlgorithm getGroupSortingMethod() {
        return groupSortingComboBox.getItemAt(groupSortingComboBox.getSelectedIndex());

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
        // There will be a file type selected.
        if (fileType == null) {
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
     * The settings are valid so enable the SearchFilterSave button
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

        javax.swing.ButtonGroup parentPathButtonGroup = new javax.swing.ButtonGroup();
        javax.swing.ButtonGroup parentIncludeButtonGroup = new javax.swing.ButtonGroup();
        javax.swing.JScrollPane filtersScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel filtersPanel = new javax.swing.JPanel();
        sizeCheckbox = new javax.swing.JCheckBox();
        dataSourceCheckbox = new javax.swing.JCheckBox();
        crFrequencyCheckbox = new javax.swing.JCheckBox();
        keywordCheckbox = new javax.swing.JCheckBox();
        parentCheckbox = new javax.swing.JCheckBox();
        dataSourceScrollPane = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();
        substringRadioButton = new javax.swing.JRadioButton();
        addButton = new javax.swing.JButton();
        deleteButton = new javax.swing.JButton();
        sizeScrollPane = new javax.swing.JScrollPane();
        sizeList = new javax.swing.JList<>();
        crFrequencyScrollPane = new javax.swing.JScrollPane();
        crFrequencyList = new javax.swing.JList<>();
        keywordScrollPane = new javax.swing.JScrollPane();
        keywordList = new javax.swing.JList<>();
        javax.swing.JLabel parentLabel = new javax.swing.JLabel();
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
        excludeRadioButton = new javax.swing.JRadioButton();
        knownFilesCheckbox = new javax.swing.JCheckBox();
        javax.swing.JPanel fullRadioPanel = new javax.swing.JPanel();
        fullRadioButton = new javax.swing.JRadioButton();
        javax.swing.JPanel includeRadioPanel = new javax.swing.JPanel();
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 32767));
        includeRadioButton = new javax.swing.JRadioButton();
        javax.swing.JPanel parentTextPanel = new javax.swing.JPanel();
        parentTextField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        javax.swing.JPanel sortingPanel = new javax.swing.JPanel();
        groupByCombobox = new javax.swing.JComboBox<>();
        orderByCombobox = new javax.swing.JComboBox<>();
        javax.swing.JLabel orderGroupsByLabel = new javax.swing.JLabel();
        javax.swing.JLabel orderByLabel = new javax.swing.JLabel();
        javax.swing.JLabel groupByLabel = new javax.swing.JLabel();
        groupSortingComboBox = new javax.swing.JComboBox<>();
        errorLabel = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();
        javax.swing.JLabel stepTwoLabel = new javax.swing.JLabel();
        javax.swing.JLabel stepThreeLabel = new javax.swing.JLabel();

        setMinimumSize(new java.awt.Dimension(10, 0));
        setPreferredSize(new java.awt.Dimension(321, 400));

        filtersScrollPane.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.filtersScrollPane.border.title"))); // NOI18N
        filtersScrollPane.setPreferredSize(new java.awt.Dimension(309, 400));

        filtersPanel.setMinimumSize(new java.awt.Dimension(280, 500));
        filtersPanel.setPreferredSize(new java.awt.Dimension(280, 540));
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
        gridBagConstraints.gridy = 12;
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
        gridBagConstraints.gridy = 7;
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

        parentPathButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.substringRadioButton.text")); // NOI18N
        substringRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(substringRadioButton, gridBagConstraints);

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
        gridBagConstraints.gridy = 11;
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
        gridBagConstraints.gridy = 10;
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
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(6, 4, 4, 6);
        filtersPanel.add(sizeScrollPane, gridBagConstraints);

        crFrequencyList.setModel(new DefaultListModel<Frequency>());
        crFrequencyList.setEnabled(false);
        crFrequencyList.setVisibleRowCount(5);
        crFrequencyScrollPane.setViewportView(crFrequencyList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(crFrequencyScrollPane, gridBagConstraints);

        keywordList.setModel(new DefaultListModel<String>());
        keywordList.setEnabled(false);
        keywordList.setVisibleRowCount(3);
        keywordScrollPane.setViewportView(keywordList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 12;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(keywordScrollPane, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(parentLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.parentLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 8;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(parentLabel, gridBagConstraints);

        parentList.setModel(new DefaultListModel<ParentSearchTerm>());
        parentList.setEnabled(false);
        parentList.setMaximumSize(null);
        parentList.setMinimumSize(new java.awt.Dimension(0, 30));
        parentList.setPreferredSize(new java.awt.Dimension(0, 30));
        parentList.setVisibleRowCount(4);
        parentList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                parentListValueChanged(evt);
            }
        });
        parentScrollPane.setViewportView(parentList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 7;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.gridheight = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.05;
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
        hashSetList.setMinimumSize(new java.awt.Dimension(0, 30));
        hashSetList.setPreferredSize(new java.awt.Dimension(0, 30));
        hashSetList.setVisibleRowCount(3);
        hashSetScrollPane.setViewportView(hashSetList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.05;
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
        gridBagConstraints.gridy = 6;
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
        gridBagConstraints.gridy = 13;
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
        gridBagConstraints.gridy = 5;
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
        gridBagConstraints.gridy = 14;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 0);
        filtersPanel.add(scoreCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(exifCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.exifCheckbox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 6);
        filtersPanel.add(exifCheckbox, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(notableCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.notableCheckbox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 15;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 6);
        filtersPanel.add(notableCheckbox, gridBagConstraints);

        objectsList.setModel(new DefaultListModel<String>());
        objectsList.setEnabled(false);
        objectsList.setMinimumSize(new java.awt.Dimension(0, 30));
        objectsList.setPreferredSize(new java.awt.Dimension(0, 30));
        objectsList.setVisibleRowCount(2);
        objectsScrollPane.setViewportView(objectsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 6;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.05;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(objectsScrollPane, gridBagConstraints);

        tagsList.setModel(new DefaultListModel<TagName>());
        tagsList.setEnabled(false);
        tagsList.setVisibleRowCount(3);
        tagsScrollPane.setViewportView(tagsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 13;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(tagsScrollPane, gridBagConstraints);

        interestingItemsList.setModel(new DefaultListModel<String>());
        interestingItemsList.setEnabled(false);
        interestingItemsList.setMinimumSize(new java.awt.Dimension(0, 30));
        interestingItemsList.setPreferredSize(new java.awt.Dimension(0, 30));
        interestingItemsList.setVisibleRowCount(2);
        interestingItemsScrollPane.setViewportView(interestingItemsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 5;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.weighty = 0.05;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(interestingItemsScrollPane, gridBagConstraints);

        scoreList.setModel(new DefaultListModel<Score>());
        scoreList.setEnabled(false);
        scoreList.setVisibleRowCount(3);
        scoreScrollPane.setViewportView(scoreList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 14;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 6);
        filtersPanel.add(scoreScrollPane, gridBagConstraints);

        parentIncludeButtonGroup.add(excludeRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(excludeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.excludeRadioButton.text")); // NOI18N
        excludeRadioButton.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.weightx = 0.1;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(excludeRadioButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(knownFilesCheckbox, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.knownFilesCheckbox.text")); // NOI18N
        knownFilesCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.knownFilesCheckbox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 16;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 4, 6);
        filtersPanel.add(knownFilesCheckbox, gridBagConstraints);

        parentPathButtonGroup.add(fullRadioButton);
        fullRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(fullRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.fullRadioButton.text")); // NOI18N
        fullRadioButton.setEnabled(false);

        javax.swing.GroupLayout fullRadioPanelLayout = new javax.swing.GroupLayout(fullRadioPanel);
        fullRadioPanel.setLayout(fullRadioPanelLayout);
        fullRadioPanelLayout.setHorizontalGroup(
            fullRadioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, fullRadioPanelLayout.createSequentialGroup()
                .addContainerGap(58, Short.MAX_VALUE)
                .addComponent(fullRadioButton)
                .addGap(20, 20, 20))
        );
        fullRadioPanelLayout.setVerticalGroup(
            fullRadioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(fullRadioPanelLayout.createSequentialGroup()
                .addComponent(fullRadioButton)
                .addGap(0, 4, Short.MAX_VALUE))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 9;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        filtersPanel.add(fullRadioPanel, gridBagConstraints);

        parentIncludeButtonGroup.add(includeRadioButton);
        includeRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(includeRadioButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.includeRadioButton.text")); // NOI18N
        includeRadioButton.setEnabled(false);

        javax.swing.GroupLayout includeRadioPanelLayout = new javax.swing.GroupLayout(includeRadioPanel);
        includeRadioPanel.setLayout(includeRadioPanelLayout);
        includeRadioPanelLayout.setHorizontalGroup(
            includeRadioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(includeRadioPanelLayout.createSequentialGroup()
                .addComponent(filler2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 58, Short.MAX_VALUE)
                .addComponent(includeRadioButton))
        );
        includeRadioPanelLayout.setVerticalGroup(
            includeRadioPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(filler2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addComponent(includeRadioButton)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 10;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        filtersPanel.add(includeRadioPanel, gridBagConstraints);

        parentTextField.setEnabled(false);

        javax.swing.GroupLayout parentTextPanelLayout = new javax.swing.GroupLayout(parentTextPanel);
        parentTextPanel.setLayout(parentTextPanelLayout);
        parentTextPanelLayout.setHorizontalGroup(
            parentTextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, parentTextPanelLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(parentTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 71, Short.MAX_VALUE))
        );
        parentTextPanelLayout.setVerticalGroup(
            parentTextPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(parentTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 11;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.5;
        gridBagConstraints.insets = new java.awt.Insets(0, 4, 4, 0);
        filtersPanel.add(parentTextPanel, gridBagConstraints);

        filtersScrollPane.setViewportView(filtersPanel);

        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        sortingPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.sortingPanel.border.title"))); // NOI18N
        sortingPanel.setPreferredSize(new java.awt.Dimension(345, 112));

        org.openide.awt.Mnemonics.setLocalizedText(orderGroupsByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.orderGroupsByLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(orderByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.orderByLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(groupByLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.groupByLabel.text")); // NOI18N

        javax.swing.GroupLayout sortingPanelLayout = new javax.swing.GroupLayout(sortingPanel);
        sortingPanel.setLayout(sortingPanelLayout);
        sortingPanelLayout.setHorizontalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(orderGroupsByLabel)
                    .addGroup(sortingPanelLayout.createSequentialGroup()
                        .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(groupByLabel)
                            .addComponent(orderByLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(groupSortingComboBox, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(orderByCombobox, javax.swing.GroupLayout.Alignment.LEADING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(groupByCombobox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        sortingPanelLayout.setVerticalGroup(
            sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sortingPanelLayout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(groupByLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(groupSortingComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orderGroupsByLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(sortingPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(orderByCombobox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(orderByLabel))
                .addContainerGap())
        );

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));

        org.openide.awt.Mnemonics.setLocalizedText(cancelButton, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.cancelButton.text")); // NOI18N
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(stepTwoLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.stepTwoLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(stepThreeLabel, org.openide.util.NbBundle.getMessage(FileSearchPanel.class, "FileSearchPanel.stepThreeLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(stepTwoLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(errorLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(cancelButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(searchButton))
                            .addComponent(stepThreeLabel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(filtersScrollPane, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(sortingPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 309, Short.MAX_VALUE))
                        .addGap(6, 6, 6))))
        );

        layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {cancelButton, searchButton});

        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(stepTwoLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(filtersScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 201, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(stepThreeLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sortingPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(errorLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(cancelButton)
                        .addComponent(searchButton)))
                .addGap(6, 6, 6))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        // Get the selected filters
        List<FileSearchFiltering.FileFilter> filters = getFilters();
        enableSearch(false);
        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.SearchStartedEvent(fileType));

        // Get the grouping attribute and group sorting method
        FileSearch.AttributeType groupingAttr = getGroupingAttribute();
        FileGroup.GroupSortingAlgorithm groupSortAlgorithm = getGroupSortingMethod();

        // Get the file sorting method
        FileSorter.SortingMethod fileSort = getFileSortingMethod();
        EamDb centralRepoDb = null;
        if (EamDb.isEnabled()) {
            try {
                centralRepoDb = EamDb.getInstance();
            } catch (EamDbException ex) {
                centralRepoDb = null;
                logger.log(Level.SEVERE, "Error loading central repository database, no central repository options will be available for File Discovery", ex);
            }
        }
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
        if (fileType == FileType.IMAGE) {
            imagesSelected(enabled, false);
        } else if (fileType == FileType.VIDEO) {
            videosSelected(enabled, false);
        }
        searchButton.setEnabled(enabled);
        cancelButton.setEnabled(!enabled);
        orderByCombobox.setEnabled(enabled);
        groupByCombobox.setEnabled(enabled);
        groupSortingComboBox.setEnabled(enabled);
    }

    SearchFilterSave getCurrentFilters() {

        SearchFilterSave search = new SearchFilterSave(fileType.getRanking(), orderByCombobox.getSelectedIndex(), groupByCombobox.getSelectedIndex(), groupSortingComboBox.getSelectedIndex());
        if (sizeCheckbox.isSelected()) {
            search.setSizeFilter(true, sizeList.getSelectedIndices());
        }
//        if (dataSourceCheckbox.isSelected()) {  Different for every case doesn't really make sense to save
//            dataSourceList.getSelectedIndices();
//        }
        if (crFrequencyCheckbox.isSelected()) {
            search.setCrFrequencyFilter(true, crFrequencyList.getSelectedIndices());
        }
        if (keywordCheckbox.isSelected()) {
            search.setKeywordFilter(true, keywordList.getSelectedIndices());
        }
        if (hashSetCheckbox.isSelected()) {
            search.setHashSetFilter(true, hashSetList.getSelectedIndices());
        }
        if (objectsCheckbox.isSelected()) {
            search.setObjectsFilter(true, objectsList.getSelectedIndices());
        }
        if (tagsCheckbox.isSelected()) {
            search.setTagsFilter(true, tagsList.getSelectedIndices());
        }
        if (interestingItemsCheckbox.isSelected()) {
            search.setInterestingItemsFilter(true, interestingItemsList.getSelectedIndices());
        }
        if (scoreCheckbox.isSelected()) {
            search.setScoreFilter(true, scoreList.getSelectedIndices());
        }
        search.setDeviceOriginalFilterEnabled(exifCheckbox.isSelected());
        search.setNotableFilesFilterEnabled(notableCheckbox.isSelected());
        search.setKnownFilesFilterEnabled(knownFilesCheckbox.isSelected());
        if (parentCheckbox.isSelected()) {
            List<ParentSearchTerm> parentTerms = new ArrayList<>();
            for (int i = 0; i < parentList.getModel().getSize(); i++) {
                parentTerms.add(parentList.getModel().getElementAt(i));
            }
            search.setParentFilter(true, parentTerms);
        }
        return search;
    }

    void loadSearch(SearchFilterSave search) throws IllegalArgumentException {
        fileType = FileType.fromRanking(search.getSelectedFileType());
        orderByCombobox.setSelectedIndex(search.getOrderByIndex());
        groupByCombobox.setSelectedIndex(search.getGroupByIndex());
        groupSortingComboBox.setSelectedIndex(search.getOrderGroupsBy());
        
        sizeCheckbox.setSelected(search.isSizeFilterEnabled());
        if (sizeCheckbox.isSelected()) {
            sizeList.setSelectedIndices(search.getSizeFilters());
        }

        //        if (dataSourceCheckbox.isSelected()) {  Different for every case doesn't really make sense to save
//            dataSourceList.getSelectedIndices();
//        }
        crFrequencyCheckbox.setSelected(search.isCrFrequencyFilterEnabled());
        if (crFrequencyCheckbox.isSelected()) {
            crFrequencyList.setSelectedIndices(search.getCrFrequencyFilters());
        }
        keywordCheckbox.setSelected(search.isKeywordFilterEnabled());
        if (keywordCheckbox.isSelected()) {
            keywordList.setSelectedIndices(search.getKeywordFilters());
        }
        hashSetCheckbox.setSelected(search.isHashSetFilterEnabled());
        if (hashSetCheckbox.isSelected()) {
            hashSetList.setSelectedIndices(search.getHashSetFilters());
        }
        objectsCheckbox.setSelected(search.isObjectsFilterEnabled());
        if (objectsCheckbox.isSelected()) {
            objectsList.setSelectedIndices(search.getObjectsFilters());
        }
        tagsCheckbox.setSelected(search.isTagsFilterEnabled());
        if (tagsCheckbox.isSelected()) {
            tagsList.setSelectedIndices(search.getTagsFilters());
        }
        interestingItemsCheckbox.setSelected(search.isInterestingItemsFilterEnabled());
        if (interestingItemsCheckbox.isSelected()) {
            interestingItemsList.setSelectedIndices(search.getInterestingItemsFilters());
        }
        scoreCheckbox.setSelected(search.isScoreFilterEnabled());
        if (scoreCheckbox.isSelected()) {
            scoreList.setSelectedIndices(search.getScoreFilters());
        }
        exifCheckbox.setSelected(search.isDeviceOriginalFilterEnabled());
        notableCheckbox.setSelected(search.isNotableFilesFilterEnabled());
        knownFilesCheckbox.setSelected(search.isKnownFilesFilterEnabled());
        parentCheckbox.setSelected(search.isParentFilterEnabled());
        if (parentCheckbox.isSelected()) {
            parentListModel.clear();
            for (ParentSearchTerm term : search.getParentFilters()){
                parentListModel.addElement(term);
            }
        }
        validateFields();
    }

    /**
     * Update the user interface when a search has been cancelled.
     *
     * @param searchCancelledEvent The SearchCancelledEvent which was received.
     */
    @Subscribe
    void handleSearchCancelledEvent(DiscoveryEventUtils.SearchCancelledEvent searchCancelledEvent) {
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
    void handleSearchCompleteEvent(DiscoveryEventUtils.SearchCompleteEvent searchCompleteEvent) {
        SwingUtilities.invokeLater(() -> {
            enableSearch(true);
        });
    }

    private void parentCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_parentCheckboxActionPerformed
        parentFilterSettings(true, true, parentCheckbox.isSelected(), null);
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
    private javax.swing.JRadioButton fullRadioButton;
    private javax.swing.JComboBox<GroupingAttributeType> groupByCombobox;
    private javax.swing.JComboBox<GroupSortingAlgorithm> groupSortingComboBox;
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
    private javax.swing.JCheckBox knownFilesCheckbox;
    private javax.swing.JCheckBox notableCheckbox;
    private javax.swing.JCheckBox objectsCheckbox;
    private javax.swing.JList<String> objectsList;
    private javax.swing.JScrollPane objectsScrollPane;
    private javax.swing.JComboBox<SortingMethod> orderByCombobox;
    private javax.swing.JCheckBox parentCheckbox;
    private javax.swing.JList<ParentSearchTerm> parentList;
    private javax.swing.JScrollPane parentScrollPane;
    private javax.swing.JTextField parentTextField;
    private javax.swing.JCheckBox scoreCheckbox;
    private javax.swing.JList<Score> scoreList;
    private javax.swing.JScrollPane scoreScrollPane;
    private javax.swing.JButton searchButton;
    private javax.swing.JCheckBox sizeCheckbox;
    private javax.swing.JList<FileSize> sizeList;
    private javax.swing.JScrollPane sizeScrollPane;
    private javax.swing.JRadioButton substringRadioButton;
    private javax.swing.JCheckBox tagsCheckbox;
    private javax.swing.JList<TagName> tagsList;
    private javax.swing.JScrollPane tagsScrollPane;
    // End of variables declaration//GEN-END:variables

}
