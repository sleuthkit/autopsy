/*
 * Autopsy Forensic Bitemser
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
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeItemDTO;

/**
 * A node to be displayed in the tree that shows the count.
 */
public abstract class TreeNode<T> extends AbstractNode implements SelectionResponder {

    private static final Logger logger = Logger.getLogger(TreeNode.class.getName());

    /**
     * Returns the default lookup based on the item dto.
     *
     * @param itemData The item dto data.
     *
     * @return The lookup to use in the node.
     */
    protected static <T> Lookup getDefaultLookup(TreeItemDTO<? extends T> itemData) {
        return Lookups.fixed(itemData, itemData.getTypeData());
    }

    private TreeItemDTO<? extends T> itemData;

    /**
     * Main constructor assuming a leaf node with default lookup.
     *
     * @param nodeName    The name of the node.
     * @param icon        The path of the icon or null.
     * @param itemData    The data to back the node.
     * @param dataObjType The type of the underlying data object within the
     *                    counts item dto.
     */
    protected TreeNode(String nodeName, String icon, TreeItemDTO<? extends T> itemData) {
        this(nodeName, icon, itemData, Children.LEAF, getDefaultLookup(itemData));
    }

    /**
     * Constructor.
     *
     * @param nodeName    The name of the node.
     * @param icon        The path of the icon or null.
     * @param itemData    The data to back the node. Must be non-null.
     * @param children    The children of this node.
     * @param lookup      The lookup for this node.
     * @param dataObjType The type of the underlying data object within the
     *                    counts item dto.
     */
    protected TreeNode(String nodeName, String icon, TreeItemDTO<? extends T> itemData, Children children, Lookup lookup) {
        super(children, lookup);
        setName(nodeName);
        if (icon != null) {
            setIconBaseWithExtension(icon);
        }
        update(itemData);
    }

    /**
     * @return The current backing item data.
     */
    protected TreeItemDTO<? extends T> getItemData() {
        return itemData;
    }

    /**
     * Sets the display name of the node to include the display name and count
     * of the item.
     *
     * @param itemData The item data.
     */
    protected void setDisplayName(TreeItemDTO<? extends T> itemData) {
        String displayName = itemData.getCount() == null
                ? itemData.getDisplayName()
                : MessageFormat.format("{0} ({1})", itemData.getDisplayName(), itemData.getCount());

        this.setDisplayName(displayName);
    }

    /**
     * Updates the backing data of this node.
     *
     * @param updatedData The updated data. Must be non-null.
     *
     * @thitems IllegalArgumentException
     */
    public void update(TreeItemDTO<? extends T> updatedData) {
        if (this.itemData != null && 
                (updatedData == null ||
                this.itemData.getId() != updatedData.getId() || 
                !this.itemData.getTypeData().equals(updatedData.getTypeData()))) {
            logger.log(Level.WARNING, MessageFormat.format(
                    "Expected update data to have same id and type data but received [id: {0}, type: {1}] replacing [id: {2}, type: {3}]",
                    (updatedData == null ? "<null>" : updatedData.getId()), 
                    (updatedData == null ? "<null>" : updatedData.getTypeData()),
                    this.itemData.getId(), 
                    this.itemData.getTypeData()));
            return;
        }
        this.itemData = updatedData;
        this.setDisplayName(updatedData);
    }
}
