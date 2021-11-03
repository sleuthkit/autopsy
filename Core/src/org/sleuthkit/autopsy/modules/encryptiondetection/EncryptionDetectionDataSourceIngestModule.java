/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.modules.encryptiondetection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Data source module to detect encryption.
 */
final class EncryptionDetectionDataSourceIngestModule implements DataSourceIngestModule {

    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(EncryptionDetectionModuleFactory.getModuleName());
    private Blackboard blackboard;
    private double calculatedEntropy;
    private final double minimumEntropy;
    private IngestJobContext context;

    /**
     * Create an EncryptionDetectionDataSourceIngestModule object that will
     * detect volumes that are encrypted and create blackboard artifacts as
     * appropriate.
     *
     * @param settings The Settings used to configure the module.
     */
    EncryptionDetectionDataSourceIngestModule(EncryptionDetectionIngestJobSettings settings) {
        minimumEntropy = settings.getMinimumEntropy();
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        validateSettings();
        blackboard = Case.getCurrentCase().getSleuthkitCase().getBlackboard();
        this.context = context;
    }

    @Messages({
        "EncryptionDetectionDataSourceIngestModule.artifactComment.bitlocker=Bitlocker encryption detected.",
        "EncryptionDetectionDataSourceIngestModule.artifactComment.suspected=Suspected encryption due to high entropy (%f).",
        "EncryptionDetectionDataSourceIngestModule.processing.message=Checking image for encryption."
    })
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        try {
            if (dataSource instanceof Image) {

                if (((Image) dataSource).getPaths().length == 0) {
                    logger.log(Level.SEVERE, String.format("Unable to process data source '%s' - image has no paths", dataSource.getName()));
                    return IngestModule.ProcessResult.ERROR;
                }

                List<VolumeSystem> volumeSystems = ((Image) dataSource).getVolumeSystems();
                progressBar.switchToDeterminate(volumeSystems.size());
                int numVolSystemsChecked = 0;
                progressBar.progress(Bundle.EncryptionDetectionDataSourceIngestModule_processing_message(), 0);
                for (VolumeSystem volumeSystem : volumeSystems) {

                    if (context.dataSourceIngestIsCancelled()) {
                        return ProcessResult.OK;
                    }

                    for (Volume volume : volumeSystem.getVolumes()) {

                        if (context.dataSourceIngestIsCancelled()) {
                            return ProcessResult.OK;
                        }
                        if (BitlockerDetection.isBitlockerVolume(volume)) {
                            return flagVolume(volume, BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, Score.SCORE_NOTABLE, 
                                    Bundle.EncryptionDetectionDataSourceIngestModule_artifactComment_bitlocker());
                        }

                        if (context.dataSourceIngestIsCancelled()) {
                            return ProcessResult.OK;
                        }
                        if (isVolumeEncrypted(volume)) {
                            return flagVolume(volume, BlackboardArtifact.Type.TSK_ENCRYPTION_SUSPECTED, Score.SCORE_LIKELY_NOTABLE,
                                    String.format(Bundle.EncryptionDetectionDataSourceIngestModule_artifactComment_suspected(), calculatedEntropy));
                        }
                    }
                    // Update progress bar
                    numVolSystemsChecked++;
                    progressBar.progress(Bundle.EncryptionDetectionDataSourceIngestModule_processing_message(), numVolSystemsChecked);
                }
            }
        } catch (ReadContentInputStream.ReadContentInputStreamException ex) {
            logger.log(Level.WARNING, String.format("Unable to read data source '%s'", dataSource.getName()), ex);
            return IngestModule.ProcessResult.ERROR;
        } catch (IOException | TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Unable to process data source '%s'", dataSource.getName()), ex);
            return IngestModule.ProcessResult.ERROR;
        }

        return IngestModule.ProcessResult.OK;
    }

    /**
     * Validate the relevant settings for the
     * EncryptionDetectionDataSourceIngestModule
     *
     * @throws IngestModule.IngestModuleException If the input is empty,
     *                                            invalid, or out of range.
     *
     */
    private void validateSettings() throws IngestModule.IngestModuleException {
        EncryptionDetectionTools.validateMinEntropyValue(minimumEntropy);
    }

    /**
     * Create a blackboard artifact.
     *
     * @param volume       The volume to be processed.
     * @param artifactType The type of artifact to create. This is assumed to be
     *                     an analysis result type.
     * @param score        The score of the analysis result.
     * @param comment      A comment to be attached to the artifact.
     *
     * @return 'OK' if the volume was processed successfully, or 'ERROR' if
     *         there was a problem.
     */
    private IngestModule.ProcessResult flagVolume(Volume volume, BlackboardArtifact.Type artifactType, Score score, String comment) {

        if (context.dataSourceIngestIsCancelled()) {
            return ProcessResult.OK;
        }

        try {
            BlackboardArtifact artifact = volume.newAnalysisResult(artifactType, score, null, null, comment, 
                    Arrays.asList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, EncryptionDetectionModuleFactory.getModuleName(), comment)))
                    .getAnalysisResult();
            
            try {
                /*
                 * post the artifact which will index the artifact for keyword
                 * search, and fire an event to notify UI of this new artifact
                 */
                blackboard.postArtifact(artifact, EncryptionDetectionModuleFactory.getModuleName(), context.getJobId());
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
            }

            /*
             * Make an ingest inbox message.
             */
            StringBuilder detailsSb = new StringBuilder("");
            detailsSb.append("File: ");
            Content parentFile = volume.getParent();
            if (parentFile != null) {
                detailsSb.append(volume.getParent().getUniquePath());
            }
            detailsSb.append(volume.getName());
            if (artifactType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED)) {
                detailsSb.append("<br/>\nEntropy: ").append(calculatedEntropy);
            }

            services.postMessage(IngestMessage.createDataMessage(EncryptionDetectionModuleFactory.getModuleName(),
                    artifactType.getDisplayName() + " Match: " + volume.getName(),
                    detailsSb.toString(),
                    volume.getName(),
                    artifact));

            return IngestModule.ProcessResult.OK;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to create blackboard artifact for '%s'.", volume.getName()), ex); //NON-NLS
            return IngestModule.ProcessResult.ERROR;
        }
    }

    /**
     * This method checks if the Volume input is encrypted. Initial
     * qualifications require that the Volume not have a file system.
     *
     * @param volume Volume to be checked.
     *
     * @return True if the Volume is encrypted.
     */
    private boolean isVolumeEncrypted(Volume volume) throws ReadContentInputStream.ReadContentInputStreamException, IOException, TskCoreException {
        /*
         * Criteria for the checks in this method are partially based on
         * http://www.forensicswiki.org/wiki/TrueCrypt#Detection
         */
        if (volume.getFileSystems().isEmpty()) {
            calculatedEntropy = EncryptionDetectionTools.calculateEntropy(volume, context);
            if (calculatedEntropy >= minimumEntropy) {
                return true;
            }
        }
        return false;
    }
}
