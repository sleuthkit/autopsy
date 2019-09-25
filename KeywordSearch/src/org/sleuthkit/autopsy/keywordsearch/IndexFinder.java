/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import org.apache.commons.lang.math.NumberUtils;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.appservices.AutopsyService;

/**
 * This class handles the task of finding and identifying KWS index folders.
 */
class IndexFinder {

    private static final Logger logger = Logger.getLogger(IndexFinder.class.getName());
    private static final String KWS_OUTPUT_FOLDER_NAME = "keywordsearch";
    private static final String KWS_DATA_FOLDER_NAME = "data";
    private static final String INDEX_FOLDER_NAME = "index";
    private static final String CURRENT_SOLR_VERSION = "4";
    private static final String CURRENT_SOLR_SCHEMA_VERSION = "2.1";

    static String getCurrentSolrVersion() {
        return CURRENT_SOLR_VERSION;
    }

    static String getCurrentSchemaVersion() {
        return CURRENT_SOLR_SCHEMA_VERSION;
    }

    static Index findLatestVersionIndexDir(List<Index> allIndexes) {
        for (Index index : allIndexes) {
            if (index.getSolrVersion().equals(CURRENT_SOLR_VERSION) && index.getSchemaVersion().equals(CURRENT_SOLR_SCHEMA_VERSION)) {
                return index;
            }
        }
        return null;
    }

    static Index createLatestVersionIndexDir(Case theCase) throws AutopsyService.AutopsyServiceException {
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema" + CURRENT_SOLR_SCHEMA_VERSION;
        // new index should be stored in "\ModuleOutput\keywordsearch\data\solrX_schemaY\index"
        File targetDirPath = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, indexFolderName, INDEX_FOLDER_NAME).toFile(); //NON-NLS
        if (!targetDirPath.mkdirs()) {
            throw new AutopsyService.AutopsyServiceException("Unable to create text index directory " + targetDirPath.getAbsolutePath());
        }
        return new Index(targetDirPath.getAbsolutePath(), CURRENT_SOLR_VERSION, CURRENT_SOLR_SCHEMA_VERSION, "", theCase.getName());
    }

    static Index identifyIndexToUse(List<Index> allIndexes) {
        /*
         * NOTE: All of the following paths are valid multi-user index paths:
         * (Solr 4, schema 1.8)
         * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema2.0\index
         */
        Index bestCandidateIndex = null;
        double solrVerFound = 0.0;
        double schemaVerFound = 0.0;
        for (Index index : allIndexes) {
            // higher Solr version takes priority because it may negate index upgrade
            if (NumberUtils.toDouble(index.getSolrVersion()) >= solrVerFound) {
                // if same solr version, pick the one with highest schema version
                if (NumberUtils.toDouble(index.getSchemaVersion()) >= schemaVerFound) {
                    bestCandidateIndex = index;
                    solrVerFound = NumberUtils.toDouble(index.getSolrVersion());
                    schemaVerFound = NumberUtils.toDouble(index.getSchemaVersion());
                }
            }
        }
        return bestCandidateIndex;
    }

    /**
     * Find existing Solr 4 Schema 1.8 index directory location for the case.
     * This is done via subdirectory search of all existing
     * "ModuleOutput/node_name/keywordsearch/data/" folders.
     *
     * @param theCase the case to get index dir for
     *
     * @return List of Index objects for each found index directory
     */
    static Index findOldIndexDir(Case theCase) {
        // first find all existing "/ModuleOutput/keywordsearch/data/" folders
        if (theCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            // multi user cases contain a subfolder for each node that participated in case ingest or review.
            // Any one (but only one!) of those subfolders may contain the actual index.
            /*
             * NOTE: the following path is an example of valid Solr 4 Schema 1.8
             * multi-user index path:
             * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
             */

            // get a list of all folder's contents
            List<File> contents = getAllContentsInFolder(theCase.getCaseDirectory());
            if (!contents.isEmpty()) {
                // decipher "ModuleOutput" directory name from module output path 
                // (e.g. X:\Case\ingest4\ModuleOutput\) because there is no other way to get it...
                String moduleOutDirName = new File(theCase.getModuleDirectory()).getName();

                // scan all topLevelOutputDir subfolders for presence of non-empty "/ModuleOutput/keywordsearch/data/" folder
                for (File item : contents) {
                    File path = Paths.get(item.getAbsolutePath(), moduleOutDirName, KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, INDEX_FOLDER_NAME).toFile(); //NON-NLS
                    // must be a non-empty index directory
                    if (isNonEmptyIndexFolder(path)) {
                        return new Index(path.toString(), "4", "1.8", theCase.getTextIndexName(), theCase.getName());
                    }
                }
            }
        } else {
            // single user case
            /*
             * NOTE: the following path is valid single user Solr 4 Schema 1.8
             * index path: X:\Case\ModuleOutput\keywordsearch\data\index
             */
            File path = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, INDEX_FOLDER_NAME).toFile(); //NON-NLS
            // must be a non-empty index directory
            if (isNonEmptyIndexFolder(path)) {
                return new Index(path.toString(), "4", "1.8", theCase.getTextIndexName(), theCase.getName());
            }
        }
        return null;
    }

    /**
     * Returns a list of all contents in the folder of interest.
     *
     * @param path Absolute targetDirPath of the folder of interest
     *
     * @return List of all contents in the folder of interest
     */
    private static List<File> getAllContentsInFolder(String path) {
        File directory = new File(path);
        File[] contents = directory.listFiles();
        if (contents == null) {
            // the directory file is not really a directory..
            return Collections.emptyList();
        } else if (contents.length == 0) {
            // Folder is empty
            return Collections.emptyList();
        } else {
            // Folder has contents
            return new ArrayList<>(Arrays.asList(contents));
        }
    }

    private static boolean isNonEmptyIndexFolder(File path) {
        if (path.exists() && path.isDirectory() && path.getName().equals(INDEX_FOLDER_NAME) && path.listFiles().length > 0) {
            return true;
        }
        return false;
    }
}
