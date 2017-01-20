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

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService;
import org.sleuthkit.autopsy.corecomponentinterfaces.AutopsyService.CaseContext;
import org.sleuthkit.autopsy.corecomponentinterfaces.CoreComponentControl;
import org.sleuthkit.autopsy.corecomponentinterfaces.ProgressIndicator;
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
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An Autopsy case. Currently, only one case at a time may be open.
 */
public class Case implements SleuthkitCase.ErrorObserver {

    private static final int NAME_LOCK_TIMOUT_HOURS = 12;
    private static final int DIR_LOCK_TIMOUT_HOURS = 12;
    private static final int RESOURCE_LOCK_TIMOUT_HOURS = 12;
    private static final int MAX_SANITIZED_CASE_NAME_LEN = 47;
    private static final String EVENT_CHANNEL_NAME = "%s-Case-Events"; //NON-NLS
    private static final String CACHE_FOLDER = "Cache"; //NON-NLS
    private static final String EXPORT_FOLDER = "Export"; //NON-NLS
    private static final String LOG_FOLDER = "Log"; //NON-NLS
    private static final String REPORTS_FOLDER = "Reports"; //NON-NLS
    private static final String TEMP_FOLDER = "Temp"; //NON-NLS
    private static final int MIN_SECS_BETWEEN_TSK_ERROR_REPORTS = 60;
    static final String MODULE_FOLDER = "ModuleOutput"; //NON-NLS // RJCTODO
    private static final Logger LOGGER = Logger.getLogger(Case.class.getName());
    private static final AutopsyEventPublisher eventPublisher = new AutopsyEventPublisher();
    private static String appName;
    private static Case currentCase;
    private static CoordinationService.Lock currentCaseDirLock;
    private static ExecutorService singleThreadedExecutor;
    private final CaseMetadata caseMetadata;
    private final SleuthkitCase db;
    private final Services services;
    private CollaborationMonitor collaborationMonitor;
    private boolean hasDataSources;
    private volatile IntervalErrorReportData tskErrorReporter;

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
     * Gets the application name.
     *
     * @return The application name.
     */
    // RJCTODO: Comment on funky way this works, deprecate and make it return Autopsy, make a private method that does funkiness
    public static String getAppName() {
        if ((appName == null) || appName.isEmpty()) {
            appName = WindowManager.getDefault().getMainWindow().getTitle();
        }
        return appName;
    }

    /**
     * Checks if a case name is valid, i.e., does not include any characters
     * that cannot be used in file names.
     *
     * @param caseName The case name.
     *
     * @return True or false.
     */
    public static boolean isValidName(String caseName) {
        /*
         * TODO(JIRA-2221): This should incorporate the validity checks of
         * sanitizeCaseName. RJCTODO: This is no longer necessary, kill off this
         * story
         */
        return !(caseName.contains("\\") || caseName.contains("/") || caseName.contains(":")
                || caseName.contains("*") || caseName.contains("?") || caseName.contains("\"")
                || caseName.contains("<") || caseName.contains(">") || caseName.contains("|"));
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
     */
    @Messages({
        "Case.creationException.illegalCaseName=Could not create case: case name contains illegal characters.",
        "# {0} - exception message", "Case.creationException.couldNotCreateCase=Could not create case: {0}",
        "Case.progressIndicatorTitle.creatingCase=Creating Case",
        "Case.progressIndicatorCancelButton.cancelLabel=Cancel",
        "Case.progressMessage.preparingToCreateCase=Preparing to create case...",
        "Case.progressMessage.acquiringLocks=Acquiring locks...",})
    public static void createCurrentCase(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {

        /*
         * Clean up the display name for the case to make a suitable case name.
         */
        String caseName;
        try {
            caseName = sanitizeCaseName(caseDisplayName);
        } catch (IllegalCaseNameException ex) {
            throw new CaseActionException(Bundle.Case_creationException_illegalCaseName(), ex);
        }
        LOGGER.log(Level.INFO, "Attempting to create case {0} (display name = {1}) in directory = {2}", new Object[]{caseName, caseDisplayName, caseDir}); //NON-NLS

        /*
         * Set up either a visual progress indicator or a logging progress
         * indicator, depending on whether a GUI is present.
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
         * ensures that case directory lock is released in the same thread in
         * which it was acquired, as is required by the coordination service.
         */
        try {
            Future<Void> future = getSingleThreadedExecutor().submit(() -> {
                if (CaseType.SINGLE_USER_CASE == caseType) {
                    createCase(caseDir, caseName, caseDisplayName, caseNumber, examiner, caseType);
                } else {
                    /*
                     * First, acquire an exclusive case name lock to prevent two
                     * nodes from creating the same case at the same time. Next,
                     * acquire a shared case directory lock that will be held as
                     * long as this node has this case open, in order to prevent
                     * deletion of the case by another node. Finally, acquire an
                     * exclusive case resources lock to allow only one node at a
                     * time to create/open/upgrade case resources.
                     */
                    progressIndicator.start(Bundle.Case_creationMessage_acquiringLocks());
                    try (CoordinationService.Lock nameLock = Case.acquireExclusiveCaseNameLock(caseName)) {
                        assert (null != nameLock);
                        acquireSharedCaseDirLock(caseDir);
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(caseName)) {
                            assert (null != resourcesLock);
                            createCase(caseDir, caseName, caseDisplayName, caseNumber, examiner, caseType);
                        }
                    }
                }
                return null;
            });
            if (RuntimeProperties.runningWithGUI()) {
                listener.setCaseActionFuture(future);
                ((ModalDialogProgressIndicator) progressIndicator).setVisible(true);
            }
            future.get();
        } catch (InterruptedException | ExecutionException ex) {
            if (CaseType.SINGLE_USER_CASE == caseType) {
                releaseSharedCaseDirLock(caseName);
            }
            if (ex instanceof InterruptedException) {
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase("Interrupted during locks acquisition"), ex); //RJCTODO
            } else {
                /*
                 * The methods called within the task MUST throw a
                 * CaseActionException with a user-friendly error message
                 * suitable for substitution in the error message below.
                 *
                 * TODO (JIRA-2206): Update Case API to throw more specific
                 * exceptions so that clients can display error messages based
                 * on exception type rather than having localized log messages.
                 */
                throw new CaseActionException(Bundle.Case_creationException_couldNotCreateCase(ex.getCause().getMessage()), ex);
            }
        } finally {
            progressIndicator.finish("");
            if (RuntimeProperties.runningWithGUI()) {
                ((ModalDialogProgressIndicator) progressIndicator).setVisible(false);
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
    })
    public static void openCurrentCase(String caseMetadataFilePath) throws CaseActionException {
        LOGGER.log(Level.INFO, "Opening case with metadata file path {0}", caseMetadataFilePath); //NON-NLS
        try {
            if (!caseMetadataFilePath.endsWith(CaseMetadata.getFileExtension())) {
                throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.open.exception.checkFile.msg", CaseMetadata.getFileExtension()));
            }
            CaseMetadata metadata = new CaseMetadata(Paths.get(caseMetadataFilePath));

            /*
             * Set up either a visual progress indicator or a logging progress
             * indicator, depending on whether a GUI is present.
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
             * Creating a case is always done in the same non-UI thread that
             * will be used later to close the case. If the case is a multi-user
             * case, this ensures that case directory lock is released in the
             * same thread in which it was acquired, as is required by the
             * coordination service.
             */
            CaseType caseType = metadata.getCaseType();
            String caseName = metadata.getCaseName();
            try {
                Future<Void> future = getSingleThreadedExecutor().submit(() -> {
                    if (CaseType.SINGLE_USER_CASE == caseType) {
                        openCase(metadata);
                    } else {
                        /*
                         * First, acquire a shared case directory lock that will
                         * be held as long as this node has this case open, in
                         * order to prevent deletion of the case by another
                         * node. Next, acquire an exclusive case resources lock
                         * to allow only one node at a time to
                         * create/open/upgrade case resources.
                         */
                        acquireSharedCaseDirLock(metadata.getCaseDirectory());
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(metadata.getCaseName())) {
                            assert (null != resourcesLock);
                            openCase(metadata);
                        }
                    }
                    return null;
                });
                future.get();
            } catch (InterruptedException | ExecutionException ex) {
                if (CaseType.SINGLE_USER_CASE == caseType) {
                    releaseSharedCaseDirLock(caseName);
                }
                if (ex instanceof ExecutionException) {
                    /*
                     * The methods called within the task MUST throw a
                     * CaseActionException with a user-friendly error message
                     * suitable for substitution in the error message below.
                     *
                     * RJCTODO: Add TODO comment referencing JIRA
                     */
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex);
                } else {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Interrupted during locks acquisition"), ex);
                }
            }

        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Failed to access case metadata"), ex);
        }
    }

    /**
     * Checks if case is currently open.
     *
     * @return True or false.
     */
    // RJCTODO: Deprecate this, it cannot work
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
     * @throws CaseActionException
     */
    @Messages({
        "# {0} - exception message", "Case.closeException.couldNotCloseCase=Error closing case: {0}"
    })
    public static void closeCurrentCase() throws CaseActionException {
        if (null != currentCase) {
            try {
                /*
                 * Closing a case is always done in the same non-UI thread that
                 * opened/created the case. If the case is a multi-user case,
                 * this ensures that case directory lock is released in the same
                 * thread in which it was acquired, as is required by the
                 * coordination service.
                 */
                Future<Void> future = getSingleThreadedExecutor().submit(() -> {
                    if (CaseType.SINGLE_USER_CASE == currentCase.getCaseType()) {
                        closeTheCase();
                    } else {
                        String caseName = currentCase.getCaseMetadata().getCaseName();
                        /*
                         * Only one node at a time is allowed to close a case,
                         * so acquire an exclusive case resources lock.
                         */
                        try (CoordinationService.Lock resourcesLock = acquireExclusiveCaseResourcesLock(caseName)) {
                            assert (null != resourcesLock);
                            closeTheCase();
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
                future.get();
            } catch (InterruptedException | ExecutionException ex) {
                if (ex instanceof ExecutionException) {
                    /*
                     * The methods called within the task MUST throw a
                     * CaseActionException with a user-friendly error message
                     * suitable for substitution in the error message below.
                     *
                     * RJCTODO: Add TODO comment referencing JIRA
                     */
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase(ex.getCause().getMessage()), ex);
                } else {
                    throw new CaseActionException(Bundle.Case_openException_couldNotOpenCase("Interrupted during locks acquisition"), ex);
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
        return this.db;
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
     * Gets the case name.
     *
     * @return The case name.
     */
    public String getName() {
        return getCaseMetadata().getCaseName();
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
        List<Content> list = db.getRootObjects();
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
        Report report = this.db.addReport(normalizedLocalPath, srcModuleName, reportName);
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
        return this.db.getAllReports();
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
            this.db.deleteReport(report);
            eventPublisher.publish(new AutopsyEvent(Events.REPORT_DELETED.toString(), report, null));
        }
    }

    /**
     * Allows the case database to report internal error conditions in
     * situations where throwing an exception is not appropriate.
     *
     * @param context      The context of the error condition.
     * @param errorMessage An error message.
     */
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
     * Deletes the case folder for this Autopsy case and sets the current case
     * to null. It does not not delete the case database for a multi-user case.
     *
     * @param caseDir The case directory to delete.
     *
     * @throws CaseActionException exception throw if case could not be deleted
     */
    void deleteCase(File caseDir) throws CaseActionException {
        LOGGER.log(Level.INFO, "Deleting case.\ncaseDir: {0}", caseDir); //NON-NLS

        try {
            boolean result = deleteCaseDirectory(caseDir);

            RecentCases.getInstance().removeRecentCase(this.caseMetadata.getCaseName(), this.caseMetadata.getFilePath().toString()); // remove it from the recent case
            Case.changeCurrentCase(null);
            if (result == false) {
                throw new CaseActionException(
                        NbBundle.getMessage(this.getClass(), "Case.deleteCase.exception.msg", caseDir));
            }

        } catch (MissingResourceException | CaseActionException ex) {
            LOGGER.log(Level.SEVERE, "Error deleting the current case dir: " + caseDir, ex); //NON-NLS
            throw new CaseActionException(
                    NbBundle.getMessage(this.getClass(), "Case.deleteCase.exception.msg2", caseDir), ex);
        }
    }

    /**
     * Sanitizes the case name for PostgreSQL database, Solr cores, and ActiveMQ
     * topics. Makes it plain-vanilla enough that each item should be able to
     * use it.
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
     * SQLite: Uses autopsy.db for the database name and follows the Windows
     * naming convention
     *
     * @param caseName A candidate case name.
     *
     * @return The sanitized case name.
     *
     * @throws org.sleuthkit.autopsy.casemodule.Case.IllegalCaseNameException
     */
    // RJCTODO: Get Eugene to Update and also reference story
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
     * Creates a new Autopsy case.
     *
     * @param caseDir         The full path of the case directory.
     * @param caseName        The name of case.
     * @param caseDisplayName The name of case.
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
    @Messages({"Case.creationException.couldNotCreateMetadataFile=Could not create case: failed to create case metadata file."})
    private static void createCase(String caseDir, String caseName, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException {
        /*
         * Create the case directory, if it does not already exist.
         *
         * CAUTION: The reason for the check for the existence of the case
         * directory is not at all obvious. It reflects the assumption that if
         * the case directory already exists, it is because the case is being
         * created using the the "New Case" wizard, which separates the creation
         * of the case directory from the creation of the case, with the idea
         * that if the case directory cannot be created, the user can be asked
         * to supply a different case directory path. This of course creates
         * subtle and undetectable coupling between this code and the wizard
         * code. The desired effect could be accomplished more simply and safely
         * by having this method throw a specific exception to indicate that the
         * case directory could not be created. A FEW specific exception types
         * would in turn allow us to put localized, user-friendly messages in
         * the GUI instead of putting user-friendly, localized messages in the
         * exceptions, which cause them to appear in the logs, where it would be
         * better to have English for readability by the broadest group of
         * developers.
         *
         * TODO (JIRA-2180): Fix the problem described above.
         */
        if (new File(caseDir).exists() == false) {
            Case.createCaseDirectory(caseDir, caseType);
        }

        /*
         * Create a unique keyword search index name, and create a standard
         * (single-user) or unique (multi-user) case database name.
         *
         * TODO (JIRA-2207): The Case class should not be responsible for
         * creating and storing (in the case metadata) unique text index names
         * for Autopsy services. The SolrSearchService, for example, should
         * handle this internally and deletion of Solr cores when a case is
         * deleted should be refactored out of the AutoIngestManager.
         */
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        String indexName = caseName + "_" + dateFormat.format(date);
        String dbName = null;
        if (caseType == CaseType.SINGLE_USER_CASE) {
            dbName = caseDir + File.separator + "autopsy.db"; //NON-NLS
        } else if (caseType == CaseType.MULTI_USER_CASE) {
            dbName = indexName;
        }

        /*
         * Create the case metadata (.aut) file.
         *
         * TODO (JIRA-2207): See above.
         */
        CaseMetadata metadata;
        try {
            metadata = new CaseMetadata(caseDir, caseType, caseName, caseDisplayName, caseNumber, examiner, dbName, indexName);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotCreateMetadataFile(), ex);
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
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            });
            /*
             * SleuthkitCase.newCase throws TskCoreExceptions with user-friendly
             * messages, so propagate the exception message.
             */
            throw new CaseActionException(String.format("Error creating the case database %s for %s ", dbName, caseName), ex); //NON-NLS RJCTODO

        } catch (UserPreferencesException ex) {
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            });
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.databaseConnectionInfo.error.msg"), ex); // RJCTODO
        }

        /*
         * Create the case and make it the current case.
         */
        Case newCase = new Case(metadata, db);
        changeCurrentCase(newCase);

        LOGGER.log(Level.INFO, "Created case {0} in directory = {1}", new Object[]{caseName, caseDir}); //NON-NLS
    }

    /**
     * Opens an existing Autopsy case.
     *
     * @param caseMetadataFilePath The path of the case metadata (.aut) file.
     *
     * @throws CaseActionException if there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    private static void openCase(CaseMetadata metadata) throws CaseActionException {
        /*
         * Open the case database.
         */
        SleuthkitCase db = null;
        try {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                db = SleuthkitCase.openCase(metadata.getCaseDatabasePath());
            } else if (UserPreferences.getIsMultiUserModeEnabled()) {
                try {
                    db = SleuthkitCase.openCase(metadata.getCaseDatabaseName(), UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());

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

        /*
         * Check for the presence of the UI and do things that can only be done
         * with user interaction.
         */
        if (RuntimeProperties.runningWithGUI()) {
            /*
             * If the case database was upgraded for a new schema, notify the
             * user.
             */
            final String backupDbPath = db.getBackupDatabasePath();

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
            Map<Long, String> imgPaths = getImagePaths(db);
            for (Map.Entry<Long, String> entry : imgPaths.entrySet()) {
                long obj_id = entry.getKey();
                String path = entry.getValue();
                boolean fileExists = (new File(path).isFile() || DriveUtils.driveExists(path));

                if (!fileExists) {
                    int ret = JOptionPane.showConfirmDialog(
                            WindowManager.getDefault().getMainWindow(),
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.msg", getAppName(), path),
                            NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.title"),
                            JOptionPane.YES_NO_OPTION);
                    if (ret == JOptionPane.YES_OPTION) {
                        MissingImageDialog.makeDialog(obj_id, db);
                    } else {
                        LOGGER.log(Level.WARNING, "Selected image files don't match old files!"); //NON-NLS
                    }
                }
            }
        }
        Case openedCase = new Case(metadata, db);
        changeCurrentCase(openedCase);
        LOGGER.log(Level.INFO, "Opened case {0} in directory = {1}", new Object[]{metadata.getCaseName(), metadata.getCaseDirectory()}); //NON-NLS RJCTODO
    }

    /**
     * Closes the current case.
     *
     * @throws CaseActionException
     */
    private static void closeTheCase() throws CaseActionException {
        if (null != currentCase) {
            Case caseToClose = currentCase;
            changeCurrentCase(null); // RJCTODO: Refactor
            try {
                caseToClose.getServices().close();
                caseToClose.db.close();
            } catch (IOException ex) {
                throw new CaseActionException(Bundle.Case_closeException_couldNotCloseCase(ex.getMessage()), ex);
            }
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
            currentCaseDirLock = CoordinationService.getServiceForNamespace(CoordinationServiceNamespace.getRoot()).tryGetSharedLock(CoordinationService.CategoryNode.CASES, caseDir, DIR_LOCK_TIMOUT_HOURS, TimeUnit.HOURS);
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
        // RJCTODO: Guard currentCaseLock?
        if (currentCaseDirLock != null) {
            try {
                currentCaseDirLock.release();
                currentCaseDirLock = null;
            } catch (CoordinationService.CoordinationServiceException ex) {
                LOGGER.log(Level.SEVERE, String.format("Failed to release shared case directory lock for %s", caseDir), ex);
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
     * Updates the current case to the given case, firing property change events
     * and updating the UI.
     *
     * @param newCase The new current case or null if there is no new current
     *                case.
     */
    private static void changeCurrentCase(Case newCase) {
        // close the existing case
        Case oldCase = Case.currentCase;
        Case.currentCase = null;
        if (oldCase != null) {
            SwingUtilities.invokeLater(() -> {
                WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            });
            IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);
            completeCaseChange(null); //closes windows, etc   
            if (null != oldCase.tskErrorReporter) {
                oldCase.tskErrorReporter.shutdown(); // stop listening for TSK errors for the old case
                oldCase.tskErrorReporter = null;
            }
            eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), oldCase, null));

            AutopsyService.CaseContext context = new AutopsyService.CaseContext(oldCase, new LoggingProgressIndicator());
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
            currentCase.tskErrorReporter = new IntervalErrorReportData(currentCase, MIN_SECS_BETWEEN_TSK_ERROR_REPORTS,
                    NbBundle.getMessage(Case.class, "IntervalErrorReport.ErrorText"));
            completeCaseChange(currentCase);
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
                    LOGGER.log(Level.SEVERE, "Failed to setup for collaboration", ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.Title"), NbBundle.getMessage(Case.class, "Case.CollaborationSetup.FailNotify.ErrMsg"));
                }
            }

            // RJCTODO: Remove, mention to Eugene.
            AutopsyService.CaseContext context = new AutopsyService.CaseContext(Case.currentCase, new LoggingProgressIndicator());
            String serviceName = "";
            for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class
            )) {
                try {
                    serviceName = service.getServiceName();
                    service.openCaseResources(context);
                } catch (AutopsyService.AutopsyServiceException ex) {
                    Case.LOGGER.log(Level.SEVERE, String.format("%s service failed to open case resources", serviceName), ex);
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

    /**
     * Updates the UI and does miscellaneous other things to complete changing
     * the current case.
     *
     * @param newCase The new current case or null if there is no new current
     *                case.
     */
    private static void completeCaseChange(Case newCase) {
        LOGGER.log(Level.INFO, "Changing Case to: {0}", newCase); //NON-NLS
        if (newCase != null) { // new case is open

            // clear the temp folder when the case is created / opened
            Case.clearTempFolder();

            if (RuntimeProperties.runningWithGUI()) {
                // enable these menus
                SwingUtilities.invokeLater(() -> {
                    CallableSystemAction.get(AddImageAction.class
                    ).setEnabled(true);
                    CallableSystemAction
                            .get(CaseCloseAction.class
                            ).setEnabled(true);
                    CallableSystemAction
                            .get(CasePropertiesAction.class
                            ).setEnabled(true);
                    CallableSystemAction
                            .get(CaseDeleteAction.class
                            ).setEnabled(true); // Delete Case menu
                    CallableSystemAction
                            .get(OpenTimelineAction.class
                            ).setEnabled(true);

                    if (newCase.hasData()) {
                        // open all top components
                        CoreComponentControl.openCoreWindows();
                    } else {
                        // close all top components
                        CoreComponentControl.closeCoreWindows();
                    }
                    addCaseNameToMainWindowTitle(currentCase.getName());
                });
            } else {
                SwingUtilities.invokeLater(() -> {
                    Frame f = WindowManager.getDefault().getMainWindow();
                    f.setTitle(getAppName()); // set the window name to just application name           
                });
            }

        } else { // case is closed
            SwingUtilities.invokeLater(() -> {
                if (RuntimeProperties.runningWithGUI()) {

                    // close all top components first
                    CoreComponentControl.closeCoreWindows();

                    // disable these menus
                    CallableSystemAction
                            .get(AddImageAction.class
                            ).setEnabled(false); // Add Image menu
                    CallableSystemAction
                            .get(CaseCloseAction.class
                            ).setEnabled(false); // Case Close menu
                    CallableSystemAction
                            .get(CasePropertiesAction.class
                            ).setEnabled(false); // Case Properties menu
                    CallableSystemAction
                            .get(CaseDeleteAction.class
                            ).setEnabled(false); // Delete Case menu
                    CallableSystemAction
                            .get(OpenTimelineAction.class
                            ).setEnabled(false);
                }

                //clear pending notifications
                MessageNotifyUtil.Notify.clear();
                Frame f = WindowManager.getDefault().getMainWindow();
                f.setTitle(getAppName()); // set the window name to just application name
            });

            //try to force gc to happen
            System.gc();
            System.gc();
        }

        //log memory usage after case changed
        LOGGER.log(Level.INFO, PlatformUtil.getAllMemUsageInfo());

    }

    /**
     * Empties the temp subdirectory for the current case.
     */
    private static void clearTempFolder() {
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
     * Changes the title of the main window to include the case name.
     *
     * @param newCaseName The name of the case.
     */
    private static void addCaseNameToMainWindowTitle(String newCaseName) {
        if (!newCaseName.isEmpty()) {
            Frame f = WindowManager.getDefault().getMainWindow();
            f.setTitle(newCaseName + " - " + getAppName());
        }
    }

    /**
     * Get the single thread executor for the current case, creating it if
     * necessary.
     *
     * @return The executor
     */
    private static ExecutorService getSingleThreadedExecutor() { // RJCTODO: Does this need synch?
        if (null == singleThreadedExecutor) {
            singleThreadedExecutor = Executors.newSingleThreadExecutor();
        }
        return singleThreadedExecutor;

    }

    /**
     * Constructs an Autopsy case.
     */
    private Case(CaseMetadata caseMetadata, SleuthkitCase db) {
        this.caseMetadata = caseMetadata;
        this.db = db;
        this.services = new Services(db);
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

        private Future<Void> caseActionFuture;
        private CaseContext caseContext;

        private void setCaseActionFuture(Future<Void> caseActionFuture) {
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
            Image newDataSource = db.getImageById(imgId);
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

    /**
     * Closes this Autopsy case.
     *
     * @throws CaseActionException if there is a problem closing the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     * @deprecated Use Case.closeCurrentCase instead. instead.
     */
    @Deprecated
    public void closeCase() throws CaseActionException {
        Case.closeCurrentCase();
    }

    @Deprecated
    public static final String propStartup = "LBL_StartupDialog"; //NON-NLS

}
