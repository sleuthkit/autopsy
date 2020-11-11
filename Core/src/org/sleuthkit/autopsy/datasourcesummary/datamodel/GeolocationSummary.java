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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.stream.Collectors;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultArtifactUpdateGovernor;
import org.sleuthkit.autopsy.geolocation.KdTree.XYZPoint;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationParseResult;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;
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
    public class CityCount {

        private final CityRecord cityRecord;
        private final int count;

        /**
         * Main constructor.
         *
         * @param cityRecord The record for the city including name, country,
         * and location.
         * @param count The number of hits in proximity to that city.
         */
        CityCount(CityRecord cityRecord, int count) {
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
     * A record for a particular city including country and location.
     */
    public class CityRecord extends XYZPoint {

        private final String cityName;
        private final String country;

        /**
         * Main constructor.
         *
         * @param cityName The name of the city.
         * @param country The country of that city.
         * @param latitude Latitude for the city.
         * @param longitude Longitude for the city.
         */
        CityRecord(String cityName, String country, double latitude, double longitude) {
            super(latitude, longitude);
            this.cityName = cityName;
            this.country = country;
        }

        /**
         * @return The name of the city.
         */
        public String getCityName() {
            return cityName;
        }

        /**
         * @return The country of that city.
         */
        public String getCountry() {
            return country;
        }

        /**
         * @return Latitude for the city.
         */
        public double getLatitude() {
            return getX();
        }

        /**
         * @return Longitude for the city.
         */
        public double getLongitude() {
            return getY();
        }

        @Override
        public int hashCode() {
            int hash = super.hashCode();
            hash = 37 * hash + Objects.hashCode(this.cityName);
            hash = 37 * hash + Objects.hashCode(this.country);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CityRecord other = (CityRecord) obj;
            if (!Objects.equals(this.cityName, other.cityName)) {
                return false;
            }
            if (!Objects.equals(this.country, other.country)) {
                return false;
            }

            return super.equals(obj);
        }

        @Override
        public String toString() {
            return "CityRecord{" + "cityName=" + cityName + ", country=" + country + ", lat=" + getX() + ", lng=" + getY() + '}';
        }
    }

    // taken from GeoFilterPanel: all of the GPS artifact types.
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

    private static GeolocationSummary instance = null;

    /**
     * @return The singleton instance of this class.
     */
    public static GeolocationSummary getInstance() {
        if (instance == null) {
            instance = new GeolocationSummary();
        }

        return instance;
    }

    private final SleuthkitCaseProvider provider;

    /**
     * Main constructor.
     */
    private GeolocationSummary() {
        this(SleuthkitCaseProvider.DEFAULT);
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public GeolocationSummary(SleuthkitCaseProvider provider) {
        this.provider = provider;
    }

    @Override
    public Set<Integer> getArtifactTypeIdsForRefresh() {
        return GPS_ARTIFACT_TYPE_IDS;
    }

    /**
     * Get this list of hits per city where the list is sorted descending by
     * number of found hits (i.e. most hits is first index).
     *
     * @param dataSource The data source.
     * @return The sorted list.
     * @throws SleuthkitCaseProviderException
     * @throws GeoLocationDataException
     * @throws InterruptedException
     */
    public List<CityCount> getCityCounts(DataSource dataSource) throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException {
        List<Waypoint> dataSourcePoints = getPoints(dataSource);
        Map<CityRecord, Integer> cityCounts = getCounts(dataSourcePoints);

        return cityCounts.entrySet().stream()
                .map(e -> new CityCount(e.getKey(), e.getValue()))
                .sorted((cityCount1, cityCount2) -> -Integer.compare(cityCount1.getCount(), cityCount2.getCount()))
                .collect(Collectors.toList());
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
    private List<Waypoint> getPoints(DataSource dataSource) throws SleuthkitCaseProviderException, GeoLocationDataException, InterruptedException {
        // make asynchronous callback synchronous (the callback nature will be handled in a different level)
        // see the following: https://stackoverflow.com/questions/20659961/java-synchronous-callback
        final BlockingQueue<GeoLocationParseResult<Waypoint>> asyncResult = new SynchronousQueue<>();

        final WaypointBuilder.WaypointFilterQueryCallBack callback = (result) -> {
            try {
                asyncResult.put(result);
            } catch (InterruptedException ignored) {
                // ignored cancellations
            }
        };

        WaypointBuilder.getAllWaypoints(provider.get(),
                Arrays.asList(dataSource),
                GPS_ARTIFACT_TYPES,
                true,
                -1,
                false,
                callback);

        GeoLocationParseResult<Waypoint> result;

        result = asyncResult.take();

        if (result.isSuccessfullyParsed()) {
            return result.getItems();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Pre-loads city data.
     */
    public void load() throws IOException {
        // TODO
        throw new UnsupportedOperationException();
    }

    /**
     * Determines closest city to each waypoint and returns a map of the city to
     * the number of hits closest to that city.
     *
     * @param waypoints The waypoints.
     * @return A map of city to the number of hits.
     */
    private Map<CityRecord, Integer> getCounts(List<Waypoint> waypoints) {
        // TODO
        throw new UnsupportedOperationException();
    }
}
