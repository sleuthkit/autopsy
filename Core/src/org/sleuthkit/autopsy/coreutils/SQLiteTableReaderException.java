/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.coreutils;

/**
 *
 * @author dsmyda
 */
public class SQLiteTableReaderException extends Exception {
    
    public SQLiteTableReaderException(String msg, Throwable ex) {
        super(msg, ex);
    }
    
    public SQLiteTableReaderException(Throwable ex) {
        super(ex);
    }
}
