/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.textextractors;

/**
 * 
 */
public class InitReaderException extends Exception {
    public InitReaderException(String msg, Throwable ex) {
        super(msg, ex);
    }

    public InitReaderException(Throwable ex) {
        super(ex);
    }

    public InitReaderException(String msg) {
        super(msg);
    }
}