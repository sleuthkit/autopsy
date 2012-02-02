
package org.sleuthkit.autopsy.datamodel;

import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;

public class KeyValueNode extends AbstractNode {
    
    KeyValue thing;
    
    public KeyValueNode(KeyValue thing, Children children) {
        super(children);
        this.setName(thing.getName());
        this.thing = thing;
    }
    
    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        Sheet.Set ss = s.get(Sheet.PROPERTIES);
        if (ss == null) {
            ss = Sheet.createPropertiesSet();
            s.put(ss);
        }
        
        // table view drops first column of properties under assumption
        // that it contains the node's name
        ss.put(new NodeProperty("Name", "Name", "n/a", thing.getName()));
        
        for (Map.Entry<String, Object> entry : thing.getMap().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            ss.put(new NodeProperty(key, key, "n/a", value));
        }

        return s;
    }
}
