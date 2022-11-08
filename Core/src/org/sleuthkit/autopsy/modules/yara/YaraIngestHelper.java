/*
 * Autopsy Forensic Browser
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.yara;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSet;
import org.sleuthkit.autopsy.modules.yara.rules.RuleSetManager;
import org.sleuthkit.autopsy.yara.YaraJNIWrapper;
import org.sleuthkit.autopsy.yara.YaraWrapperException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_RULE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Methods for scanning files for yara rule matches.
 */
final class YaraIngestHelper {
    
    private static final String YARA_DIR = "yara";
    private static final String YARA_C_EXE = "yarac64.exe";
    private static final String MODULE_NAME = YaraIngestModuleFactory.getModuleName();

    private YaraIngestHelper() {
    }

    /**
     * Uses the yarac tool to compile the rules in the given rule sets.
     *
     * @param ruleSetNames List of names of the selected rule sets.
     * @param tempDir      Path of the directory to put the compiled rule files.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    static void compileRules(List<String> ruleSetNames, Path outputDir) throws IngestModuleException {
        if (ruleSetNames == null || ruleSetNames.isEmpty()) {
            throw new IngestModule.IngestModuleException(Bundle.YaraIngestModule_no_ruleSets());
        }

        // Find javac
        File exeFile = InstalledFileLocator.getDefault().locate(
                Paths.get(YARA_DIR, YARA_C_EXE).toString(),
                YaraIngestModule.class.getPackage().getName(), false);

        if (exeFile == null) {
            throw new IngestModuleException(Bundle.YaraIngestModule_yarac_not_found());
        }

        for (RuleSet set : getRuleSetsForNames(ruleSetNames)) {
            compileRuleSet(set, outputDir, exeFile);
        }
    }

    /**
     * Scan the given AbstractFile for yara rule matches from the rule sets in
     * the given directory creating a blackboard artifact for each matching
     * rule.
     *
     * The baseDirectory should contain a series of directories one for each
     * rule set.
     *
     * @param file                 The file to scan.
     * @param baseRuleSetDirectory Base directory for the compiled rule sets.
     *
     * @throws TskCoreException
     */
    static List<BlackboardArtifact> scanFileForMatches(AbstractFile file, File baseRuleSetDirectory, byte[] fileData, int fileDataSize, int timeout) throws TskCoreException, YaraWrapperException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();

        File[] ruleSetDirectories = baseRuleSetDirectory.listFiles();
        for (File ruleSetDirectory : ruleSetDirectories) {

            List<String> ruleMatches = YaraIngestHelper.scanFileForMatches(fileData, fileDataSize, ruleSetDirectory, timeout);
            if (!ruleMatches.isEmpty()) {
                artifacts.addAll(YaraIngestHelper.createArtifact(file, ruleSetDirectory.getName(), ruleMatches));
            }
        }

        return artifacts;
    }

    /**
     * Scan the given AbstractFile for yara rule matches from the rule sets in
     * the given directory creating a blackboard artifact for each matching
     * rule.
     *
     * @param file                 The Abstract File being processed.
     * @param baseRuleSetDirectory Base directory of the compiled rule sets.
     * @param localFile            Local copy of file.
     * @param timeout              Yara file scan timeout in seconds.
     *
     * @return
     *
     * @throws TskCoreException
     * @throws YaraWrapperException
     */
    static List<BlackboardArtifact> scanFileForMatches(AbstractFile file, File baseRuleSetDirectory, File localFile, int timeout) throws TskCoreException, YaraWrapperException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();

        File[] ruleSetDirectories = baseRuleSetDirectory.listFiles();
        for (File ruleSetDirectory : ruleSetDirectories) {
            List<String> ruleMatches = YaraIngestHelper.scanFileForMatch(localFile, ruleSetDirectory, timeout);
            if (!ruleMatches.isEmpty()) {
                artifacts.addAll(YaraIngestHelper.createArtifact(file, ruleSetDirectory.getName(), ruleMatches));
            }
        }

        return artifacts;
    }

    /**
     * Scan the given file byte array for rule matches using the YaraJNIWrapper
     * API.
     *
     * @param fileBytes        An array of the file data.
     * @param ruleSetDirectory Base directory of the compiled rule sets.
     *
     * @return List of rules that match from the given file from the given rule
     *         set. Empty list is returned if no matches where found.
     *
     * @throws TskCoreException
     */
    private static List<String> scanFileForMatches(byte[] fileBytes, int fileSize, File ruleSetDirectory, int timeout) throws YaraWrapperException {
        List<String> matchingRules = new ArrayList<>();

        File[] ruleSetCompiledFileList = ruleSetDirectory.listFiles();

        for (File ruleFile : ruleSetCompiledFileList) {
            matchingRules.addAll(YaraJNIWrapper.findRuleMatch(ruleFile.getAbsolutePath(), fileBytes, fileSize, timeout));
        }

        return matchingRules;
    }

    /**
     * Scan the given file for rules that match from the given rule set
     * directory.
     *
     * @param scanFile         Locally stored file to scan.
     * @param ruleSetDirectory Base directory of the compiled rule sets.
     * @param timeout          YARA Scanner timeout value.
     *
     * @return List of matching rules, if none were found the list will be
     *         empty.
     *
     * @throws YaraWrapperException
     */
    private static List<String> scanFileForMatch(File scanFile, File ruleSetDirectory, int timeout) throws YaraWrapperException {
        List<String> matchingRules = new ArrayList<>();

        File[] ruleSetCompiledFileList = ruleSetDirectory.listFiles();

        for (File ruleFile : ruleSetCompiledFileList) {
            matchingRules.addAll(YaraJNIWrapper.findRuleMatchFile(ruleFile.getAbsolutePath(), scanFile.getAbsolutePath(), timeout));
        }

        return matchingRules;
    }

    /**
     * Create a list of Blackboard Artifacts, one for each matching rule.
     *
     * @param abstractFile  File to add artifact to.
     * @param ruleSetName   Name rule set with matching rule.
     * @param matchingRules Matching rule.
     *
     * @return List of artifacts or empty list if none were found.
     *
     * @throws TskCoreException
     */
    private static List<BlackboardArtifact> createArtifact(AbstractFile abstractFile, String ruleSetName, List<String> matchingRules) throws TskCoreException {
        List<BlackboardArtifact> artifacts = new ArrayList<>();
        for (String rule : matchingRules) {

            List<BlackboardAttribute> attributes = new ArrayList<>();

            attributes.add(new BlackboardAttribute(TSK_SET_NAME, MODULE_NAME, ruleSetName));
            attributes.add(new BlackboardAttribute(TSK_RULE, MODULE_NAME, rule));

            BlackboardArtifact artifact = abstractFile.newAnalysisResult(BlackboardArtifact.Type.TSK_YARA_HIT, Score.SCORE_NOTABLE, null, ruleSetName, rule, attributes)
                    .getAnalysisResult();

            artifacts.add(artifact);
        }
        return artifacts;
    }

    @NbBundle.Messages({
        "YaraIngestModule_yarac_not_found=Unable to compile YARA rules files. Unable to find executable at.",
        "YaraIngestModule_no_ruleSets=Unable to run YARA ingest, list of YARA rule sets was empty."
    })

    /**
     * Compiles the rule files in the given rule set.
     *
     * The compiled rule files are created in outputDir\RuleSetName.
     *
     * @param set       RuleSet for which to compile files.
     * @param outputDir Output directory for the compiled rule files.
     * @param yarac     yarac executeable file.
     *
     * @throws org.sleuthkit.autopsy.ingest.IngestModule.IngestModuleException
     */
    static private void compileRuleSet(RuleSet set, Path outputDir, File yarac) throws IngestModuleException {
        File tempFolder = Paths.get(outputDir.toString(), set.getName()).toFile();
        if (!tempFolder.exists()) {
            tempFolder.mkdir();
        }

        List<File> fileList = set.getRuleFiles();
        for (File file : fileList) {
            List<String> commandList = new ArrayList<>();
            commandList.add(String.format("\"%s\"", yarac.toString()));
            commandList.add(String.format("\"%s\"", file.toString()));
            commandList.add(String.format("\"%s\"", Paths.get(tempFolder.getAbsolutePath(), "compiled_" + file.getName())));

            ProcessBuilder builder = new ProcessBuilder(commandList);
            try {
                int result = ExecUtil.execute(builder);
                if (result != 0) {
                    throw new IngestModuleException(String.format("Failed to compile Yara rules file %s. Compile error %d", file.toString(), result));
                }
            } catch (SecurityException | IOException ex) {
                throw new IngestModuleException(String.format("Failed to compile Yara rules file, %s", file.toString()), ex);
            }

        }
    }

    /**
     * Returns a list of RuleSet objects for the given list of RuleSet names.
     *
     * @param names List of RuleSet names.
     *
     * @return List of RuleSet or empty list if none of the names matched
     *         existing rules.
     */
    private static List<RuleSet> getRuleSetsForNames(List<String> names) {
        List<RuleSet> ruleSetList = new ArrayList<>();

        RuleSetManager manager = RuleSetManager.getInstance();
        for (RuleSet set : manager.getRuleSetList()) {
            if (names.contains(set.getName())) {
                ruleSetList.add(set);
            }
        }

        return ruleSetList;
    }
}
