/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
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
@NbBundle.Messages({
    "# {0} - Reason for not starting Solr", "KeywordSearchIngestModule.init.tryStopSolrMsg={0}<br />Please try stopping Java Solr processes if any exist and restart the application.",
    "KeywordSearchIngestModule.init.badInitMsg=Keyword search server was not properly initialized, cannot run keyword search ingest.",
    "SolrConnectionCheck.Port=Invalid port number.",
    "# {0} - Reason for not connecting to Solr", "KeywordSearchIngestModule.init.exception.errConnToSolr.msg=Error connecting to SOLR server: {0}.",
    "KeywordSearchIngestModule.startUp.noOpenCore.msg=The index could not be opened or does not exist.",
    "CannotRunFileTypeDetection=Unable to run file type detection."
})
public final class KeywordSearchIngestModule implements FileIngestModule {

    enum UpdateFrequency {

        FAST(20),
        AVG(10),
        SLOW(5),
        SLOWEST(1),
        NONE(Integer.MAX_VALUE),
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
    private final IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private Indexer indexer;
    private FileTypeDetector fileTypeDetector;
//only search images from current ingest, not images previously ingested/indexed
    //accessed read-only by searcher thread

    private boolean startedSearching = false;
    private List<ContentTextExtractor> textExtractors;
    private StringsTextExtractor stringExtractor;
    private final KeywordSearchJobSettings settings;
    private boolean initialized = false;
    private long jobId;
    private long dataSourceId;
    private static final AtomicInteger instanceCount = new AtomicInteger(0); //just used for logging
    private int instanceNum = 0;
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private IngestJobContext context;

    private enum IngestStatus {

        TEXT_INGESTED, /// Text was extracted by knowing file type and text_ingested
        STRINGS_INGESTED, ///< Strings were extracted from file 
        METADATA_INGESTED, ///< No content, so we just text_ingested metadata
        SKIPPED_ERROR_INDEXING, ///< File was skipped because index engine had problems
        SKIPPED_ERROR_TEXTEXTRACT, ///< File was skipped because of text extraction issues
        SKIPPED_ERROR_IO    ///< File was skipped because of IO issues reading it
    };
    private static final Map<Long, Map<Long, IngestStatus>> ingestStatus = new HashMap<>(); //guarded by itself

    /**
     * Records the ingest status for a given file for a given ingest job. Used
     * for final statistics at the end of the job.
     *
     * @param ingestJobId id of ingest job
     * @param fileId      id of file
     * @param status      ingest status of the file
     */
    private static void putIngestStatus(long ingestJobId, long fileId, IngestStatus status) {
        synchronized (ingestStatus) {
            Map<Long, IngestStatus> ingestStatusForJob = ingestStatus.get(ingestJobId);
            if (ingestStatusForJob == null) {
                ingestStatusForJob = new HashMap<>();
                ingestStatus.put(ingestJobId, ingestStatusForJob);
            }
            ingestStatusForJob.put(fileId, status);
            ingestStatus.put(ingestJobId, ingestStatusForJob);
        }
    }

    KeywordSearchIngestModule(KeywordSearchJobSettings settings) {
        this.settings = settings;
        instanceNum = instanceCount.getAndIncrement();
    }

    /**
     * Initializes the module for new ingest run Sets up threads, timers,
     * retrieves settings, keyword lists to run on
     *
     */
    @Messages({
        "KeywordSearchIngestModule.startupMessage.failedToGetIndexSchema=Failed to get schema version for text index.",
        "# {0} - Solr version number", "KeywordSearchIngestModule.startupException.indexSolrVersionNotSupported=Adding text no longer supported for Solr version {0} of the text index.",
        "# {0} - schema version number", "KeywordSearchIngestModule.startupException.indexSchemaNotSupported=Adding text no longer supported for schema version {0} of the text index.",
        "KeywordSearchIngestModule.noOpenCase.errMsg=No open case available."
    })
    @Override
    public void startUp(IngestJobContext context) throws IngestModuleException {
        initialized = false;
        jobId = context.getJobId();
        dataSourceId = context.getDataSource().getId();

        Server server = KeywordSearch.getServer();
        if (server.coreIsOpen() == false) {
            throw new IngestModuleException(Bundle.KeywordSearchIngestModule_startUp_noOpenCore_msg());
        }

        try {
            Index indexInfo = server.getIndexInfo();
            if (!IndexFinder.getCurrentSolrVersion().equals(indexInfo.getSolrVersion())) {
                throw new IngestModuleException(Bundle.KeywordSearchIngestModule_startupException_indexSolrVersionNotSupported(indexInfo.getSolrVersion()));
            }
            if (!IndexFinder.getCurrentSchemaVersion().equals(indexInfo.getSchemaVersion())) {
                throw new IngestModuleException(Bundle.KeywordSearchIngestModule_startupException_indexSchemaNotSupported(indexInfo.getSchemaVersion()));
            }
        } catch (NoOpenCoreException ex) {
            throw new IngestModuleException(Bundle.KeywordSearchIngestModule_startupMessage_failedToGetIndexSchema(), ex);
        }

        try {
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }

        ingester = Ingester.getDefault();
        this.context = context;

        // increment the module reference count
        // if first instance of this module for this job then check the server and existence of keywords
        Case openCase;
        try {
            openCase = Case.getOpenCase();
        } catch (NoCurrentCaseException ex) {
            throw new IngestModuleException(Bundle.KeywordSearchIngestModule_noOpenCase_errMsg(), ex);
        }
        if (refCounter.incrementAndGet(jobId) == 1) {
            if (openCase.getCaseType() == Case.CaseType.MULTI_USER_CASE) {
                // for multi-user cases need to verify connection to remore SOLR server
                KeywordSearchService kwsService = new SolrSearchService();
                Server.IndexingServerProperties properties = Server.getMultiUserServerProperties(openCase.getCaseDirectory());
                int port;
                try {
                    port = Integer.parseInt(properties.getPort());
                } catch (NumberFormatException ex) {
                    // if there is an error parsing the port number
                    throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_badInitMsg() + " " + Bundle.SolrConnectionCheck_Port(), ex);
                }
                try {
                    kwsService.tryConnect(properties.getHost(), port);
                } catch (KeywordSearchServiceException ex) {
                    throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_badInitMsg(), ex);
                }
            } else {
                // for single-user cases need to verify connection to local SOLR service
                try {
                    if (!server.isRunning()) {
                        throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_tryStopSolrMsg(Bundle.KeywordSearchIngestModule_init_badInitMsg()));
                    }
                } catch (KeywordSearchModuleException ex) {
                    //this means Solr is not properly initialized
                    throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_tryStopSolrMsg(Bundle.KeywordSearchIngestModule_init_badInitMsg()), ex);
                }
                try {
                    // make an actual query to verify that server is responding
                    // we had cases where getStatus was OK, but the connection resulted in a 404
                    server.queryNumIndexedDocuments();
                } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
                    throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_exception_errConnToSolr_msg(ex.getMessage()), ex);
                }

                // check if this job has any searchable keywords    
                List<KeywordList> keywordLists = XmlKeywordSearchList.getCurrent().getListsL();
                boolean hasKeywordsForSearch = false;
                for (KeywordList keywordList : keywordLists) {
                    if (settings.keywordListIsEnabled(keywordList.getName()) && !keywordList.getKeywords().isEmpty()) {
                        hasKeywordsForSearch = true;
                        break;
                    }
                }
                if (!hasKeywordsForSearch) {
                    services.postMessage(IngestMessage.createWarningMessage(KeywordSearchModuleFactory.getModuleName(), NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.noKwInLstMsg"),
                            NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.init.onlyIdxKwSkipMsg")));
                }
            }
        }

        //initialize extractors
        stringExtractor = new StringsTextExtractor();
        stringExtractor.setScripts(KeywordSearchSettings.getStringExtractScripts());
        stringExtractor.setOptions(KeywordSearchSettings.getStringExtractOptions());

        textExtractors = new ArrayList<>();
        //order matters, more specific extractors first
        textExtractors.add(new HtmlTextExtractor());
        textExtractors.add(new TikaTextExtractor());

        indexer = new Indexer();
        initialized = true;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (initialized == false) //error initializing indexing/Solr
        {
            logger.log(Level.SEVERE, "Skipping processing, module not initialized, file: {0}", abstractFile.getName());  //NON-NLS
            putIngestStatus(jobId, abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            return ProcessResult.OK;
        }

        if (abstractFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.VIRTUAL_DIR)) {
            //skip indexing of virtual dirs (no content, no real name) - will index children files
            return ProcessResult.OK;
        }

        if (KeywordSearchSettings.getSkipKnown() && abstractFile.getKnown().equals(FileKnown.KNOWN)) {
            //index meta-data only
            if (context.fileIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            indexer.indexFile(abstractFile, false);
            return ProcessResult.OK;
        }

        //index the file and content (if the content is supported)
        if (context.fileIngestIsCancelled()) {
            return ProcessResult.OK;
        }
        indexer.indexFile(abstractFile, true);

        // Start searching if it hasn't started already
        if (!startedSearching) {
            if (context.fileIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            List<String> keywordListNames = settings.getNamesOfEnabledKeyWordLists();
            SearchRunner.getInstance().startJob(context, keywordListNames);
            startedSearching = true;
        }

        return ProcessResult.OK;
    }

    /**
     * After all files are ingested, execute final index commit and final search
     * Cleanup resources, threads, timers
     */
    @Override
    public void shutDown() {
        logger.log(Level.INFO, "Keyword search ingest module instance {0} shutting down", instanceNum); //NON-NLS

        if ((initialized == false) || (context == null)) {
            return;
        }

        if (context.fileIngestIsCancelled()) {
            logger.log(Level.INFO, "Keyword search ingest module instance {0} stopping search job due to ingest cancellation", instanceNum); //NON-NLS
            SearchRunner.getInstance().stopJob(jobId);
            cleanup();
            return;
        }

        // Remove from the search list and trigger final commit and final search
        SearchRunner.getInstance().endJob(jobId);

        // We only need to post the summary msg from the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            try {
                final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
                logger.log(Level.INFO, "Indexed files count: {0}", numIndexedFiles); //NON-NLS
                final int numIndexedChunks = KeywordSearch.getServer().queryNumIndexedChunks();
                logger.log(Level.INFO, "Indexed file chunks count: {0}", numIndexedChunks); //NON-NLS
            } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
                logger.log(Level.SEVERE, "Error executing Solr queries to check number of indexed files and file chunks", ex); //NON-NLS
            }
            postIndexSummary();
            synchronized (ingestStatus) {
                ingestStatus.remove(jobId);
            }
        }

        cleanup();
    }

    /**
     * Common cleanup code when module stops or final searcher completes
     */
    private void cleanup() {
        textExtractors.clear();
        textExtractors = null;
        stringExtractor = null;

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

        synchronized (ingestStatus) {
            Map<Long, IngestStatus> ingestStatusForJob = ingestStatus.get(jobId);
            if (ingestStatusForJob == null) {
                return;
            }
            for (IngestStatus s : ingestStatusForJob.values()) {
                switch (s) {
                    case TEXT_INGESTED:
                        text_ingested++;
                        break;
                    case METADATA_INGESTED:
                        metadata_ingested++;
                        break;
                    case STRINGS_INGESTED:
                        strings_ingested++;
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
        }

        StringBuilder msg = new StringBuilder();
        msg.append("<table border=0><tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.knowFileHeaderLbl")).append("</td><td>").append(text_ingested).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.fileGenStringsHead")).append("</td><td>").append(strings_ingested).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.mdOnlyLbl")).append("</td><td>").append(metadata_ingested).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.idxErrLbl")).append("</td><td>").append(error_index).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.errTxtLbl")).append("</td><td>").append(error_text).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>").append(NbBundle.getMessage(this.getClass(), "KeywordSearchIngestModule.postIndexSummary.errIoLbl")).append("</td><td>").append(error_io).append("</td></tr>"); //NON-NLS
        msg.append("</table>"); //NON-NLS
        String indexStats = msg.toString();
        logger.log(Level.INFO, "Keyword Indexing Completed: {0}", indexStats); //NON-NLS
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
         * @param aFile          file to extract strings from, divide into
         *                       chunks and index
         * @param detectedFormat mime-type detected, or null if none detected
         *
         * @return true if the file was text_ingested, false otherwise
         *
         * @throws IngesterException exception thrown if indexing failed
         */
        private boolean extractTextAndIndex(AbstractFile aFile, String detectedFormat) throws IngesterException {
            ContentTextExtractor extractor = null;

            //go over available text extractors in order, and pick the first one (most specific one)
            for (ContentTextExtractor fe : textExtractors) {
                if (fe.isSupported(aFile, detectedFormat)) {
                    extractor = fe;
                    break;
                }
            }

            if (extractor == null) {
                // No text extractor found.
                return false;
            }

            //logger.log(Level.INFO, "Extractor: " + fileExtract + ", file: " + aFile.getName());
            //divide into chunks and index
            return Ingester.getDefault().indexText(extractor, aFile, context);
        }

        /**
         * Extract strings using heuristics from the file and add to index.
         *
         * @param aFile file to extract strings from, divide into chunks and
         *              index
         *
         * @return true if the file was text_ingested, false otherwise
         */
        private boolean extractStringsAndIndex(AbstractFile aFile) {
            try {
                if (context.fileIngestIsCancelled()) {
                    return true;
                }
                if (Ingester.getDefault().indexText(stringExtractor, aFile, KeywordSearchIngestModule.this.context)) {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.STRINGS_INGESTED);
                    return true;
                } else {
                    logger.log(Level.WARNING, "Failed to extract strings and ingest, file ''{0}'' (id: {1}).", new Object[]{aFile.getName(), aFile.getId()});  //NON-NLS
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                    return false;
                }
            } catch (IngesterException ex) {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").", ex);  //NON-NLS
                putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                return false;
            }
        }

        /**
         * Adds the file to the index. Detects file type, calls extractors, etc.
         *
         * @param aFile        File to analyze
         * @param indexContent False if only metadata should be text_ingested.
         *                     True if content and metadata should be index.
         */
        private void indexFile(AbstractFile aFile, boolean indexContent) {
            //logger.log(Level.INFO, "Processing AbstractFile: " + abstractFile.getName());

            TskData.TSK_DB_FILES_TYPE_ENUM aType = aFile.getType();

            // unallocated and unused blocks can only have strings extracted from them. 
            if ((aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS) || aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS))) {
                if (context.fileIngestIsCancelled()) {
                    return;
                }
                extractStringsAndIndex(aFile);
                return;
            }

            final long size = aFile.getSize();
            //if not to index content, or a dir, or 0 content, index meta data only

            if ((indexContent == false || aFile.isDir() || size == 0)) {
                try {
                    if (context.fileIngestIsCancelled()) {
                        return;
                    }
                    ingester.indexMetaDataOnly(aFile);
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.METADATA_INGESTED);
                } catch (IngesterException ex) {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                    logger.log(Level.WARNING, "Unable to index meta-data for file: " + aFile.getId(), ex); //NON-NLS
                }
                return;
            }

            if (context.fileIngestIsCancelled()) {
                return;
            }
            String fileType = fileTypeDetector.getMIMEType(aFile);

            // we skip archive formats that are opened by the archive module. 
            // @@@ We could have a check here to see if the archive module was enabled though...
            if (ContentTextExtractor.ARCHIVE_MIME_TYPES.contains(fileType)) {
                try {
                    if (context.fileIngestIsCancelled()) {
                        return;
                    }
                    ingester.indexMetaDataOnly(aFile);
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.METADATA_INGESTED);
                } catch (IngesterException ex) {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                    logger.log(Level.WARNING, "Unable to index meta-data for file: " + aFile.getId(), ex); //NON-NLS
                }
                return;
            }

            boolean wasTextAdded = false;

            //extract text with one of the extractors, divide into chunks and index with Solr
            try {
                //logger.log(Level.INFO, "indexing: " + aFile.getName());
                if (context.fileIngestIsCancelled()) {
                    return;
                }
                if (fileType.equals("application/octet-stream")) {
                    extractStringsAndIndex(aFile);
                    return;
                }
                if (!extractTextAndIndex(aFile, fileType)) {
                    // Text extractor not found for file. Extract string only.
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                } else {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.TEXT_INGESTED);
                    wasTextAdded = true;
                }

            } catch (IngesterException e) {
                logger.log(Level.INFO, "Could not extract text with Tika, " + aFile.getId() + ", " //NON-NLS
                        + aFile.getName(), e);
                putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error extracting text with Tika, " + aFile.getId() + ", " //NON-NLS
                        + aFile.getName(), e);
                putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
            }

            // if it wasn't supported or had an error, default to strings
            if (wasTextAdded == false) {
                extractStringsAndIndex(aFile);
            }
        }
    }
}
