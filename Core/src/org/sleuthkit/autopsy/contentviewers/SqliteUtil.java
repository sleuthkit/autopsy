/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Sqlite utility class. Find and copy metafiles, write sqlite abstract files to
 * temp directory, and generate unique temp directory paths.
 */
final class SqliteUtil {

    private SqliteUtil() {

    }

    /**
     * Overloaded implementation of
     * {@link #findAndCopySQLiteMetaFile(AbstractFile, String) findAndCopySQLiteMetaFile}
     * , automatically tries to copy -wal and -shm files without needing to know
     * their existence.
     *
     * @param sqliteFile file which has -wal and -shm meta files
     *
     * @throws NoCurrentCaseException Case has been closed.
     * @throws TskCoreException       fileManager cannot find AbstractFile
     *                                files.
     * @throws IOException            Issue during writing to file.
     */
    public static void findAndCopySQLiteMetaFile(AbstractFile sqliteFile)
            throws NoCurrentCaseException, TskCoreException, IOException {

        findAndCopySQLiteMetaFile(sqliteFile, sqliteFile.getName() + "-wal");
        findAndCopySQLiteMetaFile(sqliteFile, sqliteFile.getName() + "-shm");
    }

    /**
     * Searches for a meta file associated with the give SQLite database. If
     * found, it copies this file into the temp directory of the current case.
     *
     * @param sqliteFile   file being processed
     * @param metaFileName name of meta file to look for
     *
     * @throws NoCurrentCaseException Case has been closed.
     * @throws TskCoreException       fileManager cannot find AbstractFile
     *                                files.
     * @throws IOException            Issue during writing to file.
     */
    public static void findAndCopySQLiteMetaFile(AbstractFile sqliteFile,
            String metaFileName) throws NoCurrentCaseException, TskCoreException, IOException {

        Case openCase = Case.getCurrentCaseThrows();
        SleuthkitCase sleuthkitCase = openCase.getSleuthkitCase();
        Services services = new Services(sleuthkitCase);
        FileManager fileManager = services.getFileManager();

        List<AbstractFile> metaFiles = fileManager.findFiles(
                sqliteFile.getDataSource(), metaFileName,
                sqliteFile.getParent().getName());

        if (metaFiles != null) {
            for (AbstractFile metaFile : metaFiles) {
                writeAbstractFileToLocalDisk(metaFile);
            }
        }
    }

    /**
     * Copies the file contents into a unique path in the current case temp
     * directory.
     *
     * @param file AbstractFile from the data source
     *
     * @return The path of the file on disk
     *
     * @throws IOException            Exception writing file contents
     * @throws NoCurrentCaseException Current case closed during file copying
     */
    public static String writeAbstractFileToLocalDisk(AbstractFile file)
            throws IOException, NoCurrentCaseException {

        String localDiskPath = getUniqueTempDirectoryPath(file);
        File localDatabaseFile = new File(localDiskPath);
        if (!localDatabaseFile.exists()) {
            ContentUtils.writeToFile(file, localDatabaseFile);
        }
        return localDiskPath;
    }

    /**
     * Generates a unique local disk path that resides in the temp directory of
     * the current case.
     *
     * @param file The database abstract file
     *
     * @return Unique local disk path living in the temp directory of the case
     *
     * @throws org.sleuthkit.autopsy.casemodule.NoCurrentCaseException
     */
    public static String getUniqueTempDirectoryPath(AbstractFile file) throws NoCurrentCaseException {
        return Case.getCurrentCaseThrows().getTempDirectory()
                + File.separator + file.getId() + file.getName();
    }
}
