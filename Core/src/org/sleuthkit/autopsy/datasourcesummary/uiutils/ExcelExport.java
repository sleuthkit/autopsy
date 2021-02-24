/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 *
 * @author gregd
 */
public class ExcelExport {

    private static final Logger logger = Logger.getLogger(ExcelExport.class.getName());

    public static void main(String[] args) throws IOException {
        // Create a Workbook
        Workbook workbook = new XSSFWorkbook(); // new HSSFWorkbook() for generating `.xls` file

        // Create a Font for styling header cells
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        //headerFont.setFontHeightInPoints((short) 14);

        // Create a CellStyle with the font
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);

        createSheet(workbook, headerCellStyle, GeolocationDTO.TEMPLATE_1, dtos1);
        createSheet(workbook, headerCellStyle, GeolocationDTO.TEMPLATE_2, dtos2);

        // Write the output to a file
        FileOutputStream fileOut = new FileOutputStream("C:\\Users\\gregd\\Desktop\\datasourcesummary-export.xlsx");
        workbook.write(fileOut);
        fileOut.close();

        // Closing the workbook
        workbook.close();
    }

    public static <T, C extends ExcelCellModel> Sheet createSheet(
            Workbook workbook, CellStyle headerCellStyle, TableTemplate<T, C> tableTemplate, List<T> data)
            throws IllegalArgumentException {

        if (workbook == null || tableTemplate == null) {
            throw new IllegalArgumentException("workbook and tableTemplate parameters cannot be null");
        }

        List<ColumnModel<T, C>> columns = tableTemplate.getColumns() != null
                ? tableTemplate.getColumns()
                : Collections.emptyList();

        List<T> safeData = data == null ? Collections.emptyList() : data;

        Sheet sheet = workbook.createSheet(tableTemplate.getTabName());

        // Create a header row
        Row headerRow = sheet.createRow(0);

        // Create header cells
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columns.get(i).getHeaderTitle());
            cell.setCellStyle(headerCellStyle);
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
                        : Optional.of(cellStyles.computeIfAbsent(formatString, (k) -> getCellStyles(workbook, formatString)));

                createCell(row, colNum, cellModel, cellStyle);
            }
        }

        // Resize all columns to fit the content size
        for (int i = 0; i < columns.size(); i++) {
            sheet.autoSizeColumn(i);
        }

        return sheet;
    }

    private static Cell createCell(Row row, int colNum, ExcelCellModel cellModel, Optional<CellStyle> cellStyle) {
        Object cellData = cellModel.getData();

        Cell cell = row.createCell(colNum);
        if (cellData instanceof Calendar) {
            cell.setCellValue((Calendar) cellData);
        } else if (cellData instanceof Date) {
            cell.setCellValue((Date) cellData);
        } else if (cellData instanceof Double) {
            cell.setCellValue((Double) cellData);
        } else if (cellData instanceof LocalDate) {
            cell.setCellValue((LocalDate) cellData);
        } else if (cellData instanceof LocalDateTime) {
            cell.setCellValue((LocalDateTime) cellData);
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

        cellStyle.ifPresent((cs) -> cell.setCellStyle(cs));
        return cell;
    }

    private static <T> CellStyle getCellStyles(Workbook workbook, String formatString) {
        CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(formatString));
        return cellStyle;
    }
}
