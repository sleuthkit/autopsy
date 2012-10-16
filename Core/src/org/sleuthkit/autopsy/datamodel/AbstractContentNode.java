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

import org.openide.nodes.AbstractNode;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 * Interface class that all Data nodes inherit from.
 * Provides basic information such as ID, parent ID, etc.
 * @param <T> type of wrapped Content
 */
abstract class AbstractContentNode<T extends Content> extends ContentNode {
    /**
     * Underlying Sleuth Kit Content object
     */
    T content;
    
    /**
     * Handles aspects that depend on the Content object
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
     * Reads the content of this node.
     *
     * @param offset  the starting offset
     * @param len     the length
     * @return        the bytes
     * @throws TskException
     */
    public int read(byte[] buf, long offset, long len) throws TskException {
        return content.read(buf, offset, len);
    }
}
