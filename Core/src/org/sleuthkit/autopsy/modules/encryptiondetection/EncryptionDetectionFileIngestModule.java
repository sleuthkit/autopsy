/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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

import com.healthmarketscience.jackcess.crypt.CryptCodecProvider;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.crypt.InvalidCredentialsException;
import com.healthmarketscience.jackcess.impl.CodecProvider;
import com.healthmarketscience.jackcess.impl.UnsupportedCodecException;
import com.healthmarketscience.jackcess.util.MemFileChannel;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.logging.Level;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.FileIngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.ReadContentInputStream.ReadContentInputStreamException;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * File ingest module to detect encryption and password protection.
 */
final class EncryptionDetectionFileIngestModule extends FileIngestModuleAdapter {

    private static final int FILE_SIZE_MODULUS = 512;
    
    private static final String DATABASE_FILE_EXTENSION = "db";
    private static final int MINIMUM_DATABASE_FILE_SIZE = 65536; //64 KB

    private static final String MIME_TYPE_OOXML_PROTECTED = "application/x-ooxml-protected";
    private static final String MIME_TYPE_MSWORD = "application/msword";
    private static final String MIME_TYPE_MSEXCEL = "application/vnd.ms-excel";
    private static final String MIME_TYPE_MSPOWERPOINT = "application/vnd.ms-powerpoint";
    private static final String MIME_TYPE_MSACCESS = "application/x-msaccess";
    private static final String MIME_TYPE_PDF = "application/pdf";

    private static final String[] FILE_IGNORE_LIST = {"hiberfile.sys", "pagefile.sys"};

    private final IngestServices services = IngestServices.getInstance();
    private final Logger logger = services.getLogger(EncryptionDetectionModuleFactory.getModuleName());
    private FileTypeDetector fileTypeDetector;
    private Blackboard blackboard;
    private IngestJobContext context;
    private double calculatedEntropy;
    
    private final double minimumEntropy;
    private final int minimumFileSize;
    private final boolean fileSizeMultipleEnforced;
    private final boolean slackFilesAllowed;

    /**
     * Create a EncryptionDetectionFileIngestModule object that will detect
     * files that are either encrypted or password protected and create
     * blackboard artifacts as appropriate.
     *
     * @param settings The settings used to configure the module.
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
            this.context = context;
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();

            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModule.IngestModuleException("Failed to create file type detector", ex);
        } catch (NoCurrentCaseException ex) {
            throw new IngestModule.IngestModuleException("Exception while getting open case.", ex);
        }
    }

    @Messages({
        "EncryptionDetectionFileIngestModule.artifactComment.suspected=Suspected encryption due to high entropy (%f)."
    })
    @Override
    public IngestModule.ProcessResult process(AbstractFile file) {

        try {
            /*
             * Qualify the file type, qualify it against hash databases, and
             * verify the file hasn't been deleted.
             */
            if (!file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)
                    && !file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.LOCAL_DIR)
                    && (!file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.SLACK) || slackFilesAllowed)
                    && !file.getKnown().equals(TskData.FileKnown.KNOWN)
                    && !file.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)) {
                /*
                 * Is the file in FILE_IGNORE_LIST?
                 */
                String filePath = file.getParentPath();
                if (filePath.equals("/")) {
                    String fileName = file.getName();
                    for (String listEntry : FILE_IGNORE_LIST) {
                        if (fileName.equalsIgnoreCase(listEntry)) {
                            // Skip this file.
                            return IngestModule.ProcessResult.OK;
                        }
                    }
                }

                /*
                 * Qualify the MIME type.
                 */
                String mimeType = fileTypeDetector.getMIMEType(file);
                if (mimeType.equals("application/octet-stream") && isFileEncryptionSuspected(file)) {
                    return flagFile(file, BlackboardArtifact.Type.TSK_ENCRYPTION_SUSPECTED, Score.SCORE_LIKELY_NOTABLE,
                            String.format(Bundle.EncryptionDetectionFileIngestModule_artifactComment_suspected(), calculatedEntropy));
                } else if (isFilePasswordProtected(file)) {
                    return flagFile(file, BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED, Score.SCORE_NOTABLE, 
                    EncryptionDetectionModuleFactory.PASSWORD_PROTECT_MESSAGE);
                }
            }
        } catch (ReadContentInputStreamException | SAXException | TikaException | UnsupportedCodecException ex) {
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
    private void validateSettings() throws IngestModule.IngestModuleException {
        EncryptionDetectionTools.validateMinEntropyValue(minimumEntropy);
        EncryptionDetectionTools.validateMinFileSizeValue(minimumFileSize);
    }

    /**
     * Create a blackboard artifact.
     *
     * @param file         The file to be processed.
     * @param artifactType The type of artifact to create. Assumed to be an
     *                     analysis result type.
     * @param score        The score of the analysis result.
     * @param comment      A comment to be attached to the artifact.
     *
     * @return 'OK' if the file was processed successfully, or 'ERROR' if there
     *         was a problem.
     */
    private IngestModule.ProcessResult flagFile(AbstractFile file, BlackboardArtifact.Type artifactType, Score score, String comment) {
        try {
            if (context.fileIngestIsCancelled()) {
                return IngestModule.ProcessResult.OK;
            }

            BlackboardArtifact artifact = file.newAnalysisResult(artifactType, score, null, null, comment, 
                    Arrays.asList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT,
                    EncryptionDetectionModuleFactory.getModuleName(), comment)))
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
            StringBuilder detailsSb = new StringBuilder();
            detailsSb.append("File: ").append(file.getParentPath()).append(file.getName());
            if (artifactType.equals(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_SUSPECTED)) {
                detailsSb.append("<br/>\nEntropy: ").append(calculatedEntropy);
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
     * @throws UnsupportedCodecException       If an Access database could not
     *                                         be opened by Jackcess due to
     *                                         unsupported encoding.
     */
    private boolean isFilePasswordProtected(AbstractFile file) throws ReadContentInputStreamException, IOException, SAXException, TikaException, UnsupportedCodecException {

        boolean passwordProtected = false;

        switch (file.getMIMEType()) {
            case MIME_TYPE_OOXML_PROTECTED:
                /*
                 * Office Open XML files that are password protected can be
                 * determined so simply by checking the MIME type.
                 */
                passwordProtected = true;
                break;

            case MIME_TYPE_MSWORD:
            case MIME_TYPE_MSEXCEL:
            case MIME_TYPE_MSPOWERPOINT:
            case MIME_TYPE_PDF: {
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
                break;
            }

            case MIME_TYPE_MSACCESS: {
                /*
                 * Access databases are determined to be password protected
                 * using Jackcess. If the database can be opened, the password
                 * is read from it to see if it's null. If the database can not
                 * be opened due to an InvalidCredentialException being thrown,
                 * it is automatically determined to be password protected.
                 */
                InputStream in = null;
                BufferedInputStream bin = null;

                try {
                    in = new ReadContentInputStream(file);
                    bin = new BufferedInputStream(in);
                    MemFileChannel memFileChannel = MemFileChannel.newChannel(bin);
                    CodecProvider codecProvider = new CryptCodecProvider();
                    DatabaseBuilder databaseBuilder = new DatabaseBuilder();
                    databaseBuilder.setChannel(memFileChannel);
                    databaseBuilder.setCodecProvider(codecProvider);
                    Database accessDatabase;
                    try {
                        accessDatabase = databaseBuilder.open();
                    } catch (InvalidCredentialsException ex) {
                        logger.log(Level.INFO, String.format(
                                "Jackcess throws invalid credentials exception for file (name: %s, id: %s).  It will be assumed to be password protected.",
                                file.getName(), file.getId()));
                        return true;
                    } catch (Exception ex) { // Firewall, see JIRA-7097
                        logger.log(Level.WARNING, String.format("Unexpected exception "
                                + "trying to open msaccess database using Jackcess "
                                + "(name: %s, id: %d)", file.getName(), file.getId()), ex);
                        return passwordProtected;
                    }
                    /*
                     * No exception has been thrown at this point, so the file
                     * is either a JET database, or an unprotected ACE database.
                     * Read the password from the database to see if it exists.
                     */
                    if (accessDatabase.getDatabasePassword() != null) {
                        passwordProtected = true;
                    }
                } catch (InvalidCredentialsException ex) {
                    /*
                     * The ACE database is determined to be password protected.
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
        boolean fileSizeQualified = false;
        String fileExtension = file.getNameExtension();
        long contentSize = file.getSize();
        // Database files qualify at 64 KB minimum for SQLCipher detection.
        if (fileExtension.equalsIgnoreCase(DATABASE_FILE_EXTENSION)) {
            if (contentSize >= MINIMUM_DATABASE_FILE_SIZE) {
                fileSizeQualified = true;
            }
        } else if (contentSize >= minimumFileSize) {
            if (!fileSizeMultipleEnforced || (contentSize % FILE_SIZE_MODULUS) == 0) {
                fileSizeQualified = true;
            }
        }

        if (fileSizeQualified) {
            /*
             * Qualify the entropy.
             */
            calculatedEntropy = EncryptionDetectionTools.calculateEntropy(file, context);
            if (calculatedEntropy >= minimumEntropy) {
                possiblyEncrypted = true;
            }
        }

        return possiblyEncrypted;
    }
}
