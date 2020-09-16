/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import java.awt.BorderLayout;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.TypesSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.ContainerSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.MimeTypeSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.SleuthkitCaseProvider.SleuthkitCaseProviderException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.AbstractLoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel.PieChartItem;

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
    "TypesPanel_sizeLabel_title=Size"})
class TypesPanel extends BaseDataSourceSummaryPanel {

    /**
     * A label that allows for displaying loading messages and can be used with
     * a DataFetchResult. Text displays as "<key>:<value | message>".
     */
    private static class LoadableLabel extends AbstractLoadableComponent<String> {

        private static final long serialVersionUID = 1L;

        private final JLabel label = new JLabel();
        private final String key;

        /**
         * Main constructor for the label.
         *
         * @param key The key to be displayed.
         */
        LoadableLabel(String key) {
            this.key = key;
            setLayout(new BorderLayout());
            add(label, BorderLayout.CENTER);
            this.showResults(null);
        }

        private void setValue(String value, boolean italicize) {
            String formattedKey = StringUtils.isBlank(key) ? "" : key;
            String formattedValue = StringUtils.isBlank(value) ? "" : value;
            String htmlFormattedValue = (italicize) ? String.format("<i>%s</i>", formattedValue) : formattedValue;
            label.setText(String.format("<html><div style='text-align: center;'>%s: %s</div></html>", formattedKey, htmlFormattedValue));
        }

        @Override
        protected void setMessage(boolean visible, String message) {
            setValue(message, true);
        }

        @Override
        protected void setResults(String data) {
            setValue(data, false);
        }
    }

    private static final long serialVersionUID = 1L;
    private static final DecimalFormat INTEGER_SIZE_FORMAT = new DecimalFormat("#");
    private static final DecimalFormat COMMA_FORMATTER = new DecimalFormat("#,###");

    // All file type categories.
    private static final List<Pair<String, Set<String>>> FILE_MIME_TYPE_CATEGORIES = Arrays.asList(
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_images_title(), FileTypeCategory.IMAGE.getMediaTypes()),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_videos_title(), FileTypeCategory.VIDEO.getMediaTypes()),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_audio_title(), FileTypeCategory.AUDIO.getMediaTypes()),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_documents_title(), FileTypeCategory.DOCUMENTS.getMediaTypes()),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_executables_title(), FileTypeCategory.EXECUTABLE.getMediaTypes()),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_unknown_title(), new HashSet<>(Arrays.asList("application/octet-stream")))
    );

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

    // all of the means for obtaining data for the gui components.
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents;

    /**
     * Creates a new TypesPanel.
     */
    public TypesPanel() {
        this(new MimeTypeSummary(), new TypesSummary(), new ContainerSummary());
    }

    /**
     * Creates a new TypesPanel.
     *
     * @param mimeTypeData  The service for mime types.
     * @param typeData      The service for file types data.
     * @param containerData The service for container information.
     */
    public TypesPanel(
            MimeTypeSummary mimeTypeData,
            TypesSummary typeData,
            ContainerSummary containerData) {

        super(mimeTypeData, typeData, containerData);

        this.dataFetchComponents = Arrays.asList(
                // usage label worker
                new DataFetchWorker.DataFetchComponents<>(
                        containerData::getDataSourceType,
                        usageLabel::showDataFetchResult),
                // os label worker
                new DataFetchWorker.DataFetchComponents<>(
                        containerData::getOperatingSystems,
                        osLabel::showDataFetchResult),
                // size label worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> {
                            Long size = dataSource == null ? null : dataSource.getSize();
                            return SizeRepresentationUtil.getSizeString(size, INTEGER_SIZE_FORMAT, false);
                        },
                        sizeLabel::showDataFetchResult),
                // file types worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> getMimeTypeCategoriesModel(mimeTypeData, dataSource),
                        fileMimeTypesChart::showDataFetchResult),
                // allocated files worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> getStringOrZero(typeData.getCountOfAllocatedFiles(dataSource)),
                        allocatedLabel::showDataFetchResult),
                // unallocated files worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> getStringOrZero(typeData.getCountOfUnallocatedFiles(dataSource)),
                        unallocatedLabel::showDataFetchResult),
                // slack files worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> getStringOrZero(typeData.getCountOfSlackFiles(dataSource)),
                        slackLabel::showDataFetchResult),
                // directories worker
                new DataFetchWorker.DataFetchComponents<>(
                        (dataSource) -> getStringOrZero(typeData.getCountOfDirectories(dataSource)),
                        directoriesLabel::showDataFetchResult)
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
     * @param dataSource The datasource.
     *
     * @return The pie chart items.
     */
    private List<PieChartItem> getMimeTypeCategoriesModel(MimeTypeSummary mimeTypeData, DataSource dataSource)
            throws SQLException, SleuthkitCaseProviderException, TskCoreException {

        if (dataSource == null) {
            return null;
        }

        // for each category of file types, get the counts of files
        List<Pair<String, Long>> fileCategoryItems = new ArrayList<>();
        for (Pair<String, Set<String>> strCat : FILE_MIME_TYPE_CATEGORIES) {
            fileCategoryItems.add(Pair.of(strCat.getLeft(),
                    getLongOrZero(mimeTypeData.getCountOfFilesForMimeTypes(
                            dataSource, strCat.getRight()))));
        }

        // get a count of all files with no mime type
        Long noMimeTypeCount = getLongOrZero(mimeTypeData.getCountOfFilesWithNoMimeType(dataSource));

        // get the sum of all counts for the known categories
        Long categoryTotalCount = getLongOrZero(fileCategoryItems.stream()
                .collect(Collectors.summingLong((pair) -> pair.getValue())));

        // get a count of all regular files
        Long allRegularFiles = getLongOrZero(mimeTypeData.getCountOfAllRegularFiles(dataSource));

        // create entry for mime types in other category
        fileCategoryItems.add(Pair.of(Bundle.TypesPanel_fileMimeTypesChart_other_title(),
                allRegularFiles - (categoryTotalCount + noMimeTypeCount)));

        // create entry for not analyzed mime types category
        fileCategoryItems.add(Pair.of(Bundle.TypesPanel_fileMimeTypesChart_notAnalyzed_title(),
                noMimeTypeCount));

        // create pie chart items to provide to pie chart
        return fileCategoryItems.stream()
                .filter(keyCount -> keyCount.getRight() != null && keyCount.getRight() > 0)
                .map(keyCount -> new PieChartItem(keyCount.getLeft(), keyCount.getRight()))
                .collect(Collectors.toList());
    }

    /**
     * Returns the long value or zero if longVal is null.
     *
     * @param longVal The long value.
     *
     * @return The long value or 0 if provided value is null.
     */
    private static long getLongOrZero(Long longVal) {
        return longVal == null ? 0 : longVal;
    }

    /**
     * Returns string value of long with comma separators. If null returns a
     * string of '0'.
     *
     * @param longVal The long value.
     *
     * @return The string value of the long.
     */
    private static String getStringOrZero(Long longVal) {
        return longVal == null ? "0" : COMMA_FORMATTER.format(longVal);
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
