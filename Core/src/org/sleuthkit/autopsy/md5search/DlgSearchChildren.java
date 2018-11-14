/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.util.List;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.directorytree.DataResultFilterNode;
import org.sleuthkit.autopsy.directorytree.DirectoryTreeTopComponent;

/**
 * //DLG:
 */
class DlgSearchChildren extends Children.Keys<CorrelationAttributeInstance> {
    
    DlgSearchChildren(boolean lazy, List<CorrelationAttributeInstance> fileList) {
        super(lazy);
        this.setKeys(fileList);
    }

    @Override
    protected Node[] createNodes(CorrelationAttributeInstance t) {
        //DLG:
        Node[] node = new Node[1];
        //DLG:
        node[0] = new DlgCorrelationAttributeInstanceNode(t);
        return node;
    }
}
