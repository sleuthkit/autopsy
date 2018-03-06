/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2018 Basis Technology Corp.
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import net.sf.sevenzipjbinding.ArchiveFormat;
import static net.sf.sevenzipjbinding.ArchiveFormat.RAR;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.EncodedFileOutputStream;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

class SevenZipExtractor {

    private static final Logger logger = Logger.getLogger(SevenZipExtractor.class.getName());
    private IngestServices services = IngestServices.getInstance();
    private final IngestJobContext context;
    private final FileTypeDetector fileTypeDetector;
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz",}; // NON-NLS
    //encryption type strings
    private static final String ENCRYPTION_FILE_LEVEL = NbBundle.getMessage(EmbeddedFileExtractorIngestModule.class,
            "EmbeddedFileExtractorIngestModule.ArchiveExtractor.encryptionFileLevel");
    private static final String ENCRYPTION_FULL = NbBundle.getMessage(EmbeddedFileExtractorIngestModule.class,
            "EmbeddedFileExtractorIngestModule.ArchiveExtractor.encryptionFull");
    //zip bomb detection
    private static final int MAX_DEPTH = 4;
    private static final int MAX_COMPRESSION_RATIO = 600;
    private static final long MIN_COMPRESSION_RATIO_SIZE = 500 * 1000000L;
    private static final long MIN_FREE_DISK_SPACE = 1 * 1000 * 1000000L; //1GB
    //counts archive depth
    private ArchiveDepthCountTree archiveDepthCountTree;

    private String moduleDirRelative;
    private String moduleDirAbsolute;

    private Blackboard blackboard;

    private String getLocalRootAbsPath(String uniqueArchiveFileName) {
        return moduleDirAbsolute + File.separator + uniqueArchiveFileName;
    }

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

    SevenZipExtractor(IngestJobContext context, FileTypeDetector fileTypeDetector, String moduleDirRelative, String moduleDirAbsolute) throws SevenZipNativeInitializationException {
        if (!SevenZip.isInitializedSuccessfully() && (SevenZip.getLastInitializationException() == null)) {
            SevenZip.initSevenZipFromPlatformJAR();
        }
        this.context = context;
        this.fileTypeDetector = fileTypeDetector;
        this.moduleDirRelative = moduleDirRelative;
        this.moduleDirAbsolute = moduleDirAbsolute;
        this.archiveDepthCountTree = new ArchiveDepthCountTree();
    }

    /**
     * Checks whether extraction is supported for a file, based on MIME type.
     *
     * @param abstractFile The AbstractFilw whose mimetype is to be determined.
     *
     * @return This method returns true if the file format is currently
     *         supported. Else it returns false.
     */
    boolean isSevenZipExtractionSupported(AbstractFile abstractFile) {
        String abstractFileMimeType = fileTypeDetector.getMIMEType(abstractFile);
        for (SupportedArchiveExtractionFormats s : SupportedArchiveExtractionFormats.values()) {
            if (s.toString().equals(abstractFileMimeType)) {
                return true;
            }
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
     * @param archiveName     the parent archive
     * @param archiveFileItem the archive item
     *
     * @return true if potential zip bomb, false otherwise
     */
    private boolean isZipBombArchiveItemCheck(AbstractFile archiveFile, ISimpleInArchiveItem archiveFileItem) {
        try {
            final Long archiveItemSize = archiveFileItem.getSize();

            //skip the check for small files
            if (archiveItemSize == null || archiveItemSize < MIN_COMPRESSION_RATIO_SIZE) {
                return false;
            }

            final Long archiveItemPackedSize = archiveFileItem.getPackedSize();

            if (archiveItemPackedSize == null || archiveItemPackedSize <= 0) {
                logger.log(Level.WARNING, "Cannot getting compression ratio, cannot detect if zipbomb: {0}, item: {1}", new Object[]{archiveFile.getName(), archiveFileItem.getPath()}); //NON-NLS
                return false;
            }

            int cRatio = (int) (archiveItemSize / archiveItemPackedSize);

            if (cRatio >= MAX_COMPRESSION_RATIO) {
                String itemName = archiveFileItem.getPath();
                logger.log(Level.INFO, "Possible zip bomb detected, compression ration: {0} for in archive item: {1}", new Object[]{cRatio, itemName}); //NON-NLS
                String msg = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.isZipBombCheck.warnMsg", archiveFile.getName(), itemName);
                String path;
                try {
                    path = archiveFile.getUniquePath();
                } catch (TskCoreException ex) {
                    path = archiveFile.getParentPath() + archiveFile.getName();
                }
                String details = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.isZipBombCheck.warnDetails", cRatio, path);
                //MessageNotifyUtil.Notify.error(msg, details);
                services.postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), msg, details));
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
     * Check file extension and return appropriate input options for
     * SevenZip.openInArchive()
     *
     * @param archiveFile file to check file extension
     *
     * @return input parameter for SevenZip.openInArchive()
     */
    private ArchiveFormat get7ZipOptions(AbstractFile archiveFile) {
        // try to get the file type from the BB
        String detectedFormat = null;
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
     * Unpack the file to local folder and return a list of derived files
     *
     * @param pipelineContext current ingest context
     * @param archiveFile     file to unpack
     *
     * @return list of unpacked derived files
     */
    @Messages({"SevenZipExtractor.indexError.message=Failed to index encryption detected artifact for keyword search."})
    void unpack(AbstractFile archiveFile) {
        try {
            blackboard = Case.getOpenCase().getServices().getBlackboard();
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.INFO, "Exception while getting open case.", ex); //NON-NLS
            return;
        }
        String archiveFilePath;
        try {
            archiveFilePath = archiveFile.getUniquePath();
        } catch (TskCoreException ex) {
            archiveFilePath = archiveFile.getParentPath() + archiveFile.getName();
        }

        //check if already has derived files, skip
        try {
            if (archiveFile.hasChildren()) {
                //check if local unpacked dir exists
                if (new File(EmbeddedFileExtractorIngestModule.getUniqueName(archiveFile)).exists()) {
                    logger.log(Level.INFO, "File already has been processed as it has children and local unpacked file, skipping: {0}", archiveFilePath); //NON-NLS
                    return;
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.INFO, "Error checking if file already has been processed, skipping: {0}", archiveFilePath); //NON-NLS
            return;
        }

        List<AbstractFile> unpackedFiles = Collections.<AbstractFile>emptyList();

        //recursion depth check for zip bomb
        final long archiveId = archiveFile.getId();
        SevenZipExtractor.ArchiveDepthCountTree.Archive parentAr = archiveDepthCountTree.findArchive(archiveId);
        if (parentAr == null) {
            parentAr = archiveDepthCountTree.addArchive(null, archiveId);
        } else if (parentAr.getDepth() == MAX_DEPTH) {
            String msg = NbBundle.getMessage(SevenZipExtractor.class,
                    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.warnMsg.zipBomb", archiveFile.getName());
            String details = NbBundle.getMessage(SevenZipExtractor.class,
                    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.warnDetails.zipBomb",
                    parentAr.getDepth(), archiveFilePath);
            //MessageNotifyUtil.Notify.error(msg, details);
            services.postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), msg, details));
            return;
        }

        boolean hasEncrypted = false;
        boolean fullEncryption = true;

        ISevenZipInArchive inArchive = null;
        SevenZipContentReadStream stream = null;

        final ProgressHandle progress = ProgressHandle.createHandle(Bundle.EmbeddedFileExtractorIngestModule_ArchiveExtractor_moduleName());
        int processedItems = 0;

        boolean progressStarted = false;
        try {
            stream = new SevenZipContentReadStream(new ReadContentInputStream(archiveFile));

            // for RAR files we need to open them explicitly as RAR. Otherwise, if there is a ZIP archive inside RAR archive
            // it will be opened incorrectly when using 7zip's built-in auto-detect functionality.
            // All other archive formats are still opened using 7zip built-in auto-detect functionality.
            ArchiveFormat options = get7ZipOptions(archiveFile);
            inArchive = SevenZip.openInArchive(options, stream);

            int numItems = inArchive.getNumberOfItems();
            logger.log(Level.INFO, "Count of items in archive: {0}: {1}", new Object[]{archiveFilePath, numItems}); //NON-NLS
            progress.start(numItems);
            progressStarted = true;

            final ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            //setup the archive local root folder
            final String uniqueArchiveFileName = FileUtil.escapeFileName(EmbeddedFileExtractorIngestModule.getUniqueName(archiveFile));
            final String localRootAbsPath = getLocalRootAbsPath(uniqueArchiveFileName);
            final File localRoot = new File(localRootAbsPath);
            if (!localRoot.exists()) {
                try {
                    localRoot.mkdirs();
                } catch (SecurityException e) {
                    logger.log(Level.SEVERE, "Error setting up output path for archive root: {0}", localRootAbsPath); //NON-NLS
                    //bail
                    return;
                }
            }

            //initialize tree hierarchy to keep track of unpacked file structure
            SevenZipExtractor.UnpackedTree unpackedTree = new SevenZipExtractor.UnpackedTree(moduleDirRelative + "/" + uniqueArchiveFileName, archiveFile);

            long freeDiskSpace = services.getFreeDiskSpace();

            //unpack and process every item in archive
            int itemNumber = 0;
            for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                String pathInArchive = item.getPath();

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
                        pathInArchive = "/" + archName + "/" + Integer.toString(itemNumber);
                    } else {
                        pathInArchive = "/" + useName;
                    }

                    String msg = NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.unknownPath.msg",
                            archiveFilePath, pathInArchive);
                    logger.log(Level.WARNING, msg);

                }
                archiveFilePath = FileUtil.escapeFileName(archiveFilePath);
                ++itemNumber;

                //check if possible zip bomb
                if (isZipBombArchiveItemCheck(archiveFile, item)) {
                    continue; //skip the item
                }

                //find this node in the hierarchy, create if needed
                SevenZipExtractor.UnpackedTree.UnpackedNode unpackedNode = unpackedTree.addNode(pathInArchive);

                String fileName = unpackedNode.getFileName();

                //update progress bar
                progress.progress(archiveFile.getName() + ": " + fileName, processedItems);

                final boolean isEncrypted = item.isEncrypted();
                final boolean isDir = item.isFolder();

                if (isEncrypted) {
                    logger.log(Level.WARNING, "Skipping encrypted file in archive: {0}", pathInArchive); //NON-NLS
                    hasEncrypted = true;
                    continue;
                } else {
                    fullEncryption = false;
                }

                // NOTE: item.getSize() may return null in case of certain
                // archiving formats. Eg: BZ2
                Long size = item.getSize();

                //check if unpacking this file will result in out of disk space
                //this is additional to zip bomb prevention mechanism
                if (freeDiskSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN && size != null && size > 0) { //if free space is known and file is not empty.
                    long newDiskSpace = freeDiskSpace - size;
                    if (newDiskSpace < MIN_FREE_DISK_SPACE) {
                        String msg = NbBundle.getMessage(SevenZipExtractor.class,
                                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.notEnoughDiskSpace.msg",
                                archiveFilePath, fileName);
                        String details = NbBundle.getMessage(SevenZipExtractor.class,
                                "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.notEnoughDiskSpace.details");
                        //MessageNotifyUtil.Notify.error(msg, details);
                        services.postMessage(IngestMessage.createErrorMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), msg, details));
                        logger.log(Level.INFO, "Skipping archive item due to insufficient disk space: {0}, {1}", new Object[]{archiveFilePath, fileName}); //NON-NLS
                        logger.log(Level.INFO, "Available disk space: {0}", new Object[]{freeDiskSpace}); //NON-NLS
                        continue; //skip this file
                    } else {
                        //update est. disk space during this archive, so we don't need to poll for every file extracted
                        freeDiskSpace = newDiskSpace;
                    }
                }

                final String uniqueExtractedName = FileUtil.escapeFileName(uniqueArchiveFileName + File.separator + (item.getItemIndex() / 1000) + File.separator + item.getItemIndex() + "_" + new File(pathInArchive).getName());

                //final String localRelPath = unpackDir + File.separator + localFileRelPath;
                final String localRelPath = moduleDirRelative + File.separator + uniqueExtractedName;
                final String localAbsPath = moduleDirAbsolute + File.separator + uniqueExtractedName;

                //create local dirs and empty files before extracted
                File localFile = new java.io.File(localAbsPath);
                //cannot rely on files in top-bottom order
                if (!localFile.exists()) {
                    try {
                        if (isDir) {
                            localFile.mkdirs();
                        } else {
                            localFile.getParentFile().mkdirs();
                            try {
                                localFile.createNewFile();
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, "Error creating extracted file: " + localFile.getAbsolutePath(), e); //NON-NLS
                            }
                        }
                    } catch (SecurityException e) {
                        logger.log(Level.SEVERE, "Error setting up output path for unpacked file: {0}", pathInArchive); //NON-NLS
                        //TODO consider bail out / msg to the user
                    }
                }

                // skip the rest of this loop if we couldn't create the file
                if (localFile.exists() == false) {
                    continue;
                }

                final Date createTime = item.getCreationTime();
                final Date accessTime = item.getLastAccessTime();
                final Date writeTime = item.getLastWriteTime();
                final long createtime = createTime == null ? 0L : createTime.getTime() / 1000;
                final long modtime = writeTime == null ? 0L : writeTime.getTime() / 1000;
                final long accesstime = accessTime == null ? 0L : accessTime.getTime() / 1000;

                //unpack locally if a file
                SevenZipExtractor.UnpackStream unpackStream = null;
                if (!isDir) {
                    try {
                        if (size != null) {
                            unpackStream = new SevenZipExtractor.KnownSizeUnpackStream(localAbsPath, size);
                        } else {
                            unpackStream = new SevenZipExtractor.UnknownSizeUnpackStream(localAbsPath, freeDiskSpace);
                        }
                        item.extractSlow(unpackStream);
                    } catch (Exception e) {
                        //could be something unexpected with this file, move on
                        logger.log(Level.WARNING, "Could not extract file from archive: " + localAbsPath, e); //NON-NLS
                    } finally {
                        if (unpackStream != null) {
                            //record derived data in unode, to be traversed later after unpacking the archive
                            unpackedNode.addDerivedInfo(unpackStream.getSize(), !isDir,
                                    0L, createtime, accesstime, modtime, localRelPath);
                            unpackStream.close();
                        }
                    }
                } else { // this is a directory, size is always 0
                    unpackedNode.addDerivedInfo(0, !isDir,
                            0L, createtime, accesstime, modtime, localRelPath);
                }

                //update units for progress bar
                ++processedItems;
            }

            // add them to the DB. We wait until the end so that we have the metadata on all of the
            // intermediate nodes since the order is not guaranteed
            try {
                unpackedTree.addDerivedFilesToCase();
                unpackedFiles = unpackedTree.getAllFileObjects();

                //check if children are archives, update archive depth tracking
                for (AbstractFile unpackedFile : unpackedFiles) {
                    if (isSevenZipExtractionSupported(unpackedFile)) {
                        archiveDepthCountTree.addArchive(parentAr, unpackedFile.getId());
                    }
                }

            } catch (TskCoreException | NoCurrentCaseException e) {
                logger.log(Level.SEVERE, "Error populating complete derived file hierarchy from the unpacked dir structure", e); //NON-NLS
                //TODO decide if anything to cleanup, for now bailing
            }

        } catch (SevenZipException ex) {
            logger.log(Level.WARNING, "Error unpacking file: {0}", archiveFile); //NON-NLS
            //inbox message

            // print a message if the file is allocated
            if (archiveFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                String msg = NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.errUnpacking.msg",
                        archiveFile.getName());
                String details = NbBundle.getMessage(SevenZipExtractor.class,
                        "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.errUnpacking.details",
                        archiveFilePath, ex.getMessage());
                services.postMessage(IngestMessage.createErrorMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), msg, details));
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

        //create artifact and send user message
        if (hasEncrypted) {
            String encryptionType = fullEncryption ? ENCRYPTION_FULL : ENCRYPTION_FILE_LEVEL;
            try {
                BlackboardArtifact artifact = archiveFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);
                artifact.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME, EmbeddedFileExtractorModuleFactory.getModuleName(), encryptionType));

                try {
                    // index the artifact for keyword search
                    blackboard.indexArtifact(artifact);
                } catch (Blackboard.BlackboardException ex) {
                    logger.log(Level.SEVERE, "Unable to index blackboard artifact " + artifact.getArtifactID(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(
                            Bundle.SevenZipExtractor_indexError_message(), artifact.getDisplayName());
                }

                services.fireModuleDataEvent(new ModuleDataEvent(EmbeddedFileExtractorModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating blackboard artifact for encryption detected for file: " + archiveFilePath, ex); //NON-NLS
            }

            String msg = NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.encrFileDetected.msg");
            String details = NbBundle.getMessage(SevenZipExtractor.class,
                    "EmbeddedFileExtractorIngestModule.ArchiveExtractor.unpack.encrFileDetected.details",
                    archiveFile.getName(), EmbeddedFileExtractorModuleFactory.getModuleName());
            services.postMessage(IngestMessage.createWarningMessage(EmbeddedFileExtractorModuleFactory.getModuleName(), msg, details));
        }

        // adding unpacked extracted derived files to the job after closing relevant resources.
        if (!unpackedFiles.isEmpty()) {
            //currently sending a single event for all new files
            services.fireModuleContentEvent(new ModuleContentEvent(archiveFile));
            context.addFilesToJob(unpackedFiles);
        }
    }

    /**
     * Stream used to unpack the archive to local file
     */
    private abstract static class UnpackStream implements ISequentialOutStream {

        private OutputStream output;
        private String localAbsPath;

        UnpackStream(String localAbsPath) {
            this.localAbsPath = localAbsPath;
            try {
                output = new EncodedFileOutputStream(new FileOutputStream(localAbsPath), TskData.EncodingType.XOR1);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error writing extracted file: " + localAbsPath, ex); //NON-NLS
            }

        }

        public abstract long getSize();

        OutputStream getOutput() {
            return output;
        }

        String getLocalAbsPath() {
            return localAbsPath;
        }

        public void close() {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error closing unpack stream for file: {0}", localAbsPath); //NON-NLS
                }
            }
        }
    }

    /**
     * Stream used to unpack the archive of unknown size to local file
     */
    private static class UnknownSizeUnpackStream extends UnpackStream {

        private long freeDiskSpace;
        private boolean outOfSpace = false;
        private long bytesWritten = 0;

        UnknownSizeUnpackStream(String localAbsPath, long freeDiskSpace) {
            super(localAbsPath);
            this.freeDiskSpace = freeDiskSpace;
        }

        @Override
        public long getSize() {
            return this.bytesWritten;
        }

        @Override
        public int write(byte[] bytes) throws SevenZipException {
            try {
                // If the content size is unknown, cautiously write to disk.
                // Write only if byte array is less than 80% of the current
                // free disk space.
                if (freeDiskSpace == IngestMonitor.DISK_FREE_SPACE_UNKNOWN || bytes.length < 0.8 * freeDiskSpace) {
                    getOutput().write(bytes);
                    // NOTE: this method is called multiple times for a
                    // single extractSlow() call. Update bytesWritten and
                    // freeDiskSpace after every write operation.
                    this.bytesWritten += bytes.length;
                    this.freeDiskSpace -= bytes.length;
                } else {
                    this.outOfSpace = true;
                    logger.log(Level.INFO, NbBundle.getMessage(
                            SevenZipExtractor.class,
                            "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackStream.write.noSpace.msg"));
                    throw new SevenZipException(
                            NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackStream.write.noSpace.msg"));
                }
            } catch (IOException ex) {
                throw new SevenZipException(
                        NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackStream.write.exception.msg",
                                getLocalAbsPath()), ex);
            }
            return bytes.length;
        }

        @Override
        public void close() {
            if (getOutput() != null) {
                try {
                    getOutput().flush();
                    getOutput().close();
                    if (this.outOfSpace) {
                        Files.delete(Paths.get(getLocalAbsPath()));
                    }
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error closing unpack stream for file: {0}", getLocalAbsPath()); //NON-NLS
                }
            }
        }
    }

    /**
     * Stream used to unpack the archive of known size to local file
     */
    private static class KnownSizeUnpackStream extends UnpackStream {

        private long size;

        KnownSizeUnpackStream(String localAbsPath, long size) {
            super(localAbsPath);
            this.size = size;
        }

        @Override
        public long getSize() {
            return this.size;
        }

        @Override
        public int write(byte[] bytes) throws SevenZipException {
            try {
                getOutput().write(bytes);
            } catch (IOException ex) {
                throw new SevenZipException(
                        NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackStream.write.exception.msg",
                                getLocalAbsPath()), ex);
            }
            return bytes.length;
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

        /**
         *
         * @param localPathRoot Path in module output folder that files will be
         *                      saved to
         * @param archiveFile   Archive file being extracted
         * @param fileManager
         */
        UnpackedTree(String localPathRoot, AbstractFile archiveFile) {
            this.rootNode = new UnpackedNode();
            this.rootNode.setFile(archiveFile);
            this.rootNode.setFileName(archiveFile.getName());
            this.rootNode.localRelPath = localPathRoot;
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
        UnpackedNode addNode(String filePath) {
            String[] toks = filePath.split("[\\/\\\\]");
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < toks.length; ++i) {
                if (!toks[i].isEmpty()) {
                    tokens.add(toks[i]);
                }
            }
            return addNode(rootNode, tokens);
        }

        /**
         * recursive method that traverses the path
         *
         * @param parent
         * @param tokenPath
         *
         * @return
         */
        private UnpackedNode addNode(UnpackedNode parent, List<String> tokenPath) {
            // we found all of the tokens
            if (tokenPath.isEmpty()) {
                return parent;
            }

            // get the next name in the path and look it up
            String childName = tokenPath.remove(0);
            UnpackedNode child = parent.getChild(childName);
            // create new node
            if (child == null) {
                child = new UnpackedNode(childName, parent);
            }

            // go down one more level
            return addNode(child, tokenPath);
        }

        /**
         * Get the root file objects (after createDerivedFiles() ) of this tree,
         * so that they can be rescheduled.
         *
         * @return root objects of this unpacked tree
         */
        List<AbstractFile> getRootFileObjects() {
            List<AbstractFile> ret = new ArrayList<>();
            for (UnpackedNode child : rootNode.children) {
                ret.add(child.getFile());
            }
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
            for (UnpackedNode child : rootNode.children) {
                getAllFileObjectsRec(ret, child);
            }
            return ret;
        }

        private void getAllFileObjectsRec(List<AbstractFile> list, UnpackedNode parent) {
            list.add(parent.getFile());
            for (UnpackedNode child : parent.children) {
                getAllFileObjectsRec(list, child);
            }
        }

        /**
         * Traverse the tree top-down after unzipping is done and create derived
         * files for the entire hierarchy
         */
        void addDerivedFilesToCase() throws TskCoreException, NoCurrentCaseException {
            final FileManager fileManager = Case.getOpenCase().getServices().getFileManager();
            for (UnpackedNode child : rootNode.children) {
                addDerivedFilesToCaseRec(child, fileManager);
            }
        }

        private void addDerivedFilesToCaseRec(UnpackedNode node, FileManager fileManager) throws TskCoreException {
            final String fileName = node.getFileName();

            try {
                DerivedFile df = fileManager.addDerivedFile(fileName, node.getLocalRelPath(), node.getSize(),
                        node.getCtime(), node.getCrtime(), node.getAtime(), node.getMtime(),
                        node.isIsFile(), node.getParent().getFile(), "", EmbeddedFileExtractorModuleFactory.getModuleName(),
                        "", "", TskData.EncodingType.XOR1);
                node.setFile(df);

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error adding a derived file to db:" + fileName, ex); //NON-NLS
                throw new TskCoreException(
                        NbBundle.getMessage(SevenZipExtractor.class, "EmbeddedFileExtractorIngestModule.ArchiveExtractor.UnpackedTree.exception.msg",
                                fileName), ex);
            }

            //recurse
            for (UnpackedNode child : node.children) {
                addDerivedFilesToCaseRec(child, fileManager);
            }
        }

        /**
         * A node in the unpacked tree that represents a file or folder.
         */
        private class UnpackedNode {

            private String fileName;
            private AbstractFile file;
            private List<UnpackedNode> children = new ArrayList<>();
            private String localRelPath = "";
            private long size;
            private long ctime, crtime, atime, mtime;
            private boolean isFile;
            private UnpackedNode parent;

            //root constructor
            UnpackedNode() {
            }

            //child node constructor
            UnpackedNode(String fileName, UnpackedNode parent) {
                this.fileName = fileName;
                this.parent = parent;
                this.localRelPath = parent.localRelPath + File.separator + fileName;
                //new child derived file will be set by unpack() method
                parent.children.add(this);

            }

            public long getCtime() {
                return ctime;
            }

            public long getCrtime() {
                return crtime;
            }

            public long getAtime() {
                return atime;
            }

            public long getMtime() {
                return mtime;
            }

            public void setFileName(String fileName) {
                this.fileName = fileName;
            }

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
                    if (child.fileName.equals(childFileName)) {
                        ret = child;
                        break;
                    }
                }
                return ret;
            }

            public String getFileName() {
                return fileName;
            }

            public AbstractFile getFile() {
                return file;
            }

            public String getLocalRelPath() {
                return localRelPath;
            }

            public long getSize() {
                return size;
            }

            public boolean isIsFile() {
                return isFile;
            }
        }
    }

    /**
     * Tracks archive hierarchy and archive depth
     */
    private static class ArchiveDepthCountTree {

        //keeps all nodes refs for easy search
        private final List<Archive> archives = new ArrayList<>();

        /**
         * Search for previously added parent archive by id
         *
         * @param objectId parent archive object id
         *
         * @return the archive node or null if not found
         */
        Archive findArchive(long objectId) {
            for (Archive ar : archives) {
                if (ar.objectId == objectId) {
                    return ar;
                }
            }

            return null;
        }

        /**
         * Add a new archive to track of depth
         *
         * @param parent   parent archive or null
         * @param objectId object id of the new archive
         *
         * @return the archive added
         */
        Archive addArchive(Archive parent, long objectId) {
            Archive child = new Archive(parent, objectId);
            archives.add(child);
            return child;
        }

        private static class Archive {

            int depth;
            long objectId;
            Archive parent;
            List<Archive> children;

            Archive(Archive parent, long objectId) {
                this.parent = parent;
                this.objectId = objectId;
                children = new ArrayList<>();
                if (parent != null) {
                    parent.children.add(this);
                    this.depth = parent.depth + 1;
                } else {
                    this.depth = 0;
                }
            }

            /**
             * get archive depth of this archive
             *
             * @return
             */
            int getDepth() {
                return depth;
            }
        }
    }

}
