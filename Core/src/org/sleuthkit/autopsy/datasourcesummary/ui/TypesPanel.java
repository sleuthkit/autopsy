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
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import org.sleuthkit.autopsy.guiutils.WrapLayout;

import org.sleuthkit.datamodel.DataSource;

/**
 * Panel for displaying summary information on the known files present in the
 * specified DataSource
 */
@Messages({
    "TypesPanel_artifactsTypesPieChart_title=Artifact Types",
    "TypesPanel_filesByCategoryTable_title=Files by Category",
    "TypesPanel_filesByCategoryTable_labelColumn_title=File Type",
    "TypesPanel_filesByCategoryTable_countColumn_title=Count",
    "TypesPanel_filesByCategoryTable_allRow_title=All",
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

    private static class LoadableLabel extends AbstractLoadableComponent<String> {
        private static final long serialVersionUID = 1L;
        
        private final JLabel label = new JLabel();
        private final String key;

        public LoadableLabel(String key) {
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

    private static final List<Pair<String, FileTypeCategory>> FILE_MIME_TYPE_CATEGORIES = Arrays.asList(
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_images_title(), FileTypeCategory.IMAGE),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_videos_title(), FileTypeCategory.VIDEO),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_audio_title(), FileTypeCategory.AUDIO),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_documents_title(), FileTypeCategory.DOCUMENTS),
            Pair.of(Bundle.TypesPanel_fileMimeTypesChart_executables_title(), FileTypeCategory.EXECUTABLE)
    );

    private static final Set<String> CATEGORY_MIME_TYPES = FILE_MIME_TYPE_CATEGORIES
            .stream()
            .flatMap((cat) -> cat.getRight().getMediaTypes().stream())
            .collect(Collectors.toSet());
    

    private final PieChartPanel fileMimeTypesChart = new PieChartPanel(Bundle.TypesPanel_fileMimeTypesChart_title());

    private final PieChartPanel artifactTypesChart = new PieChartPanel(Bundle.TypesPanel_artifactsTypesPieChart_title());

    private final JTablePanel<Pair<String, Long>> filesByCategoryTable
            = JTablePanel.getJTablePanel(Arrays.asList(
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_labelColumn_title(),
                            (pair) -> new DefaultCellModel(pair.getLeft()),
                            250
                    ),
                    new ColumnModel<>(
                            Bundle.TypesPanel_filesByCategoryTable_countColumn_title(),
                            (pair) -> new DefaultCellModel(Long.toString(pair.getRight() == null ? 0 : pair.getRight())),
                            150
                    )
            ));
    
    private final LoadableLabel usageLabel = new LoadableLabel(Bundle.TypesPanel_usageLabel_title());
    private final LoadableLabel osLabel = new LoadableLabel(Bundle.TypesPanel_osLabel_title());
    private final LoadableLabel sizeLabel = new LoadableLabel(Bundle.TypesPanel_sizeLabel_title());

    private final List<LoadableComponent<?>> loadables = Arrays.asList(
            usageLabel,
            osLabel,
            sizeLabel,
            fileMimeTypesChart,
            artifactTypesChart,
            filesByCategoryTable
    );

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
            // artifact counts worker
            new DataFetchWorker.DataFetchComponents<>(
                    this::getArtifactCountsModel,
                    artifactTypesChart::showDataFetchResult),
            // files by category worker
            new DataFetchWorker.DataFetchComponents<>(
                    this::getFileCategoryModel,
                    filesByCategoryTable::showDataFetchResult)
    );

    public TypesPanel() {
        initComponents();
        customizeComponents();
    }
    
    private void customizeComponents() {
        this.pieChartRow.setLayout(new WrapLayout(0,5));
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

    private List<Pair<String, Long>> getFileCategoryModel(DataSource selectedDataSource) {
        if (selectedDataSource == null) {
            return null;
        }

        List<Pair<String, Function<DataSource, Long>>> itemsAndRetrievers = Arrays.asList(
                Pair.of(Bundle.TypesPanel_filesByCategoryTable_allRow_title(), DataSourceCountsSummary::getCountOfFiles),
                Pair.of(Bundle.TypesPanel_filesByCategoryTable_allocatedRow_title(), DataSourceCountsSummary::getCountOfAllocatedFiles),
                Pair.of(Bundle.TypesPanel_filesByCategoryTable_unallocatedRow_title(), DataSourceCountsSummary::getCountOfUnallocatedFiles),
                Pair.of(Bundle.TypesPanel_filesByCategoryTable_slackRow_title(), DataSourceCountsSummary::getCountOfSlackFiles),
                Pair.of(Bundle.TypesPanel_filesByCategoryTable_directoryRow_title(), DataSourceCountsSummary::getCountOfDirectories)
        );

        return itemsAndRetrievers
                .stream()
                .map(pair -> {
                    Long result = pair.getRight().apply(selectedDataSource);
                    return Pair.of(pair.getLeft(), result == null ? 0 : result);
                })
                .collect(Collectors.toList());
    }

    private List<PieChartItem> getMimeTypeCategoriesModel(DataSource dataSource) {
        if (dataSource == null) {
            return null;
        }

        Stream<Pair<String, Long>> fileCategoryItems = FILE_MIME_TYPE_CATEGORIES
                .stream()
                .map((strCat)
                        -> Pair.of(strCat.getLeft(),
                        DataSourceMimeTypeSummary.getCountOfFilesForMimeTypes(dataSource, strCat.getRight().getMediaTypes())));

        Stream<Pair<String, Long>> otherItems = Stream.of(
                Pair.of(Bundle.TypesPanel_fileMimeTypesChart_other_title(),
                        DataSourceMimeTypeSummary.getCountOfFilesNotInMimeTypes(dataSource, CATEGORY_MIME_TYPES)),
                Pair.of(Bundle.TypesPanel_fileMimeTypesChart_notAnalyzed_title(),
                        DataSourceMimeTypeSummary.getCountOfFilesWithNoMimeType(dataSource))
        );

        return Stream.concat(fileCategoryItems, otherItems)
                .filter(keyCount -> keyCount.getRight() != null && keyCount.getRight() > 0)
                .map(keyCount -> new PieChartItem(keyCount.getLeft(), keyCount.getRight()))
                .collect(Collectors.toList());
    }

    /**
     * The counts of different artifact types found in a DataSource.
     *
     * @param selectedDataSource The DataSource.
     *
     * @return The JTable data model of counts of artifact types.
     */
    private List<PieChartItem> getArtifactCountsModel(DataSource selectedDataSource) {
        Map<String, Long> artifactMapping = DataSourceCountsSummary.getCountsOfArtifactsByType(selectedDataSource);
        if (artifactMapping == null) {
            return null;
        }

        return artifactMapping.entrySet().stream()
                .filter((entrySet) -> entrySet != null)
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map((entrySet) -> new PieChartItem(entrySet.getKey(), entrySet.getValue()))
                .collect(Collectors.toList());
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
        pieChartRow = new javax.swing.JPanel();
        javax.swing.JPanel fileMimeTypesPanel = fileMimeTypesChart;
        javax.swing.JPanel artifactTypesPanel = artifactTypesChart;
        javax.swing.Box.Filler filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 5), new java.awt.Dimension(0, 5), new java.awt.Dimension(32767, 5));
        javax.swing.JLabel filesByCategoryLabel = new javax.swing.JLabel();
        javax.swing.Box.Filler filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 2), new java.awt.Dimension(0, 2), new java.awt.Dimension(32767, 2));
        javax.swing.JPanel filesByCategoryPanel = filesByCategoryTable;

        setLayout(new java.awt.BorderLayout());

        contentParent.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentParent.setMaximumSize(new java.awt.Dimension(32787, 32787));
        contentParent.setMinimumSize(new java.awt.Dimension(650, 510));
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

        pieChartRow.setAlignmentX(0.0F);
        pieChartRow.setMaximumSize(new java.awt.Dimension(1170, 895));
        pieChartRow.setMinimumSize(null);
        pieChartRow.setPreferredSize(null);

        fileMimeTypesPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        fileMimeTypesPanel.setMaximumSize(new java.awt.Dimension(500, 375));
        fileMimeTypesPanel.setMinimumSize(new java.awt.Dimension(500, 375));
        fileMimeTypesPanel.setPreferredSize(new java.awt.Dimension(500, 375));
        pieChartRow.add(fileMimeTypesPanel);

        artifactTypesPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(5, 5, 5, 5));
        artifactTypesPanel.setMaximumSize(new java.awt.Dimension(650, 500));
        artifactTypesPanel.setMinimumSize(new java.awt.Dimension(650, 500));
        artifactTypesPanel.setPreferredSize(new java.awt.Dimension(650, 500));
        pieChartRow.add(artifactTypesPanel);

        contentParent.add(pieChartRow);
        contentParent.add(filler2);

        org.openide.awt.Mnemonics.setLocalizedText(filesByCategoryLabel, org.openide.util.NbBundle.getMessage(TypesPanel.class, "TypesPanel.filesByCategoryLabel.text")); // NOI18N
        contentParent.add(filesByCategoryLabel);
        contentParent.add(filler1);

        filesByCategoryPanel.setAlignmentX(0.0F);
        filesByCategoryPanel.setMaximumSize(new java.awt.Dimension(32767, 107));
        filesByCategoryPanel.setMinimumSize(new java.awt.Dimension(10, 107));
        filesByCategoryPanel.setPreferredSize(new java.awt.Dimension(400, 107));
        contentParent.add(filesByCategoryPanel);

        scrollParent.setViewportView(contentParent);

        add(scrollParent, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel pieChartRow;
    // End of variables declaration//GEN-END:variables
}
