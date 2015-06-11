/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-4 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.grouping;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.swing.SortOrder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.FileUpdateEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryModule;
import org.sleuthkit.autopsy.imagegallery.TagUtils;
import org.sleuthkit.autopsy.imagegallery.datamodel.Category;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Provides an abstraction layer on top of {@link  DrawableDB} ( and to some
 * extent {@link SleuthkitCase} ) to facilitate creation, retrieval, updating,
 * and sorting of {@link DrawableGroup}s.
 */
public class GroupManager implements FileUpdateEvent.FileUpdateListener {

    private static final Logger LOGGER = Logger.getLogger(GroupManager.class.getName());

    private DrawableDB db;

    private final ImageGalleryController controller;
    /**
     * map from {@link GroupKey}s to {@link  DrawableGroup}s. All groups (even
     * not
     * fully analyzed or not visible groups could be in this map
     */
    private final Map<GroupKey<?>, DrawableGroup> groupMap = new HashMap<>();

    /**
     * list of all analyzed groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<DrawableGroup> analyzedGroups = FXCollections.observableArrayList();

    private final ObservableList<DrawableGroup> publicAnalyzedGroupsWrapper = FXCollections.unmodifiableObservableList(analyzedGroups);
    /**
     * list of unseen groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<DrawableGroup> unSeenGroups = FXCollections.observableArrayList();

//    private final SortedList<Grouping> sortedUnSeenGroups = new SortedList<>(unSeenGroups);
    private final ObservableList<DrawableGroup> publicSortedUnseenGroupsWrapper = FXCollections.unmodifiableObservableList(unSeenGroups);

    private ReGroupTask<?> groupByTask;

    /* --- current grouping/sorting attributes --- */
    private volatile GroupSortBy sortBy = GroupSortBy.NONE;

    private volatile DrawableAttribute<?> groupBy = DrawableAttribute.PATH;

    private volatile SortOrder sortOrder = SortOrder.ASCENDING;
    private ReadOnlyDoubleWrapper regroupProgress = new ReadOnlyDoubleWrapper();

    public void setDB(DrawableDB db) {
        this.db = db;
        db.addUpdatedFileListener(this);
        regroup(groupBy, sortBy, sortOrder, Boolean.TRUE);
    }

    public ObservableList<DrawableGroup> getAnalyzedGroups() {
        return publicAnalyzedGroupsWrapper;
    }

    @ThreadConfined(type = ThreadType.JFX)
    public ObservableList<DrawableGroup> getUnSeenGroups() {
        return publicSortedUnseenGroupsWrapper;
    }

    /**
     * construct a group manager hooked up to the given db and controller
     *
     * @param db
     * @param controller
     */
    public GroupManager(ImageGalleryController controller) {
        this.controller = controller;

    }

    /**
     * using the current groupBy set for this manager, find groupkeys for all
     * the groups the given file is a part of
     *
     * @param file
     *
     * @returna a set of {@link GroupKey}s representing the group(s) the given
     * file is a part of
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    synchronized public Set<GroupKey<?>> getGroupKeysForFile(DrawableFile<?> file) {
        Set<GroupKey<?>> resultSet = new HashSet<>();
        for (Comparable<?> val : groupBy.getValue(file)) {
            if (groupBy == DrawableAttribute.TAGS) {
                if (((TagName) val).getDisplayName().startsWith(Category.CATEGORY_PREFIX) == false) {
                    resultSet.add(new GroupKey(groupBy, val));
                }
            } else {
                resultSet.add(new GroupKey(groupBy, val));
            }
        }
        return resultSet;
    }

    /**
     * using the current groupBy set for this manager, find groupkeys for all
     * the groups the given file is a part of
     *
     *
     *
     * @return a a set of {@link GroupKey}s representing the group(s) the given
     *         file is a part of
     */
    synchronized public Set<GroupKey<?>> getGroupKeysForFileID(Long fileID) {
        try {
            DrawableFile<?> file = db.getFileFromID(fileID);
            return getGroupKeysForFile(file);
        } catch (TskCoreException ex) {
            Logger.getLogger(GroupManager.class.getName()).log(Level.SEVERE, "failed to load file with id: " + fileID + " from database", ex);
        }
        return Collections.emptySet();
    }

    /**
     * @param groupKey
     *
     * @return return the DrawableGroup (if it exists) for the given GroupKey,
     *         or
     *         null if no group exists for that key.
     */
    public DrawableGroup getGroupForKey(GroupKey<?> groupKey) {
        synchronized (groupMap) {
            return groupMap.get(groupKey);
        }
    }

    synchronized public void clear() {

        if (groupByTask != null) {
            groupByTask.cancel(true);
        }
        sortBy = GroupSortBy.GROUP_BY_VALUE;
        groupBy = DrawableAttribute.PATH;
        sortOrder = SortOrder.ASCENDING;
        Platform.runLater(() -> {
            unSeenGroups.clear();
            analyzedGroups.clear();
        });
        synchronized (groupMap) {
            groupMap.clear();
        }
        db = null;
    }

    public boolean isRegrouping() {
        if (groupByTask == null) {
            return false;
        }

        switch (groupByTask.getState()) {
            case READY:
            case RUNNING:
            case SCHEDULED:
                return true;
            case CANCELLED:
            case FAILED:

            case SUCCEEDED:
            default:
                return false;
        }
    }

    /**
     * make and return a new group with the given key and files. If a group
     * already existed for that key, it will be replaced.
     *
     * NOTE: this is the only API for making a new group.
     *
     * @param groupKey the groupKey that uniquely identifies this group
     * @param files    a list of fileids that are members of this group
     *
     * @return the new DrawableGroup for the given key
     */
    public DrawableGroup makeGroup(GroupKey<?> groupKey, List<Long> files) {
        List<Long> newFiles = files == null ? new ArrayList<>() : files;
        final boolean groupSeen = db.isGroupSeen(groupKey);
        DrawableGroup g = new DrawableGroup(groupKey, newFiles, groupSeen);

        g.seenProperty().addListener((observable, oldSeen, newSeen) -> {
            markGroupSeen(g, newSeen);
        });
        synchronized (groupMap) {
            groupMap.put(groupKey, g);
        }
        return g;
    }

    /**
     * 'mark' the given group as seen. This removes it from the queue of groups
     * to review, and is persisted in the drawable db.
     *
     * @param group the {@link  DrawableGroup} to mark as seen
     */
    @ThreadConfined(type = ThreadType.JFX)
    public void markGroupSeen(DrawableGroup group, boolean seen) {
        db.markGroupSeen(group.getGroupKey(), seen);
        group.setSeen(seen);
        if (seen) {
            unSeenGroups.removeAll(group);
        } else if (unSeenGroups.contains(group) == false) {
            unSeenGroups.add(group);
            FXCollections.sort(unSeenGroups, sortBy.getGrpComparator(sortOrder));
        }
    }

    /**
     * remove the given file from the group with the given key. If the group
     * doesn't exist or doesn't already contain this file, this method is a
     * no-op
     *
     * @param groupKey the value of groupKey
     * @param fileID   the value of file
     */
    public synchronized void removeFromGroup(GroupKey<?> groupKey, final Long fileID) {
        //get grouping this file would be in
        final DrawableGroup group = getGroupForKey(groupKey);
        if (group != null) {
            group.removeFile(fileID);

            // If we're grouping by category, we don't want to remove empty groups.
            if (groupKey.getAttribute() != DrawableAttribute.CATEGORY) {
                if (group.fileIds().isEmpty()) {
                    synchronized (groupMap) {
                        groupMap.remove(groupKey, group);
                    }
                    Platform.runLater(() -> {
                        analyzedGroups.remove(group);
                        unSeenGroups.remove(group);
                    });
                }
            } else {
            }
        }
    }

    public synchronized void populateAnalyzedGroup(final GroupKey<?> groupKey, List<Long> filesInGroup) {
        populateAnalyzedGroup(groupKey, filesInGroup, null);
    }

    /**
     * create a group with the given GroupKey and file ids and add it to the
     * analyzed group list.
     *
     * @param groupKey
     * @param filesInGroup
     */
    private synchronized <A extends Comparable<A>> void populateAnalyzedGroup(final GroupKey<A> groupKey, List<Long> filesInGroup, ReGroupTask<A> task) {

        /* if this is not part of a regroup task or it is but the task is not
         * cancelled...
         *
         * this allows us to stop if a regroup task has been cancelled (e.g. the
         * user picked a different group by attribute, while the current task
         * was still running) */
        if (task == null || (task.isCancelled() == false)) {
            DrawableGroup g = makeGroup(groupKey, filesInGroup);
            populateAnalyzedGroup(g, task);
        }
    }

    private synchronized void populateAnalyzedGroup(final DrawableGroup g, ReGroupTask<?> task) {

        if (task == null || (task.isCancelled() == false)) {
            final boolean groupSeen = db.isGroupSeen(g.getGroupKey());

            Platform.runLater(() -> {
                if (analyzedGroups.contains(g) == false) {
                    analyzedGroups.add(g);
                }
                markGroupSeen(g, groupSeen);

            });
        }
    }

    /**
     * check if the group for the given groupkey is analyzed
     *
     * @param groupKey
     *
     * @return null if this group is not analyzed or a list of file ids in
     *         this
     *         group if they are all analyzed
     */
    public List<Long> checkAnalyzed(final GroupKey<?> groupKey) {
        try {
            /* for attributes other than path we can't be sure a group is fully
             * analyzed because we don't know all the files that will be a part
             * of that group */
            if ((groupKey.getAttribute() != DrawableAttribute.PATH) || db.isGroupAnalyzed(groupKey)) {
                return getFileIDsInGroup(groupKey);
            } else {
                return null;
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "failed to get files for group: " + groupKey.getAttribute().attrName.toString() + " = " + groupKey.getValue(), ex);
            return null;
        }
    }

    /**
     * the implementation of this should be moved to DrawableDB
     *
     * @param hashDbName
     *
     * @return
     *
     * @deprecated
     */
    @Deprecated
    private List<Long> getFileIDsWithHashSetName(String hashDbName) {
        List<Long> files = new ArrayList<>();
        try {

            final SleuthkitCase sleuthkitCase = ImageGalleryController.getDefault().getSleuthKitCase();
            String query = "SELECT obj_id FROM blackboard_attributes,blackboard_artifacts WHERE "
                    + "attribute_type_id=" + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID()
                    + " AND blackboard_attributes.artifact_id=blackboard_artifacts.artifact_id"
                    + " AND blackboard_attributes.value_text='" + hashDbName + "'"
                    + " AND blackboard_artifacts.artifact_type_id=" + BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID();

            ResultSet rs = null;
            try {
                rs = sleuthkitCase.runQuery(query);
                while (rs.next()) {
                    long id = rs.getLong("obj_id");
                    try {
                        if (ImageGalleryModule.isSupportedAndNotKnown(Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(id))) {
                            files.add(id);
                        }
                    } catch (TskCoreException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                }
            } catch (SQLException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                if (rs != null) {
                    try {
                        Case.getCurrentCase().getSleuthkitCase().closeRunQuery(rs);
                    } catch (SQLException ex) {
                        LOGGER.log(Level.WARNING, "Error closing result set after getting hashset hits", ex);
                    }
                }
            }

            return files;
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.SEVERE, "Can't get the current case; there is no case open!", ex);
            return files;
        }
    }

    /**
     * find the distinct values for the given column (DrawableAttribute)
     *
     * These values represent the groups of files.
     *
     * @param groupBy
     *
     * @return
     */
    @SuppressWarnings({"unchecked"})
    public <A extends Comparable<A>> List<A> findValuesForAttribute(DrawableAttribute<A> groupBy) {
        List<A> values;
        try {
            switch (groupBy.attrName) {
                //these cases get special treatment
                case CATEGORY:
                    values = (List<A>) Arrays.asList(Category.values());
                    break;
                case TAGS:
                    values = (List<A>) Case.getCurrentCase().getServices().getTagsManager().getTagNamesInUse().stream()
                            .filter(t -> t.getDisplayName().startsWith(Category.CATEGORY_PREFIX) == false)
                            .collect(Collectors.toList());
                    break;
                case ANALYZED:
                    values = (List<A>) Arrays.asList(false, true);
                    break;
                case HASHSET:
                    TreeSet<A> names = new TreeSet<>((Collection<? extends A>) db.getHashSetNames());
                    values = new ArrayList<>(names);
                    break;
                default:
                    //otherwise do straight db query 
                    return db.findValuesForAttribute(groupBy, sortBy, sortOrder);
            }

            return values;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting list of type {0}", groupBy.getDisplayName());
            return Collections.emptyList();
        }

    }

    public List<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {
        switch (groupKey.getAttribute().attrName) {
            //these cases get special treatment
            case CATEGORY:
                return getFileIDsWithCategory((Category) groupKey.getValue());
            case TAGS:
                return getFileIDsWithTag((TagName) groupKey.getValue());
//            case HASHSET: //comment out this case to use db functionality for hashsets
//                return getFileIDsWithHashSetName((String) groupKey.getValue());
            default:
                //straight db query
                return db.getFileIDsInGroup(groupKey);
        }
    }

    // @@@ This was kind of slow in the profiler.  Maybe we should cache it.
    // Unless the list of file IDs is necessary, use countFilesWithCategory() to get the counts.
    public List<Long> getFileIDsWithCategory(Category category) throws TskCoreException {

        try {
            if (category == Category.ZERO) {

                List<Long> files = new ArrayList<>();
                TagName[] tns = {Category.FOUR.getTagName(), Category.THREE.getTagName(), Category.TWO.getTagName(), Category.ONE.getTagName(), Category.FIVE.getTagName()};
                for (TagName tn : tns) {
                    List<ContentTag> contentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByTagName(tn);
                    for (ContentTag ct : contentTags) {
                        if (ct.getContent() instanceof AbstractFile && db.isInDB(ct.getContent().getId())) {
                            files.add(ct.getContent().getId());
                        }
                    }
                }

                return db.findAllFileIdsWhere("obj_id NOT IN (" + StringUtils.join(files, ',') + ")");
            } else {

                List<Long> files = new ArrayList<>();
                List<ContentTag> contentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByTagName(category.getTagName());
                for (ContentTag ct : contentTags) {
                    if (ct.getContent() instanceof AbstractFile && db.isInDB(ct.getContent().getId())) {
                        files.add(ct.getContent().getId());
                    }
                }

                return files;
            }
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting files in Category:" + category.getDisplayName(), ex);
            throw ex;
        }
    }

    public List<Long> getFileIDsWithTag(TagName tagName) throws TskCoreException {
        try {
            List<Long> files = new ArrayList<>();
            List<ContentTag> contentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByTagName(tagName);
            for (ContentTag ct : contentTags) {
                if (ct.getContent() instanceof AbstractFile && db.isInDB(ct.getContent().getId())) {

                    files.add(ct.getContent().getId());
                }
            }

            return files;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting files with Tag:" + tagName.getDisplayName(), ex);
            throw ex;
        }
    }

    public GroupSortBy getSortBy() {
        return sortBy;
    }

    public void setSortBy(GroupSortBy sortBy) {
        this.sortBy = sortBy;
    }

    public DrawableAttribute<?> getGroupBy() {
        return groupBy;
    }

    public void setGroupBy(DrawableAttribute<?> groupBy) {
        this.groupBy = groupBy;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * regroup all files in the database using given {@link  DrawableAttribute}
     * see {@link ReGroupTask} for more details.
     *
     * @param groupBy
     * @param sortBy
     * @param sortOrder
     * @param force     true to force a full db query regroup
     */
    public <A extends Comparable<A>> void regroup(final DrawableAttribute<A> groupBy, final GroupSortBy sortBy, final SortOrder sortOrder, Boolean force) {

        if (!Case.isCaseOpen()) {
            return;
        }

        //only re-query the db if the group by attribute changed or it is forced
        if (groupBy != getGroupBy() || force == true) {
            setGroupBy(groupBy);
            setSortBy(sortBy);
            setSortOrder(sortOrder);
            if (groupByTask != null) {
                groupByTask.cancel(true);
            }
            Platform.runLater(() -> {
                FXCollections.sort(unSeenGroups, sortBy.getGrpComparator(sortOrder));
            });

            groupByTask = new ReGroupTask<A>(groupBy, sortBy, sortOrder);
            Platform.runLater(() -> {
                regroupProgress.bind(groupByTask.progressProperty());
            });
            regroupExecutor.submit(groupByTask);
        } else {
            // just resort the list of groups
            setSortBy(sortBy);
            setSortOrder(sortOrder);
            Platform.runLater(() -> {
                FXCollections.sort(unSeenGroups, sortBy.getGrpComparator(sortOrder));
                FXCollections.sort(analyzedGroups, sortBy.getGrpComparator(sortOrder));
            });
        }
    }

    /**
     * an executor to submit async ui related background tasks to.
     */
    final ExecutorService regroupExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("ui task -%d").build());

    public ReadOnlyDoubleProperty regroupProgress() {
        return regroupProgress.getReadOnlyProperty();
    }

    /**
     * handle {@link FileUpdateEvent} sent from Db when files are
     * inserted/updated
     *
     * TODO: why isn't this just two methods!
     *
     * @param evt
     */
    @Override
    synchronized public void handleFileUpdate(FileUpdateEvent evt) {
        final Collection<Long> fileIDs = evt.getFileIDs();
        switch (evt.getUpdateType()) {
            case REMOVE:
                for (final long fileId : fileIDs) {
                    //get grouping(s) this file would be in
                    Set<GroupKey<?>> groupsForFile = getGroupKeysForFileID(fileId);

                    for (GroupKey<?> gk : groupsForFile) {
                        removeFromGroup(gk, fileId);

                        DrawableGroup g = getGroupForKey(gk);

                        if (g == null) {
                            // It may be that this was the last unanalyzed file in the group, so test
                            // whether the group is now fully analyzed.
                            //TODO: use method in groupmanager ?
                            List<Long> checkAnalyzed = checkAnalyzed(gk);
                            if (checkAnalyzed != null) { // => the group is analyzed, so add it to the ui
                                populateAnalyzedGroup(gk, checkAnalyzed);
                            }
                        }
                    }
                }

                break;
            case UPDATE:

                /**
                 * TODO: is there a way to optimize this to avoid quering to db
                 * so much. the problem is that as a new files are analyzed they
                 * might be in new groups( if we are grouping by say make or
                 * model)
                 *
                 * TODO: Should this be a InnerTask so it can be done by the
                 * WorkerThread? Is it already done by worker thread because
                 * handlefileUpdate is invoked through call on db in UpdateTask
                 * innertask? -jm
                 */
                for (final long fileId : fileIDs) {

                    controller.getHashSetManager().invalidateHashSetsForFile(fileId);

                    //get grouping(s) this file would be in
                    Set<GroupKey<?>> groupsForFile = getGroupKeysForFileID(fileId);

                    for (GroupKey<?> gk : groupsForFile) {
                        DrawableGroup g = getGroupForKey(gk);

                        if (g != null) {
                            //if there is aleady a group that was previously deemed fully analyzed, then add this newly analyzed file to it.
                            g.addFile(fileId);
                        } else {
                            //if there wasn't already a group check if there should be one now
                            //TODO: use method in groupmanager ?
                            List<Long> checkAnalyzed = checkAnalyzed(gk);
                            if (checkAnalyzed != null) { // => the group is analyzed, so add it to the ui
                                populateAnalyzedGroup(gk, checkAnalyzed);
                            }
                        }
                    }
                }

                //we fire this event for all files so that the category counts get updated during initial db population
                controller.getCategoryManager().fireChange(fileIDs);

                if (evt.getChangedAttribute() == DrawableAttribute.TAGS) {
                    TagUtils.fireChange(fileIDs);
                }
                break;
        }
    }

    /**
     * Task to query database for files in sorted groups and build
     * {@link Groupings} for them
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private class ReGroupTask<A extends Comparable<A>> extends LoggedTask<Void> {

        private ProgressHandle groupProgress;

        private final DrawableAttribute<A> groupBy;

        private final GroupSortBy sortBy;

        private final SortOrder sortOrder;

        public ReGroupTask(DrawableAttribute<A> groupBy, GroupSortBy sortBy, SortOrder sortOrder) {
            super("regrouping files by " + groupBy.attrName.toString() + " sorted by " + sortBy.name() + " in " + sortOrder.toString() + " order", true);

            this.groupBy = groupBy;
            this.sortBy = sortBy;
            this.sortOrder = sortOrder;
        }

        @Override
        public boolean isCancelled() {
            return super.isCancelled() || groupBy != getGroupBy() || sortBy != getSortBy() || sortOrder != getSortOrder();
        }

        @Override
        protected Void call() throws Exception {

            if (isCancelled()) {
                return null;
            }

            groupProgress = ProgressHandleFactory.createHandle("regrouping files by " + groupBy.attrName.toString() + " sorted by " + sortBy.name() + " in " + sortOrder.toString() + " order", this);
            Platform.runLater(() -> {
                analyzedGroups.clear();
                synchronized (unSeenGroups) {
                    unSeenGroups.clear();
                }
            });
            synchronized (groupMap) {
                groupMap.clear();
            }

            // Get the list of group keys
            final List<A> vals = findValuesForAttribute(groupBy);

            // Make a list of each group 
            final List<DrawableGroup> groups = new ArrayList<>();

            groupProgress.start(vals.size());

            int p = 0;
            // For each key value, partially create the group and add it to the list.
            for (final A val : vals) {
                if (isCancelled()) {
                    return null;//abort
                }
                p++;
                updateMessage("regrouping files by " + groupBy.attrName.toString() + " : " + val);
                updateProgress(p, vals.size());
                groupProgress.progress("regrouping files by " + groupBy.attrName.toString() + " : " + val, p);
                //check if this group is analyzed
                final GroupKey<A> groupKey = new GroupKey<>(groupBy, val);

                List<Long> checkAnalyzed = checkAnalyzed(groupKey);
                if (checkAnalyzed != null) { // != null => the group is analyzed, so add it to the ui

                    // makeGroup will create the group and add it to the map groupMap, but does not
                    // update anything else
                    DrawableGroup g = makeGroup(groupKey, checkAnalyzed);
                    groups.add(g);
                }
            }

            // Sort the group list
            Collections.sort(groups, sortBy.getGrpComparator(sortOrder));

            // Officially add all groups in order
            for (DrawableGroup g : groups) {
                populateAnalyzedGroup(g, ReGroupTask.this);
            }

            updateProgress(1, 1);
            return null;
        }

        @Override
        protected void done() {
            super.done();
            if (groupProgress != null) {
                groupProgress.finish();
                groupProgress = null;
            }
        }
    }
}
