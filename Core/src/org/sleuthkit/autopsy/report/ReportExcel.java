/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.logging.Level;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

 class ReportExcel implements TableReportModule {
    private static final Logger logger = Logger.getLogger(ReportExcel.class.getName());
    private static ReportExcel instance;
    
    private Workbook wb;
    private Sheet sheet;
    private CellStyle titleStyle;
    private CellStyle setStyle;
    private CellStyle elementStyle;
    private int rowIndex = 0;
    private int sheetColCount = 0;
    private int artifactsCount = 0;
    private String reportPath;
    
    // Get the default instance of this report
    public static synchronized ReportExcel getDefault() {
        if (instance == null) {
            instance = new ReportExcel();
        }
        return instance;
    }
    
    // Hidden constructor
    private ReportExcel() {
    }

    /**
     * Start the Excel report by creating the Workbook, initializing styles,
     * and writing the summary.
     * @param path path to save the report
     */
    @Override
    public void startReport(String path) {        
        // Set the path and save it for when the report is written to disk.
        this.reportPath = path + getFilePath();
                
        // Make a workbook.
        wb = new XSSFWorkbook();
        
        // Create some cell styles.
        // TODO: The commented out cell style settings below do not work as desired when
        // the output file is loaded by MS Excel or OfficeLibre. The font height and weight
        // settings only work as expected when the output file is loaded by OfficeLibre.
        // The alignment and text wrap settings appear to have no effect.
        titleStyle = wb.createCellStyle();
//        titleStyle.setBorderBottom((short) 1);
        Font titleFont = wb.createFont();
        titleFont.setFontHeightInPoints((short) 12);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(CellStyle.ALIGN_LEFT);
        titleStyle.setWrapText(true);
        
        setStyle = wb.createCellStyle();
        Font setFont = wb.createFont();
        setFont.setFontHeightInPoints((short) 14);
        setFont.setBoldweight((short) 10);
        setStyle.setFont(setFont);
        setStyle.setAlignment(CellStyle.ALIGN_LEFT);
        setStyle.setWrapText(true);
        
        elementStyle = wb.createCellStyle();
//        elementStyle.setF illBackgroundColor(HSSFColor.LIGHT_YELLOW.index);
        Font elementFont = wb.createFont();
        elementFont.setFontHeightInPoints((short) 14);
        elementStyle.setFont(elementFont);
        elementStyle.setAlignment(CellStyle.ALIGN_LEFT);
        elementStyle.setWrapText(true);
                
        writeSummaryWorksheet();
    }

    /**
     * Write the Workbook to a file and end the report.
     */
    @Override
    public void endReport() {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(reportPath);
            wb.write(out);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to write Excel report.", ex);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                }
            }
        }
    }
    
    /**
     * Start a new worksheet for the given data type.
     * Note: This method is a temporary workaround to avoid modifying the TableReportModule interface.     
     * 
     * @param name Name of the data type
     * @param comment Comment on the data type, may be the empty string
     */
    @Override
    public void startDataType(String name, String description) {
        // Create a worksheet for the data type (assumed to be an artifact type).
        name = escapeForExcel(name);
        sheet = wb.createSheet(name);
        sheet.setAutobreaks(true);
        rowIndex = 0;
        artifactsCount = 0;
        
        // Add a title row to the worksheet.
        Row row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(name);
        ++rowIndex;
        
        // Add an artifacts count row. The actual count will be filled in later.
        row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.numAartifacts.text"));
        ++rowIndex;

        // Add a comment row, if a comment was supplied.
        if (!description.isEmpty()) {
            row = sheet.createRow(rowIndex);
            row.setRowStyle(setStyle);
            row.createCell(0).setCellValue(description);
            ++rowIndex;
        }
        
        // Add an empty row as a separator.
        sheet.createRow(rowIndex);
        ++rowIndex;
                
        // There will be at least two columns, one each for the artifacts count and its label.
        sheetColCount = 2;
    }    
    
    /**
     * End the current data type and sheet.
     */
    @Override
    public void endDataType() {
        // Fill in the artifact count cell in row 0.
        Row row = sheet.getRow(1);
        row.setRowStyle(setStyle);
        row.createCell(1).setCellValue(artifactsCount);
    
        // Now that the sheet is complete, size the columns to the content.
        for (int i = 0; i < sheetColCount; ++i) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Start a new set for the current data type.
     * @param setName name of the set
     */
    @Override
    public void startSet(String setName) {
        setName = escapeForExcel(setName);
        Row row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(setName);
        ++rowIndex;
    }

    /**
     * End the current set.
     */
    @Override
    public void endSet() {
        // Add an empty row as a separator.
        sheet.createRow(rowIndex);
        ++rowIndex;
    }

    @Override
    public void addSetIndex(List<String> sets) {
        // Ignored in Excel Report
    }

    /**
     * Add an element to the set
     * @param elementName element name
     */
    @Override
    public void addSetElement(String elementName) {
        elementName = escapeForExcel(elementName);
        Row row = sheet.createRow(rowIndex);
        row.setRowStyle(elementStyle);
        row.createCell(0).setCellValue(elementName);
        ++rowIndex;
    }

    /**
     * Label the top of this sheet with the table column names.
     * @param titles column names
     */
    @Override
    public void startTable(List<String> titles) {
        int tableColCount = 0;
        Row row = sheet.createRow(rowIndex);
        row.setRowStyle(titleStyle);
        for (int i=0; i<titles.size(); i++) {
            row.createCell(i).setCellValue(titles.get(i));
            ++tableColCount;
        }
        ++rowIndex;
        
        // Keep track of the number of columns with data in them for later column auto-sizing.
        if (tableColCount > sheetColCount) {
            sheetColCount = tableColCount;
        }
    }

    @Override
    public void endTable() {
        // Add an empty row as a separator.
        sheet.createRow(rowIndex);
        ++rowIndex;
    }

    /**
     * Add a row of information to the report.
     * @param row cells to add
     */
    @Override
    public void addRow(List<String> rowData) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < rowData.size(); ++i) {
            row.createCell(i).setCellValue(rowData.get(i));
        }
        ++rowIndex;
        ++artifactsCount;
    }

    /**
     * Return the given long date as a String.
     * @param date as a long
     * @return String date
     */
    @Override
    public String dateToString(long date) {
        SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        return sdf.format(new java.util.Date(date * 1000));
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "ReportExcel.getName.text");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(this.getClass(), "ReportExcel.getDesc.text");
    }

    @Override
    public String getExtension() {
        return ".xlsx";
    }

    @Override
    public String getFilePath() {
        return NbBundle.getMessage(this.getClass(), "ReportExcel.getFilePath.text");
    }
    
    /**
     * Escape special chars for Excel that would cause errors/hangs in generating report
     * The following are not valid for sheet names: ? / \ * :
     * @param text
     * @return 
     */
    private static String escapeForExcel(String text) {
         return text.replaceAll("[\\/\\:\\?\\*\\\\]", "_");
    }
    
    private void writeSummaryWorksheet() {
        sheet = wb.createSheet(NbBundle.getMessage(this.getClass(), "ReportExcel.sheetName.text"));
        rowIndex = 0;
        
        Row row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.cellVal.summary"));
        ++rowIndex;

        sheet.createRow(rowIndex);
        ++rowIndex;
                                
        Case currentCase = Case.getCurrentCase();        
               
        row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.cellVal.caseName"));
        row.createCell(1).setCellValue(currentCase.getName());
        ++rowIndex;

        row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.cellVal.caseNum"));
        row.createCell(1).setCellValue(currentCase.getNumber());
        ++rowIndex;

        row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.cellVal.examiner"));
        row.createCell(1).setCellValue(currentCase.getExaminer());
        ++rowIndex;

        row = sheet.createRow(rowIndex);
        row.setRowStyle(setStyle);
        row.createCell(0).setCellValue(NbBundle.getMessage(this.getClass(), "ReportExcel.cellVal.numImages"));
        row.createCell(1).setCellValue(currentCase.getImageIDs().length);
        ++rowIndex;
        
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);        
    }
}
