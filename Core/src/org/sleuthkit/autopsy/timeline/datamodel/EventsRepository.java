/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Objects.isNull;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.concurrent.Worker;
import javax.swing.JOptionPane;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.CancellationProgressTask;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.timeline.ArtifactEventType;
import org.sleuthkit.datamodel.timeline.CombinedEvent;
import org.sleuthkit.datamodel.timeline.EventStripe;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.SingleEvent;
import org.sleuthkit.datamodel.timeline.ZoomParams;
import org.sleuthkit.datamodel.timeline.filters.RootFilter;
import org.sleuthkit.datamodel.timeline.filters.TagNameFilter;
import org.sleuthkit.datamodel.timeline.filters.TagsFilter;

/**
 * Provides higher-level public API (over TimelineManager) to access events. In
 * theory this insulates the rest of the timeline module from the details of the
 * db implementation. Since there are no other implementations of the database
 * or clients of this class, and no Java Interface defined yet, in practice this
 * just delegates everything to the eventDB. Some results are also cached by
 * this layer.
 *
 * Concurrency Policy:
 *
 * Since almost everything just delegates to the TimelineManager, which is
 * internally synchronized, we only have to worry about rebuildRepository()
 * which we synchronize on our intrinsic lock.
 *
 */
public class EventsRepository {

    private final static Logger logger = Logger.getLogger(EventsRepository.class.getName());

    private final Executor workerExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("eventrepository-worker-%d").build()); //NON-NLS
    private DBPopulationWorker dbWorker;
    private final TimelineManager eventManager;
    private final Case autoCase;
    private final FilteredEventsModel modelInstance;

    private final LoadingCache<Object, Long> maxCache;
    private final LoadingCache<Object, Long> minCache;
    private final LoadingCache<Long, SingleEvent> idToEventCache;
    private final LoadingCache<ZoomParams, Map<EventType, Long>> eventCountsCache;
    private final LoadingCache<ZoomParams, List<EventStripe>> eventStripeCache;

    private final ObservableMap<Long, String> datasourcesMap = FXCollections.observableHashMap();
    private final ObservableSet< String> hashSets = FXCollections.observableSet();
    private final ObservableList<TagName> tagNames = FXCollections.observableArrayList();

    public Case getAutoCase() {
        return autoCase;
    }

    public ObservableList<TagName> getTagNames() {
        return tagNames;
    }

    synchronized public ObservableMap<Long, String> getDatasourcesMap() {
        return datasourcesMap;
    }

    synchronized public ObservableSet< String> getHashSets() {
        return hashSets;
    }

    public Interval getBoundingEventsInterval(Interval timeRange, RootFilter filter, DateTimeZone timeZone) throws TskCoreException {
        return eventManager.getSpanningInterval(timeRange, filter, timeZone);
    }

    /**
     * @return a FilteredEvetns object with this repository as underlying source
     *         of events
     */
    public FilteredEventsModel getEventsModel() {
        return modelInstance;
    }

    public EventsRepository(Case autoCase, ReadOnlyObjectProperty<ZoomParams> currentStateProperty) throws TskCoreException {
        this.autoCase = autoCase;
        this.eventManager = autoCase.getSleuthkitCase().getTimelineManager();
        populateFilterData(autoCase.getSleuthkitCase());
        idToEventCache = CacheBuilder.newBuilder()
                .maximumSize(5000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoader<Long, SingleEvent>() {
                    @Override
                    public SingleEvent load(Long eventID) throws TskCoreException {
                        return eventManager.getEventById(eventID);
                    }
                });
        eventCountsCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoader<ZoomParams, Map<EventType, Long>>() {
                    @Override
                    public Map<EventType, Long> load(ZoomParams params) throws TskCoreException {
                        return eventManager.countEventsByType(params);
                    }
                });
        eventStripeCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES
                ).build(new CacheLoader<ZoomParams, List<EventStripe>>() {
                    @Override
                    public List<EventStripe> load(ZoomParams params) throws TskCoreException {
                        return eventManager.getEventStripes(params, TimeLineController.getJodaTimeZone());
                    }
                });
        maxCache = CacheBuilder.newBuilder().build(new CacheLoader<Object, Long>() {
            @Override
            public Long load(Object ignored) throws TskCoreException {
                return eventManager.getMaxTime();
            }
        });
        minCache = CacheBuilder.newBuilder().build(new CacheLoader<Object, Long>() {
            @Override
            public Long load(Object ignored) throws TskCoreException {
                return eventManager.getMinTime();
            }
        });
        this.modelInstance = new FilteredEventsModel(this, currentStateProperty);
    }

    /**
     * @return min time (in seconds from unix epoch)
     */
    public Long getMaxTime() {
        return maxCache.getUnchecked("max"); // NON-NLS

    }

    /**
     * @return max tie (in seconds from unix epoch)
     */
    public Long getMinTime() {
        return minCache.getUnchecked("min"); // NON-NLS

    }

    public SingleEvent getEventById(Long eventID) {
        return idToEventCache.getUnchecked(eventID);
    }

    synchronized public Set<SingleEvent> getEventsById(Collection<Long> eventIDs) {
        return eventIDs.stream()
                .map(idToEventCache::getUnchecked)
                .collect(Collectors.toSet());

    }

    synchronized public List<EventStripe> getEventStripes(ZoomParams params) {
        try {
            return eventStripeCache.get(params);
        } catch (ExecutionException ex) {
            logger.log(Level.SEVERE, "Failed to load Event Stripes from cache for " + params.toString(), ex); //NON-NLS
            return Collections.emptyList();
        }
    }

    synchronized public Map<EventType, Long> countEvents(ZoomParams params) throws ExecutionException {
        return eventCountsCache.get(params);
    }

    synchronized public int countAllEvents() throws TskCoreException {
        return eventManager.countAllEvents();
    }

    /**
     * Get a List of event IDs for the events that are derived from the given
     * file.
     *
     * @param file                    The AbstractFile to get derived event IDs
     *                                for.
     * @param includeDerivedArtifacts If true, also get event IDs for events
     *                                derived from artifacts derived form this
     *                                file. If false, only gets events derived
     *                                directly from this file (file system
     *                                timestamps).
     *
     * @return A List of event IDs for the events that are derived from the
     *         given file.
     */
    public List<Long> getEventIDsForFile(AbstractFile file, boolean includeDerivedArtifacts) throws TskCoreException {
        return eventManager.getEventIDsForFile(file, includeDerivedArtifacts);
    }

    /**
     * Get a List of event IDs for the events that are derived from the given
     * artifact.
     *
     * @param artifact The BlackboardArtifact to get derived event IDs for.
     *
     * @return A List of event IDs for the events that are derived from the
     *         given artifact.
     */
    public List<Long> getEventIDsForArtifact(BlackboardArtifact artifact) throws TskCoreException {
        return eventManager.getEventIDsForArtifact(artifact);
    }

    private void invalidateCaches() {
        minCache.invalidateAll();
        maxCache.invalidateAll();
        eventCountsCache.invalidateAll();
        eventStripeCache.invalidateAll();
        idToEventCache.invalidateAll();
    }

    public List<Long> getEventIDs(Interval timeRange, RootFilter filter) throws TskCoreException {
        return eventManager.getEventIDs(timeRange, filter);
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @param timeRange The Interval that all returned events must be within.
     * @param filter    The Filter that all returned events must pass.
     *
     * @return A List of combined events, sorted by timestamp.
     */
    public List<CombinedEvent> getCombinedEvents(Interval timeRange, RootFilter filter) throws TskCoreException {
        return eventManager.getCombinedEvents(timeRange, filter);
    }

    public Interval getSpanningInterval(Collection<Long> eventIDs) throws TskCoreException {
        return eventManager.getSpanningInterval(eventIDs);
    }

    public boolean hasNewColumns() throws TskCoreException {
        return eventManager.hasNewColumns();
    }

    /**
     * get a count of tagnames applied to the given event ids as a map from
     * tagname displayname to count of tag applications
     *
     * @param eventIDsWithTags the event ids to get the tag counts map for
     *
     * @return a map from tagname displayname to count of applications
     */
    public Map<String, Long> getTagCountsByTagName(Set<Long> eventIDsWithTags) throws TskCoreException {
        return eventManager.getTagCountsByTagName(eventIDsWithTags);
    }

    /**
     * use the given SleuthkitCase to update the data used to determine the
     * available filters.
     *
     * @param skCase
     */
    synchronized private void populateFilterData(SleuthkitCase skCase) throws TskCoreException {

        hashSets.addAll(eventManager.getHashSetNames());

        //because there is no way to remove a datasource we only add to this map.
        for (Long id : eventManager.getDataSourceIDs()) {
            try {
                datasourcesMap.putIfAbsent(id, skCase.getContentById(id).getDataSource().getName());
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Failed to get datasource by ID.", ex); //NON-NLS
            }
        }

        try {
            //should this only be tags applied to files or event bearing artifacts?
            tagNames.setAll(skCase.getTagNamesInUse());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get tag names in use.", ex); //NON-NLS
        }
    }

    synchronized public Set<Long> addTag(long objID, Long artifactID, Tag tag) throws TskCoreException {
        Set<Long> updatedEventIDs = eventManager.setEventsTagged(objID, artifactID, true);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized public Set<Long> deleteTag(long objID, Long artifactID, long tagID, boolean tagged) throws TskCoreException {
        Set<Long> updatedEventIDs = eventManager.setEventsTagged(objID, artifactID, tagged);
        if (!updatedEventIDs.isEmpty()) {
            invalidateCaches(updatedEventIDs);
        }
        return updatedEventIDs;
    }

    synchronized private void invalidateCaches(Set<Long> updatedEventIDs) {
        eventCountsCache.invalidateAll();
        eventStripeCache.invalidateAll();
        idToEventCache.invalidateAll(updatedEventIDs);
        try {
            tagNames.setAll(autoCase.getSleuthkitCase().getTagNamesInUse());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get tag names in use.", ex); //NON-NLS
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
            tagsFilter.addSubFilter(new TagNameFilter(t));
        }
        for (TagNameFilter t : tagsFilter.getSubFilters()) {
            t.setDisabled(tagNames.contains(t.getTagName()) == false);
        }
    }

    public boolean areFiltersEquivalent(RootFilter filter1, RootFilter filter2) {
        return eventManager.getSQLWhere(filter1).equals(eventManager.getSQLWhere(filter2));
    }

    /**
     *
     * rebuild the entire repo.
     *
     * @param onStateChange called when he background task changes state.
     *                      Clients can use this to handle failure, or cleanup
     *                      operations for example.
     *
     * @return the task that will rebuild the repo in a background thread. The
     *         task has already been started.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public CancellationProgressTask<Void> rebuildRepository(Consumer<Worker.State> onStateChange) {
        return rebuildRepository(DBPopulationMode.FULL, onStateChange);
    }

    /**
     *
     * drop and rebuild the tags in the repo.
     *
     * @param onStateChange called when he background task changes state.
     *                      Clients can use this to handle failure, or cleanup
     *                      operations for example.
     *
     * @return the task that will rebuild the repo in a background thread. The
     *         task has already been started.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    public CancellationProgressTask<Void> rebuildTags(Consumer<Worker.State> onStateChange) {
        return rebuildRepository(DBPopulationMode.TAGS_ONLY, onStateChange);
    }

    /**
     * rebuild the repo.
     *
     * @param mode          the rebuild mode to use.
     * @param onStateChange called when he background task changes state.
     *                      Clients can use this to handle failure, or cleanup
     *                      operations for example.
     *
     * @return the task that will rebuild the repo in a background thread. The
     *         task has already been started.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private CancellationProgressTask<Void> rebuildRepository(final DBPopulationMode mode, Consumer<Worker.State> onStateChange) {
        logger.log(Level.INFO, "(re)starting {0} db population task", mode); //NON-NLS
        if (dbWorker != null) {
            dbWorker.cancel();
        }
        dbWorker = new DBPopulationWorker(mode, onStateChange);
        workerExecutor.execute(dbWorker);
        return dbWorker;
    }

    ImmutableList<EventType> getEventTypes() {
        return eventManager.getEventTypes();
    }

    private enum DBPopulationMode {

        FULL,
        TAGS_ONLY;
    }

    /**
     *  //TODO: I don't like the coupling to ProgressHandle in this task, but
     * the alternatives I can think of seem even worse. -jm
     */
    private class DBPopulationWorker extends CancellationProgressTask<Void> {

        private final ReadOnlyBooleanWrapper cancellable = new ReadOnlyBooleanWrapper(true);

        private final DBPopulationMode dbPopulationMode;
        private final SleuthkitCase skCase;
        private final TagsManager tagsManager;

        private ProgressHandle progressHandle;

        @Override
        public ReadOnlyBooleanProperty cancellableProperty() {
            return cancellable.getReadOnlyProperty();
        }

        @Override
        public boolean requestCancel() {
            Platform.runLater(() -> cancellable.set(false));
            return super.requestCancel();
        }

        @Override
        protected void updateTitle(String title) {
            super.updateTitle(title);
            progressHandle.setDisplayName(title);
        }

        @Override
        protected void updateMessage(String message) {
            super.updateMessage(message);
            progressHandle.progress(message);
        }

        @Override
        protected void updateProgress(double workDone, double max) {
            super.updateProgress(workDone, max);
            if (workDone >= 0) {
                progressHandle.progress((int) workDone);
            }
        }

        @Override
        protected void updateProgress(long workDone, long max) {
            super.updateProgress(workDone, max);
            super.updateProgress(workDone, max);
            if (workDone >= 0) {
                progressHandle.progress((int) workDone);
            }
        }

        DBPopulationWorker(DBPopulationMode mode, Consumer<Worker.State> onStateChange) {
            skCase = autoCase.getSleuthkitCase();
            tagsManager = autoCase.getServices().getTagsManager();
            this.dbPopulationMode = mode;
            this.stateProperty().addListener(stateObservable -> onStateChange.accept(getState()));
        }

        void restartProgressHandle(String title, String message, Double workDone, double total, Boolean cancellable) {
            if (progressHandle != null) {
                progressHandle.finish();
            }
            progressHandle = cancellable
                    ? ProgressHandle.createHandle(title, this::requestCancel)
                    : ProgressHandle.createHandle(title);

            if (workDone < 0) {
                progressHandle.start();
            } else {
                progressHandle.start((int) total);
            }
            updateTitle(title);
            updateMessage(message);
            updateProgress(workDone, total);
        }

        @SuppressWarnings("deprecation") // TODO (EUR-733): Do not use SleuthkitCase.getLastObjectId         
        @Override
        @NbBundle.Messages({"progressWindow.msg.refreshingFileTags=Refreshing file tags",
            "progressWindow.msg.refreshingResultTags=Refreshing result tags",
            "progressWindow.msg.gatheringData=Gathering event data",
            "progressWindow.msg.commitingDb=Committing events database"})
        protected Void call() throws TskCoreException {
            SleuthkitCase.CaseDbTransaction trans = eventManager.beginTransaction();
            if (dbPopulationMode == DBPopulationMode.FULL) {
                //drop old db, and add back MAC and artifact events
                logger.log(Level.INFO, "Beginning population of timeline db."); // NON-NLS
                restartProgressHandle(Bundle.progressWindow_msg_gatheringData(), "", -1D, 1, true);
                //reset database //TODO: can we do more incremental updates? -jm
//                eventManager.reInitializeDB();
                //grab ids of all files
                List<Long> fileIDs = skCase.findAllFileIdsWhere("name != '.' AND name != '..'"
                                                                + " AND type != " + TskData.TSK_DB_FILES_TYPE_ENUM.SLACK.ordinal()); //NON-NLS
                final int numFiles = fileIDs.size();

                insertMACTimeEvents(numFiles, fileIDs);
                insertArtifactDerivedEvents();
            }

            //tags
            if (dbPopulationMode == DBPopulationMode.TAGS_ONLY) {
                trans = eventManager.beginTransaction();
                logger.log(Level.INFO, "dropping old tags"); // NON-NLS
//                eventManager.reInitializeTags();
            }

            logger.log(Level.INFO, "updating content tags"); // NON-NLS
            List<ContentTag> contentTags = tagsManager.getAllContentTags();
            int currentWorkTotal = contentTags.size();
            restartProgressHandle(Bundle.progressWindow_msg_refreshingFileTags(), "", 0D, currentWorkTotal, true);
            insertContentTags(currentWorkTotal, contentTags);

            logger.log(Level.INFO, "updating artifact tags"); // NON-NLS
            List<BlackboardArtifactTag> artifactTags = tagsManager.getAllBlackboardArtifactTags();
            currentWorkTotal = artifactTags.size();
            restartProgressHandle(Bundle.progressWindow_msg_refreshingResultTags(), "", 0D, currentWorkTotal, true);
            insertArtifactTags(currentWorkTotal, artifactTags);

            logger.log(Level.INFO, "committing db"); // NON-NLS
            Platform.runLater(() -> cancellable.set(false));
            restartProgressHandle(Bundle.progressWindow_msg_commitingDb(), "", -1D, 1, false);
            eventManager.commitTransaction(trans);

            eventManager.analyze();
            populateFilterData(skCase);
            invalidateCaches();

            progressHandle.finish();
            if (isCancelRequested()) {
                cancel();
            }
            return null;
        }

        private void insertArtifactTags(int currentWorkTotal, List<BlackboardArtifactTag> artifactTags) throws TskCoreException {
            for (int i = 0; i < currentWorkTotal; i++) {
                if (isCancelRequested()) {
                    break;
                }
                updateProgress(i, currentWorkTotal);
                BlackboardArtifactTag artifactTag = artifactTags.get(i);
                eventManager.setEventsTagged(artifactTag.getContent().getId(), artifactTag.getArtifact().getArtifactID(), true);
            }
        }

        private void insertContentTags(int currentWorkTotal, List<ContentTag> contentTags) throws TskCoreException {
            for (int i = 0; i < currentWorkTotal; i++) {
                if (isCancelRequested()) {
                    break;
                }
                updateProgress(i, currentWorkTotal);
                ContentTag contentTag = contentTags.get(i);
                eventManager.setEventsTagged(contentTag.getContent().getId(), null, true);
            }
        }

        private void insertArtifactDerivedEvents() {
            //insert artifact based events
            for (EventType type : eventManager.getArtifactEventTypes()) {
                if (isCancelRequested()) {
                    break;
                }
                //skip file_system events, they are already handled above.
                if (type instanceof ArtifactEventType) {
                    populateEventType((ArtifactEventType) type);
                }
            }
        }

        @NbBundle.Messages("progressWindow.msg.populateMacEventsFiles=Populating MAC time events for files")
        private void insertMACTimeEvents(final int numFiles, List<Long> fileIDs) {
            restartProgressHandle(Bundle.progressWindow_msg_populateMacEventsFiles(), "", 0D, numFiles, true);
            for (int i = 0; i < numFiles; i++) {
                if (isCancelRequested()) {
                    break;
                }
                long fID = fileIDs.get(i);
                try {
                    AbstractFile f = skCase.getAbstractFileById(fID);

                    if (isNull(f)) {
                        logger.log(Level.WARNING, "Failed to get data for file : {0}", fID); // NON-NLS
                    } else {
                        eventManager.addFileSystemEvents(f);
                        updateProgress(i, numFiles);
                        updateMessage(f.getName());
                    }
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Failed to insert MAC time events for file : " + fID, tskCoreException); // NON-NLS
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
                logger.log(Level.WARNING, "Timeline database population was cancelled by the user. " //NON-NLS
                                          + " Not all events may be present or accurate."); // NON-NLS
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Unexpected exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(), Bundle.msgdlg_problem_text());
            }
        }

        /**
         * populate all the events of one type
         *
         * @param type  the type to populate
         * @param trans the db transaction to use
         */
        @NbBundle.Messages({"# {0} - event type ", "progressWindow.populatingXevents=Populating {0} events"})
        private void populateEventType(final ArtifactEventType type) {
            try {
                //get all the blackboard artifacts corresponding to the given event sub_type
                final ArrayList<BlackboardArtifact> blackboardArtifacts = skCase.getBlackboardArtifacts(type.getArtifactTypeID());
                final int numArtifacts = blackboardArtifacts.size();
                restartProgressHandle(Bundle.progressWindow_populatingXevents(type.getDisplayName()), "", 0D, numArtifacts, true);
                for (int i = 0; i < numArtifacts; i++) {
                    try {
                        //for each artifact, extract the relevant information for the descriptions
                        eventManager.addArtifactEvent(type, blackboardArtifacts.get(i));
                        updateProgress(i, numArtifacts);
                    } catch (TskCoreException ex) {
                        logger.log(Level.SEVERE, "There was a problem inserting event for artifact: " + blackboardArtifacts.get(i).getArtifactID(), ex); // NON-NLS
                    }
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "There was a problem getting events with sub type " + type.getDisplayName() + ".", ex); // NON-NLS
            }
        }
    }
}
