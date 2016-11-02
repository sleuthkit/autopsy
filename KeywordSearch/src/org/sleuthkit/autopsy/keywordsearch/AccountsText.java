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
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Highlights account hits for a given document. Knows about pages and such for
 * the content viewer.
 *
 * Note: This class started as a copy-and-paste of HighlightedText, but it
 * proved too messy to modify HighlightedText to work for accounts also. This
 * and HighlightedText are very similar and could probably use some refactoring
 * to reduce code duplication.
 */
class AccountsText implements IndexedText {

    private static final Logger LOGGER = Logger.getLogger(AccountsText.class.getName());
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String HIGHLIGHT_POST = "</span>"; //NON-NLS
    private static final String ANCHOR_NAME_PREFIX = AccountsText.class.getName() + "_";

    private static final String INSERT_PREFIX = "<a name='" + ANCHOR_NAME_PREFIX; //NON-NLS
    private static final String INSERT_POSTFIX = "'></a>$0"; //$0 will insert current regex match  //NON-NLS
    private static final Pattern ANCHOR_DETECTION_PATTERN = Pattern.compile(HIGHLIGHT_PRE);

    private static final String HIGHLIGHT_FIELD = LuceneQuery.HIGHLIGHT_FIELD_REGEX;

    private final Server solrServer;
    private final String solrDocumentId;
    private final long solrObjectId;
    private final Integer chunkId;
    private final Set<String> keywords = new HashSet<>();
    private final String displayName;
    private final String queryString;

    private boolean isPageInfoLoaded = false;
    private int numberPagesForFile = 0;
    private int currentPage = 0;
    //list of pages, used for iterating back and forth.  Only stores pages with hits
    private final List<Integer> pages = new ArrayList<>();
    //map from page/chunk to number of hits. value is 0 if not yet known.
    private final LinkedHashMap<Integer, Integer> numberOfHitsPerPage = new LinkedHashMap<>();
    //map from page/chunk number to current hit on that page.
    private final HashMap<Integer, Integer> currentHitPerPage = new HashMap<>();

    @NbBundle.Messages({
        "AccountsText.creditCardNumber=Credit Card Number",
        "AccountsText.creditCardNumbers=Credit Card Numbers"})
    AccountsText(String objectId, Set<String> keywords) {
        this.solrDocumentId = objectId;
        this.keywords.addAll(keywords);

        //build the query string
        this.queryString = HIGHLIGHT_FIELD + ":"
                + keywords.stream()
                .map(keyword -> "/.*?" + KeywordSearchUtil.escapeLuceneQuery(keyword) + ".*?/")//surround each "keyword" with match anything regex.
                .collect(Collectors.joining(" ")); //collect as space separated string

        this.solrServer = KeywordSearch.getServer();

        final int separatorIndex = solrDocumentId.indexOf(Server.CHUNK_ID_SEPARATOR);
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
                ? Bundle.AccountsText_creditCardNumber()
                : Bundle.AccountsText_creditCardNumbers();
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
    @NbBundle.Messages("AccountsText.nextPage.exception.msg=No next page.")
    public int nextPage() {
        if (hasNextPage()) {
            currentPage = pages.get(pages.indexOf(this.currentPage) + 1);
            return currentPage;
        } else {
            throw new IllegalStateException(Bundle.AccountsText_nextPage_exception_msg());
        }
    }

    @Override
    @NbBundle.Messages("AccountsText.previousPage.exception.msg=No previous page.")
    public int previousPage() {
        if (hasPreviousPage()) {
            currentPage = pages.get(pages.indexOf(this.currentPage) - 1);
            return currentPage;
        } else {
            throw new IllegalStateException(Bundle.AccountsText_previousPage_exception_msg());
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
    @NbBundle.Messages("AccountsText.nextItem.exception.msg=No next item.")
    public int nextItem() {
        if (hasNextItem()) {
            return currentHitPerPage.merge(currentPage, 1, Integer::sum);
        } else {
            throw new IllegalStateException(Bundle.AccountsText_nextItem_exception_msg());
        }
    }

    @Override
    @NbBundle.Messages("AccountsText.previousItem.exception.msg=No previous item.")
    public int previousItem() {
        if (hasPreviousItem()) {
            return currentHitPerPage.merge(currentPage, -1, Integer::sum);
        } else {
            throw new IllegalStateException(Bundle.AccountsText_previousItem_exception_msg());
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
     * Initialize this object with information about which pages/chunks have
     * hits. Multiple calls will not change the initial results.
     */
    synchronized private void loadPageInfo() {
        if (isPageInfoLoaded) {
            return;
        }
        if (chunkId != null) {//if a chunk is specified, only show that chunk/page
            this.numberPagesForFile = 1;
            this.currentPage = chunkId;
            this.numberOfHitsPerPage.put(chunkId, 0);
            this.pages.add(chunkId);
            this.currentHitPerPage.put(chunkId, 0);
        } else {
            try {
                this.numberPagesForFile = solrServer.queryNumFileChunks(this.solrObjectId);
            } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                LOGGER.log(Level.WARNING, "Could not get number pages for content " + this.solrDocumentId, ex); //NON-NLS
                return;
            }

            //if has chunks, get pages with hits
            TreeSet<Integer> sortedPagesWithHits = new TreeSet<>();
            SolrQuery q = new SolrQuery();
            q.setShowDebugInfo(DEBUG); //debug
            q.setQuery(queryString);
            q.setFields(Server.Schema.ID.toString());  //for this case we only need the document ids
            q.addFilterQuery(Server.Schema.ID.toString() + ":" + this.solrObjectId + Server.CHUNK_ID_SEPARATOR + "*");

            try {
                QueryResponse response = solrServer.query(q, METHOD.POST);
                for (SolrDocument resultDoc : response.getResults()) {
                    final String resultDocumentId = resultDoc.getFieldValue(Server.Schema.ID.toString()).toString();
                    // Put the solr chunk id in the map
                    String resultChunkID = StringUtils.substringAfter(resultDocumentId, Server.CHUNK_ID_SEPARATOR);
                    if (StringUtils.isNotBlank(resultChunkID)) {
                        sortedPagesWithHits.add(Integer.parseInt(resultChunkID));
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
    @NbBundle.Messages({"AccountsText.getMarkup.noMatchMsg="
        + "<html><pre><span style\\\\='background\\\\:yellow'>There were no keyword hits on this page. <br />"
        + "The keyword could have been in the file name."
        + " <br />Advance to another page if present, or to view the original text, choose File Text"
        + " <br />in the drop down menu to the right...</span></pre></html>",
        "AccountsText.getMarkup.queryFailedMsg="
        + "<html><pre><span style\\\\='background\\\\:yellow'>Failed to retrieve keyword hit results."
        + " <br />Confirm that Autopsy can connect to the Solr server. "
        + "<br /></span></pre></html>"})
    public String getText() {
        loadPageInfo(); //inits once

        SolrQuery q = new SolrQuery();
        q.setShowDebugInfo(DEBUG); //debug
        q.addHighlightField(HIGHLIGHT_FIELD);
        q.setQuery(queryString);

        //set the documentID filter
        String queryDocumentID = this.solrObjectId + Server.CHUNK_ID_SEPARATOR + this.currentPage;
        q.addFilterQuery(Server.Schema.ID.toString() + ":" + queryDocumentID);

        //configure the highlighter
        q.setParam("hl.useFastVectorHighlighter", "true"); //fast highlighter scales better than standard one NON-NLS
        q.setParam("hl.tag.pre", HIGHLIGHT_PRE); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.tag.post", HIGHLIGHT_POST); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.fragListBuilder", "single"); //makes sense for FastVectorHighlighter only NON-NLS
        q.setParam("hl.maxAnalyzedChars", Server.HL_ANALYZE_CHARS_UNLIMITED); //docs says makes sense for the original Highlighter only, but not really //NON-NLS

        try {
            //extract highlighting and bail early on null responses
            Map<String, Map<String, List<String>>> highlightingPerDocument = solrServer.query(q, METHOD.POST).getHighlighting();
            Map<String, List<String>> highlightingPerField = highlightingPerDocument.get(queryDocumentID);
            if (highlightingPerField == null) {
                return Bundle.AccountsText_getMarkup_noMatchMsg();
            }
            List<String> highlights = highlightingPerField.get(HIGHLIGHT_FIELD);
            if (highlights == null) {
                return Bundle.AccountsText_getMarkup_noMatchMsg();
            }

            //There should only be one item
            String highlighting = highlights.get(0).trim();

            /*
             * use regex matcher to iterate over occurences of HIGHLIGHT_PRE,
             * and prepend them with an anchor tag.
             */
            Matcher m = ANCHOR_DETECTION_PATTERN.matcher(highlighting);
            StringBuffer sb = new StringBuffer(highlighting.length());
            int count = 0;
            while (m.find()) {
                count++;
                m.appendReplacement(sb, INSERT_PREFIX + count + INSERT_POSTFIX);
            }
            m.appendTail(sb);

            //store total hits for this page, now that we know it
            this.numberOfHitsPerPage.put(this.currentPage, count);
            if (this.currentItem() == 0 && this.hasNextItem()) {
                this.nextItem();
            }

            // extracted content (minus highlight tags) is HTML-escaped
            return "<html><pre>" + sb.toString() + "</pre></html>"; //NON-NLS
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error executing Solr highlighting query: " + keywords, ex); //NON-NLS
            return Bundle.AccountsText_getMarkup_queryFailedMsg();
        }
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean isSearchable() {
        return true;
    }

    @Override
    public String getAnchorPrefix() {
        return ANCHOR_NAME_PREFIX;
    }

    @Override
    public int getNumberHits() {
        if (!this.numberOfHitsPerPage.containsKey(this.currentPage)) {
            return 0;
        }
        return this.numberOfHitsPerPage.get(this.currentPage);
    }
}
