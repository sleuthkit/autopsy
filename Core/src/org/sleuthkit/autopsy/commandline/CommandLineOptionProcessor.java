/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commandline;

import java.io.File;
import java.util.HashSet;
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
    private final Option pathToDataSourceOption = Option.optionalArgument('l', "inputPath");
    private final Option caseNameOption = Option.optionalArgument('2', "caseName");
    private final Option runFromCommandLineOption = Option.optionalArgument('3', "runFromCommandLine");
    private String pathToDataSource;
    private String baseCaseName;
    private boolean runFromCommandLine = false;

    @Override
    protected Set<Option> getOptions() {
        Set<Option> set = new HashSet<>();
        set.add(pathToDataSourceOption);
        set.add(caseNameOption);
        set.add(runFromCommandLineOption);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        logger.log(Level.INFO, "Processing Autopsy command line options"); //NON-NLS
        System.out.println("Processing Autopsy command line options using CommandLineOptionProcessor");
        if (values.containsKey(pathToDataSourceOption) && values.containsKey(caseNameOption) && values.containsKey(runFromCommandLineOption)) {
            // parse input parameters
            String inputPath;
            String inputCaseName;
            String modeString;
            if (values.size() < 3) {
                logger.log(Level.SEVERE, "Insufficient number of input arguments. Exiting");
                System.out.println("Insufficient number of input arguments. Exiting");
                this.runFromCommandLine = false;
                return;
            } else {
                String[] argDirs = values.get(pathToDataSourceOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'inputPath'. Exiting");
                    System.out.println("Missing argument 'inputPath'. Exiting");
                    this.runFromCommandLine = false;
                    return;
                }
                inputPath = argDirs[0];

                argDirs = values.get(caseNameOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'caseName'. Exiting");
                    System.out.println("Missing argument 'caseName'. Exiting");
                    this.runFromCommandLine = false;
                    return;
                }
                inputCaseName = argDirs[0];
                
                argDirs = values.get(runFromCommandLineOption);
                if (argDirs.length < 1) {
                    logger.log(Level.SEVERE, "Missing argument 'runFromCommandLine'. Exiting");
                    System.out.println("Missing argument 'runFromCommandLine'. Exiting");
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
                logger.log(Level.SEVERE, "Input file {0} doesn''t exist. Exiting", inputPath);
                System.out.println("Input file " + inputPath + " doesn't exist. Exiting");
                this.runFromCommandLine = false;
                return;
            }

            if (inputCaseName == null || inputCaseName.isEmpty()) {
                logger.log(Level.SEVERE, "Case name argument is empty. Exiting");
                System.out.println("Case name argument is empty. Exiting");
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
        } else {
            System.out.println("Missing input arguments for CommandLineOptionProcessor. Exiting");
            logger.log(Level.SEVERE, "Missing input arguments. Exiting");
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
}
