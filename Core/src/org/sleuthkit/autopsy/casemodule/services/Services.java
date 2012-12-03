/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
