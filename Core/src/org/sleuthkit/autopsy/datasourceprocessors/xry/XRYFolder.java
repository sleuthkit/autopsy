/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors.xry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Extracts XRY files and (optionally) non-XRY files from a XRY (Report) folder.
 */
final class XRYFolder {

    //Depth that will contain XRY files. All XRY files will be immediate
    //children of their parent folder.
    private static final int XRY_FILES_DEPTH = 1;

    //Raw path to the XRY folder.
    private final Path xryFolderPath;

    public XRYFolder(Path folder) {
        xryFolderPath = folder;
    }

    /**
     * Finds all paths in the XRY report folder which are not XRY files. Only
     * the first directory level is searched. As a result, some paths may point
     * to directories.
     *
     * @return A non-null collection of paths
     * @throws IOException If an I/O error occurs.
     */
    public List<Path> getNonXRYFiles() throws IOException {
        try (Stream<Path> allFiles = Files.walk(xryFolderPath, XRY_FILES_DEPTH)) {
            List<Path> otherFiles = new ArrayList<>();
            Iterator<Path> allFilesIterator = allFiles.iterator();
            while (allFilesIterator.hasNext()) {
                Path currentPath = allFilesIterator.next();
                if (!currentPath.equals(xryFolderPath)
                        && !XRYFileReader.isXRYFile(currentPath)) {
                    otherFiles.add(currentPath);
                }
            }
            return otherFiles;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Creates XRYFileReader instances for all XRY files found in the top level
     * of the folder.
     *
     * @return A non-null collection of file readers.
     * @throws IOException If an I/O error occurs.
     */
    public List<XRYFileReader> getXRYFileReaders() throws IOException {
        try (Stream<Path> allFiles = Files.walk(xryFolderPath, XRY_FILES_DEPTH)) {
            List<XRYFileReader> fileReaders = new ArrayList<>();

            Iterator<Path> allFilesIterator = allFiles.iterator();
            while (allFilesIterator.hasNext()) {
                Path currentFile = allFilesIterator.next();
                if (XRYFileReader.isXRYFile(currentFile)) {
                    fileReaders.add(new XRYFileReader(currentFile));
                }
            }
            return fileReaders;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    /**
     * Searches for XRY files at the top level of a given folder. If at least
     * one file matches, the entire directory is assumed to be an XRY report.
     *
     * This function will not follow any symbolic links, the directory is tested
     * as is.
     *
     * @param folder Path to test. Assumes that caller has read access to the
     * folder and all of the top level files.
     * @return Indicates whether the Path is an XRY report.
     *
     * @throws IOException Error occurred during File I/O.
     * @throws SecurityException If the security manager denies access to any of
     * the files.
     */
    public static boolean isXRYFolder(Path folder) throws IOException {
        BasicFileAttributes attr = Files.readAttributes(folder,
                BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

        if (!attr.isDirectory()) {
            return false;
        }

        //Files.walk by default will not follow symbolic links.
        try (Stream<Path> allFiles = Files.walk(folder, XRY_FILES_DEPTH)) {
            Iterator<Path> allFilesIterator = allFiles.iterator();
            while (allFilesIterator.hasNext()) {
                Path currentFile = allFilesIterator.next();
                if (XRYFileReader.isXRYFile(currentFile)) {
                    return true;
                }
            }

            return false;
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
