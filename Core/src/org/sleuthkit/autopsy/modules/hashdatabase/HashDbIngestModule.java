/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.hashdatabase.HashDbManager.HashDb;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.HashHitInfo;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * File ingest module to mark files based on hash values.
 */
@Messages({
    "HashDbIngestModule.noKnownBadHashDbSetMsg=No notable hash set.",
    "HashDbIngestModule.knownBadFileSearchWillNotExecuteWarn=Notable file search will not be executed.",
    "HashDbIngestModule.noKnownHashDbSetMsg=No known hash set.",
    "HashDbIngestModule.knownFileSearchWillNotExecuteWarn=Known file search will not be executed.",
    "# {0} - fileName", "HashDbIngestModule.lookingUpKnownBadHashValueErr=Error encountered while looking up notable hash value for {0}.",
    "# {0} - fileName", "HashDbIngestModule.lookingUpNoChangeHashValueErr=Error encountered while looking up no change hash value for {0}.",
    "# {0} - fileName", "HashDbIngestModule.lookingUpKnownHashValueErr=Error encountered while looking up known hash value for {0}.",})
public class HashDbIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(HashDbIngestModule.class.getName());

    private final Function<AbstractFile, String> knownBadLookupError
            = (file) -> Bundle.HashDbIngestModule_lookingUpKnownBadHashValueErr(file.getName());

    private final Function<AbstractFile, String> noChangeLookupError
            = (file) -> Bundle.HashDbIngestModule_lookingUpNoChangeHashValueErr(file.getName());

    private final Function<AbstractFile, String> knownLookupError
            = (file) -> Bundle.HashDbIngestModule_lookingUpKnownHashValueErr(file.getName());

    private static final int MAX_COMMENT_SIZE = 500;
    private final IngestServices services = IngestServices.getInstance();
    private final SleuthkitCase skCase;
    private final HashDbManager hashDbManager = HashDbManager.getInstance();
    private final HashLookupModuleSettings settings;
    private final List<HashDb> knownBadHashSets = new ArrayList<>();
    private final List<HashDb> knownHashSets = new ArrayList<>();
    private final List<HashDb> noChangeHashSets = new ArrayList<>();
    private long jobId;
    private static final HashMap<Long, IngestJobTotals> totalsForIngestJobs = new HashMap<>();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private Blackboard blackboard;

    /**
     * A container of values for storing ingest metrics for the job.
     */
    private static class IngestJobTotals {

        private final AtomicLong totalKnownBadCount = new AtomicLong(0);
        private final AtomicLong totalNoChangeCount = new AtomicLong(0);
        private final AtomicLong totalCalctime = new AtomicLong(0);
        private final AtomicLong totalLookuptime = new AtomicLong(0);
    }

    private static synchronized IngestJobTotals getTotalsForIngestJobs(long ingestJobId) {
        IngestJobTotals totals = totalsForIngestJobs.get(ingestJobId);
        if (totals == null) {
            totals = new HashDbIngestModule.IngestJobTotals();
            totalsForIngestJobs.put(ingestJobId, totals);
        }
        return totals;
    }

    /**
     * Create a HashDbIngestModule object that will mark files based on a
     * supplied list of hash values. The supplied HashLookupModuleSettings
     * object is used to configure the module.
     *
     * @param settings The module settings.
     *
     * @throws NoCurrentCaseException If there is no open case.
     */
    HashDbIngestModule(HashLookupModuleSettings settings) throws NoCurrentCaseException {
        this.settings = settings;
        skCase = Case.getCurrentCaseThrows().getSleuthkitCase();
    }

    @Override
    public void startUp(org.sleuthkit.autopsy.ingest.IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();
        if (!hashDbManager.verifyAllDatabasesLoadedCorrectly()) {
            throw new IngestModuleException("Could not load all hash sets");
        }

        initializeHashsets(hashDbManager.getAllHashSets());

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
     * Cycle through list of hashsets and place each HashDB in the appropriate
     * list based on KnownFilesType.
     *
     * @param allHashSets List of all hashsets from DB manager
     */
    private void initializeHashsets(List<HashDb> allHashSets) {
        for (HashDb db : allHashSets) {
            if (settings.isHashSetEnabled(db)) {
                try {
                    if (db.isValid()) {
                        switch (db.getKnownFilesType()) {
                            case KNOWN:
                                knownHashSets.add(db);
                                break;
                            case KNOWN_BAD:
                                knownBadHashSets.add(db);
                                break;
                            case NO_CHANGE:
                                noChangeHashSets.add(db);
                                break;
                            default:
                                throw new TskCoreException("Unknown KnownFilesType: " + db.getKnownFilesType());
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.WARNING, "Error getting index status for " + db.getDisplayName() + " hash set", ex); //NON-NLS
                }
            }
        }
    }

    @Messages({
        "# {0} - File name",
        "HashDbIngestModule.dialogTitle.errorFindingArtifacts=Error Finding Artifacts: {0}",
        "# {0} - File name",
        "HashDbIngestModule.errorMessage.lookingForFileArtifacts=Error encountered while looking for existing artifacts for {0}."
    })
    @Override
    public ProcessResult process(AbstractFile file) {
        try {
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex); //NON-NLS
            return ProcessResult.ERROR;
        }

        if (shouldSkip(file)) {
            return ProcessResult.OK;
        }

        // Safely get a reference to the totalsForIngestJobs object
        IngestJobTotals totals = getTotalsForIngestJobs(jobId);

        // calc hash values
        try {
            calculateHashes(file, totals);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, String.format("Error calculating hash of file '%s' (id=%d).", file.getName(), file.getId()), ex); //NON-NLS
            services.postMessage(IngestMessage.createErrorMessage(
                    HashLookupModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(), "HashDbIngestModule.fileReadErrorMsg", file.getName()),
                    NbBundle.getMessage(this.getClass(), "HashDbIngestModule.calcHashValueErr",
                            file.getParentPath() + file.getName(),
                            file.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC) ? "Allocated File" : "Deleted File")));
        }

        // the processing result of handling this file
        ProcessResult ret = ProcessResult.OK;

        // look up in notable first
        FindInHashsetsResult knownBadResult = findInHashsets(file, totals.totalKnownBadCount,
                totals.totalLookuptime, knownBadHashSets, TskData.FileKnown.BAD, knownBadLookupError);

        boolean foundBad = knownBadResult.isFound();
        if (knownBadResult.isError()) {
            ret = ProcessResult.ERROR;
        }

        // look up no change items next
        FindInHashsetsResult noChangeResult = findInHashsets(file, totals.totalNoChangeCount,
                totals.totalLookuptime, noChangeHashSets, TskData.FileKnown.UNKNOWN, noChangeLookupError);

        if (noChangeResult.isError()) {
            ret = ProcessResult.ERROR;
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
                    reportLookupError(ex, file, knownLookupError);
                    ret = ProcessResult.ERROR;
                }
            }
        }

        return ret;
    }

    /**
     * Returns true if this file should be skipped for processing.
     *
     * @param file The file to potentially skip.
     *
     * @return True if this file should be skipped.
     */
    private boolean shouldSkip(AbstractFile file) {
        // Skip unallocated space files.
        if ((file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK))) {
            return true;
        }

        /*
         * Skip directories. One reason for this is because we won't accurately
         * calculate hashes of NTFS directories that have content that spans the
         * IDX_ROOT and IDX_ALLOC artifacts. So we disable that until a solution
         * for it is developed.
         */
        if (file.isDir()) {
            return true;
        }

        // bail out if we have no hashes set
        if ((knownHashSets.isEmpty()) && (knownBadHashSets.isEmpty()) && (!settings.shouldCalculateHashes())) {
            return true;
        }

        return false;
    }

    /**
     * Reports an error when an issue is encountered looking up a file.
     *
     * @param ex                 The exception thrown in the error.
     * @param file               The file for which this error applies.
     * @param lookupErrorMessage The function that generates an error message
     *                           specific to which piece of the ingest
     *                           processing failed.
     */
    private void reportLookupError(TskException ex, AbstractFile file, Function<AbstractFile, String> lookupErrorMessage) {
        logger.log(Level.WARNING, String.format(
                "Couldn't lookup notable hash for file '%s' (id=%d) - see sleuthkit log for details", file.getName(), file.getId()), ex); //NON-NLS
        services.postMessage(IngestMessage.createErrorMessage(
                HashLookupModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(), "HashDbIngestModule.hashLookupErrorMsg", file.getName()),
                lookupErrorMessage.apply(file)));
    }

    /**
     * The result of attempting to find a file in a list of HashDB objects.
     */
    private static class FindInHashsetsResult {

        private final boolean found;
        private final boolean error;

        FindInHashsetsResult(boolean found, boolean error) {
            this.found = found;
            this.error = error;
        }

        /**
         * Returns true if the file was found in the HashDB.
         *
         * @return True if the file was found in the HashDB.
         */
        boolean isFound() {
            return found;
        }

        /**
         * Returns true if there was an error in the process of finding a file
         * in a HashDB.
         *
         * @return True if there was an error in the process of finding a file
         *         in a HashDB.
         */
        boolean isError() {
            return error;
        }
    }

    /**
     * Attempts to find an abstract file in a list of HashDB objects.
     *
     * @param file               The file to find.
     * @param totalCount         The total cound of files found in this type
     * @param totalLookupTime    The counter tracking the total amount of run
     *                           time for this operation.
     * @param hashSets           The HashDB objects to cycle through looking for
     *                           a hash hit.
     * @param statusIfFound      The FileKnown status to set on the file if the
     *                           file is found in the hashSets.
     * @param lookupErrorMessage The function that generates a message should
     *                           there be an error in looking up the file in the
     *                           hashSets.
     *
     * @return Whether or not the file was found and whether or not there was an
     *         error during the operation.
     */
    private FindInHashsetsResult findInHashsets(AbstractFile file, AtomicLong totalCount, AtomicLong totalLookupTime,
            List<HashDb> hashSets, TskData.FileKnown statusIfFound, Function<AbstractFile, String> lookupErrorMessage) {

        boolean found = false;
        boolean wasError = false;
        for (HashDb db : hashSets) {
            try {
                long lookupstart = System.currentTimeMillis();
                HashHitInfo hashInfo = db.lookupMD5(file);
                if (null != hashInfo) {
                    found = true;

                    totalCount.incrementAndGet();
                    file.setKnown(statusIfFound);
                    String comment = generateComment(hashInfo);
                    if (!createArtifactIfNotExists(file, comment, db)) {
                        wasError = true;
                    }
                }
                long delta = (System.currentTimeMillis() - lookupstart);
                totalLookupTime.addAndGet(delta);

            } catch (TskException ex) {
                reportLookupError(ex, file, lookupErrorMessage);
                wasError = true;
            }
        }

        return new FindInHashsetsResult(found, wasError);
    }

    /**
     * Generates a formatted comment.
     *
     * @param hashInfo The HashHitInfo.
     *
     * @return The formatted comment.
     */
    private String generateComment(HashHitInfo hashInfo) {
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
        return comment;
    }

    /**
     * Creates a BlackboardArtifact if artifact does not already exist.
     *
     * @param file        The file that had a hash hit.
     * @param comment     The comment to associate with this artifact.
     * @param db          the database in which this file was found.
     *
     * @return True if the operation occurred successfully and without error.
     */
    private boolean createArtifactIfNotExists(AbstractFile file, String comment, HashDb db) {
        /*
         * We have a match. Now create an artifact if it is determined that one
         * hasn't been created yet.
         */
        List<BlackboardAttribute> attributesList = new ArrayList<>();
        attributesList.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, HashLookupModuleFactory.getModuleName(), db.getDisplayName()));
        try {
            Blackboard tskBlackboard = skCase.getBlackboard();
            if (tskBlackboard.artifactExists(file, BlackboardArtifact.Type.TSK_HASHSET_HIT, attributesList) == false) {
                postHashSetHitToBlackboard(file, file.getMd5Hash(), db, comment);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format(
                    "A problem occurred while checking for existing artifacts for file '%s' (id=%d).", file.getName(), file.getId()), ex); //NON-NLS
            services.postMessage(IngestMessage.createErrorMessage(
                    HashLookupModuleFactory.getModuleName(),
                    Bundle.HashDbIngestModule_dialogTitle_errorFindingArtifacts(file.getName()),
                    Bundle.HashDbIngestModule_errorMessage_lookingForFileArtifacts(file.getName())));
            return false;
        }
        return true;
    }

    /**
     * Generates hashes for the given file if they haven't already been set. 
     * Hashes are saved to the AbstractFile object.
     *
     * @param file   The file in order to determine the hash.
     * @param totals The timing metrics for this process.
     */
    private void calculateHashes(AbstractFile file, IngestJobTotals totals) throws TskCoreException {
        
        // First check if we've already calculated the hashes.
        String md5Hash = file.getMd5Hash();
        String sha256Hash = file.getSha256Hash();
        if ((md5Hash != null && ! md5Hash.isEmpty())
                && (sha256Hash != null && ! sha256Hash.isEmpty())) {
            return;
        }

        TimingMetric metric = HealthMonitor.getTimingMetric("Disk Reads: Hash calculation");
        long calcstart = System.currentTimeMillis();
        List<HashUtility.HashResult> newHashResults = 
                HashUtility.calculateHashes(file, Arrays.asList(HashUtility.HashType.MD5,HashUtility.HashType.SHA256 ));
        if (file.getSize() > 0) {
            // Surprisingly, the hash calculation does not seem to be correlated that
            // strongly with file size until the files get large.
            // Only normalize if the file size is greater than ~1MB.
            if (file.getSize() < 1000000) {
                HealthMonitor.submitTimingMetric(metric);
            } else {
                // In testing, this normalization gave reasonable resuls
                HealthMonitor.submitNormalizedTimingMetric(metric, file.getSize() / 500000);
            }
        }
        for (HashUtility.HashResult hash : newHashResults) {
            if (hash.getType().equals(HashUtility.HashType.MD5)) {
                file.setMd5Hash(hash.getValue());
            } else if (hash.getType().equals(HashUtility.HashType.SHA256)) {
                file.setSha256Hash(hash.getValue());
            }
        }
        long delta = (System.currentTimeMillis() - calcstart);
        totals.totalCalctime.addAndGet(delta);
    }

    /**
     * Converts HashDb.KnownFilesType to a Score to be used to create an analysis result.
     * @param knownFilesType The HashDb KnownFilesType to convert.
     * @return The Score to use when creating an AnalysisResult.
     */
    private Score getScore(HashDb.KnownFilesType knownFilesType) {
        if (knownFilesType == null) {
            return Score.SCORE_UNKNOWN;
        }
        switch (knownFilesType) {
            case KNOWN:
                return Score.SCORE_NONE;
            case KNOWN_BAD:
                return Score.SCORE_NOTABLE;
            default:
            case NO_CHANGE:
                return Score.SCORE_UNKNOWN;
        }
    }
    /**
     * Post a hash set hit to the blackboard.
     *
     * @param abstractFile     The file to be processed.
     * @param md5Hash          The MD5 hash value of the file.
     * @param db               The database in which this file was found.
     * @param comment          A comment to be attached to the artifact.
     */
    @Messages({
        "HashDbIngestModule.indexError.message=Failed to index hashset hit artifact for keyword search."
    })
    private void postHashSetHitToBlackboard(AbstractFile abstractFile, String md5Hash, HashDb db, String comment) {
        try {
            String moduleName = HashLookupModuleFactory.getModuleName();
            
            List<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, moduleName, db.getDisplayName()),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_HASH_MD5, moduleName, md5Hash),
                new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COMMENT, moduleName, comment)
            );

            // BlackboardArtifact.Type artifactType, Score score, String conclusion, String configuration, String justification, Collection<BlackboardAttribute> attributesList
            BlackboardArtifact badFile = abstractFile.newAnalysisResult(
                    BlackboardArtifact.Type.TSK_HASHSET_HIT, getScore(db.getKnownFilesType()), 
                    null, db.getDisplayName(), null,
                    attributes
            ).getAnalysisResult();

            try {
                /*
                 * post the artifact which will index the artifact for keyword
                 * search, and fire an event to notify UI of this new artifact
                 */
                blackboard.postArtifact(badFile, moduleName, jobId);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + badFile.getArtifactID(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(
                        Bundle.HashDbIngestModule_indexError_message(), badFile.getDisplayName());
            }

            if (db.getSendIngestMessages()) {
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
                detailsSb.append("<td>").append(db.getDisplayName()).append("</td>"); //NON-NLS
                detailsSb.append("</tr>"); //NON-NLS

                detailsSb.append("</table>"); //NON-NLS

                services.postMessage(IngestMessage.createDataMessage(HashLookupModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(), "HashDbIngestModule.postToBB.knownBadMsg", abstractFile.getName()),
                        detailsSb.toString(),
                        abstractFile.getName() + md5Hash,
                        badFile));
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error creating blackboard artifact", ex); //NON-NLS
        }
    }

    /**
     * Post a message summarizing the results of the ingest.
     *
     * @param jobId            The ID of the job.
     * @param knownBadHashSets The list of hash sets for "known bad" files.
     * @param noChangeHashSets The list of "no change" hash sets.
     * @param knownHashSets    The list of hash sets for "known" files.
     */
    @Messages("HashDbIngestModule.complete.noChangesFound=No Change items found:")
    private static synchronized void postSummary(long jobId, List<HashDb> knownBadHashSets,
            List<HashDb> noChangeHashSets, List<HashDb> knownHashSets) {

        IngestJobTotals jobTotals = getTotalsForIngestJobs(jobId);
        totalsForIngestJobs.remove(jobId);

        if ((!knownBadHashSets.isEmpty()) || (!knownHashSets.isEmpty()) || (!noChangeHashSets.isEmpty())) {
            StringBuilder detailsSb = new StringBuilder();
            //details
            detailsSb.append(
                "<table border='0' cellpadding='4' width='280'>" +
                    "<tr><td>" + NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.knownBadsFound") + "</td>" +
                    "<td>" + jobTotals.totalKnownBadCount.get() + "</td></tr>" +
                            
                    "<tr><td>" + Bundle.HashDbIngestModule_complete_noChangesFound() + "</td>" +
                    "<td>" + jobTotals.totalNoChangeCount.get() + "</td></tr>" +
                            
                    "<tr><td>" + NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.totalCalcTime") + 
                    "</td><td>" + jobTotals.totalCalctime.get() + "</td></tr>\n" +
                            
                    "<tr><td>" + NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.totalLookupTime") + 
                    "</td><td>" + jobTotals.totalLookuptime.get() + "</td></tr>\n</table>" +

                    "<p>" + NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.databasesUsed") + "</p>\n<ul>"); //NON-NLS
            
            Stream.concat(knownBadHashSets.stream(), noChangeHashSets.stream()).forEach((db) -> {
                detailsSb.append("<li>" + db.getHashSetName() + "</li>\n"); //NON-NLS    
            });

            detailsSb.append("</ul>"); //NON-NLS

            IngestServices.getInstance().postMessage(IngestMessage.createMessage(
                    IngestMessage.MessageType.INFO,
                    HashLookupModuleFactory.getModuleName(),
                    NbBundle.getMessage(HashDbIngestModule.class, "HashDbIngestModule.complete.hashLookupResults"),
                    detailsSb.toString()));
        }
    }

    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(jobId) == 0) {
            postSummary(jobId, knownBadHashSets, noChangeHashSets, knownHashSets);
        }
    }
}
