/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.filequery;

public class Search {

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

    Search(int fileTypeIndex, int orderByIndex, int groupByIndex, int orderGroupsBy) {
        this.fileTypeIndex = fileTypeIndex;
        this.orderByIndex = orderByIndex;
        this.groupByIndex = groupByIndex;
        this.orderGroupsBy = orderGroupsBy;
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
}
