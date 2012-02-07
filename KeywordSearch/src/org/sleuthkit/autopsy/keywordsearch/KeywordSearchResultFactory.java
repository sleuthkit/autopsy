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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataResultViewer;
import org.sleuthkit.autopsy.corecomponents.DataResultViewerTable;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode.FsContentPropertyType;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.datamodel.KeyValueThing;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchQueryManager.Presentation;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;

/**
 *
 * factory produces top level nodes with query
 * responsible for assembling nodes and columns in the right way
 * and performing lazy queries as needed
 */
public class KeywordSearchResultFactory extends ChildFactory<KeyValueThing> {

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
    private Collection<KeyValueThing> things;
    private static final Logger logger = Logger.getLogger(KeywordSearchResultFactory.class.getName());

    KeywordSearchResultFactory(List<Keyword> queries, Collection<KeyValueThing> things, Presentation presentation) {
        this.queries = queries;
        this.things = things;
        this.presentation = presentation;
    }

    KeywordSearchResultFactory(String query, Collection<KeyValueThing> things, Presentation presentation) {
        queries = new ArrayList<Keyword>();
        queries.add(new Keyword(query, false));
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
    protected boolean createKeys(List<KeyValueThing> toPopulate) {
        int id = 0;
        if (presentation == Presentation.DETAIL) {
            for (Keyword keyword : queries) {
                Map<String, Object> map = new LinkedHashMap<String, Object>();
                final String query = keyword.getQuery();
                initCommonProperties(map);
                setCommonProperty(map, CommonPropertyTypes.KEYWORD, query);
                setCommonProperty(map, CommonPropertyTypes.REGEX, Boolean.valueOf(!keyword.isLiteral()));
                toPopulate.add(new KeyValueThing(query, map, ++id));
            }
        } else {
            for (KeyValueThing thing : things) {
                //Map<String, Object> map = new LinkedHashMap<String, Object>();
                Map<String, Object> map = thing.getMap();
                initCommonProperties(map);
                final String query = thing.getName();
                setCommonProperty(map, CommonPropertyTypes.KEYWORD, query);
                KeyValueThingQuery thingQuery = (KeyValueThingQuery) thing;
                setCommonProperty(map, CommonPropertyTypes.REGEX, Boolean.valueOf(!thingQuery.getQuery().isEscaped()));
                //toPopulate.add(new KeyValueThing(query, map, ++id));
                toPopulate.add(thing);
            }
        }

        return true;
    }

    @Override
    protected Node createNodeForKey(KeyValueThing thing) {
        ChildFactory<KeyValueThing> childFactory = null;

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
    class ResultCollapsedChildFactory extends ChildFactory<KeyValueThing> {

        KeyValueThing queryThing;

        ResultCollapsedChildFactory(KeyValueThing queryThing) {
            this.queryThing = queryThing;
        }

        @Override
        protected boolean createKeys(List<KeyValueThing> toPopulate) {
            //final String origQuery = queryThing.getName();
            final KeyValueThingQuery queryThingQuery = (KeyValueThingQuery) queryThing;
            final KeywordSearchQuery tcq = queryThingQuery.getQuery();

            if (!tcq.validate()) {
                //TODO mark the particular query node RED
                return false;
            }

            //execute the query and get fscontents matching
            List<FsContent> fsContents = tcq.performQuery();


            String highlightQueryEscaped = null;
            final boolean literal_query = tcq.isEscaped();

            if (literal_query) {
                //literal, treat as non-regex, non-term component query
                highlightQueryEscaped = tcq.getEscapedQueryString();
            } else {
                //construct a Solr query using aggregated terms to get highlighting
                //the query is executed later on demand
                StringBuilder highlightQuery = new StringBuilder();
                Collection<Term> terms = tcq.getTerms();
                final int lastTerm = terms.size() - 1;
                int curTerm = 0;
                for (Term term : terms) {
                    final String termS = KeywordSearchUtil.escapeLuceneQuery(term.getTerm(), true, false);
                    if (!termS.contains("*")) {
                        highlightQuery.append(termS);
                        if (lastTerm != curTerm) {
                            highlightQuery.append(" "); //acts as OR ||
                        }
                    }
                }
                //String highlightQueryEscaped = KeywordSearchUtil.escapeLuceneQuery(highlightQuery.toString());
                highlightQueryEscaped = highlightQuery.toString();
            }


            int resID = 0;
            for (FsContent f : fsContents) {
                //get unique match result files
                Map<String, Object> resMap = new LinkedHashMap<String, Object>();
                AbstractFsContentNode.fillPropertyMap(resMap, f);
                setCommonProperty(resMap, CommonPropertyTypes.MATCH, f.getName());
                if (literal_query) {
                    final String snippet = LuceneQuery.getSnippet(tcq.getQueryString(), f.getId());
                    setCommonProperty(resMap, CommonPropertyTypes.CONTEXT, snippet);
                }
                toPopulate.add(new KeyValueThingContent(f.getName(), resMap, ++resID, f, highlightQueryEscaped));
            }

            return true;
        }

        @Override
        protected Node createNodeForKey(KeyValueThing thing) {
            //return new KeyValueNode(thing, Children.LEAF);
            //return new KeyValueNode(thing, Children.create(new ResultFilesChildFactory(thing), true));
            final KeyValueThingContent thingContent = (KeyValueThingContent) thing;
            final Content content = thingContent.getContent();
            final String query = thingContent.getQuery();

            Node kvNode = new KeyValueNode(thingContent, Children.LEAF, Lookups.singleton(content));
            //wrap in KeywordSearchFilterNode for the markup content, might need to override FilterNode for more customization
            HighlightedMatchesSource highlights = new HighlightedMatchesSource(content, query);
            return new KeywordSearchFilterNode(highlights, kvNode, query);

        }
    }

    /**
     * factory produces top level result nodes showing *exact* regex match result
     */
    class ResulTermsMatchesChildFactory extends ChildFactory<KeyValueThing> {

        Collection<KeyValueThing> things;

        ResulTermsMatchesChildFactory(Collection<KeyValueThing> things) {
            this.things = things;
        }

        @Override
        protected boolean createKeys(List<KeyValueThing> toPopulate) {
            return toPopulate.addAll(things);
        }

        @Override
        protected Node createNodeForKey(KeyValueThing thing) {
            //return new KeyValueNode(thing, Children.LEAF);
            return new KeyValueNode(thing, Children.create(new ResultFilesChildFactory(thing), true));
        }

        /**
         * factory produces 2nd level child nodes showing files with *approximate* matches
         * since they rely on underlying Lucene query to get details
         * To implement exact regex match detail view, we need to extract files content
         * returned by Lucene and further narrow down by applying a Java regex
         */
        class ResultFilesChildFactory extends ChildFactory<KeyValueThing> {

            private KeyValueThing thing;

            ResultFilesChildFactory(KeyValueThing thing) {
                this.thing = thing;
            }

            @Override
            protected boolean createKeys(List<KeyValueThing> toPopulate) {
                //use Lucene query to get files with regular expression match result
                final String keywordQuery = thing.getName();
                LuceneQuery filesQuery = new LuceneQuery(keywordQuery);
                filesQuery.escape();
                List<FsContent> matches = filesQuery.performQuery();

                //get unique match result files
                Set<FsContent> uniqueMatches = new LinkedHashSet<FsContent>();
                uniqueMatches.addAll(matches);

                int resID = 0;
                for (FsContent f : uniqueMatches) {
                    Map<String, Object> resMap = new LinkedHashMap<String, Object>();
                    AbstractFsContentNode.fillPropertyMap(resMap, (File) f);
                    toPopulate.add(new KeyValueThingContent(f.getName(), resMap, ++resID, f, keywordQuery));
                }

                return true;
            }

            @Override
            protected Node createNodeForKey(KeyValueThing thing) {
                final KeyValueThingContent thingContent = (KeyValueThingContent) thing;
                final Content content = thingContent.getContent();
                final String query = thingContent.getQuery();

                final String contentStr = KeywordSearch.getServer().getCore().getSolrContent(content);

                //postprocess
                //make sure Solr result contains a match (this gets rid of large number of false positives)
                final boolean postprocess = false;
                boolean matchFound = true;
                if (postprocess) {
                    if (contentStr != null) {//if not null, some error getting from Solr, handle it by not filtering out
                        //perform java regex to validate match from Solr
                        String origQuery = thingContent.getQuery();

                        //since query is a match result, we can assume literal pattern
                        origQuery = Pattern.quote(origQuery);
                        Pattern p = Pattern.compile(origQuery, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

                        Matcher m = p.matcher(contentStr);
                        matchFound = m.find();
                    }
                }

                if (matchFound) {
                    Node kvNode = new KeyValueNode(thingContent, Children.LEAF, Lookups.singleton(content));
                    //wrap in KeywordSearchFilterNode for the markup content
                    HighlightedMatchesSource highlights = new HighlightedMatchesSource(content, query);
                    return new KeywordSearchFilterNode(highlights, kvNode, query);
                } else {
                    return null;
                }
            }
        }
    }

    /*
     * custom KeyValueThing that also stores retrieved Content and query string used
     */
    private static class KeyValueThingContent extends KeyValueThing {

        private Content content;
        private String query;

        Content getContent() {
            return content;
        }

        String getQuery() {
            return query;
        }

        public KeyValueThingContent(String name, Map<String, Object> map, int id, Content content, String query) {
            super(name, map, id);
            this.content = content;
            this.query = query;
        }
    }
}
