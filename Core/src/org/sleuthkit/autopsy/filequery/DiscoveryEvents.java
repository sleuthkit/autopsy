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

import com.google.common.eventbus.EventBus;
import java.util.List;
import java.util.LinkedHashMap;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Class to handle envent bus and events for file discovery tool
 */
final class DiscoveryEvents {

    private final static EventBus discoveryEventBus = new EventBus();

    /**
     * Get the file discovery event bus
     *
     * @return the file discovery event bus
     */
    static EventBus getDiscoveryEventBus() {
        return discoveryEventBus;
    }

    private DiscoveryEvents() {
    }

    /**
     * Event to signal the start of a search being performed
     */
    static final class SearchStartedEvent {

        private final FileType fileType;

        /**
         * Construct a new SearchStartedEvent
         *
         * @param type the type of file the search event is for
         */
        SearchStartedEvent(FileType type) {
            this.fileType = type;
        }

        /**
         * Get the type of file the search is being performed for
         *
         * @return the type of files being searched for
         */
        FileType getType() {
            return fileType;
        }
    }

    /**
     * Event to signal the completion of a search being performed
     */
    static final class SearchCompleteEvent {

        private final LinkedHashMap<String, Integer> groupMap;
        private final List<FileSearchFiltering.FileFilter> searchfilters;
        private final FileSearch.AttributeType groupingAttribute;
        private final FileGroup.GroupSortingAlgorithm groupSort;
        private final FileSorter.SortingMethod fileSortMethod;

        /**
         * Construct a new SearchCompleteEvent
         *
         * @param results the groupMap which were found by the search
         */
        SearchCompleteEvent(LinkedHashMap<String, Integer> groupMap, List<FileSearchFiltering.FileFilter> searchfilters,
                FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort,
                FileSorter.SortingMethod fileSortMethod) {
            this.groupMap = groupMap;
            this.searchfilters = searchfilters;
            this.groupingAttribute = groupingAttribute;
            this.groupSort = groupSort;
            this.fileSortMethod = fileSortMethod;
        }

        /**
         * Get the groupMap of the search
         *
         * @return the groupMap of the search
         */
        LinkedHashMap<String, Integer> getGroupMap() {
            return groupMap;
        }

        List<FileSearchFiltering.FileFilter> getFilters() {
            return searchfilters;
        }

        FileSearch.AttributeType getGroupingAttr() {
            return groupingAttribute;
        }

        FileGroup.GroupSortingAlgorithm getGroupSort() {
            return groupSort;
        }

        FileSorter.SortingMethod getFileSort() {
            return fileSortMethod;
        }

    }

    static final class PageRetrievedEvent {

        private final List<AbstractFile> results;
        private final int page;
        private final FileType resultType;

        PageRetrievedEvent(FileType resultType, int page, List<AbstractFile> results) {
            this.results = results;
            this.page = page;
            this.resultType = resultType;
        }

        /**
         * Get the groupMap of the search
         *
         * @return the groupMap of the search
         */
        List<AbstractFile> getSearchResults() {
            return results;
        }

        
        int getPageNumber(){
            return page;
        }
        /**
         * Get the type of files which exist in the group
         *
         * @return the type of files in the group
         */
        FileType getType() {
            return resultType;
        }
    }

    static final class PageChangedEvent {

        private final int startingEntry;
        private final int pageSize;

        PageChangedEvent(int startingEntry, int pageSize) {
            this.startingEntry = startingEntry;
            this.pageSize = pageSize;
        }
        
        int getStartingEntry(){
            return startingEntry;
        }
        
        int getPageSize(){
            return pageSize;
        }
    }
}
