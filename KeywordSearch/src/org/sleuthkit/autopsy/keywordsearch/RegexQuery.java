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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
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

/**
 * The RegexQuery class supports issuing regular expression queries
 * against a Lucene index. It relies on the fact that content is
 * stored in it's original form in a "string" field (Server.Schema.CONTENT_STR).
 * To indicate to Lucene that these are regular expression queries, the query
 * string must be surrounded by '/' characters. Additionally, the characters
 * ".*" need to be added both before and after the search term to get hits
 * in the middle of text.
 *
 * Regular expression syntax supported by Lucene is not the same as Java
 * regular expression syntax. The Lucene syntax is documented here:
 *
 * https://lucene.apache.org/core/5_0_0/core/org/apache/lucene/util/automaton/RegExp.html
 */
final class RegexQuery implements KeywordSearchQuery {

    public static final Logger LOGGER = Logger.getLogger(RegexQuery.class.getName());
    private final List<KeywordQueryFilter> filters = new ArrayList<>();

    private final KeywordList keywordList;
    private final Keyword keyword;
    private String field = Server.Schema.CONTENT_STR.toString();
    private final String keywordString;
    static final private int MAX_RESULTS = 512;
    private boolean escaped;
    private String escapedQuery;

    // These are the valid characters that can appear either before or after a
    // keyword hit. We use these characters to try to turn the hit into a
    // token that can be more readily matched when it comes to highlighting
    // against the Schema.TEXT field later.
    private static final String BOUNDARY_CHARS = "[\\s\\[\\]\\(\\)\\,\\\"\\\'\\!\\?\\.\\/\\:\\;\\=\\<\\>\\^\\{\\}]"; //NON-NLS

    private boolean queryStringContainsWildcardPrefix = false;
    private boolean queryStringContainsWildcardSuffix = false;

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

        if (this.keywordString.startsWith(".*")) {
            this.queryStringContainsWildcardPrefix = true;
        }

        if (this.keywordString.endsWith(".*")) {
            this.queryStringContainsWildcardSuffix = true;
        }
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    @Override
    public boolean validate() {
        // For now, we are performing Java regex validation even though Lucene
        // regex syntax is a small subset 
        if (keywordString.isEmpty()) {
            return false;
        }
        try {
            Pattern.compile(keywordString);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {
        QueryResults results = new QueryResults(this, keywordList);

        ListMultimap<Keyword, KeywordHit> hitsMultMap = ArrayListMultimap.create();

        final Server solrServer = KeywordSearch.getServer();
        SolrQuery solrQuery = new SolrQuery();

        /**
         * The provided regular expression may include wildcards at the
         * beginning and/or end. These wildcards are used to indicate that
         * the user wants to find hits for the regex that are embedded
         * within other characters. For example, if we are given .*127.0.0.1.*
         * as a regular expression, this will produce hits for:
         * (a) " 127.0.0.1 " as a standalone token (surrounded by whitespace).
         * (b) "abc127.0.0.1def" where the IP address is surrounded by other characters.
         *
         * If we are given this type of regex, we do not need to add our own
         * wildcards to anchor the query. Otherwise, we need to add wildcard
         * anchors because Lucene string regex searches default to using ^ and $
         * to match the entire string.
         */

        // We construct the query by surrounding it with slashes (to indicate it is
        // a regular expression search) and .* as anchors (if the query doesn't
        // already have them).
        solrQuery.setQuery((field == null ? Server.Schema.CONTENT_STR.toString() : field) + ":/"
                + (queryStringContainsWildcardPrefix ? "" : ".*") + getQueryString()
                + (queryStringContainsWildcardSuffix ? "" : ".*") + "/");

        // Set the fields we want to have returned by the query.
        if (KeywordSearchSettings.getShowSnippets()) {
            solrQuery.setFields(Server.Schema.CONTENT_STR.toString(), Server.Schema.ID.toString(), Server.Schema.CHUNK_SIZE.toString());
        } else {
            solrQuery.setFields(Server.Schema.ID.toString(), Server.Schema.CHUNK_SIZE.toString());
        }
        filters.stream()
                .map(KeywordQueryFilter::toString)
                .forEach(solrQuery::addFilterQuery);

        solrQuery.setRows(MAX_RESULTS);
        // Setting the sort order is necessary for cursor based paging to work.
        solrQuery.setSort(SortClause.asc(Server.Schema.ID.toString()));
        
        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        SolrDocumentList resultList = null;
        boolean allResultsProcessed = false;

        while (!allResultsProcessed) {
            try {
                solrQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                QueryResponse response = solrServer.query(solrQuery, SolrRequest.METHOD.POST);
                resultList = response.getResults();

                for (SolrDocument resultDoc : resultList) {
                    try {
                        List<KeywordHit> keywordHits = createKeywordHits(resultDoc);
                        for (KeywordHit hit : keywordHits) {
                            hitsMultMap.put(new Keyword(hit.getHit(), true), hit);
                        }
                    } catch (TskException ex) {
                        //
                    }
                }
                
                String nextCursorMark = response.getNextCursorMark();
                if (cursorMark.equals(nextCursorMark)) {
                    allResultsProcessed = true;
                }
                cursorMark = nextCursorMark;
            } catch (KeywordSearchModuleException ex) {
                LOGGER.log(Level.SEVERE, "Error executing Lucene Solr Query: " + keywordString, ex); //NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(Server.class, "Server.query.exception.msg", keywordString), ex.getCause().getMessage());
            }
        }
        
        for (Keyword k : hitsMultMap.keySet()) {
            results.addResult(k, hitsMultMap.get(k));
        }

        return results;
    }

    private List<KeywordHit> createKeywordHits(SolrDocument solrDoc) throws TskException {

        List<KeywordHit> hits = new ArrayList<>();
        final String docId = solrDoc.getFieldValue(Server.Schema.ID.toString()).toString();
        final Integer chunkSize = (Integer) solrDoc.getFieldValue(Server.Schema.CHUNK_SIZE.toString());

        String content = solrDoc.getOrDefault(Server.Schema.CONTENT_STR.toString(), "").toString(); //NON-NLS

        // By default, we create keyword hits on whitespace or punctuation character boundaries.
        // Having a set of well defined boundary characters produces hits that can
        // subsequently be matched for highlighting against the tokens produced by
        // the standard tokenizer.
        // This behavior can be overridden by the user if they give us a search string
        // with .* at either the start and/or end of the string. This basically tells us find
        // all hits instead of the ones surrounded by one of our boundary characters.
        String keywordTokenRegex =
                // If the given search string starts with .*, we ignore our default
                // boundary prefix characters
                (queryStringContainsWildcardPrefix ? "" : BOUNDARY_CHARS) //NON-NLS
                + keywordString
                // If the given search string ends with .*, we ignore our default
                // boundary suffix characters
                + (queryStringContainsWildcardSuffix ? "" : BOUNDARY_CHARS); //NON-NLS

        Matcher hitMatcher = Pattern.compile(keywordTokenRegex).matcher(content);
        int offset = 0;

        while (hitMatcher.find(offset)) {
            StringBuilder snippet = new StringBuilder();

            //"parent" entries in the index don't have chunk size, so just accept those hits
            if (chunkSize != null && hitMatcher.start() >= chunkSize) {
                break;
            }

            String hit = hitMatcher.group();

            // Back the matcher offset up by 1 character as it will have eaten
            // a single space/newline/other boundary character at the end of the hit.
            // This was causing us to miss hits that appeared consecutively in the
            // input where they were separated by a single boundary character.
            offset = hitMatcher.end() - 1;

            // Remove leading and trailing whitespace.
            hit = hit.trim();

            // Remove any remaining leading and trailing boundary characters.
            if (!queryStringContainsWildcardPrefix) {
                hit = hit.replaceAll("^" + BOUNDARY_CHARS, ""); //NON-NLS
            }
            if (!queryStringContainsWildcardSuffix) {
                hit = hit.replaceAll(BOUNDARY_CHARS + "$", ""); //NON-NLS
            }

            /*
             * If searching for credit card account numbers, do a Luhn check
             * on the term and discard it if it does not pass.
             */
            if (keyword.getArtifactAttributeType() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
                Matcher ccnMatcher = CREDIT_CARD_NUM_PATTERN.matcher(hit);
                if (ccnMatcher.find()) {
                    final String ccn = CharMatcher.anyOf(" -").removeFrom(ccnMatcher.group("ccn"));
                    if (false == TermsComponentQuery.CREDIT_CARD_NUM_LUHN_CHECK.isValid(ccn)) {
                        continue;
                    }
                } else {
                    continue;
                }
            }

            /**
             * Get the snippet from the document if keyword search is configured
             * to use snippets.
             */
            if (KeywordSearchSettings.getShowSnippets()) {
                int maxIndex = content.length() - 1;
                snippet.append(content.substring(Integer.max(0, hitMatcher.start() - 20), Integer.max(0, hitMatcher.start() + 1)));
                snippet.appendCodePoint(171);
                snippet.append(hit);
                snippet.appendCodePoint(171);
                snippet.append(content.substring(Integer.min(maxIndex, hitMatcher.end() - 1), Integer.min(maxIndex, hitMatcher.end() + 20)));
            }

            hits.add(new KeywordHit(docId, snippet.toString(), hit));
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
