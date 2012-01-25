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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestService implements IngestServiceFsContent {
    
    private static HashDbIngestService instance = null;
    
    private static final Logger logger = Logger.getLogger(HashDbIngestService.class.getName());
    
    private IngestManager manager;
    
    private SleuthkitCase skCase;
    
    private HashDbIngestService() {
        
    }
     
    public static synchronized HashDbIngestService getDefault() {
        if (instance == null) {
            instance = new HashDbIngestService();
        }
        return instance;
    }
    
    
    private final static String NAME = "Hash Ingest Service";
    /**
     * notification from manager that brand new processing should be initiated.
     * Service loads its configuration and performs initialization
     * 
     * @param IngestManager handle to the manager to postMessage() to
     */
    @Override
    public void init(IngestManager manager){
        logger.log(Level.INFO, "init()");
        this.manager = manager;
        this.skCase = Case.getCurrentCase().getSleuthkitCase();
    }
     
     /**
     * notification from manager that there is no more content to process and all work is done.
     * Service performs any clean-up, notifies viewers and may also write results to the black-board
     */
    @Override
    public void complete(){
        logger.log(Level.INFO, "complete()");
    }
    
    /**
     * notification from manager to stop processing due to some interruption (user, error, exception)
     */
    @Override
    public void stop(){
        logger.log(Level.INFO, "stop()");
    }
    
    /**
     * get specific name of the service
     * should be unique across services, a user-friendly name of the service shown in GUI
     */
    @Override
    public String getName(){
        return NAME;
    }
    
    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }

    @Override
    public void process(FsContent fsContent){
        logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
        /*try{
            long status = skCase.analyzeFileMd5(fsContent);
            if(status == 1){
                manager.postMessage(IngestMessage.createDataMessage(123, this, "Found known file", null));
            }else if(status == 2){
                manager.postMessage(IngestMessage.createDataMessage(123, this, "Found known bad file", null));
            }
        } catch (TskException e){
            logger.log(Level.SEVERE, "Couldn't analyze file - see sleuthkit log for details");
        }*/
    }
    
}
