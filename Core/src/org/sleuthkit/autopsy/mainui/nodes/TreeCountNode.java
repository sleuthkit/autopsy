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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.python.icu.text.MessageFormat;
import org.sleuthkit.autopsy.mainui.datamodel.CountsRowDTO;

/**
 * A node to be displayed in the tree that shows the count.
 */
public abstract class TreeCountNode<T> extends AbstractNode implements SelectionResponder {

    /**
     * Returns the default lookup based on the row dto.
     * @param rowData The row dto data.
     * @return The lookup to use in the node.
     */
    protected static <T> Lookup getDefaultLookup(CountsRowDTO<T> rowData) {
        return Lookups.fixed(rowData, rowData.getTypeData());
    }

    private CountsRowDTO<T> rowData;

    /**
     * Main constructor assuming a leaf node with default lookup.
     * @param rowData The data to back the node.
     */
    public TreeCountNode(CountsRowDTO<T> rowData) {
        this(rowData, Children.LEAF, getDefaultLookup(rowData));
    }

    /**
     * Constructor.
     * @param rowData The data to back the node.  Must be non-null.
     * @param children The children of this node.
     * @param lookup The lookup for this node.
     */
    protected TreeCountNode(CountsRowDTO<T> rowData, Children children, Lookup lookup) {
        super(children, lookup);
        updateData(rowData);
    }

    protected CountsRowDTO<T> getRowData() {
        return rowData;
    }

    /**
     * Sets the display name of the node to include the display name and count of the row.
     * @param rowData The row data.
     */
    protected void setDisplayName(CountsRowDTO<T> rowData) {
        this.setDisplayName(MessageFormat.format("{0} ({1})", rowData.getDisplayName(), rowData.getCount()));
    }

    /**
     * Updates the backing data of this node.
     * @param updatedData The updated data.  Must be non-null.
     * @throws IllegalArgumentException
     */
    public void updateData(CountsRowDTO<T> updatedData) {
        this.rowData = updatedData;
        this.setDisplayName(updatedData);
    }
}
