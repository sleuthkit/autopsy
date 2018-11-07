package org.sleuthkit.autopsy.keywordsearch;

import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.Objects;
import java.util.logging.Level;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.SQLiteTableReaderException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.SQLiteTableReader;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Dedicated SqliteTextExtractor to solve the problems associated with Tika's
 * Sqlite parser.
 *
 * Tika problems: 1) Tika fails to open virtual tables 2) Tika fails to open
 * tables with spaces in table name 3) Tika fails to include the table names in
 * output (except for the first table it parses)
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
                throw new TextExtractorException("", ex);
            }
        }

        return new SQLiteStreamReader((AbstractFile) source);
    }

    public class SQLiteStreamReader extends Reader {

        private final SQLiteTableReader reader;
        private Iterator<String> tableNames;
        private String currentTableName;

        private char[] buf;
        private UnfinishedState unfinishedRead;
        private int rowIndex;
        private int columnCount;
        private int totalColumns;

        private int bufIndex;

        public SQLiteStreamReader(AbstractFile file) {
            reader = new SQLiteTableReader.Builder(file)
                    .onColumnNames((columnName) -> {
                        if(columnCount == 0) {
                            fillBuffer("\n"+currentTableName + "\n\n\t");
                        }
                        columnCount++;
                        
                        fillBuffer(columnName + ((columnCount == totalColumns) ? "\n" :" "));
                    })
                    .forAll((Object o) -> {
                        rowIndex++;
                        //Ignore blobs
                        String objectStr = (o instanceof byte[]) ? "" : Objects.toString(o, "");
                        
                        if(rowIndex > 1 && rowIndex < totalColumns) {
                            objectStr += " ";
                        } if(rowIndex == 1){
                            objectStr = "\t" + objectStr + " ";
                        } if(rowIndex == totalColumns) {
                            objectStr += "\n";
                        }
                       
                        fillBuffer(objectStr);
                        rowIndex = rowIndex % totalColumns;
                    }).build();
        }

        private void fillBuffer(String val) {
            for (int i = 0; i < val.length(); i++) {
                if (bufIndex != buf.length) {
                    buf[bufIndex++] = val.charAt(i);
                } else {
                    unfinishedRead = new UnfinishedState(val, i);
                    break;
                }
            }
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            buf = cbuf;

            bufIndex = off;

            if (Objects.isNull(tableNames)) {
                try {
                    tableNames = reader.getTableNames().iterator();
                } catch (SQLiteTableReaderException ex) {
                    //Can't get table names so can't read the file!
                    return -1;
                }
            }

            if (Objects.nonNull(unfinishedRead) && !unfinishedRead.isFinished()) {
                bufIndex += unfinishedRead.read(cbuf, off, len);
            }

            //while buffer is not full!
            while (bufIndex != len) {
                if (Objects.isNull(currentTableName) || reader.isFinished()) {
                    if (tableNames.hasNext()) {
                        currentTableName = tableNames.next();
                        rowIndex = 0;
                        columnCount = 0;
                        try {
                            totalColumns = reader.getColumnCount(currentTableName);
                            reader.read(currentTableName, () -> {
                                return bufIndex == len;
                            });
                        } catch (SQLiteTableReaderException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                    } else {
                        if (bufIndex == off) {
                            return -1;
                        }
                        return bufIndex;
                    }
                } else {
                    try {
                        reader.read(currentTableName, () -> {
                            return bufIndex == len;
                        });
                    } catch (SQLiteTableReaderException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            }

            return bufIndex;
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        /**
         * Wrapper for an unfinished read during the previous chunker call. So,
         * for example, the buffer passed to read() fills and we are left with
         * only a partially read entity. One of these objects will encapsulate
         * its state so that it can pick up where we left off on the next call
         * to read().
         */
        private class UnfinishedState {

            private final String entity;
            private Integer pointer;

            public UnfinishedState(String entity, Integer pointer) {
                this.entity = entity;
                this.pointer = pointer;
            }

            public boolean isFinished() {
                return entity.length() == pointer;
            }

            public int read(char[] buf, int off, int len) {
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
