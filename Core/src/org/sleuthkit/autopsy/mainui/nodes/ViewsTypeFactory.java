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
import java.util.concurrent.ExecutionException;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.ingest.ModuleContentEvent;
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

    private static AbstractFile getFileFromEvt(PropertyChangeEvent evt, Long dataSourceId) {
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
            AbstractFile evtFile = getFileFromEvt(evt, this.dataSourceId);
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
                super("FILE_SIZE", "org/sleuthkit/autopsy/images/file-size-16.png", itemData);
            }

            @Override
            public void respondSelection(DataResultTopComponent dataResultPanel) {
                dataResultPanel.displayFileSizes(this.getItemData().getTypeData());
            }

        }
    }

    public static class FileMimePrefixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

    }

    public static class FileMimeSuffixFactory extends TreeChildFactory<FileTypeMimeSearchParams> {

    }

    public static class FileExtFactory extends TreeChildFactory<FileTypeExtensionsSearchParams> {

    }

}
