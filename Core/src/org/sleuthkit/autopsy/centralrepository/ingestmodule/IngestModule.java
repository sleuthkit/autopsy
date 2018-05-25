/*
 * Central Repository
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.ingestmodule;

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbPlatformEnum;
import org.sleuthkit.autopsy.centralrepository.eventlisteners.IngestEventsListener;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.eventlisteners.IngestEventsListener;
import org.sleuthkit.autopsy.healthmonitor.EnterpriseHealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;

/**
 * Ingest module for inserting entries into the Central Repository database on
 * ingest of a data source
 */
@Messages({"IngestModule.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
    "IngestModule.prevCaseComment.text=Previous Case: "})
final class IngestModule implements FileIngestModule {

    static final boolean DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS = true;

    private final static Logger logger = Logger.getLogger(IngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final IngestModuleReferenceCounter warningMsgRefCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private CorrelationCase eamCase;
    private CorrelationDataSource eamDataSource;
    private Blackboard blackboard;
    private CorrelationAttribute.Type filesType;

    private final boolean flagTaggedNotableItems;

    /**
     * Instantiate the Correlation Engine ingest module.
     *
     * @param settings The ingest settings for the module instance.
     */
    IngestModule(IngestSettings settings) {
        flagTaggedNotableItems = settings.isFlagTaggedNotableItems();
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (EamDb.isEnabled() == false) {
            /*
             * Not signaling an error for now. This is a workaround for the way
             * all newly didscovered ingest modules are automatically anabled.
             *
             * TODO (JIRA-2731): Add isEnabled API for ingest modules.
             */
            return ProcessResult.OK;
        }

        try {
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            return ProcessResult.ERROR;
        }

        if (!EamArtifactUtil.isValidCentralRepoFile(abstractFile)) {
            return ProcessResult.OK;
        }

        EamDb dbManager;
        try {
            dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error connecting to Central Repository database.", ex);
            return ProcessResult.ERROR;
        }

        // only continue if we are correlating filesType
        if (!filesType.isEnabled()) {
            return ProcessResult.OK;
        }

        // get the hash because we're going to correlate it
        String md5 = abstractFile.getMd5Hash();
        if ((md5 == null) || (HashUtility.isNoDataMd5(md5))) {
            return ProcessResult.OK;
        }

        /*
         * Search the central repo to see if this file was previously marked as
         * being bad. Create artifact if it was.
         */
        if (abstractFile.getKnown() != TskData.FileKnown.KNOWN && flagTaggedNotableItems) {
            try {
                TimingMetric timingMetric = EnterpriseHealthMonitor.getTimingMetric("Correlation Engine: Notable artifact query");
                List<String> caseDisplayNamesList = dbManager.getListCasesHavingArtifactInstancesKnownBad(filesType, md5);
                EnterpriseHealthMonitor.submitTimingMetric(timingMetric);
                if (!caseDisplayNamesList.isEmpty()) {
                    postCorrelatedBadFileToBlackboard(abstractFile, caseDisplayNamesList);
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error searching database for artifact.", ex); // NON-NLS
                return ProcessResult.ERROR;
            }
        }

        // insert this file into the central repository
        try {
            CorrelationAttribute eamArtifact = new CorrelationAttribute(filesType, md5);
            CorrelationAttributeInstance cefi = new CorrelationAttributeInstance(
                    eamCase,
                    eamDataSource,
                    abstractFile.getParentPath() + abstractFile.getName(),
                    null,
                    TskData.FileKnown.UNKNOWN // NOTE: Known status in the CR is based on tagging, not hashes like the Case Database.
            );
            eamArtifact.addInstance(cefi);
            dbManager.prepareBulkArtifact(eamArtifact);
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error adding artifact to bulk artifacts.", ex); // NON-NLS
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        IngestEventsListener.decrementCorrelationEngineModuleCount();

        if ((EamDb.isEnabled() == false) || (eamCase == null) || (eamDataSource == null)) {
            return;
        }
        EamDb dbManager;
        try {
            dbManager = EamDb.getInstance();
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error connecting to Central Repository database.", ex);
            return;
        }
        try {
            dbManager.bulkInsertArtifacts();
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error doing bulk insert of artifacts.", ex); // NON-NLS
        }
        try {
            Long count = dbManager.getCountArtifactInstancesByCaseDataSource(eamCase.getCaseUUID(), eamDataSource.getDeviceID());
            logger.log(Level.INFO, "{0} artifacts in db for case: {1} ds:{2}", new Object[]{count, eamCase.getDisplayName(), eamDataSource.getName()}); // NON-NLS
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error counting artifacts.", ex); // NON-NLS
        }

        // TODO: once we implement shared cache, if refCounter is 1, then submit data in bulk.
        refCounter.decrementAndGet(jobId);
    }

    // see ArtifactManagerTimeTester for details
    @Messages({
        "IngestModule.notfyBubble.title=Central Repository Not Initialized",
        "IngestModule.errorMessage.isNotEnabled=Central repository settings are not initialized, cannot run Correlation Engine ingest module."
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        IngestEventsListener.incrementCorrelationEngineModuleCount();

        /*
         * Tell the IngestEventsListener to flag notable items based on the
         * current module's configuration. This is a work around for the lack of
         * an artifacts pipeline. Note that this can be changed by another
         * module instance. All modules are affected by the value. While not
         * ideal, this will be good enough until a better solution can be
         * posited.
         *
         * Note: Flagging cannot be disabled if any other instances of the
         * Correlation Engine module are running. This restriction is to prevent
         * missing results in the case where the first module is flagging
         * notable items, and the proceeding module (with flagging disabled)
         * causes the first to stop flagging.
         */
        if (IngestEventsListener.getCeModuleInstanceCount() == 1 || !IngestEventsListener.isFlagNotableItems()) {
            IngestEventsListener.setFlagNotableItems(flagTaggedNotableItems);
        }

        if (EamDb.isEnabled() == false) {
            /*
             * Not throwing the customary exception for now. This is a
             * workaround for the way all newly didscovered ingest modules are
             * automatically anabled.
             *
             * TODO (JIRA-2731): Add isEnabled API for ingest modules.
             */
            if (RuntimeProperties.runningWithGUI()) {
                if (1L == warningMsgRefCounter.incrementAndGet(jobId)) {
                    MessageNotifyUtil.Notify.warn(Bundle.IngestModule_notfyBubble_title(), Bundle.IngestModule_errorMessage_isNotEnabled());
                }
            }
            return;
        }
        Case autopsyCase;
        try {
            autopsyCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Exception while getting open case.", ex);
            throw new IngestModuleException("Exception while getting open case.", ex);
        }

        // Don't allow sqlite central repo databases to be used for multi user cases
        if ((autopsyCase.getCaseType() == Case.CaseType.MULTI_USER_CASE)
                && (EamDbPlatformEnum.getSelectedPlatform() == EamDbPlatformEnum.SQLITE)) {
            logger.log(Level.SEVERE, "Cannot run correlation engine on a multi-user case with a SQLite central repository.");
            throw new IngestModuleException("Cannot run on a multi-user case with a SQLite central repository."); // NON-NLS
        }
        jobId = context.getJobId();

        EamDb centralRepoDb;
        try {
            centralRepoDb = EamDb.getInstance();
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error connecting to central repository database.", ex); // NON-NLS
            throw new IngestModuleException("Error connecting to central repository database.", ex); // NON-NLS
        }

        try {
            filesType = centralRepoDb.getCorrelationTypeById(CorrelationAttribute.FILES_TYPE_ID);
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error getting correlation type FILES in ingest module start up.", ex); // NON-NLS
            throw new IngestModuleException("Error getting correlation type FILES in ingest module start up.", ex); // NON-NLS
        }

        try {
            eamCase = centralRepoDb.getCase(autopsyCase);
        } catch (EamDbException ex) {
            throw new IngestModuleException("Unable to get case from central repository database ", ex);
        }
        if (eamCase == null) {
            // ensure we have this case defined in the EAM DB
            try {
                eamCase = centralRepoDb.newCase(autopsyCase);
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error creating new case in ingest module start up.", ex); // NON-NLS
                throw new IngestModuleException("Error creating new case in ingest module start up.", ex); // NON-NLS
            }
        }

        try {
            eamDataSource = CorrelationDataSource.fromTSKDataSource(eamCase, context.getDataSource());
        } catch (EamDbException ex) {
            logger.log(Level.SEVERE, "Error getting data source info.", ex); // NON-NLS
            throw new IngestModuleException("Error getting data source info.", ex); // NON-NLS
        }
        // TODO: once we implement a shared cache, load/init it here w/ syncronized and define reference counter
        // if we are the first thread / module for this job, then make sure the case
        // and image exist in the DB before we associate artifacts with it.
        if (refCounter.incrementAndGet(jobId)
                == 1) {
            // ensure we have this data source in the EAM DB
            try {
                if (null == centralRepoDb.getDataSource(eamCase, eamDataSource.getDeviceID())) {
                    centralRepoDb.newDataSource(eamDataSource);
                }
            } catch (EamDbException ex) {
                logger.log(Level.SEVERE, "Error adding data source to Central Repository.", ex); // NON-NLS
                throw new IngestModuleException("Error adding data source to Central Repository.", ex); // NON-NLS
            }

        }
    }

    private void postCorrelatedBadFileToBlackboard(AbstractFile abstractFile, List<String> caseDisplayNames) {

        try {
            String MODULE_NAME = IngestModuleFactory.getModuleName();
            BlackboardArtifact tifArtifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                    Bundle.IngestModule_prevTaggedSet_text());
            BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME,
                    Bundle.IngestModule_prevCaseComment_text() + caseDisplayNames.stream().distinct().collect(Collectors.joining(",", "", "")));
            tifArtifact.addAttribute(att);
            tifArtifact.addAttribute(att2);

            try {
                // index the artifact for keyword search
                blackboard.postArtifact(tifArtifact);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
            }

            // send inbox message
            sendBadFileInboxMessage(tifArtifact, abstractFile.getName(), abstractFile.getMd5Hash());

            // fire event to notify UI of this new artifact
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
        }
    }

    /**
     * Post a message to the ingest inbox alerting the user that a bad file was
     * found.
     *
     * @param artifact badFile Blackboard Artifact
     * @param name     badFile's name
     * @param md5Hash  badFile's md5 hash
     */
    @Messages({"IngestModule.postToBB.fileName=File Name",
        "IngestModule.postToBB.md5Hash=MD5 Hash",
        "IngestModule.postToBB.hashSetSource=Source of Hash",
        "IngestModule.postToBB.eamHit=Central Repository",
        "# {0} - Name of file that is Notable",
        "IngestModule.postToBB.knownBadMsg=Notable: {0}"})
    public void sendBadFileInboxMessage(BlackboardArtifact artifact, String name, String md5Hash) {
        StringBuilder detailsSb = new StringBuilder();
        //details
        detailsSb.append("<table border='0' cellpadding='4' width='280'>"); //NON-NLS
        //hit
        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append("<th>") //NON-NLS
                .append(Bundle.IngestModule_postToBB_fileName())
                .append("</th>"); //NON-NLS
        detailsSb.append("<td>") //NON-NLS
                .append(name)
                .append("</td>"); //NON-NLS
        detailsSb.append("</tr>"); //NON-NLS

        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append("<th>") //NON-NLS
                .append(Bundle.IngestModule_postToBB_md5Hash())
                .append("</th>"); //NON-NLS
        detailsSb.append("<td>").append(md5Hash).append("</td>"); //NON-NLS
        detailsSb.append("</tr>"); //NON-NLS

        detailsSb.append("<tr>"); //NON-NLS
        detailsSb.append("<th>") //NON-NLS
                .append(Bundle.IngestModule_postToBB_hashSetSource())
                .append("</th>"); //NON-NLS
        detailsSb.append("<td>").append(Bundle.IngestModule_postToBB_eamHit()).append("</td>"); //NON-NLS            
        detailsSb.append("</tr>"); //NON-NLS

        detailsSb.append("</table>"); //NON-NLS

        services.postMessage(IngestMessage.createDataMessage(IngestModuleFactory.getModuleName(),
                Bundle.IngestModule_postToBB_knownBadMsg(name),
                detailsSb.toString(),
                name + md5Hash,
                artifact));
    }
}
