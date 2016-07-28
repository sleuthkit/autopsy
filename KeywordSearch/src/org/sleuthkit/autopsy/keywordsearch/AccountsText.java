/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.TextMarkupLookup;

/**
 * Highlights account hits for a given document. Knows about pages and such for
 * the content viewer.
 */
class AccountsText implements IndexedText, TextMarkupLookup {

    private static final Logger LOGGER = Logger.getLogger(AccountsText.class.getName());
    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String HIGHLIGHT_POST = "</span>"; //NON-NLS
    private static final String ANCHOR_PREFIX = AccountsText.class.getName() + "_";

    private final String solrDocumentId;
    private final Set<String> keywords = new HashSet<>();
    private final Server solrServer;
    private int numberPagesForFile = 0;
    private int currentPage = 0;
    private boolean hasChunks = false;
    //stores all pages/chunks that have hits as key, and number of hits as a value, or 0 if yet unknown
    private final LinkedHashMap<Integer, Integer> numberOfHitsPerPage = new LinkedHashMap<>();
    //stored page num -> current hit number mapping
    private final HashMap<Integer, Integer> currentHitPerPage = new HashMap<>();
    private final List<Integer> pages = new ArrayList<>();
    private boolean isPageInfoLoaded = false;
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);
    private final String displayName;
    private final long solrObjectId;
    private final Integer chunkId;

    String getDisplayName() {
        return displayName;
    }

    AccountsText(String objectId, Set<String> keywords) {
        this.solrDocumentId = objectId;
        this.keywords.addAll(keywords);
        this.solrServer = KeywordSearch.getServer();

        final int separatorIndex = solrDocumentId.indexOf(Server.ID_CHUNK_SEP);
        if (-1 == separatorIndex) {
            //no chunk id in solrDocumentId
            this.solrObjectId = Long.parseLong(solrDocumentId);
            this.chunkId = null;
        } else {
            //solrDocumentId includes chunk id
            this.solrObjectId = Long.parseLong(solrDocumentId.substring(0, separatorIndex));
            this.chunkId = Integer.parseInt(solrDocumentId.substring(separatorIndex + 1));
        }

        displayName = keywords.size() == 1
                ? Bundle.ExtractedContentViewer_creditCardNumber()
                : Bundle.ExtractedContentViewer_creditCardNumbers();
    }

    long getObjectId() {
        return this.solrObjectId;
    }

    @Override
    public int getNumberPages() {
        return this.numberPagesForFile;
    }

    @Override
    public int getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public boolean hasNextPage() {
        return pages.indexOf(this.currentPage) < pages.size() - 1;

    }

    @Override
    public boolean hasPreviousPage() {
        return pages.indexOf(this.currentPage) > 0;

    }

    @Override
    public int nextPage() {
        if (hasNextPage()) {
            currentPage = pages.get(pages.indexOf(this.currentPage) + 1);
            return currentPage;
        } else {
            throw new IllegalStateException(NbBundle.getMessage(AccountsText.class, "HighlightedMatchesSource.nextPage.exception.msg"));
        }
    }

    @Override
    public int previousPage() {
        if (hasPreviousPage()) {
            currentPage = pages.get(pages.indexOf(this.currentPage) - 1);
            return currentPage;
        } else {
            throw new IllegalStateException(NbBundle.getMessage(AccountsText.class, "HighlightedMatchesSource.previousPage.exception.msg"));
        }
    }

    @Override
    public boolean hasNextItem() {
        if (this.currentHitPerPage.containsKey(currentPage)) {
            return this.currentHitPerPage.get(currentPage) < this.numberOfHitsPerPage.get(currentPage);
        } else {
            return false;
        }
    }

    @Override
    public boolean hasPreviousItem() {
        if (this.currentHitPerPage.containsKey(currentPage)) {
            return this.currentHitPerPage.get(currentPage) > 1;
        } else {
            return false;
        }
    }

    @Override
    public int nextItem() {
        if (hasNextItem()) {
            return currentHitPerPage.merge(currentPage, 1, Integer::sum);
        } else {
            throw new IllegalStateException(NbBundle.getMessage(AccountsText.class, "HighlightedMatchesSource.nextItem.exception.msg"));
        }
    }

    @Override
    public int previousItem() {
        if (hasPreviousItem()) {
            return currentHitPerPage.merge(currentPage, -1, Integer::sum);
        } else {
            throw new IllegalStateException(NbBundle.getMessage(AccountsText.class, "HighlightedMatchesSource.previousItem.exception.msg"));
        }
    }

    @Override
    public int currentItem() {
        if (this.currentHitPerPage.containsKey(currentPage)) {
            return currentHitPerPage.get(currentPage);
        } else {
            return 0;
        }
    }

    @Override
    public LinkedHashMap<Integer, Integer> getHitsPages() {
        return this.numberOfHitsPerPage;
    }

    /**
     * The main goal of this method is to figure out which pages / chunks have
     * hits.
     */
    synchronized private void loadPageInfo() {
        if (isPageInfoLoaded) {
            return;
        }
        if (chunkId != null) {
            //if a chunk is specified, only show that chunk/page
            this.numberPagesForFile = 1;
            hasChunks = false;
            //no chunks
            this.numberPagesForFile = 1;
            this.currentPage = chunkId;
            numberOfHitsPerPage.put(chunkId, 0);
            pages.add(chunkId);
            currentHitPerPage.put(chunkId, 0);
        } else {
            hasChunks = true;
            try {
                this.numberPagesForFile = solrServer.queryNumFileChunks(this.solrObjectId);
            } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                LOGGER.log(Level.WARNING, "Could not get number pages for content: {0}", this.solrDocumentId); //NON-NLS
                return;
            }

            //if has chunks, get pages with hits
            TreeSet<Integer> sortedPagesWithHits = new TreeSet<>();
            //extract pages of interest, sorted

            SolrQuery q = new SolrQuery();
            q.setShowDebugInfo(DEBUG); //debug
            String query = keywords.stream().map(keyword -> "/.*" + KeywordSearchUtil.escapeLuceneQuery(keyword) + ".*/").collect(Collectors.joining(" "));
            q.setQuery(LuceneQuery.HIGHLIGHT_FIELD_REGEX + ":" + query);
            q.setFields("id");
            if (chunkId == null) {
                q.addFilterQuery(Server.Schema.ID.toString() + ":" + this.solrObjectId + "_*");
            } else {
                q.addFilterQuery(Server.Schema.ID.toString() + ":" + this.solrDocumentId);
            }
            try {
                QueryResponse response = solrServer.query(q, METHOD.POST);
                for (SolrDocument resultDoc : response.getResults()) {
                    final String resultDocumentId = resultDoc.getFieldValue(Server.Schema.ID.toString()).toString();
                    // Put the solr chunk id in the map
                    final int separatorIndex = resultDocumentId.indexOf(Server.ID_CHUNK_SEP);
                    if (-1 != separatorIndex) {
                        sortedPagesWithHits.add(Integer.parseInt(resultDocumentId.substring(separatorIndex + 1)));
                    } else {
                        sortedPagesWithHits.add(0);
                    }
                }

            } catch (KeywordSearchModuleException | NoOpenCoreException | NumberFormatException ex) {
                LOGGER.log(Level.WARNING, "Error executing Solr highlighting query: " + keywords, ex); //NON-NLS
            }

            //set page to first page having highlights
            if (sortedPagesWithHits.isEmpty()) {
                this.currentPage = 0;
            } else {
                this.currentPage = sortedPagesWithHits.first();
            }

            for (Integer page : sortedPagesWithHits) {
                numberOfHitsPerPage.put(page, 0); //unknown number of matches in the page
                pages.add(page);
                currentHitPerPage.put(page, 0); //set current hit to 0th
            }
        }

        isPageInfoLoaded = true;
    }

    @Override
    public String getText() {
        loadPageInfo(); //inits once

        String highLightField = LuceneQuery.HIGHLIGHT_FIELD_REGEX;

        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug
        String query = keywords.stream().map(keyword -> "/.*" + KeywordSearchUtil.escapeLuceneQuery(keyword) + ".*/").collect(Collectors.joining(" "));
        q.setQuery(LuceneQuery.HIGHLIGHT_FIELD_REGEX + ":" + query);

        String contentIdStr;
        if (hasChunks) {
            contentIdStr = solrObjectId + "_" + Integer.toString(this.currentPage);
        } else {
            contentIdStr = this.solrDocumentId;
        }

        final String filterQuery = Server.Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(contentIdStr);
        q.addFilterQuery(filterQuery);
        q.addHighlightField(highLightField); //for exact highlighting, try content_ws field (with stored="true" in Solr schema)

        //tune the highlighter
        q.setParam("hl.useFastVectorHighlighter", "true"); //fast highlighter scales better than standard one NON-NLS
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
            LOGGER.log(Level.WARNING, "Error executing Solr highlighting query: " + keywords, ex); //NON-NLS
            return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.queryFailedMsg");
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
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

    private String insertAnchors(String searchableContent) {

        final String insertPre = "<a name='" + ANCHOR_PREFIX; //NON-NLS
        final String insertPost = "'></a>$0"; //$0 will insert current regex match  //NON-NLS

        Matcher m = Pattern.compile(HIGHLIGHT_PRE).matcher(searchableContent);
        StringBuffer sb = new StringBuffer(searchableContent.length());
        int count;
        for (count = 0; m.find(); count++) {
            m.appendReplacement(sb, insertPre + count + insertPost);
        }
        m.appendTail(sb);

        //store total hits for this page, now that we know it
        this.numberOfHitsPerPage.put(this.currentPage, count);
        if (this.currentItem() == 0 && this.hasNextItem()) {
            this.nextItem();
        }

        return sb.toString();
    }

    @Override
    @Deprecated
    // factory method to create an instance of this object
    public AccountsText createInstance(long objectId, String keywordHitQuery, boolean isRegex, String originalQuery) {
        return new AccountsText(String.valueOf(objectId), Collections.emptySet());
    }
}
