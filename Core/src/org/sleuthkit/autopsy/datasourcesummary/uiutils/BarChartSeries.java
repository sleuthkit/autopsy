/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/**
 * Represents a series in a bar chart where all items pertain to one category.
 */
public class BarChartSeries {

    /**
     * An individual bar to be displayed in the bar chart.
     */
    public static class BarChartItem {

        private final Comparable<?> key;
        private final double value;

        /**
         * Main constructor.
         *
         * @param key The key.
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
    private final Comparable<?> key;
    private final Color color;
    private final List<BarChartItem> items;

    /**
     * Main constructor.
     *
     * @param key The key.
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
