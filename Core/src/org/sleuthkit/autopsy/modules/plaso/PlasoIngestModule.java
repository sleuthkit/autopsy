/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
import java.io.FileNotFoundException;
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
import java.util.Locale;
import static java.util.Objects.nonNull;
import java.util.concurrent.TimeUnit;
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
import org.sleuthkit.datamodel.TimelineEventType;

/**
 * Data source ingest module that runs Plaso against the image.
 */
public class PlasoIngestModule implements DataSourceIngestModule {

    private static final Logger logger = Logger.getLogger(PlasoIngestModule.class.getName());
    private static final String MODULE_NAME = PlasoModuleFactory.getModuleName();

    private static final String PLASO = "plaso"; //NON-NLS
    private static final String PLASO64 = "plaso-20180818-amd64";//NON-NLS
    private static final String PLASO32 = "plaso-20180818-win32";//NON-NLS
    private static final String LOG2TIMELINE_EXECUTABLE = "Log2timeline.exe";//NON-NLS
    private static final String PSORT_EXECUTABLE = "psort.exe";//NON-NLS
    private static final String COOKIE = "cookie";//NON-NLS
    private static final int LOG2TIMELINE_WORKERS = 2;
    private static final long TERMINATION_CHECK_INTERVAL = 5;
    private static final TimeUnit TERMINATION_CHECK_INTERVAL_UNITS = TimeUnit.SECONDS;

    private File log2TimeLineExecutable;
    private File psortExecutable;

    private final PlasoModuleSettings settings;
    private IngestJobContext context;
    private Case currentCase;
    private FileManager fileManager;

    private Image image;
    private AbstractFile previousFile = null; // cache used when looking up files in Autopsy DB

    PlasoIngestModule(PlasoModuleSettings settings) {
        this.settings = settings;
    }

    @NbBundle.Messages({
        "PlasoIngestModule.executable.not.found=Plaso Executable Not Found.",
        "PlasoIngestModule.requires.windows=Plaso module requires windows."})
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;

        if (false == PlatformUtil.isWindowsOS()) {
            throw new IngestModuleException(Bundle.PlasoIngestModule_requires_windows());
        }

        try {
            log2TimeLineExecutable = locateExecutable(LOG2TIMELINE_EXECUTABLE);
            psortExecutable = locateExecutable(PSORT_EXECUTABLE);
        } catch (FileNotFoundException exception) {
            logger.log(Level.WARNING, "Plaso executable not found.", exception); //NON-NLS
            throw new IngestModuleException(Bundle.PlasoIngestModule_executable_not_found(), exception);
        }

    }

    @NbBundle.Messages({
        "PlasoIngestModule.error.running.log2timeline=Error running log2timeline, see log file.",
        "PlasoIngestModule.error.running.psort=Error running Psort, see log file.",
        "PlasoIngestModule.error.creating.output.dir=Error creating Plaso module output directory.",
        "PlasoIngestModule.starting.log2timeline=Starting Log2timeline",
        "PlasoIngestModule.running.psort=Running Psort",
        "PlasoIngestModule.log2timeline.cancelled=Log2timeline run was canceled",
        "PlasoIngestModule.psort.cancelled=psort run was canceled",
        "PlasoIngestModule.bad.imageFile=Cannot find image file name and path",
        "PlasoIngestModule.completed=Plaso Processing Completed",
        "PlasoIngestModule.has.run=Plaso",
        "PlasoIngestModule.psort.fail=Plaso returned an error when sorting events.  Results are not complete.",
        "PlasoIngestModule.dataSource.not.an.image=Skipping non-disk image datasource"})
    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress statusHelper) {

        if (!(dataSource instanceof Image)) {
            IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                    Bundle.PlasoIngestModule_has_run(),
                    Bundle.PlasoIngestModule_dataSource_not_an_image());
            IngestServices.getInstance().postMessage(message);
            return ProcessResult.OK;
        } else {
            image = (Image) dataSource;

            statusHelper.switchToDeterminate(100);
            currentCase = Case.getCurrentCase();
            fileManager = currentCase.getServices().getFileManager();

            // Use Z here for timezone since the other formats can include a colon on some systems
            String currentTime = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss Z", Locale.US).format(System.currentTimeMillis());//NON-NLS
            Path moduleOutputPath = Paths.get(currentCase.getModuleDirectory(), PLASO, currentTime);
            try {
                Files.createDirectories(moduleOutputPath);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error creating Plaso module output directory.", ex); //NON-NLS
                return ProcessResult.ERROR;
            }

            // Run log2timeline
            logger.log(Level.INFO, "Starting Plaso Run.");//NON-NLS
            statusHelper.progress(Bundle.PlasoIngestModule_starting_log2timeline(), 0);
            ProcessBuilder log2TimeLineCommand = buildLog2TimeLineCommand(moduleOutputPath, image);
            try {
                Process log2TimeLineProcess = log2TimeLineCommand.start();
                try (BufferedReader log2TimeLineOutpout = new BufferedReader(new InputStreamReader(log2TimeLineProcess.getInputStream()))) {
                    L2TStatusProcessor statusReader = new L2TStatusProcessor(log2TimeLineOutpout, statusHelper, moduleOutputPath);
                    new Thread(statusReader, "log2timeline status reader").start();  //NON-NLS
                    ExecUtil.waitForTermination(LOG2TIMELINE_EXECUTABLE, log2TimeLineProcess, TERMINATION_CHECK_INTERVAL, TERMINATION_CHECK_INTERVAL_UNITS, new DataSourceIngestModuleProcessTerminator(context));
                    statusReader.cancel();
                }

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Log2timeline run was canceled"); //NON-NLS
                    return ProcessResult.OK;
                }
                if (Files.notExists(moduleOutputPath.resolve(PLASO))) {
                    logger.log(Level.WARNING, "Error running log2timeline: there was no storage file."); //NON-NLS
                    return ProcessResult.ERROR;
                }

                // sort the output
                statusHelper.progress(Bundle.PlasoIngestModule_running_psort(), 33);
                ProcessBuilder psortCommand = buildPsortCommand(moduleOutputPath);
                int result = ExecUtil.execute(psortCommand, new DataSourceIngestModuleProcessTerminator(context));
                if (result != 0) {
                    logger.log(Level.SEVERE, String.format("Error running Psort, error code returned %d", result)); //NON-NLS
                    MessageNotifyUtil.Notify.error(MODULE_NAME, Bundle.PlasoIngestModule_psort_fail());
                    return ProcessResult.ERROR;
                }

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "psort run was canceled"); //NON-NLS
                    return ProcessResult.OK;
                }
                Path plasoFile = moduleOutputPath.resolve("plasodb.db3");  //NON-NLS
                if (Files.notExists(plasoFile)) {
                    logger.log(Level.SEVERE, "Error running Psort: there was no sqlite db file."); //NON-NLS
                    return ProcessResult.ERROR;
                }

                // parse the output and make artifacts
                createPlasoArtifacts(plasoFile.toString(), statusHelper);

            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Error running Plaso.", ex);//NON-NLS
                return ProcessResult.ERROR;
            }

            IngestMessage message = IngestMessage.createMessage(IngestMessage.MessageType.DATA,
                    Bundle.PlasoIngestModule_has_run(),
                    Bundle.PlasoIngestModule_completed());
            IngestServices.getInstance().postMessage(message);
            return ProcessResult.OK;
        }
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
                "--workers", String.valueOf(LOG2TIMELINE_WORKERS),//NON-NLS
                moduleOutputPath.resolve(PLASO).toString(),
                image.getPaths()[0]
        );
        processBuilder.redirectError(moduleOutputPath.resolve("log2timeline_err.txt").toFile());  //NON-NLS
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

        processBuilder.redirectOutput(moduleOutputPath.resolve("psort_output.txt").toFile()); //NON-NLS
        processBuilder.redirectError(moduleOutputPath.resolve("psort_err.txt").toFile());  //NON-NLS
        return processBuilder;
    }

    private static File locateExecutable(String executableName) throws FileNotFoundException {
        String architectureFolder = PlatformUtil.is64BitOS() ? PLASO64 : PLASO32;
        String executableToFindName = Paths.get(PLASO, architectureFolder, executableName).toString();

        File exeFile = InstalledFileLocator.getDefault().locate(executableToFindName, PlasoIngestModule.class.getPackage().getName(), false);
        if (null == exeFile || exeFile.canExecute() == false) {
            throw new FileNotFoundException(executableName + " executable not found.");
        }
        return exeFile;
    }

    @NbBundle.Messages({
        "PlasoIngestModule.exception.posting.artifact=Exception Posting artifact.",
        "PlasoIngestModule.event.datetime=Event Date Time",
        "PlasoIngestModule.event.description=Event Description",
        "PlasoIngestModule.create.artifacts.cancelled=Cancelled Plaso Artifact Creation ",
        "# {0} - file that events are from",
        "PlasoIngestModule.artifact.progress=Adding events to case: {0}",
        "PlasoIngestModule.info.empty.database=Plaso database was empty.",})
    private void createPlasoArtifacts(String plasoDb, DataSourceIngestModuleProgress statusHelper) {
        Blackboard blackboard = currentCase.getSleuthkitCase().getBlackboard();

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

            boolean dbHasData = false;

            while (resultSet.next()) {
                dbHasData = true;

                if (context.dataSourceIngestIsCancelled()) {
                    logger.log(Level.INFO, "Cancelled Plaso Artifact Creation."); //NON-NLS
                    return;
                }

                String currentFileName = resultSet.getString("filename"); //NON-NLS
                statusHelper.progress(Bundle.PlasoIngestModule_artifact_progress(currentFileName), 66);
                Content resolvedFile = getAbstractFile(currentFileName);
                if (resolvedFile == null) {
                    logger.log(Level.INFO, "File {0} from Plaso output not found in case.  Associating it with the data source instead.", currentFileName);//NON-NLS
                    resolvedFile = image;
                }

                String description = resultSet.getString("description");
                TimelineEventType eventType = findEventSubtype(currentFileName, resultSet);

                // If the description is empty use the event type display name
                // as the description.
                if (description == null || description.isEmpty()) {
                    if (eventType != TimelineEventType.STANDARD_ARTIFACT_CATCH_ALL) {
                        description = eventType.getDisplayName();
                    } else {
                        continue;
                    }
                }

                Collection<BlackboardAttribute> bbattributes = Arrays.asList(
                        new BlackboardAttribute(
                                TSK_DATETIME, MODULE_NAME,
                                resultSet.getLong("epoch_date")), //NON-NLS
                        new BlackboardAttribute(
                                TSK_DESCRIPTION, MODULE_NAME,
                                description),//NON-NLS
                        new BlackboardAttribute(
                                TSK_TL_EVENT_TYPE, MODULE_NAME,
                                eventType.getTypeID()));

                try {
                    BlackboardArtifact bbart = resolvedFile.newDataArtifact(new BlackboardArtifact.Type(TSK_TL_EVENT), bbattributes);
                    try {
                        /*
                         * Post the artifact which will index the artifact for
                         * keyword search, and fire an event to notify UI of
                         * this new artifact
                         */
                        blackboard.postArtifact(bbart, MODULE_NAME, context.getJobId());
                    } catch (BlackboardException ex) {
                        logger.log(Level.SEVERE, "Error Posting Artifact.", ex);//NON-NLS
                    }
                } catch (TskCoreException ex) {
                    logger.log(Level.SEVERE, "Exception Adding Artifact.", ex);//NON-NLS
                }
            }

            // Check if there is data the db
            if (!dbHasData) {
                logger.log(Level.INFO, String.format("PlasoDB was empty: %s", plasoDb));
                MessageNotifyUtil.Notify.info(MODULE_NAME, Bundle.PlasoIngestModule_info_empty_database());
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error while trying to read into a sqlite db.", ex);//NON-NLS
        }
    }

    private AbstractFile getAbstractFile(String file) {

        Path path = Paths.get(file);
        String fileName = path.getFileName().toString();
        String filePath = path.getParent().toString().replaceAll("\\\\", "/");//NON-NLS
        if (filePath.endsWith("/") == false) {//NON-NLS
            filePath += "/";//NON-NLS
        }

        // check the cached file
        //TODO: would we reduce 'cache misses' if we retrieved the events sorted by file?  Is that overhead worth it?
        if (previousFile != null
                && previousFile.getName().equalsIgnoreCase(fileName)
                && previousFile.getParentPath().equalsIgnoreCase(filePath)) {
            return previousFile;

        }
        try {
            List<AbstractFile> abstractFiles = fileManager.findFiles(fileName, filePath);
            if (abstractFiles.size() == 1) {// TODO: why do we bother with this check.  also we don't cache the file...
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
            logger.log(Level.SEVERE, "Exception finding file.", ex);
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
    private TimelineEventType findEventSubtype(String fileName, ResultSet row) throws SQLException {
        switch (row.getString("source")) {
            case "WEBHIST":  //These shouldn't actually be present, but keeping the logic just in case...
                if (fileName.toLowerCase().contains(COOKIE)
                        || row.getString("type").toLowerCase().contains(COOKIE)) {//NON-NLS

                    return TimelineEventType.WEB_COOKIE;
                } else {
                    return TimelineEventType.WEB_HISTORY;
                }
            case "EVT":
            case "LOG":
                return TimelineEventType.LOG_ENTRY;
            case "REG":
                switch (row.getString("sourcetype").toLowerCase()) {//NON-NLS
                    case "unknown : usb entries":
                    case "unknown : usbstor entries":
                        return TimelineEventType.DEVICES_ATTACHED;
                    default:
                        return TimelineEventType.REGISTRY;
                }
            default:
                return TimelineEventType.STANDARD_ARTIFACT_CATCH_ALL;
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
        volatile private boolean cancelled = false;
        private final Path outputPath;

        private L2TStatusProcessor(BufferedReader log2TimeLineOutpout, DataSourceIngestModuleProgress statusHelper, Path outputPath) throws IOException {
            this.log2TimeLineOutpout = log2TimeLineOutpout;
            this.statusHelper = statusHelper;
            this.outputPath = outputPath;
        }

        @Override
        public void run() {
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath.resolve("log2timeline_output.txt"));) {//NON-NLS
                String line = log2TimeLineOutpout.readLine();
                while (cancelled == false && nonNull(line)) {
                    statusHelper.progress(line);
                    writer.write(line);
                    writer.newLine();
                    line = log2TimeLineOutpout.readLine();
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
