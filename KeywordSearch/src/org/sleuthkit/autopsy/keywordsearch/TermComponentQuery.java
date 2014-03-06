/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.AbstractFile;
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
    private static final String TERMS_HANDLER = "/terms";
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static Logger logger = Logger.getLogger(TermComponentQuery.class.getName());
    private String termsQuery;
    private String queryEscaped;
    private boolean isEscaped;
    private List<Term> terms;
    private Keyword keywordQuery = null;
    private final List<KeywordQueryFilter> filters = new ArrayList<KeywordQueryFilter>();
    private String field = null;
    private static int MAX_TERMS_RESULTS = 20000;
    
    private static final boolean DEBUG = (Version.getBuildType() == Version.Type.DEVELOPMENT);

    public TermComponentQuery(Keyword keywordQuery) {
        this.keywordQuery = keywordQuery;
        this.termsQuery = keywordQuery.getQuery();
        this.queryEscaped = termsQuery;
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
        queryEscaped = Pattern.quote(termsQuery);
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
        q.setTermsRegexFlag("case_insensitive");
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
        List<Term> termsCol = null;
        try {
            Server solrServer = KeywordSearch.getServer();
            TermsResponse tr = solrServer.queryTerms(q);
            termsCol = tr.getTerms(TERMS_SEARCH_FIELD);
            return termsCol;
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing the regex terms query: " + termsQuery, ex);
            return null;  //no need to create result view, just display error dialog
        }
    }

    @Override
    public String getEscapedQueryString() {
        return this.queryEscaped;
    }

    @Override
    public String getQueryString() {
        return this.termsQuery;
    }

    @Override
    public Collection<Term> getTerms() {
        return terms;
    }

    @Override
    public KeywordWriteResult writeToBlackBoard(String termHit, AbstractFile newFsHit, String snippet, String listName) {
        final String MODULE_NAME = KeywordSearchIngestModule.MODULE_NAME;

        //there is match actually in this file, create artifact only then
        BlackboardArtifact bba = null;
        KeywordWriteResult writeResult = null;
        Collection<BlackboardAttribute> attributes = new ArrayList<BlackboardAttribute>();
        try {
            bba = newFsHit.newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
            writeResult = new KeywordWriteResult(bba);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error adding bb artifact for keyword hit", e);
            return null;
        }


        //regex match
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID(), MODULE_NAME, termHit));
        //list
        if (listName == null) {
            listName = "";
        }
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID(), MODULE_NAME, listName));

        //preview
        if (snippet != null) {
            attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, snippet));
        }
        //regex keyword
        attributes.add(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, termsQuery));

        //selector TODO move to general info artifact
            /*
        if (keywordQuery != null) {
        BlackboardAttribute.ATTRIBUTE_TYPE selType = keywordQuery.getType();
        if (selType != null) {
        BlackboardAttribute selAttr = new BlackboardAttribute(selType.getTypeID(), MODULE_NAME, "", regexMatch);
        attributes.add(selAttr);
        }
        } */

        try {
            bba.addAttributes(attributes);
            writeResult.add(attributes);
            return writeResult;
        } catch (TskException e) {
            logger.log(Level.WARNING, "Error adding bb attributes for terms search artifact", e);
        }

        return null;

    }

    @Override
    public Map<String, List<ContentHit>> performQuery() throws NoOpenCoreException {
        Map<String, List<ContentHit>> results = new HashMap<String, List<ContentHit>>();

        final SolrQuery q = createQuery();
        q.setShowDebugInfo(DEBUG);
        q.setTermsLimit(MAX_TERMS_RESULTS); 
        logger.log(Level.INFO, "Query: " + q.toString());
        terms = executeQuery(q);

        int resultSize = 0;
        
        for (Term term : terms) {
            final String termStr = KeywordSearchUtil.escapeLuceneQuery(term.getTerm());

            LuceneQuery filesQuery = new LuceneQuery(termStr);
            //filesQuery.setField(TERMS_SEARCH_FIELD);
            for (KeywordQueryFilter filter : filters) {
                //set filter
                //note: we can't set filter query on terms query
                //but setting filter query on terms results query will yield the same result
                filesQuery.addFilter(filter);
            }
            try {
                Map<String, List<ContentHit>> subResults = filesQuery.performQuery();
                Set<ContentHit> filesResults = new HashSet<ContentHit>();
                for (String key : subResults.keySet()) {
                    List<ContentHit> keyRes = subResults.get(key);
                    resultSize += keyRes.size();
                    filesResults.addAll(keyRes);
                }
                results.put(term.getTerm(), new ArrayList<ContentHit>(filesResults));
            } catch (NoOpenCoreException e) {
                logger.log(Level.WARNING, "Error executing Solr query,", e);
                throw e;
            } catch (RuntimeException e) {
                logger.log(Level.WARNING, "Error executing Solr query,", e);
            }

        }
        
        //TODO limit how many results we store, not to hit memory limits
        logger.log(Level.INFO, "Regex # results: " + resultSize);


        return results;
    }
}
