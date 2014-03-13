/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012-2013 Basis Technology Corp.
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
    private List<Closeable> services = new ArrayList<>();
    
    // services
    private FileManager fileManager;
    private TagsManager tagsManager;

    public Services(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        //create and initialize FileManager as early as possibly in the new/opened Case
        fileManager = new FileManager(tskCase);
        services.add(fileManager);
        
        tagsManager = new TagsManager(tskCase);
        services.add(tagsManager);
    }
    
    public FileManager getFileManager() {
        return fileManager;
    }
    
    public TagsManager getTagsManager() {
        return tagsManager;
    }

    @Override
    public void close() throws IOException {
        // close all services
        for (Closeable service : services) {
            service.close();
        }
    }
}
