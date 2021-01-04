/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourcesummary.datamodel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.autopsy.geolocation.AbstractWaypointFetcher;
import org.sleuthkit.autopsy.geolocation.GeoFilter;
import org.sleuthkit.autopsy.geolocation.MapWaypoint;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.DataSource;

/**
 * Gathers summary data about Geolocation information for a data source.
 */
public class GeolocationSummary implements DefaultArtifactUpdateGovernor {
    /**
     * A count of hits for a particular city.
     */
    public static class CityRecordCount {

        private final CityRecord cityRecord;
        private final int count;

        /**
         * Main constructor.
         *
         * @param cityRecord The record for the city including name, country,
         * and location.
         * @param count The number of hits in proximity to that city.
         */
        CityRecordCount(CityRecord cityRecord, int count) {
            this.cityRecord = cityRecord;
            this.count = count;
        }

        /**
         * @return The record for the city including name, country, and
         * location.
         */
        public CityRecord getCityRecord() {
            return cityRecord;
        }

        /**
         * @return The number of hits in proximity to that city.
         */
        public int getCount() {
            return count;
        }
    }

    /**
     * Returned data providing counts of most common cities seen and most recent
     * cities seen.
     */
    public static class CityData {

        private final CityCountsList mostCommon;
        private final CityCountsList mostRecent;
        private final Long mostRecentSeen;

        /**
         * Main constructor.
         *
         * @param mostCommon The list of most common cities seen.
         * @param mostRecent The list of most recent cities seen.
         * @param mostRecentSeen
         */
        CityData(CityCountsList mostCommon, CityCountsList mostRecent, Long mostRecentSeen) {
            this.mostCommon = mostCommon;
            this.mostRecent = mostRecent;
            this.mostRecentSeen = mostRecentSeen;
        }

        /**
         * @return The list of most common cities found in the data source.
         */
        public CityCountsList getMostCommon() {
            return mostCommon;
        }

        /**
         * @return The list of cities seen in most recent use of data source.
         */
        public CityCountsList getMostRecent() {
            return mostRecent;
        }

        /**
         * @return The time stamp in seconds from epoch of the most recently
         * seen city
         */
        public Long getMostRecentSeen() {
            return mostRecentSeen;
        }
    }

    /**
     * Indicates a list of cities to which way points are closest. Also includes
     * the count of way points where no closest city was determined due to not
     * being close enough.
     */
    public static class CityCountsList {

        private final List<CityRecordCount> counts;
        private final int otherCount;

        /**
         * Main constructor.
         *
         * @param counts The list of cities and the count of how many points are
         * closest to that city.
         * @param otherCount The count of points where no closest city was
         * determined due to not being close enough.
         */
        CityCountsList(List<CityRecordCount> counts, int otherCount) {
            this.counts = Collections.unmodifiableList(new ArrayList<>(counts));
            this.otherCount = otherCount;
        }

        /**
         * @return The list of cities and the count of how many points are
         * closest to that city.
         */
        public List<CityRecordCount> getCounts() {
            return counts;
        }

        /**
         * @return The count of points where no closest city was determined due
         * to not being close enough.
         */
        public int getOtherCount() {
            return otherCount;
        }
    }

    // taken from GeoFilterPanel: all of the GPS artifact types.
    @SuppressWarnings("deprecation")
    private static final List<ARTIFACT_TYPE> GPS_ARTIFACT_TYPES = Arrays.asList(
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACK,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT,
            BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF
    );

    // all GPS types
    private static final Set<Integer> GPS_ARTIFACT_TYPE_IDS = GPS_ARTIFACT_TYPES.stream()
            .map(artifactType -> artifactType.getTypeID())
            .collect(Collectors.toSet());

    private final SleuthkitCaseProvider provider;
    private final java.util.logging.Logger logger;
    private final SupplierWithException<ClosestCityMapper, IOException> cityMapper;

    /**
     * A supplier of an item T that can throw an exception of type E.
     */
    public interface SupplierWithException<T, E extends Throwable> {

        /**
         * A supplier method that can throw an exception of E.
         *
         * @return The object type.
         * @throws E The exception type.
         */
        T get() throws E;
    }

    /**
     * Default constructor.
     */
    public GeolocationSummary() {
        this(() -> ClosestCityMapper.getInstance(), SleuthkitCaseProvider.DEFAULT, Logger.getLogger(GeolocationSummary.class.getName()));
    }

    /**
     * Main constructor.
     *
     * @param cityMapper A means of acquiring a ClosestCityMapper that can throw
     * an IOException.
     * @param provider A means of acquiring a SleuthkitCaseProvider.
     * @param logger The logger.
     */
    public GeolocationSummary(SupplierWithException<ClosestCityMapper, IOException> cityMapper, SleuthkitCaseProvider provider, java.util.logging.Logger logger) {
        this.cityMapper = cityMapper;
        this.provider = provider;
        this.logger = logger;
    }

    /**
     * @return Returns all the geolocation artifact types.
     */
    public List<ARTIFACT_TYPE> getGeoTypes() {
        return GPS_ARTIFACT_TYPES;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return GPS_ARTIFACT_TYPE_IDS;
    }

    /**
     * Returns whether or not the time is >= the provided minimum time handling
     * the event where either time is null.
     *
     * @param minTime The minimum time. If null is provided, this function will
     * return false.
     * @param time The time to check. If null is provided and the min time is
     * non-null, then this function will return false.
     * @return If minTime == null then false. If minTime != null && time == null
     * then false. Otherwise time >= minTime.
     */
    private boolean greaterThanOrEqual(Long minTime, Long time) {
        if (minTime != null && time != null && time >= minTime) {
            return true;
        } else {
            return false;
        }
    }

    private static final Pair<Integer, Integer> EMPTY_COUNT = Pair.of(0, 0);

    /**
     * Based on a set of waypoints, determines the count of total waypoints and
     * a total of waypoints whose time stamp is greater than or equal to
     * minTime.
     *
     * @param points The list of way points.
     * @param minTime The minimum time for most recent points count.
     * @return A pair where the left value is the total count of way points and
     * the right is the total list of way points that are >= minTime.
     */
    private Pair<Integer, Integer> getCounts(List<MapWaypoint> points, Long minTime) {

        if (points == null) {
            return EMPTY_COUNT;
        }

        return points.stream().reduce(
                EMPTY_COUNT,
                (total, w) -> Pair.of(total.getLeft() + 1, total.getRight() + (greaterThanOrEqual(minTime, w.getTimestamp()) ? 1 : 0)),
                (pair1, pair2) -> Pair.of(pair1.getLeft() + pair2.getLeft(), pair1.getRight() + pair2.getRight()));
    }
    
    
    private static final long DAY_SECS = 24 * 60 * 60;

    /**
     * Get this list of hits per city where the list is sorted descending by
     * number of found hits (i.e. most hits is first index).
     *
     * @param dataSource The data source.
     * @param daysCount  Number of days to go back.
     * @param maxCount   Maximum number of results.
     * 
     * @return The sorted list.
     * 
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    public CityData getCityCounts(DataSource dataSource, int daysCount, int maxCount)
            throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException, IOException {

        ClosestCityMapper closestCityMapper = ClosestCityMapper.getInstance();

        List<MapWaypoint> dataSourcePoints = getPoints(dataSource);

        Map<CityRecord, List<MapWaypoint>> allCityPoints = new HashMap<>();
        List<MapWaypoint> others = new ArrayList<>();
        Long mostRecent = null;

        for (MapWaypoint pt : dataSourcePoints) {
            CityRecord city = closestCityMapper.findClosest(new CityRecord(null, null, pt.getX(), pt.getY()));
            Long curTime = pt.getTimestamp();
            if (curTime != null && (mostRecent == null || curTime > mostRecent)) {
                mostRecent = curTime;
            }

            if (city == null) {
                others.add(pt);
            } else {
                List<MapWaypoint> cityPoints = allCityPoints.get(city);
                if (cityPoints == null) {
                    cityPoints = new ArrayList<>();
                    allCityPoints.put(city, cityPoints);
                }

                cityPoints.add(pt);
            }
        }

        final Long mostRecentMinTime = (mostRecent == null) ? null : mostRecent - daysCount * DAY_SECS;

        // pair left is total count and right is count within range (or mostRecent is null)
        Map<CityRecord, Pair<Integer, Integer>> allCityCounts = allCityPoints.entrySet().stream()
                .collect(Collectors.toMap((e) -> e.getKey(), (e) -> getCounts(e.getValue(), mostRecentMinTime)));

        List<CityRecordCount> mostCommonCounts = allCityCounts.entrySet().stream()
                .map(e -> new CityRecordCount(e.getKey(), e.getValue().getLeft()))
                .sorted((a, b) -> -Integer.compare(a.getCount(), b.getCount()))
                .limit(maxCount)
                .collect(Collectors.toList());

        List<CityRecordCount> mostRecentCounts = allCityCounts.entrySet().stream()
                .map(e -> new CityRecordCount(e.getKey(), e.getValue().getRight()))
                .sorted((a, b) -> -Integer.compare(a.getCount(), b.getCount()))
                .limit(maxCount)
                .collect(Collectors.toList());

        Pair<Integer, Integer> otherCounts = getCounts(others, mostRecentMinTime);
        int otherMostCommonCount = otherCounts.getLeft();
        int otherMostRecentCount = otherCounts.getRight();

        return new CityData(
                new CityCountsList(mostCommonCounts, otherMostCommonCount),
                new CityCountsList(mostRecentCounts, otherMostRecentCount),
                mostRecentMinTime);
    }

    /**
     * Means of fetching points from geolocation.
     */
    private static class PointFetcher extends AbstractWaypointFetcher {

        private final BlockingQueue<List<MapWaypoint>> asyncResult;

        /**
         * Main constructor.
         *
         * @param asyncResult Geolocation fetches results in a callback which is
         * already handled by other mechanisms in data source summary. The
         * BlockingQueue blocks until a result is received from geolocation.
         * @param filters The applicable filters for geolocation.
         */
        public PointFetcher(BlockingQueue<List<MapWaypoint>> asyncResult, GeoFilter filters) {
            super(filters);
            this.asyncResult = asyncResult;
        }

        @Override
        public void handleFilteredWaypointSet(Set<MapWaypoint> mapWaypoints, List<Set<MapWaypoint>> tracks, List<Set<MapWaypoint>> areas, boolean wasEntirelySuccessful) {
            Stream<List<Set<MapWaypoint>>> stream = Stream.of(
                    Arrays.asList(mapWaypoints),
                    tracks == null ? Collections.emptyList() : tracks,
                    areas == null ? Collections.emptyList() : areas);

            List<MapWaypoint> wayPoints = stream
                    .flatMap((List<Set<MapWaypoint>> list) -> list.stream())
                    .flatMap((Set<MapWaypoint> set) -> set.stream())
                    .collect(Collectors.toList());

            // push to blocking queue to continue
            try {
                asyncResult.put(wayPoints);
            } catch (InterruptedException ignored) {
                // ignored cancellations
            }
        }
    }

    /**
     * Fetches all GPS data for the data source from the current case.
     *
     * @param dataSource The data source.
     * @return The GPS data pertaining to the data source.
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    private List<MapWaypoint> getPoints(DataSource dataSource) throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException {
        // make asynchronous callback synchronous (the callback nature will be handled in a different level)
        // see the following: https://stackoverflow.com/questions/20659961/java-synchronous-callback
        final BlockingQueue<List<MapWaypoint>> asyncResult = new ArrayBlockingQueue<>(1);

        GeoFilter geoFilter = new GeoFilter(true, false, 0, Arrays.asList(dataSource), GPS_ARTIFACT_TYPES);

        WaypointBuilder.getAllWaypoints(provider.get(),
                Arrays.asList(dataSource),
                GPS_ARTIFACT_TYPES,
                true,
                -1,
                false,
                new PointFetcher(asyncResult, geoFilter));

        return asyncResult.take();
    }
}
