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

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.sleuthkit.autopsy.datasourcesummary.uiutils.ExcelTableExport.ExcelCellModel;

/**
 * An excel sheet export of table data.
 */
public class ExcelTableExport<T, C extends ExcelCellModel> implements ExcelExport.ExcelSheetExport {

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
        renderSheet(sheet, style, columns, data);
        // Resize all columns to fit the content size
        for (int i = 0; i < columns.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Renders the data into the excel sheet.
     *
     * @param sheet The sheet.
     * @param worksheetEnv The worksheet environment and preferences.
     * @param columns The columns.
     * @param data The data.
     * @throws ExcelExportException
     */
    private static <T, C extends ExcelCellModel> void renderSheet(
            Sheet sheet, ExcelExport.WorksheetEnv worksheetEnv, List<ColumnModel<T, C>> columns, List<T> data)
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
                        : Optional.of(cellStyles.computeIfAbsent(formatString, k -> createCellStyle(worksheetEnv.getParentWorkbook(), formatString)));
                createCell(row, colNum, cellModel, cellStyle);
            }
        }
    }

    /**
     * Create a cell style in the workbook with the given format string.
     *
     * @param workbook The workbook.
     * @param formatString The format string.
     * @return The cell style.
     */
    private static <T> CellStyle createCellStyle(Workbook workbook, String formatString) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(formatString));
        return cellStyle;
    }

    /**
     * Creates an excel cell given the model.
     *
     * @param row The row in the excel document.
     * @param colNum The column number (not zero-indexed).
     * @param cellModel The model for the cell.
     * @param cellStyle The style to use.
     * @return The created cell.
     */
    private static Cell createCell(Row row, int colNum, ExcelCellModel cellModel, Optional<CellStyle> cellStyle) {
        Object cellData = cellModel.getData();
        Cell cell = row.createCell(colNum);
        if (cellData instanceof Calendar) {
            cell.setCellValue((Calendar) cellData);
        } else if (cellData instanceof Date) {
            cell.setCellValue((Date) cellData);
        } else if (cellData instanceof Double) {
            cell.setCellValue((Double) cellData);
        } else if (cellData instanceof String) {
            cell.setCellValue((String) cellData);
        } else if (cellData instanceof Short) {
            cell.setCellValue((Short) cellData);
        } else if (cellData instanceof Integer) {
            cell.setCellValue((Integer) cellData);
        } else if (cellData instanceof Long) {
            cell.setCellValue((Long) cellData);
        } else if (cellData instanceof Float) {
            cell.setCellValue((Float) cellData);
        } else {
            cell.setCellValue(cellModel.getText());
        }
        cellStyle.ifPresent(cs -> cell.setCellStyle(cs));
        return cell;
    }

}
