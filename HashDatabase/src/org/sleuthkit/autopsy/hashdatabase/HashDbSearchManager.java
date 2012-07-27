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
package org.sleuthkit.autopsy.hashdatabase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.datamodel.FsContent;

/**
 *
 */
public class HashDbSearchManager {
    Map<String, List<FsContent>> map;
    List<KeyValue> keyValues;
    
    HashDbSearchManager(Map<String, List<FsContent>> map) {
        this.map = map;
        init();
    }
    
    private void init() {
        keyValues = new ArrayList<KeyValue>();
        int id = 0;
        for(String s : map.keySet()) {
            for(FsContent file : map.get(s)) {
                Map<String, Object> keyMap = new LinkedHashMap<String, Object>();
                keyMap.put("Hash", s);
                AbstractFsContentNode.fillPropertyMap(keyMap, file);
                KeyValue kv = new KeyValue("MD5 - Name", keyMap, ++id);
                keyValues.add(kv);
            }
        }
    }

    public void execute() {
        Collection<KeyValue> things = keyValues;
        Node rootNode = null;

        if (things.size() > 0) {
            Children childThingNodes =
                    Children.create(new HashDbSearchResultFactory(map, things), true);

            rootNode = new AbstractNode(childThingNodes);
        } else {
            rootNode = Node.EMPTY;
        }

        final String pathText = "MD5 Hash Search";
        TopComponent searchResultWin = DataResultTopComponent.createInstance("MD5 Hash Search", pathText, rootNode, things.size());
        searchResultWin.requestActive();
    }
}
