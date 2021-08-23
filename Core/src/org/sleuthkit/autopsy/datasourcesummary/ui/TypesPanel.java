/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2021 Basis Technology Corp.
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

import java.awt.Color;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TypesSummary.FileTypeCategoryData;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult.ResultType;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.IngestRunningLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableLabel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartItem;

import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource.
 */
@Messages({
    "TypesPanel_artifactsTypesPieChart_title=Artifact Types",
    "TypesPanel_filesByCategoryTable_allocatedRow_title=Allocated Files",
    "TypesPanel_filesByCategoryTable_unallocatedRow_title=Unallocated Files",
    "TypesPanel_filesByCategoryTable_slackRow_title=Slack Files",
    "TypesPanel_filesByCategoryTable_directoryRow_title=Directories",
    "TypesPanel_fileMimeTypesChart_title=File Types",
    "TypesPanel_fileMimeTypesChart_valueLabel=Count",
    "TypesPanel_fileMimeTypesChart_audio_title=Audio",
    "TypesPanel_fileMimeTypesChart_documents_title=Documents",
    "TypesPanel_fileMimeTypesChart_executables_title=Executables",
    "TypesPanel_fileMimeTypesChart_images_title=Images",
    "TypesPanel_fileMimeTypesChart_videos_title=Videos",
    "TypesPanel_fileMimeTypesChart_other_title=Other",
    "TypesPanel_fileMimeTypesChart_unknown_title=Unknown",
    "TypesPanel_fileMimeTypesChart_notAnalyzed_title=Not Analyzed",
    "TypesPanel_usageLabel_title=Usage",
    "TypesPanel_osLabel_title=OS",
    "TypesPanel_sizeLabel_title=Size",
    "TypesPanel_excelTabName=Types"})
class TypesPanel extends BaseDataSourceSummaryPanel {

    /**
     * Data for types pie chart.
     */
    private static class TypesPieChartData {

        private final List<PieChartItem> pieSlices;
        private final boolean usefulContent;

        /**
         * Main constructor.
         *
         * @param pieSlices The pie slices.
         * @param usefulContent True if this is useful content; false if there
         * is 0 mime type information.
         */
        TypesPieChartData(List<PieChartItem> pieSlices, boolean usefulContent) {
            this.pieSlices = pieSlices;
            this.usefulContent = usefulContent;
        }

        /**
         * @return The pie chart data.
         */
        List<PieChartItem> getPieSlices() {
            return pieSlices;
        }

        /**
         * @return Whether or not the data is usefulContent.
         */
        boolean isUsefulContent() {
            return usefulContent;
        }
    }

    private static final long serialVersionUID = 1L;
    private static final DecimalFormat INTEGER_SIZE_FORMAT = new DecimalFormat("#");

    private static final Color IMAGES_COLOR = new Color(156, 39, 176);
    private static final Color VIDEOS_COLOR = Color.YELLOW;
    private static final Color AUDIO_COLOR = Color.BLUE;
    private static final Color DOCUMENTS_COLOR = Color.GREEN;
    private static final Color EXECUTABLES_COLOR = new Color(0, 188, 212);
    private static final Color UNKNOWN_COLOR = Color.ORANGE;
    private static final Color OTHER_COLOR = new Color(78, 52, 46);
    private static final Color NOT_ANALYZED_COLOR = Color.WHITE;

    // All file type categories.
    private static final List<FileTypeCategoryData> FILE_MIME_TYPE_CATEGORIES = Arrays.asList(
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_images_title(), FileTypeCategory.IMAGE.getMediaTypes(), IMAGES_COLOR),
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_videos_title(), FileTypeCategory.VIDEO.getMediaTypes(), VIDEOS_COLOR),
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_audio_title(), FileTypeCategory.AUDIO.getMediaTypes(), AUDIO_COLOR),
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_documents_title(), FileTypeCategory.DOCUMENTS.getMediaTypes(), DOCUMENTS_COLOR),
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_executables_title(), FileTypeCategory.EXECUTABLE.getMediaTypes(), EXECUTABLES_COLOR),
            new FileTypeCategoryData(Bundle.TypesPanel_fileMimeTypesChart_unknown_title(), new HashSet<>(Arrays.asList("application/octet-stream")), UNKNOWN_COLOR)
    );

    private final DataFetcher<DataSource, String> usageFetcher;
    private final DataFetcher<DataSource, String> osFetcher;
    private final DataFetcher<DataSource, Long> sizeFetcher;

    private final DataFetcher<DataSource, TypesPieChartData> typesFetcher;
    
    private final DataFetcher<DataSource, Long> allocatedFetcher;
    private final DataFetcher<DataSource, Long> unallocatedFetcher;
    private final DataFetcher<DataSource, Long> slackFetcher;
    private final DataFetcher<DataSource, Long> directoriesFetcher;

    private final LoadableLabel usageLabel = new LoadableLabel(Bundle.TypesPanel_usageLabel_title());
    private final LoadableLabel osLabel = new LoadableLabel(Bundle.TypesPanel_osLabel_title());
    private final LoadableLabel sizeLabel = new LoadableLabel(Bundle.TypesPanel_sizeLabel_title());

    private final PieChartPanel fileMimeTypesChart = new PieChartPanel(Bundle.TypesPanel_fileMimeTypesChart_title());

    private final LoadableLabel allocatedLabel = new LoadableLabel(Bundle.TypesPanel_filesByCategoryTable_allocatedRow_title());
    private final LoadableLabel unallocatedLabel = new LoadableLabel(Bundle.TypesPanel_filesByCategoryTable_unallocatedRow_title());
    private final LoadableLabel slackLabel = new LoadableLabel(Bundle.TypesPanel_filesByCategoryTable_slackRow_title());
    private final LoadableLabel directoriesLabel = new LoadableLabel(Bundle.TypesPanel_filesByCategoryTable_directoryRow_title());

    // all loadable components
    private final List<LoadableComponent<?>> loadables = Arrays.asList(
            usageLabel,
            osLabel,
            sizeLabel,
            fileMimeTypesChart,
            allocatedLabel,
            unallocatedLabel,
            slackLabel,
            directoriesLabel
    );

    private final IngestRunningLabel ingestRunningLabel = new IngestRunningLabel();

    // all of the means for obtaining data for the gui components.
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    /**
     * Creates a new TypesPanel.
     */
    TypesPanel() {
        this(new MimeTypeSummaryGetter(), new TypesSummaryGetter(), new ContainerSummaryGetter());
    }

    @Override
    public void close() {
        ingestRunningLabel.unregister();
        super.close();
    }

    /**
     * Creates a new TypesPanel.
     *
     * @param mimeTypeData The service for mime types.
     * @param typeData The service for file types data.
     * @param containerData The service for container information.
     */
    TypesPanel(
            MimeTypeSummaryGetter mimeTypeData,
            TypesSummaryGetter typeData,
            ContainerSummaryGetter containerData) {

        super(mimeTypeData, typeData, containerData);

        this.usageFetcher = containerData::getDataSourceType;
        this.osFetcher = containerData::getOperatingSystems;

        this.sizeFetcher = (dataSource) -> dataSource == null ? null : dataSource.getSize();
        
        this.typesFetcher = (dataSource) -> getMimeTypeCategoriesModel(mimeTypeData, dataSource);

        this.allocatedFetcher = (dataSource) -> typeData.getCountOfAllocatedFiles(dataSource);
        this.unallocatedFetcher = (dataSource) -> typeData.getCountOfUnallocatedFiles(dataSource);
        this.slackFetcher = (dataSource) -> typeData.getCountOfSlackFiles(dataSource);
        this.directoriesFetcher = (dataSource) -> typeData.getCountOfDirectories(dataSource);

        this.dataFetchComponents = Arrays.asList(
                new DataFetchWorker.DataFetchComponents<>(usageFetcher, usageLabel::showDataFetchResult),
                new DataFetchWorker.DataFetchComponents<>(osFetcher, osLabel::showDataFetchResult),
                new DataFetchWorker.DataFetchComponents<>(sizeFetcher,
                        (sizeResult) -> sizeLabel.showDataFetchResult(
                                DataFetchResult.getSubResult(sizeResult,
                                        size -> SizeRepresentationUtil.getSizeString(size, INTEGER_SIZE_FORMAT, false)))),
                new DataFetchWorker.DataFetchComponents<>(typesFetcher, this::showMimeTypeCategories),
                new DataFetchWorker.DataFetchComponents<>(allocatedFetcher,
                        countRes -> allocatedLabel.showDataFetchResult(DataFetchResult.getSubResult(countRes, (count) -> DataSourceInfoUtilities.getStringOrZero(count)))),
                new DataFetchWorker.DataFetchComponents<>(unallocatedFetcher,
                        countRes -> unallocatedLabel.showDataFetchResult(DataFetchResult.getSubResult(countRes, (count) -> DataSourceInfoUtilities.getStringOrZero(count)))),
                new DataFetchWorker.DataFetchComponents<>(slackFetcher,
                        countRes -> slackLabel.showDataFetchResult(DataFetchResult.getSubResult(countRes, (count) -> DataSourceInfoUtilities.getStringOrZero(count)))),
                new DataFetchWorker.DataFetchComponents<>(directoriesFetcher,
                        countRes -> directoriesLabel.showDataFetchResult(DataFetchResult.getSubResult(countRes, (count) -> DataSourceInfoUtilities.getStringOrZero(count))))
        );

        initComponents();
    }

    @Override
    protected void fetchInformation(DataSource dataSource) {
        fetchInformation(dataFetchComponents, dataSource);
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        onNewDataSource(dataFetchComponents, loadables, dataSource);
    }

    /**
     * Gets all the data for the file type pie chart.
     *
     * @param mimeTypeData The means of acquiring data.
     * @param dataSource The datasource.
     *
     * @return The pie chart items.
     */
    private TypesPieChartData getMimeTypeCategoriesModel(MimeTypeSummaryGetter mimeTypeData, DataSource dataSource)
            throws SQLException, SleuthkitCaseProviderException, TskCoreException {

        if (dataSource == null) {
            return null;
        }

        // for each category of file types, get the counts of files
        List<PieChartItem> fileCategoryItems = new ArrayList<>();
        long categoryTotalCount = 0;

        for (FileTypeCategoryData cat : FILE_MIME_TYPE_CATEGORIES) {
            long thisValue = DataSourceInfoUtilities.getLongOrZero(mimeTypeData.getCountOfFilesForMimeTypes(dataSource, cat.getMimeTypes()));
            categoryTotalCount += thisValue;

            fileCategoryItems.add(new PieChartItem(
                    cat.getLabel(),
                    thisValue,
                    cat.getColor()));
        }

        // get a count of all files with no mime type
        long noMimeTypeCount = DataSourceInfoUtilities.getLongOrZero(mimeTypeData.getCountOfFilesWithNoMimeType(dataSource));

        // get a count of all regular files
        long allRegularFiles = DataSourceInfoUtilities.getLongOrZero(mimeTypeData.getCountOfAllRegularFiles(dataSource));

        // create entry for mime types in other category
        long otherCount = allRegularFiles - (categoryTotalCount + noMimeTypeCount);
        PieChartItem otherPieItem = new PieChartItem(Bundle.TypesPanel_fileMimeTypesChart_other_title(),
                otherCount, OTHER_COLOR);

        // check at this point to see if these are all 0; if so, we don't have useful content.
        boolean usefulContent = categoryTotalCount > 0 || otherCount > 0;

        // create entry for not analyzed mime types category
        PieChartItem notAnalyzedItem = new PieChartItem(Bundle.TypesPanel_fileMimeTypesChart_notAnalyzed_title(),
                noMimeTypeCount, NOT_ANALYZED_COLOR);

        // combine categories with 'other' and 'not analyzed'
        List<PieChartItem> items = Stream.concat(
                fileCategoryItems.stream(),
                Stream.of(otherPieItem, notAnalyzedItem))
                // remove items that have no value
                .filter(slice -> slice.getValue() > 0)
                .collect(Collectors.toList());

        return new TypesPieChartData(items, usefulContent);
    }

    /**
     * Handles properly showing data for the mime type categories pie chart
     * accounting for whether there are any files with mime types specified and
     * whether or not the current data source has been ingested with the file
     * type ingest module.
     *
     * @param result The result to be shown.
     */
    private void showMimeTypeCategories(DataFetchResult<TypesPieChartData> result) {
        if (result == null) {
            fileMimeTypesChart.showDataFetchResult(DataFetchResult.getSuccessResult(null));
            return;
        }

        // if error, show error
        if (result.getResultType() == ResultType.ERROR) {
            fileMimeTypesChart.showDataFetchResult(DataFetchResult.getErrorResult(result.getException()));
            return;
        }

        TypesPieChartData data = result.getData();
        if (data == null) {
            fileMimeTypesChart.showDataFetchResult(DataFetchResult.getSuccessResult(null));
        } else {
            fileMimeTypesChart.showDataFetchResult(DataFetchResult.getSuccessResult(data.getPieSlices()));
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane scrollParent = new javax.swing.JScrollPane();
        javax.swing.JPanel contentParent = new javax.swing.JPanel();
        javax.swing.JPanel ingestRunningPanel = ingestRunningLabel;
        javax.swing.JPanel usagePanel = usageLabel;
        javax.swing.JPanel osPanel = osLabel;
        javax.swing.JPanel sizePanel = sizeLabel;
        javax.swing.JPanel fileMimeTypesPanel = fileMimeTypesChart;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5), new java.awt.Dimension(32767, 5));
        javax.swing.JPanel allocatedPanel = allocatedLabel;
        javax.swing.JPanel unallocatedPanel = unallocatedLabel;
        javax.swing.JPanel slackPanel = slackLabel;
        javax.swing.JPanel directoriesPanel = directoriesLabel;
        javax.swing.Box.Filler filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        setLayout(new java.awt.BorderLayout());

        contentParent.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentParent.setMaximumSize(new java.awt.Dimension(32787, 32787));
        contentParent.setMinimumSize(new java.awt.Dimension(400, 490));
        contentParent.setLayout(new javax.swing.BoxLayout(contentParent, javax.swing.BoxLayout.PAGE_AXIS));

        ingestRunningPanel.setAlignmentX(0.0F);
        ingestRunningPanel.setMaximumSize(new java.awt.Dimension(32767, 25));
        ingestRunningPanel.setMinimumSize(new java.awt.Dimension(10, 25));
        ingestRunningPanel.setPreferredSize(new java.awt.Dimension(10, 25));
        contentParent.add(ingestRunningPanel);

        usagePanel.setAlignmentX(0.0F);
        usagePanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        usagePanel.setMinimumSize(new java.awt.Dimension(10, 20));
        usagePanel.setName(""); // NOI18N
        usagePanel.setPreferredSize(new java.awt.Dimension(800, 20));
        contentParent.add(usagePanel);

        osPanel.setAlignmentX(0.0F);
        osPanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        osPanel.setMinimumSize(new java.awt.Dimension(10, 20));
        osPanel.setPreferredSize(new java.awt.Dimension(800, 20));
        contentParent.add(osPanel);

        sizePanel.setAlignmentX(0.0F);
        sizePanel.setMaximumSize(new java.awt.Dimension(32767, 20));
        sizePanel.setMinimumSize(new java.awt.Dimension(10, 20));
        sizePanel.setPreferredSize(new java.awt.Dimension(800, 20));
        contentParent.add(sizePanel);

        fileMimeTypesPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        fileMimeTypesPanel.setAlignmentX(0.0F);
        fileMimeTypesPanel.setMaximumSize(new java.awt.Dimension(400, 300));
        fileMimeTypesPanel.setMinimumSize(new java.awt.Dimension(400, 300));
        fileMimeTypesPanel.setPreferredSize(new java.awt.Dimension(400, 300));
        contentParent.add(fileMimeTypesPanel);
        contentParent.add(filler2);

        allocatedPanel.setAlignmentX(0.0F);
        allocatedPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        allocatedPanel.setMinimumSize(new java.awt.Dimension(10, 16));
        allocatedPanel.setPreferredSize(new java.awt.Dimension(800, 16));
        contentParent.add(allocatedPanel);

        unallocatedPanel.setAlignmentX(0.0F);
        unallocatedPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        unallocatedPanel.setMinimumSize(new java.awt.Dimension(10, 16));
        unallocatedPanel.setPreferredSize(new java.awt.Dimension(800, 16));
        contentParent.add(unallocatedPanel);

        slackPanel.setAlignmentX(0.0F);
        slackPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        slackPanel.setMinimumSize(new java.awt.Dimension(10, 16));
        slackPanel.setPreferredSize(new java.awt.Dimension(800, 16));
        contentParent.add(slackPanel);

        directoriesPanel.setAlignmentX(0.0F);
        directoriesPanel.setMaximumSize(new java.awt.Dimension(32767, 16));
        directoriesPanel.setMinimumSize(new java.awt.Dimension(10, 16));
        directoriesPanel.setPreferredSize(new java.awt.Dimension(800, 16));
        contentParent.add(directoriesPanel);
        contentParent.add(filler3);

        scrollParent.setViewportView(contentParent);

        add(scrollParent, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
