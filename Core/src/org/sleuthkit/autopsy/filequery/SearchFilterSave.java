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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.filequery.FileSearchData.Frequency;
import org.sleuthkit.autopsy.filequery.FileSearchData.Score;
import org.sleuthkit.datamodel.TagName;

/**
 * Class for storing the saved state of the file discovery filters.
 */
public class SearchFilterSave {

    private final int fileTypeIndex;
    private final FileSorter.SortingMethod orderBy;
    private final FileSearch.GroupingAttributeType groupBy;
    private final FileGroup.GroupSortingAlgorithm orderGroupsBy;
    private boolean sizeFilterEnabled = false;
    private int[] sizeFilterIndices;
    private boolean dataSourceFilterEnabled = false;
    private final List<String> dataSourceFilters = new ArrayList<>();
    private boolean crFrequencyFilterEnabled = false;
    private final List<Frequency> crFrequencyFilters = new ArrayList<>();
    private boolean keywordFilterEnabled = false;
    private final List<String> keywordFilters = new ArrayList<>();
    private boolean hashSetFilterEnabled = false;
    private final List<String> hashSetFilters = new ArrayList<>();
    private boolean objectsFilterEnabled = false;
    private final List<String> objectsFilters = new ArrayList<>();
    private boolean tagsFilterEnabled = false;
    private final List<TagName> tagsFilters = new ArrayList<>();
    private boolean interestingItemsFilterEnabled = false;
    private final List<String> interestingItemsFilters = new ArrayList<>();
    private boolean scoreFilterEnabled = false;
    private final List<Score> scoreFilters = new ArrayList<>();
    private boolean userContentFilterEnabled = false;
    private boolean notableFilesFilterEnabled = false;
    private boolean knownFilesFilterEnabled = false;
    private boolean parentFilterEnabled = false;
    private final List<FileSearchFiltering.ParentSearchTerm> parentFilters = new ArrayList<>();

    /**
     * Create a new SearchFilterSave.
     *
     * @param fileTypeIndex - Index which corresponds to the selected file type.
     * @param orderBy       - The SortingMethod which is selected.
     * @param groupBy       - The GroupingAttributeType selected.
     * @param orderGroupsBy - The GroupSortingAlgoritm selected.
     */
    SearchFilterSave(int fileTypeIndex, FileSorter.SortingMethod orderBy, FileSearch.GroupingAttributeType groupBy, FileGroup.GroupSortingAlgorithm orderGroupsBy) {
        this.fileTypeIndex = fileTypeIndex;
        this.orderBy = orderBy;
        this.groupBy = groupBy;
        this.orderGroupsBy = orderGroupsBy;
    }

    /**
     * Get the index of the selected file type.
     *
     * @return The index of the selected file type.
     */
    int getSelectedFileType() {
        return fileTypeIndex;
    }

    /**
     * Get the sorting method used to order the results.
     *
     * @return The SortingMethod which will be applied to the results.
     */
    FileSorter.SortingMethod getOrderBy() {
        return orderBy;
    }

    /**
     * Get the grouping attribute used to group the results.
     *
     * @return The GroupingAttributeType which will be used to group the
     *         results.
     */
    FileSearch.GroupingAttributeType getGroupByIndex() {
        return groupBy;
    }

    /**
     * Get the group sorting algorithm used to sort the groups.
     *
     * @return The GroupSortingAlgorithm which will be used to sort the groups.
     */
    FileGroup.GroupSortingAlgorithm getOrderGroupsBy() {
        return orderGroupsBy;
    }

    /**
     * Set the size filter saved information.
     *
     * @param enabled - True if the Size filter is enabled.
     * @param indices - The indices of the size filters to select.
     */
    void setSizeFilter(boolean enabled, int[] indices) {
        sizeFilterEnabled = enabled;
        if (indices == null) {
            sizeFilterIndices = null;
        } else {
            sizeFilterIndices = indices.clone();
        }
    }

    /**
     * Identifies if the size filter is enabled.
     *
     * @return True if the size filter is enabled.
     */
    boolean isSizeFilterEnabled() {
        return sizeFilterEnabled;
    }

    /**
     * Get the indices of the size filters selected in the list of size filters.
     *
     * @return The indices of the filters selected in the list.
     */
    int[] getSizeFilters() {
        if (sizeFilterIndices == null) {
            return null;
        }
        return sizeFilterIndices.clone();
    }

    /**
     * Set the data source filter saved information.
     *
     * @param enabled - True if the data source filter is enabled.
     * @param filters - The data source filters to select.
     */
    void setDataSourceFilter(boolean enabled, List<DataSourceItem> filters) {
        dataSourceFilterEnabled = enabled;
        dataSourceFilters.clear();
        if (filters != null) {
            for (DataSourceItem dataSource : filters) {
                dataSourceFilters.add(dataSource.toString());
            }
        }
    }

    /**
     * Identifies if the data source filter is enabled.
     *
     * @return True if the data source filter is enabled.
     */
    boolean isDataSourceFilterEnabled() {
        return dataSourceFilterEnabled;
    }

    /**
     * Get the list of data source filters which should be selected.
     *
     * @return The list of filters selected in the data sources list.
     */
    List<String> getDataSourceFilters() {
        if (dataSourceFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(dataSourceFilters);
    }

    /**
     * Set the CR frequency filter saved information.
     *
     * @param enabled - True if the CR frequency filter is enabled.
     * @param filters - The CR frequency filters to select.
     */
    void setCrFrequencyFilter(boolean enabled, List<Frequency> filters) {
        crFrequencyFilterEnabled = enabled;
        crFrequencyFilters.clear();
        if (filters != null) {
            crFrequencyFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the CR frequency filter is enabled.
     *
     * @return True if the CR frequency filter is enabled.
     */
    boolean isCrFrequencyFilterEnabled() {
        return crFrequencyFilterEnabled;
    }

    /**
     * Get the list of CR frequency filters which should be selected.
     *
     * @return The list of filters selected in the CR frequency list.
     */
    List<Frequency> getCrFrequencyFilters() {
        if (crFrequencyFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(crFrequencyFilters);
    }

    /**
     * Set the keyword filter saved information.
     *
     * @param enabled - True if the keyword filter is enabled.
     * @param filters - The keyword filters to select.
     */
    void setKeywordFilter(boolean enabled, List<String> filters) {
        keywordFilterEnabled = enabled;
        keywordFilters.clear();
        if (filters != null) {
            keywordFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the keyword filter is enabled.
     *
     * @return True if the keyword filter is enabled.
     */
    boolean isKeywordFilterEnabled() {
        return keywordFilterEnabled;
    }

    /**
     * Get the list of keyword filters which should be selected.
     *
     * @return The list of filters selected in the keyword list.
     */
    List<String> getKeywordFilters() {
        if (keywordFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(keywordFilters);
    }

    /**
     * Set the hash set filter saved information.
     *
     * @param enabled - True if the hash set filter is enabled.
     * @param filters - The hash set filters to select.
     */
    void setHashSetFilter(boolean enabled, List<String> filters) {
        hashSetFilterEnabled = enabled;
        hashSetFilters.clear();
        if (filters != null) {
            hashSetFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the hash set filter is enabled.
     *
     * @return True if the hash set filter is enabled.
     */
    boolean isHashSetFilterEnabled() {
        return hashSetFilterEnabled;
    }

    /**
     * Get the list of hash set filters which should be selected.
     *
     * @return The list of filters selected in the hash sets list.
     */
    List<String> getHashSetFilters() {
        if (hashSetFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(hashSetFilters);
    }

    /**
     * Set the objects filter saved information.
     *
     * @param enabled - True if the objects filter is enabled.
     * @param filters - The objects filters to select.
     */
    void setObjectsFilter(boolean enabled, List<String> filters) {
        objectsFilterEnabled = enabled;
        objectsFilters.clear();
        if (filters != null) {
            objectsFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the objects filter is enabled.
     *
     * @return True if the objects filter is enabled.
     */
    boolean isObjectsFilterEnabled() {
        return objectsFilterEnabled;
    }

    /**
     * Get the list of objects filters which should be selected.
     *
     * @return The list of filters selected in the objects list.
     */
    List<String> getObjectsFilters() {
        if (objectsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(objectsFilters);
    }

    /**
     * Set the tags filter saved information.
     *
     * @param enabled - True if the tags filter is enabled.
     * @param filters - The tags filters to select.
     */
    void setTagsFilter(boolean enabled, List<TagName> filters) {
        tagsFilterEnabled = enabled;
        tagsFilters.clear();
        if (filters != null) {
            tagsFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the tags filter is enabled.
     *
     * @return True if the tags filter is enabled.
     */
    boolean isTagsFilterEnabled() {
        return tagsFilterEnabled;
    }

    /**
     * Get the list of tags filters which should be selected.
     *
     * @return The list of filters selected in the tags list.
     */
    List<TagName> getTagsFilters() {
        if (tagsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(tagsFilters);
    }

    /**
     * Set the interesting items filter saved information.
     *
     * @param enabled - True if the interesting items filter is enabled.
     * @param filters - The interesting items filters to select.
     */
    void setInterestingItemsFilter(boolean enabled, List<String> filters) {
        interestingItemsFilterEnabled = enabled;
        interestingItemsFilters.clear();
        if (filters != null) {
            interestingItemsFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the interesting items filter is enabled.
     *
     * @return True if the interesting items filter is enabled.
     */
    boolean isInterestingItemsFilterEnabled() {
        return interestingItemsFilterEnabled;
    }

    /**
     * Get the list of interesting items filters which should be selected.
     *
     * @return The list of filters selected in the interesting items list.
     */
    List<String> getInterestingItemsFilters() {
        if (interestingItemsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(interestingItemsFilters);
    }

    /**
     * Set the score filter saved information.
     *
     * @param enabled - True if the score filter is enabled.
     * @param filters - The score filters to select.
     */
    void setScoreFilter(boolean enabled, List<Score> filters) {
        scoreFilterEnabled = enabled;
        scoreFilters.clear();
        if (filters != null) {
            scoreFilters.addAll(filters);
        }
    }

    /**
     * Identifies if the score filter is enabled.
     *
     * @return True if the score filter is enabled.
     */
    boolean isScoreFilterEnabled() {
        return scoreFilterEnabled;
    }

    /**
     * Get the list of score filters which should be selected.
     *
     * @return The list of filters selected in the score list.
     */
    List<Score> getScoreFilters() {
        if (scoreFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(scoreFilters);
    }

    /**
     * Identifies if the user content filter is enabled.
     *
     * @return True if the user content filter is enabled.
     */
    boolean isUserContentFilterEnabled() {
        return userContentFilterEnabled;
    }

    /**
     * Set the user content filter saved information.
     *
     * @param enabled True if the userContentFilterEnabled is enabled.
     */
    void setUserContentFilterEnabled(boolean enabled) {
        this.userContentFilterEnabled = enabled;
    }

    /**
     * Identifies if the notable files filter is enabled.
     *
     * @return True if the notable files filter is enabled.
     */
    boolean isNotableFilesFilterEnabled() {
        return notableFilesFilterEnabled;
    }

    /**
     * Set the notable files filter saved information.
     *
     * @param enabled True if the notableFilesFilterEnabled is enabled.
     */
    void setNotableFilesFilterEnabled(boolean enabled) {
        this.notableFilesFilterEnabled = enabled;
    }

    /**
     * Identifies if the known files filter is enabled.
     *
     * @return True if the known files filter is enabled.
     */
    boolean isKnownFilesFilterEnabled() {
        return knownFilesFilterEnabled;
    }

    /**
     * Set the known files filter saved information.
     *
     * @param enabled True if the knownFilesFilterEnabled is enabled.
     */
    void setKnownFilesFilterEnabled(boolean enabled) {
        this.knownFilesFilterEnabled = enabled;
    }

    /**
     * Set the parent filter saved information.
     *
     * @param enabled - True if the parent filter is enabled.
     * @param filters - The parent filters to select.
     */
    void setParentFilter(boolean enabled, List<FileSearchFiltering.ParentSearchTerm> parentTerms) {
        parentFilterEnabled = enabled;
        parentFilters.clear();
        if (parentTerms != null) {
            parentFilters.addAll(parentTerms);
        }
    }

    /**
     * Identifies if the parent filter is enabled.
     *
     * @return True if the parent filter is enabled.
     */
    boolean isParentFilterEnabled() {
        return parentFilterEnabled;
    }

    /**
     * Get the list of parent filters which should be selected.
     *
     * @return The list of filters selected in the parent list.
     */
    List<FileSearchFiltering.ParentSearchTerm> getParentFilters() {
        return Collections.unmodifiableList(parentFilters);
    }
}
