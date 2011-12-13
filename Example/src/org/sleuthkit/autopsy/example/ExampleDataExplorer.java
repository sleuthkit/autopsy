/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.example;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataExplorer;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;


@ServiceProvider(service = DataExplorer.class)
public class ExampleDataExplorer implements DataExplorer {
    
    ExampleTopComponent tc;
    
    public ExampleDataExplorer() {
        tc = new ExampleTopComponent(this);
        tc.setName("Example");
    }

    @Override
    public org.openide.windows.TopComponent getTopComponent() {
        return tc;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        // nothing to do in simple example
    }
    
    static final int NUMBER_THING_ID = 41234;
    
    void makeNodes() {
        Collection<KeyValueThing> things = new ArrayList<KeyValueThing>();
        
        for (int i = 1; i <= 10; i++) {
              for (int j = 1; j <= 10; j++) {
                  Map<String, Object> kvs = new LinkedHashMap<String, Object>();
                  kvs.put("x", i);
                  kvs.put("y", j);
                  kvs.put("sum", i+j);
                  kvs.put("product", i*j);
                  
                  things.add(new KeyValueThing(i + " and " + j, kvs,
                          NUMBER_THING_ID));
            }
        }
        
        Children childThingNodes = 
               Children.create(new ExampleKeyValueChildFactory(things), true);
        
        Node rootNode = new AbstractNode(childThingNodes);
        String pathText = "foo";
        
        TopComponent searchResultWin =
                DataResultTopComponent.createInstance("Keyword search",
                pathText, rootNode, things.size());
        searchResultWin.requestActive(); // make it the active top component
    }
}
