/*
 * Autopsy Forensic Browser
 *
 * Copyright 2014 - 2021 Basis Technology Corp.
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
import javax.annotation.concurrent.GuardedBy;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;

/**
 * Performs periodic and final keyword searches for ingest jobs. Periodic
 * searches are done in background tasks. This represents a careful working
 * around of the contract for IngestModule.process(). Final searches are done
 * synchronously in the calling thread, as required by the contract for
 * IngestModule.shutDown().
 */
final class IngestSearchRunner {

    private static final Logger logger = Logger.getLogger(IngestSearchRunner.class.getName());
    private static IngestSearchRunner instance = null;
    private final IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private long currentUpdateIntervalMs;
    private volatile boolean periodicSearchTaskRunning;
    private volatile Future<?> periodicSearchTaskHandle;
    private final ScheduledThreadPoolExecutor periodicSearchTaskExecutor;
    private static final int NUM_SEARCH_SCHEDULING_THREADS = 1;
    private static final String SEARCH_SCHEDULER_THREAD_NAME = "periodic-search-scheduling-%d";
    private final Map<Long, SearchJobInfo> jobs = new ConcurrentHashMap<>(); // Ingest job ID to search job info 
    private final boolean usingNetBeansGUI = RuntimeProperties.runningWithGUI();

    /*
     * Constructs a singleton object that performs periodic and final keyword
     * searches for ingest jobs. Periodic searches are done in background tasks.
     * This represents a careful working around of the contract for
     * IngestModule.process(). Final searches are done synchronously in the
     * calling thread, as required by the contract for IngestModule.shutDown().
     */
    private IngestSearchRunner() {
        currentUpdateIntervalMs = ((long) KeywordSearchSettings.getUpdateFrequency().getTime()) * 60 * 1000;
        ingester = Ingester.getDefault();
        periodicSearchTaskExecutor = new ScheduledThreadPoolExecutor(NUM_SEARCH_SCHEDULING_THREADS, new ThreadFactoryBuilder().setNameFormat(SEARCH_SCHEDULER_THREAD_NAME).build());
    }

    /**
     * Gets the ingest search runner singleton.
     *
     * @return The ingest search runner.
     */
    public static synchronized IngestSearchRunner getInstance() {
        if (instance == null) {
            instance = new IngestSearchRunner();
        }
        return instance;
    }

    /**
     * Starts the search job for an ingest job.
     *
     * @param jobContext       The ingest job context.
     * @param keywordListNames The names of the keyword search lists for the
     *                         ingest job.
     */
    public synchronized void startJob(IngestJobContext jobContext, List<String> keywordListNames) {
        long jobId = jobContext.getJobId();
        if (jobs.containsKey(jobId) == false) {
            SearchJobInfo jobData = new SearchJobInfo(jobContext, keywordListNames);
            jobs.put(jobId, jobData);
        }

        /*
         * Keep track of the number of keyword search file ingest modules that
         * are doing analysis for the ingest job, i.e., that have called this
         * method. This is needed by endJob().
         */
        jobs.get(jobId).incrementModuleReferenceCount();

        /*
         * Start a periodic search task in the
         */
        if ((jobs.size() > 0) && (periodicSearchTaskRunning == false)) {
            currentUpdateIntervalMs = ((long) KeywordSearchSettings.getUpdateFrequency().getTime()) * 60 * 1000;
            periodicSearchTaskHandle = periodicSearchTaskExecutor.schedule(new PeriodicSearchTask(), currentUpdateIntervalMs, MILLISECONDS);
            periodicSearchTaskRunning = true;
        }
    }

    /**
     * Finishes a search job for an ingest job.
     *
     * @param jobId The ingest job ID.
     */
    public synchronized void endJob(long jobId) {
        /*
         * Only complete the job if this is the last keyword search file ingest
         * module doing annalysis for this job.
         */
        SearchJobInfo job;
        job = jobs.get(jobId);
        if (job == null) {
            return; // RJCTODO: SEVERE
        }
        if (job.decrementModuleReferenceCount() != 0) {
            jobs.remove(jobId);
        }

        /*
         * Commit the index and do the final search. The final search is done in
         * the ingest thread that shutDown() on the keyword search file ingest
         * module, per the contract of IngestModule.shutDwon().
         */
        logger.log(Level.INFO, "Commiting search index before final search for search job {0}", job.getJobId()); //NON-NLS
        commit();
        logger.log(Level.INFO, "Starting final search for search job {0}", job.getJobId()); //NON-NLS        
        doFinalSearch(job);
        logger.log(Level.INFO, "Final search for search job {0} completed", job.getJobId()); //NON-NLS                        

        if (jobs.isEmpty()) {
            cancelPeriodicSearchSchedulingTask();
        }
    }

    /**
     * Stops the search job for an ingest job.
     *
     * @param jobId The ingest job ID.
     */
    public synchronized void stopJob(long jobId) {
        logger.log(Level.INFO, "Stopping search job {0}", jobId); //NON-NLS
        commit();

        SearchJobInfo job;
        job = jobs.get(jobId);
        if (job == null) {
            return;
        }

        /*
         * Request cancellation of the current keyword search, whether it is a
         * preiodic search or a final search.
         */
        IngestSearchRunner.Searcher currentSearcher = job.getCurrentSearcher();
        if ((currentSearcher != null) && (!currentSearcher.isDone())) {
            logger.log(Level.INFO, "Cancelling search job {0}", jobId); //NON-NLS
            currentSearcher.cancel(true);
        }

        jobs.remove(jobId);

        if (jobs.isEmpty()) {
            cancelPeriodicSearchSchedulingTask();
        }
    }

    /**
     * Adds the given keyword list names to the set of keyword lists to be
     * searched by ALL keyword search jobs. This supports adding one or more
     * keyword search lists to ingest jobs already in progress.
     *
     * @param keywordListNames The n ames of the additional keyword lists.
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
     * Commits the Solr index for the current case and publishes an event
     * indicating the current number of indexed items (this is no longer just
     * files).
     */
    private void commit() {
        ingester.commit();

        /*
         * Publish an event advertising the number of indexed items. Note that
         * this is no longer the number of indexed files, since the text of many
         * items in addition to files is indexed.
         */
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            KeywordSearch.fireNumIndexedFilesChange(null, numIndexedFiles);
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Error executing Solr query for number of indexed files", ex); //NON-NLS
        }
    }

    /**
     * Performs the final keyword search for an ingest job. The search is done
     * synchronously, as required by the contract for IngestModule.shutDown().
     *
     * @param job The keyword search job info.
     */
    private void doFinalSearch(SearchJobInfo job) {
        if (!job.getKeywordListNames().isEmpty()) {
            try {
                /*
                 * Wait for any periodic searches being done in a SwingWorker
                 * pool thread to finish.
                 */
                job.waitForCurrentWorker();
                IngestSearchRunner.Searcher finalSearcher = new IngestSearchRunner.Searcher(job, true);
                job.setCurrentSearcher(finalSearcher);
                /*
                 * Do the final search synchronously on the current ingest
                 * thread, per the contract specified
                 */
                finalSearcher.doInBackground();
            } catch (InterruptedException | CancellationException ex) {
                logger.log(Level.INFO, "Final search for search job {0} interrupted or cancelled", job.getJobId()); //NON-NLS
            } catch (Exception ex) {
                logger.log(Level.SEVERE, String.format("Final search for search job %d failed", job.getJobId()), ex); //NON-NLS
            }
        }
    }

    /**
     * Cancels the current periodic search scheduling task.
     */
    private synchronized void cancelPeriodicSearchSchedulingTask() {
        if (periodicSearchTaskHandle != null) {
            logger.log(Level.INFO, "No more search jobs, stopping periodic search scheduling"); //NON-NLS
            periodicSearchTaskHandle.cancel(true);
            periodicSearchTaskRunning = false;
        }
    }

    /**
     * Task that runs in ScheduledThreadPoolExecutor to periodically start and
     * wait for keyword search tasks for each keyword search job in progress.
     * The keyword search tasks for individual ingest jobs are implemented as
     * SwingWorkers to support legacy APIs.
     */
    private final class PeriodicSearchTask implements Runnable {

        @Override
        public void run() {
            /*
             * If there are no more jobs or this task has been cancelled, exit.
             */
            if (jobs.isEmpty() || periodicSearchTaskHandle.isCancelled()) {
                logger.log(Level.INFO, "Periodic search scheduling task has been cancelled, exiting"); //NON-NLS
                periodicSearchTaskRunning = false;
                return;
            }

            /*
             * Commit the Solr index for the current case before doing the
             * searches.
             */
            commit();

            /*
             * Do a keyword search for each ingest job in progress. When the
             * searches are done, recalculate the "hold off" time between
             * searches to prevent back-to-back periodic searches and schedule
             * the nect periodic search task.
             */
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            for (Iterator<Entry<Long, SearchJobInfo>> iterator = jobs.entrySet().iterator(); iterator.hasNext();) {
                SearchJobInfo job = iterator.next().getValue();

                if (periodicSearchTaskHandle.isCancelled()) {
                    logger.log(Level.INFO, "Periodic search scheduling task has been cancelled, exiting"); //NON-NLS
                    periodicSearchTaskRunning = false;
                    return;
                }

                if (!job.getKeywordListNames().isEmpty() && !job.isWorkerRunning()) {
                    logger.log(Level.INFO, "Starting periodic search for search job {0}", job.getJobId());
                    Searcher searcher = new Searcher(job, false);
                    job.setCurrentSearcher(searcher);
                    searcher.execute();
                    job.setWorkerRunning(true);
                    try {
                        searcher.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        logger.log(Level.SEVERE, String.format("Error performing keyword search for ingest job %d", job.getJobId()), ex); //NON-NLS
                        services.postMessage(IngestMessage.createErrorMessage(
                                KeywordSearchModuleFactory.getModuleName(),
                                NbBundle.getMessage(this.getClass(), "SearchRunner.Searcher.done.err.msg"), ex.getMessage()));
                    } catch (java.util.concurrent.CancellationException ex) {
                        logger.log(Level.SEVERE, String.format("Keyword search for ingest job %d cancelled", job.getJobId()), ex); //NON-NLS
                    }
                }
            }
            stopWatch.stop();
            logger.log(Level.INFO, "Periodic searches for all ingest jobs cumulatively took {0} secs", stopWatch.getElapsedTimeSecs()); //NON-NLS
            recalculateUpdateIntervalTime(stopWatch.getElapsedTimeSecs()); // ELDEBUG
            periodicSearchTaskHandle = periodicSearchTaskExecutor.schedule(new PeriodicSearchTask(), currentUpdateIntervalMs, MILLISECONDS);
        }

        /**
         * Sets the time interval between periodic keyword searches to avoid
         * running back-to-back searches. If the most recent round of searches
         * took longer that 1/4 of the current interval, doubles the interval.
         *
         * @param lastSerchTimeSec The time in seconds used to execute the most
         *                         recent round of keword searches.
         */
        private void recalculateUpdateIntervalTime(long lastSerchTimeSec) {
            if (lastSerchTimeSec * 1000 < currentUpdateIntervalMs / 4) {
                return;
            }
            currentUpdateIntervalMs *= 2;
            logger.log(Level.WARNING, "Last periodic search took {0} sec. Increasing search interval to {1} sec", new Object[]{lastSerchTimeSec, currentUpdateIntervalMs / 1000});
        }
    }

    /**
     * A data structure to keep track of the keyword lists, current results, and
     * search running status for an ingest job.
     */
    private class SearchJobInfo {

        private final IngestJobContext jobContext;
        private final long jobId;
        private final long dataSourceId;
        private volatile boolean workerRunning;
        @GuardedBy("this")
        private final List<String> keywordListNames;
        @GuardedBy("this")
        private final Map<Keyword, Set<Long>> currentResults; // Keyword to object IDs of items with hits
        private IngestSearchRunner.Searcher currentSearcher;
        private final AtomicLong moduleReferenceCount = new AtomicLong(0);
        private final Object finalSearchLock = new Object();

        private SearchJobInfo(IngestJobContext jobContext, List<String> keywordListNames) {
            this.jobContext = jobContext;
            jobId = jobContext.getJobId();
            dataSourceId = jobContext.getDataSource().getId();
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
         * Waits for the current search task to complete.
         *
         * @throws InterruptedException
         */
        private void waitForCurrentWorker() throws InterruptedException {
            synchronized (finalSearchLock) {
                while (workerRunning) {
                    logger.log(Level.INFO, String.format("Waiting for previous search task for job %d to finish", jobId)); //NON-NLS
                    finalSearchLock.wait();
                    logger.log(Level.INFO, String.format("Notified previous search task for job %d to finish", jobId)); //NON-NLS
                }
            }
        }

        /**
         * Signals any threads waiting on the current search task to complete.
         */
        private void searchNotify() {
            synchronized (finalSearchLock) {
                workerRunning = false;
                finalSearchLock.notify();
            }
        }
    }

    /*
     * A SwingWorker responsible for searching the Solr index of the current
     * case for the keywords for an ingest job. Keyword hit analysis results are
     * created and posted to the blackboard and notifications are sent to the
     * ingest inbox.
     */
    private final class Searcher extends SwingWorker<Object, Void> {

        /*
         * Searcher has private copies/snapshots of the lists and keywords
         */
        private final SearchJobInfo job;
        private final List<Keyword> keywords; //keywords to search
        private final List<String> keywordListNames; // lists currently being searched
        private final List<KeywordList> keywordLists;
        private final Map<Keyword, KeywordList> keywordToList; //keyword to list name mapping
        @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
        private ProgressHandle progressIndicator;
        private boolean finalRun = false;

        Searcher(SearchJobInfo job, boolean finalRun) {
            this.job = job;
            this.finalRun = finalRun;
            keywordListNames = job.getKeywordListNames();
            keywords = new ArrayList<>();
            keywordToList = new HashMap<>();
            keywordLists = new ArrayList<>();
        }

        @Override
        @Messages("SearchRunner.query.exception.msg=Error performing query:")
        protected Object doInBackground() throws Exception {
            try {
                if (usingNetBeansGUI) {
                    /*
                     * If running in the NetBeans thick client application
                     * version of Autopsy, NetBeans progress handles (i.e.,
                     * progress bars) are used to display search progress in the
                     * lower right hand corner of the main application window.
                     *
                     * A layer of abstraction to allow alternate representations
                     * of progress could be used here, as it is in other places
                     * in the application (see implementations and usage of
                     * org.sleuthkit.autopsy.progress.ProgressIndicator
                     * interface), to better decouple keyword search from the
                     * application's presentation layer.
                     */
                    SwingUtilities.invokeAndWait(() -> {
                        final String displayName = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.displayName")
                                + (finalRun ? (" - " + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.finalizeMsg")) : "");
                        progressIndicator = ProgressHandle.createHandle(displayName, new Cancellable() {
                            @Override
                            public boolean cancel() {
                                if (progressIndicator != null) {
                                    progressIndicator.setDisplayName(displayName + " " + NbBundle.getMessage(this.getClass(), "SearchRunner.doInBackGround.cancelMsg"));
                                }
                                logger.log(Level.INFO, "Search cancelled by user"); //NON-NLS
                                new Thread(() -> {
                                    IngestSearchRunner.Searcher.this.cancel(true);
                                }).start();
                                return true;
                            }
                        });
                        progressIndicator.start();
                        progressIndicator.switchToIndeterminate();
                    });
                }

                updateKeywords();
                for (Keyword keyword : keywords) {
                    if (isCancelled() || job.getJobContext().fileIngestIsCancelled()) {
                        logger.log(Level.INFO, "Cancellation requested, exiting before new keyword processed: {0}", keyword.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    KeywordList keywordList = keywordToList.get(keyword);
                    if (usingNetBeansGUI) {
                        String searchTermStr = keyword.getSearchTerm();
                        if (searchTermStr.length() > 50) {
                            searchTermStr = searchTermStr.substring(0, 49) + "...";
                        }
                        final String progressMessage = keywordList.getName() + ": " + searchTermStr;
                        SwingUtilities.invokeLater(() -> {
                            progressIndicator.progress(progressMessage);
                        });
                    }

                    // Filtering
                    //limit search to currently ingested data sources
                    //set up a filter with 1 or more image ids OR'ed
                    KeywordSearchQuery keywordSearchQuery = KeywordSearchUtil.getQueryForKeyword(keyword, keywordList);
                    KeywordQueryFilter dataSourceFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.DATA_SOURCE, job.getDataSourceId());
                    keywordSearchQuery.addFilter(dataSourceFilter);

                    // Do the actual search
                    QueryResults queryResults;
                    try {
                        queryResults = keywordSearchQuery.performQuery();
                    } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                        logger.log(Level.SEVERE, "Error performing query: " + keyword.getSearchTerm(), ex); //NON-NLS
                        if (usingNetBeansGUI) {
                            final String userMessage = Bundle.SearchRunner_query_exception_msg() + keyword.getSearchTerm();
                            SwingUtilities.invokeLater(() -> {
                                MessageNotifyUtil.Notify.error(userMessage, ex.getCause().getMessage());
                            });
                        }
                        //no reason to continue with next query if recovery failed
                        //or wait for recovery to kick in and run again later
                        //likely case has closed and threads are being interrupted
                        return null;
                    } catch (CancellationException e) {
                        logger.log(Level.INFO, "Cancellation requested, exiting during keyword query: {0}", keyword.getSearchTerm()); //NON-NLS
                        return null;
                    }

                    // Reduce the results of the query to only those hits we
                    // have not already seen. 
                    QueryResults newResults = filterResults(queryResults);

                    if (!newResults.getKeywords().isEmpty()) {
                        // Create blackboard artifacts                
                        newResults.process(this, keywordList.getIngestMessages(), true, job.getJobId());
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, String.format("Error performing keyword search for ingest job %d", job.getJobId()), ex); //NON-NLS
            } finally {
                if (progressIndicator != null) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            progressIndicator.finish();
                            progressIndicator = null;
                        }
                    });
                }
                // In case a thread is waiting on this worker to be done
                job.searchNotify();
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
