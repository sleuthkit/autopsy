/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import static org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode.AbstractFilePropertyType.LOCATION;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchResultFactory.KeyValueQueryContent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP;
import org.sleuthkit.datamodel.Content;

/**
 * Node factory that performs the keyword search and creates children nodes for
 * each content.
 *
 * Responsible for assembling nodes and columns in the right way and performing
 * lazy queries as needed.
 */
class KeywordSearchResultFactory extends ChildFactory<KeyValueQueryContent> {

    private static final Logger logger = Logger.getLogger(KeywordSearchResultFactory.class.getName());

    //common properties (superset of all Node properties) to be displayed as columns
    static final List<String> COMMON_PROPERTIES
            = Stream.concat(
                    Stream.of(
                            TSK_KEYWORD,
                            TSK_KEYWORD_REGEXP,
                            TSK_KEYWORD_PREVIEW)
                    .map(BlackboardAttribute.ATTRIBUTE_TYPE::getDisplayName),
                    Arrays.stream(AbstractAbstractFileNode.AbstractFilePropertyType.values())
                    .map(Object::toString))
            .collect(Collectors.toList());

    private final Collection<QueryRequest> queryRequests;

    KeywordSearchResultFactory(Collection<QueryRequest> queryRequests) {
        this.queryRequests = queryRequests;
    }

    /**
     * Call this at least for the parent Node, to make sure all common
     * properties are displayed as columns (since we are doing lazy child Node
     * load we need to preinitialize properties when sending parent Node)
     *
     * @param toSet property set map for a Node
     */
    @Override
    protected boolean createKeys(List<KeyValueQueryContent> toPopulate) {

        for (QueryRequest queryRequest : queryRequests) {
            /**
             * Check the validity of the requested query.
             */
            if (!queryRequest.getQuery().validate()) {
                //TODO mark the particular query node RED
                break;
            }

            //JMTODO: It looks like this map is not actually used for anything...
            Map<String, Object> map = queryRequest.getProperties();
            /*
             * make sure all common properties are displayed as columns (since
             * we are doing lazy child Node load we need to preinitialize
             * properties when sending parent Node)
             */
            COMMON_PROPERTIES.stream()
                    .forEach((propertyType) -> map.put(propertyType, ""));
            map.put(TSK_KEYWORD.getDisplayName(), queryRequest.getQueryString());
            map.put(TSK_KEYWORD_REGEXP.getDisplayName(), !queryRequest.getQuery().isLiteral());

            createFlatKeys(queryRequest.getQuery(), toPopulate);
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
    @NbBundle.Messages({"KeywordSearchResultFactory.query.exception.msg=Could not perform the query "})
    private boolean createFlatKeys(KeywordSearchQuery queryRequest, List<KeyValueQueryContent> toPopulate) {

        /**
         * Execute the requested query.
         */
        QueryResults queryResults;
        try {
            queryResults = queryRequest.performQuery();
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            logger.log(Level.SEVERE, "Could not perform the query " + queryRequest.getQueryString(), ex); //NON-NLS
            MessageNotifyUtil.Notify.error(Bundle.KeywordSearchResultFactory_query_exception_msg() + queryRequest.getQueryString(), ex.getCause().getMessage());
            return false;
        }

        int hitNumber = 0;
        List<KeyValueQueryContent> tempList = new ArrayList<>();
        for (KeywordHit hit : getOneHitPerObject(queryResults)) {
            /**
             * Get file properties.
             */
            Map<String, Object> properties = new LinkedHashMap<>();
            Content content = hit.getContent();
            String contentName = content.getName();
            if (content instanceof AbstractFile) {
                AbstractFsContentNode.fillPropertyMap(properties, (AbstractFile) content);
            } else {
                properties.put(LOCATION.toString(), contentName);
            }

            /**
             * Add a snippet property, if available.
             */
            if (hit.hasSnippet()) {
                properties.put(TSK_KEYWORD_PREVIEW.getDisplayName(), hit.getSnippet());
            }

            //JMTODO: I don't understand this comment or the below code... 
            //@@@ USE ConentHit in UniqueFileMap instead of the below search
            //get unique match result files
            // BC: @@@ THis is really ineffecient.  We should keep track of this when
            // we flattened the list of files to the unique files.            
            final String highlightQueryEscaped = getHighlightQuery(queryRequest, queryRequest.isLiteral(), queryResults, content);

            String hitName = hit.isArtifactHit()
                    ? hit.getArtifact().getDisplayName() + " Artifact" //NON-NLS
                    : contentName;

            hitNumber++;
            tempList.add(new KeyValueQueryContent(hitName, properties, hitNumber, hit.getSolrObjectId(), content, highlightQueryEscaped, queryRequest, queryResults));
        }

        // Add all the nodes to toPopulate at once. Minimizes node creation
        // EDT threads, which can slow and/or hang the UI on large queries.
        toPopulate.addAll(tempList);

        //write to bb
        //cannot reuse snippet in BlackboardResultWriter
        //because for regex searches in UI we compress results by showing a content per regex once (even if multiple term hits)
        //whereas in bb we write every hit per content separately
        new BlackboardResultWriter(queryResults, queryRequest.getKeywordList().getName()).execute();

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
        HashMap<Long, KeywordHit> hits = new HashMap<>();
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
        if (literal_query) {
            //literal, treat as non-regex, non-term component query
            return constructEscapedSolrQuery(query.getQueryString(), literal_query);
        } else //construct a Solr query using aggregated terms to get highlighting
        //the query is executed later on demand
        {
            if (queryResults.getKeywords().size() == 1) {
                //simple case, no need to process subqueries and do special escaping
                Keyword keyword = queryResults.getKeywords().iterator().next();
                return constructEscapedSolrQuery(keyword.getSearchTerm(), literal_query);
            } else {
                //find terms for this content hit
                List<Keyword> hitTerms = new ArrayList<>();
                for (Keyword keyword : queryResults.getKeywords()) {
                    for (KeywordHit hit : queryResults.getResults(keyword)) {
                        if (hit.getContent().equals(content)) {
                            hitTerms.add(keyword);
                            break; //go to next term
                        }
                    }
                }

                StringBuilder highlightQuery = new StringBuilder();
                final int lastTerm = hitTerms.size() - 1;
                int curTerm = 0;
                for (Keyword term : hitTerms) {
                    //escape subqueries, MAKE SURE they are not escaped again later
                    highlightQuery.append(constructEscapedSolrQuery(term.getSearchTerm(), literal_query));
                    if (lastTerm != curTerm) {
                        highlightQuery.append(" "); //acts as OR ||
                    }

                    ++curTerm;
                }
                return highlightQuery.toString();
            }
        }
    }

    /**
     * Constructs a complete, escaped Solr query that is ready to be used.
     *
     * @param query         keyword term to be searched for
     * @param literal_query flag whether query is literal or regex
     *
     * @return Solr query string
     */
    private String constructEscapedSolrQuery(String query, boolean literal_query) {
        StringBuilder highlightQuery = new StringBuilder();
        highlightQuery.append(LuceneQuery.HIGHLIGHT_FIELD).append(":").append("\"").append(KeywordSearchUtil.escapeLuceneQuery(query)).append("\"");
        return highlightQuery.toString();
    }

    @Override
    protected Node createNodeForKey(KeyValueQueryContent key) {
        final Content content = key.getContent();
        final String queryStr = key.getQueryStr();
        QueryResults hits = key.getHits();

        Node kvNode = new KeyValueNode(key, Children.LEAF, Lookups.singleton(content));

        //wrap in KeywordSearchFilterNode for the markup content, might need to override FilterNode for more customization
        // store the data in HighlightedMatchesSource so that it can be looked up (in content viewer)
        HighlightedText highlights = new HighlightedText(key.solrObjectId, queryStr, !key.getQuery().isLiteral(), hits);
        return new KeywordSearchFilterNode(highlights, kvNode);
    }

    /**
     * Used to display keyword search results in table. Eventually turned into a
     * node.
     */
    class KeyValueQueryContent extends KeyValue {

        private long solrObjectId;
        private final Content content;
        private final String queryStr;
        private final QueryResults hits;
        private final KeywordSearchQuery query;

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
         * @param hits     Full set of search results (for all files! @@@)
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

        private static final List<BlackboardResultWriter> writers = new ArrayList<>();
        private ProgressHandle progress;
        private final KeywordSearchQuery query;
        private final QueryResults hits;
        private Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();
        private static final int QUERY_DISPLAY_LEN = 40;

        BlackboardResultWriter(QueryResults hits, String listName) {
            this.hits = hits;
            this.query = hits.getQuery();
        }

        protected void finalizeWorker() {
            deregisterWriter(this);
            EventQueue.invokeLater(progress::finish);
        }

        @Override
        protected Object doInBackground() throws Exception {
            registerWriter(this); //register (synchronized on class) outside of writerLock to prevent deadlock
            final String queryStr = query.getQueryString();
            final String queryDisp = queryStr.length() > QUERY_DISPLAY_LEN ? queryStr.substring(0, QUERY_DISPLAY_LEN - 1) + " ..." : queryStr;
            try {
                progress = ProgressHandle.createHandle(NbBundle.getMessage(this.getClass(), "KeywordSearchResultFactory.progress.saving", queryDisp), () -> BlackboardResultWriter.this.cancel(true));
                newArtifacts = hits.writeAllHitsToBlackBoard(progress, null, this, false);
            } finally {
                finalizeWorker();
            }
            return null;
        }

        @Override
        protected void done() {
            try {
                get();
            } catch (InterruptedException | CancellationException ex) {
                logger.log(Level.WARNING, "User cancelled writing of ad hoc search query results for '{0}' to the blackboard", query.getQueryString()); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, "Error writing of ad hoc search query results for " + query.getQueryString() + " to the blackboard", ex); //NON-NLS
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
