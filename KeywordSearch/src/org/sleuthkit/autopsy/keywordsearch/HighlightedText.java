/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.TextMarkupLookup;
import org.sleuthkit.autopsy.keywordsearch.KeywordQueryFilter.FilterType;

/**
 * Highlights hits for a given document. Knows about pages and such for the
 * content viewer.
 */
class HighlightedText implements IndexedText, TextMarkupLookup {

    private static final Logger logger = Logger.getLogger(HighlightedText.class.getName());
    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String HIGHLIGHT_POST = "</span>"; //NON-NLS
    private static final String ANCHOR_PREFIX = HighlightedText.class.getName() + "_";

    private long objectId;
    private String keywordHitQuery;
    private Server solrServer;
    private int numberPages;
    private int currentPage;
    private boolean isRegex = false;
    private boolean group = true;
    private boolean hasChunks = false;
    //stores all pages/chunks that have hits as key, and number of hits as a value, or 0 if yet unknown
    private LinkedHashMap<Integer, Integer> hitsPages;
    //stored page num -> current hit number mapping
    private HashMap<Integer, Integer> pagesToHits;
    private List<Integer> pages;
    private QueryResults hits = null; //original hits that may get passed in
    private String originalQuery = null; //or original query if hits are not available
    private boolean isPageInfoLoaded = false;
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    HighlightedText(long objectId, String keywordHitQuery, boolean isRegex) {
        this.objectId = objectId;
        this.keywordHitQuery = keywordHitQuery;
        this.isRegex = isRegex;
        this.group = true;
        this.hitsPages = new LinkedHashMap<>();
        this.pages = new ArrayList<>();
        this.pagesToHits = new HashMap<>();

        this.solrServer = KeywordSearch.getServer();
        this.numberPages = 0;
        this.currentPage = 0;
        //hits are unknown

    }

    //when the results are not known and need to requery to get hits
    HighlightedText(long objectId, String solrQuery, boolean isRegex, String originalQuery) {
        this(objectId, KeywordSearchUtil.quoteQuery(solrQuery), isRegex);
        this.originalQuery = originalQuery;
    }

    HighlightedText(long objectId, String solrQuery, boolean isRegex, QueryResults hits) {
        this(objectId, solrQuery, isRegex);
        this.hits = hits;
    }

    HighlightedText(long objectId, String solrQuery, boolean isRegex, boolean group, QueryResults hits) {
        this(objectId, solrQuery, isRegex, hits);
        this.group = group;
    }

    /**
     * The main goal of this method is to figure out which pages / chunks have
     * hits.
     */
    @Messages({"HighlightedText.query.exception.msg=Could not perform the query to get chunk info and get highlights:"})
    private void loadPageInfo() {
        if (isPageInfoLoaded) {
            return;
        }
        try {
            this.numberPages = solrServer.queryNumFileChunks(this.objectId);
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Could not get number pages for content: " + this.objectId); //NON-NLS
            return;
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not get number pages for content: " + this.objectId); //NON-NLS
            return;
        }

        if (this.numberPages == 0) {
            hasChunks = false;
        } else {
            hasChunks = true;
        }

        //if has chunks, get pages with hits
        if (hasChunks) {
            //extract pages of interest, sorted

            /*
             * If this is being called from the artifacts / dir tree, then we
             * need to perform the search to get the highlights.
             */
            if (hits == null) {
                String queryStr = KeywordSearchUtil.escapeLuceneQuery(this.keywordHitQuery);
                if (isRegex) {
                    //use white-space sep. field to get exact matches only of regex query result
                    queryStr = Server.Schema.CONTENT_WS + ":" + "\"" + queryStr + "\"";
                }

                Keyword keywordQuery = new Keyword(queryStr, !isRegex);
                List<Keyword> keywords = new ArrayList<>();
                keywords.add(keywordQuery);
                KeywordSearchQuery chunksQuery = new LuceneQuery(new KeywordList(keywords), keywordQuery);

                chunksQuery.addFilter(new KeywordQueryFilter(FilterType.CHUNK, this.objectId));
                try {
                    hits = chunksQuery.performQuery();
                } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                    logger.log(Level.SEVERE, "Could not perform the query to get chunk info and get highlights:" + keywordQuery.getSearchTerm(), ex); //NON-NLS
                    MessageNotifyUtil.Notify.error(Bundle.HighlightedText_query_exception_msg() + keywordQuery.getSearchTerm(), ex.getCause().getMessage());
                    return;
                }
            }

            //organize the hits by page, filter as needed
            TreeSet<Integer> pagesSorted = new TreeSet<>();
            for (Keyword k : hits.getKeywords()) {
                for (KeywordHit hit : hits.getResults(k)) {
                    int chunkID = hit.getChunkId();
                    if (chunkID != 0 && this.objectId == hit.getSolrObjectId()) {
                        pagesSorted.add(chunkID);
                    }
                }
            }

            //set page to first page having highlights
            if (pagesSorted.isEmpty()) {
                this.currentPage = 0;
            } else {
                this.currentPage = pagesSorted.first();
            }

            for (Integer page : pagesSorted) {
                hitsPages.put(page, 0); //unknown number of matches in the page
                pages.add(page);
                pagesToHits.put(page, 0); //set current hit to 0th
            }

        } else {
            //no chunks
            this.numberPages = 1;
            this.currentPage = 1;
            hitsPages.put(1, 0);
            pages.add(1);
            pagesToHits.put(1, 0);
        }
        isPageInfoLoaded = true;
    }

    //constructor for dummy singleton factory instance for Lookup
    private HighlightedText() {
    }

    long getObjectId() {
        return this.objectId;
    }

    @Override
    public int getNumberPages() {
        return this.numberPages;
        //return number of pages that have hits
        //return this.hitsPages.keySet().size();
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public boolean hasNextPage() {
        final int numPages = pages.size();
        int idx = pages.indexOf(this.currentPage);
        return idx < numPages - 1;

    }

    @Override
    public boolean hasPreviousPage() {
        int idx = pages.indexOf(this.currentPage);
        return idx > 0;

    }

    @Override
    public int nextPage() {
        if (!hasNextPage()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.nextPage.exception.msg"));
        }
        int idx = pages.indexOf(this.currentPage);
        currentPage = pages.get(idx + 1);
        return currentPage;
    }

    @Override
    public int previousPage() {
        if (!hasPreviousPage()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.previousPage.exception.msg"));
        }
        int idx = pages.indexOf(this.currentPage);
        currentPage = pages.get(idx - 1);
        return currentPage;
    }

    @Override
    public boolean hasNextItem() {
        if (!this.pagesToHits.containsKey(currentPage)) {
            return false;
        }
        return this.pagesToHits.get(currentPage) < this.hitsPages.get(currentPage);
    }

    @Override
    public boolean hasPreviousItem() {
        if (!this.pagesToHits.containsKey(currentPage)) {
            return false;
        }
        return this.pagesToHits.get(currentPage) > 1;
    }

    @Override
    public int nextItem() {
        if (!hasNextItem()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.nextItem.exception.msg"));
        }
        int cur = pagesToHits.get(currentPage) + 1;
        pagesToHits.put(currentPage, cur);
        return cur;
    }

    @Override
    public int previousItem() {
        if (!hasPreviousItem()) {
            throw new IllegalStateException(
                    NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.previousItem.exception.msg"));
        }
        int cur = pagesToHits.get(currentPage) - 1;
        pagesToHits.put(currentPage, cur);
        return cur;
    }

    @Override
    public int currentItem() {
        if (!this.pagesToHits.containsKey(currentPage)) {
            return 0;
        }
        return pagesToHits.get(currentPage);
    }

    @Override
    public LinkedHashMap<Integer, Integer> getHitsPages() {
        return this.hitsPages;
    }

    @Override
    public String getText() {
        loadPageInfo(); //inits once

        String highLightField = null;

        if (isRegex) {
            highLightField = LuceneQuery.HIGHLIGHT_FIELD_REGEX;
        } else {
            highLightField = LuceneQuery.HIGHLIGHT_FIELD_LITERAL;
        }

        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug

        // input query has already been properly constructed and escaped
        q.setQuery(keywordHitQuery);

        String contentIdStr = Long.toString(this.objectId);
        if (hasChunks) {
            contentIdStr += "_" + Integer.toString(this.currentPage);
        }

        final String filterQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentIdStr);
        q.addFilterQuery(filterQuery);
        q.addHighlightField(highLightField); //for exact highlighting, try content_ws field (with stored="true" in Solr schema)

        //q.setHighlightSimplePre(HIGHLIGHT_PRE); //original highlighter only
        //q.setHighlightSimplePost(HIGHLIGHT_POST); //original highlighter only
        q.setHighlightFragsize(0); // don't fragment the highlight, works with original highlighter, or needs "single" list builder with FVH

        //tune the highlighter
        q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one NON-NLS
        q.setParam("hl.tag.pre", HIGHLIGHT_PRE); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.tag.post", HIGHLIGHT_POST); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.fragListBuilder", "single"); //makes sense for FastVectorHighlighter only NON-NLS

        //docs says makes sense for the original Highlighter only, but not really
        q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); //NON-NLS

        try {
            QueryResponse response = solrServer.query(q, METHOD.POST);
            Map<String, Map<String, List<String>>> responseHighlight = response.getHighlighting();

            Map<String, List<String>> responseHighlightID = responseHighlight.get(contentIdStr);
            if (responseHighlightID == null) {
                return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.noMatchMsg");
            }
            List<String> contentHighlights = responseHighlightID.get(highLightField);
            if (contentHighlights == null) {
                return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.noMatchMsg");
            } else {
                // extracted content (minus highlight tags) is HTML-escaped
                String highlightedContent = contentHighlights.get(0).trim();
                highlightedContent = insertAnchors(highlightedContent);

                return "<html><pre>" + highlightedContent + "</pre></html>"; //NON-NLS
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error executing Solr highlighting query: " + keywordHitQuery, ex); //NON-NLS
            return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.queryFailedMsg");
        }
    }

    @Override
    public String toString() {
        return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.toString");
    }

    @Override
    public boolean isSearchable() {
        return true;
    }

    @Override
    public String getAnchorPrefix() {
        return ANCHOR_PREFIX;
    }

    @Override
    public int getNumberHits() {
        if (!this.hitsPages.containsKey(this.currentPage)) {
            return 0;
        }
        return this.hitsPages.get(this.currentPage);
    }

    private String insertAnchors(String searchableContent) {
        int searchOffset = 0;
        int index = -1;

        StringBuilder buf = new StringBuilder(searchableContent);

        final String searchToken = HIGHLIGHT_PRE;
        final int indexSearchTokLen = searchToken.length();
        final String insertPre = "<a name='" + ANCHOR_PREFIX; //NON-NLS
        final String insertPost = "'></a>"; //NON-NLS
        int count = 0;
        while ((index = buf.indexOf(searchToken, searchOffset)) >= 0) {
            String insertString = insertPre + Integer.toString(count + 1) + insertPost;
            int insertStringLen = insertString.length();
            buf.insert(index, insertString);
            searchOffset = index + indexSearchTokLen + insertStringLen; //next offset past this anchor
            ++count;
        }

        //store total hits for this page, now that we know it
        this.hitsPages.put(this.currentPage, count);
        if (this.currentItem() == 0 && this.hasNextItem()) {
            this.nextItem();
        }

        return buf.toString();
    }
    //dummy instance for Lookup only
    private static TextMarkupLookup instance = null;

    //getter of the singleton dummy instance solely for Lookup purpose
    //this instance does not actually work with Solr
    public static synchronized TextMarkupLookup getDefault() {
        if (instance == null) {
            instance = new HighlightedText();
        }
        return instance;
    }

    @Override
    // factory method to create an instance of this object
    public TextMarkupLookup createInstance(long objectId, String keywordHitQuery, boolean isRegex, String originalQuery) {
        return new HighlightedText(objectId, keywordHitQuery, isRegex, originalQuery);
    }
}
