/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 - 2017 Basis Technology Corp.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
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
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;

/**
 * Singleton keyword search manager: Launches search threads for each job and
 * performs commits, both on timed intervals.
 */
final class IngestSearchRunner {

    private static final Logger logger = Logger.getLogger(IngestSearchRunner.class.getName());
    private static IngestSearchRunner instance = null;
    private IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private long currentUpdateIntervalMs;
    private volatile boolean periodicSearchTaskRunning = false;
    private Future<?> jobProcessingTaskFuture;
    private final ScheduledThreadPoolExecutor jobProcessingExecutor;
    private static final int NUM_SEARCH_SCHEDULING_THREADS = 1;
    private static final String SEARCH_SCHEDULER_THREAD_NAME = "periodic-search-scheduler-%d";

    // maps a jobID to the search
    private Map<Long, SearchJobInfo> jobs = new ConcurrentHashMap<>();

    IngestSearchRunner() {
        currentUpdateIntervalMs = ((long) KeywordSearchSettings.getUpdateFrequency().getTime()) * 60 * 1000;
        ingester = Ingester.getDefault();
        jobProcessingExecutor = new ScheduledThreadPoolExecutor(NUM_SEARCH_SCHEDULING_THREADS, new ThreadFactoryBuilder().setNameFormat(SEARCH_SCHEDULER_THREAD_NAME).build());
    }

    /**
     *
     * @return the singleton object
     */
    public static synchronized IngestSearchRunner getInstance() {
        if (instance == null) {
            instance = new IngestSearchRunner();
        }
        return instance;
    }

    /**
     *
     * @param jobContext
     * @param keywordListNames
     */
    public synchronized void startJob(IngestJobContext jobContext, List<String> keywordListNames) {
        long jobId = jobContext.getJobId();
        if (jobs.containsKey(jobId) == false) {
            logger.log(Level.INFO, "Adding job {0}", jobId); //NON-NLS
            SearchJobInfo jobData = new SearchJobInfo(jobContext, keywordListNames);
            jobs.put(jobId, jobData);
        }

        // keep track of how many threads / module instances from this job have asked for this
        jobs.get(jobId).incrementModuleReferenceCount();

        // start the timer, if needed
        if ((jobs.size() > 0) && (periodicSearchTaskRunning == false)) {
            // reset the default periodic search frequency to the user setting
            logger.log(Level.INFO, "Resetting periodic search time out to default value"); //NON-NLS
            currentUpdateIntervalMs = ((long) KeywordSearchSettings.getUpdateFrequency().getTime()) * 60 * 1000;
            jobProcessingTaskFuture = jobProcessingExecutor.schedule(new PeriodicSearchTask(), currentUpdateIntervalMs, MILLISECONDS);
            periodicSearchTaskRunning = true;
        }
    }

    /**
     * Perform normal finishing of searching for this job, including one last
     * commit and search. Blocks until the final search is complete.
     *
     * @param jobId
     */
    public synchronized void endJob(long jobId) {
        SearchJobInfo job;
        boolean readyForFinalSearch = false;
        job = jobs.get(jobId);
        if (job == null) {
            return;
        }

        // Only do final search if this is the last module/thread in this job to call endJob()
        if (job.decrementModuleReferenceCount() == 0) {
            jobs.remove(jobId);
            readyForFinalSearch = true;
        }

        if (readyForFinalSearch) {
            logger.log(Level.INFO, "Commiting search index before final search for search job {0}", job.getJobId()); //NON-NLS
            commit();
            doFinalSearch(job); //this will block until it's done

            // new jobs could have been added while we were doing final search
            if (jobs.isEmpty()) {
                // no more jobs left. stop the PeriodicSearchTask. 
                // A new one will be created for future jobs. 
                logger.log(Level.INFO, "No more search jobs. Stopping periodic search task"); //NON-NLS
                periodicSearchTaskRunning = false;
                jobProcessingTaskFuture.cancel(true);
            }
        }
    }

    /**
     * Immediate stop and removal of job from SearchRunner. Cancels the
     * associated search worker if it's still running.
     *
     * @param jobId
     */
    public synchronized void stopJob(long jobId) {
        logger.log(Level.INFO, "Stopping search job {0}", jobId); //NON-NLS
        commit();

        SearchJobInfo job;
        job = jobs.get(jobId);
        if (job == null) {
            return;
        }

        //stop currentSearcher
        IngestSearchRunner.Searcher currentSearcher = job.getCurrentSearcher();
        if ((currentSearcher != null) && (!currentSearcher.isDone())) {
            logger.log(Level.INFO, "Cancelling search job {0}", jobId); //NON-NLS
            currentSearcher.cancel(true);
        }

        jobs.remove(jobId);

        if (jobs.isEmpty()) {
            // no more jobs left. stop the PeriodicSearchTask. 
            // A new one will be created for future jobs. 
            logger.log(Level.INFO, "No more search jobs. Stopping periodic search task"); //NON-NLS
            periodicSearchTaskRunning = false;
            jobProcessingTaskFuture.cancel(true);
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
            logger.log(Level.SEVERE, "Error executing Solr query to check number of indexed files", ex); //NON-NLS
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
        logger.log(Level.INFO, "Starting final search for search job {0}", job.getJobId());         //NON-NLS
        if (!job.getKeywordListNames().isEmpty()) {
            try {
                // In case this job still has a worker running, wait for it to finish
                logger.log(Level.INFO, "Checking for previous search for search job {0} before executing final search", job.getJobId()); //NON-NLS
                job.waitForCurrentWorker();

                IngestSearchRunner.Searcher finalSearcher = new IngestSearchRunner.Searcher(job, true);
                job.setCurrentSearcher(finalSearcher); //save the ref
                logger.log(Level.INFO, "Kicking off final search for search job {0}", job.getJobId()); //NON-NLS
                finalSearcher.execute(); //start thread

                // block until the search is complete
                logger.log(Level.INFO, "Waiting for final search for search job {0}", job.getJobId()); //NON-NLS
                finalSearcher.get();
                logger.log(Level.INFO, "Final search for search job {0} completed", job.getJobId()); //NON-NLS

            } catch (InterruptedException | CancellationException ex) {
                logger.log(Level.INFO, "Final search for search job {0} interrupted or cancelled", job.getJobId()); //NON-NLS
            } catch (ExecutionException ex) {
                logger.log(Level.SEVERE, String.format("Final search for search job %d failed", job.getJobId()), ex); //NON-NLS
            }
        }
    }

    /**
     * Task to perform periodic searches for each job (does a single index commit first)
     */
    private final class PeriodicSearchTask implements Runnable {

        private final Logger logger = Logger.getLogger(IngestSearchRunner.PeriodicSearchTask.class.getName());

        @Override
        public void run() {
            // If no jobs then cancel the task. If more job(s) come along, a new task will start up.
            if (jobs.isEmpty() || jobProcessingTaskFuture.isCancelled()) {
                logger.log(Level.INFO, "Exiting periodic search task"); //NON-NLS
                periodicSearchTaskRunning = false;
                return;
            }

            commit();

            logger.log(Level.INFO, "Starting periodic searches");
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            // NOTE: contents of "jobs" ConcurrentHashMap can be modified in stopJob() and endJob() while we are inside this loop
            for (Iterator<Entry<Long, SearchJobInfo>> iterator = jobs.entrySet().iterator(); iterator.hasNext();) {
                SearchJobInfo job = iterator.next().getValue();

                if (jobProcessingTaskFuture.isCancelled()) {
                    logger.log(Level.INFO, "Search has been cancelled. Exiting periodic search task."); //NON-NLS
                    periodicSearchTaskRunning = false;
                    return;
                }

                // If no lists or the worker is already running then skip it
                if (!job.getKeywordListNames().isEmpty() && !job.isWorkerRunning()) {
                    // Spawn a search thread for each job
                    logger.log(Level.INFO, "Executing periodic search for search job {0}", job.getJobId());
                    Searcher searcher = new Searcher(job);  // SwingWorker
                    job.setCurrentSearcher(searcher); //save the ref
                    searcher.execute(); //start thread
                    job.setWorkerRunning(true);

                    try {
                        // wait for the searcher to finish
                        searcher.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.log(Level.SEVERE, "Error performing keyword search: {0}", ex.getMessage()); //NON-NLS
                        services.postMessage(IngestMessage.createErrorMessage(KeywordSearchModuleFactory.getModuleName(),
                                NbBundle.getMessage(this.getClass(),
                                        "SearchRunner.Searcher.done.err.msg"), ex.getMessage()));
                    }// catch and ignore if we were cancelled
                      catch (java.util.concurrent.CancellationException ex) {
                    }
                }
            }
            stopWatch.stop();
            logger.log(Level.INFO, "All periodic searches cumulatively took {0} secs", stopWatch.getElapsedTimeSecs()); //NON-NLS
            
            // calculate "hold off" time
            recalculateUpdateIntervalTime(stopWatch.getElapsedTimeSecs()); // ELDEBUG
            
            // schedule next PeriodicSearchTask
            jobProcessingTaskFuture = jobProcessingExecutor.schedule(new PeriodicSearchTask(), currentUpdateIntervalMs, MILLISECONDS);
            
            // exit this thread
            return;
        }
        
        
        private void recalculateUpdateIntervalTime(long lastSerchTimeSec) {
            // If periodic search takes more than 1/4 of the current periodic search interval, then double the search interval
            if (lastSerchTimeSec * 1000 < currentUpdateIntervalMs / 4) {
                return;
            }
            // double the search interval
            currentUpdateIntervalMs = currentUpdateIntervalMs * 2;
            logger.log(Level.WARNING, "Last periodic search took {0} sec. Increasing search interval to {1} sec", new Object[]{lastSerchTimeSec, currentUpdateIntervalMs/1000});
            return;
        }
    }

    /**
     * Data structure to keep track of keyword lists, current results, and
     * search running status for each jobid
     */
    private class SearchJobInfo {

        private final IngestJobContext jobContext;
        private final long jobId;
        private final long dataSourceId;
        // mutable state:
        private volatile boolean workerRunning;
        private List<String> keywordListNames; //guarded by SearchJobInfo.this

        // Map of keyword to the object ids that contain a hit
        private Map<Keyword, Set<Long>> currentResults; //guarded by SearchJobInfo.this
        private IngestSearchRunner.Searcher currentSearcher;
        private AtomicLong moduleReferenceCount = new AtomicLong(0);
        private final Object finalSearchLock = new Object(); //used for a condition wait

        private SearchJobInfo(IngestJobContext jobContext, List<String> keywordListNames) {
            this.jobContext = jobContext;
            this.jobId = jobContext.getJobId();
            this.dataSourceId = jobContext.getDataSource().getId();
            this.keywordListNames = new ArrayList<>(keywordListNames);
            currentResults = new HashMap<>();
            workerRunning = false;
            currentSearcher = null;
        }

        private IngestJobContext getJobContext() {
            return jobContext;
        }

        private long getJobId() {
            return jobId;
        }

        private long getDataSourceId() {
            return dataSourceId;
        }

        private synchronized List<String> getKeywordListNames() {
            return new ArrayList<>(keywordListNames);
        }

        private synchronized void addKeywordListName(String keywordListName) {
            if (!keywordListNames.contains(keywordListName)) {
                keywordListNames.add(keywordListName);
            }
        }

        private synchronized Set<Long> currentKeywordResults(Keyword k) {
            return currentResults.get(k);
        }

        private synchronized void addKeywordResults(Keyword k, Set<Long> resultsIDs) {
            currentResults.put(k, resultsIDs);
        }

        private boolean isWorkerRunning() {
            return workerRunning;
        }

        private void setWorkerRunning(boolean flag) {
            workerRunning = flag;
        }

        private synchronized IngestSearchRunner.Searcher getCurrentSearcher() {
            return currentSearcher;
        }

        private synchronized void setCurrentSearcher(IngestSearchRunner.Searcher searchRunner) {
            currentSearcher = searchRunner;
        }

        private void incrementModuleReferenceCount() {
            moduleReferenceCount.incrementAndGet();
        }

        private long decrementModuleReferenceCount() {
            return moduleReferenceCount.decrementAndGet();
        }

        /**
         * In case this job still has a worker running, wait for it to finish
         *
         * @throws InterruptedException
         */
        private void waitForCurrentWorker() throws InterruptedException {
            synchronized (finalSearchLock) {
                while (workerRunning) {
                    logger.log(Level.INFO, "Waiting for previous worker to finish"); //NON-NLS
                    finalSearchLock.wait(); //wait() releases the lock
                    logger.log(Level.INFO, "Notified previous worker finished"); //NON-NLS
                }
            }
        }

        /**
         * Unset workerRunning and wake up thread(s) waiting on finalSearchLock
         */
        private void searchNotify() {
            synchronized (finalSearchLock) {
                logger.log(Level.INFO, "Notifying after finishing search"); //NON-NLS
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
        private Map<Keyword, KeywordList> keywordToList; //keyword to list name mapping
        private AggregateProgressHandle progressGroup;
        private final Logger logger = Logger.getLogger(IngestSearchRunner.Searcher.class.getName());
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
                    progressGroup.finish();
                    return IngestSearchRunner.Searcher.this.cancel(true);
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

                for (Keyword keyword : keywords) {
                    if (this.isCancelled() || this.job.getJobContext().fileIngestIsCancelled()) {
                        logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: {0}", keyword.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    final KeywordList keywordList = keywordToList.get(keyword);

                    //new subProgress will be active after the initial query
                    //when we know number of hits to start() with
                    if (keywordsSearched > 0) {
                        subProgresses[keywordsSearched - 1].finish();
                    }

                    KeywordSearchQuery keywordSearchQuery = KeywordSearchUtil.getQueryForKeyword(keyword, keywordList);

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
                        logger.log(Level.SEVERE, "Error performing query: " + keyword.getSearchTerm(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(Bundle.SearchRunner_query_exception_msg() + keyword.getSearchTerm(), ex.getCause().getMessage());
                        //no reason to continue with next query if recovery failed
                        //or wait for recovery to kick in and run again later
                        //likely case has closed and threads are being interrupted
                        return null;
                    } catch (CancellationException e) {
                        logger.log(Level.INFO, "Cancel detected, bailing during keyword query: {0}", keyword.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    // Reduce the results of the query to only those hits we
                    // have not already seen. 
                    QueryResults newResults = filterResults(queryResults);

                    if (!newResults.getKeywords().isEmpty()) {

                        // Write results to BB
                        //scale progress bar more more granular, per result sub-progress, within per keyword
                        int totalUnits = newResults.getKeywords().size();
                        subProgresses[keywordsSearched].start(totalUnits);
                        int unitProgress = 0;
                        String queryDisplayStr = keyword.getSearchTerm();
                        if (queryDisplayStr.length() > 50) {
                            queryDisplayStr = queryDisplayStr.substring(0, 49) + "...";
                        }
                        subProgresses[keywordsSearched].progress(keywordList.getName() + ": " + queryDisplayStr, unitProgress);

                        // Create blackboard artifacts                
                        newResults.process(null, subProgresses[keywordsSearched], this, keywordList.getIngestMessages(), true);

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
                    logger.log(Level.INFO, "Searcher took {0} secs to run (final = {1})", new Object[]{stopWatch.getElapsedTimeSecs(), this.finalRun}); //NON-NLS
                } finally {
                    // In case a thread is waiting on this worker to be done
                    job.searchNotify();
                }
            }

            return null;
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
                    keywordToList.put(k, list);
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

        /**
         * This method filters out all of the hits found in earlier periodic
         * searches and returns only the results found by the most recent
         * search.
         *
         * This method will only return hits for objects for which we haven't
         * previously seen a hit for the keyword.
         *
         * @param queryResult The results returned by a keyword search.
         *
         * @return A unique set of hits found by the most recent search for
         *         objects that have not previously had a hit. The hits will be
         *         for the lowest numbered chunk associated with the object.
         *
         */
        private QueryResults filterResults(QueryResults queryResult) {

            // Create a new (empty) QueryResults object to hold the most recently
            // found hits.
            QueryResults newResults = new QueryResults(queryResult.getQuery());

            // For each keyword represented in the results.
            for (Keyword keyword : queryResult.getKeywords()) {
                // These are all of the hits across all objects for the most recent search.
                // This may well include duplicates of hits we've seen in earlier periodic searches.
                List<KeywordHit> queryTermResults = queryResult.getResults(keyword);

                // Sort the hits for this keyword so that we are always 
                // guaranteed to return the hit for the lowest chunk.
                Collections.sort(queryTermResults);

                // This will be used to build up the hits we haven't seen before
                // for this keyword.
                List<KeywordHit> newUniqueHits = new ArrayList<>();

                // Get the set of object ids seen in the past by this searcher
                // for the given keyword.
                Set<Long> curTermResults = job.currentKeywordResults(keyword);
                if (curTermResults == null) {
                    // We create a new empty set if we haven't seen results for
                    // this keyword before.
                    curTermResults = new HashSet<>();
                }

                // For each hit for this keyword.
                for (KeywordHit hit : queryTermResults) {
                    if (curTermResults.contains(hit.getSolrObjectId())) {
                        // Skip the hit if we've already seen a hit for
                        // this keyword in the object.
                        continue;
                    }

                    // We haven't seen the hit before so add it to list of new
                    // unique hits.
                    newUniqueHits.add(hit);

                    // Add the object id to the results we've seen for this
                    // keyword.
                    curTermResults.add(hit.getSolrObjectId());
                }

                // Update the job with the list of objects for which we have
                // seen hits for the current keyword.
                job.addKeywordResults(keyword, curTermResults);

                // Add the new hits for the current keyword into the results
                // to be returned.
                newResults.addResult(keyword, newUniqueHits);
            }

            return newResults;
        }
    }
}
