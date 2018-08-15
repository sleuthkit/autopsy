/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.sqlitereader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;


/**
 *
 * @author dsmyda
 */
public class ExcelReader extends AbstractReader {  
    
    private Workbook workbook;
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(ExcelReader.class.getName());
    private String XLSXMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private String XLSMimeType = "application/vnd.ms-excel";

    public ExcelReader(AbstractFile file, String localDiskPath, String mimeType) 
            throws FileReaderInitException {
        super(file, localDiskPath);
        try {
            this.workbook = createWorkbook(localDiskPath, mimeType);
        } catch (IOException ex) {
            throw new FileReaderInitException(ex);
        }
    }
    
    private Workbook createWorkbook(String localDiskPath, String mimeType) throws 
            IOException, FileReaderInitException {
        if(mimeType.equals(XLSMimeType)) {
            return new HSSFWorkbook(new FileInputStream(new File(localDiskPath)));
        } else if(mimeType.equals(XLSXMimeType)) {
            return new XSSFWorkbook(new FileInputStream(new File(localDiskPath)));
        } else {
            throw new FileReaderInitException(String.format("Excel reader for mime "
                        + "type [%s] is not supported", mimeType));
        }
    }
    
    /**
     * Returns the number of rows in a given excel table (aka sheet). 
     * 
     * @param tableName Name of table to count total rows from
     * @return row count for requested table name
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException 
     */
    @Override
    public Integer getRowCountFromTable(String tableName) throws FileReaderException {
        return workbook.getSheet(tableName).getLastRowNum();
    }

    /**
     * Returns a collection of all the rows from a given table in an excel document.
     * 
     * @param tableName
     * @return
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException 
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName) throws FileReaderException {
        List<Map<String, Object>> rowContents = new ArrayList<>();
        Iterator<Row> iterator = workbook.getSheet(tableName).rowIterator();
        //Consume header
        if(iterator.hasNext()) {
            //Consume header
            iterator.next();
        }
        
        while(iterator.hasNext()) {
            Map<String, Object> contents = new HashMap<>();
            Row r = iterator.next();
            for(Cell c : r) {
                addCellValueToMap(c, contents);
            }
            rowContents.add(contents);
        }
        return rowContents;
    }
    
    /**
     * 
     * @param cell
     * @param contents 
     */
    private void addCellValueToMap(Cell cell, Map<String, Object> contents){
        String columnName = getCellValuesColumnName(cell);
        switch (cell.getCellTypeEnum()) {
            case BOOLEAN:
                contents.put(columnName, cell.getBooleanCellValue());
                break;
            case STRING:
                contents.put(columnName, cell.getRichStringCellValue().getString());
                break;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    contents.put(columnName, cell.getDateCellValue());
                } else {
                    contents.put(columnName, cell.getNumericCellValue());
                }
                break;
            case FORMULA:
                contents.put(columnName, cell.getCellFormula());
                break;
            default:
                contents.put(columnName,"");
        }
    }
    
    /**
     * 
     * @param cell
     * @return 
     */
    private String getCellValuesColumnName(Cell cell) {
        Row header = cell.getSheet().getRow(0);
        if(header != null) {
            Cell columnCell = header.getCell(cell.getRowIndex());
            
        }
        return "";
    }

    /**
     * 
     * 
     * @return
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderException 
     */
    @Override
    public Map<String, String> getTableSchemas() throws FileReaderException {     
        Map<String, String> tableSchemas = new HashMap<>();
        for(Sheet sheet : workbook) {
            Row header = sheet.getRow(0);
            if(header != null) {
                String headerStringFormat = StringUtils.join(header.cellIterator(), ", ");
                tableSchemas.put(sheet.getSheetName(), headerStringFormat);
            } else {
                tableSchemas.put(sheet.getSheetName(), "");
            }
        }
        return tableSchemas;
    }
    
    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException ex) {
            //Non-essential exception, user has no need for the connection 
            //object at this stage so closing details are not important
            logger.log(Level.WARNING, "Could not close excel file input stream", ex);
        }
    }
}
