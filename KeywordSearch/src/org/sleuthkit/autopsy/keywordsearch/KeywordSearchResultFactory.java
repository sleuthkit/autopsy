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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Cancellable;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.datamodel.AbstractAbstractFileNode;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
import org.sleuthkit.autopsy.datamodel.KeyValue;
import org.sleuthkit.autopsy.datamodel.KeyValueNode;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.keywordsearch.KeywordSearchResultFactory.KeyValueQueryContent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskData.TSK_DB_FILES_TYPE_ENUM;

/**
 * Node factory that performs the keyword search and creates children nodes for
 * each file.
 *
 *
 * responsible for assembling nodes and columns in the right way and performing
 * lazy queries as needed
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
    private List<Keyword> queries;
    private Collection<QueryRequest> queryRequests;
    private final DataResultTopComponent viewer; //viewer driving this child node factory
    private static final Logger logger = Logger.getLogger(KeywordSearchResultFactory.class.getName());

    KeywordSearchResultFactory(List<Keyword> queries, Collection<QueryRequest> queryRequests, DataResultTopComponent viewer) {
        this.queries = queries;
        this.queryRequests = queryRequests;
        this.viewer = viewer;
    }

    KeywordSearchResultFactory(Keyword query, Collection<QueryRequest> queryRequests, DataResultTopComponent viewer) {
        queries = new ArrayList<>();
        queries.add(query);
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
            setCommonProperty(map, CommonPropertyTypes.REGEX, Boolean.valueOf(!queryRequest.getQuery().isLiteral()));
            createFlatKeys(queryRequest, toPopulate);
        }

        return true;
    }

    

    /**
     *
     * @param queryRequest
     * @param toPopulate
     * @return
     */
    protected boolean createFlatKeys(QueryRequest queryRequest, List<KeyValueQueryContent> toPopulate) {
        final KeywordSearchQuery keywordSearchQuery = queryRequest.getQuery();

        if (!keywordSearchQuery.validate()) {
            //TODO mark the particular query node RED
            return false;
        }

        //execute the query and get fscontents matching
        QueryResults queryResults;
        try {
            queryResults = keywordSearchQuery.performQuery();
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Could not perform the query. ", ex); //NON-NLS
            return false;
        }

        //get listname
        String listName = "";
        KeywordList list = KeywordSearchListsXML.getCurrent().getListWithKeyword(keywordSearchQuery.getQueryString());
        if (list != null) {
            listName = list.getName();
        }

        final boolean literal_query = keywordSearchQuery.isLiteral();

        int resID = 0;

        List<KeyValueQueryContent> tempList = new ArrayList<>();
        final Map<AbstractFile, ContentHit> uniqueFileMap = queryResults.getUniqueFiles();
        for (final AbstractFile f : uniqueFileMap.keySet()) {

            //@@@ USE ConentHit in UniqueFileMap instead of the below search
            //get unique match result files
            Map<String, Object> resMap = new LinkedHashMap<>();

            
            // BC: @@@ THis is really ineffecient.  We should keep track of this when
            // we flattened the list of files to the unique files.

            /* Find a keyword in that file so that we can generate a
             * single snippet for it. 
             */
            
            ContentHit chit = uniqueFileMap.get(f);
            if (chit.hasSnippet()) {
                setCommonProperty(resMap, CommonPropertyTypes.CONTEXT, chit.getSnippet());
            }
            
//            boolean hitFound = false;
//            for (String hitKey : queryResults.getKeywords()) {
//                for (ContentHit contentHit : queryResults.getResults(hitKey)) {
//                    if (contentHit.getContent().equals(f)) {
//                        hitFound = true;
//                        if (contentHit.hasSnippet() && (KeywordSearchUtil.escapeLuceneQuery(hitKey) != null)) {
//                            setCommonProperty(resMap, CommonPropertyTypes.CONTEXT, contentHit.getSnippet());
//                        }
//                        break;
//                    }
//                }
//                if (hitFound) {
//                    break;
//                }
//            }
            if (f.getType() == TSK_DB_FILES_TYPE_ENUM.FS) {
                AbstractFsContentNode.fillPropertyMap(resMap, (FsContent) f);
            }
            final String highlightQueryEscaped = getHighlightQuery(keywordSearchQuery, literal_query, queryResults, f);
            tempList.add(new KeyValueQueryContent(f.getName(), resMap, ++resID, f, highlightQueryEscaped, keywordSearchQuery, queryResults));
        }

        // Add all the nodes to toPopulate at once. Minimizes node creation
        // EDT threads, which can slow and/or hang the UI on large queries.
        toPopulate.addAll(tempList);

        //write to bb
        //cannot reuse snippet in BlackboardResultWriter
        //because for regex searches in UI we compress results by showing a file per regex once (even if multiple term hits)
        //whereas in bb we write every hit per file separately
        new BlackboardResultWriter(queryResults, keywordSearchQuery, listName).execute();

        return true;
    }

    /**
     * Return the string used to later have SOLR highlight the document with.
     *
     * @param query
     * @param literal_query
     * @param queryResults
     * @param f
     * @return
     */
    private String getHighlightQuery(KeywordSearchQuery query, boolean literal_query, QueryResults queryResults, AbstractFile f) {
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
                String term = queryResults.getKeywords().iterator().next();
                highlightQuery.append(term);
            } else {
                //find terms for this file hit
                List<String> hitTerms = new ArrayList<>();
                for (String term : queryResults.getKeywords()) {
                    List<ContentHit> hitList = queryResults.getResults(term);

                    for (ContentHit h : hitList) {
                        if (h.getContent().equals(f)) {
                            hitTerms.add(term);
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
        HighlightedMatchesSource highlights = new HighlightedMatchesSource(content, queryStr, !key.getQuery().isLiteral(), false, hits);
        return new KeywordSearchFilterNode(highlights, kvNode);
    }

    /**
     * Used to display keyword search results in table. Eventually turned into a
     * node.
     */
    class KeyValueQueryContent extends KeyValue {

        private Content content;
        private String queryStr;
        private QueryResults hits;
        private KeywordSearchQuery query;

        
        /**
         * NOTE Parameters are defined based on how they are currently used in
         * practice
         *
         * @param name File name that has hit.
         * @param map Contains file metadata, snippets, etc. (property map)
         * @param id User incremented ID
         * @param content File that had the hit.
         * @param queryStr Query used in search
         * @param query Query used in search
         * @param hits Full set of search results (for all files!
         * @@@)
         */
        public KeyValueQueryContent(String name, Map<String, Object> map, int id, Content content, String queryStr, KeywordSearchQuery query, QueryResults hits) {
            super(name, map, id);
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
        final Collection<BlackboardArtifact> na = new ArrayList<>();
        private static final int QUERY_DISPLAY_LEN = 40;

        BlackboardResultWriter(QueryResults hits, KeywordSearchQuery query, String listName) {
            this.hits = hits;
            this.query = query;
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

            if (!this.isCancelled() && !na.isEmpty()) {
                IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(KeywordSearchModuleFactory.getModuleName(), ARTIFACT_TYPE.TSK_KEYWORD_HIT, na));
            }
        }

        @Override
        protected Object doInBackground() throws Exception {
            registerWriter(this); //register (synchronized on class) outside of writerLock to prevent deadlock

            //block until previous writer is done
            //writerLock.lock();
            try {
                final String queryStr = query.getQueryString();
                final String queryDisp = queryStr.length() > QUERY_DISPLAY_LEN ? queryStr.substring(0, QUERY_DISPLAY_LEN - 1) + " ..." : queryStr;
                progress = ProgressHandleFactory.createHandle(
                        NbBundle.getMessage(this.getClass(), "KeywordSearchResultFactory.progress.saving", queryDisp), new Cancellable() {
                    @Override
                    public boolean cancel() {
                        return BlackboardResultWriter.this.cancel(true);
                    }
                });

                progress.start(hits.getKeywords().size());
                int processedFiles = 0;
                for (final String hit : hits.getKeywords()) {
                    progress.progress(hit, ++processedFiles);
                    if (this.isCancelled()) {
                        break;
                    }
                    Map<AbstractFile, Integer> flattened = hits.getUniqueFiles(hit);
                    for (AbstractFile f : flattened.keySet()) {
                        int chunkId = flattened.get(f);
                        final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(hit);
                        String snippet;
                        try {
                            snippet = LuceneQuery.querySnippet(snippetQuery, f.getId(), chunkId, !query.isLiteral(), true);
                        } catch (NoOpenCoreException e) {
                            logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                            //no reason to continie
                            return null;
                        } catch (Exception e) {
                            logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e); //NON-NLS
                            continue;
                        }
                        if (snippet != null) {
                            KeywordWriteResult written = query.writeToBlackBoard(hit, f, snippet, listName);
                            if (written != null) {
                                na.add(written.getArtifact());
                            }
                        }
                    }
                }
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
