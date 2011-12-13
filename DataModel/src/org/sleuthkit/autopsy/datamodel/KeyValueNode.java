
package org.sleuthkit.autopsy.datamodel;

import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;

public class KeyValueNode extends AbstractNode {
    
    KeyValueThing thing;
    
    public KeyValueNode(KeyValueThing thing, Children children) {
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
        
        for (Map.Entry<String, Object> entry : thing.getMap().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            ss.put(new NodeProperty(key, key, "n/a", value));
        }

        return s;
    }
}
