/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datasourcesummary.uiutils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.openide.util.NbBundle.Messages;

/**
 *
 * @author gregd
 */
public class ExcelExport {

    public static class ExcelExportException extends Exception {

    }

    public static class WorksheetEnv {

        private final CellStyle headerStyle;
        private final Workbook parentWorkbook;

        public WorksheetEnv(CellStyle headerStyle, Workbook parentWorkbook) {
            this.headerStyle = headerStyle;
            this.parentWorkbook = parentWorkbook;
        }

        public CellStyle getHeaderStyle() {
            return headerStyle;
        }

        public Workbook getParentWorkbook() {
            return parentWorkbook;
        }

    }

    public static interface ExcelSheetExport {

        String getSheetName();

        void renderSheet(Sheet sheet, WorksheetEnv style) throws ExcelExportException;
    }

    public static class ExcelTableExport<T, C extends ExcelCellModel> implements ExcelSheetExport {

        private final String sheetName;
        private final List<ColumnModel<T, C>> columns;
        private final List<T> data;

        public ExcelTableExport(String sheetName, List<ColumnModel<T, C>> columns, List<T> data) {
            this.sheetName = sheetName;
            this.columns = columns;
            this.data = data;
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

        @Override
        public String getSheetName() {
            return sheetName;
        }

        private static <T, C extends ExcelCellModel> void renderSheet(
                Sheet sheet,
                WorksheetEnv worksheetEnv,
                List<ColumnModel<T, C>> columns,
                List<T> data)
                throws ExcelExportException {

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
                            : Optional.of(cellStyles.computeIfAbsent(formatString, (k) -> getCellStyles(worksheetEnv.getParentWorkbook(), formatString)));

                    createCell(row, colNum, cellModel, cellStyle);
                }
            }
        }

        @Override
        public void renderSheet(Sheet sheet, WorksheetEnv style) throws ExcelExportException {
            renderSheet(sheet, style, columns, data);

            // Resize all columns to fit the content size
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }
        }
    }

    private static final Logger logger = Logger.getLogger(ExcelExport.class.getName());
    private static ExcelExport instance = null;

    public static ExcelExport getInstance() {
        if (instance == null) {
            instance = new ExcelExport();
        }

        return instance;
    }

    private ExcelExport() {

    }

    @Messages({
        "# {0} - sheetNumber",
        "ExcelExport_writeExcel_noSheetName=Sheet {0}"
    })
    public void writeExcel(List<ExcelSheetExport> exports, File path) throws IOException, ExcelExportException {
        // Create a Workbook
        Workbook workbook = new XSSFWorkbook(); // new HSSFWorkbook() for generating `.xls` file

        // Create a Font for styling header cells
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        //headerFont.setFontHeightInPoints((short) 14);

        // Create a CellStyle with the font
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);

        WorksheetEnv env = new WorksheetEnv(headerCellStyle, workbook);

        if (exports != null) {
            for (int i = 0; i < exports.size(); i++) {
                ExcelSheetExport export = exports.get(i);
                if (export == null) {
                    continue;
                }

                String sheetName = export.getSheetName();
                if (sheetName == null) {
                    sheetName = Bundle.ExcelExport_writeExcel_noSheetName(i + 1);
                }

                Sheet sheet = workbook.createSheet(sheetName);
                export.renderSheet(sheet, env);
            }
        }

        // Write the output to a file
        FileOutputStream fileOut = new FileOutputStream(path);
        workbook.write(fileOut);
        fileOut.close();

        // Closing the workbook
        workbook.close();
    }
}
