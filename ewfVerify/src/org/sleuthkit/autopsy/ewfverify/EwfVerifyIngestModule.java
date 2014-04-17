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
package org.sleuthkit.autopsy.ewfverify;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.openide.util.NbBundle;

/**
 * Data source ingest module that verifies the integrity of an Expert Witness
 * Format (EWF) E01 image file by generating a hash of the file and comparing it
 * to the value stored in the image.
 */
public class EwfVerifyIngestModule extends IngestModuleAdapter implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(EwfVerifyIngestModule.class.getName());
    private static final long DEFAULT_CHUNK_SIZE = 32 * 1024;
    private static final IngestServices services = IngestServices.getInstance();
    private Image img;
    private String imgName;
    private MessageDigest messageDigest;
    private boolean verified = false;
    private boolean skipped = false;
    private String calculatedHash = "";
    private String storedHash = "";
    private IngestJobContext context;

    EwfVerifyIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        verified = false;
        skipped = false;
        img = null;
        imgName = "";
        storedHash = "";
        calculatedHash = "";

        if (messageDigest == null) {
            try {
                messageDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {
                logger.log(Level.WARNING, "Error getting md5 algorithm", ex);
                throw new RuntimeException(
                        NbBundle.getMessage(this.getClass(), "EwfVerifyIngestModule.startUp.exception.failGetMd5"));
            }
        } else {
            messageDigest.reset();
        }
    }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        imgName = dataSource.getName();
        try {
            img = dataSource.getImage();
        } catch (TskCoreException ex) {
            img = null;
            logger.log(Level.SEVERE, "Failed to get image from Content.", ex);
            services.postMessage(IngestMessage.createMessage( MessageType.ERROR, EwfVerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                    "EwfVerifyIngestModule.process.errProcImg",
                    imgName)));
            return ProcessResult.ERROR;
        }

        // Skip images that are not E01
        if (img.getType() != TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_EWF_EWF) {
            img = null;
            logger.log(Level.INFO, "Skipping non-ewf image {0}", imgName);
            services.postMessage(IngestMessage.createMessage( MessageType.INFO, EwfVerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                    "EwfVerifyIngestModule.process.skipNonEwf",
                    imgName)));
            skipped = true;
            return ProcessResult.OK;
        }

        if ((img.getMd5() != null) && !img.getMd5().isEmpty()) {
            storedHash = img.getMd5().toLowerCase();
            logger.log(Level.INFO, "Hash value stored in {0}: {1}", new Object[]{imgName, storedHash});
        } else {
            services.postMessage(IngestMessage.createMessage( MessageType.ERROR, EwfVerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                    "EwfVerifyIngestModule.process.noStoredHash",
                    imgName)));
            return ProcessResult.ERROR;
        }

        logger.log(Level.INFO, "Starting hash verification of {0}", img.getName());
        services.postMessage(IngestMessage.createMessage( MessageType.INFO, EwfVerifierModuleFactory.getModuleName(),
                NbBundle.getMessage(this.getClass(),
                "EwfVerifyIngestModule.process.startingImg",
                imgName)));

        long size = img.getSize();
        if (size == 0) {
            logger.log(Level.WARNING, "Size of image {0} was 0 when queried.", imgName);
            services.postMessage(IngestMessage.createMessage( MessageType.ERROR, EwfVerifierModuleFactory.getModuleName(),
                    NbBundle.getMessage(this.getClass(),
                    "EwfVerifyIngestModule.process.errGetSizeOfImg",
                    imgName)));
        }

        // Libewf uses a sector size of 64 times the sector size, which is the
        // motivation for using it here.
        long chunkSize = 64 * img.getSsize();
        chunkSize = (chunkSize == 0) ? DEFAULT_CHUNK_SIZE : chunkSize;

        int totalChunks = (int) Math.ceil(size / chunkSize);
        logger.log(Level.INFO, "Total chunks = {0}", totalChunks);
        int read;

        byte[] data;
        statusHelper.switchToDeterminate(totalChunks);

        // Read in byte size chunks and update the hash value with the data.
        for (int i = 0; i < totalChunks; i++) {
            if (context.isJobCancelled()) {
                return ProcessResult.OK;
            }
            data = new byte[(int) chunkSize];
            try {
                read = img.read(data, i * chunkSize, chunkSize);
            } catch (TskCoreException ex) {
                String msg = NbBundle.getMessage(this.getClass(),
                        "EwfVerifyIngestModule.process.errReadImgAtChunk", imgName, i);
                services.postMessage(IngestMessage.createMessage( MessageType.ERROR, EwfVerifierModuleFactory.getModuleName(), msg));
                logger.log(Level.SEVERE, msg, ex);
                return ProcessResult.ERROR;
            }
            messageDigest.update(data);
            statusHelper.progress(i);
        }

        // Finish generating the hash and get it as a string value
        calculatedHash = DatatypeConverter.printHexBinary(messageDigest.digest()).toLowerCase();
        verified = calculatedHash.equals(storedHash);
        logger.log(Level.INFO, "Hash calculated from {0}: {1}", new Object[]{imgName, calculatedHash});
        return ProcessResult.OK;
    }

    @Override
    public void shutDown(boolean ingestJobCancelled) {
        logger.log(Level.INFO, "complete() {0}", EwfVerifierModuleFactory.getModuleName());
        if (skipped == false) {
            String msg = "";
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
            services.postMessage(IngestMessage.createMessage( MessageType.INFO, EwfVerifierModuleFactory.getModuleName(), imgName + msg, extra));
            logger.log(Level.INFO, "{0}{1}", new Object[]{imgName, msg});
        }
    }
}
