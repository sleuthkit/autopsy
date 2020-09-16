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
import java.awt.Font;
import java.awt.Graphics2D;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JLabel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.PieSectionLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;
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

        /**
         * Main constructor.
         *
         * @param label The label for this pie slice.
         * @param value The value for this item.
         */
        public PieChartItem(String label, double value) {
            this.label = label;
            this.value = value;
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
    }

    /**
     * A JFreeChart message overlay that can show a message for the purposes of
     * the LoadableComponent.
     */
    private static class MessageOverlay extends AbstractOverlay implements Overlay {

        private static final long serialVersionUID = 1L;
        private final BaseMessageOverlay overlay = new BaseMessageOverlay();

        /**
         * Sets this layer visible when painted. In order to be shown in UI,
         * this component needs to be repainted.
         *
         * @param visible Whether or not it is visible.
         */
        void setVisible(boolean visible) {
            overlay.setVisible(visible);
        }

        /**
         * Sets the message to be displayed in the child jlabel.
         *
         * @param message The message to be displayed.
         */
        void setMessage(String message) {
            overlay.setMessage(message);
        }

        @Override
        public void paintOverlay(Graphics2D gd, ChartPanel cp) {
            overlay.paintOverlay(gd, cp.getWidth(), cp.getHeight());
        }

    }

    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();
    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));
    private static final PieSectionLabelGenerator DEFAULT_LABEL_GENERATOR
            = new StandardPieSectionLabelGenerator(
                    "{0}: {1} ({2})", new DecimalFormat("#,###"), new DecimalFormat("0.0%"));

    private final MessageOverlay overlay = new MessageOverlay();
    private final DefaultPieDataset dataset = new DefaultPieDataset();
    private final JFreeChart chart;

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
                true,
                true,
                false);

        chart.setBackgroundPaint(null);
        chart.getLegend().setItemFont(DEFAULT_FONT);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        // don't show a legend by default
        chart.removeLegend();

        PiePlot plot = ((PiePlot) chart.getPlot());

        plot.setLabelGenerator(DEFAULT_LABEL_GENERATOR);
        plot.setLabelFont(DEFAULT_FONT);
        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

        // Create Panel
        ChartPanel panel = new ChartPanel(chart);
        panel.addOverlay(overlay);
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
        if (data != null && !data.isEmpty()) {
            for (PieChartPanel.PieChartItem slice : data) {
                this.dataset.setValue(slice.getLabel(), slice.getValue());
            }
        } else {
            // show a no data label if no data.
            this.dataset.setValue(Bundle.PieChartPanel_noDataLabel(), 0);
        }
    }

    /**
     * Shows a message on top of data.
     *
     * @param data    The data.
     * @param message The message.
     */
    public synchronized void showDataWithMessage(List<PieChartPanel.PieChartItem> data, String message) {
        setResults(data);
        setMessage(true, message);
        repaint();
    }
}
