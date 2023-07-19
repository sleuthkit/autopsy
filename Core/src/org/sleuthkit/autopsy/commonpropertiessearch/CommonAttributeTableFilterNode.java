/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.commonpropertiessearch;

import org.openide.nodes.Node;
import org.sleuthkit.autopsy.corecomponents.TableFilterNode;

/**
 * Wrapper class used for the common attribute search results that
 * should display descendants to ensure
 * they are handled correctly in the result viewer.
 */
public class CommonAttributeTableFilterNode extends TableFilterNode {
    
    public CommonAttributeTableFilterNode(Node node, int childLayerDepth){
        super(node, childLayerDepth);
    }
}
