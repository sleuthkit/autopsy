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
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.nodes.Children;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtDocumentFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtExecutableFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtRootFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtSearchFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeMimeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeSizeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileSizeFilter;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * Factories for displaying views.
 */
public class ViewsTypeFactory {

    /**
     * Returns an AbstractFile if the event contains a ModuleContentEvent which
     * contains an abstract file and that file belongs to the data source if a
     * data source id is specified. Otherwise, returns null.
     *
     * @param evt          The event
     * @param dataSourceId The data source object id that will be the parent of
     *                     the file or null.
     *
     * @return The file meeting criteria or null.
     */
    private static AbstractFile getFileInDataSourceFromEvt(PropertyChangeEvent evt, Long dataSourceId) {
        if (!(evt.getOldValue() instanceof ModuleContentEvent)) {
            return null;
        }

        ModuleContentEvent contentEvt = (ModuleContentEvent) evt.getOldValue();
        if (!(contentEvt.getSource() instanceof AbstractFile)) {
            return null;
        }

        AbstractFile file = (AbstractFile) contentEvt.getSource();
        if (dataSourceId != null && file.getDataSourceObjectId() != dataSourceId) {
            return null;
        }

        return file;
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

//        @Override
//        public boolean isRefreshRequired(PropertyChangeEvent evt) {
//            AbstractFile evtFile = getFileInDataSourceFromEvt(evt, this.dataSourceId);
//            if (evtFile == null) {
//                return false;
//            }
//
//            long size = evtFile.getSize();
//            for (FileSizeFilter filter : FileSizeFilter.values()) {
//                if (size >= filter.getMinBound() || size < filter.getMaxBound()) {
//                    return true;
//                }
//            }
//
//            return false;
//        }

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
                super("FILE_SIZE_" + itemData.getTypeData().getSizeFilter().getName(), "org/sleuthkit/autopsy/images/file-size-16.png", itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileSizes(this.getItemData().getTypeData());
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

//        @Override
//        public boolean isRefreshRequired(PropertyChangeEvent evt) {
//            return getFileInDataSourceFromEvt(evt, this.dataSourceId) != null;
//        }

        static class FileMimePrefixNode extends TreeNode<FileTypeMimeSearchParams> {

            /**
             * Main constructor.
             *
             * @param itemData The data for the node.
             */
            public FileMimePrefixNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> itemData) {
                super(
                        "FILE_MIME_" + itemData.getTypeData().getMimeType(),
                        "org/sleuthkit/autopsy/images/file_types.png",
                        itemData,
                        Children.create(new FileMimeSuffixFactory(itemData.getTypeData().getDataSourceId(), itemData.getTypeData().getMimeType()), true),
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

//        @Override
//        public boolean isRefreshRequired(PropertyChangeEvent evt) {
//            AbstractFile file = getFileInDataSourceFromEvt(evt, dataSourceId);
//            if (file == null || file.getMIMEType() == null) {
//                return false;
//            }
//
//            return file.getMIMEType().toLowerCase().startsWith(this.mimeTypePrefix.toLowerCase());
//        }

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
                super("FILE_MIME_" + itemData.getTypeData().getMimeType(),
                        "org/sleuthkit/autopsy/images/file-filter-icon.png",
                        itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileMimes(this.getItemData().getTypeData());
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
            if (rowData.getTypeData().getFilter() == FileExtRootFilter.TSK_DOCUMENT_FILTER) {
                childFilters = Stream.of(FileExtDocumentFilter.values()).collect(Collectors.toList());
            } else if (rowData.getTypeData().getFilter() == FileExtRootFilter.TSK_EXECUTABLE_FILTER) {
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

//        @Override
//        public boolean isRefreshRequired(PropertyChangeEvent evt) {
//            AbstractFile file = getFileInDataSourceFromEvt(evt, this.dataSourceId);
//            return file != null && file.getNameExtension() != null && 
//                    this.childFilters.stream().anyMatch((filter) -> filter.getFilter().contains("." + file.getNameExtension().toLowerCase()));
//        }

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
                super("FILE_EXT_" + itemData.getTypeData().getFilter().getName(),
                        childFilters == null ? "org/sleuthkit/autopsy/images/file-filter-icon.png" : "org/sleuthkit/autopsy/images/file_types.png",
                        itemData,
                        childFilters == null ? Children.LEAF : Children.create(new FileExtFactory(itemData.getTypeData().getDataSourceId(), childFilters), true),
                        getDefaultLookup(itemData));

                this.childFilters = childFilters;
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                if (childFilters == null) {
                    dataResultPanel.displayFileExtensions(this.getItemData().getTypeData());
                } else {
                    super.respondSelection(dataResultPanel);
                }
            }

        }
    }

}
