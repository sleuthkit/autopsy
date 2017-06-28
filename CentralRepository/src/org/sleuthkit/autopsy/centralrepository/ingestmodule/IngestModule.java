/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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

import org.sleuthkit.autopsy.centralrepository.datamodel.EamCase;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifact;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamArtifactInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDataSource;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.HashUtility;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamOrganization;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Ingest module for inserting entries into the Central Repository
 * database on ingest of a data source
 */
@Messages({"IngestModule.prevcases.text=Previous Cases"})
class IngestModule implements FileIngestModule {

    private final static Logger LOGGER = Logger.getLogger(IngestModule.class.getName());
    private final IngestServices services = IngestServices.getInstance();
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private static final IngestModuleReferenceCounter warningMsgRefCounter = new IngestModuleReferenceCounter();
    private long jobId;
    private EamCase eamCase;
    private EamDataSource eamDataSource;
    private Blackboard blackboard;
    private EamArtifact.Type filesType;

    @Override
    public ProcessResult process(AbstractFile af) {
        if (EamDb.isEnabled() == false) {
            /*
             * Not signaling an error for now. This is a workaround for the way
             * all newly didscovered ingest modules are automatically anabled.
             *
             * TODO (JIRA-2731): Add isEnabled API for ingest modules.
             */
            return ProcessResult.OK;
        }

        blackboard = Case.getCurrentCase().getServices().getBlackboard();

        if ((af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                || (af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                || (af.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.SLACK)
                || (af.getKnown() == TskData.FileKnown.KNOWN)
                || (af.isDir() == true)) {
            return ProcessResult.OK;
        }

        EamDb dbManager = EamDb.getInstance();

        // only continue if we are correlating filesType
        if (!filesType.isEnabled()) {
            return ProcessResult.OK;
        }

        // get the hash because we're going to correlate it
        String md5 = af.getMd5Hash();
        if ((md5 == null) || (HashUtility.isNoDataMd5(md5))) {
            return ProcessResult.OK;
        }

        EamArtifact eamArtifact = new EamArtifact(filesType, md5);

        // If unknown to both the hash module and as a globally known artifact in the EAM DB, correlate to other cases
        if (af.getKnown() == TskData.FileKnown.UNKNOWN) {
            // query db for artifact instances having this MD5 and knownStatus = "Bad".
            try {
                // if af.getKnown() is "UNKNOWN" and this artifact instance was marked bad in a previous case, 
                // create TSK_INTERESTING_FILE artifact on BB.
                List<String> caseDisplayNames = dbManager.getListCasesHavingArtifactInstancesKnownBad(eamArtifact);
                if (!caseDisplayNames.isEmpty()) {
                    postCorrelatedBadFileToBlackboard(af, caseDisplayNames);
                }
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error counting known bad artifacts.", ex); // NON-NLS
                return ProcessResult.ERROR;
            }
        }

        // Make a TSK_HASHSET_HIT blackboard artifact for global known bad files
        try {
            if (dbManager.isArtifactGlobalKnownBad(eamArtifact)) {
                postCorrelatedHashHitToBlackboard(af);
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error retrieving global known status.", ex); // NON-NLS
            return ProcessResult.ERROR;
        }

        try {
            EamArtifactInstance cefi = new EamArtifactInstance(
                    eamCase,
                    eamDataSource,
                    af.getParentPath() + af.getName(),
                    "",
                    TskData.FileKnown.UNKNOWN,
                    EamArtifactInstance.GlobalStatus.LOCAL
            );
            eamArtifact.addInstance(cefi);
            dbManager.prepareBulkArtifact(eamArtifact);
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error adding artifact to bulk artifacts.", ex); // NON-NLS
            return ProcessResult.ERROR;
        }

        return ProcessResult.OK;
    }

    @Override
    public void shutDown() {
        if (EamDb.isEnabled() == false) {
            /*
             * Not signaling an error for now. This is a workaround for the way
             * all newly didscovered ingest modules are automatically anabled.
             *
             * TODO (JIRA-2731): Add isEnabled API for ingest modules.
             */
            return;
        }
        
        EamDb dbManager = EamDb.getInstance();
        try {
            dbManager.bulkInsertArtifacts();
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error doing bulk insert of artifacts.", ex); // NON-NLS
        }
        try {
            Long count = dbManager.getCountArtifactInstancesByCaseDataSource(new EamArtifactInstance(eamCase, eamDataSource));
            LOGGER.log(Level.INFO, "{0} artifacts in db for case: {1} ds:{2}", new Object[]{count, eamCase.getDisplayName(), eamDataSource.getName()}); // NON-NLS
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error counting artifacts.", ex); // NON-NLS
        }

        // TODO: once we implement shared cache, if refCounter is 1, then submit data in bulk.
        refCounter.decrementAndGet(jobId);
    }

    // see ArtifactManagerTimeTester for details
    @Messages({
        "IngestModule.notfyBubble.title=Central Repository Not Initialized",
        "IngestModule.errorMessage.isNotEnabled=Central Repository settings are not initialized, cannot run Correlation Engine ingest module."
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
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

        jobId = context.getJobId();
        eamCase = new EamCase(Case.getCurrentCase().getName(), Case.getCurrentCase().getDisplayName());

        String deviceId = "";
        try {
            deviceId = Case.getCurrentCase().getSleuthkitCase().getDataSource(context.getDataSource().getId()).getDeviceId();
        } catch (TskCoreException | TskDataException ex) {
        }

        eamDataSource = new EamDataSource(deviceId, context.getDataSource().getName());

        EamDb dbManager = EamDb.getInstance();
        try {
            filesType = dbManager.getCorrelationArtifactTypeByName("FILES");
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error getting correlation type FILES in startUp.", ex); // NON-NLS
            throw new IngestModuleException("Error getting correlation type FILES in startUp.", ex); // NON-NLS
        }

        // TODO: once we implement a shared cache, load/init it here w/ syncronized and define reference counter
        // if we are the first thread / module for this job, then make sure the case
        // and image exist in the DB before we associate artifacts with it.
        if (refCounter.incrementAndGet(jobId)
                == 1) {
            // ensure we have this data source in the EAM DB
            try {
                if (null == dbManager.getDataSourceDetails(eamDataSource.getDeviceID())) {
                    dbManager.newDataSource(eamDataSource);
                }
            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error creating new data source in startUp.", ex); // NON-NLS
                throw new IngestModuleException("Error creating new data source in startUp.", ex); // NON-NLS
            }

            // ensure we have this case defined in the EAM DB
            EamCase existingCase;
            Case curCase = Case.getCurrentCase();
            EamCase curCeCase = new EamCase(
                    -1,
                    curCase.getName(), // unique case ID
                    EamOrganization.getDefault(),
                    curCase.getDisplayName(),
                    curCase.getCreatedDate(),
                    curCase.getNumber(),
                    curCase.getExaminer(),
                    "",
                    "",
                    "");
            try {
                existingCase = dbManager.getCaseDetails(curCeCase.getCaseUUID());
                if (existingCase == null) {
                    dbManager.newCase(curCeCase);
                }

            } catch (EamDbException ex) {
                LOGGER.log(Level.SEVERE, "Error creating new case in startUp.", ex); // NON-NLS
                throw new IngestModuleException("Error creating new case in startUp.", ex); // NON-NLS
            }
        }
    }

    private void postCorrelatedBadFileToBlackboard(AbstractFile abstractFile, List<String> caseDisplayNames) {

        try {
            String MODULE_NAME = IngestModuleFactory.getModuleName();
            BlackboardArtifact tifArtifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                    Bundle.IngestModule_prevcases_text());
            BlackboardAttribute att2 = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME,
                    "Previous Case: " + caseDisplayNames.stream().distinct().collect(Collectors.joining(",", "", "")));
            tifArtifact.addAttribute(att);
            tifArtifact.addAttribute(att2);

            try {
                // index the artifact for keyword search
                blackboard.indexArtifact(tifArtifact);
            } catch (Blackboard.BlackboardException ex) {
                LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
            }

            // send inbox message
            sendBadFileInboxMessage(tifArtifact, abstractFile.getName(), abstractFile.getMd5Hash());

            // fire event to notify UI of this new artifact
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
        }
    }

    private void postCorrelatedHashHitToBlackboard(AbstractFile abstractFile) {
        try {
            String MODULE_NAME = IngestModuleFactory.getModuleName();
            BlackboardArtifact tifArtifact = abstractFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT);
            BlackboardAttribute att = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME,
                    Bundle.IngestModule_prevcases_text());
            tifArtifact.addAttribute(att);

            try {
                // index the artifact for keyword search
                blackboard.indexArtifact(tifArtifact);
            } catch (Blackboard.BlackboardException ex) {
                LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + tifArtifact.getArtifactID(), ex); //NON-NLS
            }

            // send inbox message
            sendBadFileInboxMessage(tifArtifact, abstractFile.getName(), abstractFile.getMd5Hash());

            // fire event to notify UI of this new artifact
            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT));
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardArtifact.", ex); // NON-NLS
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create BlackboardAttribute.", ex); // NON-NLS
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
        "# {0} - Name of file that is Known Bad",
        "IngestModule.postToBB.knownBadMsg=Known Bad: {0}"})
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
