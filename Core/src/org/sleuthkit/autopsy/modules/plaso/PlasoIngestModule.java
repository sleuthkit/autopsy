/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Cancellable;
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
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.Blackboard.BlackboardException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE.TSK_TL_EVENT;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_TL_EVENT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Image;
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

    private static final int LOG2TIMELINE_WORKERS = 2;

    private File log2TimeLineExecutable;
    private File psortExecutable;

    private IngestJobContext context;
    private Image image;
    private AbstractFile previousFile = null; // cache used when looking up files in Autopsy DB
    private Case currentCase;
    private FileManager fileManager;

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
        currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        //we should do this check at startup...
        if (!(dataSource instanceof Image)) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_dataSource_not_an_image());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.OK;
        }
        image = (Image) dataSource;

        String currentTime = TimeUtilities.epochToTime(System.currentTimeMillis() / 1000);
        currentTime = currentTime.replaceAll(":", "-"); //NON-NLS
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO, currentTime);
        File directory = moduleOutputPath.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        logger.log(Level.INFO, Bundle.PlasoIngestModule_startUp_message());
        statusHelper.progress(Bundle.PlasoIngestModule_running_log2timeline(), 0);
        ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(moduleOutputPath, image);

        try {
            // Run log2timeline
            Process log2TimeLine = log2TimeLineCommand.start();

            try (BufferedReader log2TimeLineOutpout = new BufferedReader(new InputStreamReader(log2TimeLine.getInputStream()))) {
                L2TStatusProcessor statusReader = new L2TStatusProcessor(log2TimeLineOutpout, statusHelper, moduleOutputPath);
                new Thread(statusReader, "log2timeline status reader").start();  //NON-NLS

                ExecUtil.waitForTermination(LOG2TIMELINE_EXECUTABLE, log2TimeLine, new DataSourceIngestModuleProcessTerminator(context));
                statusReader.cancel();
            }

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_log2timeline_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_log2timeline_cancelled());
                return ProcessResult.OK;
            }

            if (Files.notExists(moduleOutputPath.resolve(PLASO))) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_log2timeline()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_log2timeline());
                return ProcessResult.ERROR;
            }

            // sort the output
            statusHelper.progress(Bundle.PlasoIngestModule_running_psort(), 33);
            ProcessBuilder psortCommand = buildPsortCommand(moduleOutputPath);
            ExecUtil.execute(psortCommand, new DataSourceIngestModuleProcessTerminator(context));

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_psort_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_psort_cancelled());
                return ProcessResult.OK;
            }
            Path plasoFile = moduleOutputPath.resolve("plasodb.db3");  //NON-NLS
            if (Files.notExists(plasoFile)) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_psort());
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_psort());
                return ProcessResult.ERROR;
            }

            // parse the output and make artifacts
            createPlasoArtifacts(plasoFile.toString(), statusHelper);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_error_running(), ex);
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
        }

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.PlasoIngestModule_has_run(), Bundle.PlasoIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    private ProcessBuilder buildLog2TimeLineCommand(Path moduleOutputPath, Image image) {
        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker("\"" + log2TimeLineExecutable + "\"", //NON-NLS
                "--vss-stores", "all", //NON-NLS
                "-z", image.getTimeZone(), //NON-NLS
                "--partitions", "all", //NON-NLS
                "--hasher_file_size_limit", "1", //NON-NLS
                "--hashers", "none", //NON-NLS
                "--no_dependencies_check", //NON-NLS
                "--workers", String.valueOf(LOG2TIMELINE_WORKERS),//NON-NLS
                moduleOutputPath.resolve(PLASO).toString(),
                image.getPaths()[0]
        );
        processBuilder.redirectError(moduleOutputPath.resolve("log2timeline_err.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /* Add an environment variable to force log2timeline/psort to run with
         * the same permissions Autopsy uses. */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS
        return processBuilder;
    }

    private ProcessBuilder buildPsortCommand(Path moduleOutputPath) {
        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + psortExecutable + "\"", //NON-NLS
                "-o", "4n6time_sqlite", //NON-NLS
                "-w",//NON-NLS
                moduleOutputPath.resolve("plasodb.db3").toString(), //NON-NLS
                moduleOutputPath.resolve(PLASO).toString()
        );

        processBuilder.redirectOutput(moduleOutputPath.resolve("psort_output.txt").toFile()); //NON-NLS
        processBuilder.redirectError(moduleOutputPath.resolve("psort_err.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    private static File locateExecutable(String executableName) {
        if (!PlatformUtil.isWindowsOS()) {
            return null;
        }

        String executableToFindName = Paths.get(PlatformUtil.is64BitOS() ? PLASO64 : PLASO32, executableName).toString();

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PlasoIngestModule.class.getPackage().getName(), false);

        if (null != exeFile && exeFile.canExecute()) {
            return exeFile;
        }
        return null;
    }

    @NbBundle.Messages({
        "PlasoIngestModule_exception_posting_artifact=Exception Posting artifact.",
        "PlasoIngestModule_event_datetime=Event Date Time",
        "PlasoIngestModule_event_description=Event Description",
        "PlasoIngestModule_exception_adding_artifact=Exception Adding Artifact",
        "PlasoIngestModule_exception_database_error=Error while trying to read into a sqlite db.",
        "PlasoIngestModule_error_posting_artifact=Error Posting Artifact  ",
        "PlasoIngestModule_create_artifacts_cancelled=Cancelled Plaso Artifact Creation ",
        "# {0} - file that events are from",
        "PlasoIngestModule_artifact_progress=Adding events to case: {0}"
    })
    private void createPlasoArtifacts(String plasoDb, DataSourceIngestModuleProgress statusHelper) {
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();
        //NON-NLS
        String sqlStatement = "SELECT substr(filename,1) AS  filename, "
                              + "   strftime('%s', datetime) AS epoch_date, "
                              + "   description, "
                              + "   source, "
                              + "   type, "
                              + "   sourcetype "
                              + " FROM log2timeline "
                              + " WHERE source NOT IN ('FILE', "
                              + "                       'WEBHIST') " // bad dates and duplicates with what we have.
                              + "   AND sourcetype NOT IN ('UNKNOWN', "
                              + "                          'PE Import Time');"; // lots of bad dates //NON-NLS

        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + plasoDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {
            while (resultSet.next()) {
                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_create_artifacts_cancelled());
                    MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_create_artifacts_cancelled());
                    return;
                }

                String currentFileName = resultSet.getString("filename"); //NON-NLS
                statusHelper.progress(Bundle.PlasoIngestModule_artifact_progress(currentFileName), 66);
                Content resolvedFile = getAbstractFile(currentFileName);
                if (resolvedFile == null) {
                    logger.log(Level.INFO, "File from Plaso output not found.  Associating with data source instead: {0}", currentFileName);  //NON-NLS
                    resolvedFile = image;
                }

                long eventType = findEventSubtype(currentFileName, resultSet);
                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_DATETIME, MODULE_NAME,
                                resultSet.getLong("epoch_date")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DESCRIPTION, MODULE_NAME,
                                resultSet.getString("description")),//NON-NLS
                        new BlackboardAttribute(
                                TSK_TL_EVENT_TYPE, MODULE_NAME,
                                eventType));

                try {
                    BlackboardArtifact bbart = resolvedFile.newArtifact(TSK_TL_EVENT);
                    bbart.addAttributes(bbattributes);
                    try {
                        /* Post the artifact which will index the artifact for
                         * keyword search, and fire an event to notify UI of
                         * this new artifact */
                        blackboard.postArtifact(bbart, MODULE_NAME);
                    } catch (BlackboardException ex) {
                        logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_posting_artifact(), ex);
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_adding_artifact(), ex);
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_database_error(), ex);
        }
    }

    @NbBundle.Messages({"PlasoIngestModule_exception_find_file=Exception finding file."})
    private AbstractFile getAbstractFile(String file) {

        Path path = Paths.get(file);
        String fileName = path.getFileName().toString();
        String filePath = path.getParent().toString().replaceAll("\\\\", "/"); //NON-NLS
        if (filePath.endsWith("/") == false) { //NON-NLS
            filePath += "/"; //NON-NLS
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

    /**
     * Determine the event_type_id of the event from the plaso information.
     *
     * @param fileName The name of the file this event is from.
     * @param row      The row returned from the log2timeline table of th eplaso
     *                 output.
     *
     * @return the event_type_id of the EventType of the given event.
     *
     * @throws SQLException
     */
    private long findEventSubtype(String fileName, ResultSet row) throws SQLException {
        switch (row.getString("source")) {
            case "WEBHIST":
                if (fileName.toLowerCase().contains("cookie")//NON-NLS
                    || row.getString("type").toLowerCase().contains("cookie")//NON-NLS
                        ) {//NON-NLS
                    return EventType.WEB_COOKIE.getTypeID();
                } else {
                    return EventType.WEB_HISTORY.getTypeID();
                }
            case "EVT":
            case "LOG":
                return EventType.LOG_ENTRY.getTypeID();
            case "REG":
                switch (row.getString("sourcetype").toLowerCase()) {//NON-NLS
                    case "unknown : usb entries":
                    case "unknown : usbstor entries":
                        return EventType.DEVICES_ATTACHED.getTypeID();
                    default:
                        return EventType.REGISTRY.getTypeID();
                }
            default:
                return EventType.OTHER.getTypeID();
        }
    }

    /**
     * Runs in a thread and reads the output of log2timeline. It redirectes the
     * output both to a log file, and to the status message of the Plaso ingest
     * module progress bar.
     */
    private static class L2TStatusProcessor implements Runnable, Cancellable {

        private final BufferedReader log2TimeLineOutpout;
        private final DataSourceIngestModuleProgress statusHelper;
        private boolean cancelled = false;
        private final Path outputPath;

        private L2TStatusProcessor(BufferedReader log2TimeLineOutpout, DataSourceIngestModuleProgress statusHelper, Path outputPath) throws IOException {
            this.log2TimeLineOutpout = log2TimeLineOutpout;
            this.statusHelper = statusHelper;
            this.outputPath = outputPath;
        }

        @Override
        public void run() {
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve("log2timeline_output.txt"));) {//NON-NLS
                String line;
                while (null != (line = log2TimeLineOutpout.readLine())
                       && cancelled == false) {
                    statusHelper.progress(line);
                    writer.write(line);
                    writer.newLine();
                }
                writer.flush();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error reading log2timeline output stream.", ex);//NON-NLS
            }
        }

        @Override
        public boolean cancel() {
            cancelled = true;
            return true;
        }
    }
}
