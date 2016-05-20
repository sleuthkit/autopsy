/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this content except in compliance with the License.
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

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchResultFactory.KeyValueQueryContent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;

/**
 * Node factory that performs the keyword search and creates children nodes for
 * each content.
 *
 * Responsible for assembling nodes and columns in the right way and performing
 * lazy queries as needed.
 */
class KeywordSearchResultFactory extends ChildFactory<KeyValueQueryContent> {

    //common properties (superset of all Node properties) to be displayed as columns
    //these are merged with FsContentPropertyType defined properties
    public static enum CommonPropertyTypes {

        KEYWORD {
            @Override
            public String toString() {
                return BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getDisplayName();
            }
        },
        REGEX {
            @Override
            public String toString() {
                return BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getDisplayName();
            }
        },
        CONTEXT {
            @Override
            public String toString() {
                return BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getDisplayName();
            }
        },
    }
    private Collection<QueryRequest> queryRequests;
    private final DataResultTopComponent viewer; //viewer driving this child node factory
    private static final Logger logger = Logger.getLogger(KeywordSearchResultFactory.class.getName());

    KeywordSearchResultFactory(Collection<QueryRequest> queryRequests, DataResultTopComponent viewer) {
        this.queryRequests = queryRequests;
        this.viewer = viewer;
    }

    /**
     * call this at least for the parent Node, to make sure all common
     * properties are displayed as columns (since we are doing lazy child Node
     * load we need to preinitialize properties when sending parent Node)
     *
     * @param toSet property set map for a Node
     */
    public static void initCommonProperties(Map<String, Object> toSet) {
        CommonPropertyTypes[] commonTypes = CommonPropertyTypes.values();
        final int COMMON_PROPS_LEN = commonTypes.length;
        for (int i = 0; i < COMMON_PROPS_LEN; ++i) {
            toSet.put(commonTypes[i].toString(), "");
        }

        AbstractAbstractFileNode.AbstractFilePropertyType[] fsTypes = AbstractAbstractFileNode.AbstractFilePropertyType.values();
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
    protected boolean createKeys(List<KeyValueQueryContent> toPopulate) {

        for (QueryRequest queryRequest : queryRequests) {
            Map<String, Object> map = queryRequest.getProperties();
            initCommonProperties(map);
            final String query = queryRequest.getQueryString();
            setCommonProperty(map, CommonPropertyTypes.KEYWORD, query);
            setCommonProperty(map, CommonPropertyTypes.REGEX, !queryRequest.getQuery().isLiteral());
            createFlatKeys(queryRequest, toPopulate);
        }

        return true;
    }

    /**
     *
     * @param queryRequest
     * @param toPopulate
     *
     * @return
     */
    private boolean createFlatKeys(QueryRequest queryRequest, List<KeyValueQueryContent> toPopulate) {
        /**
         * Check the validity of the requested query.
         */
        final KeywordSearchQuery keywordSearchQuery = queryRequest.getQuery();
        if (!keywordSearchQuery.validate()) {
            //TODO mark the particular query node RED
            return false;
        }

        /**
         * Execute the requested query.
         */
        QueryResults queryResults;
        try {
            queryResults = keywordSearchQuery.performQuery();
        } catch (NoOpenCoreException | KeywordSearchSettingsManager.KeywordSearchSettingsManagerException ex) {
            logger.log(Level.SEVERE, "Could not perform the query " + keywordSearchQuery.getQueryString(), ex); //NON-NLS
            return false;
        }
        //NON-NLS

        int id = 0;
        List<KeyValueQueryContent> tempList = new ArrayList<>();
        for (KeywordHit hit : getOneHitPerObject(queryResults)) {
            /**
             * Get file properties.
             */
            Map<String, Object> properties = new LinkedHashMap<>();
            Content content = hit.getContent();
            if (content instanceof AbstractFile) {
                AbstractFsContentNode.fillPropertyMap(properties, (AbstractFile) content);
            } else {
                properties.put(AbstractAbstractFileNode.AbstractFilePropertyType.LOCATION.toString(), content.getName());
            }

            /**
             * Add a snippet property, if available.
             */
            if (hit.hasSnippet()) {
                setCommonProperty(properties, CommonPropertyTypes.CONTEXT, hit.getSnippet());
            }

            //@@@ USE ConentHit in UniqueFileMap instead of the below search
            //get unique match result files
            // BC: @@@ THis is really ineffecient.  We should keep track of this when
            // we flattened the list of files to the unique files.            
            final String highlightQueryEscaped = getHighlightQuery(keywordSearchQuery, keywordSearchQuery.isLiteral(), queryResults, content);

            String name = content.getName();
            if (hit.isArtifactHit()) {
                name = hit.getArtifact().getDisplayName() + " Artifact"; // NON-NLS
            }
            tempList.add(new KeyValueQueryContent(name, properties, ++id, hit.getSolrObjectId(), content, highlightQueryEscaped, keywordSearchQuery, queryResults));
        }

        // Add all the nodes to toPopulate at once. Minimizes node creation
        // EDT threads, which can slow and/or hang the UI on large queries.
        toPopulate.addAll(tempList);

        //write to bb
        //cannot reuse snippet in BlackboardResultWriter
        //because for regex searches in UI we compress results by showing a content per regex once (even if multiple term hits)
        //whereas in bb we write every hit per content separately
        new BlackboardResultWriter(queryResults, queryRequest.getQuery().getKeywordList().getName()).execute();

        return true;
    }

    /**
     * This method returns a collection of KeywordHits with lowest SolrObjectID-
     * Chunk-ID combination. The output generated is consistent across multiple
     * runs.
     *
     * @param queryResults QueryResult object
     *
     * @return A consistent collection of keyword hits
     */
    Collection<KeywordHit> getOneHitPerObject(QueryResults queryResults) {
        HashMap<Long, KeywordHit> hits = new HashMap<Long, KeywordHit>();
        for (Keyword keyWord : queryResults.getKeywords()) {
            for (KeywordHit hit : queryResults.getResults(keyWord)) {
                // add hit with lowest SolrObjectID-Chunk-ID combination.
                if (!hits.containsKey(hit.getSolrObjectId())) {
                    hits.put(hit.getSolrObjectId(), hit);
                } else if (hit.getChunkId() < hits.get(hit.getSolrObjectId()).getChunkId()) {
                    hits.put(hit.getSolrObjectId(), hit);
                }
            }
        }
        return hits.values();
    }

    /**
     * Return the string used to later have SOLR highlight the document with.
     *
     * @param query
     * @param literal_query
     * @param queryResults
     * @param file
     *
     * @return
     */
    private String getHighlightQuery(KeywordSearchQuery query, boolean literal_query, QueryResults queryResults, Content content) {
        String highlightQueryEscaped;
        if (literal_query) {
            //literal, treat as non-regex, non-term component query
            highlightQueryEscaped = query.getQueryString();
        } else {
            //construct a Solr query using aggregated terms to get highlighting
            //the query is executed later on demand
            StringBuilder highlightQuery = new StringBuilder();

            if (queryResults.getKeywords().size() == 1) {
                //simple case, no need to process subqueries and do special escaping
                Keyword term = queryResults.getKeywords().iterator().next();
                highlightQuery.append(term.toString());
            } else {
                //find terms for this content hit
                List<String> hitTerms = new ArrayList<>();
                for (Keyword keyword : queryResults.getKeywords()) {
                    for (KeywordHit hit : queryResults.getResults(keyword)) {
                        if (hit.getContent().equals(content)) {
                            hitTerms.add(keyword.toString());
                            break; //go to next term
                        }
                    }
                }

                final int lastTerm = hitTerms.size() - 1;
                int curTerm = 0;
                for (String term : hitTerms) {
                    //escape subqueries, they shouldn't be escaped again later
                    final String termS = KeywordSearchUtil.escapeLuceneQuery(term);
                    highlightQuery.append("\"");
                    highlightQuery.append(termS);
                    highlightQuery.append("\"");
                    if (lastTerm != curTerm) {
                        highlightQuery.append(" "); //acts as OR ||
                        //force HIGHLIGHT_FIELD_REGEX index and stored content
                        //in each term after first. First term taken care by HighlightedMatchesSource
                        highlightQuery.append(LuceneQuery.HIGHLIGHT_FIELD_REGEX).append(":");
                    }

                    ++curTerm;
                }
            }
            //String highlightQueryEscaped = KeywordSearchUtil.escapeLuceneQuery(highlightQuery.toString());
            highlightQueryEscaped = highlightQuery.toString();
        }

        return highlightQueryEscaped;
    }

    @Override
    protected Node createNodeForKey(KeyValueQueryContent key) {
        final Content content = key.getContent();
        final String queryStr = key.getQueryStr();;
        QueryResults hits = key.getHits();

        Node kvNode = new KeyValueNode(key, Children.LEAF, Lookups.singleton(content));

        //wrap in KeywordSearchFilterNode for the markup content, might need to override FilterNode for more customization
        // store the data in HighlightedMatchesSource so that it can be looked up (in content viewer)
        HighlightedText highlights = new HighlightedText(key.solrObjectId, queryStr, !key.getQuery().isLiteral(), false, hits);
        return new KeywordSearchFilterNode(highlights, kvNode);
    }

    /**
     * Used to display keyword search results in table. Eventually turned into a
     * node.
     */
    class KeyValueQueryContent extends KeyValue {

        private long solrObjectId;
        private Content content;
        private String queryStr;
        private QueryResults hits;
        private KeywordSearchQuery query;

        /**
         * NOTE Parameters are defined based on how they are currently used in
         * practice
         *
         * @param name     File name that has hit.
         * @param map      Contains content metadata, snippets, etc. (property
         *                 map)
         * @param id       User incremented ID
         * @param content  File that had the hit.
         * @param queryStr Query used in search
         * @param query    Query used in search
         * @param hits     Full set of search results (for all files!
         *
         * @@@)
         */
        public KeyValueQueryContent(String name, Map<String, Object> map, int id, long solrObjectId, Content content, String queryStr, KeywordSearchQuery query, QueryResults hits) {
            super(name, map, id);
            this.solrObjectId = solrObjectId;
            this.content = content;
            this.queryStr = queryStr;
            this.hits = hits;
            this.query = query;
        }

        Content getContent() {
            return content;
        }

        String getQueryStr() {
            return queryStr;
        }

        QueryResults getHits() {
            return hits;
        }

        KeywordSearchQuery getQuery() {
            return query;
        }
    }

    /**
     * worker for writing results to bb, with progress bar, cancellation, and
     * central registry of workers to be stopped when case is closed
     */
    static class BlackboardResultWriter extends SwingWorker<Object, Void> {

        private static List<BlackboardResultWriter> writers = new ArrayList<>();
        //lock utilized to enqueue writers and limit execution to 1 at a time
        private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy
        //private static final Lock writerLock = rwLock.writeLock();
        private ProgressHandle progress;
        private KeywordSearchQuery query;
        private String listName;
        private QueryResults hits;
        private Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();
        private static final int QUERY_DISPLAY_LEN = 40;

        BlackboardResultWriter(QueryResults hits, String listName) {
            this.hits = hits;
            this.query = hits.getQuery();
            this.listName = listName;
        }

        protected void finalizeWorker() {
            deregisterWriter(this);

            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progress.finish();
                }
            });
        }

        @Override
        protected Object doInBackground() throws Exception {
            registerWriter(this); //register (synchronized on class) outside of writerLock to prevent deadlock
            final String queryStr = query.getQueryString();
            final String queryDisp = queryStr.length() > QUERY_DISPLAY_LEN ? queryStr.substring(0, QUERY_DISPLAY_LEN - 1) + " ..." : queryStr;
            //block until previous writer is done
            //writerLock.lock();

            try {
                progress = ProgressHandle.createHandle(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchResultFactory.progress.saving", queryDisp), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        return BlackboardResultWriter.this.cancel(true);
                    }
                });

                // Create blackboard artifacts
                newArtifacts = hits.writeAllHitsToBlackBoard(progress, null, this, false);
            } finally {
                finalizeWorker();
            }

            return null;
        }

        @Override
        protected void done() {
            try {
                // test if any exceptions were thrown
                get();
            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.SEVERE, "Error querying ", ex); //NON-NLS
            }
        }

        private static synchronized void registerWriter(BlackboardResultWriter writer) {
            writers.add(writer);
        }

        private static synchronized void deregisterWriter(BlackboardResultWriter writer) {
            writers.remove(writer);
        }

        static synchronized void stopAllWriters() {
            for (BlackboardResultWriter w : writers) {
                w.cancel(true);
                writers.remove(w);
            }
        }
    }
}
