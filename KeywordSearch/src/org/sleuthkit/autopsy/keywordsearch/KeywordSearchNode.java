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

import java.sql.SQLException;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;
import org.sleuthkit.autopsy.datamodel.ContentFilterNode;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.sleuthkit.autopsy.datamodel.ContentNodeVisitor;
import org.sleuthkit.autopsy.datamodel.RootContentChildren;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

/**
 * Root Node for keyword search results
 */
class KeywordSearchNode extends AbstractNode implements ContentNode {

    private String solrQuery;

    KeywordSearchNode(List<FsContent> keys, final String solrQuery) {
        super(new RootContentChildren(keys) {


            @Override
            protected Node[] createNodes(Content key) {
                Node[] originalNodes = super.createNodes(key);
                Node[] filterNodes = new Node[originalNodes.length];

                // Use filter node to add a MarkupSource for the search results
                // to the lookup
                int i = 0;
                for (Node original : originalNodes) {
                    MarkupSource markup = new HighlightedMatchesSource(key, solrQuery);
                    Lookup filterLookup = new ProxyLookup(Lookups.singleton(markup), original.getLookup());
                    filterNodes[i++] = new ContentFilterNode((ContentNode) original, null, filterLookup);
                }

                return filterNodes;
            }
        });

        this.solrQuery = solrQuery;
    }

    @Override
    public long getID() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Object[][] getRowValues(int rows) throws SQLException {
        int totalNodes = getChildren().getNodesCount();

        Object[][] objs;
        int maxRows = 0;
        if (totalNodes > rows) {
            objs = new Object[rows][];
            maxRows = rows;
        } else {
            objs = new Object[totalNodes][];
            maxRows = totalNodes;
        }

        for (int i = 0; i < maxRows; i++) {
            PropertySet[] props = getChildren().getNodeAt(i).getPropertySets();
            Property[] property = props[0].getProperties();
            objs[i] = new Object[property.length];

            for (int j = 0; j < property.length; j++) {
                try {
                    objs[i][j] = property[j].getValue();
                } catch (Exception ex) {
                    objs[i][j] = "n/a";
                }
            }
        }
        return objs;
    }

    @Override
    public byte[] read(long offset, long len) throws TskException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Content getContent() {
        return null;
    }

    @Override
    public String[] getDisplayPath() {
        return new String[]{"Solr query: " + this.solrQuery};
    }

    @Override
    public String[] getSystemPath() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T accept(ContentNodeVisitor<T> v) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
