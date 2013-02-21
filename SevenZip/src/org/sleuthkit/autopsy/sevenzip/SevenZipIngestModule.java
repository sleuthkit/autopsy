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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import javax.swing.JPanel;
import net.sf.sevenzipjbinding.ISequentialOutStream;
import net.sf.sevenzipjbinding.ISevenZipInArchive;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;
import net.sf.sevenzipjbinding.simple.ISimpleInArchive;
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.IngestContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * 7Zip ingest module Extracts supported archives, adds extracted DerivedFiles,
 * reschedules extracted DerivedFiles for ingest.
 *
 * Updates datamodel / directory tree with new files.
 */
public final class SevenZipIngestModule implements IngestModuleAbstractFile {

    private static final Logger logger = Logger.getLogger(SevenZipIngestModule.class.getName());
    public static final String MODULE_NAME = "Archive Extractor";
    public static final String MODULE_DESCRIPTION = "Extracts archive files (zip, rar, arj, 7z, gzip, bzip2, tar), reschedules them to current ingest and populates directory tree with new files.";
    final public static String MODULE_VERSION = "1.0";
    private String args;
    private IngestServices services;
    private volatile int messageID = 0;
    private int processedFiles = 0;
    private SleuthkitCase caseHandle = null;
    private boolean initialized = false;
    private static SevenZipIngestModule instance = null;
    //TODO use content type detection instead of extensions
    static final String[] SUPPORTED_EXTENSIONS = {"zip", "rar", "arj", "7z", "7zip", "gzip", "gz", "bzip2", "tar", }; // "iso"};
    private String unpackDir; //relative to the case, to store in db
    private String unpackDirPath; //absolute, to extract to
    private FileManager fileManager;
    //encryption type strings
    private static final String ENCRYPTION_FILE_LEVEL = "File-level Encryption";
    private static final String ENCRYPTION_FULL = "Full Encryption";

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
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init()");
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
                MessageNotifyUtil.Notify.error("Error initializing " + MODULE_NAME, "Error initializing output dir: " + unpackDirPath + ": " + e.getMessage());
                return;
            }
        }

        try {
            SevenZip.initSevenZipFromPlatformJAR();
            String platform = SevenZip.getUsedPlatform();
            logger.log(Level.INFO, "7-Zip-JBinding library was initialized on supported platform: " + platform);
        } catch (SevenZipNativeInitializationException e) {
            logger.log(Level.SEVERE, "Error initializing 7-Zip-JBinding library", e);
            MessageNotifyUtil.Notify.error("Error initializing " + MODULE_NAME, "Could not initialize 7-ZIP library");
            return;
        }


        initialized = true;
    }

    @Override
    public ProcessResult process(IngestContext<IngestModuleAbstractFile>ingestContext, AbstractFile abstractFile) {

        if (initialized == false) { //error initializing the module
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: " + abstractFile.getName());
            return ProcessResult.OK;
        }

        if (abstractFile.isFile() == false || !isSupported(abstractFile)) {
            //do not process dirs and files that are not supported
            return ProcessResult.OK;
        }

        //check if already has derived files, skip
        try {
            if (abstractFile.hasChildren()) {
                logger.log(Level.INFO, "File already has been processed as it has children, skipping: " + abstractFile.getName());
                return ProcessResult.OK;
            }
        } catch (TskCoreException e) {
            logger.log(Level.INFO, "Error checking if file already has been processed, skipping: " + abstractFile.getName());
            return ProcessResult.OK;
        }


        logger.log(Level.INFO, "Processing with 7ZIP: " + abstractFile.getName());
        ++processedFiles;


        List<AbstractFile> unpackedFiles = unpack(abstractFile);
        if (!unpackedFiles.isEmpty()) {
            sendNewFilesEvent(unpackedFiles);
            rescheduleNewFiles(ingestContext, unpackedFiles);
        }

        //process, return error if occurred

        return ProcessResult.OK;
    }

    private void sendNewFilesEvent(List<AbstractFile> unpackedFiles) {
    }
    
    private void rescheduleNewFiles (IngestContext<IngestModuleAbstractFile>ingestContext, List<AbstractFile> unpackedFiles) {
        for (AbstractFile unpackedFile : unpackedFiles) {
            services.scheduleFile(unpackedFile, ingestContext);
        }
    }

    /**
     * Unpack the file to local folder and return a list of derived files
     *
     * @param archiveFile file to unpack
     * @return list of unpacked derived files
     */
    private List<AbstractFile> unpack(AbstractFile archiveFile) {
        List<AbstractFile> unpackedFiles = Collections.<AbstractFile>emptyList();

        boolean hasEncrypted = false;
        boolean fullEncryption = true;

        ISevenZipInArchive inArchive = null;
        SevenZipContentReadStream stream = null;

        final ProgressHandle progress = ProgressHandleFactory.createHandle(MODULE_NAME + " Extracting Archive");
        int processedItems = 0;

        String compressMethod = null;
        try {
            stream = new SevenZipContentReadStream(new ReadContentInputStream(archiveFile));
            inArchive = SevenZip.openInArchive(null, // autodetect archive type
                    stream);

            int numItems = inArchive.getNumberOfItems();
            logger.log(Level.INFO, "Count of items in archive: " + archiveFile.getName() + ": "
                    + numItems);
            progress.start(numItems);

            final ISimpleInArchive simpleInArchive = inArchive.getSimpleInterface();

            //setup the archive local root folder
            String localRootPath = archiveFile.getName() + "_" + archiveFile.getId();
            String localRootAbsPath = unpackDirPath + File.separator + localRootPath;
            File localRoot = new File(localRootAbsPath);
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
            UnpackedTree uTree = new UnpackedTree(unpackDir + "/" + localRootPath, archiveFile, fileManager);

            //unpack and process every item in archive
            for (ISimpleInArchiveItem item : simpleInArchive.getArchiveItems()) {
                final String extractedPath = item.getPath();
                logger.log(Level.INFO, "Extracted item path: " + extractedPath);

                //find this node in the hierarchy, create if needed
                UnpackedTree.Data uNode = uTree.find(extractedPath);
                String fileName = uNode.getFileName();

                //update progress bar
                progress.progress(archiveFile.getName() + ": " + fileName, processedItems);

                if (compressMethod == null) {
                    compressMethod = item.getMethod();
                }
                final boolean isDir = item.isFolder();
                final boolean isEncrypted = item.isEncrypted();

                if (isEncrypted) {
                    logger.log(Level.WARNING, "Skipping encrypted file in archive: " + extractedPath);
                    hasEncrypted = true;
                    continue;
                } else {
                    fullEncryption = false;
                }

                //TODO get file mac times and add to db

                final String localFileRelPath = localRootPath + File.separator + extractedPath;
                //final String localRelPath = unpackDir + File.separator + localFileRelPath;
                final String localAbsPath = unpackDirPath + File.separator + localFileRelPath;

                //create local dirs and empty files before extracted
                File localFile = new java.io.File(localAbsPath);
                //cannot rely on files in top-bottom order
                if (!localFile.exists()) {
                    //TODO check, might give file locking issues, since 7zip is writing to these dirs
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

                final long size = item.getSize();
                final Date createTime = item.getCreationTime();
                final Date accessTime = item.getLastAccessTime();
                final Date writeTime = item.getLastWriteTime();
                final long createtime = createTime==null? 0L : createTime.getTime() / 1000;
                final long modtime = writeTime==null? 0L : writeTime.getTime() / 1000;
                final long accesstime = accessTime==null? 0L : accessTime.getTime() / 1000;
                
                //record derived data in unode, to be traversed later after unpacking the archive
                uNode.addDerivedInfo(size, !isDir, 
                        modtime, createtime, accesstime, modtime);

                //unpack locally if a file
                if (!isDir) {
                    UnpackStream unpackStream = null;
                    try {
                        unpackStream = new UnpackStream(localAbsPath);
                        item.extractSlow(unpackStream);
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
            } catch (TskCoreException e) {
                logger.log(Level.SEVERE, "Error populating complete derived file hierarchy from the unpacked dir structure");
                //TODO decide if should cleanup, for now bailing
            }

        } catch (SevenZipException ex) {
            logger.log(Level.SEVERE, "Error unpacking file: " + archiveFile, ex);
            //inbox message
            String msg = "Error unpacking file: " + archiveFile.getName();
            String details = msg + ". " + ex.getMessage();
            services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
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
            progress.finish();
        }

        //create artifact and send user message
        if (hasEncrypted) {
            String encryptionType = fullEncryption ? ENCRYPTION_FULL : ENCRYPTION_FILE_LEVEL;
            try {
                BlackboardArtifact generalInfo = archiveFile.newArtifact(ARTIFACT_TYPE.TSK_GEN_INFO);
                generalInfo.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ENCRYPTION_DETECTED.getTypeID(),
                        MODULE_NAME, encryptionType));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error creating blackboard artifact for encryption detected for file: " + archiveFile, ex);
            }

            String msg = "Encrypted files in archive detected. ";
            String details = "Some files in archive: " + archiveFile.getName() + " are encrypted. "
                    + MODULE_NAME + " extractor was unable to extract all files from this archive.";
            MessageNotifyUtil.Notify.info(msg, details);

            services.postMessage(IngestMessage.createMessage(++messageID, IngestMessage.MessageType.INFO, instance, msg, details));
        }


        return unpackedFiles;
    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
        if (initialized == false) {
            return;
        }

        //cleanup if any

    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //cleanup if any

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
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        return false;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return false;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return false;
    }

    @Override
    public void saveSimpleConfiguration() {
    }

    @Override
    public void saveAdvancedConfiguration() {
    }

    @Override
    public JPanel getSimpleConfiguration() {
        return null;
    }

    @Override
    public JPanel getAdvancedConfiguration() {
        return null;
    }

    public boolean isSupported(AbstractFile file) {
        String fileNameLower = file.getName().toLowerCase();
        int dotI = fileNameLower.lastIndexOf(".");
        if (dotI == -1 || dotI == fileNameLower.length() - 1) {
            return false; //no extension
        }
        final String extension = fileNameLower.substring(dotI + 1);
        for (int i = 0; i < SUPPORTED_EXTENSIONS.length; ++i) {
            if (extension.equals(SUPPORTED_EXTENSIONS[i])) {
                return true;
            }
        }
        return false;
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
     * of local tree file hierarchy and files created to easily and reliably get
     * parent AbstractFile for unpacked file. So that we don't have to depend on
     * type of traversal of unpacked files handed to us by 7zip unpacker.
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
                this.localRelPath = parent.localRelPath + "/" + fileName;
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
}
