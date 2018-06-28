/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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

import com.google.common.eventbus.Subscribe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import static javafx.concurrent.Worker.State.CANCELLED;
import static javafx.concurrent.Worker.State.FAILED;
import static javafx.concurrent.Worker.State.READY;
import static javafx.concurrent.Worker.State.RUNNING;
import static javafx.concurrent.Worker.State.SCHEDULED;
import static javafx.concurrent.Worker.State.SUCCEEDED;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SortOrder;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.events.ContentTagAddedEvent;
import org.sleuthkit.autopsy.casemodule.events.ContentTagDeletedEvent;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.datamodel.DhsImageCategory;
import org.sleuthkit.autopsy.imagegallery.datamodel.CategoryManager;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableTagsManager;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.DbType;

/**
 * Provides an abstraction layer on top of {@link  DrawableDB} ( and to some
 * extent {@link SleuthkitCase} ) to facilitate creation, retrieval, updating,
 * and sorting of {@link DrawableGroup}s.
 */
public class GroupManager {

    private static final Logger LOGGER = Logger.getLogger(GroupManager.class.getName());

    private DrawableDB db;

    private final ImageGalleryController controller;

    /**
     * map from {@link GroupKey}s to {@link  DrawableGroup}s. All groups (even
     * not fully analyzed or not visible groups could be in this map
     */
    @GuardedBy("this")
    private final Map<GroupKey<?>, DrawableGroup> groupMap = new HashMap<>();

    /**
     * list of all analyzed groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<DrawableGroup> analyzedGroups = FXCollections.observableArrayList();
    private final ObservableList<DrawableGroup> unmodifiableAnalyzedGroups = FXCollections.unmodifiableObservableList(analyzedGroups);

    /**
     * list of unseen groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<DrawableGroup> unSeenGroups = FXCollections.observableArrayList();
    private final ObservableList<DrawableGroup> unmodifiableUnSeenGroups = FXCollections.unmodifiableObservableList(unSeenGroups);

    private ReGroupTask<?> groupByTask;

    /*
     * --- current grouping/sorting attributes ---
     */
    private volatile GroupSortBy sortBy = GroupSortBy.PRIORITY;
    private volatile DrawableAttribute<?> groupBy = DrawableAttribute.PATH;
    private volatile SortOrder sortOrder = SortOrder.ASCENDING;

    private final ReadOnlyObjectWrapper< Comparator<DrawableGroup>> sortByProp = new ReadOnlyObjectWrapper<>(sortBy);
    private final ReadOnlyObjectWrapper< DrawableAttribute<?>> groupByProp = new ReadOnlyObjectWrapper<>(groupBy);
    private final ReadOnlyObjectWrapper<SortOrder> sortOrderProp = new ReadOnlyObjectWrapper<>(sortOrder);

    private final ReadOnlyDoubleWrapper regroupProgress = new ReadOnlyDoubleWrapper();

    public void setDB(DrawableDB db) {
        this.db = db;
        regroup(groupBy, sortBy, sortOrder, Boolean.TRUE);
    }

    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableList<DrawableGroup> getAnalyzedGroups() {
        return unmodifiableAnalyzedGroups;
    }

    @ThreadConfined(type = ThreadType.JFX)
    @SuppressWarnings("ReturnOfCollectionOrArrayField")
    public ObservableList<DrawableGroup> getUnSeenGroups() {
        return unmodifiableUnSeenGroups;
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
    synchronized public Set<GroupKey<?>> getGroupKeysForFile(DrawableFile file) {
        Set<GroupKey<?>> resultSet = new HashSet<>();
        for (Comparable<?> val : groupBy.getValue(file)) {
            if (groupBy == DrawableAttribute.TAGS) {
                if (CategoryManager.isNotCategoryTagName((TagName) val)) {
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
     * @return a a set of {@link GroupKey}s representing the group(s) the given
     *         file is a part of
     */
    synchronized public Set<GroupKey<?>> getGroupKeysForFileID(Long fileID) {
        try {
            if (nonNull(db)) {
                DrawableFile file = db.getFileFromID(fileID);
                return getGroupKeysForFile(file);
            } else {
                Logger.getLogger(GroupManager.class.getName()).log(Level.WARNING, "Failed to load file with id: {0} from database.  There is no database assigned.", fileID); //NON-NLS
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(GroupManager.class.getName()).log(Level.SEVERE, "failed to load file with id: " + fileID + " from database", ex); //NON-NLS
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
    public DrawableGroup getGroupForKey(@Nonnull GroupKey<?> groupKey) {
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
            unSeenGroups.forEach(controller.getCategoryManager()::unregisterListener);
            unSeenGroups.clear();
            analyzedGroups.forEach(controller.getCategoryManager()::unregisterListener);
            analyzedGroups.clear();

        });
        synchronized (groupMap) {
            groupMap.values().forEach(controller.getCategoryManager()::unregisterListener);
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
     * 'mark' the given group as seen. This removes it from the queue of groups
     * to review, and is persisted in the drawable db.
     *
     * @param group the {@link  DrawableGroup} to mark as seen
     */
    @ThreadConfined(type = ThreadType.JFX)
    public void markGroupSeen(DrawableGroup group, boolean seen) {
        if (nonNull(db)) {
            db.markGroupSeen(group.getGroupKey(), seen);
            group.setSeen(seen);
            if (seen) {
                unSeenGroups.removeAll(group);
            } else if (unSeenGroups.contains(group) == false) {
                unSeenGroups.add(group);
            }
            FXCollections.sort(unSeenGroups, applySortOrder(sortOrder, sortBy));
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
    public synchronized DrawableGroup removeFromGroup(GroupKey<?> groupKey, final Long fileID) {
        //get grouping this file would be in
        final DrawableGroup group = getGroupForKey(groupKey);
        if (group != null) {
            Platform.runLater(() -> {
                group.removeFile(fileID);
            });

            // If we're grouping by category, we don't want to remove empty groups.
            if (groupKey.getAttribute() != DrawableAttribute.CATEGORY) {
                if (group.getFileIDs().isEmpty()) {
                    Platform.runLater(() -> {
                        if (analyzedGroups.contains(group)) {
                            analyzedGroups.remove(group);
                            FXCollections.sort(analyzedGroups, applySortOrder(sortOrder, sortBy));
                        }
                        if (unSeenGroups.contains(group)) {
                            unSeenGroups.remove(group);
                            FXCollections.sort(unSeenGroups, applySortOrder(sortOrder, sortBy));
                        }
                    });
                }
            } else { //group == null
                // It may be that this was the last unanalyzed file in the group, so test
                // whether the group is now fully analyzed.
                popuplateIfAnalyzed(groupKey, null);
            }
        }
        return group;
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
        List<A> values = Collections.emptyList();
        try {
            switch (groupBy.attrName) {
                //these cases get special treatment
                case CATEGORY:
                    values = (List<A>) Arrays.asList(DhsImageCategory.values());
                    break;
                case TAGS:
                    values = (List<A>) controller.getTagsManager().getTagNamesInUse().stream()
                            .filter(CategoryManager::isNotCategoryTagName)
                            .collect(Collectors.toList());
                    break;
                case ANALYZED:
                    values = (List<A>) Arrays.asList(false, true);
                    break;
                case HASHSET:
                    if (nonNull(db)) {
                        TreeSet<A> names = new TreeSet<>((Collection<? extends A>) db.getHashSetNames());
                        values = new ArrayList<>(names);
                    }
                    break;
                case MIME_TYPE:
                    if (nonNull(db)) {
                        HashSet<String> types = new HashSet<>();
                        // Use the group_concat function to get a list of files for each mime type.  
                        // This has sifferent syntax on Postgres vs SQLite
                        String groupConcatClause;
                        if (DbType.POSTGRESQL == controller.getSleuthKitCase().getDatabaseType()) {
                            groupConcatClause = " array_to_string(array_agg(obj_id), ',') as object_ids";
                        }
                        else {
                            groupConcatClause = "select group_concat(obj_id) as object_ids";
                        }
                        String querySQL  = "select " + groupConcatClause + ", mime_type from tsk_files group by mime_type ";
                        try (SleuthkitCase.CaseDbQuery executeQuery = controller.getSleuthKitCase().executeQuery(querySQL); //NON-NLS
                                ResultSet resultSet = executeQuery.getResultSet();) {
                            while (resultSet.next()) {
                                final String mimeType = resultSet.getString("mime_type"); //NON-NLS
                                String objIds = resultSet.getString("object_ids"); //NON-NLS

                                Pattern.compile(",").splitAsStream(objIds)
                                        .map(Long::valueOf)
                                        .filter(db::isInDB)
                                        .findAny().ifPresent(obj_id -> types.add(mimeType));
                            }
                        } catch (SQLException | TskCoreException ex) {
                            Exceptions.printStackTrace(ex);
                        }
                        values = new ArrayList<>((Collection<? extends A>) types);
                    }
                    break;
                default:
                    //otherwise do straight db query 
                    if (nonNull(db)) {
                        values = db.findValuesForAttribute(groupBy, sortBy, sortOrder);
                    }
            }

            return values;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting list of type {0}", groupBy.getDisplayName()); //NON-NLS
            return Collections.emptyList();
        }

    }

    public Set<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {
        Set<Long> fileIDsToReturn = Collections.emptySet();
        switch (groupKey.getAttribute().attrName) {
            //these cases get special treatment
            case CATEGORY:
                fileIDsToReturn = getFileIDsWithCategory((DhsImageCategory) groupKey.getValue());
                break;
            case TAGS:
                fileIDsToReturn = getFileIDsWithTag((TagName) groupKey.getValue());
                break;
            case MIME_TYPE:
                fileIDsToReturn = getFileIDsWithMimeType((String) groupKey.getValue());
                break;
//            case HASHSET: //comment out this case to use db functionality for hashsets
//                return getFileIDsWithHashSetName((String) groupKey.getValue());
            default:
                //straight db query
                if (nonNull(db)) {
                    fileIDsToReturn = db.getFileIDsInGroup(groupKey);
                }
        }
        return fileIDsToReturn;
    }

    // @@@ This was kind of slow in the profiler.  Maybe we should cache it.
    // Unless the list of file IDs is necessary, use countFilesWithCategory() to get the counts.
    public Set<Long> getFileIDsWithCategory(DhsImageCategory category) throws TskCoreException {
        Set<Long> fileIDsToReturn = Collections.emptySet();
        if (nonNull(db)) {
            try {
                final DrawableTagsManager tagsManager = controller.getTagsManager();
                if (category == DhsImageCategory.ZERO) {
                    List< TagName> tns = Stream.of(DhsImageCategory.ONE, DhsImageCategory.TWO, DhsImageCategory.THREE, DhsImageCategory.FOUR, DhsImageCategory.FIVE)
                            .map(tagsManager::getTagName)
                            .collect(Collectors.toList());

                    Set<Long> files = new HashSet<>();
                    for (TagName tn : tns) {
                        if (tn != null) {
                            List<ContentTag> contentTags = tagsManager.getContentTagsByTagName(tn);
                            files.addAll(contentTags.stream()
                                    .filter(ct -> ct.getContent() instanceof AbstractFile)
                                    .filter(ct -> db.isInDB(ct.getContent().getId()))
                                    .map(ct -> ct.getContent().getId())
                                    .collect(Collectors.toSet()));
                        }
                    }

                    fileIDsToReturn = db.findAllFileIdsWhere("obj_id NOT IN (" + StringUtils.join(files, ',') + ")"); //NON-NLS
                } else {

                    List<ContentTag> contentTags = tagsManager.getContentTagsByTagName(tagsManager.getTagName(category));
                    fileIDsToReturn = contentTags.stream()
                            .filter(ct -> ct.getContent() instanceof AbstractFile)
                            .filter(ct -> db.isInDB(ct.getContent().getId()))
                            .map(ct -> ct.getContent().getId())
                            .collect(Collectors.toSet());
                }
            } catch (TskCoreException ex) {
                LOGGER.log(Level.WARNING, "TSK error getting files in Category:" + category.getDisplayName(), ex); //NON-NLS
                throw ex;
            }
        }
        return fileIDsToReturn;
    }

    public Set<Long> getFileIDsWithTag(TagName tagName) throws TskCoreException {
        try {
            Set<Long> files = new HashSet<>();
            List<ContentTag> contentTags = controller.getTagsManager().getContentTagsByTagName(tagName);
            for (ContentTag ct : contentTags) {
                if (ct.getContent() instanceof AbstractFile && nonNull(db) && db.isInDB(ct.getContent().getId())) {
                    files.add(ct.getContent().getId());
                }
            }
            return files;
        } catch (TskCoreException ex) {
            LOGGER.log(Level.WARNING, "TSK error getting files with Tag:" + tagName.getDisplayName(), ex); //NON-NLS
            throw ex;
        }
    }

    public GroupSortBy getSortBy() {
        return sortBy;
    }

    void setSortBy(GroupSortBy sortBy) {
        this.sortBy = sortBy;
        Platform.runLater(() -> sortByProp.set(sortBy));
    }

    public ReadOnlyObjectProperty< Comparator<DrawableGroup>> getSortByProperty() {
        return sortByProp.getReadOnlyProperty();
    }

    public DrawableAttribute<?> getGroupBy() {
        return groupBy;
    }

    void setGroupBy(DrawableAttribute<?> groupBy) {
        this.groupBy = groupBy;
        Platform.runLater(() -> groupByProp.set(groupBy));
    }

    public ReadOnlyObjectProperty<DrawableAttribute<?>> getGroupByProperty() {
        return groupByProp.getReadOnlyProperty();
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder;
        Platform.runLater(() -> sortOrderProp.set(sortOrder));
    }

    public ReadOnlyObjectProperty<SortOrder> getSortOrderProperty() {
        return sortOrderProp.getReadOnlyProperty();
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
    public synchronized <A extends Comparable<A>> void regroup(final DrawableAttribute<A> groupBy, final GroupSortBy sortBy, final SortOrder sortOrder, Boolean force) {

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

            groupByTask = new ReGroupTask<>(groupBy, sortBy, sortOrder);
            Platform.runLater(() -> regroupProgress.bind(groupByTask.progressProperty()));
            regroupExecutor.submit(groupByTask);
        } else {
            // resort the list of groups
            setSortBy(sortBy);
            setSortOrder(sortOrder);
            Platform.runLater(() -> {
                FXCollections.sort(analyzedGroups, applySortOrder(sortOrder, sortBy));
                FXCollections.sort(unSeenGroups, applySortOrder(sortOrder, sortBy));
            });
        }
    }

    /**
     * an executor to submit async ui related background tasks to.
     */
    final ExecutorService regroupExecutor = Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().namingPattern("ui task -%d").build()); //NON-NLS

    public ReadOnlyDoubleProperty regroupProgress() {
        return regroupProgress.getReadOnlyProperty();
    }

    @Subscribe
    public void handleTagAdded(ContentTagAddedEvent evt) {
        GroupKey<?> newGroupKey = null;
        final long fileID = evt.getAddedTag().getContent().getId();
        if (groupBy == DrawableAttribute.CATEGORY && CategoryManager.isCategoryTagName(evt.getAddedTag().getName())) {
            newGroupKey = new GroupKey<>(DrawableAttribute.CATEGORY, CategoryManager.categoryFromTagName(evt.getAddedTag().getName()));
            for (GroupKey<?> oldGroupKey : groupMap.keySet()) {
                if (oldGroupKey.equals(newGroupKey) == false) {
                    removeFromGroup(oldGroupKey, fileID);
                }
            }
        } else if (groupBy == DrawableAttribute.TAGS && CategoryManager.isNotCategoryTagName(evt.getAddedTag().getName())) {
            newGroupKey = new GroupKey<>(DrawableAttribute.TAGS, evt.getAddedTag().getName());
        }
        if (newGroupKey != null) {
            DrawableGroup g = getGroupForKey(newGroupKey);
            addFileToGroup(g, newGroupKey, fileID);
        }
    }

    @SuppressWarnings("AssignmentToMethodParameter")
    private void addFileToGroup(DrawableGroup g, final GroupKey<?> groupKey, final long fileID) {
        if (g == null) {
            //if there wasn't already a group check if there should be one now
            g = popuplateIfAnalyzed(groupKey, null);
        }
        DrawableGroup group = g;
        if (group != null) {
            //if there is aleady a group that was previously deemed fully analyzed, then add this newly analyzed file to it.
            Platform.runLater(() -> group.addFile(fileID));
        }
    }

    @Subscribe
    public void handleTagDeleted(ContentTagDeletedEvent evt) {
        GroupKey<?> groupKey = null;
        final ContentTagDeletedEvent.DeletedContentTagInfo deletedTagInfo = evt.getDeletedTagInfo();
        final TagName tagName = deletedTagInfo.getName();
        if (groupBy == DrawableAttribute.CATEGORY && CategoryManager.isCategoryTagName(tagName)) {
            groupKey = new GroupKey<>(DrawableAttribute.CATEGORY, CategoryManager.categoryFromTagName(tagName));
        } else if (groupBy == DrawableAttribute.TAGS && CategoryManager.isNotCategoryTagName(tagName)) {
            groupKey = new GroupKey<>(DrawableAttribute.TAGS, tagName);
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
            Set<GroupKey<?>> groupsForFile = getGroupKeysForFileID(fileId);

            for (GroupKey<?> gk : groupsForFile) {
                removeFromGroup(gk, fileId);
            }
        }
    }

    /**
     * handle {@link FileUpdateEvent} sent from Db when files are
     * inserted/updated
     *
     * @param evt
     */
    @Subscribe
    synchronized public void handleFileUpdate(Collection<Long> updatedFileIDs) {
        /**
         * TODO: is there a way to optimize this to avoid quering to db so much.
         * the problem is that as a new files are analyzed they might be in new
         * groups( if we are grouping by say make or model) -jm
         */
        for (long fileId : updatedFileIDs) {

            controller.getHashSetManager().invalidateHashSetsForFile(fileId);

            //get grouping(s) this file would be in
            Set<GroupKey<?>> groupsForFile = getGroupKeysForFileID(fileId);
            for (GroupKey<?> gk : groupsForFile) {
                DrawableGroup g = getGroupForKey(gk);
                addFileToGroup(g, gk, fileId);
            }
        }

        //we fire this event for all files so that the category counts get updated during initial db population
        controller.getCategoryManager().fireChange(updatedFileIDs, null);
    }

    private DrawableGroup popuplateIfAnalyzed(GroupKey<?> groupKey, ReGroupTask<?> task) {

        if (Objects.nonNull(task) && (task.isCancelled())) {
            /*
             * if this method call is part of a ReGroupTask and that task is
             * cancelled, no-op
             *
             * this allows us to stop if a regroup task has been cancelled (e.g.
             * the user picked a different group by attribute, while the current
             * task was still running)
             */

        } else // no task or un-cancelled task
        {
            if (nonNull(db) && ((groupKey.getAttribute() != DrawableAttribute.PATH) || db.isGroupAnalyzed(groupKey))) {
                /*
                 * for attributes other than path we can't be sure a group is
                 * fully analyzed because we don't know all the files that will
                 * be a part of that group,. just show them no matter what.
                 */

                try {
                    Set<Long> fileIDs = getFileIDsInGroup(groupKey);
                    if (Objects.nonNull(fileIDs)) {
                        DrawableGroup group;
                        final boolean groupSeen = db.isGroupSeen(groupKey);
                        synchronized (groupMap) {
                            if (groupMap.containsKey(groupKey)) {
                                group = groupMap.get(groupKey);

                                group.setFiles(ObjectUtils.defaultIfNull(fileIDs, Collections.emptySet()));
                            } else {
                                group = new DrawableGroup(groupKey, fileIDs, groupSeen);
                                controller.getCategoryManager().registerListener(group);
                                group.seenProperty().addListener((o, oldSeen, newSeen) -> 
                                    Platform.runLater(() -> markGroupSeen(group, newSeen))
                                );
                                groupMap.put(groupKey, group);
                            }
                        }
                        Platform.runLater(() -> {
                            if (analyzedGroups.contains(group) == false) {
                                analyzedGroups.add(group);
                                if (Objects.isNull(task)) {
                                    FXCollections.sort(analyzedGroups, applySortOrder(sortOrder, sortBy));
                               }
                            }
                            markGroupSeen(group, groupSeen);
                        });
                        return group;

                    }
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "failed to get files for group: " + groupKey.getAttribute().attrName.toString() + " = " + groupKey.getValue(), ex); //NON-NLS
                }
            }
        }
        return null;
    }

    public Set<Long> getFileIDsWithMimeType(String mimeType) throws TskCoreException {

        HashSet<Long> hashSet = new HashSet<>();
        String query = (null == mimeType)
                ? "SELECT obj_id FROM tsk_files WHERE mime_type IS NULL" //NON-NLS
                : "SELECT obj_id FROM tsk_files WHERE mime_type = '" + mimeType + "'"; //NON-NLS

        try (SleuthkitCase.CaseDbQuery executeQuery = controller.getSleuthKitCase().executeQuery(query);
                ResultSet resultSet = executeQuery.getResultSet();) {
            while (resultSet.next()) {
                final long fileID = resultSet.getLong("obj_id"); //NON-NLS
                if (nonNull(db) && db.isInDB(fileID)) {
                    hashSet.add(fileID);
                }
            }
            return hashSet;

        } catch (Exception ex) {
            Exceptions.printStackTrace(ex);
            throw new TskCoreException("Failed to get file ids with mime type " + mimeType, ex);
        }
    }

    /**
     * Task to query database for files in sorted groups and build
     * {@link Groupings} for them
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @NbBundle.Messages({"# {0} - groupBy attribute Name",
        "# {1} - sortBy name",
        "# {2} - sort Order",
        "ReGroupTask.displayTitle=regrouping files by {0} sorted by {1} in {2} order",
        "# {0} - groupBy attribute Name",
        "# {1} - atribute value",
        "ReGroupTask.progressUpdate=regrouping files by {0} : {1}"})
    private class ReGroupTask<AttrType extends Comparable<AttrType>> extends LoggedTask<Void> {

        private ProgressHandle groupProgress;

        private final DrawableAttribute<AttrType> groupBy;

        private final GroupSortBy sortBy;

        private final SortOrder sortOrder;

        ReGroupTask(DrawableAttribute<AttrType> groupBy, GroupSortBy sortBy, SortOrder sortOrder) {
            super(Bundle.ReGroupTask_displayTitle(groupBy.attrName.toString(), sortBy.getDisplayName(), sortOrder.toString()), true);

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

            groupProgress = ProgressHandle.createHandle(Bundle.ReGroupTask_displayTitle(groupBy.attrName.toString(), sortBy.getDisplayName(), sortOrder.toString()), this);
            Platform.runLater(() -> {
                analyzedGroups.clear();
                unSeenGroups.clear();
            });

            // Get the list of group keys
            final List<AttrType> vals = findValuesForAttribute(groupBy);

            groupProgress.start(vals.size());

            int p = 0;
            // For each key value, partially create the group and add it to the list.
            for (final AttrType val : vals) {
                if (isCancelled()) {
                    return null;//abort
                }
                p++;
                updateMessage(Bundle.ReGroupTask_progressUpdate(groupBy.attrName.toString(), val));
                updateProgress(p, vals.size());
                groupProgress.progress(Bundle.ReGroupTask_progressUpdate(groupBy.attrName.toString(), val), p);
                popuplateIfAnalyzed(new GroupKey<>(groupBy, val), this);
            }
            Platform.runLater(() -> FXCollections.sort(analyzedGroups, applySortOrder(sortOrder, sortBy)));

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

    private static <T> Comparator<T> applySortOrder(final SortOrder sortOrder, Comparator<T> comparator) {
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
}
