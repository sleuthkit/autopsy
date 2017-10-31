/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.communications;

import java.util.ArrayList;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Sheet;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactNode;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 */
public class RelationShipFilterNode extends FilterNode {


    public RelationShipFilterNode(BlackboardArtifactNode wrappedNode) {
        super(wrappedNode, Children.LEAF);
        setDisplayName( StringUtils.stripEnd(wrappedNode.getArtifact().getDisplayName(),"s"));
    }

    @Override
    public PropertySet[] getPropertySets() {
        PropertySet[] propertySets = super.getPropertySets();

        HashSet<String> propertyNames = new HashSet<>();
        propertyNames.add("Source File");
        propertyNames.add("Data Source");
        propertyNames.add("Path");
        propertyNames.add("Message ID");
        propertyNames.add("Tags");
        propertyNames.add("Text");
        propertyNames.add("Read");
        propertyNames.add("Directon");
        propertyNames.add("Name");
        propertyNames.add("Message (Plaintext)");

        ArrayList<PropertySet> retPropSets = new ArrayList<>();
        boolean first = true;
        for (PropertySet set : propertySets) {
            Sheet.Set set1 = copySet(set);
            if (first) {
                first = false;
                set1.put(new NodeProperty<>("Type", "Type", "Type", getDisplayName()));
            }

            for (Property<?> p : set.getProperties()) {
                if (false == propertyNames.contains(p.getName())) {
                    set1.put(p);
                }
            }
            retPropSets.add(set1);
        }
        return retPropSets.toArray(new PropertySet[retPropSets.size()]);
    }
    private static final BlackboardAttribute.Type MSG_TYPE = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE);

    private Sheet.Set copySet(PropertySet set) {
        Sheet.Set set1 = new Sheet.Set();
        set1.setName(set.getName());
        set1.setDisplayName(set.getDisplayName());
        set1.setShortDescription(set.getShortDescription());
        return set1;
    }
}
