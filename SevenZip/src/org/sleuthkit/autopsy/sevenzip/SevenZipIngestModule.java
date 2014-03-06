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
package org.sleuthkit.autopsy.sevenzip;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMonitor;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * 7Zip ingest module Extracts supported archives, adds extracted DerivedFiles,
 * reschedules extracted DerivedFiles for ingest.
 *
 * Updates datamodel / directory tree with new files.
 */
public final class SevenZipIngestModule extends IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(SevenZipIngestModule.class.getName());
    public static final String MODULE_NAME = "Archive Extractor";
    public static final String MODULE_DESCRIPTION = "Extracts archive files (zip, rar, arj, 7z, gzip, bzip2, tar), reschedules them to current ingest and populates directory tree with new files.";
    final public static String MODULE_VERSION = Version.getVersion();
    private IngestServices services;
    private volatile int messageID = 0;
    private boolean initialized = false;
    private static SevenZipIngestModule instance = null;
    //TODO use content type detection instead of extensions
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", "tgz", }; // "iso"};
    private String unpackDir; //relative to the case, to store in db
    private String unpackDirPath; //absolute, to extract to
    private FileManager fileManager;
    //encryption type strings
    private static final String ENCRYPTION_FILE_LEVEL = "File-level Encryption";
    private static final String ENCRYPTION_FULL = "Full Encryption";
    //zip bomb detection
    private static final int MAX_DEPTH = 4;
    private static final int MAX_COMPRESSION_RATIO = 600;
    private static final long MIN_COMPRESSION_RATIO_SIZE = 500 * 1000000L;
    private static final long MIN_FREE_DISK_SPACE = 1 * 1000 * 1000000L; //1GB
    //counts archive depth
    private ArchiveDepthCountTree archiveDepthCountTree;
    //buffer for checking file headers and signatures
    private static final int readHeaderSize = 4;
    private final byte[] fileHeaderBuffer = new byte[readHeaderSize];
    private static final int ZIP_SIGNATURE_BE = 0x504B0304;

    //private constructor to ensure singleton instance 
    private SevenZipIngestModule() {
    }

    /**
     * Returns singleton instance of the module, creates one if needed
     *
     * @return instance of the module
     */
    public static synchronized SevenZipIngestModule getDefault() {
        if (instance == null) {
            instance = new SevenZipIngestModule();
        }
        return instance;
    }

    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        services = IngestServices.getDefault();
        initialized = false;

        final Case currentCase = Case.getCurrentCase();

        unpackDir = Case.getModulesOutputDirRelPath() + File.separator + MODULE_NAME;
        unpackDirPath = currentCase.getModulesOutputDirAbsPath() + File.separator + MODULE_NAME;

        fileManager = currentCase.getServices().getFileManager();

        File unpackDirPathFile = new File(unpackDirPath);
        if (!unpackDirPathFile.exists()) {
            try {
                unpackDirPathFile.mkdirs();
            } catch (SecurityException e) {
                logger.log(Level.SEVERE, "Error initializing output dir: " + unpackDirPath, e);
                String msg = "Error initializing " + MODULE_NAME;
                String details = "Error initializing output dir: " + unpackDirPath + ": " + e.getMessage();
                //MessageNotifyUtil.Notify.error(msg, details);
                services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
                throw e;
            }
        }

        try {
            SevenZip.initSevenZipFromPlatformJAR();
            String platform = SevenZip.getUsedPlatform();
            logger.log(Level.INFO, "7-Zip-JBinding library was initialized on supported platform: " + platform);
        } catch (SevenZipNativeInitializationException e) {
            logger.log(Level.SEVERE, "Error initializing 7-Zip-JBinding library", e);
            String msg = "Error initializing " + MODULE_NAME;
            String details = "Could not initialize 7-ZIP library: " + e.getMessage();
            //MessageNotifyUtil.Notify.error(msg, details);
            services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
            throw new RuntimeException(e);
        }

        archiveDepthCountTree = new ArchiveDepthCountTree();

        initialized = true;
    }

    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {

        if (initialized == false) { //error initializing the module
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: " + abstractFile.getName());
            return ProcessResult.OK;
        }
        
        //skip unalloc
        if(abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }
        
        // skip known
        if (abstractFile.getKnown().equals(TskData.FileKnown.KNOWN)) {
            return IngestModuleAbstractFile.ProcessResult.OK;
        }

        if (abstractFile.isFile() == false || !isSupported(abstractFile)) {
            //do not process dirs and files that are not supported
            return ProcessResult.OK;
        }

        //check if already has derived files, skip
        try {
            if (abstractFile.hasChildren()) {
                //check if local unpacked dir exists
                final String uniqueFileName = getUniqueName(abstractFile);
                final String localRootAbsPath = getLocalRootAbsPath(uniqueFileName);
                if (new File(localRootAbsPath).exists()) {
                    logger.log(Level.INFO, "File already has been processed as it has children and local unpacked file, skipping: " + abstractFile.getName());
                    return ProcessResult.OK;
                }
            }
        } catch (TskCoreException e) {
            logger.log(Level.INFO, "Error checking if file already has been processed, skipping: " + abstractFile.getName());
            return ProcessResult.OK;
        }

        logger.log(Level.INFO, "Processing with " + MODULE_NAME + ": " + abstractFile.getName());

        List<AbstractFile> unpackedFiles = unpack(abstractFile);
        if (!unpackedFiles.isEmpty()) {
            sendNewFilesEvent(abstractFile, unpackedFiles);
            rescheduleNewFiles(pipelineContext, unpackedFiles);
        }

        return ProcessResult.OK;
    }

    private void sendNewFilesEvent(AbstractFile archive, List<AbstractFile> unpackedFiles) {
        //currently sending a single event for all new files
        services.fireModuleContentEvent(new ModuleContentEvent(archive));
    }

    private void rescheduleNewFiles(PipelineContext<IngestModuleAbstractFile> pipelineContext, List<AbstractFile> unpackedFiles) {
        for (AbstractFile unpackedFile : unpackedFiles) {
            services.scheduleFile(unpackedFile, pipelineContext);
        }
    }

    /**
     * Get local relative path to the unpacked archive root
     *
     * @param archiveFile
     * @return
     */
    private String getUniqueName(AbstractFile archiveFile) {
        return archiveFile.getName() + "_" + archiveFile.getId();
    }

    /**
     * Get local abs path to the unpacked archive root
     *
     * @param localRootRelPath relative path to archive, from
     * getUniqueName()
     * @return
     */
    private String getLocalRootAbsPath(String localRootRelPath) {
        return unpackDirPath + File.separator + localRootRelPath;
    }

    /**
     * Check if the item inside archive is a potential zipbomb
     *
     * Currently checks compression ratio.
     *
     * More heuristics to be added here
     *
     * @param archiveName the parent archive
     * @param archiveFileItem the archive item
     * @return true if potential zip bomb, false otherwise
     */
    private boolean isZipBombArchiveItemCheck(String archiveName, ISimpleInArchiveItem archiveFileItem) {
        try {
            final long archiveItemSize = archiveFileItem.getSize();

            //logger.log(Level.INFO, "ARCHIVE ITEM:  " + archiveFileItem.getPath() + ", SIZE: " + archiveItemSize + " AR NAME: " + archiveName);

            //skip the check for small files
            if (archiveItemSize < MIN_COMPRESSION_RATIO_SIZE) {
                return false;
            }

            final long archiveItemPackedSize = archiveFileItem.getPackedSize();

            if (archiveItemPackedSize <= 0) {
                logger.log(Level.WARNING, "Cannot getting compression ratio, cannot detect if zipbomb: "
                        + archiveName + ", item: " + archiveFileItem.getPath());
                return false;
            }

            int cRatio = (int) (archiveItemSize / archiveItemPackedSize);

            if (cRatio >= MAX_COMPRESSION_RATIO) {
                String itemName = archiveFileItem.getPath();
                logger.log(Level.INFO, "Possible zip bomb detected, compression ration: " + cRatio
                        + " for in archive item: " + itemName);
                String msg = "Possible ZIP bomb detected in archive: " + archiveName
                        + ", item: " + itemName;
                String details = "The archive item compression ratio is " + cRatio
                        + ", skipping processing of this archive item. ";
                //MessageNotifyUtil.Notify.error(msg, details);
                services.postMessage(IngestMessage.createWarningMessage(++messageID, instance, msg, details));

                return true;
            } else {
                return false;
            }

        } catch (SevenZipException ex) {
            logger.log(Level.SEVERE, "Error getting archive item size and cannot detect if zipbomb. ", ex);
            return false;
        }
    }

    /**
     * Unpack the file to local folder and return a list of derived files
     *
     * @param pipelineContext current ingest context
     * @param archiveFile file to unpack
     * @return list of unpacked derived files
     */
    private List<AbstractFile> unpack(AbstractFile archiveFile) {
        List<AbstractFile> unpackedFiles = Collections.<AbstractFile>emptyList();

        //recursion depth check for zip bomb
        final long archiveId = archiveFile.getId();
        ArchiveDepthCountTree.Archive parentAr = archiveDepthCountTree.findArchive(archiveId);
        if (parentAr == null) {
            parentAr = archiveDepthCountTree.addArchive(null, archiveId);
        } else if (parentAr.getDepth() == MAX_DEPTH) {
            String msg = "Possible ZIP bomb detected: " + archiveFile.getName();
            String details = "The archive is " + parentAr.getDepth()
                    + " levels deep, skipping processing of this archive and its contents ";
            //MessageNotifyUtil.Notify.error(msg, details);
            services.postMessage(IngestMessage.createWarningMessage(++messageID, instance, msg, details));
            return unpackedFiles;
        }

        boolean hasEncrypted = false;
        boolean fullEncryption = true;

        ISevenZipInArchive inArchive = null;
        SevenZipContentReadStream stream = null;

        final ProgressHandle progress = ProgressHandleFactory.createHandle(MODULE_NAME);
        int processedItems = 0;

        String compressMethod = null;
        boolean progressStarted = false;
        try {
            stream = new SevenZipContentReadStream(new ReadContentInputStream(archiveFile));
            inArchive = SevenZip.openInArchive(null, // autodetect archive type
                    stream);

            int numItems = inArchive.getNumberOfItems();
            logger.log(Level.INFO, "Count of items in archive: " + archiveFile.getName() + ": "
                    + numItems);
            progress.start(numItems);
            progressStarted = true;

            final ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            //setup the archive local root folder
            final String uniqueFileName = getUniqueName(archiveFile);
            final String localRootAbsPath = getLocalRootAbsPath(uniqueFileName);
            final File localRoot = new File(localRootAbsPath);
            if (!localRoot.exists()) {
                try {
                    localRoot.mkdirs();
                } catch (SecurityException e) {
                    logger.log(Level.SEVERE, "Error setting up output path for archive root: " + localRootAbsPath);
                    //bail
                    return unpackedFiles;
                }
            }

            //initialize tree hierarchy to keep track of unpacked file structure
            UnpackedTree uTree = new UnpackedTree(unpackDir + "/" + uniqueFileName, archiveFile, fileManager);

            long freeDiskSpace = services.getFreeDiskSpace();

            //unpack and process every item in archive
            int itemNumber = 0;
            for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                String extractedPath = item.getPath();
                if (extractedPath == null || extractedPath.isEmpty() ) {
                    //some formats (.tar.gz) may not be handled correctly -- file in archive has no name/path
                    //handle this for .tar.gz and tgz but assuming the child is tar,
                    //otherwise, unpack using itemNumber as name
                    
                    //TODO this should really be signature based, not extension based
                    String archName = archiveFile.getName();
                    int dotI = archName.lastIndexOf(".");
                    String useName = null;
                    if (dotI != -1 ) {
                        String base = archName.substring(0, dotI);
                        String ext = archName.substring(dotI);
                        if (ext.equals(".gz") ) {
                            useName = base;
                        }
                        else if (ext.equals(".tgz")) {
                            useName = base + ".tar";
                        }
                    }
                    
                    if (useName == null) {
                        extractedPath = "/" + archName + "/" + Integer.toString(itemNumber);
                    }
                    else {
                        extractedPath = "/" + useName;
                    }
                    
                    String msg = "Unknown item path in archive: " + archiveFile.getName() + ", will use: " + extractedPath;
                    logger.log(Level.WARNING, msg);
                    
                }
                ++itemNumber;
                logger.log(Level.INFO, "Extracted item path: " + extractedPath);

                //check if possible zip bomb
                if (isZipBombArchiveItemCheck(archiveFile.getName(), item)) {
                    continue; //skip the item
                }

                //find this node in the hierarchy, create if needed
                UnpackedTree.Data uNode = uTree.find(extractedPath);

                String fileName = uNode.getFileName();

                //update progress bar
                progress.progress(archiveFile.getName() + ": " + fileName, processedItems);

                if (compressMethod == null) {
                    compressMethod = item.getMethod();
                }

                final boolean isEncrypted = item.isEncrypted();
                final boolean isDir = item.isFolder();

                if (isEncrypted) {
                    logger.log(Level.WARNING, "Skipping encrypted file in archive: " + extractedPath);
                    hasEncrypted = true;
                    continue;
                } else {
                    fullEncryption = false;
                }

                final long size = item.getSize();

                //check if unpacking this file will result in out of disk space
                //this is additional to zip bomb prevention mechanism
                if (freeDiskSpace != IngestMonitor.DISK_FREE_SPACE_UNKNOWN && size > 0) { //if known free space and file not empty
                    long newDiskSpace = freeDiskSpace - size;
                    if (newDiskSpace < MIN_FREE_DISK_SPACE) {
                        String msg = "Not enough disk space to unpack archive item: " + archiveFile.getName() + ", " + fileName;
                        String details = "The archive item is too large to unpack, skipping unpacking this item. ";
                        //MessageNotifyUtil.Notify.error(msg, details);
                        services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
                        logger.log(Level.INFO, "Skipping archive item due not sufficient disk space for this item: " + archiveFile.getName() + ", " + fileName);
                        continue; //skip this file
                    } else {
                        //update est. disk space during this archive, so we don't need to poll for every file extracted
                        freeDiskSpace = newDiskSpace;
                    }
                }

                final String localFileRelPath = uniqueFileName + File.separator + extractedPath;
                //final String localRelPath = unpackDir + File.separator + localFileRelPath;
                final String localAbsPath = unpackDirPath + File.separator + localFileRelPath;

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
                            } catch (IOException ex) {
                                logger.log(Level.SEVERE, "Error creating extracted file: " + localFile.getAbsolutePath(), ex);
                            }
                        }
                    } catch (SecurityException e) {
                        logger.log(Level.SEVERE, "Error setting up output path for unpacked file: " + extractedPath);
                        //TODO consider bail out / msg to the user
                    }
                }

                final Date createTime = item.getCreationTime();
                final Date accessTime = item.getLastAccessTime();
                final Date writeTime = item.getLastWriteTime();
                final long createtime = createTime == null ? 0L : createTime.getTime() / 1000;
                final long modtime = writeTime == null ? 0L : writeTime.getTime() / 1000;
                final long accesstime = accessTime == null ? 0L : accessTime.getTime() / 1000;

                //record derived data in unode, to be traversed later after unpacking the archive
                uNode.addDerivedInfo(size, !isDir,
                        0L, createtime, accesstime, modtime);

                //unpack locally if a file
                if (!isDir) {
                    UnpackStream unpackStream = null;
                    try {
                        unpackStream = new UnpackStream(localAbsPath);
                        item.extractSlow(unpackStream);
                    } catch (Exception e) {
                        //could be something unexpected with this file, move on
                        logger.log(Level.WARNING, "Could not extract file from archive: " + localAbsPath, e);
                    } finally {
                        if (unpackStream != null) {
                            unpackStream.close();
                        }
                    }
                }

                //update units for progress bar
                ++processedItems;
            } //for every item in archive

            try {
                uTree.createDerivedFiles();
                unpackedFiles = uTree.getAllFileObjects();

                //check if children are archives, update archive depth tracking
                for (AbstractFile unpackedFile : unpackedFiles) {
                    if (isSupported(unpackedFile)) {
                        archiveDepthCountTree.addArchive(parentAr, unpackedFile.getId());
                    }
                }

            } catch (TskCoreException e) {
                logger.log(Level.SEVERE, "Error populating complete derived file hierarchy from the unpacked dir structure");
                //TODO decide if anything to cleanup, for now bailing
            }

        } catch (SevenZipException ex) {
            logger.log(Level.SEVERE, "Error unpacking file: " + archiveFile, ex);
            //inbox message
            String fullName;
            try {
                fullName = archiveFile.getUniquePath();
            } catch (TskCoreException ex1) {
                fullName = archiveFile.getName();
            }

            // print a message if the file is allocated
            if (archiveFile.isMetaFlagSet(TskData.TSK_FS_META_FLAG_ENUM.ALLOC)) {
                String msg = "Error unpacking " + archiveFile.getName();
                String details = "Error unpacking  " + fullName + ". " + ex.getMessage();
                services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
            }
        } finally {
            if (inArchive != null) {
                try {
                    inArchive.close();
                } catch (SevenZipException e) {
                    logger.log(Level.SEVERE, "Error closing archive: " + archiveFile, e);
                }
            }

            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Error closing stream after unpacking archive: " + archiveFile, ex);
                }
            }

            //close progress bar
            if (progressStarted)
                progress.finish();
        }

        //create artifact and send user message
        if (hasEncrypted) {
            String encryptionType = fullEncryption ? ENCRYPTION_FULL : ENCRYPTION_FILE_LEVEL;
            try {
                BlackboardArtifact artifact = archiveFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED);
                artifact.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_NAME.getTypeID(), MODULE_NAME, encryptionType));
                services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating blackboard artifact for encryption detected for file: " + archiveFile, ex);
            }

            String msg = "Encrypted files in archive detected. ";
            String details = "Some files in archive: " + archiveFile.getName() + " are encrypted. "
                    + MODULE_NAME + " extractor was unable to extract all files from this archive.";
            // MessageNotifyUtil.Notify.info(msg, details);

            services.postMessage(IngestMessage.createWarningMessage(++messageID, instance, msg, details));
        }

        return unpackedFiles;
    }

    @Override
    public void complete() {
        if (initialized == false) {
            return;
        }
       archiveDepthCountTree = null;
    }

    @Override
    public void stop() {
        archiveDepthCountTree = null;
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public String getVersion() {
        return MODULE_VERSION;
    }


    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    private boolean isSupported(AbstractFile file) {
        // see if it is on the list of extensions
        final String extension = file.getNameExtension();
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; ++i) {
            if (extension.equals(SUPPORTED_EXTENSIONS[i])) {
                return true;
            }
        }
            
        // if no extension match, check the blackboard for the file type
        boolean attributeFound = false;
        try {
            ArrayList<BlackboardAttribute> attributes = file.getGenInfoAttributes(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_FILE_TYPE_SIG);
            for (BlackboardAttribute attribute : attributes) {
                attributeFound = true;
                String fileType = attribute.getValueString();
                if (!fileType.isEmpty() && fileType.equals("application/zip")) {
                    return true;
                }
            }
        } catch (TskCoreException ex) {
            
        }   

        // if no blackboard entry for file type, do it manually for ZIP files:
        if (attributeFound) {
            return false;
        }
        else {
            return isZipFileHeader(file);
        }
    }

    /**
     * Check if is zip file based on header
     *
     * @param file
     * @return true if zip file, false otherwise
     */
    private boolean isZipFileHeader(AbstractFile file) {
        if (file.getSize() < readHeaderSize) {
            return false;
        }

        int bytesRead = 0;
        try {
            bytesRead = file.read(fileHeaderBuffer, 0, readHeaderSize);
        } catch (TskCoreException ex) {
            //ignore if can't read the first few bytes, not a ZIP
            return false;
        }
        if (bytesRead != readHeaderSize) {
            return false;
        }

        ByteBuffer bytes = ByteBuffer.wrap(fileHeaderBuffer);
        int signature = bytes.getInt();

        return signature == ZIP_SIGNATURE_BE;
    }

    /**
     * Stream used to unpack the archive to local file
     */
    private static class UnpackStream implements ISequentialOutStream {

        private OutputStream output;
        private String localAbsPath;

        UnpackStream(String localAbsPath) {
            try {
                output = new BufferedOutputStream(new FileOutputStream(localAbsPath));
            } catch (FileNotFoundException ex) {
                logger.log(Level.SEVERE, "Error writing extracted file: " + localAbsPath, ex);
            }

        }

        @Override
        public int write(byte[] bytes) throws SevenZipException {
            try {
                output.write(bytes);
            } catch (IOException ex) {
                throw new SevenZipException("Error writing unpacked file to: " + localAbsPath, ex);
            }
            return bytes.length;
        }

        public void close() {
            if (output != null) {
                try {
                    output.flush();
                    output.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error closing unpack stream for file: " + localAbsPath);
                }
            }
        }
    }

    /**
     * Representation of local directory tree of unpacked archive. Used to track
     * of local tree file hierarchy, archive depth, and files created to easily
     * and reliably get parent AbstractFile for unpacked file. So that we don't
     * have to depend on type of traversal of unpacked files handed to us by
     * 7zip unpacker.
     */
    private static class UnpackedTree {

        final String localPathRoot;
        final Data root; //dummy root to hold children
        final FileManager fileManager;

        UnpackedTree(String localPathRoot, AbstractFile archiveRoot, FileManager fileManager) {
            this.localPathRoot = localPathRoot;
            this.fileManager = fileManager;
            this.root = new Data();
            this.root.setFile(archiveRoot);
            this.root.setFileName(archiveRoot.getName());
            this.root.localRelPath = localPathRoot;
        }

        /**
         * Tokenizes filePath passed in and traverses the dir structure,
         * creating data nodes on the path way as needed
         *
         * @param filePath file path with 1 or more tokens separated by /
         * @return child node for the last file token in the filePath
         */
        Data find(String filePath) {
            String[] toks = filePath.split("[\\/\\\\]");
            List<String> tokens = new ArrayList<String>();
            for (int i = 0; i < toks.length; ++i) {
                if (!toks[i].isEmpty()) {
                    tokens.add(toks[i]);
                }
            }
            return find(root, tokens);
        }

        /**
         * recursive method that traverses the path
         *
         * @param tokenPath
         * @return
         */
        private Data find(Data parent, List<String> tokenPath) {
            //base case
            if (tokenPath.isEmpty()) {
                return parent;
            }

            String childName = tokenPath.remove(0); //step towards base case
            Data child = parent.getChild(childName);
            if (child == null) {
                child = new Data(childName, parent);
            }
            return find(child, tokenPath);

        }

        /**
         * Get the root file objects (after createDerivedFiles() ) of this tree,
         * so that they can be rescheduled.
         *
         * @return root objects of this unpacked tree
         */
        List<AbstractFile> getRootFileObjects() {
            List<AbstractFile> ret = new ArrayList<AbstractFile>();
            for (Data child : root.children) {
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
            List<AbstractFile> ret = new ArrayList<AbstractFile>();
            for (Data child : root.children) {
                getAllFileObjectsRec(ret, child);
            }
            return ret;
        }

        private void getAllFileObjectsRec(List<AbstractFile> list, Data parent) {
            list.add(parent.getFile());
            for (Data child : parent.children) {
                getAllFileObjectsRec(list, child);
            }
        }

        /**
         * Traverse the tree top-down after unzipping is done and create derived
         * files for the entire hierarchy
         */
        void createDerivedFiles() throws TskCoreException {
            for (Data child : root.children) {
                createDerivedFilesRec(child);
            }

        }

        private void createDerivedFilesRec(Data node) throws TskCoreException {
            final String fileName = node.getFileName();
            final String localRelPath = node.getLocalRelPath();
            final long size = node.getSize();
            final boolean isFile = node.isIsFile();
            final AbstractFile parent = node.getParent().getFile();

            try {
                DerivedFile df = fileManager.addDerivedFile(fileName, localRelPath, size,
                        node.getCtime(), node.getCrtime(), node.getAtime(), node.getMtime(),
                        isFile, parent, "", MODULE_NAME, "", "");
                node.setFile(df);


            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error adding a derived file to db:" + fileName, ex);
                throw new TskCoreException("Error adding a derived file to db:" + fileName, ex);
            }

            //recurse
            for (Data child : node.children) {
                createDerivedFilesRec(child);
            }


        }

        private static class Data {

            private String fileName;
            private AbstractFile file;
            private List<Data> children = new ArrayList<Data>();
            private String localRelPath;
            private long size;
            private long ctime, crtime, atime, mtime;
            private boolean isFile;
            private Data parent;

            //root constructor
            Data() {
            }

            //child node constructor
            Data(String fileName, Data parent) {
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

            Data getParent() {
                return parent;
            }

            void addDerivedInfo(long size,
                    boolean isFile,
                    long ctime, long crtime, long atime, long mtime) {
                this.size = size;
                this.isFile = isFile;
                this.ctime = ctime;
                this.crtime = crtime;
                this.atime = atime;
                this.mtime = mtime;
            }

            void setFile(AbstractFile file) {
                this.file = file;
            }

            /**
             * get child by name or null if it doesn't exist
             *
             * @param childFileName
             * @return
             */
            Data getChild(String childFileName) {
                Data ret = null;
                for (Data child : children) {
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
        private final List<Archive> archives = new ArrayList<Archive>();

        /**
         * Search for previously added parent archive by id
         *
         * @param objectId parent archive object id
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
         * @param parent parent archive or null
         * @param objectId object id of the new archive
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
                children = new ArrayList<Archive>();
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
