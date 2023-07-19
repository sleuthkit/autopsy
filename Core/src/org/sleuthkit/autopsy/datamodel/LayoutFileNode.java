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
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Node for layout file
 */
public class LayoutFileNode extends AbstractAbstractFileNode<LayoutFile> {
    
    private static final Logger logger = Logger.getLogger(LayoutFileNode.class.getName());

    @Deprecated
    public static enum LayoutContentPropertyType {

        PARTS {
            @Override
            public String toString() {
                return NbBundle.getMessage(this.getClass(), "LayoutFileNode.propertyType.parts");
            }
        }
    }

    public static String nameForLayoutFile(LayoutFile lf) {
        return lf.getName();
    }

    public LayoutFileNode(LayoutFile lf) {
        super(lf);

        this.setDisplayName(nameForLayoutFile(lf));

        if (lf.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED)) {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
        } else if (lf.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.LAYOUT_FILE)) {
            if (lf.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
            } else {
                this.setIconBaseWithExtension(FileNode.getIconForFileType(lf));
            }
        } else {
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
        }
    }

    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    @NbBundle.Messages({
        "LayoutFileNode.getActions.viewFileInDir.text=View File in Directory"})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction(Bundle.LayoutFileNode_getActions_viewFileInDir_text(), this));
        actionsList.add(null); // Creates an item separator
        
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "LayoutFileNode.getActions.viewInNewWin.text"), this));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(
                    NbBundle.getMessage(this.getClass(), "LayoutFileNode.getActions.openInExtViewer.text"), this));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(ViewFileInTimelineAction.createViewFileAction(getContent()));
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
    public String getItemType() {
        return getClass().getName();
    }

}
