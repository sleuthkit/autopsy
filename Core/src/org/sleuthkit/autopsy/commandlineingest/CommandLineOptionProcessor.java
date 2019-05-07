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
    private final Option dataSourcePathOption = Option.optionalArgument('4', "dataSourcePath");    
    private final Option runFromCommandLineOption = Option.optionalArgument('3', "runFromCommandLine");
    private String pathToDataSource;
    private String baseCaseName;
    private boolean runFromCommandLine = false;
    
    private List<CommandLineCommand> commands = new ArrayList<>();

    @Override
    protected Set<Option> getOptions() {
        Set<Option> set = new HashSet<>();
        set.add(caseNameOption);
        set.add(caseBaseDirOption);
        set.add(dataSourcePathOption);
        set.add(runFromCommandLineOption);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        logger.log(Level.INFO, "Processing Autopsy command line options"); //NON-NLS
        System.out.println("Processing Autopsy command line options");
        if (values.containsKey(caseNameOption) || values.containsKey(caseBaseDirOption) || values.containsKey(dataSourcePathOption) || values.containsKey(runFromCommandLineOption)) {
            // parse input parameters
            String inputPath = "C:\\TEST\\Inputs\\Small\\small2.img";
            String inputCaseName;
            String caseBaseDir;
            String modeString;
            String[] argDirs;
            if (values.size() < 1) { // ELTODO
                logger.log(Level.SEVERE, "Insufficient number of input arguments to run command line ingest");
                System.out.println("Insufficient number of input arguments to run command line ingest");
                this.runFromCommandLine = false;
                return;
            } else {
                if (values.containsKey(dataSourcePathOption)) {
                    argDirs = values.get(dataSourcePathOption);
                    if (argDirs.length < 1) {
                        logger.log(Level.SEVERE, "Missing argument 'dataSourcePath'");
                        System.out.println("Missing argument 'dataSourcePath'");
                        this.runFromCommandLine = false;
                        return;

                    }
                    inputPath = argDirs[0];
                }

                argDirs = values.get(caseNameOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'caseName'");
                    System.out.println("Missing argument 'caseName'");
                    this.runFromCommandLine = false;
                    return;
                }
                inputCaseName = argDirs[0];
                
                argDirs = values.get(caseBaseDirOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'caseBaseDir'");
                    System.out.println("Missing argument 'caseBaseDir'");
                    this.runFromCommandLine = false;
                    return;
                }
                caseBaseDir = argDirs[0];                
                
                argDirs = values.get(runFromCommandLineOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'runFromCommandLine'");
                    System.out.println("Missing argument 'runFromCommandLine'");
                    this.runFromCommandLine = false;
                    return;
                }
                modeString = argDirs[0];

                // verify inputs
                if (modeString == null || modeString.isEmpty()) {
                    this.runFromCommandLine = false;
                    System.out.println("runFromCommandLine argument is empty");
                    return;
                }

                if (modeString.equalsIgnoreCase("true")) {
                    this.runFromCommandLine = true;
                }

                System.out.println("runFromCommandLine = " + this.runFromCommandLine);                
            }

            // verify inputs
            if (inputPath == null || inputPath.isEmpty() || !(new File(inputPath).exists())) {
                logger.log(Level.SEVERE, "Input file {0} doesn''t exist", inputPath);
                System.out.println("Input file " + inputPath + " doesn't exist");
                this.runFromCommandLine = false;
                return;
            }

            if (inputCaseName == null || inputCaseName.isEmpty()) {
                logger.log(Level.SEVERE, "Case name argument is empty");
                System.out.println("Case name argument is empty");
                this.runFromCommandLine = false;
                return;
            }

            // save the inputs
            this.pathToDataSource = inputPath;
            this.baseCaseName = inputCaseName;
            logger.log(Level.INFO, "Input file = {0}", this.pathToDataSource); //NON-NLS
            logger.log(Level.INFO, "Case name = {0}", this.baseCaseName); //NON-NLS
            logger.log(Level.INFO, "runFromCommandLine = {0}", this.runFromCommandLine); //NON-NLS
            System.out.println("Input file = " + this.pathToDataSource);
            System.out.println("Case name = " + this.baseCaseName);
            System.out.println("runFromCommandLine = " + this.runFromCommandLine);
            
            CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.CREATE_CASE);
            newCommand.addInputValue("caseName", inputCaseName);
            newCommand.addInputValue("caseBaseDir", caseBaseDir);
            commands.add(newCommand);
        } else {
            System.out.println("Missing input arguments to run command line ingest");
            logger.log(Level.SEVERE, "Missing input arguments to run command line ingest");
        }
    }

    /**
     * Returns user specified path to data source
     *
     * @return the inputPath
     */
    String getPathToDataSource() {
        return pathToDataSource;
    }

    /**
     * Returns user specified case name
     *
     * @return the inputCaseName
     */
    String getBaseCaseName() {
        return baseCaseName;
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
