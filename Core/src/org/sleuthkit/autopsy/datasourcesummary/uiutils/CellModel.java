/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import javax.swing.JLabel;

/**
 * Basic interface for a cell model.
 */
public interface CellModel {

    /**
     * Describes the horizontal alignment.
     */
    public enum HorizontalAlign {
        LEFT(JLabel.LEFT),
        CENTER(JLabel.CENTER),
        RIGHT(JLabel.RIGHT);

        private final int jlabelAlignment;

        /**
         * Constructor for a HorizontalAlign enum.
         *
         * @param jlabelAlignment The corresponding JLabel horizontal alignment
         * number.
         */
        HorizontalAlign(int jlabelAlignment) {
            this.jlabelAlignment = jlabelAlignment;
        }

        /**
         * @return The corresponding JLabel horizontal alignment (i.e.
         * JLabel.LEFT).
         */
        int getJLabelAlignment() {
            return this.jlabelAlignment;
        }
    }

    /**
     * @return The root data object.
     */
    Object getData();

    /**
     * @return The text to be shown in the cell.
     */
    default String getText() {
        Object data = getData();
        return (data == null) ? null : data.toString();
    }

    /**
     * @return The tooltip (if any) to be displayed in the cell.
     */
    String getTooltip();

    /**
     * @return The horizontal alignment for the text in the cell.
     */
    HorizontalAlign getHorizontalAlignment();
}
