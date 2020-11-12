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
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityCount;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.GeolocationSummary.CityRecord;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.datamodel.DataSource;

/**
 * A tab shown in data source summary displaying information about a data source's geolocation data.
 */
@Messages({
    "GeolocationPanel_cityColumn_title=City",
    "GeolocationPanel_countColumn_title=Count",
    "GeolocationPanel_onNoCrIngest_message=No results will be shown because the GPX Parser was not run."
})
public class GeolocationPanel extends BaseDataSourceSummaryPanel {

    private static final long serialVersionUID = 1L;
    private static final String GPX_FACTORY = "org.sleuthkit.autopsy.modules.vmextractor.VMExtractorIngestModuleFactory";
    private static final String GPX_NAME =  "GPX Parser";

    /**
     * Retrieves the city name to display from the record.
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
    
    private static final ColumnModel<CityCount> CITY_COL = new ColumnModel<>(
            Bundle.GeolocationPanel_cityColumn_title(),
            (cityCount) -> new DefaultCellModel(getCityName(cityCount.getCityRecord())),
            300
    );

    private static final ColumnModel<CityCount> COUNT_COL = new ColumnModel<>(
            Bundle.GeolocationPanel_countColumn_title(),
            (cityCount) -> new DefaultCellModel(Integer.toString(cityCount.getCount())),
            100
    );

    // table displaying city and number of hits for that city
    private final JTablePanel<CityCount> cityCountsTable = JTablePanel.getJTablePanel(Arrays.asList(CITY_COL, COUNT_COL));

    // loadable components on this tab
    private final List<JTablePanel<?>> tables = Arrays.asList(
            cityCountsTable
    );

    // means of fetching and displaying data
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    /**
     * Main constructor.
     */
    public GeolocationPanel() {
        this(GeolocationSummary.getInstance());
    }

    /**
     * Main constructor.
     * @param geolocationData The GeolocationSummary instance to use.
     */
    public GeolocationPanel(GeolocationSummary geolocationData) {
        // set up data acquisition methods
        dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> geolocationData.getCityCounts(dataSource),
                        (result) -> showResultWithModuleCheck(cityCountsTable, result, GPX_FACTORY, GPX_NAME)));

        initComponents();
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
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
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20), new java.awt.Dimension(0, 20));
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2));
        javax.swing.Box.Filler filler5 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        mainContentPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainContentPanel.setLayout(new javax.swing.BoxLayout(mainContentPanel, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        mainContentPanel.add(ingestRunningPanel);

        org.openide.awt.Mnemonics.setLocalizedText(cityCountsLabel, org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "GeolocationPanel.cityCountsLabel.text")); // NOI18N
        mainContentPanel.add(cityCountsLabel);
        cityCountsLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GeolocationPanel.class, "PastCasesPanel.notableFileLabel.text")); // NOI18N

        filler1.setAlignmentX(0.0F);
        mainContentPanel.add(filler1);

        cityCountsPanel.setAlignmentX(0.0F);
        cityCountsPanel.setMaximumSize(new java.awt.Dimension(32767, 212));
        cityCountsPanel.setMinimumSize(new java.awt.Dimension(100, 212));
        cityCountsPanel.setPreferredSize(new java.awt.Dimension(100, 212));
        mainContentPanel.add(cityCountsPanel);

        filler2.setAlignmentX(0.0F);
        mainContentPanel.add(filler2);

        filler3.setAlignmentX(0.0F);
        mainContentPanel.add(filler3);

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


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
