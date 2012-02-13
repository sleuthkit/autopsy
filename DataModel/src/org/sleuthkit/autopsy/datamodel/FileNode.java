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
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.TskData;

/**
 * This class is used to represent the "Node" for the file.
 * It has no children.
 *
 */
public class FileNode extends AbstractFsContentNode<File> {

    /**
     * Helper so that the display name and the name used in building the path
     * are determined the same way.
     * @param f File to get the name of
     * @return short name for the File
     */
    static String nameForFile(File f) {
        return f.getName();
    }

    /**
     * 
     * @param file underlying Content
     */
    public FileNode(File file) {
        super(file);

        // set name, display name, and icon
        String fileName = nameForFile(file);
        this.setDisplayName(fileName);
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
