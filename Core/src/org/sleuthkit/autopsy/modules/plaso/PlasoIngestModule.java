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
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Data source ingest module that runs plaso against the image 
 */

public class PlasoIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(PlasoIngestModule.class.getName());
    private static final IngestServices services = IngestServices.getInstance();
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

    PlasoIngestModule() {
    }

    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

    }

    @NbBundle.Messages({
        "PlasoIngestModule_startUp_message=Starting Plaso Run.",
        "PlasoIngestModule_error_running=Error running Plaso."
    })

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        String imgName = dataSource.getName();
        File log2TimeLineExecutable;
        File psortExecutable;
        Case currentCase;
        String moduleOutputPath;
        String tempOutputPath;
        
        Image img = (Image) dataSource;

        currentCase = Case.getCurrentCase();

        log2TimeLineExecutable = locateExecutable(LOG2TIMELINE_EXECUTABLE);
        if (log2TimeLineExecutable == null) {
            //TODO   Throw exeption
        }
        psortExecutable = locateExecutable(PSORT_EXECUTABLE);
        if (psortExecutable == null) {
            //TODO   Throw exeption
        }
        
        String[] imgFile = img.getPaths();
        
        /*
        * Make an module and temp output folder unique to this data source.
        */
        Long dataSourceId = dataSource.getId();
        moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO).toString();
        File directory = new File(String.valueOf(moduleOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();   
        }
        tempOutputPath = Paths.get(currentCase.getTempDirectory(), PLASO, imgName).toString();
        directory = new File(String.valueOf(tempOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();
        }

        ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(log2TimeLineExecutable, tempOutputPath, imgFile[0]);
        ProcessBuilder psortCommand = buildPsortCommand(psortExecutable, tempOutputPath);
        //logger.log(Level.INFO, "Hash value stored in {0}: {1}", new Object[]{imgName, storedHash}); //NON-NLS

        logger.log(Level.INFO, Bundle.PlasoIngestModule_startUp_message()); //NON-NLS

        try {
            plasoWorker processImage = new plasoWorker(log2TimeLineCommand, psortCommand, moduleOutputPath, tempOutputPath);
            processImage.execute();
        } catch (Exception ex) {
          //TODO Make a real exception
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_error_running(), ex);
            }
 
        return ProcessResult.OK;
    }
    
    private ProcessBuilder buildLog2TimeLineCommand(File log2TimeLineExecutable, String tempOutputPath, String imageName) {
       
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
        commandLine.add(tempOutputPath + File.separator + PLASO);
        commandLine.add(imageName);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(tempOutputPath + File.separator +  "log2timeline_output.txt"));
        processBuilder.redirectError(new File(tempOutputPath + File.separator +  "log2timeline_err.txt"));  //NON-NLS

        return processBuilder; 
    }
    
    private ProcessBuilder buildPsortCommand(File psortExecutable, String tempOutputPath) {
       
        List<String> commandLine = new ArrayList<>();
        commandLine.add("\"" + psortExecutable + "\""); //NON-NLS
        commandLine.add("-o"); //NON-NLS
        commandLine.add("4n6time_sqlite"); //NON-NLS
        commandLine.add("-w");
        commandLine.add(tempOutputPath + File.separator + "plasodb.db3");
        commandLine.add(tempOutputPath + File.separator +  PLASO);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force psort to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(tempOutputPath + File.separator +  "psort_output.txt"));
        processBuilder.redirectError(new File(tempOutputPath + File.separator +  "psort_err.txt"));  //NON-NLS

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

    /**
     * Thread that does the actual extraction work
     */
    private class plasoWorker extends SwingWorker<Void, Void> {
        private final Logger logger = Logger.getLogger(plasoWorker.class.getName());
        private ProgressHandle progress;
        private final ProcessBuilder log2TimeLineCommand;
        private final ProcessBuilder psortCommand;
        private final String moduleOutputPath;
        private final String tempOutputPath;
        private final FileManager fileManager;
        private final Case currentCase;
        
        plasoWorker(ProcessBuilder log2TimeLineCommand, ProcessBuilder psortCommand, String moduleOutputPath, String tempOutputPath) {
            this.log2TimeLineCommand = log2TimeLineCommand;
            this.psortCommand = psortCommand;
            this.moduleOutputPath = moduleOutputPath;
            this.tempOutputPath = tempOutputPath;
            this.currentCase = Case.getCurrentCase();
            this.fileManager = currentCase.getServices().getFileManager();

        }

        @NbBundle.Messages({
            "PlasoWorker_displayName=Running Plaso Against Image..",
            "PlasoWorker_cancel_displayName=Running Plaso.",
            "PlasoWorker_cancel_message=Cancel running plaso."
        })
        @Override
        protected Void doInBackground() throws Exception {
        // Setup progress bar.
            //final String displayName = NbBundle.getMessage(this.getClass(), "ExtractAction.progress.extracting");
            //final String displayName = "Running Plaso Against Image";
            progress = ProgressHandle.createHandle(Bundle.PlasoWorker_displayName(), new Cancellable() {
                @Override
                public boolean cancel() {
                    if (progress != null) {
                        progress.setDisplayName(Bundle.PlasoWorker_cancel_displayName());
                    }
                    MessageNotifyUtil.Message.info(Bundle.PlasoWorker_cancel_message());
                    return PlasoIngestModule.plasoWorker.this.cancel(true);
                }
            });
            progress.start();
            progress.switchToIndeterminate();

            executePlaso(log2TimeLineCommand, psortCommand);
            return null;
        }

        @NbBundle.Messages({
            "PlasoWorker_error_running_plaso=Error running plaso check log file.",
            "PlasoWorker_normal_complete=Plaso processing complete.",
            "PlasoWorker_tempDir_deletion_Error=Plaso Temp directory not deleted."
        })
        @Override
        protected void done() {
            boolean msgDisplayed = false;
            try {
                super.get();
            } catch (Exception ex) {
                if (!this.isCancelled()) {
                    logger.log(Level.SEVERE, Bundle.PlasoWorker_error_running_plaso(), ex); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.PlasoWorker_error_running_plaso());
                    msgDisplayed = true;
                }
            } finally {
                progress.finish();
                if (!this.isCancelled() && !msgDisplayed) {
                    MessageNotifyUtil.Message.info(Bundle.PlasoWorker_normal_complete());
                }
                if (this.isCancelled()) {
                    File deleteTemp = new File(this.tempOutputPath);
                    if (!FileUtil.deleteDir(new File(deleteTemp.getParent()))) {
                        logger.log(Level.INFO, Bundle.PlasoWorker_tempDir_deletion_Error());
                    }
                }
            }
        }       
       
        @NbBundle.Messages({
            "PlasoWorker_error_running_log2timeline=Error running log2timeline.",
            "PlasoWorker_error_running_psort=Error Running psort.",
            "PlasoWorker_tempDir_deletion_execution_error=Plaso Temp directory not deleted.",
            "PlasoWorker_completed=Plaso Processing Completed",
            "PlasoWorker_has_run=Plaso Plugin has been run.",
            "PlasoWorker_exception_running_plaso=Error Running Plaso Plugin see Logs"
        })
        private void executePlaso(ProcessBuilder log2TimeLineCommand, ProcessBuilder psortCommand) throws TskCoreException {

            try {
                int exitVal = ExecUtil.execute(log2TimeLineCommand);
                if (exitVal != 0) {
                    logger.log(Level.SEVERE, Bundle.PlasoWorker_error_running_log2timeline(), exitVal); //NON-NLS
                    return;
                }
                exitVal = ExecUtil.execute(psortCommand);
                if (exitVal != 0) {
                    logger.log(Level.SEVERE, Bundle.PlasoWorker_error_running_psort(), exitVal); //NON-NLS
                    return;
                }
                String plasoDb = tempOutputPath + File.separator + "plasodb.db3";
                createPlasoArtifacts(plasoDb);
 
                File deleteTemp = new File(tempOutputPath);
                try {
                    // Use the deleteTemp since it will have the same name for the directory.
                    // Need to refactor a little to make more sense.
                    String dummyFolder = FileUtil.copyFolder(tempOutputPath, moduleOutputPath, deleteTemp.getName());
                    if (!FileUtil.deleteDir(new File(deleteTemp.getParent()))) {
                        logger.log(Level.INFO, Bundle.PlasoWorker_tempDir_deletion_execution_error());
                    }
                } catch (Exception ex) {
                    logger.log(Level.INFO, Bundle.PlasoWorker_tempDir_deletion_execution_error(), ex);
                }
                IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, Bundle.PlasoWorker_has_run(), Bundle.PlasoWorker_completed() );
                IngestServices.getInstance().postMessage(message);

            } catch (Exception ex) {
                logger.log(Level.SEVERE, Bundle.PlasoWorker_exception_running_plaso(), ex);
        }

        }
  
        @NbBundle.Messages({
            "PlasoWorker_exception_posting_artifact=Exception Posting artifact.",
            "PlasoWorker_event_datetime=Event Date Time",
            "PlasoWorker_event_description=Event Description",
            "PlasoWorker_exception_adding_artifact=Exception Adding Artifact",
            "PlasoWorker_exception_database_error=Error while trying to read into a sqlite db."
        })
        private void createPlasoArtifacts(String plasoDb) {
            org.sleuthkit.datamodel.Blackboard blackboard;
            SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
            blackboard = sleuthkitCase.getBlackboard();
            ResultSet resultSet;
            String connectionString = "jdbc:sqlite:" + plasoDb; //NON-NLS
            String sqlStatement = "select substr(filename,1) filename, strftime('%s', datetime) 'TSK_DATETIME', description 'TSK_DESCRIPTION' \n" +
                                  "  from log2timeline where source != 'FILE';";
//            String sqlStatement = "select datetime('unixepoch', datetime) 'TSK_DATETIME', description 'TSK_DESCRIPTION' "
//                                +  " from log2timeline where source != 'REG';";
            try {
                SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString); //NON-NLS
                resultSet = tempdbconnect.executeQry(sqlStatement);
                while (resultSet.next()) {
                    Collection<BlackboardAttribute> bbattributes = new ArrayList<>();
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, Bundle.PlasoWorker_event_datetime(), resultSet.getLong("TSK_DATETIME")));
                    bbattributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DESCRIPTION, Bundle.PlasoWorker_event_description(), resultSet.getString("TSK_DESCRIPTION")));
                    
                    List<AbstractFile> resolvedFiles = getAbstractFile(resultSet.getString("filename"));
                    try {
                        for (AbstractFile resolvedFile : resolvedFiles) {
                            BlackboardArtifact bbart = resolvedFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_EVENT);
                            if (bbart != null) {
                                bbart.addAttributes(bbattributes);
                                try {
                                   blackboard.postArtifact(bbart);
                                } catch (org.sleuthkit.datamodel.Blackboard.BlackboardException ex) {
                                    logger.log(Level.INFO, Bundle.PlasoWorker_exception_posting_artifact(), ex); //NON-NLS
                                }
                            }
                        }
                    } catch (TskCoreException ex) {
                        logger.log(Level.INFO, Bundle.PlasoWorker_exception_adding_artifact(), ex);
                    }
                }
                tempdbconnect.closeConnection();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, Bundle.PlasoWorker_exception_database_error(), ex); //NON-NLS
            }
        }
        
        @NbBundle.Messages({
            "PlasoWorker_exception_find_file=Exception finding file."
        })
        private List<AbstractFile> getAbstractFile(String file) {
            List<AbstractFile> abstractFile; 
            File eventFile = new File(file.replaceAll("\\\\", "/"));
            String fileName = eventFile.getName();
            String filePath = eventFile.getParent();
            filePath = filePath.replaceAll("\\\\", "/");
            try {
                abstractFile = fileManager.findFiles(fileName.toLowerCase(), filePath.toLowerCase());
                return abstractFile;
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, Bundle.PlasoWorker_exception_find_file(), ex);
            }
            return null;
        }
    }
}
