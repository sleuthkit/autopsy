/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.datamodel.grouping;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.isNull;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SortOrder;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Examiner;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.DbType;
import org.sleuthkit.datamodel.TskDataException;

/**
 * Provides an abstraction layer on top of DrawableDB ( and to some extent
 * SleuthkitCase ) to facilitate creation, retrieval, updating, and sorting of
 * DrawableGroups.
 */
public class GroupManager {

    private static final Logger logger = Logger.getLogger(GroupManager.class.getName());

    /**
     * An executor to submit async UI related background tasks to.
     */
    private final ListeningExecutorService exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor(
            new BasicThreadFactory.Builder().namingPattern("GroupManager BG Thread-%d").build())); //NON-NLS

    private final ImageGalleryController controller;

    /**
     * Keeps track of the current path group - a change in path indicates the
     * current path group is analyzed
     */
    @GuardedBy("this") //NOPMD
    private GroupKey<?> currentPathGroup = null;

    /**
     * list of all analyzed groups - i.e. groups that are ready to be shown to
     * user. These are groups under the selected groupBy attribute.
     */
    @GuardedBy("this") //NOPMD
    private final ObservableList<DrawableGroup> analyzedGroups = FXCollections.observableArrayList();
    private final ObservableList<DrawableGroup> unmodifiableAnalyzedGroups = FXCollections.unmodifiableObservableList(analyzedGroups);

    /**
     * list of unseen groups These are groups under the selected groupBy
     * attribute.
     */
    @GuardedBy("this") //NOPMD
    private final ObservableList<DrawableGroup> unSeenGroups = FXCollections.observableArrayList();
    private final ObservableList<DrawableGroup> unmodifiableUnSeenGroups = FXCollections.unmodifiableObservableList(unSeenGroups);
    /**
     * map from GroupKey} to DrawableGroupSs. All groups (even not fully
     * analyzed or not visible groups could be in this map
     */
    @GuardedBy("this") //NOPMD
    private final Map<GroupKey<?>, DrawableGroup> groupMap = new HashMap<>();

    /*
     * --- current grouping/sorting attributes --- all guarded by GroupManager
     * intrisic lock, aka 'this'
     */
    @GuardedBy("this") //NOPMD
    private final ReadOnlyObjectWrapper< GroupSortBy> sortByProp = new ReadOnlyObjectWrapper<>(GroupSortBy.PRIORITY);
    private final ReadOnlyObjectWrapper< DrawableAttribute<?>> groupByProp = new ReadOnlyObjectWrapper<>(DrawableAttribute.PATH);
    private final ReadOnlyObjectWrapper<SortOrder> sortOrderProp = new ReadOnlyObjectWrapper<>(SortOrder.ASCENDING);
    private final ReadOnlyObjectWrapper<DataSource> dataSourceProp = new ReadOnlyObjectWrapper<>(null);//null indicates all datasources
    /**
     * Is the GroupManager operating in 'collaborative mode': In collaborative
     * mode, groups seen by ANY examiner are considered seen. When NOT in
     * collaborative mode, groups seen by other examiners, are NOT considered
     * seen.
     */
    @GuardedBy("this") //NOPMD
    private final ReadOnlyBooleanWrapper collaborativeModeProp = new ReadOnlyBooleanWrapper(false);

    private final GroupingService regrouper;

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableList<DrawableGroup> getAnalyzedGroupsForCurrentGroupBy() {
        return unmodifiableAnalyzedGroups;
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableList<DrawableGroup> getUnSeenGroupsForCurrentGroupBy() {
        return unmodifiableUnSeenGroups;
    }

    /**
     * construct a group manager hooked up to the given db and controller
     *
     * @param controller
     */
    public GroupManager(ImageGalleryController controller) {
        this.controller = controller;
        this.regrouper = new GroupingService();
        regrouper.setExecutor(exec);
    }

    /**
     * Find and return groupkeys for all the groups the given file is a part of
     *
     * @param file file for which to get the groups
     *
     * @return A a set of GroupKeys representing the group(s) the given file is
     *         a part of.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    synchronized public Set<GroupKey<?>> getAllGroupKeysForFile(DrawableFile file) throws TskCoreException, TskDataException {
        Set<GroupKey<?>> resultSet = new HashSet<>();

        for (DrawableAttribute<?> attr : DrawableAttribute.getGroupableAttrs()) {
            for (Comparable<?> val : attr.getValue(file)) {

                if (attr == DrawableAttribute.PATH) {
                    resultSet.add(new GroupKey(attr, val, file.getDataSource()));
                } else if (attr == DrawableAttribute.TAGS) {
                    //don't show groups for the categories when grouped by tags.
                    if (controller.getCategoryManager().isNotCategoryTagName((TagName) val)) {
                        resultSet.add(new GroupKey(attr, val, null));
                    }
                } else {
                    resultSet.add(new GroupKey(attr, val, null));
                }
            }
        }
        return resultSet;
    }

    /**
     *
     * Returns GroupKeys for all the Groups the given file is a part of.
     *
     * @param fileID The Id of the file to get group keys for.
     *
     * @return A set of GroupKeys representing the group(s) the given file is a
     *         part of
     */
    synchronized public Set<GroupKey<?>> getAllGroupKeysForFile(Long fileID) {
        try {
            DrawableFile file = getDrawableDB().getFileFromID(fileID);
            return getAllGroupKeysForFile(file);

        } catch (TskCoreException | TskDataException ex) {
            logger.log(Level.SEVERE, "Failed to get group keys for file with ID " + fileID, ex); //NON-NLS
        }
        return Collections.emptySet();
    }

    /**
     * @param groupKey
     *
     * @return return the DrawableGroup (if it exists) for the given GroupKey,
     *         or null if no group exists for that key.
     */
    @Nullable
    synchronized public DrawableGroup getGroupForKey(@Nonnull GroupKey<?> groupKey) {
        return groupMap.get(groupKey);
    }

    synchronized public void reset() {
        Platform.runLater(regrouper::cancel);

        setSortBy(GroupSortBy.GROUP_BY_VALUE);
        setGroupBy(DrawableAttribute.PATH);
        setSortOrder(SortOrder.ASCENDING);
        setDataSource(null);

        unSeenGroups.forEach(controller.getCategoryManager()::unregisterListener);
        unSeenGroups.clear();
        analyzedGroups.forEach(controller.getCategoryManager()::unregisterListener);
        analyzedGroups.clear();

        groupMap.values().forEach(controller.getCategoryManager()::unregisterListener);
        groupMap.clear();
    }

    public boolean isRegrouping() {
        return Arrays.asList(Worker.State.READY, Worker.State.RUNNING, Worker.State.SCHEDULED)
                .contains(regrouper.getState());
    }

    public ReadOnlyObjectProperty<Worker.State> reGroupingState() {
        return regrouper.stateProperty();
    }

    /**
     * Marks the given group as 'seen' by the current examiner, in drawable db.
     *
     * @param group The DrawableGroup to mark as seen.
     *
     * @return A ListenableFuture that encapsulates saving the seen state to the
     *         DB.
     *
     *
     */
    public ListenableFuture<?> markGroupSeen(DrawableGroup group) {
        return exec.submit(() -> {
            try {
                Examiner examiner = controller.getCaseDatabase().getCurrentExaminer();
                getDrawableDB().markGroupSeen(group.getGroupKey(), examiner.getId());
                // only update and reshuffle if its new results
                if (group.isSeen() != true) {
                    group.setSeen(true);
                    updateUnSeenGroups(group);
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error setting seen status for group: %s", group.getGroupKey().getValue().toString()), ex); //NON-NLS
            }
        });
    }

    /**
     * Marks the given group as unseen in the drawable db.
     *
     * @param group The DrawableGroup.
     *
     * @return A ListenableFuture that encapsulates saving the seen state to the
     *         DB.
     */
    public ListenableFuture<?> markGroupUnseen(DrawableGroup group) {
        return exec.submit(() -> {
            try {

                getDrawableDB().markGroupUnseen(group.getGroupKey());
                // only update and reshuffle if its new results        
                if (group.isSeen() == true) {
                    group.setSeen(false);
                }
                // The group may already be in 'unseen' state, e.g. when new files are added, 
                // but not be on the unseenGroupsList yet.
                updateUnSeenGroups(group);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, String.format("Error setting group: %s to unseen.", group.getGroupKey().getValue().toString()), ex); //NON-NLS
            }
        });
    }

    /**
     * Update unseenGroups list accordingly based on the current status of
     * 'group'. Removes it if it is seen or adds it if it is unseen.
     *
     * @param group
     */
    synchronized private void updateUnSeenGroups(DrawableGroup group) {
        if (group.isSeen()) {
            unSeenGroups.removeAll(group);
        } else if (unSeenGroups.contains(group) == false
                && getGroupBy() == group.getGroupKey().getAttribute()) {
            unSeenGroups.add(group);
        }
        sortUnseenGroups();
    }

    /**
     * remove the given file from the group with the given key. If the group
     * doesn't exist or doesn't already contain this file, this method is a
     * no-op
     *
     * @param groupKey the value of groupKey
     * @param fileID   the value of file
     *
     * @return The DrawableGroup the file was removed from.
     *
     */
    public synchronized DrawableGroup removeFromGroup(GroupKey<?> groupKey, final Long fileID) {
        //get grouping this file would be in
        final DrawableGroup group = getGroupForKey(groupKey);
        if (group != null) {
            synchronized (group) {
                group.removeFile(fileID);

                // If we're grouping by category, we don't want to remove empty groups.
                if (group.getFileIDs().isEmpty()) {
                    markGroupSeen(group);
                    if (groupKey.getAttribute() != DrawableAttribute.CATEGORY) {
                        if (analyzedGroups.contains(group)) {
                            analyzedGroups.remove(group);
                            sortAnalyzedGroups();
                        }

                        if (unSeenGroups.contains(group)) {
                            unSeenGroups.remove(group);
                            sortUnseenGroups();
                        }
                    }
                }
                return group;
            }
        } else { //group == null
            // It may be that this was the last unanalyzed file in the group, so test
            // whether the group is now fully analyzed.
            return populateIfAnalyzed(groupKey, null);
        }
    }

    synchronized private void sortUnseenGroups() {
        if (isNotEmpty(unSeenGroups)) {
            FXCollections.sort(unSeenGroups, makeGroupComparator(getSortOrder(), getSortBy()));
        }
    }

    synchronized private void sortAnalyzedGroups() {
        if (isNotEmpty(analyzedGroups)) {
            FXCollections.sort(analyzedGroups, makeGroupComparator(getSortOrder(), getSortBy()));
        }
    }

    synchronized public Set<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {

        switch (groupKey.getAttribute().attrName) {
            //these cases get special treatment
            case CATEGORY:
                return getFileIDsWithCategory((TagName) groupKey.getValue());
            case TAGS:
                return getFileIDsWithTag((TagName) groupKey.getValue());
            case MIME_TYPE:
                return getFileIDsWithMimeType((String) groupKey.getValue());
//            case HASHSET: //comment out this case to use db functionality for hashsets
//                return getFileIDsWithHashSetName((String) groupKey.getValue());
            default:
                //straight db query
                return getDrawableDB().getFileIDsInGroup(groupKey);
        }
    }

    // @@@ This was kind of slow in the profiler.  Maybe we should cache it.
    // Unless the list of file IDs is necessary, use countFilesWithCategory() to get the counts.
    synchronized public Set<Long> getFileIDsWithCategory(TagName category) throws TskCoreException {
        Set<Long> fileIDsToReturn = Collections.emptySet();

        try {
            final DrawableTagsManager tagsManager = controller.getTagsManager();

            List<ContentTag> contentTags = tagsManager.getContentTagsByTagName(category);
            fileIDsToReturn = contentTags.stream()
                    .filter(ct -> ct.getContent() instanceof AbstractFile)
                    .filter(ct -> getDrawableDB().isInDB(ct.getContent().getId()))
                    .map(ct -> ct.getContent().getId())
                    .collect(Collectors.toSet());
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "TSK error getting files in Category:" + category.getDisplayName(), ex); //NON-NLS
            throw ex;
        }

        return fileIDsToReturn;
    }

    synchronized public Set<Long> getFileIDsWithTag(TagName tagName) throws TskCoreException {
        return controller.getTagsManager().getContentTagsByTagName(tagName).stream()
                .map(ContentTag::getContent)
                .filter(AbstractFile.class::isInstance)
                .map(Content::getId)
                .filter(getDrawableDB()::isInDB)
                .collect(Collectors.toSet());
    }

    public synchronized GroupSortBy getSortBy() {
        return sortByProp.get();
    }

    synchronized void setSortBy(GroupSortBy sortBy) {
        sortByProp.set(sortBy);
    }

    public ReadOnlyObjectProperty< GroupSortBy> getSortByProperty() {
        return sortByProp.getReadOnlyProperty();
    }

    public synchronized DrawableAttribute<?> getGroupBy() {
        return groupByProp.get();
    }

    synchronized void setGroupBy(DrawableAttribute<?> groupBy) {
        groupByProp.set(groupBy);
    }

    public ReadOnlyObjectProperty<DrawableAttribute<?>> getGroupByProperty() {
        return groupByProp.getReadOnlyProperty();
    }

    public synchronized SortOrder getSortOrder() {
        return sortOrderProp.get();
    }

    synchronized void setSortOrder(SortOrder sortOrder) {
        sortOrderProp.set(sortOrder);
    }

    public ReadOnlyObjectProperty<SortOrder> getSortOrderProperty() {
        return sortOrderProp.getReadOnlyProperty();
    }

    /**
     *
     * @return null if all data sources are being displayed
     */
    public synchronized DataSource getDataSource() {
        return dataSourceProp.get();
    }

    /**
     *
     * @param dataSource Data source to display or null to display all of them
     */
    public synchronized void setDataSource(DataSource dataSource) {
        dataSourceProp.set(dataSource);
    }

    public ReadOnlyObjectProperty<DataSource> getDataSourceProperty() {
        return dataSourceProp.getReadOnlyProperty();
    }

    /**
     * Regroup all files in the database. see ReGroupTask for more details.
     *
     * @param <A>        The type of the values of the groupBy attriubte.
     * @param dataSource The DataSource to show. Null for all data sources.
     * @param groupBy    The DrawableAttribute to group by
     * @param sortBy     The GroupSortBy to sort the groups by
     * @param sortOrder  The SortOrder to use when sorting the groups.
     * @param force      true to force a full db query regroup, even if only the
     *                   sorting has changed.
     */
    public synchronized <A extends Comparable<A>> void regroup(DataSource dataSource, DrawableAttribute<A> groupBy, GroupSortBy sortBy, SortOrder sortOrder, Boolean force) {

        if (!Case.isCaseOpen()) {
            return;
        }
        setSortBy(sortBy);
        setSortOrder(sortOrder);
        //only re-query the db if the data source or group by attribute changed or it is forced
        if (dataSource != getDataSource()
                || groupBy != getGroupBy()
                || force) {

            setDataSource(dataSource);
            setGroupBy(groupBy);

            Platform.runLater(regrouper::restart);
        } else {
            // resort the list of groups

            sortAnalyzedGroups();
            sortUnseenGroups();
        }
    }

    public ReadOnlyDoubleProperty regroupProgress() {
        return regrouper.progressProperty();
    }

    public ReadOnlyStringProperty regroupMessage() {
        return regrouper.messageProperty();
    }

    @Subscribe
    synchronized public void handleTagAdded(ContentTagAddedEvent evt) {
        GroupKey<?> newGroupKey = null;
        final long fileID = evt.getAddedTag().getContent().getId();
        if (getGroupBy() == DrawableAttribute.CATEGORY && controller.getCategoryManager().isCategoryTagName(evt.getAddedTag().getName())) {
            newGroupKey = new GroupKey<>(DrawableAttribute.CATEGORY, evt.getAddedTag().getName(), getDataSource());
            for (GroupKey<?> oldGroupKey : groupMap.keySet()) {
                if (oldGroupKey.equals(newGroupKey) == false) {
                    removeFromGroup(oldGroupKey, fileID);
                }
            }
        } else if (getGroupBy() == DrawableAttribute.TAGS && controller.getCategoryManager().isNotCategoryTagName(evt.getAddedTag().getName())) {
            newGroupKey = new GroupKey<>(DrawableAttribute.TAGS, evt.getAddedTag().getName(), getDataSource());
        }
        if (newGroupKey != null) {
            DrawableGroup g = getGroupForKey(newGroupKey);
            addFileToGroup(g, newGroupKey, fileID);
        }
    }

    /**
     * Adds an analyzed file to the in-memory group data structures. Marks the
     * group as unseen.
     *
     * @param group    Group being added to (will be null if a group has not yet
     *                 been created)
     * @param groupKey Group type/value
     * @param fileID
     */
    @SuppressWarnings("AssignmentToMethodParameter")
    synchronized private void addFileToGroup(DrawableGroup group, final GroupKey<?> groupKey, final long fileID) {

        // NOTE: We assume that it has already been determined that GroupKey can be displayed based on Data Source filters
        if (group == null) {
            //if there wasn't already a DrawableGroup, then check if this group is now 
            // in an appropriate state to get one made.  
            // Path group, for example, only gets a DrawableGroup created when all files are analyzed
            /*
             * NOTE: With the current (Jan 2019) behavior of how we detect a
             * PATH group as being analyzed, the group is not marked as analyzed
             * until we add a file for another folder. So, when the last picture
             * in a folder is added to the group, the call to
             * 'populateIfAnalyzed' will still not return a group and therefore
             * this method will never mark the group as unseen.
             */
            group = populateIfAnalyzed(groupKey, null);
        } else {
            //if there is aleady a group that was previously deemed fully analyzed, then add this newly analyzed file to it.
            group.addFile(fileID);
        }

        // reset the seen status for the group (if it is currently considered analyzed)
        if (group != null) {
            markGroupUnseen(group);
        }
    }

    @Subscribe
    synchronized public void handleTagDeleted(ContentTagDeletedEvent evt) {
        GroupKey<?> groupKey = null;
        final ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        final TagName deletedTagName = deletedTagInfo.getName();
        if (getGroupBy() == DrawableAttribute.CATEGORY && controller.getCategoryManager().isCategoryTagName(deletedTagName)) {
            groupKey = new GroupKey<>(DrawableAttribute.CATEGORY, deletedTagName, null);
        } else if (getGroupBy() == DrawableAttribute.TAGS && controller.getCategoryManager().isNotCategoryTagName(deletedTagName)) {
            groupKey = new GroupKey<>(DrawableAttribute.TAGS, deletedTagName, null);
        }
        if (groupKey != null) {
            final long fileID = deletedTagInfo.getContentID();
            DrawableGroup g = removeFromGroup(groupKey, fileID);
        }
    }

    @Subscribe
    synchronized public void handleFileRemoved(Collection<Long> removedFileIDs) {

        for (final long fileId : removedFileIDs) {
            //get grouping(s) this file would be in
            Set<GroupKey<?>> groupsForFile = getAllGroupKeysForFile(fileId);

            for (GroupKey<?> gk : groupsForFile) {
                removeFromGroup(gk, fileId);
            }
        }
    }

    /**
     * Handle notifications sent from Db when files are inserted/updated
     *
     * @param updatedFileIDs The ID of the inserted/updated files.
     */
    @Subscribe
    synchronized public void handleFileUpdate(Collection<Long> updatedFileIDs) {
        /**
         * TODO: is there a way to optimize this to avoid quering to db so much.
         * the problem is that as a new files are analyzed they might be in new
         * groups( if we are grouping by say make or model) -jm
         */
        for (long fileId : updatedFileIDs) {
            // reset the hash cache
            controller.getHashSetManager().invalidateHashSetsCacheForFile(fileId);

            // first of all, update the current path group, regardless of what grouping is in view
            try {
                DrawableFile file = getDrawableDB().getFileFromID(fileId);
                String pathVal = file.getDrawablePath();
                GroupKey<?> pathGroupKey = new GroupKey<>(DrawableAttribute.PATH, pathVal, file.getDataSource());

                updateCurrentPathGroup(pathGroupKey);
            } catch (TskCoreException | TskDataException ex) {
                logger.log(Level.WARNING, "Error getting drawabledb for fileId " + fileId, ex);
            }

            // Update all the groups that this file belongs to
            Set<GroupKey<?>> groupsForFile = getAllGroupKeysForFile(fileId);
            for (GroupKey<?> gk : groupsForFile) {
                // see if a group has been created yet for the key
                DrawableGroup g = getGroupForKey(gk);
                addFileToGroup(g, gk, fileId);
            }
        }

        //we fire this event for all files so that the category counts get updated during initial db population
        controller.getCategoryManager().fireChange(updatedFileIDs, null);
    }

    /**
     * Checks if the given path is different from the current path group. If so,
     * updates the current path group as analyzed, and sets current path group
     * to the given path.
     *
     * The idea is that when the path of the files being processed changes, we
     * have moved from one folder to the next, and the group for the previous
     * PATH can be considered as analyzed and can be displayed.
     *
     * NOTE: this a close approximation for when all files in a folder have been
     * processed, but there's some room for error - files may go down the ingest
     * pipleline out of order or the events may not always arrive in the same
     * order
     *
     * @param groupKey
     */
    synchronized private void updateCurrentPathGroup(GroupKey<?> groupKey) {
        try {
            if (groupKey.getAttribute() == DrawableAttribute.PATH) {

                if (this.currentPathGroup == null) {
                    currentPathGroup = groupKey;
                } else if (groupKey.getValue().toString().equalsIgnoreCase(this.currentPathGroup.getValue().toString()) == false) {
                    // mark the last path group as analyzed
                    getDrawableDB().markGroupAnalyzed(currentPathGroup);
                    populateIfAnalyzed(currentPathGroup, null);

                    currentPathGroup = groupKey;
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error setting is_analyzed status for group: %s", groupKey.getValue().toString()), ex); //NON-NLS
        }
    }

    /**
     * Resets current path group, after marking the current path group as
     * analyzed.
     */
    synchronized public void resetCurrentPathGroup() {
        try {
            if (currentPathGroup != null) {
                getDrawableDB().markGroupAnalyzed(currentPathGroup);
                populateIfAnalyzed(currentPathGroup, null);
                currentPathGroup = null;
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Error resetting last path group: %s", currentPathGroup.getValue().toString()), ex); //NON-NLS
        }
    }

    /**
     * If the group is analyzed (or other criteria based on grouping) and should
     * be shown to the user, then add it to the appropriate data structures so
     * that it can be viewed.
     *
     * @returns null if Group is not ready to be viewed
     */
    synchronized private DrawableGroup populateIfAnalyzed(GroupKey<?> groupKey, ReGroupTask<?> task) {
        /*
         * If this method call is part of a ReGroupTask and that task is
         * cancelled, no-op.
         *
         * This allows us to stop if a regroup task has been cancelled (e.g. the
         * user picked a different group by attribute, while the current task
         * was still running)
         */
        if (isNull(task) == false && task.isCancelled() == true) {
            return null;
        }

        /*
         * For attributes other than path we can't be sure a group is fully
         * analyzed because we don't know all the files that will be a part of
         * that group. just show them no matter what.
         */
        try {
            if (groupKey.getAttribute() != DrawableAttribute.PATH
                    || getDrawableDB().isGroupAnalyzed(groupKey)) {
                Set<Long> fileIDs = getFileIDsInGroup(groupKey);
                if (Objects.nonNull(fileIDs) && ! fileIDs.isEmpty()) {

                    long examinerID = collaborativeModeProp.get() ? -1 : controller.getCaseDatabase().getCurrentExaminer().getId();
                    final boolean groupSeen = getDrawableDB().isGroupSeenByExaminer(groupKey, examinerID);
                    DrawableGroup group;

                    if (groupMap.containsKey(groupKey)) {
                        group = groupMap.get(groupKey);
                        group.setFiles(fileIDs);
                        group.setSeen(groupSeen);
                    } else {
                        group = new DrawableGroup(groupKey, fileIDs, groupSeen, controller.getDrawablesDatabase(), controller.getHashSetManager());
                        controller.getCategoryManager().registerListener(group);
                        groupMap.put(groupKey, group);
                    }

                    // Add to analyzedGroups only if it's the a group with the selected groupBy attribute
                    if ((analyzedGroups.contains(group) == false)
                            && (getGroupBy() == group.getGroupKey().getAttribute())) {
                        analyzedGroups.add(group);
                        sortAnalyzedGroups();
                    }
                    updateUnSeenGroups(group);

                    return group;
                }
            }
        } catch (SQLException | TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get files for group: " + groupKey.getAttribute().attrName.toString() + " = " + groupKey.getValue(), ex); //NON-NLS
        }

        return null;
    }

    synchronized public Set<Long> getFileIDsWithMimeType(String mimeType) throws TskCoreException {

        HashSet<Long> hashSet = new HashSet<>();
        String query = (null == mimeType)
                ? "SELECT obj_id FROM tsk_files WHERE mime_type IS NULL" //NON-NLS
                : "SELECT obj_id FROM tsk_files WHERE mime_type = '" + mimeType + "'"; //NON-NLS

        try (SleuthkitCase.CaseDbQuery executeQuery = controller.getCaseDatabase().executeQuery(query);
                ResultSet resultSet = executeQuery.getResultSet();) {
            while (resultSet.next()) {
                final long fileID = resultSet.getLong("obj_id"); //NON-NLS
                if (getDrawableDB().isInDB(fileID)) {
                    hashSet.add(fileID);
                }
            }
            return hashSet;

        } catch (Exception ex) {
            throw new TskCoreException("Failed to get file ids with mime type " + mimeType, ex);
        }
    }

    synchronized public void setCollaborativeMode(Boolean newValue) {
        collaborativeModeProp.set(newValue);
        analyzedGroups.forEach(group -> {
            try {
                boolean groupSeenByExaminer = getDrawableDB().isGroupSeenByExaminer(
                        group.getGroupKey(),
                        newValue ? -1 : controller.getCaseDatabase().getCurrentExaminer().getId()
                );
                group.setSeen(groupSeenByExaminer);
                updateUnSeenGroups(group);
                if (group.isSeen()) {
                    unSeenGroups.removeAll(group);
                } else if (unSeenGroups.contains(group) == false) {
                    unSeenGroups.add(group);
                }

            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking seen state of group.", ex);
            }
        });
        sortUnseenGroups();

    }

    /**
     * Task to query database for files in sorted groups and build
     * DrawableGroups for them.
     *
     * @param <AttrValType> The type of the values that this task will group by.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @NbBundle.Messages({"# {0} - groupBy attribute Name",
        "ReGroupTask.displayTitle=regrouping by {0}: "})
    class ReGroupTask<AttrValType extends Comparable<AttrValType>> extends LoggedTask<Void> {

        private final DataSource dataSource;
        private final DrawableAttribute<AttrValType> groupBy;
        private final GroupSortBy sortBy;
        private final SortOrder sortOrder;

        ReGroupTask(DataSource dataSource, DrawableAttribute<AttrValType> groupBy, GroupSortBy sortBy, SortOrder sortOrder) {
            super(Bundle.ReGroupTask_displayTitle(groupBy.attrName.toString()), true);
            this.dataSource = dataSource;
            this.groupBy = groupBy;
            this.sortBy = sortBy;
            this.sortOrder = sortOrder;

            updateTitle(Bundle.ReGroupTask_displayTitle(groupBy.attrName.toString()));
        }

        @Override
        protected Void call() throws Exception {
            try {
                if (isCancelled()) {
                    return null;
                }

                updateProgress(-1, 1);

                analyzedGroups.clear();
                unSeenGroups.clear();

                // Get the list of group keys
                Multimap<DataSource, AttrValType> valsByDataSource = findValuesForAttribute();

                updateProgress(0, valsByDataSource.entries().size());
                int p = 0;
                // For each key value, partially create the group and add it to the list.
                for (final Map.Entry<DataSource, AttrValType> valForDataSource : valsByDataSource.entries()) {
                    if (isCancelled()) {
                        return null;
                    }
                    p++;
                    updateMessage(Bundle.ReGroupTask_displayTitle(groupBy.attrName.toString()) + valForDataSource.getValue());
                    updateProgress(p, valsByDataSource.size());
                    populateIfAnalyzed(new GroupKey<>(groupBy, valForDataSource.getValue(), valForDataSource.getKey()), this);
                }

                Optional<DrawableGroup> viewedGroup
                        = Optional.ofNullable(controller.getViewState())
                                .flatMap(GroupViewState::getGroup);
                Optional<GroupKey<?>> viewedKey = viewedGroup.map(DrawableGroup::getGroupKey);
                DataSource dataSourceOfCurrentGroup
                        = viewedKey.flatMap(GroupKey::getDataSource).orElse(null);
                DrawableAttribute attributeOfCurrentGroup
                        = viewedKey.map(GroupKey::getAttribute).orElse(null);

                if (viewedGroup.isPresent() == false //if no group was being viewed,
                        || (dataSource != null && notEqual(dataSourceOfCurrentGroup, dataSource)) //or the datasource of the viewed group is wrong,
                        || groupBy != attributeOfCurrentGroup) { // or the groupBy attribute is wrong...

                    //the current group should not be visible so ...
                    if (isNotEmpty(unSeenGroups)) {
                        //  show then next unseen group 
                        controller.advance(GroupViewState.createTile(unSeenGroups.get(0)));
                    } else if (isNotEmpty(analyzedGroups)) {
                        //show the first analyzed group.
                        controller.advance(GroupViewState.createTile(analyzedGroups.get(0)));
                    } else { //there are no groups,  clear the group area.
                        controller.advance(GroupViewState.createTile(null));
                    }
                }
            } finally {
                updateProgress(1, 1);
                updateMessage("");
            }
            return null;
        }

        @Override
        protected void done() {
            super.done();
            try {
                get();
            } catch (CancellationException cancelEx) { //NOPMD
                //cancellation is normal
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Error while regrouping.", ex);
            }
        }

        /**
         * Find the distinct values for the given column (DrawableAttribute).
         * These values represent the groups of files.
         *
         * @return map of data source (or null if group by attribute ignores
         *         data sources) to list of unique group values
         */
        public Multimap<DataSource, AttrValType> findValuesForAttribute() {

            Multimap results = HashMultimap.create();
            try {
                switch (groupBy.attrName) {
                    //these cases get special treatment
                    case CATEGORY:
                        results.putAll(null, controller.getCategoryManager().getCategories());
                        break;
                    case TAGS:
                        results.putAll(null, controller.getTagsManager().getTagNamesInUse().stream()
                                .filter(controller.getCategoryManager()::isNotCategoryTagName)
                                .collect(Collectors.toList()));
                        break;

                    case ANALYZED:
                        results.putAll(null, Arrays.asList(false, true));
                        break;
                    case HASHSET:

                        results.putAll(null, new TreeSet<>(getDrawableDB().getHashSetNames()));

                        break;
                    case MIME_TYPE:

                        HashSet<String> types = new HashSet<>();

                        // Use the group_concat function to get a list of files for each mime type.  
                        // This has different syntax on Postgres vs SQLite
                        String groupConcatClause;
                        if (DbType.POSTGRESQL == controller.getCaseDatabase().getDatabaseType()) {
                            groupConcatClause = " array_to_string(array_agg(obj_id), ',') as object_ids";
                        } else {
                            groupConcatClause = " group_concat(obj_id) as object_ids";
                        }
                        String query = "select " + groupConcatClause + " , mime_type from tsk_files group by mime_type ";
                        try (SleuthkitCase.CaseDbQuery executeQuery = controller.getCaseDatabase().executeQuery(query); //NON-NLS
                                ResultSet resultSet = executeQuery.getResultSet();) {
                            while (resultSet.next()) {
                                final String mimeType = resultSet.getString("mime_type"); //NON-NLS
                                String objIds = resultSet.getString("object_ids"); //NON-NLS

                                Pattern.compile(",").splitAsStream(objIds)
                                        .map(Long::valueOf)
                                        .filter(getDrawableDB()::isInDB)
                                        .findAny().ifPresent(obj_id -> types.add(mimeType));
                            }
                        } catch (SQLException | TskCoreException ex) {
                            logger.log(Level.WARNING, "Error getting group by MIME type", ex);
                        }
                        results.putAll(null, types);

                        break;
                    default:
                        //otherwise do straight db query 
                        results.putAll(getDrawableDB().findValuesForAttribute(groupBy, sortBy, sortOrder, dataSource));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "TSK error getting list of type {0}", groupBy.getDisplayName()); //NON-NLS
            }
            return results;

        }
    }

    private static Comparator<DrawableGroup> makeGroupComparator(final SortOrder sortOrder, GroupSortBy comparator) {
        switch (sortOrder) {
            case ASCENDING:
                return comparator;
            case DESCENDING:
                return comparator.reversed();
            case UNSORTED:
            default:
                return new GroupSortBy.AllEqualComparator<>();
        }
    }

    /**
     * @return the drawableDB
     */
    private DrawableDB getDrawableDB() {
        return controller.getDrawablesDatabase();

    }

    class GroupingService extends Service< Void> {

        @Override
        protected Task<Void> createTask() {
            synchronized (GroupManager.this) {
                return new ReGroupTask<>(getDataSource(), getGroupBy(), getSortBy(), getSortOrder());
            }
        }
    }
}
