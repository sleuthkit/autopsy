/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import org.sleuthkit.datamodel.SleuthkitCase;

/**
 *
 * @author mciver
 */
public class Services {
    
    private SleuthkitCase tskCase;
    private FileManager fileManager;

    public Services(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
    }
    
    public FileManager getFileManager() {
        if (fileManager == null) {
            fileManager = new FileManager(tskCase);
        }
        return fileManager;
    }
}
