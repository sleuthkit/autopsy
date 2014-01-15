/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Data Source Ingest Module that generates a hash of an E01 image file and
 * verifies it with the value stored in the image.
 * 
 * @author jwallace
 */
public class EwfVerifyIngestModule extends IngestModuleDataSource {
    private static final String MODULE_NAME = "EWF Verify";
    private static final String MODULE_VERSION = Version.getVersion();
    private static final String MODULE_DESCRIPTION = "Validates the integrity of E01 files.";
    private static final long DEFAULT_CHUNK_SIZE = 32 * 1024;
    private IngestServices services;
    private volatile boolean running = false;
    private Image img;
    private String imgName;
    private MessageDigest messageDigest;
    private static Logger logger = null;
    private static int messageId = 0;
    private boolean verified = false;
    private boolean skipped = false;
    private String calculatedHash = "";
    private String storedHash = "";
    private SleuthkitCase skCase;

    public EwfVerifyIngestModule() {
    }
    
    @Override
    public void process(PipelineContext<IngestModuleDataSource> pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        imgName = dataSource.getName();
        try {
            img = dataSource.getImage();
        } catch (TskCoreException ex) {
            img = null;
            logger.log(Level.SEVERE, "Failed to get image from Content.", ex);
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, 
                    "Error processing " + imgName));
            return;
        }
        
        // Skip images that are not E01
        if (img.getType() != TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_EWF_EWF) {
            img = null;
            logger.log(Level.INFO, "Skipping non-ewf image " + imgName);
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, 
                    "Skipping non-ewf image " + imgName));
            skipped = true;
            return;
        }
        
        // Get the hash stored in the E01 file from the database
        if (skCase.imageHasHash(img)) {
            try {
                storedHash = skCase.getImageHash(img).toLowerCase();
                logger.info("Hash value stored in " + imgName + ": " + storedHash);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get stored hash from image " + imgName, ex);
                services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, 
                        "Error retrieving stored hash value from " + imgName));
                return;
            }
        } else {
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, 
                    "Image " + imgName + " does not have stored hash."));
            return;
        }

        logger.log(Level.INFO, "Starting ewf verification of " + img.getName());
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, 
                "Starting " + imgName));
        
        long size = img.getSize();
        if (size == 0) {
            logger.log(Level.WARNING, "Size of image " + imgName + " was 0 when queried.");
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, 
                    "Error getting size of " + imgName + ". Image will not be processed."));
        }
        
        // Libewf uses a sector size of 64 times the sector size, which is the
        // motivation for using it here.
        long chunkSize = 64 * img.getSsize();
        chunkSize = (chunkSize == 0) ? DEFAULT_CHUNK_SIZE : chunkSize;
        
        int totalChunks = (int) Math.ceil(size / chunkSize);
        logger.log(Level.INFO, "Total chunks = " + totalChunks);
        int read;
        
        byte[] data;
        controller.switchToDeterminate(totalChunks);
        
        running = true;
        // Read in byte size chunks and update the hash value with the data.
        for (int i = 0; i < totalChunks; i++) {
            if (controller.isCancelled()) {
                running = false;
                return;
            }
            data = new byte[ (int) chunkSize ];
            try {
                read = img.read(data, i * chunkSize, chunkSize);
            } catch (TskCoreException ex) {
                String msg = "Error reading " + imgName + " at chunk " + i;
                services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, msg));
                logger.log(Level.SEVERE, msg, ex);
                return;
            }
            messageDigest.update(data);
            controller.progress(i);
        }
        
        // Finish generating the hash and get it as a string value
        calculatedHash = DatatypeConverter.printHexBinary(messageDigest.digest()).toLowerCase();
        verified = calculatedHash.equals(storedHash);
        logger.info("Hash calculated from " + imgName + ": " + calculatedHash);
        running = false;
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        skCase = Case.getCurrentCase().getSleuthkitCase();
        running = false;
        verified = false;
        skipped = false;
        img = null;
        imgName = "";
        storedHash = "";
        calculatedHash = "";
        
        if (logger == null) {
            logger = services.getLogger(this);
        }
        
        if (messageDigest == null) {
            try {
                messageDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException ex) {
                logger.log(Level.WARNING, "Error getting md5 algorithm", ex);
                throw new RuntimeException("Failed to get MD5 algorithm");
            }
        } else {
            messageDigest.reset();
        }
    }

    @Override
    public void complete() {
        logger.info("complete() " + this.getName());
        if (skipped == false) {
            String msg = verified ? " verified" : " not verified";
            String extra = "<p>EWF Verification Results for " + imgName + "</p>";
            extra += "<li>Result:" + msg + "</li>";
            extra += "<li>Calculated hash: " + calculatedHash + "</li>";
            extra += "<li>Stored hash: " + storedHash + "</li>";
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, imgName +  msg, extra));
            logger.info(imgName + msg);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return running;
    }
}
