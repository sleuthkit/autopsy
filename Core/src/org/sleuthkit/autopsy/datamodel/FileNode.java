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
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * This class is used to represent the "Node" for the file. It may have derived
 * files children.
 */
public class FileNode extends AbstractFsContentNode<AbstractFile> {

    /**
     * @param file underlying Content
     */
    public FileNode(AbstractFile file) {
        this(file, true);

        setIcon(file);
    }

    public FileNode(AbstractFile file, boolean directoryBrowseMode) {
        super(file, directoryBrowseMode);

        setIcon(file);
    }

    private void setIcon(AbstractFile file) {
        // set name, display name, and icon
        if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
            if (file.getType().equals(TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-icon-16.png");
            } else {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
            }
        } else {
            this.setIconBaseWithExtension(getIconForFileType(file));
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
        List<Action> actionsList = new ArrayList<>();        
        if (!this.getDirectoryBrowseMode()) {
            actionsList.add(new ViewContextAction(NbBundle.getMessage(this.getClass(), "FileNode.viewFileInDir.text"), this));
            actionsList.add(null); // creates a menu separator
        }
        actionsList.add(new NewWindowViewAction(
                NbBundle.getMessage(this.getClass(), "FileNode.getActions.viewInNewWin.text"), this));
        actionsList.add(new ExternalViewerAction(
                NbBundle.getMessage(this.getClass(), "FileNode.getActions.openInExtViewer.text"), this));
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(new HashSearchAction(
                NbBundle.getMessage(this.getClass(), "FileNode.getActions.searchFilesSameMD5.text"), this));
        actionsList.add(null); // creates a menu separator        
        actionsList.add(AddContentTagAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());        
        return actionsList.toArray(new Action[0]);
    }

    @Override
    public <T> T accept(ContentNodeVisitor< T> v) {
        return v.visit(this);
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor< T> v) {
        return v.visit(this);
    }

    // Given a file, returns the correct icon for said
    // file based off it's extension
    static String getIconForFileType(AbstractFile file) {
        // Get the name, extension
        String name = file.getName();
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex == -1) {
            return "org/sleuthkit/autopsy/images/file-icon.png";
        }
        String ext = name.substring(dotIndex).toLowerCase();

        // Images
        for (String s : FileTypeExtensions.getImageExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/image-file.png";
            }
        }
        // Videos
        for (String s : FileTypeExtensions.getVideoExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/video-file.png";
            }
        }
        // Audio Files
        for (String s : FileTypeExtensions.getAudioExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/audio-file.png";
            }
        }
        // Documents
        for (String s : FileTypeExtensions.getDocumentExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/doc-file.png";
            }
        }
        // Executables / System Files
        for (String s : FileTypeExtensions.getExecutableExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/exe-file.png";
            }
        }
        // Text Files
        for (String s : FileTypeExtensions.getTextExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/text-file.png";
            }
        }
        // Web Files
        for (String s : FileTypeExtensions.getWebExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/web-file.png";
            }
        }
        // PDFs
        for (String s : FileTypeExtensions.getPDFExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/pdf-file.png";
            }
        }
        // Archives
        for (String s : FileTypeExtensions.getArchiveExtensions()) {
            if (ext.equals(s)) {
                return "org/sleuthkit/autopsy/images/archive-file.png";
            }
        }
        // Else return the default
        return "org/sleuthkit/autopsy/images/file-icon.png";

    }

    @Override
    public boolean isLeafTypeNode() {
        return true;
    }
}