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
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.netbeans.api.progress.aggregate.AggregateProgressFactory;
import org.netbeans.api.progress.aggregate.AggregateProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.Cancellable;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;

/**
 *
 */
public final class SearchRunner {
    private static final Logger logger = Logger.getLogger(SearchRunner.class.getName());
    private static SearchRunner instance = null;
    private Map<Long, SearchRunnerJob> jobs = new HashMap<>();
            
    public static synchronized SearchRunner getInstance() {
        if (instance == null) {
            instance = new SearchRunner();
        }
        return instance;
    }
    
    ///@todo Can we get the keyword lists here instead of passing them in?
    public void startJob(long jobId, long dataSourceId, List<KeywordList> keywordLists) {
        if (!jobs.containsKey(jobId)) {
            SearchRunnerJob runnerJob = new SearchRunnerJob(jobId, dataSourceId);
            runnerJob.setKeywordLists(keywordLists);
            jobs.put(jobId, runnerJob);
            
            ///@todo start it up
        }
    }
    
    public void endJob(long jobId) {
        ///@todo remove this comment if this is ok. otherwise we might need to do module counting
        
        ///@todo stuff
        SearchRunnerJob runnerJob = jobs.get(jobId);
        if (runnerJob != null) {
            runnerJob.doFinalSearch();
        }
    }

    private void doFinalSearch() {
        
    }
    
    ///@todo currentResults?
    private class SearchRunnerJob {
        private long jobId;
        private long dataSourceId;
        private List<KeywordList> keywordLists = new ArrayList<>(); // lists currently being searched
        private List<Keyword> keywords; //keywords to search
        private Map<Keyword, List<Long>> currentResults;
        private SearchRunner.Searcher currentSearcher;
        private SearchRunner.Searcher finalSearcher;
        private List<String> keywordListNames = null;
            
        public SearchRunnerJob(long jobId, long dataSourceId) {
            this.jobId = jobId;
            this.dataSourceId = dataSourceId;
        }
        
        public void setKeywordLists(List<KeywordList> keywordLists) {
            this.keywordLists = keywordLists;
            //List<String> keywordListNames = settings.getNamesOfEnabledKeyWordLists();
        }
        
        public List<KeywordList> getKeywordLists() {
            return keywordLists;
        }
        
        public void doFinalSearch() {
            finalSearcher = new SearchRunner.Searcher(keywordListNames, true);
            finalSearcher.execute();
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
        private List<Keyword> keywords; //keywords to search
        private List<String> keywordLists; // lists currently being searched
        private Map<String, KeywordList> keywordToList; //keyword to list name mapping
        private AggregateProgressHandle progressGroup;
        private final Logger logger = Logger.getLogger(SearchRunner.Searcher.class.getName());
        private boolean finalRun = false;
        private Timer searchTimer;
        private IngestServices services = IngestServices.getDefault();
        //private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy
        //private static final Lock searcherLock = rwLock.writeLock();
        
        Searcher(List<String> keywordLists) {
            this.keywordLists = new ArrayList<>(keywordLists);
            this.keywords = new ArrayList<>();
            this.keywordToList = new HashMap<>();
            //keywords are populated as searcher runs
        }

        Searcher(List<String> keywordLists, boolean finalRun) {
            this(keywordLists);
            this.finalRun = finalRun;
        }

        @Override
        protected Object doInBackground() throws Exception {
            if (finalRun) {
                logger.log(Level.INFO, "Pending start of new (final) searcher");
            } else {
                logger.log(Level.INFO, "Pending start of new searcher");
            }

            final String displayName = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.displayName")
                    + (finalRun ? (" - " + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.finalizeMsg")) : "");
            progressGroup = AggregateProgressFactory.createSystemHandle(displayName + (" ("
                    + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.pendingMsg") + ")"), null, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Cancelling the searcher by user.");
                    if (progressGroup != null) {
                        progressGroup.setDisplayName(displayName + " (" + NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.cancelMsg") + "...)");
                    }
                    return SearchRunner.Searcher.this.cancel(true);
                }
            }, null);

            updateKeywords();

            ProgressContributor[] subProgresses = new ProgressContributor[keywords.size()];
            int i = 0;
            for (Keyword keywordQuery : keywords) {
                subProgresses[i] =
                        AggregateProgressFactory.createProgressContributor(keywordQuery.getQuery());
                progressGroup.addContributor(subProgresses[i]);
                i++;
            }

            progressGroup.start();

            //block to ensure previous searcher is completely done with doInBackground()
            //even after previous searcher cancellation, we need to check this
            searcherLock.lock();
            final StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            try {
                logger.log(Level.INFO, "Started a new searcher");
                progressGroup.setDisplayName(displayName);
                //make sure other searchers are not spawned 
                searcherDone = false;
                runSearcher = false;
                if (searchTimer.isRunning()) {
                    searchTimer.stop();
                }

                int keywordsSearched = 0;

                //updateKeywords();

                for (Keyword keywordQuery : keywords) {
                    if (this.isCancelled()) {
                        logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: {0}", keywordQuery.getQuery());
                        return null;
                    }

                    final String queryStr = keywordQuery.getQuery();
                    final KeywordList list = keywordToList.get(queryStr);
                    final String listName = list.getName();

                    //new subProgress will be active after the initial query
                    //when we know number of hits to start() with
                    if (keywordsSearched > 0) {
                        subProgresses[keywordsSearched - 1].finish();
                    }


                    KeywordSearchQuery del = null;

                    boolean isRegex = !keywordQuery.isLiteral();
                    if (isRegex) {
                        del = new TermComponentQuery(keywordQuery);
                    } else {
                        del = new LuceneQuery(keywordQuery);
                        del.escape();
                    }

                    //limit search to currently ingested data sources
                    //set up a filter with 1 or more image ids OR'ed
                    final KeywordQueryFilter dataSourceFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.DATA_SOURCE, curDataSourceIds);
                    del.addFilter(dataSourceFilter);

                    Map<String, List<ContentHit>> queryResult;

                    try {
                        queryResult = del.performQuery();
                    } catch (NoOpenCoreException ex) {
                        logger.log(Level.WARNING, "Error performing query: " + keywordQuery.getQuery(), ex);
                        //no reason to continue with next query if recovery failed
                        //or wait for recovery to kick in and run again later
                        //likely case has closed and threads are being interrupted
                        return null;
                    } catch (CancellationException e) {
                        logger.log(Level.INFO, "Cancel detected, bailing during keyword query: {0}", keywordQuery.getQuery());
                        return null;
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Error performing query: " + keywordQuery.getQuery(), e);
                        continue;
                    }

                    // calculate new results but substracting results already obtained in this ingest
                    // this creates a map of each keyword to the list of unique files that have that hit.  
                    Map<Keyword, List<ContentHit>> newResults = filterResults(queryResult, isRegex);

                    if (!newResults.isEmpty()) {

                        //write results to BB

                        //new artifacts created, to report to listeners
                        Collection<BlackboardArtifact> newArtifacts = new ArrayList<>();

                        //scale progress bar more more granular, per result sub-progress, within per keyword
                        int totalUnits = newResults.size();
                        subProgresses[keywordsSearched].start(totalUnits);
                        int unitProgress = 0;
                        String queryDisplayStr = keywordQuery.getQuery();
                        if (queryDisplayStr.length() > 50) {
                            queryDisplayStr = queryDisplayStr.substring(0, 49) + "...";
                        }
                        subProgresses[keywordsSearched].progress(listName + ": " + queryDisplayStr, unitProgress);


                        /* cycle through the keywords returned -- only one unless it was a regexp */
                        for (final Keyword hitTerm : newResults.keySet()) {
                            //checking for cancellation between results
                            if (this.isCancelled()) {
                                logger.log(Level.INFO, "Cancel detected, bailing before new hit processed for query: {0}", keywordQuery.getQuery());
                                return null;
                            }

                            // update progress display
                            String hitDisplayStr = hitTerm.getQuery();
                            if (hitDisplayStr.length() > 50) {
                                hitDisplayStr = hitDisplayStr.substring(0, 49) + "...";
                            }
                            subProgresses[keywordsSearched].progress(listName + ": " + hitDisplayStr, unitProgress);
                            //subProgresses[keywordsSearched].progress(unitProgress);

                            // this returns the unique files in the set with the first chunk that has a hit
                            Map<AbstractFile, Integer> contentHitsFlattened = ContentHit.flattenResults(newResults.get(hitTerm));
                            for (final AbstractFile hitFile : contentHitsFlattened.keySet()) {

                                // get the snippet for the first hit in the file
                                String snippet;
                                final String snippetQuery = KeywordSearchUtil.escapeLuceneQuery(hitTerm.getQuery());
                                int chunkId = contentHitsFlattened.get(hitFile);
                                try {
                                    snippet = LuceneQuery.querySnippet(snippetQuery, hitFile.getId(), chunkId, isRegex, true);
                                } catch (NoOpenCoreException e) {
                                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e);
                                    //no reason to continue
                                    return null;
                                } catch (Exception e) {
                                    logger.log(Level.WARNING, "Error querying snippet: " + snippetQuery, e);
                                    continue;
                                }

                                // write the blackboard artifact for this keyword in this file
                                KeywordWriteResult written = del.writeToBlackBoard(hitTerm.getQuery(), hitFile, snippet, listName);
                                if (written == null) {
                                    logger.log(Level.WARNING, "BB artifact for keyword hit not written, file: {0}, hit: {1}", new Object[]{hitFile, hitTerm.toString()});
                                    continue;
                                }

                                newArtifacts.add(written.getArtifact());

                                //generate an ingest inbox message for this keyword in this file
                                if (list.getIngestMessages()) {
                                    StringBuilder subjectSb = new StringBuilder();
                                    StringBuilder detailsSb = new StringBuilder();
                                    //final int hitFiles = newResults.size();

                                    if (!keywordQuery.isLiteral()) {
                                        subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExpHitLbl"));
                                    } else {
                                        subjectSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitLbl"));
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
                                    detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.kwHitLThLbl"));
                                    detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>");
                                    detailsSb.append("</tr>");

                                    //preview
                                    attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID());
                                    if (attr != null) {
                                        detailsSb.append("<tr>");
                                        detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.previewThLbl"));
                                        detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>");
                                        detailsSb.append("</tr>");

                                    }

                                    //file
                                    detailsSb.append("<tr>");
                                    detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.fileThLbl"));
                                    detailsSb.append("<td>").append(hitFile.getParentPath()).append(hitFile.getName()).append("</td>");

                                    detailsSb.append("</tr>");


                                    //list
                                    attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());
                                    detailsSb.append("<tr>");
                                    detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.listThLbl"));
                                    detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                                    detailsSb.append("</tr>");

                                    //regex
                                    if (!keywordQuery.isLiteral()) {
                                        attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_REGEXP.getTypeID());
                                        if (attr != null) {
                                            detailsSb.append("<tr>");
                                            detailsSb.append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.regExThLbl"));
                                            detailsSb.append("<td>").append(attr.getValueString()).append("</td>");
                                            detailsSb.append("</tr>");

                                        }
                                    }
                                    detailsSb.append("</table>");

                                    services.postMessage(IngestMessage.createDataMessage(++messageID, KeywordSearchModuleFactory.getModuleName(), subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));
                                }
                            } //for each file hit

                            ++unitProgress;

                        }//for each hit term

                        //update artifact browser
                        if (!newArtifacts.isEmpty()) {
                            services.fireModuleDataEvent(new ModuleDataEvent(KeywordSearchModuleFactory.getModuleName(), BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
                        }
                    } //if has results

                    //reset the status text before it goes away
                    subProgresses[keywordsSearched].progress("");

                    ++keywordsSearched;

                } //for each keyword

            } //end try block
            catch (Exception ex) {
                logger.log(Level.WARNING, "searcher exception occurred", ex);
            } finally {
                try {
                    finalizeSearcher();
                    stopWatch.stop();
                    logger.log(Level.INFO, "Searcher took to run: {0} secs.", stopWatch.getElapsedTimeSecs());
                } finally {
                    searcherLock.unlock();
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
                logger.log(Level.SEVERE, "Error performing keyword search: " + e.getMessage());
                services.postMessage(IngestMessage.createErrorMessage(++messageID, KeywordSearchModuleFactory.getModuleName(), "Error performing keyword search", e.getMessage()));
            } // catch and ignore if we were cancelled
            catch (java.util.concurrent.CancellationException ex) {
            }
        }

        /**
         * Sync-up the updated keywords from the currently used lists in the XML
         */
        private void updateKeywords() {
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

            this.keywords.clear();
            this.keywordToList.clear();

            for (String name : this.keywordLists) {
                KeywordList list = loader.getList(name);
                for (Keyword k : list.getKeywords()) {
                    this.keywords.add(k);
                    this.keywordToList.put(k.getQuery(), list);
                }
            }


        }

        //perform all essential cleanup that needs to be done right AFTER doInBackground() returns
        //without relying on done() method that is not guaranteed to run after background thread completes
        //NEED to call this method always right before doInBackground() returns
        /**
         * Performs the cleanup that needs to be done right AFTER
         * doInBackground() returns without relying on done() method that is not
         * guaranteed to run after background thread completes REQUIRED to call
         * this method always right before doInBackground() returns
         */
        private void finalizeSearcher() {
            logger.log(Level.INFO, "Searcher finalizing");
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressGroup.finish();
                }
            });
            searcherDone = true;  //next currentSearcher can start

            if (finalRun) {
                //this is the final searcher
                logger.log(Level.INFO, "The final searcher in this ingest done.");

                //run module cleanup
                cleanup();
            } else {
                //start counting time for a new searcher to start
                //unless final searcher is pending
                if (finalSearcher == null) {
                    //we need a new Timer object, because restarting previus will not cause firing of the action
                    final int updateIntervalMs = KeywordSearchSettings.getUpdateFrequency().getTime() * 60 * 1000;
                    searchTimer = new Timer(updateIntervalMs, new KeywordSearchIngestModule.SearchTimerAction());
                    searchTimer.start();
                }
            }
        }

        //calculate new results but substracting results already obtained in this ingest
        //update currentResults map with the new results
        private Map<Keyword, List<ContentHit>> filterResults(Map<String, List<ContentHit>> queryResult, boolean isRegex) {
            Map<Keyword, List<ContentHit>> newResults = new HashMap<>();

            for (String termResult : queryResult.keySet()) {
                List<ContentHit> queryTermResults = queryResult.get(termResult);

                //translate to list of IDs that we keep track of
                List<Long> queryTermResultsIDs = new ArrayList<>();
                for (ContentHit ch : queryTermResults) {
                    queryTermResultsIDs.add(ch.getId());
                }

                Keyword termResultK = new Keyword(termResult, !isRegex);
                List<Long> curTermResults = currentResults.get(termResultK);
                if (curTermResults == null) {
                    currentResults.put(termResultK, queryTermResultsIDs);
                    newResults.put(termResultK, queryTermResults);
                } else {
                    //some AbstractFile hits already exist for this keyword
                    for (ContentHit res : queryTermResults) {
                        if (!curTermResults.contains(res.getId())) {
                            //add to new results
                            List<ContentHit> newResultsFs = newResults.get(termResultK);
                            if (newResultsFs == null) {
                                newResultsFs = new ArrayList<>();
                                newResults.put(termResultK, newResultsFs);
                            }
                            newResultsFs.add(res);
                            curTermResults.add(res.getId());
                        }
                    }
                }
            }

            return newResults;

        }
    }    
    
}
