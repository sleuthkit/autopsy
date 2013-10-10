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
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestDataSourceWorkerController;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleDataSource;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * @author jwallace
 */
public class EwfVerifyIngestModule extends IngestModuleDataSource {
    private static final String MODULE_NAME = "ewf Verify";
    private static final String MODULE_VERSION = "1.0";
    private static final String MODULE_DESCRIPTION = "Validates the integrity of E01 files.";
    private static final long CHUNK_SIZE = 16 * 1024;
    private IngestServices services;
    private volatile boolean running = false;
    private Image img;
    private MessageDigest md;
    private Logger logger;
    private static int messageId = 0;
    private volatile boolean cancelled = false;
    private boolean verified = false;

    public EwfVerifyIngestModule() {
    }
    
    @Override
    public void process(PipelineContext<IngestModuleDataSource> pipelineContext, Content dataSource, IngestDataSourceWorkerController controller) {
        try {
            img = dataSource.getImage();
        } catch (TskCoreException ex) {
            img = null;
            logger.log(Level.SEVERE, "Failed to get image from Content.", ex);
            services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, "Error processing " + dataSource.getName()));
        }
        
        if (img.getType() != TskData.TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_EWF_EWF) {
            img = null;
            // TODO notify?
            logger.log(Level.INFO, "Skipping non-ewf image " + img.getName());
            return;
        }

        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, "Starting " + dataSource.getName()));
        long size = img.getSize();      // size of the image
        
        // TODO handle size = 0
        
        int totalChunks = (int) Math.ceil(size / CHUNK_SIZE);
        System.out.println("TOTAL CHUNKS = " + totalChunks);
        int read;
        
        // TODO find an appropriate size for this.
        byte[] data;
        controller.switchToDeterminate(totalChunks);
        
        running = true;
        StopWatch timer = new StopWatch();
        timer.start();
        for (int i = 0; i < totalChunks; i++) {
            if (cancelled) {
                timer.stop();
                running = false;
                return;
            }
            data = new byte[ (int) CHUNK_SIZE ];
            try {
                read = img.read(data, i * CHUNK_SIZE, CHUNK_SIZE);
            } catch (TskCoreException ex) {
                services.postMessage(IngestMessage.createMessage(++messageId, MessageType.ERROR, this, "Error processing " + img.getName()));
                logger.log(Level.SEVERE, "Error reading from image: " + img.getName(), ex);
            }
            md.update(data);
            controller.progress(i);
        }
        timer.stop();
        byte[] byteHash = md.digest();
        String hash = bytesToString(byteHash);
        System.out.println("MD5 HASH: " + hash);
        System.out.println("GENERATING HASH TOOK " + timer.getElapsedTimeSecs() + " SECONDS");
        running = false;
        // TODO logic to check if it is verified.
        verified = true;
    }

    @Override
    public void init(IngestModuleInit initContext) {
        services = IngestServices.getDefault();
        logger = services.getLogger(this);
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException ex) {
            logger.log(Level.WARNING, "Error getting md5 algorithm", ex);
            throw new RuntimeException("Failed to get MD5 algorithm");
        }
        cancelled = false;
        running = false;
        img = null;
    }

    @Override
    public void complete() {
        logger.info("complete() " + this.getName());
        String msg = verified ? " verified." : " not verified.";
        services.postMessage(IngestMessage.createMessage(++messageId, MessageType.INFO, this, img.getName() +  msg));
    }

    @Override
    public void stop() {
        cancelled = true;
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

    private String bytesToString(byte[] byteHash) {
        StringBuilder sb = new StringBuilder();
        for (byte b : byteHash) {
            sb.append(String.format("%02x", b&0xff));
        }
        return sb.toString();
    }
}
