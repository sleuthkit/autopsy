/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
