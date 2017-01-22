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
import javax.annotation.concurrent.GuardedBy;
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
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService.CaseContext;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.corecomponentinterfaces.ProgressIndicator;
import org.sleuthkit.autopsy.coreutils.DriveUtils;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;
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

    /*
     * Constants.
     */
    private static final int NAME_LOCK_TIMOUT_HOURS = 12;
    private static final int SHARED_DIR_LOCK_TIMOUT_HOURS = 12;
    private static final int RESOURCE_LOCK_TIMOUT_HOURS = 12;
    private static final int MAX_SANITIZED_CASE_NAME_LEN = 47;
    private static final String EVENT_CHANNEL_NAME = "%s-Case-Events"; //NON-NLS
    private static final String CACHE_FOLDER = "Cache"; //NON-NLS
    private static final String EXPORT_FOLDER = "Export"; //NON-NLS
    private static final String LOG_FOLDER = "Log"; //NON-NLS
    private static final String REPORTS_FOLDER = "Reports"; //NON-NLS
    private static final String TEMP_FOLDER = "Temp"; //NON-NLS
    private static final int MIN_SECS_BETWEEN_TSK_ERROR_REPORTS = 60;
    private static final String MODULE_FOLDER = "ModuleOutput"; //NON-NLS // RJCTODO
    private static final Logger LOGGER = Logger.getLogger(Case.class.getName());
    private static final ExecutorService caseActionExecutor = Executors.newSingleThreadExecutor();

    /*
     * A single-threaded executor used to guarantee that the case directory lock
     * for the current case is acquired and released in the same thread, as
     * required by the coordination service.
     */
    private static ExecutorService caseLockingExecutor;

    /*
     * The following group of fields is state associated with the "current case"
     * concept. This state is managed by a set of static methods that could be
     * refactored into a case manager class, which would be a step towards
     * supporting multiple open cases. RJCTODO: Write a story about this
     *
     * TODO (JIRA-2231): Make the application name a RuntimeProperties item set
     * by Installers.
     */
    @GuardedBy("Case.class")
    private static String appName;
    /*
     * The one and only open case.
     */
    @GuardedBy("Case.class")
    private static Case currentCase;
    /*
     * A coordination service lock on the case directory of the one and only
     * open case. Used to prevent deletion of a multi-user case by this node if
     * it is open in another node.
     */
    @GuardedBy("Case.class")
    private static CoordinationService.Lock currentCaseDirLock; // RJCTODO: Move into case
    /*
     * The collaboration monitor for the current case. It is specific to the
     * current case because it uses an event channel with a name derived from
     * the name of the current case.
     */
    @GuardedBy("Case.class")
    private static CollaborationMonitor collaborationMonitor; // RJCTODO: Move into case
    /*
     * The publisher for case events, both locally and, if the case is a
     * multi-user case, to other nodes. This is part of the state for the
     * current case because it opens and closes an event channel with a name
     * derived from the name of the current case.
     */
    @GuardedBy("Case.class")
    private static final AutopsyEventPublisher eventPublisher = new AutopsyEventPublisher();

    /*
     * Case instance data.
     */
    private CaseMetadata caseMetadata;
    private SleuthkitCase caseDb;
    private SleuthkitErrorReporter sleuthkitErrorReporter;
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
         * @return The dis[play name.
         */
        @Messages({"Case_caseType_singleUser=Single-user case", "Case_caseType_multiUser=Multi-user case"})
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
         * Tests the equality of the type name of this case type with a case
         * type name.
         *
         * @param otherTypeName A case type name
         *
         * @return True or false
         *
         * @deprecated Do not use.
         */
        @Deprecated
        public boolean equalsName(String otherTypeName) {
            return (otherTypeName == null) ? false : typeName.equals(otherTypeName);
        }

    };

    /**
     * An enumeration of the events (property change events) a case may publish
     * (fire).
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
         * PropertyChangeEvent is the old number (type: String), the new value
         * is the new case number (type: String).
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
         * access event data.
         */
        ADDING_DATA_SOURCE,
        /**
         * A failure to add a new data source to the current case has occurred.
         * The old and new values of the PropertyChangeEvent are null. Cast the
         * PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent
         * to access event data.
         */
        ADDING_DATA_SOURCE_FAILED,
        /**
         * A new data source has been added to the current case. The old value
         * of the PropertyChangeEvent is null, the new value is the newly-added
         * data source (type: Content). Cast the PropertyChangeEvent to
         * org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent to
         * access event data.
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
         * A report has been added to the current case. The old value of the
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
         * case. The old value of the PropertyChangeEvent is is the tag info
         * (type:
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
     *
     * RJCTODO: Not really needed any more with display name concept; what
     * happens in auto ingest? Make sure these things are in sanitize...etc.
     *
     * @param caseName The case name.
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
        "Case.creationException.illegalCaseName=Could not create case: case name contains illegal characters.",
        "# {0} - exception message", "Case.creationException.couldNotCreateCase=Could not create case: {0}",
        "Case.progressIndicatorTitle.creatingCase=Creating Case",
        "Case.progressIndicatorCancelButton.cancelLabel=Cancel",
        "Case.progressMessage.preparingToCreateCase=Preparing to create case...",
        "Case.progressMessage.acquiringLocks=Acquiring locks...",})
    public synchronized static void createCurrentCase(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
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
            throw new CaseActionException(Bundle.Case_creationException_illegalCaseName(), ex);
        }
        LOGGER.log(Level.INFO, "Attempting to create case {0} (display name = {1}) in directory = {2}", new Object[]{caseName, caseDisplayName, caseDir}); //NON-NLS

        /*
         * Set up either a GUI progress indicator or a logging progress
         * indicator.
         */
        CancelButtonListener listener = new CancelButtonListener();
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_creatingCase(), new String[]{Bundle.Case_progressIndicatorCancelButton_cancelLabel()}, Bundle.Case_progressIndicatorCancelButton_cancelLabel(), null, listener);
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        progressIndicator.start(Bundle.Case_progressMessage_preparingToCreateCase());

        /*
         * Creating a case is always done in the same non-UI thread that will be
         * used later to close the case. If the case is a multi-user case, this
         * ensures that case directory lock that is held as long as the case is
         * open is released in the same thread in which it was acquired, as is
         * required by the coordination service.
         */
        try {
            Future<Case> innerFuture = getCaseLockingExecutor().submit(() -> {
                Case newCase = new Case();
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
                         * create/open/upgrade the case resources.
                         */
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(caseName)) {
                            assert (null != resourcesLock);
                            newCase.open(caseDir, caseName, caseDisplayName, caseNumber, examiner, caseType, progressIndicator);
                        }
                    }
                }
                return newCase;
            });
            if (RuntimeProperties.runningWithGUI()) {
                listener.setCaseActionFuture(innerFuture);
                SwingUtilities.invokeLater(()
                        -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
            }
            currentCase = innerFuture.get();
            LOGGER.log(Level.INFO, "Created case {0} in directory = {1}", new Object[]{caseName, caseDir}); //NON-NLS
            if (RuntimeProperties.runningWithGUI()) {
                updateGUIForCaseOpened();
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));

        } catch (InterruptedException | ExecutionException ex) {
            if (CaseType.MULTI_USER_CASE == caseType) {
                releaseSharedCaseDirLock(caseName);
            }
            if (ex instanceof InterruptedException) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase("Interrupted during locks acquisition"), ex); //RJCTODO
            } else {
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase(ex.getCause().getMessage()), ex);
            }
        } finally {
            progressIndicator.finish(""); // RJCTODO: Is this right message? Nope
            if (RuntimeProperties.runningWithGUI()) {
                SwingUtilities.invokeLater(()
                        -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
            }
        }
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
     */
    @Messages({
        "# {0} - exception message", "Case.openException.couldNotOpenCase=Could not open case: {0}",
        "Case.progressIndicatorTitle.openingCase=Opening Case",
        "Case.progressMessage.preparingToOpenCase=Preparing to open case...",
        "Case.creationException.couldNotCreateMetadataFile=Could not create case: failed to create case metadata file."
    })
    public synchronized static void openCurrentCase(String caseMetadataFilePath) throws CaseActionException {
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

        LOGGER.log(Level.INFO, "Opening case with metadata file path {0}", caseMetadataFilePath); //NON-NLS
        try {
            CaseMetadata metadata = new CaseMetadata(Paths.get(caseMetadataFilePath));

            /*
             * Set up either a GUI progress indicator or a logging progress
             * indicator.
             */
            CancelButtonListener listener = new CancelButtonListener();
            ProgressIndicator progressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_openingCase(), new String[]{Bundle.Case_progressIndicatorCancelButton_cancelLabel()}, Bundle.Case_progressIndicatorCancelButton_cancelLabel(), null, listener);
            } else {
                progressIndicator = new LoggingProgressIndicator();
            }
            progressIndicator.start(Bundle.Case_progressMessage_preparingToOpenCase());

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
                    Case openedCase = new Case();
                    if (CaseType.SINGLE_USER_CASE == caseType) {
                        openedCase.open(metadata, progressIndicator);
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
                         * create/open/upgrade case resources.
                         */
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(metadata.getCaseName())) {
                            assert (null != resourcesLock);
                            openedCase.open(metadata, progressIndicator);
                        }
                    }
                    return openedCase;
                });
                if (RuntimeProperties.runningWithGUI()) {
                    listener.setCaseActionFuture(future);
                    SwingUtilities.invokeLater(()
                            -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
                }
                future.get();
                currentCase = future.get();
                LOGGER.log(Level.INFO, "Opened case {0} in directory = {1}", new Object[]{metadata.getCaseName(), metadata.getCaseDirectory()}); //NON-NLS                
                if (RuntimeProperties.runningWithGUI()) {
                    updateGUIForCaseOpened();
                }
                eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));
            } catch (InterruptedException | ExecutionException ex) {
                if (CaseType.SINGLE_USER_CASE == caseType) {
                    releaseSharedCaseDirLock(caseName);
                }
                if (ex instanceof ExecutionException) {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex);
                } else {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Interrupted during locks acquisition"), ex);
                }
            } finally {
                progressIndicator.finish(""); // RJCTODO: Is this right message?
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(()
                            -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
                }
            }
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Failed to access case metadata"), ex);
        }
    }

    /**
     * Gets the current case, if there is one.
     *
     * @return The current case.
     *
     * @throws IllegalStateException if there is no current case.
     */
    public synchronized static Case getCurrentCase() {
        if (currentCase != null) {
            return currentCase;
        } else {
            // This is for backawards compatibility. RJCTODO: Reference story
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
        "Case.caseActionException.noCurrentCase=There is no open case",
        "# {0} - exception message", "Case.closeException.couldNotCloseCase=Error closing case: {0}",
        "Case.progressIndicatorTitle.closingCase=Closing Case",
        "Case.progressMessage.preparingToCloseCase=Preparing to close case...",})
    public synchronized static void closeCurrentCase() throws CaseActionException {
        if (null == currentCase) {
            return;
        }

        /*
         * Set up either a GUI progress indicator or a logging progress
         * indicator.
         */
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_closingCase(), null, null, null, null);
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        progressIndicator.start(Bundle.Case_progressMessage_preparingToCloseCase());

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
                    try {
                        currentCase.close(progressIndicator);
                    } finally {
                        /*
                         * Always release the case directory lock that was
                         * acquired when the case was opened.
                         */
                        releaseSharedCaseDirLock(caseName);
                    }
                }
                currentCase = null;
                return null;
            });
            if (RuntimeProperties.runningWithGUI()) {
                SwingUtilities.invokeLater(()
                        -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(true));
            }
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            if (ex instanceof ExecutionException) {
                throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex);
            } else {
                throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Interrupted during locks acquisition"), ex);
            }
        } finally {
            currentCase = null;
            progressIndicator.finish(""); // RJCTODO: Is this right message? Nope
            if (RuntimeProperties.runningWithGUI()) {
                SwingUtilities.invokeLater(()
                        -> ((ModalDialogProgressIndicator) progressIndicator).setVisible(false));
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
    public synchronized static void deleteCurrentCase() throws CaseActionException {
        if (null == currentCase) {
            throw new CaseActionException(Bundle.Case_caseActionException_noCurrentCase());
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
    public static synchronized void deleteCase(CaseMetadata metadata) throws CaseActionException {
        if (null != currentCase && 0 == metadata.getCaseDirectory().compareTo(currentCase.getCaseDirectory())) {
            // RJCTODO: Throw
        }

        try {
            /*
             * Set up either a GUI progress indicator or a logging progress
             * indicator.
             */
            ProgressIndicator progressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                progressIndicator = new ModalDialogProgressIndicator(Bundle.Case_progressIndicatorTitle_closingCase(), null, null, null, null); // RJCTODO
            } else {
                progressIndicator = new LoggingProgressIndicator();
            }
            progressIndicator.start(Bundle.Case_progressMessage_preparingToCloseCase());
            Future<Void> future = getCaseLockingExecutor().submit(() -> {
                if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                    cleanupDeletedCase(metadata);
                } else {
                    String caseName = currentCase.getCaseMetadata().getCaseName();
                    /*
                     * First, acquire an exclusive case directory lock. The case
                     * cannot be deleted if another node has it open.
                     */
                    try (CoordinationService.Lock dirLock = acquireExclusiveLock(metadata.getCaseDirectory())) {
                        assert (null != dirLock);
                        /*
                         * Try to unload/delete the Solr core from the Solr
                         * server. Do this before deleting the case directory
                         * because the index files are in the case directory and
                         * the deletion will fail if the core is not unloaded
                         * first.
                         */
                        // RJCTODO: Need a method for this in keyword search service, code is in AIM at the moment

                        /*
                         * Delete the case database from the database server.
                         */
                        CaseDbConnectionInfo db = UserPreferences.getDatabaseConnectionInfo();
                        Class.forName("org.postgresql.Driver"); //NON-NLS
                        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://" + db.getHost() + ":" + db.getPort() + "/postgres", db.getUserName(), db.getPassword()); //NON-NLS
                                Statement statement = connection.createStatement();) {
                            String deleteCommand = "DROP DATABASE \"" + metadata.getCaseDatabaseName() + "\""; //NON-NLS
                            statement.execute(deleteCommand);
                        }

                        cleanupDeletedCase(metadata);
                    }
                }
                return null;
            });
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            if (ex instanceof ExecutionException) {
                throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex); // RJCTODO
            } else {
                throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Interrupted during locks acquisition"), ex); // RJCTODO
            }
        }
    }

    /**
     * Sanitizes the case name for use as a PostgreSQL database name and in
     * ActiveMQ event channle (topic) names.
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
     * Deletes a case directory.
     *
     * @param casePath A case directory path.
     *
     * @return True if the deletion succeeded, false otherwise.
     */
    // RJCTODO: Get rid of this foolishness
    static boolean deleteCaseDirectory(File casePath) {
        LOGGER.log(Level.INFO, "Deleting case directory: {0}", casePath.getAbsolutePath()); //NON-NLS
        return FileUtil.deleteDir(casePath);
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
            LOGGER.log(Level.SEVERE, "Error getting image paths", ex); //NON-NLS
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
                LOGGER.log(Level.SEVERE, "Unexpected exception getting main window title", ex);
            }
        }
    }

    // RJCTODO
    private static void cleanupDeletedCase(CaseMetadata metadata) {
        /*
         * Delete the case directory.
         */
        if (!FileUtil.deleteDir(new File(metadata.getCaseDirectory()))) {
            LOGGER.log(Level.SEVERE, "Failed to fully delete case directory {0}", metadata.getCaseDirectory());
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
        // RJCTODO: Guard currentCaseLock? Yep!
        if (currentCaseDirLock != null) {
            try {
                currentCaseDirLock.release();
                currentCaseDirLock = null;
            } catch (CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to release shared case directory lock for %s", caseDir), ex);
            }
        }
    }

    private static CoordinationService.Lock acquireExclusiveLock(String nodePath) throws CaseActionException {
        try {
            CoordinationService.Lock lock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetExclusiveLock(CoordinationService.CategoryNode.CASES, nodePath);
            if (null == lock) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireDirLock()); // RJCTODO
            }
            return lock;
        } catch (CoordinationServiceException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireNameLock(), ex); // RJCTODO
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

            // RJCTODO: Test both of these conditions
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
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.msg", appName, path), // RJCTODO: Don't use app name here
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.title"),
                            JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION) {
                        MissingImageDialog.makeDialog(obj_id, caseDb);
                    } else {
                        LOGGER.log(Level.WARNING, "Selected image files don't match old files!"); //NON-NLS // RJCTODO
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
                CallableSystemAction.get(DeleteCurrentCaseAction.class).setEnabled(true);
                CallableSystemAction.get(OpenTimelineAction.class).setEnabled(true);

                /*
                 * Add the case to the recent cases tracker that supplies a list
                 * of recent cases to the recent cases menu item and the
                 * open/create case dialog.
                 */
                RecentCases.getInstance().addRecentCase(currentCase.getName(), currentCase.getCaseMetadata().getFilePath().toString()); // update the recent cases

                /*
                 * Open the top components (windows within the main application
                 * window).
                 */
                if (currentCase.hasData()) {
                    CoreComponentControl.openCoreWindows();
                }

                /*
                 * Reset the main window title to be [application name] - [case
                 * name], instead of just the application name.
                 */
                addCaseNameToMainWindowTitle(currentCase.getName());
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
                CallableSystemAction.get(DeleteCurrentCaseAction.class).setEnabled(false);
                CallableSystemAction.get(OpenTimelineAction.class).setEnabled(false);

                /*
                 * Clear the notifications in the notfier component in the lower
                 * right hand corner of the main application window.
                 */
                MessageNotifyUtil.Notify.clear();

                /*
                 * Reset the main window title to be just the application name,
                 * instead of [application name] - [case name].
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
    // RJCTODO: Check all uses of this!
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
            LOGGER.log(Level.SEVERE, "Error getting data source time zones", ex); //NON-NLS
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
                LOGGER.log(Level.SEVERE, "Error accessing case database", ex); //NON-NLS
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
     * Updates the case name.
     *
     * This should not be called from the EDT.
     *
     * @param oldCaseName The old case name.
     * @param oldPath     The old path name.
     * @param newCaseName The new case name.
     * @param newPath     The new case path.
     */
    void updateCaseName(String oldCaseName, String oldPath, String newCaseName, String newPath) throws CaseActionException {
        try {
            caseMetadata.setCaseName(newCaseName);
            eventPublisher.publish(new AutopsyEvent(Events.NAME.toString(), oldCaseName, newCaseName));
            SwingUtilities.invokeLater(() -> {
                try {
                    RecentCases.getInstance().updateRecentCase(oldCaseName, oldPath, newCaseName, newPath); // update the recent case 
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
        "Case.progressMessage.creatingCaseMetadataFile=Creating case metadata file..."
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
            if (caseType == CaseType.SINGLE_USER_CASE) {
                dbName = Paths.get(caseDir, "autopsy.db").toString(); // RJCTODO: Move to a const, fix non-relative path, need opening db message here
                this.caseDb = SleuthkitCase.newCase(dbName);
            } else if (caseType == CaseType.MULTI_USER_CASE) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
                Date date = new Date();
                dbName = caseName + "_" + dateFormat.format(date);
                this.caseDb = SleuthkitCase.newCase(dbName, UserPreferences.getDatabaseConnectionInfo(), caseDir);
            }
        } catch (TskCoreException ex) {
            throw new CaseActionException(String.format("Error creating the case database %s: %s", dbName, ex.getMessage()), ex); //NON-NLS RJCTOD
        } catch (UserPreferencesException ex) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex); // RJCTODO
        }

        /*
         * Create the case metadata (.aut) file.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseMetadataFile());
        try {
            this.caseMetadata = new CaseMetadata(caseDir, caseType, caseName, caseDisplayName, caseNumber, examiner, dbName);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotCreateMetadataFile(), ex);
        }
        open(progressIndicator);
    }

    /**
     * Opens an exsiting case. RJCTODO
     *
     * @param metadata
     * @param progressIndicator
     *
     * @throws CaseActionException
     */
    private void open(CaseMetadata metadata, ProgressIndicator progressIndicator) throws CaseActionException {
        this.caseMetadata = metadata;

        /*
         * Open the case database.
         */
        try {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                this.caseDb = SleuthkitCase.openCase(metadata.getCaseDatabasePath());
            } else if (UserPreferences.getIsMultiUserModeEnabled()) {
                try {
                    this.caseDb = SleuthkitCase.openCase(metadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());

                } catch (UserPreferencesException ex) {
                    throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex); // RJCTODO: What does this say?
                }
            } else {
                throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.open.exception.multiUserCaseNotEnabled"));
            }
        } catch (TskCoreException ex) {
            // RJCTODO: Need proper exception message
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex);
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
        "Case.progressMessage.clearingTempDirectory=Clearing case temp directory...",
        "Case.progressMessage.openingCaseLevelServices=Opening case-level services...",
        "Case.progressMessage.openingApplicationServiceResources=Opening case-specific application service resources...",})
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
        String serviceName = "";
        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class)) {
            try {
                serviceName = service.getServiceName();
                service.openCaseResources(context);
            } catch (AutopsyService.AutopsyServiceException ex) {
                // RJCTODO: Pop up error here?
                Case.LOGGER.log(Level.SEVERE, String.format("%s service failed to open case resources", serviceName), ex);
            }
        }

        /*
         * If this case is a multi-user case, set up for communication with
         * other nodes.
         */
        if (CaseType.MULTI_USER_CASE == this.caseMetadata.getCaseType()) {
            try {
                eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, this.getCaseMetadata().getCaseName()));
                collaborationMonitor = new CollaborationMonitor();
            } catch (AutopsyEventException | CollaborationMonitor.CollaborationMonitorException ex) {
                // RJCTODO: Explain why this is not a fatal error
                LOGGER.log(Level.SEVERE, "Failed to setup for collaboration", ex); //NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.Title"), NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.ErrMsg"));
            }
        }

    }

    /*
     * RJCTODO
     */
    private void close(ProgressIndicator progressIndicator) {
        /*
         * Cancel all ingest jobs.
         *
         * TODO (JIRA-2227): Case closing should wait for ingest to stop to
         * avoid changing the case database while ingest is still using it.
         */
        IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);

        /*
         * Notify all local case event subscribers that the case is closed and
         * all interactions with the current case are no longer permitted.
         */
        eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), currentCase, null));

        /*
         * Stop sending/receiving case events to and from other nodes if this is
         * a multi-user case.
         */
        if (CaseType.MULTI_USER_CASE == currentCase.getCaseType()) {
            if (null != collaborationMonitor) {
                collaborationMonitor.shutdown();
            }
            eventPublisher.closeRemoteEventChannel();
        }

        /*
         * Allow all registered applications ervices providers to close
         * resources related to the case.
         */
        AutopsyService.CaseContext context = new AutopsyService.CaseContext(currentCase, progressIndicator);
        String serviceName = "";
        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class)) {
            try {
                serviceName = service.getServiceName();
                if (!serviceName.equals("Solr Keyword Search Service")) {
                    service.closeCaseResources(context);
                }
            } catch (AutopsyService.AutopsyServiceException ex) {
                Case.LOGGER.log(Level.SEVERE, String.format("%s service failed to close case resources", serviceName), ex);
            }
        }

        /*
         * Close the case-level services.
         */
        try {
            currentCase.getServices().close();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, String.format("Error closing internal case services for %s", getName()), ex);
        }

        /*
         * Close the case database
         */
        this.caseDb.close();

        /*
         * Disconnect the SleuthKit layer error reporter.
         */
        this.caseDb.removeErrorObserver(this.sleuthkitErrorReporter);

        // RJCTODO: Reset the log directory?
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

    private final static class IllegalCaseNameException extends Exception {

        private static final long serialVersionUID = 1L;

        private IllegalCaseNameException(String message) {
            super(message);
        }

        private IllegalCaseNameException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final static class CancelButtonListener implements ActionListener {

        private Future<?> caseActionFuture;
        private CaseContext caseContext;

        private void setCaseActionFuture(Future<?> caseActionFuture) {
            this.caseActionFuture = caseActionFuture;
        }

        private void setCaseContext(CaseContext caseContext) { // RJCTODO: USe this
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
     * @deprecated
     */
    @Deprecated
    public static void create(String caseDir, String caseDisplayName, String caseNumber, String examiner) throws CaseActionException {
        createCurrentCase(caseDir, caseDisplayName, caseNumber, examiner, CaseType.SINGLE_USER_CASE);
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
     * @deprecated
     */
    @Deprecated
    public static void create(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
        createCurrentCase(caseDir, caseDisplayName, caseNumber, examiner, caseType);
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
     * @deprecated
     */
    @Deprecated
    public static void open(String caseMetadataFilePath) throws CaseActionException {
        openCurrentCase(caseMetadataFilePath);
    }

    /**
     * Checks if case is currently open.
     *
     * @return True or false.
     *
     * @deprecated Do not use, this method is not relaible.
     */
    @Deprecated
    public static boolean isCaseOpen() {
        return currentCase != null;
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
     * @deprecated As of release 4.0
     */
    @Deprecated
    public Image addImage(String imgPath, long imgId, String timeZone) throws CaseActionException {
        try {
            Image newDataSource = caseDb.getImageById(imgId);
            notifyDataSourceAdded(newDataSource, UUID.randomUUID());
            return newDataSource;
        } catch (Exception ex) {
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

    @Deprecated
    public static final String propStartup = "LBL_StartupDialog"; //NON-NLS

}
