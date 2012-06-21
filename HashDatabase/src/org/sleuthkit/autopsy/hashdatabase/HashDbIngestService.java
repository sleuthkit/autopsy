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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServiceAbstractFile;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Hash;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestService implements IngestServiceAbstractFile {

    private static HashDbIngestService instance = null;
    public final static String MODULE_NAME = "Hash Lookup";
    public final static String MODULE_DESCRIPTION = "Identifies known and notables files using supplied hash databases, such as a standard NSRL database.";
    private static final Logger logger = Logger.getLogger(HashDbIngestService.class.getName());
    private Processor processor = new Processor();
    private IngestManagerProxy managerProxy;
    private SleuthkitCase skCase;
    private static int messageId = 0;
    private int count;
    // Whether or not to do hash lookups (only set to true if there are dbs set)
    private boolean nsrlIsSet;
    private boolean knownBadIsSet;
    private HashDb nsrlSet;
    private int nsrlPointer;
    static long calctime = 0;
    static long lookuptime = 0;
    private Map<Integer, HashDb> knownBadSets = new HashMap<Integer, HashDb>();
    

    private HashDbIngestService() {
        count = 0;
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
        HashDbManagementPanel.getDefault().setIngestRunning(true);
        HashDbSimplePanel.setIngestRunning(true);
        this.managerProxy = managerProxy;
        this.managerProxy.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Started"));
        this.skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            HashDbXML hdbxml = HashDbXML.getCurrent();
            nsrlSet = null;
            knownBadSets.clear();
            skCase.clearLookupDatabases();
            nsrlIsSet = false;
            knownBadIsSet = false;
            
            HashDb nsrl = hdbxml.getNSRLSet();
            if(nsrl != null && IndexStatus.isIngestible(nsrl.status())) {
                nsrlIsSet = true;
                this.nsrlSet = nsrl;
                nsrlPointer = skCase.setNSRLDatabase(nsrl.getDatabasePaths().get(0));
            }

            for(HashDb db : hdbxml.getKnownBadSets()) {
                IndexStatus status = db.status();
                if (db.getUseForIngest() && IndexStatus.isIngestible(status)) {
                    knownBadIsSet = true;
                    int ret = skCase.addKnownBadDatabase(db.getDatabasePaths().get(0)); // TODO: support multiple paths
                    knownBadSets.put(ret, db);
                }
            }
            
            if (!nsrlIsSet) {
                this.managerProxy.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No NSRL database set", "Known file search will not be executed."));
            }
            if (!knownBadIsSet) {
                this.managerProxy.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No known bad database set", "Known bad file search will not be executed."));
            }

        } catch (TskException ex) {
            logger.log(Level.WARNING, "Setting NSRL and Known database failed", ex);
        }
    }

    /**
     * notification from manager that there is no more content to process and all work is done.
     * Service performs any clean-up, notifies viewers and may also write results to the black-board
     */
    @Override
    public void complete() {
        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>");
        
        detailsSb.append("<tr>");
        detailsSb.append("<th>Number of notable files found:</th>");
        detailsSb.append("<td>").append(count).append("</td>");
        detailsSb.append("</tr>");

        detailsSb.append("<tr>");
        detailsSb.append("<th>Notable databases used:</th>");
        detailsSb.append("<td>Calc Time: ").append(calctime).append(" Lookup Time: " ).append(lookuptime).append("</td>");
        detailsSb.append("</tr>");
        
        for(HashDb db : knownBadSets.values()) {
            detailsSb.append("<tr><th>");
            detailsSb.append(db.getName());
            detailsSb.append("</th><td>");
            detailsSb.append(db.getDatabasePaths().get(0)); // TODO: support multiple database paths
            detailsSb.append("</td></tr>");
        }
        
        detailsSb.append("</table>");
        managerProxy.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Hash Ingest Complete", detailsSb.toString()));
        
        HashDbManagementPanel.getDefault().setIngestRunning(false);
        HashDbSimplePanel.setIngestRunning(false);
    }

    /**
     * notification from manager to stop processing due to some interruption (user, error, exception)
     */
    @Override
    public void stop() {
        //manager.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "STOP"));
        HashDbManagementPanel.getDefault().setIngestRunning(false);
        HashDbSimplePanel.setIngestRunning(false);
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
     * Process the given AbstractFile object
     * 
     * @param abstractFile the object to be processed
     * @return ProcessResult OK if file is unknown and should be processed further, otherwise STOP_COND if file is known
     */
    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        return abstractFile.accept(processor);
    }

    @Override
    public ServiceType getType() {
        return ServiceType.AbstractFile;
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
        return HashDbManagementPanel.getDefault();
    }
    
    @Override
    public void saveAdvancedConfiguration() {
    }
    
    @Override
    public void saveSimpleConfiguration() {
    }
    
    private void processBadFile(AbstractFile abstractFile, String md5Hash, String hashSetName, boolean showInboxMessage) {
        try {
            BlackboardArtifact badFile = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
            BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, "Known Bad", hashSetName);
            badFile.addAttribute(att2);
            BlackboardAttribute att3 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5.getTypeID(), MODULE_NAME, "", md5Hash);
            badFile.addAttribute(att3);
            if (showInboxMessage) {
                StringBuilder detailsSb = new StringBuilder();
                //details
                detailsSb.append("<table border='0' cellpadding='4' width='280'>");
                //hit
                detailsSb.append("<tr>");
                detailsSb.append("<th>File Name</th>");
                detailsSb.append("<td>").append(abstractFile.getName()).append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("<tr>");
                detailsSb.append("<th>MD5 Hash</th>");
                detailsSb.append("<td>").append(md5Hash).append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("<tr>");
                detailsSb.append("<th>Hashset Name</th>");
                detailsSb.append("<td>").append(hashSetName).append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("</table>");

                managerProxy.postMessage(IngestMessage.createDataMessage(++messageId, this,
                        "Notable: " + abstractFile.getName(),
                        detailsSb.toString(),
                        abstractFile.getName() + md5Hash,
                        badFile));
            }
            IngestManager.fireServiceDataEvent(new ServiceDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating blackboard artifact", ex);
        }

    }
    
    private class Processor extends ContentVisitor.Default<ProcessResult> {

        @Override
        protected ProcessResult defaultVisit(Content cntnt) {
            return ProcessResult.OK;
        }
        
        @Override
        public ProcessResult visit(File f) {
            return process(f);
        }

        private ProcessResult process(FsContent fsContent) {

            ProcessResult ret = ProcessResult.UNKNOWN;
            boolean processFile = true;
            if (fsContent.getKnown().equals(TskData.FileKnown.BAD)) {
                ret = ProcessResult.OK;
                processFile = false;
            }
            if (processFile && (nsrlIsSet || knownBadIsSet)) {
                String name = fsContent.getName();
                try {
                    String md5Hash = fsContent.getMd5Hash();
                    if (md5Hash == null || md5Hash.isEmpty()) {
                        long calcstart = System.currentTimeMillis();
                        md5Hash = Hash.calculateMd5(fsContent);
                        calctime += (System.currentTimeMillis()-calcstart);
                    }
                    TskData.FileKnown status = TskData.FileKnown.UKNOWN;
                    boolean foundBad = false;
                    for (Map.Entry<Integer, HashDb> entry : knownBadSets.entrySet()) {
                        long lookupstart = System.currentTimeMillis();
                        status = skCase.knownBadLookupMd5(md5Hash, entry.getKey());
                        lookuptime += (System.currentTimeMillis()-lookupstart);
                        if (status.equals(TskData.FileKnown.BAD)) {
                            foundBad = true;
                            count += 1;
                            skCase.setKnown(fsContent, status);
                            String hashSetName = entry.getValue().getName();
                            processBadFile(fsContent, md5Hash, hashSetName, entry.getValue().getShowInboxMessages());
                        }
                        ret = ProcessResult.OK;
                    }
                    if (!foundBad && nsrlIsSet) {
                        long lookupstart = System.currentTimeMillis();
                        status = skCase.nsrlLookupMd5(md5Hash);
                        lookuptime += (System.currentTimeMillis()-lookupstart);
                        if (status.equals(TskData.FileKnown.KNOWN)) {
                            skCase.setKnown(fsContent, status);
                            ret = ProcessResult.COND_STOP;
                        }
                    }
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't analyze file " + name + " - see sleuthkit log for details", ex);
                    managerProxy.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestService.this, "Hash Lookup Error: " + name,
                            "Error encountered while updating the hash values for " + name + "."));
                    ret = ProcessResult.ERROR;
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Error reading file " + name, ex);
                    managerProxy.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestService.this, "Read Error: " + name,
                            "Error encountered while calculating the hash value for " + name + "."));
                    ret = ProcessResult.ERROR;
                }
            }
            return ret;
        }

    }

}
