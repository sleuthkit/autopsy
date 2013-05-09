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
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * Abstraction to facilitate access to files and directories.
 */
public class FileManager implements Closeable {

    private SleuthkitCase tskCase;
    private static final Logger logger = Logger.getLogger(FileManager.class.getName());

    public FileManager(SleuthkitCase tskCase) {
        this.tskCase = tskCase;
    }

    /**
     * @param image image where to find files
     * @param fileName the name of the file or directory to match
     * @return a list of FsContent for files/directories whose name matches the
     * given fileName
     */
    public synchronized List<FsContent> findFiles(Image image, String fileName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.findFiles(image, fileName);
    }

    /**
     * @param image image where to find files
     * @param fileName the name of the file or directory to match
     * @param dirName the name of a parent directory of fileName
     * @return a list of FsContent for files/directories whose name matches
     * fileName and whose parent directory contains dirName.
     */
    public synchronized List<FsContent> findFiles(Image image, String fileName, String dirName) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.findFiles(image, fileName, dirName);
    }

    /**
     * @param image image where to find files
     * @param fileName the name of the file or directory to match
     * @param parentFsContent
     * @return a list of FsContent for files/directories whose name matches
     * fileName and that were inside a directory described by parentFsContent.
     */
    public synchronized List<FsContent> findFiles(Image image, String fileName, FsContent parentFsContent) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return findFiles(image, fileName, parentFsContent.getName());
    }

    /**
     * @param image image where to find files
     * @param filePath The full path to the file(s) of interest. This can
     * optionally include the image and volume names.
     * @return a list of FsContent that have the given file path.
     */
    public synchronized List<FsContent> openFiles(Image image, String filePath) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }
        return tskCase.openFiles(image, filePath);
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
     * Helper (internal) to add child of local dir recursively
     * @param parentVd
     * @param childLocalFile
     * @throws TskCoreException 
     */
    private void addLocalDirectoryRecInt(VirtualDirectory parentVd,
            java.io.File childLocalFile) throws TskCoreException {

        final boolean isDir = childLocalFile.isDirectory();

        if (isDir) {
            //create virtual folder
            final VirtualDirectory childVd = tskCase.addVirtualDirectory(parentVd.getId(), childLocalFile.getName());
            //add children recursively
            for (java.io.File childChild : childLocalFile.listFiles()) {
                addLocalDirectoryRecInt(childVd, childChild);
            }
        } else {
            //add leaf file, base case
            this.addLocalFileSingle(childLocalFile.getAbsolutePath(), parentVd);
        }

    }

    /**
     * Add a local directory and its children recursively. 
     * Parent container of the local dir is added for context.
     * 
     * Does not refresh the views of data (client must do it currently, 
     * will be addressed in future with node auto-refresh support)
     * 
     *
     * @param localAbsPath local absolute path of root folder whose children are
     * to be added recursively.  If there is a parent dir, it is added as a container, for context.
     * @return parent virtual directory folder created representing the
     * localAbsPath node
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed, or if the localAbsPath could not be accessed
     */
    public synchronized VirtualDirectory addLocalDir(String localAbsPath) throws TskCoreException {
        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        java.io.File localDir = new java.io.File(localAbsPath);
        if (!localDir.exists()) {
            throw new TskCoreException("Attempted to add a local dir that does not exist: " + localAbsPath);
        }
        if (!localDir.canRead()) {
            throw new TskCoreException("Attempted to add a local dir that is not readable: " + localAbsPath);
        }

        if (!localDir.isDirectory()) {
            throw new TskCoreException("Attempted to add a local dir that is not a directory: " + localAbsPath);
        }
        
        String parentName = null;
        java.io.File parentDir = localDir.getParentFile();
        if (parentDir != null) {
            parentName = parentDir.getName();
        }

        final String rootVdName = localDir.getName();

        VirtualDirectory rootVd = null;
        try {
            final long localFilesRootId = tskCase.getLocalFilesRootDirectoryId();
            if (parentName == null) {
                rootVd = tskCase.addVirtualDirectory(localFilesRootId, rootVdName);
            }
            else {
                //add parent dir for context
                final VirtualDirectory contextDir = tskCase.addVirtualDirectory(localFilesRootId, parentName);
                rootVd = tskCase.addVirtualDirectory(contextDir.getId(), rootVdName);
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
                //add childrnen recursively, at a time in separate transaction
                //consider a single transaction for everything
                addLocalDirectoryRecInt(rootVd, localChild);
            }
        } catch (TskCoreException e) {
            final String msg = "Error creating local children for local dir to be added, can't fully add addLocalDir: " + localDir;
            logger.log(Level.SEVERE, msg, e);
            throw new TskCoreException(msg);
        }


        return rootVd;
    }

    /**
     * Creates a single local file under $LocalFiles for the case, adds it to
     * the database and returns it.   Does not refresh the views of data.
     *
     * @param localAbsPath local absolute path of the local file, including the
     * file name.
     * @return newly created local file object added to the database
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed, or if the localAbsPath could not be accessed
     *
     */
    public synchronized LocalFile addLocalFileSingle(String localAbsPath) throws TskCoreException {

        if (tskCase == null) {
            throw new TskCoreException("Attempted to use FileManager after it was closed.");
        }

        return addLocalFileSingle(localAbsPath, null);
    }

    /**
     * Creates a single local file under parentFile for the case, adds it to the
     * database and returns it.  Does not refresh the views of data.
     *
     * @param localAbsPath local absolute path of the local file, including the
     * file name
     * @param parentFile parent file object (such as virtual directory, another
     * local file, or fscontent File),
     * @return newly created local file object added to the database
     * @throws TskCoreException exception thrown if the object creation failed
     * due to a critical system error or of the file manager has already been
     * closed
     *
     */
    public synchronized LocalFile addLocalFileSingle(String localAbsPath, AbstractFile parentFile) throws TskCoreException {

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

        //TODO what should do with mac times?
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
