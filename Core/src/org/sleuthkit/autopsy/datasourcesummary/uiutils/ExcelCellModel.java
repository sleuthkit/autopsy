/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

/**
 * Basic interface for a cell model.
 */
public interface ExcelCellModel extends CellModel {

    /**
     * @return The format string to be used with Apache POI during excel export
     * or null if none necessary.
     */
    String getExcelFormatString();
}
