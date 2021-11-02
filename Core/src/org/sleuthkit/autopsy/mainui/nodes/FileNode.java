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

import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * A node for representing an AbstractFile.
 */
public class FileNode extends AbstractNode implements ActionContext {

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

    @Override
    public boolean supportsViewInTimeline() {
        return true;
    }

    @Override
    public Optional<AbstractFile> getFileForViewInTimelineAction() {
        return Optional.of(fileData.getAbstractFile());
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public Optional<Node> getExternalViewerActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsExtractActions() {
        return true;
    }

    @Override
    public boolean supportsContentTagAction() {
        return true;
    }

    @Override
    public Optional<AbstractFile> getExtractArchiveWithPasswordActionFile() {
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

        return encryptionDetected ? Optional.of(fileData.getAbstractFile()) : Optional.empty();
    }

    @Override
    public Action[] getActions(boolean context) {
        return ActionsFactory.getActions(this);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), this.columns, this.fileData.getCellValues());
    }
}
