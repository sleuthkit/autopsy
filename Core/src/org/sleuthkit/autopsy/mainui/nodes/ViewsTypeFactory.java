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
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.mainui.datamodel.FileTypeSizeSearchParams;
import org.sleuthkit.autopsy.mainui.datamodel.MainDAO;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO;

/**
 *
 * Factories for displaying views.
 */
public class ViewsTypeFactory {

    public static class FileSizeTypeFactory extends TreeNode<FileTypeSizeSearchParams> {

        /**
         * Main constructor.
         *
         * @param itemData The data to display.
         */
        public FileSizeTypeFactory(TreeResultsDTO.TreeItemDTO<? extends FileTypeSizeSearchParams> itemData) {
            super(itemData.getTypeId(),
                    ICON_TBD,
                    itemData);
        }

        @Override
        public void respondSelection(DataResultTopComponent dataResultPanel) {
            dataResultPanel.displayFileSizes(this.getItemData().getTypeData());
        }

        @Override
        public boolean isRefreshRequired(PropertyChangeEvent evt) {
            MainDAO.getInstance().getViewsDAO().isFilesBySizeInvalidating(this.getItemData().getTypeData(), evt);
        }
    }

}
