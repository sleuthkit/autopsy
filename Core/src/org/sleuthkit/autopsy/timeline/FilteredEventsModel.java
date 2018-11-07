/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javax.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
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
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsAddedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsDeletedEvent;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.CheckedFunction;
import org.sleuthkit.autopsy.timeline.zooming.ZoomState;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import org.sleuthkit.datamodel.timeline.TimelineEvent;
import org.sleuthkit.datamodel.timeline.TimelineFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HashSetFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.RootFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagNameFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TagsFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TextFilter;
import org.sleuthkit.datamodel.timeline.TimelineFilter.TypeFilter;

/**
 * This class acts as the model for a TimelineView
 *
 * Views can register listeners on properties returned by methods.
 *
 * This class is implemented as a filtered view into an underlying
 * TimelineManager.
 *
 * Maintainers, NOTE: as many methods as possible should cache their results so
 * as to avoid unnecessary db calls through the TimelineManager -jm
 *
 * Concurrency Policy: TimelineManager is internally synchronized, so methods
 * that only access the repo atomically do not need further synchronization. All
 * other member state variables should only be accessed with intrinsic lock of
 * containing FilteredEventsModel held.
 *
 */
public final class FilteredEventsModel {

    private static final Logger logger = Logger.getLogger(FilteredEventsModel.class.getName());

    private final TimelineManager eventManager;

    private final Case autoCase;
    private final EventBus eventbus = new EventBus("FilteredEventsModel_EventBus"); //NON-NLS

    //Filter and zoome state
    private final ReadOnlyObjectWrapper<RootFilterState> requestedFilter = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Interval> requestedTimeRange = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<ZoomState> requestedZoomState = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper< EventTypeZoomLevel> requestedTypeZoom = new ReadOnlyObjectWrapper<>(EventTypeZoomLevel.BASE_TYPE);
    private final ReadOnlyObjectWrapper< DescriptionLoD> requestedLOD = new ReadOnlyObjectWrapper<>(DescriptionLoD.SHORT);

    //caches
    private final LoadingCache<Object, Long> maxCache;
    private final LoadingCache<Object, Long> minCache;
    private final LoadingCache<Long, TimelineEvent> idToEventCache;
    private final LoadingCache<ZoomState, Map<EventType, Long>> eventCountsCache;
    private final ObservableMap<Long, DataSource> datasourcesMap = FXCollections.observableHashMap();
    private final ObservableSet< String> hashSets = FXCollections.observableSet();
    private final ObservableList<TagName> tagNames = FXCollections.observableArrayList();

    public FilteredEventsModel(Case autoCase, ReadOnlyObjectProperty<ZoomState> currentStateProperty) throws TskCoreException {
        this.autoCase = autoCase;
        this.eventManager = autoCase.getSleuthkitCase().getTimelineManager();
        populateFilterData();

        //caches
        idToEventCache = CacheBuilder.newBuilder()
                .maximumSize(5000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(eventManager::getEventById));
        eventCountsCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(this::countEventsByType));

        maxCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> eventManager.getMaxTime()));
        minCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> eventManager.getMinTime()));

        InvalidationListener filterSyncListener = observable -> {
            RootFilterState rootFilter = filterProperty().get().copyOf();
            syncFilters(rootFilter);
            requestedFilter.set(rootFilter);
        };

        datasourcesMap.addListener(filterSyncListener);
        getHashSets().addListener(filterSyncListener);
        getTagNames().addListener(filterSyncListener);

        requestedFilter.set(getDefaultFilter());

        requestedZoomState.addListener(observable -> {
            final ZoomState zoomState = requestedZoomState.get();

            if (zoomState != null) {
                synchronized (FilteredEventsModel.this) {
                    requestedTypeZoom.set(zoomState.getTypeZoomLevel());
                    requestedFilter.set(zoomState.getFilterState());
                    requestedTimeRange.set(zoomState.getTimeRange());
                    requestedLOD.set(zoomState.getDescriptionLOD());
                }
            }
        });

        requestedZoomState.bind(currentStateProperty);
    }

    /**
     * get the count of all events that fit the given zoom params organized by
     * the EvenType of the level specified in the zoomState
     *
     * @param zoomState The params that control what events to count and how to
     *                  organize the returned map
     *
     * @return a map from event type( of the requested level) to event counts
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    private Map<EventType, Long> countEventsByType(ZoomState zoomState) throws TskCoreException {
        if (zoomState.getTimeRange() == null) {
            return Collections.emptyMap();
        } else {
            return eventManager.countEventsByType(zoomState.getTimeRange().getStartMillis() / 1000,
                    zoomState.getTimeRange().getEndMillis() / 1000,
                    zoomState.getFilterState().getActiveFilter(), zoomState.getTypeZoomLevel());
        }
    }

    public TimelineManager getEventManager() {
        return eventManager;
    }

    public SleuthkitCase getSleuthkitCase() {
        return autoCase.getSleuthkitCase();
    }

    public ObservableList<TagName> getTagNames() {
        return tagNames;
    }

    synchronized public ObservableSet< String> getHashSets() {
        return hashSets;
    }

    public Interval getBoundingEventsInterval(Interval timeRange, RootFilter filter, DateTimeZone timeZone) throws TskCoreException {
        return eventManager.getSpanningInterval(timeRange, filter, timeZone);
    }

    /**
     * Readonly observable property for the current ZoomState
     *
     * @return A readonly observable property for the current ZoomState.
     */
    synchronized public ReadOnlyObjectProperty<ZoomState> zoomStateProperty() {
        return requestedZoomState.getReadOnlyProperty();
    }

    /**
     * Get the current ZoomState
     *
     * @return The current ZoomState
     */
    synchronized public ZoomState getZoomState() {
        return requestedZoomState.get();
    }

    /**
     * Update the data used to determine the available filters.
     */
    synchronized private void populateFilterData() throws TskCoreException {
        SleuthkitCase skCase = autoCase.getSleuthkitCase();
        hashSets.addAll(eventManager.getHashSetNames());
        Set<Long> dataSourceIDs = eventManager.getDataSourceIDs();

        //because there is no way to remove a datasource we only add to this map.
        for (Long id : dataSourceIDs) {
            try {
                if (datasourcesMap.get(id) == null) {
                    datasourcesMap.put(id, skCase.getDataSource(id));
                }
            } catch (TskDataException ex) {
                throw new TskCoreException("Error looking up datasource for id " + id, ex);
            }
        }

        //should this only be tags applied to files or event bearing artifacts?
        tagNames.setAll(skCase.getTagNamesInUse());
    }

    /**
     * "sync" the given root filter with the state of the casee: Disable filters
     * for tags that are not in use in the case, and add new filters for tags,
     * hashsets, and datasources. that don't have them. New filters are selected
     * by default.
     *
     * @param rootFilter the filter state to modify so it is consistent with the
     *                   tags in use in the case
     */
    public void syncFilters(RootFilterState rootFilter) {
        TagsFilter tagsFilter = rootFilter.getTagsFilterState().getFilter();
        for (TagName tagName : tagNames) {
            tagsFilter.addSubFilter(new TagNameFilter(tagName));
        }
        for (FilterState<? extends TagNameFilter> tagFilterState : rootFilter.getTagsFilterState().getSubFilterStates()) {
            tagFilterState.setDisabled(tagNames.contains(tagFilterState.getFilter().getTagName()) == false);
        }

        DataSourcesFilter dataSourcesFilter = rootFilter.getDataSourcesFilterState().getFilter();
        for (Map.Entry<Long, DataSource> entry : datasourcesMap.entrySet()) {
            dataSourcesFilter.addSubFilter(new DataSourceFilter(entry.getValue().getName(), entry.getKey()));
        }

        HashHitsFilter hashSetsFilter = rootFilter.getHashHitsFilterState().getFilter();
        for (String hashSet : getHashSets()) {
            hashSetsFilter.addSubFilter(new HashSetFilter(hashSet));
        }
    }

    /**
     * Get a read only view of the time range currently in view.
     *
     * @return A read only view of the time range currently in view.
     */
    @NbBundle.Messages({
        "FilteredEventsModel.timeRangeProperty.errorTitle=Timeline",
        "FilteredEventsModel.timeRangeProperty.errorMessage=Error getting spanning interval."})
    synchronized public ReadOnlyObjectProperty<Interval> timeRangeProperty() {
        if (requestedTimeRange.get() == null) {
            try {
                requestedTimeRange.set(getSpanningInterval());
            } catch (TskCoreException timelineCacheException) {
                MessageNotifyUtil.Notify.error(Bundle.FilteredEventsModel_timeRangeProperty_errorTitle(),
                        Bundle.FilteredEventsModel_timeRangeProperty_errorMessage());
                logger.log(Level.SEVERE, "Error getting spanning interval.", timelineCacheException);
            }
        }
        return requestedTimeRange.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<DescriptionLoD> descriptionLODProperty() {
        return requestedLOD.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<RootFilterState> filterProperty() {
        return requestedFilter.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<EventTypeZoomLevel> eventTypeZoomProperty() {
        return requestedTypeZoom.getReadOnlyProperty();
    }

    /**
     * The time range currently in view.
     *
     * @return The time range currently in view.
     */
    synchronized public Interval getTimeRange() {
        return getZoomState().getTimeRange();
    }

    synchronized public DescriptionLoD getDescriptionLOD() {
        return getZoomState().getDescriptionLOD();
    }

    synchronized public RootFilterState getFilterState() {
        return getZoomState().getFilterState();
    }

    synchronized public EventTypeZoomLevel getEventTypeZoom() {
        return getZoomState().getTypeZoomLevel();
    }

    /**
     * @return the default filter used at startup
     */
    public RootFilterState getDefaultFilter() {
        DataSourcesFilter dataSourcesFilter = new DataSourcesFilter();

        datasourcesMap.entrySet().stream().forEach(entry -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(entry.getValue().getName(), entry.getKey());
            dataSourcesFilter.addSubFilter(dataSourceFilter);
        });

        HashHitsFilter hashHitsFilter = new HashHitsFilter();
        getHashSets().forEach(hashSetName -> {
            HashSetFilter hashSetFilter = new HashSetFilter(hashSetName);
            hashHitsFilter.addSubFilter(hashSetFilter);
        });

        TagsFilter tagsFilter = new TagsFilter();
        getTagNames().stream().forEach(tagName -> {
            TagNameFilter tagNameFilter = new TagNameFilter(tagName);
            tagsFilter.addSubFilter(tagNameFilter);
        });
        return new RootFilterState(new RootFilter(new HideKnownFilter(),
                tagsFilter,
                hashHitsFilter,
                new TextFilter(),
                new TypeFilter(EventType.ROOT_EVENT_TYPE),
                dataSourcesFilter,
                Collections.emptySet()));
    }

    public Interval getBoundingEventsInterval(DateTimeZone timeZone) throws TskCoreException {
        return eventManager.getSpanningInterval(zoomStateProperty().get().getTimeRange(), getFilterState().getActiveFilter(), timeZone);
    }

    public TimelineEvent getEventById(Long eventID) throws TskCoreException {
        try {
            return idToEventCache.get(eventID);
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached event from ID", ex);
        }
    }

    public Set<TimelineEvent> getEventsById(Collection<Long> eventIDs) throws TskCoreException {
        Set<TimelineEvent> events = new HashSet<>();
        for (Long id : eventIDs) {
            events.add(getEventById(id));
        }
        return events;
    }

    /**
     * get a count of tagnames applied to the given event ids as a map from
     * tagname displayname to count of tag applications
     *
     * @param eventIDsWithTags the event ids to get the tag counts map for
     *
     * @return a map from tagname displayname to count of applications
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Map<String, Long> getTagCountsByTagName(Set<Long> eventIDsWithTags) throws TskCoreException {
        return eventManager.getTagCountsByTagName(eventIDsWithTags);
    }

    public List<Long> getEventIDs(Interval timeRange, TimelineFilter filter) throws TskCoreException {

        final Interval overlap;
        RootFilterState intersection;
        synchronized (this) {
            overlap = getSpanningInterval().overlap(timeRange);
            intersection = getFilterState().intersect(filter);
        }

        return eventManager.getEventIDs(overlap, intersection.getActiveFilter());
    }

    /**
     * return the number of events that pass the requested filter and are within
     * the given time range.
     *
     * NOTE: this method does not change the requested time range
     *
     * @param timeRange
     *
     * @return
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Map<EventType, Long> getEventCounts(Interval timeRange) throws TskCoreException {

        final RootFilterState filter;
        final EventTypeZoomLevel typeZoom;
        synchronized (this) {
            filter = getFilterState();
            typeZoom = getEventTypeZoom();
        }
        try {
            return eventCountsCache.get(new ZoomState(timeRange, typeZoom, filter, null));
        } catch (ExecutionException executionException) {
            throw new TskCoreException("Error getting cached event counts.`1", executionException);
        }
    }

    /**
     * @return The smallest interval spanning all the events from the
     *         repository, ignoring any filters or requested ranges.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Interval getSpanningInterval() throws TskCoreException {
        return new Interval(getMinTime() * 1000, 1000 + getMaxTime() * 1000);
    }

    /**
     * @param eventIDs
     *
     * @return the smallest interval spanning all the given events
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Interval getSpanningInterval(Collection<Long> eventIDs) throws TskCoreException {
        return eventManager.getSpanningInterval(eventIDs);
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely first
     *         event available from the repository, ignoring any filters or
     *         requested ranges
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Long getMinTime() throws TskCoreException {
        try {
            return minCache.get("min"); // NON-NLS
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached min time.", ex);
        }
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely last
     *         event available from the repository, ignoring any filters or
     *         requested ranges
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public Long getMaxTime() throws TskCoreException {
        try {
            return maxCache.get("max"); // NON-NLS
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached max time.", ex);
        }
    }

    synchronized public boolean handleContentTagAdded(ContentTagAddedEvent evt) throws TskCoreException {
        ContentTag contentTag = evt.getAddedTag();
        Content content = contentTag.getContent();
        Set<Long> updatedEventIDs = addTag(content.getId(), null, contentTag);
        return postTagsAdded(updatedEventIDs);
    }

    synchronized public boolean handleArtifactTagAdded(BlackBoardArtifactTagAddedEvent evt) throws TskCoreException {
        BlackboardArtifactTag artifactTag = evt.getAddedTag();
        BlackboardArtifact artifact = artifactTag.getArtifact();
        Set<Long> updatedEventIDs = addTag(artifact.getObjectID(), artifact.getArtifactID(), artifactTag);
        return postTagsAdded(updatedEventIDs);
    }

    synchronized public boolean handleContentTagDeleted(ContentTagDeletedEvent evt) throws TskCoreException {
        DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();

        Content content = autoCase.getSleuthkitCase().getContentById(deletedTagInfo.getContentID());
        boolean tagged = autoCase.getServices().getTagsManager().getContentTagsByContent(content).isEmpty() == false;
        Set<Long> updatedEventIDs = deleteTag(content.getId(), null, deletedTagInfo.getTagID(), tagged);
        return postTagsDeleted(updatedEventIDs);
    }

    synchronized public boolean handleArtifactTagDeleted(BlackBoardArtifactTagDeletedEvent evt) throws TskCoreException {
        DeletedBlackboardArtifactTagInfo deletedTagInfo = evt.getDeletedTagInfo();

        BlackboardArtifact artifact = autoCase.getSleuthkitCase().getBlackboardArtifact(deletedTagInfo.getArtifactID());
        boolean tagged = autoCase.getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact).isEmpty() == false;
        Set<Long> updatedEventIDs = deleteTag(artifact.getObjectID(), artifact.getArtifactID(), deletedTagInfo.getTagID(), tagged);
        return postTagsDeleted(updatedEventIDs);
    }

    /**
     * Get a List of event IDs for the events that are derived from the given
     * file.
     *
     * @param file                    The AbstractFile to get derived event IDs
     *                                for.
     * @param includeDerivedArtifacts If true, also get event IDs for events
     *                                derived from artifacts derived form this
     *                                file. If false, only gets events derived
     *                                directly from this file (file system
     *                                timestamps).
     *
     * @return A List of event IDs for the events that are derived from the
     *         given file.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<Long> getEventIDsForFile(AbstractFile file, boolean includeDerivedArtifacts) throws TskCoreException {
        return eventManager.getEventIDsForFile(file, includeDerivedArtifacts);
    }

    /**
     * Get a List of event IDs for the events that are derived from the given
     * artifact.
     *
     * @param artifact The BlackboardArtifact to get derived event IDs for.
     *
     * @return A List of event IDs for the events that are derived from the
     *         given artifact.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<Long> getEventIDsForArtifact(BlackboardArtifact artifact) throws TskCoreException {
        return eventManager.getEventIDsForArtifact(artifact);
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
     * Post a RefreshRequestedEvent to all registered subscribers.
     */
    public void postRefreshRequest() {
        eventbus.post(new RefreshRequestedEvent());
    }

    /**
     * (Re)Post an AutopsyEvent received from another event distribution system
     * locally to all registered subscribers.
     *
     * @param event
     */
    public void postAutopsyEventLocally(AutopsyEvent event) {
        eventbus.post(event);
    }

    public ImmutableList<EventType> getEventTypes() {
        return eventManager.getEventTypes();
    }

    synchronized public Set<Long> addTag(long objID, Long artifactID, Tag tag) throws TskCoreException {
        Set<Long> updatedEventIDs = eventManager.setEventsTagged(objID, artifactID, true);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized public Set<Long> deleteTag(long objID, Long artifactID, long tagID, boolean tagged) throws TskCoreException {
        Set<Long> updatedEventIDs = eventManager.setEventsTagged(objID, artifactID, tagged);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized public Set<Long> setHashHit(Collection<BlackboardArtifact> artifacts, boolean hasHashHit) throws TskCoreException {
        Set<Long> updatedEventIDs = new HashSet<>();
        for (BlackboardArtifact artifact : artifacts) {
            updatedEventIDs.addAll(eventManager.setEventsHashed(artifact.getObjectID(), hasHashHit));
        }
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    /**
     * Invalidate the timeline caches for the given event IDs. Also forces the
     * filter values to be updated with any new values from the case data.( data
     * sources, tags, etc)
     *
     * @param updatedEventIDs A collection of the event IDs whose cached event
     *                        objects should be invalidated. Can be null or an
     *                        empty sett to invalidate the general caches, such
     *                        as min/max time, or the counts per event type.
     *
     * @throws TskCoreException
     */
    public synchronized void invalidateCaches(Collection<Long> updatedEventIDs) throws TskCoreException {
        minCache.invalidateAll();
        maxCache.invalidateAll();
        idToEventCache.invalidateAll(emptyIfNull(updatedEventIDs));
        eventCountsCache.invalidateAll();

        populateFilterData();

        eventbus.post(new CacheInvalidatedEvent());
    }

    /**
     * Event fired when a cache has been invalidated. The UI should make it
     * clear that the view is potentially out of date and present an action to
     * refresh the view.
     */
    public static class CacheInvalidatedEvent {

        private CacheInvalidatedEvent() {
        }
    }

    /**
     * take the result of a group_concat SQLite operation and split it into a
     * set of X using the mapper to to convert from string to X If groupConcat
     * is empty, null, or all whitespace, returns an empty list.
     *
     * @param <X>         the type of elements to return
     * @param groupConcat a string containing the group_concat result ( a comma
     *                    separated list)
     * @param mapper      a function from String to X
     *
     * @return a Set of X, each element mapped from one element of the original
     *         comma delimited string
     */
    public static <X> List<X> unGroupConcat(String groupConcat, CheckedFunction<String, X, TskCoreException> mapper) throws TskCoreException {

        if (org.apache.commons.lang3.StringUtils.isBlank(groupConcat)) {
            return Collections.emptyList();
        }

        List<X> result = new ArrayList<>();
        String[] split = groupConcat.split(",");
        for (String s : split) {
            result.add(mapper.apply(s));
        }
        return result;
    }
}
