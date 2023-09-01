/*
 * Autopsy Forensic Browser
 *
 * Copyright 2023 Basis Technology Corp.
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
package org.sleuthkit.autopsy.apiupdate;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.ParseException;
import org.sleuthkit.autopsy.apiupdate.APIDiff.ComparisonRecord;
import org.sleuthkit.autopsy.apiupdate.CLIProcessor.CLIArgs;
import org.sleuthkit.autopsy.apiupdate.ModuleUpdates.ModuleVersionNumbers;

/**
 *
 * @author gregd
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        args = "-p C:\\Users\\gregd\\Desktop\\apidiff\\old -s C:\\Users\\gregd\\Documents\\Source\\autopsy".split(" ");
        CLIArgs cliArgs;
        try {
            cliArgs = CLIProcessor.parseCli(args);
            if (cliArgs.isIsHelp()) {
                CLIProcessor.printHelp(null);
                System.exit(0);
            }
        } catch (ParseException ex) {
            CLIProcessor.printHelp(ex);
            System.exit(-1);
            return;
        }

        Map<String, ModuleVersionNumbers> newVersionNumMapping = new HashMap<>();
        
        for (String commonJarFileName : APIDiff.getCommonJars(cliArgs.getPreviousVersPath(), cliArgs.getCurrentVersPath())) {
            try {
                ModuleVersionNumbers prevVersionNums = ModuleUpdates.getVersionsFromJar(cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile());

                ComparisonRecord record = APIDiff.getComparison(
                        cliArgs.getPreviousVersion(),
                        cliArgs.getCurrentVersion(),
                        cliArgs.getPreviousVersPath().toPath().resolve(commonJarFileName).toFile(),
                        cliArgs.getCurrentVersPath().toPath().resolve(commonJarFileName).toFile());

                ModuleVersionNumbers projectedVersionNums = ModuleUpdates.getModuleVersionUpdate(prevVersionNums, record.getChangeType());

                outputDiff(commonJarFileName, record, prevVersionNums, projectedVersionNums);

                newVersionNumMapping.put(projectedVersionNums.getRelease().getModuleName(), projectedVersionNums);
            } catch (IOException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }

        }

        if (cliArgs.isMakeUpdate()) {
            ModuleUpdates.setVersions(cliArgs.getSrcPath(), newVersionNumMapping);
        }

    }

    private static void outputDiff(
            String commonJarFileName,
            ComparisonRecord record,
            ModuleVersionNumbers prevVersionNums,
            ModuleVersionNumbers projectedVersionNums
    ) {
        LOGGER.log(Level.INFO, MessageFormat.format("\n"
                + "====================================\n"
                + "DIFF FOR: {0}\n"
                + "Public API Change Type: {1}\n"
                + "Previous Version Numbers:\n"
                + "  - release: {2}\n"
                + "  - specification: {3}\n"
                + "  - implementation: {4}\n"
                + "Current Version Numbers:\n"
                + "  - release: {5}\n"
                + "  - specification: {6}\n"
                + "  - implementation: {7}\n"
                + "====================================\n"
                + "Public API packages only in previous: {8}\n"
                + "Public API packages only in current: {9}\n"
                + "{10}\n\n",
                commonJarFileName,
                record.getChangeType(),
                prevVersionNums.getRelease().getFullReleaseStr(),
                prevVersionNums.getSpec().getSemVerStr(),
                prevVersionNums.getImplementation(),
                projectedVersionNums.getRelease().getFullReleaseStr(),
                projectedVersionNums.getSpec().getSemVerStr(),
                projectedVersionNums.getImplementation(),
                record.getOnlyPrevApiPackages(),
                record.getOnlyCurrApiPackages(),
                record.getHumanReadableApiChange()
        ));
    }
}
