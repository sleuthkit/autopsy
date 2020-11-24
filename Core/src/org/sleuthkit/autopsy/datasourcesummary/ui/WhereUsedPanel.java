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

import java.util.Arrays;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.WhereUsedSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.WhereUsedSummary.CityRecordCount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.WhereUsedSummary.CityRecord;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.autopsy.geolocation.GeoFilter;
import org.sleuthkit.autopsy.geolocation.GeolocationTopComponent;
import org.sleuthkit.autopsy.geolocation.OpenGeolocationAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying information about a data
 * source's geolocation data.
 */
@Messages({
    "WhereUsedPanel_cityColumn_title=Closest City",
    "WhereUsedPanel_countColumn_title=Count",
    "WhereUsedPanel_onNoCrIngest_message=No results will be shown because the GPX Parser was not run."
})
public class WhereUsedPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final String GPX_FACTORY = "org.python.proxies.GPX_Parser_Module$GPXParserFileIngestModuleFactory";
    private static final String GPX_NAME = "GPX Parser";

    /**
     * Retrieves the city name to display from the record.
     *
     * @param record The record for the city to display.
     * @return The display name (city, country).
     */
    private static String getCityName(CityRecord record) {
        if (record == null) {
            return null;
        }

        if (StringUtils.isBlank(record.getCountry())) {
            return record.getCityName();
        }

        return String.format("%s, %s", record.getCityName(), record.getCountry());
    }

    private static final ColumnModel<CityRecordCount> CITY_COL = new ColumnModel<>(
            Bundle.WhereUsedPanel_cityColumn_title(),
            (cityCount) -> new DefaultCellModel(getCityName(cityCount.getCityRecord())),
            300
    );

    private static final ColumnModel<CityRecordCount> COUNT_COL = new ColumnModel<>(
            Bundle.WhereUsedPanel_countColumn_title(),
            (cityCount) -> new DefaultCellModel(Integer.toString(cityCount.getCount())),
            100
    );

    // table displaying city and number of hits for that city
    private final JTablePanel<CityRecordCount> cityCountsTable = JTablePanel.getJTablePanel(Arrays.asList(CITY_COL, COUNT_COL))
            .setKeyFunction((cityCount) -> cityCount.getCityRecord());

    // loadable components on this tab
    private final List<JTablePanel<?>> tables = Arrays.asList(
            cityCountsTable
    );

    // means of fetching and displaying data
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    private final WhereUsedSummary whereUsedData;
    
    /**
     * Main constructor.
     */
    public WhereUsedPanel() {
        this(WhereUsedSummary.getInstance());
    }

    /**
     * Main constructor.
     *
     * @param whereUsedData The GeolocationSummary instance to use.
     */
    public WhereUsedPanel(WhereUsedSummary whereUsedData) {
        this.whereUsedData = whereUsedData;
        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> whereUsedData.getCityCounts(dataSource),
                        (result) -> handleData(result)));

        initComponents();
    }

    private void handleData(DataFetchResult<List<CityRecordCount>> result) {
        if (result != null && result.getResultType() == DataFetchResult.ResultType.SUCCESS && CollectionUtils.isNotEmpty(result.getData())) {
            viewInGeolocationBtn.setEnabled(true);
        }

        showResultWithModuleCheck(cityCountsTable, result, GPX_FACTORY, GPX_NAME);
    }

    private void openGeolocationWindow(DataSource dataSource) {
        // open the window
        OpenGeolocationAction geoAction = CallableSystemAction.get(OpenGeolocationAction.class);
        if (geoAction != null) {
            geoAction.performAction();
        }
        
        // set the filter
        TopComponent topComponent = WindowManager.getDefault().findTopComponent(GeolocationTopComponent.class.getSimpleName());
        if (topComponent instanceof GeolocationTopComponent) {
            GeolocationTopComponent geoComponent = (GeolocationTopComponent) topComponent;
            geoComponent.fetchAndShowWaypoints(new GeoFilter(true, false, 0, Arrays.asList(dataSource), whereUsedData.getGeoTypes()));
        }
    }
    
    @Override
    protected void fetchInformation(DataSource dataSource) {
        viewInGeolocationBtn.setEnabled(false);
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        viewInGeolocationBtn.setEnabled(false);
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
        javax.swing.JLabel cityCountsLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JPanel cityCountsPanel = cityCountsTable;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.JLabel withinDistanceLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 10), new java.awt.Dimension(0, 10), new java.awt.Dimension(0, 10));
        viewInGeolocationBtn = new javax.swing.JButton();
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        org.openide.awt.Mnemonics.setLocalizedText(cityCountsLabel, org.openide.util.NbBundle.getMessage(WhereUsedPanel.class, "WhereUsedPanel.cityCountsLabel.text")); // NOI18N
        mainContentPanel.add(cityCountsLabel);
        cityCountsLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(WhereUsedPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        filler1.setAlignmentX(0.0F);
        mainContentPanel.add(filler1);

        cityCountsPanel.setAlignmentX(0.0F);
        cityCountsPanel.setMaximumSize(new java.awt.Dimension(32767, 212));
        cityCountsPanel.setMinimumSize(new java.awt.Dimension(100, 212));
        cityCountsPanel.setPreferredSize(new java.awt.Dimension(100, 212));
        mainContentPanel.add(cityCountsPanel);

        filler2.setAlignmentX(0.0F);
        mainContentPanel.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(withinDistanceLabel, org.openide.util.NbBundle.getMessage(WhereUsedPanel.class, "WhereUsedPanel.withinDistanceLabel.text")); // NOI18N
        mainContentPanel.add(withinDistanceLabel);

        filler3.setAlignmentX(0.0F);
        mainContentPanel.add(filler3);

        org.openide.awt.Mnemonics.setLocalizedText(viewInGeolocationBtn, org.openide.util.NbBundle.getMessage(WhereUsedPanel.class, "WhereUsedPanel.viewInGeolocationBtn.text")); // NOI18N
        viewInGeolocationBtn.setEnabled(false);
        viewInGeolocationBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                viewInGeolocationBtnActionPerformed(evt);
            }
        });
        mainContentPanel.add(viewInGeolocationBtn);

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
            .addComponent(mainScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void viewInGeolocationBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_viewInGeolocationBtnActionPerformed
        openGeolocationWindow(getDataSource());
    }//GEN-LAST:event_viewInGeolocationBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton viewInGeolocationBtn;
    // End of variables declaration//GEN-END:variables
}
