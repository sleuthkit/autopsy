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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sleuthkit.autopsy.filequery.FileSearchData.Frequency;
import org.sleuthkit.autopsy.filequery.FileSearchData.Score;
import org.sleuthkit.datamodel.TagName;

public class SearchFilterSave {

    private final int fileTypeIndex;
    private final int orderByIndex;
    private final int groupByIndex;
    private final int orderGroupsBy;
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
    private boolean deviceOriginalFilterEnabled = false;
    private boolean notableFilesFilterEnabled = false;
    private boolean knownFilesFilterEnabled = false;
    private boolean parentFilterEnabled = false;
    private final List<FileSearchFiltering.ParentSearchTerm> parentFilters = new ArrayList<>();

    SearchFilterSave(int fileTypeIndex, int orderByIndex, int groupByIndex, int orderGroupsBy) {
        this.fileTypeIndex = fileTypeIndex;
        this.orderByIndex = orderByIndex;
        this.groupByIndex = groupByIndex;
        this.orderGroupsBy = orderGroupsBy;
    }

    SearchFilterSave(File searchSaveFile) {
        this.fileTypeIndex = 0;
        this.orderByIndex = 0;
        this.groupByIndex = 0;
        this.orderGroupsBy = 0;
    }

    int getSelectedFileType() {
        return fileTypeIndex;
    }

    int getOrderByIndex() {
        return orderByIndex;
    }

    int getGroupByIndex() {
        return groupByIndex;
    }

    int getOrderGroupsBy() {
        return orderGroupsBy;
    }

    void setSizeFilter(boolean enabled, int[] indices) {
        sizeFilterEnabled = enabled;
        if (indices == null) {
            sizeFilterIndices = null;
        } else {
            sizeFilterIndices = indices.clone();
        }
    }

    boolean isSizeFilterEnabled() {
        return sizeFilterEnabled;
    }

    int[] getSizeFilters() {
        if (sizeFilterIndices == null) {
            return null;
        }
        return sizeFilterIndices.clone();
    }

    void setDataSourceFilter(boolean enabled, List<DataSourceItem> filters) {
        dataSourceFilterEnabled = enabled;
        dataSourceFilters.clear();
        if (filters != null) {
            for (DataSourceItem dataSource : filters) {
                dataSourceFilters.add(dataSource.toString());
            }
        }
    }

    boolean isDataSourceFilterEnabled() {
        return dataSourceFilterEnabled;
    }

    List<String> getDataSourceFilters() {
        if (dataSourceFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(dataSourceFilters);
    }

    void setCrFrequencyFilter(boolean enabled, List<Frequency> filters) {
        crFrequencyFilterEnabled = enabled;
        crFrequencyFilters.clear();
        if (filters != null) {
            crFrequencyFilters.addAll(filters);
        }
    }

    boolean isCrFrequencyFilterEnabled() {
        return crFrequencyFilterEnabled;
    }

    List<Frequency> getCrFrequencyFilters() {
        if (crFrequencyFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(crFrequencyFilters);
    }

    void setKeywordFilter(boolean enabled, List<String> filters) {
        keywordFilterEnabled = enabled;
        keywordFilters.clear();
        if (filters != null) {
            keywordFilters.addAll(filters);
        }
    }

    boolean isKeywordFilterEnabled() {
        return keywordFilterEnabled;
    }

    List<String> getKeywordFilters() {
        if (keywordFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(keywordFilters);
    }

    void setHashSetFilter(boolean enabled, List<String> filters) {
        hashSetFilterEnabled = enabled;
        hashSetFilters.clear();
        if (filters != null) {
            hashSetFilters.addAll(filters);
        }
    }

    boolean isHashSetFilterEnabled() {
        return hashSetFilterEnabled;
    }

    List<String> getHashSetFilters() {
        if (hashSetFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(hashSetFilters);
    }

    void setObjectsFilter(boolean enabled, List<String> filters) {
        objectsFilterEnabled = enabled;
        objectsFilters.clear();
        if (filters != null) {
            objectsFilters.addAll(filters);
        }
    }

    boolean isObjectsFilterEnabled() {
        return objectsFilterEnabled;
    }

    List<String> getObjectsFilters() {
        if (objectsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(objectsFilters);
    }

    void setTagsFilter(boolean enabled, List<TagName> filters) {
        tagsFilterEnabled = enabled;
        tagsFilters.clear();
        if (filters != null) {
            tagsFilters.addAll(filters);
        }
    }

    boolean isTagsFilterEnabled() {
        return tagsFilterEnabled;
    }

    List<TagName> getTagsFilters() {
        if (tagsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(tagsFilters);
    }

    void setInterestingItemsFilter(boolean enabled, List<String> filters) {
        interestingItemsFilterEnabled = enabled;
        interestingItemsFilters.clear();
        if (filters != null) {
            interestingItemsFilters.addAll(filters);
        }
    }

    boolean isInterestingItemsFilterEnabled() {
        return interestingItemsFilterEnabled;
    }

    List<String> getInterestingItemsFilters() {
        if (interestingItemsFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(interestingItemsFilters);
    }

    void setScoreFilter(boolean enabled, List<Score> filters) {
        scoreFilterEnabled = enabled;
        scoreFilters.clear();
        if (filters != null) {
            scoreFilters.addAll(filters);
        }
    }

    /**
     * @return the scoreFilterEnabled
     */
    boolean isScoreFilterEnabled() {
        return scoreFilterEnabled;
    }

    /**
     * @return the scoreFilters
     */
    List<Score> getScoreFilters() {
        if (scoreFilters == null) {
            return null;
        }
        return Collections.unmodifiableList(scoreFilters);
    }

    /**
     * @return the deviceOriginalFilterEnabled
     */
    boolean isDeviceOriginalFilterEnabled() {
        return deviceOriginalFilterEnabled;
    }

    /**
     * @param deviceOriginalFilterEnabled the deviceOriginalFilterEnabled to set
     */
    void setDeviceOriginalFilterEnabled(boolean enabled) {
        this.deviceOriginalFilterEnabled = enabled;
    }

    /**
     * @return the notableFilesFilterEnabled
     */
    boolean isNotableFilesFilterEnabled() {
        return notableFilesFilterEnabled;
    }

    /**
     * @param notableFilesFilterEnabled the notableFilesFilterEnabled to set
     */
    void setNotableFilesFilterEnabled(boolean enabled) {
        this.notableFilesFilterEnabled = enabled;
    }

    /**
     * @return the knownFilesFilterEnabled
     */
    boolean isKnownFilesFilterEnabled() {
        return knownFilesFilterEnabled;
    }

    /**
     * @param knownFilesFilterEnabled the knownFilesFilterEnabled to set
     */
    void setKnownFilesFilterEnabled(boolean enabled) {
        this.knownFilesFilterEnabled = enabled;
    }

    void setParentFilter(boolean enabled, List<FileSearchFiltering.ParentSearchTerm> parentTerms) {
        parentFilterEnabled = enabled;
        parentFilters.clear();
        if (parentTerms != null) {
            parentFilters.addAll(parentTerms);
        }
    }

    /**
     * @return the parentFilterEnabled
     */
    boolean isParentFilterEnabled() {
        return parentFilterEnabled;
    }

    /**
     * @return the parentFilters
     */
    List<FileSearchFiltering.ParentSearchTerm> getParentFilters() {
        return Collections.unmodifiableList(parentFilters);
    }
}
