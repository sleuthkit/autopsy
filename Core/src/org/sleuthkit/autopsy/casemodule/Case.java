/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.Lookup;
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
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
import org.sleuthkit.autopsy.coordinationservice.CoordinationServiceNamespace;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.coreutils.DriveUtils;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.framework.AutopsyService;
import org.sleuthkit.autopsy.framework.AutopsyService.CaseContext;
import org.sleuthkit.autopsy.framework.LoggingProgressIndicator;
import org.sleuthkit.autopsy.framework.ModalDialogProgressIndicator;
import org.sleuthkit.autopsy.framework.ProgressIndicator;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An Autopsy case. Currently, only one case at a time may be open.
 */
public class Case {

    private static final int NAME_LOCK_TIMOUT_HOURS = 12;
    private static final int SHARED_DIR_LOCK_TIMOUT_HOURS = 12;
    private static final int RESOURCE_LOCK_TIMOUT_HOURS = 12;
    private static final int MAX_SANITIZED_CASE_NAME_LEN = 47;
    private static final String SINGLE_USER_CASE_DB_NAME = "autopsy.db";
    private static final String EVENT_CHANNEL_NAME = "%s-Case-Events"; //NON-NLS
    private static final String CACHE_FOLDER = "Cache"; //NON-NLS
    private static final String EXPORT_FOLDER = "Export"; //NON-NLS
    private static final String LOG_FOLDER = "Log"; //NON-NLS
    private static final String REPORTS_FOLDER = "Reports"; //NON-NLS
    private static final String TEMP_FOLDER = "Temp"; //NON-NLS
    private static final int MIN_SECS_BETWEEN_TSK_ERROR_REPORTS = 60;
    private static final String MODULE_FOLDER = "ModuleOutput"; //NON-NLS
    private static final Logger logger = Logger.getLogger(Case.class.getName());
    private static final AutopsyEventPublisher eventPublisher = new AutopsyEventPublisher();

    /*
     * The application name, used to make the title of the main application
     * window [application name] when there is no open case and [curent case
     * display name] - [application name] when there is an open case.
     * Initialized by getting the main window title before a case has been
     * opened.
     *
     * TODO (JIRA-2231): Make the application name a RuntimeProperties item set
     * by Installers.
     */
    private static String appName;

    /*
     * The following fields are the state associated with the currently open
     * case. The coordination service lock on the case directory of the
     * currently open is used to prevent deletion of a multi-user case by this
     * node if it is open in another node. The case locking executor is a
     * single-threaded executor to guarantee that the case directory lock is
     * acquired and released in the same thread, as required by the coordination
     * service.
     */
    private static Case currentCase;
    private static CoordinationService.Lock currentCaseDirLock;
    private static ExecutorService caseLockingExecutor;

    /*
     * Case instance data.
     */
    private CaseMetadata caseMetadata;
    private SleuthkitCase caseDb;
    private SleuthkitErrorReporter sleuthkitErrorReporter;
    private CollaborationMonitor collaborationMonitor;
    private Services services;
    private boolean hasDataSources;

    /**
     * An enumeration of case types.
     */
    public enum CaseType {

        SINGLE_USER_CASE("Single-user case"), //NON-NLS
        MULTI_USER_CASE("Multi-user case");   //NON-NLS

        private final String typeName;

        /**
         * Gets a case type from a case type name string.
         *
         * @param typeName The case type name string.
         *
         * @return
         */
        public static CaseType fromString(String typeName) {
            if (typeName != null) {
                for (CaseType c : CaseType.values()) {
                    if (typeName.equalsIgnoreCase(c.toString())) {
                        return c;
                    }
                }
            }
            return null;
        }

        /**
         * Gets the string representation of this case type.
         *
         * @return
         */
        @Override
        public String toString() {
            return typeName;
        }

        /**
         * Gets the localized display name for this case type.
         *
         * @return The display name.
         */
        @Messages({
            "Case_caseType_singleUser=Single-user case",
            "Case_caseType_multiUser=Multi-user case"
        })
        String getLocalizedDisplayName() {
            if (fromString(typeName) == SINGLE_USER_CASE) {
                return Bundle.Case_caseType_singleUser();
            } else {
                return Bundle.Case_caseType_multiUser();
            }
        }

        /**
         * Constructs a case type.
         *
         * @param typeName The type name.
         */
        private CaseType(String typeName) {
            this.typeName = typeName;
        }

        /**
         * Tests the equality of the type name of this case type with another
         * case type name.
         *
         * @param otherTypeName A case type name,
         *
         * @return True or false,
         *
         * @deprecated Do not use.
         */
        @Deprecated
        public boolean equalsName(String otherTypeName) {
            return (otherTypeName == null) ? false : typeName.equals(otherTypeName);
        }

    };

    /**
     * An enumeration of the case events (property change events) a case may
     * publish (fire).
     */
    public enum Events {

        /**
         * The name of the current case has changed. The old value of the
         * PropertyChangeEvent is the old case name (type: String), the new
         * value is the new case name (type: String).
         */
        NAME,
        /**
         * The number of the current case has changed. The old value of the
         * PropertyChangeEvent is the old case number (type: String), the new
         * value is the new case number (type: String).
         */
        NUMBER,
        /**
         * The examiner associated with the current case has changed. The old
         * value of the PropertyChangeEvent is the old examiner (type: String),
         * the new value is the new examiner (type: String).
         */
        EXAMINER,
        /**
         * An attempt to add a new data source to the current case is being
         * made. The old and new values of the PropertyChangeEvent are null.
         * Cast the PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent to
         * access additional event data.
         */
        ADDING_DATA_SOURCE,
        /**
         * A failure to add a new data source to the current case has occurred.
         * The old and new values of the PropertyChangeEvent are null. Cast the
         * PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent
         * to access additional event data.
         */
        ADDING_DATA_SOURCE_FAILED,
        /**
         * A new data source has been added to the current case. The old value
         * of the PropertyChangeEvent is null, the new value is the newly-added
         * data source (type: Content). Cast the PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent to
         * access additional event data.
         */
        DATA_SOURCE_ADDED,
        /**
         * A data source has been deleted from the current case. The old value
         * of the PropertyChangeEvent is the object id of the data source that
         * was deleted (type: Long), the new value is null.
         */
        DATA_SOURCE_DELETED,
        /**
         * The current case has changed. If a case has been opened, the old
         * value of the PropertyChangeEvent is null, the new value is the new
         * case (type: Case). If a case has been closed, the old value of the
         * PropertyChangeEvent is the closed case (type: Case), the new value is
         * null.
         */
        CURRENT_CASE,
        /**
         * A report has been added to the current case. The old value of the
         * PropertyChangeEvent is null, the new value is the report (type:
         * Report).
         */
        REPORT_ADDED,
        /**
         * A report has been deleted from the current case. The old value of the
         * PropertyChangeEvent is the report (type: Report), the new value is
         * null.
         */
        REPORT_DELETED,
        /**
         * An artifact associated with the current case has been tagged. The old
         * value of the PropertyChangeEvent is null, the new value is the tag
         * (type: BlackBoardArtifactTag).
         */
        BLACKBOARD_ARTIFACT_TAG_ADDED,
        /**
         * A tag has been removed from an artifact associated with the current
         * case. The old value of the PropertyChangeEvent is the tag info (type:
         * BlackBoardArtifactTagDeletedEvent.DeletedBlackboardArtifactTagInfo),
         * the new value is null.
         */
        BLACKBOARD_ARTIFACT_TAG_DELETED,
        /**
         * Content associated with the current case has been tagged. The old
         * value of the PropertyChangeEvent is null, the new value is the tag
         * (type: ContentTag).
         */
        CONTENT_TAG_ADDED,
        /**
         * A tag has been removed from content associated with the current case.
         * The old value of the PropertyChangeEvent is is the tag info (type:
         * ContentTagDeletedEvent.DeletedContentTagInfo), the new value is null.
         */
        CONTENT_TAG_DELETED;
    };

    /**
     * Adds a subscriber to all case events. To subscribe to only specific
     * events, use one of the overloads of addEventSubscriber.
     *
     * @param listener The subscriber (PropertyChangeListener) to add.
     */
    public static void addPropertyChangeListener(PropertyChangeListener listener) {
        addEventSubscriber(Stream.of(Events.values())
                .map(Events::toString)
                .collect(Collectors.toSet()), listener);
    }

    /**
     * Removes a subscriber to all case events. To remove a subscription to only
     * specific events, use one of the overloads of removeEventSubscriber.
     *
     * @param listener The subscriber (PropertyChangeListener) to remove.
     */
    public static void removePropertyChangeListener(PropertyChangeListener listener) {
        removeEventSubscriber(Stream.of(Events.values())
                .map(Events::toString)
                .collect(Collectors.toSet()), listener);
    }

    /**
     * Adds a subscriber to specific case events.
     *
     * @param eventNames The events the subscriber is interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to add.
     */
    public static void addEventSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventNames, subscriber);
    }

    /**
     * Adds a subscriber to specific case events.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to add.
     */
    public static void addEventSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventName, subscriber);
    }

    /**
     * Removes a subscriber to specific case events.
     *
     * @param eventName  The event the subscriber is no longer interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to remove.
     */
    public static void removeEventSubscriber(String eventName, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventName, subscriber);
    }

    /**
     * Removes a subscriber to specific case events.
     *
     * @param eventNames The event the subscriber is no longer interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to remove.
     */
    public static void removeEventSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.removeSubscriber(eventNames, subscriber);
    }

    /**
     * Checks if a case display name is valid, i.e., does not include any
     * characters that cannot be used in file names.
     *
     * @param caseName The candidate case name.
     *
     * @return True or false.
     */
    public static boolean isValidName(String caseName) {
        return !(caseName.contains("\\") || caseName.contains("/") || caseName.contains(":")
                || caseName.contains("*") || caseName.contains("?") || caseName.contains("\"")
                || caseName.contains("<") || caseName.contains(">") || caseName.contains("|"));
    }

    /**
     * Creates a new case and makes it the current case.
     *
     * @param caseDir         The full path of the case directory. The directory
     *                        will be created if it doesn't already exist; if it
     *                        exists, it is ASSUMED it was created by calling
     *                        createCaseDirectory.
     * @param caseDisplayName The display name of case, which may be changed
     *                        later by the user.
     * @param caseNumber      The case number, can be the empty string.
     * @param examiner        The examiner to associate with the case, can be
     *                        the empty string.
     * @param caseType        The type of case (single-user or multi-user).
     *
     * @throws CaseActionException if there is a problem creating the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.creationException.couldNotCreateCase=Could not create case: {0}",
        "Case.exceptionMessage.illegalCaseName=Case name contains illegal characters.",
        "Case.exceptionMessage.lockAcquisitionInterrupted=Acquiring locks was interrupted.",
        "Case.progressIndicatorTitle.creatingCase=Creating Case",
        "Case.progressIndicatorCancelButton.label=Cancel",
        "Case.progressMessage.preparing=Preparing...",
        "Case.progressMessage.acquiringLocks=Acquiring locks...",
        "Case.progressMessage.finshing=Finishing..."
    })
    public static void createAsCurrentCase(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
        /*
         * If running with the desktop GUI, this needs to be done before any
         * cases are created or opened so that the application name can be
         * captured before a case name is added to the title.
         *
         * TODO (JIRA-2231): Make the application name a RuntimeProperties item
         * set by an Installer.
         */
        if (RuntimeProperties.runningWithGUI()) {
            getAppNameFromMainWindow();
        }

        /*
         * If another case is open, close it.
         */
        if (null != currentCase) {
            closeCurrentCase();
        }

        /*
         * Clean up the display name for the case to make a suitable immutable
         * case name.
         */
        String caseName;
        try {
            caseName = sanitizeCaseName(caseDisplayName);
        } catch (IllegalCaseNameException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase(Bundle.Case_exceptionMessage_illegalCaseName()), ex);
        }
        logger.log(Level.INFO, "Attempting to create case {0} (display name = {1}) in directory = {2}", new Object[]{caseName, caseDisplayName, caseDir}); //NON-NLS

        /*
         * Set up either a GUI progress indicator or a logging progress
         * indicator.
         */
        CancelButtonListener listener = new CancelButtonListener();
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_creatingCase(), new String[]{Bundle.Case_progressIndicatorCancelButton_label()}, Bundle.Case_progressIndicatorCancelButton_label(), null, listener);
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        Case newCase = new Case();
        CaseContext caseContext = new CaseContext(newCase, progressIndicator);
        listener.setCaseContext(caseContext);
        progressIndicator.start(Bundle.Case_progressMessage_preparing());

        /*
         * Creating a case is always done in the same non-UI thread that will be
         * used later to close the case. If the case is a multi-user case, this
         * ensures that case directory lock that is held as long as the case is
         * open is released in the same thread in which it was acquired, as is
         * required by the coordination service.
         */
        try {
            Future<Case> future = getCaseLockingExecutor().submit(() -> {
                if (CaseType.SINGLE_USER_CASE == caseType) {
                    newCase.open(caseDir, caseName, caseDisplayName, caseNumber, examiner, caseType, progressIndicator);
                } else {
                    /*
                     * First, acquire an exclusive case name lock to prevent two
                     * nodes from creating the same case at the same time.
                     */
                    progressIndicator.start(Bundle.Case_progressMessage_acquiringLocks());
                    try (CoordinationService.Lock nameLock = Case.acquireExclusiveCaseNameLock(caseName)) {
                        assert (null != nameLock);
                        /*
                         * Next, acquire a shared case directory lock that will
                         * be held as long as this node has this case open. This
                         * will prevent deletion of the case by another node.
                         */
                        acquireSharedCaseDirLock(caseDir);
                        /*
                         * Finally, acquire an exclusive case resources lock to
                         * ensure only one node at a time can
                         * create/open/upgrade/close the case resources.
                         */
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(caseName)) {
                            assert (null != resourcesLock);
                            try {
                                newCase.open(caseDir, caseName, caseDisplayName, caseNumber, examiner, caseType, progressIndicator);
                            } catch (CaseActionException ex) {
                                /*
                                 * Release the case directory lock immediately
                                 * if there was a problem opening the case.
                                 */
                                if (CaseType.MULTI_USER_CASE == caseType) {
                                    releaseSharedCaseDirLock(caseName);
                                }
                                throw ex;
                            }
                        }
                    }
                }
                return newCase;
            });
            if (RuntimeProperties.runningWithGUI()) {
                listener.setCaseActionFuture(future);
                SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
            }
            currentCase = future.get();
            logger.log(Level.INFO, "Created case {0} in directory = {1}", new Object[]{caseName, caseDir}); //NON-NLS
            if (RuntimeProperties.runningWithGUI()) {
                updateGUIForCaseOpened();
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));

        } catch (InterruptedException | ExecutionException ex) {
            if (ex instanceof InterruptedException) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase(Bundle.Case_exceptionMessage_lockAcquisitionInterrupted()), ex);
            } else {
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase(ex.getCause().getMessage()), ex);
            }
        } finally {
            progressIndicator.finish(Bundle.Case_progressMessage_finshing());
            if (RuntimeProperties.runningWithGUI()) {
                SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
            }
        }
    }

    /**
     * Opens an existing case and makes it the current case.
     *
     * @param caseMetadataFilePath The path of the case metadata (.aut) file.
     *
     * @throws CaseActionException if there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.openException.couldNotOpenCase=Could not open case: {0}",
        "Case.progressIndicatorTitle.openingCase=Opening Case",
        "Case.exceptionMessage.failedToReadMetadata=Failed to read metadata."
    })
    public static void openAsCurrentCase(String caseMetadataFilePath) throws CaseActionException {
        /*
         * If running with the desktop GUI, this needs to be done before any
         * cases are created or opened so that the application name can be
         * captured before a case name is added to the title.
         *
         * TODO (JIRA-2231): Make the application name a RuntimeProperties item
         * set by an Installer.
         */
        if (RuntimeProperties.runningWithGUI()) {
            getAppNameFromMainWindow();
        }

        /*
         * If another case is open, close it.
         */
        if (null != currentCase) {
            closeCurrentCase();
        }

        logger.log(Level.INFO, "Opening case with metadata file path {0}", caseMetadataFilePath); //NON-NLS
        try {
            CaseMetadata metadata = new CaseMetadata(Paths.get(caseMetadataFilePath));

            /*
             * Set up either a GUI progress indicator or a logging progress
             * indicator.
             */
            CancelButtonListener listener = new CancelButtonListener();
            ProgressIndicator progressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_openingCase(), new String[]{Bundle.Case_progressIndicatorCancelButton_label()}, Bundle.Case_progressIndicatorCancelButton_label(), null, listener);
            } else {
                progressIndicator = new LoggingProgressIndicator();
            }
            Case caseToOpen = new Case();
            CaseContext caseContext = new CaseContext(caseToOpen, progressIndicator);
            listener.setCaseContext(caseContext);
            progressIndicator.start(Bundle.Case_progressMessage_preparing());

            /*
             * Opening a case is always done in the same non-UI thread that will
             * be used later to close the case. If the case is a multi-user
             * case, this ensures that case directory lock that is held as long
             * as the case is open is released in the same thread in which it
             * was acquired, as is required by the coordination service.
             */
            CaseType caseType = metadata.getCaseType();
            String caseName = metadata.getCaseName();
            try {
                Future<Case> future = getCaseLockingExecutor().submit(() -> {
                    if (CaseType.SINGLE_USER_CASE == caseType) {
                        caseToOpen.open(metadata, progressIndicator);
                    } else {
                        /*
                         * First, acquire a shared case directory lock that will
                         * be held as long as this node has this case open, in
                         * order to prevent deletion of the case by another
                         * node.
                         */
                        progressIndicator.start(Bundle.Case_progressMessage_acquiringLocks());
                        acquireSharedCaseDirLock(metadata.getCaseDirectory());
                        /*
                         * Next, acquire an exclusive case resources lock to
                         * ensure only one node at a time can
                         * create/open/upgrade/close case resources.
                         */
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(metadata.getCaseName())) {
                            assert (null != resourcesLock);
                            try {
                                caseToOpen.open(metadata, progressIndicator);
                            } catch (CaseActionException ex) {
                                /*
                                 * Release the case directory lock immediately
                                 * if there was a problem opening the case.
                                 */
                                if (CaseType.MULTI_USER_CASE == caseType) {
                                    releaseSharedCaseDirLock(caseName);
                                }
                                throw ex;
                            }
                        }
                    }
                    return caseToOpen;
                });
                if (RuntimeProperties.runningWithGUI()) {
                    listener.setCaseActionFuture(future);
                    SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
                }
                future.get();
                currentCase = future.get();
                logger.log(Level.INFO, "Opened case with metadata file path {0}", caseMetadataFilePath); //NON-NLS                
                if (RuntimeProperties.runningWithGUI()) {
                    updateGUIForCaseOpened();
                }
                eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));
            } catch (InterruptedException | ExecutionException ex) {
                if (ex instanceof ExecutionException) {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex);
                } else {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(Bundle.Case_exceptionMessage_lockAcquisitionInterrupted()), ex);
                }
            } finally {
                progressIndicator.finish(Bundle.Case_progressMessage_finshing());
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
                }
            }
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(Bundle.Case_exceptionMessage_failedToReadMetadata()), ex);
        }
    }

    /**
     * Checks if a case, the current case, is open.
     *
     * @return True or false.
     */
    public static boolean isCaseOpen() {
        return currentCase != null;
    }

    /**
     * Gets the current case, if there is one.
     *
     * @return The current case.
     *
     * @throws IllegalStateException if there is no current case.
     */
    public static Case getCurrentCase() {
        if (currentCase != null) {
            return currentCase;
        } else {
            throw new IllegalStateException(NbBundle.getMessage(Case.class, "Case.getCurCase.exception.noneOpen"));
        }
    }

    /**
     * Closes the current case if there is a current case.
     *
     * @throws CaseActionException if there is a problem closing the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.closeException.couldNotCloseCase=Error closing case: {0}",
        "Case.progressIndicatorTitle.closingCase=Closing Case"
    })
    public static void closeCurrentCase() throws CaseActionException {
        if (null == currentCase) {
            return;
        }

        /*
         * Set up either a GUI progress indicator or a logging progress
         * indicator.
         */
        CancelButtonListener listener = new CancelButtonListener();
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(
                    Bundle.Case_progressIndicatorTitle_closingCase(),
                    new String[]{Bundle.Case_progressIndicatorCancelButton_label()},
                    Bundle.Case_progressIndicatorCancelButton_label(),
                    null,
                    listener);
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        CaseContext caseContext = new CaseContext(currentCase, progressIndicator);
        listener.setCaseContext(caseContext);
        progressIndicator.start(Bundle.Case_progressMessage_preparing());

        logger.log(Level.INFO, "Closing case with metadata file path {0}", currentCase.getCaseMetadata().getFilePath()); //NON-NLS
        try {
            /*
             * Closing a case is always done in the same non-UI thread that
             * opened/created the case. If the case is a multi-user case, this
             * ensures that case directory lock that is held as long as the case
             * is open is released in the same thread in which it was acquired,
             * as is required by the coordination service.
             */
            Future<Void> future = getCaseLockingExecutor().submit(() -> {
                if (CaseType.SINGLE_USER_CASE == currentCase.getCaseType()) {
                    currentCase.close(progressIndicator);
                } else {
                    String caseName = currentCase.getCaseMetadata().getCaseName();
                    /*
                     * Acquire an exclusive case resources lock to ensure only
                     * one node at a time can create/open/upgrade/close the case
                     * resources.
                     */
                    progressIndicator.start(Bundle.Case_progressMessage_acquiringLocks());
                    try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(currentCase.getName())) {
                        assert (null != resourcesLock);
                        currentCase.close(progressIndicator);
                    } finally {
                        /*
                         * Always release the case directory lock that was
                         * acquired when the case was opened.
                         */
                        releaseSharedCaseDirLock(caseName);
                    }
                }
                return null;
            });
            if (RuntimeProperties.runningWithGUI()) {
                listener.setCaseActionFuture(future);
                SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
            }
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            if (ex instanceof ExecutionException) {
                throw new CaseActionException(Bundle.Case_closeException_couldNotCloseCase(ex.getCause().getMessage()), ex);
            } else {
                throw new CaseActionException(Bundle.Case_closeException_couldNotCloseCase(Bundle.Case_exceptionMessage_lockAcquisitionInterrupted()), ex);
            }
        } finally {
            /*
             * The case is no longer the current case, even if an exception was
             * thrown.
             */
            logger.log(Level.INFO, "Closed case with metadata file path {0}", currentCase.getCaseMetadata().getFilePath()); //NON-NLS
            Case closedCase = currentCase;
            currentCase = null;
            if (RuntimeProperties.runningWithGUI()) {
                updateGUIForCaseClosed();
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), closedCase, null));
            progressIndicator.finish(Bundle.Case_progressMessage_finshing());
            if (RuntimeProperties.runningWithGUI()) {
                SwingUtilities.invokeLater(() -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
            }
        }
    }

    /**
     * Deletes the current case.
     *
     * @throws CaseActionException if there is a problem deleting the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    public static void deleteCurrentCase() throws CaseActionException {
        if (null == currentCase) {
            return;
        }
        CaseMetadata metadata = currentCase.getCaseMetadata();
        closeCurrentCase();
        deleteCase(metadata);
    }

    /**
     * Deletes a case. This method cannot be used to delete the current case;
     * deleting the current case must be done by calling Case.deleteCurrentCase.
     *
     * @param metadata The metadata for the case to delete.
     *
     * @throws CaseActionException if there is a problem deleting the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.deleteException.couldNotDeleteCase=Could not delete case: {0}",
        "Case.progressIndicatorTitle.deletingCase=Deleting Case",
        "Case.exceptionMessage.cannotDeleteCurrentCase=Cannot delete current case, it must be closed first.",
        "Case.progressMessage.deletingTextIndex=Deleting text index...",
        "Case.progressMessage.deletingCaseDatabase=Deleting case database..."
    })
    public static void deleteCase(CaseMetadata metadata) throws CaseActionException {
        if (null != currentCase && 0 == metadata.getCaseDirectory().compareTo(metadata.getCaseDirectory())) {
            throw new CaseActionException(Bundle.Case_deleteException_couldNotDeleteCase(Bundle.Case_exceptionMessage_cannotDeleteCurrentCase()));
        }

        logger.log(Level.INFO, "Deleting case with metadata file path {0}", metadata.getFilePath()); //NON-NLS
        try {
            /*
             * Set up either a GUI progress indicator or a logging progress
             * indicator.
             */
            CancelButtonListener listener = new CancelButtonListener();
            ProgressIndicator progressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                progressIndicator = new ModalDialogProgressIndicator(
                        Bundle.Case_progressIndicatorTitle_deletingCase(),
                        new String[]{Bundle.Case_progressIndicatorCancelButton_label()},
                        Bundle.Case_progressIndicatorCancelButton_label(),
                        null,
                        listener);
            } else {
                progressIndicator = new LoggingProgressIndicator();
            }
            progressIndicator.start(Bundle.Case_progressMessage_preparing());
            Future<Void> future = getCaseLockingExecutor().submit(() -> {
                if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                    cleanupDeletedCase(metadata, progressIndicator);
                } else {
                    /*
                     * First, acquire an exclusive case directory lock. The case
                     * cannot be deleted if another node has it open.
                     */
                    progressIndicator.start(Bundle.Case_progressMessage_acquiringLocks());
                    try (CoordinationService.Lock dirLock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, metadata.getCaseDirectory())) {
                        assert (null != dirLock);

                        /*
                         * Delete the text index.
                         */
                        progressIndicator.start(Bundle.Case_progressMessage_deletingTextIndex());
                        for (KeywordSearchService searchService : Lookup.getDefault().lookupAll(KeywordSearchService.class)) {
                            searchService.deleteTextIndex(metadata.getTextIndexName());
                        }

                        if (CaseType.MULTI_USER_CASE == metadata.getCaseType()) {
                            /*
                             * Delete the case database from the database
                             * server. The case database for a single-user case
                             * is in the case directory and will be deleted whe
                             * it is deleted.
                             */
                            progressIndicator.start(Bundle.Case_progressMessage_deletingCaseDatabase());
                            CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
                            Class.forName("org.postgresql.Driver"); //NON-NLS
                            try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                                    Statement statement = connection.createStatement();) {
                                String deleteCommand = "DROP DATABASE \"" + metadata.getCaseDatabaseName() + "\""; //NON-NLS
                                statement.execute(deleteCommand);
                            }
                        }

                        cleanupDeletedCase(metadata, progressIndicator);
                    }
                }
                return null;
            });
            logger.log(Level.INFO, "Deleted case with metadata file path {0}", metadata.getFilePath()); //NON-NLS            
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            if (ex instanceof ExecutionException) {
                throw new CaseActionException(Bundle.Case_deleteException_couldNotDeleteCase(ex.getCause().getMessage()), ex);
            } else {
                throw new CaseActionException(Bundle.Case_deleteException_couldNotDeleteCase(Bundle.Case_exceptionMessage_lockAcquisitionInterrupted()), ex);
            }
        }
    }

    /**
     * Sanitizes the case name for use as a PostgreSQL database name and in
     * ActiveMQ event channel (topic) names.
     *
     * PostgreSQL:
     * http://www.postgresql.org/docs/9.4/static/sql-syntax-lexical.html 63
     * chars max, must start with a-z or _ following chars can be letters _ or
     * digits
     *
     * ActiveMQ:
     * http://activemq.2283324.n4.nabble.com/What-are-limitations-restrictions-on-destination-name-td4664141.html
     * may not be ?
     *
     * @param caseName A candidate case name.
     *
     * @return The sanitized case name.
     *
     * @throws org.sleuthkit.autopsy.casemodule.Case.IllegalCaseNameException
     */
    static String sanitizeCaseName(String caseName) throws IllegalCaseNameException {

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
        if (result.length() > MAX_SANITIZED_CASE_NAME_LEN) {
            result = result.substring(0, MAX_SANITIZED_CASE_NAME_LEN);
        }

        if (result.isEmpty()) {
            throw new IllegalCaseNameException(String.format("Failed to sanitize case name '%s'", caseName));
        }

        return result;
    }

    /**
     * Creates a case directory and its subdirectories.
     *
     * @param caseDir  Path to the case directory (typically base + case name).
     * @param caseType The type of case, single-user or multi-user.
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

        } catch (MissingResourceException | CaseActionException e) {
            throw new CaseActionException(
                    NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.gen", caseDir), e);
        }
    }

    /**
     * Gets the paths of data sources that are images.
     *
     * @param db A case database.
     *
     * @return A mapping of object ids to image paths.
     */
    static Map<Long, String> getImagePaths(SleuthkitCase db) {
        Map<Long, String> imgPaths = new HashMap<>();
        try {
            Map<Long, List<String>> imgPathsList = db.getImagePaths();
            for (Map.Entry<Long, List<String>> entry : imgPathsList.entrySet()) {
                if (entry.getValue().size() > 0) {
                    imgPaths.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting image paths", ex); //NON-NLS
        }
        return imgPaths;
    }

    /**
     * Use the main window of the desktop application to initialize the
     * application name. Should be called BEFORE any case is opened or created.
     */
    private static void getAppNameFromMainWindow() {
        /*
         * This is tricky and fragile. What looks like lazy initialization of
         * the appName field is actually getting the application name from the
         * main window title BEFORE a case has been opened and a case name has
         * been included in the title. It is also very specific to the desktop
         * GUI.
         *
         * TODO (JIRA-2231): Make the application name a RuntimeProperties item
         * set by Installers.
         */
        if (RuntimeProperties.runningWithGUI() && (null == appName || appName.isEmpty())) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    appName = WindowManager.getDefault().getMainWindow().getTitle();
                });
            } catch (InterruptedException | InvocationTargetException ex) {
                logger.log(Level.SEVERE, "Unexpected exception getting main window title", ex);
            }
        }
    }

    /**
     * Deletes the case directory of a deleted case and removes the case form
     * the Recent Cases menu.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.deletingCaseDirectory=Deleting case directory..."
    })
    private static void cleanupDeletedCase(CaseMetadata metadata, ProgressIndicator progressIndicator) {
        /*
         * Delete the case directory.
         */
        progressIndicator.start(Bundle.Case_progressMessage_deletingCaseDirectory());
        if (!FileUtil.deleteDir(new File(metadata.getCaseDirectory()))) {
            logger.log(Level.SEVERE, "Failed to fully delete case directory {0}", metadata.getCaseDirectory());
        }

        /*
         * If running in a GUI, remove the case from the Recent Cases menu
         */
        if (RuntimeProperties.runningWithGUI()) {
            SwingUtilities.invokeLater(() -> {
                RecentCases.getInstance().removeRecentCase(metadata.getCaseDisplayName(), metadata.getFilePath().toString());
            });
        }
    }

    /**
     * Acquires an exclusive case name lock.
     *
     * @param caseName The case name (not the case display name, which can be
     *                 changed by a user).
     *
     * @return The lock.
     *
     * @throws CaseActionException with a user-friendly message if the lock
     *                             cannot be acquired.
     */
    @Messages({"Case.creationException.couldNotAcquireNameLock=Failed to get lock on case name."})
    private static CoordinationService.Lock acquireExclusiveCaseNameLock(String caseName) throws CaseActionException {
        try {
            Lock lock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, caseName, NAME_LOCK_TIMOUT_HOURS, TimeUnit.HOURS);
            if (null == lock) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireNameLock());
            }
            return lock;

        } catch (InterruptedException | CoordinationServiceException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireNameLock(), ex);
        }
    }

    /**
     * Acquires a shared case directory lock for the current case.
     *
     * @param caseDir The full path of the case directory.
     *
     * @throws CaseActionException with a user-friendly message if the lock
     *                             cannot be acquired.
     */
    @Messages({"Case.creationException.couldNotAcquireDirLock=Failed to get lock on case directory."})
    private static void acquireSharedCaseDirLock(String caseDir) throws CaseActionException {
        try {
            currentCaseDirLock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetSharedLock(CoordinationService.CategoryNode.CASES, caseDir, SHARED_DIR_LOCK_TIMOUT_HOURS, TimeUnit.HOURS);
            if (null == currentCaseDirLock) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireDirLock());
            }
        } catch (InterruptedException | CoordinationServiceException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireNameLock(), ex);
        }
    }

    /**
     * Releases a shared case directory lock for the current case.
     *
     * @param caseDir The full path of the case directory.
     */
    private static void releaseSharedCaseDirLock(String caseDir) {
        if (currentCaseDirLock != null) {
            try {
                currentCaseDirLock.release();
                currentCaseDirLock = null;
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Failed to release shared case directory lock for %s", caseDir), ex);
            }
        }
    }

    /**
     * Acquires an exclusive case resources lock.
     *
     * @param caseName The case name (not the case display name, which can be
     *                 changed by a user).
     *
     * @return The lock.
     *
     * @throws CaseActionException with a user-friendly message if the lock
     *                             cannot be acquired.
     */
    @Messages({"Case.creationException.couldNotAcquireResourcesLock=Failed to get lock on case resources."})
    private static CoordinationService.Lock acquireExclusiveCaseResourcesLock(String caseName) throws CaseActionException {
        try {
            String resourcesNodeName = caseName + "_resources";
            Lock lock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, resourcesNodeName, RESOURCE_LOCK_TIMOUT_HOURS, TimeUnit.HOURS);
            if (null == lock) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireResourcesLock());
            }
            return lock;

        } catch (InterruptedException | CoordinationServiceException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireResourcesLock(), ex);
        }
    }

    /**
     * Update the GUI to to reflect the current case.
     */
    private static void updateGUIForCaseOpened() {
        if (RuntimeProperties.runningWithGUI() && null != currentCase) {

            SleuthkitCase caseDb = currentCase.getSleuthkitCase();

            /*
             * If the case database was upgraded for a new schema and a backup
             * database was created, notify the user.
             */
            final String backupDbPath = caseDb.getBackupDatabasePath();
            if (null != backupDbPath) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(
                            WindowManager.getDefault().getMainWindow(),
                            NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.msg", backupDbPath),
                            NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.title"),
                            JOptionPane.INFORMATION_MESSAGE);
                });
            }

            /*
             * Look for the files for the data sources listed in the case
             * database and give the user the opportunity to locate any that are
             * missing.
             */
            Map<Long, String> imgPaths = getImagePaths(caseDb);
            for (Map.Entry<Long, String> entry : imgPaths.entrySet()) {
                long obj_id = entry.getKey();
                String path = entry.getValue();
                boolean fileExists = (new File(path).isFile() || DriveUtils.driveExists(path));
                if (!fileExists) {
                    int ret = JOptionPane.showConfirmDialog(
                            WindowManager.getDefault().getMainWindow(),
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.msg", appName, path),
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.title"),
                            JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION) {
                        MissingImageDialog.makeDialog(obj_id, caseDb);
                    } else {
                        logger.log(Level.SEVERE, "User proceeding with missing image files"); //NON-NLS
                    }
                }
            }

            /*
             * Enable the case-specific actions.
             */
            SwingUtilities.invokeLater(() -> {
                CallableSystemAction.get(AddImageAction.class).setEnabled(true);
                CallableSystemAction.get(CaseCloseAction.class).setEnabled(true);
                CallableSystemAction.get(CasePropertiesAction.class).setEnabled(true);
                CallableSystemAction.get(CaseDeleteAction.class).setEnabled(true);
                CallableSystemAction.get(OpenTimelineAction.class).setEnabled(true);

                /*
                 * Add the case to the recent cases tracker that supplies a list
                 * of recent cases to the recent cases menu item and the
                 * open/create case dialog.
                 */
                RecentCases.getInstance().addRecentCase(currentCase.getDisplayName(), currentCase.getCaseMetadata().getFilePath().toString());

                /*
                 * Open the top components (windows within the main application
                 * window).
                 */
                if (currentCase.hasData()) {
                    CoreComponentControl.openCoreWindows();
                }

                /*
                 * Reset the main window title to be [curent case display name]
                 * - [application name], instead of just the application name.
                 */
                addCaseNameToMainWindowTitle(currentCase.getDisplayName());
            });
        }
    }

    /*
     * Update the GUI to to reflect the lack of a current case.
     */
    private static void updateGUIForCaseClosed() {
        if (RuntimeProperties.runningWithGUI()) {
            SwingUtilities.invokeLater(() -> {

                /*
                 * Close the top components (windows within the main application
                 * window).
                 */
                CoreComponentControl.closeCoreWindows();

                /*
                 * Disable the case-specific menu items.
                 */
                CallableSystemAction.get(AddImageAction.class).setEnabled(false);
                CallableSystemAction.get(CaseCloseAction.class).setEnabled(false);
                CallableSystemAction.get(CasePropertiesAction.class).setEnabled(false);
                CallableSystemAction.get(CaseDeleteAction.class).setEnabled(false);
                CallableSystemAction.get(OpenTimelineAction.class).setEnabled(false);

                /*
                 * Clear the notifications in the notfier component in the lower
                 * right hand corner of the main application window.
                 */
                MessageNotifyUtil.Notify.clear();

                /*
                 * Reset the main window title to be just the application name,
                 * instead of [curent case display name] - [application name].
                 */
                Frame mainWindow = WindowManager.getDefault().getMainWindow();
                mainWindow.setTitle(appName);
            });
        }
    }

    /**
     * Changes the title of the main window to include the case name.
     *
     * @param caseName The name of the case.
     */
    private static void addCaseNameToMainWindowTitle(String caseName) {
        if (!caseName.isEmpty()) {
            Frame frame = WindowManager.getDefault().getMainWindow();
            frame.setTitle(caseName + " - " + appName);
        }
    }

    /**
     * Get the single thread executor for the current case, creating it if
     * necessary.
     *
     * @return The executor
     */
    private static ExecutorService getCaseLockingExecutor() {
        if (null == caseLockingExecutor) {
            caseLockingExecutor = Executors.newSingleThreadExecutor();
        }
        return caseLockingExecutor;
    }

    /**
     * Empties the temp subdirectory for the current case.
     */
    private static void clearTempSubDir(String tempSubDirPath) {
        File tempFolder = new File(tempSubDirPath);
        if (tempFolder.isDirectory()) {
            File[] files = tempFolder.listFiles();
            if (files.length > 0) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        FileUtil.deleteDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
    }

    /**
     * Gets the case database.
     *
     * @return The case database.
     */
    public SleuthkitCase getSleuthkitCase() {
        return this.caseDb;
    }

    /**
     * Gets the case services manager.
     *
     * @return The case services manager.
     */
    public Services getServices() {
        return services;
    }

    /**
     * Gets the case type.
     *
     * @return The case type.
     */
    public CaseType getCaseType() {
        return getCaseMetadata().getCaseType();
    }

    /**
     * Gets the case create date.
     *
     * @return case The case create date.
     */
    public String getCreatedDate() {
        return getCaseMetadata().getCreatedDate();
    }

    /**
     * Gets the immutable case name.
     *
     * @return The case name.
     */
    public String getName() {
        return getCaseMetadata().getCaseName();
    }

    /**
     * Gets the case name that can be changed by the user.
     *
     * @return The case display name.
     */
    public String getDisplayName() {
        return getCaseMetadata().getCaseDisplayName();
    }

    /**
     * Gets the case number.
     *
     * @return The case number
     */
    public String getNumber() {
        return caseMetadata.getCaseNumber();
    }

    /**
     * Gets the examiner name.
     *
     * @return The examiner name.
     */
    public String getExaminer() {
        return caseMetadata.getExaminer();
    }

    /**
     * Gets the path to the top-level case directory.
     *
     * @return The top-level case directory path.
     */
    public String getCaseDirectory() {
        return caseMetadata.getCaseDirectory();
    }

    /**
     * Gets the root case output directory for this case, creating it if it does
     * not exist. If the case is a single-user case, this is the case directory.
     * If the case is a multi-user case, this is a subdirectory of the case
     * directory specific to the host machine.
     *
     * @return the path to the host output directory.
     */
    public String getOutputDirectory() {
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
     * Gets the full path to the temp directory for this case, creating it if it
     * does not exist.
     *
     * @return The temp subdirectory path.
     */
    public String getTempDirectory() {
        return getOrCreateSubdirectory(TEMP_FOLDER);
    }

    /**
     * Gets the full path to the cache directory for this case, creating it if
     * it does not exist.
     *
     * @return The cache directory path.
     */
    public String getCacheDirectory() {
        return getOrCreateSubdirectory(CACHE_FOLDER);
    }

    /**
     * Gets the full path to the export directory for this case, creating it if
     * it does not exist.
     *
     * @return The export directory path.
     */
    public String getExportDirectory() {
        return getOrCreateSubdirectory(EXPORT_FOLDER);
    }

    /**
     * Gets the full path to the log directory for this case, creating it if it
     * does not exist.
     *
     * @return The log directory path.
     */
    public String getLogDirectoryPath() {
        return getOrCreateSubdirectory(LOG_FOLDER);
    }

    /**
     * Gets the full path to the reports directory for this case, creating it if
     * it does not exist.
     *
     * @return The report directory path.
     */
    public String getReportDirectory() {
        return getOrCreateSubdirectory(REPORTS_FOLDER);
    }

    /**
     * Gets the full path to the module output directory for this case, creating
     * it if it does not exist.
     *
     * @return The module output directory path.
     */
    public String getModuleDirectory() {
        return getOrCreateSubdirectory(MODULE_FOLDER);
    }

    /**
     * Gets the path of the module output directory for this case, relative to
     * the case directory, creating it if it does not exist.
     *
     * @return The path to the module output directory, relative to the case
     *         directory.
     */
    public String getModuleOutputDirectoryRelativePath() {
        Path path = Paths.get(getModuleDirectory());
        if (getCaseType() == CaseType.MULTI_USER_CASE) {
            return path.subpath(path.getNameCount() - 2, path.getNameCount()).toString();
        } else {
            return path.subpath(path.getNameCount() - 1, path.getNameCount()).toString();
        }
    }

    /**
     * Gets the data sources for the case.
     *
     * @return A list of data sources.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException if there is a problem
     *                                                  querying the case
     *                                                  database.
     */
    public List<Content> getDataSources() throws TskCoreException {
        List<Content> list = caseDb.getRootObjects();
        hasDataSources = (list.size() > 0);
        return list;
    }

    /**
     * Gets the time zone(s) of the image data source(s) in this case.
     *
     * @return The set of time zones in use.
     */
    public Set<TimeZone> getTimeZones() {
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
            logger.log(Level.SEVERE, "Error getting data source time zones", ex); //NON-NLS
        }
        return timezones;
    }

    /**
     * Sets the name of the keyword search index for the case.
     *
     * @param textIndexName The text index name.
     *
     * @throws CaseMetadataException
     */
    public void setTextIndexName(String textIndexName) throws CaseMetadataException {
        getCaseMetadata().setTextIndexName(textIndexName);
    }

    /**
     * Gets the name of the keyword search index for the case.
     *
     * @return The index name.
     */
    public String getTextIndexName() {
        return getCaseMetadata().getTextIndexName();
    }

    /**
     * Queries whether or not the case has data, i.e., whether or not at least
     * one data source has been added to the case.
     *
     * @return True or false.
     */
    public boolean hasData() {
        if (!hasDataSources) {
            try {
                hasDataSources = (getDataSources().size() > 0);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error accessing case database", ex); //NON-NLS
            }
        }
        return hasDataSources;
    }

    /**
     * Notifies case event subscribers that a data source is being added to the
     * case.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param eventId A unique identifier for the event. This UUID must be used
     *                to call notifyFailedAddingDataSource or
     *                notifyNewDataSource after the data source is added.
     */
    public void notifyAddingDataSource(UUID eventId) {
        eventPublisher.publish(new AddingDataSourceEvent(eventId));
    }

    /**
     * Notifies case event subscribers that a data source failed to be added to
     * the case.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param addingDataSourceEventId The unique identifier for the
     *                                corresponding adding data source event
     *                                (see notifyAddingDataSource).
     */
    public void notifyFailedAddingDataSource(UUID addingDataSourceEventId) {
        eventPublisher.publish(new AddingDataSourceFailedEvent(addingDataSourceEventId));
    }

    /**
     * Notifies case event subscribers that a data source has been added to the
     * case database.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param dataSource              The data source.
     * @param addingDataSourceEventId The unique identifier for the
     *                                corresponding adding data source event
     *                                (see notifyAddingDataSource).
     */
    public void notifyDataSourceAdded(Content dataSource, UUID addingDataSourceEventId) {
        eventPublisher.publish(new DataSourceAddedEvent(dataSource, addingDataSourceEventId));
    }

    /**
     * Notifies case event subscribers that a content tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new ContentTag added
     */
    public void notifyContentTagAdded(ContentTag newTag) {
        eventPublisher.publish(new ContentTagAddedEvent(newTag));
    }

    /**
     * Notifies case event subscribers that a content tag has been deleted.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param deletedTag ContentTag deleted
     */
    public void notifyContentTagDeleted(ContentTag deletedTag) {
        eventPublisher.publish(new ContentTagDeletedEvent(deletedTag));
    }

    /**
     * Notifies case event subscribers that an artifact tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new BlackboardArtifactTag added
     */
    public void notifyBlackBoardArtifactTagAdded(BlackboardArtifactTag newTag) {
        eventPublisher.publish(new BlackBoardArtifactTagAddedEvent(newTag));
    }

    /**
     * Notifies case event subscribers that an artifact tag has been deleted.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param deletedTag BlackboardArtifactTag deleted
     */
    public void notifyBlackBoardArtifactTagDeleted(BlackboardArtifactTag deletedTag) {
        eventPublisher.publish(new BlackBoardArtifactTagDeletedEvent(deletedTag));
    }

    /**
     * Adds a report to the case.
     *
     * @param localPath     The path of the report file, must be in the case
     *                      directory or one of its subdirectories.
     * @param srcModuleName The name of the module that created the report.
     * @param reportName    The report name, may be empty.
     *
     * @throws TskCoreException if there is a problem adding the report to the
     *                          case database.
     */
    public void addReport(String localPath, String srcModuleName, String reportName) throws TskCoreException {
        String normalizedLocalPath;
        try {
            normalizedLocalPath = Paths.get(localPath).normalize().toString();
        } catch (InvalidPathException ex) {
            String errorMsg = "Invalid local path provided: " + localPath; // NON-NLS
            throw new TskCoreException(errorMsg, ex);
        }
        Report report = this.caseDb.addReport(normalizedLocalPath, srcModuleName, reportName);
        eventPublisher.publish(new ReportAddedEvent(report));
    }

    /**
     * Gets the reports that have been added to the case.
     *
     * @return A collection of report objects.
     *
     * @throws TskCoreException if there is a problem querying the case
     *                          database.
     */
    public List<Report> getAllReports() throws TskCoreException {
        return this.caseDb.getAllReports();
    }

    /**
     * Deletes one or more reports from the case database. Does not delete the
     * report files.
     *
     * @param reports The report(s) to be deleted from the case.
     *
     * @throws TskCoreException if there is an error deleting the report(s).
     */
    public void deleteReports(Collection<? extends Report> reports) throws TskCoreException {
        for (Report report : reports) {
            this.caseDb.deleteReport(report);
            eventPublisher.publish(new AutopsyEvent(Events.REPORT_DELETED.toString(), report, null));
        }
    }

    /**
     * Gets the case metadata.
     *
     * @return A CaseMetaData object.
     */
    CaseMetadata getCaseMetadata() {
        return caseMetadata;
    }

    /**
     * Updates the case display name name.
     *
     * @param oldCaseName The old case name.
     * @param oldPath     The old path name.
     * @param newCaseName The new case name.
     * @param newPath     The new case path.
     */
    void updateCaseName(String oldCaseName, String oldPath, String newCaseName, String newPath) throws CaseActionException {
        try {
            caseMetadata.setCaseDisplayName(newCaseName);
            eventPublisher.publish(new AutopsyEvent(Events.NAME.toString(), oldCaseName, newCaseName));
            SwingUtilities.invokeLater(() -> {
                try {
                    RecentCases.getInstance().updateRecentCase(oldCaseName, oldPath, newCaseName, newPath);
                    addCaseNameToMainWindowTitle(newCaseName);
                } catch (Exception ex) {
                    Logger.getLogger(Case.class.getName()).log(Level.SEVERE, "Error updating case name in UI", ex); //NON-NLS
                }
            });
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(NbBundle.getMessage(this.getClass(), "Case.updateCaseName.exception.msg"), ex);
        }
    }

    /**
     * Constructs an Autopsy case.
     */
    private Case() {
    }

    /**
     * Creates and opens a new case.
     *
     * @param caseDir         The full path of the case directory. The directory
     *                        will be created if it doesn't already exist; if it
     *                        exists, it is ASSUMED it was created by calling
     *                        createCaseDirectory.
     * @param caseDisplayName The display name of case, which may be changed
     *                        later by the user.
     * @param caseNumber      The case number, can be the empty string.
     * @param examiner        The examiner to associate with the case, can be
     *                        the empty string.
     * @param caseType        The type of case (single-user or multi-user).
     *
     * @throws CaseActionException if there is a problem creating the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "Case.progressMessage.creatingCaseDirectory=Creating case directory...",
        "Case.progressMessage.creatingCaseDatabase=Creating case database...",
        "Case.progressMessage.creatingCaseMetadataFile=Creating case metadata file...",
        "Case.exceptionMessage.couldNotCreateMetadataFile=Failed to create case metadata file.",
        "Case.exceptionMessage.couldNotCreateCaseDatabase=Failed to create case database."
    })
    private void open(String caseDir, String caseName, String caseDisplayName, String caseNumber, String examiner, CaseType caseType, ProgressIndicator progressIndicator) throws CaseActionException {
        /*
         * Create the case directory, if it does not already exist.
         *
         * TODO (JIRA-2180): The reason for this check for the existence of the
         * case directory is not at all obvious. It reflects the assumption that
         * if the case directory already exists, it is because the case is being
         * created using the the "New Case" wizard, which separates the creation
         * of the case directory from the creation of the case, with the idea
         * that if the case directory cannot be created, the user can be asked
         * to supply a different case directory path. This of course creates
         * subtle and undetectable coupling between this code and the wizard
         * code. The desired effect could be accomplished more simply and safely
         * by having this method throw a specific exception to indicate that the
         * case directory could not be created. In fact, a FEW specific
         * exception types would in turn allow us to put localized,
         * user-friendly messages in the GUI instead of putting user-friendly,
         * localized messages in the exceptions, which causes them to appear in
         * the application log, where it would be better to use English for
         * readability by the broadest group of developers.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseDirectory());
        if (new File(caseDir).exists() == false) {
            Case.createCaseDirectory(caseDir, caseType);
        }

        /*
         * Create the case database.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseDatabase());
        String dbName = null;
        try {
            if (CaseType.SINGLE_USER_CASE == caseType) {
                /*
                 * For single-user cases, the case database is a SQLite database
                 * with a fixed name and is physically located in the root of
                 * the case directory.
                 */
                dbName = SINGLE_USER_CASE_DB_NAME;
                this.caseDb = SleuthkitCase.newCase(Paths.get(caseDir, SINGLE_USER_CASE_DB_NAME).toString());
            } else if (CaseType.MULTI_USER_CASE == caseType) {
                /*
                 * For multi-user cases, the case database is a PostgreSQL
                 * database with a name consiting of the case name with a time
                 * stamp suffix and is physically located on the database
                 * server.
                 */
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
                Date date = new Date();
                dbName = caseName + "_" + dateFormat.format(date);
                this.caseDb = SleuthkitCase.newCase(dbName, UserPreferences.getDatabaseConnectionInfo(), caseDir);
            }
        } catch (TskCoreException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotCreateCaseDatabase(), ex);
        } catch (UserPreferencesException ex) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex);
        }

        /*
         * Create the case metadata (.aut) file.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseMetadataFile());
        try {
            this.caseMetadata = new CaseMetadata(caseDir, caseType, caseName, caseDisplayName, caseNumber, examiner, dbName);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotCreateMetadataFile(), ex);
        }
        open(progressIndicator);
    }

    /**
     * Opens an existing case.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException if there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "Case.progressMessage.openingCaseDatabase=Opening case database...",
        "Case.exceptionMessage.couldNotOpenCaseDatabase=Failed to open case database."
    })
    private void open(CaseMetadata metadata, ProgressIndicator progressIndicator) throws CaseActionException {
        this.caseMetadata = metadata;

        /*
         * Open the case database.
         */
        try {
            progressIndicator.progress(Bundle.Case_progressMessage_openingCaseDatabase());
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                this.caseDb = SleuthkitCase.openCase(Paths.get(metadata.getCaseDirectory(), metadata.getCaseDatabaseName()).toString());
            } else if (UserPreferences.getIsMultiUserModeEnabled()) {
                try {
                    this.caseDb = SleuthkitCase.openCase(metadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());

                } catch (UserPreferencesException ex) {
                    throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex);
                }
            } else {
                throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.open.exception.multiUserCaseNotEnabled"));
            }
        } catch (TskCoreException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotOpenCaseDatabase(), ex);
        }
        open(progressIndicator);
    }

    /**
     * Completes the case opening tasks common to both new cases and existing
     * cases.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.switchingLogDirectory=Switching log directory...",
        "Case.progressMessage.settingUpTskErrorReporting=Setting up SleuthKit error reporting...",
        "Case.progressMessage.openingCaseLevelServices=Opening case-level services...",
        "Case.progressMessage.openingApplicationServiceResources=Opening case-specific application service resources...",
        "Case.progressMessage.settingUpNetworkCommunications=Setting up network communications...",
        "# {0} - service name", "# {1} - exception message", "Case.servicesException.serviceResourcesOpenError=Could not open case resources for {0} service: {1}",
        "# {0} - service name", "Case.servicesException.notificationTitle={0} Service Error"
    })
    private void open(ProgressIndicator progressIndicator) {
        /*
         * Switch to writing to the application logs in the logs subdirectory.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_switchingLogDirectory());
        Logger.setLogDirectory(getLogDirectoryPath());

        /*
         * Hook up a SleuthKit layer error reporter.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_settingUpTskErrorReporting());
        this.sleuthkitErrorReporter = new SleuthkitErrorReporter(MIN_SECS_BETWEEN_TSK_ERROR_REPORTS, NbBundle.getMessage(Case.class, "IntervalErrorReport.ErrorText"));
        this.caseDb.addErrorObserver(this.sleuthkitErrorReporter);

        /*
         * Clear the temp subdirectory.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_clearingTempDirectory());
        Case.clearTempSubDir(this.getTempDirectory());

        /*
         * Open the case-level services.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_openingCaseLevelServices());
        this.services = new Services(this.caseDb);

        /*
         * Allow any registered application services to open any resources
         * specific to this case.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_openingApplicationServiceResources());
        AutopsyService.CaseContext context = new AutopsyService.CaseContext(this, progressIndicator);
        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class)) {
            try {
                service.openCaseResources(context);
            } catch (AutopsyService.AutopsyServiceException ex) {
                /*
                 * The case-specific application service resources are not
                 * essential. Log an error and notify the user, but do not
                 * throw.
                 */
                Case.logger.log(Level.SEVERE, String.format("%s service failed to open case resources", service.getServiceName()), ex);
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(() -> MessageNotifyUtil.Notify.error(Bundle.Case_servicesException_notificationTitle(service.getServiceName()), Bundle.Case_servicesException_serviceResourcesOpenError(service.getServiceName(), ex.getLocalizedMessage())));
                }
            }
        }

        /*
         * If this case is a multi-user case, set up for communication with
         * other nodes.
         */
        if (CaseType.MULTI_USER_CASE == this.caseMetadata.getCaseType()) {
            progressIndicator.progress(Bundle.Case_progressMessage_settingUpNetworkCommunications());
            try {
                eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, this.getName()));
                collaborationMonitor = new CollaborationMonitor(this.getName());
            } catch (AutopsyEventException | CollaborationMonitor.CollaborationMonitorException ex) {
                /*
                 * The collaboration monitor and event channel are not
                 * essential. Log an error and notify the user, but do not
                 * throw.
                 */
                logger.log(Level.SEVERE, "Failed to setup network communications", ex); //NON-NLS
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(() -> MessageNotifyUtil.Notify.error(NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.Title"), NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.ErrMsg")));
                }
            }
        }

    }

    /**
     * Closes the case.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.cancellingIngestJobs=Cancelling ingest jobs...",
        "Case.progressMessage.notifyingCaseEventSubscribers=Notifying case event subscribers...",
        "Case.progressMessage.clearingTempDirectory=Clearing case temp directory...",
        "Case.progressMessage.closingCaseLevelServices=Closing case-level services...",
        "Case.progressMessage.closingApplicationServiceResources=Closing case-specific application service resources...",
        "Case.progressMessage.tearingDownNetworkCommunications=Tearing down network communications...",
        "Case.progressMessage.closingCaseDatabase=Closing case database...",
        "Case.progressMessage.tearingDownTskErrorReporting=Tearing down SleuthKit error reporting..."
    })
    private void close(ProgressIndicator progressIndicator) {
        /*
         * Cancel all ingest jobs.
         *
         * TODO (JIRA-2227): Case closing should wait for ingest to stop to
         * avoid changing the case database while ingest is still using it.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_cancellingIngestJobs());
        IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);

        /*
         * Notify all local case event subscribers that the case is closed and
         * all interactions with the current case are no longer permitted.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_notifyingCaseEventSubscribers());
        eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), currentCase, null));

        /*
         * Stop sending/receiving case events to and from other nodes if this is
         * a multi-user case.
         */
        if (CaseType.MULTI_USER_CASE == currentCase.getCaseType()) {
            progressIndicator.progress(Bundle.Case_progressMessage_tearingDownNetworkCommunications());
            if (null != collaborationMonitor) {
                collaborationMonitor.shutdown();
            }
            eventPublisher.closeRemoteEventChannel();
        }

        /*
         * Allow all registered applications ervices providers to close
         * resources related to the case.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_closingApplicationServiceResources());
        AutopsyService.CaseContext context = new AutopsyService.CaseContext(currentCase, progressIndicator);
        String serviceName = "";
        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class)) {
            try {
                serviceName = service.getServiceName();
                if (!serviceName.equals("Solr Keyword Search Service")) {
                    service.closeCaseResources(context);
                }
            } catch (AutopsyService.AutopsyServiceException ex) {
                Case.logger.log(Level.SEVERE, String.format("%s service failed to close case resources", serviceName), ex);
            }
        }

        /*
         * Close the case-level services.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_closingCaseLevelServices());
        try {
            currentCase.getServices().close();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Error closing internal case services for %s at %s", this.getName(), this.getCaseDirectory()), ex);
        }

        /*
         * Close the case database
         */
        progressIndicator.progress(Bundle.Case_progressMessage_closingCaseDatabase());
        this.caseDb.close();

        /*
         * Disconnect the SleuthKit layer error reporter.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_tearingDownTskErrorReporting());
        this.caseDb.removeErrorObserver(this.sleuthkitErrorReporter);

        progressIndicator.progress(Bundle.Case_progressMessage_switchingLogDirectory());
        Logger.setLogDirectory(PlatformUtil.getLogDirectory());
    }

    /**
     * Gets the path to the specified subdirectory of the case directory,
     * creating it if it does not already exist.
     *
     * @return The absolute path to the specified subdirectory.
     */
    private String getOrCreateSubdirectory(String subDirectoryName) {
        File subDirectory = Paths.get(getOutputDirectory(), subDirectoryName).toFile();
        if (!subDirectory.exists()) {
            subDirectory.mkdirs();
        }
        return subDirectory.toString();

    }

    private final static class CancelButtonListener implements ActionListener {

        private Future<?> caseActionFuture;
        private CaseContext caseContext;

        private void setCaseActionFuture(Future<?> caseActionFuture) {
            this.caseActionFuture = caseActionFuture;
        }

        private void setCaseContext(CaseContext caseContext) {
            this.caseContext = caseContext;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (null != this.caseContext) {
                this.caseContext.requestCancel();
            }
            if (null != this.caseActionFuture) {
                this.caseActionFuture.cancel(true);
            }
        }

    }

    /**
     * An exception to throw when a case name with invalid characters is
     * encountered.
     */
    final static class IllegalCaseNameException extends Exception {

        private static final long serialVersionUID = 1L;

        /**
         * Constructs an exception to throw when a case name with invalid
         * characters is encountered.
         *
         * @param message The exception message.
         */
        IllegalCaseNameException(String message) {
            super(message);
        }

        /**
         * Constructs an exception to throw when a case name with invalid
         * characters is encountered.
         *
         * @param message The exception message.
         * @param cause   The exceptin cause.
         */
        IllegalCaseNameException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Creates a new, single-user Autopsy case.
     *
     * @param caseDir         The full path of the case directory. The directory
     *                        will be created if it doesn't already exist; if it
     *                        exists, it is ASSUMED it was created by calling
     *                        createCaseDirectory.
     * @param caseDisplayName The display name of case, which may be changed
     *                        later by the user.
     * @param caseNumber      The case number, can be the empty string.
     * @param examiner        The examiner to associate with the case, can be
     *                        the empty string.
     *
     * @throws CaseActionException if there is a problem creating the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     * @deprecated Use createAsCurrentCase instead.
     */
    @Deprecated
    public static void create(String caseDir, String caseDisplayName, String caseNumber, String examiner) throws CaseActionException {
        createAsCurrentCase(caseDir, caseDisplayName, caseNumber, examiner, CaseType.SINGLE_USER_CASE);
    }

    /**
     * Creates a new Autopsy case and makes it the current case.
     *
     * @param caseDir         The full path of the case directory. The directory
     *                        will be created if it doesn't already exist; if it
     *                        exists, it is ASSUMED it was created by calling
     *                        createCaseDirectory.
     * @param caseDisplayName The display name of case, which may be changed
     *                        later by the user.
     * @param caseNumber      The case number, can be the empty string.
     * @param examiner        The examiner to associate with the case, can be
     *                        the empty string.
     * @param caseType        The type of case (single-user or multi-user).
     *
     * @throws CaseActionException if there is a problem creating the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     * @deprecated Use createAsCurrentCase instead.
     */
    @Deprecated
    public static void create(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
        createAsCurrentCase(caseDir, caseDisplayName, caseNumber, examiner, caseType);
    }

    /**
     * Opens an existing Autopsy case and makes it the current case.
     *
     * @param caseMetadataFilePath The path of the case metadata (.aut) file.
     *
     * @throws CaseActionException if there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     * @deprecated Use openAsCurrentCase instead.
     */
    @Deprecated
    public static void open(String caseMetadataFilePath) throws CaseActionException {
        openAsCurrentCase(caseMetadataFilePath);
    }

/**
     * Closes this Autopsy case.
     *
     * @throws CaseActionException if there is a problem closing the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     * @deprecated Use closeCurrentCase instead.
     */
    @Deprecated
    public void closeCase() throws CaseActionException {
        closeCurrentCase();
    }    
    
    /**
     * Invokes the startup dialog window.
     *
     * @deprecated Use StartupWindowProvider.getInstance().open() instead.
     */
    @Deprecated
    public static void invokeStartupDialog() {
        StartupWindowProvider.getInstance().open();
    }

    /**
     * Converts a Java timezone id to a coded string with only alphanumeric
     * characters. Example: "America/New_York" is converted to "EST5EDT" by this
     * method.
     *
     * @param timeZoneId The time zone id.
     *
     * @return The converted time zone string.
     *
     * @deprecated Use
     * org.sleuthkit.autopsy.coreutils.TimeZoneUtils.convertToAlphaNumericFormat
     * instead.
     */
    @Deprecated
    public static String convertTimeZone(String timeZoneId) {
        return TimeZoneUtils.convertToAlphaNumericFormat(timeZoneId);
    }

    /**
     * Check if file exists and is a normal file.
     *
     * @param filePath The file path.
     *
     * @return True or false.
     *
     * @deprecated Use java.io.File.exists or java.io.File.isFile instead
     */
    @Deprecated
    public static boolean pathExists(String filePath) {
        return new File(filePath).isFile();
    }

    /**
     * Gets the Autopsy version.
     *
     * @return The Autopsy version.
     *
     * @deprecated Use org.sleuthkit.autopsy.coreutils.Version.getVersion
     * instead
     */
    @Deprecated
    public static String getAutopsyVersion() {
        return Version.getVersion();
    }

    /**
     * Check if case is currently open.
     *
     * @return True if a case is open.
     *
     * @deprecated Use isCaseOpen instead.
     */
    @Deprecated
    public static boolean existsCurrentCase() {
        return currentCase != null;
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
    public static PropertyChangeSupport
            getPropertyChangeSupport() {
        return new PropertyChangeSupport(Case.class
        );
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
     * Adds an image to the current case after it has been added to the DB.
     * Sends out event and reopens windows if needed.
     *
     * @param imgPath  The path of the image file.
     * @param imgId    The ID of the image.
     * @param timeZone The time zone of the image.
     *
     * @return
     *
     * @throws org.sleuthkit.autopsy.casemodule.CaseActionException
     *
     * @deprecated As of release 4.0
     */
    @Deprecated
    public Image addImage(String imgPath, long imgId, String timeZone) throws CaseActionException {
        try {
            Image newDataSource = caseDb.getImageById(imgId);
            notifyDataSourceAdded(newDataSource, UUID.randomUUID());
            return newDataSource;
        } catch (TskCoreException ex) {
            throw new CaseActionException(NbBundle.getMessage(this.getClass(), "Case.addImg.exception.msg"), ex);
        }
    }

    /**
     * Gets the time zone(s) of the image data source(s) in this case.
     *
     * @return The set of time zones in use.
     *
     * @deprecated Use getTimeZones instead.
     */
    @Deprecated
    public Set<TimeZone> getTimeZone() {
        return getTimeZones();
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
