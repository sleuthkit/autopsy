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
import com.google.common.cache.CacheLoader;
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
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.CombinedEvent;
import org.sleuthkit.datamodel.timeline.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventStripe;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import org.sleuthkit.datamodel.timeline.SingleEvent;
import org.sleuthkit.datamodel.timeline.ZoomParams;
import org.sleuthkit.datamodel.timeline.filters.DataSourceFilter;
import org.sleuthkit.datamodel.timeline.filters.DataSourcesFilter;
import org.sleuthkit.datamodel.timeline.filters.Filter;
import org.sleuthkit.datamodel.timeline.filters.HashHitsFilter;
import org.sleuthkit.datamodel.timeline.filters.HashSetFilter;
import org.sleuthkit.datamodel.timeline.filters.HideKnownFilter;
import org.sleuthkit.datamodel.timeline.filters.RootFilter;
import org.sleuthkit.datamodel.timeline.filters.TagNameFilter;
import org.sleuthkit.datamodel.timeline.filters.TagsFilter;
import org.sleuthkit.datamodel.timeline.filters.TextFilter;
import org.sleuthkit.datamodel.timeline.filters.TypeFilter;

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
    private final ReadOnlyObjectWrapper<RootFilter> requestedFilter = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Interval> requestedTimeRange = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<ZoomParams> requestedZoomParamters = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper< EventTypeZoomLevel> requestedTypeZoom = new ReadOnlyObjectWrapper<>(EventTypeZoomLevel.BASE_TYPE);
    private final ReadOnlyObjectWrapper< DescriptionLoD> requestedLOD = new ReadOnlyObjectWrapper<>(DescriptionLoD.SHORT);

    //caches
    private final LoadingCache<Object, Long> maxCache;
    private final LoadingCache<Object, Long> minCache;
    private final LoadingCache<Long, SingleEvent> idToEventCache;
    private final LoadingCache<ZoomParams, Map<EventType, Long>> eventCountsCache;
    private final LoadingCache<ZoomParams, List<EventStripe>> eventStripeCache;
    private final ObservableMap<Long, String> datasourcesMap = FXCollections.observableHashMap();
    private final ObservableSet< String> hashSets = FXCollections.observableSet();
    private final ObservableList<TagName> tagNames = FXCollections.observableArrayList();

    public FilteredEventsModel(Case autoCase, ReadOnlyObjectProperty<ZoomParams> currentStateProperty) throws TskCoreException {
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
                .build(new CacheLoaderImpl<>(eventManager::countEventsByType));
        eventStripeCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(params -> eventManager.getEventStripes(params, TimeLineController.getJodaTimeZone())));

        maxCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> eventManager.getMaxTime()));
        minCache = CacheBuilder.newBuilder()
                .build(new CacheLoaderImpl<>(ignored -> eventManager.getMinTime()));

        getDatasourcesMap().addListener((MapChangeListener.Change<? extends Long, ? extends String> change) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(change.getValueAdded(), change.getKey());
            RootFilter rootFilter = filterProperty().get();
            rootFilter.getDataSourcesFilter().addSubFilter(dataSourceFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        getHashSets().addListener((SetChangeListener.Change< ? extends String> change) -> {
            HashSetFilter hashSetFilter = new HashSetFilter(change.getElementAdded());
            RootFilter rootFilter = filterProperty().get();
            rootFilter.getHashHitsFilter().addSubFilter(hashSetFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        getTagNames().addListener((ListChangeListener.Change<? extends TagName> change) -> {
            RootFilter rootFilter = filterProperty().get();
            TagsFilter tagsFilter = rootFilter.getTagsFilter();
            syncTagsFilter(tagsFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        requestedFilter.set(getDefaultFilter());

        //TODO: use bindings to keep these in sync? -jm
        requestedZoomParamters.addListener((Observable observable) -> {
            final ZoomParams zoomParams = requestedZoomParamters.get();

            if (zoomParams != null) {
                synchronized (FilteredEventsModel.this) {
                    requestedTypeZoom.set(zoomParams.getTypeZoomLevel());
                    requestedFilter.set(zoomParams.getFilter());
                    requestedTimeRange.set(zoomParams.getTimeRange());
                    requestedLOD.set(zoomParams.getDescriptionLOD());
                }
            }
        });

        requestedZoomParamters.bind(currentStateProperty);
    }

    public ObservableList<TagName> getTagNames() {
        return tagNames;
    }

    synchronized public ObservableMap<Long, String> getDatasourcesMap() {
        return datasourcesMap;
    }

    synchronized public ObservableSet< String> getHashSets() {
        return hashSets;
    }

    public Interval getBoundingEventsInterval(Interval timeRange, RootFilter filter, DateTimeZone timeZone) throws TskCoreException {
        return eventManager.getSpanningInterval(timeRange, filter, timeZone);
    }

    /**
     * Readonly observable property for the current ZoomParams
     *
     * @return A readonly observable property for the current ZoomParams.
     */
    synchronized public ReadOnlyObjectProperty<ZoomParams> zoomParametersProperty() {
        return requestedZoomParamters.getReadOnlyProperty();
    }

    /**
     * Get the current ZoomParams
     *
     * @return The current ZoomParams
     */
    synchronized public ZoomParams getZoomParamaters() {
        return requestedZoomParamters.get();
    }

    /**
     * Use the given SleuthkitCase to update the data used to determine the
     * available filters.
     *
     * @param skCase
     */
    synchronized private void populateFilterData() throws TskCoreException {
        SleuthkitCase skCase = autoCase.getSleuthkitCase();
        hashSets.addAll(eventManager.getHashSetNames());

        //because there is no way to remove a datasource we only add to this map.
        for (Long id : eventManager.getDataSourceIDs()) {
            datasourcesMap.putIfAbsent(id, skCase.getContentById(id).getDataSource().getName());
        }

        //should this only be tags applied to files or event bearing artifacts?
        tagNames.setAll(skCase.getTagNamesInUse());
    }

    /**
     * "sync" the given tags filter with the tagnames in use: Disable filters
     * for tags that are not in use in the case, and add new filters for tags
     * that don't have them. New filters are selected by default.
     *
     * @param tagsFilter the tags filter to modify so it is consistent with the
     *                   tags in use in the case
     */
    public void syncTagsFilter(TagsFilter tagsFilter) {
        for (TagName tagName : tagNames) {
            tagsFilter.addSubFilter(new TagNameFilter(tagName));
        }
        for (TagNameFilter filter : tagsFilter.getSubFilters()) {
            filter.setDisabled(tagNames.contains(filter.getTagName()) == false);
        }
    }

    public boolean areFiltersEquivalent(RootFilter filter1, RootFilter filter2) {
        return eventManager.getSQLWhere(filter1).equals(eventManager.getSQLWhere(filter2));
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

    synchronized public ReadOnlyObjectProperty<RootFilter> filterProperty() {
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
        return timeRangeProperty().get();
    }

    synchronized public DescriptionLoD getDescriptionLOD() {
        return requestedLOD.get();
    }

    synchronized public RootFilter getFilter() {
        return requestedFilter.get();
    }

    synchronized public EventTypeZoomLevel getEventTypeZoom() {
        return requestedTypeZoom.get();
    }

    /**
     * @return the default filter used at startup
     */
    public RootFilter getDefaultFilter() {
        DataSourcesFilter dataSourcesFilter = new DataSourcesFilter();

        getDatasourcesMap().entrySet().stream().forEach((Map.Entry<Long, String> entry) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(entry.getValue(), entry.getKey());
            dataSourceFilter.setSelected(true);
            dataSourcesFilter.addSubFilter(dataSourceFilter);
        });

        HashHitsFilter hashHitsFilter = new HashHitsFilter();
        getHashSets().forEach(hashSetName -> {
            HashSetFilter hashSetFilter = new HashSetFilter(hashSetName);
            hashSetFilter.setSelected(true);
            hashHitsFilter.addSubFilter(hashSetFilter);
        });

        TagsFilter tagsFilter = new TagsFilter();
        getTagNames().stream().forEach(tagName -> {
            TagNameFilter tagNameFilter = new TagNameFilter(tagName);
            tagNameFilter.setSelected(true);
            tagsFilter.addSubFilter(tagNameFilter);
        });
        return new RootFilter(new HideKnownFilter(), tagsFilter, hashHitsFilter, new TextFilter(), new TypeFilter(EventType.ROOT_EVEN_TYPE), dataSourcesFilter, Collections.emptySet());
    }

    public Interval getBoundingEventsInterval(DateTimeZone timeZone) throws TskCoreException {
        return eventManager.getSpanningInterval(zoomParametersProperty().get().getTimeRange(), zoomParametersProperty().get().getFilter(), timeZone);
    }

    public SingleEvent getEventById(Long eventID) throws TskCoreException {
        try {
            return idToEventCache.get(eventID);
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached event from ID", ex);
        }
    }

    public Set<SingleEvent> getEventsById(Collection<Long> eventIDs) throws TskCoreException {
        Set<SingleEvent> events = new HashSet<>();
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
     */
    public Map<String, Long> getTagCountsByTagName(Set<Long> eventIDsWithTags) throws TskCoreException {
        return eventManager.getTagCountsByTagName(eventIDsWithTags);
    }

    public List<Long> getEventIDs(Interval timeRange, Filter filter) throws TskCoreException {

        final Interval overlap;
        final RootFilter intersect;
        synchronized (this) {
            overlap = getSpanningInterval().overlap(timeRange);
            intersect = requestedFilter.get().copyOf();
        }
        intersect.getSubFilters().add(filter);
        return eventManager.getEventIDs(overlap, intersect);
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @return A List of combined events, sorted by timestamp.
     */
    public List<CombinedEvent> getCombinedEvents() throws TskCoreException {
        return eventManager.getCombinedEvents(requestedTimeRange.get(), requestedFilter.get());
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
     */
    public Map<EventType, Long> getEventCounts(Interval timeRange) throws TskCoreException {

        final RootFilter filter;
        final EventTypeZoomLevel typeZoom;
        synchronized (this) {
            filter = requestedFilter.get();
            typeZoom = requestedTypeZoom.get();
        }
        try {
            return eventCountsCache.get(new ZoomParams(timeRange, typeZoom, filter, null));
        } catch (ExecutionException executionException) {
            throw new TskCoreException("Error getting cached event counts.`1", executionException);
        }
    }

    /**
     * @return The smallest interval spanning all the events from the
     *         repository, ignoring any filters or requested ranges.
     */
    public Interval getSpanningInterval() throws TskCoreException {
        return new Interval(getMinTime() * 1000, 1000 + getMaxTime() * 1000);
    }

    /**
     * @return the smallest interval spanning all the given events
     */
    public Interval getSpanningInterval(Collection<Long> eventIDs) throws TskCoreException {
        return eventManager.getSpanningInterval(eventIDs);
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely first
     *         event available from the repository, ignoring any filters or
     *         requested ranges
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
     */
    public Long getMaxTime() throws TskCoreException {
        try {
            return maxCache.get("max"); // NON-NLS
        } catch (ExecutionException ex) {
            throw new TskCoreException("Error getting cached max time.", ex);
        }
    }

    /**
     *
     * @return a list of event clusters at the requested zoom levels that are
     *         within the requested time range and pass the requested filter
     */
    public List<EventStripe> getEventStripes() throws TskCoreException {
        final Interval range;
        final RootFilter filter;
        final EventTypeZoomLevel zoom;
        final DescriptionLoD lod;
        synchronized (this) {
            range = requestedTimeRange.get();
            filter = requestedFilter.get();
            zoom = requestedTypeZoom.get();
            lod = requestedLOD.get();
        }
        return getEventStripes(new ZoomParams(range, zoom, filter, lod));
    }

    /**
     * @param params
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     */
    public List<EventStripe> getEventStripes(ZoomParams params) throws TskCoreException {
        try {
            return eventStripeCache.get(params);
        } catch (ExecutionException ex) {
            logger.log(Level.SEVERE, "Failed to load Event Stripes from cache for " + params.toString(), ex); //NON-NLS
            return Collections.emptyList();
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

    synchronized public boolean handleContentTagDeleted(ContentTagDeletedEvent evt) {
        DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        try {
            Content content = autoCase.getSleuthkitCase().getContentById(deletedTagInfo.getContentID());
            boolean tagged = autoCase.getServices().getTagsManager().getContentTagsByContent(content).isEmpty() == false;
            Set<Long> updatedEventIDs = deleteTag(content.getId(), null, deletedTagInfo.getTagID(), tagged);
            return postTagsDeleted(updatedEventIDs);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "unable to determine tagged status of content.", ex); //NON-NLS
        }
        return false;
    }

    synchronized public boolean handleArtifactTagDeleted(BlackBoardArtifactTagDeletedEvent evt) {
        DeletedBlackboardArtifactTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        try {
            BlackboardArtifact artifact = autoCase.getSleuthkitCase().getBlackboardArtifact(deletedTagInfo.getArtifactID());
            boolean tagged = autoCase.getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact).isEmpty() == false;
            Set<Long> updatedEventIDs = deleteTag(artifact.getObjectID(), artifact.getArtifactID(), deletedTagInfo.getTagID(), tagged);
            return postTagsDeleted(updatedEventIDs);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "unable to determine tagged status of artifact.", ex); //NON-NLS
        }
        return false;
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

    synchronized Set<Long> setFileStatus(AbstractFile file) throws TskCoreException {
        Set<Long> updatedEventIDs = eventManager.setFileStatus(file);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized void invalidateAllCaches() {
        minCache.invalidateAll();
        maxCache.invalidateAll();
        idToEventCache.invalidateAll();
        invalidateCaches(Collections.emptyList());
    }

    synchronized private void invalidateCaches(Collection<Long> updatedEventIDs) {
        idToEventCache.invalidateAll(updatedEventIDs);
        eventCountsCache.invalidateAll();
        eventStripeCache.invalidateAll();
        try {
            populateFilterData();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed topopulate filter data.", ex); //NON-NLS
        }
        eventbus.post(new CacheInvalidatedEvent());
    }

    /**
     * Extension of CacheLoader that delegates the load method to the Function
     * passed to the constructor.
     *
     * @param <K> Key type.
     * @param <V> Value type.
     */
    private static class CacheLoaderImpl<K, V> extends CacheLoader<K, V> {

        /**
         * Functinal interface for a function from I to O that throws an
         * Exception.
         *
         * @param <I> Input type.
         * @param <O> Output type.
         */
        @FunctionalInterface
        interface CheckedFunction<I, O> {

            O apply(I input) throws Exception;
        }

        private final CheckedFunction<K, V> func;

        CacheLoaderImpl(CheckedFunction<K, V> func) {
            this.func = func;
        }

        @Override
        public V load(K key) throws Exception {
            return func.apply(key);
        }

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

}
