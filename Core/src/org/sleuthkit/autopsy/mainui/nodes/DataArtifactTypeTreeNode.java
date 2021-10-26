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

import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.mainui.datamodel.CountsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactSearchParam;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Display name and count of a data artifact type in the tree.
 */
public class DataArtifactTypeTreeNode extends TreeCountNode<DataArtifactSearchParam> {
    private static String getIconPath(BlackboardArtifact.Type artType) {
            String iconPath = IconsUtil.getIconFilePath(artType.getTypeID());
            return iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath;
    }
    
    
    public DataArtifactTypeTreeNode(CountsRowDTO<DataArtifactSearchParam> rowData) {
        super(rowData.getTypeData().getArtifactType().getTypeName(), 
                getIconPath(rowData.getTypeData().getArtifactType()), 
                rowData, 
                DataArtifactSearchParam.class);
    }
    

    @Override
    public void respondSelection(DataResultTopComponent dataResultPanel) {
        dataResultPanel.displayDataArtifact(this.getRowData().getTypeData());
    }
}
