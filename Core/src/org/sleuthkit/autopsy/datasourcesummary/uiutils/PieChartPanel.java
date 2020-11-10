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
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JLabel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import org.openide.util.NbBundle.Messages;

/**
 * A pie chart panel.
 */
@Messages({
    "PieChartPanel_noDataLabel=No Data"
})
public class PieChartPanel extends AbstractLoadableComponent<List<PieChartPanel.PieChartItem>> {

    /**
     * An individual pie chart slice in the pie chart.
     */
    public static class PieChartItem {

        private final String label;
        private final double value;
        private final Color color;

        /**
         * Main constructor.
         *
         * @param label The label for this pie slice.
         * @param value The value for this item.
         * @param color The color for the pie slice. Can be null for
         * auto-determined.
         */
        public PieChartItem(String label, double value, Color color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        /**
         * @return The label for this item.
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return The value for this item.
         */
        public double getValue() {
            return value;
        }

        /**
         * @return The color for the pie slice or null for auto-determined.
         */
        public Color getColor() {
            return color;
        }
    }

    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();

    /**
     * It appears that JFreeChart will show nothing if all values are zero. So
     * this is a value close to zero but not to be displayed.
     */
    private static final double NEAR_ZERO = Math.ulp(1d);
    private static final Color NO_DATA_COLOR = Color.WHITE;
    private static final double DEFAULT_CHART_PADDING = .1;

    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));
    private static final PieSectionLabelGenerator DEFAULT_LABEL_GENERATOR
            = new StandardPieSectionLabelGenerator(
                    "{0}: {1} ({2})", new DecimalFormat("#,###"), new DecimalFormat("0.0%"));

    private final ChartMessageOverlay overlay = new ChartMessageOverlay();
    private final DefaultPieDataset dataset = new DefaultPieDataset();
    private final JFreeChart chart;
    private final PiePlot plot;

    /**
     * Main constructor.
     */
    public PieChartPanel() {
        this(null);
    }

    /**
     * Main constructor for the pie chart.
     *
     * @param title The title for this pie chart.
     */
    public PieChartPanel(String title) {
        // Create chart
        this.chart = ChartFactory.createPieChart(
                title,
                dataset,
                false,
                false,
                false);

        chart.setBackgroundPaint(null);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        this.plot = ((PiePlot) chart.getPlot());
        plot.setInteriorGap(DEFAULT_CHART_PADDING);
        plot.setLabelGenerator(DEFAULT_LABEL_GENERATOR);
        plot.setLabelFont(DEFAULT_FONT);
        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

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
    public PieChartPanel setTitle(String title) {
        this.chart.getTitle().setText(title);
        return this;
    }

    @Override
    protected void setMessage(boolean visible, String message) {
        this.overlay.setVisible(visible);
        this.overlay.setMessage(message);
    }

    @Override
    protected void setResults(List<PieChartPanel.PieChartItem> data) {
        this.dataset.clear();
        this.plot.clearSectionPaints(false);

        if (data != null && !data.isEmpty()) {
            for (PieChartPanel.PieChartItem slice : data) {
                this.dataset.setValue(slice.getLabel(), slice.getValue());
                if (slice.getColor() != null) {
                    this.plot.setSectionPaint(slice.getLabel(), slice.getColor());
                }
            }
        } else {
            // show a no data label if no data.
            // this in fact shows a very small number for the value 
            // that should be way below rounding error for formatters
            this.dataset.setValue(Bundle.PieChartPanel_noDataLabel(), NEAR_ZERO);
            this.plot.setSectionPaint(Bundle.PieChartPanel_noDataLabel(), NO_DATA_COLOR);
        }
    }

    /**
     * Shows a message on top of data.
     *
     * @param data The data.
     * @param message The message.
     */
    public synchronized void showDataWithMessage(List<PieChartPanel.PieChartItem> data, String message) {
        setResults(data);
        setMessage(true, message);
        repaint();
    }
}
