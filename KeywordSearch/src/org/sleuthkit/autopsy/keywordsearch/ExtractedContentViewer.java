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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import org.apache.commons.lang.StringEscapeUtils;
import org.sleuthkit.autopsy.datamodel.HighlightLookup;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.Directory;

/**
 * Displays marked-up (HTML) content for a Node. The sources are all the 
 * MarkupSource items in the selected Node's lookup, plus the content that
 * Solr extracted (if there is any).
 */
@ServiceProvider(service = DataContentViewer.class, position=4)
public class ExtractedContentViewer implements DataContentViewer {

    private static final Logger logger = Logger.getLogger(ExtractedContentViewer.class.getName());
    private ExtractedContentPanel panel;
    private Node currentNode = null;
    private MarkupSource currentSource = null;
    
    //keep last content cached
    private String curContent;
    private long curContentId;
    private int curContentChunk;

    public ExtractedContentViewer() {
    }

    @Override
    public void setNode(final Node selectedNode) {
        //TODO why setNode() is called twice for the same node each time

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

                private boolean inited = false;
                private int numPages = 0;
                private int currentPage = 0;
                private boolean hasChunks = false;

                @Override
                public int getCurrentPage() {
                    return this.currentPage;
                }

                @Override
                public boolean hasNextPage() {
                    return currentPage < numPages;
                }

                @Override
                public boolean hasPreviousPage() {
                    return currentPage > 1;
                }

                @Override
                public int nextPage() {
                    if (!hasNextPage()) {
                        throw new IllegalStateException("No next page.");
                    }
                    ++currentPage;
                    return currentPage;
                }

                @Override
                public int previousPage() {
                    if (!hasPreviousPage()) {
                        throw new IllegalStateException("No previous page.");
                    }
                    --currentPage;
                    return currentPage;
                }

                @Override
                public boolean hasNextItem() {
                    throw new UnsupportedOperationException("Not supported, not a searchable source.");
                }

                @Override
                public boolean hasPreviousItem() {
                    throw new UnsupportedOperationException("Not supported, not a searchable source.");
                }

                @Override
                public int nextItem() {
                    throw new UnsupportedOperationException("Not supported, not a searchable source.");
                }

                @Override
                public int previousItem() {
                    throw new UnsupportedOperationException("Not supported, not a searchable source.");
                }

                @Override
                public int currentItem() {
                    throw new UnsupportedOperationException("Not supported, not a searchable source.");
                }

                @Override
                public String getMarkup() {
                    try {
                        return getSolrContent(selectedNode, currentPage, hasChunks);
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
                public LinkedHashMap<Integer, Integer> getHitsPages() {
                    return null;
                }

                @Override
                public int getNumberPages() {
                    if (inited) {
                        return this.numPages;
                    }

                    final Server solrServer = KeywordSearch.getServer();

                    try {
                        numPages = solrServer.queryNumFileChunks(contentID);
                        if (numPages == 0) {
                            numPages = 1;
                            hasChunks = false;
                        } else {
                            hasChunks = true;
                        }
                        inited = true;
                    } catch (SolrServerException ex) {
                        logger.log(Level.WARNING, "Could not get number of chunks: ", ex);

                    } catch (NoOpenCoreException ex) {
                        logger.log(Level.WARNING, "Could not get number of chunks: ", ex);
                    }
                    return numPages;
                }
            };

            currentSource = newSource;
            sources.add(newSource);


            //init pages
            final int totalPages = currentSource.getNumberPages();
            int currentPage = currentSource.getCurrentPage();
            if (currentPage == 0 && currentSource.hasNextPage()) {
                currentSource.nextPage();
            }


            updatePageControls();
        }


        // first source will be the default displayed
        setPanel(sources);
        // If node has been selected before, return to the previous position
        scrollToCurrentHit();
    }

    private void scrollToCurrentHit() {
        final MarkupSource source = panel.getSelectedSource();
        if (source == null || !source.isSearchable()) {
            return;
        }

        // using invokeLater to wait for ComboBox selection to complete
        EventQueue.invokeLater(new Runnable() {

            @Override
            public void run() {
                panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(source.currentItem()));
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
            panel = new ExtractedContentPanel();
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
    public int isPreferred(Node node,
            boolean isSupported) {
        BlackboardArtifact art = node.getLookup().lookup(BlackboardArtifact.class);
        if(isSupported) {
            if(art == null) {
                return 4;
            } else if(art.getArtifactTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()) {
                return 6;
            } else {
                return 4;
            }
        } else {
            return 0;
        }
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

    //get current node content id, or 0 if not available
    private long getNodeContentId() {
        Content content = currentNode.getLookup().lookup(Content.class);
        if (content == null) {
            return 0;
        }

        return content.getId();
    }

    private class IsDirVisitor extends ContentVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(Content cntnt) {
            return false;
        }

        @Override
        public Boolean visit(Directory d) {
            return true;
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

        if (content.getSize() == 0)
            return false;

        final Server solrServer = KeywordSearch.getServer();

        boolean isDir = content.accept(new IsDirVisitor());
        if (isDir) {
            return false;
        }

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
     * @param node a node that has extracted content in Solr (check with
     * solrHasContent(ContentNode))
     * @param currentPage currently used page 
     * @param hasChunks true if the content behind the node has multiple chunks. This means we need to address the content pages specially.
     * @return the extracted content
     * @throws SolrServerException if something goes wrong
     */
    private String getSolrContent(Node node, int currentPage, boolean hasChunks) throws SolrServerException {
        Content contentObj = node.getLookup().lookup(Content.class);

        final Server solrServer = KeywordSearch.getServer();

        int chunkId = 0;
        if (hasChunks) {
            chunkId = currentPage;
        }

        //check if cached
        long contentId = getNodeContentId();
        if (curContent != null) {
            if (contentId == curContentId
                    && curContentChunk == chunkId) {
                return curContent;
            }
        }

        //not cached
        try {
            curContent = StringEscapeUtils.escapeHtml(solrServer.getSolrContent(contentObj, chunkId)).trim();
            StringBuilder sb = new StringBuilder(curContent.length() + 20);
            sb.append("<pre>").append(curContent).append("</pre>");
            curContent = sb.toString();
            curContentId = contentId;
            curContentChunk = chunkId;
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Couldn't get text content.", ex);
            return "";
        }
        return curContent;
    }

    private class NextFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkupSource source = panel.getSelectedSource();
            final boolean hasNextItem = source.hasNextItem();
            final boolean hasNextPage = source.hasNextPage();
            int indexVal = 0;
            if (hasNextItem || hasNextPage) {
                if (!hasNextItem) {
                    //flip the page
                    nextPage();
                    indexVal = source.currentItem();
                } else {
                    indexVal = source.nextItem();
                }

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(source.currentItem());
                panel.updateTotaMatcheslDisplay(source.getNumberHits());

                //update controls if needed
                if (!source.hasNextItem() && !source.hasNextPage()) {
                    panel.enableNextMatchControl(false);
                }
                if (source.hasPreviousItem() || source.hasPreviousPage()) {
                    panel.enablePrevMatchControl(true);
                }
            }
        }
    }

    private class PrevFindActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkupSource source = panel.getSelectedSource();
            final boolean hasPreviousItem = source.hasPreviousItem();
            final boolean hasPreviousPage = source.hasPreviousPage();
            int indexVal = 0;
            if (hasPreviousItem || hasPreviousPage) {
                if (!hasPreviousItem) {
                    //flip the page
                    previousPage();
                    indexVal = source.currentItem();
                } else {
                    indexVal = source.previousItem();
                }

                //scroll
                panel.scrollToAnchor(source.getAnchorPrefix() + Integer.toString(indexVal));

                //update display
                panel.updateCurrentMatchDisplay(source.currentItem());
                panel.updateTotaMatcheslDisplay(source.getNumberHits());

                //update controls if needed
                if (!source.hasPreviousItem() && !source.hasPreviousPage()) {
                    panel.enablePrevMatchControl(false);
                }
                if (source.hasNextItem() || source.hasNextPage()) {
                    panel.enableNextMatchControl(true);
                }
            }
        }
    }

    private class SourceChangeActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {

            currentSource = panel.getSelectedSource();

            if (currentSource == null) {
                //TODO might need to reset something
                return;
            }

            final int totalPages = currentSource.getNumberPages();
            final int currentPage = currentSource.getCurrentPage();

            updatePageControls();
            updateSearchControls();

        }
    }

    private void updateSearchControls() {
        //setup search controls
        if (currentSource != null && currentSource.isSearchable()) {

            panel.updateCurrentMatchDisplay(currentSource.currentItem());
            panel.updateTotaMatcheslDisplay(currentSource.getNumberHits());

            if (currentSource.hasNextItem() || currentSource.hasNextPage()) {
                panel.enableNextMatchControl(true);
            } else {
                panel.enableNextMatchControl(false);
            }

            if (currentSource.hasPreviousItem() || currentSource.hasPreviousPage()) {
                panel.enablePrevMatchControl(true);
            } else {
                panel.enablePrevMatchControl(false);
            }

        } else {
            panel.enableNextMatchControl(false);
            panel.enablePrevMatchControl(false);
            panel.updateCurrentMatchDisplay(0);
            panel.updateTotaMatcheslDisplay(0);
        }
    }

    private void updatePageControls() {
        if (currentSource == null) {
            return;
        }

        final int currentPage = currentSource.getCurrentPage();
        final int totalPages = currentSource.getNumberPages();
        panel.updateTotalPageslDisplay(totalPages);
        panel.updateCurrentPageDisplay(currentPage);


        if (totalPages == 1) {
            panel.enableNextPageControl(false);
            panel.enablePrevPageControl(false);
        } else {
            if (currentSource.hasNextPage()) {
                panel.enableNextPageControl(true);
            } else {
                panel.enableNextPageControl(false);
            }

            if (currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(true);
            } else {
                panel.enablePrevPageControl(false);
            }
        }

    }

    private void nextPage() {
        if (currentSource.hasNextPage()) {
            currentSource.nextPage();

            //set new text
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            panel.refreshCurrentMarkup();
            panel.setCursor(null);

            //update display
            panel.updateCurrentPageDisplay(currentSource.getCurrentPage());

            //scroll to current selection
            ExtractedContentViewer.this.scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasNextPage()) {
                panel.enableNextPageControl(false);
            }
            if (currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(true);
            }

            updateSearchControls();
        }
    }

    private void previousPage() {
        if (currentSource.hasPreviousPage()) {
            currentSource.previousPage();

            //set new text
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            panel.refreshCurrentMarkup();
            panel.setCursor(null);

            //update display
            panel.updateCurrentPageDisplay(currentSource.getCurrentPage());

            //scroll to current selection
            ExtractedContentViewer.this.scrollToCurrentHit();

            //update controls if needed
            if (!currentSource.hasPreviousPage()) {
                panel.enablePrevPageControl(false);
            }
            if (currentSource.hasNextPage()) {
                panel.enableNextPageControl(true);
            }

            updateSearchControls();


        }
    }

    class NextPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            nextPage();
        }
    }

    private class PrevPageActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent e) {
            previousPage();
        }
    }
}
