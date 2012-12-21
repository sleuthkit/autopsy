/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;

public class ReportExcel implements TableReportModule {
    private static final Logger logger = Logger.getLogger(ReportExcel.class.getName());
    private static ReportExcel instance;
    private Case currentCase;
    
    private Workbook wb;
    private Sheet sheet;
    private CellStyle titleStyle;
    private CellStyle setStyle;
    private CellStyle elementStyle;
    private int rowCount = 2;
    
    private Map<String, Integer> dataTypes;
    private String currentDataType;
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
        currentCase = Case.getCurrentCase();
        
        wb = new XSSFWorkbook();
        
        titleStyle = wb.createCellStyle();
        titleStyle.setBorderBottom((short) 1);
        Font titleFont = wb.createFont();
        titleFont.setFontHeightInPoints((short) 12);
        titleStyle.setFont(titleFont);
        
        setStyle = wb.createCellStyle();
        Font setFont = wb.createFont();
        setFont.setFontHeightInPoints((short) 14);
        setFont.setBoldweight((short) 10);
        setStyle.setFont(setFont);
        
        elementStyle = wb.createCellStyle();
        elementStyle.setFillBackgroundColor(HSSFColor.LIGHT_YELLOW.index);
        Font elementFont = wb.createFont();
        elementFont.setFontHeightInPoints((short) 14);
        elementStyle.setFont(elementFont);
        
        dataTypes = new TreeMap<String, Integer>();
        this.reportPath = path + getFilePath();
        
        // Write the summary
        sheet = wb.createSheet("Summary");
        sheet.createRow(0).createCell(0).setCellValue("Case Name:");
        sheet.getRow(0).createCell(1).setCellValue(currentCase.getName());
        sheet.createRow(1).createCell(0).setCellValue("Case Number:");
        sheet.getRow(1).createCell(1).setCellValue(currentCase.getNumber());
        sheet.createRow(2).createCell(0).setCellValue("Examiner:");
        sheet.getRow(2).createCell(1).setCellValue(currentCase.getExaminer());
        sheet.createRow(3).createCell(0).setCellValue("# of Images:");
        sheet.getRow(3).createCell(1).setCellValue(currentCase.getImageIDs().length);
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
     * Start a new sheet for the given data type.
     * @param title data type name
     */
    @Override
    public void startDataType(String title) {
        sheet = wb.createSheet(title);
        sheet.setAutobreaks(true);
        currentDataType = title;
    }

    /**
     * End the current data type and sheet.
     */
    @Override
    public void endDataType() {
        Row temp = sheet.createRow(0);
        temp.createCell(0).setCellValue("Number of " + currentDataType + " artifacts:");
        temp.createCell(1).setCellValue(rowCount);
        
        dataTypes.put(currentDataType, rowCount);
        rowCount = 2;
    }

    /**
     * Start a new set for the current data type.
     * @param setName name of the set
     */
    @Override
    public void startSet(String setName) {
        Row temp = sheet.createRow(rowCount);
        temp.setRowStyle(setStyle);
        temp.createCell(0).setCellValue(setName);
        rowCount++;
    }

    /**
     * End the current set.
     */
    @Override
    public void endSet() {
        rowCount++; // Put a space between the sets
    }

    // Ignored in Excel Report
    @Override
    public void addSetIndex(List<String> sets) {
    }

    /**
     * Add an element to the set
     * @param elementName element name
     */
    @Override
    public void addSetElement(String elementName) {
        Row temp = sheet.createRow(rowCount);
        temp.setRowStyle(elementStyle);
        temp.createCell(0).setCellValue(elementName);
        rowCount++;
    }

    /**
     * Label the top of this sheet with the table column names.
     * @param titles column names
     */
    @Override
    public void startTable(List<String> titles) {
        Row temp = sheet.createRow(rowCount);
        temp.setRowStyle(titleStyle);
        for (int i=0; i<titles.size(); i++) {
            temp.createCell(i).setCellValue(titles.get(i));
        }
        rowCount++;
    }

    // Do nothing on end table
    @Override
    public void endTable() {
    }

    /**
     * Add a row of information to the report.
     * @param row cells to add
     */
    @Override
    public void addRow(List<String> row) {
        Row temp = sheet.createRow(rowCount);
        for (int i=0; i<row.size(); i++) {
            temp.createCell(i).setCellValue(row.get(i));
        }
        rowCount++;
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
        return "Excel";
    }

    @Override
    public String getDescription() {
        return "An XLS formatted report which is meant to be viewed in Excel.";
    }

    @Override
    public String getExtension() {
        return ".xlsx";
    }

    @Override
    public String getFilePath() {
        return "Excel.xlsx";
    }
    
}
