/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.autopsy.modules.embeddedfileextractor.ExtractArchiveWithPasswordAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * A node for representing an AbstractFile.
 */
public class FileNode extends AbstractNode {

    /**
     * Gets the path to the icon file that should be used to visually represent
     * an AbstractFile, using the file name extension to select the icon.
     *
     * @param file An AbstractFile.
     *
     * @return An icon file path.
     */
    static String getIconForFileType(ExtensionMediaType fileType) {
        if (fileType == null) {
            return "org/sleuthkit/autopsy/images/file-icon.png";
        }

        switch (fileType) {
            case IMAGE:
                return "org/sleuthkit/autopsy/images/image-file.png";
            case VIDEO:
                return "org/sleuthkit/autopsy/images/video-file.png";
            case AUDIO:
                return "org/sleuthkit/autopsy/images/audio-file.png";
            case DOC:
                return "org/sleuthkit/autopsy/images/doc-file.png";
            case EXECUTABLE:
                return "org/sleuthkit/autopsy/images/exe-file.png";
            case TEXT:
                return "org/sleuthkit/autopsy/images/text-file.png";
            case WEB:
                return "org/sleuthkit/autopsy/images/web-file.png";
            case PDF:
                return "org/sleuthkit/autopsy/images/pdf-file.png";
            case ARCHIVE:
                return "org/sleuthkit/autopsy/images/archive-file.png";
            default:
            case UNCATEGORIZED:
                return "org/sleuthkit/autopsy/images/file-icon.png";
        }
    }
    
    private final boolean directoryBrowseMode;
    private final FileRowDTO fileData;
    private final List<ColumnKey> columns;


    public FileNode(SearchResultsDTO results, FileRowDTO file) {
        this(results, file, true);
    }


    public FileNode(SearchResultsDTO results, FileRowDTO file, boolean directoryBrowseMode) {
        // GVDTODO: at some point, this leaf will need to allow for children
        super(Children.LEAF, ContentNodeUtil.getLookup(file.getAbstractFile()));
        setIcon(file);
        setDisplayName(ContentNodeUtil.getContentDisplayName(file.getFileName()));
        setName(ContentNodeUtil.getContentName(file.getId()));
        this.directoryBrowseMode = directoryBrowseMode;
        this.fileData = file;
        this.columns = results.getColumns();
    }

    /*
     * Sets the icon for the node, based on properties of the AbstractFile.
     */
    private void setIcon(FileRowDTO file) {
        if (!file.getAllocated()) {
            if (TSK_DB_FILES_TYPE_ENUM.CARVED.equals(file.getFileType())) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
            } else {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
            }
        } else {
            this.setIconBaseWithExtension(getIconForFileType(file.getExtensionMediaType()));
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
        "FileNodev2.getActions.viewFileInDir.text=View File in Directory",
        "FileNodev2.getActions.viewInNewWin.text=View Item in New Window",
        "FileNodev2.getActions.openInExtViewer.text=Open in External Viewer  Ctrl+E",
        "FileNodev2.getActions.searchFilesSameMD5.text=Search for files with the same MD5 hash"})
    public Action[] getActions(boolean context) {
        List<Action> actionsList = new ArrayList<>();

        // GVDTODO: action requires node
//        if (!this.directoryBrowseMode) {
//            actionsList.add(new ViewContextAction(Bundle.FileNodev2_getActions_viewFileInDir_text(), this));
//        }


        actionsList.add(ViewFileInTimelineAction.createViewFileAction(this.fileData.getAbstractFile()));
        actionsList.add(null); // Creates an item separator

        actionsList.add(new NewWindowViewAction(Bundle.FileNodev2_getActions_viewInNewWin_text(), this));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(
                    Bundle.FileNodev2_getActions_openInExtViewer_text(), this));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }

        actionsList.add(null); // Creates an item separator

        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // Creates an item separator

        actionsList.add(AddContentTagAction.getInstance());
        if (1 == selectedFilesList.size()) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        
        // GVDTODO: HANDLE THIS ACTION IN A BETTER WAY!-----
        // See JIRA-8099
        AbstractFile file = this.fileData.getAbstractFile();
        boolean isArchive = FileTypeExtensions.getArchiveExtensions().contains("." + file.getNameExtension().toLowerCase());
        boolean encryptionDetected = false;
        try {
            encryptionDetected = isArchive && file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0;
        } catch (TskCoreException ex) {
            // TODO
        }
        if (encryptionDetected) {
            actionsList.add(new ExtractArchiveWithPasswordAction(this.fileData.getAbstractFile()));
        }
        //------------------------------------------------

        actionsList.add(null);
        actionsList.addAll(Arrays.asList(super.getActions(true)));

        return actionsList.toArray(new Action[actionsList.size()]);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), this.columns, this.fileData.getCellValues());
    }
}
