/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandlineingest;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * This class can be used to add command line options to Autopsy
 */
@ServiceProvider(service = OptionProcessor.class)
public class CommandLineOptionProcessor extends OptionProcessor {

    private static final Logger logger = Logger.getLogger(CommandLineOptionProcessor.class.getName());
    private final Option caseNameOption = Option.optionalArgument('1', "caseName");
    private final Option caseBaseDirOption = Option.optionalArgument('2', "caseBaseDir");
    private final Option createCaseCommandOption = Option.optionalArgument('3', "createCase");
    private final Option dataSourcePathOption = Option.optionalArgument('4', "dataSourcePath");
    private boolean runFromCommandLine = false;

    private final List<CommandLineCommand> commands = new ArrayList<>();

    @Override
    protected Set<Option> getOptions() {
        Set<Option> set = new HashSet<>();
        set.add(createCaseCommandOption);
        set.add(caseNameOption);
        set.add(caseBaseDirOption);
        set.add(dataSourcePathOption);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        logger.log(Level.INFO, "Processing Autopsy command line options"); //NON-NLS
        System.out.println("Processing Autopsy command line options");
        runFromCommandLine = false;

        // input arguments must contain at least one command
        if (!values.containsKey(createCaseCommandOption) /* || OtherCommands */) {
            // not running from command line
            logger.log(Level.INFO, "No command line commands passed in as inputs. Not running from command line."); //NON-NLS
            System.out.println("No command line commands passed in as inputs. Not running from command line.");
            return;
        }

        // parse input parameters
        String[] argDirs;
        String inputCaseName = "";
        if (values.containsKey(caseNameOption)) {
            argDirs = values.get(caseNameOption);
            if (argDirs.length < 1) {
                logger.log(Level.SEVERE, "Missing argument 'caseName'");
                System.out.println("Missing argument 'caseName'");
                return;
            }
            inputCaseName = argDirs[0];
        }

        String caseBaseDir = "";
        if (values.containsKey(caseBaseDirOption)) {
            argDirs = values.get(caseBaseDirOption);
            if (argDirs.length < 1) {
                logger.log(Level.SEVERE, "Missing argument 'caseBaseDir'");
                System.out.println("Missing argument 'caseBaseDir'");
                return;
            }
            caseBaseDir = argDirs[0];
        }

        String inputPath = "";
        if (values.containsKey(dataSourcePathOption)) {

            argDirs = values.get(dataSourcePathOption);
            if (argDirs.length < 1) {
                logger.log(Level.SEVERE, "Missing argument 'dataSourcePath'");
                System.out.println("Missing argument 'dataSourcePath'");
                return;

            }
            inputPath = argDirs[0];

            // verify inputs
            if (inputPath == null || inputPath.isEmpty() || !(new File(inputPath).exists())) {
                logger.log(Level.SEVERE, "Input file {0} doesn''t exist", inputPath);
                System.out.println("Input file " + inputPath + " doesn't exist");
                return;
            }
        }

        // Create commands in order in which they should be executed:
        // First create the "CREATE_CASE" command, if present
        if (values.containsKey(createCaseCommandOption)) {

            if (inputCaseName == null || inputCaseName.isEmpty()) {
                logger.log(Level.SEVERE, "'caseName' argument is empty");
                System.out.println("'caseName' argument is empty");
                runFromCommandLine = false;
                return;
            }

            if (caseBaseDir == null || caseBaseDir.isEmpty() || !(new File(caseBaseDir).exists())) {
                logger.log(Level.SEVERE, "'caseBaseDir' directory doesn't exist");
                System.out.println("'caseBaseDir' directory doesn't exist");
                runFromCommandLine = false;
                return;
            }

            CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.CREATE_CASE);
            newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
            newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
            commands.add(newCommand);
            runFromCommandLine = true;
        }

        // Add ADD_DATA_SOURCE command, if present
        // Add RUN_INGEST command, if present
    }

    /**
     * Returns whether Autopsy should be running in command line mode or not.
     *
     * @return true if running in command line mode, false otherwise.
     */
    public boolean isRunFromCommandLine() {
        return runFromCommandLine;
    }

    /**
     * Returns list of all commands passed in via command line.
     *
     * @return list of input commands
     */
    List<CommandLineCommand> getCommands() {
        return commands;
    }
}
