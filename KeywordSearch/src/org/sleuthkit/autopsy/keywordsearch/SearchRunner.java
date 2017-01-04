/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 - 2014 Basis Technology Corp.
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
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.aggregate.AggregateProgressFactory;
import org.netbeans.api.progress.aggregate.AggregateProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Singleton keyword search manager: Launches search threads for each job and
 * performs commits, both on timed intervals.
 */
public final class SearchRunner {

    private static final Logger logger = Logger.getLogger(SearchRunner.class.getName());
    private static SearchRunner instance = null;
    private IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private volatile boolean updateTimerRunning = false;
    private Timer updateTimer;

    // maps a jobID to the search
    private Map<Long, SearchJobInfo> jobs = new HashMap<>(); //guarded by "this"

    SearchRunner() {
        ingester = Server.getIngester();
        updateTimer = new Timer(NbBundle.getMessage(this.getClass(), "SearchRunner.updateTimer.title.text"), true); // run as a daemon
    }

    /**
     *
     * @return the singleton object
     */
    public static synchronized SearchRunner getInstance() {
        if (instance == null) {
            instance = new SearchRunner();
        }
        return instance;
    }

    /**
     * Add a new job. Searches will be periodically performed after this is
     * called.
     *
     * @param jobId            Job ID that this is associated with
     * @param dataSourceId     Data source that is being indexed and that
     *                         searches should be restricted to.
     * @param keywordListNames List of keyword lists that will be searched. List
     *                         contents will be refreshed each search.
     */
    public synchronized void startJob(long jobId, long dataSourceId, List<String> keywordListNames) {
        if (jobs.containsKey(jobId) == false) {
            logger.log(Level.INFO, "Adding job {0}", jobId); //NON-NLS
            SearchJobInfo jobData = new SearchJobInfo(jobId, dataSourceId, keywordListNames);
            jobs.put(jobId, jobData);
        }

        // keep track of how many threads / module instances from this job have asked for this
        jobs.get(jobId).incrementModuleReferenceCount();

        // start the timer, if needed
        if ((jobs.size() > 0) && (updateTimerRunning == false)) {
            final long updateIntervalMs = ((long) KeywordSearchSettings.getUpdateFrequency().getTime()) * 60 * 1000;
            updateTimer.scheduleAtFixedRate(new UpdateTimerTask(), updateIntervalMs, updateIntervalMs);
            updateTimerRunning = true;
        }
    }

    /**
     * Perform normal finishing of searching for this job, including one last
     * commit and search. Blocks until the final search is complete.
     *
     * @param jobId
     */
    public void endJob(long jobId) {
        SearchJobInfo job;
        boolean readyForFinalSearch = false;
        synchronized (this) {
            job = jobs.get(jobId);
            if (job == null) {
                return;
            }

            // Only do final search if this is the last module/thread in this job to call endJob()
            if (job.decrementModuleReferenceCount() == 0) {
                jobs.remove(jobId);
                readyForFinalSearch = true;
            }
        }

        if (readyForFinalSearch) {
            commit();
            doFinalSearch(job); //this will block until it's done
        }
    }

    /**
     * Immediate stop and removal of job from SearchRunner. Cancels the
     * associated search worker if it's still running.
     *
     * @param jobId
     */
    public void stopJob(long jobId) {
        logger.log(Level.INFO, "Stopping job {0}", jobId); //NON-NLS
        commit();

        SearchJobInfo job;
        synchronized (this) {
            job = jobs.get(jobId);
            if (job == null) {
                return;
            }

            //stop currentSearcher
            SearchRunner.Searcher currentSearcher = job.getCurrentSearcher();
            if ((currentSearcher != null) && (!currentSearcher.isDone())) {
                currentSearcher.cancel(true);
            }

            jobs.remove(jobId);
        }
    }

    /**
     * Add these lists to all of the jobs. Used when user wants to search for a
     * list while ingest has already started.
     *
     * @param keywordListNames
     */
    public synchronized void addKeywordListsToAllJobs(List<String> keywordListNames) {
        for (String listName : keywordListNames) {
            logger.log(Level.INFO, "Adding keyword list {0} to all jobs", listName); //NON-NLS
            for (SearchJobInfo j : jobs.values()) {
                j.addKeywordListName(listName);
            }
        }
    }

    /**
     * Commits index and notifies listeners of index update
     */
    private void commit() {
        ingester.commit();

        // Signal a potential change in number of text_ingested files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            KeywordSearch.fireNumIndexedFilesChange(null, numIndexedFiles);
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files: ", ex); //NON-NLS
        }
    }

    /**
     * A final search waits for any still-running workers, and then executes a
     * new one and waits until that is done.
     *
     * @param job
     */
    private void doFinalSearch(SearchJobInfo job) {
        // Run one last search as there are probably some new files committed
        logger.log(Level.INFO, "Running final search for jobid {0}", job.getJobId());         //NON-NLS
        if (!job.getKeywordListNames().isEmpty()) {
            try {
                // In case this job still has a worker running, wait for it to finish
                job.waitForCurrentWorker();

                SearchRunner.Searcher finalSearcher = new SearchRunner.Searcher(job, true);
                job.setCurrentSearcher(finalSearcher); //save the ref
                finalSearcher.execute(); //start thread

                // block until the search is complete
                finalSearcher.get();

            } catch (InterruptedException | ExecutionException ex) {
                logger.log(Level.WARNING, "Job {1} final search thread failed: {2}", new Object[]{job.getJobId(), ex}); //NON-NLS
            }
        }
    }

    /**
     * Timer triggered re-search for each job (does a single index commit first)
     */
    private class UpdateTimerTask extends TimerTask {

        private final Logger logger = Logger.getLogger(SearchRunner.UpdateTimerTask.class.getName());

        @Override
        public void run() {
            // If no jobs then cancel the task. If more job(s) come along, a new task will start up.
            if (jobs.isEmpty()) {
                this.cancel(); //terminate this timer task
                updateTimerRunning = false;
                return;
            }

            commit();

            synchronized (SearchRunner.this) {
                // Spawn a search thread for each job
                for (Entry<Long, SearchJobInfo> j : jobs.entrySet()) {
                    SearchJobInfo job = j.getValue();
                    // If no lists or the worker is already running then skip it
                    if (!job.getKeywordListNames().isEmpty() && !job.isWorkerRunning()) {
                        Searcher searcher = new Searcher(job);
                        job.setCurrentSearcher(searcher); //save the ref
                        searcher.execute(); //start thread
                        job.setWorkerRunning(true);
                    }
                }
            }
        }
    }

    /**
     * Data structure to keep track of keyword lists, current results, and
     * search running status for each jobid
     */
    private class SearchJobInfo {

        private final long jobId;
        private final long dataSourceId;
        // mutable state:
        private volatile boolean workerRunning;
        private List<String> keywordListNames; //guarded by SearchJobInfo.this
        private Map<Keyword, List<Long>> currentResults; //guarded by SearchJobInfo.this
        private SearchRunner.Searcher currentSearcher;
        private AtomicLong moduleReferenceCount = new AtomicLong(0);
        private final Object finalSearchLock = new Object(); //used for a condition wait

        public SearchJobInfo(long jobId, long dataSourceId, List<String> keywordListNames) {
            this.jobId = jobId;
            this.dataSourceId = dataSourceId;
            this.keywordListNames = new ArrayList<>(keywordListNames);
            currentResults = new HashMap<>();
            workerRunning = false;
            currentSearcher = null;
        }

        public long getJobId() {
            return jobId;
        }

        public long getDataSourceId() {
            return dataSourceId;
        }

        public synchronized List<String> getKeywordListNames() {
            return new ArrayList<>(keywordListNames);
        }

        public synchronized void addKeywordListName(String keywordListName) {
            if (!keywordListNames.contains(keywordListName)) {
                keywordListNames.add(keywordListName);
            }
        }

        public synchronized List<Long> currentKeywordResults(Keyword k) {
            return currentResults.get(k);
        }

        public synchronized void addKeywordResults(Keyword k, List<Long> resultsIDs) {
            currentResults.put(k, resultsIDs);
        }

        public boolean isWorkerRunning() {
            return workerRunning;
        }

        public void setWorkerRunning(boolean flag) {
            workerRunning = flag;
        }

        public synchronized SearchRunner.Searcher getCurrentSearcher() {
            return currentSearcher;
        }

        public synchronized void setCurrentSearcher(SearchRunner.Searcher searchRunner) {
            currentSearcher = searchRunner;
        }

        public void incrementModuleReferenceCount() {
            moduleReferenceCount.incrementAndGet();
        }

        public long decrementModuleReferenceCount() {
            return moduleReferenceCount.decrementAndGet();
        }

        /**
         * In case this job still has a worker running, wait for it to finish
         *
         * @throws InterruptedException
         */
        public void waitForCurrentWorker() throws InterruptedException {
            synchronized (finalSearchLock) {
                while (workerRunning) {
                    finalSearchLock.wait(); //wait() releases the lock
                }
            }
        }

        /**
         * Unset workerRunning and wake up thread(s) waiting on finalSearchLock
         */
        public void searchNotify() {
            synchronized (finalSearchLock) {
                workerRunning = false;
                finalSearchLock.notify();
            }
        }
    }

    /**
     * Searcher responsible for searching the current index and writing results
     * to blackboard and the inbox. Also, posts results to listeners as Ingest
     * data events. Searches entire index, and keeps track of only new results
     * to report and save. Runs as a background thread.
     */
    private final class Searcher extends SwingWorker<Object, Void> {

        /**
         * Searcher has private copies/snapshots of the lists and keywords
         */
        private SearchJobInfo job;
        private List<Keyword> keywords; //keywords to search
        private List<String> keywordListNames; // lists currently being searched
        private List<KeywordList> keywordLists;
        private Map<String, KeywordList> keywordToList; //keyword to list name mapping
        private AggregateProgressHandle progressGroup;
        private final Logger logger = Logger.getLogger(SearchRunner.Searcher.class.getName());
        private boolean finalRun = false;

        Searcher(SearchJobInfo job) {
            this.job = job;
            keywordListNames = job.getKeywordListNames();
            keywords = new ArrayList<>();
            keywordToList = new HashMap<>();
            keywordLists = new ArrayList<>();
            //keywords are populated as searcher runs
        }

        Searcher(SearchJobInfo job, boolean finalRun) {
            this(job);
            this.finalRun = finalRun;
        }

        @Override
        @Messages("SearchRunner.query.exception.msg=Error performing query:")
        protected Object doInBackground() throws Exception {
            final String displayName = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.displayName")
                    + (finalRun ? (" - " + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.finalizeMsg")) : "");
            final String pgDisplayName = displayName + (" (" + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.pendingMsg") + ")");
            progressGroup = AggregateProgressFactory.createSystemHandle(pgDisplayName, null, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Cancelling the searcher by user."); //NON-NLS
                    if (progressGroup != null) {
                        progressGroup.setDisplayName(displayName + " " + NbBundle.getMessage(this.getClass(), "SearchRunner.doInBackGround.cancelMsg"));
                    }
                    return SearchRunner.Searcher.this.cancel(true);
                }
            }, null);

            updateKeywords();

            ProgressContributor[] subProgresses = new ProgressContributor[keywords.size()];
            int i = 0;
            for (Keyword keywordQuery : keywords) {
                subProgresses[i] = AggregateProgressFactory.createProgressContributor(keywordQuery.getSearchTerm());
                progressGroup.addContributor(subProgresses[i]);
                i++;
            }

            progressGroup.start();

            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            try {
                progressGroup.setDisplayName(displayName);

                int keywordsSearched = 0;

                for (Keyword keywordQuery : keywords) {
                    if (this.isCancelled()) {
                        logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: {0}", keywordQuery.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    final String queryStr = keywordQuery.getSearchTerm();
                    final KeywordList list = keywordToList.get(queryStr);

                    //new subProgress will be active after the initial query
                    //when we know number of hits to start() with
                    if (keywordsSearched > 0) {
                        subProgresses[keywordsSearched - 1].finish();
                    }

                    KeywordSearchQuery keywordSearchQuery = null;

                    boolean isRegex = !keywordQuery.searchTermIsLiteral();
                    if (isRegex) {
                        keywordSearchQuery = new TermsComponentQuery(list, keywordQuery);
                    } else {
                        keywordSearchQuery = new LuceneQuery(list, keywordQuery);
                        keywordSearchQuery.escape();
                    }

                    // Filtering
                    //limit search to currently ingested data sources
                    //set up a filter with 1 or more image ids OR'ed
                    final KeywordQueryFilter dataSourceFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.DATA_SOURCE, job.getDataSourceId());
                    keywordSearchQuery.addFilter(dataSourceFilter);

                    QueryResults queryResults;

                    // Do the actual search
                    try {
                        queryResults = keywordSearchQuery.performQuery();
                    } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                        logger.log(Level.SEVERE, "Error performing query: " + keywordQuery.getSearchTerm(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(Bundle.SearchRunner_query_exception_msg() + keywordQuery.getSearchTerm(), ex.getCause().getMessage());
                        //no reason to continue with next query if recovery failed
                        //or wait for recovery to kick in and run again later
                        //likely case has closed and threads are being interrupted
                        return null;
                    } catch (CancellationException e) {
                        logger.log(Level.INFO, "Cancel detected, bailing during keyword query: {0}", keywordQuery.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    // calculate new results by substracting results already obtained in this ingest
                    // this creates a map of each keyword to the list of unique files that have that hit. 
                    QueryResults newResults = filterResults(queryResults);

                    if (!newResults.getKeywords().isEmpty()) {

                        // Write results to BB
                        //new artifacts created, to report to listeners
                        Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();

                        //scale progress bar more more granular, per result sub-progress, within per keyword
                        int totalUnits = newResults.getKeywords().size();
                        subProgresses[keywordsSearched].start(totalUnits);
                        int unitProgress = 0;
                        String queryDisplayStr = keywordQuery.getSearchTerm();
                        if (queryDisplayStr.length() > 50) {
                            queryDisplayStr = queryDisplayStr.substring(0, 49) + "...";
                        }
                        subProgresses[keywordsSearched].progress(list.getName() + ": " + queryDisplayStr, unitProgress);

                        // Create blackboard artifacts                
                        newArtifacts = newResults.writeAllHitsToBlackBoard(null, subProgresses[keywordsSearched], this, list.getIngestMessages());

                    } //if has results

                    //reset the status text before it goes away
                    subProgresses[keywordsSearched].progress("");

                    ++keywordsSearched;

                } //for each keyword

            } //end try block
            catch (Exception ex) {
                logger.log(Level.WARNING, "searcher exception occurred", ex); //NON-NLS
            } finally {
                try {
                    finalizeSearcher();
                    stopWatch.stop();

                    logger.log(Level.INFO, "Searcher took to run: {0} secs.", stopWatch.getElapsedTimeSecs()); //NON-NLS
                } finally {
                    // In case a thread is waiting on this worker to be done
                    job.searchNotify();
                }
            }

            return null;
        }

        @Override
        protected void done() {
            // call get to see if there were any errors
            try {
                get();
            } catch (InterruptedException | ExecutionException e) {
                logger.log(Level.SEVERE, "Error performing keyword search: " + e.getMessage()); //NON-NLS
                services.postMessage(IngestMessage.createErrorMessage(KeywordSearchModuleFactory.getModuleName(),
                        NbBundle.getMessage(this.getClass(),
                                "SearchRunner.Searcher.done.err.msg"), e.getMessage()));
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            }
        }

        /**
         * Sync-up the updated keywords from the currently used lists in the XML
         */
        private void updateKeywords() {
            XmlKeywordSearchList loader = XmlKeywordSearchList.getCurrent();

            keywords.clear();
            keywordToList.clear();
            keywordLists.clear();

            for (String name : keywordListNames) {
                KeywordList list = loader.getList(name);
                keywordLists.add(list);
                for (Keyword k : list.getKeywords()) {
                    keywords.add(k);
                    keywordToList.put(k.getSearchTerm(), list);
                }
            }
        }

        /**
         * Performs the cleanup that needs to be done right AFTER
         * doInBackground() returns without relying on done() method that is not
         * guaranteed to run.
         */
        private void finalizeSearcher() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressGroup.finish();
                }
            });
        }

        //calculate new results but substracting results already obtained in this ingest
        //update currentResults map with the new results
        private QueryResults filterResults(QueryResults queryResult) {

            QueryResults newResults = new QueryResults(queryResult.getQuery(), queryResult.getKeywordList());

            for (Keyword keyword : queryResult.getKeywords()) {
                List<KeywordHit> queryTermResults = queryResult.getResults(keyword);

                //translate to list of IDs that we keep track of
                List<Long> queryTermResultsIDs = new ArrayList<>();
                for (KeywordHit ch : queryTermResults) {
                    queryTermResultsIDs.add(ch.getSolrObjectId());
                }

                List<Long> curTermResults = job.currentKeywordResults(keyword);
                if (curTermResults == null) {
                    job.addKeywordResults(keyword, queryTermResultsIDs);
                    newResults.addResult(keyword, queryTermResults);
                } else {
                    //some AbstractFile hits already exist for this keyword
                    for (KeywordHit res : queryTermResults) {
                        if (!curTermResults.contains(res.getSolrObjectId())) {
                            //add to new results
                            List<KeywordHit> newResultsFs = newResults.getResults(keyword);
                            if (newResultsFs == null) {
                                newResultsFs = new ArrayList<>();
                                newResults.addResult(keyword, newResultsFs);
                            }
                            newResultsFs.add(res);
                            curTermResults.add(res.getSolrObjectId());
                        }
                    }
                }
            }

            return newResults;
        }
    }
}
