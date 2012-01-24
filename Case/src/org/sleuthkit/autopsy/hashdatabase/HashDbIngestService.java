/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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



package org.sleuthkit.autopsy.hashdatabase;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.datamodel.FsContent;

public class HashDbIngestService implements IngestServiceFsContent {
    
    private static HashDbIngestService instance = null;

    private static String SERVICE_NAME = "Hash Db";
    
    private static final Logger logger = Logger.getLogger(HashDbIngestService.class.getName());
    
    private HashDbIngestService() {
        
    }
     
    public static synchronized HashDbIngestService getDefault() {
        if (instance == null) {
            instance = new HashDbIngestService();
        }
        return instance;
    }
    
    @Override
    public void process(FsContent fsContent) {
        logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    @Override
    public void init(IngestManager manager) {
        logger.log(Level.INFO, "init()");
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");
    }
    
    
    
}
