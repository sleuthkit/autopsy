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
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.openide.util.Exceptions;
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

    private static final Logger LOGGER = Logger.getLogger(AccountsText.class.getName());
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    private static final String HIGHLIGHT_PRE = "<span style='background:yellow'>"; //NON-NLS
    private static final String HIGHLIGHT_POST = "</span>"; //NON-NLS
    private static final String ANCHOR_NAME_PREFIX = AccountsText.class.getName() + "_";

    private static final String INSERT_PREFIX = "<a name='" + ANCHOR_NAME_PREFIX; //NON-NLS
    private static final String INSERT_POSTFIX = "'></a>$0"; //$0 will insert current regex match  //NON-NLS
    private static final Pattern ANCHOR_DETECTION_PATTERN = Pattern.compile(HIGHLIGHT_PRE);

    private static final BlackboardAttribute.Type TSK_KEYWORD_SEARCH_DOCUMENT_ID = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID);
    private static final BlackboardAttribute.Type TSK_KEYWORD_HIT_DOCUMENT_IDS = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_HIT_DOCUMENT_IDS);
    private static final BlackboardAttribute.Type TSK_CARD_NUMBER = new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER);

    private static final String FIELD = Server.Schema.CONTENT_STR.toString();

    private final Server solrServer = KeywordSearch.getServer();

    private long solrObjectId = 0;
    private final Integer chunkId;

    private final Set<String> accountNumbers = new HashSet<>();

    private final String displayName;

    private boolean isPageInfoLoaded = false;
    private int numberPagesForFile = 0;
    private int currentPage = 0;
    //list of pages, used for iterating back and forth.  Only stores pages with hits
    private List<Integer> pages = new ArrayList<>();
    //map from page/chunk to number of hits. value is 0 if not yet known.
    private final LinkedHashMap<Integer, Integer> numberOfHitsPerPage = new LinkedHashMap<>();
    //map from page/chunk number to current hit on that page.
    private final HashMap<Integer, Integer> currentHitPerPage = new HashMap<>();

    AccountsText(BlackboardArtifact artifact) {
        this(Arrays.asList(artifact));
    }

    AccountsText(Collection<? extends BlackboardArtifact> artifacts) {
        for (BlackboardArtifact artifact : artifacts) {
            final long objectID = artifact.getObjectID();

            if (solrObjectId == 0) {
                solrObjectId = objectID;
            } else if (solrObjectId != objectID) {
                throw new IllegalStateException("not all artifacts are from the same object!");
            }

            try {
                accountNumbers.add(artifact.getAttribute(TSK_CARD_NUMBER).getValueString());
                final BlackboardAttribute docIDs = artifact.getAttribute(TSK_KEYWORD_HIT_DOCUMENT_IDS);
                List<String> rawIDs = new ArrayList<>();
                if (docIDs != null) {
                    rawIDs.addAll(Arrays.asList(docIDs.getValueString().split(",")));
                }

                final BlackboardAttribute docID = artifact.getAttribute(TSK_KEYWORD_SEARCH_DOCUMENT_ID);
                if (docID != null) {
                    rawIDs.add(docID.getValueString());
                }

                rawIDs.stream()
                        .map(String::trim)
                        .map(t -> StringUtils.substringAfterLast(t, Server.CHUNK_ID_SEPARATOR))
                        .map(Integer::valueOf)
                        .forEach(chunkID -> {
                            pages.add(chunkID);
                            numberOfHitsPerPage.put(chunkID, 0);
                            currentHitPerPage.put(chunkID, 0);
                        });

            } catch (TskCoreException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
        
        pages = pages.stream().sorted().distinct().collect(Collectors.toList());
        if (pages.size() == 1) {
            chunkId = pages.get(0);
         
        } else {
            this.chunkId = null;
        }

        this.currentPage = pages.stream().findFirst().orElse(1);

        displayName = artifacts.size() == 1
                ? Bundle.AccountsText_creditCardNumber()
                : Bundle.AccountsText_creditCardNumbers();
    }

    @NbBundle.Messages({
        "AccountsText.creditCardNumber=Credit Card Number",
        "AccountsText.creditCardNumbers=Credit Card Numbers"})
    AccountsText(String solrDocumentId, Set<String> keywords) {

        this.accountNumbers.addAll(keywords);

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

        displayName = accountNumbers.size() == 1
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
     
            try {
                this.numberPagesForFile = solrServer.queryNumFileChunks(this.solrObjectId);
            } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                LOGGER.log(Level.WARNING, "Could not get number pages for content " + this.solrObjectId, ex); //NON-NLS
                return;
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

        //set the documentID filter
        String queryDocumentID = this.solrObjectId + Server.CHUNK_ID_SEPARATOR + this.currentPage;
        q.setQuery(Server.Schema.ID.toString() + ":" + queryDocumentID);
        q.setFields(FIELD);

        try {
            QueryResponse queryResponse = solrServer.query(q, METHOD.POST);

            String highlighting = attemptManualHighlighting(queryResponse.getResults()).trim();

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
            LOGGER.log(Level.WARNING, "Error executing Solr highlighting query: " + accountNumbers, ex); //NON-NLS
            return Bundle.AccountsText_getMarkup_queryFailedMsg();
        }
    }

    private String attemptManualHighlighting(SolrDocumentList solrDocumentList) {
        if (solrDocumentList.isEmpty()) {
            return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.noMatchMsg");
        }

        // It doesn't make sense for there to be more than a single document in
        // the list since this class presents a single page (document) of highlighted
        // content at a time.  Hence we can just use get(0).
        String text = solrDocumentList.get(0).getOrDefault(FIELD, "").toString();

        // Escape any HTML content that may be in the text. This is needed in
        // order to correctly display the text in the content viewer.
        // Must be done before highlighting tags are added. If we were to 
        // perform HTML escaping after adding the highlighting tags we would
        // not see highlighted text in the content viewer.
        text = StringEscapeUtils.escapeHtml(text);

        StringBuilder highlightedText = new StringBuilder("");

        for (String unquotedKeyword : accountNumbers) {
            int textOffset = 0;
            int hitOffset;
            while ((hitOffset = StringUtils.indexOfIgnoreCase(text, unquotedKeyword, textOffset)) != -1) {
                // Append the portion of text up to (but not including) the hit.
                highlightedText.append(text.substring(textOffset, hitOffset));
                // Add in the highlighting around the keyword.
                highlightedText.append(HIGHLIGHT_PRE);
                highlightedText.append(unquotedKeyword);
                highlightedText.append(HIGHLIGHT_POST);

                // Advance the text offset past the keyword.
                textOffset = hitOffset + unquotedKeyword.length();
            }
            // Append the remainder of text field
            highlightedText.append(text.substring(textOffset, text.length()));
            if (highlightedText.length() > 0) {

            } else {
                return NbBundle.getMessage(this.getClass(), "HighlightedMatchesSource.getMarkup.noMatchMsg");
            }
            text = highlightedText.toString();
            highlightedText = new StringBuilder("");
        }
        return text;
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
