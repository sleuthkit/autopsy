/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Data source module to detect encryption.
 */
final class EncryptionDetectionDataSourceIngestModule implements DataSourceIngestModule {

    private static final int BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE = 0x54;
    private static final byte[] BITLOCKER_SIGNATURE_BYTES = {'-', 'F', 'V', 'E', '-', 'F', 'S', '-'};
    private static final int BITLOCKER_ADDRESS_SIGNATURE = 0x3;
    private static final int BITLOCKER_ADDRESS_SECTORS_PER_CLUSTER = 0xD;
    private static final int BITLOCKER_ADDRESS_RESERVED_CLUSTERS = 0xE;
    private static final int BITLOCKER_ADDRESS_FAT_COUNT = 0x10;
    private static final int BITLOCKER_ADDRESS_ROOT_ENTRIES = 0x11;
    private static final int BITLOCKER_ADDRESS_SECTORS = 0x13;
    private static final int BITLOCKER_ADDRESS_SECTORS_PER_FAT = 0x16;
    private static final int BITLOCKER_ADDRESS_LARGE_SECTORS = 0x20;

    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(EncryptionDetectionModuleFactory.getModuleName());
    private Blackboard blackboard;
    private double calculatedEntropy;
    private final double minimumEntropy;

    /**
     * Create an EncryptionDetectionDataSourceIngestModule object that will
     * detect volumes that are encrypted and create blackboard artifacts as
     * appropriate. The supplied EncryptionDetectionIngestJobSettings object is
     * used to configure the module.
     */
    EncryptionDetectionDataSourceIngestModule(EncryptionDetectionIngestJobSettings settings) {
        minimumEntropy = settings.getMinimumEntropy();
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        try {
            validateSettings();
            blackboard = Case.getOpenCase().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {

        try {
            if (dataSource instanceof Image) {
                List<VolumeSystem> volumeSystems = ((Image) dataSource).getVolumeSystems();
                for (VolumeSystem volumeSystem : volumeSystems) {
                    for (Volume volume : volumeSystem.getVolumes()) {
                        if (isBitlockerVolume(volume)) {
                            return flagVolume(volume, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);
                        }
                        if (isVolumeEncrypted(volume)) {
                            return flagVolume(volume, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED);
                        }
                    }
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
     * @param artifactType The type of artifact to create.
     *
     * @return 'OK' if the volume was processed successfully, or 'ERROR' if
     *         there was a problem.
     */
    private IngestModule.ProcessResult flagVolume(Volume volume, BlackboardArtifact.ARTIFACT_TYPE artifactType) {
        try {
            BlackboardArtifact artifact = volume.newArtifact(artifactType);

            try {
                /*
                 * Index the artifact for keyword search.
                 */
                blackboard.indexArtifact(artifact);
            } catch (Blackboard.BlackboardException ex) {
                logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
            }

            /*
             * Send an event to update the view with the new result.
             */
            services.fireModuleDataEvent(new ModuleDataEvent(EncryptionDetectionModuleFactory.getModuleName(), artifactType, Collections.singletonList(artifact)));

            /*
             * Make an ingest inbox message.
             */
            StringBuilder detailsSb = new StringBuilder("");
            detailsSb.append("File: ").append(volume.getParent().getUniquePath()).append(volume.getName());
            if (artifactType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED)) {
                detailsSb.append("<br/>\n").append("Entropy: ").append(calculatedEntropy);
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
            calculatedEntropy = EncryptionDetectionTools.calculateEntropy(volume);
            if (calculatedEntropy >= minimumEntropy) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method checks if the Volume input has been encrypted with Bitlocker.
     *
     * @param volume Volume to be checked.
     *
     * @return True if the Volume has been encrypted with Bitlocker.
     * 
     * @throws ReadContentInputStreamException If there is a failure reading
     *                                         from the InputStream.
     * @throws IOException                     If there is a failure closing or
     *                                         reading from the InputStream.
     */
    private boolean isBitlockerVolume(Volume volume) throws ReadContentInputStream.ReadContentInputStreamException, IOException {
        /*
         * Logic in this method is based on
         * https://blogs.msdn.microsoft.com/si_team/2006/10/26/detecting-bitlocker/
         */

        boolean bitlockerVolume = false;

        InputStream in = null;
        BufferedInputStream bin = null;

        try {
            in = new ReadContentInputStream(volume);
            bin = new BufferedInputStream(in);

            byte[] bpbArray = new byte[BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE];
            bin.read(bpbArray, 0, BITLOCKER_BIOS_PARAMETER_BLOCK_SIZE);

            boolean signatureMatches = true;
            for (int i = 0; i < BITLOCKER_SIGNATURE_BYTES.length; i++) {
                if (bpbArray[BITLOCKER_ADDRESS_SIGNATURE + i] != BITLOCKER_SIGNATURE_BYTES[i]) {
                    signatureMatches = false;
                    break;
                }
            }

            if (signatureMatches) {
                switch ((int) bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_CLUSTER]) {
                    case 0x01:
                    case 0x02:
                    case 0x04:
                    case 0x08:
                    case 0x10:
                    case 0x20:
                    case 0x40:
                    case 0x80:
                        short reservedClusters
                                = (short) ((bpbArray[BITLOCKER_ADDRESS_RESERVED_CLUSTERS] << 8)
                                | (bpbArray[BITLOCKER_ADDRESS_RESERVED_CLUSTERS + 1] & 0xFF));
                        byte fatCount
                                = bpbArray[BITLOCKER_ADDRESS_FAT_COUNT];
                        short rootEntries
                                = (short) ((bpbArray[BITLOCKER_ADDRESS_ROOT_ENTRIES] << 8)
                                | (bpbArray[BITLOCKER_ADDRESS_ROOT_ENTRIES + 1] & 0xFF));
                        short sectors
                                = (short) ((bpbArray[BITLOCKER_ADDRESS_SECTORS] << 8)
                                | (bpbArray[BITLOCKER_ADDRESS_SECTORS + 1] & 0xFF));
                        short sectorsPerFat
                                = (short) ((bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_FAT] << 8)
                                | (bpbArray[BITLOCKER_ADDRESS_SECTORS_PER_FAT + 1] & 0xFF));
                        int largeSectors
                                = ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS] << 24)
                                | ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 1] & 0xFF) << 16)
                                | ((bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 2] & 0xFF) << 8)
                                | (bpbArray[BITLOCKER_ADDRESS_LARGE_SECTORS + 3] & 0xFF));

                        if (reservedClusters == 0 && fatCount == 0 && rootEntries == 0
                                && sectors == 0 && sectorsPerFat == 0 && largeSectors == 0) {
                            bitlockerVolume = true;
                        }
                }
            }

            return bitlockerVolume;

        } finally {
            if (in != null) {
                in.close();
            }
            if (bin != null) {
                bin.close();
            }
        }
    }
}
