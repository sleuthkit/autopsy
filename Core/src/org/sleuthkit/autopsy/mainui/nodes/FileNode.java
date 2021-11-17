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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.ExtensionMediaType;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.LayoutFileRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.FileRowDTO.SlackFileRowDTO;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.mainui.sco.SCOFetcher;
import org.sleuthkit.autopsy.mainui.sco.SCOSupporter;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;
import org.sleuthkit.datamodel.TskData.TSK_FS_NAME_FLAG_ENUM;

/**
 * A node for representing an AbstractFile.
 */
public class FileNode extends BaseNode<SearchResultsDTO, FileRowDTO> implements SCOSupporter {

    private static final Logger logger = Logger.getLogger(FileNode.class.getName());

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
        super(Children.LEAF, ContentNodeUtil.getLookup(file.getAbstractFile()), results, file);
        setIcon(file);
        setDisplayName(ContentNodeUtil.getContentDisplayName(file.getFileName()));
        setName(ContentNodeUtil.getContentName(file.getId()));
        setShortDescription(ContentNodeUtil.getContentDisplayName(file.getFileName()));
        this.directoryBrowseMode = directoryBrowseMode;
        this.fileData = file;
        this.columns = results.getColumns();
    }

    /*
     * Sets the icon for the node, based on properties of the AbstractFile.
     */
    void setIcon(FileRowDTO fileData) {
        if (!fileData.getAllocated()) {
            if (TSK_DB_FILES_TYPE_ENUM.CARVED.equals(fileData.getFileType())) {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
            } else {
                this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
            }
        } else {
            this.setIconBaseWithExtension(getIconForFileType(fileData.getExtensionMediaType()));
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
    public Optional<AbstractFile> getFileForDirectoryBrowseMode() {
        if (directoryBrowseMode) {
            return Optional.of(fileData.getAbstractFile());
        }

        return Optional.empty();
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
        Sheet sheet = super.createSheet();
        backgroundTasksPool.submit(new SCOFetcher<>(new WeakReference<>(this)));
        return sheet;
    }

    @Override
    public Optional<Content> getContent() {
        return Optional.ofNullable(fileData.getAbstractFile());
    }

    @Override
    public void updateSheet(List<NodeProperty<?>> newProps) {
        super.updateSheet(newProps);
    }

    @Override
    public Optional<List<Tag>> getAllTagsFromDatabase() {
        try {
            List<ContentTag> contentTags = ContentNodeUtil.getContentTagsFromDatabase(fileData.getAbstractFile());
            if (!contentTags.isEmpty()) {
                List<Tag> tags = new ArrayList<>();
                tags.addAll(contentTags);
                return Optional.of(tags);
            }

        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get content tags from database for AbstractFile id=" + fileData.getAbstractFile().getId(), ex);
        }
        return Optional.empty();
    }

    /**
     * A node for representing a LayoutFile.
     */
    public static class LayoutFileNode extends FileNode {

        private final LayoutFileRowDTO layoutFileRow;

        public LayoutFileNode(SearchResultsDTO results, LayoutFileRowDTO file) {
            super(results, file, true);
            layoutFileRow = file;
        }

        @Override
        void setIcon(FileRowDTO fileData) {
            LayoutFile lf = ((LayoutFileRowDTO) fileData).getLayoutFile();
            switch (lf.getType()) {
                case CARVED:
                    setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png");
                    break;
                case LAYOUT_FILE:
                    if (lf.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                        setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
                    } else {
                        setIconBaseWithExtension(getIconForFileType(layoutFileRow.getExtensionMediaType()));
                    }
                    break;
                default:
                    setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png");
            }
        }
    }

    /**
     * A node for representing a SlackFile.
     */
    public static class SlackFileNode extends FileNode {

        public SlackFileNode(SearchResultsDTO results, SlackFileRowDTO file) {
            super(results, file);
        }

        @Override
        void setIcon(FileRowDTO fileData) {
            AbstractFile file = fileData.getAbstractFile();
            if (file.isDirNameFlagSet(TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                if (file.getType().equals(TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                    this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/carved-file-x-icon-16.png"); //NON-NLS
                } else {
                    this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/file-icon-deleted.png"); //NON-NLS
                }
            } else {
                this.setIconBaseWithExtension(getIconForFileType(fileData.getExtensionMediaType()));
            }
        }
    }
}
