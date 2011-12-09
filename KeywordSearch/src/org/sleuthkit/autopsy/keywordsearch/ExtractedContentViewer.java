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

import java.awt.Component;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.sleuthkit.autopsy.datamodel.ContentNode;
import org.apache.commons.lang.StringEscapeUtils;

/**
 * Displays marked-up (HTML) content for a Node. The sources are all the 
 * MarkupSource items in the selected Node's lookup, plus the content that
 * Solr extracted (if there is any).
 */
@ServiceProvider(service = DataContentViewer.class)
public class ExtractedContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(ExtractedContentViewer.class.getName());
    private ExtractedContentPanel panel;

    public ExtractedContentViewer() {
    }

    @Override
    public void setNode(final ContentNode selectedNode) {

        // to clear the viewer
        if (selectedNode == null) {
            resetComponent();
            return;
        }

        // sources are custom markup from the node (if available) and default
        // markup is fetched from solr
        List<MarkupSource> sources = new ArrayList<MarkupSource>();

        sources.addAll(((Node) selectedNode).getLookup().lookupAll(MarkupSource.class));


        if (solrHasContent(selectedNode)) {
            
            sources.add(new MarkupSource() {

                @Override
                public String getMarkup() {
                    try {
                        String content = StringEscapeUtils.escapeHtml(getSolrContent(selectedNode));
                        return "<pre>" + content.trim() + "</pre>";
                    } catch (SolrServerException ex) {
                        logger.log(Level.WARNING, "Couldn't get extracted content.", ex);
                        return "";
                    }
                }

                @Override
                public String toString() {
                    return "Extracted Content";
                }
            });
            
        }

        // first source will be the default displayed
        setPanel(sources);
    }

    @Override
    public String getTitle() {
        return "Extracted Content";
    }

    @Override
    public DataContentViewer getInstance() {
        return new ExtractedContentViewer();
    }

    @Override
    public Component getComponent() {
        if (panel == null) {
            panel = new ExtractedContentPanel();
        }
        return panel;
    }

    @Override
    public void resetComponent() {
        setPanel(Collections.EMPTY_LIST);
    }

    @Override
    public boolean isSupported(ContentNode node) {
        if (node == null) {
            return false;
        }

        Collection<? extends MarkupSource> sources = ((Node) node).getLookup().lookupAll(MarkupSource.class);

        return !sources.isEmpty() || solrHasContent(node);
    }
    
    @Override
    public boolean isPreferred(ContentNode node) {
        return isSupported(node);
    }

    /**
     * Set the MarkupSources for the panel to display (safe to call even if the
     * panel hasn't been created yet)
     * @param sources 
     */
    private void setPanel(List<MarkupSource> sources) {
        if (panel != null) {
            panel.setSources(sources);
        }
    }

    /**
     * Check if Solr has extracted content for a given node
     * @param node
     * @return true if Solr has content, else false
     */
    private boolean solrHasContent(ContentNode node) {
        Server.Core solrCore = KeywordSearch.getServer().getCore();
        SolrQuery q = new SolrQuery();
        q.setQuery("*:*");
        q.addFilterQuery("id:" + node.getContent().getId());
        q.setFields("id");

        try {
            return !solrCore.query(q).getResults().isEmpty();
        } catch (SolrServerException ex) {
            logger.log(Level.WARNING, "Couldn't determine whether content is supported.", ex);
            return false;
        }
    }

    /**
     * Get extracted content for a node from Solr
     * @param cNode a node that has extracted content in Solr (check with
     * solrHasContent(ContentNode))
     * @return the extracted content
     * @throws SolrServerException if something goes wrong
     */
    private String getSolrContent(ContentNode cNode) throws SolrServerException {
        Server.Core solrCore = KeywordSearch.getServer().getCore();
        SolrQuery q = new SolrQuery();
        q.setQuery("*:*");
        q.addFilterQuery("id:" + cNode.getContent().getId());
        q.setFields("content");

        String content = (String) solrCore.query(q).getResults().get(0).getFieldValue("content");
        return content;
    }
}
