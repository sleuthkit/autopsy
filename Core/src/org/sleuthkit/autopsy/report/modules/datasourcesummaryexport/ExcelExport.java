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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.report.modules.datasourcesummaryexport.CellModel.HorizontalAlign;

/**
 * Class for handling Excel exporting.
 */
class ExcelExport {

    /**
     * Exception thrown in the event of an excel export issue.
     */
    static class ExcelExportException extends Exception {

        /**
         * Constructor.
         *
         * @param string The message.
         */
        ExcelExportException(String string) {
            super(string);
        }

        /**
         * Constructor.
         *
         * @param string The message.
         * @param thrwbl The inner exception.
         */
        ExcelExportException(String string, Throwable thrwbl) {
            super(string, thrwbl);
        }
    }

    /**
     * A cell style key that can be used with the WorksheetEnv to generate a
     * cell style to be used in a POI excel document.
     */
    static class CellStyleKey {

        private final String formatString;
        private final CellStyle cellStyle;
        private final HorizontalAlign alignment;

        /**
         * Main constructor.
         *
         * @param formatString The format string or null if no special
         * formatting.
         * @param cellStyle The base cell style or null if default is to be
         * used.
         * @param alignment The horizontal alignment or null if default is to be
         * used.
         */
        CellStyleKey(String formatString, CellStyle cellStyle, HorizontalAlign alignment) {
            this.formatString = formatString;
            this.cellStyle = cellStyle;
            this.alignment = alignment;
        }

        /**
         * @return The format string or null if no special formatting.
         */
        String getFormatString() {
            return formatString;
        }

        /**
         * @return The base cell style or null if default is to be used.
         */
        CellStyle getCellStyle() {
            return cellStyle;
        }

        /**
         * @return The horizontal alignment or null if default is to be used.
         */
        HorizontalAlign getAlignment() {
            return alignment;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 29 * hash + Objects.hashCode(this.formatString);
            hash = 29 * hash + Objects.hashCode(this.cellStyle);
            hash = 29 * hash + Objects.hashCode(this.alignment);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CellStyleKey other = (CellStyleKey) obj;
            if (!Objects.equals(this.formatString, other.formatString)) {
                return false;
            }
            if (!Objects.equals(this.cellStyle, other.cellStyle)) {
                return false;
            }
            if (this.alignment != other.alignment) {
                return false;
            }
            return true;
        }
    }

    /**
     * Class detailing aspects of the worksheet.
     */
    static class WorksheetEnv {

        private final CellStyle headerStyle;
        private final Workbook parentWorkbook;
        private final CellStyle defaultStyle;

        // maps a data format string / original cell style combination to a created cell style
        private final Map<CellStyleKey, CellStyle> cellStyleCache = new HashMap<>();

        /**
         * Main constructor.
         *
         * @param headerStyle The cell style to use for headers.
         * @param defaultStyle The cell style to use as a default.
         * @param parentWorkbook The parent workbook.
         */
        WorksheetEnv(CellStyle headerStyle, CellStyle defaultStyle, Workbook parentWorkbook) {
            this.headerStyle = headerStyle;
            this.defaultStyle = defaultStyle;
            this.parentWorkbook = parentWorkbook;
        }

        /**
         * Returns a cell style signified by the given cell style key. If the
         * key is already present, a cached version is returned.
         *
         * @param cellStyleKey The key.
         * @return The cell style representing this key.
         */
        CellStyle getCellStyle(CellStyleKey cellStyleKey) {
            return cellStyleCache.computeIfAbsent(cellStyleKey, (pair) -> {
                CellStyle computed = this.parentWorkbook.createCellStyle();
                computed.cloneStyleFrom(cellStyleKey.getCellStyle() == null ? defaultStyle : cellStyleKey.getCellStyle());

                if (cellStyleKey.getAlignment() != null) {
                    computed.setAlignment(cellStyleKey.getAlignment().getPoiAlignment());
                }

                if (cellStyleKey.getFormatString() != null) {
                    computed.setDataFormat(this.parentWorkbook.getCreationHelper().createDataFormat().getFormat(cellStyleKey.getFormatString()));
                }
                return computed;
            });
        }

        /**
         * Returns the cell style to use for headers.
         *
         * @return The cell style to use for headers.
         */
        CellStyle getHeaderStyle() {
            return headerStyle;
        }

        /**
         * Returns the cell style for default items.
         *
         * @return The cell style for default items.
         */
        CellStyle getDefaultCellStyle() {
            return defaultStyle;
        }

        /**
         * Returns the parent workbook.
         *
         * @return The parent workbook.
         */
        Workbook getParentWorkbook() {
            return parentWorkbook;
        }
    }

    /**
     * An item to be exported as a sheet during export.
     */
    static interface ExcelSheetExport {

        /**
         * Returns the name of the sheet to use with this item.
         *
         * NOTE: there can be no duplicates in a workbook.
         *
         * @return The name of the sheet to use with this item.
         */
        String getSheetName();

        /**
         * Renders this item to an excel worksheet.
         *
         * @param sheet The worksheet.
         * @param env The environment and preferences to use while exporting.
         * @throws ExcelExportException
         */
        void renderSheet(Sheet sheet, WorksheetEnv env) throws ExcelExportException;
    }

    private ExcelExport() {
    }

    /**
     * Writes the exports to a workbook.
     *
     * @param exports The sheets to export.
     * @param path The path to the output file.
     * @throws IOException
     * @throws ExcelExportException
     */
    @Messages({
        "# {0} - sheetNumber",
        "ExcelExport_writeExcel_noSheetName=Sheet {0}"
    })
    static void writeExcel(List<ExcelSheetExport> exports, File path) throws IOException, ExcelExportException {
        // Create a Workbook
        Workbook workbook = new XSSFWorkbook(); // new HSSFWorkbook() for generating `.xls` file

        // Create a Font for styling header cells
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        //headerFont.setFontHeightInPoints((short) 14);

        // Create a CellStyle with the font
        HorizontalAlignment alignment = HorizontalAlignment.LEFT;
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFont(headerFont);
        headerCellStyle.setAlignment(alignment);

        CellStyle defaultCellStyle = workbook.createCellStyle();
        defaultCellStyle.setAlignment(alignment);

        WorksheetEnv env = new WorksheetEnv(headerCellStyle, defaultCellStyle, workbook);

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

    /**
     * Creates an excel cell given the model.
     *
     * @param env The work sheet environment including the workbook.
     * @param row The row in the excel document.
     * @param colNum The column number (not zero-indexed).
     * @param cellModel The model for the cell.
     * @param cellStyle The style to use.
     * @return The created cell.
     */
    static Cell createCell(WorksheetEnv env, Row row, int colNum, CellModel cellModel, Optional<CellStyle> cellStyle) {
        CellStyle cellStyleToUse = cellStyle.orElse(env.getDefaultCellStyle());

        if (cellModel.getExcelFormatString() != null || cellModel.getHorizontalAlignment() != null) {
            cellStyleToUse = env.getCellStyle(new CellStyleKey(cellModel.getExcelFormatString(), cellStyleToUse, cellModel.getHorizontalAlignment()));
        }

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
        cell.setCellStyle(cellStyleToUse);
        return cell;
    }
}
