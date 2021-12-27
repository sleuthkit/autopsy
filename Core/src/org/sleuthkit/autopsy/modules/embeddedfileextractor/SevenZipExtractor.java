/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.embeddedfileextractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.sf.sevenzipjbinding.ArchiveFormat;
import static net.sf.sevenzipjbinding.ArchiveFormat.RAR;
import net.sf.sevenzipjbinding.ExtractAskMode;
import net.sf.sevenzipjbinding.ExtractOperationResult;
import net.sf.sevenzipjbinding.IArchiveExtractCallback;
import net.sf.sevenzipjbinding.ICryptoGetTextPassword;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.PropID;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import org.apache.tika.Tika;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.modules.encryptiondetection.EncryptionDetectionModuleFactory;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.FileTaskExecutor.FileTaskFailedException;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * An embedded file extractor that uses 7Zip via Java bindings to extract the
 * contents of an archive file to a directory named for the archive file.
 */
class SevenZipExtractor {

    private static final Logger logger = Logger.getLogger(SevenZipExtractor.class.getName());

    private static final String MODULE_NAME = EmbeddedFileExtractorModuleFactory.getModuleName();
    
    //encryption type strings
    private static final String ENCRYPTION_FILE_LEVEL = NbBundle.getMessage(EmbeddedFileExtractorIngestModule.class,
            "EmbeddedFileExtractorIngestModule.ArchiveExtractor.encryptionFileLevel");
    private static final String ENCRYPTION_FULL = EncryptionDetectionModuleFactory.PASSWORD_PROTECT_MESSAGE;

    //zip bomb detection
    private static final int MAX_DEPTH = 4;
    private static final int MAX_COMPRESSION_RATIO = 600;
    private static final long MIN_COMPRESSION_RATIO_SIZE = 500 * 1000000L;
    private static final long MIN_FREE_DISK_SPACE = 1 * 1000 * 1000000L; //1GB

    private IngestServices services = IngestServices.getInstance();
    private final IngestJobContext context;
    private final FileTypeDetector fileTypeDetector;
    private final FileTaskExecutor fileTaskExecutor;

    private String moduleDirRelative;
    private String moduleDirAbsolute;

    private Blackboard blackboard;

    private ProgressHandle progress;
    private int numItems;
    private String currentArchiveName;

    /**
     * Enum of mimetypes which support archive extraction
     */
    private enum SupportedArchiveExtractionFormats {

        ZIP("application/zip"), //NON-NLS
        SEVENZ("application/x-7z-compressed"), //NON-NLS
        GZIP("application/gzip"), //NON-NLS
        XGZIP("application/x-gzip"), //NON-NLS
        XBZIP2("application/x-bzip2"), //NON-NLS
        XTAR("application/x-tar"), //NON-NLS
        XGTAR("application/x-gtar"),
        XRAR("application/x-rar-compressed"); //NON-NLS

        private final String mimeType;

        SupportedArchiveExtractionFormats(final String mimeType) {
            this.mimeType = mimeType;
        }

        @Override
        public String toString() {
            return this.mimeType;
        }
        // TODO Expand to support more formats after upgrading Tika
    }

    /**
     * Constructs an embedded file extractor that uses 7Zip via Java bindings to
     * extract the contents of an archive file to a directory named for the
     * archive file.
     *
     * @param context            The ingest job context, if being used as part
     *                           of an ingest job. Optional, may be null.
     * @param fileTypeDetector   A file type detector.
     * @param moduleDirRelative  The relative path to the output directory for
     *                           the extracted files. Used in the case database
     *                           for extracted (derived) file paths for the
     *                           extracted files.
     * @param moduleDirAbsolute  The absolute path to the output directory for
     *                           the extracted files.
     * @param fileIoTaskExecutor A file I/O task executor.
     *
     * @throws SevenZipNativeInitializationException If there was an error
     *                                               initializing the 7Zip Java
     *                                               bindings.
     */
    SevenZipExtractor(IngestJobContext context, FileTypeDetector fileTypeDetector, String moduleDirRelative, String moduleDirAbsolute, FileTaskExecutor fileTaskExecutor) throws SevenZipNativeInitializationException {
        if (!SevenZip.isInitializedSuccessfully()) {
            throw new SevenZipNativeInitializationException("SevenZip has not been previously initialized.");
        }
        this.context = context;
        this.fileTypeDetector = fileTypeDetector;
        this.moduleDirRelative = moduleDirRelative;
        this.moduleDirAbsolute = moduleDirAbsolute;
        this.fileTaskExecutor = fileTaskExecutor;
    }

    /**
     * Checks whether extraction is supported for a file, based on MIME type.
     *
     * @param file The file.
     *
     * @return This method returns true if the file format is currently
     *         supported. Else it returns false.
     */
    boolean isSevenZipExtractionSupported(AbstractFile file) {
        String fileMimeType = fileTypeDetector.getMIMEType(file);
        for (SupportedArchiveExtractionFormats mimeType : SupportedArchiveExtractionFormats.values()) {
            if (checkForIngestCancellation(file)) {
                break;
            }
            if (mimeType.toString().equals(fileMimeType)) {
                return true;
            }
        }
        return false;
    }
    
    boolean isSevenZipExtractionSupported(String mimeType) {
        for (SupportedArchiveExtractionFormats supportedMimeType : SupportedArchiveExtractionFormats.values()) {
            if (mimeType.contains(supportedMimeType.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Private helper method to standardize the cancellation check that is
     * performed when running ingest. Will return false if the SevenZipExtractor
     * is being used without an IngestJobContext.
     *
     * @param file The file being extracted, this is only used for logging
     *             purposes.
     *
     * @return True if ingest has been cancelled, false otherwise.
     */
    private boolean checkForIngestCancellation(AbstractFile file) {
        if (fileTaskExecutor != null && context != null && context.fileIngestIsCancelled()) {
            logger.log(Level.INFO, "Ingest was cancelled. Results extracted from the following archive file may be incomplete. Name: {0}Object ID: {1}", new Object[]{file.getName(), file.getId()});
            return true;
        }
        return false;
    }

    /**
     * Check if the item inside archive is a potential zipbomb
     *
     * Currently checks compression ratio.
     *
     * More heuristics to be added here
     *
     * @param archiveFile        the AbstractFile for the parent archive which
     *                           which we are checking
     * @param inArchive          The SevenZip archive currently open for
     *                           extraction
     *
     * @param inArchiveItemIndex Index of item inside the SevenZip archive. Each
     *                           file inside an archive is associated with a
     *                           unique integer
     *
     * @param depthMap           a concurrent hashmap which keeps track of the
     *                           depth of all nested archives, key of objectID
     * @param escapedFilePath    the path to the archiveFileItem which has been
     *                           escaped
     *
     * @return true if potential zip bomb, false otherwise
     */
    private boolean isZipBombArchiveItemCheck(AbstractFile archiveFile, IInArchive inArchive, int inArchiveItemIndex, ConcurrentHashMap<Long, Archive> depthMap, String escapedFilePath) {
        //If a file is corrupted as a result of reconstructing it from unallocated space, then
        //7zip does a poor job estimating the original uncompressed file size. 
        //As a result, many corrupted files have wonky compression ratios and could flood the UI
        //with false zip bomb notifications. The decision was made to skip compression ratio checks 
        //for unallocated zip files. Instead, we let the depth be an indicator of a zip bomb.
        //Gzip archives compress a single file. They may have a sparse file,
        //and that file could be much larger, however it won't be the exponential growth seen with more dangerous zip bombs.
        //In addition a fair number of browser cache files will be gzip archives,
        //and their file sizes are frequently retrieved incorrectly so ignoring gzip files is a reasonable decision.
        if (archiveFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC) || archiveFile.getMIMEType().equalsIgnoreCase(SupportedArchiveExtractionFormats.XGZIP.toString())) {
            return false;
        }

        try {
            final Long archiveItemSize = (Long) inArchive.getProperty(
                    inArchiveItemIndex, PropID.SIZE);

            //skip the check for small files
            if (archiveItemSize == null || archiveItemSize < MIN_COMPRESSION_RATIO_SIZE) {
                return false;
            }

            final Long archiveItemPackedSize = (Long) inArchive.getProperty(
                    inArchiveItemIndex, PropID.PACKED_SIZE);

            if (archiveItemPackedSize == null || archiveItemPackedSize <= 0) {
                logger.log(Level.WARNING, "Cannot getting compression ratio, cannot detect if zipbomb: {0}, item: {1}", //NON-NLS
                        new Object[]{archiveFile.getName(), (String) inArchive.getProperty(inArchiveItemIndex, PropID.PATH)}); //NON-NLS
                return false;
            }

            int cRatio = (int) (archiveItemSize / archiveItemPackedSize);

            if (cRatio >= MAX_COMPRESSION_RATIO) {
                Archive rootArchive = depthMap.get(depthMap.get(archiveFile.getId()).getRootArchiveId());
                String details = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.isZipBombCheck.warnDetails",
                        cRatio, FileUtil.escapeFileName(getArchiveFilePath(rootArchive.getArchiveFile())));

                flagRootArchiveAsZipBomb(rootArchive, archiveFile, details, escapedFilePath);
                return true;
            } else {
                return false;
            }

        } catch (SevenZipException ex) {
            logger.log(Level.WARNING, "Error getting archive item size and cannot detect if zipbomb. ", ex); //NON-NLS
            return false;
        }
    }

    /**
     * Flag the root archive archive as a zipbomb by creating an interesting
     * file artifact and posting a message to the inbox for the user.
     *
     * @param rootArchive     - the Archive which the artifact is to be for
     * @param archiveFile     - the AbstractFile which for the file which
     *                        triggered the potential zip bomb to be detected
     * @param details         - the String which contains the details about how
     *                        the potential zip bomb was detected
     * @param escapedFilePath - the escaped file path for the archiveFile
     */
    private void flagRootArchiveAsZipBomb(Archive rootArchive, AbstractFile archiveFile, String details, String escapedFilePath) {
        rootArchive.flagAsZipBomb();
        logger.log(Level.INFO, details);
        
        String setName = "Possible Zip Bomb";
        try {
            Collection<BlackboardAttribute> attributes = Arrays.asList(
                    new BlackboardAttribute(
                            TSK_SET_NAME, MODULE_NAME,
                            setName),
                    new BlackboardAttribute(
                            TSK_DESCRIPTION, MODULE_NAME,
                            Bundle.SevenZipExtractor_zipBombArtifactCreation_text(archiveFile.getName())),
                    new BlackboardAttribute(
                            TSK_COMMENT, MODULE_NAME,
                            details));

            if (!blackboard.artifactExists(archiveFile, BlackboardArtifact.Type.TSK_INTERESTING_ITEM, attributes)) {
                BlackboardArtifact artifact = rootArchive.getArchiveFile().newAnalysisResult(
                        BlackboardArtifact.Type.TSK_INTERESTING_ITEM, Score.SCORE_LIKELY_NOTABLE, 
                        null, setName, null, 
                        attributes)
                        .getAnalysisResult();

                try {
                    /*
                     * post the artifact which will index the artifact for
                     * keyword search, and fire an event to notify UI of this
                     * new artifact
                     */
                    blackboard.postArtifact(artifact, MODULE_NAME, context.getJobId());

                    String msg = NbBundle.getMessage(SevenZipExtractor.class,
                            "EmbeddedFileExtractorIngestModule.ArchiveExtractor.isZipBombCheck.warnMsg", archiveFile.getName(), escapedFilePath);//NON-NLS

                    services.postMessage(IngestMessage.createWarningMessage(MODULE_NAME, msg, details));

                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.SevenZipExtractor_indexError_message(), artifact.getDisplayName());
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error creating blackboard artifact for Zip Bomb Detection for file: " + escapedFilePath, ex); //NON-NLS
        }
    }

    /**
     * Check file extension and return appropriate input options for
     * SevenZip.openInArchive()
     *
     * @param archiveFile file to check file extension
     *
     * @return input parameter for SevenZip.openInArchive()
     */
    private ArchiveFormat get7ZipOptions(AbstractFile archiveFile) {
        // try to get the file type from the BB
        String detectedFormat;
        detectedFormat = archiveFile.getMIMEType();

        if (detectedFormat == null) {
            logger.log(Level.WARNING, "Could not detect format for file: {0}", archiveFile); //NON-NLS

            // if we don't have attribute info then use file extension
            String extension = archiveFile.getNameExtension();
            if ("rar".equals(extension)) //NON-NLS
            {
                // for RAR files we need to open them explicitly as RAR. Otherwise, if there is a ZIP archive inside RAR archive
                // it will be opened incorrectly when using 7zip's built-in auto-detect functionality
                return RAR;
            }

            // Otherwise open the archive using 7zip's built-in auto-detect functionality
            return null;
        } else if (detectedFormat.contains("application/x-rar-compressed")) //NON-NLS
        {
            // for RAR files we need to open them explicitly as RAR. Otherwise, if there is a ZIP archive inside RAR archive
            // it will be opened incorrectly when using 7zip's built-in auto-detect functionality
            return RAR;
        }

        // Otherwise open the archive using 7zip's built-in auto-detect functionality
        return null;
    }

    /**
     * Get the data source object id of the root data source for the specified
     * archive
     *
     * @param file the archive which the root data source id is being found
     *
     * @return the data source object id of the root data source
     *
     * @throws TskCoreException
     */
    private long getRootArchiveId(AbstractFile file) throws TskCoreException {
        long id = file.getId();
        Content parentContent = file.getParent();
        while (parentContent != null) {
            id = parentContent.getId();
            parentContent = parentContent.getParent();
        }
        return id;
    }

    /**
     * Queries the case database to get any files already extracted from the
     * given archive. The archive file path is used to find the files by parent
     * path.
     *
     * @param archiveFile     The archive.
     * @param archiveFilePath The archive file path.
     *
     * @return A list of the files already extracted from the given archive.
     *
     * @throws TskCoreException          If there is an error querying the case
     *                                   database.
     * @throws InterruptedException      If checking for the existence of the
     *                                   extracted file directory is
     *                                   interrupted.
     * @throws FileIoTaskFailedException If there is an error checking for the
     *                                   existence of the extracted file
     *                                   directory.
     */
    private List<AbstractFile> getAlreadyExtractedFiles(AbstractFile archiveFile, String archiveFilePath) throws TskCoreException, InterruptedException, FileTaskExecutor.FileTaskFailedException {
        /*
         * TODO (Jira-7145): Is this logic correct?
         */
        List<AbstractFile> extractedFiles = new ArrayList<>();
        File outputDirectory = new File(moduleDirAbsolute, EmbeddedFileExtractorIngestModule.getUniqueName(archiveFile));
        if (archiveFile.hasChildren() && fileTaskExecutor.exists(outputDirectory)) {
            Case currentCase = Case.getCurrentCase();
            FileManager fileManager = currentCase.getServices().getFileManager();
            extractedFiles.addAll(fileManager.findFilesByParentPath(getRootArchiveId(archiveFile), archiveFilePath));
        }
        return extractedFiles;
    }

    /**
     * Get the archiveFilePath
     *
     * @param archiveFile the archiveFile to get the path for
     *
     * @return the archiveFilePath to be used by the unpack method
     */
    private String getArchiveFilePath(AbstractFile archiveFile) {
        return archiveFile.getParentPath() + archiveFile.getName();
    }

    /**
     * Creates the root directory for files extracted from this archive. The
     * directory name is a unique name derived from the archive file name and
     * its object ID.
     *
     * @param uniqueArchiveFileName The unique name of the archive file.
     *
     * @return True on success, false on failure.
     */
    private boolean makeExtractedFilesDirectory(String uniqueArchiveFileName) {
        boolean success = true;
        Path rootDirectoryPath = Paths.get(moduleDirAbsolute, uniqueArchiveFileName);
        File rootDirectory = rootDirectoryPath.toFile();
        try {
            if (!fileTaskExecutor.exists(rootDirectory)) {
                success = fileTaskExecutor.mkdirs(rootDirectory);
            }
        } catch (SecurityException | FileTaskFailedException | InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("Error creating root extracted files directory %s", rootDirectory), ex); //NON-NLS
            success = false;
        }
        return success;
    }

    /**
     * Get the path in the archive of the specified item
     *
     * @param archive            - the archive to get the path for
     * @param inArchiveItemIndex - the item index to help provide uniqueness to
     *                           the path
     * @param archiveFile        - the archive file the item exists in
     *
     * @return a string representing the path to the item in the archive
     *
     * @throws SevenZipException
     */
    private String getPathInArchive(IInArchive archive, int inArchiveItemIndex, AbstractFile archiveFile) throws SevenZipException {
        String pathInArchive = (String) archive.getProperty(inArchiveItemIndex, PropID.PATH);

        if (pathInArchive == null || pathInArchive.isEmpty()) {
            //some formats (.tar.gz) may not be handled correctly -- file in archive has no name/path
            //handle this for .tar.gz and tgz but assuming the child is tar,
            //otherwise, unpack using itemNumber as name

            //TODO this should really be signature based, not extension based
            String archName = archiveFile.getName();
            int dotI = archName.lastIndexOf(".");
            String useName = null;
            if (dotI != -1) {
                String base = archName.substring(0, dotI);
                String ext = archName.substring(dotI);
                int colonIndex = ext.lastIndexOf(":");
                if (colonIndex != -1) {
                    // If alternate data stream is found, fix the name 
                    // so Windows doesn't choke on the colon character.
                    ext = ext.substring(0, colonIndex);
                }
                switch (ext) {
                    case ".gz": //NON-NLS
                        useName = base;
                        break;
                    case ".tgz": //NON-NLS
                        useName = base + ".tar"; //NON-NLS
                        break;
                    case ".bz2": //NON-NLS
                        useName = base;
                        break;
                }
            }
            if (useName == null) {
                pathInArchive = "/" + archName + "/" + Integer.toString(inArchiveItemIndex);
            } else {
                pathInArchive = "/" + useName;
            }
        }
        return pathInArchive;
    }

    private byte[] getPathBytesInArchive(IInArchive archive, int inArchiveItemIndex, AbstractFile archiveFile) throws SevenZipException {
        return (byte[]) archive.getProperty(inArchiveItemIndex, PropID.PATH_BYTES);
    }

    /*
     * Get the String that will represent the key for the hashmap which keeps
     * track of existing files from an AbstractFile
     */
    private String getKeyAbstractFile(AbstractFile fileInDatabase) {
        return fileInDatabase == null ? null : fileInDatabase.getParentPath() + fileInDatabase.getName();
    }

    /*
     * Get the String that will represent the key for the hashmap which keeps
     * track of existing files from an unpacked node and the archiveFilePath
     */
    private String getKeyFromUnpackedNode(UnpackedTree.UnpackedNode node, String archiveFilePath) {
        return node == null ? null : archiveFilePath + "/" + node.getFileName();
    }

    /**
     * Unpack the file to local folder.
     *
     * @param archiveFile file to unpack
     * @param depthMap    - a concurrent hashmap which keeps track of the depth
     *                    of all nested archives, key of objectID
     */
    void unpack(AbstractFile archiveFile, ConcurrentHashMap<Long, Archive> depthMap) {
        unpack(archiveFile, depthMap, null);
    }

    /**
     * Unpack the file to local folder and return a list of derived files, use
     * the password if specified.
     *
     * @param archiveFile - file to unpack
     * @param depthMap    - a concurrent hashmap which keeps track of the depth
     *                    of all nested archives, key of objectID
     * @param password    - the password to use, null for no password
     *
     * @return true if unpacking is complete
     */
    @Messages({"SevenZipExtractor.indexError.message=Failed to index encryption detected artifact for keyword search.",
        "# {0} -  rootArchive",
        "SevenZipExtractor.zipBombArtifactCreation.text=Zip Bomb Detected {0}"})
    boolean unpack(AbstractFile archiveFile, ConcurrentHashMap<Long, Archive> depthMap, String password) {
        boolean unpackSuccessful = true; //initialized to true change to false if any files fail to extract and
        boolean hasEncrypted = false;
        boolean fullEncryption = true;
        boolean progressStarted = false;
        final String archiveFilePath = getArchiveFilePath(archiveFile);
        final String escapedArchiveFilePath = FileUtil.escapeFileName(archiveFilePath);
        HashMap<String, ZipFileStatusWrapper> statusMap = new HashMap<>();
        List<AbstractFile> unpackedFiles = Collections.<AbstractFile>emptyList();

        currentArchiveName = archiveFile.getName();

        SevenZipContentReadStream stream = null;
        progress = ProgressHandle.createHandle(Bundle.EmbeddedFileExtractorIngestModule_ArchiveExtractor_moduleName());
        //recursion depth check for zip bomb
        Archive parentAr;
        try {
            blackboard = Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.INFO, "Exception while getting open case.", ex); //NON-NLS
            unpackSuccessful = false;
            return unpackSuccessful;
        }
        if (checkForIngestCancellation(archiveFile)) {
            return false;
        }
        try {
            List<AbstractFile> existingFiles = getAlreadyExtractedFiles(archiveFile, archiveFilePath);
            for (AbstractFile file : existingFiles) {
                statusMap.put(getKeyAbstractFile(file), new ZipFileStatusWrapper(file, ZipFileStatus.EXISTS));
            }
        } catch (TskCoreException | FileTaskFailedException | InterruptedException ex) {
            logger.log(Level.SEVERE, String.format("Error checking if %s has already been processed, skipping", escapedArchiveFilePath), ex); //NON-NLS
            unpackSuccessful = false;
            return unpackSuccessful;
        }
        if (checkForIngestCancellation(archiveFile)) {
            return false;
        }
        parentAr = depthMap.get(archiveFile.getId());
        if (parentAr == null) {
            parentAr = new Archive(0, archiveFile.getId(), archiveFile);
            depthMap.put(archiveFile.getId(), parentAr);
        } else {
            Archive rootArchive = depthMap.get(parentAr.getRootArchiveId());
            if (rootArchive.isFlaggedAsZipBomb()) {
                //skip this archive as the root archive has already been determined to contain a zip bomb     
                unpackSuccessful = false;
                return unpackSuccessful;
            } else if (parentAr.getDepth() == MAX_DEPTH) {
                String details = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.warnDetails.zipBomb",
                        parentAr.getDepth(), FileUtil.escapeFileName(getArchiveFilePath(rootArchive.getArchiveFile())));
                flagRootArchiveAsZipBomb(rootArchive, archiveFile, details, escapedArchiveFilePath);
                unpackSuccessful = false;
                return unpackSuccessful;
            }
        }
        if (checkForIngestCancellation(archiveFile)) {
            return false;
        }
        IInArchive inArchive = null;
        try {
            stream = new SevenZipContentReadStream(new ReadContentInputStream(archiveFile));
            // for RAR files we need to open them explicitly as RAR. Otherwise, if there is a ZIP archive inside RAR archive
            // it will be opened incorrectly when using 7zip's built-in auto-detect functionality.
            // All other archive formats are still opened using 7zip built-in auto-detect functionality.
            ArchiveFormat options = get7ZipOptions(archiveFile);
            if (checkForIngestCancellation(archiveFile)) {
                return false;
            }
            if (password == null) {
                inArchive = SevenZip.openInArchive(options, stream);
            } else {
                inArchive = SevenZip.openInArchive(options, stream, password);
            }
            numItems = inArchive.getNumberOfItems();
            progress.start(numItems);
            progressStarted = true;
            if (checkForIngestCancellation(archiveFile)) {
                return false;
            }
            //setup the archive local root folder
            final String uniqueArchiveFileName = FileUtil.escapeFileName(EmbeddedFileExtractorIngestModule.getUniqueName(archiveFile));
            if (!makeExtractedFilesDirectory(uniqueArchiveFileName)) {
                return false;
            }

            //initialize tree hierarchy to keep track of unpacked file structure
            SevenZipExtractor.UnpackedTree unpackedTree = new SevenZipExtractor.UnpackedTree(moduleDirRelative + "/" + uniqueArchiveFileName, archiveFile);

            long freeDiskSpace;
            try {
                freeDiskSpace = services.getFreeDiskSpace();
            } catch (NullPointerException ex) {
                //If ingest has not been run at least once getFreeDiskSpace() will throw a null pointer exception
                //currently getFreeDiskSpace always returns DISK_FREE_SPACE_UNKNOWN
                freeDiskSpace = IngestMonitor.DISK_FREE_SPACE_UNKNOWN;
            }

            Map<Integer, InArchiveItemDetails> archiveDetailsMap = new HashMap<>();
            for (int inArchiveItemIndex = 0; inArchiveItemIndex < numItems; inArchiveItemIndex++) {
                if (checkForIngestCancellation(archiveFile)) {
                    return false;
                }
                progress.progress(String.format("%s: Analyzing archive metadata and creating local files (%d of %d)", currentArchiveName, inArchiveItemIndex + 1, numItems), 0);
                if (isZipBombArchiveItemCheck(archiveFile, inArchive, inArchiveItemIndex, depthMap, escapedArchiveFilePath)) {
                    unpackSuccessful = false;
                    return unpackSuccessful;
                }

                String pathInArchive = getPathInArchive(inArchive, inArchiveItemIndex, archiveFile);
                byte[] pathBytesInArchive = getPathBytesInArchive(inArchive, inArchiveItemIndex, archiveFile);
                UnpackedTree.UnpackedNode unpackedNode = unpackedTree.addNode(pathInArchive, pathBytesInArchive);
                if (checkForIngestCancellation(archiveFile)) {
                    return false;
                }
                final boolean isEncrypted = (Boolean) inArchive.getProperty(inArchiveItemIndex, PropID.ENCRYPTED);

                if (isEncrypted && password == null) {
                    logger.log(Level.WARNING, "Skipping encrypted file in archive: {0}", pathInArchive); //NON-NLS
                    hasEncrypted = true;
                    unpackSuccessful = false;
                    continue;
                } else {
                    fullEncryption = false;
                }

                // NOTE: item size may return null in case of certain
                // archiving formats. Eg: BZ2
                //check if unpacking this file will result in out of disk space
                //this is additional to zip bomb prevention mechanism
                Long archiveItemSize = (Long) inArchive.getProperty(
                        inArchiveItemIndex, PropID.SIZE);
                if (freeDiskSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN && archiveItemSize != null && archiveItemSize > 0) { //if free space is known and file is not empty.
                    String archiveItemPath = (String) inArchive.getProperty(
                            inArchiveItemIndex, PropID.PATH);
                    long newDiskSpace = freeDiskSpace - archiveItemSize;
                    if (newDiskSpace < MIN_FREE_DISK_SPACE) {
                        String msg = NbBundle.getMessage(SevenZipExtractor.class,
                                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.notEnoughDiskSpace.msg",
                                escapedArchiveFilePath, archiveItemPath);
                        String details = NbBundle.getMessage(SevenZipExtractor.class,
                                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.notEnoughDiskSpace.details");
                        services.postMessage(IngestMessage.createErrorMessage(MODULE_NAME, msg, details));
                        logger.log(Level.INFO, "Skipping archive item due to insufficient disk space: {0}, {1}", new String[]{escapedArchiveFilePath, archiveItemPath}); //NON-NLS
                        logger.log(Level.INFO, "Available disk space: {0}", new Object[]{freeDiskSpace}); //NON-NLS
                        unpackSuccessful = false;
                        continue; //skip this file
                    } else {
                        //update est. disk space during this archive, so we don't need to poll for every file extracted
                        freeDiskSpace = newDiskSpace;
                    }
                }
                if (checkForIngestCancellation(archiveFile)) {
                    return false;
                }
                final String uniqueExtractedName = FileUtil.escapeFileName(uniqueArchiveFileName + File.separator + (inArchiveItemIndex / 1000) + File.separator + inArchiveItemIndex);
                final String localAbsPath = moduleDirAbsolute + File.separator + uniqueExtractedName;
                final String localRelPath = moduleDirRelative + File.separator + uniqueExtractedName;

                //create local dirs and empty files before extracted
                //cannot rely on files in top-bottom order
                File localFile = new File(localAbsPath);
                boolean localFileExists;
                try {
                    if ((Boolean) inArchive.getProperty(inArchiveItemIndex, PropID.IS_FOLDER)) {
                        localFileExists = findOrCreateDirectory(localFile);
                    } else {
                        localFileExists = findOrCreateEmptyFile(localFile);
                    }
                } catch (FileTaskFailedException | InterruptedException ex) {
                    localFileExists = false;
                    logger.log(Level.SEVERE, String.format("Error fiding or creating %s", localFile.getAbsolutePath()), ex); //NON-NLS
                }
                if (checkForIngestCancellation(archiveFile)) {
                    return false;
                }
                // skip the rest of this loop if we couldn't create the file
                //continue will skip details from being added to the map
                if (!localFileExists) {
                    logger.log(Level.SEVERE, String.format("Skipping %s because it could not be created", localFile.getAbsolutePath())); //NON-NLS
                    continue;
                }

                //Store archiveItemIndex with local paths and unpackedNode reference.
                //Necessary for the extract call back to write the current archive
                //file to the correct disk location and to correctly update it's 
                //corresponding unpackedNode
                archiveDetailsMap.put(inArchiveItemIndex, new InArchiveItemDetails(
                        unpackedNode, localAbsPath, localRelPath));
            }

            int[] extractionIndices = getExtractableFilesFromDetailsMap(archiveDetailsMap);
            if (checkForIngestCancellation(archiveFile)) {
                return false;
            }
            StandardIArchiveExtractCallback archiveCallBack
                    = new StandardIArchiveExtractCallback(
                            inArchive, archiveFile, progress,
                            archiveDetailsMap, password, freeDiskSpace);

            //According to the documentation, indices in sorted order are optimal 
            //for efficiency. Hence, the HashMap and linear processing of 
            //inArchiveItemIndex. False indicates non-test mode
            inArchive.extract(extractionIndices, false, archiveCallBack);
            if (checkForIngestCancellation(archiveFile)) {
                return false;
            }
            unpackSuccessful &= archiveCallBack.wasSuccessful();

            archiveDetailsMap = null;

            // add them to the DB. We wait until the end so that we have the metadata on all of the
            // intermediate nodes since the order is not guaranteed
            try {
                unpackedTree.updateOrAddFileToCaseRec(statusMap, archiveFilePath, parentAr, archiveFile, depthMap);
                unpackedTree.commitCurrentTransaction();
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error populating complete derived file hierarchy from the unpacked dir structure", ex); //NON-NLS
                //TODO decide if anything to cleanup, for now bailing               
                unpackedTree.rollbackCurrentTransaction();
            }
                
            if (checkForIngestCancellation(archiveFile)) {
                return false;
            }
            
            // Get the new files to be added to the case.
            unpackedFiles = unpackedTree.getAllFileObjects();
        } catch (SevenZipException | IllegalArgumentException ex) {
            logger.log(Level.WARNING, "Error unpacking file: " + archiveFile, ex); //NON-NLS
            //inbox message

            // print a message if the file is allocated
            if (archiveFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                String msg = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.errUnpacking.msg",
                        currentArchiveName);
                String details = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.errUnpacking.details",
                        escapedArchiveFilePath, ex.getMessage());
                services.postMessage(IngestMessage.createErrorMessage(MODULE_NAME, msg, details));
            }
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException e) {
                    logger.log(Level.SEVERE, "Error closing archive: " + archiveFile, e); //NON-NLS
                }
            }

            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error closing stream after unpacking archive: " + archiveFile, ex); //NON-NLS
                }
            }

            //close progress bar
            if (progressStarted) {
                progress.finish();
            }
        }
        if (checkForIngestCancellation(archiveFile)) {
            return false;
        }
        //create artifact and send user message
        if (hasEncrypted) {
            String encryptionType = fullEncryption ? ENCRYPTION_FULL : ENCRYPTION_FILE_LEVEL;
            try {
                BlackboardArtifact artifact = archiveFile.newAnalysisResult(
                        new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED), 
                        Score.SCORE_NOTABLE, 
                        null, null, encryptionType, 
                        Arrays.asList(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COMMENT, MODULE_NAME, encryptionType)))
                        .getAnalysisResult();

                try {
                    /*
                     * post the artifact which will index the artifact for
                     * keyword search, and fire an event to notify UI of this
                     * new artifact
                     */
                    blackboard.postArtifact(artifact, MODULE_NAME, context.getJobId());
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to post blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.SevenZipExtractor_indexError_message(), artifact.getDisplayName());
                }

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating blackboard artifact for encryption detected for file: " + escapedArchiveFilePath, ex); //NON-NLS
            }

            String msg = NbBundle.getMessage(SevenZipExtractor.class,
                    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.encrFileDetected.msg");
            String details = NbBundle.getMessage(SevenZipExtractor.class,
                    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.encrFileDetected.details",
                    currentArchiveName, MODULE_NAME);
            services.postMessage(IngestMessage.createWarningMessage(MODULE_NAME, msg, details));
        }

        // adding unpacked extracted derived files to the job after closing relevant resources.
        if (!unpackedFiles.isEmpty()) {
            //currently sending a single event for all new files
            services.fireModuleContentEvent(new ModuleContentEvent(archiveFile));
            if (context != null) {
                context.addFilesToJob(unpackedFiles);
            }
        }

        return unpackSuccessful;
    }

    /**
     * Finds or creates a given directory.
     *
     * @param directory The directory.
     *
     * @return True on success, false on failure.
     */
    private boolean findOrCreateDirectory(File directory) throws FileTaskFailedException, InterruptedException {
        if (!fileTaskExecutor.exists(directory)) {
            return fileTaskExecutor.mkdirs(directory);
        } else {
            return true;
        }
    }

    /**
     * Finds or creates a given file. If the file is created, it will be empty.
     *
     * @param file The file.
     *
     * @return True on success, false on failure.
     */
    private boolean findOrCreateEmptyFile(File file) throws FileTaskFailedException, InterruptedException {
        if (!fileTaskExecutor.exists(file)) {
            fileTaskExecutor.mkdirs(file.getParentFile());
            return fileTaskExecutor.createNewFile(file);
        } else {
            return true;
        }
    }

    private Charset detectFilenamesCharset(List<byte[]> byteDatas) {
        Charset detectedCharset = null;
        CharsetDetector charsetDetector = new CharsetDetector();
        int byteSum = 0;
        int fileNum = 0;
        for (byte[] byteData : byteDatas) {
            fileNum++;
            byteSum += byteData.length;
            // Only read ~1000 bytes of filenames in this directory
            if (byteSum >= 1000) {
                break;
            }
        }
        byte[] allBytes = new byte[byteSum];
        int start = 0;
        for (int i = 0; i < fileNum; i++) {
            byte[] byteData = byteDatas.get(i);
            System.arraycopy(byteData, 0, allBytes, start, byteData.length);
            start += byteData.length;
        }
        charsetDetector.setText(allBytes);
        CharsetMatch cm = charsetDetector.detect();
        if (cm != null && cm.getConfidence() >= 90 && Charset.isSupported(cm.getName())) {
            detectedCharset = Charset.forName(cm.getName());
        }
        return detectedCharset;
    }

    /**
     * Produce a list of archive indices needed for the call to extract, which
     * will open the archive and begin unpacking the files.
     */
    private int[] getExtractableFilesFromDetailsMap(
            Map<Integer, InArchiveItemDetails> archiveDetailsMap) {

        Integer[] wrappedExtractionIndices = archiveDetailsMap.keySet()
                .toArray(new Integer[archiveDetailsMap.size()]);

        return Arrays.stream(wrappedExtractionIndices)
                .mapToInt(Integer::intValue)
                .toArray();

    }

    /**
     * UnpackStream used by the SevenZipBindings to do archive extraction. A
     * memory leak exists in the SevenZip library that will not let go of the
     * streams until the entire archive extraction is complete. Instead of
     * creating a new UnpackStream for every file in the archive, instead we
     * just rebase our EncodedFileOutputStream pointer for every new file.
     */
    private final static class UnpackStream implements ISequentialOutStream {

        private EncodedFileOutputStream output;
        private String localAbsPath;
        private int bytesWritten;
        private static final Tika tika = new Tika();
        private String mimeType = "";

        UnpackStream(String localAbsPath) throws IOException {
            this.output = new EncodedFileOutputStream(new FileOutputStream(localAbsPath), TskData.EncodingType.XOR1);
            this.localAbsPath = localAbsPath;
            this.bytesWritten = 0;
        }

        public void setNewOutputStream(String localAbsPath) throws IOException {
            this.output.close();
            this.output = new EncodedFileOutputStream(new FileOutputStream(localAbsPath), TskData.EncodingType.XOR1);
            this.localAbsPath = localAbsPath;
            this.bytesWritten = 0;
            this.mimeType = "";
        }

        public int getSize() {
            return bytesWritten;
        }

        @Override
        public int write(byte[] bytes) throws SevenZipException {
            try {
                // Detect MIME type now while the file is in memory
                if (bytesWritten == 0) {
                    mimeType = tika.detect(bytes);
                }
                output.write(bytes);
                this.bytesWritten += bytes.length;
            } catch (IOException ex) {
                throw new SevenZipException(
                        NbBundle.getMessage(SevenZipExtractor.class,
                                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackStream.write.exception.msg",
                                localAbsPath), ex);
            }
            return bytes.length;
        }

        public String getMIMEType() {
            return mimeType;
        }

        public void close() throws IOException {
            try (EncodedFileOutputStream out = output) {
                out.flush();
            }
        }

    }

    /**
     * Wrapper for necessary details used in StandardIArchiveExtractCallback
     */
    private static class InArchiveItemDetails {

        private final SevenZipExtractor.UnpackedTree.UnpackedNode unpackedNode;
        private final String localAbsPath;
        private final String localRelPath;

        InArchiveItemDetails(
                SevenZipExtractor.UnpackedTree.UnpackedNode unpackedNode,
                String localAbsPath, String localRelPath) {
            this.unpackedNode = unpackedNode;
            this.localAbsPath = localAbsPath;
            this.localRelPath = localRelPath;
        }

        public SevenZipExtractor.UnpackedTree.UnpackedNode getUnpackedNode() {
            return unpackedNode;
        }

        public String getLocalAbsPath() {
            return localAbsPath;
        }

        public String getLocalRelPath() {
            return localRelPath;
        }
    }

    /**
     * Call back class used by extract to expand archive files. This is the most
     * efficient way to process according to the sevenzip binding documentation.
     */
    private static class StandardIArchiveExtractCallback
            implements IArchiveExtractCallback, ICryptoGetTextPassword {

        private final AbstractFile archiveFile;
        private final IInArchive inArchive;
        private UnpackStream unpackStream = null;
        private final Map<Integer, InArchiveItemDetails> archiveDetailsMap;
        private final ProgressHandle progressHandle;

        private int inArchiveItemIndex;

        private long createTimeInSeconds;
        private long modTimeInSeconds;
        private long accessTimeInSeconds;

        private boolean isFolder;
        private final String password;

        private boolean unpackSuccessful = true;

        StandardIArchiveExtractCallback(IInArchive inArchive,
                AbstractFile archiveFile, ProgressHandle progressHandle,
                Map<Integer, InArchiveItemDetails> archiveDetailsMap,
                String password, long freeDiskSpace) {
            this.inArchive = inArchive;
            this.progressHandle = progressHandle;
            this.archiveFile = archiveFile;
            this.archiveDetailsMap = archiveDetailsMap;
            this.password = password;
        }

        /**
         * Get stream is called by the internal framework as it traverses the
         * archive structure. The ISequentialOutStream is where the archive file
         * contents will be expanded and written to the local disk.
         *
         * Skips folders, as there is nothing to extract.
         *
         * @param inArchiveItemIndex current location of the
         * @param mode               Will always be EXTRACT
         *
         * @return
         *
         * @throws SevenZipException
         */
        @Override
        public ISequentialOutStream getStream(int inArchiveItemIndex,
                ExtractAskMode mode) throws SevenZipException {

            this.inArchiveItemIndex = inArchiveItemIndex;

            isFolder = (Boolean) inArchive
                    .getProperty(inArchiveItemIndex, PropID.IS_FOLDER);
            if (isFolder || mode != ExtractAskMode.EXTRACT) {
                return null;
            }

            final String localAbsPath = archiveDetailsMap.get(
                    inArchiveItemIndex).getLocalAbsPath();

            //If the Unpackstream has been allocated, then set the Outputstream 
            //to another file rather than creating a new unpack stream. The 7Zip 
            //binding has a memory leak, so creating new unpack streams will not be
            //dereferenced. As a fix, we create one UnpackStream, and mutate its state,
            //so that there only exists one 8192 byte buffer in memory per archive.
            try {
                if (unpackStream != null) {
                    unpackStream.setNewOutputStream(localAbsPath);
                } else {
                    unpackStream = new UnpackStream(localAbsPath);
                }
            } catch (IOException ex) {
                logger.log(Level.WARNING, String.format("Error opening or setting new stream " //NON-NLS
                        + "for archive file at %s", localAbsPath), ex.getMessage()); //NON-NLS
                return null;
            }

            return unpackStream;
        }

        /**
         * Retrieves the file metadata from the archive before extraction.
         * Called after getStream.
         *
         * @param mode Will always be EXTRACT.
         *
         * @throws SevenZipException
         */
        @Override
        public void prepareOperation(ExtractAskMode mode) throws SevenZipException {
            final Date createTime = (Date) inArchive.getProperty(
                    inArchiveItemIndex, PropID.CREATION_TIME);
            final Date accessTime = (Date) inArchive.getProperty(
                    inArchiveItemIndex, PropID.LAST_ACCESS_TIME);
            final Date writeTime = (Date) inArchive.getProperty(
                    inArchiveItemIndex, PropID.LAST_MODIFICATION_TIME);

            createTimeInSeconds = createTime == null ? 0L
                    : createTime.getTime() / 1000;
            modTimeInSeconds = writeTime == null ? 0L
                    : writeTime.getTime() / 1000;
            accessTimeInSeconds = accessTime == null ? 0L
                    : accessTime.getTime() / 1000;

            progressHandle.progress(archiveFile.getName() + ": "
                    + (String) inArchive.getProperty(inArchiveItemIndex, PropID.PATH),
                    inArchiveItemIndex);

        }

        /**
         * Updates the unpackedNode data in the tree after the archive has been
         * expanded to local disk.
         *
         * @param result - ExtractOperationResult
         *
         * @throws SevenZipException
         */
        @Override
        public void setOperationResult(ExtractOperationResult result) throws SevenZipException {

            final SevenZipExtractor.UnpackedTree.UnpackedNode unpackedNode
                    = archiveDetailsMap.get(inArchiveItemIndex).getUnpackedNode();
            final String localRelPath = archiveDetailsMap.get(
                    inArchiveItemIndex).getLocalRelPath();
            if (isFolder) {
                unpackedNode.addDerivedInfo(0,
                        !(Boolean) inArchive.getProperty(inArchiveItemIndex, PropID.IS_FOLDER),
                        0L, createTimeInSeconds, accessTimeInSeconds, modTimeInSeconds,
                        localRelPath);
                return;
            } else {
                unpackedNode.setMimeType(unpackStream.getMIMEType());
            }

            final String localAbsPath = archiveDetailsMap.get(
                    inArchiveItemIndex).getLocalAbsPath();
            if (result != ExtractOperationResult.OK) {
                if (archiveFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.UNALLOC)) {
                    logger.log(Level.WARNING, "Extraction of : {0} encountered error {1} (file is unallocated and may be corrupt)", //NON-NLS
                            new Object[]{localAbsPath, result});
                } else {
                    logger.log(Level.WARNING, "Extraction of : {0} encountered error {1}", //NON-NLS
                            new Object[]{localAbsPath, result});
                }
                unpackSuccessful = false;
            }

            //record derived data in unode, to be traversed later after unpacking the archive
            unpackedNode.addDerivedInfo(unpackStream.getSize(),
                    !(Boolean) inArchive.getProperty(inArchiveItemIndex, PropID.IS_FOLDER),
                    0L, createTimeInSeconds, accessTimeInSeconds, modTimeInSeconds, localRelPath);

            try {
                unpackStream.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing unpack stream for file: {0}", localAbsPath); //NON-NLS
            }
        }

        @Override
        public void setTotal(long value) throws SevenZipException {
            //Not necessary for extract, left intenionally blank
        }

        @Override
        public void setCompleted(long value) throws SevenZipException {
            //Not necessary for extract, left intenionally blank
        }

        /**
         * Called when opening encrypted archive files.
         *
         * @return - Password supplied by user
         *
         * @throws SevenZipException
         */
        @Override
        public String cryptoGetTextPassword() throws SevenZipException {
            return password;
        }

        public boolean wasSuccessful() {
            return unpackSuccessful;
        }
    }

    /**
     * Representation of the files in the archive. Used to track of local tree
     * file hierarchy, archive depth, and files created to easily and reliably
     * get parent AbstractFile for unpacked file. So that we don't have to
     * depend on type of traversal of unpacked files handed to us by 7zip
     * unpacker.
     */
    private class UnpackedTree {

        final UnpackedNode rootNode;
        private int nodesProcessed = 0;
            
        // It is significantly faster to add the DerivedFiles to the case on a transaction,
        // but we don't want to hold the transaction (and case write lock) for the entire 
        // stage. Instead, we use the same transaction for MAX_TRANSACTION_SIZE database operations
        // and then commit that transaction and start a new one, giving at least a short window
        // for other processes.
        private CaseDbTransaction currentTransaction = null;
        private long transactionCounter = 0;
        private final static long MAX_TRANSACTION_SIZE = 1000;

        /**
         *
         * @param localPathRoot Path in module output folder that files will be
         *                      saved to
         * @param archiveFile   Archive file being extracted
         */
        UnpackedTree(String localPathRoot, AbstractFile archiveFile) {
            this.rootNode = new UnpackedNode();
            this.rootNode.setFile(archiveFile);
            this.rootNode.setFileName(archiveFile.getName());
            this.rootNode.setLocalRelPath(localPathRoot);
        }

        /**
         * Creates a node in the tree at the given path. Makes intermediate
         * nodes if needed. If a node already exists at that path, it is
         * returned.
         *
         * @param filePath file path with 1 or more tokens separated by /
         *
         * @return child node for the last file token in the filePath
         */
        UnpackedNode addNode(String filePath, byte[] filePathBytes) {
            String[] toks = filePath.split("[\\/\\\\]");
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < toks.length; ++i) {
                if (!toks[i].isEmpty()) {
                    tokens.add(toks[i]);
                }
            }

            List<byte[]> byteTokens;
            if (filePathBytes == null) {
                return addNode(rootNode, tokens, null);
            } else {
                byteTokens = new ArrayList<>(tokens.size());
                int last = 0;
                for (int i = 0; i < filePathBytes.length; i++) {
                    if (filePathBytes[i] == '/') {
                        int len = i - last;
                        if (len > 0) {
                            byte[] arr = new byte[len];
                            System.arraycopy(filePathBytes, last, arr, 0, len);
                            byteTokens.add(arr);
                        }
                        last = i + 1;
                    }
                }
                int len = filePathBytes.length - last;
                if (len > 0) {
                    byte[] arr = new byte[len];
                    System.arraycopy(filePathBytes, last, arr, 0, len);
                    byteTokens.add(arr);
                }

                if (tokens.size() != byteTokens.size()) {
                    String rootFileInfo = "(unknown)";
                    if (rootNode.getFile() != null) {
                        rootFileInfo = rootNode.getFile().getParentPath() + rootNode.getFile().getName() 
                                + "(ID: " + rootNode.getFile().getId() + ")";
                    }
                    logger.log(Level.WARNING, "Could not map path bytes to path string while extracting archive {0} (path string: \"{1}\", bytes: {2})", 
                            new Object[]{rootFileInfo, this.rootNode.getFile().getId(), filePath, bytesToString(filePathBytes)});
                    return addNode(rootNode, tokens, null);
                }
            }

            return addNode(rootNode, tokens, byteTokens);
        }
        
        /**
         * Convert byte array to string representation.
         * 
         * @param bytes Byte array
         * 
         * @return Byte array as lower case hex string.
         */
        private String bytesToString(byte[] bytes) {
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        }

        /**
         * recursive method that traverses the path
         *
         * @param parent
         * @param tokenPath
         * @param tokenPathBytes
         *
         * @return
         */
        private UnpackedNode addNode(UnpackedNode parent,
                List<String> tokenPath, List<byte[]> tokenPathBytes) {
            // we found all of the tokens
            if (tokenPath.isEmpty()) {
                return parent;
            }

            // get the next name in the path and look it up
            String childName = tokenPath.remove(0);
            byte[] childNameBytes = null;
            if (tokenPathBytes != null) {
                childNameBytes = tokenPathBytes.remove(0);
            }
            UnpackedNode child = parent.getChild(childName);
            // create new node
            if (child == null) {
                child = new UnpackedNode(childName, parent);
                child.setFileNameBytes(childNameBytes);
                parent.addChild(child);
            }

            // go down one more level
            return addNode(child, tokenPath, tokenPathBytes);
        }

        /**
         * Get the root file objects (after createDerivedFiles() ) of this tree,
         * so that they can be rescheduled.
         *
         * @return root objects of this unpacked tree
         */
        List<AbstractFile> getRootFileObjects() {
            List<AbstractFile> ret = new ArrayList<>();
            rootNode.getChildren().forEach((child) -> {
                ret.add(child.getFile());
            });
            return ret;
        }

        /**
         * Get the all file objects (after createDerivedFiles() ) of this tree,
         * so that they can be rescheduled.
         *
         * @return all file objects of this unpacked tree
         */
        List<AbstractFile> getAllFileObjects() {
            List<AbstractFile> ret = new ArrayList<>();
            rootNode.getChildren().forEach((child) -> {
                getAllFileObjectsRec(ret, child);
            });
            return ret;
        }

        private void getAllFileObjectsRec(List<AbstractFile> list, UnpackedNode parent) {
            list.add(parent.getFile());
            parent.getChildren().forEach((child) -> {
                getAllFileObjectsRec(list, child);
            });
        }

        /**
         * Traverse the tree top-down after unzipping is done and create derived
         * files for the entire hierarchy
         */
        void updateOrAddFileToCaseRec(HashMap<String, ZipFileStatusWrapper> statusMap, String archiveFilePath, Archive parentAr, AbstractFile archiveFile, ConcurrentHashMap<Long, Archive> depthMap) throws TskCoreException, NoCurrentCaseException {
            final FileManager fileManager = Case.getCurrentCaseThrows().getServices().getFileManager();
            for (UnpackedNode child : rootNode.getChildren()) {
                updateOrAddFileToCaseRec(child, fileManager, statusMap, archiveFilePath, parentAr, archiveFile, depthMap);
            }
        }

        /**
         * Add derived files to the case if they do not exist, update the
         * derived file data if the new file contains more information than the
         * existing one, and do nothing if the existing information is complete.
         *
         * @param node            - the UnpackedNode for the file which is being
         *                        added or updated
         * @param fileManager     - the file manager to perform the adding or
         *                        updating
         * @param statusMap       - the map of existing files and their status
         * @param archiveFilePath - the archive file path for the unpacked node
         * @param parentAr        - the parent archive as an Archive object
         * @param archiveFile     - the parent archive as an AbstractFile
         * @param depthMap        - the depth map (to prevent zip bombs)
         *
         * @throws TskCoreException
         */
        private void updateOrAddFileToCaseRec(UnpackedNode node, FileManager fileManager, HashMap<String, ZipFileStatusWrapper> statusMap, String archiveFilePath, Archive parentAr, AbstractFile archiveFile, ConcurrentHashMap<Long, Archive> depthMap) throws TskCoreException {
            DerivedFile df;
            progress.progress(String.format("%s: Adding/updating files in case database (%d of %d)", currentArchiveName, ++nodesProcessed, numItems));
            try {
                String nameInDatabase = getKeyFromUnpackedNode(node, archiveFilePath);
                ZipFileStatusWrapper existingFile = nameInDatabase == null ? null : statusMap.get(nameInDatabase);
                if (existingFile == null) {
                    df = Case.getCurrentCaseThrows().getSleuthkitCase().addDerivedFile(node.getFileName(), node.getLocalRelPath(), node.getSize(),
                            node.getCtime(), node.getCrtime(), node.getAtime(), node.getMtime(),
                            node.isIsFile(), node.getParent().getFile(), "", MODULE_NAME,
                            "", "", TskData.EncodingType.XOR1, getCurrentTransaction());
                    statusMap.put(getKeyAbstractFile(df), new ZipFileStatusWrapper(df, ZipFileStatus.EXISTS));
                } else {
                    String key = getKeyAbstractFile(existingFile.getFile());
                    if (existingFile.getStatus() == ZipFileStatus.EXISTS && existingFile.getFile().getSize() < node.getSize()) {
                        existingFile.setStatus(ZipFileStatus.UPDATE);
                        statusMap.put(key, existingFile);
                    }
                    if (existingFile.getStatus() == ZipFileStatus.UPDATE) {
                        //if the we are updating a file and its mime type was octet-stream we want to re-type it
                        String mimeType = existingFile.getFile().getMIMEType().equalsIgnoreCase("application/octet-stream") ? null : existingFile.getFile().getMIMEType();
                        df = Case.getCurrentCaseThrows().getSleuthkitCase().updateDerivedFile((DerivedFile) existingFile.getFile(), node.getLocalRelPath(), node.getSize(),
                                node.getCtime(), node.getCrtime(), node.getAtime(), node.getMtime(),
                                node.isIsFile(), mimeType, "", MODULE_NAME,
                                "", "", TskData.EncodingType.XOR1, existingFile.getFile().getParent(), getCurrentTransaction());
                    } else {
                        //ALREADY CURRENT - SKIP
                        statusMap.put(key, new ZipFileStatusWrapper(existingFile.getFile(), ZipFileStatus.SKIP));
                        df = (DerivedFile) existingFile.getFile();
                    }
                }
                node.setFile(df);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error adding a derived file to db:" + node.getFileName(), ex); //NON-NLS
                throw new TskCoreException(
                        NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackedTree.exception.msg",
                                node.getFileName()), ex);
            }

            // Determine encoding of children
            if (node.getChildren().size() > 0) {
                String names = "";
                ArrayList<byte[]> byteDatas = new ArrayList<>();
                for (UnpackedNode child : node.getChildren()) {
                    byte[] childBytes = child.getFileNameBytes();
                    if (childBytes != null) {
                        byteDatas.add(childBytes);
                    }
                    names += child.getFileName();
                }
                Charset detectedCharset = detectFilenamesCharset(byteDatas);

                // If a charset was detected, transcode filenames accordingly
                if (detectedCharset != null && detectedCharset.canEncode()) {
                    for (UnpackedNode child : node.getChildren()) {
                        byte[] childBytes = child.getFileNameBytes();
                        if (childBytes != null) {
                            String decodedName = new String(childBytes, detectedCharset);
                            child.setFileName(decodedName);
                        }
                    }
                }
            }
            
            // Check for zip bombs
            if (isSevenZipExtractionSupported(node.getMimeType())) {
                Archive child = new Archive(parentAr.getDepth() + 1, parentAr.getRootArchiveId(), archiveFile);
                parentAr.addChild(child);
                depthMap.put(node.getFile().getId(), child);
            }

            //recurse adding the children if this file was incomplete the children presumably need to be added
            for (UnpackedNode child : node.getChildren()) {
                updateOrAddFileToCaseRec(child, fileManager, statusMap, getKeyFromUnpackedNode(node, archiveFilePath), parentAr, archiveFile, depthMap);
            }
        }
        
        /**
         * Get the current transaction being used in updateOrAddFileToCaseRec().
         * If there is no transaction, one will be started. After the
         * transaction has been used MAX_TRANSACTION_SIZE, it will be committed and a
         * new transaction will be opened.
         * 
         * @return The open transaction.
         * 
         * @throws TskCoreException 
         */
        private CaseDbTransaction getCurrentTransaction() throws TskCoreException {
        
            if (currentTransaction == null) {
                startTransaction();
            } 

            if (transactionCounter > MAX_TRANSACTION_SIZE) {
                commitCurrentTransaction();
                startTransaction();
            }

            transactionCounter++;
            return currentTransaction;
        }
    
        /**
         * Open a transaction.
         * 
         * @throws TskCoreException 
         */
        private void startTransaction() throws TskCoreException {
            try {
                currentTransaction = Case.getCurrentCaseThrows().getSleuthkitCase().beginTransaction();
                transactionCounter = 0;
            } catch (NoCurrentCaseException ex) {
                throw new TskCoreException("Case is closed");
            }
        }
    
        /**
         * Commit the current transaction.
         * 
         * @throws TskCoreException 
         */
        private void commitCurrentTransaction() throws TskCoreException {
            if (currentTransaction != null) {
                currentTransaction.commit();
                currentTransaction = null;
            }
        }
    
        /**
         * Rollback the current transaction.
         */
        private void rollbackCurrentTransaction() {
            if (currentTransaction != null) {
                try {
                    currentTransaction.rollback();
                    currentTransaction = null;
                } catch (TskCoreException ex) {
                    // Ignored
                }
            }
        }

        /**
         * A node in the unpacked tree that represents a file or folder.
         */
        private class UnpackedNode {

            private String fileName;
            private byte[] fileNameBytes;
            private AbstractFile file;
            private final List<UnpackedNode> children = new ArrayList<>();
            private String localRelPath = "";
            private long size;
            private long ctime, crtime, atime, mtime;
            private boolean isFile;
            private String mimeType = "";
            private UnpackedNode parent;

            //root constructor
            UnpackedNode() {
            }

            //child node constructor
            UnpackedNode(String fileName, UnpackedNode parent) {
                this.fileName = fileName;
                this.parent = parent;
                this.localRelPath = parent.getLocalRelPath() + File.separator + fileName;
            }

            long getCtime() {
                return ctime;
            }

            long getCrtime() {
                return crtime;
            }

            long getAtime() {
                return atime;
            }

            long getMtime() {
                return mtime;
            }

            void setFileName(String fileName) {
                this.fileName = fileName;
            }

            /**
             * Add a child to the list of child nodes associated with this node.
             *
             * @param child - the node which is a child node of this node
             */
            void addChild(UnpackedNode child) {
                children.add(child);
            }

            /**
             * Get this nodes list of child UnpackedNode
             *
             * @return children - the UnpackedNodes which are children of this
             *         node.
             */
            List<UnpackedNode> getChildren() {
                return children;
            }

            /**
             * Gets the parent node of this node.
             *
             * @return - the parent UnpackedNode
             */
            UnpackedNode getParent() {
                return parent;
            }

            void addDerivedInfo(long size,
                    boolean isFile,
                    long ctime, long crtime, long atime, long mtime, String relLocalPath) {
                this.size = size;
                this.isFile = isFile;
                this.ctime = ctime;
                this.crtime = crtime;
                this.atime = atime;
                this.mtime = mtime;
                this.localRelPath = relLocalPath;
            }

            void setFile(AbstractFile file) {
                this.file = file;
            }
            
            void setMimeType(String mimeType) {
                this.mimeType = mimeType;
            }
            
            String getMimeType() {
                return mimeType;
            }

            /**
             * get child by name or null if it doesn't exist
             *
             * @param childFileName
             *
             * @return
             */
            UnpackedNode getChild(String childFileName) {
                UnpackedNode ret = null;
                for (UnpackedNode child : children) {
                    if (child.getFileName().equals(childFileName)) {
                        ret = child;
                        break;
                    }
                }
                return ret;
            }

            String getFileName() {
                return fileName;
            }

            AbstractFile getFile() {
                return file;
            }

            String getLocalRelPath() {
                return localRelPath;
            }

            /**
             * Set the local relative path associated with this UnpackedNode
             *
             * @param localRelativePath - the local relative path to be
             *                          associated with this node.
             */
            void setLocalRelPath(String localRelativePath) {
                localRelPath = localRelativePath;
            }

            long getSize() {
                return size;
            }

            boolean isIsFile() {
                return isFile;
            }

            void setFileNameBytes(byte[] fileNameBytes) {
                if (fileNameBytes != null) {
                    this.fileNameBytes = Arrays.copyOf(fileNameBytes, fileNameBytes.length);
                }
            }

            byte[] getFileNameBytes() {
                if (fileNameBytes == null) {
                    return null;
                }
                return Arrays.copyOf(fileNameBytes, fileNameBytes.length);
            }
        }
    }

    /**
     * Class to keep track of an objects id and its depth in the archive
     * structure.
     */
    static class Archive {

        //depth will be 0 for the root archive unpack was called on, and increase as unpack recurses down through archives contained within
        private final int depth;
        private final List<Archive> children;
        private final long rootArchiveId;
        private boolean flaggedAsZipBomb = false;
        private final AbstractFile archiveFile;

        /**
         * Create a new Archive object.
         *
         * @param depth         the depth in the archive structure - 0 will be
         *                      the root archive unpack was called on, and it
         *                      will increase as unpack recurses down through
         *                      archives contained within
         * @param rootArchiveId the unique object id of the root parent archive
         *                      of this archive
         * @param archiveFile   the AbstractFile which this Archive object
         *                      represents
         */
        Archive(int depth, long rootArchiveId, AbstractFile archiveFile) {
            this.children = new ArrayList<>();
            this.depth = depth;
            this.rootArchiveId = rootArchiveId;
            this.archiveFile = archiveFile;
        }

        /**
         * Add a child to the list of child archives associated with this
         * archive.
         *
         * @param child - the archive which is a child archive of this archive
         */
        void addChild(Archive child) {
            children.add(child);
        }

        /**
         * Set the flag which identifies whether this file has been determined
         * to be a zip bomb to true.
         */
        synchronized void flagAsZipBomb() {
            flaggedAsZipBomb = true;
        }

        /**
         * Gets whether or not this archive has been flagged as a zip bomb.
         *
         * @return True when flagged as a zip bomb, false if it is not flagged
         */
        synchronized boolean isFlaggedAsZipBomb() {
            return flaggedAsZipBomb;
        }

        /**
         * Get the AbstractFile which this Archive object represents.
         *
         * @return archiveFile - the AbstractFile which this Archive represents.
         */
        AbstractFile getArchiveFile() {
            return archiveFile;
        }

        /**
         * Get the object id of the root archive which contained this archive.
         *
         * @return rootArchiveId - the objectID of the root archive
         */
        long getRootArchiveId() {
            return rootArchiveId;
        }

        /**
         * Get the object id of this archive.
         *
         * @return the unique objectId of this archive from its AbstractFile
         */
        long getObjectId() {
            return archiveFile.getId();
        }

        /**
         * Get archive depth of this archive
         *
         * @return depth - an integer representing that represents how many
         *         times the upack method has been recursed from the root
         *         archive unpack was called on
         */
        int getDepth() {
            return depth;
        }
    }

    /**
     * A class which wraps an AbstractFile and an enum identifing whether the
     * file which exists in the case database is current
     */
    private final class ZipFileStatusWrapper {

        private final AbstractFile abstractFile;
        private ZipFileStatus zipStatus;

        /**
         * Construct a ZipFileStatusWrapper to wrap the given AbstractFile and
         * status
         *
         * @param file   - The AbstractFile which exists in the case database
         * @param status - an indicator of if the file information is current
         */
        private ZipFileStatusWrapper(AbstractFile file, ZipFileStatus status) {
            abstractFile = file;
            zipStatus = status;
        }

        /**
         * Get the AbstractFile contained in this object
         *
         * @return archiveFile - The archiveFile this object wraps
         */
        private AbstractFile getFile() {
            return abstractFile;
        }

        /**
         * Get whether the file should be skipped or updated
         *
         * @return zipStatus - an Enum value indicating if the file is current
         */
        private ZipFileStatus getStatus() {
            return zipStatus;
        }

        /**
         * Set the zipStatus of the file being wrapped when it changes
         *
         * @param status - an Enum value indicating if the file is current
         */
        private void setStatus(ZipFileStatus status) {
            zipStatus = status;
        }

    }

    /**
     * The status of the file from the archive in regards to whether it should
     * be updated
     */
    private enum ZipFileStatus {
        UPDATE, //Should be updated //NON-NLS
        SKIP, //File is current can be skipped //NON-NLS
        EXISTS //File exists but it is unknown if it is current //NON-NLS
    }
}
