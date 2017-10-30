/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.ArrayList;
import java.util.HashSet;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardAttribute;

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

        HashSet<String> suppressedPropertyNames = new HashSet<>();
        suppressedPropertyNames.add("Source File");
        suppressedPropertyNames.add("Data Source");
        suppressedPropertyNames.add("Path");
        suppressedPropertyNames.add("Message (Plaintext)");

        ArrayList<PropertySet> retPropSets = new ArrayList<>();
        boolean first = false;
        for (PropertySet set : propertySets) {
            Sheet.Set set1 = copySet(set);
            if (first){
                first = false;
                
                String valueString = wrappedNode.getArtifact().getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE)).getValueString();
                set1.put(new NodeProperty<>("Type", "Type", "Type", valueString));
            }

            for (Property<?> p : set.getProperties()) {
                if (false == suppressedPropertyNames.contains(p.getName())) {
                    set1.put(p);
                }
            }
            retPropSets.add(set1);
        }
        return retPropSets.toArray(new PropertySet[retPropSets.size()]);
    }

    private Sheet.Set copySet(PropertySet set) {
        Sheet.Set set1 = new Sheet.Set();
        set1.setName(set.getName());
        set1.setDisplayName(set.getDisplayName());
        set1.setShortDescription(set.getShortDescription());
        return set1;
    }
}
