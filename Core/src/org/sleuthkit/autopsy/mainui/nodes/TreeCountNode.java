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

import java.text.MessageFormat;
import java.util.logging.Level;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.CountsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.RowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;

/**
 * A node to be displayed in the tree that shows the count.
 */
public abstract class TreeCountNode<T> extends UpdatableNode implements SelectionResponder {

    private static final Logger logger = Logger.getLogger(TreeCountNode.class.getName());

    /**
     * Returns the default lookup based on the row dto.
     *
     * @param rowData The row dto data.
     *
     * @return The lookup to use in the node.
     */
    protected static <T> Lookup getDefaultLookup(CountsRowDTO<T> rowData) {
        return Lookups.fixed(rowData, rowData.getTypeData());
    }

    private final Class<?> dataObjType;
    private CountsRowDTO<T> rowData;

    /**
     * Main constructor assuming a leaf node with default lookup.
     *
     * @param nodeName    The name of the node.
     * @param icon        The path of the icon or null.
     * @param rowData     The data to back the node.
     * @param dataObjType The type of the underlying data object within the
     *                    counts row dto.
     */
    protected TreeCountNode(String nodeName, String icon, CountsRowDTO<T> rowData, Class<T> dataObjType) {
        this(nodeName, icon, rowData, Children.LEAF, getDefaultLookup(rowData), dataObjType);
    }

    /**
     * Constructor.
     *
     * @param nodeName    The name of the node.
     * @param icon        The path of the icon or null.
     * @param rowData     The data to back the node. Must be non-null.
     * @param children    The children of this node.
     * @param lookup      The lookup for this node.
     * @param dataObjType The type of the underlying data object within the
     *                    counts row dto.
     */
    protected TreeCountNode(String nodeName, String icon, CountsRowDTO<T> rowData, Children children, Lookup lookup, Class<T> dataObjType) {
        super(children, lookup);
        setName(nodeName);
        if (icon != null) {
            setIconBaseWithExtension(icon);
        }
        updateData(rowData);
        this.dataObjType = dataObjType;
    }

    /**
     * @return The current backing row data.
     */
    protected CountsRowDTO<T> getRowData() {
        return rowData;
    }

    /**
     * Sets the display name of the node to include the display name and count
     * of the row.
     *
     * @param rowData The row data.
     */
    protected void setDisplayName(CountsRowDTO<T> rowData) {
        this.setDisplayName(MessageFormat.format("{0} ({1})", rowData.getDisplayName(), rowData.getCount()));
    }

    /**
     * Updates the backing data of this node.
     *
     * @param updatedData The updated data. Must be non-null.
     *
     * @throws IllegalArgumentException
     */
    private void updateData(CountsRowDTO<T> updatedData) {
        this.rowData = updatedData;
        this.setDisplayName(updatedData);
    }

    @Override
    public void update(SearchResultsDTO results, RowDTO row) {
        if (row instanceof CountsRowDTO && ((CountsRowDTO<?>) row).getTypeData().getClass().isAssignableFrom(this.dataObjType)) {
            updateData((CountsRowDTO<T>) row);
        } else {
            logger.log(Level.WARNING, MessageFormat.format("Unable to cast row dto {0} to generic of {1}", row, dataObjType));
        }
    }
}
