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

import java.io.IOException;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.datamodel.FsContentStringStream;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.autopsy.ingest.ServiceDataEvent;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskData;

//service provider registered in layer.xml
public final class KeywordSearchIngestService implements IngestServiceFsContent {

    private static final Logger logger = Logger.getLogger(KeywordSearchIngestService.class.getName());
    public static final String MODULE_NAME = "Keyword Search";
    public static final String MODULE_DESCRIPTION = "Performs file indexing and periodic search using keywords and regular expressions in lists.";
    private static KeywordSearchIngestService instance = null;
    private IngestManagerProxy managerProxy;
    private static final long MAX_STRING_CHUNK_SIZE = 1 * (1 << 10) * (1 << 10);
    private static final long MAX_INDEX_SIZE = 100 * (1 << 10) * (1 << 10);
    private Ingester ingester = null;
    private volatile boolean commitIndex = false; //whether to commit index next time
    private List<Keyword> keywords; //keywords to search
    private List<String> keywordLists; // lists currently being searched
    private Map<String, String> keywordToList; //keyword to list name mapping
    //private final Object lock = new Object();
    private Timer commitTimer;
    private Indexer indexer;
    private Searcher searcher;
    private volatile boolean searcherDone = true;
    private Map<Keyword, List<ContentHit>> currentResults;
    private volatile int messageID = 0;
    private boolean processedFiles;
    private volatile boolean finalRun = false;
    private volatile boolean finalRunComplete = false;
    private final String hashDBServiceName = "Hash Lookup";
    private SleuthkitCase caseHandle = null;
    boolean initialized = false;
    private final byte[] STRING_CHUNK_BUF = new byte[(int) MAX_STRING_CHUNK_SIZE];

    public enum IngestStatus {

        INGESTED, EXTRACTED_INGESTED, SKIPPED,};
    private Map<Long, IngestStatus> ingestStatus;

    public static synchronized KeywordSearchIngestService getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestService();
        }
        return instance;
    }

    @Override
    public ProcessResult process(FsContent fsContent) {

        if (initialized == false) //error initializing indexing/Solr
        {
            return ProcessResult.OK;
        }

        //check if we should skip this file according to HashDb service
        //if so do not index it, also postpone indexing and keyword search threads to later
        IngestServiceFsContent.ProcessResult hashDBResult = managerProxy.getFsContentServiceResult(hashDBServiceName);
        //logger.log(Level.INFO, "hashdb result: " + hashDBResult + "file: " + fsContent.getName());
        if (hashDBResult == IngestServiceFsContent.ProcessResult.COND_STOP) {
            return ProcessResult.OK;
        } else if (hashDBResult == IngestServiceFsContent.ProcessResult.ERROR) {
            //notify depending service that keyword search (would) encountered error for this file
            return ProcessResult.ERROR;
        }

        if (processedFiles == false) {
            processedFiles = true;
        }

        //check if time to commit and previous search is not running
        //commiting while searching causes performance issues
        if (commitIndex && searcherDone) {
            logger.log(Level.INFO, "Commiting index");
            commit();
            commitIndex = false;
            indexChangeNotify();

            updateKeywords();
            //start search if previous not running
            if (keywords != null && !keywords.isEmpty() && searcherDone) {
                searcher = new Searcher(keywords);
                searcher.execute();
            }
        }

        indexer.indexFile(fsContent);
        return ProcessResult.OK;

    }

    @Override
    public void complete() {
        if (initialized == false) {
            return;
        }

        //logger.log(Level.INFO, "complete()");
        commitTimer.stop();

        //handle case if previous search running
        //cancel it, will re-run after final commit
        //note: cancellation of Searcher worker is graceful (between keywords)
        if (searcher != null) {
            searcher.cancel(true);
        }

        //final commit
        commit();

        //signal a potential change in number of indexed files
        indexChangeNotify();

        postIndexSummary();

        updateKeywords();
        //run one last search as there are probably some new files committed
        if (keywords != null && !keywords.isEmpty() && processedFiles == true) {
            finalRun = true;
            searcher = new Searcher(keywords);
            searcher.execute();
        } else {
            finalRunComplete = true;
            managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Completed"));
        }

        //postSummary();
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //stop timer
        commitTimer.stop();
        //stop searcher
        if (searcher != null) {
            searcher.cancel(true);
        }

        //commit uncommited files, don't search again
        commit();

        indexChangeNotify();
        //postSummary();
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @Override
    public String getDescription() {
        return MODULE_DESCRIPTION;
    }

    @Override
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init()");
        initialized = false;

        caseHandle = Case.getCurrentCase().getSleuthkitCase();

        this.managerProxy = managerProxy;

        Server solrServer = KeywordSearch.getServer();


        ingester = solrServer.getIngester();

        ingestStatus = new HashMap<Long, IngestStatus>();

        keywords = new ArrayList<Keyword>();
        keywordLists = new ArrayList<String>();
        keywordToList = new HashMap<String, String>();

        initKeywords();

        if (keywords.isEmpty() || keywordLists.isEmpty()) {
            managerProxy.postMessage(IngestMessage.createWarningMessage(++messageID, instance, "No keywords in keyword list.", "Only indexing will be done and and keyword search will be skipped (it can be executed later again as ingest or using toolbar search feature)."));
        }

        processedFiles = false;
        finalRun = false;
        finalRunComplete = false;
        searcherDone = true; //make sure to start the initial searcher
        //keeps track of all results per run not to repeat reporting the same hits
        currentResults = new HashMap<Keyword, List<ContentHit>>();

        indexer = new Indexer();

        final int commitIntervalMs = managerProxy.getUpdateFrequency() * 60 * 1000;
        logger.log(Level.INFO, "Using refresh interval (ms): " + commitIntervalMs);

        commitTimer = new Timer(commitIntervalMs, new CommitTimerAction());

        initialized = true;

        commitTimer.start();

        managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Started"));
    }

    @Override
    public ServiceType getType() {
        return ServiceType.FsContent;
    }

    @Override
    public boolean hasSimpleConfiguration() {
        return true;
    }

    @Override
    public boolean hasAdvancedConfiguration() {
        return true;
    }

    @Override
    public javax.swing.JPanel getSimpleConfiguration() {
        return new KeywordSearchIngestSimplePanel();
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        return KeywordSearchConfigurationPanel.getDefault();
    }

    @Override
    public void saveAdvancedConfiguration() {
        KeywordSearchConfigurationPanel.getDefault().editListPanel.save();
    }

    @Override
    public void saveSimpleConfiguration() {
    }

    @Override
    public boolean hasBackgroundJobsRunning() {
        if (searcher != null && searcherDone == false) {
            return true;
        } else {
            return false;
        }

        //no need to check timer thread

    }

    private void commit() {
        if (initialized) {
            ingester.commit();
        }
    }

    private void postIndexSummary() {
        int indexed = 0;
        int indexed_extr = 0;
        int skipped = 0;
        for (IngestStatus s : ingestStatus.values()) {
            switch (s) {
                case INGESTED:
                    ++indexed;
                    break;
                case EXTRACTED_INGESTED:
                    ++indexed_extr;
                    break;
                case SKIPPED:
                    ++skipped;
                    break;
                default:
                    ;
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("Indexed files: ").append(indexed).append("<br />Indexed strings: ").append(indexed_extr);
        msg.append("<br />Skipped files: ").append(skipped).append("<br />");

        managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Keyword Indexing Completed", msg.toString()));

    }

    private void indexChangeNotify() {
        //signal a potential change in number of indexed files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            KeywordSearch.changeSupport.firePropertyChange(KeywordSearch.NUM_FILES_CHANGE_EVT, null, new Integer(numIndexedFiles));
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files: ", ex);
        } catch (SolrServerException se) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files: ", se);
        }
    }

    /**
     * Initialize the keyword search lists from the XML loader
     */
    private void initKeywords() {
        KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

        keywords.clear();
        keywordLists.clear();
        keywordToList.clear();

        for (KeywordSearchList list : loader.getListsL()) {
            String listName = list.getName();
            if (list.getUseForIngest()) {
                keywordLists.add(listName);
            }
            for (Keyword keyword : list.getKeywords()) {
                keywords.add(keyword);
                keywordToList.put(keyword.getQuery(), listName);
            }

        }
    }

    /**
     * Retrieve the updated keyword search lists from the XML loader
     */
    private void updateKeywords() {
        KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

        keywords.clear();
        keywordToList.clear();

        for (String name : keywordLists) {
            for (Keyword k : loader.getList(name).getKeywords()) {
                keywords.add(k);
                keywordToList.put(k.getQuery(), name);
            }
        }
    }

    List<String> getKeywordLists() {
        return keywordLists == null ? new ArrayList<String>() : keywordLists;
    }

    void addToKeywordLists(String name) {
        if (!keywordLists.contains(name)) {
            keywordLists.add(name);
        }
    }

    //CommitTimerAction to run by commitTimer
    //sets a flag for indexer to commit after indexing next file
    private class CommitTimerAction implements ActionListener {

        private final Logger logger = Logger.getLogger(CommitTimerAction.class.getName());

        @Override
        public void actionPerformed(ActionEvent e) {
            commitIndex = true;
            logger.log(Level.INFO, "CommitTimer awake");
        }
    }

    //Indexer thread that processes files in the queue
    //commits when timer expires
    //sleeps if nothing in the queue
    private class Indexer {

        private final Logger logger = Logger.getLogger(Indexer.class.getName());
        private static final String DELETED_MSG = "The file is an unallocated or orphan file (deleted) and entire content is no longer recoverable. ";

        private boolean extractAndIngest(File file) {
            boolean indexed = false;
            FileExtract fe = new FileExtract(file);
            try {
                indexed = fe.index(ingester);
            } catch (IngesterException ex) {
                logger.log(Level.WARNING, "Error extracting strings and indexing file: " + file.getName(), ex);
                indexed = false;
            }

            return indexed;

        }

        private void indexFile(FsContent fsContent) {
            final long size = fsContent.getSize();
            //logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
            if (!fsContent.isFile()) {
                return;
            }
            File file = (File) fsContent;

            boolean ingestible = Ingester.isIngestible(file);

            //limit size of entire file, do not limit strings
            if (size == 0 || (ingestible && size > MAX_INDEX_SIZE)) {
                ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                return;
            }


            final String fileName = file.getName();

            String deletedMessage = "";
            if ((file.getMeta_flags() & (TskData.TSK_FS_META_FLAG_ENUM.ORPHAN.getMetaFlag() | TskData.TSK_FS_META_FLAG_ENUM.UNALLOC.getMetaFlag())) != 0) {
                deletedMessage = DELETED_MSG;
            }

            if (ingestible == true) {

                try {
                    //logger.log(Level.INFO, "indexing: " + fsContent.getName());
                    ingester.ingest(file);
                    ingestStatus.put(file.getId(), IngestStatus.INGESTED);
                } catch (IngesterException e) {
                    ingestStatus.put(file.getId(), IngestStatus.SKIPPED);
                    //try to extract strings
                    boolean processed = processNonIngestible(file);
                    //postIngestibleErrorMessage(processed, fileName, deletedMessage);

                } catch (Exception e) {
                    ingestStatus.put(file.getId(), IngestStatus.SKIPPED);
                    //try to extract strings
                    boolean processed = processNonIngestible(file);

                    //postIngestibleErrorMessage(processed, fileName, deletedMessage);

                }
            } else {
                boolean processed = processNonIngestible(file);
                //postNonIngestibleErrorMessage(processed, fsContent, deletedMessage);

            }
        }

        private void postNonIngestibleErrorMessage(boolean stringsExtracted, File file, String deletedMessage) {
            String fileName = file.getName();
            if (!stringsExtracted) {
                managerProxy.postMessage(IngestMessage.createMessage(++messageID, IngestMessage.MessageType.INFO, KeywordSearchIngestService.instance, "Skipped indexing strings: " + fileName, "Skipped extracting string content from this file (of unsupported format) due to the file size.  The file will not be included in the search results.<br />File: " + fileName));
            }

        }

        private void postIngestibleErrorMessage(boolean stringsExtracted, String fileName, String deletedMessage) {
            if (stringsExtracted) {
                managerProxy.postMessage(IngestMessage.createWarningMessage(++messageID, KeywordSearchIngestService.instance, "Indexed strings only: " + fileName, "Error encountered extracting file content. " + deletedMessage + "Used string extraction to index strings for partial analysis on this file.<br />File: " + fileName));
            } else {
                managerProxy.postMessage(IngestMessage.createErrorMessage(++messageID, KeywordSearchIngestService.instance, "Error indexing: " + fileName, "Error encountered extracting file content and strings from this file. " + deletedMessage + "The file will not be included in the search results.<br />File: " + fileName));
            }
        }

        private boolean processNonIngestible(File file) {
            if (!extractAndIngest(file)) {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + file.getName() + "' (id: " + file.getId() + ").");
                ingestStatus.put(file.getId(), IngestStatus.SKIPPED);
                return false;
            } else {
                ingestStatus.put(file.getId(), IngestStatus.EXTRACTED_INGESTED);
                return true;
            }

        }
    }

    private class Searcher extends SwingWorker<Object, Void> {

        private List<Keyword> keywords;
        private ProgressHandle progress;
        private final Logger logger = Logger.getLogger(Searcher.class.getName());

        Searcher(List<Keyword> keywords) {
            this.keywords = keywords;
        }

        @Override
        protected Object doInBackground() throws Exception {
            //make sure other searchers are not spawned 
            //slight chance if interals are tight or data sets are large
            //(would still work, but for performance reasons)
            searcherDone = false;
            //logger.log(Level.INFO, "Starting search");

            progress = ProgressHandleFactory.createHandle("Keyword Search", new Cancellable() {

                @Override
                public boolean cancel() {
                    finalRunComplete = true;
                    return Searcher.this.cancel(true);
                }
            });

            progress.start(keywords.size());
            int numSearched = 0;

            for (Keyword keywordQuery : keywords) {
                if (this.isCancelled()) {
                    return null;
                }
                final String queryStr = keywordQuery.getQuery();
                final String listName = keywordToList.get(queryStr);

                //logger.log(Level.INFO, "Searching: " + queryStr);

                progress.progress(queryStr, numSearched);

                KeywordSearchQuery del = null;

                boolean isRegex = !keywordQuery.isLiteral();
                if (!isRegex) {
                    del = new LuceneQuery(keywordQuery);
                    del.escape();
                } else {
                    del = new TermComponentQuery(keywordQuery);
                }

                Map<String, List<ContentHit>> queryResult = null;

                try {
                    queryResult = del.performQuery();
                } catch (NoOpenCoreException ex) {
                    logger.log(Level.WARNING, "Error performing query: " + keywordQuery.getQuery(), ex);
                    //no reason to continue with next query if recovery failed
                    //or wait for recovery to kick in and run again later
                    //likely case has closed and threads are being interrupted
                    return null;
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error performing query: " + keywordQuery.getQuery(), e);
                    continue;
                }

                //calculate new results but substracting results already obtained in this run
                Map<Keyword, List<ContentHit>> newResults = new HashMap<Keyword, List<ContentHit>>();

                for (String termResult : queryResult.keySet()) {
                    List<ContentHit> queryTermResults = queryResult.get(termResult);
                    Keyword termResultK = new Keyword(termResult, !isRegex);
                    List<ContentHit> curTermResults = currentResults.get(termResultK);
                    if (curTermResults == null) {
                        currentResults.put(termResultK, queryTermResults);
                        newResults.put(termResultK, queryTermResults);
                    } else {
                        //some fscontent hits already exist for this keyword
                        for (ContentHit res : queryTermResults) {
                            if (! previouslyHit(curTermResults, res)) {
                                //add to new results
                                List<ContentHit> newResultsFs = newResults.get(termResultK);
                                if (newResultsFs == null) {
                                    newResultsFs = new ArrayList<ContentHit>();
                                    newResults.put(termResultK, newResultsFs);
                                }
                                newResultsFs.add(res);
                                curTermResults.add(res);
                            }
                        }
                    }

                }


                if (!newResults.isEmpty()) {
                    
                    //write results to BB
                    Collection<BlackboardArtifact> newArtifacts = new ArrayList<BlackboardArtifact>(); //new artifacts to report
                    for (final Keyword hitTerm : newResults.keySet()) {
                        List<ContentHit> contentHitsAll = newResults.get(hitTerm);
                        Map<FsContent,Integer>contentHitsFlattened = ContentHit.flattenResults(contentHitsAll);
                        for (final FsContent hitFile : contentHitsFlattened.keySet()) {
                            if (this.isCancelled()) {
                                return null;
                            }

                            String snippet = null;
                            final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(hitTerm.getQuery(), true, false);
                            int chunkId = contentHitsFlattened.get(hitFile);
                            try {
                                snippet = LuceneQuery.querySnippet(snippetQuery, hitFile.getId(), chunkId, isRegex, true);
                            } catch (NoOpenCoreException e) {
                                logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e);
                                //no reason to continie
                                return null;
                            } catch (Exception e) {
                                logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e);
                                continue;
                            }
                            
                            KeywordWriteResult written = del.writeToBlackBoard(hitTerm.getQuery(), hitFile, snippet, listName);
                            if (written == null) {
                                //logger.log(Level.INFO, "BB artifact for keyword not written: " + hitTerm.toString());
                                continue;
                            }

                            newArtifacts.add(written.getArtifact());

                            //generate a data message for each artifact
                            StringBuilder subjectSb = new StringBuilder();
                            StringBuilder detailsSb = new StringBuilder();
                            //final int hitFiles = newResults.size();

                            if (!keywordQuery.isLiteral()) {
                                subjectSb.append("RegExp hit: ");
                            } else {
                                subjectSb.append("Keyword hit: ");
                            }
                            //subjectSb.append("<");
                            String uniqueKey = null;
                            BlackboardAttribute attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID());
                            if (attr != null) {
                                final String keyword = attr.getValueString();
                                subjectSb.append(keyword);
                                uniqueKey = keyword.toLowerCase();
                            }

                            //subjectSb.append(">");
                            //String uniqueKey = queryStr;

                            //details
                            detailsSb.append("<table border='0' cellpadding='4' width='280'>");
                            //hit
                            detailsSb.append("<tr>");
                            detailsSb.append("<th>Keyword hit</th>");
                            detailsSb.append("<td>").append(StringEscapeUtils.escapeHtml(attr.getValueString())).append("</td>");
                            detailsSb.append("</tr>");

                            //preview
                            attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID());
                            if (attr != null) {
                                detailsSb.append("<tr>");
                                detailsSb.append("<th>Preview</th>");
                                detailsSb.append("<td>").append(StringEscapeUtils.escapeHtml(attr.getValueString())).append("</td>");
                                detailsSb.append("</tr>");

                            }

                            //file
                            detailsSb.append("<tr>");
                            detailsSb.append("<th>File</th>");
                            detailsSb.append("<td>").append(hitFile.getParentPath()).append(hitFile.getName()).append("</td>");
                            detailsSb.append("</tr>");


                            //list
                            attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SET.getTypeID());
                            detailsSb.append("<tr>");
                            detailsSb.append("<th>List</th>");
                            detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                            detailsSb.append("</tr>");

                            //regex
                            if (!keywordQuery.isLiteral()) {
                                attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID());
                                if (attr != null) {
                                    detailsSb.append("<tr>");
                                    detailsSb.append("<th>RegEx</th>");
                                    detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                                    detailsSb.append("</tr>");

                                }
                            }
                            detailsSb.append("</table>");

                            managerProxy.postMessage(IngestMessage.createDataMessage(++messageID, instance, subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));

                        } //for each term hit
                    }//for each file hit

                    //update artifact browser
                    if (!newArtifacts.isEmpty()) {
                        IngestManager.fireServiceDataEvent(new ServiceDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
                    }
                }
                progress.progress(queryStr, ++numSearched);
            }

            return null;
        }

        @Override
        protected void done() {
            super.done();
            searcherDone = true;  //next searcher can start      

            progress.finish();

            //logger.log(Level.INFO, "Finished search");
            if (finalRun) {
                finalRunComplete = true;
                keywords.clear();
                keywordLists.clear();
                managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, KeywordSearchIngestService.instance, "Completed"));
            }
        }
    }
    
    //check if fscontent already hit, ignore chunks
    private static boolean previouslyHit(List<ContentHit> contents, ContentHit hit) {
        boolean ret = false;
        long hitId = hit.getId();
        for (ContentHit c : contents) {
            if (c.getId() == hitId) {
                ret = true;
                break;
            }
        }
        return ret;
    }
}
