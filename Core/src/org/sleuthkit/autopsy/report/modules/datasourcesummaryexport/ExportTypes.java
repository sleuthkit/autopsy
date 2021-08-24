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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.awt.Color;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartItem;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataFetcher;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceInfoUtilities;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.MimeTypeSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TypesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TypesSummary.FileTypeCategoryData;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.KeyValueItemExportable;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Class to export summary information on the known files present in the
 * specified DataSource.
 */
@Messages({
    "ExportTypes_artifactsTypesPieChart_title=Artifact Types",
    "ExportTypes_filesByCategoryTable_allocatedRow_title=Allocated Files",
    "ExportTypes_filesByCategoryTable_unallocatedRow_title=Unallocated Files",
    "ExportTypes_filesByCategoryTable_slackRow_title=Slack Files",
    "ExportTypes_filesByCategoryTable_directoryRow_title=Directories",
    "ExportTypes_fileMimeTypesChart_title=File Types",
    "ExportTypes_fileMimeTypesChart_valueLabel=Count",
    "ExportTypes_fileMimeTypesChart_audio_title=Audio",
    "ExportTypes_fileMimeTypesChart_documents_title=Documents",
    "ExportTypes_fileMimeTypesChart_executables_title=Executables",
    "ExportTypes_fileMimeTypesChart_images_title=Images",
    "ExportTypes_fileMimeTypesChart_videos_title=Videos",
    "ExportTypes_fileMimeTypesChart_other_title=Other",
    "ExportTypes_fileMimeTypesChart_unknown_title=Unknown",
    "ExportTypes_fileMimeTypesChart_notAnalyzed_title=Not Analyzed",
    "ExportTypes_usageLabel_title=Usage",
    "ExportTypes_osLabel_title=OS",
    "ExportTypes_sizeLabel_title=Size",
    "ExportTypes_excelTabName=Types"})
class ExportTypes {

    /**
     * Data for types pie chart.
     */
    private static class TypesPieChartData {

        private final List<PieChartItem> pieSlices;
        private final boolean usefulContent;

        /**
         * Main constructor.
         *
         * @param pieSlices     The pie slices.
         * @param usefulContent True if this is useful content; false if there
         *                      is 0 mime type information.
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
    
    private final MimeTypeSummary mimeTypeSummary;
    private final ContainerSummary containerSummary;
    private final TypesSummary typesSummary;
    private final SleuthkitCaseProvider provider;

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
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_images_title(), FileTypeCategory.IMAGE.getMediaTypes(), IMAGES_COLOR),
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_videos_title(), FileTypeCategory.VIDEO.getMediaTypes(), VIDEOS_COLOR),
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_audio_title(), FileTypeCategory.AUDIO.getMediaTypes(), AUDIO_COLOR),
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_documents_title(), FileTypeCategory.DOCUMENTS.getMediaTypes(), DOCUMENTS_COLOR),
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_executables_title(), FileTypeCategory.EXECUTABLE.getMediaTypes(), EXECUTABLES_COLOR),
            new FileTypeCategoryData(Bundle.ExportTypes_fileMimeTypesChart_unknown_title(), new HashSet<>(Arrays.asList("application/octet-stream")), UNKNOWN_COLOR)
    );

    ExportTypes() {
        this.provider = SleuthkitCaseProvider.DEFAULT;
        containerSummary = new ContainerSummary();
        typesSummary = new TypesSummary();
        mimeTypeSummary = new MimeTypeSummary();
    }

    /**
     * Gets all the data for the file type pie chart.
     *
     * @param mimeTypeData The means of acquiring data.
     * @param dataSource   The datasource.
     *
     * @return The pie chart items.
     */
    private TypesPieChartData getMimeTypeCategoriesModel(DataSource dataSource)
            throws SQLException, TskCoreException, SleuthkitCaseProvider.SleuthkitCaseProviderException {

        if (dataSource == null) {
            return null;
        }

        // for each category of file types, get the counts of files
        List<PieChartItem> fileCategoryItems = new ArrayList<>();
        long categoryTotalCount = 0;

        for (FileTypeCategoryData cat : FILE_MIME_TYPE_CATEGORIES) {
            long thisValue = DataSourceInfoUtilities.getLongOrZero(mimeTypeSummary.getCountOfFilesForMimeTypes(dataSource, cat.getMimeTypes()));
            categoryTotalCount += thisValue;

            fileCategoryItems.add(new PieChartItem(
                    cat.getLabel(),
                    thisValue,
                    cat.getColor()));
        }

        // get a count of all files with no mime type
        long noMimeTypeCount = DataSourceInfoUtilities.getLongOrZero(mimeTypeSummary.getCountOfFilesWithNoMimeType(dataSource));

        // get a count of all regular files
        long allRegularFiles = DataSourceInfoUtilities.getLongOrZero(DataSourceInfoUtilities.getCountOfRegNonSlackFiles(provider.get(), dataSource, null));

        // create entry for mime types in other category
        long otherCount = allRegularFiles - (categoryTotalCount + noMimeTypeCount);
        PieChartItem otherPieItem = new PieChartItem(Bundle.ExportTypes_fileMimeTypesChart_other_title(),
                otherCount, OTHER_COLOR);

        // check at this point to see if these are all 0; if so, we don't have useful content.
        boolean usefulContent = categoryTotalCount > 0 || otherCount > 0;

        // create entry for not analyzed mime types category
        PieChartItem notAnalyzedItem = new PieChartItem(Bundle.ExportTypes_fileMimeTypesChart_notAnalyzed_title(),
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
     * Returns a key value pair to be exported in a sheet.
     *
     * @param fetcher    The means of fetching the data.
     * @param key        The key to use.
     * @param dataSource The data source containing the data.
     *
     * @return The key value pair to be exported.
     */
    private static KeyValueItemExportable getStrExportable(DataFetcher<DataSource, String> fetcher, String key, DataSource dataSource) {
        String result = ExcelExportAction.getFetchResult(fetcher, "Types", dataSource);
        return (result == null) ? null : new KeyValueItemExportable(key, new DefaultCellModel<>(result));
    }

    /**
     * Returns a key value pair to be exported in a sheet formatting the long
     * with commas separated by orders of 1000.
     *
     * @param fetcher    The means of fetching the data.
     * @param key        The string key for this key value pair.
     * @param dataSource The data source.
     *
     * @return The key value pair.
     */
    private static KeyValueItemExportable getCountExportable(DataFetcher<DataSource, Long> fetcher, String key, DataSource dataSource) {
        Long count = ExcelExportAction.getFetchResult(fetcher, "Types", dataSource);
        return (count == null) ? null : new KeyValueItemExportable(key,
                new DefaultCellModel<Long>(count, DataSourceInfoUtilities.COMMA_FORMATTER::format, DataSourceInfoUtilities.COMMA_FORMAT_STR));
    }

    List<ExcelExport.ExcelSheetExport> getExports(DataSource dataSource) {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        DataFetcher<DataSource, String> usageFetcher = (ds) -> containerSummary.getDataSourceType(ds);
        DataFetcher<DataSource, String> osFetcher = (ds) -> containerSummary.getOperatingSystems(ds);
        DataFetcher<DataSource, Long> sizeFetcher = (ds) -> ds == null ? null : ds.getSize();

        DataFetcher<DataSource, TypesPieChartData> typesFetcher = (ds) -> getMimeTypeCategoriesModel(ds);

        DataFetcher<DataSource, Long> allocatedFetcher = (ds) -> typesSummary.getCountOfAllocatedFiles(ds);
        DataFetcher<DataSource, Long> unallocatedFetcher = (ds) -> typesSummary.getCountOfUnallocatedFiles(ds);
        DataFetcher<DataSource, Long> slackFetcher = (ds) -> typesSummary.getCountOfSlackFiles(ds);
        DataFetcher<DataSource, Long> directoriesFetcher = (ds) -> typesSummary.getCountOfDirectories(ds);

        // Retrieve data to create the types pie chart
        TypesPieChartData typesData = ExcelExportAction.getFetchResult(typesFetcher, "Types", dataSource);
        PieChartExport typesChart = (typesData == null || !typesData.isUsefulContent()) ? null
                : new PieChartExport(
                        Bundle.ExportTypes_fileMimeTypesChart_title(),
                        Bundle.ExportTypes_fileMimeTypesChart_valueLabel(),
                        "#,###",
                        Bundle.ExportTypes_fileMimeTypesChart_title(),
                        typesData.getPieSlices());

        return Arrays.asList(new ExcelSpecialFormatExport(Bundle.ExportTypes_excelTabName(),
                Stream.of(
                        getStrExportable(usageFetcher, Bundle.ExportTypes_usageLabel_title(), dataSource),
                        getStrExportable(osFetcher, Bundle.ExportTypes_osLabel_title(), dataSource),
                        new KeyValueItemExportable(Bundle.ExportTypes_sizeLabel_title(),
                                SizeRepresentationUtil.getBytesCell(ExcelExportAction.getFetchResult(sizeFetcher, "Types", dataSource))),
                        typesChart,
                        getCountExportable(allocatedFetcher, Bundle.ExportTypes_filesByCategoryTable_allocatedRow_title(), dataSource),
                        getCountExportable(unallocatedFetcher, Bundle.ExportTypes_filesByCategoryTable_unallocatedRow_title(), dataSource),
                        getCountExportable(slackFetcher, Bundle.ExportTypes_filesByCategoryTable_slackRow_title(), dataSource),
                        getCountExportable(directoriesFetcher, Bundle.ExportTypes_filesByCategoryTable_directoryRow_title(), dataSource))
                        .filter(sheet -> sheet != null)
                        .collect(Collectors.toList())
        ));
    }
}
