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
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;
import org.sleuthkit.autopsy.framework.AutopsyService;
import org.sleuthkit.autopsy.framework.ProgressIndicator;

/**
 * This class handles the task of finding and identifying KWS index folders.
 */
class IndexFinder {

    private static final Logger logger = Logger.getLogger(IndexFinder.class.getName());
    private final UNCPathUtilities uncPathUtilities;
    private static final String KWS_OUTPUT_FOLDER_NAME = "keywordsearch";
    private static final String KWS_DATA_FOLDER_NAME = "data";
    private static final String INDEX_FOLDER_NAME = "index";
    private static final String CURRENT_SOLR_VERSION = "6";
    private static final String CURRENT_SOLR_SCHEMA_VERSION = "2.0";
    private static final Pattern INDEX_FOLDER_NAME_PATTERN = Pattern.compile("^solr(\\d{1,2})_schema_(\\d{1,2}\\.\\d{1,2})$");
    // If SOLR_HOME environment variable doesn't exist, try these relative paths to find Solr config sets:
    private static final String RELATIVE_PATH_TO_CONFIG_SET = "autopsy/solr/solr/configsets/";
    private static final String RELATIVE_PATH_TO_CONFIG_SET_2 = "release/solr/solr/configsets/";

    IndexFinder() {
        uncPathUtilities = new UNCPathUtilities();
    }

    static String getCurrentSolrVersion() {
        return CURRENT_SOLR_VERSION;
    }

    static String getCurrentSchemaVersion() {
        return CURRENT_SOLR_SCHEMA_VERSION;
    }

    static Index findLatestVersionIndexDir(List<Index> allIndexes) {
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema_" + CURRENT_SOLR_SCHEMA_VERSION;
        for (Index index : allIndexes) {
            String path = index.getIndexPath();
            if (path.contains(indexFolderName)) {
                return index;
            }
        }
        return null;
    }

    static Index createLatestVersionIndexDir(Case theCase) {
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema_" + CURRENT_SOLR_SCHEMA_VERSION;
        // new index should be stored in "\ModuleOutput\keywordsearch\data\solrX_schema_Y\index"
        File targetDirPath = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, indexFolderName, INDEX_FOLDER_NAME).toFile(); //NON-NLS
        targetDirPath.mkdirs();
        return new Index(targetDirPath.getAbsolutePath(), CURRENT_SOLR_VERSION, CURRENT_SOLR_SCHEMA_VERSION);
    }

    static Index identifyIndexToUpgrade(List<Index> allIndexes) {
        /*
         * NOTE: All of the following paths are valid multi-user index paths:
         * (Solr 4, schema 1.8)
         * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
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
     * Creates a copy of an existing Solr index as well as a reference copy of
     * Solr config set.
     *
     * @param indexToUpgrade        Index object to create a copy of
     * @param context               AutopsyService.CaseContext object
     * @param numCompletedWorkUnits Number of completed progress units so far
     *
     * @return The absolute path of the new Solr index directory or null if
     *         cancelled.
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    @NbBundle.Messages({
        "SolrSearch.copyIndex.msg=Copying existing text index",
        "SolrSearch.copyConfigSet.msg=Copying Solr config set",})
    String copyIndexAndConfigSet(Index indexToUpgrade, AutopsyService.CaseContext context, int startNumCompletedWorkUnits) throws AutopsyService.AutopsyServiceException {

        int numCompletedWorkUnits = startNumCompletedWorkUnits;
        ProgressIndicator progress = context.getProgressIndicator();

        if (context.cancelRequested()) {
            return null;
        }

        // Copy the "old" index into ModuleOutput/keywordsearch/data/solrX_schema_Y/index
        numCompletedWorkUnits++;
        progress.progress(Bundle.SolrSearch_copyIndex_msg(), numCompletedWorkUnits);
        String newIndexDirPath = copyExistingIndex(context.getCase(), indexToUpgrade);
        File newIndexDir = new File(newIndexDirPath);

        if (context.cancelRequested()) {
            return null;
        }

        // Make a “reference copy” of the configset and place it in ModuleOutput/keywordsearch/data/solrX_schema_Y/configset
        numCompletedWorkUnits++;
        progress.progress(Bundle.SolrSearch_copyConfigSet_msg(), numCompletedWorkUnits);
        createReferenceConfigSetCopy(newIndexDir.getParent());

        if (context.cancelRequested()) {
            return null;
        }

        return newIndexDirPath;
    }

    private static String copyExistingIndex(Case theCase, Index indexToUpgrade) throws AutopsyService.AutopsyServiceException {
        // folder name for the upgraded index should be latest Solr version BUT schema verion of the existing index
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema_" + indexToUpgrade.getSchemaVersion();
        try {
            // new index should be stored in "\ModuleOutput\keywordsearch\data\solrX_schema_Y\index"
            File targetDirPath = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, indexFolderName, INDEX_FOLDER_NAME).toFile(); //NON-NLS
            if (targetDirPath.exists()) {
                // targetDirPath should not exist, at least the target directory should be empty
                List<File> contents = getAllContentsInFolder(targetDirPath.getAbsolutePath());
                if (!contents.isEmpty()) {
                    // target directory is not empty
                    throw new AutopsyService.AutopsyServiceException("Directory to store the upgraded index must be empty " + targetDirPath.getAbsolutePath());
                }
            }
            targetDirPath.mkdirs();
            FileUtils.copyDirectory(new File(indexToUpgrade.getIndexPath()), targetDirPath);
            return targetDirPath.getAbsolutePath();
        } catch (AutopsyService.AutopsyServiceException | IOException ex) {
            throw new AutopsyService.AutopsyServiceException("Error occurred while creating a copy of keyword search index", ex);
        }
    }

    // ELTODO This functionality is NTH:
    private void createReferenceConfigSetCopy(String indexPath) {
        File pathToConfigSet = new File("");
        try {
            // See if there is SOLR_HOME environment variable first
            String solrHome = System.getenv("SOLR_HOME");
            if (solrHome != null && !solrHome.isEmpty()) {
                // ELTODO pathToConfigSet = 
                return; // ELTODO remove
            } else {
                // if there is no SOLR_HOME:
                // this will only work for Windows OS
                if (!PlatformUtil.isWindowsOS()) {
                    throw new AutopsyService.AutopsyServiceException("Creating a reference config set copy is currently a Windows-only feature");
                }
                // config set should be located in "C:/some/directory/AutopsyXYZ/autopsy/solr/solr/configsets/"
                pathToConfigSet = Paths.get(System.getProperty("user.dir"), RELATIVE_PATH_TO_CONFIG_SET).toFile();
                if (!pathToConfigSet.exists() || !pathToConfigSet.isDirectory()) {
                    // try the "release/solr/solr/configsets/" folder instead
                    pathToConfigSet = Paths.get(System.getProperty("user.dir"), RELATIVE_PATH_TO_CONFIG_SET_2).toFile();
                    if (!pathToConfigSet.exists() || !pathToConfigSet.isDirectory()) {
                        logger.log(Level.WARNING, "Unable to locate KWS config set in order to create a reference copy"); //NON-NLS
                        return;
                    }
                }
            }
            File targetDirPath = new File(indexPath); //NON-NLS
            if (!targetDirPath.exists()) {
                targetDirPath.mkdirs();
            }
            // copy config set 
            if (!pathToConfigSet.getAbsolutePath().isEmpty() && pathToConfigSet.exists()) {
                FileUtils.copyDirectory(pathToConfigSet, new File(indexPath));
            }
        } catch (AutopsyService.AutopsyServiceException | IOException ex) {
            // This feature is a NTH so don't re-throw 
        }
    }

    /**
     * Find index directory location(s) for the case. This is done via
     * subdirectory search of all existing
     * "ModuleOutput/node_name/keywordsearch/data/" folders.
     *
     * @param theCase the case to get index dir for
     *
     * @return List of Index objects for each found index directory
     */
    List<Index> findAllIndexDirs(Case theCase) {
        ArrayList<String> candidateIndexDirs = new ArrayList<>();
        // first find all existing "/ModuleOutput/keywordsearch/data/" folders
        if (theCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
            // multi user cases contain a subfolder for each node that participated in case ingest or review.
            // Any one (but only one!) of those subfolders may contain the actual index.
            /*
             * NOTE: All of the following paths are valid multi-user index
             * paths: X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
             * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
             * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
             * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
             */

            // get a list of all folder's contents
            List<File> contents = getAllContentsInFolder(theCase.getCaseDirectory());
            if (!contents.isEmpty()) {
                // decipher "ModuleOutput" directory name from module output path 
                // (e.g. X:\Case\ingest4\ModuleOutput\) because there is no other way to get it...
                String moduleOutDirName = new File(theCase.getModuleDirectory()).getName();

                // scan all topLevelOutputDir subfolders for presence of non-empty "/ModuleOutput/keywordsearch/data/" folder
                for (File item : contents) {
                    File path = Paths.get(item.getAbsolutePath(), moduleOutDirName, KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME).toFile(); //NON-NLS
                    // must be a non-empty directory
                    if (path.exists() && path.isDirectory()) {
                        candidateIndexDirs.add(path.toString());
                    }
                }
            }
        } else {
            // single user case
            /*
             * NOTE: All of the following paths are valid single user index
             * paths: X:\Case\ModuleOutput\keywordsearch\data\index
             * X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
             * X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
             * X:\Case\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
             */
            File path = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME).toFile(); //NON-NLS
            // must be a non-empty directory
            if (path.exists() && path.isDirectory()) {
                candidateIndexDirs.add(path.toString());
            }
        }

        // analyze possible index folders
        ArrayList<Index> indexes = new ArrayList<>();
        for (String path : candidateIndexDirs) {
            List<String> validIndexPaths = containsValidIndexFolders(path);
            for (String validPath : validIndexPaths) {
                String solrVersion = getSolrVersionFromIndexPath(validPath);
                String schemaVersion = getSchemaVersionFromIndexPath(validPath);
                if (!validPath.isEmpty() && !solrVersion.isEmpty() && !schemaVersion.isEmpty()) {
                    indexes.add(new Index(convertPathToUNC(validPath), solrVersion, schemaVersion));
                    // there can be multiple index folders (e.g. current version and "old" version) so keep looking
                }
            }
        }
        return indexes;
    }

    String getSolrVersionFromIndexPath(String path) {
        /*
         * NOTE: All of the following paths are valid multi-user index paths:
         * (Solr 4, schema 1.8)
         * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
         */
        File file = new File(path);
        // sanity check - must be "index" folder
        if (!file.getName().equals(INDEX_FOLDER_NAME)) {
            // invalid index path
            return "";
        }
        String parentFolderName = file.getParentFile().getName();
        if (parentFolderName.equals(KWS_DATA_FOLDER_NAME)) {
            // this is a Solr4 path, e.g. X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
            return "4";
        }

        // extract Solr version if name matches "solrX_schema_Y" format
        return getSolrVersionFromIndexFolderName(parentFolderName);
    }

    String getSchemaVersionFromIndexPath(String path) {
        /*
         * NOTE: All of the following paths are valid multi-user index paths:
         * (Solr 4, schema 1.8)
         * X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
         */
        File file = new File(path);
        // sanity check - must be "index" folder
        if (!file.getName().equals(INDEX_FOLDER_NAME)) {
            // invalid index path
            return "";
        }
        String parentFolderName = file.getParentFile().getName();
        if (parentFolderName.equals(KWS_DATA_FOLDER_NAME)) {
            // this is a Solr 4 schema 1.8 path, e.g. X:\Case\ingest1\ModuleOutput\keywordsearch\data\index
            return "1.8";
        }

        // extract schema version if name matches "solrX_schema_Y" format
        return getSchemaVersionFromIndexFolderName(parentFolderName);
    }

    String convertPathToUNC(String indexDir) {
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

    private static List<String> containsValidIndexFolders(String path) {
        /*
         * NOTE: All of the following paths are valid index paths:
         * X:\Case\ModuleOutput\keywordsearch\data\index
         * X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
         * X:\Case\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
         * X:\Case\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_2.0\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr6_schema_1.8\index
         * X:\Case\ingest4\ModuleOutput\keywordsearch\data\solr7_schema_2.0\index
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
    private static boolean matchesIndexFolderNameStandard(String inputString) {
        Matcher m = INDEX_FOLDER_NAME_PATTERN.matcher(inputString);
        return m.find();
    }

    /**
     * Gets Solr version number if index folder name matches the standard
     *
     * @param inputString The string to check.
     *
     * @return Solr version, empty string on error
     */
    static String getSolrVersionFromIndexFolderName(String inputString) {
        Matcher m = INDEX_FOLDER_NAME_PATTERN.matcher(inputString);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }

    /**
     * Gets Solr schema version number if index folder name matches the standard
     *
     * @param inputString The string to check.
     *
     * @return Solr schema version, empty string on error
     */
    static String getSchemaVersionFromIndexFolderName(String inputString) {
        Matcher m = INDEX_FOLDER_NAME_PATTERN.matcher(inputString);
        if (m.find()) {
            return m.group(2);
        }
        return "";
    }
}
