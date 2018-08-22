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
public final class ExcelReader extends AbstractReader {  
    /* Boilerplate code */
    private final static IngestServices services = IngestServices.getInstance();
    private final static Logger logger = services.getLogger(ExcelReader.class.getName());
    
    private Workbook workbook;
    private final static String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final static String XLS_MIME_TYPE = "application/vnd.ms-excel";
    private final static String EMPTY_CELL_STRING = "";
    private Map<String, Row> headerCache;

    public ExcelReader(AbstractFile file, String localDiskPath, String mimeType) 
            throws FileReaderInitException {
        super(file, localDiskPath);
        try {
            this.workbook = createWorkbook(localDiskPath, mimeType);
            headerCache = new HashMap<>();
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
            case XLS_MIME_TYPE:
                try {
                    //Apache POI only supports BIFF8 format, anything below is considered
                    //old excel format and is not a concern for us.
                    return new HSSFWorkbook(new FileInputStream(new File(localDiskPath)));
                } catch (OldExcelFormatException e) {
                    throw new FileReaderInitException(e);
                }
            case XLSX_MIME_TYPE:
                //StreamingReader is part of the xlsx streamer dependency that creates
                //a streaming version of XSSFWorkbook for reading (SXSSFWorkbook is only for writing
                //large workbooks, not reading). This libary provides a workbook interface
                //that is mostly identical to the poi workbook api, hence both the HSSFWorkbook
                //and this can use the same functions below.
                return StreamingReader.builder().rowCacheSize(500).open(new File(localDiskPath));
            default:
                throw new FileReaderInitException(String.format("Excel reader for mime " + 
                        "type [%s] is not supported", mimeType));
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
        //Pad with + 1 because rows are zero index, thus a LastRowNum() (in getRowCountFromTable()) of 1
        //indicates that there are records in 0 and 1 and so a total row count of 
        //2. This also implies there is no way to determine if a workbook is empty,
        //since a last row num of 0 doesnt differentiate between a record in 0 or 
        //nothing in the workbook. Such a HSSF.
        return getRowsFromTable(tableName, 0, getRowCountFromTable(tableName));
    }
    
    /**
     * Returns a window of rows starting at the offset and ending when the number of rows read 
     * equals the 'numRowsToRead' parameter or the iterator has nothing left to read.
     * 
     * For instance: offset 1, numRowsToRead 5 would return 5 results (1-5).
     *               offset 0, numRowsToRead 5 would return 5 results (0-4).
     * 
     * @param tableName Current name of sheet to be read
     * @param offset start index to begin reading (documents are 0 indexed)
     * @param numRowsToRead number of rows to read
     * @return
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName, 
            int offset, int numRowsToRead) throws FileReaderException {
        //StreamingReader maintains the same pointer to a sheet rowIterator, so this
        //call returns an iterator that could have already been iterated on instead
        //of a fresh copy. We must cache the header value from the call to
        //getTableSchemas as important information in the first row could have been
        //missed.
        Iterator<Row> sheetIter = workbook.getSheet(tableName).rowIterator();
        List<Map<String, Object>> rowList = new ArrayList<>();
        
        //Read the header value as the header may be a row of data in the
        //excel sheet
        if(headerCache.containsKey(tableName)) {
            Row header = headerCache.get(tableName);
            if(header.getRowNum() >= offset 
                    && header.getRowNum() < (offset + numRowsToRead)) {
                rowList.add(getRowMap(tableName, header));
            }
        }
        
        while(sheetIter.hasNext()) {
            Row currRow = sheetIter.next();
            //If the current row number is within the window of our row capture
            if(currRow.getRowNum() >= offset 
                    && currRow.getRowNum() < (offset + numRowsToRead)) {
                rowList.add(getRowMap(tableName, currRow));
            }
            
            //if current row number is equal to our upper bound
            //of rows requested to be read.
            if(currRow.getRowNum() >= (offset + numRowsToRead)) {
                break;
            }
        }
        
        return rowList;
    }
    
    private Map<String, Object> getRowMap(String tableName, Row row) {
        Map<String, Object> rowMap = new HashMap<>();
        for(Cell cell : row) {
            String columnName = getColumnName(cell, tableName);
            Object value = getCellValue(cell);
            rowMap.put(columnName, value);
        }
        return rowMap;
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
        if(headerCache.containsKey(tableName)) {
            Row header = headerCache.get(tableName);
            Cell columnHeaderCell = header.getCell(cell.getRowIndex());
            if(columnHeaderCell == null) {
                return EMPTY_CELL_STRING;
            }
            Object columnHeaderValue = getCellValue(columnHeaderCell);
            return columnHeaderValue.toString();
        }
        //No header present
        return EMPTY_CELL_STRING;
    }

    /**
     * Returns a map of sheet names to headers (header is in a comma-seperated string).
     * Warning: Only call this ONCE per excel file.
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
                headerCache.put(sheet.getSheetName(), header);
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
