/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2013 Basis Technology Corp.
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
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * This class is used to represent the "Node" for the directory. Its children
 * are more directories.
 */
public class DirectoryNode extends AbstractFsContentNode<AbstractFile> {

    public static final String DOTDOTDIR = NbBundle.getMessage(DirectoryNode.class, "DirectoryNode.parFolder.text");
    public static final String DOTDIR = NbBundle.getMessage(DirectoryNode.class, "DirectoryNode.curFolder.text");

    public DirectoryNode(Directory dir) {
        this(dir, true);

        setIcon(dir);
    }

    public DirectoryNode(AbstractFile dir, boolean directoryBrowseMode) {
        super(dir, directoryBrowseMode);

        setIcon(dir);
    }

    private void setIcon(AbstractFile dir) {
        // set name, display name, and icon
        if (dir.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/folder-icon-deleted.png"); //NON-NLS
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS
        }
    }

    /**
     * Right click action for this node
     *
     * @param popup
     *
     * @return
     */
    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actions = new ArrayList<>();
        for (Action a : super.getActions(true)) {
            actions.add(a);
        }
        if (!getDirectoryBrowseMode()) {
            actions.add(new ViewContextAction(
                    NbBundle.getMessage(this.getClass(), "DirectoryNode.getActions.viewFileInDir.text"), this));
            actions.add(null); // creates a menu separator
        }
        actions.add(new NewWindowViewAction(NbBundle.getMessage(this.getClass(), "DirectoryNode.viewInNewWin.text"), this));
        actions.add(ViewFileInTimelineAction.createViewFileAction(getContent()));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions.toArray(new Action[actions.size()]);
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
    public boolean isLeafTypeNode() {
        return false;
    }

    /*
     * TODO (AUT-1849): Correct or remove peristent column reordering code
     *
     * Added to support this feature.
     */
//    @Override
//    public String getItemType() {
//        return "Directory"; //NON-NLS
//    }
}
