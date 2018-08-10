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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public abstract class TabularFileReader implements AutoCloseable {
    
    public TabularFileReader(AbstractFile file, String localDiskPath) 
            throws IOException, TskCoreException {
        writeDataSourceToLocalDisk(file, localDiskPath);
    }
    
    /**
     * Copies the data source file contents to local drive for processing.
     * 
     * @param file AbstractFile from the data source 
     * @param localDiskPath Local drive path to copy AbstractFile contents
     * @throws IOException Exception writing file contents
     * @throws NoCurrentCaseException Current case closed during file copying
     * @throws TskCoreException Exception finding files from abstract file
     */
    private void writeDataSourceToLocalDisk(AbstractFile file, String localDiskPath) 
        throws IOException, TskCoreException {
        
        File localDatabaseFile = new File(localDiskPath);
        if (!localDatabaseFile.exists()) {
            ContentUtils.writeToFile(file, localDatabaseFile);
        }
    }
    
    public abstract Map<String, String> getTableSchemas();
    
    public abstract Integer getRowCountFromTable(String tableName);
    
    public abstract List<Map<String, Object>> getRowsFromTable(String tableName);

    @Override
    public abstract void close();   
}
