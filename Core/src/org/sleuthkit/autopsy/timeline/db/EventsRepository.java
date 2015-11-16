/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.db;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.isNull;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javax.swing.JOptionPane;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Interval;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.timeline.ProgressUpdate;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.FileSystemTypes;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.RootEventType;
import org.sleuthkit.autopsy.timeline.filters.RootFilter;
import org.sleuthkit.autopsy.timeline.filters.TagNameFilter;
import org.sleuthkit.autopsy.timeline.filters.TagsFilter;
import org.sleuthkit.autopsy.timeline.zooming.ZoomParams;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Provides higher-level public API (over EventsDB) to access events. In theory
 * this insulates the rest of the timeline module form the details of the db
 * implementation. Since there are no other implementations of the database or
 * clients of this class, and no Java Interface defined yet, in practice this
 * just delegates everything to the eventDB. Some results are also cached by
 * this layer.
 *
 * Concurrency Policy:
 *
 * Since almost everything just delegates to the EventDB, which is internally
 * synchronized, we only have to worry about rebuildRepository() which we
 * synchronize on our intrinsic lock.
 *
 */
public class EventsRepository {

    private final static Logger LOGGER = Logger.getLogger(EventsRepository.class.getName());

    private final EventDB eventDB;

    private final DBPopulationService dbPopulationService = new DBPopulationService(this);

    private final LoadingCache<Object, Long> maxCache;

    private final LoadingCache<Object, Long> minCache;

    private final FilteredEventsModel modelInstance;

    private final LoadingCache<Long, TimeLineEvent> idToEventCache;
    private final LoadingCache<ZoomParams, Map<EventType, Long>> eventCountsCache;
    private final LoadingCache<ZoomParams, List<EventCluster>> eventClusterCache;

    private final ObservableMap<Long, String> datasourcesMap = FXCollections.observableHashMap();
    private final ObservableMap<Long, String> hashSetMap = FXCollections.observableHashMap();
    private final ObservableList<TagName> tagNames = FXCollections.observableArrayList();
    private final Case autoCase;

    public Case getAutoCase() {
        return autoCase;
    }

    public ObservableList<TagName> getTagNames() {
        return tagNames;
    }

    synchronized public ObservableMap<Long, String> getDatasourcesMap() {
        return datasourcesMap;
    }

    synchronized public ObservableMap<Long, String> getHashSetMap() {
        return hashSetMap;
    }

    public Interval getBoundingEventsInterval(Interval timeRange, RootFilter filter) {
        return eventDB.getBoundingEventsInterval(timeRange, filter);
    }

    /**
     * @return a FilteredEvetns object with this repository as underlying source
     *         of events
     */
    public FilteredEventsModel getEventsModel() {
        return modelInstance;
    }

    public EventsRepository(Case autoCase, ReadOnlyObjectProperty<ZoomParams> currentStateProperty) {
        this.autoCase = autoCase;
        //TODO: we should check that case is open, or get passed a case object/directory -jm
        this.eventDB = EventDB.getEventDB(autoCase);
        populateFilterData(autoCase.getSleuthkitCase());
        idToEventCache = CacheBuilder.newBuilder()
                .maximumSize(5000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(CacheLoader.from(eventDB::getEventById));
        eventCountsCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(CacheLoader.from(eventDB::countEventsByType));
        eventClusterCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES
                ).build(CacheLoader.from(eventDB::getClusteredEvents));
        maxCache = CacheBuilder.newBuilder().build(CacheLoader.from(eventDB::getMaxTime));
        minCache = CacheBuilder.newBuilder().build(CacheLoader.from(eventDB::getMinTime));
        this.modelInstance = new FilteredEventsModel(this, currentStateProperty);
    }

    /**
     * @return min time (in seconds from unix epoch)
     */
    public Long getMaxTime() {
        return maxCache.getUnchecked("max"); // NON-NLS
//        return eventDB.getMaxTime();
    }

    /**
     * @return max tie (in seconds from unix epoch)
     */
    public Long getMinTime() {
        return minCache.getUnchecked("min"); // NON-NLS
//        return eventDB.getMinTime();
    }

    private void recordLastArtifactID(long lastArtfID) {
        eventDB.recordLastArtifactID(lastArtfID);
    }

    private void recordWasIngestRunning(Boolean wasIngestRunning) {
        eventDB.recordWasIngestRunning(wasIngestRunning);
    }

    private void recordLastObjID(Long lastObjID) {
        eventDB.recordLastObjID(lastObjID);
    }

    public boolean getWasIngestRunning() {
        return eventDB.getWasIngestRunning();
    }

    public Long getLastObjID() {
        return eventDB.getLastObjID();
    }

    public long getLastArtfactID() {
        return eventDB.getLastArtfactID();
    }

    public TimeLineEvent getEventById(Long eventID) {
        return idToEventCache.getUnchecked(eventID);
    }

    synchronized public Set<TimeLineEvent> getEventsById(Collection<Long> eventIDs) {
        return eventIDs.stream()
                .map(idToEventCache::getUnchecked)
                .collect(Collectors.toSet());

    }

    synchronized public List<EventCluster> getEventClusters(ZoomParams params) {
        return eventClusterCache.getUnchecked(params);
    }

    synchronized public Map<EventType, Long> countEvents(ZoomParams params) {
        return eventCountsCache.getUnchecked(params);
    }

    private void invalidateCaches() {
        minCache.invalidateAll();
        maxCache.invalidateAll();
        eventCountsCache.invalidateAll();
        eventClusterCache.invalidateAll();
        idToEventCache.invalidateAll();
    }

    public Set<Long> getEventIDs(Interval timeRange, RootFilter filter) {
        return eventDB.getEventIDs(timeRange, filter);
    }

    public Interval getSpanningInterval(Collection<Long> eventIDs) {
        return eventDB.getSpanningInterval(eventIDs);
    }

    public boolean hasNewColumns() {
        return eventDB.hasNewColumns();
    }

    /**
     * get a count of tagnames applied to the given event ids as a map from
     * tagname displayname to count of tag applications
     *
     * @param eventIDsWithTags the event ids to get the tag counts map for
     *
     * @return a map from tagname displayname to count of applications
     */
    public Map<String, Long> getTagCountsByTagName(Set<Long> eventIDsWithTags) {
        return eventDB.getTagCountsByTagName(eventIDsWithTags);
    }

    /**
     * use the given SleuthkitCase to update the data used to determine the
     * available filters.
     *
     * @param skCase
     */
    synchronized private void populateFilterData(SleuthkitCase skCase) {

        for (Map.Entry<Long, String> hashSet : eventDB.getHashSetNames().entrySet()) {
            hashSetMap.putIfAbsent(hashSet.getKey(), hashSet.getValue());
        }
        //because there is no way to remove a datasource we only add to this map.
        for (Long id : eventDB.getDataSourceIDs()) {
            try {
                datasourcesMap.putIfAbsent(id, skCase.getContentById(id).getDataSource().getName());
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Failed to get datasource by ID.", ex);
            }
        }

        try {
            //should this only be tags applied to files or event bearing artifacts?
            tagNames.setAll(skCase.getTagNamesInUse());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get tag names in use.", ex);
        }
    }

    synchronized public Set<Long> addTag(long objID, Long artifactID, Tag tag) {
        Set<Long> updatedEventIDs = eventDB.addTag(objID, artifactID, tag);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized public Set<Long> deleteTag(long objID, Long artifactID, long tagID, boolean tagged) {
        Set<Long> updatedEventIDs = eventDB.deleteTag(objID, artifactID, tagID, tagged);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized private void invalidateCaches(Set<Long> updatedEventIDs) {
        eventCountsCache.invalidateAll();
        eventClusterCache.invalidateAll();
        idToEventCache.invalidateAll(updatedEventIDs);
        try {
            tagNames.setAll(autoCase.getSleuthkitCase().getTagNamesInUse());
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get tag names in use.", ex);
        }
    }

    /**
     * "sync" the given tags filter with the tagnames in use: Disable filters
     * for tags that are not in use in the case, and add new filters for tags
     * that don't have them. New filters are selected by default.
     *
     * @param tagsFilter the tags filter to modify so it is consistent with the
     *                   tags in use in the case
     */
    public void syncTagsFilter(TagsFilter tagsFilter) {
        for (TagName t : tagNames) {
            tagsFilter.addSubFilter(new TagNameFilter(t, autoCase));
        }
        for (TagNameFilter t : tagsFilter.getSubFilters()) {
            t.setDisabled(tagNames.contains(t.getTagName()) == false);
        }
    }

    static private final Executor workerExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("eventrepository-worker-%d").build());

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public Worker<Void> rebuildRepository() {
        return rebuildRepository(DBPopulationService.DBPopulationMode.FULL);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public Worker<Void> rebuildTags() {
        return rebuildRepository(DBPopulationService.DBPopulationMode.TAGS_ONLY);
    }

    /**
     *
     * @param mode the value of mode
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private Worker<Void> rebuildRepository(final DBPopulationService.DBPopulationMode mode) {
        LOGGER.log(Level.INFO, "(re) starting {0}db population task", mode);
        dbPopulationService.setDBPopulationMode(mode);
        dbPopulationService.restart();
        return dbPopulationService;
    }

    private static class DBPopulationService extends Service<Void> {

        enum DBPopulationMode {

            FULL,
            TAGS_ONLY;
        }

        private final EventsRepository eventRepo;

        @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
        private DBPopulationMode dbPopulationMode = DBPopulationMode.FULL;

        DBPopulationService(EventsRepository eventRepo) {
            this.eventRepo = eventRepo;
            setExecutor(workerExecutor);
        }

        @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
        public final void setDBPopulationMode(DBPopulationMode value) {
            dbPopulationMode = value;
        }

        @Override
        protected Task<Void> createTask() {
            DBPopulationMode dbPopMode = dbPopulationMode;
            switch (dbPopMode) {
                case FULL:
                    return eventRepo.new DBPopulationWorker();
                case TAGS_ONLY:
                    return eventRepo.new RebuildTagsWorker();
                default:
                    throw new IllegalArgumentException("Unknown db population mode: " + dbPopMode + ". Skipping db population.");
            }
        }
    }

    /**
     *
     * @param lastObjId     the value of lastObjId
     * @param lastArtfID    the value of lastArtfID
     * @param injestRunning the value of injestRunning
     */
    public void recordDBPopulationState(final long lastObjId, final long lastArtfID, final Boolean injestRunning) {
        recordLastObjID(lastObjId);
        recordLastArtifactID(lastArtfID);
        recordWasIngestRunning(injestRunning);
    }

    public boolean isRebuilding() {
        FutureTask<Boolean> task = new FutureTask<>(dbPopulationService::isRunning);
        Platform.runLater(task);
        try {
            return task.get();
        } catch (InterruptedException interruptedException) {
        } catch (ExecutionException executionException) {
        }
        return false;
    }

    /**
     * A base class for Tasks that show a updates a {@link ProgressHandle} as it
     * performs its background work on the events DB.
     *
     * //TODO: I don't like the coupling to ProgressHandle, but the
     * alternatives I can think of seem even worse. -jm
     */
    private abstract class DBProgressWorker extends Task<Void> {

        final SleuthkitCase skCase;
        final TagsManager tagsManager;

        volatile ProgressHandle progressHandle;

        DBProgressWorker(String initialProgressDisplayName) {
            progressHandle = ProgressHandleFactory.createHandle(initialProgressDisplayName, this::cancel);
            skCase = autoCase.getSleuthkitCase();
            tagsManager = autoCase.getServices().getTagsManager();
        }

        /**
         * update progress UIs
         *
         * @param chunk
         */
        final protected void update(ProgressUpdate chunk) {
            updateProgress(chunk.getProgress(), chunk.getTotal());
            updateMessage(chunk.getDetailMessage());
            updateTitle(chunk.getHeaderMessage());

            if (chunk.getTotal() >= 0) {
                progressHandle.progress(chunk.getProgress());
            }
            progressHandle.setDisplayName(chunk.getHeaderMessage());
            progressHandle.progress(chunk.getDetailMessage());
        }

    }

    public boolean areFiltersEquivalent(RootFilter f1, RootFilter f2) {
        return SQLHelper.getSQLWhere(f1).equals(SQLHelper.getSQLWhere(f2));
    }

    private class RebuildTagsWorker extends DBProgressWorker {

        @NbBundle.Messages("RebuildTagsWorker.task.displayName=refreshing tags")
        RebuildTagsWorker() {
            super(Bundle.RebuildTagsWorker_task_displayName());
        }

        @Override
        @NbBundle.Messages({"progressWindow.msg.refreshingFileTags=refreshing file tags",
            "progressWindow.msg.refreshingResultTags=refreshing result tags",
            "progressWindow.msg.commitingTags=committing tag changes"})
        protected Void call() throws Exception {
            progressHandle.start();
            EventDB.EventTransaction trans = eventDB.beginTransaction();
            LOGGER.log(Level.INFO, "dropping old tags"); // NON-NLS
            eventDB.reInitializeTags();

            LOGGER.log(Level.INFO, "updating content tags"); // NON-NLS
            List<ContentTag> contentTags = tagsManager.getAllContentTags();
            progressHandle.finish();
            progressHandle = ProgressHandleFactory.createHandle(Bundle.progressWindow_msg_refreshingFileTags(), this::cancel);
            int currentWorkTotal = contentTags.size();
            progressHandle.start(currentWorkTotal);

            for (int i = 0; i < currentWorkTotal; i++) {
                if (isCancelled()) {
                    break;
                }
                update(new ProgressUpdate(i, currentWorkTotal, Bundle.progressWindow_msg_refreshingFileTags()));
                ContentTag contentTag = contentTags.get(i);
                eventDB.addTag(contentTag.getContent().getId(), null, contentTag);
            }

            LOGGER.log(Level.INFO, "updating artifact tags"); // NON-NLS
            List<BlackboardArtifactTag> artifactTags = tagsManager.getAllBlackboardArtifactTags();
            progressHandle.finish();
            progressHandle = ProgressHandleFactory.createHandle(Bundle.progressWindow_msg_refreshingResultTags(), this::cancel);
            currentWorkTotal = artifactTags.size();
            progressHandle.start(currentWorkTotal);

            for (int i = 0; i < currentWorkTotal; i++) {
                if (isCancelled()) {
                    break;
                }
                update(new ProgressUpdate(i, currentWorkTotal, Bundle.progressWindow_msg_refreshingResultTags()));
                BlackboardArtifactTag artifactTag = artifactTags.get(i);
                eventDB.addTag(artifactTag.getContent().getId(), artifactTag.getArtifact().getArtifactID(), artifactTag);
            }

            LOGGER.log(Level.INFO, "committing tags"); // NON-NLS
            progressHandle.finish();
            progressHandle = ProgressHandleFactory.createHandle(Bundle.progressWindow_msg_commitingTags());
            progressHandle.start();
            update(new ProgressUpdate(0, -1, Bundle.progressWindow_msg_commitingTags()));

            if (isCancelled()) {
                eventDB.rollBackTransaction(trans);
            } else {
                eventDB.commitTransaction(trans);
            }
            eventDB.analyze();
            populateFilterData(skCase);
            invalidateCaches();

            progressHandle.finish();
            return null;
        }

        @Override
        @NbBundle.Messages("msgdlg.tagsproblem.text=There was a problem refreshing the tagged events."
                + "  Some events may have inacurate tags. See the log for details.")
        protected void done() {
            super.done();
            try {
                get();
            } catch (CancellationException ex) {
                LOGGER.log(Level.WARNING, "Timeline database population was cancelled by the user.  "
                        + "Not all events may be present or accurate."); // NON-NLS
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Unexpected exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_tagsproblem_text());
            }
        }
    }

    private class DBPopulationWorker extends DBProgressWorker {

        @NbBundle.Messages("DBPopulationWorker.task.displayName=(re)initializing events database")
        DBPopulationWorker() {
            super(Bundle.DBPopulationWorker_task_displayName());
        }

        @Override
        @NbBundle.Messages({"progressWindow.msg.populateMacEventsFiles=Populating MAC time events for files",
            "progressWindow.msg.reinit_db=(Re)Initializing events database",
            "progressWindow.msg.gatheringData=Gather event data",
            "progressWindow.msg.commitingDb=committing events db"})
        protected Void call() throws Exception {
            LOGGER.log(Level.INFO, "Beginning population of timeline db."); // NON-NLS
            progressHandle.start();
            update(new ProgressUpdate(0, -1, Bundle.progressWindow_msg_reinit_db()));
            //reset database //TODO: can we do more incremental updates? -jm
            eventDB.reInitializeDB();

            update(new ProgressUpdate(0, -1, Bundle.progressWindow_msg_gatheringData()));
            long lastObjId = skCase.getLastObjectId();
            long lastArtfID = TimeLineController.getCaseLastArtifactID(skCase);
            boolean injestRunning = IngestManager.getInstance().isIngestRunning();

            //grab ids of all files
            List<Long> fileIDs = skCase.findAllFileIdsWhere("name != '.' AND name != '..'");
            final int numFiles = fileIDs.size();
            progressHandle.switchToDeterminate(numFiles);
            update(new ProgressUpdate(0, numFiles, Bundle.progressWindow_msg_populateMacEventsFiles()));

            //insert file events into db
            EventDB.EventTransaction trans = eventDB.beginTransaction();
            for (int i = 0; i < numFiles; i++) {
                if (isCancelled()) {
                    break;
                } else {
                    long fID = fileIDs.get(i);
                    try {
                        AbstractFile f = skCase.getAbstractFileById(fID);

                        if (isNull(f)) {
                            LOGGER.log(Level.WARNING, "Failed to get data for file : {0}", fID); // NON-NLS
                        } else {
                            insertEventsForFile(f, trans);
                            update(new ProgressUpdate(i, numFiles,
                                    Bundle.progressWindow_msg_populateMacEventsFiles(), f.getName()));
                        }
                    } catch (TskCoreException tskCoreException) {
                        LOGGER.log(Level.SEVERE, "Failed to insert MAC time events for file : " + fID, tskCoreException); // NON-NLS
                    }
                }
            }

            //insert artifact based events
            //TODO: use (not-yet existing api) to grab all artifacts with timestamps, rather than the hardcoded lists in EventType -jm
            for (EventType type : RootEventType.allTypes) {
                if (isCancelled()) {
                    break;
                }
                //skip file_system events, they are already handled above.
                if (type instanceof ArtifactEventType) {
                    populateEventType((ArtifactEventType) type, trans);
                }
            }

            progressHandle.finish();
            progressHandle = ProgressHandleFactory.createHandle(Bundle.progressWindow_msg_commitingDb());
            progressHandle.start();
            update(new ProgressUpdate(0, -1, Bundle.progressWindow_msg_commitingDb()));

            if (isCancelled()) {
                eventDB.rollBackTransaction(trans);
            } else {
                eventDB.commitTransaction(trans);
            }
            eventDB.analyze();
            populateFilterData(skCase);
            invalidateCaches();

            recordDBPopulationState(lastObjId, lastArtfID, injestRunning);
            progressHandle.finish();
            return null;
        }

        private void insertEventsForFile(AbstractFile f, EventDB.EventTransaction trans) throws TskCoreException {
            //gather time stamps into map
            EnumMap<FileSystemTypes, Long> timeMap = new EnumMap<>(FileSystemTypes.class);
            timeMap.put(FileSystemTypes.FILE_CREATED, f.getCrtime());
            timeMap.put(FileSystemTypes.FILE_ACCESSED, f.getAtime());
            timeMap.put(FileSystemTypes.FILE_CHANGED, f.getCtime());
            timeMap.put(FileSystemTypes.FILE_MODIFIED, f.getMtime());

            /*
             * if there are no legitimate ( greater tan zero ) time stamps ( eg,
             * logical/local files) skip the rest of the event generation: this
             * should result in droping logical files, since they do not have
             * legitimate time stamps.
             */
            if (Collections.max(timeMap.values()) > 0) {
                final String uniquePath = f.getUniquePath();
                final String parentPath = f.getParentPath();
                long datasourceID = f.getDataSource().getId();
                String datasourceName = StringUtils.substringBeforeLast(uniquePath, parentPath);

                String rootFolder = StringUtils.substringBefore(StringUtils.substringAfter(parentPath, "/"), "/");
                String shortDesc = datasourceName + "/" + StringUtils.defaultString(rootFolder);
                shortDesc = shortDesc.endsWith("/") ? shortDesc : shortDesc + "/";
                String medDesc = datasourceName + parentPath;

                final TskData.FileKnown known = f.getKnown();
                Set<String> hashSets = f.getHashSetNames();
                List<ContentTag> tags = tagsManager.getContentTagsByContent(f);

                for (Map.Entry<FileSystemTypes, Long> timeEntry : timeMap.entrySet()) {
                    /*
                     * if the time is legitimate ( greater than zero ) insert it
                     * into the db
                     */
                    if (timeEntry.getValue() > 0) {
                        eventDB.insertEvent(timeEntry.getValue(), timeEntry.getKey(),
                                datasourceID, f.getId(), null, uniquePath, medDesc,
                                shortDesc, known, hashSets, tags, trans);
                    }
                }
            }
        }

        @Override
        @NbBundle.Messages("msgdlg.problem.text=There was a problem populating the timeline."
                + "  Not all events may be present or accurate.")
        protected void done() {
            super.done();
            try {
                get();
            } catch (CancellationException ex) {
                LOGGER.log(Level.WARNING, "Timeline database population was cancelled by the user. "
                        + " Not all events may be present or accurate."); // NON-NLS
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Unexpected exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_problem_text());
            }
        }

        /**
         * populate all the events of one subtype
         *
         * @param subType the subtype to populate
         * @param trans   the db transaction to use
         * @param skCase  a reference to the sleuthkit case
         */
        @NbBundle.Messages({"# {0} - event type ", "progressWindow.populatingXevents=Populating {0} events"})
        private void populateEventType(final ArtifactEventType type, EventDB.EventTransaction trans) {
            try {
                //get all the blackboard artifacts corresponding to the given event sub_type
                final ArrayList<BlackboardArtifact> blackboardArtifacts = skCase.getBlackboardArtifacts(type.getArtifactType());
                final int numArtifacts = blackboardArtifacts.size();
                progressHandle.finish();
                progressHandle = ProgressHandleFactory.createHandle(Bundle.progressWindow_populatingXevents(type.getDisplayName()), () -> cancel(true));
                progressHandle.start(numArtifacts);
                for (int i = 0; i < numArtifacts; i++) {
                    try {
                        //for each artifact, extract the relevant information for the descriptions
                        insertEventForArtifact(type, blackboardArtifacts.get(i), trans);
                        update(new ProgressUpdate(i, numArtifacts,
                                Bundle.progressWindow_populatingXevents(type.getDisplayName())));
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "There was a problem inserting event for artifact: " + blackboardArtifacts.get(i).getArtifactID(), ex); // NON-NLS
                    }
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "There was a problem getting events with sub type " + type.toString() + ".", ex); // NON-NLS
            }
        }

        private void insertEventForArtifact(final ArtifactEventType type, BlackboardArtifact bbart, EventDB.EventTransaction trans) throws TskCoreException {
            ArtifactEventType.AttributeEventDescription eventDescription = ArtifactEventType.buildEventDescription(type, bbart);
            /*
             * if the time is legitimate ( greater than zero ) insert it into
             * the db
             */
            if (eventDescription != null && eventDescription.getTime() > 0) {
                long objectID = bbart.getObjectID();
                AbstractFile f = skCase.getAbstractFileById(objectID);
                long datasourceID = f.getDataSource().getId();
                long artifactID = bbart.getArtifactID();
                Set<String> hashSets = f.getHashSetNames();
                List<BlackboardArtifactTag> tags = tagsManager.getBlackboardArtifactTagsByArtifact(bbart);
                String fullDescription = eventDescription.getFullDescription();
                String medDescription = eventDescription.getMedDescription();
                String shortDescription = eventDescription.getShortDescription();
                eventDB.insertEvent(eventDescription.getTime(), type, datasourceID, objectID, artifactID, fullDescription, medDescription, shortDescription, null, hashSets, tags, trans);
            }
        }
    }
}
