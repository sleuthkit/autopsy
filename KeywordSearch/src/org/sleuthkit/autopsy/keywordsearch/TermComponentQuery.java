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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.SwingWorker;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.windows.TopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;
import org.sleuthkit.datamodel.FsContent;

public class TermComponentQuery implements KeywordSearchQuery {

    private static final int TERMS_UNLIMITED = -1;
    //corresponds to field in Solr schema, analyzed with white-space tokenizer only
    private static final String TERMS_SEARCH_FIELD = "content_ws";
    private static final String TERMS_HANDLER = "/terms";
    private static final int TERMS_TIMEOUT = 90 * 1000; //in ms
    private static Logger logger = Logger.getLogger(TermComponentQuery.class.getName());
    private String termsQuery;
    private String queryEscaped;
    private boolean isEscaped;

    public TermComponentQuery(String query) {
        this.termsQuery = query;
        this.queryEscaped = query;
        isEscaped = false;
    }

    @Override
    public void escape() {
        //treat as literal
        queryEscaped = Pattern.quote(termsQuery);
        isEscaped = true;
    }

    @Override
    public boolean validate() {
        boolean valid = true;
        try {
            Pattern.compile(termsQuery);
        } catch (PatternSyntaxException ex1) {
            valid = false;
        } catch (IllegalArgumentException ex2) {
            valid = false;
        }
        return valid;
    }

    /*
     * helper method to create a Solr terms component query
     */
    protected SolrQuery createQuery() {
        final SolrQuery q = new SolrQuery();
        q.setQueryType(TERMS_HANDLER);
        q.setTerms(true);
        q.setTermsLimit(TERMS_UNLIMITED);
        q.setTermsRegexFlag("case_insensitive");
        //q.setTermsLimit(200);
        //q.setTermsRegexFlag(regexFlag);
        //q.setTermsRaw(true);
        q.setTermsRegex(termsQuery);
        q.addTermsField(TERMS_SEARCH_FIELD);
        q.setTimeAllowed(TERMS_TIMEOUT);

        return q;

    }

    /*
     * execute query and return terms
     * helper method, can be called from the same or threaded query context
     */
    protected List<Term> executeQuery(SolrQuery q) {
        Server.Core solrCore = KeywordSearch.getServer().getCore();

        List<Term> terms = null;
        try {
            TermsResponse tr = solrCore.queryTerms(q);
            terms = tr.getTerms(TERMS_SEARCH_FIELD);
            return terms;
        } catch (SolrServerException ex) {
            logger.log(Level.SEVERE, "Error executing the regex terms query: " + termsQuery, ex);
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
    
    /**
     * return collapsed matches with all files for the query
     * without per match breakdown
     */
    @Override
    public List<FsContent> performQuery() {
        List<FsContent> results = new ArrayList();

        final SolrQuery q = createQuery();
        List<Term> terms = executeQuery(q);
        //get unique match result files
        Set<FsContent> uniqueMatches = new TreeSet<FsContent>();

        for (Term term : terms) {
            String word = term.getTerm();
            LuceneQuery filesQuery = new LuceneQuery(word);
            filesQuery.escape();
            List<FsContent> matches = filesQuery.performQuery();
            uniqueMatches.addAll(matches);
        }

        //filter out non-matching files
        //escape regex if needed 
        //(if the original query was not literal, this must be)
        String literalQuery = null;
        if (isEscaped) {
            literalQuery = this.queryEscaped;
        } else {
            literalQuery = Pattern.quote(this.termsQuery);
        }
        for (FsContent f : uniqueMatches) {
            Pattern p = Pattern.compile(literalQuery, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            final String contentStr = KeywordSearch.getServer().getCore().getSolrContent(f);
            Matcher m = p.matcher(contentStr);
            if (m.find()) {
                results.add(f);
            }
        }

        return results;
    }

    @Override
    public void execute() {
        SolrQuery q = createQuery();

        logger.log(Level.INFO, "Executing TermsComponent query: " + q.toString());

        final SwingWorker worker = new TermsQueryWorker(q);
        worker.execute();
    }

    /**
     * map Terms to generic Nodes with key/value pairs properties
     * @param terms 
     */
    private void publishNodes(List<Term> terms) {

        Collection<KeyValueThing> things = new ArrayList<KeyValueThing>();

        Iterator<Term> it = terms.iterator();
        int termID = 0;
        //long totalMatches = 0;
        while (it.hasNext()) {
            Term term = it.next();
            Map<String, Object> kvs = new LinkedHashMap<String, Object>();
            //long matches = term.getFrequency();
            final String match = term.getTerm();
            KeywordSearchResultFactory.setCommonProperty(kvs, KeywordSearchResultFactory.CommonPropertyTypes.MATCH, match);
            //setCommonProperty(kvs, CommonPropertyTypes.MATCH_RANK, Long.toString(matches));
            things.add(new KeyValueThing(match, kvs, ++termID));
            //totalMatches += matches;
        }

        Node rootNode = null;
        if (things.size() > 0) {
            Children childThingNodes =
                    Children.create(new KeywordSearchResultFactory(termsQuery, things, Presentation.DETAIL), true);

            rootNode = new AbstractNode(childThingNodes);
        } else {
            rootNode = Node.EMPTY;
        }

        final String pathText = "RegEx query";
        // String pathText = "RegEx query: " + termsQuery
        //+ "    Files with exact matches: " + Long.toString(totalMatches) + " (also listing approximate matches)";

        TopComponent searchResultWin = DataResultTopComponent.createInstance("Keyword search", pathText, rootNode, things.size());
        searchResultWin.requestActive(); // make it the active top component

    }

    class TermsQueryWorker extends SwingWorker<List<Term>, Void> {

        private SolrQuery q;
        private ProgressHandle progress;

        TermsQueryWorker(SolrQuery q) {
            this.q = q;
        }

        @Override
        protected List<Term> doInBackground() throws Exception {
            progress = ProgressHandleFactory.createHandle("Terms query task");
            progress.start();
            progress.progress("Running Terms query.");

            List<Term> terms = executeQuery(q);

            progress.progress("Terms query completed.");

            //debug query
            //StringBuilder sb = new StringBuilder();
            //for (Term t : terms) {
            //    sb.append(t.getTerm() + " : " + t.getFrequency() + "\n");
            //}
            //logger.log(Level.INFO, "TermsComponent query result: " + sb.toString());
            //end debug query

            return terms;
        }

        @Override
        protected void done() {
            if (!this.isCancelled()) {
                try {
                    List<Term> terms = get();
                    publishNodes(terms);
                } catch (InterruptedException e) {
                    logger.log(Level.INFO, "Exception while executing regex query,", e);

                } catch (ExecutionException e) {
                    logger.log(Level.INFO, "Exception while executing regex query,", e);
                } finally {
                    progress.finish();
                }
            }
        }
    }
}
