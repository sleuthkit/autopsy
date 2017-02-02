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
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.framework.AutopsyService;
import org.sleuthkit.autopsy.framework.ProgressIndicator;

/**
 * This class handles the task of upgrading old indexes to the latest supported
 * Solr version.
 */
class IndexUpgrader {

    private static final Logger logger = Logger.getLogger(IndexFinder.class.getName());
    private final String JAVA_PATH;

    IndexUpgrader() {
        JAVA_PATH = PlatformUtil.getJavaPath();
    }

    /**
     * Perform Solr text index upgrade to the latest supported version of Solr.
     *
     * @param newIndexDir           Full path to directory of Solr index to be
     *                              upgraded
     * @param indexToUpgrade        Index object of the existing Solr index
     * @param context               AutopsyService.CaseContext object
     * @param numCompletedWorkUnits Number of completed progress units so far
     *
     * @return Index object of the upgraded index, null if cancelled.
     *
     * @throws
     * org.sleuthkit.autopsy.framework.AutopsyService.AutopsyServiceException
     */
    @NbBundle.Messages({
        "SolrSearch.upgrade4to5.msg=Upgrading existing text index from Solr 4 to Solr 5",
        "SolrSearch.upgrade5to6.msg=Upgrading existing text index from Solr 5 to Solr 6",
        "SolrSearch.upgradeFailed.msg=Upgrade of existing Solr text index failed, deleting temporary directories",})
    Index performIndexUpgrade(String newIndexDir, Index indexToUpgrade, AutopsyService.CaseContext context, int startingNumCompletedWorkUnits) throws AutopsyService.AutopsyServiceException {

        int numCompletedWorkUnits = startingNumCompletedWorkUnits;
        ProgressIndicator progress = context.getProgressIndicator();

        // Run the upgrade tools on the contents (core) in ModuleOutput/keywordsearch/data/solrX_schema_Y/index
        String tempResultsDir = context.getCase().getTempDirectory();
        File tmpDir = Paths.get(tempResultsDir, "IndexUpgrade").toFile(); //NON-NLS
        tmpDir.mkdirs();

        Index upgradedIndex;
        double currentSolrVersion = NumberUtils.toDouble(indexToUpgrade.getSolrVersion());
        try {

            if (context.cancelRequested()) {
                return null;
            }

            // create process terminator that will monitor the cancellation flag
            UserCancelledProcessTerminator terminatior = new UserCancelledProcessTerminator(context);

            // upgrade from Solr 4 to 5
            numCompletedWorkUnits++;
            progress.progress(Bundle.SolrSearch_upgrade4to5_msg(), numCompletedWorkUnits);
            currentSolrVersion = upgradeSolrIndexVersion4to5(currentSolrVersion, newIndexDir, tempResultsDir, terminatior);
            if (Thread.currentThread().isInterrupted() || context.cancelRequested()) {
                return null;
            }

            // upgrade from Solr 5 to 6
            numCompletedWorkUnits++;
            progress.progress(Bundle.SolrSearch_upgrade5to6_msg(), numCompletedWorkUnits);
            currentSolrVersion = upgradeSolrIndexVersion5to6(currentSolrVersion, newIndexDir, tempResultsDir, terminatior);
            if (Thread.currentThread().isInterrupted() || context.cancelRequested()) {
                return null;
            }

            // create upgraded index object
            upgradedIndex = new Index(newIndexDir, Double.toString(currentSolrVersion), indexToUpgrade.getSchemaVersion());
            upgradedIndex.setNewIndex(true);
            return upgradedIndex;

        } catch (Exception ex) {
            // catch-all firewall for exceptions thrown by Solr upgrade tools
            // upgrade did not complete, delete the new index directories
            progress.progress(Bundle.SolrSearch_upgradeFailed_msg(), numCompletedWorkUnits);
            File newindexVersionDir = new File(newIndexDir).getParentFile();
            try {
                FileUtils.deleteDirectory(newindexVersionDir);
            } catch (IOException exx) {
                logger.log(Level.SEVERE, String.format("Failed to delete %s when upgrade failed", newindexVersionDir), exx);
            }
            throw new AutopsyService.AutopsyServiceException("Exception while running Solr index upgrade in " + newIndexDir, ex); //NON-NLS
        }
    }

    /**
     * Upgrades Solr index from version 4 to 5.
     *
     * @param currentIndexVersion Current Solr index version
     * @param solr4IndexPath      Full path to Solr v4 index directory
     * @param tempResultsDir      Path to directory where to store log output
     * @param terminator          Implementation of ExecUtil.ProcessTerminator
     *                            to terminate upgrade process
     *
     * @return The new Solr index version.
     */
    private double upgradeSolrIndexVersion4to5(double currentIndexVersion, String solr4IndexPath, String tempResultsDir, UserCancelledProcessTerminator terminator) throws AutopsyService.AutopsyServiceException {

        if (currentIndexVersion != 4.0) {
            return currentIndexVersion;
        }
        String outputFileName = "output.txt";
        logger.log(Level.INFO, "Upgrading KWS index {0} from Solr 4 to Solr 5", solr4IndexPath); //NON-NLS

        // find the index upgrade tool
        final File upgradeToolFolder = InstalledFileLocator.getDefault().locate("Solr4to5IndexUpgrade", IndexFinder.class.getPackage().getName(), false); //NON-NLS
        if (upgradeToolFolder == null) {
            throw new AutopsyService.AutopsyServiceException("Unable to locate Sorl 4 to Solr 5 upgrade tool");
        }

        // full path to index upgrade jar file
        File upgradeJarPath = Paths.get(upgradeToolFolder.getAbsolutePath(), "Solr4IndexUpgrade.jar").toFile();
        if (!upgradeJarPath.exists() || !upgradeJarPath.isFile()) {
            throw new AutopsyService.AutopsyServiceException("Unable to locate Solr 4 to Solr 5 upgrade tool's JAR file");
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
        try {
            ExecUtil.execute(processBuilder, terminator);
        } catch (SecurityException | IOException ex) {
            throw new AutopsyService.AutopsyServiceException("Error executing Solr 4 to Solr 5 upgrade tool");
        }

        // alternatively can execute lucene upgrade command from the folder where lucene jars are located
        // java -cp ".;lucene-core-5.5.1.jar;lucene-backward-codecs-5.5.1.jar;lucene-codecs-5.5.1.jar;lucene-analyzers-common-5.5.1.jar" org.apache.lucene.index.IndexUpgrader \path\to\index
        return 5.0;
    }

    /**
     * Upgrades Solr index from version 5 to 6.
     *
     * @param currentIndexVersion Current Solr index version
     * @param solr5IndexPath      Full path to Solr v5 index directory
     * @param tempResultsDir      Path to directory where to store log output
     * @param terminatior         Implementation of ExecUtil.ProcessTerminator
     *                            to terminate upgrade process
     *
     * @return The new Solr index version.
     */
    private double upgradeSolrIndexVersion5to6(double currentIndexVersion, String solr5IndexPath, String tempResultsDir, UserCancelledProcessTerminator terminatior) throws AutopsyService.AutopsyServiceException, SecurityException, IOException {
        if (currentIndexVersion != 5.0) {
            return currentIndexVersion;
        }
        String outputFileName = "output.txt";
        logger.log(Level.INFO, "Upgrading KWS index {0} from Sorl 5 to Solr 6", solr5IndexPath); //NON-NLS

        // find the index upgrade tool
        final File upgradeToolFolder = InstalledFileLocator.getDefault().locate("Solr5to6IndexUpgrade", IndexFinder.class.getPackage().getName(), false); //NON-NLS
        if (upgradeToolFolder == null) {
            throw new AutopsyService.AutopsyServiceException("Unable to locate Sorl 5 to Solr 6 upgrade tool");
        }

        // full path to index upgrade jar file
        File upgradeJarPath = Paths.get(upgradeToolFolder.getAbsolutePath(), "Solr5IndexUpgrade.jar").toFile();
        if (!upgradeJarPath.exists() || !upgradeJarPath.isFile()) {
            throw new AutopsyService.AutopsyServiceException("Unable to locate Sorl 5 to Solr 6 upgrade tool's JAR file");
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
        ExecUtil.execute(processBuilder, terminatior);

        // alternatively can execute lucene upgrade command from the folder where lucene jars are located
        // java -cp ".;lucene-core-6.2.1.jar;lucene-backward-codecs-6.2.1.jar;lucene-codecs-6.2.1.jar;lucene-analyzers-common-6.2.1.jar" org.apache.lucene.index.IndexUpgrader \path\to\index
        return 6.0;
    }

    /**
     * Process terminator that can be used to kill Solr index upgrade processes
     * if a user has requested to cancel the upgrade.
     */
    private class UserCancelledProcessTerminator implements ExecUtil.ProcessTerminator {

        AutopsyService.CaseContext context = null;

        UserCancelledProcessTerminator(AutopsyService.CaseContext context) {
            this.context = context;
        }

        @Override
        public boolean shouldTerminateProcess() {
            if (context.cancelRequested()) {
                return true;
            }
            return false;
        }
    }
}
