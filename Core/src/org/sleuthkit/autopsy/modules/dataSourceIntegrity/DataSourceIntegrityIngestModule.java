/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.dataSourceIntegrity;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Data source ingest module that verifies the integrity of an Expert Witness
 * Format (EWF) E01 image file by generating a hash of the file and comparing it
 * to the value stored in the image. Will also generate hashes for any
 * image-type data source that has none.
 */
public class DataSourceIntegrityIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(DataSourceIntegrityIngestModule.class.getName());
    private static final long DEFAULT_CHUNK_SIZE = 32 * 1024;
    private static final IngestServices services = IngestServices.getInstance();

    private final boolean computeHashes;
    private final boolean verifyHashes;

    private final List<HashData> hashDataList = new ArrayList<>();

    private IngestJobContext context;

    DataSourceIntegrityIngestModule(DataSourceIntegrityIngestSettings settings) {
        computeHashes = settings.shouldComputeHashes();
        verifyHashes = settings.shouldVerifyHashes();
    }

    @NbBundle.Messages({
        "DataSourceIntegrityIngestModule.startup.noCheckboxesSelected=At least one of the checkboxes must be selected"
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        // It's an error if the module is run without either option selected
        if (!(computeHashes || verifyHashes)) {
            throw new IngestModuleException(Bundle.DataSourceIntegrityIngestModule_startup_noCheckboxesSelected());
        }
    }

    @NbBundle.Messages({
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.skipCompute=Not computing new hashes for {0} since the option was disabled",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.skipVerify=Not verifying existing hashes for {0} since the option was disabled",
        "# {0} - hashName",
        "DataSourceIntegrityIngestModule.process.hashAlgorithmError=Error creating message digest for {0} algorithm",
        "# {0} - hashName",
        "DataSourceIntegrityIngestModule.process.hashMatch=<li>{0} hash verified </li>",
        "# {0} - hashName",
        "DataSourceIntegrityIngestModule.process.hashNonMatch=<li>{0} hash not verified </li>",
        "# {0} - calculatedHashValue",
        "# {1} - storedHashValue",
        "DataSourceIntegrityIngestModule.process.hashList=<ul><li>Calculated hash: {0} </li><li>Stored hash: {1} </li></ul>",
        "# {0} - hashName",
        "# {1} - calculatedHashValue",
        "DataSourceIntegrityIngestModule.process.calcHashWithType=<li>Calculated {0} hash: {1} </li>",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.calculateHashDone=<p>Data Source Hash Calculation Results for {0} </p>",
        "DataSourceIntegrityIngestModule.process.hashesCalculated= hashes calculated",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.errorSavingHashes= Error saving hashes for image {0} to the database",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.errorLoadingHashes= Error loading hashes for image {0} from the database",
        "# {0} - hashAlgorithm",
        "# {1} - calculatedHashValue",
        "# {2} - storedHashValue",
        "DataSourceIntegrityIngestModule.process.hashFailedForArtifact={0} hash verification failed:\n  Calculated hash: {1}\n  Stored hash: {2}\n",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.verificationSuccess=Integrity of {0} verified",
        "# {0} - imageName",
        "DataSourceIntegrityIngestModule.process.verificationFailure={0} failed integrity verification",})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        String imgName = dataSource.getName();

        // Skip non-images
        if (!(dataSource instanceof Image)) {
            logger.log(Level.INFO, "Skipping non-image {0}", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, DataSourceIntegrityModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "DataSourceIntegrityIngestModule.process.skipNonEwf",
                            imgName)));
            return ProcessResult.OK;
        }
        Image img = (Image) dataSource;

        // Get the image size. Log a warning if it is zero.
        long size = img.getSize();
        if (size == 0) {
            logger.log(Level.WARNING, "Size of image {0} was 0 when queried.", imgName); //NON-NLS
        }

        // Determine which mode we're in. 
        // - If there are any preset hashes, then we'll verify them (assuming the verify checkbox is selected)
        // - Otherwise we'll calculate and store all three hashes (assuming the compute checkbox is selected)
        // First get a list of all stored hash types
        try {
            if (img.getMd5() != null && !img.getMd5().isEmpty()) {
                hashDataList.add(new HashData(HashType.MD5, img.getMd5()));
            }
            if (img.getSha1() != null && !img.getSha1().isEmpty()) {
                hashDataList.add(new HashData(HashType.SHA1, img.getSha1()));
            }
            if (img.getSha256() != null && !img.getSha256().isEmpty()) {
                hashDataList.add(new HashData(HashType.SHA256, img.getSha256()));
            }
        } catch (TskCoreException ex) {
            String msg = Bundle.DataSourceIntegrityIngestModule_process_errorLoadingHashes(imgName);
            services.postMessage(IngestMessage.createMessage(MessageType.ERROR, DataSourceIntegrityModuleFactory.getModuleName(), msg));
            logger.log(Level.SEVERE, msg, ex);
            return ProcessResult.ERROR;
        }

        // Figure out which mode we should be in
        Mode mode;
        if (hashDataList.isEmpty()) {
            mode = Mode.COMPUTE;
        } else {
            mode = Mode.VERIFY;
        }

        // If that mode was not enabled by the user, exit
        if (mode.equals(Mode.COMPUTE) && !this.computeHashes) {
            logger.log(Level.INFO, "Not computing hashes for {0} since the option was disabled", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, DataSourceIntegrityModuleFactory.getModuleName(),
                    Bundle.DataSourceIntegrityIngestModule_process_skipCompute(imgName)));
            return ProcessResult.OK;
        } else if (mode.equals(Mode.VERIFY) && !this.verifyHashes) {
            logger.log(Level.INFO, "Not verifying hashes for {0} since the option was disabled", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, DataSourceIntegrityModuleFactory.getModuleName(),
                    Bundle.DataSourceIntegrityIngestModule_process_skipVerify(imgName)));
            return ProcessResult.OK;
        }

        // If we're in compute mode (i.e., the hash list is empty), add all hash algorithms
        // to the list.
        if (mode.equals(Mode.COMPUTE)) {
            for (HashType type : HashType.values()) {
                hashDataList.add(new HashData(type, ""));
            }
        }

        // Set up the digests
        for (HashData hashData : hashDataList) {
            try {
                hashData.digest = MessageDigest.getInstance(hashData.type.getName());
            } catch (NoSuchAlgorithmException ex) {
                String msg = Bundle.DataSourceIntegrityIngestModule_process_hashAlgorithmError(hashData.type.getName());
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, DataSourceIntegrityModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, msg, ex);
                return ProcessResult.ERROR;
            }
        }

        // Libewf uses a chunk size of 64 times the sector size, which is the
        // motivation for using it here. For other images it shouldn't matter,
        // so they can use this chunk size as well.
        long chunkSize = 64 * img.getSsize();
        chunkSize = (chunkSize == 0) ? DEFAULT_CHUNK_SIZE : chunkSize;

        // Casting to double to capture decimals
        int totalChunks = (int) Math.ceil((double) size / (double) chunkSize);
        logger.log(Level.INFO, "Total chunks = {0}", totalChunks); //NON-NLS

        if (mode.equals(Mode.VERIFY)) {
            logger.log(Level.INFO, "Starting hash verification of {0}", img.getName()); //NON-NLS
        } else {
            logger.log(Level.INFO, "Starting hash calculation for {0}", img.getName()); //NON-NLS
        }
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, DataSourceIntegrityModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "DataSourceIntegrityIngestModule.process.startingImg",
                        imgName)));

        // Set up the progress bar
        statusHelper.switchToDeterminate(totalChunks);

        // Read in byte size chunks and update the hash value with the data.
        byte[] data = new byte[(int) chunkSize];
        int read;
        for (int i = 0; i < totalChunks; i++) {
            if (context.dataSourceIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            try {
                read = img.read(data, i * chunkSize, chunkSize);
            } catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                        "DataSourceIntegrityIngestModule.process.errReadImgAtChunk", imgName, i);
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, DataSourceIntegrityModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, msg, ex);
                return ProcessResult.ERROR;
            }

            // Only update with the read bytes.
            if (read == chunkSize) {
                for (HashData struct : hashDataList) {
                    struct.digest.update(data);
                }
            } else {
                byte[] subData = Arrays.copyOfRange(data, 0, read);
                for (HashData struct : hashDataList) {
                    struct.digest.update(subData);
                }
            }
            statusHelper.progress(i);
        }

        // Produce the final hashes
        for(HashData hashData: hashDataList) {
            hashData.calculatedHash = Hex.encodeHexString(hashData.digest.digest()).toLowerCase();
            logger.log(Level.INFO, "Hash calculated from {0}: {1}", new Object[]{imgName, hashData.calculatedHash}); //NON-NLS
        }

        if (mode.equals(Mode.VERIFY)) {
            // Check that each hash matches
            boolean verified = true;
            String detailedResults = NbBundle
                    .getMessage(this.getClass(), "DataSourceIntegrityIngestModule.shutDown.verifyResultsHeader", imgName);
            String hashResults = "";
            String artifactComment = "";

            for (HashData hashData : hashDataList) {
                if (hashData.storedHash.equals(hashData.calculatedHash)) {
                    hashResults += Bundle.DataSourceIntegrityIngestModule_process_hashMatch(hashData.type.name) + " ";
                } else {
                    verified = false;
                    hashResults += Bundle.DataSourceIntegrityIngestModule_process_hashNonMatch(hashData.type.name) + " ";
                    artifactComment += Bundle.DataSourceIntegrityIngestModule_process_hashFailedForArtifact(hashData.type.name,
                            hashData.calculatedHash, hashData.storedHash) + " ";
                }
                hashResults += Bundle.DataSourceIntegrityIngestModule_process_hashList(hashData.calculatedHash, hashData.storedHash);
            }

            String verificationResultStr;
            String messageResultStr;
            MessageType messageType;
            if (verified) {
                messageType = MessageType.INFO;
                verificationResultStr = NbBundle.getMessage(this.getClass(), "DataSourceIntegrityIngestModule.shutDown.verified");
                messageResultStr = Bundle.DataSourceIntegrityIngestModule_process_verificationSuccess(imgName);
            } else {
                messageType = MessageType.WARNING;
                verificationResultStr = NbBundle.getMessage(this.getClass(), "DataSourceIntegrityIngestModule.shutDown.notVerified");
                messageResultStr = Bundle.DataSourceIntegrityIngestModule_process_verificationFailure(imgName);
            }

            detailedResults += NbBundle.getMessage(this.getClass(), "DataSourceIntegrityIngestModule.shutDown.resultLi", verificationResultStr);
            detailedResults += hashResults;

            if (!verified) {
                try {
                    BlackboardArtifact verificationFailedArtifact = Case.getCurrentCase().getSleuthkitCase().getBlackboard().newAnalysisResult(
                            BlackboardArtifact.Type.TSK_VERIFICATION_FAILED,
                            img.getId(), img.getId(),
                            Score.SCORE_NOTABLE,
                            null, null, artifactComment,
                            Arrays.asList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT,
                                    DataSourceIntegrityModuleFactory.getModuleName(), artifactComment)))
                            .getAnalysisResult();

                    Case.getCurrentCase().getServices().getArtifactsBlackboard()
                            .postArtifact(verificationFailedArtifact, DataSourceIntegrityModuleFactory.getModuleName(), context.getJobId());
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Error creating verification failed artifact", ex);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Error posting verification failed artifact", ex);
                }
            }

            services.postMessage(IngestMessage.createMessage(messageType, DataSourceIntegrityModuleFactory.getModuleName(),
                    messageResultStr, detailedResults));

        } else {
            // Store the hashes in the database and update the image
            try {
                String results = Bundle.DataSourceIntegrityIngestModule_process_calculateHashDone(imgName);

                for (HashData hashData : hashDataList) {
                    switch (hashData.type) {
                        case MD5:
                            try {
                            img.setMD5(hashData.calculatedHash);
                        } catch (TskDataException ex) {
                            logger.log(Level.SEVERE, "Error setting calculated hash", ex);
                        }
                        break;
                        case SHA1:
                            try {
                            img.setSha1(hashData.calculatedHash);
                        } catch (TskDataException ex) {
                            logger.log(Level.SEVERE, "Error setting calculated hash", ex);
                        }
                        break;
                        case SHA256:
                            try {
                            img.setSha256(hashData.calculatedHash);
                        } catch (TskDataException ex) {
                            logger.log(Level.SEVERE, "Error setting calculated hash", ex);
                        }
                        break;
                        default:
                            break;
                    }
                    results += Bundle.DataSourceIntegrityIngestModule_process_calcHashWithType(hashData.type.name, hashData.calculatedHash);
                }

                // Write the inbox message
                services.postMessage(IngestMessage.createMessage(MessageType.INFO, DataSourceIntegrityModuleFactory.getModuleName(),
                        imgName + Bundle.DataSourceIntegrityIngestModule_process_hashesCalculated(), results));

            } catch (TskCoreException ex) {
                String msg = Bundle.DataSourceIntegrityIngestModule_process_errorSavingHashes(imgName);
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, DataSourceIntegrityModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, "Error saving hash for image " + imgName + " to database", ex);
                return ProcessResult.ERROR;
            }
        }

        return ProcessResult.OK;
    }

    /**
     * Enum to track whether we're in computer or verify mode
     */
    private enum Mode {
        COMPUTE,
        VERIFY;
    }

    /**
     * Enum to hold the type of hash. The value in the "name" field should be
     * compatible with MessageDigest
     */
    private enum HashType {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256");

        private final String name; // This should be the string expected by MessageDigest

        HashType(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    /**
     * Utility class to hold data for a specific hash algorithm.
     */
    private class HashData {

        private HashType type;
        private MessageDigest digest;
        private String storedHash;
        private String calculatedHash;

        HashData(HashType type, String storedHash) {
            this.type = type;
            this.storedHash = storedHash;
        }
    }
}
