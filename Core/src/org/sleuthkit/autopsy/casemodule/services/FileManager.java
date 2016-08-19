/*
 *
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.Collection;
import java.util.List;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitCase.CaseDbTransaction;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskFileRange;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.TskDataException;
import org.apache.commons.lang3.StringUtils;
import org.sleuthkit.datamodel.CarvingResult;
import org.sleuthkit.datamodel.TskData;

/**
 * A manager that provides methods for retrieving files from the current case
 * and for adding local files, carved files, and derived files to the current
 * case.
 */
public class FileManager implements Closeable {

    private SleuthkitCase caseDb;

    /**
     * Constructs a manager that provides methods for retrieving files from the
     * current case and for adding local files, carved files, and derived files
     * to the current case.
     *
     * @param caseDb The case database.
     */
    public FileManager(SleuthkitCase caseDb) {
        this.caseDb = caseDb;
    }

    /**
     * Finds all files with types that match one of a collection of MIME types.
     *
     * @param mimeTypes The MIME types.
     *
     * @return The files.
     *
     * @throws TskCoreException If there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFilesByMimeType(Collection<String> mimeTypes) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.findAllFilesWhere(createFileTypeInCondition(mimeTypes));
    }

    /**
     * Finds all files in a given data source (image, local/logical files set,
     * etc.) with types that match one of a collection of MIME types.
     *
     * @param dataSource The data source.
     * @param mimeTypes  The MIME types.
     *
     * @return The files.
     *
     * @throws TskCoreException If there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFilesByMimeType(Content dataSource, Collection<String> mimeTypes) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.findAllFilesWhere("data_source_obj_id = " + dataSource.getId() + " AND " + createFileTypeInCondition(mimeTypes));
    }

    /**
     * Converts a list of MIME types into an SQL "mime_type IN" condition.
     *
     * @param mimeTypes The MIIME types.
     *
     * @return The condition string.
     */
    private static String createFileTypeInCondition(Collection<String> mimeTypes) {
        String types = StringUtils.join(mimeTypes, "', '");
        return "mime_type IN ('" + types + "')";
    }

    /**
     * Finds all files and directories with a given file name. The name search
     * is for full or partial matches and is case insensitive (a case
     * insensitive SQL LIKE clause is used to query the case database).
     *
     * @param fileName The full or partial file name.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(String fileName) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        List<AbstractFile> result = new ArrayList<>();
        List<Content> dataSources = caseDb.getRootObjects();
        for (Content dataSource : dataSources) {
            result.addAll(findFiles(dataSource, fileName));
        }
        return result;
    }

    /**
     * Finds all files and directories with a given file name and parent file or
     * directory name. The name searches are for full or partial matches and are
     * case insensitive (a case insensitive SQL LIKE clause is used to query the
     * case database).
     *
     * @param fileName   The full or partial file name.
     * @param parentName The full or partial parent file or directory name.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(String fileName, String parentName) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        List<AbstractFile> result = new ArrayList<>();
        List<Content> dataSources = caseDb.getRootObjects();
        for (Content dataSource : dataSources) {
            result.addAll(findFiles(dataSource, fileName, parentName));
        }
        return result;
    }

    /**
     * Finds all files and directories with a given file name and parent file or
     * directory. The name search is for full or partial matches and is case
     * insensitive (a case insensitive SQL LIKE clause is used to query the case
     * database).
     *
     * @param fileName The full or partial file name.
     * @param parent   The parent file or directory.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(String fileName, AbstractFile parent) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        List<AbstractFile> result = new ArrayList<>();
        List<Content> dataSources = caseDb.getRootObjects();
        for (Content dataSource : dataSources) {
            result.addAll(findFiles(dataSource, fileName, parent));
        }
        return result;
    }

    /**
     * Finds all files and directories with a given file name in a given data
     * source (image, local/logical files set, etc.). The name search is for
     * full or partial matches and is case insensitive (a case insensitive SQL
     * LIKE clause is used to query the case database).
     *
     * @param dataSource The data source.
     * @param fileName   The full or partial file name.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.findFiles(dataSource, fileName);
    }

    /**
     * Finds all files and directories with a given file name and parent file or
     * directory name in a given data source (image, local/logical files set,
     * etc.). The name searches are for full or partial matches and are case
     * insensitive (a case insensitive SQL LIKE clause is used to query the case
     * database).
     *
     * @param dataSource The data source.
     * @param fileName   The full or partial file name.
     * @param parentName The full or partial parent file or directory name.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, String parentName) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.findFiles(dataSource, fileName, parentName);
    }

    /**
     * Finds all files and directories with a given file name and given parent
     * file or directory in a given data source (image, local/logical files set,
     * etc.). The name search is for full or partial matches and is case
     * insensitive (a case insensitive SQL LIKE clause is used to query the case
     * database).
     *
     * @param dataSource The data source.
     * @param fileName   The full or partial file name.
     * @param parent     The parent file or directory.
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> findFiles(Content dataSource, String fileName, AbstractFile parent) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return findFiles(dataSource, fileName, parent.getName());
    }

    /**
     * Finds all files and directories with a given file name and path in a
     * given data source (image, local/logical files set, etc.). The name search
     * is for full or partial matches and is case insensitive (a case
     * insensitive SQL LIKE clause is used to query the case database). Any path
     * components at the volume level and above are removed for the search.
     *
     * @param dataSource The data source.
     * @param fileName   The full or partial file name.
     * @param filePath   The file path (path components volume at the volume
     *                   level or above will be removed).
     *
     * @return The matching files and directories.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public synchronized List<AbstractFile> openFiles(Content dataSource, String filePath) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.openFiles(dataSource, filePath);
    }

    /**
     * Adds a derived file to the case.
     *
     * @param fileName        The name of the file.
     * @param localPath       The local path of the file, relative to the case
     *                        folder and including the file name.
     * @param size            The size of the file in bytes.
     * @param ctime           The change time of the file.
     * @param crtime          The create time of the file
     * @param atime           The accessed time of the file.
     * @param mtime           The modified time of the file.
     * @param isFile          True if a file, false if a directory.
     * @param parentFile      The parent file from which the file was derived.
     * @param rederiveDetails The details needed to re-derive file (will be
     *                        specific to the derivation method), currently
     *                        unused.
     * @param toolName        The name of the derivation method or tool,
     *                        currently unused.
     * @param toolVersion     The version of the derivation method or tool,
     *                        currently unused.
     * @param otherDetails    Other details of the derivation method or tool,
     *                        currently unused.
     * @param encodingType    Type of encoding used on the file
     *
     * @return A DerivedFile object representing the derived file.
     *
     * @throws TskCoreException if there is a problem adding the file to the
     *                          case database.
     */
    public synchronized DerivedFile addDerivedFile(String fileName,
            String localPath,
            long size,
            long ctime, long crtime, long atime, long mtime,
            boolean isFile,
            AbstractFile parentFile,
            String rederiveDetails, String toolName, String toolVersion, String otherDetails,
            TskData.EncodingType encodingType) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.addDerivedFile(fileName, localPath, size,
                ctime, crtime, atime, mtime,
                isFile, parentFile, rederiveDetails, toolName, toolVersion, otherDetails, encodingType);
    }

    /**
     * Adds a carving result to the case database.
     *
     * @param carvingResult The carving result (a set of carved files and their
     *                      parent) to be added.
     *
     * @return A list of LayoutFile representations of the carved files.
     *
     * @throws TskCoreException If there is a problem completing a case database
     *                          operation.
     */
    public synchronized List<LayoutFile> addCarvedFiles(CarvingResult carvingResult) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.addCarvedFiles(carvingResult);
    }
        
    /**
     * Interface for receiving a notification for each file or directory added
     * to the case database by a FileManager add files operation.
     */
    public interface FileAddProgressUpdater {

        /**
         * Called after a file or directory is added to the case database.
         *
         * @param An AbstractFile represeting the added file or directory.
         */
        void fileAdded(AbstractFile newFile);
    }

    /**
     * Adds a set of local/logical files and/or directories to the case database
     * as data source.
     *
     * @param deviceId                 An ASCII-printable identifier for the
     *                                 device associated with the data source
     *                                 that is intended to be unique across
     *                                 multiple cases (e.g., a UUID).
     * @param rootVirtualDirectoryName The name to give to the virtual directory
     *                                 that will serve as the root for the
     *                                 local/logical files and/or directories
     *                                 that compose the data source. Pass the
     *                                 empty string to get a default name of the
     *                                 form: LogicalFileSet[N]
     * @param timeZone                 The time zone used to process the data
     *                                 source, may be the empty string.
     * @param localFilePaths           A list of local/logical file and/or
     *                                 directory localFilePaths.
     * @param progressUpdater          Called after each file/directory is added
     *                                 to the case database.
     *
     * @return A local files data source object.
     *
     * @throws TskCoreException If there is a problem completing a database
     *                          operation.
     * @throws TskDataException if any of the local file paths is for a file or
     *                          directory that does not exist or cannot be read.
     */
    public synchronized LocalFilesDataSource addLocalFilesDataSource(String deviceId, String rootVirtualDirectoryName, String timeZone, List<String> localFilePaths, FileAddProgressUpdater progressUpdater) throws TskCoreException, TskDataException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        List<java.io.File> localFiles = getFilesAndDirectories(localFilePaths);
        CaseDbTransaction trans = null;
        try {
            String rootDirectoryName = rootVirtualDirectoryName;
            if (rootDirectoryName.isEmpty()) {
                rootDirectoryName = generateFilesDataSourceName(caseDb);
            }

            /*
             * Add the root virtual directory and its local/logical file
             * children to the case database.
             */
            trans = caseDb.beginTransaction();
            LocalFilesDataSource dataSource = caseDb.addLocalFilesDataSource(deviceId, rootDirectoryName, timeZone, trans);
            VirtualDirectory rootDirectory = dataSource.getRootDirectory();
            List<AbstractFile> filesAdded = new ArrayList<>();
            for (java.io.File localFile : localFiles) {
                AbstractFile fileAdded = addLocalFile(trans, rootDirectory, localFile, TskData.EncodingType.NONE, progressUpdater);
                if (null != fileAdded) {
                    filesAdded.add(fileAdded);
                } else {
                    throw new TskCoreException(NbBundle.getMessage(this.getClass(), "FileManager.addLocalFilesDirs.exception.cantAdd.msg", localFile.getAbsolutePath()));
                }
            }
            trans.commit();

            /*
             * Publish content added events for the added files and directories.
             */
            for (AbstractFile fileAdded : filesAdded) {
                IngestServices.getInstance().fireModuleContentEvent(new ModuleContentEvent(fileAdded));
            }

            return dataSource;

        } catch (TskCoreException ex) {
            if (null != trans) {
                trans.rollback();
            }
            throw ex;
        }
    }

    /**
     * Generates a name for the root virtual directory for the data source.
     *
     * NOTE: Although this method is guarded by the file manager's monitor,
     * there is currently a minimal chance of default name duplication for
     * multi-user cases with multiple FileManagers running on different nodes.
     *
     * @return A default name for a local/logical files data source of the form:
     *         LogicalFileSet[N].
     *
     * @throws TskCoreException If there is a problem querying the case
     *                          database.
     */
    private static synchronized String generateFilesDataSourceName(SleuthkitCase caseDb) throws TskCoreException {
        int localFileDataSourcesCounter = 0;
        try {
            List<VirtualDirectory> localFileDataSources = caseDb.getVirtualDirectoryRoots();
            for (VirtualDirectory vd : localFileDataSources) {
                if (vd.getName().startsWith(VirtualDirectoryNode.LOGICAL_FILE_SET_PREFIX)) {
                    ++localFileDataSourcesCounter;
                }
            }
            return VirtualDirectoryNode.LOGICAL_FILE_SET_PREFIX + (localFileDataSourcesCounter + 1);
        } catch (TskCoreException ex) {
            throw new TskCoreException("Error querying for existing local file data sources with defualt names", ex);
        }
    }

    /**
     * Converts a list of local/logical file and/or directory paths to a list of
     * file objects.
     *
     * @param localFilePaths A list of local/logical file and/or directory
     *                       paths.
     *
     * @return A list of file objects.
     *
     * @throws TskDataException if any of the paths is for a file or directory
     *                          that does not exist or cannot be read.
     */
    private List<java.io.File> getFilesAndDirectories(List<String> localFilePaths) throws TskDataException {
        List<java.io.File> localFiles = new ArrayList<>();
        for (String path : localFilePaths) {
            java.io.File localFile = new java.io.File(path);
            if (!localFile.exists() || !localFile.canRead()) {
                throw new TskDataException(String.format("File at %s does not exist or cannot be read", localFile.getAbsolutePath()));
            }
            localFiles.add(localFile);
        }
        return localFiles;
    }

    /**
     * Adds a file or directory of logical/local files data source to the case
     * database, recursively adding the contents of directories.
     *
     * @param trans              A case database transaction.
     * @param parentDirectory    The root virtual direcotry of the data source.
     * @param localFile          The local/logical file or directory.
     * @param addProgressUpdater notifier to receive progress notifications on
     *                           folders added, or null if not used
     * @param encodingType       Type of encoding used when storing the file
     *
     * @returns File object of file added or new virtualdirectory for the
     * directory.
     * @param progressUpdater    Called after each file/directory is added to
     *                           the case database.
     *
     * @return An AbstractFile representation of the local/logical file.
     *
     * @throws TskCoreException If there is a problem completing a database
     *                          operation.
     */
    private AbstractFile addLocalFile(CaseDbTransaction trans, VirtualDirectory parentDirectory, java.io.File localFile, 
            TskData.EncodingType encodingType, FileAddProgressUpdater progressUpdater) throws TskCoreException {
        if (localFile.isDirectory()) {
            /*
             * Add the directory as a virtual directory.
             */
            VirtualDirectory virtualDirectory = caseDb.addVirtualDirectory(parentDirectory.getId(), localFile.getName(), trans);
            progressUpdater.fileAdded(virtualDirectory);

            /*
             * Add its children, if any.
             */
            final java.io.File[] childFiles = localFile.listFiles();
            if (childFiles != null && childFiles.length > 0) {
                for (java.io.File childFile : childFiles) {
                    addLocalFile(trans, virtualDirectory, childFile, progressUpdater);
                }
            }

            return virtualDirectory;
        } else {
            return caseDb.addLocalFile(localFile.getName(), localFile.getAbsolutePath(), localFile.length(),
                    0, 0, 0, 0,
                    localFile.isFile(), encodingType, parentDirectory, trans);
        }
    }

    /**
     * Closes the file manager.
     *
     * @throws IOException If there is a problem closing the file manager.
     */
    @Override
    public synchronized void close() throws IOException {
        caseDb = null;
    }
        
    /**
     * Adds a set of local/logical files and/or directories to the case database
     * as data source.
     *
     * @param localFilePaths  A list of local/logical file and/or directory
     *                        localFilePaths.
     * @param progressUpdater Called after each file/directory is added to the
     *                        case database.
     *
     * @return The root virtual directory for the local/logical files data
     *         source.
     *
     * @throws TskCoreException If any of the local file paths is for a file or
     *                          directory that does not exist or cannot be read,
     *                          or there is a problem completing a database
     *                          operation.
     * @deprecated Use addLocalFilesDataSource instead.
     */
    @Deprecated
    public synchronized VirtualDirectory addLocalFilesDirs(List<String> localFilePaths, FileAddProgressUpdater progressUpdater) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        try {
            return addLocalFilesDataSource("", "", "", localFilePaths, progressUpdater).getRootDirectory();
        } catch (TskDataException ex) {
            throw new TskCoreException(ex.getLocalizedMessage(), ex);
        }
    }

    /**
     * Adds a carved file to the '$CarvedFiles' virtual directory of a data
     * source, volume or file system.
     *
     * @param fileName    The name of the file.
     * @param fileSize    The size of the file.
     * @param parentObjId The object id of the parent data source, volume or
     *                    file system.
     * @param layout      A list of the offsets and sizes that gives the layout
     *                    of the file within its parent.
     *
     * @return A LayoutFile object representing the carved file.
     *
     * @throws TskCoreException if there is a problem adding the file to the
     *                          case database.
     * @deprecated Use List<LayoutFile> addCarvedFiles(CarvingResult
     * carvingResult instead.
     */
    @Deprecated
    public synchronized LayoutFile addCarvedFile(String fileName, long fileSize, long parentObjId, List<TskFileRange> layout) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        Content parent = caseDb.getContentById(parentObjId);
        List<CarvingResult.CarvedFile> carvedFiles = new ArrayList<>();
        carvedFiles.add(new CarvingResult.CarvedFile(fileName, fileSize, layout));
        List<LayoutFile> layoutFiles = caseDb.addCarvedFiles(new CarvingResult(parent, carvedFiles));
        return layoutFiles.get(0);
    }

    /**
     * Adds a collection of carved files to the '$CarvedFiles' virtual directory
     * of a data source, volume or file system.
     *
     * @param A collection of CarvedFileContainer objects, one per carved file,
     *          all of which must have the same parent object id.
     *
     * @return A collection of LayoutFile object representing the carved files.
     *
     * @throws TskCoreException if there is a problem adding the files to the
     *                          case database.
     * @deprecated Use List<LayoutFile> addCarvedFiles(CarvingResult
     * carvingResult instead.
     */
    @Deprecated
    public synchronized List<LayoutFile> addCarvedFiles(List<org.sleuthkit.datamodel.CarvedFileContainer> filesToAdd) throws TskCoreException {
        if (null == caseDb) {
            throw new TskCoreException("File manager has been closed");
        }
        return caseDb.addCarvedFiles(filesToAdd);
    }
    
    /**
     * Adds a derived file to the case.
     *
     * @param fileName        The name of the file.
     * @param localPath       The local path of the file, relative to the case
     *                        folder and including the file name.
     * @param size            The size of the file in bytes.
     * @param ctime           The change time of the file.
     * @param crtime          The create time of the file
     * @param atime           The accessed time of the file.
     * @param mtime           The modified time of the file.
     * @param isFile          True if a file, false if a directory.
     * @param parentFile      The parent file from which the file was derived.
     * @param rederiveDetails The details needed to re-derive file (will be
     *                        specific to the derivation method), currently
     *                        unused.
     * @param toolName        The name of the derivation method or tool,
     *                        currently unused.
     * @param toolVersion     The version of the derivation method or tool,
     *                        currently unused.
     * @param otherDetails    Other details of the derivation method or tool,
     *                        currently unused.
     *
     * @return A DerivedFile object representing the derived file.
     *
     * @throws TskCoreException if there is a problem adding the file to the
     *                          case database.
     * 
     * @Deprecated Use the version with explicit EncodingType instead
     */
    @Deprecated
    public synchronized DerivedFile addDerivedFile(String fileName,
            String localPath,
            long size,
            long ctime, long crtime, long atime, long mtime,
            boolean isFile,
            AbstractFile parentFile,
            String rederiveDetails, String toolName, String toolVersion, String otherDetails) throws TskCoreException {
        return addDerivedFile(fileName, localPath, size, ctime, crtime, atime, mtime, isFile, parentFile, 
                rederiveDetails, toolName, toolVersion, otherDetails, TskData.EncodingType.NONE);
    }
    
    /**
     * Adds a file or directory of logical/local files data source to the case
     * database, recursively adding the contents of directories.
     *
     * @param trans              A case database transaction.
     * @param parentDirectory    The root virtual direcotry of the data source.
     * @param localFile          The local/logical file or directory.
     * @param addProgressUpdater notifier to receive progress notifications on
     *                           folders added, or null if not used
     *
     * @returns File object of file added or new virtualdirectory for the
     * directory.
     * @param progressUpdater    Called after each file/directory is added to
     *                           the case database.
     *
     * @return An AbstractFile representation of the local/logical file.
     *
     * @throws TskCoreException If there is a problem completing a database
     *                          operation.
     * 
     * @Deprecated Use the version with explicit EncodingType instead
     */
    @Deprecated
    private AbstractFile addLocalFile(CaseDbTransaction trans, VirtualDirectory parentDirectory, java.io.File localFile, FileAddProgressUpdater progressUpdater) throws TskCoreException {
        return addLocalFile(trans, parentDirectory, localFile, TskData.EncodingType.NONE, progressUpdater);
    }

}
