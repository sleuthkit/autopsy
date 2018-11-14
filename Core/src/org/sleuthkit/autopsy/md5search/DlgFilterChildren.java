/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import org.openide.nodes.Children;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;

/**
 * //DLG:
 */
class DlgFilterChildren extends FilterNode.Children {
    
    public static Children createInstance(Node wrappedNode, boolean createChildren) {

        if (createChildren) {
            return new DlgFilterChildren(wrappedNode);
        } else {
            return Children.LEAF;
        }
    }
    
    DlgFilterChildren(Node wrappedNode) {
        super(wrappedNode);
    }
    
    @Override
    protected Node copyNode(Node nodeToCopy) {
        return new DlgFilterNode(nodeToCopy, false);
    }
    
    @Override
    protected Node[] createNodes(Node key) {
        return new Node[]{this.copyNode(key)};
    }
}
