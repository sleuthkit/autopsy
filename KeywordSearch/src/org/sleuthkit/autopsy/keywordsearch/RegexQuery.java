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

import com.google.common.base.CharMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.DomainValidator;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.SortClause;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CursorMarkParams;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import static org.sleuthkit.autopsy.keywordsearch.KeywordSearchSettings.MODULE_NAME;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.CREDIT_CARD_NUM_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.CREDIT_CARD_TRACK2_PATTERN;
import static org.sleuthkit.autopsy.keywordsearch.TermsComponentQuery.KEYWORD_SEARCH_DOCUMENT_ID;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.AccountFileInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * The RegexQuery class supports issuing regular expression queries against a
 * Lucene index. It relies on the fact that content is stored in it's original
 * form in a "string" field (Server.Schema.CONTENT_STR). To indicate to Lucene
 * that these are regular expression queries, the query string must be
 * surrounded by '/' characters. Additionally, the characters ".*" need to be
 * added both before and after the search term to get hits in the middle of
 * text.
 *
 * Regular expression syntax supported by Lucene is not the same as Java regular
 * expression syntax. The Lucene syntax is documented here:
 *
 * https://lucene.apache.org/core/5_0_0/core/org/apache/lucene/util/automaton/RegExp.html
 */
final class RegexQuery implements KeywordSearchQuery {

    public static final Logger LOGGER = Logger.getLogger(RegexQuery.class.getName());

    /**
     * Lucene regular expressions do not support the following Java predefined
     * and POSIX character classes. There are other valid Java character classes
     * that are not supported by Lucene but we do not check for all of them. See
     * https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html
     * for Java regex syntax. See
     * https://lucene.apache.org/core/6_4_0/core/org/apache/lucene/util/automaton/RegExp.html
     * for Lucene syntax. We use \p as a shortcut for all of the character
     * classes of the form \p{XXX}.
     */
    private static final CharSequence[] UNSUPPORTED_CHARS = {"\\d", "\\D", "\\w", "\\W", "\\s", "\\S", "\\n",
        "\\t", "\\r", "\\f", "\\a", "\\e", "\\v", "\\V", "\\h", "\\H", "\\p"}; //NON-NLS

    private static final int MAX_RESULTS_PER_CURSOR_MARK = 512;
    private static final int MIN_EMAIL_ADDR_LENGTH = 8;
    private static final String SNIPPET_DELIMITER = String.valueOf(Character.toChars(171));

    private final List<KeywordQueryFilter> filters = new ArrayList<>();
    private final KeywordList keywordList;
    private final Keyword originalKeyword; // The regular expression originalKeyword used to perform the search.
    private final String keywordString;
    private final boolean queryStringContainsWildcardPrefix;
    private final boolean queryStringContainsWildcardSuffix;

    private boolean escaped;
    private String escapedQuery;
    private String field = Server.Schema.CONTENT_STR.toString();

    /**
     * Constructor with query to process.
     *
     * @param keywordList
     * @param keyword
     */
    RegexQuery(KeywordList keywordList, Keyword keyword) {
        this.keywordList = keywordList;
        this.originalKeyword = keyword;
        this.keywordString = keyword.getSearchTerm();

        this.queryStringContainsWildcardPrefix = this.keywordString.startsWith(".*");
        this.queryStringContainsWildcardSuffix = this.keywordString.endsWith(".*");
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    @Override
    public boolean validate() {
        if (keywordString.isEmpty()) {
            return false;
        }
        try {
            // First we perform regular Java regex validation to catch errors.
            Pattern.compile(keywordString, Pattern.UNICODE_CHARACTER_CLASS);

            // Then we check for the set of Java predefined and POSIX character
            // classes. While they are valid Lucene regex characters, they will
            // behave differently than users may expect. E.g. the regex \d\d\d 
            // will not find 3 digits but will instead find a sequence of 3 'd's.
            for (CharSequence c : UNSUPPORTED_CHARS) {
                if (keywordString.contains(c)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {

        final Server solrServer = KeywordSearch.getServer();
        SolrQuery solrQuery = new SolrQuery();

        /*
         * The provided regular expression may include wildcards at the
         * beginning and/or end. These wildcards are used to indicate that the
         * user wants to find hits for the regex that are embedded within other
         * characters. For example, if we are given .*127.0.0.1.* as a regular
         * expression, this will produce hits for: (a) " 127.0.0.1 " as a
         * standalone token (surrounded by whitespace). (b) "abc127.0.0.1def"
         * where the IP address is surrounded by other characters.
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
        solrQuery.setFields(Server.Schema.CONTENT_STR.toString(), Server.Schema.ID.toString(), Server.Schema.CHUNK_SIZE.toString());

        filters.stream()
                .map(KeywordQueryFilter::toString)
                .forEach(solrQuery::addFilterQuery);

        solrQuery.setRows(MAX_RESULTS_PER_CURSOR_MARK);
        // Setting the sort order is necessary for cursor based paging to work.
        solrQuery.setSort(SortClause.asc(Server.Schema.ID.toString()));

        String cursorMark = CursorMarkParams.CURSOR_MARK_START;
        SolrDocumentList resultList;
        boolean allResultsProcessed = false;
        QueryResults results = new QueryResults(this);

        while (!allResultsProcessed) {
            try {
                solrQuery.set(CursorMarkParams.CURSOR_MARK_PARAM, cursorMark);
                QueryResponse response = solrServer.query(solrQuery, SolrRequest.METHOD.POST);
                resultList = response.getResults();

                for (SolrDocument resultDoc : resultList) {
                    try {
                        List<KeywordHit> keywordHits = createKeywordHits(resultDoc);
                        for (KeywordHit hit : keywordHits) {
                            Keyword keywordInstance = new Keyword(hit.getHit(), true, true, originalKeyword.getListName(), originalKeyword.getOriginalTerm());
                            List<KeywordHit> hitsForKeyword = results.getResults(keywordInstance);
                            if (hitsForKeyword == null) {
                                hitsForKeyword = new ArrayList<>();
                                results.addResult(keywordInstance, hitsForKeyword);
                            }
                            hitsForKeyword.add(hit);
                        }
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Error creating keyword hits", ex); //NON-NLS
                    }
                }

                String nextCursorMark = response.getNextCursorMark();
                if (cursorMark.equals(nextCursorMark)) {
                    allResultsProcessed = true;
                }
                cursorMark = nextCursorMark;
            } catch (KeywordSearchModuleException ex) {
                LOGGER.log(Level.SEVERE, "Error executing Regex Solr Query: " + keywordString, ex); //NON-NLS
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(Server.class, "Server.query.exception.msg", keywordString), ex.getCause().getMessage());
            }
        }

        return results;
    }

    private List<KeywordHit> createKeywordHits(SolrDocument solrDoc) throws TskCoreException {

        final HashMap<String, String> keywordsFoundInThisDocument = new HashMap<>();

        List<KeywordHit> hits = new ArrayList<>();
        final String docId = solrDoc.getFieldValue(Server.Schema.ID.toString()).toString();
        final Integer chunkSize = (Integer) solrDoc.getFieldValue(Server.Schema.CHUNK_SIZE.toString());

        final Collection<Object> content_str = solrDoc.getFieldValues(Server.Schema.CONTENT_STR.toString());

        final Pattern pattern = Pattern.compile(keywordString);
        try {
            for (Object content_obj : content_str) {
                String content = (String) content_obj;
                Matcher hitMatcher = pattern.matcher(content);
                int offset = 0;

                while (hitMatcher.find(offset)) {

                    // If the location of the hit is beyond this chunk (i.e. it
                    // exists in the overlap region), we skip the hit. It will
                    // show up again as a hit in the chunk following this one.
                    if (chunkSize != null && hitMatcher.start() >= chunkSize) {
                        break;
                    }

                    String hit = hitMatcher.group();

                    offset = hitMatcher.end();
                    final ATTRIBUTE_TYPE artifactAttributeType = originalKeyword.getArtifactAttributeType();

                    // We attempt to reduce false positives for phone numbers and IP address hits
                    // by querying Solr for hits delimited by a set of known boundary characters.
                    // See KeywordSearchList.PHONE_NUMBER_REGEX for an example.
                    // Because of this the hits may contain an extra character at the beginning or end that
                    // needs to be chopped off, unless the user has supplied their own wildcard suffix
                    // as part of the regex.
                    if (!queryStringContainsWildcardSuffix
                            && (artifactAttributeType == ATTRIBUTE_TYPE.TSK_PHONE_NUMBER
                            || artifactAttributeType == ATTRIBUTE_TYPE.TSK_IP_ADDRESS)) {
                        if (artifactAttributeType == ATTRIBUTE_TYPE.TSK_PHONE_NUMBER) {
                            // For phone numbers replace all non numeric characters (except "(") at the start of the hit.
                            hit = hit.replaceAll("^[^0-9\\(]", "");
                        } else {
                            // Replace all non numeric characters at the start of the hit.
                            hit = hit.replaceAll("^[^0-9]", "");
                        }
                        // Replace all non numeric at the end of the hit.
                        hit = hit.replaceAll("[^0-9]$", "");
                    }

                    /**
                     * The use of String interning is an optimization to ensure
                     * that we reuse the same keyword hit String object across
                     * all hits. Even though we benefit from G1GC String
                     * deduplication, the overhead associated with creating a
                     * new String object for every KeywordHit can be significant
                     * when the number of hits gets large.
                     */
                    hit = hit.intern();

                    // We will only create one KeywordHit instance per document for
                    // a given hit.
                    if (keywordsFoundInThisDocument.containsKey(hit)) {
                        continue;
                    }
                    keywordsFoundInThisDocument.put(hit, hit);

                    if (artifactAttributeType == null) {
                        hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                    } else {
                        switch (artifactAttributeType) {
                            case TSK_EMAIL:
                                /*
                                 * Reduce false positives by eliminating email
                                 * address hits that are either too short or are
                                 * not for valid top level domains.
                                 */
                                if (hit.length() >= MIN_EMAIL_ADDR_LENGTH
                                        && DomainValidator.getInstance(true).isValidTld(hit.substring(hit.lastIndexOf('.')))) {
                                    hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                }

                                break;
                            case TSK_CARD_NUMBER:
                                /*
                                 * If searching for credit card account numbers,
                                 * do extra validation on the term and discard
                                 * it if it does not pass.
                                 */
                                Matcher ccnMatcher = CREDIT_CARD_NUM_PATTERN.matcher(hit);

                                for (int rLength = hit.length(); rLength >= 12; rLength--) {
                                    ccnMatcher.region(0, rLength);
                                    if (ccnMatcher.find()) {
                                        final String group = ccnMatcher.group("ccn");
                                        if (CreditCardValidator.isValidCCN(group)) {
                                            hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                        }
                                    }
                                }

                                break;
                            default:
                                hits.add(new KeywordHit(docId, makeSnippet(content, hitMatcher, hit), hit));
                                break;
                        }
                    }
                }

            }
        } catch (Throwable error) {
            /*
             * NOTE: Matcher.find() is known to throw StackOverflowError in rare
             * cases (see JIRA-2700). StackOverflowError is an error, not an
             * exception, and therefore needs to be caught as a Throwable. When
             * this occurs we should re-throw the error as TskCoreException so
             * that it is logged by the calling method and move on to the next
             * Solr document.
             */
            throw new TskCoreException("Failed to create keyword hits for Solr document id " + docId + " due to " + error.getMessage());
        }
        return hits;
    }

    /**
     * Make a snippet from the given content that has the given hit plus some
     * surrounding context.
     *
     * @param content    The content to extract the snippet from.
     *
     * @param hitMatcher The Matcher that has the start/end info for where the
     *                   hit is in the content.
     * @param hit        The actual hit in the content.
     *
     * @return A snippet extracted from content that contains hit plus some
     *         surrounding context.
     */
    private String makeSnippet(String content, Matcher hitMatcher, String hit) {
        // Get the snippet from the document.
        int maxIndex = content.length() - 1;
        final int end = hitMatcher.end();
        final int start = hitMatcher.start();

        return content.substring(Integer.max(0, start - 20), Integer.max(0, start))
                + SNIPPET_DELIMITER + hit + SNIPPET_DELIMITER
                + content.substring(Integer.min(maxIndex, end), Integer.min(maxIndex, end + 20));
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
        return originalKeyword.getSearchTerm();
    }

    @Override
    synchronized public String getEscapedQueryString() {
        if (false == isEscaped()) {
            escape();
        }
        return escapedQuery;
    }

    /**
     * Posts a keyword hit artifact to the blackboard for a given keyword hit.
     *
     * @param content      The text source object for the hit.
     * @param foundKeyword The keyword that was found by the search, this may be
     *                     different than the Keyword that was searched if, for
     *                     example, it was a RegexQuery.
     * @param hit          The keyword hit.
     * @param snippet      A snippet from the text that contains the hit.
     * @param listName     The name of the keyword list that contained the
     *                     keyword for which the hit was found.
     *
     *
     * @return The newly created artifact or null if there was a problem
     *         creating it.
     */
    @Override
    public BlackboardArtifact postKeywordHitToBlackboard(Content content, Keyword foundKeyword, KeywordHit hit, String snippet, String listName) {
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        if (content == null) {
            LOGGER.log(Level.WARNING, "Error adding artifact for keyword hit to blackboard"); //NON-NLS
            return null;
        }

        /*
         * Credit Card number hits are handled differently
         */
        if (originalKeyword.getArtifactAttributeType() == ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            createCCNAccount(content, foundKeyword, hit, snippet, listName);
            return null;
        }
        
        /*
         * Create a "plain vanilla" keyword hit artifact with keyword and
         * regex attributes
         */
        BlackboardArtifact newArtifact;
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, foundKeyword.getSearchTerm()));
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, getQueryString()));
        
        try {
            newArtifact = content.newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Error adding artifact for keyword hit to blackboard", ex); //NON-NLS
            return null;
        }
        
        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }

        hit.getArtifactID().ifPresent(artifactID
                -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactID))
        );

        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.REGEX.ordinal()));

        try {
            newArtifact.addAttributes(attributes);
            return newArtifact;
        } catch (TskCoreException e) {
            LOGGER.log(Level.SEVERE, "Error adding bb attributes for terms search artifact", e); //NON-NLS
            return null;
        }
    }

    private void createCCNAccount(Content content, Keyword foundKeyword, KeywordHit hit, String snippet, String listName) {
        
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        if (originalKeyword.getArtifactAttributeType() != ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            LOGGER.log(Level.SEVERE, "Keyword hit is not a credit card number"); //NON-NLS
            return;
        }
        /*
         * Create a credit card account  with attributes
         * parsed from the snippet for the hit and looked up based on the
         * parsed bank identifcation number.
         */
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        
        Map<BlackboardAttribute.Type, BlackboardAttribute> parsedTrackAttributeMap = new HashMap<>();
        Matcher matcher = TermsComponentQuery.CREDIT_CARD_TRACK1_PATTERN.matcher(hit.getSnippet());
        if (matcher.find()) {
            parseTrack1Data(parsedTrackAttributeMap, matcher);
        }
        matcher = CREDIT_CARD_TRACK2_PATTERN.matcher(hit.getSnippet());
        if (matcher.find()) {
            parseTrack2Data(parsedTrackAttributeMap, matcher);
        }
        final BlackboardAttribute ccnAttribute = parsedTrackAttributeMap.get(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CARD_NUMBER));
        if (ccnAttribute == null || StringUtils.isBlank(ccnAttribute.getValueString())) {
           
            if (hit.isArtifactHit()) {
                LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for artifact keyword hit: term = %s, snippet = '%s', artifact id = %d", foundKeyword.getSearchTerm(), hit.getSnippet(), hit.getArtifactID().get())); //NON-NLS
            } else {
                try {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s', object id = %d", foundKeyword.getSearchTerm(), hit.getSnippet(), hit.getContentID())); //NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s' ", foundKeyword.getSearchTerm(), hit.getSnippet())); //NON-NLS
                    LOGGER.log(Level.SEVERE, "There was a error getting contentID for keyword hit.", ex); //NON-NLS
                }
            }
            return;
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
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_CARD_SCHEME, MODULE_NAME, scheme)));
            binInfo.getCardType().ifPresent(cardType
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_CARD_TYPE, MODULE_NAME, cardType)));
            binInfo.getBrand().ifPresent(brand
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_BRAND_NAME, MODULE_NAME, brand)));
            binInfo.getBankName().ifPresent(bankName
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_BANK_NAME, MODULE_NAME, bankName)));
            binInfo.getBankPhoneNumber().ifPresent(phoneNumber
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER, MODULE_NAME, phoneNumber)));
            binInfo.getBankURL().ifPresent(url
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_URL, MODULE_NAME, url)));
            binInfo.getCountry().ifPresent(country
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_COUNTRY, MODULE_NAME, country)));
            binInfo.getBankCity().ifPresent(city
                    -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_CITY, MODULE_NAME, city)));
        }

        /*
         * If the hit is from unused or unallocated space, record the Solr
         * document id to support showing just the chunk that contained the
         * hit.
         */
        if (content instanceof AbstractFile) {
            AbstractFile file = (AbstractFile) content;
            if (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS
                    || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
                attributes.add(new BlackboardAttribute(KEYWORD_SEARCH_DOCUMENT_ID, MODULE_NAME, hit.getSolrDocumentId()));
            }
        }

        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }

        hit.getArtifactID().ifPresent(artifactID
                -> attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, artifactID))
        );
        
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_TYPE, MODULE_NAME, KeywordSearch.QueryType.REGEX.ordinal()));
        
        
        /*
         * Create an account instance.
         */
        try {
            AccountFileInstance ccAccountInstance = Case.getOpenCase().getSleuthkitCase().getCommunicationsManager().createAccountFileInstance(Account.Type.CREDIT_CARD, ccnAttribute.getValueString() , MODULE_NAME, content);
            
            ccAccountInstance.addAttributes(attributes);

        } catch (TskCoreException | NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Error creating CCN account instance", ex); //NON-NLS
            
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
        addAttributeIfNotAlreadyCaptured(attributesMap, ATTRIBUTE_TYPE.TSK_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, ATTRIBUTE_TYPE.TSK_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, ATTRIBUTE_TYPE.TSK_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, ATTRIBUTE_TYPE.TSK_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(attributesMap, ATTRIBUTE_TYPE.TSK_CARD_LRC, "LRC", matcher);
    }

    /**
     * Parses the track 1 data from the snippet for a credit card account number
     * hit and turns them into artifact attributes. The track 1 data has the
     * same fields as the track two data, plus the account holder's name.
     *
     * @param attributeMap A map of artifact attribute objects, used to avoid
     *                     creating duplicate attributes.
     * @param matcher      A matcher for the snippet.
     */
    static private void parseTrack1Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Matcher matcher) {
        parseTrack2Data(attributeMap, matcher);
        addAttributeIfNotAlreadyCaptured(attributeMap, ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
    }

    /**
     * Creates an attribute of the the given type to the given artifact with a
     * value parsed from the snippet for a credit account number hit.
     *
     * @param attributeMap A map of artifact attribute objects, used to avoid
     *                     creating duplicate attributes.
     * @param attrType     The type of attribute to create.
     * @param groupName    The group name of the regular expression that was
     *                     used to parse the attribute data.
     * @param matcher      A matcher for the snippet.
     *
     */
    static private void addAttributeIfNotAlreadyCaptured(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);
        attributeMap.computeIfAbsent(type, t -> {
            String value = matcher.group(groupName);
            if (attrType.equals(ATTRIBUTE_TYPE.TSK_CARD_NUMBER)) {
                attributeMap.put(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_KEYWORD),
                        new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, value));
                value = CharMatcher.anyOf(" -").removeFrom(value);
            }
            if (StringUtils.isNotBlank(value)) {
                return new BlackboardAttribute(attrType, MODULE_NAME, value);
            } else {
                return null;
            }
        });
    }
}
