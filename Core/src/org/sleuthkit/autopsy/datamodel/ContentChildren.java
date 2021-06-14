/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.VolumeSystem;

/**
 * Makes the children nodes / keys for a given content object. Has knowledge
 * about the structure of the directory tree and what levels should be ignored.
 */
class ContentChildren extends AbstractContentChildren<Content> {

    private static final Logger logger = Logger.getLogger(ContentChildren.class.getName());

    private final Content parent;

    ContentChildren(Content parent) {
        super("content_" + Long.toString(parent.getId()));
        this.parent = parent;
    }

    /**
     * Get the children of the Content object based on what we want to display.
     * As an example, we don't display the direct children of VolumeSystems or
     * FileSystems. We hide some of the levels in the tree. This method takes
     * care of that and returns the children we want to display
     *
     * @param parent
     *
     * @return
     */
    private static List<Content> getDisplayChildren(Content parent) {
        // what does the content think its children are?
        List<Content> tmpChildren;
        try {
            tmpChildren = parent.getChildren();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Error getting Content children.", ex); //NON-NLS
            tmpChildren = Collections.emptyList();
        }

        // Cycle through the list and make a new one based
        // on what we actually want to display. 
        List<Content> children = new ArrayList<>();
        for (Content c : tmpChildren) {
            if (c instanceof VolumeSystem) {
                children.addAll(getDisplayChildren(c));
            } else if (c instanceof FileSystem) {
                children.addAll(getDisplayChildren(c));
            } else if (c instanceof Directory) {
                Directory dir = (Directory) c;
                /*
                 * For root directories, we want to return their contents.
                 * Special case though for '.' and '..' entries, because they
                 * should not have children (and in fact don't in the DB). Other
                 * drs get treated as files and added as is.
                 */
                if ((dir.isRoot()) && (dir.getName().equals(".") == false)
                        && (dir.getName().equals("..") == false)) {
                    children.addAll(getDisplayChildren(dir));
                } else {
                    children.add(c);
                }
            } else if (c instanceof LocalDirectory) {
                LocalDirectory localDir = (LocalDirectory) c;
                if (localDir.isRoot()) {
                    children.addAll(getDisplayChildren(localDir));
                } else {
                    children.add(c);
                }
            } else {
                children.add(c);
            }
        }
        return children;
    }

    @Override
    protected List<Content> makeKeys() {
        List<Content> contentList = getDisplayChildren(parent);
        
        // Call the getUniquePath method to cache the value for future use
        // in the EDT
        contentList.forEach(content->{
            try {
                content.getUniquePath();
            } catch (TskCoreException ex) {
                 logger.log(Level.SEVERE, String.format("Failed attempt to cache the "
                    + "unique path of the abstract file instance. Name: %s (objID=%d)",
                    content.getName(), content.getId()), ex);
            }
        });
        
        return contentList;
    }

    @Override
    protected void onAdd() {
        // No-op
    }

    /**
     * Refresh the list of children due to a change in one (or more) of our
     * children (e.g. archive files can change as new content is extracted from
     * them).
     */
    void refreshChildren() {
        refresh(true);
    }

    @Override
    protected void onRemove() {
        // No-op
    }
}
