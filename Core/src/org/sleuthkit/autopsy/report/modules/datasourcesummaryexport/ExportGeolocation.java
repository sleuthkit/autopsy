/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityCountsList;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityData;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityRecordCount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.CityRecord;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getFetchResult;
import static org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExportAction.getTableExport;
import org.sleuthkit.datamodel.DataSource;

/**
 * Class to export information about a data source's geolocation data.
 */
@Messages({
    "ExportGeolocation_cityColumn_title=Closest City",
    "ExportGeolocation_countColumn_title=Count",
    "ExportGeolocation_unknownRow_title=Unknown",
    "ExportGeolocation_mostCommon_tabName=Most Common Cities",
    "ExportGeolocation_mostRecent_tabName=Most Recent Cities",})
class ExportGeolocation {
    
    private final GeolocationSummary geoSummary;

    /**
     * Object encapsulating geolocation data.
     */
    private static class GeolocationData {

        private final List<Pair<String, Integer>> mostRecentData;
        private final List<Pair<String, Integer>> mostCommonData;

        /**
         * Main constructor.
         *
         * @param mostRecentData The data to be displayed in the most recent
         *                       tab.
         * @param mostCommonData The data to be displayed in the most common
         *                       tab.
         */
        GeolocationData(List<Pair<String, Integer>> mostRecentData, List<Pair<String, Integer>> mostCommonData) {
            this.mostRecentData = mostRecentData;
            this.mostCommonData = mostCommonData;
        }

        /**
         * Returns the data to be displayed in the most recent tab.
         *
         * @return The data to be displayed in the most recent tab.
         */
        List<Pair<String, Integer>> getMostRecentData() {
            return mostRecentData;
        }

        /**
         * Returns the data to be displayed in the most common tab.
         *
         * @return The data to be displayed in the most common tab.
         */
        List<Pair<String, Integer>> getMostCommonData() {
            return mostCommonData;
        }
    }

    private static final int DAYS_COUNT = 30;
    private static final int MAX_COUNT = 10;

    // The column indicating the city
    private static final ColumnModel<Pair<String, Integer>, DefaultCellModel<?>> CITY_COL = new ColumnModel<>(
            Bundle.ExportGeolocation_cityColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getLeft()),
            300
    );

    // The column indicating the count of points seen close to that city
    private static final ColumnModel<Pair<String, Integer>, DefaultCellModel<?>> COUNT_COL = new ColumnModel<>(
            Bundle.ExportGeolocation_countColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getRight()),
            100
    );

    private static final List<ColumnModel<Pair<String, Integer>, DefaultCellModel<?>>> DEFAULT_TEMPLATE = Arrays.asList(
            CITY_COL,
            COUNT_COL
    );

    ExportGeolocation() {
        geoSummary = new GeolocationSummary();
    }

    /**
     * Retrieves the city name to display from the record.
     *
     * @param record The record for the city to display.
     *
     * @return The display name (city, country).
     */
    private static String getCityName(CityRecord record) {
        if (record == null) {
            return null;
        }

        List<String> cityIdentifiers = Stream.of(record.getCityName(), record.getState(), record.getCountry())
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());

        if (cityIdentifiers.size() == 1) {
            return cityIdentifiers.get(0);
        } else if (cityIdentifiers.size() == 2) {
            return String.format("%s, %s", cityIdentifiers.get(0), cityIdentifiers.get(1));
        } else if (cityIdentifiers.size() >= 3) {
            return String.format("%s, %s; %s", cityIdentifiers.get(0), cityIdentifiers.get(1), cityIdentifiers.get(2));
        }

        return null;
    }

    /**
     * Formats one record to be displayed as a row in the tab (specifically,
     * formats the city name).
     *
     * @param cityCount The CityRecordCount representing a row.
     *
     * @return The city/count pair to be displayed as a row.
     */
    private static Pair<String, Integer> formatRecord(CityRecordCount cityCount) {
        if (cityCount == null) {
            return null;
        }

        String cityName = getCityName(cityCount.getCityRecord());
        int count = cityCount.getCount();
        return Pair.of(cityName, count);
    }

    /**
     * Formats a list of records to be displayed in a tab (specifically,
     * includes the count of points where no closest city could be determined as
     * 'unknown').
     *
     * @param countsList The CityCountsList object representing the data to be
     *                   displayed in the tab.
     *
     * @return The list of city/count tuples to be displayed as a row.
     */
    private static List<Pair<String, Integer>> formatList(CityCountsList countsList) {
        if (countsList == null) {
            return Collections.emptyList();
        }

        Stream<CityRecordCount> countsStream = ((countsList.getCounts() == null)
                ? new ArrayList<CityRecordCount>()
                : countsList.getCounts()).stream();

        Stream<Pair<String, Integer>> pairStream = countsStream.map((r) -> formatRecord(r));

        Pair<String, Integer> unknownRecord = Pair.of(Bundle.ExportGeolocation_unknownRow_title(), countsList.getOtherCount());

        return Stream.concat(pairStream, Stream.of(unknownRecord))
                .filter((p) -> p != null && p.getRight() != null && p.getRight() > 0)
                .sorted((a, b) -> -Integer.compare(a.getRight(), b.getRight()))
                .limit(MAX_COUNT)
                .collect(Collectors.toList());
    }

    /**
     * Converts CityData from GeolocationSummaryGetter into data that can be
     * directly put into tab in this panel.
     *
     * @param cityData The city data.
     *
     * @return The geolocation data.
     */
    private static GeolocationData convertToViewModel(CityData cityData) {
        if (cityData == null) {
            return new GeolocationData(Collections.emptyList(), Collections.emptyList());
        } else {
            return new GeolocationData(formatList(cityData.getMostRecent()), formatList(cityData.getMostCommon()));
        }
    }

    List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {

        DataFetcher<DataSource, GeolocationData> geolocationFetcher = (ds) -> convertToViewModel(geoSummary.getCityCounts(ds, DAYS_COUNT, MAX_COUNT));

        GeolocationData model
                = getFetchResult(geolocationFetcher, "Geolocation sheets", dataSource);
        if (model == null) {
            return Collections.emptyList();
        }

        return Arrays.asList(getTableExport(DEFAULT_TEMPLATE,
                Bundle.ExportGeolocation_mostRecent_tabName(), model.getMostRecentData()),
                getTableExport(DEFAULT_TEMPLATE,
                        Bundle.ExportGeolocation_mostCommon_tabName(), model.getMostCommonData())
        );
    }

}
