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

import java.io.IOException;
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
    private final static String NAME = "Hash Ingest Service";
    private static final Logger logger = Logger.getLogger(HashDbIngestService.class.getName());
    private IngestManager manager;
    private SleuthkitCase skCase;
    private static int messageId = 0;
    
    public static synchronized HashDbIngestService getDefault() {
        if (instance == null) {
            instance = new HashDbIngestService();
        }
        return instance;
    }
    
    /**
     * notification from manager that brand new processing should be initiated.
     * Service loads its configuration and performs initialization
     * 
     * @param IngestManager handle to the manager to postMessage() to
     */
    @Override
    public void init(IngestManager manager){
        this.manager = manager;
        manager.postMessage(IngestMessage.createMessage(1, IngestMessage.MessageType.INFO, this, "INIT"));
        this.skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            HashDbSettings hashDbSettings = HashDbSettings.getHashDbSettings();
            
            String nsrlDbPath;
            if((nsrlDbPath = hashDbSettings.getNSRLDatabasePath()) != null && !nsrlDbPath.equals(""))
                skCase.setNSRLDatabase(nsrlDbPath);
            else
                manager.postMessage(IngestMessage.createErrorMessage(++messageId, this, "No NSRL database set"));
            
            String knownBadDbPath;
            if((knownBadDbPath = hashDbSettings.getKnownBadDatabasePath()) != null && !knownBadDbPath.equals(""))
                skCase.setKnownBadDatabase(knownBadDbPath);
            else
                manager.postMessage(IngestMessage.createErrorMessage(++messageId, this, "No known bad database set"));
            
        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Setting NSRL and Known database failed", ex);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error getting Hash DB settings", ex);
        }
    }
     
     /**
     * notification from manager that there is no more content to process and all work is done.
     * Service performs any clean-up, notifies viewers and may also write results to the black-board
     */
    @Override
    public void complete(){
        manager.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "COMPLETE"));
    }
    
    /**
     * notification from manager to stop processing due to some interruption (user, error, exception)
     */
    @Override
    public void stop(){
        manager.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "STOP"));
    }
    
    /**
     * get specific name of the service
     * should be unique across services, a user-friendly name of the service shown in GUI
     * @return  The name of this Ingest Service
     */
    @Override
    public String getName(){
        return NAME;
    }

    /**
     * Process the given FsContent object
     * 
     * @param fsContent the object to be processed
     */
    @Override
    public void process(FsContent fsContent){
        String name = fsContent.getName();
        try{
            String status = skCase.lookupFileMd5(fsContent);
            if(status.equals("known") || status.equals("known bad")){
                manager.postMessage(IngestMessage.createDataMessage(++messageId, this, name + " is a " + status + " file", null));
            }
        } catch (TskException ex){
            logger.log(Level.SEVERE, "Couldn't analyze file " + name + " - see sleuthkit log for details", ex);
        }
    }

    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }
    
}
