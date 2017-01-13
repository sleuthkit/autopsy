/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;

/**
 * This class handles the task of finding KWS index folders and upgrading old
 * indexes to the latest supported Solr version. 
 */
class IndexHandling {
    
    private UNCPathUtilities uncPathUtilities = new UNCPathUtilities();
    private static final String MODULE_OUTPUT = "ModuleOutput"; // ELTODO get "ModuleOutput" somehow...
    private static final String KWS_OUTPUT_FOLDER_NAME = "keywordsearch";
    private static final String KWS_DATA_FOLDER_NAME = "data";
    private static final String INDEX_FOLDER_NAME = "index";
    private static final String CURRENT_SOLR_VERSION = "6";
    private static final String CURRENT_SOLR_SCHEMA_VERSION = "2.0";
    private static final Pattern INDEX_FOLDER_NAME_PATTERN = Pattern.compile("^solr\\d{1,2}_schema_\\d{1,2}.\\d{1,2}$");    
    
    
    static String getCurrentSolrVersion() {
        return CURRENT_SOLR_VERSION;
    }

    static String getCurrentSchemaVersion() {
        return CURRENT_SOLR_SCHEMA_VERSION;
    }
    
    static String findLatestVersionIndexDir(List<String> allIndexes) {
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema_" + CURRENT_SOLR_SCHEMA_VERSION;
        for (String path : allIndexes) {
            if (path.contains(indexFolderName)) {
                return path;
            }
        }
        return "";
    }

    /**
     * Find index directory location for the case. This is done via subdirectory
     * search of all existing "ModuleOutput/node_name/keywordsearch/data/"
     * folders.
     *
     * @param theCase the case to get index dir for
     *
     * @return absolute path to index dir
     */
    static List<String> findAllIndexDirs(Case theCase) {
        ArrayList<String> candidateIndexDirs = new ArrayList<>();
        // first find all existing "/ModuleOutput/keywordsearch/data/" folders
        if (theCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            // multi user cases contain a subfolder for each node that participated in case ingest or review.
            // Any one (but only one!) of those subfolders may contain the actual index.
            /* NOTE: All of the following paths are valid multi-user index paths:
            X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
            X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
            X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
            X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
             */

            // create a list of all sub-directories
            List<File> contents = getAllContentsInFolder(theCase.getCaseDirectory());
            
            // ELTODO decipher "ModuleOutput" from path

            // scan all topLevelOutputDir subfolders for presence of non-empty "/ModuleOutput/keywordsearch/data/" folder
            for (File item : contents) {
                File path = Paths.get(item.getAbsolutePath(), MODULE_OUTPUT, KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME).toFile(); //NON-NLS
                // must be a non-empty directory
                if (path.exists() && path.isDirectory()) {
                    candidateIndexDirs.add(path.toString());
                }
            }
        } else {
            // single user case
            /* NOTE: All of the following paths are valid single user index paths:
            X:\Case\ModuleOutput\keywordsearch\data\index
            X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
            X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
            X:\Case\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
             */
            File path = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME).toFile(); //NON-NLS
            // must be a non-empty directory
            if (path.exists() && path.isDirectory()) {
                candidateIndexDirs.add(path.toString());
            }
        }
        
        // analyze possible index folders
        ArrayList<String> indexDirs = new ArrayList<>();
        for (String path : candidateIndexDirs) {
            List<String> validIndexPaths = containsValidIndexFolders(path);
            for (String validPath : validIndexPaths) {
                indexDirs.add(validPath);
                // ELTODO indexDirs.add(convertPathToUNC(validPath));
                // there can be multiple index folders (e.g. current version and "old" version) so keep looking
            }
        }
        return indexDirs;
    }
    
    String convertPathToUNC(String indexDir) {
        // ELTODO do we need to do this when searching for old index?
        if (uncPathUtilities == null) {
            return indexDir;
        }
        // if we can check for UNC paths, do so, otherwise just return the indexDir
        String result = uncPathUtilities.mappedDriveToUNC(indexDir);
        if (result == null) {
            uncPathUtilities.rescanDrives();
            result = uncPathUtilities.mappedDriveToUNC(indexDir);
        }
        if (result == null) {
            return indexDir;
        }
        return result;
    }

    /**
     * Returns a list of all contents in the folder of interest.
     *
     * @param path Absolute path of the folder of interest
     *
     * @return List of all contents in the folder of interest
     */
    private static List<File> getAllContentsInFolder(String path) {
        File directory = new File(path);
        File[] contents = directory.listFiles();
        if (contents == null) {
            // the directory file is not really a directory..
            return Collections.emptyList();
        }
        else if (contents.length == 0) {
            // Folder is empty
            return Collections.emptyList();
        }
        else {
            // Folder has contents
            return new ArrayList<>(Arrays.asList(contents));
        }
    }

    private static List<String> containsValidIndexFolders(String path) {
        /* NOTE: All of the following paths are valid index paths:
        X:\Case\ModuleOutput\keywordsearch\data\index
        X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
        X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
        X:\Case\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
        X:\Case\ingest4\ModuleOutput\keywordsearch\data\index
        X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
        X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
        X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
         */

        List<String> indexFolders = new ArrayList<>();
        List<File> contents = getAllContentsInFolder(path);
        // scan the folder for presence of non-empty "index" folder
        for (File item : contents) {
            // scan all subfolders for presence of non-empty "index" folder
            if (isNonEmptyIndexFolder(item)) {
                indexFolders.add(item.getAbsolutePath());
                // keep looking as there may be more index folders
                continue;
            }
            
            // check if the folder matches "solrX_schema_Y" patern
            if (matchesIndexFolderNameStandard(item.getName())) {
                File nextLevelIndexFolder = Paths.get(item.getAbsolutePath(), INDEX_FOLDER_NAME).toFile();
                // look for "index" sub-folder one level deeper
                if (isNonEmptyIndexFolder(nextLevelIndexFolder)) {
                    indexFolders.add(nextLevelIndexFolder.getAbsolutePath());
                    // keep looking as there may be more index folders
                }
            }
        }
        return indexFolders;
    }
    
    private static boolean isNonEmptyIndexFolder(File path) {
        if (path.exists() && path.isDirectory() && path.getName().equals(INDEX_FOLDER_NAME) && path.listFiles().length > 0) {
            return true;
        }
        return false;
    }
    
     /**
     * Checks whether a name matches index folder name standard
     *
     * @param inputString The string to check.
     *
     * @return True or false.
     */
    public static boolean matchesIndexFolderNameStandard(String inputString) {
        Matcher m = INDEX_FOLDER_NAME_PATTERN.matcher(inputString);
        return m.find();
    }    
    
}
