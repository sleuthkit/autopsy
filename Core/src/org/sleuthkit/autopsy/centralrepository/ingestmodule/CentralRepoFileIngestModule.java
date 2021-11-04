/*
 * Central Repository
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
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
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_VALUE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OTHER_CASES;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.datamodel.Score;

/**
 * A file ingest module that adds correlation attributes for files to the
 * central repository and makes previously notable analysis results based on
 * previous occurences.
 */
@Messages({"CentralRepoIngestModule.prevTaggedSet.text=Previously Tagged As Notable (Central Repository)",
    "CentralRepoIngestModule.prevCaseComment.text=Previous Case: "})
final class CentralRepoFileIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(CentralRepoFileIngestModule.class.getName());
    private static final String MODULE_NAME = CentralRepoIngestModuleFactory.getModuleName();
    private final IngestServices services = IngestServices.getInstance();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private CorrelationCase centralRepoCase;
    private CorrelationDataSource centralRepoDataSource;
    private CorrelationAttributeInstance.Type filesType;
    private final boolean flagTaggedNotableItems;
    private Blackboard blackboard;
    private final boolean createCorrelationProperties;
    private CentralRepository centralRepoDb;

    /**
     * Constructs a file ingest module that adds correlation attributes for
     * files to the central repository and makes previously notable analysis
     * results based on previous occurences.
     *
     * @param settings The ingest job settings.
     */
    CentralRepoFileIngestModule(IngestSettings settings) {
        flagTaggedNotableItems = settings.isFlagTaggedNotableItems();
        createCorrelationProperties = settings.shouldCreateCorrelationProperties();
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        if (!CorrelationAttributeUtil.isSupportedAbstractFileType(abstractFile)) {
            return ProcessResult.OK;
        }

        if (abstractFile.getKnown() == TskData.FileKnown.KNOWN) {
            return ProcessResult.OK;
        }

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
                List<String> caseDisplayNamesList = centralRepoDb.getListCasesHavingArtifactInstancesKnownBad(filesType, md5);
                HealthMonitor.submitTimingMetric(timingMetric);
                if (!caseDisplayNamesList.isEmpty()) {
                    postCorrelatedBadFileToBlackboard(abstractFile, caseDisplayNamesList, filesType, md5);
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
                        centralRepoCase,
                        centralRepoDataSource,
                        abstractFile.getParentPath() + abstractFile.getName(),
                        null,
                        TskData.FileKnown.UNKNOWN // NOTE: Known status in the CR is based on tagging, not hashes like the Case Database.
                        ,
                         abstractFile.getId());
                centralRepoDb.addAttributeInstanceBulk(cefi);
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
        if (refCounter.decrementAndGet(jobId) == 0) {
            try {
                centralRepoDb.commitAttributeInstancesBulk();
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error committing bulk insert of correlation attributes", ex); // NON-NLS
            }
        }
    }

    @Messages({
        "CentralRepoIngestModule_missingFileCorrAttrTypeErrMsg=Correlation attribute type for files not found in the central repository",
        "CentralRepoIngestModule_cannotGetCrCaseErrMsg=Case not present in the central repository",
        "CentralRepoIngestModule_cannotGetCrDataSourceErrMsg=Data source not present in the central repository"
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        jobId = context.getJobId();

        /*
         * IMPORTANT: Start up IngestModuleException messages are displayed to
         * the user, if a user is present. Therefore, an exception to the policy
         * that exception messages are not localized is appropriate here. Also,
         * the exception messages should be user-friendly.
         */
        if (!CentralRepository.isEnabled()) {
            throw new IngestModuleException(Bundle.CrDataArtifactIngestModule_crNotEnabledErrMsg());
        }

        Case autopsyCase;
        try {
            autopsyCase = Case.getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.CrDataArtifactIngestModule_noCurrentCaseErrMsg(), ex);
        }

        blackboard = autopsyCase.getSleuthkitCase().getBlackboard();

        try {
            centralRepoDb = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crInaccessibleErrMsg(), ex);
        }

        try {
            filesType = centralRepoDb.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_missingFileCorrAttrTypeErrMsg(), ex);
        }

        try {
            centralRepoCase = centralRepoDb.getCase(autopsyCase);
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_cannotGetCrCaseErrMsg(), ex);
        }

        try {
            centralRepoDataSource = CorrelationDataSource.fromTSKDataSource(centralRepoCase, context.getDataSource());
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_cannotGetCrDataSourceErrMsg(), ex);
        }

        refCounter.incrementAndGet(jobId);
    }

    /**
     * Post a new "previously seen" artifact for the file marked bad.
     *
     * @param abstractFile     The file from which to create an artifact.
     * @param caseDisplayNames Case names to be added to a TSK_COMMON attribute.
     */
    private void postCorrelatedBadFileToBlackboard(AbstractFile abstractFile, List<String> caseDisplayNames, CorrelationAttributeInstance.Type corrAtrrType, String corrAttrValue) {
        String prevCases = caseDisplayNames.stream().distinct().collect(Collectors.joining(","));
        String justification = "Previously marked as notable in cases " + prevCases;
        Collection<BlackboardAttribute> attributes = Arrays.asList(new BlackboardAttribute(
                TSK_SET_NAME, MODULE_NAME,
                Bundle.CentralRepoIngestModule_prevTaggedSet_text()),
                new BlackboardAttribute(
                        TSK_CORRELATION_TYPE, MODULE_NAME,
                        corrAtrrType.getDisplayName()),
                new BlackboardAttribute(
                        TSK_CORRELATION_VALUE, MODULE_NAME,
                        corrAttrValue),
                new BlackboardAttribute(
                        TSK_OTHER_CASES, MODULE_NAME,
                        prevCases));
        try {
            // Create artifact if it doesn't already exist.
            if (!blackboard.artifactExists(abstractFile, BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, attributes)) {
                BlackboardArtifact tifArtifact = abstractFile.newAnalysisResult(
                        BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, Score.SCORE_NOTABLE,
                        null, Bundle.CentralRepoIngestModule_prevTaggedSet_text(), justification, attributes)
                        .getAnalysisResult();
                try {
                    blackboard.postArtifact(tifArtifact, MODULE_NAME, jobId);
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

    /**
     * Post a message to the ingest inbox alerting the user that a bad file was
     * found.
     *
     * @param artifact         badFile Blackboard Artifact
     * @param name             badFile's name
     * @param md5Hash          badFile's md5 hash
     * @param caseDisplayNames List of cases that the artifact appears in.
     */
    @Messages({
        "CentralRepoIngestModule_notable_message_header=<html>A file in this data source was previously seen and tagged as Notable.<br>",
        "CentralRepoIngestModel_name_header=Name:<br>",
        "CentralRepoIngestModel_previous_case_header=<br>Previous Cases:<br>",
        "# {0} - Name of file that is Notable",
        "CentralRepoIngestModule_postToBB_knownBadMsg=Notable: {0}"
    })
    private void sendBadFileInboxMessage(BlackboardArtifact artifact, String name, String md5Hash, List<String> caseDisplayNames) {
        StringBuilder detailsSb = new StringBuilder(1024);

        detailsSb.append(Bundle.CentralRepoIngestModule_notable_message_header()).append(Bundle.CentralRepoIngestModel_name_header());
        detailsSb.append(name).append(Bundle.CentralRepoIngestModel_previous_case_header());
        for (String str : caseDisplayNames) {
            detailsSb.append(str).append("<br>");
        }
        detailsSb.append("</html>");
        services.postMessage(IngestMessage.createDataMessage(MODULE_NAME,
                Bundle.CentralRepoIngestModule_postToBB_knownBadMsg(name),
                detailsSb.toString(),
                name + md5Hash,
                artifact));
    }

}
