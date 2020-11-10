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
import java.util.Collections;
import java.util.List;
import javax.swing.JLabel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StandardBarPainter;
import org.jfree.data.category.DefaultCategoryDataset;

/**
 * A bar chart panel.
 */
public class BarChartPanel extends AbstractLoadableComponent<BarChartPanel.BarChartSeries> {

    public static class BarChartSeries {

        private final Color color;
        private final List<BarChartItem> items;

        public BarChartSeries(Color color, List<BarChartItem> items) {
            this.color = color;
            this.items = (items == null) ? Collections.emptyList() : Collections.unmodifiableList(items);
        }

        public Color getColor() {
            return color;
        }

        public List<BarChartItem> getItems() {
            return items;
        }
    }

    /**
     * An individual pie chart slice in the pie chart.
     */
    public static class BarChartItem {

        private final String label;
        private final double value;

        /**
         * Main constructor.
         *
         * @param label The label for this bar.
         * @param value The value for this item.
         * @param color The color for the bar. Can be null for auto-determined.
         */
        public BarChartItem(String label, double value) {
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

    private static final long serialVersionUID = 1L;

    private static final Font DEFAULT_FONT = new JLabel().getFont();
    private static final Font DEFAULT_HEADER_FONT = new Font(DEFAULT_FONT.getName(), DEFAULT_FONT.getStyle(), (int) (DEFAULT_FONT.getSize() * 1.5));

    private final ChartMessageOverlay overlay = new ChartMessageOverlay();
    private final DefaultCategoryDataset dataset = new DefaultCategoryDataset();
    private final JFreeChart chart;
    private final CategoryPlot plot;

    /**
     * Main constructor.
     */
    public BarChartPanel() {
        this(null, null, null);
    }

    /**
     * Main constructor for the pie chart.
     *
     * @param title The title for this pie chart.
     */
    public BarChartPanel(String title, String categoryLabel, String valueLabel) {
        this.chart = ChartFactory.createBarChart(
                title,
                categoryLabel,
                valueLabel,
                dataset,
                PlotOrientation.VERTICAL,
                false, false, false);

        chart.setBackgroundPaint(null);
        chart.getTitle().setFont(DEFAULT_HEADER_FONT);

        this.plot = ((CategoryPlot) chart.getPlot());
        this.plot.getRenderer().setBaseItemLabelFont(DEFAULT_FONT);
        plot.setBackgroundPaint(null);
        plot.setOutlinePaint(null);

        ValueAxis range = plot.getRangeAxis();
        range.setVisible(false);
        
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

    // only one category for now.
    private static final String DEFAULT_CATEGORY = "";

    private static class OrderedKey implements Comparable<OrderedKey> {

        private final Object keyValue;
        private final int keyIndex;

        OrderedKey(Object keyValue, int keyIndex) {
            this.keyValue = keyValue;
            this.keyIndex = keyIndex;
        }

        Object getKeyValue() {
            return keyValue;
        }

        int getKeyIndex() {
            return keyIndex;
        }

        @Override
        public int compareTo(OrderedKey o) {
            if (o == null) {
                return 1;
            }

            return Integer.compare(this.getKeyIndex(), o.getKeyIndex());
        }

        @Override
        public String toString() {
            return this.getKeyValue() == null ? null : this.getKeyValue().toString();
        }
    }

    @Override
    protected void setResults(BarChartPanel.BarChartSeries data) {
        this.dataset.clear();

        if (data != null && data.getItems() != null && !data.getItems().isEmpty()) {
            if (data.getColor() != null) {
                this.plot.getRenderer().setSeriesPaint(0, data.getColor());
            }

            for (int i = 0; i < data.getItems().size(); i++) {
                BarChartItem bar = data.getItems().get(i);
                this.dataset.setValue(bar.getValue(), DEFAULT_CATEGORY, new OrderedKey(bar.getLabel(), i));
            }
        }
    }

    /**
     * Shows a message on top of data.
     *
     * @param data The data.
     * @param message The message.
     */
    public synchronized void showDataWithMessage(BarChartPanel.BarChartSeries data, String message) {
        setResults(data);
        setMessage(true, message);
        repaint();
    }
}
