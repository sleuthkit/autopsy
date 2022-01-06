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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.utils.FileNameTransTask;
import org.sleuthkit.autopsy.directorytree.ExtractUnallocAction;
import org.sleuthkit.autopsy.directorytree.FileSystemDetailsAction;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemContentSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemDAO.FileSystemTreeEvent;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemDAO.FileSystemTreeItem;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemDAO.TreeContentType;
import org.sleuthkit.autopsy.mainui.datamodel.MediaTypeUtils;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.CARVED_FILE;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.DELETED_FILE;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.DELETED_FOLDER;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.FOLDER;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.autopsy.texttranslation.TextTranslationService;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;

/**
 * Factory for displaying content in the data source section of the tree.
 */
public class FileSystemFactory extends TreeChildFactory<FileSystemContentSearchParam> {

    private static final Logger logger = Logger.getLogger(FileSystemFactory.class.getName());

    private Long contentId = null;
    private Host host = null;

    /**
     * Create a factory for a given parent content ID.
     *
     * @param contentId The object ID for this node
     */
    public FileSystemFactory(Long contentId) {
        this.contentId = contentId;
    }

    /**
     * Create a factory for a given parent Host.
     *
     * @param host The parent host for this node
     */
    public FileSystemFactory(Host host) {
        this.host = host;
    }

    @Override
    protected TreeResultsDTO<? extends FileSystemContentSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
        if (host == null) {
            TreeResultsDTO<? extends FileSystemContentSearchParam> results = MainDAO.getInstance().getFileSystemDAO().getDisplayableContentChildren(contentId);
            return results;
        } else {
            TreeResultsDTO<? extends FileSystemContentSearchParam> results = MainDAO.getInstance().getFileSystemDAO().getDataSourcesForHost(host);
            return results;
        }
    }

    @Override
    protected TreeNode<FileSystemContentSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> rowData) {
        TreeContentType contentType = rowData instanceof FileSystemTreeItem ? ((FileSystemTreeItem) rowData).getContentType() : null;
        if (contentType == null) {
            logger.log(Level.WARNING, MessageFormat.format("Tree item: {0} did not have a valid content type.", rowData.getId()));
            return null;
        }

        long objId = rowData.getSearchParams().getContentObjectId();
        switch (contentType) {
            case DIRECTORY:
                return new DirectoryTreeNode(objId, rowData);
            case FILE:
                return new FileTreeNode(objId, rowData);
            case IMAGE:
                return new ImageTreeNode(objId, rowData);
            case LOCAL_FILES_DATA_SOURCE:
                return new LocalFilesDataSourceTreeNode(objId, rowData);
            case LOCAL_DIRECTORY:
                return new LocalDirectoryTreeNode(objId, rowData);
            case POOL:
                return new PoolTreeNode(objId, rowData);
            case VIRTUAL_DIRECTORY:
                return new VirtualDirectoryTreeNode(objId, rowData);
            case VOLUME:
                return new VolumeTreeNode(objId, rowData);
            case UNKNOWN:
            default:
                return new UnsupportedTreeNode(rowData);
        }
    }

    @Override
    protected TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
        if (treeEvt instanceof FileSystemTreeEvent) {
            FileSystemTreeEvent fsTreeEvent = (FileSystemTreeEvent) treeEvt;
            // when getContentObjectId == null, trigger refresh, otherwise, see if common parent
            if (fsTreeEvent.getItemRecord().getSearchParams().getContentObjectId() == null
                    || (Objects.equals(this.host, fsTreeEvent.getParentHost())
                    && Objects.equals(this.contentId, fsTreeEvent.getParentContentId()))) {

                return fsTreeEvent.getItemRecord();
            }
        }

        return null;
    }

    @Override
    public int compare(TreeItemDTO<? extends FileSystemContentSearchParam> o1, TreeItemDTO<? extends FileSystemContentSearchParam> o2) {
        if (o1 instanceof FileSystemTreeItem && o2 instanceof FileSystemTreeItem) {
            FileSystemTreeItem fs1 = (FileSystemTreeItem) o1;
            FileSystemTreeItem fs2 = (FileSystemTreeItem) o2;

            // ordering taken from SELECT_FILES_BY_PARENT in SleuthkitCase
            if ((fs1.getMetaType() != null) && (fs2.getMetaType() != null)
                    && (fs1.getMetaType().getValue() != fs2.getMetaType().getValue())) {
                return -Short.compare(fs1.getMetaType().getValue(), fs2.getMetaType().getValue());
            }

            // The case where both meta types are null will fall through to the name comparison.
            if (fs1.getMetaType() == null) {
                if (fs2.getMetaType() != null) {
                    return -1;
                }
            } else if (fs2.getMetaType() != null) {
                return 1;
            }
        }
        return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
    }

    /**
     * This factory is used to produce the single data source node under "Data
     * Source Files" when grouping by person/host is selected.
     */
    public static class DataSourceFactory extends TreeChildFactory<FileSystemContentSearchParam> {

        private final long dataSourceId;

        /**
         * Create the factory for a given data source object ID.
         *
         * @param dataSourceId The data source object ID.
         */
        public DataSourceFactory(long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeResultsDTO<? extends FileSystemContentSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
            // We're not really getting children here, just creating a node for the data source itself.
            return MainDAO.getInstance().getFileSystemDAO().getSingleDataSource(dataSourceId);
        }

        @Override
        protected TreeNode<FileSystemContentSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> rowData) {
            TreeContentType contentType = rowData instanceof FileSystemTreeItem ? ((FileSystemTreeItem) rowData).getContentType() : null;
            if (contentType == null) {
                logger.log(Level.WARNING, MessageFormat.format("Tree item: {0} did not have a valid content type.", rowData.getId()));
                return null;
            }

            long objId = rowData.getSearchParams().getContentObjectId();
            switch (contentType) {
                case IMAGE:
                    return new ImageTreeNode(objId, rowData);
                case LOCAL_FILES_DATA_SOURCE:
                    return new LocalFilesDataSourceTreeNode(objId, rowData);
                default:
                    logger.log(Level.SEVERE, "Unexpected data source type (ID: {0})", objId);
                    return null;
            }
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> getOrCreateRelevantChild(TreeEvent treeEvt) {
            if (treeEvt instanceof FileSystemTreeEvent) {
                FileSystemTreeEvent fsTreeEvent = (FileSystemTreeEvent) treeEvt;
                // when getContentObjectId == null, trigger refresh, otherwise, see if common parent
                if (fsTreeEvent.getItemRecord().getSearchParams().getContentObjectId() == null
                        || Objects.equals(fsTreeEvent.getItemRecord().getSearchParams().getContentObjectId(), dataSourceId)) {

                    return fsTreeEvent.getItemRecord();
                }
            }

            return null;

        }

        @Override
        public int compare(TreeItemDTO<? extends FileSystemContentSearchParam> o1, TreeItemDTO<? extends FileSystemContentSearchParam> o2) {
            return o1.getDisplayName().compareToIgnoreCase(o2.getDisplayName());
        }
    }

    /**
     * Display name and count of a file system node in the tree.
     */
    @NbBundle.Messages({
        "FileSystemFactory.FileSystemTreeNode.ExtractUnallocAction.text=Extract Unallocated Space to Single Files"})
    public abstract static class FileSystemTreeNode extends TreeNode<FileSystemContentSearchParam> implements ActionContext {

        private String translatedSourceName;

        protected FileSystemTreeNode(String icon, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData, Children children) {
            super(ContentNodeUtil.getContentName(itemData.getSearchParams().getContentObjectId()), icon, itemData, children, getDefaultLookup(itemData));
            startTranslationTask();
        }

        protected static Children createChildrenForContent(TreeItemDTO<? extends FileSystemContentSearchParam> treeItem) {
            if ((treeItem instanceof FileSystemTreeItem && ((FileSystemTreeItem) treeItem).isLeaf())) {
                return Children.LEAF;
            } else {
                return Children.create(new FileSystemFactory(treeItem.getSearchParams().getContentObjectId()), true);
            }
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            getItemData().getSearchParams().setNodeSelectionInfo(getNodeSelectionInfo());
            dataResultPanel.displayFileSystemContent(this.getItemData().getSearchParams());
        }

        public abstract Node clone();

        @Override
        public Action[] getActions(boolean context) {
            return ActionsFactory.getActions(this);
        }

        @Override
        protected String getDisplayNameString(String displayName, TreeResultsDTO.TreeDisplayCount displayCount) {
            // lock to prevent simultaneous reads and updates of translatedSourceName
            synchronized (this) {
                // defer to translated source name if present; otherwise use current display name
                String displayNameToUse = this.translatedSourceName != null ? this.translatedSourceName : displayName;
                return super.getDisplayNameString(displayNameToUse, displayCount);
            }
        }

        private void startTranslationTask() {
            if (TextTranslationService.getInstance().hasProvider() && UserPreferences.displayTranslatedFileNames()) {
                /*
                 * If machine translation is configured, add the original name
                 * of the of the source content of the artifact represented by
                 * this node to the sheet.
                 */

                if (translatedSourceName == null) {
                    PropertyChangeListener listener = new PropertyChangeListener() {
                        @Override
                        public void propertyChange(PropertyChangeEvent evt) {
                            String eventType = evt.getPropertyName();
                            if (eventType.equals(FileNameTransTask.getPropertyName())) {
                                // lock to prevent simultaneous reads and updates of translatedSourceName
                                synchronized (FileSystemTreeNode.this) {
                                    FileSystemTreeNode.this.translatedSourceName = evt.getNewValue().toString();
                                }

                                TreeItemDTO<? extends FileSystemContentSearchParam> itemData = FileSystemTreeNode.this.getItemData();
                                FileSystemTreeNode.this.setDisplayName(getDisplayNameString(itemData.getDisplayName(), itemData.getDisplayCount()));
                            }
                        }
                    };
                    /*
                     * NOTE: The task makes its own weak reference to the
                     * listener.
                     */
                    new FileNameTransTask(getItemData().getDisplayName(), this, listener).submit();
                }
            }
        }
    }

    static class ImageTreeNode extends FileSystemTreeNode {

        private final long objId;

        ImageTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(NodeIconUtil.IMAGE.getPath(),
                    itemData,
                    createChildrenForContent(itemData));

            this.objId = objId;
        }

        public Node clone() {
            return new ImageTreeNode(this.objId, getItemData());
        }

        @Override
        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
            group.add(new ExtractUnallocAction(
                    Bundle.FileSystemFactory_FileSystemTreeNode_ExtractUnallocAction_text(), this.objId));
            return Optional.of(group);
        }

        @Override
        public Optional<Long> getDataSourceForActions() {
            return Optional.of(this.objId);
        }

        @Override
        public boolean supportsSourceContentViewerActions() {
            return true;
        }
    }

    static class VolumeTreeNode extends FileSystemTreeNode {

        private final long objId;

        VolumeTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(NodeIconUtil.VOLUME.getPath(),
                    itemData,
                    createChildrenForContent(itemData));

            this.objId = objId;
        }

        public Node clone() {
            return new VolumeTreeNode(this.objId, getItemData());
        }

        @Override
        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
            try {
                ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
                Volume volume = (Volume) Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(objId);

                group.add(new ExtractUnallocAction(
                        Bundle.VolumnNode_ExtractUnallocAction_text(), this.objId));

                group.add(new FileSystemDetailsAction(volume));
                return Optional.of(group);
            } catch (NoCurrentCaseException | TskCoreException | ClassCastException ex) {
                logger.log(Level.WARNING, "Could not get volume for id: " + this.objId);
                return Optional.empty();
            }
        }

        @Override
        public boolean supportsSourceContentViewerActions() {
            return true;
        }
    }

    static class PoolTreeNode extends FileSystemTreeNode {

        private final long objId;

        PoolTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(NodeIconUtil.POOL.getPath(),
                    itemData,
                    createChildrenForContent(itemData));

            this.objId = objId;
        }

        public Node clone() {
            return new PoolTreeNode(this.objId, getItemData());
        }
    }

    static class DirectoryTreeNode extends FileSystemTreeNode {

        private final long objId;
        private final boolean isUnalloc;

        DirectoryTreeNode(long objId, boolean isUnalloc, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(isUnalloc ? DELETED_FOLDER.getPath() : FOLDER.getPath(),
                    itemData,
                    createChildrenForContent(itemData));
            this.objId = objId;
            this.isUnalloc = isUnalloc;
        }

        private static String getDirectoryIcon(AbstractFile dir) {
            if (dir.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                return DELETED_FOLDER.getPath();
            } else {
                return FOLDER.getPath();
            }
        }

        public Node clone() {
            return new DirectoryTreeNode(this.objId, this.isUnalloc, getItemData());
        }

        @Override
        public boolean supportsViewInTimeline() {
            return true;
        }

        @Override
        public Optional<Long> getFileForViewInTimelineAction() {
            return Optional.of(objId);
        }

        @Override
        public boolean supportsTreeExtractActions() {
            return true;
        }

        @Override
        public Optional<Long> getContentForRunIngestionModuleAction() {
            return Optional.of(this.objId);
        }

        @Override
        public boolean supportsContentTagAction() {
            return true;
        }
    }

    static abstract class SpecialDirectoryTreeNode extends FileSystemTreeNode {

        private final long objId;

        protected SpecialDirectoryTreeNode(long objId, String icon, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData, Children children) {
            super(icon, itemData, children);
            this.objId = objId;
        }

        @Override
        public boolean supportsSourceContentViewerActions() {
            return true;
        }

        @Override
        public boolean supportsTreeExtractActions() {
            return true;
        }

        protected long getObjId() {
            return objId;
        }

        @Override
        public Optional<Long> getContentForRunIngestionModuleAction() {
            return Optional.of(objId);
        }
    }

    static class LocalDirectoryTreeNode extends SpecialDirectoryTreeNode {

        LocalDirectoryTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(objId,
                    NodeIconUtil.FOLDER.getPath(),
                    itemData,
                    createChildrenForContent(itemData));
        }

        public Node clone() {
            return new LocalDirectoryTreeNode(getObjId(), getItemData());
        }

        @Override
        public boolean supportsContentTagAction() {
            return true;
        }
    }

    static class LocalFilesDataSourceTreeNode extends SpecialDirectoryTreeNode {

        LocalFilesDataSourceTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(objId,
                    NodeIconUtil.VOLUME.getPath(),
                    itemData,
                    createChildrenForContent(itemData));
        }

        public Node clone() {
            return new LocalFilesDataSourceTreeNode(getObjId(), getItemData());
        }

        @Override
        public Optional<Long> getDataSourceForActions() {
            return Optional.of(getObjId());
        }
    }

    static class VirtualDirectoryTreeNode extends SpecialDirectoryTreeNode {

        VirtualDirectoryTreeNode(long objId, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(objId,
                    NodeIconUtil.VIRTUAL_DIRECTORY.getPath(),
                    itemData,
                    createChildrenForContent(itemData));
        }

        public Node clone() {
            return new VirtualDirectoryTreeNode(getObjId(), getItemData());
        }
    }

    static class FileTreeNode extends FileSystemTreeNode {

        private final long objId;
        private final boolean isUnalloc;
        private final boolean isCarved;
        private final String extension;

        FileTreeNode(long objId, boolean isUnalloc, boolean isCarved, String extension, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(getFileIcon(isUnalloc, isCarved, extension),
                    itemData,
                    createChildrenForContent(itemData));

            this.objId = objId;
            this.isUnalloc = isUnalloc;
            this.isCarved = isCarved;
            this.extension = extension;
        }

        public Node clone() {
            return new FileTreeNode(this.objId, this.isUnalloc, this.isCarved, this.extension, getItemData());
        }

        private static String getFileIcon(boolean isUnalloc, boolean isCarved, String extension) {
            if (isUnalloc) {
                if (isCarved) {
                    return CARVED_FILE.getPath();
                } else {
                    return DELETED_FILE.getPath();
                }
            } else {
                MediaTypeUtils.ExtensionMediaType mediaType = MediaTypeUtils.getExtensionMediaType(extension);
                return MediaTypeUtils.getIconForFileType(mediaType);
            }
        }

        @Override
        public boolean supportsViewInTimeline() {
            return true;
        }

        @Override
        public Optional<Long> getFileForViewInTimelineAction() {
            return Optional.of(objId);
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
        public boolean supportsTreeExtractActions() {
            return true;
        }

        @Override
        public boolean supportsContentTagAction() {
            return true;
        }

        @Override
        public Optional<Long> getFileForDirectoryBrowseMode() {
            return Optional.of(this.objId);
        }

        @Override
        public Optional<Long> getExtractArchiveWithPasswordActionFile() {
            // TODO: See JIRA-8099
            boolean isArchive = FileTypeExtensions.getArchiveExtensions().contains("." + this.extension.toLowerCase());
            boolean encryptionDetected = false;
            try {
                encryptionDetected = isArchive && Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().getAnalysisResultsWhere(
                        "obj_id = " + objId + " AND artifact_type_id = " + BlackboardArtifact.Type.TSK_ENCRYPTION_DETECTED.getTypeID()).size() > 0;

            } catch (NoCurrentCaseException | TskCoreException ex) {
                logger.log(Level.SEVERE, "Error loading artifacts for file with ID: " + objId, ex);
            }

            return encryptionDetected ? Optional.of(objId) : Optional.empty();
        }
    }

    @NbBundle.Messages({
        "FileSystemFactory.UnsupportedTreeNode.displayName=Unsupported Content",})
    static class UnsupportedTreeNode extends FileSystemTreeNode {

        UnsupportedTreeNode(TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(NodeIconUtil.FILE.getPath(),
                    itemData,
                    createChildrenForContent(itemData));
        }

        public Node clone() {
            return new UnsupportedTreeNode(getItemData());
        }
    }
}
