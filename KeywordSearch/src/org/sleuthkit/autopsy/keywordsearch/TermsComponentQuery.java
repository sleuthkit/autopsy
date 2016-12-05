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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.LuhnCheckDigit;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.datamodel.CreditCards;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Implements a regex query that will be performed as a two step operation. In
 * the first step, the Solr terms component is used to find any terms in the
 * index that match the regex. In the second step, term queries are executed for
 * each matched term to produce the set of keyword hits for the regex.
 */
final class TermsComponentQuery implements KeywordSearchQuery {

    private static final Logger LOGGER = Logger.getLogger(TermsComponentQuery.class.getName());
    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    private static final String SEARCH_HANDLER = "/terms"; //NON-NLS
    private static final String SEARCH_FIELD = Server.Schema.CONTENT_WS.toString();
    private static final int TERMS_SEARCH_TIMEOUT = 90 * 1000; // Milliseconds
    private static final String CASE_INSENSITIVE = "case_insensitive"; //NON-NLS
    private static final boolean DEBUG_FLAG = Version.Type.DEVELOPMENT.equals(Version.getBuildType());
    private static final int MAX_TERMS_QUERY_RESULTS = 20000;
    private final KeywordList keywordList;
    private final Keyword keyword;
    private String searchTerm;
    private boolean searchTermIsEscaped;
    private final List<KeywordQueryFilter> filters = new ArrayList<>(); // THIS APPEARS TO BE UNUSED

    /*
     * The following fields are part of the initial implementation of credit
     * card account search and should be factored into another class when time
     * permits.
     */
    private static final Pattern CREDIT_CARD_NUM_PATTERN = Pattern.compile("(?<ccn>[3456]([ -]?\\d){11,18})");   //12-19 digits, with possible single spaces or dashes in between. First digit is 3,4,5, or 6 //NON-NLS
    private static final LuhnCheckDigit CREDIT_CARD_NUM_LUHN_CHECK = new LuhnCheckDigit();
    private static final Pattern CREDIT_CARD_TRACK1_PATTERN = Pattern.compile(
            /*
             * Track 1 is alphanumeric.
             *
             * This regex matches 12-19 digit ccns embeded in a track 1 formated
             * string. This regex matches (and extracts groups) even if the
             * entire track is not present as long as the part that is conforms
             * to the track format.
             */
            "(?:" //begin nested optinal group //NON-NLS
            + "%?" //optional start sentinal: % //NON-NLS
            + "B)?" //format code  //NON-NLS
            + "(?<accountNumber>[3456]([ -]?\\d){11,18})" //12-19 digits, with possible single spaces or dashes in between. first digit is 3,4,5, or 6 //NON-NLS
            + "\\^" //separator //NON-NLS
            + "(?<name>[^^]{2,26})" //2-26 charachter name, not containing ^ //NON-NLS
            + "(?:\\^" //separator //NON-NLS
            + "(?:(?:\\^|(?<expiration>\\d{4}))" //separator or 4 digit expiration YYMM //NON-NLS
            + "(?:(?:\\^|(?<serviceCode>\\d{3}))"//separator or 3 digit service code //NON-NLS
            + "(?:(?<discretionary>[^?]*)" // discretionary data not containing separator //NON-NLS
            + "(?:\\?" // end sentinal: ? //NON-NLS
            + "(?<LRC>.)" //longitudinal redundancy check //NON-NLS
            + "?)?)?)?)?)?");//close nested optional groups //NON-NLS
    private static final Pattern CREDIT_CARD_TRACK2_PATTERN = Pattern.compile(
            /*
             * Track 2 is numeric plus six punctuation symbolls :;<=>?
             *
             * This regex matches 12-19 digit ccns embeded in a track 2 formated
             * string. This regex matches (and extracts groups) even if the
             * entire track is not present as long as the part that is conforms
             * to the track format.
             *
             */
            "[:;<=>?]?" //(optional)start sentinel //NON-NLS
            + "(?<accountNumber>[3456]([ -]?\\d){11,18})" //12-19 digits, with possible single spaces or dashes in between. first digit is 3,4,5, or 6 //NON-NLS
            + "(?:[:;<=>?]" //separator //NON-NLS
            + "(?:(?<expiration>\\d{4})" //4 digit expiration date YYMM //NON-NLS
            + "(?:(?<serviceCode>\\d{3})" //3 digit service code //NON-NLS
            + "(?:(?<discretionary>[^:;<=>?]*)" //discretionary data, not containing punctuation marks //NON-NLS
            + "(?:[:;<=>?]" //end sentinel //NON-NLS
            + "(?<LRC>.)" //longitudinal redundancy check //NON-NLS
            + "?)?)?)?)?)?"); //close nested optional groups //NON-NLS
    private static final BlackboardAttribute.Type KEYWORD_SEARCH_DOCUMENT_ID = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID);

    /**
     * Constructs an object that implements a regex query that will be performed
     * as a two step operation. In the first step, the Solr terms component is
     * used to find any terms in the index that match the regex. In the second
     * step, term queries are executed for each matched term to produce the set
     * of keyword hits for the regex.
     *
     * @param keywordList A keyword list that contains the keyword that provides
     *                    the regex search term for the query.
     * @param keyword     The keyword that provides the regex search term for
     *                    the query.
     */
    // TODO: Why is both the list and the keyword added to the state of this
    // object?
    // TODO: Why is the search term not escaped and given substring wildcards,
    // if needed, here in the constructor?
    TermsComponentQuery(KeywordList keywordList, Keyword keyword) {
        this.keywordList = keywordList;
        this.keyword = keyword;
        this.searchTerm = keyword.getSearchTerm();
    }

    /**
     * Gets the keyword list that contains the keyword that provides the regex
     * search term for the query.
     *
     * @return The keyword list.
     */
    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    /**
     * Gets the original search term for the query, without any escaping or, if
     * it is a literal term, the addition of wildcards for a substring search.
     *
     * @return The original search term.
     */
    @Override
    public String getQueryString() {
        return keyword.getSearchTerm();
    }

    /**
     * Indicates whether or not the search term for the query is a literal term
     * that needs have wildcards added to it to make the query a substring
     * search.
     *
     * @return True or false.
     */
    @Override
    public boolean isLiteral() {
        return false;
    }

    /**
     * Adds wild cards to the search term for the query, which makes the query a
     * substring search, if it is a literal search term.
     */
    @Override
    public void setSubstringQuery() {
        searchTerm = ".*" + searchTerm + ".*";
    }

    /**
     * Escapes the search term for the query.
     */
    @Override
    public void escape() {
        searchTerm = Pattern.quote(keyword.getSearchTerm());
        searchTermIsEscaped = true;
    }

    /**
     * Indicates whether or not the search term has been escaped yet.
     *
     * @return True or false.
     */
    @Override
    public boolean isEscaped() {
        return searchTermIsEscaped;
    }

    /**
     * Gets the escaped search term for the query, assuming it has been escaped
     * by a call to TermsComponentQuery.escape.
     *
     * @return The search term, possibly escaped.
     */
    @Override
    public String getEscapedQueryString() {
        return this.searchTerm;
    }

    /**
     * Indicates whether or not the search term is a valid regex.
     *
     * @return True or false.
     */
    @Override
    public boolean validate() {
        if (searchTerm.isEmpty()) {
            return false;
        }
        try {
            Pattern.compile(searchTerm);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Does nothing, not applicable to a regex query, which always searches a
     * field created specifically for regex sesarches.
     *
     * @param field The name of a Solr document field to search.
     */
    @Override
    public void setField(String field) {
    }

    /**
     * Adds a filter to the query.
     *
     * @param filter The filter.
     */
    // TODO: Document this better.
    @Override
    public void addFilter(KeywordQueryFilter filter) {
        this.filters.add(filter);
    }

    /**
     * Executes the regex query as a two step operation. In the first step, the
     * Solr terms component is used to find any terms in the index that match
     * the regex. In the second step, term queries are executed for each matched
     * term to produce the set of keyword hits for the regex.
     *
     * @return A QueryResult object or null.
     *
     * @throws NoOpenCoreException
     */
    @Override
    public QueryResults performQuery() throws KeywordSearchModuleException, NoOpenCoreException {
        /*
         * Do a query using the Solr terms component to find any terms in the
         * index that match the regex.
         */
        final SolrQuery termsQuery = new SolrQuery();
        termsQuery.setRequestHandler(SEARCH_HANDLER);
        termsQuery.setTerms(true);
        termsQuery.setTermsRegexFlag(CASE_INSENSITIVE);
        termsQuery.setTermsRegex(searchTerm);
        termsQuery.addTermsField(SEARCH_FIELD);
        termsQuery.setTimeAllowed(TERMS_SEARCH_TIMEOUT);
        termsQuery.setShowDebugInfo(DEBUG_FLAG);
        termsQuery.setTermsLimit(MAX_TERMS_QUERY_RESULTS);
        List<Term> terms = KeywordSearch.getServer().queryTerms(termsQuery).getTerms(SEARCH_FIELD);
        /*
         * Do a term query for each term that matched the regex.
         */
        QueryResults results = new QueryResults(this, keywordList);
        for (Term term : terms) {
            /*
             * If searching for credit card account numbers, do a Luhn check on
             * the term and discard it if it does not pass.
             */
            if (keyword.getArtifactAttributeType() == ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
                Matcher matcher = CREDIT_CARD_NUM_PATTERN.matcher(term.getTerm());
                matcher.find();
                final String ccn = CharMatcher.anyOf(" -").removeFrom(matcher.group("ccn"));
                if (false == CREDIT_CARD_NUM_LUHN_CHECK.isValid(ccn)) {
                    continue;
                }
            }

            /*
             * Do an ordinary query with the escaped term and convert the query
             * results into a single list of keyword hits without duplicates.
             *
             * Note that the filters field appears to be unused. There is an old
             * comment here, what does it mean? "Note: we can't set filter query
             * on terms query but setting filter query on fileResults query will
             * yield the same result." The filter is NOT being added to the term
             * query.
             */
            String escapedTerm = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());
            LuceneQuery termQuery = new LuceneQuery(keywordList, new Keyword(escapedTerm, true));
            filters.forEach(termQuery::addFilter); // This appears to be unused
            QueryResults termQueryResult = termQuery.performQuery();
            Set<KeywordHit> termHits = new HashSet<>();
            for (Keyword word : termQueryResult.getKeywords()) {
                termHits.addAll(termQueryResult.getResults(word));
            }
            results.addResult(new Keyword(term.getTerm(), false), new ArrayList<>(termHits));
        }
        return results;
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
        if (keyword.getArtifactAttributeType() != ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, searchTerm));
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getSearchTerm()));
            try {
                newArtifact = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error adding artifact for keyword hit to blackboard", ex); //NON-NLS
                return null;
            }
        } else {
            /*
             * Parse the credit card account attributes from the snippet for the
             * hit.
             */
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE, MODULE_NAME, Account.Type.CREDIT_CARD.name()));
            Map<BlackboardAttribute.Type, BlackboardAttribute> parsedTrackAttributeMap = new HashMap<>();
            Matcher matcher = CREDIT_CARD_TRACK1_PATTERN.matcher(hit.getSnippet());
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
                newArtifact = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_ACCOUNT);
            } catch (TskCoreException ex) {
                LOGGER.log(Level.SEVERE, "Error adding artifact for account to blackboard", ex); //NON-NLS
                return null;
            }
        }

        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }
        if (hit.isArtifactHit()) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
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
     * @param attributesMap A map of artifact attribute objects, used to avoid
     *                      creating duplicate attributes.
     * @param matcher       A matcher for the snippet.
     */
    static private void parseTrack1Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Matcher matcher) {
        parseTrack2Data(attributeMap, matcher);
        addAttributeIfNotAlreadyCaptured(attributeMap, ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
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
    static private void addAttributeIfNotAlreadyCaptured(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);
        attributeMap.computeIfAbsent(type, (BlackboardAttribute.Type t) -> {
            String value = matcher.group(groupName);
            if (attrType.equals(ATTRIBUTE_TYPE.TSK_CARD_NUMBER)) {
                value = CharMatcher.anyOf(" -").removeFrom(value);
            }
            if (StringUtils.isNotBlank(value)) {
                return new BlackboardAttribute(attrType, MODULE_NAME, value);
            }
            return null;
        });
    }

}
