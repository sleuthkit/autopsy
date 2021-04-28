/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.eventbus.EventBus;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent.DeletedContentTagInfo;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsAddedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsDeletedEvent;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.SqlFilterState;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.FilterUtils;
import org.sleuthkit.autopsy.timeline.zooming.EventsModelParams;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.TimelineFilter.EventTypeFilter;
import org.sleuthkit.datamodel.TimelineFilter.FileTypesFilter;
import org.sleuthkit.datamodel.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.TimelineFilter.TagsFilter;
import org.sleuthkit.datamodel.TimelineFilter.TextFilter;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * In the timeline implementation of the MVC pattern, this class acts as the
 * model. The views are the event counts view, the event details view and the
 * events list view.
 *
 * Concurrency Policy: TimelineManager is internally synchronized, so methods
 * that only access the TimelineManager atomically do not need further
 * synchronization. All other member state variables should only be accessed
 * with intrinsic lock of the containing FilteredEventsModel held.
 *
 */
public final class EventsModel {

    private static final Logger logger = Logger.getLogger(EventsModel.class.getName());
    private final EventBus eventbus = new EventBus("EventsModel_EventBus"); //NON-NLS
    private final Case currentCase;
    private final TimelineManager caseDbEventManager;

    /*
     * User-specified parameters for the model exposed as JFX properties. These
     * parameters apply across all of the views of the model and are set using
     * GUI elements such the event filters panel.
     *
     * IMPORTANT: Note that the parameters are exposed both as a set and
     * individually.
     */
    private final ReadOnlyObjectWrapper<EventsModelParams> modelParamsProperty = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<RootFilterState> filterStateProperty = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Interval> timeRangeProperty = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<TimelineEventType.HierarchyLevel> eventTypesHierarchyLevelProperty = new ReadOnlyObjectWrapper<>(TimelineEventType.HierarchyLevel.CATEGORY);
    private final ReadOnlyObjectWrapper<TimelineLevelOfDetail> timelineLODProperty = new ReadOnlyObjectWrapper<>(TimelineLevelOfDetail.LOW);

    /*
     * Caches of model data from the case database.
     */
    private final ObservableMap<Long, String> datasourceIDsToNamesMap = FXCollections.observableHashMap();
    private final LoadingCache<Object, Long> maxEventTimeCache;
    private final LoadingCache<Object, Long> minEventTimeCache;
    private final LoadingCache<Long, TimelineEvent> idsToEventsCache;
    private final LoadingCache<EventsModelParams, Map<TimelineEventType, Long>> eventCountsCache;

    /**
     * Makes a new data source filter from a given entry in the cache of data
     * source object IDs to data source names.
     *
     * @param dataSourceEntry The cache entry.
     *
     * @return A new DataSourceFilter.
     */
    private static DataSourceFilter newDataSourceFilter(Map.Entry<Long, String> dataSourceEntry) {
        return new DataSourceFilter(dataSourceEntry.getValue(), dataSourceEntry.getKey());
    }

    /**
     * Constructs the model in the timeline implementation of the MVC pattern.
     *
     * @param currentCase The current case.
     * @param modelParams The initial state of the model parameters.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public EventsModel(Case currentCase, ReadOnlyObjectProperty<EventsModelParams> modelParams) throws TskCoreException {
        this.currentCase = currentCase;
        this.caseDbEventManager = currentCase.getSleuthkitCase().getTimelineManager();

        /*
         * Set up the caches of model data from the case database. Note that the
         * build() method calls specify the methods used to create default cache
         * entries when a call to get() would otherwise return a cache miss.
         */
        populateDataSourcesCache();
        idsToEventsCache = CacheBuilder.newBuilder()
                .maximumSize(5000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(caseDbEventManager::getEventById));
        eventCountsCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(this::countEventsByType));
        maxEventTimeCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> caseDbEventManager.getMaxEventTime()));
        minEventTimeCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> caseDbEventManager.getMinEventTime()));

        /*
         * Add a listener to the data sources cache that adds a data source
         * filter to the event filter state model parameter when a data source
         * is added to the cache.
         */
        InvalidationListener dataSourcesMapListener = observable -> {
            RootFilterState rootFilter = filterStateProperty.getReadOnlyProperty().get();
            addDataSourceFilters(rootFilter);
            filterStateProperty.set(rootFilter.copyOf());
        };
        datasourceIDsToNamesMap.addListener(dataSourcesMapListener);

        /*
         * Initialize the events filter state model parameter with the default
         * events filter.
         */
        filterStateProperty.set(getDefaultEventFilterState());

        /*
         * Add a listener to the model parameters property that updates the
         * properties that expose the individual model parameters when they are
         * changed through the model parameters property.
         */
        modelParamsProperty.addListener(observable -> {
            final EventsModelParams params = modelParamsProperty.get();
            if (params != null) {
                synchronized (EventsModel.this) {
                    eventTypesHierarchyLevelProperty.set(params.getEventTypesHierarchyLevel());
                    filterStateProperty.set(params.getEventFilterState());
                    timeRangeProperty.set(params.getTimeRange());
                    timelineLODProperty.set(params.getTimelineLOD());
                }
            }
        });

        modelParamsProperty.bind(modelParams);
    }

    /**
     * Populates the map of data source object IDs to data source names from the
     * data source data in the case database.
     */
    synchronized private void populateDataSourcesCache() throws TskCoreException {
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        for (DataSource ds : skCase.getDataSources()) {
            datasourceIDsToNamesMap.putIfAbsent(ds.getId(), ds.getName());
        }
    }

    /**
     * Adds a data source filter for each data source in the data sources cache
     * to a given root filter state object.
     *
     * @param rootFilterState A root filter state object.
     */
    synchronized void addDataSourceFilters(RootFilterState rootFilterState) {
        datasourceIDsToNamesMap.entrySet().forEach(entry -> rootFilterState.getDataSourcesFilterState().addSubFilterState(new SqlFilterState<>(newDataSourceFilter(entry))));
    }

    /**
     * Gets the count of all events that fit the given model parameters. The
     * counts are organized by event type for the given event types hierarchy
     * level.
     *
     * @param modelParams The model parameters.
     *
     * @return A mapping of event types to event counts at the given event types
     *         hierarchy level.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    private Map<TimelineEventType, Long> countEventsByType(EventsModelParams modelParams) throws TskCoreException {
        if (modelParams.getTimeRange() == null) {
            return Collections.emptyMap();
        } else {
            return caseDbEventManager.countEventsByType(modelParams.getTimeRange().getStartMillis() / 1000,
                    modelParams.getTimeRange().getEndMillis() / 1000,
                    modelParams.getEventFilterState().getActiveFilter(),
                    modelParams.getEventTypesHierarchyLevel());
        }
    }

    /**
     * Gets the case database events manager.
     *
     * @return The case database events manager.
     */
    public TimelineManager getEventManager() {
        return caseDbEventManager;
    }

    /**
     * Gets the case database.
     *
     * @return The case database.
     */
    public SleuthkitCase getSleuthkitCase() {
        return currentCase.getSleuthkitCase();
    }

    /**
     * Gets the model parameters property.
     *
     * @return A read only, observable property for the current model
     *         parameters.
     */
    synchronized public ReadOnlyObjectProperty<EventsModelParams> modelParamsProperty() {
        return modelParamsProperty.getReadOnlyProperty();
    }

    /**
     * Gets a read only, observable property for the time range model parameter.
     *
     * @return The time range model parameter property.
     */
    @NbBundle.Messages({
        "FilteredEventsModel.timeRangeProperty.errorTitle=Timeline",
        "FilteredEventsModel.timeRangeProperty.errorMessage=Error getting spanning interval."})
    synchronized public ReadOnlyObjectProperty<Interval> timeRangeProperty() {
        if (timeRangeProperty.get() == null) {
            try {
                timeRangeProperty.set(EventsModel.this.getSpanningInterval());
            } catch (TskCoreException timelineCacheException) {
                MessageNotifyUtil.Notify.error(Bundle.FilteredEventsModel_timeRangeProperty_errorTitle(),
                        Bundle.FilteredEventsModel_timeRangeProperty_errorMessage());
                logger.log(Level.SEVERE, "Error getting spanning interval.", timelineCacheException);
            }
        }
        return timeRangeProperty.getReadOnlyProperty();
    }

    /**
     * Gets a read only, observable property for the timeline level of detail
     * model parameter.
     *
     * @return The timeline level of detail model parameter property.
     */
    synchronized public ReadOnlyObjectProperty<TimelineLevelOfDetail> descriptionLODProperty() {
        return timelineLODProperty.getReadOnlyProperty();
    }

    /**
     * Gets a read only, observable property for the event filter model
     * parameter.
     *
     * @return The event filter model parameter property.
     */
    synchronized public ReadOnlyObjectProperty<RootFilterState> eventFilterProperty() {
        return filterStateProperty.getReadOnlyProperty();
    }

    /**
     * Gets a read only, observable property for the event types hierarchy level
     * model parameter.
     *
     * @return The event types hierarchy level model parameter property.
     */
    synchronized public ReadOnlyObjectProperty<TimelineEventType.HierarchyLevel> eventTypesHierarchyLevelProperty() {
        return eventTypesHierarchyLevelProperty.getReadOnlyProperty();
    }

    /**
     * Gets the current model parameters.
     *
     * @return The current model parameters.
     */
    synchronized public EventsModelParams getModelParams() {
        return modelParamsProperty.get();
    }

    /**
     * Gets the time range model parameter.
     *
     * @return The time range model parameter.
     */
    synchronized public Interval getTimeRange() {
        return getModelParams().getTimeRange();
    }

    /**
     * Gets the time range model parameter.
     *
     * @return The time range model parameter.
     */
    synchronized public TimelineLevelOfDetail getDescriptionLOD() {
        return getModelParams().getTimelineLOD();
    }

    /**
     * Gets the event filter model parameter.
     *
     * @return The event filter model parameter.
     */
    synchronized public RootFilterState getEventFilterState() {
        return getModelParams().getEventFilterState();
    }

    /**
     * Gets the event types hierarchy level model model parameter.
     *
     * @return The event types hierarchy level model model parameter.
     */
    synchronized public TimelineEventType.HierarchyLevel getEventTypeZoom() {
        return getModelParams().getEventTypesHierarchyLevel();
    }

    /**
     * Gets a new instance of the default event filter state model parameter,
     * with data source filters for every data source currently in the data
     * sopurces cache.
     *
     * @return An instance of the default filter state model parameter.
     */
    public synchronized RootFilterState getDefaultEventFilterState() {
        /*
         * Construct data source filters for all of the data sources in the data
         * sources cache.
         */
        DataSourcesFilter dataSourcesFilter = new DataSourcesFilter();
        datasourceIDsToNamesMap.entrySet().forEach(dataSourceEntry
                -> dataSourcesFilter.addSubFilter(newDataSourceFilter(dataSourceEntry)));

        /*
         * Make the rest of the event filters and wrap all of the filters with
         * filter state objects for the GUI.
         */
        RootFilterState rootFilterState = new RootFilterState(new RootFilter(
                new HideKnownFilter(),
                new TagsFilter(),
                new HashHitsFilter(),
                new TextFilter(),
                new EventTypeFilter(TimelineEventType.ROOT_EVENT_TYPE),
                dataSourcesFilter,
                FilterUtils.createDefaultFileTypesFilter(),
                Collections.emptySet()));

        return rootFilterState;
    }

    /**
     * Gets an event given its event ID.
     *
     * @param eventID The event ID.
     *
     * @return The event.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public TimelineEvent getEventById(Long eventID) throws TskCoreException {
        try {
            return idsToEventsCache.get(eventID);
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached event from ID", ex);
        }
    }

    /**
     * Gets a set of events given their event IDs.
     *
     * @param eventIDs The event IDs.
     *
     * @return THe events.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Set<TimelineEvent> getEventsById(Collection<Long> eventIDs) throws TskCoreException {
        Set<TimelineEvent> events = new HashSet<>();
        for (Long id : eventIDs) {
            events.add(getEventById(id));
        }
        return events;
    }

    /**
     * Gets a list of event IDs for a given time range and a given events
     * filter.
     *
     * @param timeRange   The time range.
     * @param filterState A filter state object for the events filter.
     *
     * @return The events.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public List<Long> getEventIDs(Interval timeRange, FilterState<? extends TimelineFilter> filterState) throws TskCoreException {
        final Interval overlap;
        RootFilter intersection;
        synchronized (this) {
            overlap = EventsModel.this.getSpanningInterval().overlap(timeRange);
            intersection = getEventFilterState().intersect(filterState).getActiveFilter();
        }
        return caseDbEventManager.getEventIDs(overlap, intersection);
    }

    /**
     * Gets a set of event IDs associated with a given file.
     *
     * @param file                    The file.
     * @param includeDerivedArtifacts If true, also gets the event IDs of events
     *                                associated with artifacts for which the
     *                                file is the source file.
     *
     * @return The event IDs.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Set<Long> getEventIDsForFile(AbstractFile file, boolean includeDerivedArtifacts) throws TskCoreException {
        return caseDbEventManager.getEventIDsForContent(file, includeDerivedArtifacts);
    }

    /**
     * Gets a set of event IDs associated with a given artifact.
     *
     * @param artifact The artifact.
     *
     * @return The event IDs.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public List<Long> getEventIDsForArtifact(BlackboardArtifact artifact) throws TskCoreException {
        return caseDbEventManager.getEventIDsForArtifact(artifact);
    }

    /**
     * Gets counts by event type of the events within a given time range.
     *
     * @param timeRange The time range.
     *
     * @return The event counts by type.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Map<TimelineEventType, Long> getEventCounts(Interval timeRange) throws TskCoreException {
        final RootFilterState filter;
        final TimelineEventType.HierarchyLevel typeZoom;
        synchronized (this) {
            filter = getEventFilterState();
            typeZoom = getEventTypeZoom();
        }
        try {
            return eventCountsCache.get(new EventsModelParams(timeRange, typeZoom, filter, null));
        } catch (ExecutionException executionException) {
            throw new TskCoreException("Error getting cached event counts.`1", executionException);
        }
    }

    /**
     * Gets the spanning interval for the events that fall within the time range
     * and event filter model parameters, in terms of a given time zone.
     *
     * @param timeZone The time zone.
     *
     * @return The spanning interval.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Interval getSpanningInterval(DateTimeZone timeZone) throws TskCoreException {
        return caseDbEventManager.getSpanningInterval(modelParamsProperty().get().getTimeRange(), getEventFilterState().getActiveFilter(), timeZone);
    }

    /**
     * Gets the spanning interval for all of the events in the case database.
     *
     * @return The spanning interval.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Interval getSpanningInterval() throws TskCoreException {
        return new Interval(getMinEventTime() * 1000, 1000 + getMaxEventTime() * 1000);
    }

    /**
     * Gets the spanning interval for a collection of events.
     *
     * @param eventIDs The event IDs of the events.
     *
     * @return The spanning interval.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Interval getSpanningInterval(Collection<Long> eventIDs) throws TskCoreException {
        return caseDbEventManager.getSpanningInterval(eventIDs);
    }

    /**
     * Gets the minimum event time in the case database, in seconds since the
     * UNIX epoch.
     *
     * @return The minimum event time.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Long getMinEventTime() throws TskCoreException {
        try {
            return minEventTimeCache.get("min"); // NON-NLS
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached min time.", ex);
        }
    }

    /**
     * Gets the maximum event time in the case database, in seconds since the
     * UNIX epoch.
     *
     * @return The maximum event time.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    public Long getMaxEventTime() throws TskCoreException {
        try {
            return maxEventTimeCache.get("max"); // NON-NLS
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached max time.", ex);
        }
    }

    /**
     * Updates the events model for a content tag added event and publishes a
     * tag added event via the model's event bus.
     *
     * @param evt The event.
     *
     * @return If a tags added event was published via the model's event bus.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    synchronized public boolean handleContentTagAdded(ContentTagAddedEvent evt) throws TskCoreException {
        ContentTag contentTag = evt.getAddedTag();
        Content content = contentTag.getContent();
        Set<Long> updatedEventIDs = caseDbEventManager.updateEventsForContentTagAdded(content);
        if (isNotEmpty(updatedEventIDs)) {
            invalidateCaches(updatedEventIDs);
        }
        return postTagsAdded(updatedEventIDs);
    }

    /**
     * Updates the events model for an artifact tag added event and publishes a
     * tag added event via the model's event bus.
     *
     * @param evt The event.
     *
     * @return If a tags added event was published via the model's event bus.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    synchronized public boolean handleArtifactTagAdded(BlackBoardArtifactTagAddedEvent evt) throws TskCoreException {
        BlackboardArtifactTag artifactTag = evt.getAddedTag();
        BlackboardArtifact artifact = artifactTag.getArtifact();
        Set<Long> updatedEventIDs = caseDbEventManager.updateEventsForArtifactTagAdded(artifact);
        if (isNotEmpty(updatedEventIDs)) {
            invalidateCaches(updatedEventIDs);
        }
        return postTagsAdded(updatedEventIDs);
    }

    /**
     * Updates the events model for a content tag deleted event and publishes a
     * tag deleted event via the model's event bus.
     *
     * @param evt The event.
     *
     * @return If a tags deleted event was published via the model's event bus.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    synchronized public boolean handleContentTagDeleted(ContentTagDeletedEvent evt) throws TskCoreException {
        DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        Content content = currentCase.getSleuthkitCase().getContentById(deletedTagInfo.getContentID());
        Set<Long> updatedEventIDs = caseDbEventManager.updateEventsForContentTagDeleted(content);
        if (isNotEmpty(updatedEventIDs)) {
            invalidateCaches(updatedEventIDs);
        }
        return postTagsDeleted(updatedEventIDs);
    }
    
    /**
     * Updates the events model for a data source added event.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    synchronized void handleDataSourceAdded() throws TskCoreException {
        populateDataSourcesCache();
        invalidateCaches(null);
    }

    /**
     * Updates the events model for an artifact tag deleted event and publishes
     * a tag deleted event via the model's event bus.
     *
     * @param evt The event.
     *
     * @return If a tags deleted event was published via the model's event bus.
     *
     * @throws TskCoreException If there is an error reading model data from the
     *                          case database.
     */
    synchronized public boolean handleArtifactTagDeleted(BlackBoardArtifactTagDeletedEvent evt) throws TskCoreException {
        DeletedBlackboardArtifactTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        BlackboardArtifact artifact = currentCase.getSleuthkitCase().getBlackboardArtifact(deletedTagInfo.getArtifactID());
        Set<Long> updatedEventIDs = caseDbEventManager.updateEventsForArtifactTagDeleted(artifact);
        if (isNotEmpty(updatedEventIDs)) {
            invalidateCaches(updatedEventIDs);
        }
        return postTagsDeleted(updatedEventIDs);
    }

    /**
     * Post a TagsAddedEvent to all registered subscribers, if the given set of
     * updated event IDs is not empty.
     *
     * @param updatedEventIDs The set of event ids to be included in the
     *                        TagsAddedEvent.
     *
     * @return True if an event was posted.
     */
    private boolean postTagsAdded(Set<Long> updatedEventIDs) {
        boolean tagsUpdated = !updatedEventIDs.isEmpty();
        if (tagsUpdated) {
            eventbus.post(new TagsAddedEvent(updatedEventIDs));
        }
        return tagsUpdated;
    }

    /**
     * Post a TagsDeletedEvent to all registered subscribers, if the given set
     * of updated event IDs is not empty.
     *
     * @param updatedEventIDs The set of event ids to be included in the
     *                        TagsDeletedEvent.
     *
     * @return True if an event was posted.
     */
    private boolean postTagsDeleted(Set<Long> updatedEventIDs) {
        boolean tagsUpdated = !updatedEventIDs.isEmpty();
        if (tagsUpdated) {
            eventbus.post(new TagsDeletedEvent(updatedEventIDs));
        }
        return tagsUpdated;
    }

    /**
     * Register the given object to receive events.
     *
     * @param subscriber The object to register. Must implement public methods
     *                   annotated with Subscribe.
     */
    synchronized public void registerForEvents(Object subscriber) {
        eventbus.register(subscriber);
    }

    /**
     * Un-register the given object, so it no longer receives events.
     *
     * @param subscriber The object to un-register.
     */
    synchronized public void unRegisterForEvents(Object subscriber) {
        eventbus.unregister(subscriber);
    }

    /**
     * Posts a refresh requested event to all registered subscribers.
     */
    public void postRefreshRequest() {
        eventbus.post(new RefreshRequestedEvent());
    }

    /**
     * Gets a list of the event types from the case database.
     *
     * @return The list of event types.
     */
    public ImmutableList<TimelineEventType> getEventTypes() {
        return caseDbEventManager.getEventTypes();
    }

    /**
     * Sets the hash set hits flag for the events associated with the source
     * files for a collection of hash set hit artifacts.
     *
     * @param hashSetHitArtifacts The hash set hit artifacts.
     *
     * @return The event IDs of the updated events.
     *
     * @throws TskCoreException If there is an error reading model data from or
     *                          writing model data to the case database.
     */
    synchronized public Set<Long> updateEventsForHashSetHits(Collection<BlackboardArtifact> hashSetHitArtifacts) throws TskCoreException {
        Set<Long> updatedEventIDs = new HashSet<>();
        for (BlackboardArtifact artifact : hashSetHitArtifacts) {
            Content content = currentCase.getSleuthkitCase().getContentById(artifact.getObjectID());
            updatedEventIDs.addAll(caseDbEventManager.updateEventsForHashSetHit(content));
        }
        if (isNotEmpty(updatedEventIDs)) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    /**
     * Invalidates all of the the model caches and publishes a caches
     * invalidated event. Optionally, a collection of event IDs may be supplied,
     * in which case only the corresponding entries in the event IDs cache are
     * invalidated.
     *
     * @param updatedEventIDs Either null or a collection of the event IDs.
     *
     * @throws TskCoreException
     */
    public synchronized void invalidateCaches(Collection<Long> updatedEventIDs) throws TskCoreException {
        minEventTimeCache.invalidateAll();
        maxEventTimeCache.invalidateAll();
        idsToEventsCache.invalidateAll(emptyIfNull(updatedEventIDs));
        eventCountsCache.invalidateAll();
        eventbus.post(new CacheInvalidatedEvent());
    }

    /**
     * Event fired when a cache has been invalidated and the views need to be
     * refreshed
     */
    public static class CacheInvalidatedEvent {

        private CacheInvalidatedEvent() {
        }
    }

}
