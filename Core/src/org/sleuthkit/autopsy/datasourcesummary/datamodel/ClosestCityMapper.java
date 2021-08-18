/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020-2021 Basis Technology Corp.
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
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Class that provides the closest city within a tolerance to a provided point.
 * This class relies on worldcities.csv and loads the file on first use.
 */
class ClosestCityMapper {

    // class resource for cities lat/lng taken from https://simplemaps.com/data/world-cities
    private static final String CITIES_CSV_FILENAME = "worldcities.csv";

    // index within a csv row of pertinent data
    private static final int CITY_NAME_IDX = 0;
    private static final int STATE_NAME_IDX = 7;
    private static final int COUNTRY_NAME_IDX = 4;
    private static final int LAT_IDX = 2;
    private static final int LONG_IDX = 3;

    // regex for parsing csv value from a row.  This assumes values are in quotes and no escape sequence is used.  Also performs a trim.
    private static final Pattern CSV_NAIVE_REGEX = Pattern.compile("\"\\s*(([^\"]+?)?)\\s*\"");

    // Identifies if cities are in last, first format like "Korea, South"
    private static final Pattern COUNTRY_WITH_COMMA = Pattern.compile("^\\s*([^,]*)\\s*,\\s*([^,]*)\\s*$");

    private static final int MAX_IDX = Stream.of(CITY_NAME_IDX, STATE_NAME_IDX, COUNTRY_NAME_IDX, LAT_IDX, LONG_IDX)
            .max(Integer::compare)
            .get();

    // singleton instance of this class
    private static ClosestCityMapper instance = null;

    /**
     * Retrieves singleton instance of this class.
     *
     * @return The singleton instance of this class.
     *
     * @throws IOException
     */
    static ClosestCityMapper getInstance() throws IOException {
        if (instance == null) {
            instance = new ClosestCityMapper();
        }

        return instance;
    }

    // data structure housing cities
    private LatLngMap<CityRecord> latLngMap = null;

    // the logger
    private final java.util.logging.Logger logger;

    /**
     * Main Constructor.
     *
     * @throws IOException
     */
    private ClosestCityMapper() throws IOException {
        this(
                GeolocationSummary.class.getResourceAsStream(CITIES_CSV_FILENAME),
                Logger.getLogger(ClosestCityMapper.class.getName()));
    }

    /**
     * Main Constructor loading from an input stream.
     *
     * @param citiesInputStream The input stream for the csv text file
     *                          containing the cities.
     * @param logger            The logger to be used with this.
     *
     * @throws IOException
     */
    private ClosestCityMapper(InputStream citiesInputStream, java.util.logging.Logger logger) throws IOException {
        this.logger = logger;
        latLngMap = new LatLngMap<CityRecord>(parseCsvLines(citiesInputStream, true));
    }

    /**
     * Finds the closest city to the given point. Null is returned if no close
     * city can be determined.
     *
     * @param point The point to locate.
     *
     * @return The closest city or null if no close city can be found.
     */
    CityRecord findClosest(CityRecord point) {
        return latLngMap.findClosest(point);
    }

    /**
     * Tries to parse a string to a double. If can't be parsed, null is
     * returned.
     *
     * @param s The string to parse.
     *
     * @return The double value or null if value cannot be parsed.
     */
    private Double tryParse(String s) {
        if (s == null) {
            return null;
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Parses a country name and transforms values like "last, first" to "first
     * last" (i.e. "Korea, South" becomes "South Korea").
     *
     * @param orig    The original string value.
     * @param lineNum The line number that this country was found.
     *
     * @return The country name.
     */
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

    /**
     * Parses a row from the csv creating a city record.
     *
     * @param csvRow  The row of data where each item in the list is each column
     *                in the row.
     * @param lineNum The line number for this csv row.
     *
     * @return The parsed CityRecord or null if none can be determined.
     */
    private CityRecord getCsvCityRecord(List<String> csvRow, int lineNum) {
        if (csvRow == null || csvRow.size() <= MAX_IDX) {
            logger.log(Level.WARNING, String.format("Row at line number %d is required to have at least %d elements and does not.", lineNum, (MAX_IDX + 1)));
            return null;
        }

        // city is required
        String cityName = csvRow.get(CITY_NAME_IDX);
        if (StringUtils.isBlank(cityName)) {
            logger.log(Level.WARNING, String.format("No city name determined for line %d.", lineNum));
            return null;
        }

        // state and country can be optional
        String stateName = csvRow.get(STATE_NAME_IDX);
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

        return new CityRecord(cityName, stateName, countryName, lattitude, longitude);
    }

    /**
     * Parses a row of the csv into individual column values.
     *
     * @param line    The line to parse.
     * @param lineNum The line number in the csv where this line is.
     *
     * @return The list of column values.
     */
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

    /**
     * Parses all lines in the csv file input stream into a list of city
     * records.
     *
     * @param csvInputStream  The csv file input stream.
     * @param ignoreHeaderRow Whether or not there is a header row in the csv
     *                        file.
     *
     * @return The list of city records.
     *
     * @throws IOException
     */
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
        }

        return cityRecords;
    }

}
