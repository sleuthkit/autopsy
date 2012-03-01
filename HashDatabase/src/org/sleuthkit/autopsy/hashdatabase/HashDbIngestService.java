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
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Hash;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestService implements IngestServiceFsContent {

    private static HashDbIngestService instance = null;
    private final static String NAME = "Hash Lookup";
    private static final Logger logger = Logger.getLogger(HashDbIngestService.class.getName());
    private IngestManagerProxy managerProxy;
    private SleuthkitCase skCase;
    private static int messageId = 0;
    // Whether or not to do hash lookups (only set to true if there are dbs set)
    private boolean process;
    String nsrlDbPath;
    String knownBadDbPath;
    

    private HashDbIngestService() {
    }

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
    public void init(IngestManagerProxy managerProxy) {
        this.process = false;
        this.managerProxy = managerProxy;
        this.managerProxy.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Started"));
        this.skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            HashDbSettings hashDbSettings = HashDbSettings.getHashDbSettings();

            if ((nsrlDbPath = hashDbSettings.getNSRLDatabasePath()) != null && !nsrlDbPath.equals("")) {
                skCase.setNSRLDatabase(nsrlDbPath);
                this.process = true;
            } else {
                this.managerProxy.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No NSRL database set", "Known file search will not be executed."));
            }

            if ((knownBadDbPath = hashDbSettings.getKnownBadDatabasePath()) != null && !knownBadDbPath.equals("")) {
                skCase.setKnownBadDatabase(knownBadDbPath);
                this.process = true;
            } else {
                this.managerProxy.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No known bad database set", "Known bad file search will not be executed."));
            }

        } catch (TskException ex) {
            logger.log(Level.WARNING, "Setting NSRL and Known database failed", ex);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error getting Hash DB settings", ex);
        }
    }

    /**
     * notification from manager that there is no more content to process and all work is done.
     * Service performs any clean-up, notifies viewers and may also write results to the black-board
     */
    @Override
    public void complete() {
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Complete"));
    }

    /**
     * notification from manager to stop processing due to some interruption (user, error, exception)
     */
    @Override
    public void stop() {
        //manager.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "STOP"));
    }

    /**
     * get specific name of the service
     * should be unique across services, a user-friendly name of the service shown in GUI
     * @return  The name of this Ingest Service
     */
    @Override
    public String getName() {
        return NAME;
    }

    /**
     * Process the given FsContent object
     * 
     * @param fsContent the object to be processed
     */
    @Override
    public void process(FsContent fsContent) {
        if (process) {
            String name = fsContent.getName();
            try {
                String status = skCase.lookupFileMd5(fsContent);
                if (status.equals("known bad")) {
                    BlackboardArtifact badFile = fsContent.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
                    BlackboardAttribute att1 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), NAME, "", fsContent.getName());
                    badFile.addAttribute(att1);
                    BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASHSET_NAME.getTypeID(), NAME, "Known Bad", knownBadDbPath != null ? knownBadDbPath : "");
                    badFile.addAttribute(att2);
                    //TODO: Shouldn't be calculating the hash twice.
                    BlackboardAttribute att3 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5.getTypeID(), NAME, "", Hash.calculateMd5(fsContent));
                    badFile.addAttribute(att3);
                    managerProxy.postMessage(IngestMessage.createDataMessage(++messageId, this, "Found " + status + " file: " + name, "", null, badFile));
                    IngestManager.fireServiceDataEvent(new ServiceDataEvent(NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
                }
            } catch (TskException ex) {
                // TODO: This shouldn't be at level INFO, but it needs to be to hide the popup
                logger.log(Level.INFO, "Couldn't analyze file " + name + " - see sleuthkit log for details", ex);
            }
        }
    }

    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }
    
    @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return true;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return HashDbMgmtPanel.getDefault();
    }
    
    @Override
    public void saveAdvancedConfiguration() {
        HashDbMgmtPanel.getDefault().save();
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
    
}
