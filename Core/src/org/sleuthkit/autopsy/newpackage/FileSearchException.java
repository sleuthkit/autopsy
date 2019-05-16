/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.newpackage;

/**
 *
 */
public class FileSearchException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public FileSearchException(String message) {
        super(message);
    }
    
    public FileSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
