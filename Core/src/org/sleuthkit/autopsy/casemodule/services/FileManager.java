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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.CarvedFileContainer;

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
            logger.log(Level.SEVERE, "Error initializing FileManager and getting number of local file sets"); //NON-NLS
        }

    }
    
    /**
     * Finds a set of files that meets the name criteria in all data sources in the current case.
     *
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         the given fileName
     */
    public synchronized List<AbstractFile> findFiles(String fileName) throws TskCoreException {
        List<AbstractFile> result = new ArrayList<>();
        
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles.exception.msg"));
        }
        List<Content> dataSources = tskCase.getRootObjects();
        for (Content dataSource : dataSources) {
            result.addAll(findFiles(dataSource, fileName));
        }
        return result;
    }

    /**
     * Finds a set of files that meets the name criteria in all data sources in the current case.
     *
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     * @param dirName    Pattern of the name of the parent directory to use as
     *                   the root of the search (case insensitive, used in LIKE
     *                   SQL statement).
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         fileName and whose parent directory contains dirName.
     */
    public synchronized List<AbstractFile> findFiles(String fileName, String dirName) throws TskCoreException {
        List<AbstractFile> result = new ArrayList<>();
        
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles2.exception.msg"));
        }
        List<Content> dataSources = tskCase.getRootObjects();
        for (Content dataSource : dataSources) {
            result.addAll(findFiles(dataSource, fileName, dirName));
        }
        return result;
    }

    /**
     * Finds a set of files that meets the name criteria in all data sources in the current case.
     *
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     * @param parentFile Object of root/parent directory to restrict search to.
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         fileName and that were inside a directory described by
     *         parentFsContent.
     */
    public synchronized List<AbstractFile> findFiles(String fileName, AbstractFile parentFile) throws TskCoreException {
        List<AbstractFile> result = new ArrayList<>();
        
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles3.exception.msg"));
        }
        List<Content> dataSources = tskCase.getRootObjects();
        for (Content dataSource : dataSources) {
          result.addAll(findFiles(dataSource, fileName, parentFile));
        }
        return result;
    }

    /**
     * Finds a set of files that meets the name criteria.
     *
     * @param dataSource Root data source to limit search results to (Image,
     *                   VirtualDirectory, etc.).
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         the given fileName
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles.exception.msg"));
        }
        return tskCase.findFiles(dataSource, fileName);
    }

    /**
     * Finds a set of files that meets the name criteria.
     *
     * @param dataSource Root data source to limit search results to (Image,
     *                   VirtualDirectory, etc.).
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     * @param dirName    Pattern of the name of the parent directory to use as
     *                   the root of the search (case insensitive, used in LIKE
     *                   SQL statement).
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         fileName and whose parent directory contains dirName.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, String dirName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles2.exception.msg"));
        }
        return tskCase.findFiles(dataSource, fileName, dirName);
    }

    /**
     * Finds a set of files that meets the name criteria.
     *
     * @param dataSource Root data source to limit search results to (Image,
     *                   VirtualDirectory, etc.).
     * @param fileName   Pattern of the name of the file or directory to match
     *                   (case insensitive, used in LIKE SQL statement).
     * @param parentFile Object of root/parent directory to restrict search to.
     *
     * @return a list of AbstractFile for files/directories whose name matches
     *         fileName and that were inside a directory described by
     *         parentFsContent.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, AbstractFile parentFile) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.findFiles3.exception.msg"));
        }
        return findFiles(dataSource, fileName, parentFile.getName());
    }

    /**
     * @param dataSource data source Content (Image, parent-less
     *                   VirtualDirectory) where to find files
     * @param filePath   The full path to the file(s) of interest. This can
     *                   optionally include the image and volume names.
     *
     * @return a list of AbstractFile that have the given file path.
     */
    public synchronized List<AbstractFile> openFiles(Content dataSource, String filePath) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.openFiles.exception.msg"));
        }
        return tskCase.openFiles(dataSource, filePath);
    }

    /**
     * Creates a derived file, adds it to the database and returns it.
     *
     * @param fileName        file name the derived file
     * @param localPath       local path of the derived file, including the file
     *                        name. The path is relative to the case folder.
     * @param size            size of the derived file in bytes
     * @param ctime
     * @param crtime
     * @param atime
     * @param mtime
     * @param isFile          whether a file or directory, true if a file
     * @param parentFile      the parent file object this the new file was
     *                        derived from, either a fs file or parent derived
     *                        file/dikr\\r
     * @param rederiveDetails details needed to re-derive file (will be specific
     *                        to the derivation method), currently unused
     * @param toolName        name of derivation method/tool, currently unused
     * @param toolVersion     version of derivation method/tool, currently
     *                        unused
     * @param otherDetails    details of derivation method/tool, currently
     *                        unused
     *
     * @return newly created derived file object added to the database
     *
     * @throws TskCoreException exception thrown if the object creation failed
     *                          due to a critical system error or of the file
     *                          manager has already been closed
     *
     */
    public synchronized DerivedFile addDerivedFile(String fileName, String localPath, long size,
            long ctime, long crtime, long atime, long mtime,
            boolean isFile, AbstractFile parentFile,
            String rederiveDetails, String toolName, String toolVersion, String otherDetails) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.addDerivedFile.exception.msg"));
        }

        return tskCase.addDerivedFile(fileName, localPath, size,
                ctime, crtime, atime, mtime,
                isFile, parentFile, rederiveDetails, toolName, toolVersion, otherDetails);
    }

    /**
     * Adds a carved file to the VirtualDirectory '$CarvedFiles' in the volume
     * or image given by systemId.
     *
     * @param carvedFileName the name of the carved file (containing appropriate
     *                       extension)
     * @param carvedFileSize size of the carved file to add
     * @param systemId       the ID of the parent volume or file system
     * @param sectors        a list of SectorGroups giving this sectors that
     *                       make up this carved file.
     *
     * @throws TskCoreException exception thrown when critical tsk error
     *                          occurred and carved file could not be added
     */
    public synchronized LayoutFile addCarvedFile(String carvedFileName, long carvedFileSize,
            long systemId, List<TskFileRange> sectors) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.addCarvedFile.exception.msg"));
        }

        return tskCase.addCarvedFile(carvedFileName, carvedFileSize, systemId, sectors);
    }

    /**
     * Adds a collection of carved files to the VirtualDirectory '$CarvedFiles'
     * in the volume or image given by systemId. Creates $CarvedFiles if it does
     * not exist already.
     *
     * @param filesToAdd a list of CarvedFileContainer files to add as carved
     *                   files
     *
     * @return List<LayoutFile> This is a list of the files added to the
     *         database
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<LayoutFile> addCarvedFiles(List<CarvedFileContainer> filesToAdd) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.addCarvedFile.exception.msg"));
        } else {
            return tskCase.addCarvedFiles(filesToAdd);
        }
    }

    /**
     *
     * Interface for receiving notifications on folders being added via a
     * callback
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
     * Add a set of local/logical files and dirs.
     *
     * @param localAbsPaths      list of absolute paths to local files and dirs
     * @param addProgressUpdater notifier to receive progress notifications on
     *                           folders added, or null if not used
     *
     * @return file set root VirtualDirectory contained containing all
     *         AbstractFile objects added
     *
     * @throws TskCoreException exception thrown if the object creation failed
     *                          due to a critical system error or of the file
     *                          manager has already been closed. There is no
     *                          "revert" logic if one of the additions fails.
     *                          The addition stops with the first error
     *                          encountered.
     */
    public synchronized VirtualDirectory addLocalFilesDirs(List<String> localAbsPaths, FileAddProgressUpdater addProgressUpdater) throws TskCoreException {
        final List<java.io.File> rootsToAdd = new ArrayList<>();
        //first validate all the inputs before any additions
        for (String absPath : localAbsPaths) {
            java.io.File localFile = new java.io.File(absPath);
            if (!localFile.exists() || !localFile.canRead()) {
                String msg = NbBundle
                        .getMessage(this.getClass(), "FileManager.addLocalFilesDirs.exception.notReadable.msg",
                                localFile.getAbsolutePath());
                logger.log(Level.SEVERE, msg);
                throw new TskCoreException(msg);
            }
            rootsToAdd.add(localFile);
        }

        CaseDbTransaction trans = tskCase.beginTransaction();
        // make a virtual top-level directory for this set of files/dirs
        final VirtualDirectory fileSetRootDir = addLocalFileSetRootDir(trans);

        try {
            // recursively add each item in the set
            for (java.io.File localRootToAdd : rootsToAdd) {
                AbstractFile localFileAdded = addLocalDirInt(trans, fileSetRootDir, localRootToAdd, addProgressUpdater);

                if (localFileAdded == null) {
                    String msg = NbBundle
                            .getMessage(this.getClass(), "FileManager.addLocalFilesDirs.exception.cantAdd.msg",
                                    localRootToAdd.getAbsolutePath());
                    logger.log(Level.SEVERE, msg);
                    throw new TskCoreException(msg);
                } else {
                    //added.add(localFileAdded);
                    //send new content event
                    //for now reusing ingest events, in future this will be replaced by datamodel / observer sending out events
                    // @@@ Is this the right place for this? A directory tree refresh will be triggered, so this may be creating a race condition
                    // since the transaction is not yet committed.   
                    IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(localFileAdded));
                }
            }

            trans.commit();
        } catch (TskCoreException ex) {
            trans.rollback();
        }
        return fileSetRootDir;
    }

    /**
     * Adds a new virtual directory root object with FileSet X name and
     * consecutive sequence number characteristic to every add operation
     *
     * @return the virtual dir root container created
     *
     * @throws TskCoreException
     */
    private VirtualDirectory addLocalFileSetRootDir(CaseDbTransaction trans) throws TskCoreException {

        VirtualDirectory created = null;

        int newFileSetCount = curNumFileSets + 1;
        final String fileSetName = VirtualDirectoryNode.LOGICAL_FILE_SET_PREFIX + newFileSetCount;

        try {
            created = tskCase.addVirtualDirectory(0, fileSetName, trans);
            curNumFileSets = newFileSetCount;
        } catch (TskCoreException ex) {
            String msg = NbBundle
                    .getMessage(this.getClass(), "FileManager.addLocalFileSetRootDir.exception.errCreateDir.msg",
                            fileSetName);
            logger.log(Level.SEVERE, msg, ex);
            throw new TskCoreException(msg, ex);
        }

        return created;
    }

    /**
     * Helper (internal) method to recursively add contents of a folder. Node
     * passed in can be a file or directory. Children of directories are added.
     *
     * @param parentVd           Dir that is the parent of localFile
     * @param localFile          File/Dir that we are adding
     * @param addProgressUpdater notifier to receive progress notifications on
     *                           folders added, or null if not used
     *
     * @returns File object of file added or new virtualdirectory for the
     * directory.
     * @throws TskCoreException
     */
    private AbstractFile addLocalDirInt(CaseDbTransaction trans, VirtualDirectory parentVd,
            java.io.File localFile, FileAddProgressUpdater addProgressUpdater) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException(
                    NbBundle.getMessage(this.getClass(), "FileManager.addLocalDirInt.exception.closed.msg"));
        }

        //final String localName = localDir.getName();
        if (!localFile.exists()) {
            throw new TskCoreException(
                    NbBundle.getMessage(this.getClass(), "FileManager.addLocalDirInt.exception.doesntExist.msg",
                            localFile.getAbsolutePath()));
        }
        if (!localFile.canRead()) {
            throw new TskCoreException(
                    NbBundle.getMessage(this.getClass(), "FileManager.addLocalDirInt.exception.notReadable.msg",
                            localFile.getAbsolutePath()));
        }

        if (localFile.isDirectory()) {
            //create virtual folder (we don't have a notion of a 'local folder')
            final VirtualDirectory childVd = tskCase.addVirtualDirectory(parentVd.getId(), localFile.getName(), trans);
            if (childVd != null && addProgressUpdater != null) {
                addProgressUpdater.fileAdded(childVd);
            }
            //add children recursively
            final java.io.File[] childrenFiles = localFile.listFiles();
            if (childrenFiles != null) {
                for (java.io.File childFile : childrenFiles) {
                    addLocalDirInt(trans, childVd, childFile, addProgressUpdater);
                }
            }
            return childVd;
        } else {
            //add leaf file, base case
            return this.addLocalFileInt(parentVd, localFile, trans);
        }
    }

    /**
     * Adds a single local/logical file to the case. Adds it to the database.
     * Does not refresh the views of data. Assumes that the local file exists
     * and can be read. This checking is done by addLocalDirInt().
     *
     * @param parentFile parent file object container (such as virtual
     *                   directory, another local file, or fscontent File),
     * @param localFile  File that we are adding
     *
     * @return newly created local file object added to the database
     *
     * @throws TskCoreException exception thrown if the object creation failed
     *                          due to a critical system error or of the file
     *                          manager has already been closed
     */
    private synchronized LocalFile addLocalFileInt(AbstractFile parentFile, java.io.File localFile, CaseDbTransaction trans) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException(
                    NbBundle.getMessage(this.getClass(), "FileManager.addLocalDirInt2.exception.closed.msg"));
        }

        long size = localFile.length();
        boolean isFile = localFile.isFile();

        long ctime = 0;
        long crtime = 0;
        long atime = 0;
        long mtime = 0;

        String fileName = localFile.getName();

        LocalFile lf = tskCase.addLocalFile(fileName, localFile.getAbsolutePath(), size,
                ctime, crtime, atime, mtime,
                isFile, parentFile, trans);

        return lf;
    }

    @Override
    public synchronized void close() throws IOException {
        tskCase = null;
    }
}
