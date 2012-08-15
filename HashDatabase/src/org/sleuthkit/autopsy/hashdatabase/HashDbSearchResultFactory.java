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

import java.util.Collection;
import java.util.List;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

/**
 * Factory class to create a KeyValueFileNode for each KeyValueContent in the Collection.
 */
public class HashDbSearchResultFactory extends ChildFactory<KeyValueContent> {
    Collection<KeyValueContent>  kvContents;
    
    HashDbSearchResultFactory(Collection<KeyValueContent> kvContents) {
        this.kvContents = kvContents;
    }
    
    @Override
    protected boolean createKeys(List<KeyValueContent> toPopulate) {
        toPopulate.addAll(kvContents);
        return true;
    }

    @Override
    protected Node createNodeForKey(KeyValueContent content) {
        return new KeyValueFileNode(content, Children.LEAF);
    }
}
