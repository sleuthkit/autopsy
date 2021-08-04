/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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

import org.sleuthkit.autopsy.featureaccess.FeatureAccessUtils;
import com.google.common.annotations.Beta;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.Cursor;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.StringUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.actions.OpenOutputFolderAction;
import org.sleuthkit.autopsy.appservices.AutopsyService;
import org.sleuthkit.autopsy.appservices.AutopsyService.CaseContext;
import org.sleuthkit.autopsy.casemodule.CaseMetadata.CaseMetadataException;
import org.sleuthkit.autopsy.datasourcesummary.ui.DataSourceSummaryAction;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceEvent;
import org.sleuthkit.autopsy.casemodule.events.AddingDataSourceFailedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.BlackBoardArtifactTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.CommentChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.DataSourceNameChangedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsAddedToPersonEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsUpdatedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.HostsRemovedFromPersonEvent;
import org.sleuthkit.autopsy.casemodule.events.OsAccountsAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.OsAccountsUpdatedEvent;
import org.sleuthkit.autopsy.casemodule.events.OsAccountsDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.OsAcctInstancesAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.PersonsAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.PersonsUpdatedEvent;
import org.sleuthkit.autopsy.casemodule.events.PersonsDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.ReportAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.TagNamesEvent.TagNamesAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.TagNamesEvent.TagNamesDeletedEvent;
import org.sleuthkit.autopsy.casemodule.events.TagNamesEvent.TagNamesUpdatedEvent;
import org.sleuthkit.autopsy.casemodule.events.TagSetsEvent.TagSetsAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.TagSetsEvent.TagSetsDeletedEvent;
import org.sleuthkit.autopsy.casemodule.multiusercases.CaseNodeData.CaseNodeDataException;
import org.sleuthkit.autopsy.casemodule.multiusercases.CoordinationServiceUtils;
import org.sleuthkit.autopsy.casemodule.services.Services;
import org.sleuthkit.autopsy.commonpropertiessearch.CommonAttributeSearchAction;
import org.sleuthkit.autopsy.communications.OpenCommVisualizationToolAction;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CategoryNode;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.CoordinationServiceException;
import org.sleuthkit.autopsy.coordinationservice.CoordinationService.Lock;
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
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.hosts.OpenHostsAction;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.events.AutopsyEventException;
import org.sleuthkit.autopsy.events.AutopsyEventPublisher;
import org.sleuthkit.autopsy.discovery.ui.OpenDiscoveryAction;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.machinesettings.UserMachinePreferences;
import org.sleuthkit.autopsy.progress.LoggingProgressIndicator;
import org.sleuthkit.autopsy.progress.ModalDialogProgressIndicator;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.events.TimelineEventAddedEvent;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Person;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.SleuthkitCaseAdminUtil;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskEvent;
import org.sleuthkit.datamodel.TskUnsupportedSchemaVersionException;

/**
 * An Autopsy case. Currently, only one case at a time may be open.
 */
public class Case {

    private static final int CASE_LOCK_TIMEOUT_MINS = 1;
    private static final int CASE_RESOURCES_LOCK_TIMEOUT_HOURS = 1;
    private static final String APP_NAME = UserPreferences.getAppName();
    private static final String TEMP_FOLDER = "Temp";
    private static final String SINGLE_USER_CASE_DB_NAME = "autopsy.db";
    private static final String EVENT_CHANNEL_NAME = "%s-Case-Events"; //NON-NLS
    private static final String CACHE_FOLDER = "Cache"; //NON-NLS
    private static final String EXPORT_FOLDER = "Export"; //NON-NLS
    private static final String LOG_FOLDER = "Log"; //NON-NLS
    private static final String REPORTS_FOLDER = "Reports"; //NON-NLS
    private static final String CONFIG_FOLDER = "Config"; // NON-NLS
    private static final String MODULE_FOLDER = "ModuleOutput"; //NON-NLS
    private static final String CASE_ACTION_THREAD_NAME = "%s-case-action";
    private static final String CASE_RESOURCES_THREAD_NAME = "%s-manage-case-resources";
    private static final String NO_NODE_ERROR_MSG_FRAGMENT = "KeeperErrorCode = NoNode";
    private static final Logger logger = Logger.getLogger(Case.class.getName());
    private static final AutopsyEventPublisher eventPublisher = new AutopsyEventPublisher();
    private static final Object caseActionSerializationLock = new Object();
    private static Future<?> backgroundOpenFileSystemsFuture = null;
    private static final ExecutorService openFileSystemsExecutor
            = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("case-open-file-systems-%d").build());
    private static volatile Frame mainFrame;
    private static volatile Case currentCase;
    private final CaseMetadata metadata;
    private volatile ExecutorService caseActionExecutor;
    private CoordinationService.Lock caseLock;
    private SleuthkitCase caseDb;
    private final SleuthkitEventListener sleuthkitEventListener;
    private CollaborationMonitor collaborationMonitor;
    private Services caseServices;

    private volatile boolean hasDataSource = false;
    private volatile boolean hasData = false;

    /*
     * Get a reference to the main window of the desktop application to use to
     * parent pop up dialogs and initialize the application name for use in
     * changing the main window title.
     */
    static {
        WindowManager.getDefault().invokeWhenUIReady(() -> {
            mainFrame = WindowManager.getDefault().getMainWindow();
        });
    }

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
         *
         * @deprecated CASE_DETAILS event should be used instead
         */
        @Deprecated
        NAME,
        /**
         * The number of the current case has changed. The old value of the
         * PropertyChangeEvent is the old case number (type: String), the new
         * value is the new case number (type: String).
         *
         * @deprecated CASE_DETAILS event should be used instead
         */
        @Deprecated
        NUMBER,
        /**
         * The examiner associated with the current case has changed. The old
         * value of the PropertyChangeEvent is the old examiner (type: String),
         * the new value is the new examiner (type: String).
         *
         * @deprecated CASE_DETAILS event should be used instead
         */
        @Deprecated
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
         * A new data source or series of data sources have been added to the
         * current case. The old value of the PropertyChangeEvent is null, the
         * new value is the newly-added data source (type: Content). Cast the
         * PropertyChangeEvent to
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
         * A data source's name has changed. The new value of the property
         * change event is the new name.
         */
        DATA_SOURCE_NAME_CHANGED,
        /**
         * The current case has changed.
         *
         * If a new case has been opened as the current case, the old value of
         * the PropertyChangeEvent is null, and the new value is the new case
         * (type: Case).
         *
         * If the current case has been closed, the old value of the
         * PropertyChangeEvent is the closed case (type: Case), and the new
         * value is null. IMPORTANT: Subscribers to this event should not call
         * isCaseOpen or getCurrentCase in the interval between a case closed
         * event and a case opened event. If there is any need for upon closing
         * interaction with a closed case, the case in the old value should be
         * used, and it should be done synchronously in the CURRENT_CASE event
         * handler.
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
        CONTENT_TAG_DELETED,
        /**
         * The case display name or an optional detail which can be provided
         * regarding a case has been changed. The optional details include the
         * case number, the examiner name, examiner phone, examiner email, and
         * the case notes.
         */
        CASE_DETAILS,
        /**
         * A tag definition has changed (e.g., description, known status). The
         * old value of the PropertyChangeEvent is the display name of the tag
         * definition that has changed.
         */
        TAG_DEFINITION_CHANGED,
        /**
         * An timeline event, such mac time or web activity was added to the
         * current case. The old value is null and the new value is the
         * TimelineEvent that was added.
         */
        TIMELINE_EVENT_ADDED,
        /*
         * An item in the central repository has had its comment modified. The
         * old value is null, the new value is string for current comment.
         */
        CR_COMMENT_CHANGED,
        /**
         * One or more OS accounts have been added to the case.
         */
        OS_ACCOUNTS_ADDED,
        /**
         * One or more OS accounts in the case have been updated.
         */
        OS_ACCOUNTS_UPDATED,
        /**
         * One or more OS accounts have been deleted from the case.
         */
        OS_ACCOUNTS_DELETED,
        /**
         * One or more OS account instances have been added to the case.
         */
        OS_ACCT_INSTANCES_ADDED,
        /**
         * One or more hosts have been added to the case.
         */
        HOSTS_ADDED,
        /**
         * One or more hosts in the case have been updated.
         */
        HOSTS_UPDATED,
        /**
         * One or more hosts have been deleted from the case.
         */
        HOSTS_DELETED,
        /**
         * One or more persons have been added to the case.
         */
        PERSONS_ADDED,
        /**
         * One or more persons in the case have been updated.
         */
        PERSONS_UPDATED,
        /**
         * One or more persons been deleted from the case.
         */
        PERSONS_DELETED,
        /**
         * One or more hosts have been added to a person.
         */
        HOSTS_ADDED_TO_PERSON,
        /**
         * One or more hosts have been removed from a person.
         */
        HOSTS_REMOVED_FROM_PERSON,
        
        /**
         * One or more TagNames have been added.
         */
        TAG_NAMES_ADDED,
        
        /**
         * One or more TagNames have been updated.
         */
        TAG_NAMES_UPDATED,
        
        /**
         * One or more TagNames have been deleted.
         */
        TAG_NAMES_DELETED,
        
        /**
         * One or more TagSets have been added.
         */
        TAG_SETS_ADDED,
        
        /**
         * One or more TagSets have been removed.
         */
        TAG_SETS_DELETED;

    };

    /**
     * An instance of this class is registered as a listener on the event bus
     * associated with the case database so that selected SleuthKit layer
     * application events can be published as Autopsy application events.
     */
    private final class SleuthkitEventListener {

        @Subscribe
        public void publishTimelineEventAddedEvent(TimelineManager.TimelineEventAddedEvent event) {
            eventPublisher.publish(new TimelineEventAddedEvent(event));
        }

        @Subscribe
        public void publishOsAccountsAddedEvent(TskEvent.OsAccountsAddedTskEvent event) {
            hasData = true;
            eventPublisher.publish(new OsAccountsAddedEvent(event.getOsAcounts()));
        }

        @Subscribe
        public void publishOsAccountsUpdatedEvent(TskEvent.OsAccountsUpdatedTskEvent event) {
            eventPublisher.publish(new OsAccountsUpdatedEvent(event.getOsAcounts()));
        }

        @Subscribe
        public void publishOsAccountDeletedEvent(TskEvent.OsAccountsDeletedTskEvent event) {
            try {
                hasData = dbHasData();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to retrieve the hasData status from the db", ex);
            }
            eventPublisher.publish(new OsAccountsDeletedEvent(event.getOsAccountObjectIds()));
        }

        @Subscribe
        public void publishOsAccountInstancesAddedEvent(TskEvent.OsAcctInstancesAddedTskEvent event) {
            eventPublisher.publish(new OsAcctInstancesAddedEvent(event.getOsAccountInstances()));            
        }
        
        /**
         * Publishes an autopsy event from the sleuthkit HostAddedEvent
         * indicating that hosts have been created.
         *
         * @param event The sleuthkit event for the creation of hosts.
         */
        @Subscribe
        public void publishHostsAddedEvent(TskEvent.HostsAddedTskEvent event) {
            hasData = true;
            eventPublisher.publish(new HostsAddedEvent(event.getHosts()));
        }

        /**
         * Publishes an autopsy event from the sleuthkit HostUpdateEvent
         * indicating that hosts have been updated.
         *
         * @param event The sleuthkit event for the updating of hosts.
         */
        @Subscribe
        public void publishHostsUpdatedEvent(TskEvent.HostsUpdatedTskEvent event) {
            eventPublisher.publish(new HostsUpdatedEvent(event.getHosts()));
        }

        /**
         * Publishes an autopsy event from the sleuthkit HostDeletedEvent
         * indicating that hosts have been deleted.
         *
         * @param event The sleuthkit event for the deleting of hosts.
         */
        @Subscribe
        public void publishHostsDeletedEvent(TskEvent.HostsDeletedTskEvent event) {
            try {
                hasData = dbHasData();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Unable to retrieve the hasData status from the db", ex);
            }

            eventPublisher.publish(new HostsDeletedEvent(event.getHostIds()));
        }

        /**
         * Publishes an autopsy event from the sleuthkit PersonAddedEvent
         * indicating that persons have been created.
         *
         * @param event The sleuthkit event for the creation of persons.
         */
        @Subscribe
        public void publishPersonsAddedEvent(TskEvent.PersonsAddedTskEvent event) {
            eventPublisher.publish(new PersonsAddedEvent(event.getPersons()));
        }

        /**
         * Publishes an autopsy event from the sleuthkit PersonChangedEvent
         * indicating that persons have been updated.
         *
         * @param event The sleuthkit event for the updating of persons.
         */
        @Subscribe
        public void publishPersonsUpdatedEvent(TskEvent.PersonsUpdatedTskEvent event) {
            eventPublisher.publish(new PersonsUpdatedEvent(event.getPersons()));
        }

        /**
         * Publishes an autopsy event from the sleuthkit PersonDeletedEvent
         * indicating that persons have been deleted.
         *
         * @param event The sleuthkit event for the deleting of persons.
         */
        @Subscribe
        public void publishPersonsDeletedEvent(TskEvent.PersonsDeletedTskEvent event) {
            eventPublisher.publish(new PersonsDeletedEvent(event.getPersonIds()));
        }

        @Subscribe
        public void publishHostsAddedToPersonEvent(TskEvent.HostsAddedToPersonTskEvent event) {
            eventPublisher.publish(new HostsAddedToPersonEvent(event.getPerson(), event.getHosts()));
        }

        @Subscribe
        public void publisHostsRemovedFromPersonEvent(TskEvent.HostsRemovedFromPersonTskEvent event) {
            eventPublisher.publish(new HostsRemovedFromPersonEvent(event.getPerson(), event.getHostIds()));
        }
        
        @Subscribe
        public void publicTagNamesAdded(TskEvent.TagNamesAddedTskEvent event) {
            eventPublisher.publish(new TagNamesAddedEvent(event.getTagNames()));
        }

        @Subscribe
        public void publicTagNamesUpdated(TskEvent.TagNamesUpdatedTskEvent event) {
            eventPublisher.publish(new TagNamesUpdatedEvent(event.getTagNames()));
        }

        @Subscribe
        public void publicTagNamesDeleted(TskEvent.TagNamesDeletedTskEvent event) {
            eventPublisher.publish(new TagNamesDeletedEvent(event.getTagNameIds()));
        }

        @Subscribe
        public void publicTagSetsAdded(TskEvent.TagSetsAddedTskEvent event) {
            eventPublisher.publish(new TagSetsAddedEvent(event.getTagSets()));
        }

        @Subscribe
        public void publicTagSetsDeleted(TskEvent.TagSetsDeletedTskEvent event) {
            eventPublisher.publish(new TagSetsDeletedEvent(event.getTagSetIds()));
        }
    }

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
     *
     * @deprecated Use addEventTypeSubscriber instead.
     */
    @Deprecated
    public static void addEventSubscriber(Set<String> eventNames, PropertyChangeListener subscriber) {
        eventPublisher.addSubscriber(eventNames, subscriber);
    }

    /**
     * Adds a subscriber to specific case events.
     *
     * @param eventTypes The events the subscriber is interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to add.
     */
    public static void addEventTypeSubscriber(Set<Events> eventTypes, PropertyChangeListener subscriber) {
        eventTypes.forEach((Events event) -> {
            eventPublisher.addSubscriber(event.toString(), subscriber);
        });
    }

    /**
     * Adds a subscriber to specific case events.
     *
     * @param eventName  The event the subscriber is interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to add.
     *
     * @deprecated Use addEventTypeSubscriber instead.
     */
    @Deprecated
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
     * Removes a subscriber to specific case events.
     *
     * @param eventTypes The events the subscriber is no longer interested in.
     * @param subscriber The subscriber (PropertyChangeListener) to remove.
     */
    public static void removeEventTypeSubscriber(Set<Events> eventTypes, PropertyChangeListener subscriber) {
        eventTypes.forEach((Events event) -> {
            eventPublisher.removeSubscriber(event.toString(), subscriber);
        });
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
     * IMPORTANT: This method should not be called in the event dispatch thread
     * (EDT).
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
     * @throws CaseActionException          If there is a problem creating the
     *                                      case.
     * @throws CaseActionCancelledException If creating the case is cancelled.
     *
     * @deprecated use createAsCurrentCase(CaseType caseType, String caseDir,
     * CaseDetails caseDetails) instead
     */
    @Deprecated
    public static void createAsCurrentCase(String caseDir, String caseDisplayName, String caseNumber, String examiner, CaseType caseType) throws CaseActionException, CaseActionCancelledException {
        createAsCurrentCase(caseType, caseDir, new CaseDetails(caseDisplayName, caseNumber, examiner, "", "", ""));
    }

    /**
     * Creates a new case and makes it the current case.
     *
     * IMPORTANT: This method should not be called in the event dispatch thread
     * (EDT).
     *
     * @param caseDir     The full path of the case directory. The directory
     *                    will be created if it doesn't already exist; if it
     *                    exists, it is ASSUMED it was created by calling
     *                    createCaseDirectory.
     * @param caseType    The type of case (single-user or multi-user).
     * @param caseDetails Contains the modifiable details of the case such as
     *                    the case display name, the case number, and the
     *                    examiner related data.
     *
     * @throws CaseActionException          If there is a problem creating the
     *                                      case.
     * @throws CaseActionCancelledException If creating the case is cancelled.
     */
    @Messages({
        "Case.exceptionMessage.emptyCaseName=Must specify a case name.",
        "Case.exceptionMessage.emptyCaseDir=Must specify a case directory path."
    })
    public static void createAsCurrentCase(CaseType caseType, String caseDir, CaseDetails caseDetails) throws CaseActionException, CaseActionCancelledException {
        if (caseDetails.getCaseDisplayName().isEmpty()) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_emptyCaseName());
        }
        if (caseDir.isEmpty()) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_emptyCaseDir());
        }
        openAsCurrentCase(new Case(caseType, caseDir, caseDetails), true);
    }

    /**
     * Opens an existing case and makes it the current case.
     *
     * IMPORTANT: This method should not be called in the event dispatch thread
     * (EDT).
     *
     * @param caseMetadataFilePath The path of the case metadata (.aut) file.
     *
     * @throws CaseActionException If there is a problem opening the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.exceptionMessage.failedToReadMetadata=Failed to read case metadata:\n{0}.",
        "Case.exceptionMessage.cannotOpenMultiUserCaseNoSettings=Multi-user settings are missing (see Tools, Options, Multi-user tab), cannot open a multi-user case."
    })
    public static void openAsCurrentCase(String caseMetadataFilePath) throws CaseActionException {
        CaseMetadata metadata;
        try {
            metadata = new CaseMetadata(Paths.get(caseMetadataFilePath));
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_failedToReadMetadata(ex.getLocalizedMessage()), ex);
        }
        if (CaseType.MULTI_USER_CASE == metadata.getCaseType() && !UserPreferences.getIsMultiUserModeEnabled()) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_cannotOpenMultiUserCaseNoSettings());
        }
        openAsCurrentCase(new Case(metadata), false);
    }

    /**
     * Checks if a case, the current case, is open at the time it is called.
     *
     * @return True or false.
     */
    public static boolean isCaseOpen() {
        return currentCase != null;
    }

    /**
     * Gets the current case. This method should only be called by clients that
     * can be sure a case is currently open. Some examples of suitable clients
     * are data source processors, ingest modules, and report modules.
     *
     * @return The current case.
     */
    public static Case getCurrentCase() {
        try {
            return getCurrentCaseThrows();
        } catch (NoCurrentCaseException ex) {
            /*
             * Throw a runtime exception, since this is a programming error.
             */
            throw new IllegalStateException(NbBundle.getMessage(Case.class, "Case.getCurCase.exception.noneOpen"), ex);
        }
    }

    /**
     * Gets the current case, if there is one, or throws an exception if there
     * is no current case. This method should only be called by methods known to
     * run in threads where it is possible that another thread has closed the
     * current case. The exception provides some protection from the
     * consequences of the race condition between the calling thread and a case
     * closing thread, but it is not fool-proof. Background threads calling this
     * method should put all operations in an exception firewall with a try and
     * catch-all block to handle the possibility of bad timing.
     *
     * @return The current case.
     *
     * @throws NoCurrentCaseException if there is no current case.
     */
    public static Case getCurrentCaseThrows() throws NoCurrentCaseException {
        /*
         * TODO (JIRA-3825): Introduce a reference counting scheme for this get
         * case method.
         */
        Case openCase = currentCase;
        if (openCase == null) {
            throw new NoCurrentCaseException(NbBundle.getMessage(Case.class, "Case.getCurCase.exception.noneOpen"));
        } else {
            return openCase;
        }
    }

    /**
     * Closes the current case.
     *
     * @throws CaseActionException If there is a problem closing the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "# {0} - exception message", "Case.closeException.couldNotCloseCase=Error closing case: {0}",
        "Case.progressIndicatorTitle.closingCase=Closing Case"
    })
    public static void closeCurrentCase() throws CaseActionException {
        synchronized (caseActionSerializationLock) {
            if (null == currentCase) {
                return;
            }
            Case closedCase = currentCase;
            try {
                eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), closedCase, null));
                logger.log(Level.INFO, "Closing current case {0} ({1}) in {2}", new Object[]{closedCase.getDisplayName(), closedCase.getName(), closedCase.getCaseDirectory()}); //NON-NLS
                closedCase.doCloseCaseAction();
                logger.log(Level.INFO, "Closed current case {0} ({1}) in {2}", new Object[]{closedCase.getDisplayName(), closedCase.getName(), closedCase.getCaseDirectory()}); //NON-NLS
            } catch (CaseActionException ex) {
                logger.log(Level.SEVERE, String.format("Error closing current case %s (%s) in %s", closedCase.getDisplayName(), closedCase.getName(), closedCase.getCaseDirectory()), ex); //NON-NLS                
                throw ex;
            } finally {
                currentCase = null;
                if (RuntimeProperties.runningWithGUI()) {
                    updateGUIForCaseClosed();
                }
            }
        }
    }

    /**
     * Deletes the current case.
     *
     * @throws CaseActionException If there is a problem deleting the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    public static void deleteCurrentCase() throws CaseActionException {
        synchronized (caseActionSerializationLock) {
            if (null == currentCase) {
                return;
            }
            CaseMetadata metadata = currentCase.getMetadata();
            closeCurrentCase();
            deleteCase(metadata);
        }
    }

    /**
     * Deletes a data source from the current case.
     *
     * @param dataSourceObjectID The object ID of the data source to delete.
     *
     * @throws CaseActionException If there is a problem deleting the case. The
     *                             exception will have a user-friendly message
     *                             and may be a wrapper for a lower-level
     *                             exception.
     */
    @Messages({
        "Case.progressIndicatorTitle.deletingDataSource=Removing Data Source"
    })
    static void deleteDataSourceFromCurrentCase(Long dataSourceObjectID) throws CaseActionException {
        synchronized (caseActionSerializationLock) {
            if (null == currentCase) {
                return;
            }

            /*
             * Close the current case to release the shared case lock.
             */
            CaseMetadata caseMetadata = currentCase.getMetadata();
            closeCurrentCase();

            /*
             * Re-open the case with an exclusive case lock, delete the data
             * source, and close the case again, releasing the exclusive case
             * lock.
             */
            Case theCase = new Case(caseMetadata);
            theCase.doOpenCaseAction(Bundle.Case_progressIndicatorTitle_deletingDataSource(), theCase::deleteDataSource, CaseLockType.EXCLUSIVE, false, dataSourceObjectID);

            /*
             * Re-open the case with a shared case lock.
             */
            openAsCurrentCase(new Case(caseMetadata), false);
        }
    }

    /**
     * Deletes a case. The case to be deleted must not be the "current case."
     * Deleting the current case must be done by calling Case.deleteCurrentCase.
     *
     * @param metadata The case metadata.
     *
     * @throws CaseActionException If there were one or more errors deleting the
     *                             case. The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    @Messages({
        "Case.progressIndicatorTitle.deletingCase=Deleting Case",
        "Case.exceptionMessage.cannotDeleteCurrentCase=Cannot delete current case, it must be closed first.",
        "# {0} - case display name", "Case.exceptionMessage.deletionInterrupted=Deletion of the case {0} was cancelled."
    })
    public static void deleteCase(CaseMetadata metadata) throws CaseActionException {
        synchronized (caseActionSerializationLock) {
            if (null != currentCase) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_cannotDeleteCurrentCase());
            }
        }

        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(mainFrame, Bundle.Case_progressIndicatorTitle_deletingCase());
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        progressIndicator.start(Bundle.Case_progressMessage_preparing());
        try {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                deleteSingleUserCase(metadata, progressIndicator);
            } else {
                try {
                    deleteMultiUserCase(metadata, progressIndicator);
                } catch (InterruptedException ex) {
                    /*
                     * Note that task cancellation is not currently supported
                     * for this code path, so this catch block is not expected
                     * to be executed.
                     */
                    throw new CaseActionException(Bundle.Case_exceptionMessage_deletionInterrupted(metadata.getCaseDisplayName()), ex);
                }
            }
        } finally {
            progressIndicator.finish();
        }
    }

    /**
     * Opens a new or existing case as the current case.
     *
     * @param newCurrentCase The case.
     * @param isNewCase      True for a new case, false otherwise.
     *
     * @throws CaseActionException          If there is a problem creating the
     *                                      case.
     * @throws CaseActionCancelledException If creating the case is cancelled.
     */
    @Messages({
        "Case.progressIndicatorTitle.creatingCase=Creating Case",
        "Case.progressIndicatorTitle.openingCase=Opening Case",
        "Case.exceptionMessage.cannotLocateMainWindow=Cannot locate main application window"
    })
    private static void openAsCurrentCase(Case newCurrentCase, boolean isNewCase) throws CaseActionException, CaseActionCancelledException {
        synchronized (caseActionSerializationLock) {
            if (null != currentCase) {
                try {
                    closeCurrentCase();
                } catch (CaseActionException ex) {
                    /*
                     * Notify the user and continue (the error has already been
                     * logged in closeCurrentCase.
                     */
                    MessageNotifyUtil.Message.error(ex.getLocalizedMessage());
                }
            }
            try {
                logger.log(Level.INFO, "Opening {0} ({1}) in {2} as the current case", new Object[]{newCurrentCase.getDisplayName(), newCurrentCase.getName(), newCurrentCase.getCaseDirectory()}); //NON-NLS
                String progressIndicatorTitle;
                CaseAction<ProgressIndicator, Object, Void> openCaseAction;
                if (isNewCase) {
                    progressIndicatorTitle = Bundle.Case_progressIndicatorTitle_creatingCase();
                    openCaseAction = newCurrentCase::create;
                } else {
                    progressIndicatorTitle = Bundle.Case_progressIndicatorTitle_openingCase();
                    openCaseAction = newCurrentCase::open;
                }
                newCurrentCase.doOpenCaseAction(progressIndicatorTitle, openCaseAction, CaseLockType.SHARED, true, null);
                currentCase = newCurrentCase;
                logger.log(Level.INFO, "Opened {0} ({1}) in {2} as the current case", new Object[]{newCurrentCase.getDisplayName(), newCurrentCase.getName(), newCurrentCase.getCaseDirectory()}); //NON-NLS
                if (RuntimeProperties.runningWithGUI()) {
                    updateGUIForCaseOpened(newCurrentCase);
                }
                eventPublisher.publishLocally(new AutopsyEvent(Events.CURRENT_CASE.toString(), null, currentCase));
            } catch (CaseActionCancelledException ex) {
                logger.log(Level.INFO, String.format("Cancelled opening %s (%s) in %s as the current case", newCurrentCase.getDisplayName(), newCurrentCase.getName(), newCurrentCase.getCaseDirectory())); //NON-NLS                
                throw ex;
            } catch (CaseActionException ex) {
                logger.log(Level.SEVERE, String.format("Error opening %s (%s) in %s as the current case", newCurrentCase.getDisplayName(), newCurrentCase.getName(), newCurrentCase.getCaseDirectory()), ex); //NON-NLS                
                throw ex;
            }
        }
    }

    /**
     * Transforms a case display name into a unique case name that can be used
     * to identify the case even if the display name is changed.
     *
     * @param caseDisplayName A case display name.
     *
     * @return The unique case name.
     */
    private static String displayNameToUniqueName(String caseDisplayName) {
        /*
         * Replace all non-ASCII characters.
         */
        String uniqueCaseName = caseDisplayName.replaceAll("[^\\p{ASCII}]", "_"); //NON-NLS

        /*
         * Replace all control characters.
         */
        uniqueCaseName = uniqueCaseName.replaceAll("[\\p{Cntrl}]", "_"); //NON-NLS

        /*
         * Replace /, \, :, ?, space, ' ".
         */
        uniqueCaseName = uniqueCaseName.replaceAll("[ /?:'\"\\\\]", "_"); //NON-NLS

        /*
         * Make it all lowercase.
         */
        uniqueCaseName = uniqueCaseName.toLowerCase();

        /*
         * Add a time stamp for uniqueness.
         */
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();
        uniqueCaseName = uniqueCaseName + "_" + dateFormat.format(date);

        return uniqueCaseName;
    }

    /**
     * Creates a case directory and its subdirectories.
     *
     * @param caseDirPath Path to the case directory (typically base + case
     *                    name).
     * @param caseType    The type of case, single-user or multi-user.
     *
     * @throws CaseActionException throw if could not create the case dir
     */
    public static void createCaseDirectory(String caseDirPath, CaseType caseType) throws CaseActionException {
        /*
         * Check the case directory path and permissions. The case directory may
         * already exist.
         */
        File caseDir = new File(caseDirPath);
        if (caseDir.exists()) {
            if (caseDir.isFile()) {
                throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.existNotDir", caseDirPath));
            } else if (!caseDir.canRead() || !caseDir.canWrite()) {
                throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.existCantRW", caseDirPath));
            }
        }

        /*
         * Create the case directory, if it does not already exist.
         */
        if (!caseDir.mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreate", caseDirPath));
        }

        /*
         * Create the subdirectories of the case directory, if they do not
         * already exist. Note that multi-user cases get an extra layer of
         * subdirectories, one subdirectory per application host machine.
         */
        String hostPathComponent = "";
        if (caseType == CaseType.MULTI_USER_CASE) {
            hostPathComponent = File.separator + NetworkUtils.getLocalHostName();
        }

        Path exportDir = Paths.get(caseDirPath, hostPathComponent, EXPORT_FOLDER);
        if (!exportDir.toFile().mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateCaseDir", exportDir));
        }

        Path logsDir = Paths.get(caseDirPath, hostPathComponent, LOG_FOLDER);
        if (!logsDir.toFile().mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateCaseDir", logsDir));
        }

        Path cacheDir = Paths.get(caseDirPath, hostPathComponent, CACHE_FOLDER);
        if (!cacheDir.toFile().mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateCaseDir", cacheDir));
        }

        Path moduleOutputDir = Paths.get(caseDirPath, hostPathComponent, MODULE_FOLDER);
        if (!moduleOutputDir.toFile().mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateModDir", moduleOutputDir));
        }

        Path reportsDir = Paths.get(caseDirPath, hostPathComponent, REPORTS_FOLDER);
        if (!reportsDir.toFile().mkdirs()) {
            throw new CaseActionException(NbBundle.getMessage(Case.class, "Case.createCaseDir.exception.cantCreateReportsDir", reportsDir));
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
     * Acquires an exclusive case resources lock.
     *
     * @param caseDir The full path of the case directory.
     *
     * @return The lock or null if the lock could not be acquired.
     *
     * @throws CaseActionException with a user-friendly message if the lock
     *                             cannot be acquired due to an exception.
     */
    @Messages({
        "Case.creationException.couldNotAcquireResourcesLock=Failed to get lock on case resources"
    })
    private static CoordinationService.Lock acquireCaseResourcesLock(String caseDir) throws CaseActionException {
        try {
            Path caseDirPath = Paths.get(caseDir);
            String resourcesNodeName = CoordinationServiceUtils.getCaseResourcesNodePath(caseDirPath);
            Lock lock = CoordinationService.getInstance().tryGetExclusiveLock(CategoryNode.CASES, resourcesNodeName, CASE_RESOURCES_LOCK_TIMEOUT_HOURS, TimeUnit.HOURS);
            return lock;
        } catch (InterruptedException ex) {
            throw new CaseActionCancelledException(Bundle.Case_exceptionMessage_cancelled());
        } catch (CoordinationServiceException ex) {
            throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireResourcesLock(), ex);
        }
    }

    private static String getNameForTitle() {
        //Method should become unnecessary once technical debt story 3334 is done.
        if (UserPreferences.getAppName().equals(Version.getName())) {
            //Available version number is version number for this application
            return String.format("%s %s", UserPreferences.getAppName(), Version.getVersion());
        } else {
            return UserPreferences.getAppName();
        }
    }

    /**
     * Update the GUI to to reflect the current case.
     */
    private static void updateGUIForCaseOpened(Case newCurrentCase) {
        /*
         * If the case database was upgraded for a new schema and a backup
         * database was created, notify the user.
         */
        SleuthkitCase caseDb = newCurrentCase.getSleuthkitCase();
        String backupDbPath = caseDb.getBackupDatabasePath();
        if (null != backupDbPath) {
            JOptionPane.showMessageDialog(
                    mainFrame,
                    NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.msg", backupDbPath),
                    NbBundle.getMessage(Case.class, "Case.open.msgDlg.updated.title"),
                    JOptionPane.INFORMATION_MESSAGE);
        }

        /*
         * Look for the files for the data sources listed in the case database
         * and give the user the opportunity to locate any that are missing.
         */
        Map<Long, String> imgPaths = getImagePaths(caseDb);
        for (Map.Entry<Long, String> entry : imgPaths.entrySet()) {
            long obj_id = entry.getKey();
            String path = entry.getValue();
            boolean fileExists = (new File(path).isFile() || DriveUtils.driveExists(path));
            if (!fileExists) {
                try {
                    // Using invokeAndWait means that the dialog will
                    // open on the EDT but this thread will wait for an 
                    // answer. Using invokeLater would cause this loop to
                    // end before all of the dialogs appeared.  
                    SwingUtilities.invokeAndWait(new Runnable() {
                        @Override
                        public void run() {
                            int response = JOptionPane.showConfirmDialog(
                                    mainFrame,
                                    NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.msg", path),
                                    NbBundle.getMessage(Case.class, "Case.checkImgExist.confDlg.doesntExist.title"),
                                    JOptionPane.YES_NO_OPTION);
                            if (response == JOptionPane.YES_OPTION) {
                                MissingImageDialog.makeDialog(obj_id, caseDb);
                            } else {
                                logger.log(Level.SEVERE, "User proceeding with missing image files"); //NON-NLS

                            }
                        }

                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, "Failed to show missing image confirmation dialog", ex); //NON-NLS 
                }
            }
        }

        /*
         * Enable the case-specific actions.
         */
        CallableSystemAction.get(AddImageAction.class).setEnabled(FeatureAccessUtils.canAddDataSources());
        CallableSystemAction.get(OpenHostsAction.class).setEnabled(true);
        CallableSystemAction.get(CaseCloseAction.class).setEnabled(true);
        CallableSystemAction.get(CaseDetailsAction.class).setEnabled(true);
        CallableSystemAction.get(DataSourceSummaryAction.class).setEnabled(true);
        CallableSystemAction.get(CaseDeleteAction.class).setEnabled(FeatureAccessUtils.canDeleteCurrentCase());
        CallableSystemAction.get(OpenTimelineAction.class).setEnabled(true);
        CallableSystemAction.get(OpenCommVisualizationToolAction.class).setEnabled(true);
        CallableSystemAction.get(CommonAttributeSearchAction.class).setEnabled(true);
        CallableSystemAction.get(OpenOutputFolderAction.class).setEnabled(false);
        CallableSystemAction.get(OpenDiscoveryAction.class).setEnabled(true);

        /*
         * Add the case to the recent cases tracker that supplies a list of
         * recent cases to the recent cases menu item and the open/create case
         * dialog.
         */
        RecentCases.getInstance().addRecentCase(newCurrentCase.getDisplayName(), newCurrentCase.getMetadata().getFilePath().toString());
        final boolean hasData = newCurrentCase.hasData();

        SwingUtilities.invokeLater(() -> {
            /*
             * Open the top components (windows within the main application
             * window).
             *
             * Note: If the core windows are not opened here, they will be
             * opened via the DirectoryTreeTopComponent 'propertyChange()'
             * method on a DATA_SOURCE_ADDED event.
             */
            mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            if (hasData) {
                CoreComponentControl.openCoreWindows();
            } else {
                //ensure that the DirectoryTreeTopComponent is open so that it's listener can open the core windows including making it visible.
                DirectoryTreeTopComponent.findInstance();
            }
            mainFrame.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            /*
             * Reset the main window title to:
             *
             * [curent case display name] - [application name].
             */
            mainFrame.setTitle(newCurrentCase.getDisplayName() + " - " + getNameForTitle());
        });
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
                CallableSystemAction.get(OpenHostsAction.class).setEnabled(false);
                CallableSystemAction.get(CaseCloseAction.class).setEnabled(false);
                CallableSystemAction.get(CaseDetailsAction.class).setEnabled(false);
                CallableSystemAction.get(DataSourceSummaryAction.class).setEnabled(false);
                CallableSystemAction.get(CaseDeleteAction.class).setEnabled(false);
                CallableSystemAction.get(OpenTimelineAction.class).setEnabled(false);
                CallableSystemAction.get(OpenCommVisualizationToolAction.class).setEnabled(false);
                CallableSystemAction.get(OpenOutputFolderAction.class).setEnabled(false);
                CallableSystemAction.get(CommonAttributeSearchAction.class).setEnabled(false);
                CallableSystemAction.get(OpenDiscoveryAction.class).setEnabled(false);

                /*
                 * Clear the notifications in the notfier component in the lower
                 * right hand corner of the main application window.
                 */
                MessageNotifyUtil.Notify.clear();

                /*
                 * Reset the main window title to be just the application name,
                 * instead of [curent case display name] - [application name].
                 */
                mainFrame.setTitle(getNameForTitle());
            });
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
        return caseServices;
    }

    /**
     * Gets the case type.
     *
     * @return The case type.
     */
    public CaseType getCaseType() {
        return metadata.getCaseType();
    }

    /**
     * Gets the case create date.
     *
     * @return case The case create date.
     */
    public String getCreatedDate() {
        return metadata.getCreatedDate();
    }

    /**
     * Gets the unique and immutable case name.
     *
     * @return The case name.
     */
    public String getName() {
        return metadata.getCaseName();
    }

    /**
     * Gets the case name that can be changed by the user.
     *
     * @return The case display name.
     */
    public String getDisplayName() {
        return metadata.getCaseDisplayName();
    }

    /**
     * Gets the case number.
     *
     * @return The case number
     */
    public String getNumber() {
        return metadata.getCaseNumber();
    }

    /**
     * Gets the examiner name.
     *
     * @return The examiner name.
     */
    public String getExaminer() {
        return metadata.getExaminer();
    }

    /**
     * Gets the examiner phone number.
     *
     * @return The examiner phone number.
     */
    public String getExaminerPhone() {
        return metadata.getExaminerPhone();
    }

    /**
     * Gets the examiner email address.
     *
     * @return The examiner email address.
     */
    public String getExaminerEmail() {
        return metadata.getExaminerEmail();
    }

    /**
     * Gets the case notes.
     *
     * @return The case notes.
     */
    public String getCaseNotes() {
        return metadata.getCaseNotes();
    }

    /**
     * Gets the path to the top-level case directory.
     *
     * @return The top-level case directory path.
     */
    public String getCaseDirectory() {
        return metadata.getCaseDirectory();
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
        if (CaseType.MULTI_USER_CASE == metadata.getCaseType()) {
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
     * @return A subdirectory of java.io.tmpdir.
     */
    private Path getBaseSystemTempPath() {
        return Paths.get(System.getProperty("java.io.tmpdir"), APP_NAME, getName());
    }

    /**
     * Gets the full path to the temp directory for this case, creating it if it
     * does not exist.
     *
     * @return The temp subdirectory path.
     */
    public String getTempDirectory() {
        // NOTE: UserPreferences may also be affected by changes in this method.
        // See JIRA-7505 for more information.
        Path basePath = null;
        // get base temp path for the case based on user preference
        switch (UserMachinePreferences.getTempDirChoice()) {
            case CUSTOM:
                String customDirectory = UserMachinePreferences.getCustomTempDirectory();
                basePath = (StringUtils.isBlank(customDirectory))
                        ? null
                        : Paths.get(customDirectory, APP_NAME, getName());
                break;
            case CASE:
                basePath = Paths.get(getCaseDirectory());
                break;
            case SYSTEM:
            default:
                // at this level, if the case directory is specified for a temp
                // directory, return the system temp directory instead.
                basePath = getBaseSystemTempPath();
                break;
        }

        basePath = basePath == null ? getBaseSystemTempPath() : basePath;

        // get sub directories based on multi user vs. single user
        Path caseRelPath = (CaseType.MULTI_USER_CASE.equals(getCaseType()))
                ? Paths.get(NetworkUtils.getLocalHostName(), TEMP_FOLDER)
                : Paths.get(TEMP_FOLDER);

        File caseTempDir = basePath
                .resolve(caseRelPath)
                .toFile();

        // ensure directory exists
        if (!caseTempDir.exists()) {
            caseTempDir.mkdirs();
        }

        return caseTempDir.getAbsolutePath();
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
     * Gets the full path to the config directory for this case, creating it if
     * it does not exist.
     *
     * @return The config directory path.
     */
    public String getConfigDirectory() {
        return getOrCreateSubdirectory(CONFIG_FOLDER);
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
     * @return A list of data sources, possibly empty.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException if there is a problem
     *                                                  querying the case
     *                                                  database.
     */
    public List<Content> getDataSources() throws TskCoreException {
        return caseDb.getRootObjects();
    }

    /**
     * Gets the time zone(s) of the image data source(s) in this case.
     *
     * @return The set of time zones in use.
     */
    public Set<TimeZone> getTimeZones() {
        Set<TimeZone> timezones = new HashSet<>();
        String query = "SELECT time_zone FROM data_source_info";
        try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery(query)) {
            ResultSet timeZoneSet = dbQuery.getResultSet();
            while (timeZoneSet.next()) {
                String timeZone = timeZoneSet.getString("time_zone");
                if (timeZone != null && !timeZone.isEmpty()) {
                    timezones.add(TimeZone.getTimeZone(timeZone));
                }
            }
        } catch (TskCoreException | SQLException ex) {
            logger.log(Level.SEVERE, "Error getting data source time zones", ex); //NON-NLS
        }
        return timezones;
    }

    /**
     * Gets the name of the legacy keyword search index for the case. Not for
     * general use.
     *
     * @return The index name.
     */
    public String getTextIndexName() {
        return getMetadata().getTextIndexName();
    }

    /**
     * Returns true if there is any data in the case.
     *
     * @return True or false.
     */
    public boolean hasData() {
        return hasData;
    }

    /**
     * Returns true if there is one or more data sources in the case.
     *
     * @return True or false.
     */
    public boolean hasDataSource() {
        return hasDataSource;
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
        hasDataSource = true;
        hasData = true;
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
     * Notifies case event subscribers that a data source has been added to the
     * case database.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param dataSource The data source.
     * @param newName    The new name for the data source
     */
    public void notifyDataSourceNameChanged(Content dataSource, String newName) {
        eventPublisher.publish(new DataSourceNameChangedEvent(dataSource, newName));
    }

    /**
     * Notifies case event subscribers that a content tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new ContentTag added
     */
    public void notifyContentTagAdded(ContentTag newTag) {
        notifyContentTagAdded(newTag, null);
    }

    /**
     * Notifies case event subscribers that a content tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag         The added ContentTag.
     * @param deletedTagList List of ContentTags that were removed as a result
     *                       of the addition of newTag.
     */
    public void notifyContentTagAdded(ContentTag newTag, List<ContentTag> deletedTagList) {
        eventPublisher.publish(new ContentTagAddedEvent(newTag, deletedTagList));
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
     * Notifies case event subscribers that a tag definition has changed.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param changedTagName the name of the tag definition which was changed
     */
    public void notifyTagDefinitionChanged(String changedTagName) {
        //leaving new value of changedTagName as null, because we do not currently support changing the display name of a tag. 
        eventPublisher.publish(new AutopsyEvent(Events.TAG_DEFINITION_CHANGED.toString(), changedTagName, null));
    }

    /**
     * Notifies case event subscribers that a central repository comment has
     * been changed.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param contentId  the objectId for the Content which has had its central
     *                   repo comment changed
     * @param newComment the new value of the comment
     */
    public void notifyCentralRepoCommentChanged(long contentId, String newComment) {
        try {
            eventPublisher.publish(new CommentChangedEvent(contentId, newComment));
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Unable to send notifcation regarding comment change due to no current case being open", ex);
        }
    }

    /**
     * Notifies case event subscribers that an artifact tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag new BlackboardArtifactTag added
     */
    public void notifyBlackBoardArtifactTagAdded(BlackboardArtifactTag newTag) {
        notifyBlackBoardArtifactTagAdded(newTag, null);
    }

    /**
     * Notifies case event subscribers that an artifact tag has been added.
     *
     * This should not be called from the event dispatch thread (EDT)
     *
     * @param newTag         The added ContentTag.
     * @param removedTagList List of ContentTags that were removed as a result
     *                       of the addition of newTag.
     */
    public void notifyBlackBoardArtifactTagAdded(BlackboardArtifactTag newTag, List<BlackboardArtifactTag> removedTagList) {
        eventPublisher.publish(new BlackBoardArtifactTagAddedEvent(newTag, removedTagList));
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
        addReport(localPath, srcModuleName, reportName, null);
    }

    /**
     * Adds a report to the case.
     *
     * @param localPath     The path of the report file, must be in the case
     *                      directory or one of its subdirectories.
     * @param srcModuleName The name of the module that created the report.
     * @param reportName    The report name, may be empty.
     * @param parent        The Content used to create the report, if available.
     *
     * @return The new Report instance.
     *
     * @throws TskCoreException if there is a problem adding the report to the
     *                          case database.
     */
    public Report addReport(String localPath, String srcModuleName, String reportName, Content parent) throws TskCoreException {
        String normalizedLocalPath;
        try {
            if (localPath.toLowerCase().contains("http:")) {
                normalizedLocalPath = localPath;
            } else {
                normalizedLocalPath = Paths.get(localPath).normalize().toString();
            }
        } catch (InvalidPathException ex) {
            String errorMsg = "Invalid local path provided: " + localPath; // NON-NLS
            throw new TskCoreException(errorMsg, ex);
        }
        hasData = true;

        Report report = this.caseDb.addReport(normalizedLocalPath, srcModuleName, reportName, parent);
        eventPublisher.publish(new ReportAddedEvent(report));
        return report;
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
        }

        try {
            hasData = dbHasData();
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Unable to retrieve the hasData status from the db", ex);
        }

        for (Report report : reports) {
            eventPublisher.publish(new AutopsyEvent(Events.REPORT_DELETED.toString(), report, null));
        }
    }

    /**
     * Gets the case metadata.
     *
     * @return A CaseMetaData object.
     */
    public CaseMetadata getMetadata() {
        return metadata;
    }

    /**
     * Updates the case details.
     *
     * @param newDisplayName the new display name for the case
     *
     * @throws org.sleuthkit.autopsy.casemodule.CaseActionException
     */
    @Messages({
        "Case.exceptionMessage.metadataUpdateError=Failed to update case metadata"
    })
    void updateCaseDetails(CaseDetails caseDetails) throws CaseActionException {
        CaseDetails oldCaseDetails = metadata.getCaseDetails();
        try {
            metadata.setCaseDetails(caseDetails);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_metadataUpdateError(), ex);
        }
        if (getCaseType() == CaseType.MULTI_USER_CASE && !oldCaseDetails.getCaseDisplayName().equals(caseDetails.getCaseDisplayName())) {
            try {
                CaseNodeData nodeData = CaseNodeData.readCaseNodeData(metadata.getCaseDirectory());
                nodeData.setDisplayName(caseDetails.getCaseDisplayName());
                CaseNodeData.writeCaseNodeData(nodeData);
            } catch (CaseNodeDataException | InterruptedException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotUpdateCaseNodeData(ex.getLocalizedMessage()), ex);
            }
        }
        if (!oldCaseDetails.getCaseNumber().equals(caseDetails.getCaseNumber())) {
            eventPublisher.publish(new AutopsyEvent(Events.NUMBER.toString(), oldCaseDetails.getCaseNumber(), caseDetails.getCaseNumber()));
        }
        if (!oldCaseDetails.getExaminerName().equals(caseDetails.getExaminerName())) {
            eventPublisher.publish(new AutopsyEvent(Events.NUMBER.toString(), oldCaseDetails.getExaminerName(), caseDetails.getExaminerName()));
        }
        if (!oldCaseDetails.getCaseDisplayName().equals(caseDetails.getCaseDisplayName())) {
            eventPublisher.publish(new AutopsyEvent(Events.NAME.toString(), oldCaseDetails.getCaseDisplayName(), caseDetails.getCaseDisplayName()));
        }
        eventPublisher.publish(new AutopsyEvent(Events.CASE_DETAILS.toString(), oldCaseDetails, caseDetails));
        if (RuntimeProperties.runningWithGUI()) {
            SwingUtilities.invokeLater(() -> {
                mainFrame.setTitle(caseDetails.getCaseDisplayName() + " - " + getNameForTitle());
                try {
                    RecentCases.getInstance().updateRecentCase(oldCaseDetails.getCaseDisplayName(), metadata.getFilePath().toString(), caseDetails.getCaseDisplayName(), metadata.getFilePath().toString());
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Error updating case name in UI", ex); //NON-NLS
                }
            });
        }
    }

    /**
     * Constructs a Case object for a new Autopsy case.
     *
     * @param caseType    The type of case (single-user or multi-user).
     * @param caseDir     The full path of the case directory. The directory
     *                    will be created if it doesn't already exist; if it
     *                    exists, it is ASSUMED it was created by calling
     *                    createCaseDirectory.
     * @param caseDetails Contains details of the case, such as examiner,
     *                    display name, etc
     *
     */
    private Case(CaseType caseType, String caseDir, CaseDetails caseDetails) {
        this(new CaseMetadata(caseType, caseDir, displayNameToUniqueName(caseDetails.getCaseDisplayName()), caseDetails));
    }

    /**
     * Constructs a Case object for an existing Autopsy case.
     *
     * @param caseMetaData The metadata for the case.
     */
    private Case(CaseMetadata caseMetaData) {
        metadata = caseMetaData;
        sleuthkitEventListener = new SleuthkitEventListener();
    }

    /**
     * Performs a case action that involves creating or opening a case. If the
     * case is a multi-user case, the action is done after acquiring a
     * coordination service case lock. This case lock must be released in the
     * same thread in which it was acquired, as required by the coordination
     * service. A single-threaded executor is therefore created to do the case
     * opening action, and is saved for an eventual case closing action.
     *
     * IMPORTANT: If an open case action for a multi-user case is terminated
     * because an exception is thrown or the action is cancelled, the action is
     * responsible for releasing the case lock while still running in the case
     * action executor's thread. This method assumes this has been done and
     * performs an orderly shut down of the case action executor.
     *
     * @param progressIndicatorTitle A title for the progress indicator for the
     *                               case action.
     * @param caseAction             The case action method.
     * @param caseLockType           The type of case lock required for the case
     *                               action.
     * @param allowCancellation      Whether or not to allow the action to be
     *                               cancelled.
     * @param additionalParams       An Object that holds any additional
     *                               parameters for a case action.
     *
     * @throws CaseActionException If there is a problem completing the action.
     *                             The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    @Messages({
        "Case.progressIndicatorCancelButton.label=Cancel",
        "Case.progressMessage.preparing=Preparing...",
        "Case.progressMessage.cancelling=Cancelling...",
        "Case.exceptionMessage.cancelled=Cancelled.",
        "# {0} - exception message", "Case.exceptionMessage.execExceptionWrapperMessage={0}"
    })
    private void doOpenCaseAction(String progressIndicatorTitle, CaseAction<ProgressIndicator, Object, Void> caseAction, CaseLockType caseLockType, boolean allowCancellation, Object additionalParams) throws CaseActionException {
        /*
         * Create and start either a GUI progress indicator (with or without a
         * cancel button) or a logging progress indicator.
         */
        CancelButtonListener cancelButtonListener = null;
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            if (allowCancellation) {
                cancelButtonListener = new CancelButtonListener(Bundle.Case_progressMessage_cancelling());
                progressIndicator = new ModalDialogProgressIndicator(
                        mainFrame,
                        progressIndicatorTitle,
                        new String[]{Bundle.Case_progressIndicatorCancelButton_label()},
                        Bundle.Case_progressIndicatorCancelButton_label(),
                        cancelButtonListener);
            } else {
                progressIndicator = new ModalDialogProgressIndicator(
                        mainFrame,
                        progressIndicatorTitle);
            }
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        progressIndicator.start(Bundle.Case_progressMessage_preparing());

        /*
         * Do the case action in the single thread in the case action executor.
         * If the case is a multi-user case, a case lock is acquired and held
         * until explictly released and an exclusive case resources lock is
         * aquired and held for the duration of the action.
         */
        TaskThreadFactory threadFactory = new TaskThreadFactory(String.format(CASE_ACTION_THREAD_NAME, metadata.getCaseName()));
        caseActionExecutor = Executors.newSingleThreadExecutor(threadFactory);
        Future<Void> future = caseActionExecutor.submit(() -> {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                caseAction.execute(progressIndicator, additionalParams);
            } else {
                acquireCaseLock(caseLockType);
                try (CoordinationService.Lock resourcesLock = acquireCaseResourcesLock(metadata.getCaseDirectory())) {
                    if (null == resourcesLock) {
                        throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireResourcesLock());
                    }
                    caseAction.execute(progressIndicator, additionalParams);
                } catch (CaseActionException ex) {
                    releaseCaseLock();
                    throw ex;
                }
            }
            return null;
        });
        if (null != cancelButtonListener) {
            cancelButtonListener.setCaseActionFuture(future);
        }

        /*
         * Wait for the case action task to finish.
         */
        try {
            future.get();
        } catch (InterruptedException discarded) {
            /*
             * The thread this method is running in has been interrupted.
             */
            if (null != cancelButtonListener) {
                cancelButtonListener.actionPerformed(null);
            } else {
                future.cancel(true);
            }
            ThreadUtils.shutDownTaskExecutor(caseActionExecutor);
        } catch (CancellationException discarded) {
            /*
             * The case action has been cancelled.
             */
            ThreadUtils.shutDownTaskExecutor(caseActionExecutor);
            throw new CaseActionCancelledException(Bundle.Case_exceptionMessage_cancelled());
        } catch (ExecutionException ex) {
            /*
             * The case action has thrown an exception.
             */
            ThreadUtils.shutDownTaskExecutor(caseActionExecutor);
            throw new CaseActionException(Bundle.Case_exceptionMessage_execExceptionWrapperMessage(ex.getCause().getLocalizedMessage()), ex);
        } finally {
            progressIndicator.finish();
        }
    }

    /**
     * A case action (interface CaseAction<T, V, R>) that creates the case
     * directory and case database and opens the application services for this
     * case.
     *
     * @param progressIndicator A progress indicator.
     * @param additionalParams  An Object that holds any additional parameters
     *                          for a case action. For this action, this is
     *                          null.
     *
     * @throws CaseActionException If there is a problem completing the action.
     *                             The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    private Void create(ProgressIndicator progressIndicator, Object additionalParams) throws CaseActionException {
        assert (additionalParams == null);
        try {
            checkForCancellation();
            createCaseDirectoryIfDoesNotExist(progressIndicator);
            checkForCancellation();
            switchLoggingToCaseLogsDirectory(progressIndicator);
            checkForCancellation();
            saveCaseMetadataToFile(progressIndicator);
            checkForCancellation();
            createCaseNodeData(progressIndicator);
            checkForCancellation();
            checkForCancellation();
            createCaseDatabase(progressIndicator);
            checkForCancellation();
            openCaseLevelServices(progressIndicator);
            checkForCancellation();
            openAppServiceCaseResources(progressIndicator, true);
            checkForCancellation();
            openCommunicationChannels(progressIndicator);
            return null;

        } catch (CaseActionException ex) {
            /*
             * Cancellation or failure. The sleep is a little hack to clear the
             * interrupted flag for this thread if this is a cancellation
             * scenario, so that the clean up can run to completion in the
             * current thread.
             */
            try {
                Thread.sleep(1);
            } catch (InterruptedException discarded) {
            }
            close(progressIndicator);
            throw ex;
        }
    }

    /**
     * A case action (interface CaseAction<T, V, R>) that opens the case
     * database and application services for this case.
     *
     * @param progressIndicator A progress indicator.
     * @param additionalParams  An Object that holds any additional parameters
     *                          for a case action. For this action, this is
     *                          null.
     *
     * @throws CaseActionException If there is a problem completing the action.
     *                             The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    private Void open(ProgressIndicator progressIndicator, Object additionalParams) throws CaseActionException {
        assert (additionalParams == null);
        try {
            checkForCancellation();
            switchLoggingToCaseLogsDirectory(progressIndicator);
            checkForCancellation();
            updateCaseNodeData(progressIndicator);
            checkForCancellation();
            deleteTempfilesFromCaseDirectory(progressIndicator);
            checkForCancellation();
            openCaseDataBase(progressIndicator);
            checkForCancellation();
            openCaseLevelServices(progressIndicator);
            checkForCancellation();
            openAppServiceCaseResources(progressIndicator, false);
            checkForCancellation();
            openCommunicationChannels(progressIndicator);
            checkForCancellation();
            openFileSystemsInBackground();
            return null;

        } catch (CaseActionException ex) {
            /*
             * Cancellation or failure. The sleep is a little hack to clear the
             * interrupted flag for this thread if this is a cancellation
             * scenario, so that the clean up can run to completion in the
             * current thread.
             */
            try {
                Thread.sleep(1);
            } catch (InterruptedException discarded) {
            }
            close(progressIndicator);
            throw ex;
        }
    }

    /**
     * Starts a background task that reads a sector from each file system of
     * each image of a case to do an eager open of the filesystems in the case.
     * If this method is called before another background file system read has
     * finished the earlier one will be cancelled.
     *
     * @throws CaseActionCancelledException Exception thrown if task is
     *                                      cancelled.
     */
    @Messages({
        "# {0} - case", "Case.openFileSystems.retrievingImages=Retrieving images for case: {0}...",
        "# {0} - image", "Case.openFileSystems.openingImage=Opening all filesystems for image: {0}..."
    })
    private void openFileSystemsInBackground() {
        if (backgroundOpenFileSystemsFuture != null && !backgroundOpenFileSystemsFuture.isDone()) {
            backgroundOpenFileSystemsFuture.cancel(true);
        }

        BackgroundOpenFileSystemsTask backgroundTask = new BackgroundOpenFileSystemsTask(this.caseDb, new LoggingProgressIndicator());
        backgroundOpenFileSystemsFuture = openFileSystemsExecutor.submit(backgroundTask);
    }

    /**
     * This task opens all the filesystems of all images in the case in the
     * background. It also responds to cancellation events.
     */
    private static class BackgroundOpenFileSystemsTask implements Runnable {

        private final SleuthkitCase tskCase;
        private final String caseName;
        private final long MAX_IMAGE_THRESHOLD = 100;
        private final ProgressIndicator progressIndicator;

        /**
         * Main constructor for the BackgroundOpenFileSystemsTask.
         *
         * @param tskCase           The case database to query for filesystems
         *                          to open.
         * @param progressIndicator The progress indicator for file systems
         *                          opened.
         */
        BackgroundOpenFileSystemsTask(SleuthkitCase tskCase, ProgressIndicator progressIndicator) {
            this.tskCase = tskCase;
            this.progressIndicator = progressIndicator;
            caseName = (this.tskCase != null) ? this.tskCase.getDatabaseName() : "";
        }

        /**
         * Checks if thread has been cancelled and throws an
         * InterruptedException if it has.
         *
         * @throws InterruptedException The exception thrown if the operation
         *                              has been cancelled.
         */
        private void checkIfCancelled() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
        }

        /**
         * Retrieves all images present in the sleuthkit case.
         *
         * @return All images present in the sleuthkit case.
         */
        private List<Image> getImages() {
            progressIndicator.progress(Bundle.Case_openFileSystems_retrievingImages(caseName));
            try {
                return this.tskCase.getImages();
            } catch (TskCoreException ex) {
                logger.log(
                        Level.SEVERE,
                        String.format("Could not obtain images while opening case: %s.", caseName),
                        ex);

                return null;
            }
        }

        /**
         * Opens all file systems in the list of images provided.
         *
         * @param images The images whose file systems will be opened.
         *
         * @throws CaseActionCancelledException The exception thrown in the
         *                                      event that the operation is
         *                                      cancelled prior to completion.
         */
        private void openFileSystems(List<Image> images) throws TskCoreException, InterruptedException {
            byte[] tempBuff = new byte[512];

            for (Image image : images) {
                String imageStr = image.getName();

                progressIndicator.progress(Bundle.Case_openFileSystems_openingImage(imageStr));

                Collection<FileSystem> fileSystems = this.tskCase.getImageFileSystems(image);
                checkIfCancelled();
                for (FileSystem fileSystem : fileSystems) {
                    fileSystem.read(tempBuff, 0, 512);
                    checkIfCancelled();
                }

            }
        }

        @Override
        public void run() {
            try {
                checkIfCancelled();
                List<Image> images = getImages();
                if (images == null) {
                    return;
                }

                if (images.size() > MAX_IMAGE_THRESHOLD) {
                    // If we have a large number of images, don't try to preload anything
                    logger.log(
                            Level.INFO,
                            String.format("Skipping background load of file systems due to large number of images in case (%d)", images.size()));
                    return;
                }

                checkIfCancelled();
                openFileSystems(images);
            } catch (InterruptedException ex) {
                logger.log(
                        Level.INFO,
                        String.format("Background operation opening all file systems in %s has been cancelled.", caseName));
            } catch (Exception ex) {
                // Exception firewall
                logger.log(Level.WARNING, "Error while opening file systems in background", ex);
            }
        }

    }

    /**
     * A case action (interface CaseAction<T, V, R>) that opens a case, deletes
     * a data source from the case, and closes the case.
     *
     * @param progressIndicator A progress indicator.
     * @param additionalParams  An Object that holds any additional parameters
     *                          for a case action. For this action, this the
     *                          object ID of the data source to be deleted.
     *
     * @throws CaseActionException If there is a problem completing the action.
     *                             The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    @Messages({
        "Case.progressMessage.deletingDataSource=Removing the data source from the case...",
        "Case.exceptionMessage.dataSourceNotFound=The data source was not found.",
        "Case.exceptionMessage.errorDeletingDataSourceFromCaseDb=An error occurred while removing the data source from the case database.",
        "Case.exceptionMessage.errorDeletingDataSourceFromTextIndex=An error occurred while removing the data source from the text index.",})
    Void deleteDataSource(ProgressIndicator progressIndicator, Object additionalParams) throws CaseActionException {
        assert (additionalParams instanceof Long);
        open(progressIndicator, null);
        try {
            progressIndicator.progress(Bundle.Case_progressMessage_deletingDataSource());
            Long dataSourceObjectID = (Long) additionalParams;
            try {
                DataSource dataSource = this.caseDb.getDataSource(dataSourceObjectID);
                if (dataSource == null) {
                    throw new CaseActionException(Bundle.Case_exceptionMessage_dataSourceNotFound());
                }
                SleuthkitCaseAdminUtil.deleteDataSource(this.caseDb, dataSourceObjectID);
            } catch (TskDataException | TskCoreException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_errorDeletingDataSourceFromCaseDb(), ex);
            }
            try {
                this.caseServices.getKeywordSearchService().deleteDataSource(dataSourceObjectID);
            } catch (KeywordSearchServiceException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_errorDeletingDataSourceFromTextIndex(), ex);
            }
            eventPublisher.publish(new DataSourceDeletedEvent(dataSourceObjectID));
            return null;
        } finally {
            close(progressIndicator);
            releaseCaseLock();
        }
    }

    /**
     * Create an empty portable case from the current case
     *
     * @param caseName           Case name
     * @param portableCaseFolder Case folder - must not exist
     *
     * @return The portable case database
     *
     * @throws TskCoreException
     */
    public SleuthkitCase createPortableCase(String caseName, File portableCaseFolder) throws TskCoreException {

        if (portableCaseFolder.exists()) {
            throw new TskCoreException("Portable case folder " + portableCaseFolder.toString() + " already exists");
        }
        if (!portableCaseFolder.mkdirs()) {
            throw new TskCoreException("Error creating portable case folder " + portableCaseFolder.toString());
        }

        CaseDetails details = new CaseDetails(caseName, getNumber(), getExaminer(),
                getExaminerPhone(), getExaminerEmail(), getCaseNotes());
        try {
            CaseMetadata portableCaseMetadata = new CaseMetadata(Case.CaseType.SINGLE_USER_CASE, portableCaseFolder.toString(),
                    caseName, details, metadata);
            portableCaseMetadata.setCaseDatabaseName(SINGLE_USER_CASE_DB_NAME);
        } catch (CaseMetadataException ex) {
            throw new TskCoreException("Error creating case metadata", ex);
        }

        // Create the Sleuthkit case
        SleuthkitCase portableSleuthkitCase;
        String dbFilePath = Paths.get(portableCaseFolder.toString(), SINGLE_USER_CASE_DB_NAME).toString();
        portableSleuthkitCase = SleuthkitCase.newCase(dbFilePath);

        return portableSleuthkitCase;
    }

    /**
     * Checks current thread for an interrupt. Usage: checking for user
     * cancellation of a case creation/opening operation, as reflected in the
     * exception message.
     *
     * @throws CaseActionCancelledException If the current thread is
     *                                      interrupted, assumes interrupt was
     *                                      due to a user action.
     */
    private static void checkForCancellation() throws CaseActionCancelledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new CaseActionCancelledException(Bundle.Case_exceptionMessage_cancelled());
        }
    }

    /**
     * Creates the case directory, if it does not already exist.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.creatingCaseDirectory=Creating case directory..."
    })
    private void createCaseDirectoryIfDoesNotExist(ProgressIndicator progressIndicator) throws CaseActionException {
        /*
         * TODO (JIRA-2180): Always create the case directory as part of the
         * case creation process.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseDirectory());
        if (new File(metadata.getCaseDirectory()).exists() == false) {
            progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseDirectory());
            Case.createCaseDirectory(metadata.getCaseDirectory(), metadata.getCaseType());
        }
    }

    /**
     * Switches from writing log messages to the application logs to the logs
     * subdirectory of the case directory.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.switchingLogDirectory=Switching log directory..."
    })
    private void switchLoggingToCaseLogsDirectory(ProgressIndicator progressIndicator) {
        progressIndicator.progress(Bundle.Case_progressMessage_switchingLogDirectory());
        Logger.setLogDirectory(getLogDirectoryPath());
    }

    /**
     * Saves teh case metadata to a file.SHould not be called until the case
     * directory has been created.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.savingCaseMetadata=Saving case metadata to file...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotSaveCaseMetadata=Failed to save case metadata:\n{0}."
    })
    private void saveCaseMetadataToFile(ProgressIndicator progressIndicator) throws CaseActionException {
        progressIndicator.progress(Bundle.Case_progressMessage_savingCaseMetadata());
        try {
            this.metadata.writeToFile();
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotSaveCaseMetadata(ex.getLocalizedMessage()), ex);
        }
    }

    /**
     * Creates the node data for the case directory lock coordination service
     * node.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.creatingCaseNodeData=Creating coordination service node data...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotCreateCaseNodeData=Failed to create coordination service node data:\n{0}."
    })
    private void createCaseNodeData(ProgressIndicator progressIndicator) throws CaseActionException {
        if (getCaseType() == CaseType.MULTI_USER_CASE) {
            progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseNodeData());
            try {
                CaseNodeData.createCaseNodeData(metadata);
            } catch (CaseNodeDataException | InterruptedException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotCreateCaseNodeData(ex.getLocalizedMessage()), ex);
            }
        }
    }

    /**
     * Updates the node data for the case directory lock coordination service
     * node.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.updatingCaseNodeData=Updating coordination service node data...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotUpdateCaseNodeData=Failed to update coordination service node data:\n{0}."
    })
    private void updateCaseNodeData(ProgressIndicator progressIndicator) throws CaseActionException {
        if (getCaseType() == CaseType.MULTI_USER_CASE) {
            progressIndicator.progress(Bundle.Case_progressMessage_updatingCaseNodeData());
            try {
                CaseNodeData nodeData = CaseNodeData.readCaseNodeData(metadata.getCaseDirectory());
                nodeData.setLastAccessDate(new Date());
                CaseNodeData.writeCaseNodeData(nodeData);
            } catch (CaseNodeDataException | InterruptedException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotUpdateCaseNodeData(ex.getLocalizedMessage()), ex);
            }
        }
    }

    /**
     * Deletes any files in the temp subdirectory of the case directory.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.clearingTempDirectory=Clearing case temp directory..."
    })
    private void deleteTempfilesFromCaseDirectory(ProgressIndicator progressIndicator) {
        /*
         * Clear the temp subdirectory of the case directory.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_clearingTempDirectory());
        FileUtil.deleteDir(new File(this.getTempDirectory()));
    }

    /**
     * Creates the node data for the case directory lock coordination service
     * node, the case directory, the case database and the case metadata file.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.creatingCaseDatabase=Creating case database...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotGetDbServerConnectionInfo=Failed to get case database server conneciton info:\n{0}.",
        "# {0} - exception message", "Case.exceptionMessage.couldNotCreateCaseDatabase=Failed to create case database:\n{0}.",
        "# {0} - exception message", "Case.exceptionMessage.couldNotSaveDbNameToMetadataFile=Failed to save case database name to case metadata file:\n{0}."
    })
    private void createCaseDatabase(ProgressIndicator progressIndicator) throws CaseActionException {
        progressIndicator.progress(Bundle.Case_progressMessage_creatingCaseDatabase());
        try {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                /*
                 * For single-user cases, the case database is a SQLite database
                 * with a standard name, physically located in the case
                 * directory.
                 */
                caseDb = SleuthkitCase.newCase(Paths.get(metadata.getCaseDirectory(), SINGLE_USER_CASE_DB_NAME).toString());
                metadata.setCaseDatabaseName(SINGLE_USER_CASE_DB_NAME);
            } else {
                /*
                 * For multi-user cases, the case database is a PostgreSQL
                 * database with a name derived from the case display name,
                 * physically located on the PostgreSQL database server.
                 */
                caseDb = SleuthkitCase.newCase(metadata.getCaseDisplayName(), UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());
                metadata.setCaseDatabaseName(caseDb.getDatabaseName());
            }
        } catch (TskCoreException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotCreateCaseDatabase(ex.getLocalizedMessage()), ex);
        } catch (UserPreferencesException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotGetDbServerConnectionInfo(ex.getLocalizedMessage()), ex);
        } catch (CaseMetadataException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotSaveDbNameToMetadataFile(ex.getLocalizedMessage()), ex);
        }
    }

    /**
     * Updates the node data for an existing case directory lock coordination
     * service node and opens an existing case database.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.openingCaseDatabase=Opening case database...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotOpenCaseDatabase=Failed to open case database:\n{0}.",
        "# {0} - exception message", "Case.exceptionMessage.unsupportedSchemaVersionMessage=Unsupported case database schema version:\n{0}.",
        "Case.open.exception.multiUserCaseNotEnabled=Cannot open a multi-user case if multi-user cases are not enabled. See Tools, Options, Multi-User."
    })
    private void openCaseDataBase(ProgressIndicator progressIndicator) throws CaseActionException {
        progressIndicator.progress(Bundle.Case_progressMessage_openingCaseDatabase());
        try {
            String databaseName = metadata.getCaseDatabaseName();
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                caseDb = SleuthkitCase.openCase(Paths.get(metadata.getCaseDirectory(), databaseName).toString());
            } else if (UserPreferences.getIsMultiUserModeEnabled()) {
                caseDb = SleuthkitCase.openCase(databaseName, UserPreferences.getDatabaseConnectionInfo(), metadata.getCaseDirectory());
            } else {
                throw new CaseActionException(Bundle.Case_open_exception_multiUserCaseNotEnabled());
            }
            updateDataParameters();
        } catch (TskUnsupportedSchemaVersionException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_unsupportedSchemaVersionMessage(ex.getLocalizedMessage()), ex);
        } catch (UserPreferencesException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotGetDbServerConnectionInfo(ex.getLocalizedMessage()), ex);
        } catch (TskCoreException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotOpenCaseDatabase(ex.getLocalizedMessage()), ex);
        }
    }

    /**
     * Opens the case-level services: the files manager, tags manager and
     * blackboard.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.openingCaseLevelServices=Opening case-level services...",})
    private void openCaseLevelServices(ProgressIndicator progressIndicator) {
        progressIndicator.progress(Bundle.Case_progressMessage_openingCaseLevelServices());
        this.caseServices = new Services(caseDb);
        /*
         * RC Note: JM put this initialization here. I'm not sure why. However,
         * my attempt to put it in the openCaseDatabase method seems to lead to
         * intermittent unchecked exceptions concerning a missing subscriber.
         */
        caseDb.registerForEvents(sleuthkitEventListener);
    }

    /**
     * Allows any registered application-level services to open resources
     * specific to this case.
     *
     * @param progressIndicator A progress indicator.
     * @param isNewCase         True if case is new
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @NbBundle.Messages({
        "Case.progressMessage.openingApplicationServiceResources=Opening application service case resources...",
        "# {0} - service name", "Case.serviceOpenCaseResourcesProgressIndicator.title={0} Opening Case Resources",
        "# {0} - service name", "Case.serviceOpenCaseResourcesProgressIndicator.cancellingMessage=Cancelling opening case resources by {0}...",
        "# {0} - service name", "Case.servicesException.notificationTitle={0} Error"
    })
    private void openAppServiceCaseResources(ProgressIndicator progressIndicator, boolean isNewCase) throws CaseActionException {
        /*
         * Each service gets its own independently cancellable/interruptible
         * task, running in a named thread managed by an executor service, with
         * its own progress indicator. This allows for cancellation of the
         * opening of case resources for individual services. It also makes it
         * possible to ensure that each service task completes before the next
         * one starts by awaiting termination of the executor service.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_openingApplicationServiceResources());

        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class
        )) {
            /*
             * Create a progress indicator for the task and start the task. If
             * running with a GUI, the progress indicator will be a dialog box
             * with a Cancel button.
             */
            CancelButtonListener cancelButtonListener = null;
            ProgressIndicator appServiceProgressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                cancelButtonListener = new CancelButtonListener(Bundle.Case_serviceOpenCaseResourcesProgressIndicator_cancellingMessage(service.getServiceName()));
                appServiceProgressIndicator = new ModalDialogProgressIndicator(
                        mainFrame,
                        Bundle.Case_serviceOpenCaseResourcesProgressIndicator_title(service.getServiceName()),
                        new String[]{Bundle.Case_progressIndicatorCancelButton_label()},
                        Bundle.Case_progressIndicatorCancelButton_label(),
                        cancelButtonListener);
            } else {
                appServiceProgressIndicator = new LoggingProgressIndicator();
            }
            appServiceProgressIndicator.start(Bundle.Case_progressMessage_preparing());
            AutopsyService.CaseContext context = new AutopsyService.CaseContext(this, appServiceProgressIndicator, isNewCase);
            String threadNameSuffix = service.getServiceName().replaceAll("[ ]", "-"); //NON-NLS
            threadNameSuffix = threadNameSuffix.toLowerCase();
            TaskThreadFactory threadFactory = new TaskThreadFactory(String.format(CASE_RESOURCES_THREAD_NAME, threadNameSuffix));
            ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
            Future<Void> future = executor.submit(() -> {
                service.openCaseResources(context);
                return null;
            });
            if (null != cancelButtonListener) {
                cancelButtonListener.setCaseContext(context);
                cancelButtonListener.setCaseActionFuture(future);
            }

            /*
             * Wait for the task to either be completed or
             * cancelled/interrupted, or for the opening of the case to be
             * cancelled.
             */
            try {
                future.get();
            } catch (InterruptedException discarded) {
                /*
                 * The parent create/open case task has been cancelled.
                 */
                Case.logger.log(Level.WARNING, String.format("Opening of %s (%s) in %s cancelled during opening of case resources by %s", getDisplayName(), getName(), getCaseDirectory(), service.getServiceName()));
                future.cancel(true);
            } catch (CancellationException discarded) {
                /*
                 * The opening of case resources by the application service has
                 * been cancelled, so the executor service has thrown. Note that
                 * there is no guarantee the task itself has responded to the
                 * cancellation request yet.
                 */
                Case.logger.log(Level.WARNING, String.format("Opening of case resources by %s for %s (%s) in %s cancelled", service.getServiceName(), getDisplayName(), getName(), getCaseDirectory(), service.getServiceName()));
            } catch (ExecutionException ex) {
                /*
                 * An exception was thrown while executing the task. The
                 * case-specific application service resources are not
                 * essential. Log an error and notify the user if running the
                 * desktop GUI, but do not throw.
                 */
                Case.logger.log(Level.SEVERE, String.format("%s failed to open case resources for %s", service.getServiceName(), this.getDisplayName()), ex);
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(() -> {
                        MessageNotifyUtil.Notify.error(Bundle.Case_servicesException_notificationTitle(service.getServiceName()), ex.getLocalizedMessage());
                    });
                }
            } finally {
                /*
                 * Shut down the executor service and wait for it to finish.
                 * This ensures that the task has finished. Without this, it
                 * would be possible to start the next task before the current
                 * task responded to a cancellation request.
                 */
                ThreadUtils.shutDownTaskExecutor(executor);
                appServiceProgressIndicator.finish();
            }
            checkForCancellation();
        }
    }

    /**
     * If this case is a multi-user case, sets up for communication with other
     * application nodes.
     *
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is a problem completing the
     *                             operation. The exception will have a
     *                             user-friendly message and may be a wrapper
     *                             for a lower-level exception.
     */
    @Messages({
        "Case.progressMessage.settingUpNetworkCommunications=Setting up network communications...",
        "# {0} - exception message", "Case.exceptionMessage.couldNotOpenRemoteEventChannel=Failed to open remote events channel:\n{0}.",
        "# {0} - exception message", "Case.exceptionMessage.couldNotCreatCollaborationMonitor=Failed to create collaboration monitor:\n{0}."
    })
    private void openCommunicationChannels(ProgressIndicator progressIndicator) throws CaseActionException {
        if (CaseType.MULTI_USER_CASE == metadata.getCaseType()) {
            progressIndicator.progress(Bundle.Case_progressMessage_settingUpNetworkCommunications());
            try {
                eventPublisher.openRemoteEventChannel(String.format(EVENT_CHANNEL_NAME, metadata.getCaseName()));
                checkForCancellation();
                collaborationMonitor = new CollaborationMonitor(metadata.getCaseName());
            } catch (AutopsyEventException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotOpenRemoteEventChannel(ex.getLocalizedMessage()), ex);
            } catch (CollaborationMonitor.CollaborationMonitorException ex) {
                throw new CaseActionException(Bundle.Case_exceptionMessage_couldNotCreatCollaborationMonitor(ex.getLocalizedMessage()), ex);
            }
        }
    }

    /**
     * Performs a case action that involves closing a case opened by calling
     * doOpenCaseAction. If the case is a multi-user case, the coordination
     * service case lock acquired by the call to doOpenCaseAction is released.
     * This case lock must be released in the same thread in which it was
     * acquired, as required by the coordination service. The single-threaded
     * executor saved during the case opening action is therefore used to do the
     * case closing action.
     *
     * @throws CaseActionException If there is a problem completing the action.
     *                             The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    private void doCloseCaseAction() throws CaseActionException {
        /*
         * Set up either a GUI progress indicator without a Cancel button or a
         * logging progress indicator.
         */
        ProgressIndicator progressIndicator;
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator = new ModalDialogProgressIndicator(
                    mainFrame,
                    Bundle.Case_progressIndicatorTitle_closingCase());
        } else {
            progressIndicator = new LoggingProgressIndicator();
        }
        progressIndicator.start(Bundle.Case_progressMessage_preparing());

        /*
         * Closing a case is always done in the same non-UI thread that
         * opened/created the case. If the case is a multi-user case, this
         * ensures that case lock that is held as long as the case is open is
         * released in the same thread in which it was acquired, as is required
         * by the coordination service.
         */
        Future<Void> future = caseActionExecutor.submit(() -> {
            if (CaseType.SINGLE_USER_CASE == metadata.getCaseType()) {
                close(progressIndicator);
            } else {
                /*
                 * Acquire an exclusive case resources lock to ensure only one
                 * node at a time can create/open/upgrade/close the case
                 * resources.
                 */
                progressIndicator.progress(Bundle.Case_progressMessage_preparing());
                try (CoordinationService.Lock resourcesLock = acquireCaseResourcesLock(metadata.getCaseDirectory())) {
                    if (null == resourcesLock) {
                        throw new CaseActionException(Bundle.Case_creationException_couldNotAcquireResourcesLock());
                    }
                    close(progressIndicator);
                } finally {
                    /*
                     * Always release the case directory lock that was acquired
                     * when the case was opened.
                     */
                    releaseCaseLock();
                }
            }
            return null;
        });

        try {
            future.get();
        } catch (InterruptedException | CancellationException unused) {
            /*
             * The wait has been interrupted by interrupting the thread running
             * this method. Not allowing cancellation of case closing, so ignore
             * the interrupt. Likewise, cancellation of the case closing task is
             * not supported.
             */
        } catch (ExecutionException ex) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_execExceptionWrapperMessage(ex.getCause().getMessage()), ex);
        } finally {
            ThreadUtils.shutDownTaskExecutor(caseActionExecutor);
            progressIndicator.finish();
        }
    }

    /**
     * Closes the case.
     *
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.shuttingDownNetworkCommunications=Shutting down network communications...",
        "Case.progressMessage.closingApplicationServiceResources=Closing case-specific application service resources...",
        "Case.progressMessage.closingCaseDatabase=Closing case database..."
    })
    private void close(ProgressIndicator progressIndicator) {
        IngestManager.getInstance().cancelAllIngestJobs(IngestJob.CancellationReason.CASE_CLOSED);

        /*
         * Stop sending/receiving case events to and from other nodes if this is
         * a multi-user case.
         */
        if (CaseType.MULTI_USER_CASE == metadata.getCaseType()) {
            progressIndicator.progress(Bundle.Case_progressMessage_shuttingDownNetworkCommunications());
            if (null != collaborationMonitor) {
                collaborationMonitor.shutdown();
            }
            eventPublisher.closeRemoteEventChannel();
        }

        /*
         * Allow all registered application services providers to close
         * resources related to the case.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_closingApplicationServiceResources());
        closeAppServiceCaseResources();

        /*
         * Close the case database.
         */
        if (null != caseDb) {
            progressIndicator.progress(Bundle.Case_progressMessage_closingCaseDatabase());
            caseDb.unregisterForEvents(sleuthkitEventListener);
            caseDb.close();
        }

        /**
         * Delete files from temp directory if any exist.
         */
        deleteTempfilesFromCaseDirectory(progressIndicator);

        /*
         * Switch the log directory.
         */
        progressIndicator.progress(Bundle.Case_progressMessage_switchingLogDirectory());
        Logger.setLogDirectory(PlatformUtil.getLogDirectory());
    }

    /**
     * Allows any registered application-level services to close any resources
     * specific to this case.
     */
    @Messages({
        "# {0} - serviceName", "Case.serviceCloseResourcesProgressIndicator.title={0} Closing Case Resources",
        "# {0} - service name", "# {1} - exception message", "Case.servicesException.serviceResourcesCloseError=Could not close case resources for {0} service: {1}"
    })
    private void closeAppServiceCaseResources() {
        /*
         * Each service gets its own independently cancellable task, and thus
         * its own task progress indicator.
         */
        for (AutopsyService service : Lookup.getDefault().lookupAll(AutopsyService.class
        )) {
            ProgressIndicator progressIndicator;
            if (RuntimeProperties.runningWithGUI()) {
                progressIndicator = new ModalDialogProgressIndicator(
                        mainFrame,
                        Bundle.Case_serviceCloseResourcesProgressIndicator_title(service.getServiceName()));
            } else {
                progressIndicator = new LoggingProgressIndicator();
            }
            progressIndicator.start(Bundle.Case_progressMessage_preparing());
            AutopsyService.CaseContext context = new AutopsyService.CaseContext(this, progressIndicator);
            String threadNameSuffix = service.getServiceName().replaceAll("[ ]", "-"); //NON-NLS
            threadNameSuffix = threadNameSuffix.toLowerCase();
            TaskThreadFactory threadFactory = new TaskThreadFactory(String.format(CASE_RESOURCES_THREAD_NAME, threadNameSuffix));
            ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
            Future<Void> future = executor.submit(() -> {
                service.closeCaseResources(context);
                return null;
            });
            try {
                future.get();
            } catch (InterruptedException ex) {
                Case.logger.log(Level.SEVERE, String.format("Unexpected interrupt while waiting on %s service to close case resources", service.getServiceName()), ex);
            } catch (CancellationException ex) {
                Case.logger.log(Level.SEVERE, String.format("Unexpected cancellation while waiting on %s service to close case resources", service.getServiceName()), ex);
            } catch (ExecutionException ex) {
                Case.logger.log(Level.SEVERE, String.format("%s service failed to open case resources", service.getServiceName()), ex);
                if (RuntimeProperties.runningWithGUI()) {
                    SwingUtilities.invokeLater(() -> MessageNotifyUtil.Notify.error(
                            Bundle.Case_servicesException_notificationTitle(service.getServiceName()),
                            Bundle.Case_servicesException_serviceResourcesCloseError(service.getServiceName(), ex.getLocalizedMessage())));
                }
            } finally {
                ThreadUtils.shutDownTaskExecutor(executor);
                progressIndicator.finish();
            }
        }
    }

    /**
     * Acquires a case (case directory) lock for the current case.
     *
     * @throws CaseActionException If the lock cannot be acquired.
     */
    @Messages({
        "Case.lockingException.couldNotAcquireSharedLock=Failed to get a shared lock on the case.",
        "Case.lockingException.couldNotAcquireExclusiveLock=Failed to get an exclusive lock on the case."
    })
    private void acquireCaseLock(CaseLockType lockType) throws CaseActionException {
        String caseDir = metadata.getCaseDirectory();
        try {
            CoordinationService coordinationService = CoordinationService.getInstance();
            caseLock = lockType == CaseLockType.SHARED
                    ? coordinationService.tryGetSharedLock(CategoryNode.CASES, caseDir, CASE_LOCK_TIMEOUT_MINS, TimeUnit.MINUTES)
                    : coordinationService.tryGetExclusiveLock(CategoryNode.CASES, caseDir, CASE_LOCK_TIMEOUT_MINS, TimeUnit.MINUTES);
            if (caseLock == null) {
                if (lockType == CaseLockType.SHARED) {
                    throw new CaseActionException(Bundle.Case_lockingException_couldNotAcquireSharedLock());
                } else {
                    throw new CaseActionException(Bundle.Case_lockingException_couldNotAcquireExclusiveLock());
                }
            }
        } catch (InterruptedException | CoordinationServiceException ex) {
            if (lockType == CaseLockType.SHARED) {
                throw new CaseActionException(Bundle.Case_lockingException_couldNotAcquireSharedLock(), ex);
            } else {
                throw new CaseActionException(Bundle.Case_lockingException_couldNotAcquireExclusiveLock(), ex);
            }
        }
    }

    /**
     * Releases a case (case directory) lock for the current case.
     */
    private void releaseCaseLock() {
        if (caseLock != null) {
            try {
                caseLock.release();
                caseLock = null;
            } catch (CoordinationService.CoordinationServiceException ex) {
                logger.log(Level.SEVERE, String.format("Failed to release shared case directory lock for %s", getMetadata().getCaseDirectory()), ex);
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

    /**
     * Deletes a single-user case.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there were one or more errors deleting the
     *                             case. The exception will have a user-friendly
     *                             message and may be a wrapper for a
     *                             lower-level exception.
     */
    @Messages({
        "Case.exceptionMessage.errorsDeletingCase=Errors occured while deleting the case. See the application log for details."
    })
    private static void deleteSingleUserCase(CaseMetadata metadata, ProgressIndicator progressIndicator) throws CaseActionException {
        boolean errorsOccurred = false;
        try {
            deleteTextIndex(metadata, progressIndicator);
        } catch (KeywordSearchServiceException ex) {
            errorsOccurred = true;
            logger.log(Level.WARNING, String.format("Failed to delete text index for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
        }

        try {
            deleteCaseDirectory(metadata, progressIndicator);
        } catch (CaseActionException ex) {
            errorsOccurred = true;
            logger.log(Level.WARNING, String.format("Failed to delete case directory for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
        }

        deleteFromRecentCases(metadata, progressIndicator);

        if (errorsOccurred) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_errorsDeletingCase());
        }
    }

    /**
     * Deletes a multi-user case. This method does so after acquiring the case
     * directory coordination service lock and is intended to be used for
     * deleting simple multi-user cases without auto ingest input. Note that the
     * case directory coordination service node for the case is only deleted if
     * no errors occurred.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException  If there were one or more errors deleting
     *                              the case. The exception will have a
     *                              user-friendly message and may be a wrapper
     *                              for a lower-level exception.
     * @throws InterruptedException If the thread this code is running in is
     *                              interrupted while blocked, i.e., if
     *                              cancellation of the operation is detected
     *                              during a wait.
     */
    @Messages({
        "Case.progressMessage.connectingToCoordSvc=Connecting to coordination service...",
        "# {0} - exception message", "Case.exceptionMessage.failedToConnectToCoordSvc=Failed to connect to coordination service:\n{0}.",
        "Case.exceptionMessage.cannotGetLockToDeleteCase=Cannot delete case because it is open for another user or host.",
        "# {0} - exception message", "Case.exceptionMessage.failedToLockCaseForDeletion=Failed to exclusively lock case for deletion:\n{0}.",
        "Case.progressMessage.fetchingCoordSvcNodeData=Fetching coordination service node data for the case...",
        "# {0} - exception message", "Case.exceptionMessage.failedToFetchCoordSvcNodeData=Failed to fetch coordination service node data:\n{0}.",
        "Case.progressMessage.deletingResourcesCoordSvcNode=Deleting case resources coordination service node...",
        "Case.progressMessage.deletingCaseDirCoordSvcNode=Deleting case directory coordination service node..."
    })
    private static void deleteMultiUserCase(CaseMetadata metadata, ProgressIndicator progressIndicator) throws CaseActionException, InterruptedException {
        progressIndicator.progress(Bundle.Case_progressMessage_connectingToCoordSvc());
        CoordinationService coordinationService;
        try {
            coordinationService = CoordinationService.getInstance();
        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Failed to connect to coordination service when attempting to delete %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
            throw new CaseActionException(Bundle.Case_exceptionMessage_failedToConnectToCoordSvc(ex.getLocalizedMessage()));
        }

        CaseNodeData caseNodeData;
        boolean errorsOccurred = false;
        try (CoordinationService.Lock dirLock = coordinationService.tryGetExclusiveLock(CategoryNode.CASES, metadata.getCaseDirectory())) {
            if (dirLock == null) {
                logger.log(Level.INFO, String.format("Could not delete %s (%s) in %s because a case directory lock was held by another host", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory())); //NON-NLS
                throw new CaseActionException(Bundle.Case_exceptionMessage_cannotGetLockToDeleteCase());
            }

            progressIndicator.progress(Bundle.Case_progressMessage_fetchingCoordSvcNodeData());
            try {
                caseNodeData = CaseNodeData.readCaseNodeData(metadata.getCaseDirectory());
            } catch (CaseNodeDataException | InterruptedException ex) {
                logger.log(Level.SEVERE, String.format("Failed to get coordination service node data %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
                throw new CaseActionException(Bundle.Case_exceptionMessage_failedToFetchCoordSvcNodeData(ex.getLocalizedMessage()));
            }

            errorsOccurred = deleteMultiUserCase(caseNodeData, metadata, progressIndicator, logger);

            progressIndicator.progress(Bundle.Case_progressMessage_deletingResourcesCoordSvcNode());
            try {
                String resourcesLockNodePath = CoordinationServiceUtils.getCaseResourcesNodePath(caseNodeData.getDirectory());
                coordinationService.deleteNode(CategoryNode.CASES, resourcesLockNodePath);
            } catch (CoordinationServiceException ex) {
                if (!isNoNodeException(ex)) {
                    errorsOccurred = true;
                    logger.log(Level.WARNING, String.format("Error deleting the case resources coordination service node for the case at %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
                }
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, String.format("Error deleting the case resources coordination service node for the case at %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
            }

        } catch (CoordinationServiceException ex) {
            logger.log(Level.SEVERE, String.format("Error exclusively locking the case directory for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
            throw new CaseActionException(Bundle.Case_exceptionMessage_failedToLockCaseForDeletion(ex.getLocalizedMessage()));
        }

        if (!errorsOccurred) {
            progressIndicator.progress(Bundle.Case_progressMessage_deletingCaseDirCoordSvcNode());
            try {
                String casDirNodePath = CoordinationServiceUtils.getCaseDirectoryNodePath(caseNodeData.getDirectory());
                coordinationService.deleteNode(CategoryNode.CASES, casDirNodePath);
            } catch (CoordinationServiceException | InterruptedException ex) {
                logger.log(Level.SEVERE, String.format("Error deleting the case directory lock node for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
                errorsOccurred = true;
            }
        }

        if (errorsOccurred) {
            throw new CaseActionException(Bundle.Case_exceptionMessage_errorsDeletingCase());
        }
    }

    /**
     * IMPORTANT: This is a "beta" method and is subject to change or removal
     * without notice!
     *
     * Deletes a mulit-user case by attempting to delete the case database, the
     * text index, the case directory, and the case resources coordination
     * service node for a case, and removes the case from the recent cases menu
     * of the main application window. Callers of this method MUST acquire and
     * release the case directory lock for the case and are responsible for
     * deleting the corresponding coordination service nodes, if desired.
     *
     * @param caseNodeData      The coordination service node data for the case.
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     * @param logger            A logger.
     *
     * @return True if one or more errors occurred (see log for details), false
     *         otherwise.
     *
     * @throws InterruptedException If the thread this code is running in is
     *                              interrupted while blocked, i.e., if
     *                              cancellation of the operation is detected
     *                              during a wait.
     */
    @Beta
    public static boolean deleteMultiUserCase(CaseNodeData caseNodeData, CaseMetadata metadata, ProgressIndicator progressIndicator, Logger logger) throws InterruptedException {
        boolean errorsOccurred = false;
        try {
            deleteMultiUserCaseDatabase(caseNodeData, metadata, progressIndicator, logger);
            deleteMultiUserCaseTextIndex(caseNodeData, metadata, progressIndicator, logger);
            deleteMultiUserCaseDirectory(caseNodeData, metadata, progressIndicator, logger);
            deleteFromRecentCases(metadata, progressIndicator);
        } catch (UserPreferencesException | ClassNotFoundException | SQLException ex) {
            errorsOccurred = true;
            logger.log(Level.WARNING, String.format("Failed to delete the case database for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
        } catch (KeywordSearchServiceException ex) {
            errorsOccurred = true;
            logger.log(Level.WARNING, String.format("Failed to delete the text index for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
        } catch (CaseActionException ex) {
            errorsOccurred = true;
            logger.log(Level.WARNING, String.format("Failed to delete the case directory for %s (%s) in %s", metadata.getCaseDisplayName(), metadata.getCaseName(), metadata.getCaseDirectory()), ex); //NON-NLS
        }
        return errorsOccurred;
    }

    /**
     * Attempts to delete the case database for a multi-user case.
     *
     * @param caseNodeData      The coordination service node data for the case.
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     * @param logger            A logger.
     *
     * @throws UserPreferencesException if there is an error getting the
     *                                  database server connection info.
     * @throws ClassNotFoundException   if there is an error gettting the
     *                                  required JDBC driver.
     * @throws SQLException             if there is an error executing the SQL
     *                                  to drop the database from the database
     *                                  server.
     * @throws InterruptedException     If interrupted while blocked waiting for
     *                                  coordination service data to be written
     *                                  to the coordination service node
     *                                  database.
     */
    @Messages({
        "Case.progressMessage.deletingCaseDatabase=Deleting case database..."
    })
    private static void deleteMultiUserCaseDatabase(CaseNodeData caseNodeData, CaseMetadata metadata, ProgressIndicator progressIndicator, Logger logger) throws UserPreferencesException, ClassNotFoundException, SQLException, InterruptedException {
        if (!caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DB)) {
            progressIndicator.progress(Bundle.Case_progressMessage_deletingCaseDatabase());
            logger.log(Level.INFO, String.format("Deleting case database for %s (%s) in %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory())); //NON-NLS
            CaseDbConnectionInfo info = UserPreferences.getDatabaseConnectionInfo();
            String url = "jdbc:postgresql://" + info.getHost() + ":" + info.getPort() + "/postgres"; //NON-NLS
            Class.forName("org.postgresql.Driver"); //NON-NLS
            try (Connection connection = DriverManager.getConnection(url, info.getUserName(), info.getPassword()); Statement statement = connection.createStatement()) {
                String dbExistsQuery = "SELECT 1 from pg_database WHERE datname = '" + metadata.getCaseDatabaseName() + "'"; //NON-NLS
                try (ResultSet queryResult = statement.executeQuery(dbExistsQuery)) {
                    if (queryResult.next()) {
                        String deleteCommand = "DROP DATABASE \"" + metadata.getCaseDatabaseName() + "\""; //NON-NLS
                        statement.execute(deleteCommand);
                    }
                }
            }
            setDeletedItemFlag(caseNodeData, CaseNodeData.DeletedFlags.CASE_DB);
        }
    }

    /**
     * Attempts to delete the text index for a multi-user case.
     *
     * @param caseNodeData      The coordination service node data for the case.
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     * @param logger            A logger.
     *
     * @throws KeywordSearchServiceException If there is an error deleting the
     *                                       text index.
     * @throws InterruptedException          If interrupted while blocked
     *                                       waiting for coordination service
     *                                       data to be written to the
     *                                       coordination service node database.
     */
    private static void deleteMultiUserCaseTextIndex(CaseNodeData caseNodeData, CaseMetadata metadata, ProgressIndicator progressIndicator, Logger logger) throws KeywordSearchServiceException, InterruptedException {
        if (!caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.TEXT_INDEX)) {
            logger.log(Level.INFO, String.format("Deleting text index for %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory())); //NON-NLS
            deleteTextIndex(metadata, progressIndicator);
            setDeletedItemFlag(caseNodeData, CaseNodeData.DeletedFlags.TEXT_INDEX);
        }
    }

    /**
     * Attempts to delete the text index for a case.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     *
     * @throws KeywordSearchServiceException If there is an error deleting the
     *                                       text index.
     */
    @Messages({
        "Case.progressMessage.deletingTextIndex=Deleting text index..."
    })
    private static void deleteTextIndex(CaseMetadata metadata, ProgressIndicator progressIndicator) throws KeywordSearchServiceException {
        progressIndicator.progress(Bundle.Case_progressMessage_deletingTextIndex());

        for (KeywordSearchService searchService : Lookup.getDefault().lookupAll(KeywordSearchService.class
        )) {
            searchService.deleteTextIndex(metadata);
        }
    }

    /**
     * Attempts to delete the case directory for a multi-user case.
     *
     * @param caseNodeData      The coordination service node data for the case.
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     * @param logger            A logger.
     *
     * @throws CaseActionException  if there is an error deleting the case
     *                              directory.
     * @throws InterruptedException If interrupted while blocked waiting for
     *                              coordination service data to be written to
     *                              the coordination service node database.
     */
    private static void deleteMultiUserCaseDirectory(CaseNodeData caseNodeData, CaseMetadata metadata, ProgressIndicator progressIndicator, Logger logger) throws CaseActionException, InterruptedException {
        if (!caseNodeData.isDeletedFlagSet(CaseNodeData.DeletedFlags.CASE_DIR)) {
            logger.log(Level.INFO, String.format("Deleting case directory for %s", caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory())); //NON-NLS
            deleteCaseDirectory(metadata, progressIndicator);
            setDeletedItemFlag(caseNodeData, CaseNodeData.DeletedFlags.CASE_DIR);
        }
    }

    /**
     * Attempts to delete the case directory for a case.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     *
     * @throws CaseActionException If there is an error deleting the case
     *                             directory.
     */
    @Messages({
        "Case.progressMessage.deletingCaseDirectory=Deleting case directory..."
    })
    private static void deleteCaseDirectory(CaseMetadata metadata, ProgressIndicator progressIndicator) throws CaseActionException {
        progressIndicator.progress(Bundle.Case_progressMessage_deletingCaseDirectory());
        if (!FileUtil.deleteDir(new File(metadata.getCaseDirectory()))) {
            throw new CaseActionException(String.format("Failed to delete %s", metadata.getCaseDirectory())); //NON-NLS
        }
    }

    /**
     * Attempts to remove a case from the recent cases menu if the main
     * application window is present.
     *
     * @param metadata          The case metadata.
     * @param progressIndicator A progress indicator.
     */
    @Messages({
        "Case.progressMessage.removingCaseFromRecentCases=Removing case from Recent Cases menu..."
    })
    private static void deleteFromRecentCases(CaseMetadata metadata, ProgressIndicator progressIndicator) {
        if (RuntimeProperties.runningWithGUI()) {
            progressIndicator.progress(Bundle.Case_progressMessage_removingCaseFromRecentCases());
            SwingUtilities.invokeLater(() -> {
                RecentCases.getInstance().removeRecentCase(metadata.getCaseDisplayName(), metadata.getFilePath().toString());
            });
        }
    }

    /**
     * Examines a coordination service exception to try to determine if it is a
     * "no node" exception, i.e., an operation was attempted on a node that does
     * not exist.
     *
     * @param ex A coordination service exception.
     *
     * @return True or false.
     */
    private static boolean isNoNodeException(CoordinationServiceException ex) {
        boolean isNodeNodeEx = false;
        Throwable cause = ex.getCause();
        if (cause != null) {
            String causeMessage = cause.getMessage();
            isNodeNodeEx = causeMessage.contains(NO_NODE_ERROR_MSG_FRAGMENT);
        }
        return isNodeNodeEx;
    }

    /**
     * Sets a deleted item flag in the coordination service node data for a
     * multi-user case.
     *
     * @param caseNodeData The coordination service node data for the case.
     * @param flag         The flag to set.
     *
     * @throws InterruptedException If interrupted while blocked waiting for
     *                              coordination service data to be written to
     *                              the coordination service node database.
     */
    private static void setDeletedItemFlag(CaseNodeData caseNodeData, CaseNodeData.DeletedFlags flag) throws InterruptedException {
        try {
            caseNodeData.setDeletedFlag(flag);
            CaseNodeData.writeCaseNodeData(caseNodeData);
        } catch (CaseNodeDataException ex) {
            logger.log(Level.SEVERE, String.format("Error updating deleted item flag %s for %s (%s) in %s", flag.name(), caseNodeData.getDisplayName(), caseNodeData.getName(), caseNodeData.getDirectory()), ex);

        }
    }

    /**
     * Initialize the hasData and hasDataSource parameters by checking the
     * database.
     *
     * hasDataSource will be true if any data Source exists the db.
     *
     * hasData will be true if hasDataSource is true or if there are entries in
     * the tsk_object or tsk_host tables.
     *
     * @throws TskCoreException
     */
    private void updateDataParameters() throws TskCoreException {
        hasDataSource = dbHasDataSource();

        if (!hasDataSource) {
            hasData = dbHasData();
        } else {
            hasData = true;
        }
    }

    /**
     * Returns true of there are any data sources in the case database.
     *
     * @return True if this case as a data source.
     *
     * @throws TskCoreException
     */
    private boolean dbHasDataSource() throws TskCoreException {
        String query = "SELECT count(*) AS count FROM (SELECT * FROM data_source_info LIMIT 1)t";
        try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            if (resultSet.next()) {
                return resultSet.getLong("count") > 0;
            }
            return false;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error accessing case database", ex); //NON-NLS
            throw new TskCoreException("Error accessing case databse", ex);
        }
    }

    /**
     * Returns true if the case has data. A case has data if there is at least
     * one row in either the tsk_objects or tsk_hosts table.
     *
     * @return True if there is data in this case.
     *
     * @throws TskCoreException
     */
    private boolean dbHasData() throws TskCoreException {
        // The LIMIT 1 in the subquery should limit the data returned and 
        // make the overall query more efficent.
        String query = "SELECT SUM(cnt) total FROM "
                + "(SELECT COUNT(*) AS cnt FROM "
                + "(SELECT * FROM tsk_objects LIMIT 1)t "
                + "UNION ALL "
                + "SELECT COUNT(*) AS cnt FROM "
                + "(SELECT * FROM tsk_hosts LIMIT 1)r) s";
        try (SleuthkitCase.CaseDbQuery dbQuery = caseDb.executeQuery(query)) {
            ResultSet resultSet = dbQuery.getResultSet();
            if (resultSet.next()) {
                return resultSet.getLong("total") > 0;
            } else {
                return false;
            }
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Error accessing case database", ex); //NON-NLS
            throw new TskCoreException("Error accessing case databse", ex);
        }
    }

    /**
     * Defines the signature for case action methods that can be passed as
     * arguments to the doCaseAction method.
     *
     * @param <T> A ProgressIndicator
     * @param <V> The optional parameters stored in an Object.
     * @param <R> The return type of Void.
     */
    private interface CaseAction<T, V, R> {

        /**
         * The signature for a case action method.
         *
         * @param progressIndicator A ProgressIndicator.
         * @param additionalParams  The optional parameters stored in an Object.
         *
         * @return A Void object (null).
         *
         * @throws CaseActionException
         */
        R execute(T progressIndicator, V additionalParams) throws CaseActionException;
    }

    /**
     * The choices for the case (case directory) coordination service lock used
     * for multi-user cases.
     */
    private enum CaseLockType {
        SHARED, EXCLUSIVE;
    }

    /**
     * A case operation Cancel button listener for use with a
     * ModalDialogProgressIndicator when running with a GUI.
     */
    @ThreadSafe
    private final static class CancelButtonListener implements ActionListener {

        private final String cancellationMessage;
        @GuardedBy("this")
        private boolean cancelRequested;
        @GuardedBy("this")
        private CaseContext caseContext;
        @GuardedBy("this")
        private Future<?> caseActionFuture;

        /**
         * Constructs a case operation Cancel button listener for use with a
         * ModalDialogProgressIndicator when running with a GUI.
         *
         * @param cancellationMessage The message to display in the
         *                            ModalDialogProgressIndicator when the
         *                            cancel button is pressed.
         */
        private CancelButtonListener(String cancellationMessage) {
            this.cancellationMessage = cancellationMessage;
        }

        /**
         * Sets a case context for this listener.
         *
         * @param caseContext A case context object.
         */
        private synchronized void setCaseContext(CaseContext caseContext) {
            this.caseContext = caseContext;
            /*
             * If the cancel button has already been pressed, pass the
             * cancellation on to the case context.
             */
            if (cancelRequested) {
                cancel();
            }
        }

        /**
         * Sets a Future object for a task associated with this listener.
         *
         * @param caseActionFuture A task Future object.
         */
        private synchronized void setCaseActionFuture(Future<?> caseActionFuture) {
            this.caseActionFuture = caseActionFuture;
            /*
             * If the cancel button has already been pressed, cancel the Future
             * of the task.
             */
            if (cancelRequested) {
                cancel();
            }
        }

        /**
         * The event handler for Cancel button pushes.
         *
         * @param event The button event, ignored, can be null.
         */
        @Override
        public synchronized void actionPerformed(ActionEvent event) {
            cancel();
        }

        /**
         * Handles a cancellation request.
         */
        private void cancel() {
            /*
             * At a minimum, set the cancellation requested flag of this
             * listener.
             */
            this.cancelRequested = true;
            if (null != this.caseContext) {
                /*
                 * Set the cancellation request flag and display the
                 * cancellation message in the progress indicator for the case
                 * context associated with this listener.
                 */
                if (RuntimeProperties.runningWithGUI()) {
                    ProgressIndicator progressIndicator = this.caseContext.getProgressIndicator();
                    if (progressIndicator instanceof ModalDialogProgressIndicator) {
                        ((ModalDialogProgressIndicator) progressIndicator).setCancelling(cancellationMessage);
                    }
                }
                this.caseContext.requestCancel();
            }
            if (null != this.caseActionFuture) {
                /*
                 * Cancel the Future of the task associated with this listener.
                 * Note that the task thread will be interrupted if the task is
                 * blocked.
                 */
                this.caseActionFuture.cancel(true);
            }
        }
    }

    /**
     * A thread factory that provides named threads.
     */
    private static class TaskThreadFactory implements ThreadFactory {

        private final String threadName;

        private TaskThreadFactory(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public Thread newThread(Runnable task) {
            return new Thread(task, threadName);
        }

    }

    /**
     * Gets the application name.
     *
     * @return The application name.
     *
     * @deprecated
     */
    @Deprecated
    public static String getAppName() {
        return UserPreferences.getAppName();
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
        return isCaseOpen();
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
