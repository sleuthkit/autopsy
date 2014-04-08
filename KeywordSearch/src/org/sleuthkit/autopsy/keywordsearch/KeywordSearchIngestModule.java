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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.apache.tika.Tika;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.StringExtract.StringExtractUnicodeTable.SCRIPT;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleAdapter;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
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
 */
public final class KeywordSearchIngestModule extends IngestModuleAdapter implements FileIngestModule {

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
    private IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private Indexer indexer;
    //only search images from current ingest, not images previously ingested/indexed
    //accessed read-only by searcher thread

    private boolean startedSearching = false;
    private SleuthkitCase caseHandle = null;
    private List<AbstractFileExtract> textExtractors;
    private AbstractFileStringExtract stringExtractor;
    private final KeywordSearchJobSettings settings;
    private boolean initialized = false;
    private Tika tikaFormatDetector;
    private long jobId;
    private long dataSourceId;   
    private static AtomicInteger instanceCount = new AtomicInteger(0); //just used for logging
    private int instanceNum = 0;
    
    private enum IngestStatus {

        TEXT_INGESTED, /// Text was extracted by knowing file type and text_ingested
        STRINGS_INGESTED, ///< Strings were extracted from file 
        METADATA_INGESTED, ///< No content, so we just text_ingested metadata
        SKIPPED_ERROR_INDEXING, ///< File was skipped because index engine had problems
        SKIPPED_ERROR_TEXTEXTRACT, ///< File was skipped because of text extraction issues
        SKIPPED_ERROR_IO    ///< File was skipped because of IO issues reading it
    };
    private Map<Long, IngestStatus> ingestStatus;

    KeywordSearchIngestModule(KeywordSearchJobSettings settings) {
        this.settings = settings;
        instanceNum = instanceCount.getAndIncrement();
    }

    /**
     * Initializes the module for new ingest run Sets up threads, timers,
     * retrieves settings, keyword lists to run on
     *
     */
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        logger.log(Level.INFO, "Initializing instance {0}", instanceNum);
        initialized = false;
        
        jobId = context.getJobId();
        caseHandle = Case.getCurrentCase().getSleuthkitCase();
        tikaFormatDetector = new Tika();
        ingester = Server.getIngester();

        final Server server = KeywordSearch.getServer();
        try {
            if (!server.isRunning()) {
                String msg = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.badInitMsg");
                logger.log(Level.SEVERE, msg);
                String details = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.tryStopSolrMsg", msg);
                services.postMessage(IngestMessage.createErrorMessage(KeywordSearchModuleFactory.getModuleName(), msg, details));
                throw new IngestModuleException(msg);
            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error checking if Solr server is running while initializing ingest", ex);
            //this means Solr is not properly initialized
            String msg = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.badInitMsg");
            String details = NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.tryStopSolrMsg", msg);
            services.postMessage(IngestMessage.createErrorMessage(KeywordSearchModuleFactory.getModuleName(), msg, details));
            throw new IngestModuleException(msg);
        }
        try {
            // make an actual query to verify that server is responding
            // we had cases where getStatus was OK, but the connection resulted in a 404
            server.queryNumIndexedDocuments();
        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            throw new IngestModuleException(
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.exception.errConnToSolr.msg",
                    ex.getMessage()));
        }

        //initialize extractors
        stringExtractor = new AbstractFileStringExtract(this);
        stringExtractor.setScripts(KeywordSearchSettings.getStringExtractScripts());
        stringExtractor.setOptions(KeywordSearchSettings.getStringExtractOptions());

        //log the scripts used for debugging
        final StringBuilder sbScripts = new StringBuilder();
        for (SCRIPT s : KeywordSearchSettings.getStringExtractScripts()) {
            sbScripts.append(s.name()).append(" ");
        }
        logger.log(Level.INFO, "Using string extract scripts: {0}", sbScripts.toString());

        textExtractors = new ArrayList<>();
        //order matters, more specific extractors first
        textExtractors.add(new AbstractFileHtmlExtract(this));
        textExtractors.add(new AbstractFileTikaTextExtract(this));

        ingestStatus = new HashMap<>();

        List<KeywordList> keywordLists = KeywordSearchListsXML.getCurrent().getListsL();
        boolean hasKeywordsForSearch = false;
        for (KeywordList keywordList : keywordLists) {
            if (settings.isKeywordListEnabled(keywordList.getName()) && !keywordList.getKeywords().isEmpty()) {
                hasKeywordsForSearch = true;
                break;
            }
        }
        if (!hasKeywordsForSearch) {
            services.postMessage(IngestMessage.createWarningMessage(KeywordSearchModuleFactory.getModuleName(), NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.noKwInLstMsg"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.onlyIdxKwSkipMsg")));
        }

        indexer = new Indexer();
        initialized = true;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (initialized == false) //error initializing indexing/Solr
        {
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: {0}", abstractFile.getName());
            ingestStatus.put(abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            return ProcessResult.OK;
        }
        try {
            //add data source id of the file to the set, keeping track of images being ingested
            dataSourceId = caseHandle.getFileDataSource(abstractFile);

        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error getting image id of file processed by keyword search: " + abstractFile.getName(), ex);
        }

        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
            //skip indexing of virtual dirs (no content, no real name) - will index children files
            return ProcessResult.OK;
        }

        if (KeywordSearchSettings.getSkipKnown() && abstractFile.getKnown().equals(FileKnown.KNOWN)) {
            //index meta-data only
            indexer.indexFile(abstractFile, false);
            return ProcessResult.OK;
        }

        //index the file and content (if the content is supported)
        indexer.indexFile(abstractFile, true);

        // Start searching if it hasn't started already
        if (!startedSearching) {
            List<String> keywordListNames = settings.getNamesOfEnabledKeyWordLists();
            SearchRunner.getInstance().startJob(jobId, dataSourceId, keywordListNames);
            startedSearching = true;
        }
        
        return ProcessResult.OK;
    }

    /**
     * After all files are ingested, execute final index commit and final search
     * Cleanup resources, threads, timers
     */
    @Override
    public void shutDown(boolean ingestJobCancelled) {
        logger.log(Level.INFO, "Instance {0}", instanceNum);
       
        if (initialized == false) {
            return;
        }

        if (ingestJobCancelled) {
            logger.log(Level.INFO, "Ingest job cancelled");
            stop();
            return;
        }

        // Remove from the search list and trigger final commit and final search
        SearchRunner.getInstance().endJob(jobId);
        
        postIndexSummary();        
        
        //log number of files / chunks in index
        //signal a potential change in number of text_ingested files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            final int numIndexedChunks = KeywordSearch.getServer().queryNumIndexedChunks();
            logger.log(Level.INFO, "Indexed files count: {0}", numIndexedFiles);
            logger.log(Level.INFO, "Indexed file chunks count: {0}", numIndexedChunks);
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files/chunks: ", ex);
        }
    }

    /**
     * Handle stop event (ingest interrupted) Cleanup resources, threads, timers
     */
    private void stop() {
        logger.log(Level.INFO, "stop()");

        SearchRunner.getInstance().stopJob(jobId);
    
        cleanup();
    }

    /**
     * Common cleanup code when module stops or final searcher completes
     */
    private void cleanup() {
        ingestStatus.clear();

        textExtractors.clear();
        textExtractors = null;
        stringExtractor = null;

        tikaFormatDetector = null;

        initialized = false;
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
        logger.log(Level.INFO, "Keyword Indexing Completed: {0}", indexStats);
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, KeywordSearchModuleFactory.getModuleName(), NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxResultsLbl"), indexStats));
        if (error_index > 0) {
            MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxErrsTitle"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxErrMsgFiles", error_index));
        } else if (error_io + error_text > 0) {
            MessageNotifyUtil.Notify.warn(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.kwIdxWarnMsgTitle"),
                    NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.idxErrReadFilesMsg"));
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
                logger.log(Level.INFO, "No text extractor found for file id:{0}, name: {1}, detected format: {2}", new Object[]{aFile.getId(), aFile.getName(), detectedFormat});
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
                    logger.log(Level.WARNING, "Failed to extract strings and ingest, file ''{0}'' (id: {1}).", new Object[]{aFile.getName(), aFile.getId()});
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
         * @param indexContent False if only metadata should be text_ingested.
         * True if content and metadata should be index.
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
                } catch (IngesterException ex) {
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
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not detect format using tika for file: " + aFile, e);
            } finally {
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
                } catch (IngesterException ex) {
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
                        logger.log(Level.WARNING, "Failed to extract text and ingest, file ''{0}'' (id: {1}).", new Object[]{aFile.getName(), aFile.getId()});
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

}
