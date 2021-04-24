/*
 * Autopsy
 *
 * Copyright 2019-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.search;

import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.discovery.search.DiscoveryKeyUtils.GroupKey;
import org.sleuthkit.autopsy.discovery.search.SearchData.Type;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Class to handle event bus and events for discovery tool.
 */
public final class DiscoveryEventUtils {

    private final static EventBus discoveryEventBus = new EventBus();

    /**
     * Get the discovery event bus.
     *
     * @return The discovery event bus.
     */
    public static EventBus getDiscoveryEventBus() {
        return discoveryEventBus;
    }

    /**
     * Private no arg constructor for Utility class.
     */
    private DiscoveryEventUtils() {
        //Utility class private constructor intentionally left blank.
    }

    /**
     * Event to signal the start of a search being performed.
     */
    public static final class SearchStartedEvent {

        private final Type type;

        /**
         * Construct a new SearchStartedEvent.
         *
         * @param type The type of result the search event is for.
         */
        public SearchStartedEvent(Type type) {
            this.type = type;
        }

        /**
         * Get the type of result the search is being performed for.
         *
         * @return The type of results being searched for.
         */
        public Type getType() {
            return type;
        }

    }

    /**
     * Event to signal that the Instances list should have selection cleared.
     */
    public static final class ClearInstanceSelectionEvent {

        /**
         * Construct a new ClearInstanceSelectionEvent.
         */
        public ClearInstanceSelectionEvent() {
            //no arg constructor
        }
    }

    /**
     * Event to signal that any background tasks currently running should be
     * cancelled.
     */
    public static final class CancelBackgroundTasksEvent {

        public CancelBackgroundTasksEvent() {
            //no-arg constructor
        }
    }

    /**
     * Event to signal that the Instances list should be populated.
     */
    public static final class PopulateInstancesListEvent {

        private final List<AbstractFile> instances;

        /**
         * Construct a new PopulateInstancesListEvent.
         */
        public PopulateInstancesListEvent(List<AbstractFile> files) {
            instances = files;
        }

        /**
         * Get the list of AbstractFiles for the instances list.
         *
         * @return The list of AbstractFiles for the instances list.
         */
        public List<AbstractFile> getInstances() {
            return Collections.unmodifiableList(instances);
        }
    }

    /**
     * Event to signal that the list should be populated.
     */
    public static final class PopulateDomainTabsEvent {

        private final String domain;

        /**
         * Construct a new PopulateDomainTabsEvent.
         *
         * @param domain The domain this event is for, or empty if no domain is
         *               selected.
         */
        public PopulateDomainTabsEvent(String domain) {
            this.domain = domain;
        }

        /**
         * Get the domain for the details area.
         *
         * @return The the domain for the details area.
         */
        public String getDomain() {
            return domain;
        }
    }

    /**
     * Event to signal the completion of a search being performed.
     */
    public static final class SearchCompleteEvent {

        private final Map<GroupKey, Integer> groupMap;
        private final List<AbstractFilter> searchFilters;
        private final DiscoveryAttributes.AttributeType groupingAttribute;
        private final Group.GroupSortingAlgorithm groupSort;
        private final ResultsSorter.SortingMethod sortMethod;

        /**
         * Construct a new SearchCompleteEvent,
         *
         * @param groupMap          The map of groups which were found by the
         *                          search.
         * @param searchfilters     The search filters which were used by the
         *                          search.
         * @param groupingAttribute The grouping attribute used by the search.
         * @param groupSort         The sorting algorithm used for groups.
         * @param sortMethod        The sorting method used for results.
         */
        public SearchCompleteEvent(Map<GroupKey, Integer> groupMap, List<AbstractFilter> searchfilters,
                DiscoveryAttributes.AttributeType groupingAttribute, Group.GroupSortingAlgorithm groupSort,
                ResultsSorter.SortingMethod sortMethod) {
            this.groupMap = groupMap;
            this.searchFilters = searchfilters;
            this.groupingAttribute = groupingAttribute;
            this.groupSort = groupSort;
            this.sortMethod = sortMethod;
        }

        /**
         * Get the map of groups found by the search.
         *
         * @return The map of groups which were found by the search.
         */
        public Map<GroupKey, Integer> getGroupMap() {
            return Collections.unmodifiableMap(groupMap);
        }

        /**
         * Get the filters used by the search.
         *
         * @return The search filters which were used by the search.
         */
        public List<AbstractFilter> getFilters() {
            return Collections.unmodifiableList(searchFilters);
        }

        /**
         * Get the grouping attribute used by the search.
         *
         * @return The grouping attribute used by the search.
         */
        public DiscoveryAttributes.AttributeType getGroupingAttr() {
            return groupingAttribute;
        }

        /**
         * Get the sorting algorithm used for groups.
         *
         * @return The sorting algorithm used for groups.
         */
        public Group.GroupSortingAlgorithm getGroupSort() {
            return groupSort;
        }

        /**
         * Get the sorting method used for results.
         *
         * @return The sorting method used for results.
         */
        public ResultsSorter.SortingMethod getResultSort() {
            return sortMethod;
        }

    }

    /**
     * Event to signal the completion of a search being performed.
     */
    public static final class ArtifactSearchResultEvent {

        private final List<BlackboardArtifact> listOfArtifacts = new ArrayList<>();
        private final BlackboardArtifact.ARTIFACT_TYPE artifactType;
        private final boolean grabFocus;

        /**
         * Construct a new ArtifactSearchResultEvent with a list of specified
         * results and an artifact type.
         *
         * @param artifactType    The type of results in the list.
         * @param listOfArtifacts The list of results retrieved.
         * @param shouldGrabFocus True if the list of artifacts should have
         *                        focus, false otherwise.
         */
        public ArtifactSearchResultEvent(BlackboardArtifact.ARTIFACT_TYPE artifactType, List<BlackboardArtifact> listOfArtifacts, boolean shouldGrabFocus) {
            if (listOfArtifacts != null) {
                this.listOfArtifacts.addAll(listOfArtifacts);
            }
            this.artifactType = artifactType;
            this.grabFocus = shouldGrabFocus;
        }

        /**
         * Get the list of results included in the event.
         *
         * @return The list of results retrieved.
         */
        public List<BlackboardArtifact> getListOfArtifacts() {
            return Collections.unmodifiableList(listOfArtifacts);
        }

        /**
         * Get the type of BlackboardArtifact type of which exist in the list.
         *
         * @return The BlackboardArtifact type of which exist in the list.
         */
        public BlackboardArtifact.ARTIFACT_TYPE getArtifactType() {
            return artifactType;
        }

        /**
         * Get whether or not the artifacts list should grab focus.
         *
         * @return True if the list of artifacts should have focus, false
         *         otherwise.
         */
        public boolean shouldGrabFocus() {
            return grabFocus;
        }

    }

    /**
     * Event to signal the completion of a search for mini timeline results
     * being performed.
     */
    public static final class MiniTimelineResultEvent {

        private final List<MiniTimelineResult> results = new ArrayList<>();
        private final String domain;
        private final boolean grabFocus;

        /**
         * Construct a new MiniTimelineResultEvent.
         *
         * @param results         The list of MiniTimelineResults contained in
         *                        this event.
         * @param domain          The domain the results are for.
         * @param shouldGrabFocus True if the list of dates should have focus,
         *                        false otherwise.
         */
        public MiniTimelineResultEvent(List<MiniTimelineResult> results, String domain, boolean shouldGrabFocus) {
            if (results != null) {
                this.results.addAll(results);
            }
            this.grabFocus = shouldGrabFocus;
            this.domain = domain;
        }

        /**
         * Get the list of results included in the event.
         *
         * @return The list of results found.
         */
        public List<MiniTimelineResult> getResultList() {
            return Collections.unmodifiableList(results);
        }

        /**
         * Get the domain this list of results is for.
         *
         * @return The domain the list of results is for.
         */
        public String getDomain() {
            return domain;
        }

        /**
         * Get whether or not the dates list should grab focus.
         *
         * @return True if the list of dates should have focus, false otherwise.
         */
        public boolean shouldGrabFocus() {
            return grabFocus;
        }
    }

    /**
     * Event to signal the completion of page retrieval and include the page
     * contents.
     */
    public static final class PageRetrievedEvent {

        private final List<Result> results;
        private final int page;
        private final Type resultType;

        /**
         * Construct a new PageRetrievedEvent.
         *
         * @param resultType The type of results which exist in the page.
         * @param page       The number of the page which was retrieved.
         * @param results    The list of results in the page retrieved.
         */
        public PageRetrievedEvent(Type resultType, int page, List<Result> results) {
            this.results = results;
            this.page = page;
            this.resultType = resultType;
        }

        /**
         * Get the list of results in the page retrieved.
         *
         * @return The list of results in the page retrieved.
         */
        public List<Result> getSearchResults() {
            return Collections.unmodifiableList(results);
        }

        /**
         * Get the page number which was retrieved.
         *
         * @return The number of the page which was retrieved.
         */
        public int getPageNumber() {
            return page;
        }

        /**
         * Get the type of results which exist in the page.
         *
         * @return The type of results which exist in the page.
         */
        public Type getType() {
            return resultType;
        }
    }

    /**
     * Event to signal that there were no results for the search.
     */
    public static final class NoResultsEvent {

        /**
         * Construct a new NoResultsEvent.
         */
        public NoResultsEvent() {
            //no arg constructor
        }
    }

    /**
     * Event to signal that a search has been cancelled.
     */
    public static final class SearchCancelledEvent {

        /**
         * Construct a new SearchCancelledEvent.
         */
        public SearchCancelledEvent() {
            //no arg constructor
        }

    }

    /**
     * Event to signal that a group has been selected.
     */
    public static final class GroupSelectedEvent {

        private final Type resultType;
        private final GroupKey groupKey;
        private final int groupSize;
        private final List<AbstractFilter> searchfilters;
        private final DiscoveryAttributes.AttributeType groupingAttribute;
        private final Group.GroupSortingAlgorithm groupSort;
        private final ResultsSorter.SortingMethod sortMethod;

        /**
         * Construct a new GroupSelectedEvent.
         *
         * @param searchfilters     The search filters which were used by the
         *                          search.
         * @param groupingAttribute The grouping attribute used by the search.
         * @param groupSort         The sorting algorithm used for groups.
         * @param sortMethod        The sorting method used for results.
         * @param groupKey          The key associated with the group which was
         *                          selected.
         * @param groupSize         The number of results in the group which was
         *                          selected.
         * @param resultType        The type of results which exist in the
         *                          group.
         */
        public GroupSelectedEvent(List<AbstractFilter> searchfilters,
                DiscoveryAttributes.AttributeType groupingAttribute, Group.GroupSortingAlgorithm groupSort,
                ResultsSorter.SortingMethod sortMethod, GroupKey groupKey, int groupSize, Type resultType) {
            this.searchfilters = searchfilters;
            this.groupingAttribute = groupingAttribute;
            this.groupSort = groupSort;
            this.sortMethod = sortMethod;
            this.groupKey = groupKey;
            this.groupSize = groupSize;
            this.resultType = resultType;
        }

        /**
         * Get the type of results which exist in the group.
         *
         * @return The type of results which exist in the group.
         */
        public Type getResultType() {
            return resultType;
        }

        /**
         * Get the group key which is used to uniquely identify the group
         * selected.
         *
         * @return The group key which is used to uniquely identify the group
         *         selected.
         */
        public GroupKey getGroupKey() {
            return groupKey;
        }

        /**
         * Get the number of results in the group which was selected.
         *
         * @return The number of results in the group which was selected.
         */
        public int getGroupSize() {
            return groupSize;
        }

        /**
         * Get the sorting algorithm used in the group which was selected.
         *
         * @return The sorting algorithm used for groups.
         */
        public Group.GroupSortingAlgorithm getGroupSort() {
            return groupSort;
        }

        /**
         * Get the sorting method used for results in the group.
         *
         * @return The sorting method used for results.
         */
        public ResultsSorter.SortingMethod getResultSort() {
            return sortMethod;
        }

        /**
         * Get the result filters which were used by the search.
         *
         * @return The search filters which were used by the search.
         */
        public List<AbstractFilter> getFilters() {
            return Collections.unmodifiableList(searchfilters);
        }

        /**
         * Get the grouping attribute used to create the groups.
         *
         * @return The grouping attribute used by the search.
         */
        public DiscoveryAttributes.AttributeType getGroupingAttr() {
            return groupingAttribute;
        }

    }

    /**
     * Event to signal that the visibility of the Details area should change.
     */
    public static class DetailsVisibleEvent {

        private final boolean showDetailsArea;

        /**
         * Construct a new DetailsVisibleEvent.
         *
         * @param isVisible True if the details area should be visible, false
         *                  otherwise.
         */
        public DetailsVisibleEvent(boolean isVisible) {
            showDetailsArea = isVisible;
        }

        /**
         * Get the visibility of the Details area.
         *
         * @return True if the details area should be visible, false otherwise.
         */
        public boolean isShowDetailsArea() {
            return showDetailsArea;
        }
    }

}
