/*
 *
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
 * 
 * Copyright 2012 42six Solutions.
 * Contact: aebadirad <at> 42six <dot> com
 * Project Contact/Architect: carrier <at> sleuthkit <dot> org
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
package org.sleuthkit.autopsy.casemodule.services;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;

/**
 * Abstraction to facilitate access to files and directories.
 */
public class FileManager implements Closeable {

    private SleuthkitCase tskCase;
    private static final Logger logger = Logger.getLogger(FileManager.class.getName());
    private volatile int curNumFileSets;  //current number of filesets (root virt dir objects)

    public FileManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
        init();
    }

    /**
     * initialize the file manager for the case
     */
    private synchronized void init() {
        //get the number of local file sets in db
        List<VirtualDirectory> virtRoots;
        curNumFileSets = 0;
        try {
            virtRoots = tskCase.getVirtualDirectoryRoots();
            for (VirtualDirectory vd : virtRoots) {
                if (vd.getName().startsWith(VirtualDirectoryNode.LOGICAL_FILE_SET_PREFIX)) {
                    ++curNumFileSets;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error initializing FileManager and getting number of local file sets");
        }


    }

    /**
     * @param dataSource data source Content (Image, parent-less
     * VirtualDirectory) where to find files
     * @param fileName the name of the file or directory to match
     * @return a list of AbstractFile for files/directories whose name matches
     * the given fileName
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.findFiles(dataSource, fileName);
    }

    /**
     * @param dataSource data source Content (Image, parent-less
     * VirtualDirectory) where to find files
     * @param fileName the name of the file or directory to match
     * @param dirName the name of a parent directory of fileName
     * @return a list of AbstractFile for files/directories whose name matches
     * fileName and whose parent directory contains dirName.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, String dirName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.findFiles(dataSource, fileName, dirName);
    }

    /**
     * @param dataSource data source Content (Image, parent-less
     * VirtualDirectory) where to find files
     * @param fileName the name of the file or directory to match
     * @param parentFile parent file/dir of the file to find
     * @return a list of AbstractFile for files/directories whose name matches
     * fileName and that were inside a directory described by parentFsContent.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, AbstractFile parentFile) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return findFiles(dataSource, fileName, parentFile.getName());
    }

    /**
     * @param dataSource data source Content (Image, parent-less
     * VirtualDirectory) where to find files
     * @param filePath The full path to the file(s) of interest. This can
     * optionally include the image and volume names.
     * @return a list of AbstractFile that have the given file path.
     */
    public synchronized List<AbstractFile> openFiles(Content dataSource, String filePath) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.openFiles(dataSource, filePath);
    }

    /**
     * Creates a derived file, adds it to the database and returns it.
     *
     * @param fileName file name the derived file
     * @param localPath local path of the derived file, including the file name.
     * The path is relative to the database path.
     * @param size size of the derived file in bytes
     * @param ctime
     * @param crtime
     * @param atime
     * @param mtime
     * @param isFile whether a file or directory, true if a file
     * @param parentFile the parent file object this the new file was derived
     * from, either a fs file or parent derived file/dikr\\r
     * @param rederiveDetails details needed to re-derive file (will be specific
     * to the derivation method), currently unused
     * @param toolName name of derivation method/tool, currently unused
     * @param toolVersion version of derivation method/tool, currently unused
     * @param otherDetails details of derivation method/tool, currently unused
     * @return newly created derived file object added to the database
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed
     *
     */
    public synchronized DerivedFile addDerivedFile(String fileName, String localPath, long size,
            long ctime, long crtime, long atime, long mtime,
            boolean isFile, AbstractFile parentFile,
            String rederiveDetails, String toolName, String toolVersion, String otherDetails) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        return tskCase.addDerivedFile(fileName, localPath, size,
                ctime, crtime, atime, mtime,
                isFile, parentFile, rederiveDetails, toolName, toolVersion, otherDetails);
    }

    /**
     * Adds a carved file to the VirtualDirectory '$CarvedFiles' in the volume
     * or file system given by systemId.
     *
     * @param carvedFileName the name of the carved file (containing appropriate
     * extension)
     * @param carvedFileSize size of the carved file to add
     * @param systemId the ID of the parent volume or file system
     * @param sectors a list of SectorGroups giving this sectors that make up
     * this carved file.
     * @throws TskCoreException exception thrown when critical tsk error
     * occurred and carved file could not be added
     */
    public synchronized LayoutFile addCarvedFile(String carvedFileName, long carvedFileSize,
            long systemId, List<TskFileRange> sectors) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        return tskCase.addCarvedFile(carvedFileName, carvedFileSize, systemId, sectors);
    }

    /**
     * Interface for receiving notifications on folders being added via a callback
     */
    public interface FileAddProgressUpdater {

        /**
         * Called when new folders has been added
         *
         * @param newFile the file/folder added to the Case
         */
        public void fileAdded(AbstractFile newFile);
    }

    /**
     * Add local files and dirs - the general method that does it all for files
     * and dirs and in bulk/
     *
     * @param localAbsPaths list of absolute paths to local files and dirs
     * @param addProgressUpdater notifier to receive progress notifications on
     * folders added, or null if not used
     * @return file set root VirtualDirectory contained containing all
     * AbstractFile objects added
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed. There is no "revert" logic if one of the additions fails. The
     * addition stops with the first error encountered.
     */
    public synchronized VirtualDirectory addLocalFilesDirs(List<String> localAbsPaths, FileAddProgressUpdater addProgressUpdater) throws TskCoreException {
        final List<java.io.File> rootsToAdd = new ArrayList<java.io.File>();
        //first validate all the inputs before any additions
        for (String absPath : localAbsPaths) {
            java.io.File localFile = new java.io.File(absPath);
            if (!localFile.exists() || !localFile.canRead()) {
                String msg = "One of the local files/dirs to add is not readable: " + localFile.getAbsolutePath() + ", aborting the process before any files added";
                logger.log(Level.SEVERE, msg);
                throw new TskCoreException(msg);
            }
            rootsToAdd.add(localFile);
        }

        final VirtualDirectory fileSetDir = addLocalFileSetDir();

        for (java.io.File localRootToAdd : rootsToAdd) {
            AbstractFile localFileAdded = null;
            if (localRootToAdd.isFile()) {
                localFileAdded = addLocalFileSingle(localRootToAdd.getAbsolutePath(), fileSetDir);
            } else {
                localFileAdded = this.addLocalDir(localRootToAdd.getAbsolutePath(), fileSetDir, addProgressUpdater);
            }
            if (localFileAdded == null) {
                String msg = "One of the local files/dirs could not be added: " + localRootToAdd.getAbsolutePath();
                logger.log(Level.SEVERE, msg);
                throw new TskCoreException(msg);
            } else {
                //added.add(localFileAdded);
                //send new content event
                //for now reusing ingest events, in future this will be replaced by datamodel / observer sending out events
                IngestServices.getDefault().fireModuleContentEvent(new ModuleContentEvent(localFileAdded));
            }
        }


        return fileSetDir;
    }

    /**
     * Adds a new virtual directory root object with FileSet X name and
     * consecutive sequence number characteristic to every add operation
     *
     * @return the virtual dir root container created
     * @throws TskCoreException
     */
    private VirtualDirectory addLocalFileSetDir() throws TskCoreException {

        VirtualDirectory created = null;

        int newFileSetCount = curNumFileSets + 1;
        final String fileSetName = VirtualDirectoryNode.LOGICAL_FILE_SET_PREFIX + newFileSetCount;

        try {
            created = tskCase.addVirtualDirectory(0, fileSetName);
            curNumFileSets = newFileSetCount;
        } catch (TskCoreException ex) {
            String msg = "Error creating local file set dir: " + fileSetName;
            logger.log(Level.SEVERE, msg, ex);
            throw new TskCoreException(msg, ex);
        }

        return created;
    }

    /**
     * Helper (internal) to add child of local dir recursively
     *
     * @param parentVd
     * @param childLocalFile
     * @param addProgressUpdater notifier to receive progress notifications on
     * folders added, or null if not used
     * @throws TskCoreException
     */
    private void addLocalDirectoryRecInt(VirtualDirectory parentVd,
            java.io.File childLocalFile, FileAddProgressUpdater addProgressUpdater) throws TskCoreException {

        final boolean isDir = childLocalFile.isDirectory();

        if (isDir) {
            //create virtual folder
            final VirtualDirectory childVd = tskCase.addVirtualDirectory(parentVd.getId(), childLocalFile.getName());
            if (childVd != null && addProgressUpdater != null ) {
                addProgressUpdater.fileAdded(childVd);
            }
            //add children recursively
            for (java.io.File childChild : childLocalFile.listFiles()) {
                addLocalDirectoryRecInt(childVd, childChild, addProgressUpdater);
            }
        } else {
            //add leaf file, base case
            this.addLocalFileSingle(childLocalFile.getAbsolutePath(), parentVd);
        }

    }

    /**
     * Return count of local files already in this case that represent the same
     * file/dir (have the same localAbsPath)
     *
     * @param parent parent dir of the files to check
     * @param localAbsPath local absolute path of the file to check
     * @param localName the name of the file to check
     * @return count of objects representing the same local file
     * @throws TskCoreException
     */
    private int getCountMatchingLocalFiles(AbstractFile parent, String localAbsPath, String localName) throws TskCoreException {
        int count = 0;

        for (Content child : parent.getChildren()) {

            if (child instanceof VirtualDirectory && localName.equals(child.getName())) {
                ++count;
            } else if (child instanceof AbstractFile
                    && localAbsPath.equals(((AbstractFile) child).getLocalAbsPath())) {
                ++count;
            }

        }

        return count;

    }

    /**
     * Add a local directory and its children recursively. Parent container of
     * the local dir is added for context.
     *
     *
     * @param localAbsPath local absolute path of root folder whose children are
     * to be added recursively. If there is a parent dir, it is added as a
     * container, for context.
     * @param fileSetDir the parent file set directory container
     * @param addProgressUpdater notifier to receive progress notifications on
     * folders added, or null if not used
     * @return parent virtual directory folder created representing the
     * localAbsPath node
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed, or if the localAbsPath could not be accessed
     */
    private synchronized VirtualDirectory addLocalDir(String localAbsPath, VirtualDirectory fileSetDir, FileAddProgressUpdater addProgressUpdater) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        final java.io.File localDir = new java.io.File(localAbsPath);
        //final String localName = localDir.getName();
        if (!localDir.exists()) {
            throw new TskCoreException("Attempted to add a local dir that does not exist: " + localAbsPath);
        }
        if (!localDir.canRead()) {
            throw new TskCoreException("Attempted to add a local dir that is not readable: " + localAbsPath);
        }

        if (!localDir.isDirectory()) {
            throw new TskCoreException("Attempted to add a local dir that is not a directory: " + localAbsPath);
        }


        String rootVdName = localDir.getName();

        VirtualDirectory rootVd = null;
        try {
            rootVd = tskCase.addVirtualDirectory(fileSetDir.getId(), rootVdName);
            if (rootVd != null && addProgressUpdater != null) {
                addProgressUpdater.fileAdded(rootVd);
            }
        } catch (TskCoreException e) {
            //log and rethrow
            final String msg = "Error creating root dir for local dir to be added, can't addLocalDir: " + localDir;
            logger.log(Level.SEVERE, msg, e);
            throw new TskCoreException(msg);
        }

        try {
            java.io.File[] localChildren = localDir.listFiles();
            for (java.io.File localChild : localChildren) {
                //add children recursively, at a time in separate transaction
                //consider a single transaction for everything
                addLocalDirectoryRecInt(rootVd, localChild, addProgressUpdater);
            }
        } catch (TskCoreException e) {
            final String msg = "Error creating local children for local dir to be added, can't fully add addLocalDir: " + localDir;
            logger.log(Level.SEVERE, msg, e);
            throw new TskCoreException(msg);
        }


        return rootVd;
    }

    /**
     * Creates a single local file under parentFile for the case, adds it to the
     * database and returns it. Does not refresh the views of data.
     *
     * @param localAbsPath local absolute path of the local file, including the
     * file name
     * @param parentFile parent file object container (such as virtual
     * directory, another local file, or fscontent File),
     * @return newly created local file object added to the database
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed
     *
     */
    private synchronized LocalFile addLocalFileSingle(String localAbsPath, AbstractFile parentFile ) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        java.io.File localFile = new java.io.File(localAbsPath);
        if (!localFile.exists()) {
            throw new TskCoreException("Attempted to add a local file that does not exist: " + localAbsPath);
        }
        if (!localFile.canRead()) {
            throw new TskCoreException("Attempted to add a local file that is not readable: " + localAbsPath);
        }

        long size = localFile.length();
        boolean isFile = localFile.isFile();

        long ctime = 0;
        long crtime = 0;
        long atime = 0;
        long mtime = 0;

        String fileName = localFile.getName();

        LocalFile lf = tskCase.addLocalFile(fileName, localAbsPath, size,
                ctime, crtime, atime, mtime,
                isFile, parentFile);

        return lf;
    }

    @Override
    public synchronized void close() throws IOException {
        tskCase = null;
    }
}
