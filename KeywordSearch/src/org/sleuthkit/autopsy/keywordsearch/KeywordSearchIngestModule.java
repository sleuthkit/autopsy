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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.apache.tika.Tika;
import org.netbeans.api.progress.aggregate.AggregateProgressFactory;
import org.netbeans.api.progress.aggregate.AggregateProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.Cancellable;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.PipelineContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAbstractFile;
import org.sleuthkit.autopsy.ingest.IngestModuleInit;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskData.FileKnown;

/**
 * An ingest module on a file level Performs indexing of allocated and Solr
 * supported files, string extraction and indexing of unallocated and not Solr
 * supported files Index commit is done periodically (determined by user set
 * ingest update interval) Runs a periodic keyword / regular expression search
 * on currently configured lists for ingest and writes results to blackboard
 * Reports interesting events to Inbox and to viewers
 *
 * Registered as a module in layer.xml
 */
public final class KeywordSearchIngestModule extends IngestModuleAbstractFile {

    enum UpdateFrequency {

        FAST(20),
        AVG(10),
        SLOW(5),
        SLOWEST(1),
        DEFAULT(5);
        private final int time;

        UpdateFrequency(int time) {
            this.time = time;
        }

        int getTime() {
            return time;
        }
    };
    private static final Logger logger = Logger.getLogger(KeywordSearchIngestModule.class.getName());
    public static final String MODULE_NAME = NbBundle.getMessage(KeywordSearchIngestModule.class,
                                                                 "KeywordSearchIngestModule.moduleName");
    public static final String MODULE_DESCRIPTION = NbBundle.getMessage(KeywordSearchIngestModule.class,
                                                                        "KeywordSearchIngestModule.moduleDescription");
    final public static String MODULE_VERSION = Version.getVersion();
    private static KeywordSearchIngestModule instance = null;
    private IngestServices services;
    private Ingester ingester = null;
    private volatile boolean commitIndex = false; //whether to commit index next time
    private volatile boolean runSearcher = false; //whether to run searcher next time
    private List<Keyword> keywords; //keywords to search
    private List<String> keywordLists; // lists currently being searched
    private Map<String, KeywordSearchListsAbstract.KeywordSearchList> keywordToList; //keyword to list name mapping
    private Timer commitTimer;
    private Timer searchTimer;
    private Indexer indexer;
    private Searcher currentSearcher;
    private Searcher finalSearcher;
    private volatile boolean searcherDone = true; //mark as done, until it's inited
    private Map<Keyword, List<Long>> currentResults;
    //only search images from current ingest, not images previously ingested/indexed
    //accessed read-only by searcher thread
    private Set<Long> curDataSourceIds;
    private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy
    private static final Lock searcherLock = rwLock.writeLock();
    private volatile int messageID = 0;
    private boolean processedFiles;
    private volatile boolean finalSearcherDone = true;  //mark as done, until it's inited
    private final String hashDBModuleName = NbBundle
            .getMessage(this.getClass(), "KeywordSearchIngestModule.hashDbModuleName"); //NOTE this needs to match the HashDB module getName()
    private SleuthkitCase caseHandle = null;
    private static List<AbstractFileExtract> textExtractors;
    private static AbstractFileStringExtract stringExtractor;
    private boolean initialized = false;
    private KeywordSearchIngestSimplePanel simpleConfigPanel;
    private KeywordSearchConfigurationPanel advancedConfigPanel;
    private Tika tikaFormatDetector;
    

    private enum IngestStatus {
        TEXT_INGESTED,   /// Text was extracted by knowing file type and text_ingested
        STRINGS_INGESTED, ///< Strings were extracted from file 
        METADATA_INGESTED,   ///< No content, so we just text_ingested metadata
        SKIPPED_ERROR_INDEXING, ///< File was skipped because index engine had problems
        SKIPPED_ERROR_TEXTEXTRACT, ///< File was skipped because of text extraction issues
        SKIPPED_ERROR_IO    ///< File was skipped because of IO issues reading it
    };
    private Map<Long, IngestStatus> ingestStatus;

    //private constructor to ensure singleton instance 
    private KeywordSearchIngestModule() {
    }

    /**
     * Returns singleton instance of the module, creates one if needed
     *
     * @return instance of the module
     */
    public static synchronized KeywordSearchIngestModule getDefault() {
        if (instance == null) {
            instance = new KeywordSearchIngestModule();
        }
        return instance;
    }

    @Override
    public ProcessResult process(PipelineContext<IngestModuleAbstractFile> pipelineContext, AbstractFile abstractFile) {

        if (initialized == false) //error initializing indexing/Solr
        {
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: " + abstractFile.getName());
            ingestStatus.put(abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            return ProcessResult.OK;
        }
        try {
            //add data source id of the file to the set, keeping track of images being ingested
            final long fileSourceId = caseHandle.getFileDataSource(abstractFile);
            curDataSourceIds.add(fileSourceId);

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting image id of file processed by keyword search: " + abstractFile.getName(), ex);
        }
        
        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
            //skip indexing of virtual dirs (no content, no real name) - will index children files
            return ProcessResult.OK;
        }

        //check if we should index meta-data only when 1) it is known 2) HashDb module errored on it
        if (services.getAbstractFileModuleResult(hashDBModuleName) == IngestModuleAbstractFile.ProcessResult.ERROR) {
            indexer.indexFile(abstractFile, false);
            //notify depending module that keyword search (would) encountered error for this file
            ingestStatus.put(abstractFile.getId(), IngestStatus.SKIPPED_ERROR_IO);
            return ProcessResult.ERROR;
        } 
        else if (KeywordSearchSettings.getSkipKnown() && abstractFile.getKnown().equals(FileKnown.KNOWN)) {
            //index meta-data only
            indexer.indexFile(abstractFile, false);
            return ProcessResult.OK;
        }

        processedFiles = true;        

        //check if it's time to commit after previous processing
        checkRunCommitSearch();

        //index the file and content (if the content is supported)
        indexer.indexFile(abstractFile, true);

        return ProcessResult.OK;
    }

    /**
     * After all files are ingested, execute final index commit and final search
     * Cleanup resources, threads, timers
     */
    @Override
    public void complete() {
        if (initialized == false) {
            return;
        }

        //logger.log(Level.INFO, "complete()");
        commitTimer.stop();

        //NOTE, we let the 1 before last searcher complete fully, and enqueue the last one

        //cancel searcher timer, ensure unwanted searcher does not start 
        //before we start the final one
        if (searchTimer.isRunning()) {
            searchTimer.stop();
        }
        runSearcher = false;

        logger.log(Level.INFO, "Running final index commit and search");
        //final commit
        commit();

        postIndexSummary();

        //run one last search as there are probably some new files committed
        if (keywordLists != null && !keywordLists.isEmpty() && processedFiles == true) {
            finalSearcher = new Searcher(keywordLists, true); //final searcher run
            finalSearcher.execute();
        } else {
            finalSearcherDone = true;
        }

        //log number of files / chunks in index
        //signal a potential change in number of text_ingested files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            final int numIndexedChunks = KeywordSearch.getServer().queryNumIndexedChunks();
            logger.log(Level.INFO, "Indexed files count: " + numIndexedFiles);
            logger.log(Level.INFO, "Indexed file chunks count: " + numIndexedChunks);
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files/chunks: ", ex);
        } catch (KeywordSearchModuleException se) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files/chunks: ", se);
        }

        //cleanup done in final searcher

        //postSummary();
    }

    /**
     * Handle stop event (ingest interrupted) Cleanup resources, threads, timers
     */
    @Override
    public void stop() {
        logger.log(Level.INFO, "stop()");

        //stop timer
        commitTimer.stop();
        //stop currentSearcher
        if (currentSearcher != null) {
            currentSearcher.cancel(true);
        }

        //cancel searcher timer, ensure unwanted searcher does not start 
        if (searchTimer.isRunning()) {
            searchTimer.stop();
        }
        runSearcher = false;
        finalSearcherDone = true;


        //commit uncommited files, don't search again
        commit();

        //postSummary();

        cleanup();
    }

    /**
     * Common cleanup code when module stops or final searcher completes
     */
    private void cleanup() {
        ingestStatus.clear();
        currentResults.clear();
        curDataSourceIds.clear();
        currentSearcher = null;
        //finalSearcher = null; //do not collect, might be finalizing

        commitTimer.stop();
        searchTimer.stop();
        commitTimer = null;
        //searchTimer = null; // do not collect, final searcher might still be running, in which case it throws an exception

        textExtractors.clear();
        textExtractors = null;
        stringExtractor = null;

        keywords.clear();
        keywordLists.clear();
        keywordToList.clear();

        tikaFormatDetector = null;

        initialized = false;
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
    public String getVersion() {
        return MODULE_VERSION;
    }

    /**
     * Initializes the module for new ingest run Sets up threads, timers,
     * retrieves settings, keyword lists to run on
     *
     */
    @Override
    public void init(IngestModuleInit initContext) throws IngestModuleException {
        logger.log(Level.INFO, "init()");
        services = IngestServices.getDefault();
        initialized = false;

        caseHandle = Case.getCurrentCase().getSleuthkitCase();

        tikaFormatDetector = new Tika();

        ingester = Server.getIngester();

        final Server server = KeywordSearch.getServer();
        try {
            if (!server.isRunning()) {
                String msg = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.badInitMsg");
                logger.log(Level.SEVERE, msg);
                String details = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.tryStopSolrMsg", msg);
                services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
                throw new IngestModuleException(msg);
            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error checking if Solr server is running while initializing ingest", ex);
            //this means Solr is not properly initialized
            String msg = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.badInitMsg");
            String details = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.tryStopSolrMsg", msg);
            services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
            throw new IngestModuleException(msg);
        }
        try {
            // make an actual query to verify that server is responding
            // we had cases where getStatus was OK, but the connection resulted in a 404
            server.queryNumIndexedDocuments();
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            throw new IngestModuleException("Error connecting to SOLR server: " + ex.getMessage());
        }

        //initialize extractors
        stringExtractor = new AbstractFileStringExtract();
        stringExtractor.setScripts(KeywordSearchSettings.getStringExtractScripts());
        stringExtractor.setOptions(KeywordSearchSettings.getStringExtractOptions());


        //log the scripts used for debugging
        final StringBuilder sbScripts = new StringBuilder();
        for (SCRIPT s : KeywordSearchSettings.getStringExtractScripts()) {
            sbScripts.append(s.name()).append(" ");
        }
        logger.log(Level.INFO, "Using string extract scripts: " + sbScripts.toString());

        textExtractors = new ArrayList<AbstractFileExtract>();
        //order matters, more specific extractors first
        textExtractors.add(new AbstractFileHtmlExtract());
        textExtractors.add(new AbstractFileTikaTextExtract());


        ingestStatus = new HashMap<Long, IngestStatus>();

        keywords = new ArrayList<Keyword>();
        keywordLists = new ArrayList<String>();
        keywordToList = new HashMap<String, KeywordSearchListsAbstract.KeywordSearchList>();

        initKeywords();

        if (keywords.isEmpty() || keywordLists.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageID, instance, NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.noKwInLstMsg"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.onlyIdxKwSkipMsg")));
        }

        processedFiles = false;
        finalSearcherDone = false;
        searcherDone = true; //make sure to start the initial currentSearcher
        //keeps track of all results per run not to repeat reporting the same hits
        currentResults = new HashMap<Keyword, List<Long>>();

        curDataSourceIds = new HashSet<Long>();

        indexer = new Indexer();
        
        final int updateIntervalMs = KeywordSearchSettings.getUpdateFrequency().getTime() * 60 * 1000;
        logger.log(Level.INFO, "Using commit interval (ms): " + updateIntervalMs);
        logger.log(Level.INFO, "Using searcher interval (ms): " + updateIntervalMs);

        commitTimer = new Timer(updateIntervalMs, new CommitTimerAction());
        searchTimer = new Timer(updateIntervalMs, new SearchTimerAction());

        initialized = true;

        commitTimer.start();
        searchTimer.start();
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
    public javax.swing.JPanel getSimpleConfiguration(String context) {
        KeywordSearchListsXML.getCurrent().reload();
        
        if (null == simpleConfigPanel) {
           simpleConfigPanel = new KeywordSearchIngestSimplePanel();  
        }
        else {
            simpleConfigPanel.load();
        }
        
        return simpleConfigPanel;
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration(String context) {
        if (advancedConfigPanel == null) {
            advancedConfigPanel = new KeywordSearchConfigurationPanel();
        }
        
        advancedConfigPanel.load();
        return advancedConfigPanel;
    }

    @Override
    public void saveAdvancedConfiguration() {
        if (advancedConfigPanel != null) {
            advancedConfigPanel.store();
        }
        
        if (simpleConfigPanel != null) {
            simpleConfigPanel.load();
        }
    }

    @Override
    public void saveSimpleConfiguration() {
        KeywordSearchListsXML.getCurrent().save();
    }

    /**
     * The modules maintains background threads, return true if background
     * threads are running or there are pending tasks to be run in the future,
     * such as the final search post-ingest completion
     *
     * @return
     */
    @Override
    public boolean hasBackgroundJobsRunning() {
        if ((currentSearcher != null && searcherDone == false)
                || (finalSearcherDone == false)) {
            return true;
        } else {
            return false;
        }

    }

    /**
     * Commits index and notifies listeners of index update
     */
    private void commit() {
        if (initialized) {
            logger.log(Level.INFO, "Commiting index");
            ingester.commit();
            logger.log(Level.INFO, "Index comitted");
            //signal a potential change in number of text_ingested files
            indexChangeNotify();
        }
    }

    /**
     * Posts inbox message with summary of text_ingested files
     */
    private void postIndexSummary() {
        int text_ingested = 0;
        int metadata_ingested = 0;
        int strings_ingested = 0;
        int error_text = 0;
        int error_index = 0;
        int error_io = 0;
        for (IngestStatus s : ingestStatus.values()) {
            switch (s) {
                case TEXT_INGESTED:
                    ++text_ingested;
                    break;
                case METADATA_INGESTED:
                    ++metadata_ingested;
                    break;
                case STRINGS_INGESTED:
                    ++strings_ingested;
                    break;
                case SKIPPED_ERROR_TEXTEXTRACT:
                    error_text++;
                    break;
                case SKIPPED_ERROR_INDEXING:
                    error_index++;
                    break;
                case SKIPPED_ERROR_IO:
                    error_io++;
                    break;
                default:
                    ;
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append("<table border=0><tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.knowFileHeaderLbl")).append("</td><td>").append(text_ingested).append("</td></tr>");
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.fileGenStringsHead")).append("</td><td>").append(strings_ingested).append("</td></tr>");
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.mdOnlyLbl")).append("</td><td>").append(metadata_ingested).append("</td></tr>");
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.idxErrLbl")).append("</td><td>").append(error_index).append("</td></tr>");
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.errTxtLbl")).append("</td><td>").append(error_text).append("</td></tr>");
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.errIoLbl")).append("</td><td>").append(error_io).append("</td></tr>");
        msg.append("</table>");
        String indexStats = msg.toString();
        logger.log(Level.INFO, "Keyword Indexing Completed: " + indexStats);
        services.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxResultsLbl"), indexStats));
        if (error_index > 0) {
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxErrsTitle"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxErrMsgFiles", error_index));
        }
        else if (error_io + error_text > 0) {
            MessageNotifyUtil.Notify.warn(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxWarnMsgTitle"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.idxErrReadFilesMsg"));
        }
    }

    /**
     * Helper method to notify listeners on index update
     */
    private void indexChangeNotify() {
        //signal a potential change in number of text_ingested files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            KeywordSearch.fireNumIndexedFilesChange(null, new Integer(numIndexedFiles));
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files: ", ex);
        } catch (KeywordSearchModuleException se) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files: ", se);
        }
    }

    /**
     * Initialize the keyword search lists and associated keywords from the XML
     * loader Use the lists to ingest that are set in the permanent XML
     * configuration
     */
    private void initKeywords() {
        addKeywordLists(null);
    }

    /**
     * If ingest is ongoing, this will add additional keyword search lists to
     * the ongoing ingest The lists to add may be temporary and not necessary
     * set to be added to ingest permanently in the XML configuration. The lists
     * will be reset back to original (permanent configuration state) on the
     * next ingest.
     *
     * @param listsToAdd lists to add temporarily to the ongoing ingest
     */
    void addKeywordLists(List<String> listsToAdd) {
        KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

        keywords.clear();
        keywordLists.clear();
        keywordToList.clear();

        StringBuilder sb = new StringBuilder();

        for (KeywordSearchListsAbstract.KeywordSearchList list : loader.getListsL()) {
            final String listName = list.getName();
            if (list.getUseForIngest() == true
                    || (listsToAdd != null && listsToAdd.contains(listName))) {
                keywordLists.add(listName);
                sb.append(listName).append(" ");
            }
            for (Keyword keyword : list.getKeywords()) {
                if (!keywords.contains(keyword)) {
                    keywords.add(keyword);
                    keywordToList.put(keyword.getQuery(), list);
                }
            }

        }

        logger.log(Level.INFO, "Set new effective keyword lists: " + sb.toString());

    }

    List<String> getKeywordLists() {
        return keywordLists == null ? new ArrayList<String>() : keywordLists;
    }

    /**
     * Check if time to commit, if so, run commit. Then run search if search
     * timer is also set.
     */
    void checkRunCommitSearch() {
        if (commitIndex) {
            logger.log(Level.INFO, "Commiting index");
            commit();
            commitIndex = false;

            //after commit, check if time to run searcher
            //NOTE commit/searcher timings don't need to align
            //in worst case, we will run search next time after commit timer goes off, or at the end of ingest
            if (searcherDone && runSearcher) {
                //start search if previous not running
                if (keywordLists != null && !keywordLists.isEmpty()) {
                    currentSearcher = new Searcher(keywordLists);
                    currentSearcher.execute();//searcher will stop timer and restart timer when done
                }
            }
        }
    }

    /**
     * CommitTimerAction to run by commitTimer Sets a flag to indicate we are
     * ready for commit
     */
    private class CommitTimerAction implements ActionListener {

        private final Logger logger = Logger.getLogger(CommitTimerAction.class.getName());

        @Override
        public void actionPerformed(ActionEvent e) {
            commitIndex = true;
            logger.log(Level.INFO, "CommitTimer awake");
        }
    }

    /**
     * SearchTimerAction to run by searchTimer Sets a flag to indicate we are
     * ready to search
     */
    private class SearchTimerAction implements ActionListener {

        private final Logger logger = Logger.getLogger(SearchTimerAction.class.getName());

        @Override
        public void actionPerformed(ActionEvent e) {
            runSearcher = true;
            logger.log(Level.INFO, "SearchTimer awake");
        }
    }

    /**
     * File indexer, processes and indexes known/allocated files,
     * unknown/unallocated files and directories accordingly
     */
    private class Indexer {

        private final Logger logger = Logger.getLogger(Indexer.class.getName());

        /**
         * Extract text with Tika or other text extraction modules (by
         * streaming) from the file Divide the file into chunks and index the
         * chunks
         *
         * @param aFile file to extract strings from, divide into chunks and
         * index
         * @param detectedFormat mime-type detected, or null if none detected
         * @return true if the file was text_ingested, false otherwise
         * @throws IngesterException exception thrown if indexing failed
         */
        private boolean extractTextAndIndex(AbstractFile aFile, String detectedFormat) throws IngesterException {
            AbstractFileExtract fileExtract = null;

            //go over available text extractors in order, and pick the first one (most specific one)
            for (AbstractFileExtract fe : textExtractors) {
                if (fe.isSupported(aFile, detectedFormat)) {
                    fileExtract = fe;
                    break;
                }
            }

            if (fileExtract == null) {
                logger.log(Level.INFO, "No text extractor found for file id:"
                        + aFile.getId() + ", name: " + aFile.getName() + ", detected format: " + detectedFormat);
                return false;
            }

            //logger.log(Level.INFO, "Extractor: " + fileExtract + ", file: " + aFile.getName());

            //divide into chunks and index
            return fileExtract.index(aFile);
        }

        /**
         * Extract strings using heuristics from the file and add to index.
         *
         * @param aFile file to extract strings from, divide into chunks and
         * index
         * @return true if the file was text_ingested, false otherwise
         */
        private boolean extractStringsAndIndex(AbstractFile aFile) {
            try {
                if (stringExtractor.index(aFile)) {
                    ingestStatus.put(aFile.getId(), IngestStatus.STRINGS_INGESTED);
                    return true;
                } else {
                    logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").");
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                    return false;
                }
            } catch (IngesterException ex) {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").", ex);
                ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                return false;
            }
        }

        /**
         * Check with every extractor if it supports the file with the detected
         * format
         *
         * @param aFile file to check for
         * @param detectedFormat mime-type with detected format (such as
         * text/plain) or null if not detected
         * @return true if text extraction is supported
         */
        private boolean isTextExtractSupported(AbstractFile aFile, String detectedFormat) {
            for (AbstractFileExtract extractor : textExtractors) {
                if (extractor.isContentTypeSpecific() == true
                        && extractor.isSupported(aFile, detectedFormat)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Adds the file to the index. Detects file type, calls extractors, etc.
         *
         * @param aFile File to analyze
         * @param indexContent False if only metadata should be text_ingested. True if
         * content and metadata should be index.
         */
        private void indexFile(AbstractFile aFile, boolean indexContent) {
            //logger.log(Level.INFO, "Processing AbstractFile: " + abstractFile.getName());

            TskData.TSK_DB_FILES_TYPE_ENUM aType = aFile.getType(); 
            
            // unallocated and unused blocks can only have strings extracted from them. 
            if ((aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) || aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS))) {
                extractStringsAndIndex(aFile);
            }

            final long size = aFile.getSize();
            //if not to index content, or a dir, or 0 content, index meta data only
            if ((indexContent == false || aFile.isDir() || size == 0)) {
                try {
                    ingester.ingest(aFile, false); //meta-data only
                    ingestStatus.put(aFile.getId(), IngestStatus.METADATA_INGESTED);
                } 
                catch (IngesterException ex) {
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                    logger.log(Level.WARNING, "Unable to index meta-data for file: " + aFile.getId(), ex);
                }
                return;
            }

            //use Tika to detect the format
            String detectedFormat = null;
            InputStream is = null;
            try {
                is = new ReadContentInputStream(aFile);
                detectedFormat = tikaFormatDetector.detect(is, aFile.getName());
            } 
            catch (Exception e) {
                logger.log(Level.WARNING, "Could not detect format using tika for file: " + aFile, e);
            } 
            finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not close stream after detecting format using tika for file: "
                                + aFile, ex);
                    }
                }
            }
            
            // @@@ Add file type signature to blackboard here
            
            //logger.log(Level.INFO, "Detected format: " + aFile.getName() + " " + detectedFormat);

            // we skip archive formats that are opened by the archive module. 
            // @@@ We could have a check here to see if the archive module was enabled though...
            if (AbstractFileExtract.ARCHIVE_MIME_TYPES.contains(detectedFormat)) {
                try {
                    ingester.ingest(aFile, false); //meta-data only
                    ingestStatus.put(aFile.getId(), IngestStatus.METADATA_INGESTED);
                } 
                catch (IngesterException ex) {
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                    logger.log(Level.WARNING, "Unable to index meta-data for file: " + aFile.getId(), ex);
                }
                return;
            }

            boolean wasTextAdded = false;
            if (isTextExtractSupported(aFile, detectedFormat)) {
                //extract text with one of the extractors, divide into chunks and index with Solr
                try {
                    //logger.log(Level.INFO, "indexing: " + aFile.getName());
                    if (!extractTextAndIndex(aFile, detectedFormat)) {
                        logger.log(Level.WARNING, "Failed to extract text and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").");
                        ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                    } else {
                        ingestStatus.put(aFile.getId(), IngestStatus.TEXT_INGESTED);
                        wasTextAdded = true;
                    }

                } catch (IngesterException e) {
                    logger.log(Level.INFO, "Could not extract text with Tika, " + aFile.getId() + ", "
                            + aFile.getName(), e);
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error extracting text with Tika, " + aFile.getId() + ", "
                            + aFile.getName(), e);
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                }
            }

            // if it wasn't supported or had an error, default to strings
            if (wasTextAdded == false) {
                extractStringsAndIndex(aFile);
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
        private List<Keyword> keywords; //keywords to search
        private List<String> keywordLists; // lists currently being searched
        private Map<String, KeywordSearchListsAbstract.KeywordSearchList> keywordToList; //keyword to list name mapping
        private AggregateProgressHandle progressGroup;
        private final Logger logger = Logger.getLogger(Searcher.class.getName());
        private boolean finalRun = false;

        Searcher(List<String> keywordLists) {
            this.keywordLists = new ArrayList<String>(keywordLists);
            this.keywords = new ArrayList<Keyword>();
            this.keywordToList = new HashMap<String, KeywordSearchListsAbstract.KeywordSearchList>();
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

            final String displayName = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.displayName") +
                    (finalRun ? (" - "+ NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.finalizeMsg")) : "");
            progressGroup = AggregateProgressFactory.createSystemHandle(displayName + (" ("+
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.pendingMsg") +")"), null, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Cancelling the searcher by user.");
                    if (progressGroup != null) {
                        progressGroup.setDisplayName(displayName + " ("+ NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.doInBackGround.cancelMsg") +"...)");
                    }
                    return Searcher.this.cancel(true);
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
                        logger.log(Level.INFO, "Cancel detected, bailing before new keyword processed: " + keywordQuery.getQuery());
                        return null;
                    }

                    final String queryStr = keywordQuery.getQuery();
                    final KeywordSearchListsAbstract.KeywordSearchList list = keywordToList.get(queryStr);
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
                    } 
                    else {
                        del = new LuceneQuery(keywordQuery);
                        del.escape();    
                    }

                    //limit search to currently ingested data sources
                    //set up a filter with 1 or more image ids OR'ed
                    final KeywordQueryFilter dataSourceFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.DATA_SOURCE, curDataSourceIds);
                    del.addFilter(dataSourceFilter);

                    Map<String, List<ContentHit>> queryResult = null;

                    try {
                        queryResult = del.performQuery();
                    } catch (NoOpenCoreException ex) {
                        logger.log(Level.WARNING, "Error performing query: " + keywordQuery.getQuery(), ex);
                        //no reason to continue with next query if recovery failed
                        //or wait for recovery to kick in and run again later
                        //likely case has closed and threads are being interrupted
                        return null;
                    } catch (CancellationException e) {
                        logger.log(Level.INFO, "Cancel detected, bailing during keyword query: " + keywordQuery.getQuery());
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
                        Collection<BlackboardArtifact> newArtifacts = new ArrayList<BlackboardArtifact>();

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
                                logger.log(Level.INFO, "Cancel detected, bailing before new hit processed for query: " + keywordQuery.getQuery());
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
                                String snippet = null;
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
                                    logger.log(Level.WARNING, "BB artifact for keyword hit not written, file: " + hitFile + ", hit: " + hitTerm.toString());
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
                                
                                    services.postMessage(IngestMessage.createDataMessage(++messageID, instance, subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));
                                }
                            } //for each file hit

                            ++unitProgress;

                        }//for each hit term

                        //update artifact browser
                        if (!newArtifacts.isEmpty()) {
                            services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
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
                    logger.log(Level.INFO, "Searcher took to run: " + stopWatch.getElapsedTimeSecs() + " secs.");
                } finally {
                    searcherLock.unlock();
                }
            }

            return null;
        }

        /**
         * Sync-up the updated keywords from the currently used lists in the XML
         */
        private void updateKeywords() {
            KeywordSearchListsXML loader = KeywordSearchListsXML.getCurrent();

            this.keywords.clear();
            this.keywordToList.clear();

            for (String name : this.keywordLists) {
                KeywordSearchListsAbstract.KeywordSearchList list = loader.getList(name);
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
                finalSearcherDone = true;

                //run module cleanup
                cleanup();
            } else {
                //start counting time for a new searcher to start
                //unless final searcher is pending
                if (finalSearcher == null) {
                    //we need a new Timer object, because restarting previus will not cause firing of the action
                    final int updateIntervalMs = KeywordSearchSettings.getUpdateFrequency().getTime() * 60 * 1000;
                    searchTimer = new Timer(updateIntervalMs, new SearchTimerAction());
                    searchTimer.start();
                }
            }
        }

        //calculate new results but substracting results already obtained in this ingest
        //update currentResults map with the new results
        private Map<Keyword, List<ContentHit>> filterResults(Map<String, List<ContentHit>> queryResult, boolean isRegex) {
            Map<Keyword, List<ContentHit>> newResults = new HashMap<Keyword, List<ContentHit>>();

            for (String termResult : queryResult.keySet()) {
                List<ContentHit> queryTermResults = queryResult.get(termResult);

                //translate to list of IDs that we keep track of
                List<Long> queryTermResultsIDs = new ArrayList<Long>();
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
                                newResultsFs = new ArrayList<ContentHit>();
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
