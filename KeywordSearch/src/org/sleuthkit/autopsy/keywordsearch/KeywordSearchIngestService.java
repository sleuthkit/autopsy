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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestManagerProxy;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestServiceFsContent;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
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
    //private final Object lock = new Object();
    private Thread timer;
    private Indexer indexer;
    private SwingWorker searcher;
    private volatile boolean searcherDone = true;
    private Map<Keyword, List<FsContent>> currentResults;
    private volatile int messageID = 0;
    private volatile boolean finalRun = false;
    private final SleuthkitCase caseHandle = Case.getCurrentCase().getSleuthkitCase();
    private static final String[] ingestibleExtensions = {"tar", "jar", "zip", "bzip2",
        "gz", "tgz", "doc", "xls", "ppt", "rtf", "pdf", "html", "xhtml", "txt",
        "bmp", "gif", "png", "jpeg", "tiff", "mp3", "aiff", "au", "midi", "wav",
        "pst", "xml", "class"};

    public enum IngestStatus {

        INGESTED, EXTRACTED_INGESTED, SKIPPED,};
    private Map<Long, IngestStatus> ingestStatus;
    private Map<String, List<FsContent>> reportedHits; //already reported hits

    public static synchronized KeywordSearchIngestService getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestService();
        }
        return instance;
    }

    @Override
    public void process(FsContent fsContent) {
        //check if time to commit and previous search is not running
        //commiting while searching causes performance issues
        if (commitIndex && searcherDone) {
            logger.log(Level.INFO, "Commiting index");
            commit();
            commitIndex = false;
            indexChangeNotify();
            //start search if previous not running
            if (keywords != null && !keywords.isEmpty() && searcherDone) {
                searcher = new Searcher(keywords);
                searcher.execute();
            }
        }
        indexer.indexFile(fsContent);

    }

    @Override
    public void complete() {
        logger.log(Level.INFO, "complete()");
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

        this.managerProxy = managerProxy;

        final Server.Core solrCore = KeywordSearch.getServer().getCore();
        ingester = solrCore.getIngester();

        ingestStatus = new HashMap<Long, IngestStatus>();

        reportedHits = new HashMap<String, List<FsContent>>();

        keywords = new ArrayList(KeywordSearchListTopComponent.getDefault().getAllKeywords());
        if (keywords.isEmpty()) {
            managerProxy.postMessage(IngestMessage.createErrorMessage(++messageID, instance, "No keywords in keyword list.  Will index and skip search."));
        }

        finalRun = false;
        searcherDone = true; //make sure to start the initial searcher
        //keeps track of all results per run not to repeat reporting the same hits
        currentResults = new HashMap<Keyword, List<FsContent>>();

        indexer = new Indexer();

        //final int commitIntervalMs = managerProxy.getUpdateFrequency() * 1000;
        final int commitIntervalMs = 60 * 1000;

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
    public void userConfigure() {
    }

    private void commit() {
        ingester.commit();

    }

    private void postSummary() {
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
        managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Indexed files: " + indexed));
        managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Indexed strings: " + indexed_extr));
        managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Skipped files: " + skipped));
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

                //logger.log(Level.INFO, "Searching: " + queryStr);

                progress.progress(queryStr, numSearched);

                KeywordSearchQuery del = null;

                if (query.isLiteral()) {
                    del = new LuceneQuery(queryStr);
                } else {
                    del = new TermComponentQuery(queryStr);
                }

                if (query.isLiteral()) {
                    del.escape();
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
                    StringBuilder sb = new StringBuilder();
                    final int hitFiles = newResults.size();
                    sb.append("New hit: ").append("<").append(queryStr);
                    if (!query.isLiteral()) {
                        sb.append(" (regex)");
                    }
                    sb.append(">");
                    sb.append(" in ").append(hitFiles).append((hitFiles > 1 ? " files" : " file"));
                    managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, instance, sb.toString()));

                    //write results to BB
                    for (FsContent hitFile : newResults) {
                        BlackboardArtifact bba = null;
                        try {
                            bba = hitFile.newArtifact(ARTIFACT_TYPE.TSK_KEYWORD_HIT);
                        } catch (Exception e) {
                            logger.log(Level.INFO, "Error adding bb artifact for keyword hit", e);
                            continue;
                        }
                        if (query.isLiteral()) {
                            String snippet = null;
                            try {
                                snippet = LuceneQuery.getSnippet(queryStr, hitFile.getId());
                            } catch (Exception e) {
                                logger.log(Level.INFO, "Error querying snippet: " + queryStr, e);
                            }
                            if (snippet != null) {
                                try {
                                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, "keyword", snippet));
                                } catch (Exception e1) {
                                    logger.log(Level.INFO, "Error adding bb snippet attribute, will encode and retry", e1);
                                    try {
                                        //escape in case of garbage so that sql accepts it
                                        snippet = URLEncoder.encode(snippet, "UTF-8");
                                        bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, "keyword", snippet));
                                    }
                                    catch (Exception e2) {
                                        logger.log(Level.INFO, "Error adding bb snippet attribute", e2);
                                    }
                                }
                            }
                            try {
                                //keyword
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID(), MODULE_NAME, "keyword", queryStr));
                                //bogus 
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, "keyword", ""));
                            } catch (Exception e) {
                                logger.log(Level.INFO, "Error adding bb attribute", e);
                            }
                        } else {
                            //regex case
                            try {
                                //regex keyword
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID(), MODULE_NAME, "keyword", queryStr));
                                //bogus
                                bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD.getTypeID(), MODULE_NAME, "keyword", ""));
                            } catch (Exception e) {
                                logger.log(Level.INFO, "Error adding bb attribute", e);
                            }
                            //build preview query from terms
                            StringBuilder termSb = new StringBuilder();
                            Collection<Term> terms = del.getTerms();
                            int i = 0;
                            final int total = terms.size();
                            for (Term term : terms) {
                                termSb.append(term.getTerm());
                                if (i < total - 1) {
                                    termSb.append(" "); //OR
                                }
                                ++i;
                            }
                            final String termSnipQuery = termSb.toString();
                            String snippet = null;
                            try {
                                snippet = LuceneQuery.getSnippet(termSnipQuery, hitFile.getId());
                            } catch (Exception e) {
                                logger.log(Level.INFO, "Error querying snippet: " + termSnipQuery, e);
                            }

                            if (snippet != null) {
                                try {
                                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, "keyword", snippet));
                                } catch (Exception e) {
                                    logger.log(Level.INFO, "Error adding bb snippet attribute, will encode and retry", e);
                                    try {
                                        //escape in case of garbage so that sql accepts it
                                        snippet = URLEncoder.encode(snippet, "UTF-8");
                                        bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID(), MODULE_NAME, "keyword", snippet));
                                    } catch (Exception e2) {
                                        logger.log(Level.INFO, "Error adding bb snippet attribute", e2);
                                    }
                                }
                            }

                            //TODO add all terms that matched to attribute
                        }

                    }

                    //update artifact browser
                    //TODO use has data evt
                    IngestManager.firePropertyChange(IngestManager.SERVICE_STARTED_EVT, MODULE_NAME);
                    IngestManager.firePropertyChange(IngestManager.SERVICE_HAS_DATA_EVT, MODULE_NAME);
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
                managerProxy.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, KeywordSearchIngestService.instance, "Completed"));
            }
        }
    }
}
