/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.keywordsearch.KeywordQueryFilter.FilterType;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearch.QueryType;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Highlights hits for a given document. Knows about pages and such for the
 * content viewer.
 */
class HighlightedText implements IndexedText {

    private static final Logger logger = Logger.getLogger(HighlightedText.class.getName());

    private static final BlackboardAttribute.Type TSK_KEYWORD_HIT_DOCUMENT_IDS = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_HIT_DOCUMENT_IDS);
    private static final BlackboardAttribute.Type TSK_KEYWORD_SEARCH_TYPE = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE);
    private static final BlackboardAttribute.Type TSK_KEYWORD = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD);

    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String HIGHLIGHT_POST = "</span>"; //NON-NLS
    private static final String ANCHOR_PREFIX = HighlightedText.class.getName() + "_"; //NON-NLS

    final private Server solrServer = KeywordSearch.getServer();

    private final long objectId;
    private final Set<String> keywords = new HashSet<>();

    private int numberPages = 0;
    private int currentPage = 0;

    private boolean hasChunks = false;
    /**
     * stores all pages/chunks that have hits as key, and number of hits as a
     * value, or 0 if yet unknown
     */
    private final LinkedHashMap<Integer, Integer> numberOfHitsPerPage = new LinkedHashMap<>();
    /*
     * stored page num -> current hit number mapping
     */
    private final HashMap<Integer, Integer> currentHitPerPage = new HashMap<>();
    private final List<Integer> pages = new ArrayList<>();
    private QueryResults hits = null; //original hits that may get passed in
    private boolean isPageInfoLoaded = false;
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);
    private BlackboardArtifact artifact;
    private KeywordSearch.QueryType qt;
    private boolean isLiteral;

    /**
     * This constructor is used when keyword hits are accessed from the ad-hoc
     * search results. In that case we have the entire QueryResults object and
     * need to arrange the paging.
     *
     * @param objectId
     * @param originalQuery The original query string that produced the hit. If
     *                      isRegex is true, this will be the regular expression
     *                      that produced the hit.
     */
    HighlightedText(long objectId, QueryResults hits) {
        this.objectId = objectId;
        this.hits = hits;
    }

    /**
     * This constructor is used when keyword hits are accessed from the "Keyword
     * Hits" node in the directory tree in Autopsy. In that case we have the
     * keyword hit artifact which has the chunks for which a hit had previously
     * been found to work out the paging for.
     *
     *
     * @param artifact
     *
     * @throws TskCoreException
     */
    HighlightedText(BlackboardArtifact artifact) {
        this.artifact = artifact;
        this.objectId = artifact.getObjectID();

    }

    private void loadPageInfoFromArtifact() throws TskCoreException, NumberFormatException {
        final String keyword = artifact.getAttribute(TSK_KEYWORD).getValueString();
        this.keywords.add(keyword);

        final BlackboardAttribute qtAttribute = artifact.getAttribute(TSK_KEYWORD_SEARCH_TYPE);

        qt = (qtAttribute != null)
                ? KeywordSearch.QueryType.values()[qtAttribute.getValueInt()] : null;

        final BlackboardAttribute docIDsArtifact = artifact.getAttribute(TSK_KEYWORD_HIT_DOCUMENT_IDS);

        if (qt == QueryType.REGEX && docIDsArtifact != null) {
            isLiteral = false;
            //regex search records the chunks in the artifact
            String chunkIDsString = docIDsArtifact.getValueString();
            Set<String> chunkIDs = Arrays.stream(chunkIDsString.split(",")).map(StringUtils::strip).collect(Collectors.toSet());
            for (String solrDocumentId : chunkIDs) {
                int chunkID;
                final int separatorIndex = solrDocumentId.indexOf(Server.CHUNK_ID_SEPARATOR);
                if (-1 != separatorIndex) {
                    chunkID = Integer.parseInt(solrDocumentId.substring(separatorIndex + 1));
                } else {

                    chunkID = 0;
                }
                pages.add(chunkID);
                numberOfHitsPerPage.put(chunkID, 0);
                currentHitPerPage.put(chunkID, 0);
            }
            this.currentPage = pages.stream().sorted().findFirst().orElse(1);
            isPageInfoLoaded = true;
        } else {
            isLiteral = true;
            /*
             * non-regex searches don't record the chunks in the artifacts, so
             * we need to look them up
             */
            Keyword keywordQuery = new Keyword(keyword, true);
            KeywordSearchQuery chunksQuery
                    = new LuceneQuery(new KeywordList(Arrays.asList(keywordQuery)), keywordQuery);
            chunksQuery.addFilter(new KeywordQueryFilter(FilterType.CHUNK, this.objectId));
            try {
                hits = chunksQuery.performQuery();
                loadPageInfoFromHits();
            } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                logger.log(Level.SEVERE, "Could not perform the query to get chunk info and get highlights:" + keywordQuery.getSearchTerm(), ex); //NON-NLS
                MessageNotifyUtil.Notify.error(Bundle.HighlightedText_query_exception_msg() + keywordQuery.getSearchTerm(), ex.getCause().getMessage());
            }
        }
    }

    /**
     * Return the string used to later have SOLR highlight the document with.
     *
     * @param query
     * @param literal_query
     * @param queryResults
     * @param file
     *
     * @return
     */
    /**
     * Constructs a complete, escaped Solr query that is ready to be used.
     *
     * @param query         keyword term to be searched for
     * @param literal_query flag whether query is literal or regex
     *
     * @return Solr query string
     */
    static private String constructEscapedSolrQuery(String query) {
        return LuceneQuery.HIGHLIGHT_FIELD + ":" + "\"" + KeywordSearchUtil.escapeLuceneQuery(query) + "\"";
    }

    /**
     * The main goal of this method is to figure out which pages / chunks have
     * hits.
     */
    @Messages({"HighlightedText.query.exception.msg=Could not perform the query to get chunk info and get highlights:"})
    private void loadPageInfo() throws TskCoreException {
        if (isPageInfoLoaded) {
            return;
        }

        try {
            this.numberPages = solrServer.queryNumFileChunks(this.objectId);
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not get number pages for content: {0}", this.objectId); //NON-NLS
            return;
        }

        if (this.numberPages == 0) {
            hasChunks = false;
        } else {
            hasChunks = true;
        }

        if (artifact != null) {
            /*
             * this could go in the constructor but is here to keep it near the
             * functionaly similar code for non regex searches
             */ loadPageInfoFromArtifact();
        } else if (hasChunks) {
            // if the file has chunks, get pages with hits, sorted
            loadPageInfoFromHits();
        } else {
            //non-regex, no chunks
            this.numberPages = 1;
            this.currentPage = 1;
            numberOfHitsPerPage.put(1, 0);
            pages.add(1);
            currentHitPerPage.put(1, 0);
            isPageInfoLoaded = true;
        }

    }

    private void loadPageInfoFromHits() {
        isLiteral = hits.getQuery().isLiteral();
        //organize the hits by page, filter as needed
        TreeSet<Integer> pagesSorted = new TreeSet<>();
        for (Keyword k : hits.getKeywords()) {
            for (KeywordHit hit : hits.getResults(k)) {
                int chunkID = hit.getChunkId();
                if (chunkID != 0 && this.objectId == hit.getSolrObjectId()) {
                    pagesSorted.add(chunkID);
                    if (StringUtils.isNotBlank(hit.getHit())) {
                        this.keywords.add(hit.getHit());
                    }
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
            numberOfHitsPerPage.put(page, 0); //unknown number of matches in the page
            pages.add(page);
            currentHitPerPage.put(page, 0); //set current hit to 0th
        }
        isPageInfoLoaded = true;
    }

    @Override
    public int getNumberPages() {
        //return number of pages that have hits
        return this.numberPages;
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
        if (false == hasNextPage()) {
            throw new IllegalStateException("No next page.");
        }
        int idx = pages.indexOf(this.currentPage);
        currentPage = pages.get(idx + 1);
        return currentPage;
    }

    @Override
    public int previousPage() {
        if (!hasPreviousPage()) {
            throw new IllegalStateException("No previous page.");
        }
        int idx = pages.indexOf(this.currentPage);
        currentPage = pages.get(idx - 1);
        return currentPage;
    }

    @Override
    public boolean hasNextItem() {
        if (!this.currentHitPerPage.containsKey(currentPage)) {
            return false;
        }
        return this.currentHitPerPage.get(currentPage) < this.numberOfHitsPerPage.get(currentPage);
    }

    @Override
    public boolean hasPreviousItem() {
        if (!this.currentHitPerPage.containsKey(currentPage)) {
            return false;
        }
        return this.currentHitPerPage.get(currentPage) > 1;
    }

    @Override
    public int nextItem() {
        if (!hasNextItem()) {
            throw new IllegalStateException("No next item.");
        }
        int cur = currentHitPerPage.get(currentPage) + 1;
        currentHitPerPage.put(currentPage, cur);
        return cur;
    }

    @Override
    public int previousItem() {
        if (!hasPreviousItem()) {
            throw new IllegalStateException("No previous item.");
        }
        int cur = currentHitPerPage.get(currentPage) - 1;
        currentHitPerPage.put(currentPage, cur);
        return cur;
    }

    @Override
    public int currentItem() {
        if (!this.currentHitPerPage.containsKey(currentPage)) {
            return 0;
        }
        return currentHitPerPage.get(currentPage);
    }

    @Override
    public LinkedHashMap<Integer, Integer> getHitsPages() {
        return this.numberOfHitsPerPage;
    }

    @Override
    public String getText() {

        try {
            loadPageInfo(); //inits once
            SolrQuery q = new SolrQuery();
            q.setShowDebugInfo(DEBUG); //debug

            String contentIdStr = Long.toString(this.objectId);
            if (hasChunks) {
                final String chunkID = Integer.toString(this.currentPage);
                contentIdStr += "0".equals(chunkID) ? "" : "_" + chunkID;
            }
            final String filterQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentIdStr);
            if (isLiteral) {
                final String highlightQuery = keywords.stream()
                        .map(HighlightedText::constructEscapedSolrQuery)
                        .collect(Collectors.joining(" "));

                q.setQuery(highlightQuery);
                q.addField(Server.Schema.TEXT.toString());
                q.addFilterQuery(filterQuery);
                q.addHighlightField(LuceneQuery.HIGHLIGHT_FIELD);
                q.setHighlightFragsize(0); // don't fragment the highlight, works with original highlighter, or needs "single" list builder with FVH

                //tune the highlighter
                q.setParam("hl.useFastVectorHighlighter", "on"); //fast highlighter scales better than standard one NON-NLS
                q.setParam("hl.tag.pre", HIGHLIGHT_PRE); //makes sense for FastVectorHighlighter only NON-NLS
                q.setParam("hl.tag.post", HIGHLIGHT_POST); //makes sense for FastVectorHighlighter only NON-NLS
                q.setParam("hl.fragListBuilder", "single"); //makes sense for FastVectorHighlighter only NON-NLS

                //docs says makes sense for the original Highlighter only, but not really
                q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); //NON-NLS
            } else {
                q.setQuery(filterQuery);
                q.addField(Server.Schema.CONTENT_STR.toString());
            }

            QueryResponse response = solrServer.query(q, METHOD.POST);

            // There should never be more than one document since there will 
            // either be a single chunk containing hits or we narrow our
            // query down to the current page/chunk.
            if (response.getResults().size() > 1) {
                logger.log(Level.WARNING, "Unexpected number of results for Solr highlighting query: {0}", q); //NON-NLS
            }
            String highlightedContent;
            Map<String, Map<String, List<String>>> responseHighlight = response.getHighlighting();
            String highlightField = isLiteral
                    ? LuceneQuery.HIGHLIGHT_FIELD
                    : Server.Schema.CONTENT_STR.toString();
            if (responseHighlight == null) {
                highlightedContent = attemptManualHighlighting(response.getResults(), highlightField, keywords);
            } else {
                Map<String, List<String>> responseHighlightID = responseHighlight.get(contentIdStr);

                if (responseHighlightID == null) {
                    highlightedContent = attemptManualHighlighting(response.getResults(), highlightField, keywords);
                } else {
                    List<String> contentHighlights = responseHighlightID.get(LuceneQuery.HIGHLIGHT_FIELD);
                    if (contentHighlights == null) {
                        highlightedContent = attemptManualHighlighting(response.getResults(), highlightField, keywords);
                    } else {
                        // extracted content (minus highlight tags) is HTML-escaped
                        highlightedContent = contentHighlights.get(0).trim();
                    }
                }
            }
            highlightedContent = insertAnchors(highlightedContent);

            return "<html><pre>" + highlightedContent + "</pre></html>"; //NON-NLS
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Error getting highlighted text for " + objectId, ex); //NON-NLS
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
        if (!this.numberOfHitsPerPage.containsKey(this.currentPage)) {
            return 0;
        }
        return this.numberOfHitsPerPage.get(this.currentPage);
    }

    /**
     * If the Solr query does not produce valid highlighting, we attempt to add
     * the highlighting ourselves. We do this by taking the text returned from
     * the document that contains a hit and searching that text for the keyword
     * that produced the hit.
     *
     * @param solrDocumentList The list of Solr documents returned in response
     *                         to a Solr query. We expect there to only ever be
     *                         a single document.
     *
     * @return Either a string with the keyword highlighted or a string
     *         indicating that we did not find a hit in the document.
     */
    static String attemptManualHighlighting(SolrDocumentList solrDocumentList, String highlightField, Collection<String> keywords) {
        if (solrDocumentList.isEmpty()) {
            return NbBundle.getMessage(HighlightedText.class, "HighlightedMatchesSource.getMarkup.noMatchMsg");
        }

        // It doesn't make sense for there to be more than a single document in
        // the list since this class presents a single page (document) of highlighted
        // content at a time.  Hence we can just use get(0).
        String text = solrDocumentList.get(0).getOrDefault(highlightField, "").toString();

        // Escape any HTML content that may be in the text. This is needed in
        // order to correctly display the text in the content viewer.
        // Must be done before highlighting tags are added. If we were to 
        // perform HTML escaping after adding the highlighting tags we would
        // not see highlighted text in the content viewer.
        text = StringEscapeUtils.escapeHtml(text);

        StringBuilder highlightedText = new StringBuilder("");

        for (String keyword : keywords) {
            int textOffset = 0;
            int hitOffset;
            while ((hitOffset = StringUtils.indexOfIgnoreCase(text, keyword, textOffset)) != -1) {
                // Append the portion of text up to (but not including) the hit.
                highlightedText.append(text.substring(textOffset, hitOffset));
                // Add in the highlighting around the keyword.
                highlightedText.append(HIGHLIGHT_PRE);
                highlightedText.append(keyword);
                highlightedText.append(HIGHLIGHT_POST);

                // Advance the text offset past the keyword.
                textOffset = hitOffset + keyword.length();
            }
            // Append the remainder of text field
            highlightedText.append(text.substring(textOffset, text.length()));
            if (highlightedText.length() > 0) {

            } else {
                return NbBundle.getMessage(HighlightedText.class, "HighlightedMatchesSource.getMarkup.noMatchMsg");
            }
            text = highlightedText.toString();
            highlightedText = new StringBuilder("");
        }
        return text;
    }

    /**
     * Anchors are used to navigate back and forth between hits on the same page
     * and to navigate to hits on the next/previous page.
     *
     * @param searchableContent
     *
     * @return
     */
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
        this.numberOfHitsPerPage.put(this.currentPage, count);
        if (this.currentItem() == 0 && this.hasNextItem()) {
            this.nextItem();
        }

        return buf.toString();
    }

}
