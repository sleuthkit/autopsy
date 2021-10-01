/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A Node for a LocalFile or DerivedFile content object.
 */
public class LocalFileNode extends AbstractAbstractFileNode<AbstractFile> {

    private static final Logger logger = Logger.getLogger(LocalFileNode.class.getName());

    public LocalFileNode(AbstractFile af) {
        super(af);

        this.setDisplayName(af.getName());

        // set name, display name, and icon
        if (af.isDir()) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/Folder-icon.png"); //NON-NLS
        } else {
            this.setIconBaseWithExtension(FileNode.getIconForFileType(af));
        }

    }

    @Override
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();

        actionsList.add(new ViewContextAction(NbBundle.getMessage(this.getClass(), "LocalFileNode.viewFileInDir.text"), this.content));
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "LocalFileNode.getActions.viewInNewWin.text"), this));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(
                    NbBundle.getMessage(this.getClass(), "LocalFileNode.getActions.openInExtViewer.text"), this));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator

        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());

        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }

        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        if (FileTypeExtensions.getArchiveExtensions().contains("." + this.content.getNameExtension().toLowerCase())) {
            try {
                if (this.content.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0) {
                    actionsList.add(new ExtractArchiveWithPasswordAction(this.getContent()));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.WARNING, "Unable to add unzip with password action to context menus", ex);
            }
        }
        
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

    @Override
    public boolean isLeafTypeNode() {
        // This seems wrong, but it also seems that it is never called
        // because the visitor to figure out if there are children or 
        // not will check if it has children using the Content API
        return true; //!this.hasContentChildren();
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
