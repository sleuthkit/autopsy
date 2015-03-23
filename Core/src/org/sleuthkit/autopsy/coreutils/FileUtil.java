/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012 Basis Technology Corp.
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
import java.util.regex.Matcher;
import org.openide.filesystems.FileObject;

/**
 * File and dir utilities
 */
 public class FileUtil {

    private static final Logger logger = Logger.getLogger(FileUtil.class.getName());

    /**
     * Recursively delete a dir
     *
     * @param dirPath dir path of a dir to delete
     * @return true if the dir deleted, false otherwise
     */
    public static boolean deleteDir(File dirPath) {
        if (dirPath.exists()) {
            File[] files = dirPath.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    deleteDir(files[i]);
                } else {
                    files[i].delete();
                }
            }
        }
        return (dirPath.delete());

    }

    /**
     * Check if given path is a file or directory, then delete it through
     * recursion with path leading to a file as the base case.
     *
     * @param path the path to the file or directory to delete
     * @return true if the File at path is deleted, false otherwise
     */
    public static boolean deleteFileDir(File path) {
        boolean sucess = true;
        if (path.isFile()) { // If it's a file
            if (!path.delete()) {
                sucess = false;
                logger.log(Level.WARNING, "Failed to delete file {0}", path.getPath()); //NON-NLS
            }
        } else { // If it's a directory
            if (path.list().length == 0) { // If the dir is empty
                if (!path.delete()) {
                    sucess = false;
                    logger.log(Level.WARNING, "Failed to delete the empty directory at {0}", path.getPath()); //NON-NLS
                }
            } else {
                String files[] = path.list();
                for (String s : files) {
                    File sub = new File(path, s);
                    sucess = deleteFileDir(sub);
                }
                if (path.list().length == 0) { // Delete the newly-empty dir
                    if (!path.delete()) {
                        sucess = false;
                        logger.log(Level.WARNING, "Failed to delete the empty directory at {0}", path.getPath()); //NON-NLS
                    }
                } else {
                    sucess = false;
                    logger.log(Level.WARNING, "Directory {0} did not recursivly delete sucessfully.", path.getPath()); //NON-NLS
                }
            }
        }
        return sucess;
    }

    /**
     * Copy a file to a new directory, potentially new file name, and overwrite old one if requested
     * 
     * @param source source file path
     * @param destFolder destination folder path
     * @param newName file name of the copied file, which can be different from original
     * @param ext file extension, e.g. ".java"
     * @param overwrite if new file, already exists, overwrite it (delete it first)
     *
     * @return path to the created file, or null if file was not created
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
     * @param source path to the source folder
     * @param path destination path of the new folder
     * @param folderName name of the new folder
     * 
     * @return path to the new folder if created, null if it was not created
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
     * @param fileName to escape
     * @return escaped string
     */
    public static String escapeFileName(String fileName) {        
        //for now escaping / (not valid in file name, at least on Windows)
        //with underscores.  Windows/Java seem to ignore \\/ and \\\\/ escapings 
        return fileName.replaceAll("/", "_"); 
    }
}
