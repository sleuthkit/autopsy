/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.md5search;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;

/**
 * //DLG:
 */
final class CorrelationAttributeInstanceChildNodeFactory extends ChildFactory<KeyValue> {
    private final Collection<CorrelationAttributeInstance> correlationInstances;
    
    CorrelationAttributeInstanceChildNodeFactory(Collection<CorrelationAttributeInstance> correlationInstances) {
        this.correlationInstances = correlationInstances;
    }

    @Override
    protected boolean createKeys(List<KeyValue> list) {
        for (CorrelationAttributeInstance instance : correlationInstances) {
            Map<String, Object> properties = new HashMap<>(); //DLG:
            
            final String caseName = instance.getCorrelationCase().getDisplayName();
            final String dataSourceName = instance.getCorrelationDataSource().getName();
            //DLG: final ? knownStatus
            final String fullPath = instance.getFilePath();
            //DLG: final String comment
            //DLG: final String deviceId

            final File file = new File(fullPath);
            final String name = file.getName();
            final String parent = file.getParent();
            
            properties.put("caseName", caseName);
            properties.put("dataSourceName", dataSourceName);
            properties.put("knownStatus", ""); //DLG:
            properties.put("fullPath", fullPath);
            properties.put("comment", ""); //DLG:
            properties.put("deviceId", ""); //DLG:
            properties.put("name", name);
            properties.put("parent", parent);
            
            list.add(new KeyValue(String.valueOf(instance.getID()), properties, instance.getID()));
        }
        return true;
    }

    @Override
    protected Node createNodeForKey(KeyValue key) {
        Map<String, Object> map = key.getMap();
        Node kvNode = new KeyValueNode(key, Children.LEAF, Lookups.fixed(correlationInstances.toArray()));
        //DLG: Node resultNode = new CorrelationAttributeInstanceChildNode(kvNode);
        return null;
    }
}
