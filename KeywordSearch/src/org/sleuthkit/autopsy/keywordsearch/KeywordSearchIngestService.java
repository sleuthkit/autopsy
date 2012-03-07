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
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
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
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskException;

//service provider registered in layer.xml
public final class KeywordSearchIngestService implements IngestServiceFsContent {

    private static final Logger logger = Logger.getLogger(KeywordSearchIngestService.class.getName());
    public static final String MODULE_NAME = "Keyword Search";
    private static KeywordSearchIngestService instance = null;
    private IngestManagerProxy managerProxy;
    private static final long MAX_STRING_EXTRACT_SIZE = 10 * (1 << 10) * (1 << 10);
    private static final long MAX_INDEX_SIZE = 200 * (1 << 10) * (1 << 10);
    private Ingester ingester;
    private volatile boolean commitIndex = false; //whether to commit index next time
    private volatile boolean runTimer = false;
    private List<Keyword> keywords; //keywords to search
    private List<String> keywordLists; // lists currently being searched
    private Map<String, String> keywordToList; //keyword to list name mapping
    //private final Object lock = new Object();
    private Thread timer;
    private Indexer indexer;
    private SwingWorker searcher;
    private volatile boolean searcherDone = true;
    private Map<Keyword, List<FsContent>> currentResults;
    private volatile int messageID = 0;
    private volatile boolean finalRun = false;
    private final String hashDBServiceName = "Hash Lookup";
    private SleuthkitCase caseHandle = null;
    // TODO: use a more robust method than checking file extension to determine
    // whether to try a file
    // supported extensions list from http://www.lucidimagination.com/devzone/technical-articles/content-extraction-tika
    static final String[] ingestibleExtensions = {"tar", "jar", "zip", "bzip2",
        "gz", "tgz", "doc", "xls", "ppt", "rtf", "pdf", "html", "htm", "xhtml", "txt",
        "bmp", "gif", "png", "jpeg", "tiff", "mp3", "aiff", "au", "midi", "wav",
        "pst", "xml", "class"};
   

    public enum IngestStatus {

        INGESTED, EXTRACTED_INGESTED, SKIPPED,
    };
    private Map<Long, IngestStatus> ingestStatus;
    private Map<String, List<FsContent>> reportedHits; //already reported hits

    public static synchronized KeywordSearchIngestService getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestService();
        }
        return instance;
    }

    @Override
    public ProcessResult process(FsContent fsContent) {
        //check if we should skip this file according to HashDb service
        //if so do not index it, also postpone indexing and keyword search threads to later
        IngestServiceFsContent.ProcessResult hashDBResult = managerProxy.getFsContentServiceResult(hashDBServiceName);
        if (hashDBResult == IngestServiceFsContent.ProcessResult.COND_STOP) {
            return ProcessResult.OK;
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
        //logger.log(Level.INFO, "complete()");
        runTimer = false;

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
        if (keywords != null && !keywords.isEmpty()) {
            finalRun = true;
            searcher = new Searcher(keywords);
            searcher.execute();
        } else {
            managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Completed"));
        }
        //postSummary();
    }

    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //stop timer
        runTimer = false;
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
    public void init(IngestManagerProxy managerProxy) {
        logger.log(Level.INFO, "init()");

        caseHandle = Case.getCurrentCase().getSleuthkitCase();

        this.managerProxy = managerProxy;

        final Server.Core solrCore = KeywordSearch.getServer().getCore();
        ingester = solrCore.getIngester();

        ingestStatus = new HashMap<Long, IngestStatus>();

        reportedHits = new HashMap<String, List<FsContent>>();

        keywords = new ArrayList<Keyword>();
        keywordLists = new ArrayList<String>();
        keywordToList = new HashMap<String, String>();

        initKeywords();

        if (keywords.isEmpty() || keywordLists.isEmpty()) {
            managerProxy.postMessage(IngestMessage.createWarningMessage(++messageID, instance, "No keywords in keyword list.", "Only indexing will be done and and keyword search will be skipped (it can be executed later again as ingest or using toolbar search feature)."));
        }

        finalRun = false;
        searcherDone = true; //make sure to start the initial searcher
        //keeps track of all results per run not to repeat reporting the same hits
        currentResults = new HashMap<Keyword, List<FsContent>>();

        indexer = new Indexer();

        final int commitIntervalMs = managerProxy.getUpdateFrequency() * 60 * 1000;
        logger.log(Level.INFO, "Using refresh interval (ms): " + commitIntervalMs);

        timer = new CommitTimer(commitIntervalMs);
        runTimer = true;
        timer.start();

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
        ingester.commit();

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
            final int numIndexedFiles = KeywordSearch.getServer().getCore().queryNumIndexedFiles();
            SwingUtilities.invokeLater(new Runnable() {

                @Override
                public void run() {
                    KeywordSearch.changeSupport.firePropertyChange(KeywordSearch.NUM_FILES_CHANGE_EVT, null, new Integer(numIndexedFiles));
                }
            });
        } catch (SolrServerException se) {
            logger.log(Level.INFO, "Error executing Solr query to check number of indexed files: ", se);
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

    //CommitTimer wakes up every interval ms
    //and sets a flag for indexer to commit after indexing next file
    private class CommitTimer extends Thread {

        private final Logger logger = Logger.getLogger(CommitTimer.class.getName());
        private int interval;

        CommitTimer(int interval) {
            this.interval = interval;
        }

        @Override
        public void run() {
            while (runTimer) {
                try {
                    Thread.sleep(interval);
                    commitIndex = true;
                    logger.log(Level.INFO, "CommitTimer awake");
                } catch (InterruptedException e) {
                }

            }
            commitIndex = false;
            return;
        }
    }

    //Indexer thread that processes files in the queue
    //commits when timer expires
    //sleeps if nothing in the queue
    private class Indexer {

        private final Logger logger = Logger.getLogger(Indexer.class.getName());

        private boolean extractAndIngest(FsContent f) {
            boolean success = false;
            FsContentStringStream fscs = new FsContentStringStream(f, FsContentStringStream.Encoding.ASCII);
            try {
                fscs.convert();
                ingester.ingest(fscs);
                success = true;
            } catch (TskException tskEx) {
                logger.log(Level.INFO, "Problem extracting string from file: '" + f.getName() + "' (id: " + f.getId() + ").", tskEx);
            } catch (IngesterException ingEx) {
                logger.log(Level.INFO, "Ingester had a problem with extracted strings from file '" + f.getName() + "' (id: " + f.getId() + ").", ingEx);
            } catch (Exception ingEx) {
                logger.log(Level.INFO, "Ingester had a problem with extracted strings from file '" + f.getName() + "' (id: " + f.getId() + ").", ingEx);
            }
            return success;
        }

        private void indexFile(FsContent fsContent) {
            final long size = fsContent.getSize();
            //logger.log(Level.INFO, "Processing fsContent: " + fsContent.getName());
            if (!fsContent.isFile()) {
                return;
            }

            if (size == 0 || size > MAX_INDEX_SIZE) {
                ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                return;
            }

            boolean ingestible = false;
            final String fileName = fsContent.getName();
            for (String ext : ingestibleExtensions) {
                if (fileName.endsWith(ext)) {
                    ingestible = true;
                    break;
                }
            }

            if (ingestible == true) {
                try {
                    //logger.log(Level.INFO, "indexing: " + fsContent.getName());
                    ingester.ingest(fsContent);
                    ingestStatus.put(fsContent.getId(), IngestStatus.INGESTED);
                } catch (IngesterException e) {
                    ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                    //try to extract strings
                    processNonIngestible(fsContent);

                } catch (Exception e) {
                    ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                    //try to extract strings
                    processNonIngestible(fsContent);

                }
            } else {
                processNonIngestible(fsContent);
            }
        }

        private void processNonIngestible(FsContent fsContent) {
            if (fsContent.getSize() < MAX_STRING_EXTRACT_SIZE) {
                if (!extractAndIngest(fsContent)) {
                    logger.log(Level.INFO, "Failed to extract strings and ingest, file '" + fsContent.getName() + "' (id: " + fsContent.getId() + ").");
                } else {
                    ingestStatus.put(fsContent.getId(), IngestStatus.EXTRACTED_INGESTED);
                }
            } else {
                ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
            }
        }
    }

    private class Searcher extends SwingWorker {

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
                    return Searcher.this.cancel(true);
                }
            });

            progress.start(keywords.size());
            int numSearched = 0;

            for (Keyword query : keywords) {
                if (this.isCancelled()) {
                    return null;
                }
                final String queryStr = query.getQuery();
                final String listName = keywordToList.get(queryStr);

                //logger.log(Level.INFO, "Searching: " + queryStr);

                progress.progress(queryStr, numSearched);

                KeywordSearchQuery del = null;

                if (query.isLiteral()) {
                    del = new LuceneQuery(queryStr);
                    del.escape();
                } else {
                    del = new TermComponentQuery(queryStr);
                }

                List<FsContent> queryResult = null;

                try {
                    queryResult = del.performQuery();
                } catch (Exception e) {
                    logger.log(Level.INFO, "Error performing query: " + query.getQuery(), e);
                    continue;
                }

                //calculate new results but substracting results already obtained in this run
                List<FsContent> newResults = new ArrayList<FsContent>();

                List<FsContent> curResults = currentResults.get(query);
                if (curResults == null) {
                    currentResults.put(query, queryResult);
                    newResults = queryResult;
                } else {
                    for (FsContent res : queryResult) {
                        if (!curResults.contains(res)) {
                            //add to new results
                            newResults.add(res);
                        }
                    }
                    //update current result with new ones
                    curResults.addAll(newResults);

                }

                if (!newResults.isEmpty()) {

                    //write results to BB
                    Collection<BlackboardArtifact> newArtifacts = new ArrayList<BlackboardArtifact>(); //new artifacts to report
                    for (FsContent hitFile : newResults) {
                        if (this.isCancelled()) {
                            return null;
                        }
                        Collection<KeywordWriteResult> written = del.writeToBlackBoard(hitFile, listName);
                        for (KeywordWriteResult res : written) {
                            newArtifacts.add(res.getArtifact());

                            //generate a data message for each artifact
                            StringBuilder subjectSb = new StringBuilder();
                            StringBuilder detailsSb = new StringBuilder();
                            //final int hitFiles = newResults.size();

                            subjectSb.append("Keyword hit: ").append("<");
                            String uniqueKey = null;
                            BlackboardAttribute attr = res.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID());
                            if (attr != null) {
                                final String keyword = attr.getValueString();
                                subjectSb.append(keyword);
                                uniqueKey = keyword.toLowerCase();
                            }

                            subjectSb.append(">");
                            //String uniqueKey = queryStr;

                            //details
                            //title
                            detailsSb.append("<table border='1' width='200'><tr><th>Keyword hit</th><th>Preview</th>");
                            detailsSb.append("<th>File</th><th>List</th>");
                            if (! query.isLiteral())
                                detailsSb.append("<th>Regex</th>");
                            detailsSb.append("</tr><tr>");
                            
                            //hit
                            detailsSb.append("<td>").append(StringEscapeUtils.escapeHtml(attr.getValueString())).append("</td>");
                            
                             //preview
                            attr = res.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID());
                            if (attr != null) {
                                detailsSb.append("<td>").append(StringEscapeUtils.escapeHtml(attr.getValueString())).append("</td>");
                            }
                            
                            //file
                            detailsSb.append("<td>").append(hitFile.getParentPath()).append(hitFile.getName()).append("</td>");
                            
                            //list
                            attr = res.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_SET.getTypeID());
                            detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                            
                            //regex
                            if (!query.isLiteral()) {
                                attr = res.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID());
                                if (attr != null) {
                                    detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                                }
                            }
                            detailsSb.append("</tr></table>");

                            managerProxy.postMessage(IngestMessage.createDataMessage(++messageID, instance, subjectSb.toString(), detailsSb.toString(), uniqueKey, res.getArtifact()));
                        }
                    } //for each file hit

                    //update artifact browser
                    IngestManager.fireServiceDataEvent(new ServiceDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
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
                keywords.clear();
                keywordLists.clear();
                managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, KeywordSearchIngestService.instance, "Completed"));
            }
        }
    }
}
