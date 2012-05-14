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
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.datamodel.HighlightLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;

/**
 * Displays marked-up (HTML) content for a Node. The sources are all the 
 * MarkupSource items in the selected Node's lookup, plus the content that
 * Solr extracted (if there is any).
 */
@ServiceProvider(service = DataContentViewer.class)
public class ExtractedContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(ExtractedContentViewer.class.getName());
    private ExtractedContentPanel panel;
    private ExtractedContentFind find;
    private ExtractedContentPaging paging;
    private Node currentNode = null;
    private MarkupSource currentSource = null;

    public ExtractedContentViewer() {
        find = new ExtractedContentFind();
        paging = new ExtractedContentPaging();
    }

    @Override
    public void setNode(final Node selectedNode) {

        // to clear the viewer
        if (selectedNode == null) {
            currentNode = null;
            resetComponent();
            return;
        }

        this.currentNode = selectedNode;

        // sources are custom markup from the node (if available) and default
        // markup is fetched from solr
        List<MarkupSource> sources = new ArrayList<MarkupSource>();

        //add additional registered sources for this node
        sources.addAll(selectedNode.getLookup().lookupAll(MarkupSource.class));

        if (solrHasContent(selectedNode)) {
            Content content = selectedNode.getLookup().lookup(Content.class);
            if (content == null) {
                return;
            }

            //add to page tracking if not there yet
            final long contentID = content.getId();

            MarkupSource newSource = new MarkupSource() {

                @Override
                public String getMarkup(int pageNum) {
                    try {
                        String content = StringEscapeUtils.escapeHtml(getSolrContent(selectedNode, this));
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

                @Override
                public boolean isSearchable() {
                    return false;
                }

                @Override
                public String getAnchorPrefix() {
                    return "";
                }

                @Override
                public int getNumberHits() {
                    return 0;
                }

                @Override
                public int getNumberPages() {
                    final Server solrServer = KeywordSearch.getServer();
                    int numChunks = 0;
                    try {
                        numChunks = solrServer.queryNumFileChunks(contentID);

                    } catch (SolrServerException ex) {
                        logger.log(Level.WARNING, "Could not get number of chunks: ", ex);

                    } catch (NoOpenCoreException ex) {
                        logger.log(Level.WARNING, "Could not get number of chunks: ", ex);
                    }
                    return numChunks;
                }
            };

            currentSource = newSource;
            sources.add(newSource);

            //init paging for all sources for this node, if not inited
            for (MarkupSource source : sources) {
                if (!paging.isTracked(source)) {
                    int numPages = source.getNumberPages();
                    paging.add(source, numPages);
                }
            }

            final int totalPages = paging.getTotalPages(newSource);
            final int currentPage = paging.getCurrentPage(newSource);

            updatePageControls(currentPage, totalPages);
        }


        // first source will be the default displayed
        setPanel(sources);
        // If node has been selected before, return to the previous position
        // using invokeLater to wait for ComboBox selection to complete
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                MarkupSource source = panel.getSelectedSource();
                if (source != null) {
                    panel.scrollToAnchor(source.getAnchorPrefix() + Long.toString(find.getCurrentIndexI(source)));
                }
            }
        });
    }

    @Override
    public String getTitle() {
        return "Text View";
    }

    @Override
    public String getToolTip() {
        return "Displays extracted text and keyword-search results.";
    }

    @Override
    public DataContentViewer getInstance() {
        return new ExtractedContentViewer();
    }

    @Override
    public Component getComponent() {
        if (panel == null) {
            panel = new ExtractedContentPanel(this);
            panel.addPrevMatchControlListener(new PrevFindActionListener());
            panel.addNextMatchControlListener(new NextFindActionListener());
            panel.addPrevPageControlListener(new PrevPageActionListener());
            panel.addNextPageControlListener(new NextPageActionListener());
            panel.addSourceComboControlListener(new SourceChangeActionListener());
        }
        return panel;
    }

    @Override
    public void resetComponent() {
        setPanel(new ArrayList<MarkupSource>());
        panel.resetDisplay();
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }

        Collection<? extends MarkupSource> sources = node.getLookup().lookupAll(MarkupSource.class);
        HighlightLookup highlight = node.getLookup().lookup(HighlightLookup.class);

        return !sources.isEmpty() || highlight != null || solrHasContent(node);
    }

    @Override
    public boolean isPreferred(Node node,
            boolean isSupported) {
        BlackboardArtifact art = node.getLookup().lookup(BlackboardArtifact.class);
        return isSupported && (art == null || art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID());
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
    private boolean solrHasContent(Node node) {
        Content content = node.getLookup().lookup(Content.class);
        if (content == null) {
            return false;
        }

        final Server solrServer = KeywordSearch.getServer();

        final long contentID = content.getId();

        try {
            return solrServer.queryIsIndexed(contentID);
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't determine whether content is supported.", ex);
            return false;
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
    private String getSolrContent(Node node, MarkupSource source) throws SolrServerException {
        Content contentObj = node.getLookup().lookup(Content.class);
        
        final Server solrServer = KeywordSearch.getServer();

        //if no paging, curChunk is 0
        int curPage = paging.getCurrentPage(source);

        String content = null;
        try {
            content = (String) solrServer.getSolrContent(contentObj, curPage);
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get text content.", ex);
            return "";
        }
        return content;
    }

    /**
     * figure out text to show from the page number and source
     * 
     * @param source the current source
     * @return text for the source and the current page num
     */
  
    String getDisplayText(MarkupSource source) {
        currentSource = source;
        int pageNum = paging.getCurrentPage(currentSource);
        return source.getMarkup(pageNum);
    }

    private class NextFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkupSource source = panel.getSelectedSource();
            if (find.hasNext(source)) {
                long indexVal = find.getNext(source);

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Long.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(find.getCurrentIndexI(source) + 1);
                panel.updateTotaMatcheslDisplay(find.getCurrentIndexTotal(source));

                //update controls if needed
                if (!find.hasNext(source)) {
                    panel.enableNextMatchControl(false);
                }
                if (find.hasPrevious(source)) {
                    panel.enablePrevMatchControl(true);
                }
            }
        }
    }

    private class PrevFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkupSource source = panel.getSelectedSource();
            if (find.hasPrevious(source)) {
                long indexVal = find.getPrevious(source);

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Long.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(find.getCurrentIndexI(source) + 1);
                panel.updateTotaMatcheslDisplay(find.getCurrentIndexTotal(source));

                //update controls if needed
                if (!find.hasPrevious(source)) {
                    panel.enablePrevMatchControl(false);
                }
                if (find.hasNext(source)) {
                    panel.enableNextMatchControl(true);
                }
            }
        }
    }

    private class SourceChangeActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkupSource source = panel.getSelectedSource();

            //setup find controls
            if (source != null && source.isSearchable()) {
                find.init(source);
                panel.updateCurrentMatchDisplay(find.getCurrentIndexI(source) + 1);
                panel.updateTotaMatcheslDisplay(find.getCurrentIndexTotal(source));

                if (find.hasNext(source)) {
                    panel.enableNextMatchControl(true);
                } else {
                    panel.enableNextMatchControl(false);
                }

                if (find.hasPrevious(source)) {
                    panel.enablePrevMatchControl(true);
                } else {
                    panel.enablePrevMatchControl(false);
                }
            } else {
                panel.enableNextMatchControl(false);
                panel.enablePrevMatchControl(false);
            }


        }
    }

    private void updatePageControls(int currentPage, int totalPages) {

        if (totalPages == 0) {
            //no chunks case
            panel.updateTotalPageslDisplay(1);
            panel.updateCurrentPageDisplay(1);
        } else {
            panel.updateTotalPageslDisplay(totalPages);
            panel.updateCurrentPageDisplay(currentPage);
        }

        if (totalPages < 2) {
            panel.enableNextPageControl(false);
            panel.enablePrevPageControl(false);
        } else {
            if (currentPage < totalPages) {
                panel.enableNextPageControl(true);
            } else {
                panel.enableNextPageControl(false);
            }

            if (currentPage > 1) {
                panel.enablePrevPageControl(true);
            } else {
                panel.enablePrevPageControl(false);
            }
        }

    }

    class NextPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {

            if (paging.hasNext(currentSource)) {
                paging.next(currentSource);

                //set new text
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                panel.refreshCurrentMarkup();
                panel.setCursor(null);

                //update display
                panel.updateCurrentPageDisplay(paging.getCurrentPage(currentSource));

                //update controls if needed
                if (!paging.hasNext(currentSource)) {
                    panel.enableNextPageControl(false);
                }
                if (paging.hasPrevious(currentSource)) {
                    panel.enablePrevPageControl(true);
                }
            }
        }
    }

    private class PrevPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            if (paging.hasPrevious(currentSource)) {
                paging.previous(currentSource);

                //set new text
                panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                panel.refreshCurrentMarkup();
                panel.setCursor(null);

                //update display
                panel.updateCurrentPageDisplay(paging.getCurrentPage(currentSource));

                //update controls if needed
                if (!paging.hasPrevious(currentSource)) {
                    panel.enablePrevPageControl(false);
                }
                if (paging.hasNext(currentSource)) {
                    panel.enableNextPageControl(true);
                }
            }
        }
    }
}
