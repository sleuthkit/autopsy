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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * @author dsmyda
 */
public class ExcelReader extends TabularFileReader {
    static {    
        final String SUPPORTED_MIME_TYPE = "application/vnd.ms-excel";
        FileReaderFactory.registerReaderType(SUPPORTED_MIME_TYPE, ExcelReader.class);
    }

    public ExcelReader(AbstractFile file, String localDiskPath) 
            throws IOException, TskCoreException {
        super(file, localDiskPath);
    }

    @Override
    public Map<String, String> getTableSchemas() throws Exception {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Integer getRowCountFromTable(String tableName) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public List<Map<String, Object>> getRowsFromTable(String tableName) throws Exception {
        throw new UnsupportedOperationException("Not supported yet."); 
    }

    @Override
    public void close() {
        throw new UnsupportedOperationException("Not supported yet."); 
    }
}
