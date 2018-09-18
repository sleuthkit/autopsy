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

import static com.google.common.collect.Lists.newArrayList;
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
 * Reads excel files and implements the abstract reader api for interfacing with
 * the content. Supports .xls and .xlsx files.
 */
public final class ExcelReader extends AbstractReader {

    private final static IngestServices services = IngestServices.getInstance();
    private final static Logger logger = services.getLogger(ExcelReader.class.getName());

    private Workbook workbook;
    private final static String XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final static String XLS_MIME_TYPE = "application/vnd.ms-excel";
    private final static String EMPTY_CELL_STRING = "";

    private String LOCAL_DISK_PATH;
    private String ACTIVE_MIME_TYPE;

    public ExcelReader(AbstractFile file, String localDiskPath, String mimeType)
            throws FileReaderInitException {
        super(file, localDiskPath);
        this.LOCAL_DISK_PATH = localDiskPath;
        this.ACTIVE_MIME_TYPE = mimeType;

        try {
            this.workbook = createWorkbook();
        } catch (IOException ex) {
            throw new FileReaderInitException(ex);
        }
    }

    /**
     * Internal factory for creating the correct workbook given the mime type.
     * The file reader factory in this module passes both the XLSMimeType and
     * XLSXMimeType into this constructor for the reader to handle. This avoided
     * the need for creating an AbstractExcelReader class and two sub classes
     * overriding the workbook field. Additionally, I don't forsee needing to
     * support more than these two mime types.
     *
     *
     * @return The corrent workbook instance
     *
     * @throws IOException             Issue with input stream and opening file
     *                                 location at localDiskPath
     * @throws FileReaderInitException mimetype unsupported
     */
    private Workbook createWorkbook() throws
            IOException, FileReaderInitException {
        switch (ACTIVE_MIME_TYPE) {
            case XLS_MIME_TYPE:
                try {
                    //Apache POI only supports BIFF8 format, anything below is considered
                    //old excel format and is not a concern for us.
                    return new HSSFWorkbook(new FileInputStream(new File(LOCAL_DISK_PATH)));
                } catch (OldExcelFormatException e) {
                    throw new FileReaderInitException(e);
                }
            case XLSX_MIME_TYPE:
                //StreamingReader is part of the xlsx streamer dependency that creates
                //a streaming version of XSSFWorkbook for reading (SXSSFWorkbook is only for writing
                //large workbooks, not reading). This libary provides a workbook interface
                //that is mostly identical to the poi workbook api, hence both the HSSFWorkbook
                //and this can use the same functions below.
                return StreamingReader.builder().rowCacheSize(500).open(new File(LOCAL_DISK_PATH));
            default:
                throw new FileReaderInitException(String.format("Excel reader for mime "
                        + "type [%s] is not supported", ACTIVE_MIME_TYPE));
        }
    }

    /**
     * Returns the number of rows in a given excel table (aka sheet).
     *
     * @param tableName Name of table to count total rows from
     *
     * @return row count for requested table name
     *
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    @Override
    public Integer getRowCountFromTable(String tableName) throws FileReaderException {
        return workbook.getSheet(tableName).getLastRowNum();
    }

    /**
     * Returns a collection of all the rows from a given table in an excel
     * document.
     *
     * @param tableName Current sheet name being read
     *
     * @return A collection of row maps
     *
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName) throws FileReaderException {
        //StreamingReader maintains the same pointer to a sheet rowIterator, so this
        //call returns an iterator that could have already been iterated on instead
        //of a fresh copy. We must cache the header value from the call to
        //getTableSchemas as important information in the first row could have been
        //missed.
        Iterator<Row> sheetIter = workbook.getSheet(tableName).rowIterator();
        List<Map<String, Object>> rowList = new ArrayList<>();

        while (sheetIter.hasNext()) {
            Row currRow = sheetIter.next();
            rowList.add(getRowMap(tableName, currRow));
        }

        //Reset the streaming reader for xlsx, so that there is a fresh iterator 
        //on each sheet. That way each call to this function returns all the results.
        resetStreamingReader();

        return rowList;
    }

    /**
     * Returns a map of column numbers to a list of column values.
     *
     * @param tableName
     *
     * @return
     *
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    @Override
    public Map<String, List<Object>> getColumnsFromTable(String tableName) throws FileReaderException {
        Map<String, List<Object>> columnViewOfSheet = new HashMap<>();

        Iterator<Row> sheetIter = workbook.getSheet(tableName).rowIterator();

        while (sheetIter.hasNext()) {
            Row row = sheetIter.next();
            for (Cell cell : row) {
                String index = String.valueOf(cell.getColumnIndex());
                if (columnViewOfSheet.containsKey(index)) {
                    columnViewOfSheet.get(index).add(getCellValue(cell));
                } else {
                    columnViewOfSheet.put(index, newArrayList(getCellValue(cell)));
                }
            }
        }

        //Reset the streaming reader for xlsx, so that there is a fresh iterator 
        //on each sheet. That way each call to this function returns all the results.
        resetStreamingReader();

        return columnViewOfSheet;
    }

    /**
     * Currently not supported. Returns a window of rows starting at the offset
     * and ending when the number of rows read equals the 'numRowsToRead'
     * parameter or the iterator has nothing left to read.
     *
     * For instance: offset 1, numRowsToRead 5 would return 5 results (1-5).
     * offset 0, numRowsToRead 5 would return 5 results (0-4).
     *
     * @param tableName     Current name of sheet to be read
     * @param offset        start index to begin reading (documents are 0
     *                      indexed)
     * @param numRowsToRead number of rows to read
     *
     * @return
     *
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName,
            int offset, int numRowsToRead) throws FileReaderException {
        throw new FileReaderException("Operation Not Supported.");
    }

    private Map<String, Object> getRowMap(String tableName, Row row) {
        Map<String, Object> rowMap = new HashMap<>();
        for (Cell cell : row) {
            Object value = getCellValue(cell);
            rowMap.put(String.valueOf(cell.getColumnIndex()), value);
        }
        return rowMap;
    }

    /**
     * Returns the value of a given cell. The correct value function must be
     * called on a cell depending on its type, hence the switch.
     *
     * @param cell Cell object containing a getter function for its value type
     *
     * @return A generic object pointer to the cell's value
     */
    private Object getCellValue(Cell cell) {
        switch (cell.getCellTypeEnum()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                return cell.getStringCellValue();
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
     * Returns a map of sheet names to headers (header is in a comma-seperated
     * string). Warning: Only call this ONCE per excel file.
     *
     * @return A map of sheet names to header strings.
     *
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    @Override
    public Map<String, String> getTableSchemas() throws FileReaderException {
        Map<String, String> tableSchemas = new HashMap<>();
        for (Sheet sheet : workbook) {
            Iterator<Row> iterator = sheet.rowIterator();
            if (iterator.hasNext()) {
                //Consume header
                Row header = iterator.next();
                String headerStringFormat = StringUtils.join(header.cellIterator(), ", ");
                tableSchemas.put(sheet.getSheetName(), headerStringFormat);
            }
        }

        //Reset the streaming reader for xlsx, so that there is a fresh iterator 
        //on each sheet. That way each call to this function returns all the results.
        resetStreamingReader();

        return tableSchemas;
    }

    /**
     * Resets the streaming reader so that the iterator starts at the start of each
     * sheet. Matches functionality provided by apache POI.
     * 
     * @throws
     * org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException
     */
    public void resetStreamingReader() throws FileReaderException {
        if (ACTIVE_MIME_TYPE.equals(XLSX_MIME_TYPE)) {
            try {
                this.workbook = createWorkbook();
            } catch (IOException | FileReaderInitException ex) {
                throw new FileReaderException("Could not reset streaming iterator", ex);
            }
        }
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
