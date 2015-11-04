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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Interval;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.ProgressWindow;
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

    @GuardedBy("this")
    private SwingWorker<Void, ProgressWindow.ProgressUpdate> dbPopulationWorker;

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

    public void recordLastArtifactID(long lastArtfID) {
        eventDB.recordLastArtifactID(lastArtfID);
    }

    public void recordWasIngestRunning(Boolean wasIngestRunning) {
        eventDB.recordWasIngestRunning(wasIngestRunning);
    }

    public void recordLastObjID(Long lastObjID) {
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

    synchronized public void rebuildRepository(Runnable r) {
        if (dbPopulationWorker != null) {
            dbPopulationWorker.cancel(true);

        }
        dbPopulationWorker = new DBPopulationWorker(r);
        dbPopulationWorker.execute();
    }

    synchronized public void rebuildTags(Runnable r) {
        if (dbPopulationWorker != null) {
            dbPopulationWorker.cancel(true);

        }
        dbPopulationWorker = new RebuildTagsWorker(r);
        dbPopulationWorker.execute();
    }

    private class RebuildTagsWorker extends SwingWorker<Void, ProgressWindow.ProgressUpdate> {

        private final ProgressWindow progressDialog;

        //TODO: can we avoid this with a state listener?  does it amount to the same thing?
        //post population operation to execute
        private final Runnable postPopulationOperation;
        private final SleuthkitCase skCase;
        private final TagsManager tagsManager;
        private final ProgressHandle progressHandle;

        public RebuildTagsWorker(Runnable postPopulationOperation) {
            progressDialog = new ProgressWindow(null, false, this);
            progressHandle = ProgressHandleFactory.createHandle("refreshing tags", () -> cancel(true));
            progressDialog.setVisible(true);

            skCase = autoCase.getSleuthkitCase();
            tagsManager = autoCase.getServices().getTagsManager();

            this.postPopulationOperation = postPopulationOperation;
        }

        @Override
        protected Void doInBackground() throws Exception {
            progressHandle.start();
            EventDB.EventTransaction trans = eventDB.beginTransaction();
            LOGGER.log(Level.INFO, "dropping old tags"); // NON-NLS
            eventDB.reInitializeTags();

            LOGGER.log(Level.INFO, "updating content tags"); // NON-NLS
            List<ContentTag> contentTags = tagsManager.getAllContentTags();
            int size = contentTags.size();
            for (int i = 0; i < size; i++) {
                if (isCancelled()) {
                    break;
                }
                publish(new ProgressWindow.ProgressUpdate(i, size, "refreshing file tags", ""));
                ContentTag contentTag = contentTags.get(i);
                eventDB.addTag(contentTag.getContent().getId(), null, contentTag);
            }
            LOGGER.log(Level.INFO, "updating artifact tags"); // NON-NLS
            List<BlackboardArtifactTag> artifactTags = tagsManager.getAllBlackboardArtifactTags();
            size = artifactTags.size();
            for (int i = 0; i < size; i++) {
                if (isCancelled()) {
                    break;
                }
                publish(new ProgressWindow.ProgressUpdate(i, size, "refreshing result tags", ""));
                BlackboardArtifactTag artifactTag = artifactTags.get(i);
                eventDB.addTag(artifactTag.getContent().getId(), artifactTag.getArtifact().getArtifactID(), artifactTag);
            }

            LOGGER.log(Level.INFO, "committing tags"); // NON-NLS
            publish(new ProgressWindow.ProgressUpdate(0, -1, "committing tag changes", ""));
            eventDB.analyze();
            if (isCancelled()) {
                eventDB.rollBackTransaction(trans);
            } else {
                eventDB.commitTransaction(trans);
            }

            populateFilterData(skCase);
            invalidateCaches();

            return null;
        }

        /**
         * handle intermediate 'results': just update progress dialog
         *
         * @param chunks
         */
        @Override
        protected void process(List<ProgressWindow.ProgressUpdate> chunks) {
            super.process(chunks);
            ProgressWindow.ProgressUpdate chunk = chunks.get(chunks.size() - 1);
            progressDialog.update(chunk);

            progressHandle.switchToDeterminate(chunk.getTotal());
            progressHandle.setDisplayName(chunk.getHeaderMessage());
            progressHandle.progress(chunk.getDetailMessage());
            progressHandle.progress(chunk.getProgress());
        }

        @Override
        @NbBundle.Messages("msgdlg.tagsproblem.text=There was a problem refreshing the tagged events."
                + "  Some events may have inacurate tags. See the log for details.")
        protected void done() {
            super.done();

            progressHandle.finish();
            progressDialog.close();
            try {
                get();
            } catch (CancellationException ex) {
                LOGGER.log(Level.WARNING, "Database population was cancelled by the user.  Not all events may be present or accurate. See the log for details.", ex); // NON-NLS
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.WARNING, "Exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_tagsproblem_text());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Unexpected exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_tagsproblem_text());
            }
            postPopulationOperation.run();  //execute post db population operation
        }
    }

    private class DBPopulationWorker extends SwingWorker<Void, ProgressWindow.ProgressUpdate> {

        private final ProgressWindow progressDialog;

        //TODO: can we avoid this with a state listener?  does it amount to the same thing?
        //post population operation to execute
        private final Runnable postPopulationOperation;
        private final SleuthkitCase skCase;
        private final TagsManager tagsManager;
        private final ProgressHandle progressHandle;

        public DBPopulationWorker(Runnable postPopulationOperation) {
            progressDialog = new ProgressWindow(null, false, this);
            progressDialog.setVisible(true);
            progressHandle = ProgressHandleFactory.createHandle("(re)initializing events database", () -> this.cancel(true));
            skCase = autoCase.getSleuthkitCase();
            tagsManager = autoCase.getServices().getTagsManager();

            this.postPopulationOperation = postPopulationOperation;
        }

        @Override
        @NbBundle.Messages({"progressWindow.msg.populateMacEventsFiles=Populating MAC time events for files:",
            "progressWindow.msg.reinit_db=(re)initializing events database",
            "progressWindow.msg.commitingDb=committing events db"})
        protected Void doInBackground() throws Exception {
            progressHandle.start();
            publish(new ProgressWindow.ProgressUpdate(0, -1, Bundle.progressWindow_msg_reinit_db(), ""));
            //reset database 
            //TODO: can we do more incremental updates? -jm
            eventDB.reInitializeDB();

            //grab ids of all files
            List<Long> files = skCase.findAllFileIdsWhere("name != '.' AND name != '..'");

            final int numFiles = files.size();
            publish(new ProgressWindow.ProgressUpdate(0, numFiles, Bundle.progressWindow_msg_populateMacEventsFiles(), ""));

            //insert file events into db
            int i = 1;
            EventDB.EventTransaction trans = eventDB.beginTransaction();
            for (final Long fID : files) {
                if (isCancelled()) {
                    break;
                } else {
                    try {
                        AbstractFile f = skCase.getAbstractFileById(fID);

                        if (f == null) {
                            LOGGER.log(Level.WARNING, "Failed to get data for file : {0}", fID); // NON-NLS
                        } else {
                            //TODO: This is broken for logical files? fix -jm
                            //TODO: logical files don't necessarily have valid timestamps, so ... -jm
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

                            //insert it into the db if time is > 0  => time is legitimate (drops logical files)
                            if (f.getAtime() > 0) {
                                eventDB.insertEvent(f.getAtime(), FileSystemTypes.FILE_ACCESSED, datasourceID, fID, null, uniquePath, medDesc, shortDesc, known, hashSets, tags, trans);
                            }
                            if (f.getMtime() > 0) {
                                eventDB.insertEvent(f.getMtime(), FileSystemTypes.FILE_MODIFIED, datasourceID, fID, null, uniquePath, medDesc, shortDesc, known, hashSets, tags, trans);
                            }
                            if (f.getCtime() > 0) {
                                eventDB.insertEvent(f.getCtime(), FileSystemTypes.FILE_CHANGED, datasourceID, fID, null, uniquePath, medDesc, shortDesc, known, hashSets, tags, trans);
                            }
                            if (f.getCrtime() > 0) {
                                eventDB.insertEvent(f.getCrtime(), FileSystemTypes.FILE_CREATED, datasourceID, fID, null, uniquePath, medDesc, shortDesc, known, hashSets, tags, trans);
                            }

                            publish(new ProgressWindow.ProgressUpdate(i, numFiles,
                                    Bundle.progressWindow_msg_populateMacEventsFiles(), f.getName()));
                        }
                    } catch (TskCoreException tskCoreException) {
                        LOGGER.log(Level.WARNING, "failed to insert mac event for file : " + fID, tskCoreException); // NON-NLS
                    }
                }
                i++;
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

            publish(new ProgressWindow.ProgressUpdate(0, -1, Bundle.progressWindow_msg_commitingDb(), ""));

            eventDB.analyze();

            if (isCancelled()) {
                eventDB.rollBackTransaction(trans);
            } else {
                eventDB.commitTransaction(trans);
            }

            populateFilterData(skCase);
            invalidateCaches();

            return null;
        }

        /**
         * handle intermediate 'results': just update progress dialog
         *
         * @param chunks
         */
        @Override
        protected void process(List<ProgressWindow.ProgressUpdate> chunks) {
            super.process(chunks);
            ProgressWindow.ProgressUpdate chunk = chunks.get(chunks.size() - 1);
            progressDialog.update(chunk);

            if (chunk.getTotal() >= 0) {
                progressHandle.switchToDeterminate(chunk.getTotal());
            } else {
                progressHandle.switchToIndeterminate();
            }
            progressHandle.setDisplayName(chunk.getHeaderMessage());
            progressHandle.progress(chunk.getDetailMessage());
            progressHandle.progress(chunk.getProgress());
        }

        @Override
        @NbBundle.Messages("msgdlg.problem.text=There was a problem populating the timeline."
                + "  Not all events may be present or accurate. See the log for details.")
        protected void done() {
            super.done();
            progressHandle.finish();
            progressDialog.close();
            try {
                get();
            } catch (CancellationException ex) {
                LOGGER.log(Level.WARNING, "Database population was cancelled by the user.  Not all events may be present or accurate. See the log for details."); // NON-NLS
            } catch (InterruptedException | ExecutionException ex) {
                LOGGER.log(Level.WARNING, "Exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_problem_text());
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Unexpected exception while populating database.", ex); // NON-NLS
                JOptionPane.showMessageDialog(null, Bundle.msgdlg_problem_text());
            }
            postPopulationOperation.run();  //execute post db population operation
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

                for (int i = 0; i < numArtifacts; i++) {
                    publish(new ProgressWindow.ProgressUpdate(i, numArtifacts,
                            Bundle.progressWindow_populatingXevents(type.getDisplayName()), ""));

                    //for each artifact, extract the relevant information for the descriptions
                    BlackboardArtifact bbart = blackboardArtifacts.get(i);
                    ArtifactEventType.AttributeEventDescription eventDescription = ArtifactEventType.buildEventDescription(type, bbart);

                    //insert it into the db if time is > 0  => time is legitimate
                    if (eventDescription != null && eventDescription.getTime() > 0L) {
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
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "There was a problem getting events with sub type = " + type.toString() + ".", ex); // NON-NLS
            }
        }
    }

    public boolean areFiltersEquivalent(RootFilter f1, RootFilter f2) {
        return SQLHelper.getSQLWhere(f1).equals(SQLHelper.getSQLWhere(f2));

    }
}
