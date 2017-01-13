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
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.openide.modules.InstalledFileLocator;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * This class handles the task of finding KWS index folders and upgrading old
 * indexes to the latest supported Solr version.
 */
class IndexHandling {

    private UNCPathUtilities uncPathUtilities = new UNCPathUtilities();
    private static final String JAVA_PATH = PlatformUtil.getJavaPath();
    private static final String MODULE_OUTPUT = "ModuleOutput"; // ELTODO get "ModuleOutput" somehow...
    private static final String KWS_OUTPUT_FOLDER_NAME = "keywordsearch";
    private static final String KWS_DATA_FOLDER_NAME = "data";
    private static final String INDEX_FOLDER_NAME = "index";
    private static final String CURRENT_SOLR_VERSION = "6";
    private static final String CURRENT_SOLR_SCHEMA_VERSION = "2.0";
    private static final Pattern INDEX_FOLDER_NAME_PATTERN = Pattern.compile("^solr\\d{1,2}_schema_\\d{1,2}.\\d{1,2}$");
    // If SOLR_HOME environment variable doesn't exist, try these relative paths to find Solr config sets:
    private static final String RELATIVE_PATH_TO_CONFIG_SET = "autopsy/solr/solr/configsets/";
    private static final String RELATIVE_PATH_TO_CONFIG_SET_2 = "release/solr/solr/configsets/";

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

    static String createReferenceIndexCopy(Case theCase, String indexPath) throws AutopsyService.AutopsyServiceException {
        Logger logger = Logger.getLogger(IndexHandling.class.getName());    // ELTODO REMOVE
        logger.log(Level.SEVERE, "ELTODO copying index at path {0} ", indexPath); //NON-NLS
        String indexFolderName = "solr" + CURRENT_SOLR_VERSION + "_schema_" + CURRENT_SOLR_SCHEMA_VERSION;
        try {
            // new index should be stored in "\ModuleOutput\keywordsearch\data\solrX_schema_Y\index"
            File targetDirPath = Paths.get(theCase.getModuleDirectory(), KWS_OUTPUT_FOLDER_NAME, KWS_DATA_FOLDER_NAME, indexFolderName, INDEX_FOLDER_NAME).toFile(); //NON-NLS
            if (targetDirPath.exists()) {
                // targetDirPath should not exist, at least the target directory should be empty
                List<File> contents = getAllContentsInFolder(targetDirPath.getAbsolutePath());
                if (!contents.isEmpty()) {
                    // target directory is not empty
                    throw new AutopsyService.AutopsyServiceException("ELTODO");
                }
            }
            targetDirPath.mkdirs();
            FileUtils.copyDirectory(new File(indexPath), targetDirPath);
            return targetDirPath.getAbsolutePath();
        } catch (Exception ex) {
            throw new AutopsyService.AutopsyServiceException("ELTODO");
        }
    }

    static void createReferenceConfigSetCopy(String indexPath) throws AutopsyService.AutopsyServiceException {
        File pathToConfigSet = new File("");
        Logger logger = Logger.getLogger(IndexHandling.class.getName());    // ELTODO REMOVE
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
                    throw new AutopsyService.AutopsyServiceException("ELTODO");
                }
                // config set should be located in "C:/some/directory/AutopsyXYZ/autopsy/solr/solr/configsets/"
                pathToConfigSet = Paths.get(System.getProperty("user.dir"), RELATIVE_PATH_TO_CONFIG_SET).toFile();
                if (!pathToConfigSet.exists() || !pathToConfigSet.isDirectory()) {
                    logger.log(Level.SEVERE, "ELTODO path {0} doesn''t exist", pathToConfigSet.getAbsolutePath()); //NON-NLS
                    // try the "release/solr/solr/configsets/" folder instead
                    pathToConfigSet = Paths.get(System.getProperty("user.dir"), RELATIVE_PATH_TO_CONFIG_SET_2).toFile();
                    if (!pathToConfigSet.exists() || !pathToConfigSet.isDirectory()) {
                        logger.log(Level.SEVERE, "ELTODO path {0} doesn''t exist", pathToConfigSet.getAbsolutePath()); //NON-NLS
                        return;
                        // ELTODO This is NTH: throw new AutopsyService.AutopsyServiceException("ELTODO");
                    }
                }
            }
            File targetDirPath = new File(indexPath); //NON-NLS
            if (!targetDirPath.exists()) {
                targetDirPath.mkdirs();
            }
            // copy config set 
            FileUtils.copyDirectory(pathToConfigSet, new File(indexPath));
        } catch (Exception ex) {
            throw new AutopsyService.AutopsyServiceException("ELTODO");
        }
    }

    /**
     * Find index directory location for the case. This is done via subdirectory
     * search of all existing "ModuleOutput/node_name/keywordsearch/data/"
     * folders.
     *
     * @param theCase the case to get index dir for
     *
     * @return List of absolute paths to all found index directories
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

            // ELTODO decipher "ModuleOutput" from targetDirPath
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

    /**
     * Upgrades Solr index from version 4 to 5.
     *
     * @param solr4IndexPath Full path to Solr v4 index directory
     * @param tempResultsDir Path to directory where to store log output
     *
     * @return True is index upgraded successfully, false otherwise
     */
    static boolean upgradeSolrIndexVersion4to5(String solr4IndexPath, String tempResultsDir) {

        boolean success = true;
        String outputFileName = "output.txt";
        try {
            // find the index upgrade tool
            final File upgradeToolFolder = InstalledFileLocator.getDefault().locate("Solr4to5IndexUpgrade", IndexHandling.class.getPackage().getName(), false); //NON-NLS
            if (upgradeToolFolder == null) {
                return false;
            }

            // full path to index upgrade jar file
            File upgradeJarPath = Paths.get(upgradeToolFolder.getAbsolutePath(), "Solr4IndexUpgrade.jar").toFile();
            if (!upgradeJarPath.exists() || !upgradeJarPath.isFile()) {
                return false;
            }
            
            // create log output directory if it doesn't exist
            new File(tempResultsDir).mkdirs();

            final String outputFileFullPath = Paths.get(tempResultsDir, outputFileName).toString();
            final String errFileFullPath = Paths.get(tempResultsDir, outputFileName + ".err").toString(); //NON-NLS
            List<String> commandLine = new ArrayList<>();
            commandLine.add(JAVA_PATH);
            commandLine.add("-jar");
            commandLine.add(upgradeJarPath.getAbsolutePath());
            commandLine.add(solr4IndexPath);
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            processBuilder.redirectOutput(new File(outputFileFullPath));
            processBuilder.redirectError(new File(errFileFullPath));
            ExecUtil.execute(processBuilder);
        } catch (Exception ex) {
            success = false;
        }
        // alternatively can execute lucene upgrade command from the folder where lucene jars are located
        // java -cp ".;lucene-core-5.5.1.jar;lucene-backward-codecs-5.5.1.jar;lucene-codecs-5.5.1.jar;lucene-analyzers-common-5.5.1.jar" org.apache.lucene.index.IndexUpgrader \path\to\index
        return success;
    }

    /**
     * Upgrades Solr index from version 5 to 6.
     *
     * @param solr5IndexPath Full path to Solr v5 index directory
     * @param tempResultsDir Path to directory where to store log output
     *
     * @return True is index upgraded successfully, false otherwise
     */
    static boolean upgradeSolrIndexVersion5to6(String solr5IndexPath, String tempResultsDir) {

        boolean success = true;
        String outputFileName = "output.txt";
        try {
            // find the index upgrade tool
            final File upgradeToolFolder = InstalledFileLocator.getDefault().locate("Solr5to6IndexUpgrade", IndexHandling.class.getPackage().getName(), false); //NON-NLS
            if (upgradeToolFolder == null) {
                return false;
            }

            // full path to index upgrade jar file
            File upgradeJarPath = Paths.get(upgradeToolFolder.getAbsolutePath(), "Solr5IndexUpgrade.jar").toFile();
            if (!upgradeJarPath.exists() || !upgradeJarPath.isFile()) {
                return false;
            }

            // create log output directory if it doesn't exist
            new File(tempResultsDir).mkdirs();

            final String outputFileFullPath = Paths.get(tempResultsDir, outputFileName).toString();
            final String errFileFullPath = Paths.get(tempResultsDir, outputFileName + ".err").toString(); //NON-NLS
            List<String> commandLine = new ArrayList<>();
            commandLine.add(JAVA_PATH);
            commandLine.add("-jar");
            commandLine.add(upgradeJarPath.getAbsolutePath());
            commandLine.add(solr5IndexPath);
            ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
            processBuilder.redirectOutput(new File(outputFileFullPath));
            processBuilder.redirectError(new File(errFileFullPath));
            ExecUtil.execute(processBuilder);
        } catch (Exception ex) {
            success = false;
        }

        // alternatively can execute lucene upgrade command from the folder where lucene jars are located
        // java -cp ".;lucene-core-6.2.1.jar;lucene-backward-codecs-6.2.1.jar;lucene-codecs-6.2.1.jar;lucene-analyzers-common-6.2.1.jar" org.apache.lucene.index.IndexUpgrader \path\to\index
        return success;
    }
}
