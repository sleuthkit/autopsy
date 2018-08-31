/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader;
import org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderInitException;
import org.sleuthkit.autopsy.tabulardatareader.SQLiteReader;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public class SqliteTextExtractor extends ContentTextExtractor {
    
    private final String SQLITE_MIMETYPE = "application/x-sqlite3";

    @Override
    boolean isContentTypeSpecific() {
        return true;
    }

    @Override
    boolean isSupported(Content file, String detectedFormat) {
        return SQLITE_MIMETYPE.equals(detectedFormat);
    }

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
        //TODO - come back.
    }
    
    private final class SqliteTextReader extends InputStream {
    
        private final Content source;
        private final SQLiteReader reader;
        private StringBuffer fileData;
        private int currIndex;
        private final int NO_CONTENT_LEFT = -1;
        
        public SqliteTextReader(Content source) throws TextExtractorException {
            this.source = source;
            try {
                this.reader = new SQLiteReader((AbstractFile) source.getDataSource());
            } catch (TskCoreException ex) {
                throw new TextExtractorException(
                        String.format("Encountered a TskCoreException when getting "
                                + "root data source for Content with id:[%s], name:[%s].", 
                                source.getId(), source.getName()));
            } catch (FileReaderInitException ex) {
                throw new TextExtractorException(
                        String.format("Encountered a FileReaderInitException when trying "
                                + "to initialize a SQLiteReader for Content with id:[%s], "
                                + "name:[%s].", source.getId(), source.getName()));
            }
            this.fileData = new StringBuffer();
            //Fill the entire buffer on instantiation
            copySqliteFileIntoStringBuffer(source);
        }
        
        private void copySqliteFileIntoStringBuffer(Content source){
            Map<String, String> tables;
            try {
                //Table name to table schema mapping
                tables = reader.getTableSchemas();    
                for(String tableName : tables.keySet()) {
                try {
                    List<Map<String, Object>> rowsInTable = reader.getRowsFromTable(tableName);
                    for(Map<String, Object> row : rowsInTable) {
                        //Only interested in row values, not the column name
                        row.values().forEach(cell -> {
                            fileData.append(cell.toString());
                        });
                    }
                } catch(AbstractReader.FileReaderException ex) {
                   // logger.log(Level.WARNING, 
                   //         String.format("Error attempting to read file table: [%s]" //NON-NLS
                   //                 + " for file: [%s] (id=%d).", tableName, //NON-NLS
                   //                 source.getName(), source.getId()),
                   //        ex);
                }
            }
            } catch (AbstractReader.FileReaderException ex) {
                //logger.log(Level.WARNING, String.format("Error attempting to get tables from " //NON-NLS
                //                    + "file: [%s] (id=%d).", //NON-NLS
                //                    source.getName(), source.getId()), ex);
            }
        }

        @Override
        public int read() throws IOException {
            if (currIndex == fileData.length() - 1) {
                return NO_CONTENT_LEFT;
            }
            return fileData.charAt(currIndex++);
        }
    }
    
}
