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

import org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderInitException;
import org.sleuthkit.datamodel.Content;

/**
 * Factory for creating the correct reader given the mime type of a file. 
 */
public final class FileReaderFactory {
    
    private FileReaderFactory() {
    }
    /**
     * Instantiates the appropriate reader given the mimeType argument. Currently
     * supports SQLite files and Excel files (.xls and .xlsx). BIFF5 format of .xls
     * is not supported.
     * 
     * @param mimeType mimeType passed in from the ingest module
     * @param file current file under inspection
     * @return The correct reader class needed to read the file contents
     * @throws org.sleuthkit.autopsy.tabulardatareader.AbstractReader.FileReaderInitException 
     */
    public static AbstractReader createReader(String mimeType, Content file) 
            throws FileReaderInitException {
        switch (mimeType) {
            case "application/x-sqlite3":
                return new SQLiteReader(file);
            case "application/vnd.ms-excel":
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
                try {
                    return new ExcelReader(file, mimeType);
                    //Catches runtime exceptions being emitted from Apache
                    //POI (such as EncryptedDocumentException) and wraps them
                    //into FileReaderInitException to be caught and logged
                    //in the ingest module.
                } catch(Exception poiInitException) {
                    throw new FileReaderInitException(poiInitException);
                }
            default:
                throw new FileReaderInitException(String.format("Reader for mime "
                        + "type [%s] is not supported", mimeType));
        }
    }
}
