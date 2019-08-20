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
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sleuthkit.autopsy.filequery.FileSearchData.FileType;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Class to handle event bus and events for file discovery tool.
 */
final class DiscoveryEvents {

    private final static EventBus discoveryEventBus = new EventBus();

    /**
     * Get the file discovery event bus.
     *
     * @return The file discovery event bus.
     */
    static EventBus getDiscoveryEventBus() {
        return discoveryEventBus;
    }

    private DiscoveryEvents() {
    }

    /**
     * Event to signal the start of a search being performed.
     */
    static final class SearchStartedEvent {

        private final FileType fileType;

        /**
         * Construct a new SearchStartedEvent
         *
         * @param type The type of file the search event is for.
         */
        SearchStartedEvent(FileType type) {
            this.fileType = type;
        }

        /**
         * Get the type of file the search is being performed for.
         *
         * @return The type of files being searched for.
         */
        FileType getType() {
            return fileType;
        }
    }

    /**
     * Event to signal the completion of a search being performed.
     */
    static final class SearchCompleteEvent {

        private final Map<String, Integer> groupMap;
        private final List<FileSearchFiltering.FileFilter> searchFilters;
        private final FileSearch.AttributeType groupingAttribute;
        private final FileGroup.GroupSortingAlgorithm groupSort;
        private final FileSorter.SortingMethod fileSortMethod;

        /**
         * Construct a new SearchCompleteEvent,
         *
         * @param groupMap          The map of groups which were found by the
         *                          search.
         * @param searchFilters     The search filters which were used by the
         *                          search.
         * @param groupingAttribute The grouping attribute used by the search.
         * @param groupSort         The sorting algorithm used for groups.
         * @param fileSortMethod    The sorting method used for files.
         */
        SearchCompleteEvent(Map<String, Integer> groupMap, List<FileSearchFiltering.FileFilter> searchfilters,
                FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort,
                FileSorter.SortingMethod fileSortMethod) {
            this.groupMap = groupMap;
            this.searchFilters = searchfilters;
            this.groupingAttribute = groupingAttribute;
            this.groupSort = groupSort;
            this.fileSortMethod = fileSortMethod;
        }

        /**
         * Get the map of groups found by the search.
         *
         * @return The map of groups which were found by the search.
         */
        Map<String, Integer> getGroupMap() {
            return Collections.unmodifiableMap(groupMap);
        }

        /**
         * Get the file filters used by the search.
         *
         * @return The search filters which were used by the search.
         */
        List<FileSearchFiltering.FileFilter> getFilters() {
            return Collections.unmodifiableList(searchFilters);
        }

        /**
         * Get the grouping attribute used by the search.
         *
         * @return The grouping attribute used by the search.
         */
        FileSearch.AttributeType getGroupingAttr() {
            return groupingAttribute;
        }

        /**
         * Get the sorting algorithm used for groups.
         *
         * @return The sorting algorithm used for groups.
         */
        FileGroup.GroupSortingAlgorithm getGroupSort() {
            return groupSort;
        }

        /**
         * Get the sorting method used for files.
         *
         * @return The sorting method used for files.
         */
        FileSorter.SortingMethod getFileSort() {
            return fileSortMethod;
        }

    }

    /**
     * Event to signal the completion of page retrieval and include the page
     * contents.
     */
    static final class PageRetrievedEvent {

        private final List<AbstractFile> results;
        private final int page;
        private final FileType resultType;

        /**
         * Construct a new PageRetrievedEvent.
         *
         * @param resultType The type of files which exist in the page.
         * @param page       The number of the page which was retrieved.
         * @param results    The list of files in the page retrieved.
         */
        PageRetrievedEvent(FileType resultType, int page, List<AbstractFile> results) {
            this.results = results;
            this.page = page;
            this.resultType = resultType;
        }

        /**
         * Get the list of files in the page retrieved.
         *
         * @return The list of files in the page retrieved.
         */
        List<AbstractFile> getSearchResults() {
            return Collections.unmodifiableList(results);
        }

        /**
         * Get the page number which was retrieved.
         *
         * @return The number of the page which was retrieved.
         */
        int getPageNumber() {
            return page;
        }

        /**
         * Get the type of files which exist in the page.
         *
         * @return The type of files which exist in the page.
         */
        FileType getType() {
            return resultType;
        }
    }

    /**
     * Event to signal that there were no results for the search.
     */
    static final class NoResultsEvent {

        /**
         * Construct a new NoResultsEvent.
         */
        NoResultsEvent() {
            //no arg conustructor
        }
    }

    /**
     * Event to signal that a group has been selected.
     */
    static final class GroupSelectedEvent {

        private final FileType resultType;
        private final String groupName;
        private final int groupSize;
        private final List<FileSearchFiltering.FileFilter> searchfilters;
        private final FileSearch.AttributeType groupingAttribute;
        private final FileGroup.GroupSortingAlgorithm groupSort;
        private final FileSorter.SortingMethod fileSortMethod;

        /**
         * Construct a new GroupSelectedEvent.
         *
         * @param searchfilters     The search filters which were used by the
         *                          search.
         * @param groupingAttribute The grouping attribute used by the search.
         * @param groupSort         The sorting algorithm used for groups.
         * @param fileSortMethod    The sorting method used for files.
         * @param groupName         The name of the group which was selected.
         * @param groupSize         The number of files in the group which was
         *                          selected.
         * @param resultType        The type of files which exist in the group.
         */
        GroupSelectedEvent(List<FileSearchFiltering.FileFilter> searchfilters,
                FileSearch.AttributeType groupingAttribute, FileGroup.GroupSortingAlgorithm groupSort,
                FileSorter.SortingMethod fileSortMethod, String groupName, int groupSize, FileType resultType) {
            this.searchfilters = searchfilters;
            this.groupingAttribute = groupingAttribute;
            this.groupSort = groupSort;
            this.fileSortMethod = fileSortMethod;
            this.groupName = groupName;
            this.groupSize = groupSize;
            this.resultType = resultType;
        }

        /**
         * Get the type of files which exist in the group.
         *
         * @return The type of files which exist in the group.
         */
        FileType getResultType() {
            return resultType;
        }

        /**
         * Get the name of the group which was selected.
         *
         * @return The name of the group which was selected.
         */
        String getGroupName() {
            return groupName;
        }

        /**
         * Get the number of files in the group which was selected.
         *
         * @return The number of files in the group which was selected.
         */
        int getGroupSize() {
            return groupSize;
        }

        /**
         * Get the sorting algorithm used in the group which was selected.
         *
         * @return The sorting algorithm used for groups.
         */
        FileGroup.GroupSortingAlgorithm getGroupSort() {
            return groupSort;
        }

        /**
         * Get the sorting method used for files in the group.
         *
         * @return The sorting method used for files.
         */
        FileSorter.SortingMethod getFileSort() {
            return fileSortMethod;
        }

        /**
         * Get the file filters which were used by the search
         *
         * @return The search filters which were used by the search.
         */
        List<FileSearchFiltering.FileFilter> getFilters() {
            return Collections.unmodifiableList(searchfilters);
        }

        /**
         * Get the grouping attribute used to create the groups.
         *
         * @return The grouping attribute used by the search.
         */
        FileSearch.AttributeType getGroupingAttr() {
            return groupingAttribute;
        }

    }
}
