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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
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
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepository;
import static org.sleuthkit.autopsy.centralrepository.ingestmodule.CentralRepoIngestModuleUtils.makePrevNotableAnalysisResult;

/**
 * A file ingest module that adds correlation attributes for files to the
 * central repository, and makes previously notable analysis results for files
 * marked as notable in other cases.
 */
final class CentralRepoIngestModule implements FileIngestModule {

    private static final Logger logger = Logger.getLogger(CentralRepoIngestModule.class.getName());
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private final boolean flagNotableItems;
    private final boolean saveCorrAttrInstances;
    private CorrelationAttributeInstance.Type filesType;
    private IngestJobContext context;
    private CentralRepository centralRepo;
    
    /**
     * Constructs a file ingest module that adds correlation attributes for
     * files to the central repository, and makes previously notable analysis
     * results for files marked as notable in other cases.
     *
     * @param settings The ingest job settings.
     */
    CentralRepoIngestModule(IngestSettings settings) {
        flagNotableItems = settings.isFlagTaggedNotableItems();
        saveCorrAttrInstances = settings.shouldCreateCorrelationProperties();
    }    
    
    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (!flagNotableItems && !saveCorrAttrInstances) {
            return ProcessResult.OK;
        }

        if (!filesType.isEnabled()) {
            return ProcessResult.OK;
        }

        if (abstractFile.getKnown() == TskData.FileKnown.KNOWN) {
            return ProcessResult.OK;
        }

        if (!CorrelationAttributeUtil.isSupportedAbstractFileType(abstractFile)) {
            return ProcessResult.OK;
        }
 
        /*
         * The correlation attribute value for a file is its MD5 hash. This
         * module cannot do anything with a file if the hash calculation has not
         * been done, but the decision has been made to not do a hash
         * calculation here if the file hashing and lookup module is not in this
         * pipeline ahead of this module (affirmed per BC, 11/8/21).
         */
        String md5 = abstractFile.getMd5Hash();
        if ((md5 == null) || (HashUtility.isNoDataMd5(md5))) {
            return ProcessResult.OK;
        }

        if (flagNotableItems) {
            try {
                TimingMetric timingMetric = HealthMonitor.getTimingMetric("Central Repository: Notable artifact query");
                Set<String> otherCases = new HashSet<>();
                otherCases.addAll(centralRepo.getListCasesHavingArtifactInstancesKnownBad(filesType, md5));
                HealthMonitor.submitTimingMetric(timingMetric);
                if (!otherCases.isEmpty()) {
                    makePrevNotableAnalysisResult(abstractFile, otherCases, filesType, md5, context.getDataSource().getId(), context.getJobId());
                }
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, "Error searching database for artifact.", ex); // NON-NLS
            } catch (CorrelationAttributeNormalizationException ex) {
                logger.log(Level.INFO, "Error searching database for artifact: " +  ex.getMessage()); // NON-NLS
            }
        }

        if (saveCorrAttrInstances) {
            List<CorrelationAttributeInstance> corrAttrs = CorrelationAttributeUtil.makeCorrAttrsToSave(abstractFile);
            for (CorrelationAttributeInstance corrAttr : corrAttrs) {
                try {
                    centralRepo.addAttributeInstanceBulk(corrAttr);
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "Error adding artifact to bulk artifacts.", ex); // NON-NLS
                }
            }
        }

        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        if (refCounter.decrementAndGet(context.getJobId()) == 0) {
            try {
                centralRepo.commitAttributeInstancesBulk();
            } catch (CentralRepoException ex) {
                logger.log(Level.SEVERE, String.format("Error committing bulk insert of correlation attributes (job ID=%d)", context.getJobId()), ex); // NON-NLS
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
        this.context = context;

        if (!CentralRepository.isEnabled()) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crNotEnabledErrMsg());
        }

        try {
            centralRepo = CentralRepository.getInstance();
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_crInaccessibleErrMsg(), ex);
        }

        /*
         * Make sure the correlation attribute type definition is in the central
         * repository. Currently (11/8/21) it is cached, but there is no harm in
         * saving it here for use in process().
         */
        try {
            filesType = centralRepo.getCorrelationTypeById(CorrelationAttributeInstance.FILES_TYPE_ID);
        } catch (CentralRepoException ex) {
            throw new IngestModuleException(Bundle.CentralRepoIngestModule_missingFileCorrAttrTypeErrMsg(), ex);
        }

        /*
         * The first module instance started for this job makes sure the current
         * case and data source are in the central repository. Currently
         * (11/8/21), these are cached upon creation / first retreival.
         */
        if (refCounter.incrementAndGet(context.getJobId()) == 1) {
            Case currentCase;
            try {
                currentCase = Case.getCurrentCaseThrows();
            } catch (NoCurrentCaseException ex) {
                throw new IngestModuleException(Bundle.CentralRepoIngestModule_noCurrentCaseErrMsg(), ex);
            }

            CorrelationCase centralRepoCase;
            try {
                centralRepoCase = centralRepo.getCase(currentCase);
            } catch (CentralRepoException ex) {
                throw new IngestModuleException(Bundle.CentralRepoIngestModule_cannotGetCrCaseErrMsg(), ex);
            }

            try {
                CorrelationDataSource.fromTSKDataSource(centralRepoCase, context.getDataSource());
            } catch (CentralRepoException ex) {
                throw new IngestModuleException(Bundle.CentralRepoIngestModule_cannotGetCrDataSourceErrMsg(), ex);
            }
        }
    }

}
