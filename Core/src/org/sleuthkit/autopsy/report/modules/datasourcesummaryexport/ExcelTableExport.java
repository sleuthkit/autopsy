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
package org.sleuthkit.autopsy.report.modules.datasourcesummaryexport;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelExportException;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelExport.ExcelSheetExport;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ExcelItemExportable;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.ExcelSpecialFormatExport.ItemDimensions;

/**
 * An excel sheet export of table data.
 */
class ExcelTableExport<T, C extends CellModel> implements ExcelSheetExport, ExcelItemExportable {

    private final String sheetName;
    private final List<ColumnModel<T, C>> columns;
    private final List<T> data;
    private final int columnIndent;

    /**
     * Main constructor.
     *
     * @param sheetName The name of the sheet. NOTE: There can be no duplicates
     * in a workbook.
     * @param columns The columns of the table.
     * @param data The data to export.
     */
    ExcelTableExport(String sheetName, List<ColumnModel<T, C>> columns, List<T> data) {
        this(sheetName, columns, data, 0);
    }

    /**
     * Main constructor.
     *
     * @param sheetName The name of the sheet. NOTE: There can be no duplicates
     * in a workbook.
     * @param columns The columns of the table.
     * @param data The data to export.
     * @param columnIndent The column indent.
     */
    ExcelTableExport(String sheetName, List<ColumnModel<T, C>> columns, List<T> data, int columnIndent) {
        this.sheetName = sheetName;
        this.columns = columns;
        this.data = data;
        this.columnIndent = columnIndent;
    }

    @Override
    public String getSheetName() {
        return sheetName;
    }

    @Override
    public void renderSheet(Sheet sheet, ExcelExport.WorksheetEnv style) throws ExcelExport.ExcelExportException {
        renderSheet(sheet, style, 0, columnIndent, columns, data);

        // Resize all columns to fit the content size
        for (int i = 0; i < columns.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        // freeze header row
        sheet.createFreezePane(0, 1);
    }

    @Override
    public ItemDimensions write(Sheet sheet, int rowStart, int colStart, ExcelExport.WorksheetEnv env) throws ExcelExportException {
        int columnStart = columnIndent + colStart;
        int rowsWritten = renderSheet(sheet, env, rowStart, columnStart, columns, data);
        return new ItemDimensions(rowStart, columnStart, rowStart + rowsWritten - 1, this.columns == null ? columnStart : columnStart + this.columns.size());
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
    private static <T, C extends CellModel> int renderSheet(
            Sheet sheet,
            ExcelExport.WorksheetEnv worksheetEnv,
            int rowStart,
            int colStart,
            List<ColumnModel<T, C>> columns, List<T> data)
            throws ExcelExport.ExcelExportException {

        List<T> safeData = data == null ? Collections.emptyList() : data;
        // Create a header row
        Row headerRow = sheet.createRow(rowStart);
        // Create header cells
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i + colStart);
            cell.setCellValue(columns.get(i).getHeaderTitle());
            cell.setCellStyle(worksheetEnv.getHeaderStyle());
        }

        // Create Cell Style for each column (if one is needed)
        for (int rowNum = 0; rowNum < safeData.size(); rowNum++) {
            T rowData = safeData.get(rowNum);
            Row row = sheet.createRow(rowNum + rowStart + 1);
            for (int colNum = 0; colNum < columns.size(); colNum++) {
                ColumnModel<T, ? extends CellModel> colModel = columns.get(colNum);
                CellModel cellModel = colModel.getCellRenderer().apply(rowData);
                ExcelExport.createCell(worksheetEnv, row, colNum + colStart, cellModel, Optional.empty());
            }
        }

        return safeData.size() + 1;
    }
}
