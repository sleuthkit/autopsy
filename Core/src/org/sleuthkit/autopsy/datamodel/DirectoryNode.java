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

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.TagAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * This class is used to represent the "Node" for the directory. Its children
 * are more directories.
 */
public class DirectoryNode extends AbstractFsContentNode<Directory> {

    public static final String DOTDOTDIR = "[parent folder]";
    public static final String DOTDIR = "[current folder]";

    public DirectoryNode(Directory dir) {
        this(dir, true);

        setIcon(dir);
    }

    public DirectoryNode(Directory dir, boolean directoryBrowseMode) {
        super(dir, directoryBrowseMode);

        setIcon(dir);
    }

    private void setIcon(Directory dir) {
        // set name, display name, and icon
        if (dir.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-deleted.png");
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png");
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
        List<Action> actions = new ArrayList<Action>();
        if (!getDirectoryBrowseMode()) {
            actions.add(new ViewContextAction("View File in Directory", this));
            actions.add(null); // creates a menu separator
        }
        actions.add(new NewWindowViewAction("View in New Window", this));
        actions.add(null); // creates a menu separator
        actions.add(new ExtractAction("Extract Directory", this));
        actions.add(null); // creates a menu separator
        actions.add(new TagAction(this));
        return actions.toArray(new Action[0]);
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> v) {
        return v.visit(this);
    }

    @Override
    public TYPE getDisplayableItemNodeType() {
        return TYPE.CONTENT;
    }
}
