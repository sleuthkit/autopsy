/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import org.openide.nodes.FilterNode;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;

/**
 *
 */
public class RelationShipFilterNode extends FilterNode {

    private final BlackboardArtifactNode wrappedNode;

    public RelationShipFilterNode(BlackboardArtifactNode wrappedNode) {
        super(wrappedNode, Children.LEAF);
        this.wrappedNode = wrappedNode;
    }

    @Override
    public PropertySet[] getPropertySets() {
        PropertySet[] propertySets = super.getPropertySets();
        for (PropertySet set : propertySets) {
            for (Property<?> p : set.getProperties()) {
//                p.getName().equals();
            }
        }
        return propertySets;
    }
}
