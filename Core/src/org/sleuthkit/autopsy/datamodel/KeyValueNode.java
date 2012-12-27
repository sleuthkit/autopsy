/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.awt.datatransfer.Transferable;
import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.datatransfer.PasteType;
import org.openide.util.lookup.Lookups;

/**
 * Node that contains a KeyValue object. The node also has that KeyValue object
 * set to its lookup so that when the node is passed to the content viewers its
 * string will be displayed.
 * @author alawrence
 */
public class KeyValueNode extends AbstractNode {
    
    private KeyValue data;
    
    public KeyValueNode(KeyValue thing, Children children) {
        super(children, Lookups.singleton(thing));
        this.setName(thing.getName());
        this.data = thing;
    }
    
    public KeyValueNode(KeyValue thing, Children children, Lookup lookup) {
         super(children, lookup);
         this.setName(thing.getName());
         this.data = thing;
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
        ss.put(new NodeProperty("Name", "Name", "n/a", data.getName()));
        
        for (Map.Entry<String, Object> entry : data.getMap().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            ss.put(new NodeProperty(key, key, "n/a", value));
        }

        return s;
    }
}
