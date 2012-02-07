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
package org.sleuthkit.autopsy.keywordsearch;

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DisplayableItem;
import org.sleuthkit.datamodel.FsContent;

/**
 * Root Node for keyword search results
 */
class KeywordSearchNode extends AbstractNode {

    KeywordSearchNode(List<FsContent> keys, final String solrQuery) {
        super(new RootContentChildren(keys) {

            @Override
            protected Node[] createNodes(DisplayableItem key) {
                Node[] originalNodes = super.createNodes(key);
                Node[] filterNodes = new Node[originalNodes.length];

                // Use filter node to add a MarkupSource for the search results
                // to the lookup
                int i = 0;
                for (Node original : originalNodes) {
                    HighlightedMatchesSource markup = new HighlightedMatchesSource((Content)key, solrQuery);
                    filterNodes[i++] = new KeywordSearchFilterNode(markup, original, solrQuery);
                }

                return filterNodes;
            }
        });
    }
}
