/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.util.List;
import java.util.logging.Level;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskException;

/**
 * Interface class that all Data nodes inherit from. Provides basic information
 * such as ID, parent ID, etc.
 *
 * @param <T> type of wrapped Content
 */
abstract class AbstractContentNode<T extends Content> extends ContentNode {

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
        //TODO consider child factory for the content children
        super(new ContentChildren(content), Lookups.singleton(content));
        this.content = content;
        super.setName(ContentUtils.getSystemName(content));
    }

    @Override
    public void setName(String name) {
        throw new UnsupportedOperationException("Can't change the system name.");
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
    public boolean hasContentChildren() {
        boolean hasChildren = false;

        if (content != null) {
            try {
                hasChildren = content.hasChildren();
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error checking if the node has children, for content: " + content, ex);
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
                logger.log(Level.SEVERE, "Error getting children ids, for content: " + content, ex);
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
                logger.log(Level.SEVERE, "Error getting children, for content: " + content, ex);
            }
        }

        return children;

    }

    /**
     * Reads the content of this node.
     *
     * @param offset the starting offset
     * @param len the length
     * @return the bytes
     * @throws TskException
     */
    public int read(byte[] buf, long offset, long len) throws TskException {
        return content.read(buf, offset, len);
    }
}
