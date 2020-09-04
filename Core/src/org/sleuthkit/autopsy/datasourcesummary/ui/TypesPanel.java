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
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JLabel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceCountsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceDetailsSummary;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceMimeTypeSummary;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.AbstractLoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.CellModelTableCellRenderer.DefaultCellModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchResult;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.DataFetchWorker.DataFetchComponents;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.JTablePanel.ColumnModel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.LoadableComponent;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.PieChartPanel.PieChartItem;

import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource.
 */
@Messages({
    "TypesPanel_artifactsTypesPieChart_title=Artifact Types",
    "TypesPanel_filesByCategoryTable_title=Files by Category",
    "TypesPanel_filesByCategoryTable_labelColumn_title=File Type",
    "TypesPanel_filesByCategoryTable_countColumn_title=Count",
    "TypesPanel_filesByCategoryTable_allocatedRow_title=Allocated",
    "TypesPanel_filesByCategoryTable_unallocatedRow_title=Unallocated",
    "TypesPanel_filesByCategoryTable_slackRow_title=Slack",
    "TypesPanel_filesByCategoryTable_directoryRow_title=Directory",
    "TypesPanel_fileMimeTypesChart_title=File Types",
    "TypesPanel_fileMimeTypesChart_audio_title=Audio",
    "TypesPanel_fileMimeTypesChart_documents_title=Documents",
    "TypesPanel_fileMimeTypesChart_executables_title=Executables",
    "TypesPanel_fileMimeTypesChart_images_title=Images",
    "TypesPanel_fileMimeTypesChart_videos_title=Videos",
    "TypesPanel_fileMimeTypesChart_other_title=Other",
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

    // All file type categories.
    private static final List<Pair<String, FileTypeCategory>> FILE_MIME_TYPE_CATEGORIES = Arrays.asList(
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_images_title(), FileTypeCategory.IMAGE),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_videos_title(), FileTypeCategory.VIDEO),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_audio_title(), FileTypeCategory.AUDIO),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_documents_title(), FileTypeCategory.DOCUMENTS),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_executables_title(), FileTypeCategory.EXECUTABLE)
    );

    // The mime types in those categories.
    private static final Set<String> CATEGORY_MIME_TYPES = FILE_MIME_TYPE_CATEGORIES
            .stream()
            .flatMap((cat) -> cat.getRight().getMediaTypes().stream())
            .collect(Collectors.toSet());

    private final PieChartPanel fileMimeTypesChart = new PieChartPanel(Bundle.TypesPanel_fileMimeTypesChart_title());

    private final JTablePanel<Pair<String, Long>> filesByCategoryTable
            = JTablePanel.getJTablePanel(Arrays.asList(
                    // title column
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_labelColumn_title(),
                            (pair) -> new DefaultCellModel(pair.getLeft()),
                            250
                    ),
                    // count column
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_countColumn_title(),
                            (pair) -> new DefaultCellModel(Long.toString(pair.getRight() == null ? 0 : pair.getRight())),
                            150
                    )
            ));

    private final LoadableLabel usageLabel = new LoadableLabel(Bundle.TypesPanel_usageLabel_title());
    private final LoadableLabel osLabel = new LoadableLabel(Bundle.TypesPanel_osLabel_title());
    private final LoadableLabel sizeLabel = new LoadableLabel(Bundle.TypesPanel_sizeLabel_title());

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
            filesByCategoryTable,
            allocatedLabel,
            unallocatedLabel,
            slackLabel,
            directoriesLabel
    );

    // all of the means for obtaining data for the gui components.
    private final List<DataFetchComponents<DataSource, ?>> dataFetchComponents = Arrays.asList(
            // usage label worker
            new DataFetchWorker.DataFetchComponents<>(
                    DataSourceDetailsSummary::getDataSourceType,
                    usageLabel::showDataFetchResult),
            // os label worker
            new DataFetchWorker.DataFetchComponents<>(
                    DataSourceDetailsSummary::getOperatingSystems,
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
                    this::getMimeTypeCategoriesModel,
                    fileMimeTypesChart::showDataFetchResult),
            // allocated files worker
            new DataFetchWorker.DataFetchComponents<>(
                    (dataSource) -> getStringOrZero(DataSourceCountsSummary.getCountOfAllocatedFiles(dataSource)),
                    allocatedLabel::showDataFetchResult),
            // unallocated files worker
            new DataFetchWorker.DataFetchComponents<>(
                    (dataSource) -> getStringOrZero(DataSourceCountsSummary.getCountOfUnallocatedFiles(dataSource)),
                    unallocatedLabel::showDataFetchResult),
            // slack files worker
            new DataFetchWorker.DataFetchComponents<>(
                    (dataSource) -> getStringOrZero(DataSourceCountsSummary.getCountOfSlackFiles(dataSource)),
                    slackLabel::showDataFetchResult),
            // directories worker
            new DataFetchWorker.DataFetchComponents<>(
                    (dataSource) -> getStringOrZero(DataSourceCountsSummary.getCountOfDirectories(dataSource)),
                    directoriesLabel::showDataFetchResult)
    );

    /**
     * Main constructor.
     */
    public TypesPanel() {
        initComponents();
    }

    @Override
    protected void onNewDataSource(DataSource dataSource) {
        // if no data source is present or the case is not open,
        // set results for tables to null.
        if (dataSource == null || !Case.isCaseOpen()) {
            this.dataFetchComponents.forEach((item) -> item.getResultHandler()
                    .accept(DataFetchResult.getSuccessResult(null)));

        } else {
            // set tables to display loading screen
            this.loadables.forEach((table) -> table.showDefaultLoadingMessage());

            // create swing workers to run for each table
            List<DataFetchWorker<?, ?>> workers = dataFetchComponents
                    .stream()
                    .map((components) -> new DataFetchWorker<>(components, dataSource))
                    .collect(Collectors.toList());

            // submit swing workers to run
            submit(workers);
        }
    }

    /**
     * Gets all the data for the file type pie chart.
     *
     * @param dataSource The datasource.
     *
     * @return The pie chart items.
     */
    private List<PieChartItem> getMimeTypeCategoriesModel(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        // for each category of file types, get the counts of files
        Stream<Pair<String, Long>> fileCategoryItems = FILE_MIME_TYPE_CATEGORIES
                .stream()
                .map((strCat)
                        -> Pair.of(strCat.getLeft(),
                        DataSourceMimeTypeSummary.getCountOfFilesForMimeTypes(dataSource, strCat.getRight().getMediaTypes())));

        // also get counts for other and not analayzed
        Stream<Pair<String, Long>> otherItems = Stream.of(
                Pair.of(Bundle.TypesPanel_fileMimeTypesChart_other_title(),
                        DataSourceMimeTypeSummary.getCountOfFilesNotInMimeTypes(dataSource, CATEGORY_MIME_TYPES)),
                Pair.of(Bundle.TypesPanel_fileMimeTypesChart_notAnalyzed_title(),
                        DataSourceMimeTypeSummary.getCountOfFilesWithNoMimeType(dataSource))
        );

        // create pie chart items to provide to pie chart
        return Stream.concat(fileCategoryItems, otherItems)
                .filter(keyCount -> keyCount.getRight() != null && keyCount.getRight() > 0)
                .map(keyCount -> new PieChartItem(keyCount.getLeft(), keyCount.getRight()))
                .collect(Collectors.toList());
    }

    /**
     * Returns string value of long. If null returns a string of '0'.
     *
     * @param longVal The long value.
     *
     * @return The string value of the long.
     */
    private String getStringOrZero(Long longVal) {
        return String.valueOf(longVal == null ? 0 : longVal);
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
        javax.swing.JLabel filesByCategoryLabel = new javax.swing.JLabel();
        javax.swing.JPanel allocatedPanel = allocatedLabel;
        javax.swing.JPanel unallocatedPanel = unallocatedLabel;
        javax.swing.JPanel slackPanel = slackLabel;
        javax.swing.JPanel directoriesPanel = directoriesLabel;
        filler3 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 32767));

        setLayout(new java.awt.BorderLayout());

        contentParent.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentParent.setMaximumSize(new java.awt.Dimension(32787, 32787));
        contentParent.setMinimumSize(new java.awt.Dimension(400, 490));
        contentParent.setPreferredSize(null);
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

        filesByCategoryLabel.setFont(new java.awt.Font("Segoe UI", 1, 12)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(filesByCategoryLabel, org.openide.util.NbBundle.getMessage(TypesPanel.class, "TypesPanel.filesByCategoryLabel.text")); // NOI18N
        filesByCategoryLabel.setMaximumSize(new java.awt.Dimension(120, 20));
        filesByCategoryLabel.setMinimumSize(new java.awt.Dimension(120, 20));
        filesByCategoryLabel.setPreferredSize(new java.awt.Dimension(120, 20));
        contentParent.add(filesByCategoryLabel);

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
    private javax.swing.Box.Filler filler3;
    // End of variables declaration//GEN-END:variables
}
