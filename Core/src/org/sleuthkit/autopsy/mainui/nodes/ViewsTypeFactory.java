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

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.nodes.Children;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.AnalysisResultSearchParam;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtDocumentFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtExecutableFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtRootFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtSearchFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeMimeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.DeletedContentSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeSizeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOAggregateEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.DAOEvent;
import org.sleuthkit.autopsy.mainui.datamodel.events.TreeEvent;
import org.sleuthkit.autopsy.mainui.nodes.TreeNode.StaticTreeNode;

/**
 *
 * Factories for displaying views.
 */
public class ViewsTypeFactory {

    private static final Comparator<String> STRING_COMPARATOR = Comparator.nullsFirst(Comparator.naturalOrder());

    private static final String FILE_TYPES_ICON = "org/sleuthkit/autopsy/images/file_types.png";
    private static final String SIZE_ICON = "org/sleuthkit/autopsy/images/file-size-16.png";

    /**
     * Node for file extensions parent in the tree.
     */
    @Messages({"ViewsTypeFactory_ExtensionParentNode_displayName=By Extension"})
    private static class ExtensionParentNode extends StaticTreeNode {

        ExtensionParentNode(Long dataSourceId) {
            super(
                    "FILE_VIEW_EXTENSIONS_PARENT",
                    Bundle.ViewsTypeFactory_ExtensionParentNode_displayName(),
                    FILE_TYPES_ICON,
                    new FileExtFactory(dataSourceId)
            );
        }
    }

    /**
     * Parent mime types node in the tree.
     */
    @Messages({"ViewsTypeFactory_MimeParentNode_displayName=By MIME Type"})
    public static class MimeParentNode extends StaticTreeNode {

        MimeParentNode(Long dataSourceId) {
            super(
                    "FILE_VIEW_MIME_TYPE_PARENT",
                    Bundle.ViewsTypeFactory_MimeParentNode_displayName(),
                    FILE_TYPES_ICON,
                    new FileMimePrefixFactory(dataSourceId)
            );
        }
    }

    /**
     * Parent of deleted content nodes in the tree.
     */
    @Messages({"ViewsTypeFactory_DeletedParentNode_displayName=Deleted Files"})
    private static class DeletedParentNode extends StaticTreeNode {

        DeletedParentNode(Long dataSourceId) {
            super(
                    "FILE_VIEW_DELETED_PARENT",
                    Bundle.ViewsTypeFactory_DeletedParentNode_displayName(),
                    NodeIconUtil.DELETED_FILE.getPath(),
                    new DeletedContentFactory(dataSourceId)
            );
        }
    }

    /**
     * Parent of file size nodes in the tree.
     */
    @Messages({"ViewsTypeFactory_SizeParentNode_displayName=File Size"})
    private static class SizeParentNode extends StaticTreeNode {

        SizeParentNode(Long dataSourceId) {
            super(
                    "FILE_VIEW_SIZE_PARENT",
                    Bundle.ViewsTypeFactory_SizeParentNode_displayName(),
                    SIZE_ICON,
                    new FileSizeTypeFactory(dataSourceId)
            );
        }
    }

    /**
     * 'File Types' children in the tree.
     */
    public static class FileTypesChildren extends Children.Array {

        FileTypesChildren(Long dataSourceId) {
            super(ImmutableList.of(
                    new ExtensionParentNode(dataSourceId),
                    new MimeParentNode(dataSourceId)
            ));
        }
    }

    /**
     * 'File Types' parent node in the tree.
     */
    @Messages({"ViewsTypeFactory_FileTypesParentNode_displayName=File Types"})
    private static class FileTypesParentNode extends StaticTreeNode {

        public FileTypesParentNode(Long dataSourceId) {
            super(
                    "FILE_TYPES_PARENT",
                    Bundle.ViewsTypeFactory_FileTypesParentNode_displayName(),
                    FILE_TYPES_ICON,
                    new FileTypesChildren(dataSourceId)
            );
        }

    }

    /**
     * Children of 'File Views' in the tree.
     */
    public static class ViewsChildren extends Children.Array {

        public ViewsChildren(Long dataSourceId) {
            super(ImmutableList.of(
                    new FileTypesParentNode(dataSourceId),
                    new DeletedParentNode(dataSourceId),
                    new SizeParentNode(dataSourceId)
            ));
        }
    }

    /**
     * The factory for creating deleted content tree nodes.
     */
    public static class DeletedContentFactory extends TreeChildFactory<DeletedContentSearchParams> {

        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source to filter files to or null.
         */
        public DeletedContentFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<DeletedContentSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends DeletedContentSearchParams> rowData) {
            return new DeletedContentTypeNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends DeletedContentSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getDeletedContentCounts(dataSourceId);
        }

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof TreeEvent) {
                    TreeResultsDTO.TreeItemDTO<DeletedContentSearchParams> treeItem = super.getTypedTreeItem((TreeEvent) evt, DeletedContentSearchParams.class);
                    // if search params has null filter, trigger full refresh
                    if (treeItem != null && treeItem.getSearchParams().getFilter() == null) {
                        super.update();
                        return;
                    }
                }
            }

            super.handleDAOAggregateEvent(aggEvt);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends DeletedContentSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<DeletedContentSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, DeletedContentSearchParams.class);

            if (originalTreeItem != null
                    // only create child if size filter is present (if null, update should be triggered separately)
                    && originalTreeItem.getSearchParams().getFilter() != null
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                DeletedContentSearchParams searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        DeletedContentSearchParams.getTypeId(),
                        new DeletedContentSearchParams(searchParam.getFilter(), this.dataSourceId),
                        searchParam.getFilter(),
                        searchParam.getFilter().getDisplayName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends DeletedContentSearchParams> o1, TreeItemDTO<? extends DeletedContentSearchParams> o2) {
            return Integer.compare(o1.getSearchParams().getFilter().getId(), o2.getSearchParams().getFilter().getId());
        }

        /**
         * Shows a deleted content tree node.
         */
        static class DeletedContentTypeNode extends TreeNode<DeletedContentSearchParams> {

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            DeletedContentTypeNode(TreeResultsDTO.TreeItemDTO<? extends DeletedContentSearchParams> itemData) {
                super("DELETED_CONTENT_" + itemData.getSearchParams().getFilter().getName(), NodeIconUtil.DELETED_FILE.getPath(), itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayDeletedContent(this.getItemData().getSearchParams());
            }

        }
    }

    /**
     * The factory for creating file size tree nodes.
     */
    public static class FileSizeTypeFactory extends TreeChildFactory<FileTypeSizeSearchParams> {

        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source to filter files to or null.
         */
        public FileSizeTypeFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<FileTypeSizeSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeSizeSearchParams> rowData) {
            return new FileSizeTypeNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends FileTypeSizeSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFileSizeCounts(this.dataSourceId);
        }

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof TreeEvent) {
                    TreeResultsDTO.TreeItemDTO<FileTypeSizeSearchParams> treeItem = super.getTypedTreeItem((TreeEvent) evt, FileTypeSizeSearchParams.class);
                    // if file type size search params has null filter, trigger full refresh
                    if (treeItem != null && treeItem.getSearchParams().getSizeFilter() == null) {
                        super.update();
                        return;
                    }
                }
            }

            super.handleDAOAggregateEvent(aggEvt);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends FileTypeSizeSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<FileTypeSizeSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, FileTypeSizeSearchParams.class);

            if (originalTreeItem != null
                    // only create child if size filter is present (if null, update should be triggered separately)
                    && originalTreeItem.getSearchParams().getSizeFilter() != null
                    && (this.dataSourceId == null
                    || originalTreeItem.getSearchParams().getDataSourceId() == null
                    || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                FileTypeSizeSearchParams searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        FileTypeSizeSearchParams.getTypeId(),
                        new FileTypeSizeSearchParams(searchParam.getSizeFilter(), this.dataSourceId),
                        searchParam.getSizeFilter(),
                        searchParam.getSizeFilter().getDisplayName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends FileTypeSizeSearchParams> o1, TreeItemDTO<? extends FileTypeSizeSearchParams> o2) {
            return Integer.compare(o1.getSearchParams().getSizeFilter().getId(), o2.getSearchParams().getSizeFilter().getId());
        }

        /**
         * Shows a file size tree node.
         */
        static class FileSizeTypeNode extends TreeNode<FileTypeSizeSearchParams> {

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            FileSizeTypeNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeSizeSearchParams> itemData) {
                super("FILE_SIZE_" + itemData.getSearchParams().getSizeFilter().getName(), SIZE_ICON, itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileSizes(this.getItemData().getSearchParams());
            }

        }
    }

    /**
     * Factory to display mime type prefix tree nodes (i.e. audio, multipart).
     */
    public static class FileMimePrefixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source to filter files to or null.
         */
        public FileMimePrefixFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<FileTypeMimeSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> rowData) {
            return new FileMimePrefixNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends FileTypeMimeSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFileMimeCounts(null, this.dataSourceId);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<FileTypeMimeSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, FileTypeMimeSearchParams.class);

            if (originalTreeItem != null
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                FileTypeMimeSearchParams searchParam = originalTreeItem.getSearchParams();
                String mimePrefix = searchParam.getMimeType() == null ? "" : searchParam.getMimeType();
                int indexOfSlash = mimePrefix.indexOf("/");
                if (indexOfSlash >= 0) {
                    mimePrefix = mimePrefix.substring(0, indexOfSlash);
                }

                return new TreeResultsDTO.TreeItemDTO<>(
                        FileTypeMimeSearchParams.getTypeId(),
                        new FileTypeMimeSearchParams(mimePrefix, this.dataSourceId),
                        mimePrefix,
                        mimePrefix,
                        originalTreeItem.getDisplayCount());
            }
            return null;

        }

        @Override
        public int compare(TreeItemDTO<? extends FileTypeMimeSearchParams> o1, TreeItemDTO<? extends FileTypeMimeSearchParams> o2) {
            return STRING_COMPARATOR.compare(o1.getSearchParams().getMimeType(), o2.getSearchParams().getMimeType());
        }

        static class FileMimePrefixNode extends TreeNode<FileTypeMimeSearchParams> {

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            public FileMimePrefixNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> itemData) {
                super(
                        "FILE_MIME_" + itemData.getSearchParams().getMimeType(),
                        FILE_TYPES_ICON,
                        itemData,
                        Children.create(new FileMimeSuffixFactory(itemData.getSearchParams().getDataSourceId(), itemData.getSearchParams().getMimeType()), true),
                        getDefaultLookup(itemData));
            }
        }
    }

    /**
     * Displays mime type suffixes of a prefix (i.e. for prefix 'audio', a
     * suffix could be 'aac').
     */
    public static class FileMimeSuffixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

        private final String mimeTypePrefix;
        private final Long dataSourceId;

        /**
         * Main constructor.
         *
         * @param dataSourceId   The data source to filter files to or null.
         * @param mimeTypePrefix The mime type prefix (i.e. 'audio',
         *                       'multipart').
         */
        private FileMimeSuffixFactory(Long dataSourceId, String mimeTypePrefix) {
            this.dataSourceId = dataSourceId;
            this.mimeTypePrefix = mimeTypePrefix;
        }

        @Override
        protected TreeNode<FileTypeMimeSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> rowData) {
            return new FileMimeSuffixNode(rowData);
        }

        @Override
        protected TreeResultsDTO<? extends FileTypeMimeSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFileMimeCounts(this.mimeTypePrefix, this.dataSourceId);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<FileTypeMimeSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, FileTypeMimeSearchParams.class);

            String prefixWithSlash = this.mimeTypePrefix + "/";
            if (originalTreeItem != null
                    && (originalTreeItem.getSearchParams().getMimeType().startsWith(prefixWithSlash))
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                FileTypeMimeSearchParams searchParam = originalTreeItem.getSearchParams();
                String mimeSuffix = searchParam.getMimeType().substring(prefixWithSlash.length());
                return new TreeResultsDTO.TreeItemDTO<>(
                        FileTypeMimeSearchParams.getTypeId(),
                        new FileTypeMimeSearchParams(searchParam.getMimeType(), this.dataSourceId),
                        mimeSuffix,
                        mimeSuffix,
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends FileTypeMimeSearchParams> o1, TreeItemDTO<? extends FileTypeMimeSearchParams> o2) {
            return STRING_COMPARATOR.compare(o1.getSearchParams().getMimeType(), o2.getSearchParams().getMimeType());
        }

        /**
         * Displays an individual suffix node in the tree (i.e. 'aac' underneath
         * 'audio').
         */
        static class FileMimeSuffixNode extends TreeNode<FileTypeMimeSearchParams> {

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            public FileMimeSuffixNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> itemData) {
                super("FILE_MIME_" + itemData.getSearchParams().getMimeType(),
                        "org/sleuthkit/autopsy/images/file-filter-icon.png",
                        itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileMimes(this.getItemData().getSearchParams());
            }

        }
    }

    /**
     * Displays file extension tree nodes with possibly nested tree nodes (for
     * documents and executables).
     */
    public static class FileExtFactory extends TreeChildFactory<FileTypeExtensionsSearchParams> {

        private final Long dataSourceId;
        private final Collection<FileExtSearchFilter> childFilters;

        /**
         * Main constructor using root filters.
         *
         * @param dataSourceId The data source to filter files to or null.
         */
        public FileExtFactory(Long dataSourceId) {
            this(dataSourceId, Stream.of(FileExtRootFilter.values()).collect(Collectors.toList()));
        }

        /**
         * Main constructor.
         *
         * @param dataSourceId The data source to filter files to or null.
         * @param childFilters The file extension filters that will each be a
         *                     child tree node of this factory.
         */
        private FileExtFactory(Long dataSourceId, Collection<FileExtSearchFilter> childFilters) {
            this.childFilters = childFilters;
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<FileTypeExtensionsSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeExtensionsSearchParams> rowData) {
            Collection<FileExtSearchFilter> childFilters;
            if (rowData.getSearchParams().getFilter() == FileExtRootFilter.TSK_DOCUMENT_FILTER) {
                childFilters = Stream.of(FileExtDocumentFilter.values()).collect(Collectors.toList());
            } else if (rowData.getSearchParams().getFilter() == FileExtRootFilter.TSK_EXECUTABLE_FILTER) {
                childFilters = Stream.of(FileExtExecutableFilter.values()).collect(Collectors.toList());
            } else {
                childFilters = null;
            }

            return new FileExtNode(rowData, childFilters);
        }

        @Override
        protected TreeResultsDTO<? extends FileTypeExtensionsSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFileExtCounts(this.childFilters, this.dataSourceId);
        }

        @Override
        protected void handleDAOAggregateEvent(DAOAggregateEvent aggEvt) {
            for (DAOEvent evt : aggEvt.getEvents()) {
                if (evt instanceof TreeEvent) {
                    TreeResultsDTO.TreeItemDTO<FileTypeExtensionsSearchParams> treeItem = super.getTypedTreeItem((TreeEvent) evt, FileTypeExtensionsSearchParams.class);
                    // if search params has null filter, trigger full refresh
                    if (treeItem != null && treeItem.getSearchParams().getFilter() == null) {
                        super.update();
                        return;
                    }
                }
            }

            super.handleDAOAggregateEvent(aggEvt);
        }

        @Override
        protected TreeResultsDTO.TreeItemDTO<? extends FileTypeExtensionsSearchParams> getOrCreateRelevantChild(TreeEvent treeEvt) {
            TreeResultsDTO.TreeItemDTO<FileTypeExtensionsSearchParams> originalTreeItem = super.getTypedTreeItem(treeEvt, FileTypeExtensionsSearchParams.class);

            if (originalTreeItem != null
                    // if filter is null, this should trigger a full refresh which should be handled in handleDAOAggregateEvent
                    && originalTreeItem.getSearchParams().getFilter() != null
                    && this.childFilters.contains(originalTreeItem.getSearchParams().getFilter())
                    && (this.dataSourceId == null || Objects.equals(this.dataSourceId, originalTreeItem.getSearchParams().getDataSourceId()))) {

                // generate new type so that if it is a subtree event (i.e. keyword hits), the right tree item is created.
                FileTypeExtensionsSearchParams searchParam = originalTreeItem.getSearchParams();
                return new TreeResultsDTO.TreeItemDTO<>(
                        FileTypeExtensionsSearchParams.getTypeId(),
                        new FileTypeExtensionsSearchParams(searchParam.getFilter(), this.dataSourceId),
                        searchParam.getFilter(),
                        searchParam.getFilter().getDisplayName(),
                        originalTreeItem.getDisplayCount());
            }
            return null;
        }

        @Override
        public int compare(TreeItemDTO<? extends FileTypeExtensionsSearchParams> o1, TreeItemDTO<? extends FileTypeExtensionsSearchParams> o2) {
            return Integer.compare(o1.getSearchParams().getFilter().getId(), o1.getSearchParams().getFilter().getId());
        }

        /**
         * Represents a file extension tree node that may or may not have child
         * filters.
         */
        static class FileExtNode extends TreeNode<FileTypeExtensionsSearchParams> {

            private final Collection<FileExtSearchFilter> childFilters;

            /**
             * Main constructor.
             *
             * @param itemData     The data for the node.
             * @param childFilters The file filters that will be used to make
             *                     children of this node.
             */
            public FileExtNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeExtensionsSearchParams> itemData, Collection<FileExtSearchFilter> childFilters) {
                super("FILE_EXT_" + itemData.getSearchParams().getFilter().getName(),
                        childFilters == null ? "org/sleuthkit/autopsy/images/file-filter-icon.png" : "org/sleuthkit/autopsy/images/file_types.png",
                        itemData,
                        childFilters == null ? Children.LEAF : Children.create(new FileExtFactory(itemData.getSearchParams().getDataSourceId(), childFilters), true),
                        getDefaultLookup(itemData));

                this.childFilters = childFilters;
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                if (childFilters == null) {
                    dataResultPanel.displayFileExtensions(this.getItemData().getSearchParams());
                } else {
                    super.respondSelection(dataResultPanel);
                }
            }

        }
    }

}
