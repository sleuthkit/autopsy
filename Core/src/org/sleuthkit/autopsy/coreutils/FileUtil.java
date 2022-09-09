/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.coreutils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.openide.filesystems.FileObject;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * File and dir utilities
 */
public class FileUtil {

    private static final Logger logger = Logger.getLogger(FileUtil.class.getName());
    private static final String TEMP_FILE_NAME = "Autopsy"; //NON-NLS
    private static final String TEMP_FILE_EXT = null; //NON-NLS

    /**
     * Recursively delete all of the files and sub-directories in a directory.
     * Use deleteFileDir() if you are not sure if the path is a file or
     * directory.
     *
     * @param dirPath Path of the directory to delete
     *
     * @return true if the dir was deleted with no errors. False otherwise
     *         (including if the passed in path was for a file).
     */
    public static boolean deleteDir(File dirPath) {
        if (dirPath.isDirectory() == false || dirPath.exists() == false) {
            logger.log(Level.WARNING, "deleteDir passed in a non-directory: {0}", dirPath.getPath()); //NON-NLS
            return false;
        }

        File[] files = dirPath.listFiles();
        boolean hadErrors = false;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    if (deleteDir(file) == false) {
                        // message was already logged
                        hadErrors = true;
                    }
                } else {
                    if (file.delete() == false) {
                        logger.log(Level.WARNING, "Failed to delete file {0}", file.getPath()); //NON-NLS
                        hadErrors = true;
                    }
                }
            }
        }
        if (dirPath.delete() == false) {
            logger.log(Level.WARNING, "Failed to delete the empty directory at {0}", dirPath.getPath()); //NON-NLS
            hadErrors = true;
        }

        return !hadErrors;
    }

    /**
     * Delete the file or dir at the given path. If the path is for a directory,
     * recursively delete its contents.
     *
     * @param path the path to the file or directory to delete
     *
     * @return true if the file or directory were deleted with no errors. False
     *         otherwise.
     */
    public static boolean deleteFileDir(File path) {
        boolean sucess = true;
        if (path.isFile()) { // If it's a file
            if (!path.delete()) {
                sucess = false;
                logger.log(Level.WARNING, "Failed to delete file {0}", path.getPath()); //NON-NLS
            }
        } else { // If it's a directory
            sucess = deleteDir(path);
        }
        return sucess;
    }

    /**
     * Copy a file to a new directory, potentially new file name, and overwrite
     * old one if requested
     *
     * @param source     source file path
     * @param destFolder destination folder path
     * @param newName    file name of the copied file, which can be different
     *                   from original
     * @param ext        file extension, e.g. ".java"
     * @param overwrite  if new file, already exists, overwrite it (delete it
     *                   first)
     *
     * @return path to the created file, or null if file was not created
     *
     * @throws IOException exception thrown if file copying failed
     */
    public static String copyFile(String source, String destFolder, String newName, String ext, boolean overwrite)
            throws IOException {

        final String destFileName = destFolder + File.separator + newName + ext;
        final File destFile = new File(destFileName);
        if (destFile.exists()) {
            if (overwrite) {
                destFile.delete();
            } else {
                return null;
            }
        }

        final FileObject sourceFileObj = org.openide.filesystems.FileUtil.createData(new File(source));
        final FileObject destFolderObj = org.openide.filesystems.FileUtil.createData(new File(destFolder));

        // org.openide.filesystems.FileUtil.copyFile requires an extension without the "." e.g. "java"
        FileObject created = org.openide.filesystems.FileUtil.copyFile(sourceFileObj, destFolderObj, newName, ext.substring(1));

        return created.getPath();

    }

    /**
     * Copy a folder into a new directory.
     *
     * @param source     path to the source folder
     * @param path       destination path of the new folder
     * @param folderName name of the new folder
     *
     * @return path to the new folder if created, null if it was not created
     *
     * @throws IOException exception thrown if file copying failed
     */
    public static String copyFolder(String source, String path, String folderName) throws IOException {
        String destFolder = path + File.separator + folderName;
        org.openide.filesystems.FileUtil.createFolder(new File(destFolder));

        final FileObject sourceFileObj = org.openide.filesystems.FileUtil.createData(new File(source));
        final FileObject destFolderObj = org.openide.filesystems.FileUtil.createData(new File(destFolder));

        FileObject created = org.openide.filesystems.FileUtil.copyFile(sourceFileObj, destFolderObj, sourceFileObj.getName(), sourceFileObj.getExt());

        return created.getPath();
    }

    /**
     * Escape special characters in a file name or a file name component
     *
     * @param fileName to escape
     *
     * @return escaped string
     */
    public static String escapeFileName(String fileName) {
        //for now escaping /:"*?<>| (not valid in file name, at least on Windows)
        //with underscores. We are only keeping \ as it could be part of the path.
        // Also trim empty space characters at the beginning and end of file name.
        return fileName.replaceAll("[\\p{Cntrl}/:\"*?<>|]+", "_").trim();
    }

    /**
     * Test if the current user has read and write access to the dirPath.
     *
     * @param dirPath The path to the directory to test for read and write
     *                access.
     *
     * @return True if we have both read and write access, false otherwise.
     */
    public static boolean hasReadWriteAccess(Path dirPath) {
        Path p = null;
        try {
            p = Files.createTempFile(dirPath, TEMP_FILE_NAME, TEMP_FILE_EXT);
            return (p.toFile().canRead() && p.toFile().canWrite());
        } catch (IOException ex) {
            return false;
        } finally {
            if (p != null) {
                try {
                    p.toFile().delete();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Prevents instantiation of this utility class.
     */
    private FileUtil() {
    }
}
