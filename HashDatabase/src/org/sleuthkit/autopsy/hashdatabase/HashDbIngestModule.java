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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestMessage;
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
import org.sleuthkit.autopsy.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.datamodel.HashInfo;

public class HashDbIngestModule extends IngestModuleAdapter implements FileIngestModule {
    private static final Logger logger = Logger.getLogger(HashDbIngestModule.class.getName());
    private static final int MAX_COMMENT_SIZE = 500;
    private static int messageId = 0; // RJCTODO: This is not thread safe
    private final IngestServices services = IngestServices.getDefault();
    private final Hash hasher = new Hash();
    private final SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
    private final HashDbManager hashDbManager = HashDbManager.getInstance();
    private final HashLookupModuleSettings settings;
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private List<HashDb> knownHashSets = new ArrayList<>();
    private int knownBadCount = 0;
    private long calctime = 0;
    private long lookuptime = 0;
    
    HashDbIngestModule(HashLookupModuleSettings settings) {
        this.settings = settings;
    }
        
    @Override
    public void startUp(org.sleuthkit.autopsy.ingest.IngestJobContext context) throws IngestModuleException {
        getEnabledHashSets(hashDbManager.getKnownBadFileHashSets(), knownBadHashSets);
        if (knownBadHashSets.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageId,
                                HashLookupModuleFactory.getModuleName(),
                                NbBundle.getMessage(this.getClass(),
                                                    "HashDbIngestModule.noKnownBadHashDbSetMsg"),
                                NbBundle.getMessage(this.getClass(),
                                                    "HashDbIngestModule.knownBadFileSearchWillNotExecuteWarn")));
        }        

        getEnabledHashSets(hashDbManager.getKnownFileHashSets(), knownHashSets);        
        if (knownHashSets.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageId,
                                HashLookupModuleFactory.getModuleName(),
                                NbBundle.getMessage(this.getClass(),
                                                    "HashDbIngestModule.noKnownHashDbSetMsg"),
                                NbBundle.getMessage(this.getClass(),
                                                    "HashDbIngestModule.knownFileSearchWillNotExecuteWarn")));
        }
    }
    
    private void getEnabledHashSets(List<HashDb> hashSets, List<HashDb> enabledHashSets) {
        enabledHashSets.clear();
        for (HashDb db : hashSets) {
            if (settings.isHashSetEnabled(db.getHashSetName())) {
                try {
                    if (db.hasIndex()) {
                        enabledHashSets.add(db);
                    }
                }
                catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error getting index status for " + db.getHashSetName() +" hash database", ex);
                }
            }
        }        
    }
    
    @Override
    public ProcessResult process(AbstractFile file) {
        // Skip unallocated space files.
        if (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return ProcessResult.OK;
        }
        
        // bail out if we have no hashes set
        if ((knownHashSets.isEmpty()) && (knownBadHashSets.isEmpty()) && (!settings.shouldCalculateHashes())) {
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
                services.postMessage(IngestMessage.createErrorMessage(++messageId,
                                      HashLookupModuleFactory.getModuleName(),
                                      NbBundle.getMessage(this.getClass(),
                                                          "HashDbIngestModule.fileReadErrorMsg",
                                                          name),
                                      NbBundle.getMessage(this.getClass(),
                                                          "HashDbIngestModule.calcHashValueErr",
                                                          name)));
                return ProcessResult.ERROR;
            }
        }

        // look up in known bad first
        boolean foundBad = false;
        ProcessResult ret = ProcessResult.OK;
        for (HashDb db : knownBadHashSets) {
            try {
                long lookupstart = System.currentTimeMillis();
                HashInfo hashInfo = db.lookUp(file);
                if (null != hashInfo) {
                    foundBad = true;
                    knownBadCount += 1;
                    try {
                        skCase.setKnown(file, TskData.FileKnown.BAD);
                    } catch (TskException ex) {
                        logger.log(Level.WARNING, "Couldn't set known bad state for file " + name + " - see sleuthkit log for details", ex);
                        services.postMessage(IngestMessage.createErrorMessage(++messageId,
                                              HashLookupModuleFactory.getModuleName(),
                                              NbBundle.getMessage(this.getClass(),
                                                                  "HashDbIngestModule.hashLookupErrorMsg",
                                                                  name),
                                              NbBundle.getMessage(this.getClass(),
                                                                  "HashDbIngestModule.settingKnownBadStateErr",
                                                                  name)));
                        ret = ProcessResult.ERROR;
                    }                    
                    String hashSetName = db.getHashSetName();
                    
                    String comment = "";                   
                    ArrayList<String> comments = hashInfo.getComments();
                    int i = 0;
                    for (String c : comments) {
                        if (++i > 1) {
                            comment += " ";
                        }
                        comment += c;
                        if (comment.length() > MAX_COMMENT_SIZE) {
                            comment = comment.substring(0, MAX_COMMENT_SIZE) + "...";
                            break;
                        }                        
                    }

                    postHashSetHitToBlackboard(file, md5Hash, hashSetName, comment, db.getSendIngestMessages());
                }
                lookuptime += (System.currentTimeMillis() - lookupstart);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Couldn't lookup known bad hash for file " + name + " - see sleuthkit log for details", ex);
                services.postMessage(IngestMessage.createErrorMessage(++messageId,
                                      HashLookupModuleFactory.getModuleName(),
                                      NbBundle.getMessage(this.getClass(),
                                                          "HashDbIngestModule.hashLookupErrorMsg",
                                                          name),
                                      NbBundle.getMessage(this.getClass(),
                                                          "HashDbIngestModule.lookingUpKnownBadHashValueErr",
                                                          name)));
                ret = ProcessResult.ERROR;
            }
        }

        // If the file is not in the known bad sets, search for it in the known sets. 
        // Any hit is sufficient to classify it as known, and there is no need to create 
        // a hit artifact or send a message to the application inbox.
        if (!foundBad) {
            for (HashDb db : knownHashSets) {
                try {
                    long lookupstart = System.currentTimeMillis();
                    if (db.hasMd5HashOf(file)) {
                        try {
                            skCase.setKnown(file, TskData.FileKnown.KNOWN);
                            break;
                        } catch (TskException ex) {
                            logger.log(Level.WARNING, "Couldn't set known state for file " + name + " - see sleuthkit log for details", ex);
                            ret = ProcessResult.ERROR;
                        }
                    }
                    lookuptime += (System.currentTimeMillis() - lookupstart);
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't lookup known hash for file " + name + " - see sleuthkit log for details", ex);
                    services.postMessage(IngestMessage.createErrorMessage(++messageId,
                                          HashLookupModuleFactory.getModuleName(),
                                          NbBundle.getMessage(this.getClass(),
                                                              "HashDbIngestModule.hashLookupErrorMsg",
                                                              name),
                                          NbBundle.getMessage(this.getClass(),
                                                              "HashDbIngestModule.lookingUpKnownHashValueErr",
                                                              name)));
                    ret = ProcessResult.ERROR;
                }
            }
        }

        return ret;
    }
        
    private void postHashSetHitToBlackboard(AbstractFile abstractFile, String md5Hash, String hashSetName, String comment, boolean showInboxMessage) {
        try {
            String MODULE_NAME = NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleName");
            
            BlackboardArtifact badFile = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
            //TODO Revisit usage of deprecated constructor as per TSK-583
            //BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, "Known Bad", hashSetName);
            BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, hashSetName);
            badFile.addAttribute(att2);
            BlackboardAttribute att3 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5.getTypeID(), MODULE_NAME, md5Hash);
            badFile.addAttribute(att3);
            BlackboardAttribute att4 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT.getTypeID(), MODULE_NAME, comment);
            badFile.addAttribute(att4);
            
            if (showInboxMessage) {
                StringBuilder detailsSb = new StringBuilder();
                //details
                detailsSb.append("<table border='0' cellpadding='4' width='280'>");
                //hit
                detailsSb.append("<tr>");
                detailsSb.append("<th>")
                         .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.fileName"))
                         .append("</th>");
                detailsSb.append("<td>")
                         .append(abstractFile.getName())
                         .append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("<tr>");
                detailsSb.append("<th>")
                         .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.md5Hash"))
                         .append("</th>");
                detailsSb.append("<td>").append(md5Hash).append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("<tr>");
                detailsSb.append("<th>")
                         .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.hashsetName"))
                         .append("</th>");
                detailsSb.append("<td>").append(hashSetName).append("</td>");
                detailsSb.append("</tr>");

                detailsSb.append("</table>");

                services.postMessage(IngestMessage.createDataMessage(++messageId, HashLookupModuleFactory.getModuleName(),
                         NbBundle.getMessage(this.getClass(),
                                             "HashDbIngestModule.postToBB.knownBadMsg",
                                             abstractFile.getName()),
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
    public void shutDown(boolean ingestJobCancelled) {
        if ((!knownBadHashSets.isEmpty()) || (!knownHashSets.isEmpty())) {
            StringBuilder detailsSb = new StringBuilder();
            //details
            detailsSb.append("<table border='0' cellpadding='4' width='280'>");

            detailsSb.append("<tr><td>")
                     .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.complete.knownBadsFound"))
                     .append("</td>");
            detailsSb.append("<td>").append(knownBadCount).append("</td></tr>");

            detailsSb.append("<tr><td>")
                     .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.complete.totalCalcTime"))
                     .append("</td><td>").append(calctime).append("</td></tr>\n");
            detailsSb.append("<tr><td>")
                     .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.complete.totalLookupTime"))
                     .append("</td><td>").append(lookuptime).append("</td></tr>\n");
            detailsSb.append("</table>");

            detailsSb.append("<p>")
                     .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.complete.databasesUsed"))
                     .append("</p>\n<ul>");
            for (HashDb db : knownBadHashSets) {
                detailsSb.append("<li>").append(db.getHashSetName()).append("</li>\n");
            }

            detailsSb.append("</ul>");
            services.postMessage(IngestMessage.createMessage(++messageId,
                                 IngestMessage.MessageType.INFO,
                                 HashLookupModuleFactory.getModuleName(),
                                 NbBundle.getMessage(this.getClass(),
                                                     "HashDbIngestModule.complete.hashLookupResults"),
                                 detailsSb.toString()));
        }
    }
}
