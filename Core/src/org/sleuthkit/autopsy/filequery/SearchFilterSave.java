/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filequery;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchFilterSave {

    private final int fileTypeIndex;
    private final int orderByIndex;
    private final int groupByIndex;
    private final int orderGroupsBy;
    private boolean sizeFilterEnabled = false;
    private int[] sizeFilterIndices;
    private boolean dataSourceFilterEnabled = false;
    private int[] dataSourceFilterIndices;
    private boolean crFrequencyFilterEnabled = false;
    private int[] crFrequencyFilterIndices;
    private boolean keywordFilterEnabled = false;
    private int[] keywordFilterIndices;
    private boolean hashSetFilterEnabled = false;
    private int[] hashSetFilterIndices;
    private boolean objectsFilterEnabled = false;
    private int[] objectsFilterIndices;
    private boolean tagsFilterEnabled = false;
    private int[] tagsFilterIndices;
    private boolean interestingItemsFilterEnabled = false;
    private int[] interestingItemsFilterIndices;
    private boolean scoreFilterEnabled = false;
    private int[] scoreFilterIndices;
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
        sizeFilterIndices = indices.clone();
    }

    boolean isSizeFilterEnabled() {
        return sizeFilterEnabled;
    }

    int[] getSizeFilters() {
        return sizeFilterIndices.clone();
    }

    void setDataSourceFilter(boolean enabled, int[] indices) {
        dataSourceFilterEnabled = enabled;
        dataSourceFilterIndices = indices.clone();
    }

    boolean isDataSourceFilterEnabled() {
        return dataSourceFilterEnabled;
    }

    int[] getDataSourceFilters() {
        return dataSourceFilterIndices.clone();
    }

    void setCrFrequencyFilter(boolean enabled, int[] indices) {
        crFrequencyFilterEnabled = enabled;
        crFrequencyFilterIndices = indices.clone();
    }

    boolean isCrFrequencyFilterEnabled() {
        return crFrequencyFilterEnabled;
    }

    int[] getCrFrequencyFilters() {
        return crFrequencyFilterIndices.clone();
    }

    void setKeywordFilter(boolean enabled, int[] indices) {
        keywordFilterEnabled = enabled;
        keywordFilterIndices = indices.clone();
    }

    boolean isKeywordFilterEnabled() {
        return keywordFilterEnabled;
    }

    int[] getKeywordFilters() {
        return keywordFilterIndices.clone();
    }

    void setHashSetFilter(boolean enabled, int[] indices) {
        hashSetFilterEnabled = enabled;
        hashSetFilterIndices = indices.clone();
    }

    boolean isHashSetFilterEnabled() {
        return hashSetFilterEnabled;
    }

    int[] getHashSetFilters() {
        return hashSetFilterIndices.clone();
    }

    void setObjectsFilter(boolean enabled, int[] indices) {
        objectsFilterEnabled = enabled;
        objectsFilterIndices = indices.clone();
    }

    boolean isObjectsFilterEnabled() {
        return objectsFilterEnabled;
    }

    int[] getObjectsFilters() {
        return objectsFilterIndices.clone();
    }

    void setTagsFilter(boolean enabled, int[] indices) {
        tagsFilterEnabled = enabled;
        tagsFilterIndices = indices.clone();
    }

    boolean isTagsFilterEnabled() {
        return tagsFilterEnabled;
    }

    int[] getTagsFilters() {
        return tagsFilterIndices.clone();
    }

    void setInterestingItemsFilter(boolean enabled, int[] indices) {
        interestingItemsFilterEnabled = enabled;
        interestingItemsFilterIndices = indices.clone();
    }

    boolean isInterestingItemsFilterEnabled() {
        return interestingItemsFilterEnabled;
    }

    int[] getInterestingItemsFilters() {
        return interestingItemsFilterIndices.clone();
    }

    void setScoreFilter(boolean enabled, int[] indices) {
        scoreFilterEnabled = enabled;
        scoreFilterIndices = indices.clone();
    }

    /**
     * @return the scoreFilterEnabled
     */
    boolean isScoreFilterEnabled() {
        return scoreFilterEnabled;
    }

    /**
     * @return the scoreFilterIndices
     */
    int[] getScoreFilters() {
        return scoreFilterIndices.clone();
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
        getParentFilters().clear();
        getParentFilters().addAll(parentTerms);
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
