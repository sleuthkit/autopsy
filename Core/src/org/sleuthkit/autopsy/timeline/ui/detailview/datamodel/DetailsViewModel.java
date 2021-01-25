/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.eventbus.Subscribe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.EventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.UIFilter;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.EventsModelParams;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TimelineEventType;
import org.sleuthkit.datamodel.TimelineEvent;
import org.sleuthkit.datamodel.TimelineFilter;
import org.sleuthkit.datamodel.TimelineLevelOfDetail;

/**
 * Model for the Details View. Uses FilteredEventsModel as underlying datamodel
 * and supplies abstractions / data objects specific to the DetailsView
 */
final public class DetailsViewModel {

    private final static Logger logger = Logger.getLogger(DetailsViewModel.class.getName());

    private final EventsModel eventsModel;
    private final LoadingCache<EventsModelParams, List<TimelineEvent>> eventCache;
    private final TimelineManager eventManager;
    private final SleuthkitCase sleuthkitCase;

    public DetailsViewModel(EventsModel eventsModel) {
        this.eventsModel = eventsModel;
        this.eventManager = eventsModel.getEventManager();
        this.sleuthkitCase = eventsModel.getSleuthkitCase();
        eventCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(params
                        -> getEvents(params, TimeLineController.getJodaTimeZone())));
        eventsModel.registerForEvents(this);
    }

    @Subscribe
    void handleCacheInvalidation(EventsModel.CacheInvalidatedEvent event) {
        eventCache.invalidateAll();
    }

    /**
     * @param zoom
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes(EventsModelParams zoom) throws TskCoreException {
        return getEventStripes(UIFilter.getAllPassFilter(), zoom);
    }

    /**
     * @param uiFilter
     * @param zoom
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes(UIFilter uiFilter, EventsModelParams zoom) throws TskCoreException {
        DateTimeZone timeZone = TimeLineController.getJodaTimeZone();
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        TimelineLevelOfDetail descriptionLOD = zoom.getTimelineLOD();

        //intermediate results 
        Map<TimelineEventType, SetMultimap< String, EventCluster>> eventClusters = new HashMap<>();
        try {
            eventCache.get(zoom).stream()
                    .filter(uiFilter)
                    .forEach(new Consumer<TimelineEvent>() {
                @Override
                public void accept(TimelineEvent event) {
                    TimelineEventType clusterType = event.getEventType().getCategory();
                    eventClusters.computeIfAbsent(clusterType, eventType -> HashMultimap.create())
                            .put(event.getDescription(descriptionLOD), new EventCluster(event, clusterType, descriptionLOD));
                }
            });
            //get some info about the time range requested
            TimeUnits periodSize = RangeDivision.getRangeDivision(timeRange, timeZone).getPeriodSize();
            return mergeClustersToStripes(periodSize.toUnitPeriod(), eventClusters);

        } catch (ExecutionException ex) {
            throw new TskCoreException("Failed to load Event Stripes from cache for " + zoom.toString(), ex); //NON-NLS
        }
    }

    /**
     * Get a list of EventStripes, clustered according to the given zoom
     * paramaters.
     *
     * @param zoom     The ZoomState that determine the zooming, filtering and
     *                 clustering.
     * @param timeZone The time zone to use.
     *
     * @return a list of aggregate events within the given timerange, that pass
     *         the supplied filter, aggregated according to the given event type
     *         and description zoom levels
     *
     * @throws org.sleuthkit.datamodel.TskCoreException If there is an error
     *                                                  querying the db.
     */
    private List<TimelineEvent> getEvents(EventsModelParams zoom, DateTimeZone timeZone) throws TskCoreException {
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        TimelineFilter.RootFilter activeFilter = zoom.getEventFilterState().getActiveFilter();
        return eventManager.getEvents(timeRange, activeFilter);
    }

    /**
     * Merge the events in the given list if they are within the same period
     * General algorithm is as follows:
     *
     * 1) sort them into a map from (type, description)-> List<EventCluster>
     * 2) for each key in map, merge the events and accumulate them in a list to
     * return
     *
     * @param timeUnitLength
     * @param eventClusters
     *
     * @return
     */
    static private List<EventStripe> mergeClustersToStripes(Period timeUnitLength, Map<TimelineEventType, SetMultimap< String, EventCluster>> eventClusters) {

        //result list to return
        ArrayList<EventCluster> mergedClusters = new ArrayList<>();

        //For each (type, description) key, merge agg events
        for (Map.Entry<TimelineEventType, SetMultimap<String, EventCluster>> typeMapEntry : eventClusters.entrySet()) {
            TimelineEventType type = typeMapEntry.getKey();
            SetMultimap<String, EventCluster> descrMap = typeMapEntry.getValue();
            //for each description ...
            for (String descr : descrMap.keySet()) {
                Set<EventCluster> events = descrMap.get(descr);
                //run through the sorted events, merging together adjacent events
                Iterator<EventCluster> iterator = events.stream()
                        .sorted(new DetailViewEvent.StartComparator())
                        .iterator();
                EventCluster current = iterator.next();

                //JM Todo: maybe we can collect all clusters to merge in one go, rather than piece by piece for performance.
                while (iterator.hasNext()) {
                    EventCluster next = iterator.next();
                    Interval gap = current.getSpan().gap(next.getSpan());

                    //if they overlap or gap is less one quarter timeUnitLength
                    //TODO: 1/4 factor is arbitrary. review! -jm
                    if (gap == null || gap.toDuration().getMillis() <= timeUnitLength.toDurationFrom(gap.getStart()).getMillis() / 4) {
                        //merge them
                        current = EventCluster.merge(current, next);
                    } else {
                        //done merging into current, set next as new current
                        mergedClusters.add(current);
                        current = next;
                    }
                }
                mergedClusters.add(current);
            }
        }

        //merge clusters to stripes
        Map<ImmutablePair<TimelineEventType, String>, EventStripe> stripeDescMap = new HashMap<>();

        for (EventCluster eventCluster : mergedClusters) {
            stripeDescMap.merge(ImmutablePair.of(eventCluster.getEventType(), eventCluster.getDescription()),
                    new EventStripe(eventCluster), EventStripe::merge);
        }

        return stripeDescMap.values().stream()
                .sorted(new DetailViewEvent.StartComparator())
                .collect(Collectors.toList());
    }

    /** Make a sorted copy of the given set using the given comparator to sort
     * it.
     *
     * @param <X>        The type of elements in the set.
     * @param setA       The set of elements to copy into the new sorted set.
     * @param comparator The comparator to sort the new set by.
     *
     * @return A sorted copy of the given set.
     */
    static <X> SortedSet<X> copyAsSortedSet(Collection<X> setA, Comparator<X> comparator) {
        TreeSet<X> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(setA);
        return treeSet;
    }
}
