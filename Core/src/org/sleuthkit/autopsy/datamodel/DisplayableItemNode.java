/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2011 - 2015 Basis Technology Corp.
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

import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.util.Lookup;

/**
 * A DisplayableItem is any node in the Autopsy directory tree. All of the nodes
 * in the tree will eventually extend this. This includes the data source,
 * views, extracted results, etc. areas.
 */
public abstract class DisplayableItemNode extends AbstractNode {

    public DisplayableItemNode(Children children) {
        super(children);
    }

    public DisplayableItemNode(Children children, Lookup lookup) {
        super(children, lookup);
    }

    public abstract boolean isLeafTypeNode();

    public abstract <T> T accept(DisplayableItemNodeVisitor<T> v);
}
