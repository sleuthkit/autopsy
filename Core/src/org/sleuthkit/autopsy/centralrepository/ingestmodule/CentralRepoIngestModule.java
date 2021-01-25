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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.eventlisteners.IngestEventsListener;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;

/**
 * Ingest module for inserting entries into the Central Repository database on
 * ingest of a data source
 */
@Messages({"CentralRepoIngestModule.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
    "CentralRepoIngestModule.prevCaseComment.text=Previous Case: "})
final class CentralRepoIngestModule implements FileIngestModule {

    private static final String MODULE_NAME = CentralRepoIngestModuleFactory.getModuleName();

    static final boolean DEFAULT_FLAG_TAGGED_NOTABLE_ITEMS = false;
    static final boolean DEFAULT_FLAG_PREVIOUS_DEVICES = false;
    static final boolean DEFAULT_CREATE_CR_PROPERTIES = true;

    private final static Logger logger = Logger.getLogger(CentralRepoIngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final IngestModuleReferenceCounter warningMsgRefCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private CorrelationCase eamCase;
    private CorrelationDataSource eamDataSource;
    private CorrelationAttributeInstance.Type filesType;
    private final boolean flagTaggedNotableItems;
    private final boolean flagPreviouslySeenDevices;
    private Blackboard blackboard;
    private final boolean createCorrelationProperties;

    /**
     * Instantiate the Central Repository ingest module.
     *
     * @param settings The ingest settings for the module instance.
     */
    CentralRepoIngestModule(IngestSettings settings) {
        flagTaggedNotableItems = settings.isFlagTaggedNotableItems();
        flagPreviouslySeenDevices = settings.isFlagPreviousDevices();
        createCorrelationProperties = settings.shouldCreateCorrelationProperties();
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (CentralRepository.isEnabled() == false) {
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

        if (!CorrelationAttributeUtil.isSupportedAbstractFileType(abstractFile)) {
            return ProcessResult.OK;
        }

        if (abstractFile.getKnown() == TskData.FileKnown.KNOWN) {
            return ProcessResult.OK;
        }

        CentralRepository dbManager;
        try {
            dbManager = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
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
                TimingMetric timingMetric = HealthMonitor.getTimingMetric("Central Repository: Notable artifact query");
                List<String> caseDisplayNamesList = dbManager.getListCasesHavingArtifactInstancesKnownBad(filesType, md5);
                HealthMonitor.submitTimingMetric(timingMetric);
                if (!caseDisplayNamesList.isEmpty()) {
                    postCorrelatedBadFileToBlackboard(abstractFile, caseDisplayNamesList);
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error searching database for artifact.", ex); // NON-NLS
                return ProcessResult.ERROR;
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.INFO, "Error searching database for artifact.", ex); // NON-NLS
                return ProcessResult.ERROR;
            }
        }

        // insert this file into the central repository 
        if (createCorrelationProperties) {
            try {
                CorrelationAttributeInstance cefi = new CorrelationAttributeInstance(
                        filesType,
                        md5,
                        eamCase,
                        eamDataSource,
                        abstractFile.getParentPath() + abstractFile.getName(),
                        null,
                        TskData.FileKnown.UNKNOWN // NOTE: Known status in the CR is based on tagging, not hashes like the Case Database.
                        ,
                         abstractFile.getId());
                dbManager.addAttributeInstanceBulk(cefi);
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error adding artifact to bulk artifacts.", ex); // NON-NLS
                return ProcessResult.ERROR;
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.INFO, "Error adding artifact to bulk artifacts.", ex); // NON-NLS
                return ProcessResult.ERROR;
            }
        }
        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        IngestEventsListener.decrementCorrelationEngineModuleCount();

        if ((CentralRepository.isEnabled() == false) || (eamCase == null) || (eamDataSource == null)) {
            return;
        }
        CentralRepository dbManager;
        try {
            dbManager = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error connecting to Central Repository database.", ex);
            return;
        }
        try {
            dbManager.commitAttributeInstancesBulk();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error doing bulk insert of artifacts.", ex); // NON-NLS
        }
        try {
            Long count = dbManager.getCountArtifactInstancesByCaseDataSource(eamDataSource);
            logger.log(Level.INFO, "{0} artifacts in db for case: {1} ds:{2}", new Object[]{count, eamCase.getDisplayName(), eamDataSource.getName()}); // NON-NLS
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error counting artifacts.", ex); // NON-NLS
        }

        // TODO: once we implement shared cache, if refCounter is 1, then submit data in bulk.
        refCounter.decrementAndGet(jobId);
    }

    // see ArtifactManagerTimeTester for details
    @Messages({
        "CentralRepoIngestModule.notfyBubble.title=Central Repository Not Initialized",
        "CentralRepoIngestModule.errorMessage.isNotEnabled=Central repository settings are not initialized, cannot run Central Repository ingest module."
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
         * Central Repository module are running. This restriction is to prevent
         * missing results in the case where the first module is flagging
         * notable items, and the proceeding module (with flagging disabled)
         * causes the first to stop flagging.
         */
        if (IngestEventsListener.getCeModuleInstanceCount() == 1 || !IngestEventsListener.isFlagNotableItems()) {
            IngestEventsListener.setFlagNotableItems(flagTaggedNotableItems);
        }
        if (IngestEventsListener.getCeModuleInstanceCount() == 1 || !IngestEventsListener.isFlagSeenDevices()) {
            IngestEventsListener.setFlagSeenDevices(flagPreviouslySeenDevices);
        }
        if (IngestEventsListener.getCeModuleInstanceCount() == 1 || !IngestEventsListener.shouldCreateCrProperties()) {
            IngestEventsListener.setCreateCrProperties(createCorrelationProperties);
        }

        if (CentralRepository.isEnabled() == false) {
            /*
             * Not throwing the customary exception for now. This is a
             * workaround for the way all newly didscovered ingest modules are
             * automatically anabled.
             *
             * TODO (JIRA-2731): Add isEnabled API for ingest modules.
             */
            if (RuntimeProperties.runningWithGUI()) {
                if (1L == warningMsgRefCounter.incrementAndGet(jobId)) {
                    MessageNotifyUtil.Notify.warn(Bundle.CentralRepoIngestModule_notfyBubble_title(), Bundle.CentralRepoIngestModule_errorMessage_isNotEnabled());
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
                && (CentralRepoDbManager.getSavedDbChoice().getDbPlatform() == CentralRepoPlatforms.SQLITE)) {
            logger.log(Level.SEVERE, "Cannot run Central Repository ingest module on a multi-user case with a SQLite central repository.");
            throw new IngestModuleException("Cannot run on a multi-user case with a SQLite central repository."); // NON-NLS
        }
        jobId = context.getJobId();

        CentralRepository centralRepoDb;
        try {
            centralRepoDb = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error connecting to central repository database.", ex); // NON-NLS
            throw new IngestModuleException("Error connecting to central repository database.", ex); // NON-NLS
        }

        try {
            filesType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "Error getting correlation type FILES in ingest module start up.", ex); // NON-NLS
            throw new IngestModuleException("Error getting correlation type FILES in ingest module start up.", ex); // NON-NLS
        }

        try {
            eamCase = centralRepoDb.getCase(autopsyCase);
        } catch (CentralRepoException ex) {
            throw new IngestModuleException("Unable to get case from central repository database ", ex);
        }

        try {
            eamDataSource = CorrelationDataSource.fromTSKDataSource(eamCase, context.getDataSource());
        } catch (CentralRepoException ex) {
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
                if (null == centralRepoDb.getDataSource(eamCase, eamDataSource.getDataSourceObjectID())) {
                    centralRepoDb.newDataSource(eamDataSource);
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error adding data source to Central Repository.", ex); // NON-NLS
                throw new IngestModuleException("Error adding data source to Central Repository.", ex); // NON-NLS
            }

        }
    }

    /**
     * Post a new interesting artifact for the file marked bad.
     *
     * @param abstractFile     The file from which to create an artifact.
     * @param caseDisplayNames Case names to be added to a TSK_COMMON attribute.
     */
    private void postCorrelatedBadFileToBlackboard(AbstractFile abstractFile, List<String> caseDisplayNames) {

        Collection<BlackboardAttribute> attributes = Arrays.asList(
                new BlackboardAttribute(
                        TSK_SET_NAME, MODULE_NAME,
                        Bundle.CentralRepoIngestModule_prevTaggedSet_text()),
                new BlackboardAttribute(
                        TSK_COMMENT, MODULE_NAME,
                        Bundle.CentralRepoIngestModule_prevCaseComment_text() + caseDisplayNames.stream().distinct().collect(Collectors.joining(","))));
        try {

            // Create artifact if it doesn't already exist.
            if (!blackboard.artifactExists(abstractFile, TSK_INTERESTING_FILE_HIT, attributes)) {
                BlackboardArtifact tifArtifact = abstractFile.newArtifact(TSK_INTERESTING_FILE_HIT);
                tifArtifact.addAttributes(attributes);
                try {
                    // index the artifact for keyword search
                    blackboard.postArtifact(tifArtifact, MODULE_NAME);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
                }
                // send inbox message
                sendBadFileInboxMessage(tifArtifact, abstractFile.getName(), abstractFile.getMd5Hash(), caseDisplayNames);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            logger.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
        }
    }

    @Messages({
        "CentralRepoIngestModule_notable_message_header=<html>A file in this data source was previously seen and tagged as Notable.<br>",
        "CentralRepoIngestModel_name_header=Name:<br>",
        "CentralRepoIngestModel_previous_case_header=<br>Previous Cases:<br>",
        "# {0} - Name of file that is Notable",
        "CentralRepoIngestModule_postToBB_knownBadMsg=Notable: {0}"
    })

    /**
     * Post a message to the ingest inbox alerting the user that a bad file was
     * found.
     *
     * @param artifact         badFile Blackboard Artifact
     * @param name             badFile's name
     * @param md5Hash          badFile's md5 hash
     * @param caseDisplayNames List of cases that the artifact appears in.
     */
    private void sendBadFileInboxMessage(BlackboardArtifact artifact, String name, String md5Hash, List<String> caseDisplayNames) {
        StringBuilder detailsSb = new StringBuilder(1024);

        detailsSb.append(Bundle.CentralRepoIngestModule_notable_message_header()).append(Bundle.CentralRepoIngestModel_name_header());
        detailsSb.append(name).append(Bundle.CentralRepoIngestModel_previous_case_header());
        for (String str : caseDisplayNames) {
            detailsSb.append(str).append("<br>");
        }
        detailsSb.append("</html>");
        services.postMessage(IngestMessage.createDataMessage(CentralRepoIngestModuleFactory.getModuleName(),
                Bundle.CentralRepoIngestModule_postToBB_knownBadMsg(name),
                detailsSb.toString(),
                name + md5Hash,
                artifact));
    }
}
