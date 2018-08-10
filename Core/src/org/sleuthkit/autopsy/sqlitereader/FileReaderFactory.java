/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.sqlitereader;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * @author dsmyda
 */
public final class FileReaderFactory {
    
    private final static Map<String, Class> REGISTERED_READER_TYPES;
            
    static {
        REGISTERED_READER_TYPES = new HashMap();
    }
    
    public static void registerReaderType(String mimeType, Class reader) {
        REGISTERED_READER_TYPES.put(mimeType, reader);
    }
    
    public static TabularFileReader createReader(String mimeType, AbstractFile file, String localDiskPath) 
            throws InstantiationException, IllegalAccessException, 
            IllegalArgumentException, InvocationTargetException, NoSuchMethodException {
        
        Class readerClass = (Class) REGISTERED_READER_TYPES.get(mimeType);
        Constructor readerConstructor = Class.class.getDeclaredConstructor(new Class[] {String.class, String.class});
        return (TabularFileReader) readerConstructor.newInstance(new Object[] {file, localDiskPath});
    }
}
