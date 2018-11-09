/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2014 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.e01verify;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.xml.bind.DatatypeConverter;
//import org.python.bouncycastle.util.Arrays;
import java.util.Arrays;
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
import org.sleuthkit.datamodel.TskData;
import org.openide.util.NbBundle;

/**
 * Data source ingest module that verifies the integrity of an Expert Witness
 * Format (EWF) E01 image file by generating a hash of the file and comparing it
 * to the value stored in the image.
 */
@NbBundle.Messages({
    "UnableToCalculateHashes=Unable to calculate MD5 hashes."
})
public class E01VerifyIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(E01VerifyIngestModule.class.getName());
    private static final long DEFAULT_CHUNK_SIZE = 32 * 1024;
    private static final IngestServices services = IngestServices.getInstance();

    private final boolean computeHashes;
    private final boolean verifyHashes;
    
    //private final List<MessageDigest> messageDigests = new ArrayList<>();
    private final List<HashStruct> hashInfo = new ArrayList<>();
    
    private boolean verified = false;
    private String calculatedHash = "";
    private String storedHash = "";
    private IngestJobContext context;
    
    E01VerifyIngestModule(IngestSettings settings) {
        computeHashes = settings.shouldComputeHashes();
        verifyHashes = settings.shouldVerifyHashes();
    }

    @NbBundle.Messages({
        "E01VerifyIngestModule.startup.noCheckboxesSelected=At least one of the checkboxes must be selected"
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        verified = false;
        storedHash = "";
        calculatedHash = "";
        
        // It's an error if the module is run without either option selected
        if (!(computeHashes || verifyHashes)) {
            throw new IngestModuleException(Bundle.E01VerifyIngestModule_startup_noCheckboxesSelected());
        }
    }
    
    // MOVE AND RENAME THIS
    private class HashStruct {
        HashType type;
        MessageDigest digest;
        String storedHash;
        String calculatedHash;
        
        HashStruct(HashType type, String storedHash) {
            this.type = type;
            this.storedHash = storedHash;
        }
        
        void setMessageDigest(MessageDigest digest) {
            this.digest = digest;
        }
    }
    
    @NbBundle.Messages({
        "# {0} - imageName",
        "E01VerifyIngestModule.process.skipCompute=Not computing new hashes for {0} since the option was disabled",
        "# {0} - imageName",
        "E01VerifyIngestModule.process.skipVerify=Not verifying existing hashes for {0} since the option was disabled",
        "# {0} - hashName",
        "E01VerifyIngestModule.process.hashAlgorithmError=Error creating message digest for {0} algorithm",
        "# {0} - hashName",
        "E01VerifyIngestModule.process.hashMatch=<li>{0} hash verified</li>",
        "# {0} - hashName",
        "E01VerifyIngestModule.process.hashNonMatch=<li>{0} hash not verified</li>",
        "# {0} - calculatedHashValue",
        "E01VerifyIngestModule.process.calculatedHash=<li>   Calculated hash: {0}</li>",
        "# {0} - storedHashValue",
        "E01VerifyIngestModule.process.storedHash=<li>   Stored hash: {0}</li>",
        "E01VerifyIngestModule.process.listTest=<li>Level 1</li><ul><li>Level 2 a</li><li>Level 2b</li></ul><li> Level 1 again</li>",
        "# {0} - calculatedHashValue",
        "# {1} - storedHashValue",
        "E01VerifyIngestModule.process.hashList=<ul><li>Calculated hash: {0}</li><li>Stored hash: {1}</li></ul>",
    })
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        String imgName = dataSource.getName();

        // Skip non-images
        if (!(dataSource instanceof Image)) {
            logger.log(Level.INFO, "Skipping non-image {0}", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.skipNonEwf",
                            imgName)));
            return ProcessResult.OK;
        }
        Image img = (Image) dataSource;

        // Make sure the image size we have is not zero
        long size = img.getSize();
        if (size == 0) {
            logger.log(Level.WARNING, "Size of image {0} was 0 when queried.", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.errGetSizeOfImg",
                            imgName)));
        }
        
        // Determine which mode we're in. 
        // - If there are any preset hashes, then we'll verify them (assuming the verify checkbox is selected)
        // - Otherwise we'll calculate and store all three hashes (assuming the compute checkbox is selected)
        
        // First get a list of all stored hash types
        //List<HashType> hashesToCalculate = new ArrayList<>();
        if (img.getMd5() != null && ! img.getMd5().isEmpty()) {
            //hashesToCalculate.add(HashType.MD5);
            hashInfo.add(new HashStruct(HashType.MD5, img.getMd5()));
        }
        if (img.getSha1() != null && ! img.getSha1().isEmpty()) {
            //hashesToCalculate.add(HashType.SHA1);
            hashInfo.add(new HashStruct(HashType.SHA1, img.getSha1()));
        }
        if (img.getSha256() != null && ! img.getSha256().isEmpty()) {
            //hashesToCalculate.add(HashType.SHA256);
            hashInfo.add(new HashStruct(HashType.SHA256, img.getSha256()));
        }
        
        // Figure out which mode we should be in
        Mode mode;
        //if (hashesToCalculate.isEmpty()) {
        if (hashInfo.isEmpty()) {
            mode = Mode.COMPUTE;
        } else {
            mode = Mode.VERIFY;
        }
        
        // If that mode was not enabled by the user, exit
        if (mode.equals(Mode.COMPUTE) && ! this.computeHashes) {
            logger.log(Level.INFO, "Not computing hashes for {0} since the option was disabled", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                    Bundle.E01VerifyIngestModule_process_skipCompute(imgName)));
            return ProcessResult.OK;
        } else if (mode.equals(Mode.VERIFY) && ! this.verifyHashes) {
            logger.log(Level.INFO, "Not verifying hashes for {0} since the option was disabled", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                    Bundle.E01VerifyIngestModule_process_skipVerify(imgName)));
            return ProcessResult.OK;
        }
        
        // If we're in compute mode (i.e., the hash list is empty), add all hash algorithms
        // to the list.
        if (mode.equals(Mode.COMPUTE)) {
            //hashesToCalculate.addAll(Arrays.asList(HashType.values()));
            for(HashType type : HashType.values()) {
                hashInfo.add(new HashStruct(type, ""));
            }
        }
        
        // Set up the digests
        //for (HashType hashType:hashesToCalculate) {
        for (HashStruct struct:hashInfo) {
            try {
                //messageDigests.add(MessageDigest.getInstance(hashType.getName())); //NON-NLS
                struct.setMessageDigest(MessageDigest.getInstance(struct.type.getName()));
            } catch (NoSuchAlgorithmException ex) {
                String msg = Bundle.E01VerifyIngestModule_process_hashAlgorithmError(struct.type.getName());
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(), msg));
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
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
        NbBundle.getMessage(this.getClass(),
                "EwfVerifyIngestModule.process.startingImg",
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
                        "EwfVerifyIngestModule.process.errReadImgAtChunk", imgName, i);
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, msg, ex);
                return ProcessResult.ERROR;
            }

            // Only update with the read bytes.
            if (read == chunkSize) {
                for (HashStruct struct:hashInfo) {
                    struct.digest.update(data);
                }
            } else {
                byte[] subData = Arrays.copyOfRange(data, 0, read);
                for (HashStruct struct:hashInfo) {
                    struct.digest.update(subData);
                }
            }
            statusHelper.progress(i);
        }
        
        // Produce the final hashes
        for(HashStruct struct:hashInfo) {
            struct.calculatedHash = DatatypeConverter.printHexBinary(struct.digest.digest()).toLowerCase();
            logger.log(Level.INFO, "Hash calculated from {0}: {1}", new Object[]{imgName, struct.calculatedHash}); //NON-NLS
        }
        
        if (mode.equals(Mode.VERIFY)) {
            // Check that each hash matches
            boolean verified = true;
            String detailedResults = NbBundle
                .getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.verifyResultsHeader", imgName);
            String hashResults = "";
            
            for (HashStruct struct:hashInfo) {
                if (struct.storedHash.equals(struct.calculatedHash)) {
                    hashResults += Bundle.E01VerifyIngestModule_process_hashMatch(struct.type.name);
                } else {
                    verified = false;
                    hashResults += Bundle.E01VerifyIngestModule_process_hashNonMatch(struct.type.name);
                }
                hashResults += Bundle.E01VerifyIngestModule_process_hashList(struct.calculatedHash, struct.storedHash);
                //hashResults += Bundle.E01VerifyIngestModule_process_hashList(struct.calculatedHash, struct.storedHash);
                //hashResults += Bundle.E01VerifyIngestModule_process_calculatedHash(struct.calculatedHash);
                //hashResults += Bundle.E01VerifyIngestModule_process_storedHash(struct.storedHash);
                //hashResults += Bundle.E01VerifyIngestModule_process_listTest();
            }
            
            String verificationResultStr;
            if (verified) {
                verificationResultStr = NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.verified");
            } else {
                verificationResultStr = NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.notVerified");
            }
            
            detailedResults += NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.resultLi", verificationResultStr);
            detailedResults += hashResults;

            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(), imgName + verificationResultStr, detailedResults));
        
        } else {
            // Store the hashes in the database
            // TO DO
        }
        
        return ProcessResult.OK;
    }

    public ProcessResult process2(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        String imgName = dataSource.getName();

        // Skip non-images
        if (!(dataSource instanceof Image)) {
            logger.log(Level.INFO, "Skipping non-image {0}", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.skipNonEwf",
                            imgName)));
            return ProcessResult.OK;
        }
        Image img = (Image) dataSource;

        // Skip images that are not E01
        if (img.getType() != TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_EWF_EWF) {
            logger.log(Level.INFO, "Skipping non-ewf image {0}", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.skipNonEwf",
                            imgName)));
            return ProcessResult.OK;
        }

        // Report an error for null or empty MD5
        if ((img.getMd5() == null) || img.getMd5().isEmpty()) {
            services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.noStoredHash",
                            imgName)));
            return ProcessResult.ERROR;
        }

        storedHash = img.getMd5().toLowerCase();
        logger.log(Level.INFO, "Hash value stored in {0}: {1}", new Object[]{imgName, storedHash}); //NON-NLS

        logger.log(Level.INFO, "Starting hash verification of {0}", img.getName()); //NON-NLS
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                        "EwfVerifyIngestModule.process.startingImg",
                        imgName)));

        long size = img.getSize();
        if (size == 0) {
            logger.log(Level.WARNING, "Size of image {0} was 0 when queried.", imgName); //NON-NLS
            services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                            "EwfVerifyIngestModule.process.errGetSizeOfImg",
                            imgName)));
        }

        // Libewf uses a sector size of 64 times the sector size, which is the
        // motivation for using it here.
        long chunkSize = 64 * img.getSsize();
        chunkSize = (chunkSize == 0) ? DEFAULT_CHUNK_SIZE : chunkSize;

        // Casting to double to capture decimals
        int totalChunks = (int) Math.ceil((double) size / (double) chunkSize);
        logger.log(Level.INFO, "Total chunks = {0}", totalChunks); //NON-NLS
        int read;

        byte[] data = new byte[(int) chunkSize];
        statusHelper.switchToDeterminate(totalChunks);

        // Read in byte size chunks and update the hash value with the data.
        for (int i = 0; i < totalChunks; i++) {
            if (context.dataSourceIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            try {
                read = img.read(data, i * chunkSize, chunkSize);
            } catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                        "EwfVerifyIngestModule.process.errReadImgAtChunk", imgName, i);
                services.postMessage(IngestMessage.createMessage(MessageType.ERROR, E01VerifierModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, msg, ex);
                return ProcessResult.ERROR;
            }

            // Only update with the read bytes.
            if (read == chunkSize) {
                //messageDigest.update(data);
            } else {
                byte[] subData = Arrays.copyOfRange(data, 0, read);
                //messageDigest.update(subData);
            }
            statusHelper.progress(i);
        }

        // Finish generating the hash and get it as a string value
        //calculatedHash = DatatypeConverter.printHexBinary(messageDigest.digest()).toLowerCase();
        verified = calculatedHash.equals(storedHash);
        logger.log(Level.INFO, "Hash calculated from {0}: {1}", new Object[]{imgName, calculatedHash}); //NON-NLS

        logger.log(Level.INFO, "complete() {0}", E01VerifierModuleFactory.getModuleName()); //NON-NLS
        String msg;
        if (verified) {
            msg = NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.verified");
        } else {
            msg = NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.notVerified");
        }
        String extra = NbBundle
                .getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.verifyResultsHeader", imgName);
        extra += NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.resultLi", msg);
        extra += NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.calcHashLi", calculatedHash);
        extra += NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.shutDown.storedHashLi", storedHash);
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, E01VerifierModuleFactory.getModuleName(), imgName + msg, extra));
        logger.log(Level.INFO, "{0}{1}", new Object[]{imgName, msg});

        return ProcessResult.OK;
    }
    
    private enum Mode {
        COMPUTE,
        VERIFY;
    }
    
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
}
