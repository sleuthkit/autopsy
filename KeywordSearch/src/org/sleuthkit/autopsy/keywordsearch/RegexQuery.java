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

import com.google.common.base.CharMatcher;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import static org.sleuthkit.autopsy.keywordsearch.KeywordSearchSettings.MODULE_NAME;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.CREDIT_CARD_NUM_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.CREDIT_CARD_TRACK2_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.KEYWORD_SEARCH_DOCUMENT_ID;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskException;

final class RegexQuery implements KeywordSearchQuery {

    public static final Logger LOGGER = Logger.getLogger(RegexQuery.class.getName());
    private final List<KeywordQueryFilter> filters = new ArrayList<>();

    private final KeywordList keywordList;
    private final Keyword keyword;
    private String field = "content_str";
    private final String keywordString;
    static final private int MAX_RESULTS = 20000;
    private boolean escaped;
    private String escapedQuery;

    /**
     * Constructor with query to process.
     *
     * @param keywordList
     * @param keyword
     */
    RegexQuery(KeywordList keywordList, Keyword keyword) {
        this.keywordList = keywordList;
        this.keyword = keyword;
        this.keywordString = keyword.getSearchTerm();
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {
        QueryResults results = new QueryResults(this, keywordList);

        ListMultimap<Keyword, KeywordHit> hitsMultMap = ArrayListMultimap.create();

        final Server solrServer = KeywordSearch.getServer();
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setShowDebugInfo(true); //debug

        solrQuery.setQuery((field == null ? Server.Schema.CONTENT_STR : field) + ":/.*" + getQueryString() + ".*/");
        solrQuery.setRows(MAX_RESULTS);
        if (KeywordSearchSettings.getShowSnippets()) {
            solrQuery.setFields("content_str", Server.Schema.ID.toString());
        } else {
            solrQuery.setFields(Server.Schema.ID.toString());
        }
        filters.stream()
                .map(KeywordQueryFilter::toString)
                .forEach(solrQuery::addFilterQuery);

        int start = 0;
        SolrDocumentList resultList = null;
        // cycle through results in sets of MAX_RESULTS
        while (resultList == null || start < resultList.getNumFound()) {
            solrQuery.setStart(start);

            try {
                final QueryResponse response = solrServer.query(solrQuery, SolrRequest.METHOD.POST);
                resultList = response.getResults();

                for (SolrDocument resultDoc : resultList) {

                    try {
                        List<KeywordHit> keywordHits = createKeywordtHits(resultDoc);
                        for (KeywordHit hit : keywordHits) {
                            hitsMultMap.put(new Keyword(hit.getHit(), true), hit);
                        }
                    } catch (TskException ex) {
                        //
                    }
                }
            } catch (KeywordSearchModuleException ex) {
                LOGGER.log(Level.SEVERE, "Error executing Lucene Solr Query: " + keywordString, ex); //NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(Server.class, "Server.query.exception.msg", keywordString), ex.getCause().getMessage());
            }

            start = start + MAX_RESULTS;
        }
        for (Keyword k : hitsMultMap.keySet()) {
            results.addResult(k, hitsMultMap.get(k));
        }

        return results;
    }

    /**
     * Create the minimum set of documents. Ignores chunk IDs. Only one hit per
     * file in results.
     *
     * @param resultList
     *
     * @return
     */
    private Set<SolrDocument> filterOneHitPerDocument(SolrDocumentList resultList) {
        // sort the list so that we consistently pick the same chunk each time.
        // note this sort is doing a string comparison and not an integer comparison, so
        // chunk 10 will be smaller than chunk 9.
        Collections.sort(resultList, (SolrDocument left, SolrDocument right) -> {
            // ID is in the form of ObjectId_Chunk
            String leftID = left.getFieldValue(Server.Schema.ID.toString()).toString();
            String rightID = right.getFieldValue(Server.Schema.ID.toString()).toString();
            return leftID.compareTo(rightID);
        });

        // NOTE: We could probably just iterate through the list and compare each ID with the
        // previous ID to get the unique documents faster than using this set now that the list
        // is sorted.
        Set<SolrDocument> solrDocumentsWithMatches = new TreeSet<>(new LuceneQuery.SolrDocumentComparatorIgnoresChunkId());
        solrDocumentsWithMatches.addAll(resultList);
        return solrDocumentsWithMatches;
    }

    private List<KeywordHit> createKeywordtHits(SolrDocument solrDoc) throws TskException {

        List<KeywordHit> hits = new ArrayList<>();
        /**
         * Get the first snippet from the document if keyword search is
         * configured to use snippets.
         */
        final String docId = solrDoc.getFieldValue(Server.Schema.ID.toString()).toString();

        Collection<Object> fieldValues = solrDoc.getFieldValues("content_str");

        for (Object value : fieldValues) {
            String content = value.toString();

            Matcher hitMatcher = Pattern.compile(keywordString, Pattern.CASE_INSENSITIVE).matcher(content);

            while (hitMatcher.find()) {
                String snippet = "";
                final String hit = hitMatcher.group();
                /*
                 * If searching for credit card account numbers, do a Luhn check
                 * on the term and discard it if it does not pass.
                 */
                if (keyword.getArtifactAttributeType() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
                    Matcher ccnMatcher = CREDIT_CARD_NUM_PATTERN.matcher(hit);
                    ccnMatcher.find();
                    final String ccn = CharMatcher.anyOf(" -").removeFrom(ccnMatcher.group("ccn"));
                    if (false == TermsComponentQuery.CREDIT_CARD_NUM_LUHN_CHECK.isValid(ccn)) {
                        continue;
                    }
                }

                if (KeywordSearchSettings.getShowSnippets()) {
                    int maxIndex = content.length() - 1;
                    snippet = content.substring(Integer.max(0, hitMatcher.start() - 30), Integer.max(0, hitMatcher.start() - 1));
                    snippet += "<<" + hit + "<<";
                    snippet += content.substring(Integer.min(maxIndex, hitMatcher.end() + 1), Integer.min(maxIndex, hitMatcher.end() + 30));
                }

                hits.add(new KeywordHit(docId, snippet, hit));
            }
        }
        return hits;
    }

    @Override
    public void addFilter(KeywordQueryFilter filter) {
        this.filters.add(filter);
    }

    @Override
    public void setField(String field) {
        this.field = field;
    }

    @Override
    public void setSubstringQuery() {
    }

    @Override
    synchronized public void escape() {
        if (isEscaped() == false) {
            escapedQuery = KeywordSearchUtil.escapeLuceneQuery(keywordString);
            escaped = true;
        }
    }

    @Override
    synchronized public boolean isEscaped() {
        return escaped;
    }

    @Override
    public boolean isLiteral() {
        return false;
    }

    @Override
    public String getQueryString() {
        return keyword.getSearchTerm();
    }

    @Override
    synchronized public String getEscapedQueryString() {
        if (false == isEscaped()) {
            escape();
        }
        return escapedQuery;
    }

    /**
     * Converts the keyword hits for a given search term into artifacts.
     *
     * @param searchTerm The search term.
     * @param hit        The keyword hit.
     * @param snippet    The document snippet that contains the hit
     * @param listName   The name of the keyword list that contained the keyword
     *                   for which the hit was found.
     *
     *
     *
     * @return An object that wraps an artifact and a mapping by id of its
     *         attributes.
     */
    // TODO: Are we actually making meaningful use of the KeywordCachedArtifact
    // class?
    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String searchTerm, KeywordHit hit, String snippet, String listName) {
        /*
         * Create either a "plain vanilla" keyword hit artifact with keyword and
         * regex attributes, or a credit card account artifact with attributes
         * parsed from from the snippet for the hit and looked up based on the
         * parsed bank identifcation number.
         */
        BlackboardArtifact newArtifact;
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        if (keyword.getArtifactAttributeType() != BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, searchTerm));
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getSearchTerm()));
            try {
                newArtifact = hit.getContent().newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error adding artifact for keyword hit to blackboard", ex); //NON-NLS
                return null;
            }
        } else {
            /*
             * Parse the credit card account attributes from the snippet for the
             * hit.
             */
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE, MODULE_NAME, Account.Type.CREDIT_CARD.name()));
            Map<BlackboardAttribute.Type, BlackboardAttribute> parsedTrackAttributeMap = new HashMap<>();
            Matcher matcher = TermsComponentQuery.CREDIT_CARD_TRACK1_PATTERN.matcher(hit.getSnippet());
            if (matcher.find()) {
                parseTrack1Data(parsedTrackAttributeMap, matcher);
            }
            matcher = CREDIT_CARD_TRACK2_PATTERN.matcher(hit.getSnippet());
            if (matcher.find()) {
                parseTrack2Data(parsedTrackAttributeMap, matcher);
            }
            final BlackboardAttribute ccnAttribute = parsedTrackAttributeMap.get(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER));
            if (ccnAttribute == null || StringUtils.isBlank(ccnAttribute.getValueString())) {
                if (hit.isArtifactHit()) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for artifact keyword hit: term = %s, snippet = '%s', artifact id = %d", searchTerm, hit.getSnippet(), hit.getArtifact().getArtifactID())); //NON-NLS
                } else {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s', object id = %d", searchTerm, hit.getSnippet(), hit.getContent().getId())); //NON-NLS
                }
                return null;
            }
            attributes.addAll(parsedTrackAttributeMap.values());

            /*
             * Look up the bank name, scheme, etc. attributes for the bank
             * indentification number (BIN).
             */
            final int bin = Integer.parseInt(ccnAttribute.getValueString().substring(0, 8));
            CreditCards.BankIdentificationNumber binInfo = CreditCards.getBINInfo(bin);
            if (binInfo != null) {
                binInfo.getScheme().ifPresent(scheme
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_SCHEME, MODULE_NAME, scheme)));
                binInfo.getCardType().ifPresent(cardType
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_TYPE, MODULE_NAME, cardType)));
                binInfo.getBrand().ifPresent(brand
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BRAND_NAME, MODULE_NAME, brand)));
                binInfo.getBankName().ifPresent(bankName
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_BANK_NAME, MODULE_NAME, bankName)));
                binInfo.getBankPhoneNumber().ifPresent(phoneNumber
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, MODULE_NAME, phoneNumber)));
                binInfo.getBankURL().ifPresent(url
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_URL, MODULE_NAME, url)));
                binInfo.getCountry().ifPresent(country
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNTRY, MODULE_NAME, country)));
                binInfo.getBankCity().ifPresent(city
                        -> attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CITY, MODULE_NAME, city)));
            }

            /*
             * If the hit is from unused or unallocated space, record the Solr
             * document id to support showing just the chunk that contained the
             * hit.
             */
            if (hit.getContent() instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) hit.getContent();
                if (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS
                        || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
                    attributes.add(new BlackboardAttribute(KEYWORD_SEARCH_DOCUMENT_ID, MODULE_NAME, hit.getSolrDocumentId()));
                }
            }

            /*
             * Create an account artifact.
             */
            try {
                newArtifact = hit.getContent().newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_ACCOUNT);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error adding artifact for account to blackboard", ex); //NON-NLS
                return null;
            }
        }

        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }
        if (hit.isArtifactHit()) {
            attributes.add(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
        }

        try {
            newArtifact.addAttributes(attributes);
            KeywordCachedArtifact writeResult = new KeywordCachedArtifact(newArtifact);
            writeResult.add(attributes);
            return writeResult;
        } catch (TskCoreException e) {
            LOGGER.log(Level.SEVERE, "Error adding bb attributes for terms search artifact", e); //NON-NLS
            return null;
        }
    }

    /**
     * Parses the track 2 data from the snippet for a credit card account number
     * hit and turns them into artifact attributes.
     *
     * @param attributesMap A map of artifact attribute objects, used to avoid
     *                      creating duplicate attributes.
     * @param matcher       A matcher for the snippet.
     */
    static private void parseTrack2Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributesMap, Matcher matcher) {
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_LRC, "LRC", matcher);
    }

    /**
     * Parses the track 1 data from the snippet for a credit card account number
     * hit and turns them into artifact attributes. The track 1 data has the
     * same fields as the track two data, plus the account holder's name.
     *
     * @param attributesMap A map of artifact attribute objects, used to avoid
     *                      creating duplicate attributes.
     * @param matcher       A matcher for the snippet.
     */
    static private void parseTrack1Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Matcher matcher) {
        parseTrack2Data(attributeMap, matcher);
        addAttributeIfNotAlreadyCaptured(attributeMap, BlackboardAttribute.ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
    }

    /**
     * Creates an attribute of the the given type to the given artifact with a
     * value parsed from the snippet for a credit account number hit.
     *
     * @param attributesMap A map of artifact attribute objects, used to avoid
     *                      creating duplicate attributes.
     * @param attrType      The type of attribute to create.
     * @param groupName     The group name of the regular expression that was
     *                      used to parse the attribute data.
     * @param matcher       A matcher for the snippet.
     */
    static private void addAttributeIfNotAlreadyCaptured(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, BlackboardAttribute.ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);
        attributeMap.computeIfAbsent(type, (BlackboardAttribute.Type t) -> {
            String value = matcher.group(groupName);
            if (attrType.equals(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER)) {
                value = CharMatcher.anyOf(" -").removeFrom(value);
            }
            if (StringUtils.isNotBlank(value)) {
                return new BlackboardAttribute(attrType, MODULE_NAME, value);
            }
            return null;
        });
    }

}
