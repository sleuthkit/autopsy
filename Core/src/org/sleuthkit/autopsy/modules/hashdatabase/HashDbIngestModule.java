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
package org.sleuthkit.autopsy.modules.hashdatabase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.HashHitInfo;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

@NbBundle.Messages({
    "HashDbIngestModule.noKnownBadHashDbSetMsg=No notable hash set.",
    "HashDbIngestModule.knownBadFileSearchWillNotExecuteWarn=Notable file search will not be executed.",
    "HashDbIngestModule.noKnownHashDbSetMsg=No known hash set.",
    "HashDbIngestModule.knownFileSearchWillNotExecuteWarn=Known file search will not be executed."
})
public class HashDbIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(HashDbIngestModule.class.getName());
    private static final int MAX_COMMENT_SIZE = 500;
    private final IngestServices services = IngestServices.getInstance();
    private final SleuthkitCase skCase;
    private final HashDbManager hashDbManager = HashDbManager.getInstance();
    private final HashLookupModuleSettings settings;
    private List<HashDb> knownBadHashSets = new ArrayList<>();
    private List<HashDb> knownHashSets = new ArrayList<>();
    private long jobId;
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private Blackboard blackboard;

    private static class IngestJobTotals {

        private AtomicLong totalKnownBadCount = new AtomicLong(0);
        private AtomicLong totalCalctime = new AtomicLong(0);
        private AtomicLong totalLookuptime = new AtomicLong(0);
    }

    private static synchronized IngestJobTotals getTotalsForIngestJobs(long ingestJobId) {
        IngestJobTotals totals = totalsForIngestJobs.get(ingestJobId);
        if (totals == null) {
            totals = new HashDbIngestModule.IngestJobTotals();
            totalsForIngestJobs.put(ingestJobId, totals);
        }
        return totals;
    }

    HashDbIngestModule(HashLookupModuleSettings settings) throws NoCurrentCaseException {
        this.settings = settings;
        skCase = Case.getOpenCase().getSleuthkitCase();
    }

    @Override
    public void startUp(org.sleuthkit.autopsy.ingest.IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        if (!hashDbManager.verifyAllDatabasesLoadedCorrectly()) {
            throw new IngestModuleException("Could not load all hash sets");
        }
        updateEnabledHashSets(hashDbManager.getKnownBadFileHashSets(), knownBadHashSets);
        updateEnabledHashSets(hashDbManager.getKnownFileHashSets(), knownHashSets);

        if (refCounter.incrementAndGet(jobId) == 1) {
            // initialize job totals
            getTotalsForIngestJobs(jobId);

            // if first module for this job then post error msgs if needed
            if (knownBadHashSets.isEmpty()) {
                services.postMessage(IngestMessage.createWarningMessage(
                        HashLookupModuleFactory.getModuleName(),
                        Bundle.HashDbIngestModule_noKnownBadHashDbSetMsg(),
                        Bundle.HashDbIngestModule_knownBadFileSearchWillNotExecuteWarn()));
            }

            if (knownHashSets.isEmpty()) {
                services.postMessage(IngestMessage.createWarningMessage(
                        HashLookupModuleFactory.getModuleName(),
                        Bundle.HashDbIngestModule_noKnownHashDbSetMsg(),
                        Bundle.HashDbIngestModule_knownFileSearchWillNotExecuteWarn()));
            }
        }
    }

    /**
     * Cycle through list of hashsets and return the subset that is enabled.
     *
     * @param allHashSets     List of all hashsets from DB manager
     * @param enabledHashSets List of enabled ones to return.
     */
    private void updateEnabledHashSets(List<HashDb> allHashSets, List<HashDb> enabledHashSets) {
        enabledHashSets.clear();
        for (HashDb db : allHashSets) {
            if (settings.isHashSetEnabled(db)) {
                try {
                    if (db.isValid()) {
                        enabledHashSets.add(db);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error getting index status for " + db.getDisplayName()+ " hash set", ex); //NON-NLS
                }
            }
        }
    }

    @Override
    public ProcessResult process(AbstractFile file) {
        try {
            blackboard = Case.getOpenCase().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        // Skip unallocated space files.
        if ((file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) ||
                file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return ProcessResult.OK;
        }

        /*
         * Skip directories. One reason for this is because we won't accurately
         * calculate hashes of NTFS directories that have content that spans the
         * IDX_ROOT and IDX_ALLOC artifacts. So we disable that until a solution
         * for it is developed.
         */
        if (file.isDir()) {
            return ProcessResult.OK;
        }

        // bail out if we have no hashes set
        if ((knownHashSets.isEmpty()) && (knownBadHashSets.isEmpty()) && (!settings.shouldCalculateHashes())) {
            return ProcessResult.OK;
        }

        // Safely get a reference to the totalsForIngestJobs object
        IngestJobTotals totals = getTotalsForIngestJobs(jobId);

        // calc hash value
        String name = file.getName();
        String md5Hash = file.getMd5Hash();
        if (md5Hash == null || md5Hash.isEmpty()) {
            try {
                long calcstart = System.currentTimeMillis();
                md5Hash = HashUtility.calculateMd5Hash(file);
                file.setMd5Hash(md5Hash);
                long delta = (System.currentTimeMillis() - calcstart);
                totals.totalCalctime.addAndGet(delta);

            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error calculating hash of file " + name, ex); //NON-NLS
                services.postMessage(IngestMessage.createErrorMessage(
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

        // look up in notable first
        boolean foundBad = false;
        ProcessResult ret = ProcessResult.OK;
        for (HashDb db : knownBadHashSets) {
            try {
                long lookupstart = System.currentTimeMillis();
                HashHitInfo hashInfo = db.lookupMD5(file);
                if (null != hashInfo) {
                    foundBad = true;
                    totals.totalKnownBadCount.incrementAndGet();

                    file.setKnown(TskData.FileKnown.BAD);

                    String hashSetName = db.getDisplayName();

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
                long delta = (System.currentTimeMillis() - lookupstart);
                totals.totalLookuptime.addAndGet(delta);

            } catch (TskException ex) {
                logger.log(Level.WARNING, "Couldn't lookup notable hash for file " + name + " - see sleuthkit log for details", ex); //NON-NLS
                services.postMessage(IngestMessage.createErrorMessage(
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

        // If the file is not in the notable sets, search for it in the known sets. 
        // Any hit is sufficient to classify it as known, and there is no need to create 
        // a hit artifact or send a message to the application inbox.
        if (!foundBad) {
            for (HashDb db : knownHashSets) {
                try {
                    long lookupstart = System.currentTimeMillis();
                    if (db.lookupMD5Quick(file)) {
                        file.setKnown(TskData.FileKnown.KNOWN);
                        break;
                    }
                    long delta = (System.currentTimeMillis() - lookupstart);
                    totals.totalLookuptime.addAndGet(delta);

                } catch (TskException ex) {
                    logger.log(Level.WARNING, "Couldn't lookup known hash for file " + name + " - see sleuthkit log for details", ex); //NON-NLS
                    services.postMessage(IngestMessage.createErrorMessage(
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

    @Messages({"HashDbIngestModule.indexError.message=Failed to index hashset hit artifact for keyword search."})
    private void postHashSetHitToBlackboard(AbstractFile abstractFile, String md5Hash, String hashSetName, String comment, boolean showInboxMessage) {
        try {
            String MODULE_NAME = NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.moduleName");

            BlackboardArtifact badFile = abstractFile.newArtifact(ARTIFACT_TYPE.TSK_HASHSET_HIT);
            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            //TODO Revisit usage of deprecated constructor as per TSK-583
            //BlackboardAttribute att2 = new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, "Known Bad", hashSetName);
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, hashSetName));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5, MODULE_NAME, md5Hash));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, comment));

            badFile.addAttributes(attributes);

            try {
                // index the artifact for keyword search
                blackboard.indexArtifact(badFile);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + badFile.getArtifactID(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(
                        Bundle.HashDbIngestModule_indexError_message(), badFile.getDisplayName());
            }

            if (showInboxMessage) {
                StringBuilder detailsSb = new StringBuilder();
                //details
                detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
                //hit
                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append("<th>") //NON-NLS
                        .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.fileName"))
                        .append("</th>"); //NON-NLS
                detailsSb.append("<td>") //NON-NLS
                        .append(abstractFile.getName())
                        .append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS

                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append("<th>") //NON-NLS
                        .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.md5Hash"))
                        .append("</th>"); //NON-NLS
                detailsSb.append("<td>").append(md5Hash).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS

                detailsSb.append("<tr>"); //NON-NLS
                detailsSb.append("<th>") //NON-NLS
                        .append(NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.hashsetName"))
                        .append("</th>"); //NON-NLS
                detailsSb.append("<td>").append(hashSetName).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS

                detailsSb.append("</table>"); //NON-NLS

                services.postMessage(IngestMessage.createDataMessage(HashLookupModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(),
                                "HashDbIngestModule.postToBB.knownBadMsg",
                                abstractFile.getName()),
                        detailsSb.toString(),
                        abstractFile.getName() + md5Hash,
                        badFile));
            }
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_HASHSET_HIT, Collections.singletonList(badFile)));
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating blackboard artifact", ex); //NON-NLS
        }
    }

    private static synchronized void postSummary(long jobId,
            List<HashDb> knownBadHashSets, List<HashDb> knownHashSets) {
        IngestJobTotals jobTotals = getTotalsForIngestJobs(jobId);
        totalsForIngestJobs.remove(jobId);

        if ((!knownBadHashSets.isEmpty()) || (!knownHashSets.isEmpty())) {
            StringBuilder detailsSb = new StringBuilder();
            //details
            detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS

            detailsSb.append("<tr><td>") //NON-NLS
                    .append(NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.knownBadsFound"))
                    .append("</td>"); //NON-NLS
            detailsSb.append("<td>").append(jobTotals.totalKnownBadCount.get()).append("</td></tr>"); //NON-NLS

            detailsSb.append("<tr><td>") //NON-NLS
                    .append(NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.totalCalcTime"))
                    .append("</td><td>").append(jobTotals.totalCalctime.get()).append("</td></tr>\n"); //NON-NLS
            detailsSb.append("<tr><td>") //NON-NLS
                    .append(NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.totalLookupTime"))
                    .append("</td><td>").append(jobTotals.totalLookuptime.get()).append("</td></tr>\n"); //NON-NLS
            detailsSb.append("</table>"); //NON-NLS

            detailsSb.append("<p>") //NON-NLS
                    .append(NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.databasesUsed"))
                    .append("</p>\n<ul>"); //NON-NLS
            for (HashDb db : knownBadHashSets) {
                detailsSb.append("<li>").append(db.getHashSetName()).append("</li>\n"); //NON-NLS
            }

            detailsSb.append("</ul>"); //NON-NLS

            IngestServices.getInstance().postMessage(IngestMessage.createMessage(
                    IngestMessage.MessageType.INFO,
                    HashLookupModuleFactory.getModuleName(),
                    NbBundle.getMessage(HashDbIngestModule.class,
                            "HashDbIngestModule.complete.hashLookupResults"),
                    detailsSb.toString()));
        }
    }

    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(jobId) == 0) {
            postSummary(jobId, knownBadHashSets, knownHashSets);
        }
    }
}
