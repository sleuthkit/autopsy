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
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
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
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Data source ingest module that runs plaso against the image
 */
public class PlasoIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(PlasoIngestModule.class.getName());
    private static final String MODULE_NAME = PlasoModuleFactory.getModuleName();

    private static final String PLASO = "plaso"; //NON-NLS
    private static final String PLASO64 = "plaso//plaso-20180818-amd64";//NON-NLS
    private static final String PLASO32 = "plaso//plaso-20180818-win32";//NON-NLS
    private static final String LOG2TIMELINE_EXECUTABLE = "Log2timeline.exe";//NON-NLS
    private static final String PSORT_EXECUTABLE = "psort.exe";//NON-NLS

    private static final String WEBHIST = "WEBHIST";
    private static final String COOKIE = "cookie";

    private IngestJobContext context;

    private File log2TimeLineExecutable;
    private File psortExecutable;
    private Image image;
    private AbstractFile previousFile = null; // cache used when looking up files in Autopsy DB
    private final PlasoModuleSettings settings;
    private Case currentCase;
    private FileManager fileManager;

    PlasoIngestModule(PlasoModuleSettings settings) {
        this.settings = settings;
    }

    @NbBundle.Messages({
        "PlasoIngestModule.error.running=Error running Plaso, see log file.",
        "PlasoIngestModule.log2timeline.executable.not.found=Log2timeline Executable Not Found",
        "PlasoIngestModule.psort.executable.not.found=psort Executable Not Found"})
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
        "PlasoIngestModule.startUp.message=Starting Plaso Run.",
        "PlasoIngestModule.error.running.log2timeline=Error running log2timeline, see log file.",
        "PlasoIngestModule.error.running.psort=Error running Psort, see log file.",
        "PlasoIngestModule.log2timeline.cancelled=Log2timeline run was canceled",
        "PlasoIngestModule.psort.cancelled=psort run was canceled",
        "PlasoIngestModule.bad.imageFile=Cannot find image file name and path",
        "PlasoIngestModule.dataSource.not.an.image=Datasource is not an Image.",
        "PlasoIngestModule.running.log2timeline=Running Log2timeline",
        "PlasoIngestModule.running.psort=Running Psort",
        "PlasoIngestModule.completed=Plaso Processing Completed",
        "PlasoIngestModule.has.run=Plaso Plugin has been run."})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {
        statusHelper.switchToDeterminate(100);
        currentCase = Case.getCurrentCase();
        fileManager = currentCase.getServices().getFileManager();

        if (!(dataSource instanceof Image)) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_dataSource_not_an_image());
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.OK;
        }
        image = (Image) dataSource;
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss z").format(System.currentTimeMillis());//NON-NLS
        
        Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO, currentTime);
        File directory = moduleOutputPath.toFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(moduleOutputPath, image);

        logger.log(Level.INFO, Bundle.PlasoIngestModule_startUp_message());
        statusHelper.progress(Bundle.PlasoIngestModule_running_log2timeline(), 0);
        try {
            // Run log2timeline
            Process log2TimeLine = log2TimeLineCommand.start();

            try (BufferedReader log2TimeLineOutpout = new BufferedReader(new InputStreamReader(log2TimeLine.getInputStream()))) {
                L2TStatusProcessor statusReader = new L2TStatusProcessor(log2TimeLineOutpout, statusHelper, moduleOutputPath);
                new Thread(statusReader, "log2timeline status reader thread").start();  //NON-NLS

                ExecUtil.waitForTermination(LOG2TIMELINE_EXECUTABLE, log2TimeLine, new DataSourceIngestModuleProcessTerminator(context));
                statusReader.cancel();
            }

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_log2timeline_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_log2timeline_cancelled());
                return ProcessResult.OK;
            }

            if (!moduleOutputPath.resolve(PLASO).toFile().exists()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_log2timeline()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_log2timeline());
                return ProcessResult.OK;
            }

            // sort the output
            ProcessBuilder psortCommand = buildPsortCommand(moduleOutputPath);
            psortCommand.redirectError(moduleOutputPath.resolve("psort_err.txt").toFile());  //NON-NLS
            psortCommand.redirectOutput(moduleOutputPath.resolve("psort_output.txt").toFile());  //NON-NLS

            statusHelper.progress(Bundle.PlasoIngestModule_running_psort(), 33);
            ExecUtil.execute(psortCommand, new DataSourceIngestModuleProcessTerminator(context));

            if (context.dataSourceIngestIsCancelled()) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_psort_cancelled()); //NON-NLS
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_psort_cancelled());
                return ProcessResult.OK;
            }
            Path plasoFile = moduleOutputPath.resolve("plasodb.db3");  //NON-NLS
            if (Files.exists(plasoFile) == false) {
                logger.log(Level.INFO, Bundle.PlasoIngestModule_error_running_psort());
                MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running_psort());
                return ProcessResult.OK;
            }

            // parse the output and make artifacts
            createPlasoArtifacts(plasoFile.toString(), statusHelper);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, Bundle.PlasoIngestModule_error_running(), ex);//NON-NLS
            MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_error_running());
            return ProcessResult.ERROR;
        }

        IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                Bundle.PlasoIngestModule_has_run(),
                Bundle.PlasoIngestModule_completed());
        IngestServices.getInstance().postMessage(message);
        return ProcessResult.OK;
    }

    private ProcessBuilder buildLog2TimeLineCommand(Path moduleOutputPath, Image image) {
        //make a csv list of disabled parsers.
        String parsersString = settings.getParsers().entrySet().stream()
                .filter(entry -> entry.getValue() == false)
                .map(entry -> "!" + entry.getKey()) // '!' prepended to parsername disables it. //NON-NLS
                .collect(Collectors.joining(","));//NON-NLS

        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + log2TimeLineExecutable + "\"", //NON-NLS
                "--vss-stores", "all", //NON-NLS
                "-z", image.getTimeZone(), //NON-NLS
                "--partitions", "all", //NON-NLS
                "--hasher_file_size_limit", "1", //NON-NLS
                "--hashers", "none", //NON-NLS
                "--parsers", "\"" + parsersString + "\"",//NON-NLS
                "--no_dependencies_check", //NON-NLS
                moduleOutputPath.resolve(PLASO).toString(),
                image.getPaths()[0]
        );
        processBuilder.redirectError(new File(moduleOutputPath + File.separator + "log2timeline_err.txt"));  //NON-NLS

        return processBuilder;
    }

    static private ProcessBuilder buildProcessWithRunAsInvoker(String... commandLine) {
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine);
        /*
         * Add an environment variable to force log2timeline/psort to run with
         * the same permissions Autopsy uses.
         */
        processBuilder.environment().put("__COMPAT_LAYER", "RunAsInvoker"); //NON-NLS

        return processBuilder;
    }

    private ProcessBuilder buildPsortCommand(Path moduleOutputPath) {
        ProcessBuilder processBuilder = buildProcessWithRunAsInvoker(
                "\"" + psortExecutable + "\"", //NON-NLS
                "-o", "4n6time_sqlite", //NON-NLS
                "-w", moduleOutputPath.resolve("plasodb.db3").toString(), //NON-NLS
                moduleOutputPath.resolve(PLASO).toString()
        );

        processBuilder.redirectOutput(moduleOutputPath.resolve("psort_output.txt").toFile());
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
        "PlasoIngestModule.exception.posting.artifact=Exception Posting artifact.",
        "PlasoIngestModule.event.datetime=Event Date Time",
        "PlasoIngestModule.event.description=Event Description",
        "PlasoIngestModule.exception.adding.artifact=Exception Adding Artifact",
        "PlasoIngestModule.exception.database.error=Error while trying to read into a sqlite db.",
        "PlasoIngestModule.error.posting.artifact=Error Posting Artifact  ",
        "PlasoIngestModule.create.artifacts.cancelled=Cancelled Plaso Artifact Creation ",
        "# {0} - file that events are from",
        "PlasoIngestModule.artifact.progress=Adding events to case: {0}"})
    private void createPlasoArtifacts(String plasoDb, DataSourceIngestModuleProgress statusHelper) {
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();

        String sqlStatement = "SELECT substr(filename,1) AS filename, "
                              + "   strftime('%s', datetime) AS epoch_date,"
                              + "   description, "
                              + "   source, "
                              + "   type, "
                              + "   sourcetype "
                              + " FROM log2timeline "
                              + " WHERE source NOT IN ('FILE'," // bad dates and duplicates with what we have.
                              + "'WEBHSIST') "
                              + "   AND sourcetype NOT IN ('UNKNOWN',"
                              + "'PE Import Time');";    // lots of bad dates //NON-NLS
        try (SQLiteDBConnect tempdbconnect = new SQLiteDBConnect("org.sqlite.JDBC", "jdbc:sqlite:" + plasoDb); //NON-NLS
                ResultSet resultSet = tempdbconnect.executeQry(sqlStatement)) {

            while (resultSet.next()) {
                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, Bundle.PlasoIngestModule_create_artifacts_cancelled()); //NON-NLS
                    MessageNotifyUtil.Message.info(Bundle.PlasoIngestModule_create_artifacts_cancelled());
                    return;
                }

                String currentFileName = resultSet.getString("filename"); //NON-NLS
                statusHelper.progress(Bundle.PlasoIngestModule_artifact_progress(currentFileName), 66);
                Content resolvedFile = getAbstractFile(currentFileName);
                if (resolvedFile == null) {
                    logger.log(Level.INFO, "File from Plaso output not found.  Associating with data source instead: {0}", currentFileName);//NON-NLS
                    resolvedFile = image;
                }
                String source = resultSet.getString("source");  //NON-NLS
                String sourceType = resultSet.getString("sourcetype");  //NON-NLS
                String description = resultSet.getString("description"); //NON-NLS
                String type = resultSet.getString("type");
                long eventType = findEventSubtype(source, currentFileName, type, sourceType); //NON-NLS

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_DATETIME, MODULE_NAME,
                                resultSet.getLong("epoch_date")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DESCRIPTION, MODULE_NAME,
                                description),
                        new BlackboardAttribute(
                                TSK_TL_EVENT_TYPE, MODULE_NAME,
                                eventType));

                try {
                    BlackboardArtifact bbart = resolvedFile.newArtifact(TSK_TL_EVENT);
                    bbart.addAttributes(bbattributes);
                    try {
                        /*
                         * Post the artifact which will index the artifact for
                         * keyword search, and fire an event to notify UI of
                         * this new artifact
                         */
                        blackboard.postArtifact(bbart, MODULE_NAME);
                    } catch (BlackboardException ex) {
                        logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_posting_artifact(), ex); //NON-NLS
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, Bundle.PlasoIngestModule_exception_adding_artifact(), ex);
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
        String filePath = path.getParent().toString().replaceAll("\\\\", "/");//NON-NLS
        if (filePath.endsWith("/") == false) {//NON-NLS
            filePath += "/";//NON-NLS
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

    private long findEventSubtype(String plasoSource, String plasoFileName, String plasoType, String plasoSourceType) {
        //These aren't actually used, but 
        if (plasoSource.matches(WEBHIST)) {
            if (plasoFileName.toLowerCase().contains(COOKIE)
                || plasoType.toLowerCase().contains(COOKIE)) {
                return EventType.WEB_COOKIE.getTypeID();
            }
            return EventType.WEB_HISTORY.getTypeID();
        }
        if (plasoSource.matches("EVT")
            || plasoSource.matches("LOG")) {//NON-NLS
            return EventType.LOG_ENTRY.getTypeID();
        }
        if (plasoSource.matches("REG")) {
            String plasoSourceTypeLower = plasoSourceType.toLowerCase();
            if (plasoSourceTypeLower.matches("unknown : usb entries")//NON-NLS
                || plasoSourceTypeLower.matches("unknown : usbstor entries")) {//NON-NLS

                return EventType.DEVICES_ATTACHED.getTypeID();
            }
            return EventType.REGISTRY.getTypeID();
        }
        return EventType.OTHER.getTypeID();
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
