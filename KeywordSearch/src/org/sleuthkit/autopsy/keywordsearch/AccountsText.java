/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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

import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.concurrent.GuardedBy;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

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

    private static final Logger logger = Logger.getLogger(AccountsText.class.getName());
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    private static final String CCN_REGEX = "(%?)(B?)([0-9][ \\-]*?){12,19}(\\^?)";

    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String ANCHOR_NAME_PREFIX = AccountsText.class.getName() + "_";

    private static final String INSERT_PREFIX = "<a name='" + ANCHOR_NAME_PREFIX; //NON-NLS
    private static final String INSERT_POSTFIX = "'></a>$0"; //$0 will insert current regex match  //NON-NLS
    private static final Pattern ANCHOR_DETECTION_PATTERN = Pattern.compile(HIGHLIGHT_PRE);

    private static final BlackboardAttribute.Type TSK_KEYWORD_SEARCH_DOCUMENT_ID = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID);
    private static final BlackboardAttribute.Type TSK_CARD_NUMBER = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER);
    private static final BlackboardAttribute.Type TSK_KEYWORD = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD);

    private static final String FIELD = Server.Schema.CONTENT_STR.toString();

    private final Server solrServer = KeywordSearch.getServer();

    private final long solrObjectId;
    private final Collection<? extends BlackboardArtifact> artifacts;
    private final Set<String> accountNumbers = new HashSet<>();
    private final String title;

    @GuardedBy("this")
    private boolean isPageInfoLoaded = false;
    private int numberPagesForFile = 0;
    private Integer currentPage = 0;

    /**
     * map from page/chunk to number of hits. value is 0 if not yet known.
     */
    private final TreeMap<Integer, Integer> numberOfHitsPerPage = new TreeMap<>();

    /**
     * set of pages, used for iterating back and forth. Only stores pages with
     * hits
     */
    private final Set<Integer> pages = numberOfHitsPerPage.keySet();

    /**
     * map from page/chunk number to current hit on that page.
     */
    private final HashMap<Integer, Integer> currentHitPerPage = new HashMap<>();

    AccountsText(long objectID, BlackboardArtifact artifact) {
        this(objectID, Arrays.asList(artifact));
    }

    @NbBundle.Messages({
        "AccountsText.creditCardNumber=Credit Card Number",
        "AccountsText.creditCardNumbers=Credit Card Numbers"})
    AccountsText(long objectID, Collection<? extends BlackboardArtifact> artifacts) {
        this.solrObjectId = objectID;
        this.artifacts = artifacts;
        title = artifacts.size() == 1
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
        return getIndexOfCurrentPage() < pages.size() - 1;

    }

    @Override
    public boolean hasPreviousPage() {
        return getIndexOfCurrentPage() > 0;
    }

    @Override
    @NbBundle.Messages("AccountsText.nextPage.exception.msg=No next page.")
    public int nextPage() {
        if (hasNextPage()) {
            currentPage = Iterators.get(pages.iterator(), getIndexOfCurrentPage() + 1);
            return currentPage;
        } else {
            throw new IllegalStateException(Bundle.AccountsText_nextPage_exception_msg());
        }
    }

    @Override
    @NbBundle.Messages("AccountsText.previousPage.exception.msg=No previous page.")
    public int previousPage() {
        if (hasPreviousPage()) {
            currentPage = Iterators.get(pages.iterator(), getIndexOfCurrentPage() - 1);
            return currentPage;
        } else {
            throw new IllegalStateException(Bundle.AccountsText_previousPage_exception_msg());
        }
    }

    private int getIndexOfCurrentPage() {
        return Iterators.indexOf(pages.iterator(), this.currentPage::equals);
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
        return currentHitPerPage.getOrDefault(currentPage, 0);
    }

    /**
     * Initialize this object with information about which pages/chunks have
     * hits. Multiple calls will not change the initial results.
     */
    synchronized private void loadPageInfo() throws IllegalStateException, TskCoreException, KeywordSearchModuleException, NoOpenCoreException {
        if (isPageInfoLoaded) {
            return;
        }

        this.numberPagesForFile = solrServer.queryNumFileChunks(this.solrObjectId);

        boolean needsQuery = false;

        for (BlackboardArtifact artifact : artifacts) {
            if (solrObjectId != artifact.getObjectID()) {
                throw new IllegalStateException("not all artifacts are from the same object!");
            }

            //add both the canonical form and the form in the text as accountNumbers to highlight.
            BlackboardAttribute attribute = artifact.getAttribute(TSK_KEYWORD);
            this.accountNumbers.add(attribute.getValueString());
            attribute = artifact.getAttribute(TSK_CARD_NUMBER);
            this.accountNumbers.add(attribute.getValueString());

            //if the chunk id is present just use that.
            Optional<Integer> chunkID =
                    Optional.ofNullable(artifact.getAttribute(TSK_KEYWORD_SEARCH_DOCUMENT_ID))
                            .map(BlackboardAttribute::getValueString)
                            .map(String::trim)
                            .map(kwsdocID -> StringUtils.substringAfterLast(kwsdocID, Server.CHUNK_ID_SEPARATOR))
                            .map(Integer::valueOf);
            if (chunkID.isPresent()) {
                numberOfHitsPerPage.put(chunkID.get(), 0);
                currentHitPerPage.put(chunkID.get(), 0);
            } else {
                //otherwise we need to do a query to figure out the paging.
                needsQuery = true;
                // we can't break the for loop here because we need to accumulate all the accountNumbers
            }
        }

        if (needsQuery) {
            // Run a query to figure out which chunks for the current object have hits.
            Keyword queryKeyword = new Keyword(CCN_REGEX, false, false);
            KeywordSearchQuery chunksQuery = KeywordSearchUtil.getQueryForKeyword(queryKeyword, new KeywordList(Arrays.asList(queryKeyword)));
            chunksQuery.addFilter(new KeywordQueryFilter(KeywordQueryFilter.FilterType.CHUNK, this.solrObjectId));
            //load the chunks/pages from the result of the query.
            loadPageInfoFromHits(chunksQuery.performQuery());
        }

        this.currentPage = pages.stream().findFirst().orElse(1);

        isPageInfoLoaded = true;
    }

    /**
     * Load the paging info from the QueryResults object.
     *
     * @param hits The QueryResults to load the paging info from.
     */
    synchronized private void loadPageInfoFromHits(QueryResults hits) {
        //organize the hits by page, filter as needed
        for (Keyword k : hits.getKeywords()) {
            for (KeywordHit hit : hits.getResults(k)) {
                int chunkID = hit.getChunkId();
                if (chunkID != 0 && this.solrObjectId == hit.getSolrObjectId()) {
                    String hitString = hit.getHit();
                    if (accountNumbers.stream().anyMatch(hitString::contains)) {
                        numberOfHitsPerPage.put(chunkID, 0); //unknown number of matches in the page
                        currentHitPerPage.put(chunkID, 0); //set current hit to 0th
                    }
                }
            }
        }
    }

    @Override
    public String getText() {
        try {
            loadPageInfo(); //inits once

            SolrQuery q = new SolrQuery();
            q.setShowDebugInfo(DEBUG); //debug

            String contentIdStr = this.solrObjectId + Server.CHUNK_ID_SEPARATOR + this.currentPage;
            final String filterQuery = Server.Schema.ID.toString() + ":" + contentIdStr;
            //set the documentID filter
            q.setQuery(filterQuery);
            q.setFields(FIELD);

            QueryResponse queryResponse = solrServer.query(q, METHOD.POST);

            String highlightedText =
                    HighlightedText.attemptManualHighlighting(
                            queryResponse.getResults(),
                            Server.Schema.CONTENT_STR.toString(),
                            accountNumbers
                    ).trim();

            highlightedText = insertAnchors(highlightedText);

            // extracted content (minus highlight tags) is HTML-escaped
            return "<html><pre>" + highlightedText + "</pre></html>"; //NON-NLS
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Error getting highlighted text for Solr doc id " + this.solrObjectId + ", chunkID " + this.currentPage, ex); //NON-NLS
            return Bundle.IndexedText_errorMessage_errorGettingText();
        }
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
        /*
         * use regex matcher to iterate over occurences of HIGHLIGHT_PRE, and
         * prepend them with an anchor tag.
         */
        Matcher m = ANCHOR_DETECTION_PATTERN.matcher(searchableContent);
        StringBuffer sb = new StringBuffer(searchableContent.length());
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
        return sb.toString();
    }

    @Override
    public String toString() {
        return title;
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
        return numberOfHitsPerPage.getOrDefault(currentPage, 0);
    }
}
