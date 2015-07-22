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
package org.sleuthkit.autopsy.timeline.events;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableMap;
import javax.annotation.concurrent.GuardedBy;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.TimeLineView;
import org.sleuthkit.autopsy.timeline.events.db.EventsRepository;
import org.sleuthkit.autopsy.timeline.events.type.EventType;
import org.sleuthkit.autopsy.timeline.events.type.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.DataSourceFilter;
import org.sleuthkit.autopsy.timeline.filters.DataSourcesFilter;
import org.sleuthkit.autopsy.timeline.filters.Filter;
import org.sleuthkit.autopsy.timeline.filters.HideKnownFilter;
import org.sleuthkit.autopsy.timeline.filters.IntersectionFilter;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TextFilter;
import org.sleuthkit.autopsy.timeline.filters.TypeFilter;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLOD;
import org.sleuthkit.autopsy.timeline.zooming.EventTypeZoomLevel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;

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

    /* requested time range, filter, event_type zoom, and description level of
     * detail. if specifics are not passed to methods, the values of these
     * members are used to query repository. */
    /**
     * time range that spans the filtered events
     */
    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Interval> requestedTimeRange = new ReadOnlyObjectWrapper<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<Filter> requestedFilter = new ReadOnlyObjectWrapper<>();

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper< EventTypeZoomLevel> requestedTypeZoom = new ReadOnlyObjectWrapper<>(EventTypeZoomLevel.BASE_TYPE);

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper< DescriptionLOD> requestedLOD = new ReadOnlyObjectWrapper<>(DescriptionLOD.SHORT);

    @GuardedBy("this")
    private final ReadOnlyObjectWrapper<ZoomParams> requestedZoomParamters = new ReadOnlyObjectWrapper<>();

    /**
     * The underlying repo for events. Atomic access to repo is synchronized
     * internally, but compound access should be done with the intrinsic lock of
     * this FilteredEventsModel object
     */
    @GuardedBy("this")
    private final EventsRepository repo;

    /** @return the default filter used at startup */
    public RootFilter getDefaultFilter() {
        DataSourcesFilter dataSourcesFilter = new DataSourcesFilter();
        repo.getDatasourcesMap().entrySet().stream().forEach((Map.Entry<Long, String> t) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(t.getValue(), t.getKey());
            dataSourceFilter.setActive(Boolean.TRUE);
            dataSourcesFilter.addDataSourceFilter(dataSourceFilter);
        });
        return new RootFilter(new HideKnownFilter(), new TextFilter(), new TypeFilter(RootEventType.getInstance()), dataSourcesFilter);
    }

    public FilteredEventsModel(EventsRepository repo, ReadOnlyObjectProperty<ZoomParams> currentStateProperty) {
        this.repo = repo;

        repo.getDatasourcesMap().addListener((MapChangeListener.Change<? extends Long, ? extends String> change) -> {
            DataSourceFilter dataSourceFilter = new DataSourceFilter(change.getValueAdded(), change.getKey());
            RootFilter rootFilter = (RootFilter) filter().get();
            rootFilter.getDataSourcesFilter().addDataSourceFilter(dataSourceFilter);
            requestedFilter.set(rootFilter);
        });
        requestedFilter.set(getDefaultFilter());

        requestedZoomParamters.addListener((Observable observable) -> {
            final ZoomParams zoomParams = requestedZoomParamters.get();

            if (zoomParams != null) {
                if (zoomParams.getTypeZoomLevel().equals(requestedTypeZoom.get()) == false
                        || zoomParams.getDescrLOD().equals(requestedLOD.get()) == false
                        || zoomParams.getFilter().equals(requestedFilter.get()) == false
                        || zoomParams.getTimeRange().equals(requestedTimeRange.get()) == false) {

                    requestedTypeZoom.set(zoomParams.getTypeZoomLevel());
                    requestedFilter.set(zoomParams.getFilter().copyOf());
                    requestedTimeRange.set(zoomParams.getTimeRange());
                    requestedLOD.set(zoomParams.getDescrLOD());
                }
            }
        });

        requestedZoomParamters.bind(currentStateProperty);
    }

    public Interval getBoundingEventsInterval() {
        return repo.getBoundingEventsInterval(getRequestedZoomParamters().get().getTimeRange(), getRequestedZoomParamters().get().getFilter());
    }

    synchronized public ReadOnlyObjectProperty<ZoomParams> getRequestedZoomParamters() {
        return requestedZoomParamters.getReadOnlyProperty();
    }

    public TimeLineEvent getEventById(Long eventID) {
        return repo.getEventById(eventID);
    }

    public Set<Long> getEventIDs(Interval timeRange, Filter filter) {
        final Interval overlap;
        final IntersectionFilter intersect;
        synchronized (this) {
            overlap = getSpanningInterval().overlap(timeRange);
            intersect = Filter.intersect(new Filter[]{filter, requestedFilter.get()});
        }
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

        final Filter filter;
        final EventTypeZoomLevel typeZoom;
        synchronized (this) {
            filter = requestedFilter.get();
            typeZoom = requestedTypeZoom.get();
        }
        return repo.countEvents(new ZoomParams(timeRange, typeZoom, filter, null));
    }

    /**
     * @return a read only view of the time range requested via
     *         {@link #requestTimeRange(org.joda.time.Interval)}
     */
    synchronized public ReadOnlyObjectProperty<Interval> timeRange() {
        if (requestedTimeRange.get() == null) {
            requestedTimeRange.set(getSpanningInterval());
        }
        return requestedTimeRange.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<DescriptionLOD> descriptionLOD() {
        return requestedLOD.getReadOnlyProperty();
    }

    synchronized public ReadOnlyObjectProperty<Filter> filter() {
        return requestedFilter.getReadOnlyProperty();
    }

    /**
     * @return the smallest interval spanning all the events from the
     *         repository, ignoring any filters or requested ranges
     */
    public final Interval getSpanningInterval() {
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
     *         event available from the repository, ignoring any filters or requested
     *         ranges
     */
    public final Long getMinTime() {
        return repo.getMinTime();
    }

    /**
     * @return the time (in seconds from unix epoch) of the absolutely last
     *         event available from the repository, ignoring any filters or requested
     *         ranges
     */
    public final Long getMaxTime() {
        return repo.getMaxTime();
    }

    /**
     * @param aggregation
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation to
     *         control the grouping of events
     */
    public List<AggregateEvent> getAggregatedEvents() {
        final Interval range;
        final Filter filter;
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
     *         range and pass the requested filter, using the given aggregation to
     *         control the grouping of events
     */
    public List<AggregateEvent> getAggregatedEvents(ZoomParams params) {
        return repo.getAggregatedEvents(params);
    }

    synchronized public ReadOnlyObjectProperty<EventTypeZoomLevel> eventTypeZoom() {
        return requestedTypeZoom.getReadOnlyProperty();
    }

    synchronized public EventTypeZoomLevel getEventTypeZoom() {
        return requestedTypeZoom.get();
    }

    synchronized public DescriptionLOD getDescriptionLOD() {
        return requestedLOD.get();
    }

    public ObservableMap<Long, String> getDataSources() {
        return repo.getDatasourcesMap();
    }

}
