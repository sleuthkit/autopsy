/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import org.apache.commons.lang3.StringUtils;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * A node for representing an AbstractFile. It may have derived file node
 * children.
 */
public class FileNode extends AbstractFsContentNode<AbstractFile> {

    /**
     * Gets the path to the icon file that should be used to visually represent
     * an AbstractFile, using the file name extension to select the icon.
     *
     * @param file An AbstractFile.
     *
     * @return An icon file path.
     */
    static String getIconForFileType(AbstractFile file) {
        String ext = file.getNameExtension();
        if (StringUtils.isBlank(ext)) {
            return "org/sleuthkit/autopsy/images/file-icon.png"; //NON-NLS
        } else {
            ext = "." + ext;
        }
        if (FileTypeExtensions.getImageExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/image-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getVideoExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/video-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getAudioExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/audio-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getDocumentExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/doc-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getExecutableExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/exe-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getTextExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/text-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getWebExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/web-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getPDFExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/pdf-file.png"; //NON-NLS
        }
        if (FileTypeExtensions.getArchiveExtensions().contains(ext)) {
            return "org/sleuthkit/autopsy/images/archive-file.png"; //NON-NLS
        }
        return "org/sleuthkit/autopsy/images/file-icon.png"; //NON-NLS
    }

    /**
     * Constructs a node for representing an AbstractFile. It may have derived
     * file node children.
     *
     * @param file An AbstractFile object.
     */
    public FileNode(AbstractFile file) {
        this(file, true);
        setIcon(file);
    }

    /**
     * Constructs a node for representing an AbstractFile. It may have derived
     * file node children.
     *
     * @param file                An AbstractFile object.
     * @param directoryBrowseMode
     */
    public FileNode(AbstractFile file, boolean directoryBrowseMode) {
        super(file, directoryBrowseMode);
        setIcon(file);
    }

    /*
     * Sets the icon for the node, based on properties of the AbstractFile.
     */
    private void setIcon(AbstractFile file) {
        if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            if (file.getType().equals(TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-icon-16.png"); //NON-NLS
            } else {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
            }
        } else {
            this.setIconBaseWithExtension(getIconForFileType(file));
        }
    }

    /**
     * Gets the set of actions that are associated with this node. This set is
     * used to construct the context menu for the node.
     *
     * @param context Whether to find actions for context meaning or for the
     *                node itself.
     *
     * @return An array of the actions.
     */
    @Override
    @NbBundle.Messages({
        "FileNode.getActions.viewFileInDir.text=View File in Directory",
        "FileNode.getActions.viewInNewWin.text=View in New Window",
        "FileNode.getActions.openInExtViewer.text=Open in External Viewer",
        "FileNode.getActions.searchFilesSameMD5.text=Search for files with the same MD5 hash"})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.addAll(Arrays.asList(super.getActions(true)));
        
        if (!this.getDirectoryBrowseMode()) {
            actionsList.add(new ViewContextAction(Bundle.FileNode_getActions_viewFileInDir_text(), this));
            actionsList.add(null); // Creates an item separator
        }

        actionsList.add(new NewWindowViewAction(Bundle.FileNode_getActions_viewInNewWin_text(), this));
        actionsList.add(new ExternalViewerAction(Bundle.FileNode_getActions_openInExtViewer_text(), this));
        actionsList.add(ViewFileInTimelineAction.createViewFileAction(getContent()));
        actionsList.add(null); // Creates an item separator

        actionsList.add(ExtractAction.getInstance());
        actionsList.add(new HashSearchAction(Bundle.FileNode_getActions_searchFilesSameMD5_text(), this));
        actionsList.add(null); // Creates an item separator

        actionsList.add(AddContentTagAction.getInstance());
        final Collection<AbstractFile> selectedFilesList = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (1 == selectedFilesList.size()) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList.toArray(new Action[actionsList.size()]);
    }

    /**
     * Accepts a ContentNodeVisitor.
     *
     * @param <T>     The type parameter of the Visitor.
     * @param visitor The Visitor.
     *
     * @return An object determied by the type parameter of the Visitor.
     */
    @Override
    public <T> T accept(ContentNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Accepts a DisplayableItemNodeVisitor.
     *
     * @param <T>     The type parameter of the Visitor.
     * @param visitor The Visitor.
     *
     * @return An object determied by the type parameter of the Visitor.
     */
    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Indicates whether or not the node is capable of having child nodes.
     * Should only return true if the node is ALWAYS a leaf node.
     *
     * @return True or false.
     */
    @Override
    public boolean isLeafTypeNode() {
        /*
         * A FileNode may have FileNodes for derived files as children.
         */
        return false;
    }

    /**
     * Gets the item type string of the node, suitable for use as a key.
     *
     * @return A String representing the item type of node.
     */
    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
