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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.JLabel;
import org.apache.commons.collections4.CollectionUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.BarChartSeries.BarChartItem;

/**
 * A bar chart panel.
 */
public class BarChartPanel extends AbstractLoadableComponent<List<BarChartSeries>> {

    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();
    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));

    private final ChartMessageOverlay overlay = new ChartMessageOverlay();
    private final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    private final JFreeChart chart;
    private final CategoryPlot plot;

    /**
     * Main constructor assuming null values for all items.
     */
    public BarChartPanel() {
        this(null, null, null);
    }

    /**
     * Main constructor for the pie chart.
     *
     * @param title The title for this pie chart.
     * @param categoryLabel The x-axis label.
     * @param valueLabel The y-axis label.
     */
    public BarChartPanel(String title, String categoryLabel, String valueLabel) {
        this.chart = ChartFactory.createStackedBarChart(
                title,
                categoryLabel,
                valueLabel,
                dataset,
                PlotOrientation.VERTICAL,
                true, false, false);

        // set style to match autopsy components
        chart.setBackgroundPaint(null);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        this.plot = ((CategoryPlot) chart.getPlot());
        this.plot.getRenderer().setDefaultItemLabelFont(DEFAULT_FONT);
        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

        // hide y axis labels
        ValueAxis range = plot.getRangeAxis();
        range.setVisible(false);

        // make sure x axis labels don't get cut off
        plot.getDomainAxis().setMaximumCategoryLabelWidthRatio(10);

        ((BarRenderer) plot.getRenderer()).setBarPainter(new StandardBarPainter());

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        panel.addOverlay(overlay);
        panel.setPopupMenu(null);

        this.setLayout(new BorderLayout());
        this.add(panel, BorderLayout.CENTER);
    }

    /**
     * @return The title for this chart if one exists.
     */
    public String getTitle() {
        return (this.chart == null || this.chart.getTitle() == null)
                ? null
                : this.chart.getTitle().getText();
    }

    /**
     * Sets the title for this pie chart.
     *
     * @param title The title.
     *
     * @return As a utility, returns this.
     */
    public BarChartPanel setTitle(String title) {
        this.chart.getTitle().setText(title);
        return this;
    }

    @Override
    protected void setMessage(boolean visible, String message) {
        this.overlay.setVisible(visible);
        this.overlay.setMessage(message);
    }

    @Override
    protected void setResults(List<BarChartSeries> data) {
        this.dataset.clear();

        if (CollectionUtils.isNotEmpty(data)) {
            for (int s = 0; s < data.size(); s++) {
                BarChartSeries series = data.get(s);
                if (series != null && CollectionUtils.isNotEmpty(series.getItems())) {
                    if (series.getColor() != null) {
                        this.plot.getRenderer().setSeriesPaint(s, series.getColor());
                    }

                    for (int i = 0; i < series.getItems().size(); i++) {
                        BarChartItem bar = series.getItems().get(i);
                        this.dataset.setValue(bar.getValue(), series.getKey(), bar.getKey());
                    }
                }
            }
        }
    }

}
