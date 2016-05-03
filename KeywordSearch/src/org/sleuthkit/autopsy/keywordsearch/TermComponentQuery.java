/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2014 Basis Technology Corp.
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
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.TskException;

/**
 * Performs a regular expression query to the SOLR/Lucene instance.
 */
class TermComponentQuery implements KeywordSearchQuery {

    private static final int TERMS_UNLIMITED = -1;
    //corresponds to field in Solr schema, analyzed with white-space tokenizer only
    private static final String TERMS_SEARCH_FIELD = Server.Schema.CONTENT_WS.toString();
    private static final String TERMS_HANDLER = "/terms"; //NON-NLS
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static final Logger logger = Logger.getLogger(TermComponentQuery.class.getName());
    private String queryEscaped;
    private final KeywordList keywordList;
    private final Keyword keyword;
    private boolean isEscaped;
    private List<Term> terms;
    private final List<KeywordQueryFilter> filters = new ArrayList<>();
    private String field;
    private static final int MAX_TERMS_RESULTS = 20000;

    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    public TermComponentQuery(KeywordList keywordList, Keyword keyword) {
        this.field = null;
        this.keyword = keyword;
        this.keywordList = keywordList;
        this.queryEscaped = keyword.getQuery();
        isEscaped = false;
        terms = null;
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
        queryEscaped = ".*" + queryEscaped + ".*";
    }

    @Override
    public void escape() {
        queryEscaped = Pattern.quote(keyword.getQuery());
        isEscaped = true;
    }

    @Override
    public boolean validate() {
        if (queryEscaped.equals("")) {
            return false;
        }

        boolean valid = true;
        try {
            Pattern.compile(queryEscaped);
        } catch (PatternSyntaxException ex1) {
            valid = false;
        } catch (IllegalArgumentException ex2) {
            valid = false;
        }
        return valid;
    }

    @Override
    public boolean isEscaped() {
        return isEscaped;
    }

    @Override
    public boolean isLiteral() {
        return false;
    }

    /*
     * helper method to create a Solr terms component query
     */
    protected SolrQuery createQuery() {
        final SolrQuery q = new SolrQuery();
        q.setRequestHandler(TERMS_HANDLER);
        q.setTerms(true);
        q.setTermsLimit(TERMS_UNLIMITED);
        q.setTermsRegexFlag("case_insensitive"); //NON-NLS
        //q.setTermsLimit(200);
        //q.setTermsRegexFlag(regexFlag);
        //q.setTermsRaw(true);
        q.setTermsRegex(queryEscaped);
        q.addTermsField(TERMS_SEARCH_FIELD);
        q.setTimeAllowed(TERMS_TIMEOUT);

        return q;

    }

    /*
     * execute query and return terms, helper method
     */
    protected List<Term> executeQuery(SolrQuery q) throws NoOpenCoreException {
        try {
            Server solrServer = KeywordSearch.getServer();
            TermsResponse tr = solrServer.queryTerms(q);
            List<Term> termsCol = tr.getTerms(TERMS_SEARCH_FIELD);
            return termsCol;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing the regex terms query: " + keyword.getQuery(), ex); //NON-NLS
            return null;  //no need to create result view, just display error dialog
        }
    }

    @Override
    public String getEscapedQueryString() {
        return this.queryEscaped;
    }

    @Override
    public String getQueryString() {
        return keyword.getQuery();
    }

    @Override
    public KeywordCachedArtifact writeSingleFileHitsToBlackBoard(String termHit, KeywordHit hit, String snippet, String listName) {
        final String MODULE_NAME = KeywordSearchModuleFactory.getModuleName();

        //there is match actually in this file, create artifact only then
        BlackboardArtifact bba;
        KeywordCachedArtifact writeResult;
        Collection<BlackboardAttribute> attributes = new ArrayList<>();
        try {
            bba = hit.getContent().newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            writeResult = new KeywordCachedArtifact(bba);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error adding bb artifact for keyword hit", e); //NON-NLS
            return null;
        }

        //regex match
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD, MODULE_NAME, termHit));

        if ((listName != null) && (listName.equals("") == false)) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME, MODULE_NAME, listName));
        }

        //preview
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW, MODULE_NAME, snippet));
        }
        //regex keyword
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP, MODULE_NAME, keyword.getQuery()));

        if (hit.isArtifactHit()) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_ASSOCIATED_ARTIFACT, MODULE_NAME, hit.getArtifact().getArtifactID()));
        }

        try {
            bba.addAttributes(attributes);
            writeResult.add(attributes);
            return writeResult;
        } catch (TskException e) {
            logger.log(Level.WARNING, "Error adding bb attributes for terms search artifact", e); //NON-NLS
        }

        return null;
    }

    @Override
    public QueryResults performQuery() throws NoOpenCoreException, KeywordSearchSettingsManager.KeywordSearchSettingsManagerException {

        final SolrQuery q = createQuery();
        q.setShowDebugInfo(DEBUG);
        q.setTermsLimit(MAX_TERMS_RESULTS);
        logger.log(Level.INFO, "Query: {0}", q.toString()); //NON-NLS
        terms = executeQuery(q);

        QueryResults results = new QueryResults(this, keywordList);
        int resultSize = 0;

        for (Term term : terms) {
            final String termStr = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());

            LuceneQuery filesQuery = new LuceneQuery(keywordList, new Keyword(termStr, true));

            //filesQuery.setField(TERMS_SEARCH_FIELD);
            for (KeywordQueryFilter filter : filters) {
                //set filter
                //note: we can't set filter query on terms query
                //but setting filter query on terms results query will yield the same result
                filesQuery.addFilter(filter);
            }
            try {
                QueryResults subResults = filesQuery.performQuery();
                Set<KeywordHit> filesResults = new HashSet<>();
                for (Keyword key : subResults.getKeywords()) {
                    List<KeywordHit> keyRes = subResults.getResults(key);
                    resultSize += keyRes.size();
                    filesResults.addAll(keyRes);
                }
                results.addResult(new Keyword(term.getTerm(), false), new ArrayList<>(filesResults));
            } catch (NoOpenCoreException | RuntimeException | KeywordSearchSettingsManager.KeywordSearchSettingsManagerException e) {
                logger.log(Level.WARNING, "Error executing Solr query,", e); //NON-NLS
                throw e;
            }
            //NON-NLS
            //NON-NLS
            

        }

        //TODO limit how many results we store, not to hit memory limits
        logger.log(Level.INFO, "Regex # results: {0}", resultSize); //NON-NLS

        return results;
    }

    @Override
    public KeywordList getKeywordList() {
        return keywordList;
    }
}
