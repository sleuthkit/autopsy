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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderInitException;
import org.sleuthkit.datamodel.Content;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.autopsy.tabulardatareader.FileReaderFactory;

/**
 * Dedicated SqliteTextExtractor to solve the problems associated with Tika's
 * Sqlite parser.
 *
 * Tika problems: 
 *  1) Tika fails to open virtual tables 
 *  2) Tika fails to open tables with spaces in table name 
 *  3) Tika fails to include the table names in output (except for the first table it parses) 
 *  4) BasisTech > Apache
 *
 */
public class SqliteTextExtractor extends ContentTextExtractor {

    private final String SQLITE_MIMETYPE = "application/x-sqlite3";
    private static final Logger logger = Logger.getLogger(SqliteTextExtractor.class.getName());

    @Override
    boolean isContentTypeSpecific() {
        return true;
    }

    /**
     * Supports only the sqlite mimetypes
     *
     * @param file           Content file
     * @param detectedFormat Mimetype of content file
     *
     * @return true if x-sqlite3
     */
    @Override
    boolean isSupported(Content file, String detectedFormat) {
        return SQLITE_MIMETYPE.equals(detectedFormat);
    }

    /**
     * Returns an input stream that will read from a sqlite database.
     *
     * @param source Content file
     *
     * @return An InputStream that reads from a Sqlite database.
     *
     * @throws
     * org.sleuthkit.autopsy.keywordsearch.TextExtractor.TextExtractorException
     */
    @Override
    public Reader getReader(Content source) throws TextExtractorException {
        return new InputStreamReader(new SqliteTextReader(source));
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void logWarning(String msg, Exception ex) {
        logger.log(Level.WARNING, msg, ex); //NON-NLS
    }

    /**
     * InputStream that is returned from the getReader method. This stream opens
     * a sqlite file and loads its contents into a buffer that can be read from
     * the read function.
     */
    private final class SqliteTextReader extends InputStream {

        private StringBuilder databaseBuffer;
        private int currReadIndex;
        private final int NO_CONTENT_LEFT = -1;

        /**
         * The buffer is filled during initialization, meaning the whole sqlite
         * file is read during construction.
         *
         * @param source Content file that is the sqlite database
         *
         * @throws
         * org.sleuthkit.autopsy.keywordsearch.TextExtractor.TextExtractorException
         */
        public SqliteTextReader(Content source) throws TextExtractorException {
            try (AbstractReader reader = FileReaderFactory.createReader(
                    SQLITE_MIMETYPE, source)) {
                this.databaseBuffer = new StringBuilder();
                //Fill the entire buffer upon instantiation
                copyDatabaseIntoBuffer(source, reader);
            } catch (FileReaderInitException ex) {
                throw new TextExtractorException(
                        String.format("Encountered a FileReaderInitException" //NON-NLS
                                + " when trying to initialize a SQLiteReader" //NON-NLS
                                + " for Content with id:[%s], name:[%s].", //NON-NLS
                                source.getId(), source.getName()));
            }
        }

        /**
         * Queries the sqlite database and adds all tables and rows to a
         * TableBuilder, which formats the strings into a table view for clean
         * results while searching for keywords in the application.
         *
         * @param reader
         */
        private void copyDatabaseIntoBuffer(Content source, AbstractReader reader) {
            try {
                Map<String, String> tables = reader.getTableSchemas();
                iterateTablesAndPopulateBuffer(tables, reader, source);
            } catch (AbstractReader.FileReaderException ex) {
                logger.log(Level.WARNING, String.format(
                        "Error attempting to get tables from file: " //NON-NLS
                        + "[%s] (id=%d).", source.getName(), //NON-NLS
                        source.getId()), ex);
            }
        }

        /**
         * Iterates all of the tables and passes the rows to a helper function
         * for reading.
         *
         * @param tables A map of table names to table schemas
         * @param reader SqliteReader for interfacing with the database
         * @param source Source database file for logging
         */
        private void iterateTablesAndPopulateBuffer(Map<String, String> tables,
                AbstractReader reader, Content source) {

            for (String tableName : tables.keySet()) {
                TableBuilder tableBuilder = new TableBuilder();
                tableBuilder.addSection(tableName);
                try {
                    List<Map<String, Object>> rowsInTable
                            = reader.getRowsFromTable(tableName);
                    addRowsToTableBuilder(tableBuilder, rowsInTable);
                } catch (AbstractReader.FileReaderException ex) {
                    logger.log(Level.WARNING, String.format(
                            "Error attempting to read file table: [%s]" //NON-NLS
                            + " for file: [%s] (id=%d).", tableName, //NON-NLS
                            source.getName(), source.getId()), ex);
                }
            }
        }

        /**
         * Iterates all rows in the table and adds the rows to the TableBuilder
         * class which formats the input into a table view.
         *
         * @param tableBuilder
         * @param rowsInTable  list of rows from the sqlite table
         */
        private void addRowsToTableBuilder(TableBuilder tableBuilder,
                List<Map<String, Object>> rowsInTable) {
            if (!rowsInTable.isEmpty()) {
                //Create a collection from the header set, so that the TableBuilder
                //can easily format it
                tableBuilder.addHeader(new ArrayList<>(
                        rowsInTable.get(0).keySet()));
                for (Map<String, Object> row : rowsInTable) {
                    tableBuilder.addRow(row.values());
                }
            }
            //If rowsInTable was empty, just append the table as is
            databaseBuffer.append(tableBuilder);
        }

        /**
         * Returns one byte of the buffer at a time. This buffer was completely
         * loaded during construction. Consider a lazy approach or a
         * multi-threaded one if too slow.
         *
         * @return @throws IOException
         */
        @Override
        public int read() throws IOException {
            //End of the buffer if true
            if (currReadIndex == databaseBuffer.length() - 1) {
                return NO_CONTENT_LEFT;
            }

            return databaseBuffer.charAt(currReadIndex++);
        }
    }

    /*
     * Formats input so that it reads as a table in the console or in a text
     * viewer
     */
    private class TableBuilder {

        private List<String[]> rows = new LinkedList<>();

        //Formatters
        private final String HORIZONTAL_DELIMITER = "-";
        private final String VERTICAL_DELIMITER = "|";
        private final String HEADER_CORNER = "+";

        private final String TAB = "\t";
        private final String NEW_LINE = "\n";
        private final String SPACE = " ";

        private String section = "";

        /**
         * Add the section to the top left corner of the table. This is where
         * the name of the table should go.
         *
         * @param section Table name
         */
        public void addSection(String section) {
            this.section = section + NEW_LINE + NEW_LINE;
        }

        /**
         * Creates a horizontal bar given the length param. These are used to
         * box the header up and at the bottom of the table.
         *
         * @return Ex: \t+----------------------+\n
         */
        private String buildHorizontalBar(int length) {
            if (length == 0) {
                return "";
            }
            //Output: \t+----------------------+\n
            return TAB + HEADER_CORNER + StringUtils.repeat(
                    HORIZONTAL_DELIMITER, length) + HEADER_CORNER + NEW_LINE;
        }

        /**
         * Add header row to underlying list collection, which will be formatted
         * when toString is called.
         *
         * @param vals
         */
        public void addHeader(Collection<Object> vals) {
            addRow(vals);
        }

        /**
         * Add a row to the underlying list collection, which will be formatted
         * when toString is called.
         *
         * @param vals
         */
        public void addRow(Collection<Object> vals) {
            List<String> rowValues = new ArrayList<>();
            vals.forEach((val) -> {
                rowValues.add(String.valueOf(val));
            });
            rows.add(rowValues.toArray(
                    new String[rowValues.size()]));
        }

        /**
         * Gets the max width of a cell in each column and the max number of
         * columns in any given row. This ensures that there is enough space for
         * even the longest entry and enough columns.
         *
         * @return
         */
        private int[] getMaxWidthPerColumn() {
            int maxNumberOfColumns = 0;
            for (String[] row : rows) {
                maxNumberOfColumns = Math.max(
                        maxNumberOfColumns, row.length);
            }

            int[] widths = new int[maxNumberOfColumns];
            for (String[] row : rows) {
                for (int colNum = 0; colNum < row.length; colNum++) {
                    widths[colNum] = Math.max(
                            widths[colNum],
                            StringUtils.length(row[colNum])
                    );
                }
            }

            return widths;
        }

        /**
         * Returns a string version of the table, when printed to console it
         * will be fully formatted.
         *
         * @return
         */
        @Override
        public String toString() {
            StringBuilder outputTable = new StringBuilder();

            int barLength = 0;
            int[] colMaxWidths = getMaxWidthPerColumn();
            boolean header = true;
            for (String[] row : rows) {
                addFormattedRowToBuffer(row, colMaxWidths, outputTable);
                if (header) {
                    //Get the length of the horizontal bar from the length of the
                    //formatted header, minus the one tab added at the beginning
                    //of the row (we want to count the vertical delimiters since 
                    //we want it all to line up.
                    barLength = outputTable.length() - 2;
                }
                addFormattedHeaderToBuffer(outputTable, barLength, header);
                header = false;
            }
            outputTable.append(buildHorizontalBar(barLength));
            outputTable.append(NEW_LINE);

            return outputTable.toString();
        }

        /**
         * Outputs a fully formatted row in the table
         *
         * Example: \t| John | 12345678 | john@email.com |\n
         *
         * @param row
         * @param colMaxWidths
         * @param buf
         */
        private void addFormattedRowToBuffer(String[] row,
                int[] colMaxWidths, StringBuilder outputTable) {
            outputTable.append(TAB);
            for (int colNum = 0; colNum < row.length; colNum++) {
                outputTable.append(VERTICAL_DELIMITER);
                outputTable.append(SPACE);
                outputTable.append(StringUtils.rightPad(
                        StringUtils.defaultString(row[colNum]),
                        colMaxWidths[colNum]));
                outputTable.append(SPACE);
            }
            outputTable.append(VERTICAL_DELIMITER);
            outputTable.append(NEW_LINE);
        }

        /**
         * Outputs a fully formatted header.
         *
         * Example: \t+----------------------+\n 
         *          \t| Email | Phone | Name |\n
         *          \t+----------------------+\n
         *
         * @param buf
         * @param barLength
         * @param header
         */
        private void addFormattedHeaderToBuffer(StringBuilder outputTable,
                int barLength, boolean header) {
            if (header) {
                outputTable.insert(0, buildHorizontalBar(barLength));
                outputTable.insert(0, section);
                outputTable.append(buildHorizontalBar(barLength));
            }
        }
    }
}
