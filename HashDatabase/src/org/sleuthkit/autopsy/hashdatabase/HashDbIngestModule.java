/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2013 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.Hash;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

public class HashDbIngestModule implements IngestModuleAbstractFile {

    private static HashDbIngestModule instance = null;
    public final static String MODULE_NAME = "Hash Lookup";
    public final static String MODULE_DESCRIPTION = "Identifies known and notables files using supplied hash databases, such as a standard NSRL database.";
    final public static String MODULE_VERSION = "1.0";
    private String args;
    private static final Logger logger = Logger.getLogger(HashDbIngestModule.class.getName());
    private IngestServices services;
    private SleuthkitCase skCase;
    private static int messageId = 0;
    private int knownBadCount;
    // Whether or not to do hash lookups (only set to true if there are dbs set)
    private boolean nsrlIsSet;
    private boolean knownBadIsSet;
    private boolean calcHashesIsSet;
    private HashDb nsrlSet;
    private int nsrlPointer;
    static long calctime = 0;
    static long lookuptime = 0;
    private Map<Integer, HashDb> knownBadSets = new HashMap<>();
    private HashDbManagementPanel panel;
    private final Hash hasher = new Hash();

    private HashDbIngestModule() {
        knownBadCount = 0;
    }

    public static synchronized HashDbIngestModule getDefault() {
        if (instance == null) {
            instance = new HashDbIngestModule();
        }
        return instance;
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        this.skCase = Case.getCurrentCase().getSleuthkitCase();
        try {
            HashDbXML hdbxml = HashDbXML.getCurrent();
            nsrlSet = null;
            knownBadSets.clear();
            skCase.clearLookupDatabases();
            nsrlIsSet = false;
            knownBadIsSet = false;
            calcHashesIsSet = hdbxml.getCalculate();

            HashDb nsrl = hdbxml.getNSRLSet();
            if (nsrl != null && nsrl.getUseForIngest() && IndexStatus.isIngestible(nsrl.status())) {
                nsrlIsSet = true;
                this.nsrlSet = nsrl;
                nsrlPointer = skCase.setNSRLDatabase(nsrl.getDatabasePaths().get(0));
            }

            for (HashDb db : hdbxml.getKnownBadSets()) {
                IndexStatus status = db.status();
                if (db.getUseForIngest() && IndexStatus.isIngestible(status)) {
                    knownBadIsSet = true;
                    int ret = skCase.addKnownBadDatabase(db.getDatabasePaths().get(0)); // TODO: support multiple paths
                    knownBadSets.put(ret, db);
                }
            }

            if (!nsrlIsSet) {
                this.services.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No NSRL database set", "Known file search will not be executed."));
            }
            if (!knownBadIsSet) {
                this.services.postMessage(IngestMessage.createWarningMessage(++messageId, this, "No known bad database set", "Known bad file search will not be executed."));
            }

        } catch (TskException ex) {
            logger.log(Level.SEVERE, "Setting NSRL and Known database failed", ex);
            this.services.postMessage(IngestMessage.createErrorMessage(++messageId, this, "Error Configuring Hash Databases", "Setting NSRL and Known database failed."));
        }
    }

    @Override
    public void complete() {
        if ((knownBadIsSet) || (nsrlIsSet)) {
            StringBuilder detailsSb = new StringBuilder();
            //details
            detailsSb.append("<table border='0' cellpadding='4' width='280'>");

            detailsSb.append("<tr>");
            detailsSb.append("<th>Number of notable files found:</th>");
            detailsSb.append("<td>").append(knownBadCount).append("</td>");
            detailsSb.append("</tr>");

            detailsSb.append("<tr>");
            detailsSb.append("<th>Notable databases used:</th>");
            detailsSb.append("<td>Calc Time: ").append(calctime).append(" Lookup Time: ").append(lookuptime).append("</td>");
            detailsSb.append("</tr>");

            for (HashDb db : knownBadSets.values()) {
                detailsSb.append("<tr><th>");
                detailsSb.append(db.getName());
                detailsSb.append("</th><td>");
                detailsSb.append(db.getDatabasePaths().get(0)); // TODO: support multiple database paths
                detailsSb.append("</td></tr>");
            }

            detailsSb.append("</table>");
            services.postMessage(IngestMessage.createMessage(++messageId, IngestMessage.MessageType.INFO, this, "Hash Lookup Results", detailsSb.toString()));
        }
    }

    /**
     * notification from manager to stop processing due to some interruption
     * (user, error, exception)
     */
    @Override
    public void stop() {
    }

    /**
     * get specific name of the module should be unique across modules, a
     * user-friendly name of the module shown in GUI
     *
     * @return The name of this Ingest Module
     */
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
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }

    /**
     * Process the given AbstractFile object
     *
     * @param file the object to be processed
     * @return ProcessResult OK if file is unknown and should be processed
     * further, otherwise STOP_COND if file is known
     */
    @Override
    public ProcessResult process(AbstractFile file) {
        //skip unalloc
        if (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }

        return processFile(file);
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
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
        HashDbXML.getCurrent().reload();
        return new HashDbSimplePanel();
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        //return HashDbManagementPanel.getDefault();
        getPanel().load();
        return getPanel();
    }

    @Override
    public void saveAdvancedConfiguration() {
        getPanel().store();
    }

    private HashDbManagementPanel getPanel() {
        if (panel == null) {
            panel = new HashDbManagementPanel();
        }
        return panel;
    }

    @Override
    public void saveSimpleConfiguration() {
        HashDbXML.getCurrent().save();
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
                        "Notable: " + abstractFile.getName(),
                        detailsSb.toString(),
                        abstractFile.getName() + md5Hash,
                        badFile));
            }
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating blackboard artifact", ex);
        }

    }

    private ProcessResult processFile(AbstractFile file) {
        // bail out if we have no hashes set
        if ((nsrlIsSet == false) && (knownBadIsSet == false) && (calcHashesIsSet == false)) {
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
        TskData.FileKnown status = TskData.FileKnown.UKNOWN;
        boolean foundBad = false;
        ProcessResult ret = ProcessResult.OK;

        if (knownBadIsSet) {
            for (Map.Entry<Integer, HashDb> entry : knownBadSets.entrySet()) {

                try {
                    long lookupstart = System.currentTimeMillis();
                    status = skCase.knownBadLookupMd5(md5Hash, entry.getKey());
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
                    String hashSetName = entry.getValue().getName();
                    processBadFile(file, md5Hash, hashSetName, entry.getValue().getShowInboxMessages());
                }
            }
        }

        // only do NSRL if we didn't find a known bad
        if (!foundBad && nsrlIsSet) {
            try {
                long lookupstart = System.currentTimeMillis();
                status = skCase.nsrlLookupMd5(md5Hash);
                lookuptime += (System.currentTimeMillis() - lookupstart);
            } catch (TskException ex) {
                logger.log(Level.WARNING, "Couldn't lookup NSRL hash for file " + name + " - see sleuthkit log for details", ex);
                services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                        "Error encountered while looking up NSRL hash value for " + name + "."));
                ret = ProcessResult.ERROR;
            }

            if (status.equals(TskData.FileKnown.KNOWN)) {
                try {
                    skCase.setKnown(file, TskData.FileKnown.KNOWN);
                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't set known state for file " + name + " - see sleuthkit log for details", ex);
                    services.postMessage(IngestMessage.createErrorMessage(++messageId, HashDbIngestModule.this, "Hash Lookup Error: " + name,
                            "Error encountered while setting known (NSRL) state for " + name + "."));
                    ret = ProcessResult.ERROR;
                }
            }
        }

        return ret;
    }
}
