/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.hashdatabase;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.datamodel.FsContent;

/**
 *
 * @author dhurd
 */
public class HashDbSearchResultFactory extends ChildFactory<KeyValue> {
    Collection<KeyValue>  keyValues;
    Map<String, List<FsContent>> map;
    
    HashDbSearchResultFactory(Map<String, List<FsContent>> map, Collection<KeyValue> keyValues) {
        this.keyValues = keyValues;
        this.map = map;
    }
    
    @Override
    protected boolean createKeys(List<KeyValue> toPopulate) {
        toPopulate.addAll(keyValues);
        return true;
    }

    @Override
    protected Node createNodeForKey(KeyValue thing) {
        return new KeyValueNode(thing, null);
    }
}
