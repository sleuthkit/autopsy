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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
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
@Messages({
    "GeolocationSummary_cities_noRecordFound=Other"
})
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
    public static class CityRecord extends XYZPoint {

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
    private LatLngMap<CityRecord> latLngMap = null;
    private final java.util.logging.Logger logger;

    /**
     * Main constructor.
     */
    private GeolocationSummary() {
        this(SleuthkitCaseProvider.DEFAULT, Logger.getLogger(GeolocationSummary.class.getName()));
    }

    /**
     * Main constructor.
     *
     * @param provider The means of obtaining a sleuthkit case.
     */
    public GeolocationSummary(SleuthkitCaseProvider provider, java.util.logging.Logger logger) {
        this.provider = provider;
        this.logger = logger;
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
        latLngMap = new LatLngMap<CityRecord>(parseCsvLines(GeolocationSummary.class.getResourceAsStream("worldcities.csv"), true));
    }
    
    private static CityRecord OTHER_RECORD = new CityRecord(Bundle.GeolocationSummary_cities_noRecordFound(), "", 0, 0);

    /**
     * Determines closest city to each waypoint and returns a map of the city to
     * the number of hits closest to that city.
     *
     * @param waypoints The waypoints.
     * @return A map of city to the number of hits.
     */
    private Map<CityRecord, Integer> getCounts(List<Waypoint> waypoints) {
        Map<CityRecord, Integer> toRet = waypoints.stream()
                .map((point) -> latLngMap.findClosest(new CityRecord(null, null, point.getLatitude(), point.getLongitude())))
                .collect(Collectors.toMap(city -> city == null ? OTHER_RECORD : city, city -> 1, (count1, count2) -> count1 + count2));

        return toRet;
    }
    
    
    
    private static final int CITY_NAME_IDX = 0;
    private static final int COUNTRY_NAME_IDX = 4;
    private static final int LAT_IDX = 2;
    private static final int LONG_IDX = 3;

    private static final int MAX_IDX = Stream.of(CITY_NAME_IDX, COUNTRY_NAME_IDX, LAT_IDX, LONG_IDX)
            .max(Integer::compare)
            .get();

    private static Double tryParse(String s) {
        if (s == null) {
            return null;
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String parseCountryName(String orig, int lineNum) {
        if (StringUtils.isBlank(orig)) {
            logger.log(Level.WARNING, String.format("No country name determined for line %d.", lineNum));
            return null;
        }

        Matcher m = COUNTRY_WITH_COMMA.matcher(orig);
        if (m.find()) {
            return String.format("%s %s", m.group(1), m.group(2));
        }

        return orig;
    }

    private CityRecord getCsvCityRecord(List<String> csvRow, int lineNum) {
        if (csvRow == null || csvRow.size() <= MAX_IDX) {
            logger.log(Level.WARNING, String.format("Row at line number %d is required to have at least %d elements and does not.", lineNum, (MAX_IDX + 1)));
        }

        String cityName = csvRow.get(CITY_NAME_IDX);
        if (StringUtils.isBlank(cityName)) {
            logger.log(Level.WARNING, String.format("No city name determined for line %d.", lineNum));
            return null;
        }

        String countryName = parseCountryName(csvRow.get(COUNTRY_NAME_IDX), lineNum);

        Double lattitude = tryParse(csvRow.get(LAT_IDX));
        if (lattitude == null) {
            logger.log(Level.WARNING, String.format("No lattitude determined for line %d.", lineNum));
            return null;
        }

        Double longitude = tryParse(csvRow.get(LONG_IDX));
        if (longitude == null) {
            logger.log(Level.WARNING, String.format("No longitude determined for line %d.", lineNum));
            return null;
        }

        return new CityRecord(cityName, countryName, lattitude, longitude);
    }
    
    private static Pattern CSV_NAIVE_REGEX = Pattern.compile("\"\\s*(([^\"]+?)?)\\s*\"");
    private static Pattern COUNTRY_WITH_COMMA = Pattern.compile("^\\s*([^,]*)\\s*,\\s*([^,]*)\\s*$");

    private List<String> parseCsvLine(String line, int lineNum) {
        if (line == null || line.length() <= 0) {
            logger.log(Level.INFO, String.format("Line at %d had no content", lineNum));
            return null;
        }

        List<String> allMatches = new ArrayList<String>();
        Matcher m = CSV_NAIVE_REGEX.matcher(line);
        while (m.find()) {
            allMatches.add(m.group(1));
        }

        return allMatches;
    }

    private List<CityRecord> parseCsvLines(InputStream csvInputStream, boolean ignoreHeaderRow) throws IOException {
        List<CityRecord> cityRecords = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream, "UTF-8"))) {
            int lineNum = 1;
            String line = reader.readLine();

            if (line != null && ignoreHeaderRow) {
                line = reader.readLine();
                lineNum++;
            }

            while (line != null) {
                // read next line
                List<String> rowElements = parseCsvLine(line, lineNum);

                if (rowElements != null) {
                    cityRecords.add(getCsvCityRecord(rowElements, lineNum));
                }

                line = reader.readLine();
                lineNum++;
            }
            reader.close();
        }
        
        return cityRecords;
    }
}
