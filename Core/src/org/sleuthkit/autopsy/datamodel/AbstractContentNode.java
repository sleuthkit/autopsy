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
package org.sleuthkit.autopsy.datamodel;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import org.apache.commons.lang3.tuple.Pair;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.lookup.Lookups;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Score;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

/**
 * Interface class that all Data nodes inherit from. Provides basic information
 * such as ID, parent ID, etc.
 *
 * @param <T> type of wrapped Content
 */
public abstract class AbstractContentNode<T extends Content> extends ContentNode {

    /**
     * Underlying Sleuth Kit Content object
     */
    protected final T content;
    private static final Logger logger = Logger.getLogger(AbstractContentNode.class.getName());

    /**
     * A pool of background tasks to run any long computation needed to populate
     * this node.
     */
    static final ExecutorService backgroundTasksPool;
    private static final Integer MAX_POOL_SIZE = 10;

    /**
     * Default no description string
     */
    @NbBundle.Messages({"AbstractContentNode.nodescription=no description",
        "AbstractContentNode.valueLoading=value loading"})
    protected static final String NO_DESCR = Bundle.AbstractContentNode_nodescription();
    protected static final String VALUE_LOADING = Bundle.AbstractContentNode_valueLoading();

    /**
     * Event signals to indicate the background tasks have completed processing.
     * Currently, we have one property task in the background:
     *
     * 1) Retrieving the translation of the file name
     */
    enum NodeSpecificEvents {
        TRANSLATION_AVAILABLE,
        SCO_AVAILABLE
    }

    static {
        //Initialize this pool only once! This will be used by every instance of AAFN
        //to do their heavy duty SCO column and translation updates.
        backgroundTasksPool = Executors.newFixedThreadPool(MAX_POOL_SIZE,
                new ThreadFactoryBuilder().setNameFormat("content-node-background-task-%d").build());
    }

    /**
     * Handles aspects that depend on the Content object
     *
     * @param content Underlying Content instances
     */
    AbstractContentNode(T content) {
        this(content, Lookups.fixed(content, new TskContentItem<>(content)));
    }

    /**
     * Handles aspects that depend on the Content object
     *
     * @param content Underlying Content instances
     * @param lookup  The Lookup object for the node.
     */
    AbstractContentNode(T content, Lookup lookup) {
        super(Children.create(new ContentChildren(content), true), lookup);
        this.content = content;
        //super.setName(ContentUtils.getSystemName(content));
        super.setName("content_" + Long.toString(content.getId())); //NON-NLS
    }

    /**
     * Return the content data associated with this node
     *
     * @return the content object wrapped by this node
     */
    public T getContent() {
        return content;
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public String getName() {
        return super.getName();
    }

    /**
     * Return true if the underlying content object has children Useful for lazy
     * loading.
     *
     * @return true if has children
     */
    public boolean hasVisibleContentChildren() {
        return contentHasVisibleContentChildren(content);
    }

    /**
     * Return true if the given content object has children. Useful for lazy
     * loading.
     *
     * @param c The content object to look for children on
     *
     * @return true if has children
     */
    public static boolean contentHasVisibleContentChildren(Content c) {
        if (c != null) {

            try {
                if (!c.hasChildren()) {
                    return false;
                }
            } catch (TskCoreException ex) {

                logger.log(Level.SEVERE, "Error checking if the node has children, for content: " + c, ex); //NON-NLS
                return false;
            }

            String query = "SELECT COUNT(obj_id) AS count FROM "
                    + " ( SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + c.getId() + " AND type = "
                    + TskData.ObjectType.ARTIFACT.getObjectType()
                    + "   INTERSECT SELECT artifact_obj_id FROM blackboard_artifacts WHERE obj_id = " + c.getId()
                    + "     AND (artifact_type_id = " + ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID()
                    + " OR artifact_type_id = " + ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() + ") "
                    + "   UNION SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + c.getId()
                    + "     AND type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + ") AS OBJECT_IDS"; //NON-NLS;

            try (SleuthkitCase.CaseDbQuery dbQuery = Case.getCurrentCaseThrows().getSleuthkitCase().executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                if (resultSet.next()) {
                    return (0 < resultSet.getInt("count"));
                }
            } catch (TskCoreException | SQLException | NoCurrentCaseException ex) {
                logger.log(Level.SEVERE, "Error checking if the node has children, for content: " + c, ex); //NON-NLS
            }
        }
        return false;
    }

    /**
     * Return true if the underlying content object has children Useful for lazy
     * loading.
     *
     * @return true if has children
     */
    public boolean hasContentChildren() {
        boolean hasChildren = false;

        if (content != null) {
            try {
                hasChildren = content.hasChildren();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking if the node has children, for content: " + content, ex); //NON-NLS
            }
        }

        return hasChildren;
    }

    /**
     * Return ids of children of the underlying content. The ids can be treated
     * as keys - useful for lazy loading.
     *
     * @return list of content ids of children content.
     */
    public List<Long> getContentChildrenIds() {
        List<Long> childrenIds = null;

        if (content != null) {
            try {
                childrenIds = content.getChildrenIds();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting children ids, for content: " + content, ex); //NON-NLS
            }
        }

        return childrenIds;

    }

    /**
     * Return children of the underlying content.
     *
     * @return list of content children content.
     */
    public List<Content> getContentChildren() {
        List<Content> children = null;

        if (content != null) {
            try {
                children = content.getChildren();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting children, for content: " + content, ex); //NON-NLS
            }
        }

        return children;

    }

    /**
     * Get count of the underlying content object children.
     *
     * Useful for lazy loading.
     *
     * @return content children count
     */
    public int getContentChildrenCount() {
        int childrenCount = -1;

        if (content != null) {
            try {
                childrenCount = content.getChildrenCount();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking node content children count, for content: " + content, ex); //NON-NLS
            }
        }

        return childrenCount;
    }

    /**
     * Reads the content of this node (of the underlying content object).
     *
     * @param buf    buffer to read into
     * @param offset the starting offset in the content object
     * @param len    the length to read
     *
     * @return the bytes read
     *
     * @throws TskException exception thrown if the requested part of content
     *                      could not be read
     */
    public int read(byte[] buf, long offset, long len) throws TskException {
        return content.read(buf, offset, len);
    }

    /**
     * Updates the values of the properties in the current property sheet with
     * the new properties being passed in. Only if that property exists in the
     * current sheet will it be applied. That way, we allow for subclasses to
     * add their own (or omit some!) properties and we will not accidentally
     * disrupt their UI.
     *
     * Race condition if not synchronized. Only one update should be applied at
     * a time.
     *
     * @param newProps New file property instances to be updated in the current
     *                 sheet.
     */
    protected synchronized void updateSheet(NodeProperty<?>... newProps) {
        SwingUtilities.invokeLater(() -> {
            /*
             * Refresh ONLY those properties in the sheet currently. Subclasses
             * may have only added a subset of our properties or their own
             * properties.
             */
            Sheet visibleSheet = this.getSheet();
            Sheet.Set visibleSheetSet = visibleSheet.get(Sheet.PROPERTIES);
            Property<?>[] visibleProps = visibleSheetSet.getProperties();
            for (NodeProperty<?> newProp : newProps) {
                for (int i = 0; i < visibleProps.length; i++) {
                    if (visibleProps[i].getName().equals(newProp.getName())) {
                        visibleProps[i] = newProp;
                    }
                }
            }
            visibleSheetSet.put(visibleProps);
            visibleSheet.put(visibleSheetSet);
            //setSheet() will notify Netbeans to update this node in the UI.
            this.setSheet(visibleSheet);
        });
    }

    /**
     * Reads and returns a list of all tags associated with this content node.
     *
     * @return list of tags associated with the node.
     */
    abstract protected List<Tag> getAllTagsFromDatabase();

    /**
     * Returns Score property for the node.
     *
     * @return Score property for the underlying content of the node.
     */
    @Messages({
        "# {0} - significanceDisplayName",
        "AbstractContentNode_getScorePropertyAndDescription_description=Has an {0} analysis result score"
    })
    protected Pair<Score, String> getScorePropertyAndDescription() {
        Score score = Score.SCORE_UNKNOWN;
        try {
            score = this.content.getAggregateScore();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Unable to get aggregate score for content with id: " + this.content.getId(), ex);
        }

        String significanceDisplay = score.getSignificance().getDisplayName();
        String description = Bundle.AbstractContentNode_getScorePropertyAndDescription_description(significanceDisplay);
        return Pair.of(score, description);
    }

    /**
     * Returns comment property for the node.
     *
     * Default implementation is a null implementation.
     *
     * @param tags       The list of tags.
     * @param attributes The list of correlation attribute instances.
     *
     * @return Comment property for the underlying content of the node.
     */
    protected DataResultViewerTable.HasCommentStatus getCommentProperty(List<Tag> tags, List<CorrelationAttributeInstance> attributes) {
        return DataResultViewerTable.HasCommentStatus.NO_COMMENT;
    }

    /**
     * Returns occurrences/count property for the node.
     *
     * Default implementation is a null implementation.
     *
     * @param attribute          The correlation attribute for which data will
     *                           be retrieved.
     * @param defaultDescription A description to use when none is determined by
     *                           the getCountPropertyAndDescription method.
     *
     * @return count property for the underlying content of the node.
     */
    protected Pair<Long, String> getCountPropertyAndDescription(CorrelationAttributeInstance attribute, String defaultDescription) {
        return Pair.of(-1L, NO_DESCR);
    }
}
