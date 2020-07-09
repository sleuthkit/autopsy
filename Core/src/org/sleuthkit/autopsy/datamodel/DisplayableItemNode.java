/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datamodel;

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An abstract base class for nodes that are eligible for display in the tree
 * view or results view. Capabilitites of a DisplayableItemNode include
 * accepting a DisplayableItemNodeVisitor, indicating whether or not the node is
 * a leaf node, providing an item type string suitable for use as a key, and
 * storing information about a child node that is to be selected if the node is
 * selected in the tree view.
 */
public abstract class DisplayableItemNode extends AbstractNode {

    /*
     * An item type shared by DisplayableItemNodes that can be the parents of
     * file nodes.
     */
    static final String FILE_PARENT_NODE_KEY = "orgsleuthkitautopsydatamodel" + "FileTypeParentNode";

    /**
     * Gets the file, if any, linked to an artifact via a TSK_PATH_ID attribute
     *
     * @param artifact The artifact.
     *
     * @return An AbstractFile or null.
     *
     * @throws TskCoreException
     */
    static AbstractFile findLinked(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardAttribute pathIDAttribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PATH_ID));
        if (pathIDAttribute != null) {
            long contentID = pathIDAttribute.getValueLong();
            if (contentID != -1) {
                return artifact.getSleuthkitCase().getAbstractFileById(contentID);
            }
        }
        return null;
    }

    private NodeSelectionInfo selectedChildNodeInfo;

    /**
     * Constructs a node that is eligible for display in the tree view or
     * results view. Capabilitites include accepting a
     * DisplayableItemNodeVisitor, indicating whether or not the node is a leaf
     * node, providing an item type string suitable for use as a key, and
     * storing information about a child node that is to be selected if the node
     * is selected in the tree view.
     *
     * @param children The Children object for the node.
     */
    public DisplayableItemNode(Children children) {
        super(children);
    }

    /**
     * Constructs a node that is eligible for display in the tree view or
     * results view. Capabilitites include accepting a
     * DisplayableItemNodeVisitor, indicating whether or not the node is a leaf
     * node, providing an item type string suitable for use as a key, and
     * storing information about a child node that is to be selected if the node
     * is selected in the tree view.
     *
     * @param children The Children object for the node.
     * @param lookup   The Lookup object for the node.
     */
    public DisplayableItemNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    /**
     * Accepts a visitor DisplayableItemNodeVisitor that will perform an
     * operation on this artifact type and return some object as the result of
     * the operation.
     *
     * @param visitor The visitor, where the type parameter of the visitor is
     *                the type of the object that will be returned as the result
     *                of the visit operation.
     *
     * @return An object of type T.
     */
    public abstract <T> T accept(DisplayableItemNodeVisitor<T> visitor);

    /**
     * Indicates whether or not the node is capable of having child nodes.
     * Should only return true if the node is ALWAYS a leaf node.
     *
     * @return True or false.
     */
    public abstract boolean isLeafTypeNode();

    /**
     * Gets the item type string of the node, suitable for use as a key.
     *
     * @return A String representing the item type of node.
     */
    public abstract String getItemType();

    /**
     * Adds information about which child node of this node, if any, should be
     * selected. Can be null.
     *
     * @param selectedChildNodeInfo The child node selection information.
     */
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo) {
        this.selectedChildNodeInfo = selectedChildNodeInfo;
    }

    /**
     * Gets information about which child node of this node, if any, should be
     * selected.
     *
     * @return The child node selection information, or null if no child should
     *         be selected.
     */
    public NodeSelectionInfo getChildNodeSelectionInfo() {
        return selectedChildNodeInfo;
    }

    /**
     * Updates the node property sheet by replacing existing properties with new
     * properties with the same property name.
     *
     * @param newProps The replacement property objects.
     */
    protected synchronized final void updatePropertySheet(NodeProperty<?>... newProps) {
        Sheet currentSheet = this.getSheet();
        Sheet.Set currentPropsSet = currentSheet.get(Sheet.PROPERTIES);
        Property<?>[] currentProps = currentPropsSet.getProperties();
        for (NodeProperty<?> newProp : newProps) {
            for (int i = 0; i < currentProps.length; i++) {
                if (currentProps[i].getName().equals(newProp.getName())) {
                    currentProps[i] = newProp;
                }
            }
        }
        currentPropsSet.put(currentProps);
        currentSheet.put(currentPropsSet);
        this.setSheet(currentSheet);
    }

}
