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
package org.sleuthkit.autopsy.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.lookup.Lookups;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;
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
    T content;
    private static final Logger logger = Logger.getLogger(AbstractContentNode.class.getName());

    /**
     * Handles aspects that depend on the Content object
     *
     * @param content Underlying Content instances
     */
    AbstractContentNode(T content) {
        this(content, Lookups.singleton(content) );
    }

    /**
     * Handles aspects that depend on the Content object
     *
     * @param content Underlying Content instances
     * @param lookup   The Lookup object for the node.
     */
    AbstractContentNode(T content, Lookup lookup) {
         //TODO consider child factory for the content children
        super(new ContentChildren(content), lookup);
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
     * @return true if has children
     */
    public static boolean contentHasVisibleContentChildren(Content c){
        if (c != null) {
            String query = "SELECT COUNT(obj_id) AS count FROM "
 			+ " ( SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + c.getId() + " AND type = " 
                        +       TskData.ObjectType.ARTIFACT.getObjectType()
 			+ "   INTERSECT SELECT artifact_obj_id FROM blackboard_artifacts WHERE obj_id = " + c.getId()
 			+ "     AND (artifact_type_id = " + ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID() 
                        +          " OR artifact_type_id = " + ARTIFACT_TYPE.TSK_MESSAGE.getTypeID() + ") "
 			+ "   UNION SELECT obj_id FROM tsk_objects WHERE par_obj_id = " + c.getId()
                        + "     AND type = " + TskData.ObjectType.ABSTRACTFILE.getObjectType() + ") AS OBJECT_IDS"; //NON-NLS;
  
            
            try (SleuthkitCase.CaseDbQuery dbQuery = Case.getOpenCase().getSleuthkitCase().executeQuery(query)) {
                ResultSet resultSet = dbQuery.getResultSet();
                if(resultSet.next()){
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
}
