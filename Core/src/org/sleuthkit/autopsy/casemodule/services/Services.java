/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * A class to manage various services.
 */
public class Services implements Closeable {
    
    private SleuthkitCase tskCase;
    
    // NOTE: all new services added to Services class must be added to this list
    // of services.
    private List<Closeable> services = new ArrayList<Closeable>();
    
    // services
    private FileManager fileManager;

    public Services(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
    }
    
    public synchronized FileManager getFileManager() {
        if (fileManager == null) {
            fileManager = new FileManager(tskCase);
            services.add(fileManager);
        }
        return fileManager;
    }

    @Override
    public void close() throws IOException {
        // close all services
        for (Closeable service : services) {
            service.close();
        }
    }
}
