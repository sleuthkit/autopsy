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

import org.sleuthkit.autopsy.corecomponents.SelectionResponder;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.logging.Level;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.mainui.datamodel.TreeResultsDTO.TreeDisplayCount;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;
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
        return Lookups.fixed(itemData, itemData.getSearchParams());
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
     * @param prevData The previous item data (may be null).
     * @param curData The item data (must be non-null).
     */
    protected void updateDisplayName(TreeItemDTO<? extends T> prevData, TreeItemDTO<? extends T> curData) {
        // update display name only if there is a change.
        if (prevData == null
                || !prevData.getDisplayName().equals(curData.getDisplayName())
                || !Objects.equals(prevData.getDisplayCount(), curData.getDisplayCount())) {
            String displayName = curData.getDisplayCount() == null
                    ? curData.getDisplayName()
                    : curData.getDisplayName() + curData.getDisplayCount().getDisplaySuffix();

            this.setDisplayName(displayName);
        }
    }

    /**
     * Updates the backing data of this node.
     *
     * @param updatedData The updated data. Must be non-null.
     *
     * @thitems IllegalArgumentException
     */
    public void update(TreeItemDTO<? extends T> updatedData) {
        if (updatedData == null) {
            logger.log(Level.WARNING, "Expected non-null updatedData");
        } else if (this.itemData != null && !Objects.equals(this.itemData.getId(), updatedData.getId())) {
            logger.log(Level.WARNING, MessageFormat.format(
                    "Expected update data to have same id but received [id: {0}] replacing [id: {1}]",
                    updatedData.getId(),
                    this.itemData.getId()));
            return;
        }

        TreeItemDTO<? extends T> prevData = this.itemData;
        this.itemData = updatedData;
        updateDisplayName(prevData, updatedData);
    }

    @Override
    public void respondSelection(DataResultTopComponent dataResultPanel) {
        dataResultPanel.setNode(this);
    }
    
     
    @Override
    public Action getPreferredAction() {
        // TreeNodes are used for both the result viewer and the tree viewer. For the result viewer,
        // we want to open the child of the double-clicked node. For the tree viewer, we want the default
        // action (explanding/closing the node). If getOpenChildAction() returns null, we likely
        // have a tree node and want to call the default preferred action.
        Action openChildAction = DirectoryTreeTopComponent.getOpenChildAction(getName());
        if (openChildAction == null) {
            return super.getPreferredAction();
        }
        return openChildAction;
    }
    
    /**
     * Tree node for displaying static content in the tree.
     */
    public static class StaticTreeNode extends TreeNode<String> {
        public StaticTreeNode(String nodeName, String displayName, String icon) {
            this(nodeName, displayName, icon, Children.LEAF);
        }
        
        public StaticTreeNode(String nodeName, String displayName, String icon, ChildFactory<?> childFactory) {
            this(nodeName, displayName, icon, Children.create(childFactory, true), null);
        }
                
        public StaticTreeNode(String nodeName, String displayName, String icon, Children children) {
            this(nodeName, displayName, icon, children, null);
        }
        
        public StaticTreeNode(String nodeName, String displayName, String icon, Children children, Lookup lookup) {
            super(nodeName, icon, new TreeItemDTO<String>(nodeName, nodeName, nodeName, displayName, TreeDisplayCount.NOT_SHOWN), children, lookup);
        }   
    }    
}
