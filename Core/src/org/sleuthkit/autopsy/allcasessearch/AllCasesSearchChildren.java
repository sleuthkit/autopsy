/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.allcasessearch;

import java.util.List;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * Creates CorrelationAttributeInstanceNodes from a collection of
 * CorrelationAttributeInstances.
 */
class AllCasesSearchChildren extends Children.Keys<CorrelationAttributeInstance> {
    
    /**
     * Create an instance of AllCasesSearchChildren.
     * 
     * @param lazy     Lazy load?
     * @param fileList List of CorrelationAttributeInstances.
     */
    AllCasesSearchChildren(boolean lazy, List<CorrelationAttributeInstance> instances) {
        super(lazy);
        this.setKeys(instances);
    }

    @Override
    protected Node[] createNodes(CorrelationAttributeInstance t) {
        Node[] node = new Node[1];
        node[0] = new CorrelationAttributeInstanceNode(t);
        return node;
    }
}
