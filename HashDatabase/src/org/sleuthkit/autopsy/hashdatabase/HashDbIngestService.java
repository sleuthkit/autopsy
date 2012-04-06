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

import java.beans.PropertyChangeListener;
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
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestService implements IngestServiceFsContent {

    private static HashDbIngestService instance = null;
    public final static String MODULE_NAME = "Hash Lookup";
    public final static String MODULE_DESCRIPTION = "Identifies known and notables files using supplied hash databases, such as a standard NSRL database.";
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
     * @param managerProxy handle to the manager to postMessage() to
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
        return MODULE_NAME;
    }
    
    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    /**
     * Process the given FsContent object
     * 
     * @param fsContent the object to be processed
     * @return ProcessResult OK if file is unknown and should be processed further, otherwise STOP_COND if file is known
     */
    @Override
    public ProcessResult process(FsContent fsContent) {
        ProcessResult ret = ProcessResult.UNKNOWN;
        process = true;
        if(fsContent.getKnown().equals(TskData.FileKnown.BAD)) {
            ret = ProcessResult.OK;
            process = false;
        }
        if (process) {
            String name = fsContent.getName();
            try {
                String md5Hash = Hash.calculateMd5(fsContent);
                TskData.FileKnown status = skCase.lookupMd5(md5Hash);
                boolean changed = skCase.setKnown(fsContent, status);
                if (status.equals(TskData.FileKnown.BAD)) {
                    BlackboardArtifact badFile = fsContent.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
                    BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASHSET_NAME.getTypeID(), MODULE_NAME, "Known Bad", knownBadDbPath != null ? knownBadDbPath : "");
                    badFile.addAttribute(att2);
                    BlackboardAttribute att3 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5.getTypeID(), MODULE_NAME, "", md5Hash);
                    badFile.addAttribute(att3);
                    StringBuilder detailsSb = new StringBuilder();
                    //details
                    detailsSb.append("<table border='0' cellpadding='4' width='280'>");
                    //hit
                    detailsSb.append("<tr>");
                    detailsSb.append("<th>File Name</th>");
                    detailsSb.append("<td>").append(name).append("</td>");
                    detailsSb.append("</tr>");

                    detailsSb.append("<tr>");
                    detailsSb.append("<th>MD5 Hash</th>");
                    detailsSb.append("<td>").append(md5Hash).append("</td>");
                    detailsSb.append("</tr>");

                    detailsSb.append("<tr>");
                    detailsSb.append("<th>Hashset Name</th>");
                    detailsSb.append("<td>").append(knownBadDbPath).append("</td>");
                    detailsSb.append("</tr>");
                    
                    detailsSb.append("</table>");

                    managerProxy.postMessage(IngestMessage.createDataMessage(++messageId, this, "Notable: " + name, detailsSb.toString(), name+md5Hash, badFile));
                    IngestManager.fireServiceDataEvent(new ServiceDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
                    ret = ProcessResult.OK;
                } else if (status.equals(TskData.FileKnown.KNOWN)) {
                    ret = ProcessResult.COND_STOP;
                }
                else {
                    ret = ProcessResult.OK;
                }
            } catch (TskException ex) {
                // TODO: This shouldn't be at level INFO, but it needs to be to hide the popup
                logger.log(Level.INFO, "Couldn't analyze file " + name + " - see sleuthkit log for details", ex);
                ret = ProcessResult.ERROR;
            } catch (IOException ex) {
                // TODO: This shouldn't be at level INFO, but it needs to be to hide the popup
                logger.log(Level.INFO, "Error reading file", ex);
                ret = ProcessResult.ERROR;
            }
        }
        return ret;
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
        return true;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return true;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return new HashDbSimplePanel();
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
