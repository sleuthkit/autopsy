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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode.FsContentPropertyType;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;
import org.sleuthkit.autopsy.keywordsearch.Server.Core;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;

/**
 *
 * factory produces top level nodes with query
 * responsible for assembling nodes and columns in the right way
 * and performing lazy queries as needed
 */
public class KeywordSearchResultFactory extends ChildFactory<KeyValueQuery> {

    //common properties (superset of all Node properties) to be displayed as columns
    //these are merged with FsContentPropertyType defined properties
    public static enum CommonPropertyTypes {

        KEYWORD {

            @Override
            public String toString() {
                return "Keyword";
            }
        },
        REGEX {

            @Override
            public String toString() {
                return "Regex";
            }
        },
        MATCH {

            @Override
            public String toString() {
                return "Match";
            }
        },
        CONTEXT {

            @Override
            public String toString() {
                return "Context";
            }
        },}
    private Presentation presentation;
    private List<Keyword> queries;
    private Collection<KeyValueQuery> things;
    private static final Logger logger = Logger.getLogger(KeywordSearchResultFactory.class.getName());

    KeywordSearchResultFactory(List<Keyword> queries, Collection<KeyValueQuery> things, Presentation presentation) {
        this.queries = queries;
        this.things = things;
        this.presentation = presentation;
    }

    KeywordSearchResultFactory(Keyword query, Collection<KeyValueQuery> things, Presentation presentation) {
        queries = new ArrayList<Keyword>();
        queries.add(query);
        this.presentation = presentation;
        this.things = things;
    }

    /**
     * call this at least for the parent Node, to make sure all common 
     * properties are displayed as columns (since we are doing lazy child Node load
     * we need to preinitialize properties when sending parent Node)
     * @param toSet property set map for a Node
     */
    public static void initCommonProperties(Map<String, Object> toSet) {
        CommonPropertyTypes[] commonTypes = CommonPropertyTypes.values();
        final int COMMON_PROPS_LEN = commonTypes.length;
        for (int i = 0; i < COMMON_PROPS_LEN; ++i) {
            toSet.put(commonTypes[i].toString(), "");
        }

        FsContentPropertyType[] fsTypes = FsContentPropertyType.values();
        final int FS_PROPS_LEN = fsTypes.length;
        for (int i = 0; i < FS_PROPS_LEN; ++i) {
            toSet.put(fsTypes[i].toString(), "");
        }

    }

    public static void setCommonProperty(Map<String, Object> toSet, CommonPropertyTypes type, String value) {
        final String typeStr = type.toString();
        toSet.put(typeStr, value);
    }

    public static void setCommonProperty(Map<String, Object> toSet, CommonPropertyTypes type, Boolean value) {
        final String typeStr = type.toString();
        toSet.put(typeStr, value);
    }

    @Override
    protected boolean createKeys(List<KeyValueQuery> toPopulate) {
        int id = 0;
        if (presentation == Presentation.DETAIL) {
            Iterator<KeyValueQuery> it = things.iterator();
            for (Keyword keyword : queries) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                final String query = keyword.getQuery();
                initCommonProperties(map);
                setCommonProperty(map, CommonPropertyTypes.KEYWORD, query);
                setCommonProperty(map, CommonPropertyTypes.REGEX, Boolean.valueOf(!keyword.isLiteral()));
                KeyValueQuery kvq = null;
                if (it.hasNext()) {
                    kvq = it.next();
                }
                toPopulate.add(new KeyValueQuery(query, map, ++id, kvq.getQuery()));
            }
        } else {
            for (KeyValueQuery thing : things) {
                //Map<String, Object> map = new LinkedHashMap<String, Object>();
                Map<String, Object> map = thing.getMap();
                initCommonProperties(map);
                final String query = thing.getName();
                setCommonProperty(map, CommonPropertyTypes.KEYWORD, query);
                setCommonProperty(map, CommonPropertyTypes.REGEX, Boolean.valueOf(!thing.getQuery().isEscaped()));
                //toPopulate.add(new KeyValue(query, map, ++id));
                toPopulate.add(thing);
            }
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(KeyValueQuery thing) {
        ChildFactory<KeyValueQuery> childFactory = null;

        if (presentation == Presentation.COLLAPSE) {
            childFactory = new ResultCollapsedChildFactory(thing);
            final Node ret = new KeyValueNode(thing, Children.create(childFactory, true));
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    for (DataResultViewer view : Lookup.getDefault().lookupAll(DataResultViewer.class)) {
                        view.expandNode(ret);
                    }
                }
            });
            return ret;
        } else {

            childFactory = new ResulTermsMatchesChildFactory(things);
            return new KeyValueNode(thing, Children.create(childFactory, true));
        }
    }

    /**
     * factory produces collapsed view of all fscontent matches per query
     * the node produced is a child node
     * The factory actually executes query.
     */
    class ResultCollapsedChildFactory extends ChildFactory<KeyValueQuery> {

        KeyValueQuery queryThing;

        ResultCollapsedChildFactory(KeyValueQuery queryThing) {
            this.queryThing = queryThing;
        }

        @Override
        protected boolean createKeys(List<KeyValueQuery> toPopulate) {
            //final String origQuery = queryThing.getName();
            final KeyValueQuery queryThingQuery = queryThing;
            final KeywordSearchQuery tcq = queryThingQuery.getQuery();

            if (!tcq.validate()) {
                //TODO mark the particular query node RED
                return false;
            }

            //execute the query and get fscontents matching
            Map<String, List<FsContent>> tcqRes = tcq.performQuery();
            final Set<FsContent> fsContents = new HashSet<FsContent>();
            for (String key : tcqRes.keySet()) {
                fsContents.addAll(tcqRes.get(key));
            }

            String highlightQueryEscaped = null;
            final boolean literal_query = tcq.isEscaped();

            if (literal_query) {
                //literal, treat as non-regex, non-term component query
                highlightQueryEscaped = tcq.getQueryString();
            } else {
                //construct a Solr query using aggregated terms to get highlighting
                //the query is executed later on demand
                StringBuilder highlightQuery = new StringBuilder();
                Collection<Term> terms = tcq.getTerms();

                if (terms.size() == 1) {
                    //simple case, no need to process subqueries and do special escaping
                    Term term = terms.iterator().next();
                    highlightQuery.append(term.getTerm());
                } else {
                    final int lastTerm = terms.size() - 1;
                    int curTerm = 0;
                    for (Term term : terms) {
                        //escape subqueries, they shouldn't be escaped again later
                        final String termS = KeywordSearchUtil.escapeLuceneQuery(term.getTerm(), true, false);
                        if (!termS.contains("*")) {
                            highlightQuery.append(termS);
                            if (lastTerm != curTerm) {
                                highlightQuery.append(" "); //acts as OR ||
                                //force white-space separated index and stored content
                                //in each term after first. First term taken case by HighlightedMatchesSource
                                highlightQuery.append(LuceneQuery.HIGHLIGHT_FIELD_REGEX).append(":");
                            }
                        }
                        ++curTerm;
                    }
                }
                //String highlightQueryEscaped = KeywordSearchUtil.escapeLuceneQuery(highlightQuery.toString());
                highlightQueryEscaped = highlightQuery.toString();
            }


            //get listname
            String listName = "";
            KeywordSearchList list = KeywordSearchListsXML.getCurrent().getListWithKeyword(tcq.getQueryString());
            if (list != null) {
                listName = list.getName();
            }
            final String theListName = listName;

            int resID = 0;

            for (final FsContent f : fsContents) {
                //get unique match result files
                Map<String, Object> resMap = new LinkedHashMap<String, Object>();
                AbstractFsContentNode.fillPropertyMap(resMap, f);
                setCommonProperty(resMap, CommonPropertyTypes.MATCH, f.getName());
                if (literal_query) {
                    final String snippet = LuceneQuery.querySnippet(tcq.getEscapedQueryString(), f.getId(), false, true);
                    setCommonProperty(resMap, CommonPropertyTypes.CONTEXT, snippet);
                }
                toPopulate.add(new KeyValueQueryContent(f.getName(), resMap, ++resID, f, highlightQueryEscaped, tcq));
            }
            //write to bb
            new Thread() {

                @Override
                public void run() {
                    final Collection<BlackboardArtifact> na = new ArrayList<BlackboardArtifact>();
                    for (final FsContent f : fsContents) {
                        Collection<KeywordWriteResult> written = tcq.writeToBlackBoard(f, theListName);
                        for (KeywordWriteResult w : written) {
                            na.add(w.getArtifact());
                        }

                    }
                    if (!na.isEmpty()) {
                        IngestManager.fireServiceDataEvent(new ServiceDataEvent(KeywordSearchIngestService.MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, na));
                    }
                }
            }.start();

            return true;
        }

        @Override
        protected Node createNodeForKey(KeyValueQuery thing) {
            //return new KeyValueNode(thing, Children.LEAF);
            //return new KeyValueNode(thing, Children.create(new ResultFilesChildFactory(thing), true));
            final KeyValueQueryContent thingContent = (KeyValueQueryContent) thing;
            final Content content = thingContent.getContent();
            final String queryStr = thingContent.getQueryStr();

            Node kvNode = new KeyValueNode(thingContent, Children.LEAF, Lookups.singleton(content));
            //wrap in KeywordSearchFilterNode for the markup content, might need to override FilterNode for more customization
            HighlightedMatchesSource highlights = new HighlightedMatchesSource(content, queryStr, !thingContent.getQuery().isEscaped(), false);
            return new KeywordSearchFilterNode(highlights, kvNode, queryStr);

        }
    }

    /**
     * factory produces top level result nodes showing *exact* regex match result
     */
    class ResulTermsMatchesChildFactory extends ChildFactory<KeyValueQuery> {

        Collection<KeyValueQuery> things;

        ResulTermsMatchesChildFactory(Collection<KeyValueQuery> things) {
            this.things = things;
        }

        @Override
        protected boolean createKeys(List<KeyValueQuery> toPopulate) {
            return toPopulate.addAll(things);
        }

        @Override
        protected Node createNodeForKey(KeyValueQuery thing) {
            //return new KeyValueNode(thing, Children.LEAF);
            return new KeyValueNode(thing, Children.create(new ResultFilesChildFactory(thing), true));
        }

        /**
         * factory produces 2nd level child nodes showing files with *approximate* matches
         * since they rely on underlying Lucene query to get details
         * To implement exact regex match detail view, we need to extract files content
         * returned by Lucene and further narrow down by applying a Java regex
         */
        class ResultFilesChildFactory extends ChildFactory<KeyValueQuery> {

            private KeyValueQuery thing;

            ResultFilesChildFactory(KeyValueQuery thing) {
                this.thing = thing;
            }

            @Override
            protected boolean createKeys(List<KeyValueQuery> toPopulate) {
                //use Lucene query to get files with regular expression match result
                final String keywordQuery = thing.getName();
                LuceneQuery filesQuery = new LuceneQuery(keywordQuery);
                filesQuery.escape();

                Map<String, List<FsContent>> matchesRes = filesQuery.performQuery();
                Set<FsContent> matches = new HashSet<FsContent>();
                for (String key : matchesRes.keySet()) {
                    matches.addAll(matchesRes.get(key));
                }

                //get unique match result files
                final Set<FsContent> uniqueMatches = new LinkedHashSet<FsContent>();
                uniqueMatches.addAll(matches);

                int resID = 0;

                final KeywordSearchQuery origQuery = thing.getQuery();

                for (final FsContent f : uniqueMatches) {
                    Map<String, Object> resMap = new LinkedHashMap<String, Object>();
                    AbstractFsContentNode.fillPropertyMap(resMap, (File) f);
                    toPopulate.add(new KeyValueQueryContent(f.getName(), resMap, ++resID, f, keywordQuery, thing.getQuery()));

                }
                //write to bb
                new Thread() {

                    @Override
                    public void run() {
                        final Collection<BlackboardArtifact> na = new ArrayList<BlackboardArtifact>();
                        for (final FsContent f : uniqueMatches) {
                            Collection<KeywordWriteResult> written = origQuery.writeToBlackBoard(f, "");
                            for (KeywordWriteResult w : written) {
                                na.add(w.getArtifact());
                            }
                        }
                        if (!na.isEmpty()) {
                            IngestManager.fireServiceDataEvent(new ServiceDataEvent(KeywordSearchIngestService.MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, na));
                        }
                    }
                }.start();


                return true;
            }

            @Override
            protected Node createNodeForKey(KeyValueQuery thing) {
                final KeyValueQueryContent thingContent = (KeyValueQueryContent) thing;
                final Content content = thingContent.getContent();
                final String query = thingContent.getQueryStr();

                Node kvNode = new KeyValueNode(thingContent, Children.LEAF, Lookups.singleton(content));
                //wrap in KeywordSearchFilterNode for the markup content
                HighlightedMatchesSource highlights = new HighlightedMatchesSource(content, query, !thingContent.getQuery().isEscaped());
                return new KeywordSearchFilterNode(highlights, kvNode, query);
            }
        }
    }

    /*
     * custom KeyValue that also stores retrieved Content and query used
     */
    class KeyValueQueryContent extends KeyValueQuery {

        private Content content;
        private String queryStr;
        private KeywordSearchQuery query;

        Content getContent() {
            return content;
        }

        String getQueryStr() {
            return queryStr;
        }

        public KeyValueQueryContent(String name, Map<String, Object> map, int id, Content content, String queryStr, KeywordSearchQuery query) {
            super(name, map, id, query);
            this.content = content;
            this.queryStr = queryStr;
        }
    }
}
