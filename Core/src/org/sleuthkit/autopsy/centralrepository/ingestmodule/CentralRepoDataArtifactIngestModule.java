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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeNormalizationException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
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
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountManager;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * A data artifact ingest module that adds correlation attributes for data
 * artifacts and OS accounts to the central repository and makes analysis
 * results based on previous occurences. When the ingest job is completed,
 * ensures the data source in the central repository has hash values that match
 * those in the case database.
 */
public class CentralRepoDataArtifactIngestModule implements DataArtifactIngestModule {

    private static final Logger LOGGER = Logger.getLogger(CentralRepoDataArtifactIngestModule.class.getName());
    private static final int MAX_PREV_CASES_FOR_NOTABLE_SCORE = 10;
    private static final int MAX_PREV_CASES_FOR_PREV_SEEN = 20;
    private final Set<String> corrAttrsAlreadyCreated;
    private final boolean saveCorrAttrs;
    private final boolean flagNotableItems;
    private final boolean flagPrevSeenDevices;
    private final boolean flagUniqueArtifacts;
    private Case currentCase;
    private Blackboard blackboard;
    private OsAccountManager osAccountMgr;
    private CentralRepository centralRepo;
    private Content dataSource;
    private long ingestJobId;

    /**
     * Constructs a data artifact ingest module that adds correlation attributes
     * for data artifacts and OS accounts to the central repository and makes
     * analysis results based on previous occurences. When the ingest job is
     * completed, ensures the data source in the central repository has hash
     * values that match those in the case database.
     *
     * @param settings The ingest job settings for this module.
     */
    CentralRepoDataArtifactIngestModule(IngestSettings settings) {
        corrAttrsAlreadyCreated = new LinkedHashSet<>();
        saveCorrAttrs = settings.shouldCreateCorrelationProperties();
        flagNotableItems = settings.isFlagTaggedNotableItems();
        flagPrevSeenDevices = settings.isFlagPreviousDevices();
        flagUniqueArtifacts = settings.isFlagUniqueArtifacts();
    }

    @NbBundle.Messages({
        "CentralRepoIngestModule_crNotEnabledErrMsg=Central repository required, but not enabled",
        "CentralRepoIngestModule_noCurrentCaseErrMsg=Error getting current case",
        "CentralRepoIngestModule_osAcctMgrInaccessibleErrMsg=Error getting OS accounts manager",
        "CentralRepoIngestModule_crInaccessibleErrMsg=Error accessing central repository",
        "CentralRepoIngestModule_crDatabaseTypeMismatch=Mulit-user cases require a PostgreSQL central repository"
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        /*
         * IMPORTANT: Start up IngestModuleException messages are displayed to
         * the user, if a user is present. Therefore, an exception to the policy
         * that exception messages are not localized is appropriate here. Also,
         * the exception messages should be user-friendly.
         */
        dataSource = context.getDataSource();
        ingestJobId = context.getJobId();
        if (!CentralRepository.isEnabled()) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crNotEnabledErrMsg()); // May be displayed to user.
        }
        try {
            currentCase = Case.getCurrentCaseThrows();
            SleuthkitCase tskCase = currentCase.getSleuthkitCase();
            blackboard = tskCase.getBlackboard();
            osAccountMgr = tskCase.getOsAccountManager();
            centralRepo = CentralRepository.getInstance();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_noCurrentCaseErrMsg(), ex);
        } catch (TskCoreException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_osAcctMgrInaccessibleErrMsg(), ex);
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crInaccessibleErrMsg(), ex);
        }
        // Don't allow sqlite central repo databases to be used for multi user cases
        if ((currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) && (CentralRepoDbManager.getSavedDbChoice().getDbPlatform() == CentralRepoPlatforms.SQLITE)) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crDatabaseTypeMismatch());
        }

    }

    /**
     * Translates the attributes of a data artifact into central repository
     * correlation attributes and uses them to create analysis results and new
     * central repository correlation attribute instances, depending on ingest
     * job settings.
     *
     * @param artifact The data artifact.
     *
     * @return An ingest module process result.
     */
    @Override
    public ProcessResult process(DataArtifact artifact) {
        List<CorrelationAttributeInstance> corrAttrs = CorrelationAttributeUtil.makeCorrAttrsToSave(artifact);
        for (CorrelationAttributeInstance corrAttr : corrAttrs) {
            if (!corrAttrsAlreadyCreated.add(corrAttr.toString())) {
                /*
                 * This is a bit of a time saver. Uniqueness constraints in the
                 * central repository prevent creation of duplicate correlation
                 * attributes, so this saves no-op central repository insert
                 * attempts.
                 */
                continue;
            }

            makeAnalysisResults(artifact, corrAttr);

            if (saveCorrAttrs) {
                try {
                    centralRepo.addAttributeInstanceBulk(corrAttr);
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error adding correlation attribute '%s' to central repository for data artifact '%s' (job ID=%d)", corrAttr, artifact, ingestJobId), ex); //NON-NLS
                }
            }
        }
        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        if (saveCorrAttrs || flagPrevSeenDevices) {
            analyzeOsAccounts();
        }
        if (saveCorrAttrs) {
            try {
                centralRepo.commitAttributeInstancesBulk();
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error doing final bulk commit of correlation attributes (job ID=%d)", ingestJobId), ex); // NON-NLS
            }
        }
        syncDataSourceHashes();
    }

    /**
     * Adds correlation attributes to the central repository for the OS accounts
     * in the data source and creates previously seen analysis results for the
     * accounts if they have been seen in other cases.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevSeenOsAcctSetName=Users seen in previous cases",
        "CentralRepoIngestModule_prevSeenOsAcctConfig=Previously Seen Users (Central Repository)"
    })
    private void analyzeOsAccounts() {
        try {
            List<OsAccount> osAccounts = osAccountMgr.getOsAccountsByDataSourceObjId(dataSource.getId());
            for (OsAccount osAccount : osAccounts) {
                process(osAccount, dataSource);
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error getting OS accounts for data source %s (job ID=%d)", dataSource, ingestJobId), ex);
        }
    }

    /**
     * Translates the attributes of a OS account and its data source (an OS
     * account instance) into central repository correlation attributes and uses
     * them to create analysis results and new central repository correlation
     * attribute instances, depending on ingest job settings.
     *
     * @param osAccount  The OS account.
     * @param dataSource The data source.
     */
    private void process(OsAccount osAccount, Content dataSource) {
        List<CorrelationAttributeInstance> corrAttrs = CorrelationAttributeUtil.makeCorrAttrsToSave(osAccount, dataSource);
        for (CorrelationAttributeInstance corrAttr : corrAttrs) {
            if (!corrAttrsAlreadyCreated.add(corrAttr.toString())) {
                /*
                 * This is a bit of a time saver. Uniqueness constraints in the
                 * central repository prevent creation of duplicate correlation
                 * attributes, so this saves no-op central repository insert
                 * attempts.
                 */
                continue;
            }

            makeAnalysisResults(osAccount, corrAttr);

            if (saveCorrAttrs) {
                try {
                    centralRepo.addAttributeInstanceBulk(corrAttr);
                } catch (CentralRepoException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error adding correlation attribute '%s' to central repository for OS account '%s' (job ID=%d)", corrAttr, osAccount, ingestJobId), ex);
                }
            }
        }
    }

    /**
     * Makes analysis results for a data artifact based on previous occurrences,
     * if any, of a correlation attribute.
     *
     * @param artifact The data artifact.
     * @param corrAttr A correlation attribute for the data artifact.
     */
    private void makeAnalysisResults(DataArtifact artifact, CorrelationAttributeInstance corrAttr) {
        List<CorrelationAttributeInstance> previousOccurrences = null;
        if (flagNotableItems) {
            previousOccurrences = getOccurrencesInOtherCases(corrAttr);
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = new HashSet<>();
                for (CorrelationAttributeInstance occurrence : previousOccurrences) {
                    if (occurrence.getKnownStatus() == TskData.FileKnown.BAD) {
                        previousCases.add(occurrence.getCorrelationCase().getDisplayName());
                    }
                }
                if (!previousCases.isEmpty()) {
                    makePrevNotableAnalysisResult(artifact, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
                }
            }
        }

        if (flagPrevSeenDevices
                && (corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.USBID_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.ICCID_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.IMEI_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.IMSI_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.MAC_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.EMAIL_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.PHONE_TYPE_ID)) {
            if (previousOccurrences == null) {
                previousOccurrences = getOccurrencesInOtherCases(corrAttr);
            }
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = getPreviousCases(previousOccurrences);
                if (!previousCases.isEmpty()) {
                    makePrevSeenAnalysisResult(artifact, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
                }
            }
        }

        if (flagUniqueArtifacts
                && (corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.DOMAIN_TYPE_ID)) {
            if (previousOccurrences == null) {
                previousOccurrences = getOccurrencesInOtherCases(corrAttr);
            }
            if (previousOccurrences.isEmpty()) {
                makePrevUnseenAnalysisResult(artifact, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
            }
        }
    }

    /**
     * Makes analysis results for a data artifact based on previous occurrences,
     * if any, of a correlation attribute.
     *
     * @param artifact The data artifact.
     * @param corrAttr A correlation attribute for the data artifact.
     */
    private void makeAnalysisResults(OsAccount osAccount, CorrelationAttributeInstance corrAttr) {
        if (flagPrevSeenDevices) {
            List<CorrelationAttributeInstance> previousOccurrences = getOccurrencesInOtherCases(corrAttr);
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = getPreviousCases(previousOccurrences);
                if (!previousCases.isEmpty()) {
                    makePrevSeenAnalysisResult(osAccount, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue());
                }
            }
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
    private List<CorrelationAttributeInstance> getOccurrencesInOtherCases(CorrelationAttributeInstance corrAttr) {
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
            LOGGER.log(Level.SEVERE, String.format("Error normalizing correlation attribute value for 's' (job ID=%d)", corrAttr, ingestJobId), ex); // NON-NLS
        } catch (CentralRepoException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error getting previous occurences of correlation attribute 's' (job ID=%d)", corrAttr, ingestJobId), ex); // NON-NLS
        }
        return previousOccurrences;
    }

    /**
     * Gets a unique set of previous cases, represented by their names, from a
     * list of previous occurrences of correlation attributes.
     *
     * @param previousOccurrences The correlations attributes.
     *
     * @return The names of the previous cases.
     */
    private Set<String> getPreviousCases(List<CorrelationAttributeInstance> previousOccurrences) {
        Set<String> previousCases = new HashSet<>();
        for (CorrelationAttributeInstance occurrence : previousOccurrences) {
            previousCases.add(occurrence.getCorrelationCase().getDisplayName());
        }
        return previousCases;
    }

    /**
     * Makes a previously notable analysis result for a content.
     *
     * @param content       The content.
     * @param previousCases The names of the cases in which the artifact was
     *                      deemed notable.
     * @param corrAttrType  The type of the matched correlation attribute.
     * @param corrAttrValue The value of the matched correlation attribute.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_notableSetName=Previously Tagged As Notable (Central Repository)",
        "# {0} - list of cases",
        "CentralRepoIngestModule_notableJustification=Previously marked as notable in cases {0}"
    })
    private void makePrevNotableAnalysisResult(Content content, Set<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        String prevCases = previousCases.stream().collect(Collectors.joining(","));
        String justification = Bundle.CentralRepoIngestModule_notableJustification(prevCases);
        Collection<BlackboardAttribute> attributes = Arrays.asList(new BlackboardAttribute(TSK_SET_NAME, CentralRepoIngestModuleFactory.getModuleName(), Bundle.CentralRepoIngestModule_notableSetName()),
                new BlackboardAttribute(TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrValue),
                new BlackboardAttribute(TSK_OTHER_CASES, CentralRepoIngestModuleFactory.getModuleName(), prevCases));
        makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_NOTABLE, attributes, "", Score.SCORE_NOTABLE, justification);
    }

    /**
     * Makes a previously seen analysis result for a content, unless the content
     * is too common.
     *
     * @param content       The content.
     * @param previousCases The names of the cases in which the artifact was
     *                      previously seen.
     * @param corrAttrType  The type of the matched correlation attribute.
     * @param corrAttrValue The value of the matched correlation attribute.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevSeenSetName=Previously Seen (Central Repository)",
        "# {0} - list of cases",
        "CentralRepoIngestModule_prevSeenJustification=Previously seen in cases {0}"
    })
    private void makePrevSeenAnalysisResult(Content content, Set<String> previousCases, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        Optional<Score> score = calculateScore(previousCases.size());
        if (score.isPresent()) {
            String prevCases = previousCases.stream().collect(Collectors.joining(","));
            String justification = Bundle.CentralRepoIngestModule_prevSeenJustification(prevCases);
            Collection<BlackboardAttribute> analysisResultAttributes = Arrays.asList(
                    new BlackboardAttribute(TSK_SET_NAME, CentralRepoIngestModuleFactory.getModuleName(), Bundle.CentralRepoIngestModule_prevSeenSetName()),
                    new BlackboardAttribute(TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrType.getDisplayName()),
                    new BlackboardAttribute(TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrValue),
                    new BlackboardAttribute(TSK_OTHER_CASES, CentralRepoIngestModuleFactory.getModuleName(), prevCases));
            makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_SEEN, analysisResultAttributes, "", score.get(), justification);
        }
    }

    /**
     * Makes a previously unseen analysis result for a content.
     *
     * @param content       The content.
     * @param corrAttrType  The type of the new correlation attribute.
     * @param corrAttrValue The value of the new correlation attribute.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevUnseenJustification=Previously seen in zero cases"
    })
    private void makePrevUnseenAnalysisResult(Content content, CorrelationAttributeInstance.Type corrAttrType, String corrAttrValue) {
        Collection<BlackboardAttribute> attributesForNewArtifact = Arrays.asList(
                new BlackboardAttribute(TSK_CORRELATION_TYPE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrType.getDisplayName()),
                new BlackboardAttribute(TSK_CORRELATION_VALUE, CentralRepoIngestModuleFactory.getModuleName(), corrAttrValue));
        makeAndPostAnalysisResult(content, BlackboardArtifact.Type.TSK_PREVIOUSLY_UNSEEN, attributesForNewArtifact, "", Score.SCORE_LIKELY_NOTABLE, Bundle.CentralRepoIngestModule_prevUnseenJustification());
    }

    /**
     * Calculates a score based in a number of previous cases.
     *
     * @param numPreviousCases The number of previous cases.
     *
     * @return An Optional of a score, will be empty if there is no score
     *         because the number of previous cases is too high, indicating a
     *         common and therefore uninteresting item.
     */
    private Optional<Score> calculateScore(int numPreviousCases) {
        Score score = null;
        if (numPreviousCases <= MAX_PREV_CASES_FOR_NOTABLE_SCORE) {
            score = Score.SCORE_LIKELY_NOTABLE;
        } else if (numPreviousCases > MAX_PREV_CASES_FOR_NOTABLE_SCORE && numPreviousCases <= MAX_PREV_CASES_FOR_PREV_SEEN) {
            score = Score.SCORE_NONE;
        }
        return Optional.ofNullable(score);
    }

    /**
     * Makes a new analysis result of a given type for a content and posts it to
     * the blackboard.
     *
     * @param content             The content.
     * @param analysisResultType  The type of analysis result to make.
     * @param analysisResultAttrs The attributes of the new analysis result.
     * @param configuration       The configuration for the new analysis result.
     * @param score               The score for the new analysis result.
     * @param justification       The justification for the new analysis result.
     */
    private void makeAndPostAnalysisResult(Content content, BlackboardArtifact.Type analysisResultType, Collection<BlackboardAttribute> analysisResultAttrs, String configuration, Score score, String justification) {
        try {
            if (!blackboard.artifactExists(content, analysisResultType, analysisResultAttrs)) {
                AnalysisResult analysisResult = content.newAnalysisResult(analysisResultType, score, null, configuration, justification, analysisResultAttrs).getAnalysisResult();
                try {
                    blackboard.postArtifact(analysisResult, CentralRepoIngestModuleFactory.getModuleName(), ingestJobId);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Error posting analysis result '%s' to blackboard for content 's' (job ID=%d)", analysisResult, content, ingestJobId), ex); //NON-NLS
                }
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error creating %s analysis result for content '%s' (job ID=%d)", analysisResultType, content, ingestJobId), ex); // NON-NLS
        }
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
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the central repository for data source '%s' (job ID=%d)", dataSource.getName(), ingestJobId), ex);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the case database for data source '%s' (job ID=%d)", dataSource.getName(), ingestJobId), ex);
        }
    }

}
