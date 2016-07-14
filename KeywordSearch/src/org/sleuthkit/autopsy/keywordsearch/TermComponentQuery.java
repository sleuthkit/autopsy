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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Performs a regular expression query to the SOLR/Lucene instance.
 */
final class TermComponentQuery implements KeywordSearchQuery {

    private static final Logger LOGGER = Logger.getLogger(TermComponentQuery.class.getName());
    private static final boolean DEBUG = Version.Type.DEVELOPMENT.equals(Version.getBuildType());

    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    private static final BlackboardAttribute.Type CHUNK_ID_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CHUNK_ID);

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
            + "(?<accountNumber>\\d{12,19})" //12-19 digit ccn //NON-NLS
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
            + "(?<accountNumber>\\d{12,19})" //12-19 digit ccn //NON-NLS
            + "\\^" //separator //NON-NLS
            + "(?<name>[^^]{2,26})" //2-26 charachter name, not containing ^ //NON-NLS
            + "(?:\\^" //separator //NON-NLS
            + "(?:(?:\\^|(?<expiration>\\d{4}))" //separator or 4 digit expiration YYMM //NON-NLS
            + "(?:(?:\\^|(?<serviceCode>\\d{3}))"//separator or 3 digit service code //NON-NLS
            + "(?:(?<discretionary>[^?]*)" // discretionary data not containing separator //NON-NLS
            + "(?:\\?" // end sentinal: ? //NON-NLS
            + "(?<LRC>.)" //longitudinal redundancy check //NON-NLS
            + "?)?)?)?)?)?");//close nested optional groups //NON-NLS
    private static final Pattern CCN_PATTERN = Pattern.compile("(?<ccn>\\d{12,19})");
    private static final LuhnCheckDigit LUHN_CHECK = new LuhnCheckDigit();

    //corresponds to field in Solr schema, analyzed with white-space tokenizer only
    private static final String TERMS_SEARCH_FIELD = Server.Schema.CONTENT_WS.toString();
    private static final String TERMS_HANDLER = "/terms"; //NON-NLS
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static final String CASE_INSENSITIVE = "case_insensitive"; //NON-NLS
    private static final int MAX_TERMS_RESULTS = 20000;

    private String escapedQuery;
    private boolean isEscaped = false;
    private final KeywordList keywordList;
    private final Keyword keyword;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();

    TermComponentQuery(KeywordList keywordList, Keyword keyword) {
        this.keyword = keyword;
        this.keywordList = keywordList;
        this.escapedQuery = keyword.getQuery();
        isEscaped = false;
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
        escapedQuery = Pattern.quote(keyword.getQuery());
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
        return keyword.getQuery();
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
     * @param artifact
     * @param attrType
     * @param groupName
     * @param matcher
     *
     * @throws IllegalArgumentException
     * @throws TskCoreException
     */
    static private void addAttributeIfNotAlreadyCaptured(BlackboardArtifact artifact, ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) throws IllegalArgumentException, TskCoreException {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);
        if (artifact.getAttribute(type) == null) {
            String value = matcher.group(groupName);
            if (StringUtils.isNotBlank(value)) {
                artifact.addAttribute(new BlackboardAttribute(type, MODULE_NAME, value));
            }
        }
    }

    /**
     * Parse the track 1/2 data from a KeywordHit and add it to the given
     * artifact.
     *
     * @param artifact
     * @param matcher
     * @param hit
     * @param tryName  True if this mehtod should try to parse the name
     *                 attribute.
     *
     * @throws IllegalArgumentException
     * @throws TskCoreException
     */
    static private void parseTrackData(BlackboardArtifact artifact, Matcher matcher, KeywordHit hit, boolean tryName) throws IllegalArgumentException, TskCoreException {
        //try to add all the attrributes common to track 1 and 2
        addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_LRC, "LRC", matcher);

        if (tryName) {
            //only try to add the name for track 1
            addAttributeIfNotAlreadyCaptured(artifact, ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
        }
        if (artifact.getAttribute(CHUNK_ID_TYPE) == null) {
            artifact.addAttribute(new BlackboardAttribute(CHUNK_ID_TYPE, MODULE_NAME, hit.getChunkId()));
        }
    }

    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName) {
        try {
            BlackboardArtifact bba;

            Collection<BlackboardAttribute> attributes = new ArrayList<>();

            //if the keyword hit matched the  credit card number keyword/regex...
            if (keyword.getType() == ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER) {
                bba = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_ACCOUNT);
                //TODO: make account artifact
                //try to match it against the track 1 regex
                Matcher matcher = TRACK1_PATTERN.matcher(hit.getSnippet());
                if (matcher.find()) {
                    parseTrackData(bba, matcher, hit, true);
                }
                //then try to match it against the track 2 regex
                matcher = TRACK2_PATTERN.matcher(hit.getSnippet());
                if (matcher.find()) {
                    parseTrackData(bba, matcher, hit, false);
                }
            } else {
                //TODO: keyword hit artifact
                bba = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
                //TODO: move most of the following into the if branch for non-account keyword hits
                //regex match
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, termHit));

                if (StringUtils.isNotEmpty(listName)) {
                    attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
                }

                //regex keyword
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getQuery()));
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
                bba.addAttributes(attributes);
                KeywordCachedArtifact writeResult = new KeywordCachedArtifact(bba);
                writeResult.add(attributes);
                return writeResult;
            } catch (TskCoreException e) {
                LOGGER.log(Level.WARNING, "Error adding bb attributes for terms search artifact", e); //NON-NLS
            }
        } catch (TskCoreException e) {
            LOGGER.log(Level.WARNING, "Error adding bb artifact for keyword hit", e); //NON-NLS
        }

        return null;
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {

        /*
         * Execute the regex query to get a list of terms that match the regex.
         * Note that the field that is being searched is tokenized based on
         * whitespace.
         */
        final SolrQuery termsQuery = new SolrQuery();
        termsQuery.setShowDebugInfo(DEBUG);
        termsQuery.setRequestHandler(TERMS_HANDLER);
        termsQuery.setTerms(true);
        termsQuery.setTermsRegexFlag(CASE_INSENSITIVE);
        termsQuery.setTermsRegex(escapedQuery);
        termsQuery.addTermsField(TERMS_SEARCH_FIELD);
        termsQuery.setTimeAllowed(TERMS_TIMEOUT);
        termsQuery.setTermsLimit(MAX_TERMS_RESULTS);
        LOGGER.log(Level.INFO, "Query: {0}", termsQuery.toString()); //NON-NLS

        List<Term> terms;
        try {
            terms = KeywordSearch.getServer().queryTerms(termsQuery).getTerms(TERMS_SEARCH_FIELD);
        } catch (KeywordSearchModuleException ex) {
            LOGGER.log(Level.WARNING, "Error executing the regex terms query: " + keyword.getQuery(), ex); //NON-NLS
            //TODO: this is almost certainly wrong and guaranteed to throw a NPE at some point!!!!
            return null;  //no need to create result view, just display error dialog
        }

        /*
         * For each term that matched the regex, query to get the full set of
         * document hits for that term.
         */
        QueryResults results = new QueryResults(this, keywordList);
        int resultSize = 0;
        for (Term term : terms) {
            String escapedTermString = null;

            if (keyword.getType() == ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER) {
                //If the keyword is a credit card number, pass it through luhn validator
                Matcher matcher = CCN_PATTERN.matcher(term.getTerm());
                matcher.find();
                if (false == LUHN_CHECK.isValid(matcher.group("ccn"))) {
                    continue; //if the hit does not pass the luhn check, skip it.
                }
            }

            escapedTermString = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());

            /*
             * Note: we can't set filter query on terms query but setting filter
             * query on fileResults query will yield the same result
             */
            LuceneQuery filesQuery = new LuceneQuery(keywordList, new Keyword(escapedTermString, true));
            filters.forEach(filesQuery::addFilter);

            try {
                QueryResults fileQueryResults = filesQuery.performQuery();
                Set<KeywordHit> filesResults = new HashSet<>();

                //flatten results into a single list
                for (Keyword key : fileQueryResults.getKeywords()) {
                    List<KeywordHit> keyRes = fileQueryResults.getResults(key);
                    resultSize += keyRes.size();
                    filesResults.addAll(keyRes);
                }
                results.addResult(new Keyword(escapedTermString, false), new ArrayList<>(filesResults));
            } catch (NoOpenCoreException | RuntimeException e) {
                LOGGER.log(Level.WARNING, "Error executing Solr query,", e); //NON-NLS
                throw e;
            }
        }

        //TODO limit how many results we store, not to hit memory limits
        LOGGER.log(Level.INFO, "Regex # results: {0}", resultSize); //NON-NLS

        return results;
    }
}
