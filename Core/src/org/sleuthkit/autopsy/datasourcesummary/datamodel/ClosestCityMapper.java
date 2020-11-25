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

    private static final String CITIES_CSV_FILENAME = "worldcities.csv";

    private static ClosestCityMapper instance = null;

    public static ClosestCityMapper getInstance() throws IOException {
        if (instance == null) {
            instance = new ClosestCityMapper();
        }

        return instance;
    }

    private LatLngMap<CityRecord> latLngMap = null;
    private final java.util.logging.Logger logger;

    private ClosestCityMapper() throws IOException {
        this(
                WhereUsedSummary.class.getResourceAsStream(CITIES_CSV_FILENAME),
                Logger.getLogger(WhereUsedSummary.class.getName()));
    }

    private ClosestCityMapper(InputStream citiesInputStream, java.util.logging.Logger logger) throws IOException {
        this.logger = logger;
        load(citiesInputStream);
    }

    CityRecord findClosest(CityRecord point) {
        return latLngMap.findClosest(point);
    }
    
    

    private void load(InputStream citiesCsv) throws IOException {
        latLngMap = new LatLngMap<CityRecord>(parseCsvLines(citiesCsv, true));
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
        }

        return cityRecords;
    }
    
    
}
