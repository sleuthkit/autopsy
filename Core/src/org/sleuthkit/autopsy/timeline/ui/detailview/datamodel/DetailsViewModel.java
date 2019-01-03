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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.UIFilter;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.ZoomState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import org.sleuthkit.datamodel.timeline.TimelineEvent;
import org.sleuthkit.datamodel.timeline.TimelineFilter;

/**
 * Model for the Details View. Uses FilteredEventsModel as underlying datamodel
 * and supplies abstractions / data objects specific to the DetailsView
 */
final public class DetailsViewModel {

    private final static Logger logger = Logger.getLogger(DetailsViewModel.class.getName());

    private final FilteredEventsModel eventsModel;
    private final LoadingCache<ZoomState, List<TimelineEvent>> eventCache;
    private final TimelineManager eventManager;
    private final SleuthkitCase sleuthkitCase;

    public DetailsViewModel(FilteredEventsModel eventsModel) {
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
    void handleCacheInvalidation(FilteredEventsModel.CacheInvalidatedEvent event) {
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
    public List<EventStripe> getEventStripes(ZoomState zoom) throws TskCoreException {
        return getEventStripes(UIFilter.getAllPassFilter(), zoom);
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
    public List<EventStripe> getEventStripes(UIFilter uiFilter, ZoomState zoom) throws TskCoreException {
        DateTimeZone timeZone = TimeLineController.getJodaTimeZone();
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        DescriptionLoD descriptionLOD = zoom.getDescriptionLOD();
        EventTypeZoomLevel typeZoomLevel = zoom.getTypeZoomLevel();

        //intermediate results 
        Map<EventType, SetMultimap< String, EventCluster>> eventClusters = new HashMap<>();
        try {
            eventCache.get(zoom).stream()
                    .filter(uiFilter)
                    .forEach(event -> {
                        EventType clusterType = event.getEventType(typeZoomLevel);
                        eventClusters.computeIfAbsent(clusterType, eventType -> HashMultimap.create())
                                .put(event.getDescription(descriptionLOD), new EventCluster(event, clusterType, descriptionLOD));
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
    List<TimelineEvent> getEvents(ZoomState zoom, DateTimeZone timeZone) throws TskCoreException {
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        TimelineFilter.RootFilter activeFilter = zoom.getFilterState().getActiveFilter();

        long start = timeRange.getStartMillis() / 1000;
        long end = timeRange.getEndMillis() / 1000;

        //ensure length of querried interval is not 0
        end = Math.max(end, start + 1);

        //build dynamic parts of query
        String querySql = "SELECT time, file_obj_id, data_source_obj_id, artifact_id, " // NON-NLS
                          + "  event_id, " //NON-NLS
                          + " hash_hit, " //NON-NLS
                          + " tagged, " //NON-NLS
                          + " sub_type, base_type, "
                          + " full_description, med_description, short_description " // NON-NLS
                          + " FROM " + TimelineManager.getAugmentedEventsTablesSQL(activeFilter) // NON-NLS
                          + " WHERE time >= " + start + " AND time < " + end + " AND " + eventManager.getSQLWhere(activeFilter) // NON-NLS
                          + " ORDER BY time"; // NON-NLS

        List<TimelineEvent> events = new ArrayList<>();

        try (SleuthkitCase.CaseDbQuery dbQuery = sleuthkitCase.executeQuery(querySql);
                ResultSet resultSet = dbQuery.getResultSet();) {
            while (resultSet.next()) {
                events.add(eventHelper(resultSet));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get events with query: " + querySql, ex); // NON-NLS
            throw ex;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to get events with query: " + querySql, ex); // NON-NLS
            throw new TskCoreException("Failed to get events with query: " + querySql, ex);
        }
        return events;
    }

    /**
     * Map a single row in a ResultSet to an EventCluster
     *
     * @param resultSet      the result set whose current row should be mapped
     * @param typeColumn     The type column (sub_type or base_type) to use as
     *                       the type of the event cluster
     * @param descriptionLOD the description level of detail for this event
     *                       cluster
     *
     * @return an EventCluster corresponding to the current row in the given
     *         result set
     *
     * @throws SQLException
     */
    private TimelineEvent eventHelper(ResultSet resultSet) throws SQLException, TskCoreException {

        //the event tyepe to use to get the description.
        int eventTypeID = resultSet.getInt("sub_type");
        EventType eventType = eventManager.getEventType(eventTypeID).orElseThrow(()
                -> new TskCoreException("Error mapping event type id " + eventTypeID + "to EventType."));//NON-NLS

        return new TimelineEvent(
                resultSet.getLong("event_id"), // NON-NLS
                resultSet.getLong("data_source_obj_id"), // NON-NLS
                resultSet.getLong("file_obj_id"), // NON-NLS
                resultSet.getLong("artifact_id"), // NON-NLS
                resultSet.getLong("time"), // NON-NLS
                eventType,
                resultSet.getString("full_description"), // NON-NLS
                resultSet.getString("med_description"), // NON-NLS
                resultSet.getString("short_description"), // NON-NLS
                resultSet.getInt("hash_hit") != 0, //NON-NLS
                resultSet.getInt("tagged") != 0);

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
    static private List<EventStripe> mergeClustersToStripes(Period timeUnitLength, Map<EventType, SetMultimap< String, EventCluster>> eventClusters) {

        //result list to return
        ArrayList<EventCluster> mergedClusters = new ArrayList<>();

        //For each (type, description) key, merge agg events
        for (Map.Entry<EventType, SetMultimap<String, EventCluster>> typeMapEntry : eventClusters.entrySet()) {
            EventType type = typeMapEntry.getKey();
            SetMultimap<String, EventCluster> descrMap = typeMapEntry.getValue();
            //for each description ...
            for (String descr : descrMap.keySet()) {
                Set<EventCluster> events = descrMap.get(descr);
                //run through the sorted events, merging together adjacent events
                Iterator<EventCluster> iterator = events.stream()
                        .sorted(new DetailViewEvent.StartComparator())
                        .iterator();
                EventCluster current = iterator.next();
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
        Map<ImmutablePair<EventType, String>, EventStripe> stripeDescMap = new HashMap<>();

        for (EventCluster eventCluster : mergedClusters) {
            stripeDescMap.merge(ImmutablePair.of(eventCluster.getEventType(), eventCluster.getDescription()),
                    new EventStripe(eventCluster), EventStripe::merge);
        }

        return stripeDescMap.values().stream()
                .sorted(new DetailViewEvent.StartComparator())
                .collect(Collectors.toList());
    }
}
