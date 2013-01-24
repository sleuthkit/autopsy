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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.Long;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.netbeans.api.progress.aggregate.AggregateProgressFactory;
import org.netbeans.api.progress.aggregate.AggregateProgressHandle;
import org.netbeans.api.progress.aggregate.ProgressContributor;
import org.openide.util.Cancellable;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.EscapeUtil;
import org.sleuthkit.autopsy.coreutils.StopWatch;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
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
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
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
public final class KeywordSearchIngestModule implements IngestModuleAbstractFile {

    enum UpdateFrequency {

        FAST(20),
        AVG(10),
        SLOW(5);
        private final int time;

        UpdateFrequency(int time) {
            this.time = time;
        }

        int getTime() {
            return time;
        }
    };
    private static final Logger logger = Logger.getLogger(KeywordSearchIngestModule.class.getName());
    public static final String MODULE_NAME = "Keyword Search";
    public static final String MODULE_DESCRIPTION = "Performs file indexing and periodic search using keywords and regular expressions in lists.";
    final public static String MODULE_VERSION = "1.0";
    private String args;
    private static KeywordSearchIngestModule instance = null;
    private IngestServices services;
    private Ingester ingester = null;
    private volatile boolean commitIndex = false; //whether to commit index next time
    private volatile boolean runSearcher = false; //whether to run searcher next time
    private List<Keyword> keywords; //keywords to search
    private List<String> keywordLists; // lists currently being searched
    private Map<String, KeywordSearchList> keywordToList; //keyword to list name mapping
    private Timer commitTimer;
    private Timer searchTimer;
    private Indexer indexer;
    private Searcher currentSearcher;
    private Searcher finalSearcher;
    private volatile boolean searcherDone = true; //mark as done, until it's inited
    private Map<Keyword, List<Long>> currentResults;
    //only search images from current ingest, not images previously ingested/indexed
    //accessed read-only by searcher thread
    private Set<Long> curImageIds;
    private static final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true); //use fairness policy
    private static final Lock searcherLock = rwLock.writeLock();
    private volatile int messageID = 0;
    private boolean processedFiles;
    private volatile boolean finalSearcherDone = true;  //mark as done, until it's inited
    private final String hashDBModuleName = "Hash Lookup"; //NOTE this needs to match the HashDB module getName()
    private SleuthkitCase caseHandle = null;
    private static List<AbstractFileExtract> textExtractors;
    private static AbstractFileStringExtract stringExtractor;
    private boolean initialized = false;
    private final GetIsFileKnownV getIsFileKnown = new GetIsFileKnownV();
    private KeywordSearchConfigurationPanel panel;

    private enum IngestStatus {

        INGESTED, EXTRACTED_INGESTED, SKIPPED, INGESTED_META
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

    /**
     * Starts processing of every file provided by IngestManager. Checks if it
     * is time to commit and run search
     *
     * @param abstractFile file/unallocated file/directory to process
     * @return ProcessResult.OK in most cases and ERROR only if error in the
     * pipeline, otherwise does not advice to stop the pipeline
     */
    @Override
    public ProcessResult process(AbstractFile abstractFile) {

        if (initialized == false) //error initializing indexing/Solr
        {
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: " + abstractFile.getName());
            return ProcessResult.OK;
        }
        try {
            //add image id of the file to the set, keeping track of images being ingested
            curImageIds.add(abstractFile.getImage().getId());
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting image id of file processed by keyword search: " + abstractFile.getName(), ex);
        }

        //check if we should index meta-data only when 1) it is known 2) HashDb module errored on it
        IngestModuleAbstractFile.ProcessResult hashDBResult = services.getAbstractFileModuleResult(hashDBModuleName);
        //logger.log(Level.INFO, "hashdb result: " + hashDBResult + "file: " + AbstractFile.getName());
        if (hashDBResult == IngestModuleAbstractFile.ProcessResult.ERROR) {
            //index meta-data only
            indexer.indexFile(abstractFile, false);
            //notify depending module that keyword search (would) encountered error for this file
            return ProcessResult.ERROR;
        } else if (KeywordSearchSettings.getSkipKnown() && abstractFile.accept(getIsFileKnown) == true) {
            //index meta-data only
            indexer.indexFile(abstractFile, false);
            return ProcessResult.OK;
        }

        if (processedFiles == false) {
            processedFiles = true;
        }

        //check if it's time to commit after previous processing
        checkRunCommitSearch();

        //index the file and content (if the content is supported)
        indexer.indexFile(abstractFile, true);


        return ProcessResult.OK;
    }

    /**
     * Process content hierarchy and return true if content is a file and is set
     * as known
     */
    private class GetIsFileKnownV extends ContentVisitor.Default<Boolean> {

        @Override
        protected Boolean defaultVisit(Content cntnt) {
            return false;
        }

        @Override
        public Boolean visit(File file) {
            return file.getKnown() == FileKnown.KNOWN;
        }
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
            services.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Completed"));
        }

        //log number of files / chunks in index
        //signal a potential change in number of indexed files
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
        curImageIds.clear();
        currentSearcher = null;
        //finalSearcher = null; //do not collect, might be finalizing

        commitTimer.stop();
        searchTimer.stop();
        commitTimer = null;
        searchTimer = null;

        textExtractors.clear();
        textExtractors = null;
        stringExtractor = null;

        keywords.clear();
        keywordLists.clear();
        keywordToList.clear();

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

    @Override
    public String getArguments() {
        return args;
    }

    @Override
    public void setArguments(String args) {
        this.args = args;
    }

    /**
     * Initializes the module for new ingest run Sets up threads, timers,
     * retrieves settings, keyword lists to run on
     *
     */
    @Override
    public void init(IngestModuleInit initContext) {
        logger.log(Level.INFO, "init()");
        services = IngestServices.getDefault();
        initialized = false;

        caseHandle = Case.getCurrentCase().getSleuthkitCase();

        ingester = Server.getIngester();

        final Server server = KeywordSearch.getServer();
        try {
            if (!server.isRunning()) {
                String msg = "Keyword search server was not properly initialized, cannot run keyword search ingest. ";
                logger.log(Level.SEVERE, msg);
                String details = msg + "Please try restarting the OS and the application";
                services.postMessage(IngestMessage.createErrorMessage(++messageID, instance, msg, details));
                return;

            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error checking if Solr server is running while initializing ingest", ex);
            //this means Solr is not properly initialized
            return;
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
        keywordToList = new HashMap<String, KeywordSearchList>();

        initKeywords();

        if (keywords.isEmpty() || keywordLists.isEmpty()) {
            services.postMessage(IngestMessage.createWarningMessage(++messageID, instance, "No keywords in keyword list.", "Only indexing will be done and and keyword search will be skipped (you can still add keyword lists using the Keyword Lists - Add to Ingest)."));
        }

        processedFiles = false;
        finalSearcherDone = false;
        searcherDone = true; //make sure to start the initial currentSearcher
        //keeps track of all results per run not to repeat reporting the same hits
        currentResults = new HashMap<Keyword, List<Long>>();

        curImageIds = new HashSet<Long>();

        indexer = new Indexer();

        final int updateIntervalMs = KeywordSearchSettings.getUpdateFrequency().getTime() * 60 * 1000;
        logger.log(Level.INFO, "Using commit interval (ms): " + updateIntervalMs);
        logger.log(Level.INFO, "Using searcher interval (ms): " + updateIntervalMs);

        commitTimer = new Timer(updateIntervalMs, new CommitTimerAction());
        searchTimer = new Timer(updateIntervalMs, new SearchTimerAction());

        initialized = true;

        commitTimer.start();
        searchTimer.start();

        services.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Started"));
    }

    @Override
    public ModuleType getType() {
        return ModuleType.AbstractFile;
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
        KeywordSearchListsXML.getCurrent().reload();
        return new KeywordSearchIngestSimplePanel();
    }

    @Override
    public javax.swing.JPanel getAdvancedConfiguration() {
        //return KeywordSearchConfigurationPanel.getDefault();
        getPanel().load();
        return getPanel();
    }

    private KeywordSearchConfigurationPanel getPanel() {
        if (panel == null) {
            panel = new KeywordSearchConfigurationPanel();
        }
        return panel;
    }

    @Override
    public void saveAdvancedConfiguration() {
        getPanel().store();
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
            //signal a potential change in number of indexed files
            indexChangeNotify();
        }
    }

    /**
     * Posts inbox message with summary of indexed files
     */
    private void postIndexSummary() {
        int indexed = 0;
        int indexed_meta = 0;
        int indexed_extr = 0;
        int skipped = 0;
        for (IngestStatus s : ingestStatus.values()) {
            switch (s) {
                case INGESTED:
                    ++indexed;
                    break;
                case INGESTED_META:
                    ++indexed_meta;
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
        msg.append("<br />Indexed meta-data only: ").append(indexed_meta).append("<br />");
        msg.append("<br />Skipped files: ").append(skipped).append("<br />");
        String indexStats = msg.toString();
        logger.log(Level.INFO, "Keyword Indexing Completed: " + indexStats);
        services.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, this, "Keyword Indexing Completed", indexStats));

    }

    /**
     * Helper method to notify listeners on index update
     */
    private void indexChangeNotify() {
        //signal a potential change in number of indexed files
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

        for (KeywordSearchList list : loader.getListsL()) {
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
         * Extract strings or text with Tika (by streaming) from the file Divide
         * the file into chunks and index the chunks
         *
         * @param aFile file to extract strings from, divide into chunks and
         * index
         * @param stringsOnly true if use string extraction, false if to use a
         * content-type specific text extractor
         * @return true if the file was indexed, false otherwise
         * @throws IngesterException exception thrown if indexing failed
         */
        private boolean extractIndex(AbstractFile aFile, boolean stringsOnly) throws IngesterException {
            AbstractFileExtract fileExtract = null;

            if (stringsOnly && stringExtractor.isSupported(aFile)) {
                fileExtract = stringExtractor;
            } else {
                //go over available text extractors and pick the first one (most specific one)
                for (AbstractFileExtract fe : textExtractors) {
                    if (fe.isSupported(aFile)) {
                        fileExtract = fe;
                        break;
                    }
                }
            }

            if (fileExtract == null) {
                logger.log(Level.INFO, "No supported file extractor found for file: " + aFile.getId() + " " + aFile.getName());
                return false;
            }

            //logger.log(Level.INFO, "Extractor: " + fileExtract + ", file: " + aFile.getName());

            //divide into chunks and index
            return fileExtract.index(aFile);
        }

        private boolean isTextExtractSupported(AbstractFile aFile) {
            for (AbstractFileExtract extractor : textExtractors) {
                if (extractor.isContentTypeSpecific() == true
                        && extractor.isSupported(aFile)) {
                    return true;
                }
            }
            return false;
        }

        private void indexFile(AbstractFile aFile, boolean indexContent) {
            //logger.log(Level.INFO, "Processing AbstractFile: " + abstractFile.getName());

            FsContent fsContent = null;
            //check if alloc fs file or dir
            TskData.TSK_DB_FILES_TYPE_ENUM aType = aFile.getType();
            if (aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
                //skip indexing of virtual dirs (no content, no real name) - will index children files
                return;
            } else if (aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                fsContent = (FsContent) aFile;
            }


            final long size = aFile.getSize();
            //if alloc fs file and not to index content, or a dir, or 0 content, index meta data only
            if (fsContent != null
                    && (indexContent == false || fsContent.isDir() || size == 0)) {
                try {
                    ingester.ingest(fsContent, false); //meta-data only
                    ingestStatus.put(aFile.getId(), IngestStatus.INGESTED_META);
                } catch (IngesterException ex) {
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED);
                    logger.log(Level.WARNING, "Unable to index meta-data for fsContent: " + fsContent.getId(), ex);
                }

                return;
            }

            boolean extractTextSupported = isTextExtractSupported(aFile);
            if (fsContent != null && extractTextSupported) {
                //we know it's an allocated FS file (since it's FsContent)
                //extract text with one of the extractors, divide into chunks and index with Solr
                try {
                    //logger.log(Level.INFO, "indexing: " + fsContent.getName());
                    if (!extractIndex(aFile, false)) {
                        logger.log(Level.WARNING, "Failed to extract Tika text and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").");
                        ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED);
                        //try to extract strings, if a file
                        if (fsContent.isFile() == true) {
                            processNonIngestible(fsContent);
                        }

                    } else {
                        ingestStatus.put(aFile.getId(), IngestStatus.INGESTED);
                    }

                } catch (IngesterException e) {
                    logger.log(Level.INFO, "Could not extract text with Tika, " + fsContent.getId() + ", "
                            + fsContent.getName(), e);
                    ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                    //try to extract strings, if a file
                    if (fsContent.isFile() == true) {
                        processNonIngestible(fsContent);
                    }

                } catch (Exception e) {
                    logger.log(Level.WARNING, "Error extracting text with Tika, " + fsContent.getId() + ", "
                            + fsContent.getName(), e);
                    ingestStatus.put(fsContent.getId(), IngestStatus.SKIPPED);
                    //try to extract strings if a file
                    if (fsContent.isFile() == true) {
                        processNonIngestible(fsContent);
                    }
                }
            } else {
                //unallocated file or unsupported content type by Solr
                processNonIngestible(aFile);
            }
        }

        private boolean processNonIngestible(AbstractFile aFile) {
            try {
                if (!extractIndex(aFile, true)) {
                    logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").");
                    ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED);
                    return false;
                } else {
                    ingestStatus.put(aFile.getId(), IngestStatus.EXTRACTED_INGESTED);
                    return true;
                }
            } catch (IngesterException ex) {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").", ex);
                ingestStatus.put(aFile.getId(), IngestStatus.SKIPPED);
                return false;
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
        private Map<String, KeywordSearchList> keywordToList; //keyword to list name mapping
        private AggregateProgressHandle progressGroup;
        private final Logger logger = Logger.getLogger(Searcher.class.getName());
        private boolean finalRun = false;

        Searcher(List<String> keywordLists) {
            this.keywordLists = new ArrayList<String>(keywordLists);
            this.keywords = new ArrayList<Keyword>();
            this.keywordToList = new HashMap<String, KeywordSearchList>();
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

            final String displayName = "Keyword Search" + (finalRun ? " - Finalizing" : "");
            progressGroup = AggregateProgressFactory.createSystemHandle(displayName + (" (Pending)"), null, new Cancellable() {
                @Override
                public boolean cancel() {
                    logger.log(Level.INFO, "Cancelling the searcher by user.");
                    if (progressGroup != null) {
                        progressGroup.setDisplayName(displayName + " (Cancelling...)");
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
                    final KeywordSearchList list = keywordToList.get(queryStr);
                    final String listName = list.getName();

                    //new subProgress will be active after the initial query
                    //when we know number of hits to start() with
                    if (keywordsSearched > 0) {
                        subProgresses[keywordsSearched - 1].finish();
                    }


                    KeywordSearchQuery del = null;

                    boolean isRegex = !keywordQuery.isLiteral();
                    if (!isRegex) {
                        del = new LuceneQuery(keywordQuery);
                        del.escape();
                    } else {
                        del = new TermComponentQuery(keywordQuery);
                    }

                    //limit search to currently ingested images
                    final long imageIds[] = new long[curImageIds.size()];
                    final Iterator<Long> it = curImageIds.iterator();
                    for (int imageI = 0; it.hasNext(); ++imageI) {
                        imageIds[imageI] = it.next();
                    }
                    //set up a filter with 1 or more image ids OR'ed
                    final KeywordQueryFilter imageFilter = new KeywordQueryFilter(KeywordQueryFilter.FilterType.IMAGE, imageIds);
                    del.addFilter(imageFilter);

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

                    //calculate new results but substracting results already obtained in this ingest
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


                        for (final Keyword hitTerm : newResults.keySet()) {
                            //checking for cancellation between results
                            if (this.isCancelled()) {
                                logger.log(Level.INFO, "Cancel detected, bailing before new hit processed for query: " + keywordQuery.getQuery());
                                return null;
                            }

                            List<ContentHit> contentHitsAll = newResults.get(hitTerm);
                            Map<AbstractFile, Integer> contentHitsFlattened = ContentHit.flattenResults(contentHitsAll);
                            for (final AbstractFile hitFile : contentHitsFlattened.keySet()) {
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

                                KeywordWriteResult written = del.writeToBlackBoard(hitTerm.getQuery(), hitFile, snippet, listName);

                                if (written == null) {
                                    logger.log(Level.WARNING, "BB artifact for keyword hit not written, file: " + hitFile + ", hit: " + hitTerm.toString());
                                    continue;
                                }

                                newArtifacts.add(written.getArtifact());

                                //send notify every 250 results
                                if (newArtifacts.size() % 250 == 0) {
                                    services.fireModuleDataEvent(new ModuleDataEvent(MODULE_NAME, ARTIFACT_TYPE.TSK_KEYWORD_HIT, newArtifacts));
                                }

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
                                detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>");
                                detailsSb.append("</tr>");

                                //preview
                                attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_KEYWORD_PREVIEW.getTypeID());
                                if (attr != null) {
                                    detailsSb.append("<tr>");
                                    detailsSb.append("<th>Preview</th>");
                                    detailsSb.append("<td>").append(EscapeUtil.escapeHtml(attr.getValueString())).append("</td>");
                                    detailsSb.append("</tr>");

                                }

                                //file
                                detailsSb.append("<tr>");
                                detailsSb.append("<th>File</th>");
                                if (hitFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.FS)) {
                                    detailsSb.append("<td>").append(((FsContent) hitFile).getParentPath()).append(hitFile.getName()).append("</td>");
                                } else {
                                    detailsSb.append("<td>").append(hitFile.getName()).append("</td>");
                                }
                                detailsSb.append("</tr>");


                                //list
                                attr = written.getAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME.getTypeID());
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

                                //check if should send messages on hits on this list
                                if (list.getIngestMessages()) //post ingest inbox msg
                                {
                                    services.postMessage(IngestMessage.createDataMessage(++messageID, instance, subjectSb.toString(), detailsSb.toString(), uniqueKey, written.getArtifact()));
                                }


                            } //for each file hit

                            ++unitProgress;

                            String hitDisplayStr = hitTerm.getQuery();
                            if (hitDisplayStr.length() > 50) {
                                hitDisplayStr = hitDisplayStr.substring(0, 49) + "...";
                            }
                            subProgresses[keywordsSearched].progress(listName + ": " + hitDisplayStr, unitProgress);
                            //subProgresses[keywordsSearched].progress(unitProgress);


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
                KeywordSearchList list = loader.getList(name);
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

                services.postMessage(IngestMessage.createMessage(++messageID, MessageType.INFO, KeywordSearchIngestModule.instance, "Completed"));

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
