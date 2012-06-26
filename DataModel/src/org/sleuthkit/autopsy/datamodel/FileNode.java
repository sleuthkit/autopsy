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

import javax.swing.Action;
import org.openide.nodes.Sheet;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.TskData;

/**
 * This class is used to represent the "Node" for the file.
 * It has no children.
 *
 */
public class FileNode extends AbstractFsContentNode<File> {

    /**
     * 
     * @param file underlying Content
     */
    public FileNode(File file) {
        this(file, true);
    }
    
    public FileNode(File file, boolean directoryBrowseMode) {
        super(file, directoryBrowseMode);
        
        // set name, display name, and icon
        if (File.dirFlagToValue(file.getDir_flags()).equals(TskData.TSK_FS_NAME_FLAG_ENUM.TSK_FS_NAME_FLAG_UNALLOC.toString())) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon.png");
        }
    }

    /**
     * Right click action for this node
     *
     * @param popup
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        return new Action[]{};
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }
}
