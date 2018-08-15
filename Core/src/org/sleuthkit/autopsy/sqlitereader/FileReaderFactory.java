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

import org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderInitException;
import org.sleuthkit.datamodel.AbstractFile;

/**
 * Factory for creating the correct reader given the mime type of a file. 
 */
public final class FileReaderFactory {
    
    /**
     * 
     * @param mimeType
     * @param file
     * @param localDiskPath
     * @return
     * @throws org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderInitException 
     */
    public static AbstractReader createReader(String mimeType, AbstractFile file, 
            String localDiskPath) throws FileReaderInitException {
        switch (mimeType) {
            case "application/x-sqlite3":
                return new SQLiteReader(file, localDiskPath);
            case "application/vnd.ms-excel":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                return new ExcelReader(file, localDiskPath, mimeType);
            default:
                throw new FileReaderInitException(String.format("Reader for mime "
                        + "type [%s] is not supported", mimeType));
        }
    }
}
