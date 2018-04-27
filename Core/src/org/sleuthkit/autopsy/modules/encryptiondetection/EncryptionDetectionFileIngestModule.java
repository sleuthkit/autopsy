/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
import java.util.logging.Level;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * File ingest module to detect encryption and password protection.
 */
final class EncryptionDetectionFileIngestModule extends FileIngestModuleAdapter {

    static final double DEFAULT_CONFIG_MINIMUM_ENTROPY = 7.5;
    static final int DEFAULT_CONFIG_MINIMUM_FILE_SIZE = 5242880; // 5MB;
    static final boolean DEFAULT_CONFIG_FILE_SIZE_MULTIPLE_ENFORCED = true;
    static final boolean DEFAULT_CONFIG_SLACK_FILES_ALLOWED = true;

    static final double MINIMUM_ENTROPY_INPUT_RANGE_MIN = 6.0;
    static final double MINIMUM_ENTROPY_INPUT_RANGE_MAX = 8.0;
    static final int MINIMUM_FILE_SIZE_INPUT_RANGE_MIN = 1;

    private static final int FILE_SIZE_MODULUS = 512;
    private static final double ONE_OVER_LOG2 = 1.4426950408889634073599246810019; // (1 / log(2))
    private static final int BYTE_OCCURENCES_BUFFER_SIZE = 256;

    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(EncryptionDetectionModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    private Blackboard blackboard;
    private double calculatedEntropy;

    private final double minimumEntropy;
    private final int minimumFileSize;
    private final boolean fileSizeMultipleEnforced;
    private final boolean slackFilesAllowed;

    /**
     * Create a EncryptionDetectionFileIngestModule object that will detect
     * files that are either encrypted or password protected and create
     * blackboard artifacts as appropriate. The supplied
     * EncryptionDetectionIngestJobSettings object is used to configure the
     * module.
     */
    EncryptionDetectionFileIngestModule(EncryptionDetectionIngestJobSettings settings) {
        minimumEntropy = settings.getMinimumEntropy();
        minimumFileSize = settings.getMinimumFileSize();
        fileSizeMultipleEnforced = settings.isFileSizeMultipleEnforced();
        slackFilesAllowed = settings.isSlackFilesAllowed();
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModule.IngestModuleException {
        try {
            validateSettings();
            blackboard = Case.getOpenCase().getServices().getBlackboard();
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModule.IngestModuleException("Failed to create file type detector", ex);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Override
    public IngestModule.ProcessResult process(AbstractFile file) {

        try {
            /*
             * Qualify the file type.
             */
            if (!file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR)
                    && (!file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) || slackFilesAllowed)) {
                /*
                 * Qualify the file against hash databases.
                 */
                if (!file.getKnown().equals(TskData.FileKnown.KNOWN)) {
                    /*
                     * Qualify the MIME type.
                     */
                    String mimeType = fileTypeDetector.getMIMEType(file);
                    if (mimeType.equals("application/octet-stream")) {
                        if (isFileEncryptionSuspected(file)) {
                            return flagFile(file, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED);
                        }
                    } else {
                        if (isFilePasswordProtected(file)) {
                            return flagFile(file, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);
                        }
                    }
                }
            }
        } catch (ReadContentInputStreamException | SAXException | TikaException ex) {
            logger.log(Level.WARNING, String.format("Unable to read file '%s'", file.getParentPath() + file.getName()), ex);
            return IngestModule.ProcessResult.ERROR;
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Unable to process file '%s'", file.getParentPath() + file.getName()), ex);
            return IngestModule.ProcessResult.ERROR;
        }

        return IngestModule.ProcessResult.OK;
    }

    /**
     * Validate ingest module settings.
     *
     * @throws IngestModule.IngestModuleException If the input is empty,
     *                                            invalid, or out of range.
     */
    @NbBundle.Messages({
        "EncryptionDetectionFileIngestModule.errorMessage.minimumEntropyInput=Minimum entropy input must be a number between 6.0 and 8.0.",
        "EncryptionDetectionFileIngestModule.errorMessage.minimumFileSizeInput=Minimum file size input must be an integer (in megabytes) of 1 or greater."
    })
    private void validateSettings() throws IngestModule.IngestModuleException {
        if (minimumEntropy < MINIMUM_ENTROPY_INPUT_RANGE_MIN || minimumEntropy > MINIMUM_ENTROPY_INPUT_RANGE_MAX) {
            throw new IngestModule.IngestModuleException(Bundle.EncryptionDetectionFileIngestModule_errorMessage_minimumEntropyInput());
        }

        if (minimumFileSize < MINIMUM_FILE_SIZE_INPUT_RANGE_MIN) {
            throw new IngestModule.IngestModuleException(Bundle.EncryptionDetectionFileIngestModule_errorMessage_minimumFileSizeInput());
        }
    }

    /**
     * Create a blackboard artifact.
     *
     * @param file         The file to be processed.
     * @param artifactType The type of artifact to create.
     *
     * @return 'OK' if the file was processed successfully, or 'ERROR' if there
     *         was a problem.
     */
    private IngestModule.ProcessResult flagFile(AbstractFile file, BlackboardArtifact.ARTIFACT_TYPE artifactType) {
        try {
            BlackboardArtifact artifact = file.newArtifact(artifactType);

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
            StringBuilder detailsSb = new StringBuilder();
            detailsSb.append("File: ").append(file.getParentPath()).append(file.getName());
            if (artifactType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED)) {
                detailsSb.append("<br/>\n").append("Entropy: ").append(calculatedEntropy);
            }

            services.postMessage(IngestMessage.createDataMessage(EncryptionDetectionModuleFactory.getModuleName(),
                    artifactType.getDisplayName() + " Match: " + file.getName(),
                    detailsSb.toString(),
                    file.getName(),
                    artifact));

            return IngestModule.ProcessResult.OK;
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Failed to create blackboard artifact for '%s'.", file.getParentPath() + file.getName()), ex); //NON-NLS
            return IngestModule.ProcessResult.ERROR;
        }
    }

    /**
     * This method checks if the AbstractFile input is password protected.
     *
     * @param file AbstractFile to be checked.
     *
     * @return True if the file is password protected.
     *
     * @throws ReadContentInputStreamException If there is a failure reading
     *                                         from the InputStream.
     * @throws IOException                     If there is a failure closing or
     *                                         reading from the InputStream.
     * @throws SAXException                    If there was an issue parsing the
     *                                         file with Tika.
     * @throws TikaException                   If there was an issue parsing the
     *                                         file with Tika.
     */
    private boolean isFilePasswordProtected(AbstractFile file) throws ReadContentInputStreamException, IOException, SAXException, TikaException {

        boolean passwordProtected = false;

        switch (file.getMIMEType()) {
            case "application/x-ooxml-protected":
                /*
                 * Office Open XML files that are password protected can be
                 * determined so simply by checking the MIME type.
                 */
                passwordProtected = true;
                break;

            case "application/msword":
            case "application/vnd.ms-excel":
            case "application/vnd.ms-powerpoint":
            case "application/pdf":
                /*
                 * A file of one of these types will be determined to be
                 * password protected or not by attempting to parse it via Tika.
                 */
                InputStream in = null;
                BufferedInputStream bin = null;

                try {
                    in = new ReadContentInputStream(file);
                    bin = new BufferedInputStream(in);
                    ContentHandler handler = new BodyContentHandler(-1);
                    Metadata metadata = new Metadata();
                    metadata.add(Metadata.RESOURCE_NAME_KEY, file.getName());
                    AutoDetectParser parser = new AutoDetectParser();
                    parser.parse(bin, handler, metadata, new ParseContext());
                } catch (EncryptedDocumentException ex) {
                    /*
                     * File is determined to be password protected.
                     */
                    passwordProtected = true;
                } finally {
                    if (in != null) {
                        in.close();
                    }
                    if (bin != null) {
                        bin.close();
                    }
                }
        }

        return passwordProtected;
    }

    /**
     * This method checks if the AbstractFile input is encrypted. It must meet
     * file size requirements before its entropy is calculated. If the entropy
     * result meets the minimum entropy value set, the file will be considered
     * to be possibly encrypted.
     *
     * @param file AbstractFile to be checked.
     *
     * @return True if encryption is suspected.
     *
     * @throws ReadContentInputStreamException If there is a failure reading
     *                                         from the InputStream.
     * @throws IOException                     If there is a failure closing or
     *                                         reading from the InputStream.
     */
    private boolean isFileEncryptionSuspected(AbstractFile file) throws ReadContentInputStreamException, IOException {
        /*
         * Criteria for the checks in this method are partially based on
         * http://www.forensicswiki.org/wiki/TrueCrypt#Detection
         */

        boolean possiblyEncrypted = false;

        /*
         * Qualify the size.
         */
        long contentSize = file.getSize();
        if (contentSize >= minimumFileSize) {
            if (!fileSizeMultipleEnforced || (contentSize % FILE_SIZE_MODULUS) == 0) {
                /*
                 * Qualify the entropy.
                 */
                calculatedEntropy = calculateEntropy(file);
                if (calculatedEntropy >= minimumEntropy) {
                    possiblyEncrypted = true;
                }
            }
        }

        return possiblyEncrypted;
    }

    /**
     * Calculate the entropy of the file. The result is used to qualify the file
     * as possibly encrypted.
     *
     * @param file The file to be calculated against.
     *
     * @return The entropy of the file.
     *
     * @throws ReadContentInputStreamException If there is a failure reading
     *                                         from the InputStream.
     * @throws IOException                     If there is a failure closing or
     *                                         reading from the InputStream.
     */
    private double calculateEntropy(AbstractFile file) throws ReadContentInputStreamException, IOException {
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
            int[] byteOccurences = new int[BYTE_OCCURENCES_BUFFER_SIZE];
            int readByte;
            while ((readByte = bin.read()) != -1) {
                byteOccurences[readByte]++;
            }

            /*
             * Calculate the entropy based on the byte occurence counts.
             */
            long dataLength = file.getSize() - 1;
            double entropyAccumulator = 0;
            for (int i = 0; i < BYTE_OCCURENCES_BUFFER_SIZE; i++) {
                if (byteOccurences[i] > 0) {
                    double byteProbability = (double) byteOccurences[i] / (double) dataLength;
                    entropyAccumulator += (byteProbability * Math.log(byteProbability) * ONE_OVER_LOG2);
                }
            }

            return -entropyAccumulator;

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
