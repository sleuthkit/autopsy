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
package org.sleuthkit.autopsy.tabulardatareader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.hssf.OldExcelFormatException;


/**
 * Reads excel files and implements the abstract reader api for interfacing with the 
 * content. Supports .xls and .xlsx files.
 */
public class ExcelReader extends AbstractReader {  
    /* Boilerplate code */
    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(ExcelReader.class.getName());
    
    private Workbook workbook;
    private final String XLSXMimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final String XLSMimeType = "application/vnd.ms-excel";
    private final String EMPTY_CELL_STRING = "";

    public ExcelReader(AbstractFile file, String localDiskPath, String mimeType) 
            throws FileReaderInitException {
        super(file, localDiskPath);
        try {
            this.workbook = createWorkbook(localDiskPath, mimeType);
        } catch (IOException ex) {
            throw new FileReaderInitException(ex);
        }
    }
    
    /**
     * Internal factory for creating the correct workbook given the mime type. The 
     * file reader factory in this module passes both the XLSMimeType and XLSXMimeType
     * into this constructor for the reader to handle. This avoided the need for creating 
     * an AbstractExcelReader class and two sub classes overriding the workbook field.
     * Additionally, I don't forsee needing to support more than these two mime types.
     * 
     * @param localDiskPath To open an input stream for poi to read from
     * @param mimeType The mimeType passed to the constructor
     * @return The corrent workbook instance
     * @throws IOException Issue with input stream and opening file location at 
     * localDiskPath
     * @throws FileReaderInitException mimetype unsupported
     */
    private Workbook createWorkbook(String localDiskPath, String mimeType) throws 
            IOException, FileReaderInitException {
        switch (mimeType) {
            case XLSMimeType:
                try {
                    //Apache POI only supports BIFF8 format, anything below is considered
                    //old excel format and is not a concern for us.
                    return new HSSFWorkbook(new FileInputStream(new File(localDiskPath)));
                } catch (OldExcelFormatException e) {
                    throw new FileReaderInitException(e);
                }
            case XLSXMimeType:
                InputStream is = new FileInputStream(new File(localDiskPath));
                //StreamingReader is part of the xlsx streamer dependency that creates
                //a streaming version of XSSFWorkbook for reading (SXSSFWorkbook is only for writing
                //large workbooks, not reading). This libary provides a workbook interface
                //that is mostly identical to the poi workbook api, hence both the HSSFWorkbook
                //and this can use the same functions below.
                return StreamingReader.builder().rowCacheSize(500).bufferSize(4096).open(is);
            default:
                throw new FileReaderInitException(String.format("Excel reader for mime "
                        + "type [%s] is not supported", mimeType));
        }
    }
    
    /**
     * Returns the number of rows in a given excel table (aka sheet). 
     * 
     * @param tableName Name of table to count total rows from
     * @return row count for requested table name 
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    @Override
    public Integer getRowCountFromTable(String tableName) throws FileReaderException {
        return workbook.getSheet(tableName).getLastRowNum();
    }

    /**
     * Returns a collection of all the rows from a given table in an excel document.
     * 
     * @param tableName Current sheet name being read
     * @return A collection of row maps 
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName) throws FileReaderException {
        List<Map<String, Object>> rowList = new ArrayList<>();
        Iterator<Row> iterator = workbook.getSheet(tableName).rowIterator();
        
        while(iterator.hasNext()) {
            Map<String, Object> row = new HashMap<>();
            Row currRow = iterator.next();
            for(Cell cell : currRow) {
                String columnName = getColumnName(cell, tableName);
                Object value = getCellValue(cell);
                row.put(columnName, value);
            }
            rowList.add(row);
        }
        
        return rowList;
    }
    
    /**
     * Returns the value of a given cell. The correct value function must be 
     * called on a cell depending on its type, hence the switch.
     * 
     * @param cell Cell object containing a getter function for its value type
     * @return A generic object pointer to the cell's value
     */
    private Object getCellValue(Cell cell){
        switch (cell.getCellTypeEnum()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                return cell.getRichStringCellValue().getString();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                } else {
                    return cell.getNumericCellValue();
                }
            case FORMULA:
                return cell.getCellFormula();
            default:
                //Cell must be empty at this branch
                return EMPTY_CELL_STRING;
        }
    }
    
    /**
     * Returns the name of the column that the cell currently lives in
     * Cell Value: 6784022342 -> Header name: Phone Number
     * 
     * @param cell current cell being read
     * @param tableName current sheet name being read
     * @return the name of the column the current cell lives in
     */
    private String getColumnName(Cell cell, String tableName) {
        Iterator<Row> sheetIter = workbook.getSheet(tableName).rowIterator();
        if(sheetIter.hasNext()) {
            Row header = sheetIter.next();
            Cell columnHeaderCell = header.getCell(cell.getRowIndex());
            if(columnHeaderCell == null) {
                return EMPTY_CELL_STRING;
            }
            Object columnHeaderValue = getCellValue(columnHeaderCell);
            if(columnHeaderValue instanceof String) {
                return (String) columnHeaderValue;
            } else {
                return columnHeaderValue.toString();
            }
        }
        //No header present
        return EMPTY_CELL_STRING;
    }

    /**
     * Returns a map of sheet names to headers (header is in a comma-seperated string).
     * 
     * @return A map of sheet names to header strings. 
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    @Override
    public Map<String, String> getTableSchemas() throws FileReaderException {     
        Map<String, String> tableSchemas = new HashMap<>();
        for(Sheet sheet : workbook) {
            Iterator<Row> iterator = sheet.rowIterator();
            if(iterator.hasNext()) {
                //Consume header
                Row header = iterator.next();
                String headerStringFormat = StringUtils.join(header.cellIterator(), ", ");
                tableSchemas.put(sheet.getSheetName(), headerStringFormat);
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
