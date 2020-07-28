/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.datasourcesummary;

import java.awt.BorderLayout;
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.FileTypeUtils;

/**
 *
 * @author gregd
 */
public class FileTypePieChart extends JPanel {

    private DataSource dataSource;
    private DefaultPieDataset dataset = new DefaultPieDataset();

    public FileTypePieChart() {
        // Create chart
        JFreeChart chart = ChartFactory.createPieChart(
                NbBundle.getMessage(DataSourceSummaryCountsPanel.class, "DataSourceSummaryCountsPanel.byMimeTypeLabel.text"),
                dataset,
                true,
                true,
                false);

        //Format Label
        PieSectionLabelGenerator labelGenerator = new StandardPieSectionLabelGenerator(
                "{0}: {1} ({2})", new DecimalFormat("0"), new DecimalFormat("0.0%"));
        ((PiePlot) chart.getPlot()).setLabelGenerator(labelGenerator);

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        this.setLayout(new BorderLayout());
        this.add(panel, BorderLayout.CENTER);
    }

    DataSource getDataSource() {
        return this.dataSource;
    }

    void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
        this.dataset.clear();

        if (dataSource != null) {
            this.dataset.setValue(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_images_row(),
                    getCount(dataSource, FileTypeUtils.FileTypeCategory.IMAGE));
            this.dataset.setValue(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_videos_row(),
                    getCount(dataSource, FileTypeUtils.FileTypeCategory.VIDEO));
            this.dataset.setValue(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_audio_row(),
                    getCount(dataSource, FileTypeUtils.FileTypeCategory.AUDIO));
            this.dataset.setValue(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_documents_row(),
                    getCount(dataSource, FileTypeUtils.FileTypeCategory.DOCUMENTS));
            this.dataset.setValue(Bundle.DataSourceSummaryCountsPanel_FilesByMimeTypeTableModel_executables_row(),
                    getCount(dataSource, FileTypeUtils.FileTypeCategory.EXECUTABLE));
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
        return DataSourceInfoUtilities.getCountOfFilesForMimeTypes(dataSource, category.getMediaTypes());
    }
}
