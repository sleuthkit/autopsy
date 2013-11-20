/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Hash;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestModule extends IngestModuleAbstractFile {
    private static HashDbIngestModule instance = null;
    public final static String MODULE_NAME = "Hash Lookup";
    public final static String MODULE_DESCRIPTION = "Identifies known and notables files using supplied hash databases, such as a standard NSRL database.";
    final public static String MODULE_VERSION = Version.getVersion();
    private static final Logger logger = Logger.getLogger(HashDbIngestModule.class.getName());
    private HashDbSimpleConfigPanel simpleConfigPanel;
    private HashDbConfigPanel advancedConfigPanel;
    private IngestServices services;
    private SleuthkitCase skCase;
    private static int messageId = 0;
    private int knownBadCount = 0;
    private boolean calcHashesIsSet;
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private List<HashDb> knownHashSets = new ArrayList<>();
    static long calctime = 0;
    static long lookuptime = 0;
    private final Hash hasher = new Hash();

    private HashDbIngestModule() {
    }

    public static synchronized HashDbIngestModule getDefault() {
        if (instance == null) {
            instance = new HashDbIngestModule();
        }
        return instance;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return true;
    }
    
    @Override
    public javax.swing.JPanel getSimpleConfiguration(String context) {
        if (simpleConfigPanel == null) {
           simpleConfigPanel = new HashDbSimpleConfigPanel();  
        }
        
        return simpleConfigPanel;
    }

    @Override
    public void saveSimpleConfiguration() {
        HashDbManager.getInstance().save();
    }
    
    @Override
    public boolean hasAdvancedConfiguration() {
        return true;
    }
    
    @Override
    public javax.swing.JPanel getAdvancedConfiguration(String context) {
        if (advancedConfigPanel == null) {
            advancedConfigPanel = new HashDbConfigPanel();
        }
        
        advancedConfigPanel.load();
        return advancedConfigPanel;
    }

    @Override
    public void saveAdvancedConfiguration() {
        if (advancedConfigPanel != null) {
            advancedConfigPanel.store();
        }
        
        if (simpleConfigPanel != null) {
            simpleConfigPanel.refreshComponents();
        }
    }
    
    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        skCase = Case.getCurrentCase().getSleuthkitCase();

        HashDbManager hashDbManager = HashDbManager.getInstance();
        getHashSetsUsableForIngest(hashDbManager.getKnownBadHashSets(), knownBadHashSets);
        getHashSetsUsableForIngest(hashDbManager.getKnownHashSets(), knownHashSets);        
        calcHashesIsSet = hashDbManager.shouldAlwaysCalculateHashes();

        if (knownHashSets.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No known hash database set", "Known file search will not be executed."));
        }
        if (knownBadHashSets.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No known bad hash database set", "Known bad file search will not be executed."));
        }
    }

    private void getHashSetsUsableForIngest(List<HashDb> hashDbs, List<HashDb> hashDbsForIngest) {
        assert hashDbs != null;
        assert hashDbsForIngest != null;
        hashDbsForIngest.clear();
        for (HashDb db : hashDbs) {
            if (db.getUseForIngest()) {
                try {
                    if (db.hasLookupIndex()) {
                        hashDbsForIngest.add(db);
                    }
                }
                catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error get index status for hash database at " +db.getDatabasePath(), ex);
                }
            }
        }        
    }
    
    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile>pipelineContext, AbstractFile file) {
        //skip unalloc
        if (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }

        return processFile(file);
    }
    
    private ProcessResult processFile(AbstractFile file) {
        // bail out if we have no hashes set
        if ((knownHashSets.isEmpty()) && (knownBadHashSets.isEmpty()) && (calcHashesIsSet == false)) {
            return ProcessResult.OK;
        }

        // calc hash value
        String name = file.getName();
        String md5Hash = file.getMd5Hash();
        if (md5Hash == null || md5Hash.isEmpty()) {
            try {
                long calcstart = System.currentTimeMillis();
                md5Hash = hasher.calculateMd5(file);
                calctime += (System.currentTimeMillis() - calcstart);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error calculating hash of file " + name, ex);
                services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Read Error: " + name,
                        "Error encountered while calculating the hash value for " + name + "."));
                return ProcessResult.ERROR;
            }
        }

        // look up in known bad first
        TskData.FileKnown status = TskData.FileKnown.UNKNOWN;
        boolean foundBad = false;
        ProcessResult ret = ProcessResult.OK;
        for (HashDb db : knownBadHashSets) {
            try {
                long lookupstart = System.currentTimeMillis();
                if (db.lookUp(file)) {
                    status = TskData.FileKnown.BAD;
                }
                lookuptime += (System.currentTimeMillis() - lookupstart);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Couldn't lookup known bad hash for file " + name + " - see sleuthkit log for details", ex);
                services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                        "Error encountered while looking up known bad hash value for " + name + "."));
                ret = ProcessResult.ERROR;
            }

            if (status.equals(TskData.FileKnown.BAD)) {
                foundBad = true;
                knownBadCount += 1;
                try {
                    skCase.setKnown(file, TskData.FileKnown.BAD);
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't set known bad state for file " + name + " - see sleuthkit log for details", ex);
                    services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                            "Error encountered while setting known bad state for " + name + "."));
                    ret = ProcessResult.ERROR;
                }
                String hashSetName = db.getHashSetName();
                processBadFile(file, md5Hash, hashSetName, db.getShowInboxMessages());
            }
        }

        // If the file is not in the known bad sets, search for it in the known sets. 
        // Any hit is sufficient to classify it as known, and there is no need to create 
        // a hit artifact or send a message to the application inbox.
        if (!foundBad) {
            for (HashDb db : knownHashSets) {
                try {
                    long lookupstart = System.currentTimeMillis();
                    if (db.lookUp(file)) {
                        status = TskData.FileKnown.KNOWN;
                    }
                    lookuptime += (System.currentTimeMillis() - lookupstart);
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't lookup known hash for file " + name + " - see sleuthkit log for details", ex);
                    services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                            "Error encountered while looking up known hash value for " + name + "."));
                    ret = ProcessResult.ERROR;
                }

                if (status.equals(TskData.FileKnown.KNOWN)) {
                    try {
                        skCase.setKnown(file, TskData.FileKnown.KNOWN);
                        break;
                    } catch (TskException ex) {
                        logger.log(Level.WARNING, "Couldn't set known state for file " + name + " - see sleuthkit log for details", ex);
                        services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                                "Error encountered while setting known state for " + name + "."));
                        ret = ProcessResult.ERROR;
                    }
                }
            }
        }

        return ret;
    }

    private void processBadFile(AbstractFile abstractFile, String md5Hash, String hashSetName, boolean showInboxMessage) {
        try {
            BlackboardArtifact badFile = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
            //TODO Revisit usage of deprecated constructor as per TSK-583
            //BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, "Known Bad", hashSetName);
            BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, hashSetName);
            badFile.addAttribute(att2);
            BlackboardAttribute att3 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5.getTypeID(), MODULE_NAME, md5Hash);
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

                services.postMessage(IngestMessage.createDataMessage(++messageId, this,
                        "Known Bad: " + abstractFile.getName(),
                        detailsSb.toString(),
                        abstractFile.getName() + md5Hash,
                        badFile));
            }
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating blackboard artifact", ex);
        }
    }

    
    @Override
    public void complete() {
        if ((!knownBadHashSets.isEmpty()) || (!knownHashSets.isEmpty())) {
            StringBuilder detailsSb = new StringBuilder();
            //details
            detailsSb.append("<table border='0' cellpadding='4' width='280'>");

            detailsSb.append("<tr><td>Known bads found:</td>");
            detailsSb.append("<td>").append(knownBadCount).append("</td></tr>");

            detailsSb.append("<tr><td>Total Calculation Time</td><td>").append(calctime).append("</td></tr>\n");
            detailsSb.append("<tr><td>Total Lookup Time</td><td>").append(lookuptime).append("</td></tr>\n");
            detailsSb.append("</table>");

            detailsSb.append("<p>Databases Used:</p>\n<ul>");
            for (HashDb db : knownBadHashSets) {
                detailsSb.append("<li>").append(db.getHashSetName()).append("</li>\n");
            }

            detailsSb.append("</ul>");
            services.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Hash Lookup Results", detailsSb.toString()));
        }
    }
    
    @Override
    public void stop() {
    }
}
