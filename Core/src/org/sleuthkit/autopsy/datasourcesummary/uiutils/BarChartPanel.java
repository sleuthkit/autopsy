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
import java.awt.Color;
import java.awt.Font;
import java.util.Collections;
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

/**
 * A bar chart panel.
 */
public class BarChartPanel extends AbstractLoadableComponent<List<BarChartPanel.BarChartSeries>> {

    /**
     * Represents a series in a bar chart where all items pertain to one
     * category.
     */
    public static class BarChartSeries {

        private final Comparable<?> key;
        private final Color color;
        private final List<BarChartItem> items;

        /**
         * Main constructor.
         *
         * @param key   The key.
         * @param color The color for this series.
         * @param items The bars to be displayed for this series.
         */
        public BarChartSeries(Comparable<?> key, Color color, List<BarChartItem> items) {
            this.key = key;
            this.color = color;
            this.items = (items == null) ? Collections.emptyList() : Collections.unmodifiableList(items);
        }

        /**
         * @return The color for this series.
         */
        public Color getColor() {
            return color;
        }

        /**
         * @return The bars to be displayed for this series.
         */
        public List<BarChartItem> getItems() {
            return items;
        }

        /**
         * @return The key for this item.
         */
        public Comparable<?> getKey() {
            return key;
        }
    }

    /**
     * An individual bar to be displayed in the bar chart.
     */
    public static class BarChartItem {

        private final Comparable<?> key;
        private final double value;

        /**
         * Main constructor.
         *
         * @param key   The key.
         * @param value The value for this item.
         */
        public BarChartItem(Comparable<?> key, double value) {
            this.key = key;
            this.value = value;
        }

        /**
         * @return The key for this item.
         */
        public Comparable<?> getKey() {
            return key;
        }

        /**
         * @return The value for this item.
         */
        public double getValue() {
            return value;
        }
    }

    /**
     * JFreeChart bar charts don't preserve the order of bars provided to the
     * chart, but instead uses the comparable nature to order items. This
     * provides order using a provided index as well as the value for the axis.
     */
    public static class OrderedKey implements Comparable<OrderedKey> {

        private final Object keyValue;
        private final int keyIndex;

        /**
         * Main constructor.
         *
         * @param keyValue The value for the key to be displayed in the domain
         * axis.
         * @param keyIndex The index at which it will be displayed.
         */
        public OrderedKey(Object keyValue, int keyIndex) {
            this.keyValue = keyValue;
            this.keyIndex = keyIndex;
        }

        /**
         * @return The value for the key to be displayed in the domain axis.
         */
        Object getKeyValue() {
            return keyValue;
        }

        /**
         * @return The index at which it will be displayed.
         */
        int getKeyIndex() {
            return keyIndex;
        }

        @Override
        public int compareTo(OrderedKey o) {
            // this will have a higher value than null.
            if (o == null) {
                return 1;
            }

            // compare by index
            return Integer.compare(this.getKeyIndex(), o.getKeyIndex());
        }

        @Override
        public int hashCode() {
            int hash = 3;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final OrderedKey other = (OrderedKey) obj;
            if (this.keyIndex != other.keyIndex) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            // use toString on the key.
            return this.getKeyValue() == null ? null : this.getKeyValue().toString();
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
        this.plot.getRenderer().setBaseItemLabelFont(DEFAULT_FONT);
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
    protected void setResults(List<BarChartPanel.BarChartSeries> data) {
        this.dataset.clear();

        if (CollectionUtils.isNotEmpty(data)) {
            for (int s = 0; s < data.size(); s++) {
                BarChartPanel.BarChartSeries series = data.get(s);
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
