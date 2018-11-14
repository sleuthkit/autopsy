/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * //DLG:
 */
class DlgSearchNode extends AbstractNode {

    private DlgSearchChildren children;

    DlgSearchNode(List<CorrelationAttributeInstance> keys) {
        super(new DlgSearchChildren(true, keys));
        this.children = (DlgSearchChildren) this.getChildren();
    }

    @Override
    public String getName() {
        //DLG:
        return /*NbBundle.getMessage(this.getClass(), */"SearchNode.getName.text"/*)*/;
    }
}
