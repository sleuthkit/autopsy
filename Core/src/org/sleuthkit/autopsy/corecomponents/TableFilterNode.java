/*
 * 
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.corecomponents;

import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;

/**
 * Specifies behavior of nodes which are displayed in the DataResultTopComponent.
 */
public interface TableFilterNode {

    /**
     * Gets information about which child node of this node, if any, should be
     * selected.
     *
     * @return The child node selection information, or null if no child should
     * be selected.
     */
    public NodeSelectionInfo getChildNodeSelectionInfo();

    /**
     * @return the column order key, which allows custom column ordering to be
     * written into a properties file and be reloaded for future use in a table
     * with the same root node or for different cases. This is done by
     * DataResultViewerTable. The key should represent what kinds of items the
     * table is showing.
     */
    public String getColumnOrderKey();

    /**
     * Gets the display name for the wrapped node, for use in the first column
     * of an Autopsy table view.
     *
     * @return The display name.
     */
    public String getDisplayName();

    public String getParentDisplayName();

    /**
     * Adds information about which child node of this node, if any, should be
     * selected. Can be null.
     *
     * @param selectedChildNodeInfo The child node selection information.
     */
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo);
}
