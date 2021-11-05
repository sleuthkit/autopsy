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

import static io.grpc.Context.key;
import java.beans.PropertyChangeEvent;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtDocumentFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtExecutableFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtRootFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileExtSearchFilter;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeExtensionsSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeMimeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeSizeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeSizeSearchParams.FileSizeFilter;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 * Factories for displaying views.
 */
public class ViewsTypeFactory {

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

    public static class FileSizeTypeFactory extends TreeChildFactory<FileTypeSizeSearchParams> {

        private final Long dataSourceId;

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
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            AbstractFile evtFile = getFileInDataSourceFromEvt(evt, this.dataSourceId);
            if (evtFile == null) {
                return false;
            }

            long size = evtFile.getSize();
            for (FileSizeFilter filter : FileSizeFilter.values()) {
                if (size >= filter.getMinBound() || size < filter.getMaxBound()) {
                    return true;
                }
            }

            return false;
        }

        static class FileSizeTypeNode extends TreeNode<FileTypeSizeSearchParams> {

            FileSizeTypeNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeSizeSearchParams> itemData) {
                super("FILE_SIZE_" + itemData.getTypeData().getSizeFilter().getName(), "org/sleuthkit/autopsy/images/file-size-16.png", itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileSizes(this.getItemData().getTypeData());
            }

        }
    }

    public static class FileMimePrefixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

        private final Long dataSourceId;

        public FileMimePrefixFactory(Long dataSourceId) {
            this.dataSourceId = dataSourceId;
        }

        @Override
        protected TreeNode<FileTypeMimeSearchParams> createNewNode(TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> rowData) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        protected TreeResultsDTO<? extends FileTypeMimeSearchParams> getChildResults() throws IllegalArgumentException, ExecutionException {
            return MainDAO.getInstance().getViewsDAO().getFileMimeCounts(null, this.dataSourceId);
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            return getFileInDataSourceFromEvt(evt, this.dataSourceId) != null;
        }

        static class FileMimePrefixNode extends TreeNode<FileTypeMimeSearchParams> {

            public FileMimePrefixNode(String nodeName, String icon, TreeResultsDTO.TreeItemDTO<? extends FileTypeMimeSearchParams> itemData, Children children, Lookup lookup) {
                super(
                        "FILE_MIME_" + itemData.getTypeData().getMimeType(),
                        "org/sleuthkit/autopsy/images/file_types.png",
                        itemData,
                        Children.create(new FileMimeSuffixFactory(itemData.getTypeData().getDataSourceId(), itemData.getTypeData().getMimeType()), true),
                        getDefaultLookup(itemData));
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                // GVDTODO
            }

        }
    }

    public static class FileMimeSuffixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

        private final String mimeTypePrefix;
        private final Long dataSourceId;

        private FileMimeSuffixFactory(Long dataSourceId, String mimeType) {
            this.dataSourceId = dataSourceId;
            this.mimeTypePrefix = mimeType;
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
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            AbstractFile file = getFileInDataSourceFromEvt(evt, dataSourceId);
            if (file == null || file.getMIMEType() == null) {
                return false;
            }

            return file.getMIMEType().toLowerCase().startsWith(this.mimeTypePrefix.toLowerCase());
        }

        static class FileMimeSuffixNode extends TreeNode<FileTypeMimeSearchParams> {

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

    public static class FileExtFactory extends TreeChildFactory<FileTypeExtensionsSearchParams> {

        private final Long dataSourceId;
        private final Collection<FileExtSearchFilter> childFilters;

        public FileExtFactory(Long dataSourceId) {
            this(dataSourceId, Stream.of(FileExtRootFilter.values()).collect(Collectors.toList()));
        }
        
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

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            AbstractFile file = getFileInDataSourceFromEvt(evt, this.dataSourceId);
            return file != null && this.childFilters.stream()
                    .anyMatch((filter) -> MainDAO.getInstance().getViewsDAO().isFilesByExtInvalidating(
                            new FileTypeExtensionsSearchParams(filter, this.dataSourceId), file));
        }

        static class FileExtNode extends TreeNode<FileTypeExtensionsSearchParams> {

            private final Collection<FileExtSearchFilter> childFilters;

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
                }
            }

        }
    }

}
