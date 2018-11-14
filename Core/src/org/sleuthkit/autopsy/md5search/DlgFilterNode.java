/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.NodeSelectionInfo;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;

/**
 * //DLG:
 */
public class DlgFilterNode extends FilterNode {

    private final boolean createChildren;
    private final boolean forceUseWrappedDisplayName;
    private String columnOrderKey = "NONE";
    
    public DlgFilterNode(Node node, boolean createChildren) {
        super(node, DlgFilterChildren.createInstance(node, createChildren), Lookups.proxy(node));
        this.forceUseWrappedDisplayName = false;
        this.createChildren = createChildren;
    }
    
    public DlgFilterNode(Node node, boolean createChildren, String columnOrderKey) {
        super(node, DlgFilterChildren.createInstance(node, createChildren), Lookups.proxy(node));
        this.forceUseWrappedDisplayName = false;
        this.createChildren = createChildren;
        this.columnOrderKey = columnOrderKey;
    }
    
    /*public DlgFilterNode(Node node, int childLayerDepth) {
        super(node, TableFilterChildrenWithDescendants.createInstance(node, childLayerDepth), Lookups.proxy(node));
        this.createChildren = true;
        this.forceUseWrappedDisplayName = true;
    }*/
    
    @Override
    public String getDisplayName() {
        if (this.forceUseWrappedDisplayName) {
            return super.getDisplayName();
        } else if (createChildren) {
            return NbBundle.getMessage(this.getClass(), "TableFilterNode.displayName.text");
        } else {
            return super.getDisplayName();
        }
    }
    
    public void setChildNodeSelectionInfo(NodeSelectionInfo selectedChildNodeInfo) {
        /*
         * Currently, child selection is only supported for nodes selected in
         * the tree view and decorated with a DataResultFilterNode.
         */
        if (getOriginal() instanceof DataResultFilterNode) {
            ((DataResultFilterNode) getOriginal()).setChildNodeSelectionInfo(selectedChildNodeInfo);
        }
    }
    
    public NodeSelectionInfo getChildNodeSelectionInfo() {
        /*
         * Currently, child selection is only supported for nodes selected in
         * the tree view and decorated with a DataResultFilterNode.
         */
        if (getOriginal() instanceof DataResultFilterNode) {
            return ((DataResultFilterNode) getOriginal()).getChildNodeSelectionInfo();
        } else {
            return null;
        }
    }
    
    public String getColumnOrderKey() {
        return columnOrderKey;
    }
}
