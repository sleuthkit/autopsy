/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.sqlitereader;

import org.sleuthkit.autopsy.sqlitereader.AbstractReader.FileReaderInitException;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author dsmyda
 */
public final class FileReaderFactory {
    
    public static AbstractReader createReader(String mimeType, AbstractFile file, 
            String localDiskPath) throws FileReaderInitException {
        switch(mimeType) {
            case "application/x-sqlite3":
                return new SQLiteReader(file, localDiskPath);
            case "application/vnd.ms-excel":
                return new ExcelReader(file, localDiskPath);
            default:
                throw new FileReaderInitException(String.format("Reader for mime "
                        + "type [%s] is not supported", mimeType));
        }
    }
}
