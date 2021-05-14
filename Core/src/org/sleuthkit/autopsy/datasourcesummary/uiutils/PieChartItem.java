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

/**
 * An individual pie chart slice in the pie chart.
 */
public class PieChartItem {

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
