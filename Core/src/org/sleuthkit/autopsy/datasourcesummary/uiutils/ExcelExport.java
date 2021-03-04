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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openide.util.NbBundle.Messages;

/**
 * Class for handling Excel exporting.
 */
public class ExcelExport {

    /**
     * Exception thrown in the event of an excel export issue.
     */
    public static class ExcelExportException extends Exception {

        /**
         * Constructor.
         *
         * @param string The message.
         */
        public ExcelExportException(String string) {
            super(string);
        }

        /**
         * Constructor.
         *
         * @param string The message.
         * @param thrwbl The inner exception.
         */
        public ExcelExportException(String string, Throwable thrwbl) {
            super(string, thrwbl);
        }
    }

    /**
     * Class detailing aspects of the worksheet.
     */
    public static class WorksheetEnv {

        private final CellStyle headerStyle;
        private final Workbook parentWorkbook;

        /**
         * Main constructor.
         *
         * @param headerStyle The cell style to use for headers.
         * @param parentWorkbook The parent workbook.
         */
        WorksheetEnv(CellStyle headerStyle, Workbook parentWorkbook) {
            this.headerStyle = headerStyle;
            this.parentWorkbook = parentWorkbook;
        }

        /**
         * Returns the cell style to use for headers.
         *
         * @return The cell style to use for headers.
         */
        public CellStyle getHeaderStyle() {
            return headerStyle;
        }

        /**
         * Returns the parent workbook.
         *
         * @return The parent workbook.
         */
        public Workbook getParentWorkbook() {
            return parentWorkbook;
        }
    }

    /**
     * An item to be exported as a sheet during export.
     */
    public static interface ExcelSheetExport {

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

    private static ExcelExport instance = null;

    /**
     * Retrieves a singleton instance of this class.
     * @return The instance.
     */
    public static ExcelExport getInstance() {
        if (instance == null) {
            instance = new ExcelExport();
        }

        return instance;
    }

    private ExcelExport() {

    }

    /**
     * Writes the exports to a workbook.
     * @param exports The sheets to export.
     * @param path The path to the output file.
     * @throws IOException
     * @throws ExcelExportException 
     */
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
