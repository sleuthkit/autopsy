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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Reads Excel files and returns results in a list collection.
 */
@NbBundle.Messages({
    "ExcelReader.ReadExcelFiles.moduleName=ExcelReader"
})
public class ExcelReader extends AbstractReader {
    
    private Workbook xlsWorkbook;
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(ExcelReader.class.getName());
    
    /**
     * 
     * @param file
     * @param localDiskPath
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderInitException 
     */
    public ExcelReader(AbstractFile file, String localDiskPath) 
            throws FileReaderInitException {
        super(file, localDiskPath);
        
        try {
            getWorkbookFromLocalDisk(localDiskPath);
        } catch (IOException ex) {
            throw new FileReaderInitException(ex);
        }
    }
    
    /**
     * Opens an apache poi workbook instance on the file contents copied from the 
     * abstract file.
     * 
     * @param localDiskPath Location of the file contents on local disk
     * @throws IOException Error opening file at local disk path
     */
    private void getWorkbookFromLocalDisk(String localDiskPath) throws IOException{
        xlsWorkbook = new HSSFWorkbook(new FileInputStream(new File(localDiskPath)));
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
        return xlsWorkbook.getSheet(tableName).getLastRowNum();
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
        Iterator<Row> iterator = xlsWorkbook.getSheet(tableName).rowIterator();
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
        for(Sheet sheet : xlsWorkbook) {
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
    
    /**
     * 
     */
    @Override
    public void close() {
        try {
            xlsWorkbook.close();
        } catch (IOException ex) {
            //Non-essential exception, user has no need for the connection 
            //object at this stage so closing details are not important
            logger.log(Level.WARNING, "Could not close excel file input stream", ex);
        }
    }
}