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

import java.awt.BorderLayout;
import java.awt.Font;
import javax.swing.JPanel;
import org.sleuthkit.datamodel.DataSource;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;
import org.sleuthkit.autopsy.datasourcesummary.datamodel.DataSourceMimeTypeSummary;
import static org.sleuthkit.autopsy.coreutils.FileTypeUtils.FileTypeCategory;

/**
 * A Pie Chart that shows file mime types in a data source.
 */
class FileTypePieChart extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();
    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));

    private final DefaultPieDataset dataset = new DefaultPieDataset();
    private DataSource dataSource;

    // used for determining mime types that fall in the 'other' category
    private static final Set<String> ALL_CATEGORY_MIME_TYPES = Arrays.asList(
            FileTypeCategory.IMAGE,
            FileTypeCategory.VIDEO,
            FileTypeCategory.AUDIO,
            FileTypeCategory.DOCUMENTS,
            FileTypeCategory.EXECUTABLE)
            .stream()
            .flatMap((cat) -> cat.getMediaTypes().stream())
            .collect(Collectors.toSet());

    /**
     * Default constructor for the pie chart.
     */
    @Messages({
        "DataSourceSummaryCountsPanel.byMimeTypeLabel.text=Files by MIME Type",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.audio.row=Audio",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.documents.row=Documents",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.executables.row=Executables",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.images.row=Images",
        "DataSourceSummaryCountsPanel.FilesByMimeTypeTableModel.videos.row=Videos",
        "DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_other_label=Other",
        "DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_notAnalyzed_label=Not Analyzed"
    })
    FileTypePieChart() {
        // Create chart
        JFreeChart chart = ChartFactory.createPieChart(
                Bundle.DataSourceSummaryCountsPanel_byMimeTypeLabel_text(),
                dataset,
                true,
                true,
                false);

        chart.setBackgroundPaint(null);
        chart.getLegend().setItemFont(DEFAULT_FONT);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        PiePlot plot = ((PiePlot) chart.getPlot());

        //Format Label
        PieSectionLabelGenerator labelGenerator = new StandardPieSectionLabelGenerator(
                "{0}: {1} ({2})", new DecimalFormat("0"), new DecimalFormat("0.0%"));

        plot.setLabelGenerator(labelGenerator);
        plot.setLabelFont(DEFAULT_FONT);

        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        this.setLayout(new BorderLayout());
        this.add(panel, BorderLayout.CENTER);
    }

    /**
     * The datasource currently used as the model with this pie chart.
     *
     * @return The datasource currently being used as the model in this pie
     *         chart.
     */
    DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Sets datasource to visualize in the pie chart.
     *
     * @param dataSource The datasource to use in this pie chart.
     */
    void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        this.dataset.clear();

        if (dataSource != null) {
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_images_row(),
                    this.dataSource, FileTypeCategory.IMAGE);
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_videos_row(),
                    this.dataSource, FileTypeCategory.VIDEO);
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_audio_row(),
                    this.dataSource, FileTypeCategory.AUDIO);
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_documents_row(),
                    this.dataSource, FileTypeCategory.DOCUMENTS);
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_executables_row(),
                    this.dataSource, FileTypeCategory.EXECUTABLE);
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_other_label(),
                    DataSourceMimeTypeSummary.getCountOfFilesNotInMimeTypes(this.dataSource, ALL_CATEGORY_MIME_TYPES));
            addIfPresent(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_notAnalyzed_label(),
                    DataSourceMimeTypeSummary.getCountOfFilesWithNoMimeType(this.dataSource));
        }
    }

    /**
     * Adds count for file type category if there is a value. Uses fields
     * 'dataSource' and 'dataset'.
     *
     * @param label      The label for this pie slice.
     * @param dataSource The data source.
     * @param category   The category for the pie slice.
     */
    private void addIfPresent(String label, DataSource dataSource, FileTypeUtils.FileTypeCategory category) {
        if (dataSource == null) {
            return;
        }

        Long count = getCount(dataSource, category);
        addIfPresent(label, count);
    }

    /**
     * Adds count for a a label if the count is non-null and greater than 0.
     *
     * @param label The label.
     * @param count The count.
     */
    private void addIfPresent(String label, Long count) {
        if (count != null && count > 0) {
            this.dataset.setValue(label, count);
        }
    }

    /**
     * Retrieves the counts of files of a particular mime type for a particular
     * DataSource.
     *
     * @param dataSource The DataSource.
     * @param category   The mime type category.
     *
     * @return The count.
     */
    private static Long getCount(DataSource dataSource, FileTypeUtils.FileTypeCategory category) {
        return DataSourceMimeTypeSummary.getCountOfFilesForMimeTypes(dataSource, category.getMediaTypes());
    }
}
