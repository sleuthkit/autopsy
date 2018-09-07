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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract reader interface for retrieving contents from files via a common
 * API. 
 */
public abstract class AbstractReader implements AutoCloseable {
    
    public AbstractReader(Content file) 
            throws FileReaderInitException {
        
        try {
            writeDataSourceToLocalDisk(file, getLocalDiskPath(file));
        } catch (FileReaderException ex) {
            throw new FileReaderInitException(ex);
        }

    }
    
     /**
     * Generates a local disk path for abstract file contents to be copied.
     * All file sources must be copied to local disk to be opened by 
     * abstract reader.
     * 
     * @param file The database abstract file
     * @return Valid local path for copying
     * @throws NoCurrentCaseException if the current case has been closed.
     */
    final String getLocalDiskPath(Content file) throws FileReaderException {
        try {
            return Case.getCurrentCaseThrows().getTempDirectory() + 
                    File.separator + file.getId() + file.getName();
        } catch (NoCurrentCaseException ex) {
            throw new FileReaderException(ex);
        }
    }
    
    /**
     * Copies the data source file contents to local drive for processing.
     * This function is common to all readers.
     * 
     * @param file AbstractFile from the data source 
     * @param localDiskPath Local drive path to copy AbstractFile contents
     * @throws IOException Exception writing file contents
     * @throws NoCurrentCaseException Current case closed during file copying
     * @throws TskCoreException Exception finding files from abstract file
     */
    private void writeDataSourceToLocalDisk(Content file, String localDiskPath) 
        throws FileReaderInitException {
        
        try {
            File localDatabaseFile = new File(localDiskPath);
            if (!localDatabaseFile.exists()) {
                ContentUtils.writeToFile(file, localDatabaseFile);
            }
        } catch (IOException ex) {
            throw new FileReaderInitException(ex);
        }
    }
    
    /**
     * Return the a mapping of table names to table schemas (may be in the form of 
     * headers or create table statements for databases).
     * 
     * @return Mapping of table names to schemas
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    public abstract Map<String, String> getTableSchemas() throws FileReaderException;
    
    /**
     * Returns the row count fo the given table name.
     * 
     * @param tableName
     * @return number of rows in the current table
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    public abstract Integer getRowCountFromTable(String tableName) throws FileReaderException;
    
    /**
     * Returns a collection view of the rows in a table.
     * 
     * @param tableName
     * @return List view of the rows in the table
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    public abstract List<Map<String, Object>> getRowsFromTable(String tableName) throws FileReaderException;

    /**
     * Returns a window of rows starting at the offset and ending when the number of rows read 
     * equals the 'numRowsToRead' parameter or there is nothing left to read.
     * 
     * @param tableName table name to be read from
     * @param offset start index to begin reading
     * @param numRowsToRead number of rows to read past offset
     * @return List view of the rows in the table
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderException 
     */
    public abstract List<Map<String, Object>> getRowsFromTable(String tableName, 
            int offset, int numRowsToRead) throws FileReaderException;
    
    @Override
    public abstract void close(); 
    
    /**
     * Checked exceptions are specific to a given implementation, so this custom
     * exception allows for a common interface to accommodate all of them. Init
     * exception allows for more flexibility in logging.
     */
    public static class FileReaderInitException extends Exception {
        public FileReaderInitException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public FileReaderInitException(Throwable cause) {
            super(cause);
        }
        
        public FileReaderInitException(String message) {
            super(message);
        }
    }

    /**
     * Checked exceptions are specific to a given implementation, so this custom
     * exception allows for a common interface to accommodate all of them.
     */
    public class FileReaderException extends Exception {
        public FileReaderException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public FileReaderException(Throwable cause) {
            super(cause);
        }
        
        public FileReaderException(String message) {
            super(message);
        }
    }
}