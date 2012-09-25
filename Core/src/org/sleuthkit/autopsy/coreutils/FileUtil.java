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
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

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
     * @param path  the path to the file or directory to delete
     * @return  true if the File at path is deleted, false otherwise
     */
    public static boolean deleteFileDir(File path) {
            boolean sucess = true;
            if(path.isFile()) { // If it's a file
                if(!path.delete()) {
                    sucess = false;
                    logger.log(Level.WARNING, "Failed to delete file {0}", path.getPath());
                }
            } else { // If it's a directory
                if(path.list().length==0) { // If the dir is empty
                    if(!path.delete()) {
                        sucess = false;
                        logger.log(Level.WARNING, "Failed to delete the empty directory at {0}", path.getPath());
                    }
                } else {
                    String files[] = path.list();
                    for(String s:files) {
                        File sub = new File(path, s);
                        sucess = deleteFileDir(sub);
                    }
                    if(path.list().length==0) { // Delete the newly-empty dir
                        if(!path.delete()) {
                            sucess = false;
                            logger.log(Level.WARNING, "Failed to delete the empty directory at {0}", path.getPath());
                        }
                    } else {
                        sucess = false;
                        logger.log(Level.WARNING, "Directory {0} did not recursivly delete sucessfully.", path.getPath());
                    }
                }
            }
            return sucess;
        }
}
