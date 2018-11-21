/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import static org.sleuthkit.autopsy.timeline.FilteredEventsModel.unGroupConcat;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.autopsy.timeline.utils.TimelineDBUtils;
import org.sleuthkit.autopsy.timeline.zooming.ZoomState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;

/**
 * Model for the Details View. Uses FilteredEventsModel as underlying datamodel
 * and supplies abstractions / data objects specific to the DetailsView
 */
final public class DetailsViewModel {

    private final static Logger logger = Logger.getLogger(DetailsViewModel.class.getName());

    private final FilteredEventsModel eventsModel;
    private final LoadingCache<ZoomState, List<EventStripe>> eventStripeCache;
    private final TimelineManager eventManager;
    private final SleuthkitCase sleuthkitCase;

    public DetailsViewModel(FilteredEventsModel eventsModel) {
        this.eventsModel = eventsModel;
        this.eventManager = eventsModel.getEventManager();
        this.sleuthkitCase = eventsModel.getSleuthkitCase();
        eventStripeCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(params -> getEventStripes(params, TimeLineController.getJodaTimeZone())));
        eventsModel.registerForEvents(this);
    }

    @Subscribe
    void handleCacheInvalidation(FilteredEventsModel.CacheInvalidatedEvent event) {
        eventStripeCache.invalidateAll();
    }

    /**
     *
     * @return a list of event clusters at the requested zoom levels that are
     *         within the requested time range and pass the requested filter
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes() throws TskCoreException {
        final Interval range;
        final RootFilterState filter;
        final EventTypeZoomLevel zoom;
        final DescriptionLoD lod;
        synchronized (this) {
            range = eventsModel.getTimeRange();
            filter = eventsModel.getFilterState();
            zoom = eventsModel.getEventTypeZoom();
            lod = eventsModel.getDescriptionLOD();
        }
        return getEventStripes(new ZoomState(range, zoom, filter, lod));
    }

    /**
     * @param params
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes(ZoomState params) throws TskCoreException {
        try {
            return eventStripeCache.get(params);
        } catch (ExecutionException ex) {
            throw new TskCoreException("Failed to load Event Stripes from cache for " + params.toString(), ex); //NON-NLS
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
    public List<EventStripe> getEventStripes(ZoomState zoom, DateTimeZone timeZone) throws TskCoreException {
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        RootFilterState filterState = zoom.getFilterState();
        DescriptionLoD descriptionLOD = zoom.getDescriptionLOD();
        EventTypeZoomLevel typeZoomLevel = zoom.getTypeZoomLevel();

        long start = timeRange.getStartMillis() / 1000;
        long end = timeRange.getEndMillis() / 1000;

        //ensure length of querried interval is not 0
        end = Math.max(end, start + 1);

        //get some info about the time range requested
        RangeDivision rangeInfo = RangeDivision.getRangeDivision(timeRange, timeZone);

        //build dynamic parts of query
        String descriptionColumn = eventManager.getDescriptionColumn(descriptionLOD);
        final boolean useSubTypes = typeZoomLevel.equals(EventTypeZoomLevel.SUB_TYPE);
        String typeColumn = TimelineManager.typeColumnHelper(useSubTypes);

        TimelineDBUtils dbUtils = new TimelineDBUtils(sleuthkitCase);
        String querySql = "SELECT " + formatTimeFunctionHelper(rangeInfo.getPeriodSize().toChronoUnit(), timeZone) + " AS interval, " // NON-NLS
                          + dbUtils.csvAggFunction("tsk_events.event_id") + " as event_ids, " //NON-NLS
                          + dbUtils.csvAggFunction("CASE WHEN hash_hit = 1 THEN tsk_events.event_id ELSE NULL END") + " as hash_hits, " //NON-NLS
                          + dbUtils.csvAggFunction("CASE WHEN tagged = 1 THEN tsk_events.event_id ELSE NULL END") + " as taggeds, " //NON-NLS
                          + " min(time) AS minTime, max(time) AS maxTime,  " + typeColumn + ", " + descriptionColumn // NON-NLS
                          + " FROM " + TimelineManager.getAugmentedEventsTablesSQL(filterState.getActiveFilter()) // NON-NLS
                          + " WHERE time >= " + start + " AND time < " + end + " AND " + eventManager.getSQLWhere(filterState.getActiveFilter()) // NON-NLS
                          + " GROUP BY interval, " + typeColumn + " , " + descriptionColumn // NON-NLS
                          + " ORDER BY min(time)"; // NON-NLS

        // perform query and map results to AggregateEvent objects
        List<EventCluster> events = new ArrayList<>();

        try (SleuthkitCase.CaseDbQuery dbQuery = sleuthkitCase.executeQuery(querySql);
                ResultSet resultSet = dbQuery.getResultSet();) {
            while (resultSet.next()) {
                events.add(eventClusterHelper(resultSet, useSubTypes, descriptionLOD, timeZone));
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to get events with query: " + querySql, ex); // NON-NLS
        }

        return mergeClustersToStripes(rangeInfo.getPeriodSize().toUnitPeriod(), events);
    }

    /**
     * map a single row in a ResultSet to an EventCluster
     *
     * @param resultSet      the result set whose current row should be mapped
     * @param useSubTypes    use the sub_type column if true, else use the
     *                       base_type column
     * @param descriptionLOD the description level of detail for this event
     * @param filter
     *
     * @return an AggregateEvent corresponding to the current row in the given
     *         result set
     *
     * @throws SQLException
     */
    private EventCluster eventClusterHelper(ResultSet resultSet, boolean useSubTypes, DescriptionLoD descriptionLOD, DateTimeZone timeZone) throws SQLException, TskCoreException {
        Interval interval = new Interval(resultSet.getLong("minTime") * 1000, resultSet.getLong("maxTime") * 1000, timeZone);// NON-NLS
        String eventIDsString = resultSet.getString("event_ids");// NON-NLS
        List<Long> eventIDs = unGroupConcat(eventIDsString, Long::valueOf);
        String description = resultSet.getString(eventManager.getDescriptionColumn(descriptionLOD));
        int eventTypeID = useSubTypes
                ? resultSet.getInt("sub_type") //NON-NLS
                : resultSet.getInt("base_type"); //NON-NLS
        EventType eventType = eventManager.getEventType(eventTypeID).orElseThrow(()
                -> new TskCoreException("Error mapping event type id " + eventTypeID + "to EventType."));//NON-NLS

        List<Long> hashHits = unGroupConcat(resultSet.getString("hash_hits"), Long::valueOf); //NON-NLS
        List<Long> tagged = unGroupConcat(resultSet.getString("taggeds"), Long::valueOf); //NON-NLS

        return new EventCluster(interval, eventType, eventIDs, hashHits, tagged, description, descriptionLOD);
    }

    /**
     * merge the events in the given list if they are within the same period
     * General algorithm is as follows:
     *
     * 1) sort them into a map from (type, description)-> List<aggevent>
     * 2) for each key in map, merge the events and accumulate them in a list to
     * return
     *
     * @param timeUnitLength
     * @param preMergedEvents
     *
     * @return
     */
    static private List<EventStripe> mergeClustersToStripes(Period timeUnitLength, List<EventCluster> preMergedEvents) {

        //effectively map from type to (map from description to events)
        Map<EventType, SetMultimap< String, EventCluster>> typeMap = new HashMap<>();

        for (EventCluster aggregateEvent : preMergedEvents) {
            typeMap.computeIfAbsent(aggregateEvent.getEventType(), eventType -> HashMultimap.create())
                    .put(aggregateEvent.getDescription(), aggregateEvent);
        }
        //result list to return
        ArrayList<EventCluster> aggEvents = new ArrayList<>();

        //For each (type, description) key, merge agg events
        for (SetMultimap<String, EventCluster> descrMap : typeMap.values()) {
            //for each description ...
            for (String descr : descrMap.keySet()) {
                //run through the sorted events, merging together adjacent events
                Iterator<EventCluster> iterator = descrMap.get(descr).stream()
                        .sorted(Comparator.comparing(event -> event.getSpan().getStartMillis()))
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
                        aggEvents.add(current);
                        current = next;
                    }
                }
                aggEvents.add(current);
            }
        }

        //merge clusters to stripes
        Map<ImmutablePair<EventType, String>, EventStripe> stripeDescMap = new HashMap<>();

        for (EventCluster eventCluster : aggEvents) {
            stripeDescMap.merge(ImmutablePair.of(eventCluster.getEventType(), eventCluster.getDescription()),
                    new EventStripe(eventCluster), EventStripe::merge);
        }

        return stripeDescMap.values().stream().sorted(Comparator.comparing(EventStripe::getStartMillis)).collect(Collectors.toList());
    }

    /**
     * Get a column specification that will allow us to group by the requested
     * period size. That is, with all info more granular than that requested
     * dropped (replaced with zeros). For use in the select clause of a sql
     * query.
     *
     * @param periodSize The ChronoUnit describing what granularity to use.
     * @param timeZone
     *
     * @return
     */
    private String formatTimeFunctionHelper(ChronoUnit periodSize, DateTimeZone timeZone) {
        switch (sleuthkitCase.getDatabaseType()) {
            case SQLITE:
                String strfTimeFormat = getSQLIteTimeFormat(periodSize);
                String useLocalTime = timeZone.equals(DateTimeZone.getDefault()) ? ", 'localtime'" : ""; // NON-NLS
                return "strftime('" + strfTimeFormat + "', time , 'unixepoch'" + useLocalTime + ")";
            case POSTGRESQL:
                String formatString = getPostgresTimeFormat(periodSize);
                return "to_char(to_timestamp(time) AT TIME ZONE '" + timeZone.getID() + "', '" + formatString + "')";
            default:
                throw new UnsupportedOperationException("Unsupported DB type: " + sleuthkitCase.getDatabaseType().name());
        }
    }

    /*
     * Get a format string that will allow us to group by the requested period
     * size. That is, with all info more granular than that requested dropped
     * (replaced with zeros).
     *
     * @param timeUnit The ChronoUnit describing what granularity to build a
     * strftime string for
     *
     * @return a String formatted according to the sqlite strftime spec
     *
     * @see https://www.sqlite.org/lang_datefunc.html
     */
    private static String getSQLIteTimeFormat(ChronoUnit timeUnit) {
        switch (timeUnit) {
            case YEARS:
                return "%Y-01-01T00:00:00"; // NON-NLS
            case MONTHS:
                return "%Y-%m-01T00:00:00"; // NON-NLS
            case DAYS:
                return "%Y-%m-%dT00:00:00"; // NON-NLS
            case HOURS:
                return "%Y-%m-%dT%H:00:00"; // NON-NLS
            case MINUTES:
                return "%Y-%m-%dT%H:%M:00"; // NON-NLS
            case SECONDS:
            default:    //seconds - should never happen
                return "%Y-%m-%dT%H:%M:%S"; // NON-NLS  
        }
    }

    /**
     * Get a format string that will allow us to group by the requested period
     * size. That is, with all info more granular than that requested dropped
     * (replaced with zeros).
     *
     * @param timeUnit The ChronoUnit describing what granularity to build a
     *                 strftime string for
     *
     * @return a String formatted according to the Postgres
     *         to_char(to_timestamp(time) ... ) spec
     */
    private static String getPostgresTimeFormat(ChronoUnit timeUnit) {
        switch (timeUnit) {
            case YEARS:
                return "YYYY-01-01T00:00:00"; // NON-NLS
            case MONTHS:
                return "YYYY-MM-01T00:00:00"; // NON-NLS
            case DAYS:
                return "YYYY-MM-DDT00:00:00"; // NON-NLS
            case HOURS:
                return "YYYY-MM-DDTHH24:00:00"; // NON-NLS
            case MINUTES:
                return "YYYY-MM-DDTHH24:MI:00"; // NON-NLS
            case SECONDS:
            default:    //seconds - should never happen
                return "YYYY-MM-DDTHH24:MI:SS"; // NON-NLS  
        }
    }
}
