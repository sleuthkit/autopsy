/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel;

import com.google.common.eventbus.EventBus;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javax.annotation.concurrent.GuardedBy;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.RootEventType;
import org.sleuthkit.autopsy.timeline.db.EventsRepository;
import org.sleuthkit.autopsy.timeline.events.RefreshRequestedEvent;
import org.sleuthkit.autopsy.timeline.events.TagsUpdatedEvent;
import org.sleuthkit.autopsy.timeline.filters.DataSourceFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourcesFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.HashHitsFilter;
import org.sleuthkit.autopsy.timeline.filters.HashSetFilter;
import org.sleuthkit.autopsy.timeline.filters.HideKnownFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TagNameFilter;
import org.sleuthkit.autopsy.timeline.filters.TagsFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This class acts as the model for a {@link TimeLineView}
 *
 * Views can register listeners on properties returned by methods.
 *
 * This class is implemented as a filtered view into an underlying
 * {@link EventsRepository}.
 *
 * TODO: as many methods as possible should cache their results so as to avoid
 * unnecessary db calls through the {@link EventsRepository} -jm
 *
 * Concurrency Policy: repo is internally synchronized, so methods that only
 * access the repo atomically do not need further synchronization
 *
 * all other member state variables should only be accessed with intrinsic lock
 * of containing FilteredEventsModel held. Many methods delegate to a task
 * submitted to the dbQueryThread executor. These methods should synchronize on
 * this object, and the tasks should too. Since the tasks execute asynchronously
 * from the invoking methods, the methods will return and release the lock for
 * the tasks to obtain.
 *
 */
public final class FilteredEventsModel {

    private static final Logger LOGGER = Logger.getLogger(FilteredEventsModel.class.getName());

    /**
     * time range that spans the filtered events
     */
    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Interval> requestedTimeRange = new ReadOnlyObjectWrapper<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<RootFilter> requestedFilter = new ReadOnlyObjectWrapper<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper< EventTypeZoomLevel> requestedTypeZoom = new ReadOnlyObjectWrapper<>(EventTypeZoomLevel.BASE_TYPE);

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper< DescriptionLOD> requestedLOD = new ReadOnlyObjectWrapper<>(DescriptionLOD.SHORT);

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ZoomParams> requestedZoomParamters = new ReadOnlyObjectWrapper<>();

    private final EventBus eventbus = new EventBus("Event_Repository_EventBus");

    /**
     * The underlying repo for events. Atomic access to repo is synchronized
     * internally, but compound access should be done with the intrinsic lock of
     * this FilteredEventsModel object
     */
    @GuardedBy("this")
    private final EventsRepository repo;
    private final Case autoCase;

    public FilteredEventsModel(EventsRepository repo, ReadOnlyObjectProperty<ZoomParams> currentStateProperty) {
        this.repo = repo;
        this.autoCase = repo.getAutoCase();
        repo.getDatasourcesMap().addListener((MapChangeListener.Change<? extends Long, ? extends String> change) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(change.getValueAdded(), change.getKey());
            RootFilter rootFilter = filterProperty().get();
            rootFilter.getDataSourcesFilter().addSubFilter(dataSourceFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        repo.getHashSetMap().addListener((MapChangeListener.Change<? extends Long, ? extends String> change) -> {
            HashSetFilter hashSetFilter = new HashSetFilter(change.getValueAdded(), change.getKey());
            RootFilter rootFilter = filterProperty().get();
            rootFilter.getHashHitsFilter().addSubFilter(hashSetFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        repo.getTagNames().addListener((ListChangeListener.Change<? extends TagName> c) -> {
            RootFilter rootFilter = filterProperty().get();
            TagsFilter tagsFilter = rootFilter.getTagsFilter();
            repo.syncTagsFilter(tagsFilter);
            requestedFilter.set(rootFilter.copyOf());
        });
        requestedFilter.set(getDefaultFilter());

        requestedZoomParamters.addListener((Observable observable) -> {
            final ZoomParams zoomParams = requestedZoomParamters.get();

            if (zoomParams != null) {
                if (zoomParams.getTypeZoomLevel().equals(requestedTypeZoom.get()) == false
                        || zoomParams.getDescriptionLOD().equals(requestedLOD.get()) == false
                        || zoomParams.getFilter().equals(requestedFilter.get()) == false
                        || zoomParams.getTimeRange().equals(requestedTimeRange.get()) == false) {

                    requestedTypeZoom.set(zoomParams.getTypeZoomLevel());
                    requestedFilter.set(zoomParams.getFilter().copyOf());
                    requestedTimeRange.set(zoomParams.getTimeRange());
                    requestedLOD.set(zoomParams.getDescriptionLOD());
                }
            }
        });

        requestedZoomParamters.bind(currentStateProperty);
    }

    synchronized public ReadOnlyObjectProperty<ZoomParams> zoomParamtersProperty() {
        return requestedZoomParamters.getReadOnlyProperty();
    }

    /**
     * @return a read only view of the time range requested via
     *         {@link #requestTimeRange(org.joda.time.Interval)}
     */
    synchronized public ReadOnlyObjectProperty<Interval> timeRangeProperty() {
        if (requestedTimeRange.get() == null) {
            requestedTimeRange.set(getSpanningInterval());
        }
        return requestedTimeRange.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<DescriptionLOD> descriptionLODProperty() {
        return requestedLOD.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<RootFilter> filterProperty() {
        return requestedFilter.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<EventTypeZoomLevel> eventTypeZoomProperty() {
        return requestedTypeZoom.getReadOnlyProperty();
    }

    synchronized public DescriptionLOD getDescriptionLOD() {
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

        repo.getDatasourcesMap().entrySet().stream().forEach((Map.Entry<Long, String> t) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(t.getValue(), t.getKey());
            dataSourceFilter.setSelected(Boolean.TRUE);
            dataSourcesFilter.addSubFilter(dataSourceFilter);
        });

        HashHitsFilter hashHitsFilter = new HashHitsFilter();
        repo.getHashSetMap().entrySet().stream().forEach((Map.Entry<Long, String> t) -> {
            HashSetFilter hashSetFilter = new HashSetFilter(t.getValue(), t.getKey());
            hashSetFilter.setSelected(Boolean.TRUE);
            hashHitsFilter.addSubFilter(hashSetFilter);
        });

        TagsFilter tagsFilter = new TagsFilter();
        repo.getTagNames().stream().forEach(t -> {
            TagNameFilter tagNameFilter = new TagNameFilter(t, autoCase);
            tagNameFilter.setSelected(Boolean.TRUE);
            tagsFilter.addSubFilter(tagNameFilter);
        });
        return new RootFilter(new HideKnownFilter(), tagsFilter, hashHitsFilter, new TextFilter(), new TypeFilter(RootEventType.getInstance()), dataSourcesFilter);
    }

    public Interval getBoundingEventsInterval() {
        return repo.getBoundingEventsInterval(zoomParamtersProperty().get().getTimeRange(), zoomParamtersProperty().get().getFilter());
    }

    public TimeLineEvent getEventById(Long eventID) {
        return repo.getEventById(eventID);
    }

    public Set<TimeLineEvent> getEventsById(Collection<Long> eventIDs) {
        return repo.getEventsById(eventIDs);
    }

    public Map<String, Long> getTagCountsByTagName(Set<Long> eventIDsWithTags) {
        return repo.getTagCountsByTagName(eventIDsWithTags);
    }

    public Set<Long> getEventIDs(Interval timeRange, Filter filter) {
        final Interval overlap;
        final RootFilter intersect;
        synchronized (this) {
            overlap = getSpanningInterval().overlap(timeRange);
            intersect = requestedFilter.get().copyOf();
        }
        intersect.getSubFilters().add(filter);
        return repo.getEventIDs(overlap, intersect);
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
    public Map<EventType, Long> getEventCounts(Interval timeRange) {

        final RootFilter filter;
        final EventTypeZoomLevel typeZoom;
        synchronized (this) {
            filter = requestedFilter.get();
            typeZoom = requestedTypeZoom.get();
        }
        return repo.countEvents(new ZoomParams(timeRange, typeZoom, filter, null));
    }

    /**
     * @return the smallest interval spanning all the events from the
     *         repository, ignoring any filters or requested ranges
     */
    public Interval getSpanningInterval() {
        return new Interval(getMinTime() * 1000, 1000 + getMaxTime() * 1000, DateTimeZone.UTC);
    }

    /**
     * @return the smallest interval spanning all the given events
     */
    public Interval getSpanningInterval(Collection<Long> eventIDs) {
        return repo.getSpanningInterval(eventIDs);
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely first
     *         event available from the repository, ignoring any filters or
     *         requested ranges
     */
    public Long getMinTime() {
        return repo.getMinTime();
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely last
     *         event available from the repository, ignoring any filters or
     *         requested ranges
     */
    public Long getMaxTime() {
        return repo.getMaxTime();
    }

    /**
     * @param aggregation
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     */
    public List<AggregateEvent> getAggregatedEvents() {
        final Interval range;
        final RootFilter filter;
        final EventTypeZoomLevel zoom;
        final DescriptionLOD lod;
        synchronized (this) {
            range = requestedTimeRange.get();
            filter = requestedFilter.get();
            zoom = requestedTypeZoom.get();
            lod = requestedLOD.get();
        }
        return repo.getAggregatedEvents(new ZoomParams(range, zoom, filter, lod));
    }

    /**
     * @param aggregation
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     */
    public List<AggregateEvent> getAggregatedEvents(ZoomParams params) {
        return repo.getAggregatedEvents(params);
    }

    synchronized public boolean handleContentTagAdded(ContentTagAddedEvent evt) {
        ContentTag contentTag = evt.getTag();
        Content content = contentTag.getContent();
        Set<Long> updatedEventIDs = repo.addTag(content.getId(), null, contentTag);
        return postTagsUpdated(updatedEventIDs);
    }

    synchronized public boolean handleArtifactTagAdded(BlackBoardArtifactTagAddedEvent evt) {
        BlackboardArtifactTag artifactTag = evt.getTag();
        BlackboardArtifact artifact = artifactTag.getArtifact();
        Set<Long> updatedEventIDs = repo.addTag(artifact.getObjectID(), artifact.getArtifactID(), artifactTag);;
        return postTagsUpdated(updatedEventIDs);
    }

    synchronized public boolean handleContentTagDeleted(ContentTagDeletedEvent evt) {
        ContentTag contentTag = evt.getTag();
        Content content = contentTag.getContent();
        try {
            boolean tagged = autoCase.getServices().getTagsManager().getContentTagsByContent(content).isEmpty() == false;
            Set<Long> updatedEventIDs = repo.deleteTag(content.getId(), null, contentTag, tagged);
            return postTagsUpdated(updatedEventIDs);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "unable to determine tagged status of content.", ex);
        }
        return false;
    }

    synchronized public boolean handleArtifactTagDeleted(BlackBoardArtifactTagDeletedEvent evt) {
        BlackboardArtifactTag artifactTag = evt.getTag();
        BlackboardArtifact artifact = artifactTag.getArtifact();
        try {
            boolean tagged = autoCase.getServices().getTagsManager().getBlackboardArtifactTagsByArtifact(artifact).isEmpty() == false;
            Set<Long> updatedEventIDs = repo.deleteTag(artifact.getObjectID(), artifact.getArtifactID(), artifactTag, tagged);
            return postTagsUpdated(updatedEventIDs);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "unable to determine tagged status of artifact.", ex);
        }
        return false;
    }

    private boolean postTagsUpdated(Set<Long> updatedEventIDs) {
        boolean tagsUpdated = !updatedEventIDs.isEmpty();
        if (tagsUpdated) {
            eventbus.post(new TagsUpdatedEvent(updatedEventIDs));
        }
        return tagsUpdated;
    }

    synchronized public void registerForEvents(Object o) {
        eventbus.register(o);
    }

    synchronized public void unRegisterForEvents(Object o) {
        eventbus.unregister(0);
    }

    public void refresh() {
        eventbus.post(new RefreshRequestedEvent());
    }

}
