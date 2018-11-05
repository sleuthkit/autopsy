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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.SQLiteDBConnect;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProcessTerminator;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Data source ingest module that runs plaso against the image
 */
public class PlasoIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(PlasoIngestModule.class.getName());
    private static final String MODULE_NAME = PlasoModuleFactory.getModuleName();

    private static final String PLASO = "plaso";
    private static final String PLASO64 = "plaso//plaso-20180818-amd64";
    private static final String PLASO32 = "plaso//plaso-20180818-win32";
    private static final String LOG2TIMELINE_EXECUTABLE = "Log2timeline.exe";
    private static final String PSORT_EXECUTABLE = "psort.exe";

    private final Case currentCase = Case.getCurrentCase();
    private final FileManager fileManager = currentCase.getServices().getFileManager();

    private IngestJobContext context;

    private File log2TimeLineExecutable;
    private File psortExecutable;
    private Image image;
    private AbstractFile previousFile = null; // cache used when looking up files in Autopsy DB

    PlasoIngestModule() {
    }

    @NbBundle.Messages({
        "PlasoIngestModule_error_running=Error running Plaso, see log file.",
        "PlasoIngestModule_log2timeline_executable_not_found=Log2timeline Executable Not Found",
        "PlasoIngestModule_psort_executable_not_found=psort Executable Not Found"})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
        log2TimeLineExecutable = locateExecutable(LOG2TIMELINE_EXECUTABLE);
        if (this.log2TimeLineExecutable == null) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_log2timeline_executable_not_found());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            throw new IngestModuleException(Bundle.PlasoIngestModule_log2timeline_executable_not_found());
        }
        psortExecutable = locateExecutable(PSORT_EXECUTABLE);
        if (psortExecutable == null) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_psort_executable_not_found());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            throw new IngestModuleException(Bundle.PlasoIngestModule_psort_executable_not_found());
        }
    }

    @NbBundle.Messages({
        "PlasoIngestModule_startUp_message=Starting Plaso Run.",
        "PlasoIngestModule_error_running_log2timeline=Error running log2timeline, see log file.",
        "PlasoIngestModule_error_running_psort=Error running Psort, see log file.",
        "PlasoIngestModule_log2timeline_cancelled=Log2timeline run was canceled",
        "PlasoIngestModule_psort_cancelled=psort run was canceled",
        "PlasoIngestModule_bad_imageFile=Cannot find image file name and path",
        "PlasoIngestModule_dataSource_not_an_image=Datasource is not an Image.",
        "PlasoIngestModule_running_log2timeline=Running Log2timeline",
        "PlasoIngestModule_running_psort=Running Psort",
        "PlasoIngestModule_completed=Plaso Processing Completed",
        "PlasoIngestModule_has_run=Plaso Plugin has been run."})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        statusHelper.switchToDeterminate(100);

        if (!(dataSource instanceof Image)) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_dataSource_not_an_image());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.OK;
        }
        image = (Image) dataSource;

        String currentTime = TimeUtilities.epochToTime(System.currentTimeMillis() / 1000);
        currentTime = currentTime.replaceAll(":", "-");
        String moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO, currentTime).toString();
        File directory = new File(String.valueOf(moduleOutputPath));
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String[] imgFile = image.getPaths();
        ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(log2TimeLineExecutable, moduleOutputPath, imgFile[0], image.getTimeZone());
        ProcessBuilder psortCommand = buildPsortCommand(psortExecutable, moduleOutputPath);

        logger.log(Level.INFO, Bundle.PlasoIngestModule_startUp_message()); //NON-NLS

        try {
            // Run log2timeline
            statusHelper.progress(Bundle.PlasoIngestModule_running_log2timeline(), 0);
            ExecUtil.execute(log2TimeLineCommand, new DataSourceIngestModuleProcessTerminator(context));
            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_log2timeline_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_log2timeline_cancelled());
                return ProcessResult.OK;
            }
            File plasoFile = new File(moduleOutputPath + File.separator + PLASO);
            if (!plasoFile.exists()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_log2timeline()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_log2timeline());
                return ProcessResult.OK;
            }
            
            // sort the output
            statusHelper.progress(Bundle.PlasoIngestModule_running_psort(), 33);
            ExecUtil.execute(psortCommand, new DataSourceIngestModuleProcessTerminator(context));
            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_psort_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_psort_cancelled());
                return ProcessResult.OK;
            }
            plasoFile = new File(moduleOutputPath + File.separator + "plasodb.db3");
            if (!plasoFile.exists()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_psort()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_psort());
                return ProcessResult.OK;
            }
            
            // parse the output and make artifacts
            createPlasoArtifacts(plasoFile.getAbsolutePath(), statusHelper);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_error_running(), ex);
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
        }

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA, Bundle.PlasoIngestModule_has_run(), Bundle.PlasoIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    private ProcessBuilder buildLog2TimeLineCommand(File log2TimeLineExecutable, String moduleOutputPath, String imageName, String timeZone) {

        List<String> commandLine = Arrays.asList(
                "\"" + log2TimeLineExecutable + "\"", //NON-NLS 
                "--vss-stores", //NON-NLS
                "all", //NON-NLS
                "-z",
                timeZone,
                "--partitions",
                "all",
                "--hasher_file_size_limit",
                "1",
                "--hashers",
                "none",
                "--no_dependencies_check",
                moduleOutputPath + File.separator + PLASO,
                imageName
        );

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline to run with the
         * same permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(moduleOutputPath + File.separator + "log2timeline_output.txt"));
        processBuilder.redirectError(new File(moduleOutputPath + File.separator + "log2timeline_err.txt"));  //NON-NLS

        return processBuilder;
    }

    private ProcessBuilder buildPsortCommand(File psortExecutable, String moduleOutputPath) {

        List<String> commandLine = Arrays.asList(
                "\"" + psortExecutable + "\"", //NON-NLS
                "-o", //NON-NLS
                "4n6time_sqlite", //NON-NLS
                "-w",
                moduleOutputPath + File.separator + "plasodb.db3",
                moduleOutputPath + File.separator + PLASO);

        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force psort to run with the same
         * permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        processBuilder.redirectOutput(new File(moduleOutputPath + File.separator + "psort_output.txt"));
        processBuilder.redirectError(new File(moduleOutputPath + File.separator + "psort_err.txt"));  //NON-NLS

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
        "PlasoIngestModule_create_artifacts_cancelled=Cancelled Plaso Artifact Creation "})
    private void createPlasoArtifacts(String plasoDb, DataSourceIngestModuleProgress statusHelper) {
        org.sleuthkit.datamodel.Blackboard blackboard;
        SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
        blackboard = sleuthkitCase.getBlackboard();
        String connectionString = "jdbc:sqlite:" + plasoDb; //NON-NLS
        String sqlStatement = "select substr(filename,1) filename, strftime('%s', datetime) 'epoch_date', description, source, type, sourcetype \n"
                              + "  from log2timeline where source not in ('FILE') and sourcetype not in ('UNKNOWN');";

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", connectionString); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {
            
            while (resultSet.next()) {
                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_create_artifacts_cancelled()); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_create_artifacts_cancelled());
                    return;
                }

                // lots of bad dates
                if (resultSet.getString("sourcetype").equals("PE Import Time")) {
                    continue;
                } // bad dates and duplicates with what we have.
                // TODO: merge results somehow
                else if (resultSet.getString("source").equals("WEBHIST")) {
                    continue;
                }

                String currentFile = resultSet.getString("filename");
                statusHelper.progress("Adding events to case: " + currentFile, 66);

                Content resolvedFile = getAbstractFile(currentFile);
                if (resolvedFile == null) {
                    logger.log(Level.INFO, "File from Plaso output not found.  Associating with data source instead: {0}", resultSet.getString("filename"));
                    resolvedFile = image;
                }
                
                long eventType = findEventSubtype(resultSet.getString("source"), resultSet.getString("filename"), resultSet.getString("type"), resultSet.getString("description"), resultSet.getString("sourcetype"));
                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                ATTRIBUTE_TYPE.TSK_DATETIME, MODULE_NAME,
                                resultSet.getLong("epoch_date")),
                        new BlackboardAttribute(
                                ATTRIBUTE_TYPE.TSK_DESCRIPTION, MODULE_NAME,
                                resultSet.getString("description")),
                        new BlackboardAttribute(
                                ATTRIBUTE_TYPE.TSK_TL_EVENT_TYPE, MODULE_NAME,
                                eventType));

                try {
                    BlackboardArtifact bbart = resolvedFile.newArtifact(TSK_TL_EVENT);
                    bbart.addAttributes(bbattributes);
                    try {
                        /*
                         * post the artifact which will index the artifact for
                         * keyword search, and fire an event to notify UI of
                         * this new artifact
                         */
                        blackboard.postArtifact(bbart, MODULE_NAME);
                    } catch (BlackboardException ex) {
                        logger.log(Level.INFO, Bundle.PlasoIngestModule_exception_posting_artifact(), ex); //NON-NLS
                    }

                } catch (TskCoreException ex) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_exception_adding_artifact(), ex);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_database_error(), ex); //NON-NLS
        }
    }

    @NbBundle.Messages({"PlasoIngestModule_exception_find_file=Exception finding file."})
    private AbstractFile getAbstractFile(String file) {

        Path path = Paths.get(file);
        String fileName = path.getFileName().toString();
        String filePath = path.getParent().toString().replaceAll("\\\\", "/");
        if (filePath.endsWith("/") == false) {
            filePath += "/";
        }

        // check the cached file
        if (previousFile != null
            && previousFile.getName().equalsIgnoreCase(fileName)
            && previousFile.getParentPath().equalsIgnoreCase(filePath)) {
            return previousFile;

        }
        try {
            List<AbstractFile> abstractFiles = fileManager.findFiles(fileName, filePath);
            if (abstractFiles.size() == 1) {
                return abstractFiles.get(0);
            }
            for (AbstractFile resolvedFile : abstractFiles) {
                // double check its an exact match
                if (filePath.equalsIgnoreCase(resolvedFile.getParentPath())) {
                    // cache it for next time
                    previousFile = resolvedFile;
                    return resolvedFile;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, Bundle.PlasoIngestModule_exception_find_file(), ex);
        }
        return null;
    }

    private long findEventSubtype(String plasoSource, String fileName, String plasoType, String plasoDescription, String sourceType) {

        if (plasoSource.matches("WEBHIST")) {
            if (fileName.toLowerCase().contains("cookie") || plasoType.toLowerCase().contains("cookie") || plasoDescription.toLowerCase().contains("cookie")) {
                return EventType.WEB_COOKIE.getTypeID();
            }
            return EventType.WEB_HISTORY.getTypeID();
        }
        if (plasoSource.matches("EVT") || plasoSource.matches("LOG")) {
            return EventType.LOG_ENTRY.getTypeID();
        }
        if (plasoSource.matches("REG")) {
            if (sourceType.toLowerCase().matches("unknown : usb entries") || sourceType.toLowerCase().matches("unknown : usbstor entries")) {
                return EventType.DEVICES_ATTACHED.getTypeID();
            }
            return EventType.REGISTRY.getTypeID();
        }
        return EventType.OTHER.getTypeID();
    }
}
