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

    private static final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();
    private static final BlackboardAttribute.Type CHUNK_ID_TYPE = new BlackboardAttribute.Type(ATTRIBUTE_TYPE.TSK_CHUNK_ID);

    //corresponds to field in Solr schema, analyzed with white-space tokenizer only
    private static final String TERMS_SEARCH_FIELD = Server.Schema.CONTENT_WS.toString();
    private static final String TERMS_HANDLER = "/terms"; //NON-NLS
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static final Logger LOGGER = Logger.getLogger(TermComponentQuery.class.getName());
    private static final String CASE_INSENSITIVE = "case_insensitive"; //NON-NLS
    private String escapedQuery;
    private final KeywordList keywordList;
    private final Keyword keyword;
    private boolean isEscaped = false;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();
    private static final int MAX_TERMS_RESULTS = 20000;

    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

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
        } catch (IllegalArgumentException ex) {
            return false;
        }

        return true;
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

    static private void addAttributeIfNotAlreadyCaptured(BlackboardArtifact bba, ATTRIBUTE_TYPE attrType, String groupName, Matcher matcher) throws IllegalArgumentException, TskCoreException {
        BlackboardAttribute.Type type = new BlackboardAttribute.Type(attrType);
        if (bba.getAttribute(type) == null) {
            String value = matcher.group(groupName);
            if (StringUtils.isNotBlank(value)) {
                bba.addAttribute(new BlackboardAttribute(type, MODULE_NAME, value));
            }
        }
    }

    static private void parseTrackData(BlackboardArtifact bba, Matcher matcher, KeywordHit hit, boolean tryName) throws IllegalArgumentException, TskCoreException {
        addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER, "accountNumber", matcher);
        addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_EXPIRATION, "expiration", matcher);
        addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_SERVICE_CODE, "serviceCode", matcher);
        addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_DISCRETIONARY, "discretionary", matcher);
        addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_CREDIT_CARD_LRC, "LRC", matcher);
        if(tryName){
            addAttributeIfNotAlreadyCaptured(bba, ATTRIBUTE_TYPE.TSK_NAME_PERSON, "name", matcher);
        } 
        if (bba.getAttribute(CHUNK_ID_TYPE) == null) {
            bba.addAttribute(new BlackboardAttribute(CHUNK_ID_TYPE, MODULE_NAME, hit.getChunkId()));
        }
    }

    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName) {

        try {
            BlackboardArtifact bba;
            Collection<BlackboardAttribute> attributes = new ArrayList<>();

            bba = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);

            if (keyword.getType() == ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER) {
                Matcher matcher = TRACK1_PATTERN.matcher(hit.getSnippet());
                if (matcher.find()) {
                    parseTrackData(bba, matcher, hit, true);
                }
                matcher = TRACK2_PATTERN.matcher(hit.getSnippet());
                if (matcher.find()) {
                    parseTrackData(bba, matcher, hit,false);
                }
            } else {

            }

            //regex match
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, termHit));

            if (StringUtils.isNotEmpty(listName)) {
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
            }

            //regex keyword
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getQuery()));
            //preview
            if (snippet != null) {
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
            }

            if (hit.isArtifactHit()) {
                attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
            }

            try {
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
    private static final String CCN_WITH_TRACK_2_REGEX = "[:;<=>?]?(?<accountNumber>\\d{13,19})(?:[:;<=>?](?:(?<expiration>\\d{4})(?:(?<serviceCode>\\d{3})(?:(?<discretionary>[^:;<=>?]*)(?:[:;<=>?](?<LRC>.)?)?)?)?)?)?";  //NON-NLS
    private static final Pattern TRACK2_PATTERN = Pattern.compile(CCN_WITH_TRACK_2_REGEX);
    private static final String CCN_WITH_TRACK_1_REGEX = "(?:%?B)?(?<accountNumber>\\d{13,19})\\^(?<name>[^^]{2,26})(?:\\^(?:(?:\\^|(?<expiration>\\d{4}))(?:(?:\\^|(?<serviceCode>\\d{3}))(?:(?<discretionary>[^?]*)(?:\\?(?<LRC>.)?)?)?)?)?)?";  //NON-NLS
    private static final Pattern TRACK1_PATTERN = Pattern.compile(CCN_WITH_TRACK_1_REGEX);

    @Override
    public QueryResults performQuery() throws NoOpenCoreException {

        /*
         * Execute the regex query to get a list of terms that match the regex.
         * Note that the field that is being searched is tokenized based on
         * whitespace.
         */
        final SolrQuery termsQuery = new SolrQuery();
        termsQuery.setRequestHandler(TERMS_HANDLER);
        termsQuery.setTerms(true);
        termsQuery.setTermsRegexFlag(CASE_INSENSITIVE);
        //q.setTermsRegexFlag(regexFlag);
        //q.setTermsRaw(true);
        termsQuery.setTermsRegex(escapedQuery);
        termsQuery.addTermsField(TERMS_SEARCH_FIELD);
        termsQuery.setTimeAllowed(TERMS_TIMEOUT);
        termsQuery.setShowDebugInfo(DEBUG);
        termsQuery.setTermsLimit(MAX_TERMS_RESULTS);
        LOGGER.log(Level.INFO, "Query: {0}", termsQuery.toString()); //NON-NLS

        List<Term> terms;
        try {
            Server solrServer = KeywordSearch.getServer();
            terms = solrServer.queryTerms(termsQuery).getTerms(TERMS_SEARCH_FIELD);
        } catch (KeywordSearchModuleException ex) {
            LOGGER.log(Level.WARNING, "Error executing the regex terms query: " + keyword.getQuery(), ex); //NON-NLS
            //TODO: this almost certainly wrong and guaranteed to throw a NPE at some point!!!!
            return null;  //no need to create result view, just display error dialog
        }
        /*
         * For each term that matched the regex, query for the term to get the
         * full set of document hits.
         */
        QueryResults results = new QueryResults(this, keywordList);
        int resultSize = 0;
        for (Term term : terms) {
            String escapedTermString = null;

            if (keyword.getType() == ATTRIBUTE_TYPE.TSK_CREDIT_CARD_NUMBER) {
                if (false == new LuhnCheckDigit().isValid(term.getTerm())) {
//                        LOGGER.log(Level.INFO, term.getTerm() + " did not pass luhn validation!");
//                        continue;
//                    }
//              
                } else {

                }
            }
            escapedTermString = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());

            /*
             * Note: we can't set filter query on terms query but setting filter
             * query on terms results query will yield the same result
             */
            LuceneQuery filesQuery = new LuceneQuery(keywordList, new Keyword(escapedTermString, true));
            filters.forEach(filesQuery::addFilter);
            
            try {
                QueryResults fileResults = filesQuery.performQuery();
                Set<KeywordHit> filesResults = new HashSet<>();
                for (Keyword key : fileResults.getKeywords()) {
                    List<KeywordHit> keyRes = fileResults.getResults(key);
                    resultSize += keyRes.size();
                    filesResults.addAll(keyRes);
                }
                results.addResult(new Keyword(escapedTermString, false), new ArrayList<>(filesResults));
            } catch (NoOpenCoreException | RuntimeException  e) {
                LOGGER.log(Level.WARNING, "Error executing Solr query,", e); //NON-NLS
                throw e;
            }
        }

        //TODO limit how many results we store, not to hit memory limits
        LOGGER.log(Level.INFO, "Regex # results: {0}", resultSize); //NON-NLS

        return results;
    }

}
