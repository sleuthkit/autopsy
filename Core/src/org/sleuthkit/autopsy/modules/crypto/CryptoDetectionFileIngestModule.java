/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.crypto;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * File ingest module to detect encryption.
 */
final class CryptoDetectionFileIngestModule implements FileIngestModule {

    private static final double ENTROPY_FACTOR = 1.4426950408889634073599246810019; // (1 / log(2))

    private static final Logger LOGGER = Logger.getLogger(CryptoDetectionFileIngestModule.class.getName());
    private final IngestServices SERVICES = IngestServices.getInstance();
    private long jobId;
    private static final IngestModuleReferenceCounter REF_COUNTER = new IngestModuleReferenceCounter();
    private FileTypeDetector fileTypeDetector;
    private Blackboard blackboard;

    /**
     * Create a CryptoDetectionFileIngestModule object that will detect files
     * that are encrypted and create blackboard artifacts as appropriate.
     */
    CryptoDetectionFileIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        jobId = context.getJobId();
        REF_COUNTER.incrementAndGet(jobId);
        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModule.IngestModuleException("Failed to create file type detector", ex);
        }
    }

    @Override
    public IngestModule.ProcessResult process(AbstractFile content) {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();

        if (isFileSupported(content)) {
            return processFile(content);
        }

        return IngestModule.ProcessResult.OK;
    }

    /**
     * Process the file. If the file has an entropy value greater than seven,
     * create a blackboard artifact.
     *
     * @param The file to be processed.
     *
     * @return 'OK' if the file was processed successfully, or 'ERROR' if there
     *         was a problem.
     */
    private IngestModule.ProcessResult processFile(AbstractFile f) {
        try {
            double entropy = calculateEntropy(f);
            if (entropy > 7.5) {
                BlackboardArtifact artifact = f.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);

                try {
                    /*
                     * Index the artifact for keyword search.
                     */
                    blackboard.indexArtifact(artifact);
                } catch (Blackboard.BlackboardException ex) {
                    LOGGER.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error("Failed to index encryption detected artifact for keyword search.", artifact.getDisplayName());
                }

                /*
                 * Send an event to update the view with the new result.
                 */
                SERVICES.fireModuleDataEvent(new ModuleDataEvent(CryptoDetectionModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED, Collections.singletonList(artifact)));
            }

            return IngestModule.ProcessResult.OK;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "Failed to create blackboard artifact ({0}).", ex.getLocalizedMessage()); //NON-NLS
            return IngestModule.ProcessResult.ERROR;
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, String.format("Failed to calculate the entropy for '%s'.", Paths.get(f.getParentPath(), f.getName())), ex); //NON-NLS
            return IngestModule.ProcessResult.ERROR;
        }
    }

    /**
     * Calculate the entropy of the file. The result is used to qualify the file
     * as an encrypted file.
     *
     * @param file The file to be calculated against.
     *
     * @return The entropy of the file.
     *
     * @throws IOException If there is a failure closing or reading from the
     *                     InputStream.
     */
    private double calculateEntropy(AbstractFile file) throws IOException {
        /*
         * Logic in this method is based on
         * https://github.com/willjasen/entropy/blob/master/entropy.java
         */
        InputStream in = null;
        BufferedInputStream bin = null;

        try {
            in = new ReadContentInputStream(file);
            bin = new BufferedInputStream(in);

            /*
             * Determine the number of times each byte value appears.
             */
            int[] byteOccurences = new int[256];
            int mostRecentByte = 0;
            int readByte;
            while ((readByte = bin.read()) != -1) {
                byteOccurences[readByte]++;
                mostRecentByte = readByte;
            }
            byteOccurences[mostRecentByte]--;

            /*
             * Calculate the entropy based on the byte occurence counts.
             */
            long dataLength = file.getSize() - 1;
            double entropy = 0;
            for (int i = 0; i < 256; i++) {
                if (byteOccurences[i] > 0) {
                    double byteProbability = (double) byteOccurences[i] / (double) dataLength;
                    entropy += (byteProbability * Math.log(byteProbability) * ENTROPY_FACTOR);
                }
            }

            return -entropy;

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "IOException occurred while trying to read data from InputStream.", ex); //NON-NLS
            throw ex;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (bin != null) {
                    bin.close();
                }
            } catch (IOException ex) {
                LOGGER.log(Level.WARNING, "Failed to close InputStream.", ex); //NON-NLS
                throw ex;
            }
        }
    }

    /**
     * This method checks if the AbstractFile input is supported. To qualify, it
     * must be an actual file that is not known, has a size that's evenly
     * divisible by 512 and a minimum size of 5MB, and has a MIME type of
     * 'application/octet-stream'.
     *
     * @param file AbstractFile to be checked.
     *
     * @return True if the AbstractFile qualifies.
     */
    private boolean isFileSupported(AbstractFile file) {
        boolean supported = false;
        
        /*
         * Criteria for the checks in this method are partially based on
         * http://www.forensicswiki.org/wiki/TrueCrypt#Detection
         */

        /*
         * Qualify the file type.
         */
        if (!file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) &&
                !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS) &&
                !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR) &&
                !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR)) {
            /*
             * Qualify the file against hash databases.
             */
            if (!file.getKnown().equals(TskData.FileKnown.KNOWN)) {
                /*
                 * Qualify the size.
                 */
                long contentSize = file.getSize();
                if (contentSize >= 5242880 && (contentSize % 512) == 0) {
                    /*
                     * Qualify the MIME type.
                     */
                    try {
                        String mimeType = fileTypeDetector.getFileType(file);
                        if (mimeType != null && mimeType.equals("application/octet-stream")) {
                            supported = true;
                        }
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Failed to detect file type", ex); //NON-NLS
                    }
                }
            }
        }

        return supported;
    }

    @Override
    public void shutDown() {
        REF_COUNTER.decrementAndGet(jobId);
    }
}