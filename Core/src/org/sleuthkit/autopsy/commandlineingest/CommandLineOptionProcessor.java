/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2022 Basis Technology Corp.
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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.netbeans.api.sendopts.CommandException;
import org.netbeans.spi.sendopts.Env;
import org.netbeans.spi.sendopts.Option;
import org.netbeans.spi.sendopts.OptionProcessor;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.featureaccess.FeatureAccessUtils;

/**
 * This class can be used to add command line options to Autopsy
 */
@ServiceProvider(service = OptionProcessor.class)
public class CommandLineOptionProcessor extends OptionProcessor {

    private static final Logger logger = Logger.getLogger(CommandLineOptionProcessor.class.getName());
    private final Option caseNameOption = Option.requiredArgument('n', "caseName");
    private final Option caseTypeOption = Option.requiredArgument('t', "caseType");
    private final Option caseBaseDirOption = Option.requiredArgument('o', "caseBaseDir");
    private final Option createCaseCommandOption = Option.withoutArgument('c', "createCase");
    private final Option dataSourcePathOption = Option.requiredArgument('s', "dataSourcePath");
    private final Option dataSourceObjectIdOption = Option.requiredArgument('i', "dataSourceObjectId");
    private final Option addDataSourceCommandOption = Option.withoutArgument('a', "addDataSource");
    private final Option runIngestCommandOption = Option.optionalArgument('r', "runIngest");
    private final Option listAllDataSourcesCommandOption = Option.withoutArgument('l', "listAllDataSources");
    private final Option generateReportsOption = Option.optionalArgument('g', "generateReports");
    private final Option listAllIngestProfileOption = Option.withoutArgument('p', "listAllIngestProfiles");
    private final Option defaultArgument = Option.defaultArguments();

    private boolean runFromCommandLine = false;

    private final List<CommandLineCommand> commands = new ArrayList<>();

    final static String CASETYPE_MULTI = "multi";
    final static String CASETYPE_SINGLE = "single";

    private String defaultArgumentValue = null;
    
    private PropertyChangeSupport changes = new PropertyChangeSupport(this); 
    public static String PROCESSING_STARTED = "command line process started";
    public static String PROCESSING_COMPLETED = "command line process completed";
    
    public enum ProcessState {
        NOT_STARTED,
        RUNNING,
        COMPLETED
    }
    
    private ProcessState state = ProcessState.NOT_STARTED;

    @Override
    protected Set<Option> getOptions() {
        Set<Option> set = new HashSet<>();
        set.add(createCaseCommandOption);
        set.add(caseNameOption);
        set.add(caseTypeOption);
        set.add(caseBaseDirOption);
        set.add(dataSourcePathOption);
        set.add(addDataSourceCommandOption);
        set.add(dataSourceObjectIdOption);
        set.add(runIngestCommandOption);
        set.add(listAllDataSourcesCommandOption);
        set.add(generateReportsOption);
        set.add(listAllIngestProfileOption);
        set.add(defaultArgument);
        return set;
    }

    @Override
    protected void process(Env env, Map<Option, String[]> values) throws CommandException {
        logger.log(Level.INFO, "Processing Autopsy command line options"); //NON-NLS
        System.out.println("Processing Autopsy command line options");
        setState(ProcessState.RUNNING);
        changes.firePropertyChange(PROCESSING_STARTED, false, true);

        if (values.containsKey(defaultArgument)) {
            defaultArgumentValue = values.get(defaultArgument)[0];
            runFromCommandLine(true);
            return;
        }

        // input arguments must contain at least one command
        if (!(values.containsKey(createCaseCommandOption) || values.containsKey(addDataSourceCommandOption)
                || values.containsKey(runIngestCommandOption) || values.containsKey(listAllDataSourcesCommandOption)
                || values.containsKey(generateReportsOption) || values.containsKey(listAllIngestProfileOption))) {
            // not running from command line
            handleError("Invalid command line, an input option must be supplied.");
        }

        // parse input parameters
        String[] argDirs;
        String inputCaseName = "";
        
         if(values.containsKey(listAllIngestProfileOption)) {
            CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.LIST_ALL_INGEST_PROFILES);
            commands.add(newCommand);
            runFromCommandLine(true);
        } else {
            if (values.containsKey(caseNameOption)) {
                argDirs = values.get(caseNameOption);
                if (argDirs.length < 1) {
                    handleError("Missing argument 'caseName'");
                }
                inputCaseName = argDirs[0];

                if (inputCaseName == null || inputCaseName.isEmpty()) {
                    handleError("'caseName' argument is empty");
                }
            }

            // 'caseName' must always be specified
            if (inputCaseName == null || inputCaseName.isEmpty()) {
                handleError("'caseName' argument is empty");
            }

            String caseType = "";
            if (values.containsKey(caseTypeOption)) {
                argDirs = values.get(caseTypeOption);

                if (argDirs.length < 1) {
                    handleError("Missing argument 'caseType'");
                }
                caseType = argDirs[0];

                if (caseType == null || caseType.isEmpty()) {
                    handleError("'caseType' argument is empty");
                } else if (!caseType.equalsIgnoreCase(CASETYPE_MULTI) && !caseType.equalsIgnoreCase(CASETYPE_SINGLE)) {
                    handleError("'caseType' argument is invalid");
                } else if (caseType.equalsIgnoreCase(CASETYPE_MULTI) && !FeatureAccessUtils.canCreateMultiUserCases()) {
                    handleError("Unable to create multi user case. Confirm that multi user settings are configured correctly.");
                }
            } 

            String caseBaseDir = "";
            if (values.containsKey(caseBaseDirOption)) {
                argDirs = values.get(caseBaseDirOption);
                if (argDirs.length < 1) {
                    handleError("Missing argument 'caseBaseDir'");
                }
                caseBaseDir = argDirs[0];

                if (caseBaseDir == null || caseBaseDir.isEmpty()) {
                    handleError("Missing argument 'caseBaseDir' option");
                }

                if (!(new File(caseBaseDir).exists()) || !(new File(caseBaseDir).isDirectory())) {
                    handleError("'caseBaseDir' directory doesn't exist or is not a directory: " + caseBaseDir);
                }
            } 

            // 'caseBaseDir' must always be specified
            if (caseBaseDir == null || caseBaseDir.isEmpty()) {
                handleError("Missing argument 'caseBaseDir' option");
            }

            String dataSourcePath = "";
            if (values.containsKey(dataSourcePathOption)) {

                argDirs = values.get(dataSourcePathOption);
                if (argDirs.length < 1) {
                    handleError("Missing argument 'dataSourcePath'");
                }
                dataSourcePath = argDirs[0];

                // verify inputs
                if (dataSourcePath == null || dataSourcePath.isEmpty()) {
                    handleError("Missing argument 'dataSourcePath'");
                }

                if (!(new File(dataSourcePath).exists())) {
                    handleError("Input data source file " + dataSourcePath + " doesn't exist");
                }
            }

            String dataSourceId = "";
            if (values.containsKey(dataSourceObjectIdOption)) {

                argDirs = values.get(dataSourceObjectIdOption);
                if (argDirs.length < 1) {
                    handleError("Missing argument 'dataSourceObjectIdOption'");
                }
                dataSourceId = argDirs[0];

                // verify inputs
                if (dataSourceId == null || dataSourceId.isEmpty()) {
                    handleError("Input data source id is empty");
                }
            }

            // Create commands in order in which they should be executed:
            // First create the "CREATE_CASE" command, if present
            if (values.containsKey(createCaseCommandOption)) {

                // 'caseName' must always be specified for "CREATE_CASE" command
                if (inputCaseName == null || inputCaseName.isEmpty()) {
                    handleError("'caseName' argument is empty");
                }

                CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.CREATE_CASE);
                newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                newCommand.addInputValue(CommandLineCommand.InputType.CASE_TYPE.name(), caseType);
                commands.add(newCommand);
                runFromCommandLine(true);
            }

            // Add ADD_DATA_SOURCE command, if present
            if (values.containsKey(addDataSourceCommandOption)) {

                // 'dataSourcePath' must always be specified for "ADD_DATA_SOURCE" command
                if (dataSourcePath == null || dataSourcePath.isEmpty()) {
                    handleError("'dataSourcePath' argument is empty");
                }

                CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.ADD_DATA_SOURCE);
                newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                newCommand.addInputValue(CommandLineCommand.InputType.DATA_SOURCE_PATH.name(), dataSourcePath);
                commands.add(newCommand);
                runFromCommandLine(true);
            }

            String ingestProfile = "";
            // Add RUN_INGEST command, if present
            if (values.containsKey(runIngestCommandOption)) {

                argDirs = values.get(runIngestCommandOption);
                if(argDirs != null && argDirs.length > 0) {
                    ingestProfile = argDirs[0];
                }

                // if new data source is being added during this run, then 'dataSourceId' is not specified
                if (!values.containsKey(addDataSourceCommandOption) && dataSourceId.isEmpty()) {
                    // data source is not being added during this run, so 'dataSourceId' should have been specified
                    handleError("'dataSourceId' argument is empty");
                }

                CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.RUN_INGEST);
                newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                newCommand.addInputValue(CommandLineCommand.InputType.DATA_SOURCE_ID.name(), dataSourceId);
                newCommand.addInputValue(CommandLineCommand.InputType.INGEST_PROFILE_NAME.name(), ingestProfile);
                newCommand.addInputValue(CommandLineCommand.InputType.DATA_SOURCE_PATH.name(), dataSourcePath);
                commands.add(newCommand);
                runFromCommandLine(true);
            }

            // Add "LIST_ALL_DATA_SOURCES" command, if present
            if (values.containsKey(listAllDataSourcesCommandOption)) {

                CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.LIST_ALL_DATA_SOURCES);
                newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                commands.add(newCommand);
                runFromCommandLine(true);
            } 

            // Add "GENERATE_REPORTS" command, if present
            if (values.containsKey(generateReportsOption)) {
                List<String> reportProfiles;
                argDirs = values.get(generateReportsOption);
                if (argDirs.length > 0) {
                    // use custom report configuration(s)
                    reportProfiles = Stream.of(argDirs[0].split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());

                    if (reportProfiles == null || reportProfiles.isEmpty()) {
                        handleError("'generateReports' argument is empty");
                    }

                    for (String reportProfile : reportProfiles) {
                        if (reportProfile.isEmpty()) {
                            handleError("Empty report profile name");
                        }
                        CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.GENERATE_REPORTS);
                        newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                        newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                        newCommand.addInputValue(CommandLineCommand.InputType.REPORT_PROFILE_NAME.name(), reportProfile);
                        commands.add(newCommand);
                    }
                } else {
                    // use default report configuration
                    CommandLineCommand newCommand = new CommandLineCommand(CommandLineCommand.CommandType.GENERATE_REPORTS);
                    newCommand.addInputValue(CommandLineCommand.InputType.CASE_NAME.name(), inputCaseName);
                    newCommand.addInputValue(CommandLineCommand.InputType.CASES_BASE_DIR_PATH.name(), caseBaseDir);
                    commands.add(newCommand);
                }

                runFromCommandLine(true);
            }
        }
        
        setState(ProcessState.COMPLETED);
        System.out.println("Completed processing Autopsy command line options");
        changes.firePropertyChange(PROCESSING_COMPLETED, false, true);
    }

    /**
     * Returns whether Autopsy should be running in command line mode or not.
     *
     * @return true if running in command line mode, false otherwise.
     */
    public synchronized boolean isRunFromCommandLine() {
        return runFromCommandLine;
    }
    
    public synchronized void runFromCommandLine(boolean runFromCommandLine) {
        this.runFromCommandLine = runFromCommandLine;
    }

    /**
     * Return the value of the default argument.
     *
     * @return The default argument value or null if one was not set.
     */
    public String getDefaultArgument() {
        return defaultArgumentValue;
    }

    /**
     * Returns list of all commands passed in via command line.
     *
     * @return list of input commands
     */
    List<CommandLineCommand> getCommands() {
        return Collections.unmodifiableList(commands);
    }

    /**
     * Send the error message to the log file and create the exception.
     * 
     * @param errorMessage
     * 
     * @throws CommandException 
     */
    private void handleError(String errorMessage) throws CommandException {
        logger.log(Level.SEVERE, errorMessage);
        throw new CommandException(CommandLineIngestManager.CL_PROCESS_FAILURE, errorMessage);
    }
    
    public void addPropertyChangeListener(
        PropertyChangeListener l) {
        changes.addPropertyChangeListener(l);
    }
    public void removePropertyChangeListener(
        PropertyChangeListener l) {
        changes.removePropertyChangeListener(l);
    }
    
    private synchronized void setState(ProcessState state) {
        this.state = state;
    }
    
    public synchronized ProcessState getState() {
        return state;
    }
}
