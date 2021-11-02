/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021-2021 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import org.sleuthkit.autopsy.centralrepository.eventlisteners.CaseEventListener;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataArtifactIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_TYPE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CORRELATION_VALUE;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OTHER_CASES;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A data artifact ingest module that adds correlation attributes for a data
 * artifact to the central repository and makes analysis results based on
 * previous occurences. When the ingest job is completed, ensures the data
 * source in the central repository has hash values that match those in the case
 * database.
 */
public class CentralRepoDataArtifactIngestModule implements DataArtifactIngestModule {

    private static final Logger LOGGER = Logger.getLogger(CentralRepoDataArtifactIngestModule.class.getName());
    private final Set<String> corrAttrsCreated;
    private final boolean saveCorrAttrs;
    private final boolean flagNotableItems;
    private final boolean flagSeenDevices;
    private final boolean flagUniqueArtifacts;
    private Case currentCase;
    private Blackboard blackboard;
    private CentralRepository centralRepo;
    private Content dataSource;
    private long ingestJobId;

    /**
     * Constructs a data artifact ingest module that adds correlation attributes
     * for a data artifact to the central repository and makes analysis results
     * based on previous occurences. When the ingest job is completed, ensures
     * the data source in the central repository has hash values that match
     * those in the case database.
     *
     * @param settings The ingest job settings for this module.
     */
    CentralRepoDataArtifactIngestModule(IngestSettings settings) {
        corrAttrsCreated = new LinkedHashSet<>();
        saveCorrAttrs = settings.shouldCreateCorrelationProperties();
        flagNotableItems = settings.isFlagTaggedNotableItems();
        flagSeenDevices = settings.isFlagPreviousDevices();
        flagUniqueArtifacts = settings.isFlagUniqueArtifacts();
    }

    @NbBundle.Messages({
        "CrDataArtifactIngestModule_crNotEnabledErrMsg=Central repository required, but not enabled",
        "CrDataArtifactIngestModule_noCurrentCaseErrMsg=Error getting current case",
        "CrDataArtifactIngestModule_crInaccessibleErrMsg=Error accessing central repository",})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        dataSource = context.getDataSource();
        ingestJobId = context.getJobId();
        if (!CentralRepository.isEnabled()) {
            throw new IngestModuleException(Bundle.CrDataArtifactIngestModule_crNotEnabledErrMsg()); // May be displayed to user.
        }
        try {
            currentCase = Case.getCurrentCaseThrows();
            blackboard = currentCase.getSleuthkitCase().getBlackboard();
            centralRepo = CentralRepository.getInstance();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.CrDataArtifactIngestModule_noCurrentCaseErrMsg(), ex); // May be displayed to user.
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CrDataArtifactIngestModule_crInaccessibleErrMsg(), ex); // May be displayed to user.
        }
        /*
         * Pass the relevant ingest job settings on to the case events listener
         * for the central repository. Note that the listener's dependency on
         * these settings currently means that it can only react to new OS
         * account instances events when an ingest job with this module enabled
         * is running.
         */
        CaseEventListener.setCreateOsAcctCorrAttrs(saveCorrAttrs);
        CaseEventListener.setFlagPrevSeenOsAccts(flagSeenDevices);
    }

    @Override
    public ProcessResult process(DataArtifact artifact) {
        List<CorrelationAttributeInstance> corrAttrs = CorrelationAttributeUtil.makeCorrAttrsToSave(artifact);
        for (CorrelationAttributeInstance corrAttr : corrAttrs) {
            if (!corrAttrsCreated.add(corrAttr.toString())) {
                continue;
            }

            if (flagNotableItems || flagSeenDevices || flagUniqueArtifacts) {
                makeAnalysisResults(artifact, corrAttr);
            }

            if (saveCorrAttrs) {
                try {
                    centralRepo.addAttributeInstanceBulk(corrAttr);
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error doing bulk add of correlation attribute to central repository (%s) ", corrAttr), ex); //NON-NLS
                }
            }
        }
        return ProcessResult.OK;
    }

    /**
     * Makes analysis results for a data artifact based on previous occurences,
     * if any, of a correlation attribute.
     *
     * @param artifact The data artifact.
     * @param corrAttr A correlation attribute for the data artifact.
     */
    private void makeAnalysisResults(DataArtifact artifact, CorrelationAttributeInstance corrAttr) {
        List<CorrelationAttributeInstance> previousOccurrences = getPreviousOccurrences(corrAttr);
        if (previousOccurrences.isEmpty()) {
            return;
        }

        /*
         * Make a previously notable analysis result for the data artifact if
         * the correlation attribute has been seen in another case and marked as
         * notable (TskData.FileKnown.BAD).
         */
        if (flagNotableItems) {
            List<String> previousCaseNames = new ArrayList<>();
            for (CorrelationAttributeInstance occurrence : previousOccurrences) {
                if (occurrence.getKnownStatus() == TskData.FileKnown.BAD) {
                    previousCaseNames.add(occurrence.getCorrelationCase().getDisplayName()); // Dups are removed later
                }
            }
            if (!previousCaseNames.isEmpty()) {
                makePreviousNotableAnalysisResult(artifact, previousCaseNames, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
            }
        }

        /*
         * Make a previously seen analysis result result for the data artifact
         * if the correlation attribute has been seen in another case and is a
         * device or communication account attribute.
         */
        if (flagSeenDevices && !previousOccurrences.isEmpty()
                && (corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.USBID_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.ICCID_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.IMEI_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.IMSI_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.MAC_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.EMAIL_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.PHONE_TYPE_ID)) {
            List<String> previousCaseNames = new ArrayList<>();
            for (CorrelationAttributeInstance occurrence : previousOccurrences) {
                previousCaseNames.add(occurrence.getCorrelationCase().getDisplayName()); // Dups are removed later
            }
            if (!previousCaseNames.isEmpty()) {
                makePreviouslySeenAnalysisResult(artifact, previousCaseNames, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
            }
        }

        /*
         * Make a previously unseen analysis result result for the data artifact
         * if the correlation attribute has not been seen in another case and is
         * an app name or domain name attribute.
         */
        if (flagUniqueArtifacts
                && (corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.DOMAIN_TYPE_ID)) {
            makeAndPostPreviouslyUnseenArtifact(artifact, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
        }
    }

    /**
     * Gets any previous occurrences of a given correlation attribute in cases
     * other than the current case.
     *
     * @param corrAttr The correlation attribute.
     *
     * @return The other occurrences of the correlation attribute.
     */
    private List<CorrelationAttributeInstance> getPreviousOccurrences(CorrelationAttributeInstance corrAttr) {
        List<CorrelationAttributeInstance> previousOccurrences = new ArrayList<>();
        try {
            previousOccurrences = centralRepo.getArtifactInstancesByTypeValue(corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
            for (Iterator<CorrelationAttributeInstance> iterator = previousOccurrences.iterator(); iterator.hasNext();) {
                CorrelationAttributeInstance prevOccurrence = iterator.next();
                if (prevOccurrence.getCorrelationCase().getCaseUUID().equals(corrAttr.getCorrelationCase().getCaseUUID())) {
                    iterator.remove();
                }
            }
        } catch (CorrelationAttributeNormalizationException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error normalizing correlation attribute value (s)", corrAttr), ex); // NON-NLS
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error getting previous occurences of correlation attribute (s)", corrAttr), ex); // NON-NLS
        }
        return previousOccurrences;
    }

    /**
     * Makes a previously notable analysis result for a data artifact.
     *
     * @param artifact      The data artifact.
     * @param previousCases The names of the cases in which the artifact was
     *                      deemed notable.
     * @param corrAttrType  The type of the matched correlation attribute.
     * @param corrAttrValue The value of the matched correlation attribute.
     */
    @NbBundle.Messages({
        "CrDataArtifactIngestModule_notableSetName=Previously Tagged As Notable (Central Repository)",
        "# {0} - list of cases",
        "CrDataArtifactIngestModule_notableJustification=Previously marked as notable in cases {0}"
    })
    private void makePreviousNotableAnalysisResult(DataArtifact artifact, List<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        String prevCases = previousCases.stream().distinct().collect(Collectors.joining(","));
        String justification = Bundle.CrDataArtifactIngestModule_notableJustification(prevCases);
        Collection<BlackboardAttribute> attributes = Arrays.asList(new BlackboardAttribute(TSK_SET_NAME, CentralRepoIngestModuleFactory.getModuleName(), Bundle.CrDataArtifactIngestModule_notableSetName()),
                new BlackboardAttribute(TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrValue),
                new BlackboardAttribute(TSK_OTHER_CASES, CentralRepoIngestModuleFactory.getModuleName(), prevCases));
        makeAndPostAnalysisResult(artifact, BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, attributes, "", Score.SCORE_NOTABLE, justification);
    }

    /**
     * Makes a previously seen analysis result for a data artifact, unless the
     * artifact is too common.
     *
     * @param artifact      The data artifact.
     * @param previousCases The names of the cases in which the artifact was
     *                      previously seen.
     * @param corrAttrType  The type of the matched correlation attribute.
     * @param corrAttrValue The value of the matched correlation attribute.
     */
    @NbBundle.Messages({
        "CrDataArtifactIngestModule_prevSeenSetName=Previously Seen (Central Repository)",
        "# {0} - list of cases",
        "CrDataArtifactIngestModule_prevSeenJustification=Previously seen in cases {0}"
    })
    private void makePreviouslySeenAnalysisResult(DataArtifact artifact, List<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        Score score;
        int numCases = previousCases.size();
        if (numCases <= AnalysisParams.MAX_PREV_CASES_FOR_NOTABLE_SCORE) {
            score = Score.SCORE_LIKELY_NOTABLE;
        } else if (numCases > AnalysisParams.MAX_PREV_CASES_FOR_NOTABLE_SCORE && numCases <= AnalysisParams.MAX_PREV_CASES_FOR_PREV_SEEN) {
            score = Score.SCORE_NONE;
        } else {
            /*
             * Don't make the analysis result, the artifact is too common.
             */
            return;
        }

        String prevCases = previousCases.stream().distinct().collect(Collectors.joining(","));
        String justification = Bundle.CrDataArtifactIngestModule_prevSeenJustification(prevCases);
        Collection<BlackboardAttribute> analysisResultAttributes = Arrays.asList(
                new BlackboardAttribute(TSK_SET_NAME, CentralRepoIngestModuleFactory.getModuleName(), Bundle.CrDataArtifactIngestModule_prevSeenSetName()),
                new BlackboardAttribute(TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrValue),
                new BlackboardAttribute(TSK_OTHER_CASES, CentralRepoIngestModuleFactory.getModuleName(), prevCases));
        makeAndPostAnalysisResult(artifact, BlackboardArtifact.Type.TSK_PREVIOUSLY_SEEN, analysisResultAttributes, "", score, justification);
    }

    /**
     * Makes a previously unseen analysis result for a data artifact.
     *
     * @param artifact      The data artifact.
     * @param corrAttrType  The type of the new correlation attribute.
     * @param corrAttrValue The value of the new correlation attribute.
     */
    @NbBundle.Messages({
        "CrDataArtifactIngestModule_prevUnseenJustification=Previously seen in zero cases"
    })
    private void makeAndPostPreviouslyUnseenArtifact(DataArtifact artifact, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(new BlackboardAttribute(
                TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(),
                corrAttrType.getDisplayName()),
                new BlackboardAttribute(
                        TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(),
                        corrAttrValue));
        makeAndPostAnalysisResult(artifact, BlackboardArtifact.Type.TSK_PREVIOUSLY_UNSEEN, attributesForNewArtifact, "", Score.SCORE_LIKELY_NOTABLE, Bundle.CrDataArtifactIngestModule_prevUnseenJustification());
    }

    /**
     * Makes a new analysis result of a given type for a data artifact and posts
     * it to the blackboard.
     *
     * @param artifact            The data artifact.
     * @param analysisResultType  The type of analysis result to make.
     * @param analysisResultAttrs The attributes of the new analysis result.
     * @param configuration       The configuration for the new analysis result.
     * @param score               The score for the new analysis result.
     * @param justification       The justification for the new analysis result.
     */
    private void makeAndPostAnalysisResult(DataArtifact artifact, BlackboardArtifact.Type analysisResultType, Collection<BlackboardAttribute> analysisResultAttrs, String configuration, Score score, String justification) {
        try {
            if (!blackboard.artifactExists(artifact, analysisResultType, analysisResultAttrs)) {
                AnalysisResult analysisResult = artifact.newAnalysisResult(analysisResultType, score, null, configuration, justification, analysisResultAttrs).getAnalysisResult();
                try {
                    blackboard.postArtifact(analysisResult, CentralRepoIngestModuleFactory.getModuleName(), ingestJobId);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error posting analysis result to blackboard (*s)", analysisResult), ex); //NON-NLS
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error creating analysis result", ex); // NON-NLS
        }
    }

    @Override
    public void shutDown() {
        try {
            centralRepo.commitAttributeInstancesBulk();
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, "Error doing final bulk commit of correlation attributes", ex); // NON-NLS
        }
        /*
         * Data artifact ingest modules are shut down at the end of the ingest
         * job. Now that the job is complete, ensure that the data source in the
         * central repository that corresponds to the data source for the ingest
         * job has hash values that match those in the case database.
         */
        syncDataSourceHashes();
        /*
         * Clear the relevant ingest job settings that were passed on to the
         * case events listener for the central repository. Note that the
         * listener's dependency on these settings currently means that it can
         * only react to new OS account instances events when an ingest job with
         * this module enabled is running.
         */
        CaseEventListener.setCreateOsAcctCorrAttrs(saveCorrAttrs);
        CaseEventListener.setFlagPrevSeenOsAccts(flagSeenDevices);
    }

    /**
     * Ensures the data source in the central repository has hash values that
     * match those in the case database.
     */
    private void syncDataSourceHashes() {
        if (!(dataSource instanceof Image)) {
            return;
        }

        try {
            CorrelationCase correlationCase = centralRepo.getCase(currentCase);
            if (correlationCase == null) {
                correlationCase = centralRepo.newCase(currentCase);
            }

            CorrelationDataSource correlationDataSource = centralRepo.getDataSource(correlationCase, dataSource.getId());
            if (correlationDataSource == null) {
                correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, dataSource);
            }

            Image image = (Image) dataSource;
            String imageMd5Hash = image.getMd5();
            if (imageMd5Hash == null) {
                imageMd5Hash = "";
            }
            String crMd5Hash = correlationDataSource.getMd5();
            if (StringUtils.equals(imageMd5Hash, crMd5Hash) == false) {
                correlationDataSource.setMd5(imageMd5Hash);
            }

            String imageSha1Hash = image.getSha1();
            if (imageSha1Hash == null) {
                imageSha1Hash = "";
            }
            String crSha1Hash = correlationDataSource.getSha1();
            if (StringUtils.equals(imageSha1Hash, crSha1Hash) == false) {
                correlationDataSource.setSha1(imageSha1Hash);
            }

            String imageSha256Hash = image.getSha256();
            if (imageSha256Hash == null) {
                imageSha256Hash = "";
            }
            String crSha256Hash = correlationDataSource.getSha256();
            if (StringUtils.equals(imageSha256Hash, crSha256Hash) == false) {
                correlationDataSource.setSha256(imageSha256Hash);
            }

        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the central repository for data source '%s' (obj_id=%d)", dataSource.getName(), dataSource.getId()), ex);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the case database for data source '%s' (obj_id=%d)", dataSource.getName(), dataSource.getId()), ex);
        }
    }

}
