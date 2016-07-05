/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.Cursor;
import java.awt.Frame;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ReportAddedEvent;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.SleuthkitJNI.CaseDbHandle.AddImageProcess;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Stores all information for a given case. Only a single case can currently be
 * open at a time. Use getCurrentCase() to retrieve the object for the current
 * case.
 */
public class Case implements SleuthkitCase.ErrorObserver {

    private static final String autopsyVer = Version.getVersion(); // current version of autopsy. Change it when the version is changed
    private static final String EVENT_CHANNEL_NAME = "%s-Case-Events"; //NON-NLS
    private static String appName = null;
    volatile private IntervalErrorReportData tskErrorReporter = null;
    private static final int MIN_SECONDS_BETWEEN_ERROR_REPORTS = 60; // No less than 60 seconds between warnings for errors
    private static final int MAX_SANITIZED_NAME_LENGTH = 47;

    /**
     * Name for the property that determines whether to show the dialog at
     * startup
     */
    public static final String propStartup = "LBL_StartupDialog"; //NON-NLS

    /**
     * The event publisher is static so that subscribers only have to subscribe
     * once to receive events for all cases.
     */
    private static final AutopsyEventPublisher eventPublisher = new AutopsyEventPublisher();

    /**
     * Events that the case module will fire. Event listeners can get the event
     * name by using String returned by toString() method on a specific event.
     */
    public enum Events {

        /**
         * Property name that indicates the name of the current case has
         * changed. The old value is the old case name, the new value is the new
         * case name.
         */
        NAME,
        /**
         * Property name that indicates the number of the current case has
         * changed. Fired with the case number is changed. The value is an int:
         * the number of the case. -1 is used for no case number set.
         */
        NUMBER,
        /**
         * Property name that indicates the examiner of the current case has
         * changed. Fired with the case examiner is changed. The value is a
         * String: the name of the examiner. The empty string ("") is used for
         * no examiner set.
         */
        EXAMINER,
        /**
         * Property name used for a property change event that indicates a new
         * data source (image, local/logical file or local disk) is being added
         * to the current case. The old and new values of the
         * PropertyChangeEvent are null - cast the PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent to
         * access event data.
         */
        ADDING_DATA_SOURCE,
        /**
         * Property name used for a property change event that indicates a
         * failure adding a new data source (image, local/logical file or local
         * disk) to the current case. The old and new values of the
         * PropertyChangeEvent are null - cast the PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent
         * to access event data.
         */
        ADDING_DATA_SOURCE_FAILED,
        /**
         * Property name that indicates a new data source (image, disk or local
         * file) has been added to the current case. The new value is the
         * newly-added instance of the new data source, and the old value is
         * always null.
         */
        DATA_SOURCE_ADDED,
        /**
         * Property name that indicates a data source has been removed from the
         * current case. The "old value" is the (int) content ID of the data
         * source that was removed, the new value is the instance of the data
         * source.
         */
        DATA_SOURCE_DELETED,
        /**
         * Property name that indicates the currently open case has changed.
         * When a case is opened, the "new value" will be an instance of the
         * opened Case object and the "old value" will be null. When a case is
         * closed, the "new value" will be null and the "old value" will be the
         * instance of the Case object being closed.
         */
        CURRENT_CASE,
        /**
         * Name for property change events fired when a report is added to the
         * case. The old value supplied by the event object is null and the new
         * value is a reference to a Report object representing the new report.
         */
        REPORT_ADDED,
        /**
         * Name for the property change event when a report is deleted from the
         * case. Both the old value and the new value supplied by the event
         * object are null.
         */
        REPORT_DELETED,
        /**
         * Property name for the event when a new BlackBoardArtifactTag is
         * added. The new value is tag added, the old value is empty
         */
        BLACKBOARD_ARTIFACT_TAG_ADDED,
        /**
         * Property name for the event when a new BlackBoardArtifactTag is
         * deleted. The new value is empty, the old value is a
         * {@link BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo}
         * object with info about the deleted tag.
         */
        BLACKBOARD_ARTIFACT_TAG_DELETED,
        /**
         * Property name for the event when a new ContentTag is added. The new
         * value is tag added, the old value is empty
         */
        CONTENT_TAG_ADDED,
        /**
         * Property name for the event when a new ContentTag is deleted. The new
         * value is empty, the old value is a
         * {@link ContentTagDeletedEvent.DeletedContentTagInfo} object with info
         * about the deleted tag.
         */
        CONTENT_TAG_DELETED;
    };

    /**
     * This enum describes the type of case, either single-user (standalone) or
     * multi-user (using PostgreSql)
     */
    @NbBundle.Messages({"Case_caseType_singleUser=Single-user case",
        "Case_caseType_multiUser=Multi-user case"})
    public enum CaseType {

        SINGLE_USER_CASE("Single-user case"), //NON-NLS
        MULTI_USER_CASE("Multi-user case");   //NON-NLS

        private final String caseType;

        private CaseType(String s) {
            caseType = s;
        }

        public boolean equalsName(String otherType) {
            return (otherType == null) ? false : caseType.equals(otherType);
        }

        public static CaseType fromString(String typeName) {
            if (typeName != null) {
                for (CaseType c : CaseType.values()) {
                    if (typeName.equalsIgnoreCase(c.caseType)) {
                        return c;
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return caseType;
        }

        String getLocalizedDisplayName() {
            if (fromString(caseType) == SINGLE_USER_CASE) {
                return Bundle.Case_caseType_singleUser();
            } else {
                return Bundle.Case_caseType_multiUser();
            }
        }
    };

    private final CaseMetadata caseMetadata;
    private final SleuthkitCase db;
    // Track the current case (only set with changeCase() method)
    private static Case currentCase = null;
    private final Services services;
    private static final Logger logger = Logger.getLogger(Case.class.getName());
    private final static String CACHE_FOLDER = "Cache"; //NON-NLS
    private final static String EXPORT_FOLDER = "Export"; //NON-NLS
    private final static String LOG_FOLDER = "Log"; //NON-NLS
    final static String MODULE_FOLDER = "ModuleOutput"; //NON-NLS
    private final static String REPORTS_FOLDER = "Reports"; //NON-NLS
    private final static String TEMP_FOLDER = "Temp"; //NON-NLS

    // we cache if the case has data in it yet since a few places ask for it and we dont' need to keep going to DB
    private boolean hasData = false;

    private CollaborationMonitor collaborationMonitor;

    /**
     * Constructor for the Case class
     */
    private Case(CaseMetadata caseMetadata, SleuthkitCase db) {
        this.caseMetadata = caseMetadata;
        this.db = db;
        this.services = new Services(db);
    }

    /**
     * Gets the currently opened case, if there is one.
     *
     * @return the current open case
     *
     * @throws IllegalStateException if there is no case open.
     */
    public static Case getCurrentCase() {
        if (currentCase != null) {
            return currentCase;
        } else {
            throw new IllegalStateException(NbBundle.getMessage(Case.class, "Case.getCurCase.exception.noneOpen"));
        }
    }

    /**
     * Check if case is currently open
     *
     * @return true if case is open
     */
    public static boolean isCaseOpen() {
        return currentCase != null;
    }

    /**
     * Updates the current case to the given case and fires off the appropriate
     * property-change
     *
     * @param newCase the new current case or null if case is being closed
     *
     */
    private static void changeCase(Case newCase) {
        // close the existing case
        Case oldCase = Case.currentCase;
        Case.currentCase = null;
        if (oldCase != null) {
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            });
            IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);
            doCaseChange(null); //closes windows, etc   
            if (null != oldCase.tskErrorReporter) {
                oldCase.tskErrorReporter.shutdown(); // stop listening for TSK errors for the old case
                oldCase.tskErrorReporter = null;
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), oldCase, null));
            if (CaseType.MULTI_USER_CASE == oldCase.getCaseType()) {
                if (null != oldCase.collaborationMonitor) {
                    oldCase.collaborationMonitor.shutdown();
                }
                eventPublisher.closeRemoteEventChannel();
            }
        }

        if (newCase != null) {
            currentCase = newCase;
            Logger.setLogDirectory(currentCase.getLogDirectoryPath());
            // sanity check
            if (null != currentCase.tskErrorReporter) {
                currentCase.tskErrorReporter.shutdown();
            }
            // start listening for TSK errors for the new case
            currentCase.tskErrorReporter = new IntervalErrorReportData(currentCase, MIN_SECONDS_BETWEEN_ERROR_REPORTS,
                    NbBundle.getMessage(Case.class, "IntervalErrorReport.ErrorText"));
            doCaseChange(currentCase);
            SwingUtilities.invokeLater(() -> {
                RecentCases.getInstance().addRecentCase(currentCase.getName(), currentCase.getCaseMetadata().getFilePath().toString()); // update the recent cases
            });
            if (CaseType.MULTI_USER_CASE == newCase.getCaseType()) {
                try {
                    /**
                     * Use the text index name as the remote event channel name
                     * prefix since it is unique, the same as the case database
                     * name for a multiuser case, and is readily available
                     * through the Case.getTextIndexName() API.
                     */
                    eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, newCase.getTextIndexName()));
                    currentCase.collaborationMonitor = new CollaborationMonitor();
                } catch (AutopsyEventException | CollaborationMonitor.CollaborationMonitorException ex) {
                    logger.log(Level.SEVERE, "Failed to setup for collaboration", ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.Title"), NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.ErrMsg"));
                }
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));

        } else {
            Logger.setLogDirectory(PlatformUtil.getLogDirectory());
        }
        SwingUtilities.invokeLater(() -> {
            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        });
    }

    @Override
    public void receiveError(String context, String errorMessage) {
        /*
         * NOTE: We are accessing tskErrorReporter from two different threads.
         * This is ok as long as we only read the value of tskErrorReporter
         * because tskErrorReporter is declared as volatile.
         */
        if (null != tskErrorReporter) {
            tskErrorReporter.addProblems(context, errorMessage);
        }
    }

    AddImageProcess makeAddImageProcess(String timezone, boolean processUnallocSpace, boolean noFatOrphans) {
        return this.db.makeAddImageProcess(timezone, processUnallocSpace, noFatOrphans);
    }

    /**
     * Creates a single-user new case.
     *
     * @param caseDir    The full path of the case directory. It will be created
     *                   if it doesn't already exist; if it exists, it should
     *                   have been created using Case.createCaseDirectory() to
     *                   ensure that the required sub-directories aere created.
     * @param caseName   The name of case.
     * @param caseNumber The case number, can be the empty string.
     * @param examiner   The examiner to associate with the case, can be the
     *                   empty string.
     *
     * @throws CaseActionException if there is a problem creating the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception. If so,
     *                             CaseActionException.getCause will return a
     *                             Throwable (null otherwise).
     */
    public static void create(String caseDir, String caseName, String caseNumber, String examiner) throws CaseActionException {
        create(caseDir, caseName, caseNumber, examiner, CaseType.SINGLE_USER_CASE);
    }

    /**
     * Creates a new case.
     *
     * @param caseDir    The full path of the case directory. It will be created
     *                   if it doesn't already exist; if it exists, it should
     *                   have been created using Case.createCaseDirectory() to
     *                   ensure that the required sub-directories aere created.
     * @param caseName   The name of case.
     * @param caseNumber The case number, can be the empty string.
     * @param examiner   The examiner to associate with the case, can be the
     *                   empty string.
     * @param caseType   The type of case (single-user or multi-user). The
     *                   exception will have a user-friendly message and may be
     *                   a wrapper for a lower-level exception. If so,
     *                   CaseActionException.getCause will return a Throwable
     *                   (null otherwise).
     *
     * @throws CaseActionException if there is a problem creating the case.
     */
    @Messages({"Case.creationException=Could not create case: failed to make metadata file."})
    public static void create(String caseDir, String caseName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
        logger.log(Level.INFO, "Creating case with case directory {0}, caseName {1}", new Object[]{caseDir, caseName}); //NON-NLS

        /*
         * Create case directory if it doesn't already exist.
         */
        if (new File(caseDir).exists() == false) {
            Case.createCaseDirectory(caseDir, caseType);
        }

        /*
         * Sanitize the case name, create a unique keyword search index name,
         * and create a standard (single-user) or unique (multi-user) case
         * database name.
         */
        String santizedCaseName = sanitizeCaseName(caseName);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        String indexName = santizedCaseName + "_" + dateFormat.format(date);
        String dbName = null;
        if (caseType == CaseType.SINGLE_USER_CASE) {
            dbName = caseDir + File.separator + "autopsy.db"; //NON-NLS
        } else if (caseType == CaseType.MULTI_USER_CASE) {
            dbName = indexName;
        }

        /*
         * Create the case metadata (.aut) file.
         */
        CaseMetadata metadata;
        try {
            metadata = new CaseMetadata(caseDir, caseType, caseName, caseNumber, examiner, dbName, indexName);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_creationException(), ex);
        }

        /*
         * Create the case database.
         */
        SleuthkitCase db = null;
        try {
            if (caseType == CaseType.SINGLE_USER_CASE) {
                db = SleuthkitCase.newCase(dbName);
            } else if (caseType == CaseType.MULTI_USER_CASE) {
                db = SleuthkitCase.newCase(dbName, UserPreferences.getDatabaseConnectionInfo(), caseDir);
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error creating a case %s in %s ", caseName, caseDir), ex); //NON-NLS
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            });
            /*
             * SleuthkitCase.newCase throws TskCoreExceptions with user-friendly
             * messages, so propagate the exception message.
             */
            throw new CaseActionException(ex.getMessage(), ex); //NON-NLS
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            });
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex);
        }

        Case newCase = new Case(metadata, db);
        changeCase(newCase);
    }

    /**
     * Sanitize the case name for PostgreSQL database, Solr cores, and ActiveMQ
     * topics. Makes it plain-vanilla enough that each item should be able to
     * use it.
     *
     * Sanitize the PostgreSQL/Solr core, and ActiveMQ name by excluding:
     * Control characters Non-ASCII characters Various others shown below
     *
     * Solr:
     * http://stackoverflow.com/questions/29977519/what-makes-an-invalid-core-name
     * may not be / \ :
     *
     * ActiveMQ:
     * http://activemq.2283324.n4.nabble.com/What-are-limitations-restrictions-on-destination-name-td4664141.html
     * may not be ?
     *
     * PostgreSQL:
     * http://www.postgresql.org/docs/9.4/static/sql-syntax-lexical.html 63
     * chars max, must start with a-z or _ following chars can be letters _ or
     * digits
     *
     * SQLite: Uses autopsy.db for the database name follows Windows naming
     * convention
     *
     * @param caseName The name of the case as typed in by the user
     *
     * @return the sanitized case name to use for Database, Solr, and ActiveMQ
     */
    static String sanitizeCaseName(String caseName) {

        String result;

        // Remove all non-ASCII characters
        result = caseName.replaceAll("[^\\p{ASCII}]", "_"); //NON-NLS

        // Remove all control characters
        result = result.replaceAll("[\\p{Cntrl}]", "_"); //NON-NLS

        // Remove / \ : ? space ' "
        result = result.replaceAll("[ /?:'\"\\\\]", "_"); //NON-NLS

        // Make it all lowercase
        result = result.toLowerCase();

        // Must start with letter or underscore for PostgreSQL. If not, prepend an underscore.
        if (result.length() > 0 && !(Character.isLetter(result.codePointAt(0))) && !(result.codePointAt(0) == '_')) {
            result = "_" + result;
        }

        // Chop to 63-16=47 left (63 max for PostgreSQL, taking 16 for the date _20151225_123456)
        if (result.length() > MAX_SANITIZED_NAME_LENGTH) {
            result = result.substring(0, MAX_SANITIZED_NAME_LENGTH);
        }

        if (result.isEmpty()) {
            result = "case"; //NON-NLS
        }

        return result;
    }

    /**
     * Opens an existing case.
     *
     * @param caseMetadataFilePath The path of the case metadata file.
     *
     * @throws CaseActionException if there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception. If so,
     *                             CaseActionException.getCause will return a
     *                             Throwable (null otherwise).
     */
    public static void open(String caseMetadataFilePath) throws CaseActionException {
        logger.log(Level.INFO, "Opening case with metadata file path {0}", caseMetadataFilePath); //NON-NLS

        /*
         * Verify the extension of the case metadata file.
         */
        if (!caseMetadataFilePath.endsWith(CaseMetadata.getFileExtension())) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.open.exception.checkFile.msg", CaseMetadata.getFileExtension()));
        }

        try {
            /*
             * Get the case metadata required to open the case database.
             */
            CaseMetadata metadata = new CaseMetadata(Paths.get(caseMetadataFilePath));
            CaseType caseType = metadata.getCaseType();

            /*
             * Open the case database.
             */
            SleuthkitCase db;
            String caseDir;
            if (caseType == CaseType.SINGLE_USER_CASE) {
                String dbPath = metadata.getCaseDatabasePath(); //NON-NLS
                db = SleuthkitCase.openCase(dbPath);
                caseDir = new File(dbPath).getParent();
            } else {
                if (!UserPreferences.getIsMultiUserModeEnabled()) {
                    throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.open.exception.multiUserCaseNotEnabled"));
                }
                try {
                    db = SleuthkitCase.openCase(metadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());
                } catch (UserPreferencesException ex) {
                    throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex);
                }
            }

            /*
             * Check for the presence of the UI and do things that can only be
             * done with user interaction.
             */
            if (RuntimeProperties.coreComponentsAreActive()) {
                /*
                 * If the case database was upgraded for a new schema, notify
                 * the user.
                 */
                if (null != db.getBackupDatabasePath()) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(
                                WindowManager.getDefault().getMainWindow(),
                                NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.msg", db.getBackupDatabasePath()),
                                NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.title"),
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                }

                /*
                 * Look for the files for the data sources listed in the case
                 * database and give the user the opportunity to locate any that
                 * are missing.
                 */
                Map<Long, String> imgPaths = getImagePaths(db);
                for (Map.Entry<Long, String> entry : imgPaths.entrySet()) {
                    long obj_id = entry.getKey();
                    String path = entry.getValue();
                    boolean fileExists = (pathExists(path) || driveExists(path));
                    if (!fileExists) {
                        int ret = JOptionPane.showConfirmDialog(
                                WindowManager.getDefault().getMainWindow(),
                                NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.msg", getAppName(), path),
                                NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.title"),
                                JOptionPane.YES_NO_OPTION);
                        if (ret == JOptionPane.YES_OPTION) {
                            MissingImageDialog.makeDialog(obj_id, db);
                        } else {
                            logger.log(Level.WARNING, "Selected image files don't match old files!"); //NON-NLS
                        }
                    }
                }
            }
            Case openedCase = new Case(metadata, db);
            changeCase(openedCase);

        } catch (CaseMetadataException ex) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.metaDataFileCorrupt.exception.msg"), ex); //NON-NLS
        } catch (TskCoreException ex) {
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            });
            /*
             * SleuthkitCase.openCase throws TskCoreExceptions with
             * user-friendly messages, so propagate the exception message.
             */
            throw new CaseActionException(ex.getMessage(), ex);
        }
    }

    static Map<Long, String> getImagePaths(SleuthkitCase db) { //TODO: clean this up
        Map<Long, String> imgPaths = new HashMap<>();
        try {
            Map<Long, List<String>> imgPathsList = db.getImagePaths();
            for (Map.Entry<Long, List<String>> entry : imgPathsList.entrySet()) {
                if (entry.getValue().size() > 0) {
                    imgPaths.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting image paths", ex); //NON-NLS
        }
        return imgPaths;
    }

    /**
     * Adds an image to the current case after it has been added to the DB.
     * Sends out event and reopens windows if needed.
     *
     * @param imgPath  The path of the image file.
     * @param imgId    The ID of the image.
     * @param timeZone The time zone of the image.
     *
     * @deprecated As of release 4.0
     */
    @Deprecated
    public Image addImage(String imgPath, long imgId, String timeZone) throws CaseActionException {
        try {
            Image newDataSource = db.getImageById(imgId);
            notifyDataSourceAdded(newDataSource, UUID.randomUUID());
            return newDataSource;
        } catch (Exception ex) {
            throw new CaseActionException(NbBundle.getMessage(this.getClass(), "Case.addImg.exception.msg"), ex);
        }
    }

    /**
     * Finishes adding new local data source to the case. Sends out event and
     * reopens windows if needed.
     *
     * @param newDataSource new data source added
     *
     * @deprecated As of release 4.0, replaced by {@link #notifyAddingDataSource(java.util.UUID) and
     * {@link #notifyDataSourceAdded(org.sleuthkit.datamodel.Content, java.util.UUID) and
     * {@link #notifyFailedAddingDataSource(java.util.UUID)}
     */
    @Deprecated
    void addLocalDataSource(Content newDataSource) {
        notifyDataSourceAdded(newDataSource, UUID.randomUUID());
    }

    /**
     * Notifies case event subscribers (property change listeners) that a data
     * source is being added to the case database.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param dataSourceId A unique identifier for the data source. This UUID
     *                     should be used to call notifyNewDataSource() after
     *                     the data source is added.
     */
    public void notifyAddingDataSource(UUID dataSourceId) {
        eventPublisher.publish(new AddingDataSourceEvent(dataSourceId));
    }

    /**
     * Notifies case event subscribers (property change listeners) that a data
     * source failed to be added to the case database.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param dataSourceId A unique identifier for the data source.
     */
    public void notifyFailedAddingDataSource(UUID dataSourceId) {
        eventPublisher.publish(new AddingDataSourceFailedEvent(dataSourceId));
    }

    /**
     * Notifies case event subscribers (property change listeners) that a data
     * source is being added to the case database.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newDataSource New data source added.
     * @param dataSourceId  A unique identifier for the data source. Should be
     *                      the same UUID used to call
     *                      notifyAddingNewDataSource() when the process of
     *                      adding the data source began.
     */
    public void notifyDataSourceAdded(Content newDataSource, UUID dataSourceId) {
        eventPublisher.publish(new DataSourceAddedEvent(newDataSource, dataSourceId));
    }

    /**
     * Notifies the UI that a new ContentTag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new ContentTag added
     */
    public void notifyContentTagAdded(ContentTag newTag) {
        eventPublisher.publish(new ContentTagAddedEvent(newTag));
    }

    /**
     * Notifies the UI that a ContentTag has been deleted.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param deletedTag ContentTag deleted
     */
    public void notifyContentTagDeleted(ContentTag deletedTag) {
        eventPublisher.publish(new ContentTagDeletedEvent(deletedTag));
    }

    /**
     * Notifies the UI that a new BlackboardArtifactTag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new BlackboardArtifactTag added
     */
    public void notifyBlackBoardArtifactTagAdded(BlackboardArtifactTag newTag) {
        eventPublisher.publish(new BlackBoardArtifactTagAddedEvent(newTag));
    }

    /**
     * Notifies the UI that a BlackboardArtifactTag has been deleted.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param deletedTag BlackboardArtifactTag deleted
     */
    public void notifyBlackBoardArtifactTagDeleted(BlackboardArtifactTag deletedTag) {
        eventPublisher.publish(new BlackBoardArtifactTagDeletedEvent(deletedTag));
    }

    /**
     * @return The Services object for this case.
     */
    public Services getServices() {
        return services;
    }

    /**
     * Get the underlying SleuthkitCase instance from the Sleuth Kit bindings
     * library.
     *
     * @return
     */
    public SleuthkitCase getSleuthkitCase() {
        return this.db;
    }

    /**
     * Closes this case. This methods close the xml and clear all the fields.
     */
    public void closeCase() throws CaseActionException {
        changeCase(null);
        try {
            services.close();
            this.db.close();
        } catch (Exception e) {
            throw new CaseActionException(NbBundle.getMessage(this.getClass(), "Case.closeCase.exception.msg"), e);
        }
    }

    /**
     * Delete this case. This methods delete all folders and files of this case.
     *
     * @param caseDir case dir to delete
     *
     * @throws CaseActionException exception throw if case could not be deleted
     */
    void deleteCase(File caseDir) throws CaseActionException {
        logger.log(Level.INFO, "Deleting case.\ncaseDir: {0}", caseDir); //NON-NLS

        try {
            boolean result = deleteCaseDirectory(caseDir); // delete the directory

            RecentCases.getInstance().removeRecentCase(this.caseMetadata.getCaseName(), this.caseMetadata.getFilePath().toString()); // remove it from the recent case
            Case.changeCase(null);
            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(this.getClass(), "Case.deleteCase.exception.msg", caseDir));
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error deleting the current case dir: " + caseDir, ex); //NON-NLS
            throw new CaseActionException(
                    NbBundle.getMessage(this.getClass(), "Case.deleteCase.exception.msg2", caseDir), ex);
        }
    }

    /**
     * Updates the case name.
     *
     * This should not be called from the EDT.
     *
     * @param oldCaseName the old case name that wants to be updated
     * @param oldPath     the old path that wants to be updated
     * @param newCaseName the new case name
     * @param newPath     the new path
     */
    void updateCaseName(String oldCaseName, String oldPath, String newCaseName, String newPath) throws CaseActionException {
        try {
            getCaseMetadata().setCaseName(newCaseName); // set the case
            caseMetadata.setCaseName(newCaseName); // change the local value
            eventPublisher.publish(new AutopsyEvent(Events.NAME.toString(), oldCaseName, newCaseName));
            SwingUtilities.invokeLater(() -> {
                try {
                    RecentCases.getInstance().updateRecentCase(oldCaseName, oldPath, newCaseName, newPath); // update the recent case 
                    updateMainWindowTitle(newCaseName);
                } catch (Exception e) {
                    Logger.getLogger(Case.class.getName()).log(Level.WARNING, "Error: problem updating case name.", e); //NON-NLS
                }
            });
        } catch (Exception e) {
            throw new CaseActionException(NbBundle.getMessage(this.getClass(), "Case.updateCaseName.exception.msg"), e);
        }
    }

    /**
     * Checks whether there is a current case open.
     *
     * @return True if a case is open.
     */
    public static boolean existsCurrentCase() {
        return currentCase != null;
    }

    /**
     * Returns the current version of Autopsy
     *
     * @return autopsyVer
     */
    public static String getAutopsyVersion() {
        return autopsyVer;
    }

    /**
     * @return the caseMetadata
     */
    CaseMetadata getCaseMetadata() {
        return caseMetadata;
    }

    /**
     * Gets the application name
     *
     * @return appName
     */
    public static String getAppName() {
        if ((appName == null) || appName.equals("")) {
            appName = WindowManager.getDefault().getMainWindow().getTitle();
        }
        return appName;
    }

    /**
     * Gets the case name
     *
     * @return name
     */
    public String getName() {
        return caseMetadata.getCaseName();
    }

    /**
     * Gets the case number
     *
     * @return number
     */
    public String getNumber() {
        return caseMetadata.getCaseNumber();
    }

    /**
     * Gets the Examiner name
     *
     * @return examiner
     */
    public String getExaminer() {
        return caseMetadata.getExaminer();
    }

    /**
     * Gets the case directory path
     *
     * @return caseDirectoryPath
     */
    public String getCaseDirectory() {
        return caseMetadata.getCaseDirectory();
    }

    /**
     * Get the case type.
     *
     * @return
     */
    public CaseType getCaseType() {
        return this.getCaseMetadata().getCaseType();
    }

    /**
     * Gets the full path to the temp directory of this case. Will create it if
     * it does not already exist.
     *
     * @return tempDirectoryPath
     */
    public String getTempDirectory() {
        return getDirectory(TEMP_FOLDER);
    }

    /**
     * Gets the full path to the cache directory of this case. Will create it if
     * it does not already exist.
     *
     * @return cacheDirectoryPath
     */
    public String getCacheDirectory() {
        return getDirectory(CACHE_FOLDER);
    }

    /**
     * Gets the full path to the export directory of this case. Will create it
     * if it does not already exist.
     *
     * @return exportDirectoryPath
     */
    public String getExportDirectory() {
        return getDirectory(EXPORT_FOLDER);
    }

    /**
     * Gets the full path to the log directory of this case. Will create it if
     * it does not already exist.
     *
     * @return logDirectoryPath
     */
    public String getLogDirectoryPath() {
        return getDirectory(LOG_FOLDER);
    }

    /**
     * Get the reports directory path where modules should save their reports.
     * Will create it if it does not already exist.
     *
     * @return absolute path to the report output directory
     */
    public String getReportDirectory() {
        return getDirectory(REPORTS_FOLDER);
    }

    /**
     * Get module output directory path where modules should save their
     * permanent data.
     *
     * @return absolute path to the module output directory
     */
    public String getModuleDirectory() {
        return getDirectory(MODULE_FOLDER);
    }

    /**
     * Get the output directory path where modules should save their permanent
     * data. If single-user case, the directory is a subdirectory of the case
     * directory. If multi-user case, the directory is a subdirectory of
     * HostName, which is a subdirectory of the case directory.
     *
     * @return the path to the host output directory
     */
    public String getOutputDirectory() {
        return getHostDirectory();
    }

    /**
     * Get the specified directory path, create it if it does not already exist.
     *
     * @return absolute path to the directory
     */
    private String getDirectory(String input) {
        File theDirectory = new File(getHostDirectory() + File.separator + input);
        if (!theDirectory.exists()) {  // Create it if it doesn't exist already.
            theDirectory.mkdirs();
        }
        return theDirectory.toString();
    }

    /**
     * Get relative (with respect to case dir) module output directory path
     * where modules should save their permanent data. The directory is a
     * subdirectory of this case dir.
     *
     * @return relative path to the module output dir
     */
    public String getModuleOutputDirectoryRelativePath() {
        Path thePath;
        if (getCaseType() == CaseType.MULTI_USER_CASE) {
            thePath = Paths.get(NetworkUtils.getLocalHostName(), MODULE_FOLDER);
        } else {
            thePath = Paths.get(MODULE_FOLDER);
        }
        // Do not autocreate this relative path. It will have already been
        // created when the case was made.
        return thePath.toString();
    }

    /**
     * Get the host output directory path where modules should save their
     * permanent data. If single-user case, the directory is a subdirectory of
     * the case directory. If multi-user case, the directory is a subdirectory
     * of the hostName, which is a subdirectory of the case directory.
     *
     * @return the path to the host output directory
     */
    private String getHostDirectory() {
        String caseDirectory = getCaseDirectory();
        Path hostPath;
        if (getCaseMetadata().getCaseType() == CaseType.MULTI_USER_CASE) {
            hostPath = Paths.get(caseDirectory, NetworkUtils.getLocalHostName());
        } else {
            hostPath = Paths.get(caseDirectory);
        }
        if (!hostPath.toFile().exists()) {
            hostPath.toFile().mkdirs();
        }
        return hostPath.toString();
    }

    /**
     * Get module output directory path where modules should save their
     * permanent data.
     *
     * @return absolute path to the module output directory
     *
     * @deprecated Use getModuleDirectory() instead.
     */
    @Deprecated
    public String getModulesOutputDirAbsPath() {
        return getModuleDirectory();
    }

    /**
     * Get relative (with respect to case dir) module output directory path
     * where modules should save their permanent data. The directory is a
     * subdirectory of this case dir.
     *
     * @return relative path to the module output dir
     *
     * @deprecated Use getModuleOutputDirectoryRelativePath() instead
     */
    @Deprecated
    public static String getModulesOutputDirRelPath() {
        return "ModuleOutput"; //NON-NLS
    }

    /**
     * Gets a PropertyChangeSupport object. The PropertyChangeSupport object
     * returned is not used by instances of this class and does not have any
     * PropertyChangeListeners.
     *
     * @return A new PropertyChangeSupport object.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public static PropertyChangeSupport getPropertyChangeSupport() {
        return new PropertyChangeSupport(Case.class);
    }

    /**
     * Get the data model Content objects in the root of this case's hierarchy.
     *
     * @return a list of the root objects
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<Content> getDataSources() throws TskCoreException {
        List<Content> list = db.getRootObjects();
        hasData = (list.size() > 0);
        return list;
    }

    /**
     * get the created date of this case
     *
     * @return case creation date
     */
    public String getCreatedDate() {
        return getCaseMetadata().getCreatedDate();
    }

    /**
     * Get the name of the index where extracted text is stored for the case.
     *
     * @return Index name.
     */
    public String getTextIndexName() {
        return getCaseMetadata().getTextIndexName();
    }

    /**
     * Gets the time zone(s) of the image(s) in this case.
     *
     * @return time zones the set of time zones
     */
    public Set<TimeZone> getTimeZone() {
        Set<TimeZone> timezones = new HashSet<>();
        try {
            for (Content c : getDataSources()) {
                final Content dataSource = c.getDataSource();
                if ((dataSource != null) && (dataSource instanceof Image)) {
                    Image image = (Image) dataSource;
                    timezones.add(TimeZone.getTimeZone(image.getTimeZone()));
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.INFO, "Error getting time zones", ex); //NON-NLS
        }
        return timezones;
    }

    /**
     * Adds a subscriber to all case events from this Autopsy node and other
     * Autopsy nodes. To subscribe to only specific events, use one of the
     * overloads of addEventSubscriber().
     *
     * @param listener The subscriber to add.
     */
    public static synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        addEventSubscriber(Stream.of(Events.values())
                .map(Events::toString)
                .collect(Collectors.toSet()), listener);
    }

    /**
     * Removes a subscriber from all case events from this Autopsy node and
     * other Autopsy nodes. To remove a subscription to only specific events,
     * use one of the overloads of removeEventSubscriber().
     *
     * @param listener The subscriber to add.
     */
    public static synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        removeEventSubscriber(Stream.of(Events.values())
                .map(Events::toString)
                .collect(Collectors.toSet()), listener);
    }

    /**
     * Adds a subscriber to events from this Autopsy node and other Autopsy
     * nodes.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public static void addEventSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventNames, subscriber);
    }

    /**
     * Adds a subscriber to events from this Autopsy node and other Autopsy
     * nodes.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber to add.
     */
    public static void addEventSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventName, subscriber);
    }

    /**
     * Adds a subscriber to events from this Autopsy node and other Autopsy
     * nodes.
     *
     * @param eventName  The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to add.
     */
    public static void removeEventSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventName, subscriber);
    }

    /**
     * Removes a subscriber to events from this Autopsy node and other Autopsy
     * nodes.
     *
     * @param eventNames The event the subscriber is no longer interested in.
     * @param subscriber The subscriber to add.
     */
    public static void removeEventSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventNames, subscriber);
    }

    /**
     * Check if image from the given image path exists.
     *
     * @param imgPath the image path
     *
     * @return isExist whether the path exists
     */
    public static boolean pathExists(String imgPath) {
        return new File(imgPath).isFile();
    }
    /**
     * Does the given string refer to a physical drive?
     */
    private static final String pdisk = "\\\\.\\physicaldrive"; //NON-NLS
    private static final String dev = "/dev/"; //NON-NLS

    static boolean isPhysicalDrive(String path) {
        return path.toLowerCase().startsWith(pdisk)
                || path.toLowerCase().startsWith(dev);
    }

    /**
     * Does the given string refer to a local drive / partition?
     */
    static boolean isPartition(String path) {
        return path.toLowerCase().startsWith("\\\\.\\")
                && path.toLowerCase().endsWith(":");
    }

    /**
     * Does the given drive path exist?
     *
     * @param path to drive
     *
     * @return true if the drive exists, false otherwise
     */
    static boolean driveExists(String path) {
        // Test the drive by reading the first byte and checking if it's -1
        BufferedInputStream br = null;
        try {
            File tmp = new File(path);
            br = new BufferedInputStream(new FileInputStream(tmp));
            int b = br.read();
            return b != -1;
        } catch (Exception ex) {
            return false;
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Convert the Java timezone ID to the "formatted" string that can be
     * accepted by the C/C++ code. Example: "America/New_York" converted to
     * "EST5EDT", etc
     *
     * @param timezoneID
     *
     * @return
     */
    public static String convertTimeZone(String timezoneID) {

        TimeZone zone = TimeZone.getTimeZone(timezoneID);
        int offset = zone.getRawOffset() / 1000;
        int hour = offset / 3600;
        int min = (offset % 3600) / 60;

        DateFormat dfm = new SimpleDateFormat("z");
        dfm.setTimeZone(zone);
        boolean hasDaylight = zone.useDaylightTime();
        String first = dfm.format(new GregorianCalendar(2010, 1, 1).getTime()).substring(0, 3); // make it only 3 letters code
        String second = dfm.format(new GregorianCalendar(2011, 6, 6).getTime()).substring(0, 3); // make it only 3 letters code
        int mid = hour * -1;
        String result = first + Integer.toString(mid);
        if (min != 0) {
            result = result + ":" + Integer.toString(min);
        }
        if (hasDaylight) {
            result = result + second;
        }

        return result;
    }

    /**
     * to create the case directory
     *
     * @param caseDir  Path to the case directory (typically base + case name)
     * @param caseName the case name (used only for error messages)
     *
     * @throws CaseActionException throw if could not create the case dir
     * @Deprecated
     */
    @Deprecated
    static void createCaseDirectory(String caseDir, String caseName) throws CaseActionException {
        createCaseDirectory(caseDir, CaseType.SINGLE_USER_CASE);
    }

    /**
     * Create the case directory and its needed subfolders.
     *
     * @param caseDir  Path to the case directory (typically base + case name)
     * @param caseType The type of case, single-user or multi-user
     *
     * @throws CaseActionException throw if could not create the case dir
     */
    static void createCaseDirectory(String caseDir, CaseType caseType) throws CaseActionException {

        File caseDirF = new File(caseDir);
        if (caseDirF.exists()) {
            if (caseDirF.isFile()) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.existNotDir", caseDir));
            } else if (!caseDirF.canRead() || !caseDirF.canWrite()) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.existCantRW", caseDir));
            }
        }

        try {
            boolean result = (caseDirF).mkdirs(); // create root case Directory
            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreate", caseDir));
            }

            // create the folders inside the case directory
            String hostClause = "";

            if (caseType == CaseType.MULTI_USER_CASE) {
                hostClause = File.separator + NetworkUtils.getLocalHostName();
            }
            result = result && (new File(caseDir + hostClause + File.separator + EXPORT_FOLDER)).mkdirs()
                    && (new File(caseDir + hostClause + File.separator + LOG_FOLDER)).mkdirs()
                    && (new File(caseDir + hostClause + File.separator + TEMP_FOLDER)).mkdirs()
                    && (new File(caseDir + hostClause + File.separator + CACHE_FOLDER)).mkdirs();

            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateCaseDir", caseDir));
            }

            final String modulesOutDir = caseDir + hostClause + File.separator + MODULE_FOLDER;
            result = new File(modulesOutDir).mkdir();
            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateModDir",
                                modulesOutDir));
            }

            final String reportsOutDir = caseDir + hostClause + File.separator + REPORTS_FOLDER;
            result = new File(reportsOutDir).mkdir();
            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateReportsDir",
                                modulesOutDir));
            }

        } catch (Exception e) {
            throw new CaseActionException(
                    NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.gen", caseDir), e);
        }
    }

    /**
     * delete the given case directory
     *
     * @param casePath the case path
     *
     * @return boolean whether the case directory is successfully deleted or not
     */
    static boolean deleteCaseDirectory(File casePath) {
        logger.log(Level.INFO, "Deleting case directory: {0}", casePath.getAbsolutePath()); //NON-NLS
        return FileUtil.deleteDir(casePath);
    }

    /**
     * Invoke the creation of startup dialog window.
     */
    static public void invokeStartupDialog() {
        StartupWindowProvider.getInstance().open();
    }

    /**
     * Checks if a String is a valid case name
     *
     * @param caseName the candidate String
     *
     * @return true if the candidate String is a valid case name
     */
    static public boolean isValidName(String caseName) {
        return !(caseName.contains("\\") || caseName.contains("/") || caseName.contains(":")
                || caseName.contains("*") || caseName.contains("?") || caseName.contains("\"")
                || caseName.contains("<") || caseName.contains(">") || caseName.contains("|"));
    }

    static private void clearTempFolder() {
        File tempFolder = new File(currentCase.getTempDirectory());
        if (tempFolder.isDirectory()) {
            File[] files = tempFolder.listFiles();
            if (files.length > 0) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteCaseDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
    }

    /**
     * Check for existence of certain case sub dirs and create them if needed.
     *
     * @param openedCase
     */
    private static void checkSubFolders(Case openedCase) {
        String modulesOutputDir = openedCase.getModuleDirectory();
        File modulesOutputDirF = new File(modulesOutputDir);
        if (!modulesOutputDirF.exists()) {
            logger.log(Level.INFO, "Creating modules output dir for the case."); //NON-NLS

            try {
                if (!modulesOutputDirF.mkdir()) {
                    logger.log(Level.SEVERE, "Error creating modules output dir for the case, dir: {0}", modulesOutputDir); //NON-NLS
                }
            } catch (SecurityException e) {
                logger.log(Level.SEVERE, "Error creating modules output dir for the case, dir: " + modulesOutputDir, e); //NON-NLS
            }
        }
    }

    //case change helper
    private static void doCaseChange(Case toChangeTo) {
        logger.log(Level.INFO, "Changing Case to: {0}", toChangeTo); //NON-NLS
        if (toChangeTo != null) { // new case is open

            // clear the temp folder when the case is created / opened
            Case.clearTempFolder();
            checkSubFolders(toChangeTo);

            if (RuntimeProperties.coreComponentsAreActive()) {
                // enable these menus
                SwingUtilities.invokeLater(() -> {
                    CallableSystemAction.get(AddImageAction.class).setEnabled(true);
                    CallableSystemAction.get(CaseCloseAction.class).setEnabled(true);
                    CallableSystemAction.get(CasePropertiesAction.class).setEnabled(true);
                    CallableSystemAction.get(CaseDeleteAction.class).setEnabled(true); // Delete Case menu
                    CallableSystemAction.get(OpenTimelineAction.class).setEnabled(true);

                    if (toChangeTo.hasData()) {
                        // open all top components
                        CoreComponentControl.openCoreWindows();
                    } else {
                        // close all top components
                        CoreComponentControl.closeCoreWindows();
                    }
                    updateMainWindowTitle(currentCase.getName());
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    Frame f = WindowManager.getDefault().getMainWindow();
                    f.setTitle(Case.getAppName()); // set the window name to just application name           
                });
            }

        } else { // case is closed
            SwingUtilities.invokeLater(() -> {
                if (RuntimeProperties.coreComponentsAreActive()) {

                    // close all top components first
                    CoreComponentControl.closeCoreWindows();

                    // disable these menus
                    CallableSystemAction.get(AddImageAction.class).setEnabled(false); // Add Image menu
                    CallableSystemAction.get(CaseCloseAction.class).setEnabled(false); // Case Close menu
                    CallableSystemAction.get(CasePropertiesAction.class).setEnabled(false); // Case Properties menu
                    CallableSystemAction.get(CaseDeleteAction.class).setEnabled(false); // Delete Case menu
                    CallableSystemAction.get(OpenTimelineAction.class).setEnabled(false);
                }

                //clear pending notifications
                MessageNotifyUtil.Notify.clear();
                Frame f = WindowManager.getDefault().getMainWindow();
                f.setTitle(Case.getAppName()); // set the window name to just application name
            });

            //try to force gc to happen
            System.gc();
            System.gc();
        }

        //log memory usage after case changed
        logger.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

    }

    //case name change helper
    private static void updateMainWindowTitle(String newCaseName) {
        // update case name
        if (!newCaseName.equals("")) {
            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(newCaseName + " - " + Case.getAppName()); // set the window name to the new value
        }
    }

    /**
     * Adds a report to the case.
     *
     * @param localPath     The path of the report file, must be in the case
     *                      directory or one of its subdirectories.
     * @param srcModuleName The name of the module that created the report.
     * @param reportName    The report name, may be empty.
     *
     * @throws TskCoreException
     */
    public void addReport(String localPath, String srcModuleName, String reportName) throws TskCoreException {
        String normalizedLocalPath;
        try {
            normalizedLocalPath = Paths.get(localPath).normalize().toString();
        } catch (InvalidPathException ex) {
            String errorMsg = "Invalid local path provided: " + localPath; // NON-NLS
            throw new TskCoreException(errorMsg, ex);
        }
        Report report = this.db.addReport(normalizedLocalPath, srcModuleName, reportName);
        eventPublisher.publish(new ReportAddedEvent(report));
    }

    public List<Report> getAllReports() throws TskCoreException {
        return this.db.getAllReports();
    }

    /**
     * Deletes reports from the case.
     *
     * @param reports Collection of Report to be deleted from the case.
     *
     * @throws TskCoreException If there is a problem deleting a report.
     */
    public void deleteReports(Collection<? extends Report> reports) throws TskCoreException {
        for (Report report : reports) {
            this.db.deleteReport(report);
            eventPublisher.publish(new AutopsyEvent(Events.REPORT_DELETED.toString(), null, null));
        }
    }

    /**
     * Returns if the case has data in it yet.
     *
     * @return
     */
    public boolean hasData() {
        // false is also the initial value, so make the DB trip if it is still false
        if (!hasData) {
            try {
                hasData = (getDataSources().size() > 0);
            } catch (TskCoreException ex) {
            }
        }
        return hasData;
    }

    /**
     * Gets the full path to the case metadata file for this case.
     *
     * @return configFilePath The case metadata file path.
     *
     * @deprecated Use getCaseMetadata and CaseMetadata.getFilePath instead.
     */
    @Deprecated
    String getConfigFilePath() {
        return getCaseMetadata().getFilePath().toString();
    }

    /**
     * Deletes reports from the case.
     *
     * @param reports        Collection of Report to be deleted from the case.
     * @param deleteFromDisk No longer supported - ignored.
     *
     * @throws TskCoreException
     * @deprecated Use deleteReports(Collection<? extends Report> reports)
     * instead.
     */
    @Deprecated
    public void deleteReports(Collection<? extends Report> reports, boolean deleteFromDisk) throws TskCoreException {
        deleteReports(reports);
    }

}