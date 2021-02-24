/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.util.function.Function;

/**
 *
 * @author gregd
 */
/**
 * Describes aspects of a column which can be used with getTableModel or
 * getJTablePanel. 'T' represents the object that will represent rows in the
 * table.
 */
public class ColumnModel<T, C extends CellModel> {

    private final String headerTitle;
    private final Function<T, ? extends C> cellRenderer;
    private final Integer width;

    /**
     * Constructor for a DataResultColumnModel.
     *
     * @param headerTitle The title for the column.
     * @param cellRenderer The method that generates a CellModel for the column
     * based on the data.
     */
    public ColumnModel(String headerTitle, Function<T, ? extends C> cellRenderer) {
        this(headerTitle, cellRenderer, null);
    }

    /**
     * Constructor for a DataResultColumnModel.
     *
     * @param headerTitle The title for the column.
     * @param cellRenderer The method that generates a CellModel for the column
     * based on the data.
     * @param width The preferred width of the column.
     */
    public ColumnModel(String headerTitle, Function<T, ? extends C> cellRenderer, Integer width) {
        this.headerTitle = headerTitle;
        this.cellRenderer = cellRenderer;
        this.width = width;
    }

    /**
     * @return The title for the column.
     */
    public String getHeaderTitle() {
        return headerTitle;
    }

    /**
     * @return The method that generates a CellModel for the column based on the
     * data.
     */
    public Function<T, ? extends C> getCellRenderer() {
        return cellRenderer;
    }

    /**
     * @return The preferred width of the column (can be null).
     */
    public Integer getWidth() {
        return width;
    }
}
