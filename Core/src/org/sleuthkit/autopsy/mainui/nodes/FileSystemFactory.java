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
import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import java.util.concurrent.ExecutionException;
import javax.swing.Action;
import org.openide.nodes.ChildFactory;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.FileTypeExtensions;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.directorytree.ExtractUnallocAction;
import org.sleuthkit.autopsy.directorytree.FileSystemDetailsAction;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemContentSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileSystemColumnUtils;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeContentItemDTO;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.CARVED_FILE;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.DELETED_FILE;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.DELETED_FOLDER;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.FILE;
import static org.sleuthkit.autopsy.mainui.nodes.NodeIconUtil.FOLDER;
import static org.sleuthkit.autopsy.mainui.nodes.TreeNode.getDefaultLookup;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionContext;
import org.sleuthkit.autopsy.mainui.nodes.actions.ActionsFactory;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;
import org.sleuthkit.datamodel.VirtualDirectory;
import org.sleuthkit.datamodel.Volume;
import org.sleuthkit.datamodel.Pool;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;
import org.sleuthkit.datamodel.TskData;

/**
 * Factory for displaying content in the data source section of the tree.
 */
public class FileSystemFactory extends TreeChildFactory<FileSystemContentSearchParam> {

    private Long contentId = null;
    private Host host = null;

    /**
     * Main constructor.
     *
     * @param contentId The object ID for this node
     */
    public FileSystemFactory(Long contentId) {
        System.out.println("### Creating FileSystemFactory with content ID: " + contentId);
        this.contentId = contentId;
    }
    
    public FileSystemFactory(Host host) {
        System.out.println("### Creating FileSystemFactory with host ID: " + host.getHostId());
        this.host = host;
    }

    @Override
    protected TreeResultsDTO<? extends FileSystemContentSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
        if (host == null) {
            TreeResultsDTO<? extends FileSystemContentSearchParam> results = MainDAO.getInstance().getFileSystemDAO().getDisplayableContentChildren(contentId);
            System.out.println("### getChildResults() for id: " + contentId + " has " + results.getItems().size() + " rows");
            return results;
        } else {
            TreeResultsDTO<? extends FileSystemContentSearchParam> results = MainDAO.getInstance().getFileSystemDAO().getDataSourcesForHost(host);
            System.out.println("### getChildResults() for host: " + host.getName() + " has " + results.getItems().size() + " rows");
            return results;
        }
    }

    @Override
    protected TreeNode<FileSystemContentSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> rowData) {
        try {
            Content content = Case.getCurrentCaseThrows().getSleuthkitCase().getContentById(rowData.getTypeData().getContentObjectId());
            if (content instanceof Image) {
                return new ImageTreeNode((Image) content, rowData);
            } else if (content instanceof Volume) {
                return new VolumeTreeNode((Volume) content, rowData);
            } else if (content instanceof Pool) {
                return new PoolTreeNode((Pool) content, rowData);
            } else if (content instanceof VirtualDirectory) {
                return new VirtualDirectoryTreeNode((VirtualDirectory) content, rowData);
            } else if (content instanceof LocalFilesDataSource) {
                return new LocalFilesDataSourceTreeNode((LocalFilesDataSource) content, rowData);
            } else if (content instanceof Volume) {
                return new VolumeTreeNode((Volume) content, rowData);
            } else if (content instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) content;
                if (file.isDir()) {
                    return new DirectoryTreeNode(file, rowData);
                } else {
                    return new FileTreeNode(file, rowData);
                }
            } else {
                return new UnsupportedTreeNode(content, rowData);
            }
        } catch (NoCurrentCaseException | TskCoreException ex) {
            // TODO log
            return null;
        }
    }

    @Override
    public boolean isRefreshRequired(PropertyChangeEvent evt) {
        String eventType = evt.getPropertyName();
        if (eventType.equals(IngestManager.IngestModuleEvent.DATA_ADDED.toString())) {
            /**
             * This is a stop gap measure until a different way of handling the
             * closing of cases is worked out. Currently, remote events may be
             * received for a case that is already closed.
             */
            try {
                Case.getCurrentCaseThrows();
                /**
                 * Due to some unresolved issues with how cases are closed, it
                 * is possible for the event to have a null oldValue if the
                 * event is a remote event.
                 */
                // TODO
                final ModuleDataEvent event = (ModuleDataEvent) evt.getOldValue();
                //if (null != event && Category.DATA_ARTIFACT.equals(event.getBlackboardArtifactType().getCategory())
                //        && !(DataArtifactDAO.getIgnoredTreeTypes().contains(event.getBlackboardArtifactType()))) {
                    return true;
                //}
            } catch (NoCurrentCaseException notUsed) {
                /**
                 * Case is closed, do nothing.
                 */
            }
        }
        return false;
    }
    
    /**
     * This factory is used to produce the single data source node under "Data Source Files" when
     * grouping by person/host is selected. 
     */
    public static class DataSourceFactory extends TreeChildFactory<FileSystemContentSearchParam> {
        private final long dataSourceId;
        
        public DataSourceFactory(long dataSourceId) {
            System.out.println("### Creating DataSourceFactory with dataSourceId: " + dataSourceId);
            this.dataSourceId = dataSourceId;
        }
        
        @Override
        protected TreeResultsDTO<? extends FileSystemContentSearchParam> getChildResults() throws IllegalArgumentException, ExecutionException {
            // We're not really getting children here, just creating a node for the data source itself.
            return MainDAO.getInstance().getFileSystemDAO().getSingleDataSource(dataSourceId);
        }
 
        @Override
        protected TreeNode<FileSystemContentSearchParam> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> rowData) {
            try {        
                DataSource ds = Case.getCurrentCaseThrows().getSleuthkitCase().getDataSource(dataSourceId);
                if (ds instanceof Image) {
                    return new ImageTreeNode((Image) ds, rowData);
                } else if (ds instanceof LocalFilesDataSource) {
                    return new LocalFilesDataSourceTreeNode((LocalFilesDataSource) ds, rowData);
                } else {
                    // There shouldn't be any other type
                    // TODO log
                    return null;
                }
            } catch (NoCurrentCaseException | TskCoreException | TskDataException ex) {
                // TODO log
                return null;
            }
        }
        
        
        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            // TODO
            return false;
        }
        
    }

    /**
     * Display name and count of a file system node in the tree.
     */
    public abstract static class FileSystemTreeNode extends TreeNode<FileSystemContentSearchParam> implements ActionContext {


        protected FileSystemTreeNode(String nodeName, String icon, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData, Children children, Lookup lookup) {
            //super(nodeName, icon, itemData, children, lookup);
            super(nodeName, "org/sleuthkit/autopsy/images/bank.png", itemData, children, lookup);
        }
        
        protected static Children createChildrenForContent(Long contentId) {
            try {
                if (FileSystemColumnUtils.getVisibleTreeNodeChildren(contentId).isEmpty()) {
                    return Children.LEAF;
                } else {
                    return Children.create(new FileSystemFactory(contentId), true);
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                // TODO log
                return Children.LEAF;
            }
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayFileSystemContent(this.getItemData().getTypeData());
        }

        public abstract Node clone();
        
        @Override
        public Action[] getActions(boolean context) {
            return ActionsFactory.getActions(this);
        }
    }
    
    static class ImageTreeNode extends FileSystemTreeNode {
        Image image;
        
        ImageTreeNode(Image image, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(itemData.getDisplayName(),
                    NodeIconUtil.IMAGE.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.image = image;
            System.out.println("### ImageTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new ImageTreeNode(image, getItemData());
        }
        
        @Override
        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
            group.add(new ExtractUnallocAction(
                    Bundle.ImageNode_ExtractUnallocAction_text(), image));
            return Optional.of(group);
        }

        @Override
        public Optional<Content> getDataSourceForActions() {
            return Optional.of(image);
        }

        @Override
        public Optional<Node> getNewWindowActionNode() {
            return Optional.of(this);
        }

        @Override
        public boolean supportsSourceContentViewerActions() {
            return true;
        }
    }

    static class VolumeTreeNode extends FileSystemTreeNode {
        Volume volume;
        
        VolumeTreeNode(Volume volume, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(itemData.getDisplayName(),
                    NodeIconUtil.VOLUME.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.volume = volume;
            System.out.println("### VolumeTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new VolumeTreeNode(volume, getItemData());
        }
        
        @Override
        public Optional<ActionsFactory.ActionGroup> getNodeSpecificActions() {
            ActionsFactory.ActionGroup group = new ActionsFactory.ActionGroup();
            group.add(new ExtractUnallocAction(
                    Bundle.VolumnNode_ExtractUnallocAction_text(), volume));
            group.add(new FileSystemDetailsAction(volume));
            return Optional.of(group);
        }

        @Override
        public Optional<Node> getNewWindowActionNode() {
            return Optional.of(this);
        }

        @Override
        public boolean supportsSourceContentViewerActions() {
            return true;
        }
    }

    static class PoolTreeNode extends FileSystemTreeNode {
        Pool pool;
        
        PoolTreeNode(Pool pool, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(itemData.getDisplayName(),
                    NodeIconUtil.VOLUME.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.pool = pool;
            System.out.println("### PoolTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new PoolTreeNode(pool, getItemData());
        }
    }
    
    static class DirectoryTreeNode extends FileSystemTreeNode {
        AbstractFile dir;
        
        DirectoryTreeNode(AbstractFile dir, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(itemData.getDisplayName(),
                    getDirectoryIcon(dir),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.dir = dir;
            System.out.println("### DirectoryTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
        
        private static String getDirectoryIcon(AbstractFile dir) {
            if (dir.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                return DELETED_FOLDER.getPath();
            } else {
                return FOLDER.getPath();
            }
        }
                
        public Node clone() {
            return new DirectoryTreeNode(dir, getItemData());
        }
         
        @Override
        public boolean supportsViewInTimeline() {
            return true;
        }

        @Override
        public Optional<AbstractFile> getFileForViewInTimelineAction() {
            return Optional.of(dir);
        }

        @Override
        public boolean supportsExtractActions() {
            return true;
        }

        @Override
        public Optional<Content> getContentForRunIngestionModuleAction() {
            return Optional.of(dir);
        }

        @Override
        public boolean supportsContentTagAction() {
            return true;
        }
    }
    
    static abstract class SpecialDirectoryTreeNode extends FileSystemTreeNode {
        AbstractFile dir;
        
        protected SpecialDirectoryTreeNode(AbstractFile dir, String nodeName, String icon, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData, Children children, Lookup lookup) {
            super(nodeName, icon, itemData, children, lookup);
            this.dir = dir;
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
        public boolean supportsExtractActions() {
            return true;
        }

        @Override
        public Optional<Content> getContentForRunIngestionModuleAction() {
            return Optional.of(dir);
        }

        @Override
        public Optional<Content> getContentForFileSearchAction() {
            return Optional.of(dir);
        }
    }
    
    static class LocalDirectoryTreeNode extends SpecialDirectoryTreeNode {
        LocalDirectoryTreeNode(AbstractFile dir, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(dir, 
                    itemData.getDisplayName(),
                    NodeIconUtil.FOLDER.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            System.out.println("### LocalDirectoryTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new DirectoryTreeNode(dir, getItemData());
        }
    }
    
    static class LocalFilesDataSourceTreeNode extends SpecialDirectoryTreeNode {
        
        LocalFilesDataSourceTreeNode(AbstractFile localFilesDataSource, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(localFilesDataSource,
                    itemData.getDisplayName(),
                    NodeIconUtil.VOLUME.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            System.out.println("### LocalFilesDataSourceTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new LocalFilesDataSourceTreeNode(dir, getItemData());
        }
        
        @Override
        public Optional<Content> getDataSourceForActions() {
            return Optional.of(dir);
        }
    }    
    
    static class VirtualDirectoryTreeNode extends SpecialDirectoryTreeNode {
        
        VirtualDirectoryTreeNode(AbstractFile dir, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(dir,
                    itemData.getDisplayName(),
                    NodeIconUtil.VIRTUAL_DIRECTORY.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            System.out.println("### VirtualDirectoryTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new DirectoryTreeNode(dir, getItemData());
        }
    }
    
    static class FileTreeNode extends FileSystemTreeNode {
        AbstractFile file;
        
        FileTreeNode(AbstractFile file, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(itemData.getDisplayName(),
                    getFileIcon(file),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.file = file;
            System.out.println("### FileTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new DirectoryTreeNode(file, getItemData());
        }
        
        private static String getFileIcon(AbstractFile file) {
            if (file.isDirNameFlagSet(TskData.TSK_FS_NAME_FLAG_ENUM.UNALLOC)) {
                if (file.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED)) {
                    return CARVED_FILE.getPath();
                } else {
                    return DELETED_FILE.getPath();
                }
            } else {
                return FILE.getPath();
            }
        }
        
        @Override
        public boolean supportsViewInTimeline() {
            return true;
        }

        @Override
        public Optional<AbstractFile> getFileForViewInTimelineAction() {
            return Optional.of(file);
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
            // TODO What is this?
            return Optional.of(file);
        }

        @Override
        public Optional<AbstractFile> getExtractArchiveWithPasswordActionFile() {
            // GVDTODO: HANDLE THIS ACTION IN A BETTER WAY!-----
            // See JIRA-8099
            boolean isArchive = FileTypeExtensions.getArchiveExtensions().contains("." + file.getNameExtension().toLowerCase());
            boolean encryptionDetected = false;
            try {
                encryptionDetected = isArchive && file.getArtifacts(BlackboardArtifact.ARTIFACT_TYPE.TSK_ENCRYPTION_DETECTED).size() > 0;
            } catch (TskCoreException ex) {
                // TODO
            }

            return encryptionDetected ? Optional.of(file) : Optional.empty();
        }
    }
    
    @NbBundle.Messages({
        "FileSystemFactory.UnsupportedTreeNode.displayName=Unsupported Content",})
    static class UnsupportedTreeNode extends FileSystemTreeNode {
        Content content;
        
        UnsupportedTreeNode(Content content, TreeResultsDTO.TreeItemDTO<? extends FileSystemContentSearchParam> itemData) {
            super(Bundle.FileSystemFactory_UnsupportedTreeNode_displayName(),
                    NodeIconUtil.FILE.getPath(),
                    itemData,
                    createChildrenForContent(itemData.getTypeData().getContentObjectId()),
                    getDefaultLookup(itemData));
            this.content = content;
            System.out.println("### UnsupportedTreeNode - name: " + itemData.getDisplayName() + ", contentId: " + itemData.getTypeData().getContentObjectId());
        }
                
        public Node clone() {
            return new UnsupportedTreeNode(content, getItemData());
        }
    }
}
