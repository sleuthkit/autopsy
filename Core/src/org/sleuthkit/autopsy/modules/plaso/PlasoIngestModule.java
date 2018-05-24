/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.modules.plaso;

import java.io.File;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Data source ingest module that runs plaso against the image 
 */

public class PlasoIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(PlasoIngestModule.class.getName());
    private static final String PLASO = "plaso";
    private static final String PLASO64 = "plaso//plaso-20180127-amd64";
    private static final String PLASO32 = "plaso//plaso-20180127-win32";
    private static final String LOG2TIMELINE_EXECUTABLE = "Log2timeline.exe"; 
    private static final String PSORT_EXECUTABLE = "psort.exe";
    private static final String VSS_OPTIONS = "--vss-stores";
    private static final String PARTITIONS = "--partitions";
    private static final String HASHER_FILE_SIZE_LIMIT = "--hasher_file_size_limit";
    private static final String ONE = "1";
    private static final String HASHERS = "--hashers";
    private static final String NONE = "none";
    private static final String ALL = "all";
    private IngestJobContext context;
    private final Case currentCase = Case.getCurrentCase();
    private final FileManager fileManager = currentCase.getServices().getFileManager();

    PlasoIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

    }

    @NbBundle.Messages({
        "PlasoIngestModule_startUp_message=Starting Plaso Run.",
        "PlasoIngestModule_error_running=Error running Plaso, see log file.",
        "PlasoIngestModule_log2timeline_cancelled=Log2timeline run was canceled",
        "PlasoIngestModule_psort_cancelled=psort run was canceled",
        "PlasoIngestModule_bad_imageFile=Cannot find image file name and path",
        "PlasoIngestModule_log2timeline_executable_not_found=Log2timeline Executable Not Found",
        "PlasoIngestModule_psort_executable_not_found=psort Executable Not Found",
        "PlasoIngestModule_dataSource_not_an_image=Datasource is not an Image."
    })

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        Image img;
        if (dataSource instanceof Image) {
           img = (Image) dataSource;
        } else {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_dataSource_not_an_image());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;                   
        }

        String[] imgFile = img.getPaths();

        File log2TimeLineExecutable = locateExecutable(LOG2TIMELINE_EXECUTABLE);
        if (log2TimeLineExecutable == null) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_log2timeline_executable_not_found());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
        }
        File psortExecutable = locateExecutable(PSORT_EXECUTABLE);
        if (psortExecutable == null) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_psort_executable_not_found());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
        }
        
        String moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO).toString();
        File directory = new File(String.valueOf(moduleOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();   
        }

        ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(log2TimeLineExecutable, moduleOutputPath, imgFile[0]);
        ProcessBuilder psortCommand = buildPsortCommand(psortExecutable, moduleOutputPath);

        logger.log(Level.INFO, Bundle.PlasoIngestModule_startUp_message()); //NON-NLS

        try {
            ExecUtil.execute(log2TimeLineCommand, new DataSourceIngestModuleProcessTerminator(context));
            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_log2timeline_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_log2timeline_cancelled());
                return ProcessResult.OK;
            }
            ExecUtil.execute(psortCommand, new DataSourceIngestModuleProcessTerminator(context));
            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_psort_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_psort_cancelled());
                return ProcessResult.OK;
            }
            String plasoDb = moduleOutputPath + File.separator + "plasodb.db3";
            createPlasoArtifacts(plasoDb);

        } catch (Exception ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_error_running(), ex);
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
            }
 
        return ProcessResult.OK;
    }
    
    private ProcessBuilder buildLog2TimeLineCommand(File log2TimeLineExecutable, String moduleOutputPath, String imageName) {
       
        List<String> commandLine = new ArrayList<>();
        commandLine.add("\"" + log2TimeLineExecutable + "\""); //NON-NLS
        commandLine.add(VSS_OPTIONS); //NON-NLS
        commandLine.add(ALL); //NON-NLS
        commandLine.add(PARTITIONS);
        commandLine.add(ALL);
        commandLine.add(HASHER_FILE_SIZE_LIMIT);
        commandLine.add(ONE);
        commandLine.add(HASHERS);
        commandLine.add(NONE);
        commandLine.add(moduleOutputPath + File.separator + PLASO);
        commandLine.add(imageName);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(moduleOutputPath + File.separator +  "log2timeline_output.txt"));
        processBuilder.redirectError(new File(moduleOutputPath + File.separator +  "log2timeline_err.txt"));  //NON-NLS

        return processBuilder; 
    }
    
    private ProcessBuilder buildPsortCommand(File psortExecutable, String moduleOutputPath) {
       
        List<String> commandLine = new ArrayList<>();
        commandLine.add("\"" + psortExecutable + "\""); //NON-NLS
        commandLine.add("-o"); //NON-NLS
        commandLine.add("4n6time_sqlite"); //NON-NLS
        commandLine.add("-w");
        commandLine.add(moduleOutputPath + File.separator + "plasodb.db3");
        commandLine.add(moduleOutputPath + File.separator +  PLASO);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force psort to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(moduleOutputPath + File.separator +  "psort_output.txt"));
        processBuilder.redirectError(new File(moduleOutputPath + File.separator +  "psort_err.txt"));  //NON-NLS

        return processBuilder; 
    }

    private static File locateExecutable(String executableName) {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName;
        if (PlatformUtil.is64BitOS()) {
            executableToFindName = Paths.get(PLASO64, executableName).toString();
        } else {
            executableToFindName = Paths.get(PLASO32, executableName).toString();            
        }
        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PlasoIngestModule.class.getPackage().getName(), false);
        if (null == exeFile) {
            return null;
        }

        if (!exeFile.canExecute()) {
            return null;
        }

        return exeFile;
    }
    
    @NbBundle.Messages({
        "PlasoIngestModule_exception_posting_artifact=Exception Posting artifact.",
        "PlasoIngestModule_event_datetime=Event Date Time",
        "PlasoIngestModule_event_description=Event Description",
        "PlasoIngestModule_exception_adding_artifact=Exception Adding Artifact",
        "PlasoIngestModule_exception_database_error=Error while trying to read into a sqlite db.",
        "PlasoIngestModule_error_posting_artifact=Error Posting Artifact  ",
        "PlasoIngestModule_create_artifacts_cancelled=Cancelled Plaso Artifact Creation "
    })
    private void createPlasoArtifacts(String plasoDb) {
        org.sleuthkit.datamodel.Blackboard blackboard;
        SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
        blackboard = sleuthkitCase.getBlackboard();
        String connectionString = "jdbc:sqlite:" + plasoDb; //NON-NLS
        String sqlStatement = "select substr(filename,1) filename, strftime('%s', datetime) 'TSK_DATETIME', description 'TSK_DESCRIPTION' \n" +
                              "  from log2timeline where source not in ('FILE', 'REG');";
        try {
            SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString); //NON-NLS
            ResultSet resultSet = tempdbconnect.executeQry(sqlStatement);
            while (resultSet.next()) {
                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_create_artifacts_cancelled()); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_create_artifacts_cancelled());
                    return;
                }
                Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, Bundle.PlasoIngestModule_event_datetime(), resultSet.getLong("TSK_DATETIME")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DESCRIPTION, Bundle.PlasoIngestModule_event_description(), resultSet.getString("TSK_DESCRIPTION")));
                bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_EVENT_TYPE, "PLASO", EventType.CUSTOM_TYPES.getTypeID()));
                AbstractFile resolvedFile = getAbstractFile(resultSet.getString("filename"));
                try {
                    BlackboardArtifact bbart = resolvedFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EVENT);
                    if (bbart != null) {
                        bbart.addAttributes(bbattributes);
                        try {
                            blackboard.postArtifact(bbart);
                        } catch (org.sleuthkit.datamodel.Blackboard.BlackboardException ex) {
                            logger.log(Level.INFO, Bundle.PlasoIngestModule_exception_posting_artifact(), ex); //NON-NLS
                        }
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_exception_adding_artifact(), ex);
                }
            }
            tempdbconnect.closeConnection();
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_database_error(), ex); //NON-NLS
        }
    }
        
    @NbBundle.Messages({
        "PlasoIngestModule_exception_find_file=Exception finding file."
    })
    private AbstractFile getAbstractFile(String file) {
        List<AbstractFile> abstractFiles;
        File eventFile = new File(file.replaceAll("\\\\", "/"));
        String fileName = eventFile.getName();
        String filePath = eventFile.getParent();
        filePath = filePath.replaceAll("\\\\", "/");
        filePath = filePath.toLowerCase() + "/";
        try {
            abstractFiles= fileManager.findFiles(fileName.toLowerCase(), filePath);
            for (AbstractFile resolvedFile : abstractFiles) {
                if (filePath.matches(resolvedFile.getParentPath().toLowerCase())) {
                    return resolvedFile;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, Bundle.PlasoIngestModule_exception_find_file(), ex);
        }
        return null;
    }
}
