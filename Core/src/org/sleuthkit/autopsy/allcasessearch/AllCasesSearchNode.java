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
import org.openide.nodes.AbstractNode;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;

/**
 * Parent node to AllCasesSearchChildren.
 */
class AllCasesSearchNode extends AbstractNode {

    /**
     * Create an instance of AllCasesSearchNode.
     * 
     * @param keys The list of CorrelationAttributeInstances.
     */
    AllCasesSearchNode(List<CorrelationAttributeInstance> keys) {
        super(new AllCasesSearchChildren(true, keys));
    }

    @Messages({
        "AllCasesSearchNode.getName.text=Other Cases Search"
    })
    @Override
    public String getName() {
        return Bundle.AllCasesSearchNode_getName_text();
    }
}
