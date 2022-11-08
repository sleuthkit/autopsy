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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationDataSource;
import static org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleUtils.getOccurrencesInOtherCases;
import static org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleUtils.makePrevNotableAnalysisResult;
import static org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleUtils.makePrevSeenAnalysisResult;
import static org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleUtils.makePrevUnseenAnalysisResult;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataArtifactIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccountManager;
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
    private final boolean flagNotableItems;
    private final boolean flagPrevSeenDevices;
    private final boolean flagUniqueArtifacts;
    private final boolean saveCorrAttrInstances;
    private final Set<String> corrAttrValuesAlreadyProcessed;
    private CentralRepository centralRepo;
    private IngestJobContext context;

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
        flagNotableItems = settings.isFlagTaggedNotableItems();
        flagPrevSeenDevices = settings.isFlagPreviousDevices();
        flagUniqueArtifacts = settings.isFlagUniqueArtifacts();
        saveCorrAttrInstances = settings.shouldCreateCorrelationProperties();
        corrAttrValuesAlreadyProcessed = new LinkedHashSet<>();
    }

    @NbBundle.Messages({
        "CentralRepoIngestModule_crNotEnabledErrMsg=Central repository required, but not enabled",
        "CentralRepoIngestModule_crInaccessibleErrMsg=Error accessing central repository",
        "CentralRepoIngestModule_noCurrentCaseErrMsg=Error getting current case",
        "CentralRepoIngestModule_crDatabaseTypeMismatch=Mulit-user cases require a PostgreSQL central repository"
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (!CentralRepository.isEnabled()) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crNotEnabledErrMsg()); // May be displayed to user.
        }

        try {
            centralRepo = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crInaccessibleErrMsg(), ex);
        }

        /*
         * Don't allow a SQLite central repository to be used for a multi-user
         * case.
         */
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            if ((currentCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) && (CentralRepoDbManager.getSavedDbChoice().getDbPlatform() == CentralRepoPlatforms.SQLITE)) {
                throw new IngestModuleException(Bundle.CentralRepoIngestModule_crDatabaseTypeMismatch());
            }
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_noCurrentCaseErrMsg(), ex);
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
        if (flagNotableItems || flagPrevSeenDevices || flagUniqueArtifacts || saveCorrAttrInstances) {
            for (CorrelationAttributeInstance corrAttr : CorrelationAttributeUtil.makeCorrAttrsToSave(artifact)) {
                if (corrAttrValuesAlreadyProcessed.add(corrAttr.toString())) {
                    makeAnalysisResults(artifact, corrAttr);
                    if (saveCorrAttrInstances) {
                        try {
                            centralRepo.addAttributeInstanceBulk(corrAttr);
                        } catch (CentralRepoException ex) {
                            LOGGER.log(Level.SEVERE, String.format("Error adding correlation attribute '%s' to central repository for '%s' (job ID=%d)", corrAttr, artifact, context.getJobId()), ex); //NON-NLS
                        }
                    }
                }
            }
        }
        return ProcessResult.OK;
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
            previousOccurrences = getOccurrencesInOtherCases(corrAttr, context.getJobId());
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = new HashSet<>();
                for (CorrelationAttributeInstance occurrence : previousOccurrences) {
                    if (occurrence.getKnownStatus() == TskData.FileKnown.BAD) {
                        previousCases.add(occurrence.getCorrelationCase().getDisplayName());
                    }
                }
                if (!previousCases.isEmpty()) {
                    makePrevNotableAnalysisResult(artifact, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue(), context.getDataSource().getId(), context.getJobId());
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
                previousOccurrences = getOccurrencesInOtherCases(corrAttr, context.getJobId());
            }
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = getPreviousCases(previousOccurrences);
                if (!previousCases.isEmpty()) {
                    makePrevSeenAnalysisResult(artifact, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue(), context.getDataSource().getId(), context.getJobId());
                }
            }
        }

        if (flagUniqueArtifacts
                && (corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.INSTALLED_PROGS_TYPE_ID
                || corrAttr.getCorrelationType().getId() == CorrelationAttributeInstance.DOMAIN_TYPE_ID)) {
            if (previousOccurrences == null) {
                previousOccurrences = getOccurrencesInOtherCases(corrAttr, context.getJobId());
            }
            if (previousOccurrences.isEmpty()) {
                makePrevUnseenAnalysisResult(artifact, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue(), context.getDataSource().getId(), context.getJobId());
            }
        }
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

    @Override
    public void shutDown() {
        analyzeOsAccounts();
        if (saveCorrAttrInstances) {
            try {
                centralRepo.commitAttributeInstancesBulk();
            } catch (CentralRepoException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error doing final bulk commit of correlation attributes (job ID=%d)", context.getJobId()), ex); // NON-NLS
            }
        }
        syncDataSourceHashes();
    }

    /**
     * Queries the case database for any OS accounts assoicated with the data
     * source for the ingest job. The attributes of any OS account returned by
     * the query are translated into central repository correlation attributes
     * and used them to create analysis results and new central repository
     * correlation attribute instances, depending on ingest job settings.
     */
    @NbBundle.Messages({
        "CentralRepoIngestModule_prevSeenOsAcctSetName=Users seen in previous cases",
        "CentralRepoIngestModule_prevSeenOsAcctConfig=Previously Seen Users (Central Repository)"
    })
    private void analyzeOsAccounts() {
        if (saveCorrAttrInstances || flagPrevSeenDevices) {
            try {
                OsAccountManager osAccountMgr = Case.getCurrentCaseThrows().getSleuthkitCase().getOsAccountManager();
                List<OsAccount> osAccounts = osAccountMgr.getOsAccountsByDataSourceObjId(context.getDataSource().getId());
                for (OsAccount osAccount : osAccounts) {
                    for (CorrelationAttributeInstance corrAttr : CorrelationAttributeUtil.makeCorrAttrsToSave(osAccount, context.getDataSource())) {
                        if (flagPrevSeenDevices) {
                            makeAnalysisResults(osAccount, corrAttr);
                        }
                        if (saveCorrAttrInstances) {
                            try {
                                centralRepo.addAttributeInstanceBulk(corrAttr);
                            } catch (CentralRepoException ex) {
                                LOGGER.log(Level.SEVERE, String.format("Error adding correlation attribute '%s' to central repository for '%s'(job ID=%d)", corrAttr, osAccount, context.getJobId()), ex); //NON-NLS
                            }
                        }
                    }
                }
            } catch (NoCurrentCaseException | TskCoreException ex) {
                LOGGER.log(Level.SEVERE, String.format("Error getting OS accounts for data source '%s' (job ID=%d)", context.getDataSource(), context.getJobId()), ex);
            }
        }
    }

    /**
     * Makes analysis results for an OS Account based on previous occurrences,
     * if any, of a correlation attribute.
     *
     * @param artifact The data artifact.
     * @param corrAttr A correlation attribute for the data artifact.
     */
    private void makeAnalysisResults(OsAccount osAccount, CorrelationAttributeInstance corrAttr) {
        if (flagPrevSeenDevices) {
            List<CorrelationAttributeInstance> previousOccurrences = getOccurrencesInOtherCases(corrAttr, context.getJobId());
            if (!previousOccurrences.isEmpty()) {
                Set<String> previousCases = getPreviousCases(previousOccurrences);
                if (!previousCases.isEmpty()) {
                    makePrevSeenAnalysisResult(osAccount, previousCases, corrAttr.getCorrelationType(), corrAttr.getCorrelationValue(), context.getDataSource().getId(), context.getJobId());
                }
            }
        }
    }

    /**
     * Ensures the data source in the central repository has hash values that
     * match those in the case database.
     */
    private void syncDataSourceHashes() {
        if (!(context.getDataSource() instanceof Image)) {
            return;
        }

        try {
            Case currentCase = Case.getCurrentCaseThrows();
            CorrelationCase correlationCase = centralRepo.getCase(currentCase);
            if (correlationCase == null) {
                correlationCase = centralRepo.newCase(currentCase);
            }

            CorrelationDataSource correlationDataSource = centralRepo.getDataSource(correlationCase, context.getDataSource().getId());
            if (correlationDataSource == null) {
                correlationDataSource = CorrelationDataSource.fromTSKDataSource(correlationCase, context.getDataSource());
            }

            Image image = (Image) context.getDataSource();
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
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the central repository for data source '%s' (job ID=%d)", context.getDataSource().getName(), context.getJobId()), ex);
        } catch (NoCurrentCaseException | TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error fetching data from the case database for data source '%s' (job ID=%d)", context.getDataSource().getName(), context.getJobId()), ex);
        }
    }

}
