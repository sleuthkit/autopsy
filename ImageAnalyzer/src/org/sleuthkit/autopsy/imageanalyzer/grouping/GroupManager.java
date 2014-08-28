/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imageanalyzer.grouping;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javax.swing.SortOrder;
import org.apache.commons.lang3.StringUtils;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.LoggedTask;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.ImageAnalyzerController;
import org.sleuthkit.autopsy.imageanalyzer.ImageAnalyzerModule;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.coreutils.ThreadConfined.ThreadType;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.Category;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableDB;
import org.sleuthkit.autopsy.imageanalyzer.datamodel.DrawableFile;
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
 * and sorting of {@link Grouping}s.
 */
public class GroupManager {

    private static final Logger LOGGER = Logger.getLogger(GroupManager.class.getName());

    private DrawableDB db;

    private final ImageAnalyzerController controller;

    /**
     * map from {@link GroupKey}s to {@link  Grouping}s. All groups (even not
     * fully analyzed or not visible groups could be in this map
     */
    private final Map<GroupKey<?>, Grouping> groupMap = new HashMap<>();

    /**
     * list of all analyzed groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<Grouping> analyzedGroups = FXCollections.observableArrayList();

    /**
     * list of unseen groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final ObservableList<Grouping> unSeenGroups = FXCollections.observableArrayList();

    /**
     * sorted list of unseen groups
     */
    @ThreadConfined(type = ThreadType.JFX)
    private final SortedList<Grouping> sortedUnSeenGroups = unSeenGroups.sorted();

    private ReGroupTask groupByTask;

    /* --- current grouping/sorting attributes --- */
    private volatile GroupSortBy sortBy = GroupSortBy.NONE;

    private volatile DrawableAttribute<?> groupBy = DrawableAttribute.PATH;

    private volatile SortOrder sortOrder = SortOrder.ASCENDING;

    public void setDB(DrawableDB db) {
        this.db = db;
        regroup(groupBy, sortBy, sortOrder, Boolean.TRUE);
    }

    public ObservableList<Grouping> getAnalyzedGroups() {
        return analyzedGroups;
    }

    @ThreadConfined(type = ThreadType.JFX)
    public SortedList<Grouping> getUnSeenGroups() {
        return sortedUnSeenGroups;
    }

    /**
     * construct a group manager hooked up to the given db and controller
     *
     * @param db
     * @param controller
     */
    public GroupManager(ImageAnalyzerController controller) {
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
    public Set<GroupKey<?>> getGroupKeysForFile(DrawableFile<?> file) {
        Set<GroupKey<?>> resultSet = new HashSet<>();
        final Object valueOfAttribute = file.getValueOfAttribute(groupBy);

        List<? extends Comparable<?>> vals;
        if (valueOfAttribute instanceof List<?>) {
            vals = (List<? extends Comparable<?>>) valueOfAttribute;
        } else {
            vals = Collections.singletonList((Comparable<?>) valueOfAttribute);
        }
        for (Comparable<?> val : vals) {
            final GroupKey<?> groupKey = new GroupKey(groupBy, val);
            resultSet.add(groupKey);
        }
        return resultSet;
    }

    /**
     * using the current groupBy set for this manager, find groupkeys for all
     * the groups the given file is a part of
     *
 
     *
     * @return a a set of {@link GroupKey}s representing the group(s) the given
     * file is a part of
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Set<GroupKey<?>> getGroupKeysForFileID(Long fileID) {
        Set<GroupKey<?>> resultSet = new HashSet<>();
        try {
            DrawableFile<?> file = db.getFileFromID(fileID);
            final Object valueOfAttribute = file.getValueOfAttribute(groupBy);

            List<? extends Comparable<?>> vals;
            if (valueOfAttribute instanceof List<?>) {
                vals = (List<? extends Comparable<?>>) valueOfAttribute;
            } else {
                vals = Collections.singletonList((Comparable<?>) valueOfAttribute);
            }

            for (Comparable<?> val : vals) {
                final GroupKey<?> groupKey = new GroupKey(groupBy, val);
                resultSet.add(groupKey);
            }
        } catch (TskCoreException ex) {
            Logger.getLogger(GroupManager.class.getName()).log(Level.SEVERE, "failed to load file with id: " + fileID + " from database", ex);
        }
        return resultSet;
    }

    /**
     * @param groupKey
     *
     * @return return the Grouping (if it exists) for the given GroupKey, or
     * null if no group exists for that key.
     */
    public Grouping getGroupForKey(GroupKey<?> groupKey) {
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
     * make and return a group with the given key and files. If a group already
     * existed for that key, it will be replaced.
     *
     * @param groupKey
     * @param files
     *
     * @return
     *
     * TODO: check if a group already exists for that key and ... (do what?add
     * files to it?) -jm
     */
    public Grouping makeGroup(GroupKey<?> groupKey, List<Long> files) {
        List<Long> newFiles = files == null ? new ArrayList<>() : files;

        Grouping g = new Grouping(groupKey, newFiles);
        synchronized (groupMap) {
            groupMap.put(groupKey, g);
        }
        return g;
    }

    public void markGroupSeen(Grouping group) {
        unSeenGroups.remove(group);
        db.markGroupSeen(group.groupKey);
    }

    /**
     * remove the given file from the group with the given key. If the group
     * doesn't exist or doesn't already contain this file, this method is a
     * no-op
     *
     * @param groupKey the value of groupKey
     * @param fileID the value of file
     */
    public synchronized void removeFromGroup(GroupKey<?> groupKey, final Long fileID) {
        //get grouping this file would be in
        final Grouping group = getGroupForKey(groupKey);
        if (group != null) {
            group.removeFile(fileID);
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
    private synchronized void populateAnalyzedGroup(final GroupKey<?> groupKey, List<Long> filesInGroup, ReGroupTask task) {

        /* if this is not part of a regroup task or it is but the task is not
         * cancelled...
         *
         * this allows us to stop if a regroup task has been cancelled (e.g. the
         * user picked a different group by attribute, while the current task
         * was still running) */
        if (task == null || (task.isCancelled() == false)) {
            Grouping g = makeGroup(groupKey, filesInGroup);

            final boolean groupSeen = db.isGroupSeen(groupKey);
            Platform.runLater(() -> {
                analyzedGroups.add(g);

                if (groupSeen == false) {
                    unSeenGroups.add(g);
                }
            });
        }
    }

    /**
     * check if the group for the given groupkey is analyzed
     *
     * @param groupKey
     *
     * @return null if this group is not analyzed or a list of file ids in this
     * group if they are all analyzed
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
            LOGGER.log(Level.SEVERE, "failed to get files for group: " + groupKey.getAttribute().attrName.name() + " = " + groupKey.getValue(), ex);
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

            final SleuthkitCase sleuthkitCase = ImageAnalyzerController.getDefault().getSleuthKitCase();
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
                        if (ImageAnalyzerModule.isSupportedAndNotKnown(Case.getCurrentCase().getSleuthkitCase().getAbstractFileById(id))) {
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
     * find the distinct values for the given column (DrawableAttribute) in the
     * order given by sortBy and sortOrder.
     *
     * These values represent the groups of files.
     *
     * @param regroup
     * @param sortBy
     * @param sortOrder
     *
     * @return
     */
    public <A extends Comparable<A>> List<A> findValuesForAttribute(DrawableAttribute<A> groupBy, GroupSortBy sortBy, SortOrder sortOrder) {
        try {
            List<A> values;
            switch (groupBy.attrName) {
                //these cases get special treatment
                case CATEGORY:
                    values = (List<A>) Category.valuesList();
                    break;
                case TAGS:
                    values = (List<A>) Case.getCurrentCase().getServices().getTagsManager().getTagNamesInUse();
                    break;
                case ANALYZED:
                    values = (List<A>) Arrays.asList(false, true);
                    break;
                case HASHSET:
                    TreeSet<A> names = new TreeSet<>((Set<A>) db.getHashSetNames());
                    values = new ArrayList<>(names);
                    break;
                default:
                    //otherwise do straight db query 
                    return db.findValuesForAttribute(groupBy, sortBy, sortOrder);
            }

            //sort in memory
            Collections.sort(values, sortBy.getValueComparator(groupBy, sortOrder));

            return values;
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
            return new ArrayList<>();
        }
    }

    /**
     * find the distinct values of the regroup attribute in the order given by
     * sortBy with a ascending order
     *
     * @param regroup
     * @param sortBy
     *
     * @return
     */
    public <A extends Comparable<A>> List<A> findValuesForAttribute(DrawableAttribute<A> groupBy, GroupSortBy sortBy) {
        return findValuesForAttribute(groupBy, sortBy, SortOrder.ASCENDING);
    }

    public List<Long> getFileIDsInGroup(GroupKey<?> groupKey) throws TskCoreException {
        switch (groupKey.getAttribute().attrName) {
            //these cases get special treatment
            case CATEGORY:
                return getFileIDsWithCategory((Category) groupKey.getValue());
            case TAGS:
                return getFileIDsWithTag((TagName) groupKey.getValue());
            case HASHSET: //comment out this case to use db functionality for hashsets
                return getFileIDsWithHashSetName((String) groupKey.getValue());
            default:
                //straight db query
                return db.getFileIDsInGroup(groupKey);
        }
    }

    // @@@ This was kind of slow in the profiler.  Maybe we should cache it.
    public List<Long> getFileIDsWithCategory(Category category) throws TskCoreException {

        try {
            if (category == Category.ZERO) {

                List<Long> files = new ArrayList<>();
                TagName[] tns = {Category.FOUR.getTagName(), Category.THREE.getTagName(), Category.TWO.getTagName(), Category.ONE.getTagName(), Category.FIVE.getTagName()};
                for (TagName tn : tns) {
                    List<ContentTag> contentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByTagName(tn);
                    for (ContentTag ct : contentTags) {
                        if (ct.getContent() instanceof AbstractFile && ImageAnalyzerModule.isSupportedAndNotKnown((AbstractFile) ct.getContent())) {
                            files.add(ct.getContent().getId());
                        }
                    }
                }

                return db.findAllFileIdsWhere("obj_id NOT IN (" + StringUtils.join(files, ',') + ")");
            } else {

                List<Long> files = new ArrayList<>();
                List<ContentTag> contentTags = Case.getCurrentCase().getServices().getTagsManager().getContentTagsByTagName(category.getTagName());
                for (ContentTag ct : contentTags) {
                    if (ct.getContent() instanceof AbstractFile && ImageAnalyzerModule.isSupportedAndNotKnown((AbstractFile) ct.getContent())) {
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
                if (ct.getContent() instanceof AbstractFile && ImageAnalyzerModule.isSupportedAndNotKnown((AbstractFile) ct.getContent())) {
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
     * @param force true to force a full db query regroup
     */
    public void regroup(final DrawableAttribute<?> groupBy, final GroupSortBy sortBy, final SortOrder sortOrder, Boolean force) {
        //only re-query the db if the group by attribute changed or it is forced
        if (groupBy != getGroupBy() || force == true) {
            setGroupBy(groupBy);
            setSortBy(sortBy);
            setSortOrder(sortOrder);
            if (groupByTask != null) {
                groupByTask.cancel(true);
            }
            Platform.runLater(() -> {
                sortedUnSeenGroups.setComparator(sortBy.getGrpComparator( sortOrder));
            });

            groupByTask = new ReGroupTask(groupBy, sortBy, sortOrder);
            controller.submitBGTask(groupByTask);
        } else {
            // just resort the list of groups
            setSortBy(sortBy);
            setSortOrder(sortOrder);
            Platform.runLater(() -> {
                sortedUnSeenGroups.setComparator(sortBy.getGrpComparator( sortOrder));
            });
        }
    }

    /**
     * Task to query database for files in sorted groups and build
     * {@link Groupings} for them
     */
    @SuppressWarnings({"unchecked","rawtypes"})
    private class ReGroupTask extends LoggedTask<Void> {

        private ProgressHandle groupProgress;

        private final DrawableAttribute groupBy;

        private final GroupSortBy sortBy;

        private final SortOrder sortOrder;

        public ReGroupTask(DrawableAttribute groupBy, GroupSortBy sortBy, SortOrder sortOrder) {
            super("regrouping files by " + groupBy.attrName.name() + " sorted by " + sortBy.name() + " in " + sortOrder.name() + " order", true);

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

            groupProgress = ProgressHandleFactory.createHandle(getTitle(), this);
            Platform.runLater(() -> {
                analyzedGroups.clear();
                unSeenGroups.clear();
            });
            groupMap.clear();

            //get a list of group key vals
            final List<Comparable<?>> vals = findValuesForAttribute(groupBy, sortBy, sortOrder);

            groupProgress.start(vals.size());

            int p = 0;
            //for each key value
            for (final Comparable<?> val : vals) {
                if (isCancelled()) {
                    return null;//abort
                }
                p++;
                updateMessage("regrouping files by " + groupBy.attrName.name() + " : " + val);
                updateProgress(p, vals.size());
                groupProgress.progress("regrouping files by " + groupBy.attrName.name() + " : " + val, p);
                //check if this group is analyzed
                final GroupKey<?> groupKey = new GroupKey<>(groupBy, val);

                List<Long> checkAnalyzed = checkAnalyzed(groupKey);
                if (checkAnalyzed != null) { // != null => the group is analyzed, so add it to the ui
                    populateAnalyzedGroup(groupKey, checkAnalyzed, ReGroupTask.this);
                }
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
