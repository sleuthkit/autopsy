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

import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.text.Segment;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderInitException;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException;
import org.sleuthkit.autopsy.tabulardatareader.FileReaderFactory;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Dedicated SqliteTextExtractor to solve the problems associated with Tika's
 * Sqlite parser.
 *
 * Tika problems: 
 *  1) Tika fails to open virtual tables 
 *  2) Tika fails to open tables with spaces in table name 
 *  3) Tika fails to include the table names in output (except for the first table it parses)
 */
class SqliteTextExtractor extends ContentTextExtractor {

    private static final String SQLITE_MIMETYPE = "application/x-sqlite3";
    private static final Logger logger = Logger.getLogger(SqliteTextExtractor.class.getName());
    private static final CharSequence EMPTY_CHARACTER_SEQUENCE = "";

    @Override
    boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    public boolean isDisabled() {
        return false;
    }

    @Override
    public void logWarning(String msg, Exception exception) {
        logger.log(Level.WARNING, msg, exception); //NON-NLS
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
        //Firewall for any content that is not an AbstractFile
        if (!AbstractFile.class.isInstance(source)) {
            try {
                return CharSource.wrap(EMPTY_CHARACTER_SEQUENCE).openStream();
            } catch (IOException ex) {
                throw new TextExtractorException(
                        String.format("Encountered an issue wrapping blank string" //NON-NLS
                                + " with CharSource for non-abstract file with id: [%s]," //NON-NLS
                                + " name: [%s].", source.getId(), source.getName()), ex); //NON-NLS
            }
        }

        try (AbstractReader reader = FileReaderFactory.createReader(
                (AbstractFile) source, SQLITE_MIMETYPE)) {
            final CharSequence databaseContent = getDatabaseContents(source, reader);
            //CharSource will maintain unicode strings correctly
            return CharSource.wrap(databaseContent).openStream();
        } catch (FileReaderInitException | IOException ex) {
            throw new TextExtractorException(
                    String.format("Encountered a FileReaderInitException" //NON-NLS
                            + " when trying to initialize a SQLiteReader" //NON-NLS
                            + " for AbstractFile with id: [%s], name: [%s].", //NON-NLS
                            source.getId(), source.getName()), ex);
        } catch (FileReaderException ex) {
            throw new TextExtractorException(
                    String.format("Could not get contents from database " //NON-NLS
                            + "tables for AbstractFile with id [%s], name: [%s].", //NON-NLS
                            source.getId(), source.getName()), ex);
        }
    }

    /**
     * Queries the sqlite database and adds all tables and rows to a
     * TableBuilder, which formats the strings into a table view for clean
     * results while searching for keywords in the application.
     *
     * @param reader Sqlite reader for the content source
     * @param source Sqlite file source
     */
    private CharSequence getDatabaseContents(Content source, AbstractReader reader) throws FileReaderException {
        Collection<String> databaseStorage = new LinkedList<>();

        Integer charactersCopied = loadDatabaseIntoCollection(databaseStorage, 
                reader, source);

        return toCharSequence(databaseStorage, charactersCopied);
    }

    /**
     * Iterates all of the tables and populate the TableBuilder with all of the
     * rows from the table. The table string will be added to the list of
     * contents.
     *
     * @param databaseStorage Collection containing all of the database content
     * @param tables          A map of table names to table schemas
     * @param reader          SqliteReader for interfacing with the database
     * @param source          Source database file for logging
     */
    private int loadDatabaseIntoCollection(Collection<String> databaseStorage, 
            AbstractReader reader, Content source) throws FileReaderException {
        //Will throw a FileReaderException if table schemas are unattainable
        Map<String, String> tables = reader.getTableSchemas();

        int charactersCopied = 0;
        for (String tableName : tables.keySet()) {
            TableBuilder tableBuilder = new TableBuilder();
            tableBuilder.setTableName(tableName);

            try {
                //Catch any exception at a particular table, we want to ensure we grab
                //content from as many tables as possible
                List<Map<String, Object>> rowsInTable = reader.getRowsFromTable(tableName);
                if (!rowsInTable.isEmpty()) {
                    tableBuilder.addHeader(new ArrayList<>(rowsInTable.get(0).keySet()));
                    for (Map<String, Object> row : rowsInTable) {
                        tableBuilder.addRow(row.values());
                    }
                }
            } catch (FileReaderException ex) {
                logger.log(Level.WARNING, String.format(
                        "Error attempting to read file table: [%s]" //NON-NLS
                        + " for file: [%s] (id=%d).", tableName, //NON-NLS
                        source.getName(), source.getId()), ex);
            }

            String formattedTable = tableBuilder.toString();
            charactersCopied += formattedTable.length();
            databaseStorage.add(formattedTable);
        }
        return charactersCopied;
    }


    /**
     * Copy elements from collection (which contains formatted database tables) 
     * into a CharSequence so that it can be wrapped and used by the Google CharSource
     * lib.
     * 
     * @param databaseStorage Collection containing database contents
     * @param characterCount Number of characters needed to be allocated in the buffer
     * so that all of the contents in the collection can be copied over.
     * 
     * @return CharSource of the formatted database contents
     */
    private CharSequence toCharSequence(Collection<String> databaseStorage,
            int characterCount) {

        final char[] databaseCharArray = new char[characterCount];

        int currIndex = 0;
        for (String table : databaseStorage) {
            System.arraycopy(table.toCharArray(), 0, databaseCharArray,
                    currIndex, table.length());
            currIndex += table.length();
        }

        //Segment class does not make an internal copy of the character array
        //being passed in (more efficient). It also implements a CharSequences 
        //necessary for the CharSource class to create a compatible reader.
        return new Segment(databaseCharArray, 0, characterCount);
    }

    /**
     * Formats input so that it reads as a table in the console or in a text
     * viewer
     */
    private class TableBuilder {

        private final List<String[]> rows = new LinkedList<>();

        private static final String TAB = "\t";
        private static final String NEW_LINE = "\n";
        private static final String SPACE = " ";

        private String tableName;
        private Integer charactersAdded;
        
        /**
         * Set tableName and charactersAdded to their default values.
         */
        public TableBuilder() {
            tableName = "";
            charactersAdded = 0;
        }

        /**
         * Add the section to the top left corner of the table. This is where
         * the name of the table should go.
         *
         * @param tableName Table name
         */
        public void setTableName(String tableName) {
            this.tableName = tableName + NEW_LINE + NEW_LINE;
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
            List<String> rowValues = new LinkedList<>();
            vals.forEach((val) -> {
                rowValues.add(val.toString());
                charactersAdded += val.toString().length();
            });
            rows.add(rowValues.toArray(
                    new String[rowValues.size()]));
        }

        /**
         * Returns a string version of the table, with all of the formatters and
         * escape sequences necessary to print nicely in the console output.
         *
         * @return
         */
        @Override
        public String toString() {
            StringBuilder outputTable = new StringBuilder(charactersAdded);
            outputTable.append(tableName);
            
            Iterator<String[]> rowIterator = rows.iterator();
            while (rowIterator.hasNext()) {
                appendFormattedRow(rowIterator.next(), outputTable);
            }
            
            outputTable.append(NEW_LINE);

            return outputTable.toString();
        }

        /**
         * Outputs a fully formatted row in the table
         *
         * Example: \t John 12345678 john@email.com\n
         *
         * @param row          Array containing unformatted row content
         * @param colMaxWidths An array of column maximum widths, so that
         *                     everything is pretty printed.
         * @param outputTable  Buffer that formatted contents are written to
         */
        private void appendFormattedRow(String[] row, StringBuilder outputTable) {
            outputTable.append(TAB);
            for (int colNum = 0; colNum < row.length; colNum++) {
                outputTable.append(row[colNum]);
                outputTable.append(SPACE);
            }
            outputTable.append(NEW_LINE);
        }
    }
}
