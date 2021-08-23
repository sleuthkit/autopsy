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
package org.sleuthkit.autopsy.datasourcesummary.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JButton;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityCountsList;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityData;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityRecordCount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.CityRecord;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.geolocation.GeoFilter;
import org.sleuthkit.autopsy.geolocation.GeoLocationUIException;
import org.sleuthkit.autopsy.geolocation.GeolocationTopComponent;
import org.sleuthkit.autopsy.geolocation.OpenGeolocationAction;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying information about a data
 * source's geolocation data.
 */
@Messages({
    "GeolocationPanel_cityColumn_title=Closest City",
    "GeolocationPanel_countColumn_title=Count",
    "GeolocationPanel_onNoCrIngest_message=No results will be shown because the GPX Parser was not run.",
    "GeolocationPanel_unknownRow_title=Unknown",
    "GeolocationPanel_mostCommon_tabName=Most Common Cities",
    "GeolocationPanel_mostRecent_tabName=Most Recent Cities",})
public class GeolocationPanel extends BaseDataSourceSummaryPanel {

    /**
     * Object encapsulating the view model of this panel.
     */
    private static class GeolocationViewModel {

        private final List<Pair<String, Integer>> mostRecentData;
        private final List<Pair<String, Integer>> mostCommonData;

        /**
         * Main constructor.
         *
         * @param mostRecentData The data to be displayed in the most recent
         *                       table.
         * @param mostCommonData The data to be displayed in the most common
         *                       table.
         */
        GeolocationViewModel(List<Pair<String, Integer>> mostRecentData, List<Pair<String, Integer>> mostCommonData) {
            this.mostRecentData = mostRecentData;
            this.mostCommonData = mostCommonData;
        }

        /**
         * Returns the data to be displayed in the most recent table.
         *
         * @return The data to be displayed in the most recent table.
         */
        List<Pair<String, Integer>> getMostRecentData() {
            return mostRecentData;
        }

        /**
         * Returns the data to be displayed in the most common table.
         *
         * @return The data to be displayed in the most common table.
         */
        List<Pair<String, Integer>> getMostCommonData() {
            return mostCommonData;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final int DAYS_COUNT = 30;
    private static final int MAX_COUNT = 10;

    // The column indicating the city
    private static final ColumnModel<Pair<String, Integer>, DefaultCellModel<?>> CITY_COL = new ColumnModel<>(
            Bundle.GeolocationPanel_cityColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getLeft()),
            300
    );

    // The column indicating the count of points seen close to that city
    private static final ColumnModel<Pair<String, Integer>, DefaultCellModel<?>> COUNT_COL = new ColumnModel<>(
            Bundle.GeolocationPanel_countColumn_title(),
            (pair) -> new DefaultCellModel<>(pair.getRight()),
            100
    );

    private static final List<ColumnModel<Pair<String, Integer>, DefaultCellModel<?>>> DEFAULT_TEMPLATE = Arrays.asList(
            CITY_COL,
            COUNT_COL
    );

    // tables displaying city and number of hits for that city
    private final JTablePanel<Pair<String, Integer>> mostCommonTable = JTablePanel.getJTablePanel(DEFAULT_TEMPLATE)
            .setKeyFunction((pair) -> pair.getLeft());

    private final JTablePanel<Pair<String, Integer>> mostRecentTable = JTablePanel.getJTablePanel(DEFAULT_TEMPLATE)
            .setKeyFunction((pair) -> pair.getLeft());

    // loadable components on this tab
    private final List<JTablePanel<?>> tables = Arrays.asList(mostCommonTable, mostRecentTable);

    private final Logger logger = Logger.getLogger(GeolocationPanel.class.getName());

    // means of fetching and displaying data
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    private final GeolocationSummaryGetter whereUsedData;

    private final DataFetcher<DataSource, GeolocationViewModel> geolocationFetcher;

    /**
     * Main constructor.
     */
    public GeolocationPanel() {
        this(new GeolocationSummaryGetter());
    }

    /**
     * Main constructor.
     *
     * @param whereUsedData The GeolocationSummaryGetter instance to use.
     */
    public GeolocationPanel(GeolocationSummaryGetter whereUsedData) {
        super(whereUsedData);

        this.whereUsedData = whereUsedData;

        this.geolocationFetcher = (dataSource) -> convertToViewModel(whereUsedData.getCityCounts(dataSource, DAYS_COUNT, MAX_COUNT));

        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(
                        geolocationFetcher,
                        (result) -> handleData(result)));

        initComponents();
    }

    /**
     * Means of rendering data to be shown in the tables.
     *
     * @param result The result of fetching data for a data source and
     *               processing into view model data.
     */
    private void handleData(DataFetchResult<GeolocationViewModel> result) {
        showCityContent(DataFetchResult.getSubResult(result, (dr) -> dr.getMostCommonData()), mostCommonTable, commonViewInGeolocationBtn);
        showCityContent(DataFetchResult.getSubResult(result, (dr) -> dr.getMostRecentData()), mostRecentTable, recentViewInGeolocationBtn);
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
     * Formats one record to be displayed as a row in the table (specifically,
     * formats the city name).
     *
     * @param cityCount The CityRecordCount representing a row.
     *
     * @return The city/count pair to be displayed as a row.
     */
    private Pair<String, Integer> formatRecord(CityRecordCount cityCount) {
        if (cityCount == null) {
            return null;
        }

        String cityName = getCityName(cityCount.getCityRecord());
        int count = cityCount.getCount();
        return Pair.of(cityName, count);
    }

    /**
     * Formats a list of records to be displayed in a table (specifically,
     * includes the count of points where no closest city could be determined as
     * 'unknown').
     *
     * @param countsList The CityCountsList object representing the data to be
     *                   displayed in the table.
     *
     * @return The list of city/count tuples to be displayed as a row.
     */
    private List<Pair<String, Integer>> formatList(CityCountsList countsList) {
        if (countsList == null) {
            return Collections.emptyList();
        }

        Stream<CityRecordCount> countsStream = ((countsList.getCounts() == null)
                ? new ArrayList<CityRecordCount>()
                : countsList.getCounts()).stream();

        Stream<Pair<String, Integer>> pairStream = countsStream.map((r) -> formatRecord(r));

        Pair<String, Integer> unknownRecord = Pair.of(Bundle.GeolocationPanel_unknownRow_title(), countsList.getOtherCount());

        return Stream.concat(pairStream, Stream.of(unknownRecord))
                .filter((p) -> p != null && p.getRight() != null && p.getRight() > 0)
                .sorted((a, b) -> -Integer.compare(a.getRight(), b.getRight()))
                .limit(MAX_COUNT)
                .collect(Collectors.toList());
    }

    /**
     * Converts CityData from GeolocationSummaryGetter into data that can be
     * directly put into table in this panel.
     *
     * @param cityData The city data.
     *
     * @return The view model data.
     */
    private GeolocationViewModel convertToViewModel(CityData cityData) {
        if (cityData == null) {
            return new GeolocationViewModel(Collections.emptyList(), Collections.emptyList());
        } else {
            return new GeolocationViewModel(formatList(cityData.getMostRecent()), formatList(cityData.getMostCommon()));
        }
    }

    /**
     * Shows data in a particular table.
     *
     * @param result          The result to be displayed in the table.
     * @param table           The table where the data will be displayed.
     * @param goToGeolocation The corresponding geolocation navigation button.
     */
    private void showCityContent(DataFetchResult<List<Pair<String, Integer>>> result, JTablePanel<Pair<String, Integer>> table, JButton goToGeolocation) {
        if (result != null && result.getResultType() == DataFetchResult.ResultType.SUCCESS && CollectionUtils.isNotEmpty(result.getData())) {
            goToGeolocation.setEnabled(true);
        }

        table.showDataFetchResult(result);
    }

    /**
     * Action to open the geolocation window.
     *
     * @param dataSource The data source for which the window should filter.
     * @param daysLimit  The limit for how recently the waypoints should be (for
     *                   most recent table) or null for most recent filter to
     *                   not be set (for most common table).
     */
    private void openGeolocationWindow(DataSource dataSource, Integer daysLimit) {
        // notify dialog (if in dialog) should close.
        notifyParentClose();

        // set the filter
        TopComponent topComponent = WindowManager.getDefault().findTopComponent(GeolocationTopComponent.class.getSimpleName());
        if (topComponent instanceof GeolocationTopComponent) {
            GeolocationTopComponent geoComponent = (GeolocationTopComponent) topComponent;

            GeoFilter filter = (daysLimit == null)
                    ? new GeoFilter(true, false, 0, Arrays.asList(dataSource), whereUsedData.getGeoTypes())
                    : new GeoFilter(false, false, DAYS_COUNT, Arrays.asList(dataSource), whereUsedData.getGeoTypes());

            try {
                geoComponent.setFilterState(filter);
            } catch (GeoLocationUIException ex) {
                logger.log(Level.WARNING, "There was an error setting filters in the GeoLocationTopComponent.", ex);
            }
        }

        // open the window
        OpenGeolocationAction action = CallableSystemAction.get(OpenGeolocationAction.class);
        if (action == null) {
            logger.log(Level.WARNING, "Unable to obtain an OpenGeolocationAction instance from CallableSystemAction.");
        } else {
            action.performAction();
        }
    }

    /**
     * Disables navigation buttons.
     */
    private void disableNavButtons() {
        commonViewInGeolocationBtn.setEnabled(false);
        recentViewInGeolocationBtn.setEnabled(false);
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        disableNavButtons();
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        disableNavButtons();
        onNewDataSource(dataFetchComponents, tables, dataSource);
    }

    @Override
    public void close() {
        ingestRunningLabel.unregister();
        super.close();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane mainScrollPane = new javax.swing.JScrollPane();
        javax.swing.JPanel mainContentPanel = new javax.swing.JPanel();
        javax.swing.JPanel ingestRunningPanel = ingestRunningLabel;
        javax.swing.JLabel mostRecentLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel mostRecentPanel = mostRecentTable;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JLabel withinDistanceLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5));
        recentViewInGeolocationBtn = new javax.swing.JButton();
        javax.swing.Box.Filler filler8 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.JLabel mostCommonLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler7 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel mostCommonPanel = mostCommonTable;
        javax.swing.Box.Filler filler6 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JLabel withinDistanceLabel1 = new javax.swing.JLabel();
        javax.swing.Box.Filler filler4 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5));
        commonViewInGeolocationBtn = new javax.swing.JButton();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        org.openide.awt.Mnemonics.setLocalizedText(mostRecentLabel, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.mostRecentLabel.text")); // NOI18N
        mainContentPanel.add(mostRecentLabel);
        mostRecentLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        filler1.setAlignmentX(0.0F);
        mainContentPanel.add(filler1);

        mostRecentPanel.setAlignmentX(0.0F);
        mostRecentPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        mostRecentPanel.setMinimumSize(new java.awt.Dimension(100, 106));
        mostRecentPanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(mostRecentPanel);

        filler2.setAlignmentX(0.0F);
        mainContentPanel.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(withinDistanceLabel, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.withinDistanceLabel.text")); // NOI18N
        mainContentPanel.add(withinDistanceLabel);

        filler3.setAlignmentX(0.0F);
        mainContentPanel.add(filler3);

        org.openide.awt.Mnemonics.setLocalizedText(recentViewInGeolocationBtn, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.recentViewInGeolocationBtn.text")); // NOI18N
        recentViewInGeolocationBtn.setEnabled(false);
        recentViewInGeolocationBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                recentViewInGeolocationBtnActionPerformed(evt);
            }
        });
        mainContentPanel.add(recentViewInGeolocationBtn);

        filler8.setAlignmentX(0.0F);
        mainContentPanel.add(filler8);

        org.openide.awt.Mnemonics.setLocalizedText(mostCommonLabel, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.mostCommonLabel.text")); // NOI18N
        mainContentPanel.add(mostCommonLabel);

        filler7.setAlignmentX(0.0F);
        mainContentPanel.add(filler7);

        mostCommonPanel.setAlignmentX(0.0F);
        mostCommonPanel.setMaximumSize(new java.awt.Dimension(32767, 106));
        mostCommonPanel.setMinimumSize(new java.awt.Dimension(100, 106));
        mostCommonPanel.setPreferredSize(new java.awt.Dimension(100, 106));
        mainContentPanel.add(mostCommonPanel);

        filler6.setAlignmentX(0.0F);
        mainContentPanel.add(filler6);

        org.openide.awt.Mnemonics.setLocalizedText(withinDistanceLabel1, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.withinDistanceLabel1.text")); // NOI18N
        mainContentPanel.add(withinDistanceLabel1);

        filler4.setAlignmentX(0.0F);
        mainContentPanel.add(filler4);

        org.openide.awt.Mnemonics.setLocalizedText(commonViewInGeolocationBtn, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.commonViewInGeolocationBtn.text")); // NOI18N
        commonViewInGeolocationBtn.setEnabled(false);
        commonViewInGeolocationBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                commonViewInGeolocationBtnActionPerformed(evt);
            }
        });
        mainContentPanel.add(commonViewInGeolocationBtn);

        filler5.setAlignmentX(0.0F);
        mainContentPanel.add(filler5);

        mainScrollPane.setViewportView(mainContentPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 746, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void recentViewInGeolocationBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_recentViewInGeolocationBtnActionPerformed
        openGeolocationWindow(getDataSource(), DAYS_COUNT);
    }//GEN-LAST:event_recentViewInGeolocationBtnActionPerformed

    private void commonViewInGeolocationBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_commonViewInGeolocationBtnActionPerformed
        openGeolocationWindow(getDataSource(), null);
    }//GEN-LAST:event_commonViewInGeolocationBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton commonViewInGeolocationBtn;
    private javax.swing.JButton recentViewInGeolocationBtn;
    // End of variables declaration//GEN-END:variables
}
