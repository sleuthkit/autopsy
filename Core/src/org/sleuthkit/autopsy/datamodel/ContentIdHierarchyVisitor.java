/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2012 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.TskException;
import org.sleuthkit.datamodel.VolumeSystem;


/**
 * Returns all the children Ids of a Content object, descending the hierarchy
 * past subclasses that aren't part of the exposed hierarchy (VolumeSystem,
 * FileSystem, and root Directories)
 */
public class ContentIdHierarchyVisitor extends ContentVisitor.Default<List<? extends Long>> {
    private static final Logger logger = Logger.getLogger(ContentHierarchyVisitor.class.getName());
    private static final ContentIdHierarchyVisitor INSTANCE = new ContentIdHierarchyVisitor();
    
    private ContentIdHierarchyVisitor() {}

    /**
     * Get the child Content objects according the the exposed hierarchy.
     * @param parent
     * @return 
     */
    public static List<Long> getChildren(Content parent) {
        List<Long> keys = new ArrayList<Long>();

        List<Content> children;

        try {
            children = parent.getChildren();
        } catch (TskException ex) {
            logger.log(Level.WARNING, "Error getting Content children.", ex);
            children = Collections.emptyList();
        }

        for (Content c : children) {
            keys.addAll(c.accept(INSTANCE));
        }

        return keys;
    }

    @Override
    protected List<Long> defaultVisit(Content c) {
        return Collections.singletonList(c.getId());
    }

    @Override
    public List<Long> visit(VolumeSystem vs) {
        return getChildren(vs);
    }

    @Override
    public List<Long> visit(FileSystem fs) {
        return getChildren(fs);
    }

    @Override
    public List<? extends Long> visit(Directory dir) {
        if (dir.isRoot()) {
            return getChildren(dir);
        } else {
            return Collections.singletonList(dir.getId());
        }
    }
    
    @Override
    public List<? extends Long> visit(VirtualDirectory ldir) {
        //return getChildren(ldir);
        return Collections.singletonList(ldir.getId());
    }
}