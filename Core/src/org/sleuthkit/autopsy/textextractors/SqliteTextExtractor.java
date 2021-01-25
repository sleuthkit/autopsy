/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.textextractors;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.SQLiteTableReader;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Extracts text from SQLite database files.
 *
 * This is a dedicated solution to address the problems associated with 
 * Tika's sqlite parser (version 1.17), which include the following:
 *  1) Virtual tables cause the parser to bail
 *  2) Tables that contain spaces in their name are not extracted
 *  3) Table names are not included in its output text
 */
final class SqliteTextExtractor implements TextExtractor {

    private static final String SQLITE_MIMETYPE = "application/x-sqlite3";
    private static final Logger logger = Logger.getLogger(SqliteTextExtractor.class.getName());
    private final AbstractFile file;

    SqliteTextExtractor(AbstractFile file) {
        this.file = file;
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
    public boolean isSupported() {
        return SQLITE_MIMETYPE.equals(file.getMIMEType());
    }

    /**
     * Returns a reader that will iterate over the text of a sqlite database.
     *
     * @param source Content file
     *
     * @return An InputStream that reads from a Sqlite database
     *
     * @throws TextExtractorException
     */
    @Override
    public Reader getReader() throws InitReaderException {
        return new SQLiteStreamReader(file);
    }
    
    /**
     * Produces a continuous stream of characters from a database file. To
     * achieve this, all table names are queues up and a SQLiteTableReader is
     * used to do the actual queries and table iteration.
     */
    private class SQLiteStreamReader extends Reader {

        private final SQLiteTableReader reader;
        private final AbstractFile file;

        private Iterator<String> tableNames;
        private String currentTableName;

        private char[] buf;
        private ExcessBytes leftOvers;
        private int totalColumns;

        private int bufIndex;

        /**
         * Creates a new reader for the sqlite file. This table reader class
         * will iterate through a table row by row and pass the values to
         * different functions based on data type. Here we define what to do on
         * the column names and we define what to do for all data types.
         *
         * @param file Sqlite file
         */
        SQLiteStreamReader(AbstractFile file) {
            this.file = file;
            reader = new SQLiteTableReader.Builder(file)
                    .forAllColumnNames(getColumnNameStrategy())
                    .forAllTableValues(getForAllTableValuesStrategy()).build();
        }

        /**
         * On every item in the database we want to do the following series of
         * steps: 1) Get it's string representation (ignore blobs with empty
         * string). 2) Format it based on its positioning in the row. 3) Write
         * it to buffer
         *
         * rowIndex is purely for keeping track of where the object is in the
         * table, hence the bounds checking with the mod function.
         *
         * @return Our consumer class defined to do the steps above.
         */
        private Consumer<Object> getForAllTableValuesStrategy() {
            return new Consumer<Object>() {
                private int columnIndex = 0;

                @Override
                public void accept(Object value) {
                    columnIndex++;
                    //Ignore blobs
                    String objectStr = (value instanceof byte[]) ? "" : Objects.toString(value, "");

                    if (columnIndex > 1 && columnIndex < totalColumns) {
                        objectStr += " ";
                    }
                    if (columnIndex == 1) {
                        objectStr = "\t" + objectStr + " ";
                    }
                    if (columnIndex == totalColumns) {
                        objectStr += "\n";
                    }

                    fillBuffer(objectStr);
                    columnIndex %= totalColumns;
                }
            };
        }

        /**
         * On every column name in the header do the following series of steps:
         * 1) Write the tableName before the header. 2) Format the column name
         * based on row positioning 3) Reset the count if we are at the end,
         * that way if we want to read multiple tables we can do so without
         * having to build new consumers.
         *
         * columnIndex is purely for keeping track of where the column name is
         * in the table, hence the bounds checking with the mod function.
         *
         * @return Our consumer class defined to do the steps above.
         */
        private Consumer<String> getColumnNameStrategy() {
            return new Consumer<String>() {
                private int columnIndex = 0;

                @Override
                public void accept(String columnName) {
                    if (columnIndex == 0) {
                        fillBuffer("\n" + currentTableName + "\n\n\t");
                    }
                    columnIndex++;

                    fillBuffer(columnName + ((columnIndex == totalColumns) ? "\n" : " "));

                    //Reset the columnCount to 0 for next table read
                    columnIndex %= totalColumns;
                }
            };
        }

        /**
         * This functions writes the string representation of a database value
         * into the read buffer. If the buffer becomes full, we save the extra
         * characters and hold on to them until the next call to read().
         *
         * @param val Formatted database value string
         */
        private void fillBuffer(String val) {
            for (int i = 0; i < val.length(); i++) {
                if (bufIndex != buf.length) {
                    buf[bufIndex++] = val.charAt(i);
                } else {
                    leftOvers = new ExcessBytes(val, i);
                    break;
                }
            }
        }

        /**
         * Reads database values into the buffer. This function is responsible
         * for getting the next table in the queue, initiating calls to the
         * SQLiteTableReader, and filling in any excess bytes that are lingering
         * from the previous call.
         *
         * @throws IOException
         */
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            buf = cbuf; //needs to be the same memory address and not a copy of the contents since we are filling it in

            bufIndex = off;

            //Lazily wait to get table names until first call to read.
            if (Objects.isNull(tableNames)) {
                try {
                    tableNames = reader.getTableNames().iterator();
                } catch (SQLiteTableReaderException ex) {
                    //Can't get table names so can't read the file!
                    return -1;
                }
            }

            //If there are excess bytes from last read, then copy thoses in.
            if (Objects.nonNull(leftOvers) && !leftOvers.isFinished()) {
                bufIndex += leftOvers.read(cbuf, off, len);
            }

            //Keep grabbing table names from the queue and reading them until
            //our buffer is full.
            while (bufIndex != len) {
                if (Objects.isNull(currentTableName) || reader.isFinished()) {
                    if (tableNames.hasNext()) {
                        currentTableName = tableNames.next();
                        try {
                            totalColumns = reader.getColumnCount(currentTableName);
                            reader.read(currentTableName, () -> bufIndex == len);
                        } catch (SQLiteTableReaderException ex) {
                            logger.log(Level.WARNING, String.format(
                                    "Error attempting to read file table: [%s]" //NON-NLS
                                    + " for file: [%s] (id=%d).", currentTableName, //NON-NLS
                                    file.getName(), file.getId()), ex.getMessage());
                        }
                    } else {
                        if (bufIndex == off) {
                            return -1;
                        }
                        return bufIndex;
                    }
                } else {
                    try {
                        reader.read(currentTableName, () -> bufIndex == len);
                    } catch (SQLiteTableReaderException ex) {
                        logger.log(Level.WARNING, String.format(
                                "Error attempting to read file table: [%s]" //NON-NLS
                                + " for file: [%s] (id=%d).", currentTableName, //NON-NLS
                                file.getName(), file.getId()), ex.getMessage());
                    }
                }
            }

            return bufIndex;
        }

        @Override
        public void close() throws IOException {
            try {
                reader.close();
            } catch (SQLiteTableReaderException ex) {
                logger.log(Level.WARNING, "Could not close SQliteTableReader.", ex.getMessage());
            }
        }

        /**
         * Wrapper that holds the excess bytes that were left over from the
         * previous call to read().
         */
        private class ExcessBytes {

            private final String entity;
            private Integer pointer;

            ExcessBytes(String entity, Integer pointer) {
                this.entity = entity;
                this.pointer = pointer;
            }

            boolean isFinished() {
                return entity.length() == pointer;
            }

            /**
             * Copies the excess bytes this instance is holding onto into the
             * buffer.
             *
             * @param buf buffer to write into
             * @param off index in buffer to start the write
             * @param len length of the write
             *
             * @return number of characters read into the buffer
             */
            int read(char[] buf, int off, int len) {
                for (int i = off; i < len; i++) {
                    if (isFinished()) {
                        return i - off;
                    }

                    buf[i] = entity.charAt(pointer++);
                }

                return len - off;
            }
        }
    }
}
