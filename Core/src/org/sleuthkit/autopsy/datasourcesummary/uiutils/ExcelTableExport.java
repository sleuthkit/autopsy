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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelTableExport.ExcelCellModel;

/**
 * An excel sheet export of table data.
 */
public class ExcelTableExport<T, C extends ExcelCellModel> implements ExcelSheetExport, ExcelItemExportable {

    /**
     * Basic interface for a cell model.
     */
    public interface ExcelCellModel extends CellModel {

        /**
         * @return The format string to be used with Apache POI during excel
         * export or null if none necessary.
         */
        String getExcelFormatString();
    }

    private final String sheetName;
    private final List<ColumnModel<T, C>> columns;
    private final List<T> data;

    /**
     * Main constructor.
     *
     * @param sheetName The name of the sheet. NOTE: There can be no duplicates
     * in a workbook.
     * @param columns The columns of the table.
     * @param data The data to export.
     */
    public ExcelTableExport(String sheetName, List<ColumnModel<T, C>> columns, List<T> data) {
        this.sheetName = sheetName;
        this.columns = columns;
        this.data = data;
    }

    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv style) throws ExcelExport.ExcelExportException {
        renderSheet(sheet, style, 1, 1, columns, data);

    }

    @Override
    public int write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
        int rowsWritten = renderSheet(sheet, env, rowStart, colStart, columns, data);
        return rowStart + rowsWritten - 1;
    }

    /**
     * Renders the data into the excel sheet.
     *
     * @param sheet The sheet.
     * @param worksheetEnv The worksheet environment and preferences.
     * @param rowStart The row to start in.
     * @param colStart The column to start in.
     * @param columns The columns.
     * @param data The data.
     * @throws ExcelExportException
     * @return The number of rows (including the header) written.
     */
    private static <T, C extends ExcelCellModel> int renderSheet(
            Sheet sheet, 
            ExcelExport.WorksheetEnv worksheetEnv, 
            int rowStart, 
            int colStart,
            List<ColumnModel<T, C>> columns, List<T> data)
            throws ExcelExport.ExcelExportException {

        List<T> safeData = data == null ? Collections.emptyList() : data;
        // Create a header row
        Row headerRow = sheet.createRow(0);
        // Create header cells
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getHeaderTitle());
            cell.setCellStyle(worksheetEnv.getHeaderStyle());
        }
        // freeze header row
        sheet.createFreezePane(0, 1);
        // Create Cell Style for each column (if one is needed)
        Map<String, CellStyle> cellStyles = new HashMap<>();
        for (int rowNum = 0; rowNum < safeData.size(); rowNum++) {
            T rowData = safeData.get(rowNum);
            Row row = sheet.createRow(rowNum + 1);
            for (int colNum = 0; colNum < columns.size(); colNum++) {
                ColumnModel<T, ? extends ExcelCellModel> colModel = columns.get(colNum);
                ExcelCellModel cellModel = colModel.getCellRenderer().apply(rowData);
                String formatString = cellModel.getExcelFormatString();
                Optional<CellStyle> cellStyle = (formatString == null)
                        ? Optional.empty()
                        : Optional.of(cellStyles.computeIfAbsent(formatString, k -> ExcelExport.createCellStyle(worksheetEnv.getParentWorkbook(), formatString)));
                ExcelExport.createCell(row, colNum + colStart, cellModel, cellStyle);
            }
        }
        
        // Resize all columns to fit the content size
        for (int i = colStart; i < columns.size() + colStart; i++) {
            sheet.autoSizeColumn(i);
        }
        
        return safeData.size() + 1;
    }
}
