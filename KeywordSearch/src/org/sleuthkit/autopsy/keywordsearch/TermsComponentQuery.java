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
//
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
 * Performs a regular expression query, making use of the Solr terms component.
 * The terms component is described in the Apache Solr Reference Guide as a
 * component that "provides access to the indexed terms in a field and the
 * number of documents that match each term."
 */
final class TermsComponentQuery implements KeywordSearchQuery {

    private static final Logger LOGGER = Logger.getLogger(TermsComponentQuery.class.getName());
    private static final boolean DEBUG = Version.Type.DEVELOPMENT.equals(Version.getBuildType());

    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    private static final BlackboardAttribute.Type KEYWORD_SEARCH_DOCUMENT_ID = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_KEYWORD_SEARCH_DOCUMENT_ID);

    //TODO: move these regex and the luhn check to a new class, something like: CreditCardNumberValidator
    /*
     * Track 2 is numeric plus six punctuation symbolls :;<=>?
     *
     * This regex matches 12-19 digit ccns embeded in a track 2 formated string.
     * This regex matches (and extracts groups) even if the entire track is not
     * present as long as the part that is conforms to the track format.
     *
     */
    private static final Pattern TRACK2_PATTERN = Pattern.compile(
            "[:;<=>?]?" //(optional)start sentinel //NON-NLS
            + "(?<accountNumber>[3456]([ -]?\\d){11,18})" //12-19 digits, with possible single spaces or dashes in between. first digit is 3,4,5, or 6 //NON-NLS
            + "(?:[:;<=>?]" //separator //NON-NLS
            + "(?:(?<expiration>\\d{4})" //4 digit expiration date YYMM //NON-NLS
            + "(?:(?<serviceCode>\\d{3})" //3 digit service code //NON-NLS
            + "(?:(?<discretionary>[^:;<=>?]*)" //discretionary data, not containing punctuation marks //NON-NLS
            + "(?:[:;<=>?]" //end sentinel //NON-NLS
            + "(?<LRC>.)" //longitudinal redundancy check //NON-NLS
            + "?)?)?)?)?)?"); //close nested optional groups //NON-NLS

    /*
     * Track 1 is alphanumeric.
     *
     * This regex matches 12-19 digit ccns embeded in a track 1 formated string.
     * This regex matches (and extracts groups) even if the entire track is not
     * present as long as the part that is conforms to the track format.
     */
    private static final Pattern TRACK1_PATTERN = Pattern.compile(
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
    private static final Pattern CCN_PATTERN = Pattern.compile("(?<ccn>[3456]([ -]?\\d){11,18})");   //12-19 digits, with possible single spaces or dashes in between. first digit is 3,4,5, or 6 //NON-NLS
    private static final LuhnCheckDigit LUHN_CHECK = new LuhnCheckDigit();

    //corresponds to field in Solr schema, analyzed with white-space tokenizer only
    private static final String TERMS_SEARCH_FIELD = Server.Schema.CONTENT_WS.toString();
    private static final String TERMS_HANDLER = "/terms"; //NON-NLS
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static final String CASE_INSENSITIVE = "case_insensitive"; //NON-NLS
    private static final int MAX_TERMS_RESULTS = 20000;

    private String escapedQuery;
    private final KeywordList keywordList;
    private final Keyword keyword;
    private boolean isEscaped;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();

    TermsComponentQuery(KeywordList keywordList, Keyword keyword) {
        this.keyword = keyword;

        this.keywordList = keywordList;
        this.escapedQuery = keyword.getSearchTerm();
    }

    @Override
    public void addFilter(KeywordQueryFilter filter) {
        this.filters.add(filter);
    }

    /**
     * @param field
     *
     * @deprecated This method is unused and no-op
     */
    @Override
    @Deprecated
    public void setField(String field) {
    }

    @Override
    public void setSubstringQuery() {
        escapedQuery = ".*" + escapedQuery + ".*";
    }

    @Override
    public void escape() {
        escapedQuery = Pattern.quote(keyword.getSearchTerm());
        isEscaped = true;
    }

    @Override
    public boolean validate() {
        if (escapedQuery.isEmpty()) {
            return false;
        }

        try {
            Pattern.compile(escapedQuery);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public boolean isEscaped() {
        return isEscaped;
    }

    @Override
    public boolean isLiteral() {
        return false;
    }

    @Override
    public String getEscapedQueryString() {
        return this.escapedQuery;
    }

    @Override
    public String getQueryString() {
        return keyword.getSearchTerm();
    }

    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName) {
        BlackboardArtifact newArtifact;

        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        if (keyword.getArtifactAttributeType() == ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ACCOUNT_TYPE, MODULE_NAME, Account.Type.CREDIT_CARD.name()));

            Map<BlackboardAttribute.Type, BlackboardAttribute> parsedTrackAttributeMap = new HashMap<>();

            //try to match it against the track 1 regex
            Matcher matcher = TRACK1_PATTERN.matcher(hit.getSnippet());
            if (matcher.find()) {
                parseTrack1Data(parsedTrackAttributeMap, matcher);
            }

            //then try to match it against the track 2 regex
            matcher = TRACK2_PATTERN.matcher(hit.getSnippet());
            if (matcher.find()) {
                parseTrack2Data(parsedTrackAttributeMap, matcher);
            }

            //if we couldn't parse the CCN abort this artifact
            final BlackboardAttribute ccnAttribute = parsedTrackAttributeMap.get(new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CARD_NUMBER));
            if (ccnAttribute == null || StringUtils.isBlank(ccnAttribute.getValueString())) {
                if (hit.isArtifactHit()) {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for artifact keyword hit: term = %s, snippet = '%s', artifact id = %d", termHit, hit.getSnippet(), hit.getArtifact().getArtifactID()));
                } else {
                    LOGGER.log(Level.SEVERE, String.format("Failed to parse credit card account number for content keyword hit: term = %s, snippet = '%s', object id = %d", termHit, hit.getSnippet(), hit.getContent().getId()));                    
                }
                return null;
            }

            attributes.addAll(parsedTrackAttributeMap.values());

            //look up the bank name, schem, etc from the BIN
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
             * if the hit is from unused or unalocated blocks, record the
             * KEYWORD_SEARCH_DOCUMENT_ID, so we can show just that chunk in the
             * UI
             */
            if (hit.getContent() instanceof AbstractFile) {
                AbstractFile file = (AbstractFile) hit.getContent();
                if (file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS
                        || file.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) {
                    attributes.add(new BlackboardAttribute(KEYWORD_SEARCH_DOCUMENT_ID, MODULE_NAME, hit.getSolrDocumentId()));
                }
            }

            // make account artifact
            try {
                newArtifact = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_ACCOUNT);
            } catch (TskCoreException tskCoreException) {
                LOGGER.log(Level.SEVERE, "Error adding bb artifact for account", tskCoreException); //NON-NLS
                return null;
            }
        } else {

            //regex match
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, termHit));
            //regex keyword
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getSearchTerm()));

            //make keyword hit artifact
            try {
                newArtifact = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            } catch (TskCoreException tskCoreException) {
                LOGGER.log(Level.SEVERE, "Error adding bb artifact for keyword hit", tskCoreException); //NON-NLS
                return null;
            }
        }
        if (StringUtils.isNotBlank(listName)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }
        //preview
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }

        if (hit.isArtifactHit()) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
        }

        try {
            //TODO: do we still/really need this KeywordCachedArtifact class? 
            newArtifact.addAttributes(attributes);
            KeywordCachedArtifact writeResult = new KeywordCachedArtifact(newArtifact);
            writeResult.add(attributes);
            return writeResult;
        } catch (TskCoreException e) {
            LOGGER.log(Level.SEVERE, "Error adding bb attributes for terms search artifact", e); //NON-NLS
            return null;
        }
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {
        /*
         * Execute the regex query to get a list of terms that match the regex.
         * Note that the field that is being searched is tokenized based on
         * whitespace.
         */
        //create the query
        final SolrQuery q = new SolrQuery();
        q.setRequestHandler(TERMS_HANDLER);
        q.setTerms(true);
        q.setTermsRegexFlag(CASE_INSENSITIVE);
        q.setTermsRegex(escapedQuery);
        q.addTermsField(TERMS_SEARCH_FIELD);
        q.setTimeAllowed(TERMS_TIMEOUT);
        q.setShowDebugInfo(DEBUG);
        q.setTermsLimit(MAX_TERMS_RESULTS);
        LOGGER.log(Level.INFO, "Query: {0}", q.toString()); //NON-NLS

        //execute the query
        List<Term> terms = null;
        try {
            terms = KeywordSearch.getServer().queryTerms(q).getTerms(TERMS_SEARCH_FIELD);
        } catch (KeywordSearchModuleException ex) {
            LOGGER.log(Level.SEVERE, "Error executing the regex terms query: " + keyword.getSearchTerm(), ex); //NON-NLS
            //TODO: this is almost certainly wrong and guaranteed to throw a NPE at some point!!!!
        }

        /*
         * For each term that matched the regex, query for full set of document
         * hits for that term.
         */
        QueryResults results = new QueryResults(this, keywordList);
        int resultSize = 0;

        for (Term term : terms) {
            final String termStr = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());

            if (keyword.getArtifactAttributeType() == ATTRIBUTE_TYPE.TSK_CARD_NUMBER) {
                //If the keyword is a credit card number, pass it through luhn validator
                Matcher matcher = CCN_PATTERN.matcher(term.getTerm());
                matcher.find();
                final String ccn = CharMatcher.anyOf(" -").removeFrom(matcher.group("ccn"));
                if (false == LUHN_CHECK.isValid(ccn)) {
                    continue; //if the hit does not pass the luhn check, skip it.
                }
            }

            /*
             * Note: we can't set filter query on terms query but setting filter
             * query on fileResults query will yield the same result
             */
            LuceneQuery filesQuery = new LuceneQuery(keywordList, new Keyword(termStr, true));
            filters.forEach(filesQuery::addFilter);

            try {
                QueryResults fileQueryResults = filesQuery.performQuery();
                Set<KeywordHit> filesResults = new HashSet<>();
                for (Keyword key : fileQueryResults.getKeywords()) {                //flatten results into a single list
                    List<KeywordHit> keyRes = fileQueryResults.getResults(key);
                    resultSize += keyRes.size();
                    filesResults.addAll(keyRes);
                }
                results.addResult(new Keyword(term.getTerm(), false), new ArrayList<>(filesResults));
            } catch (NoOpenCoreException | RuntimeException e) {
                LOGGER.log(Level.WARNING, "Error executing Solr query,", e); //NON-NLS
                throw e;
            }
        }

        //TODO limit how many results we store, not to hit memory limits
        LOGGER.log(Level.INFO, "Regex # results: {0}", resultSize); //NON-NLS

        return results;
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }

    /**
     * Add an attribute of the the given type to the given artifact with the
     * value taken from the matcher. If an attribute of the given type already
     * exists on the artifact or if the value is null, no attribute is added.
     *
     * @param attributeMap
     * @param attrType
     * @param groupName
     * @param matcher *
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

    /**
     * Parse the track 2 data from a KeywordHit and add it to the given
     * artifact.
     *
     * @param attributeMAp
     * @param matcher
     */
    static private void parseTrack2Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMAp, Matcher matcher) {
        //try to add all the attrributes common to track 1 and 2
        addAttributeIfNotAlreadyCaptured(attributeMAp, ATTRIBUTE_TYPE.TSK_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(attributeMAp, ATTRIBUTE_TYPE.TSK_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(attributeMAp, ATTRIBUTE_TYPE.TSK_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(attributeMAp, ATTRIBUTE_TYPE.TSK_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(attributeMAp, ATTRIBUTE_TYPE.TSK_CARD_LRC, "LRC", matcher);

    }

    /**
     * Parse the track 1 data from a KeywordHit and add it to the given
     * artifact.
     *
     * @param attributeMap
     * @param matcher
     */
    static private void parseTrack1Data(Map<BlackboardAttribute.Type, BlackboardAttribute> attributeMap, Matcher matcher) {
        // track 1 has all the fields present in track 2
        parseTrack2Data(attributeMap, matcher);
        //plus it also has the account holders name
        addAttributeIfNotAlreadyCaptured(attributeMap, ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
    }
}
