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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * This class is the Node for an AbstractFile. It may have derived files
 * children.
 */
public class SlackFileNode extends AbstractFsContentNode<AbstractFile> {

    /**
     * Constructor
     *
     * @param file underlying Content
     */
    public SlackFileNode(AbstractFile file) {
        this(file, true);

        setIcon(file);
    }

    public SlackFileNode(AbstractFile file, boolean directoryBrowseMode) {
        super(file, directoryBrowseMode);

        setIcon(file);
    }

    private void setIcon(AbstractFile file) {
        // set name, display name, and icon
        if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            if (file.getType().equals(TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
            } else {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
            }
        } else {
            this.setIconBaseWithExtension(getIconForFileType(file));
        }
    }

    @Override
    public Action[] getActions(boolean popup) {
        List<Action> actionsList = new ArrayList<>();
        if (!this.getDirectoryBrowseMode()) {
            actionsList.add(new ViewContextAction(NbBundle.getMessage(this.getClass(), "SlackFileNode.getActions.viewFileInDir.text"), this.content));
            actionsList.add(null); // creates a menu separator
        }
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "SlackFileNode.getActions.viewInNewWin.text"), this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator        
        actionsList.add(AddContentTagAction.getInstance());
        
        final Collection<AbstractFile> selectedFilesList =
                new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if(selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        actionsList.add(null);
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    // Given a file, returns the correct icon for said
    // file based off it's extension
    static String getIconForFileType(AbstractFile file) {

            return "org/sleuthkit/autopsy/images/file-icon.png"; //NON-NLS
    }

    @Override
    public boolean isLeafTypeNode() {
        // This seems wrong, but it also seems that it is never called
        // because the visitor to figure out if there are children or 
        // not will check if it has children using the Content API
        return true;
    }
    
    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
