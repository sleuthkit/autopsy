/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.ingest.FileIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;
import org.sleuthkit.autopsy.ingest.IngestMessage.MessageType;
import org.sleuthkit.autopsy.ingest.IngestModuleReferenceCounter;
import org.sleuthkit.autopsy.ingest.IngestServices;
import static org.sleuthkit.autopsy.keywordsearch.Bundle.*;
import org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchService;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.modules.filetypeid.FileTypeDetector;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TskCoreException;
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

    private static final Logger logger = Logger.getLogger(KeywordSearchIngestModule.class.getName());
    private static final IngestServices services = IngestServices.getInstance();
    private Ingester ingester = null;
    private FileTypeDetector fileTypeDetector;
    private boolean startedSearching = false;
    private List<TextExtractor> textExtractors;
    private StringsTextExtractor stringExtractor;
    private final KeywordSearchJobSettings settings;
    private boolean initialized = false;
    private long jobId;
    private long dataSourceId;
    private static final AtomicInteger instanceCount = new AtomicInteger(0); //just used for logging
    private final int instanceNum;
    private static final IngestModuleReferenceCounter refCounter = new IngestModuleReferenceCounter();
    private IngestJobContext context;

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

    private enum IngestStatus {

        TEXT_INGESTED, /// Text was extracted by knowing file type and text_ingested
        STRINGS_INGESTED, ///< Strings were extracted from file 
        METADATA_INGESTED, ///< No content, so we just text_ingested metadata
        SKIPPED_ERROR_INDEXING, ///< File was skipped because index engine had problems
        SKIPPED_ERROR_TEXTEXTRACT, ///< File was skipped because of text extraction issues
        SKIPPED_ERROR_IO    ///< File was skipped because of IO issues reading it
    };

    @GuardedBy("ingestStatus")
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
        this.instanceNum = instanceCount.getAndIncrement();
    }

    /**
     * Initializes the module for new ingest run Sets up threads, timers,
     * retrieves settings, keyword lists to run on
     *
     */
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
            fileTypeDetector = new FileTypeDetector();
        } catch (FileTypeDetector.FileTypeDetectorInitException ex) {
            throw new IngestModuleException(Bundle.CannotRunFileTypeDetection(), ex);
        }
        ingester = Server.getIngester();
        this.context = context;

        // increment the module reference count
        // if first instance of this module for this job then check the server and existence of keywords
        if (refCounter.incrementAndGet(jobId) == 1) {
            if (Case.getCurrentCase().getCaseType() == Case.CaseType.MULTI_USER_CASE) {
                // for multi-user cases need to verify connection to remore SOLR server
                KeywordSearchService kwsService = new SolrSearchService();
                int port;
                try {
                    port = Integer.parseInt(UserPreferences.getIndexingServerPort());
                } catch (NumberFormatException ex) {
                    // if there is an error parsing the port number
                    throw new IngestModuleException(Bundle.KeywordSearchIngestModule_init_badInitMsg() + " " + Bundle.SolrConnectionCheck_Port(), ex);
                }
                try {
                    kwsService.tryConnect(UserPreferences.getIndexingServerHost(), port);
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

        initialized = true;
    }

    @Override
    public ProcessResult process(AbstractFile abstractFile) {
        if (initialized == false) {//error initializing indexing/Solr
            logger.log(Level.WARNING, "Skipping processing, module not initialized, file: {0}", abstractFile.getName());  //NON-NLS
            putIngestStatus(jobId, abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            return ProcessResult.ERROR;
        }

        if (context.fileIngestIsCancelled()) {
            return ProcessResult.OK;
        }

        switch (abstractFile.getType()) {
            case VIRTUAL_DIR:
                //skip indexing of virtual dirs (no content, no real name) - will index children files
                break;
            case UNALLOC_BLOCKS:
            case UNUSED_BLOCKS:
                // unallocated and unused blocks can only have strings extracted from them.
                extractStringsAndIndex(abstractFile);
                break;
            default:
                if ((KeywordSearchSettings.getSkipKnown() && abstractFile.getKnown() == FileKnown.KNOWN)
                        || abstractFile.isDir()
                        || abstractFile.getSize() == 0) {
                    indexMetaDataOnly(abstractFile);
                } else {
                    indexContent(abstractFile);
                }
        }

        if (context.fileIngestIsCancelled()) {
            return ProcessResult.OK;
        }
        // Start searching if it hasn't started already
        if (!startedSearching) {
            List<String> keywordListNames = settings.getNamesOfEnabledKeyWordLists();
            SearchRunner.getInstance().startJob(jobId, dataSourceId, keywordListNames);
            startedSearching = true;
        }

        return ProcessResult.OK;
    }

    /**
     * Index the given file's metadata only.
     *
     * @param abstractFile
     */
    private void indexMetaDataOnly(AbstractFile abstractFile) {
        if (context.fileIngestIsCancelled()) {
            return;
        }
        try {
            ingester.ingest(abstractFile, false); //meta-data only
            putIngestStatus(jobId, abstractFile.getId(), IngestStatus.METADATA_INGESTED);
        } catch (IngesterException ex) {
            putIngestStatus(jobId, abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
            logger.log(Level.WARNING, "Unable to index meta-data for file: " + abstractFile.getId(), ex); //NON-NLS
        }
    }

    /**
     * Index the content and metadata of the fiven file.
     *
     * @param abstractFile
     */
    private void indexContent(AbstractFile abstractFile) {
        if (context.fileIngestIsCancelled()) {
            return;
        }
        try {
            String fileType = fileTypeDetector.getFileType(abstractFile);
            // we skip archive formats that are opened by the archive module.
            // @@@ We could have a check here to see if the archive module was enabled though...
            if (TextExtractor.ARCHIVE_MIME_TYPES.contains(fileType)) {
                indexMetaDataOnly(abstractFile);
            } else {
                try {
                    extractTextAndIndex(abstractFile, fileType);
                } catch (IngesterException e) {
                    logger.log(Level.INFO, "Could not extract text, " + abstractFile.getId() + ", " //NON-NLS
                            + abstractFile.getName(), e);
                    putIngestStatus(jobId, abstractFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                } catch (Exception e1) {
                    logger.log(Level.WARNING, "Error extracting text, " + abstractFile.getId() + ", " //NON-NLS
                            + abstractFile.getName(), e1);
                    putIngestStatus(jobId, abstractFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                }
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, String.format("Could not detect format using fileTypeDetector for file: %s", abstractFile), ex); //NON-NLS
            //TODO: why do we bail, shouldn't we at least run strings?
        }
    }

    /**
     * Extract text with Tika or other text extraction modules (by streaming)
     * from the file. Divide the file into chunks and index the chunks
     *
     * @param aFile          file to extract text from, divide into chunks and
     *                       index
     * @param detectedFormat mime-type detected, or null if none detected
     *
     * @throws IngesterException exception thrown if indexing failed
     */
    private void extractTextAndIndex(AbstractFile aFile, String detectedFormat) throws IngesterException {
        //go over available text extractors in order, and pick the first one (most specific one)
        for (TextExtractor fe : textExtractors) {
            if (fe.isSupported(aFile, detectedFormat)) {
                //divide into chunks and index
                final boolean indexed = fe.index(aFile, context);

                if (indexed) {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.TEXT_INGESTED);
                    return;
                }
            }
        }
        logger.log(Level.INFO, "No text extractor found for file. Extracting strings only. File id:{0}, name: {1}, detected format: {2}", new Object[]{aFile.getId(), aFile.getName(), detectedFormat}); //NON-NLS
        extractStringsAndIndex(aFile);
    }

    /**
     * Extract strings using heuristics from the file and add to index.
     *
     * @param aFile file to extract strings from, divide into chunks and index
     */
    private void extractStringsAndIndex(AbstractFile aFile) {
        if (context.fileIngestIsCancelled()) {
            return;
        }
        try {
            if (stringExtractor.index(aFile, context)) {
                putIngestStatus(jobId, aFile.getId(), IngestStatus.STRINGS_INGESTED);
            } else {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file ''{0}'' (id: {1}).", new Object[]{aFile.getName(), aFile.getId()});  //NON-NLS
                putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
            }
        } catch (IngesterException ex) {
            logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").", ex);  //NON-NLS
            putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
        }
    }

    /**
     * After all files are ingested, execute final index commit and final search
     * Cleanup resources, threads, timers
     */
    @Override
    public void shutDown() {
        logger.log(Level.INFO, "Instance {0}", instanceNum); //NON-NLS

        if ((initialized == false) || (context == null)) {
            return;
        }

        if (context.fileIngestIsCancelled()) {
            stop();
            return;
        }

        // Remove from the search list and trigger final commit and final search
        SearchRunner.getInstance().endJob(jobId);

        // We only need to post the summary msg from the last module per job
        if (refCounter.decrementAndGet(jobId) == 0) {
            postIndexSummary();
            synchronized (ingestStatus) {
                ingestStatus.remove(jobId);
            }
        }

        //log number of files / chunks in index
        //signal a potential change in number of text_ingested files
        try {
            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
            final int numIndexedChunks = KeywordSearch.getServer().queryNumIndexedChunks();
            logger.log(Level.INFO, "Indexed files count: {0}", numIndexedFiles); //NON-NLS
            logger.log(Level.INFO, "Indexed file chunks count: {0}", numIndexedChunks); //NON-NLS
        } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Error executing Solr query to check number of indexed files/chunks: ", ex); //NON-NLS
        }

        cleanup();
    }

    /**
     * Handle stop event (ingest interrupted) Cleanup resources, threads, timers
     */
    private void stop() {
        logger.log(Level.INFO, "stop()"); //NON-NLS

        SearchRunner.getInstance().stopJob(jobId);

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
    @NbBundle.Messages({"KeywordSearchIngestModule.postIndexSummary.knowFileHeaderLbl=Files with known types",
        "KeywordSearchIngestModule.postIndexSummary.fileGenStringsHead=Files with general strings extracted",
        "KeywordSearchIngestModule.postIndexSummary.kwIdxErrsTitle=Keyword Indexing Errors",
        "KeywordSearchIngestModule.postIndexSummary.errTxtLbl=Error (text extraction)",
        "KeywordSearchIngestModule.postIndexSummary.errIoLbl=Error (I/O)",
        "KeywordSearchIngestModule.postIndexSummary.kwIdxWarnMsgTitle=Keyword Indexing Warning",
        "KeywordSearchIngestModule.postIndexSummary.idxErrReadFilesMsg=Keyword index service had errors reading files and extracting text. Could have been from corrupt media or files.",
        "KeywordSearchIngestModule.postIndexSummary.kwIdxErrMsgFiles=Keyword index service had errors ingesting {0} files.",
        "KeywordSearchIngestModule.postIndexSummary.kwIdxResultsLbl=Keyword Indexing Results",
        "KeywordSearchIngestModule.postIndexSummary.mdOnlyLbl=Metadata only was indexed",
        "KeywordSearchIngestModule.postIndexSummary.idxErrLbl=Error (indexer)"})
    private void postIndexSummary() {

        Map<IngestStatus, Long> statusCounts;
        synchronized (ingestStatus) {
            Map<Long, IngestStatus> ingestStatusForJob = ingestStatus.get(jobId);
            if (ingestStatusForJob == null) {
                return;
            }
            statusCounts = ingestStatusForJob.values().stream().
                    collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        }

        final Long metadataIndexed = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.METADATA_INGESTED), 0L);
        final Long stringsIndexed = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.STRINGS_INGESTED), 0L);
        final Long textIndexed = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.TEXT_INGESTED), 0L);
        final Long extractionErrors = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.SKIPPED_ERROR_TEXTEXTRACT), 0L);
        final Long ioErrors = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.SKIPPED_ERROR_IO), 0L);
        final Long indexingErrors = MoreObjects.firstNonNull(statusCounts.get(IngestStatus.SKIPPED_ERROR_INDEXING), 0L);

        StringBuilder msg = new StringBuilder();
        msg.append("<table border=0><tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_knowFileHeaderLbl())
                .append("</td><td>").append(textIndexed).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_fileGenStringsHead())
                .append("</td><td>").append(stringsIndexed).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_mdOnlyLbl())
                .append("</td><td>").append(metadataIndexed).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_idxErrLbl())
                .append("</td><td>").append(indexingErrors).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_errTxtLbl())
                .append("</td><td>").append(extractionErrors).append("</td></tr>"); //NON-NLS
        msg.append("<tr><td>")//NON-NLS
                .append(KeywordSearchIngestModule_postIndexSummary_errIoLbl())
                .append("</td><td>").append(ioErrors).append("</td></tr>"); //NON-NLS
        msg.append("</table>"); //NON-NLS

        String indexStats = msg.toString();
        logger.log(Level.INFO, "Keyword Indexing Completed: {0}", indexStats); //NON-NLS
        services.postMessage(IngestMessage.createMessage(MessageType.INFO, KeywordSearchModuleFactory.getModuleName(),
                KeywordSearchIngestModule_postIndexSummary_kwIdxResultsLbl(), indexStats));
        if (indexingErrors > 0) {
            MessageNotifyUtil.Notify.error(KeywordSearchIngestModule_postIndexSummary_kwIdxErrsTitle(),
                    KeywordSearchIngestModule_postIndexSummary_kwIdxErrMsgFiles(indexingErrors));
        } else if (ioErrors + extractionErrors > 0) {
            MessageNotifyUtil.Notify.warn(KeywordSearchIngestModule_postIndexSummary_kwIdxWarnMsgTitle(),
                    KeywordSearchIngestModule_postIndexSummary_idxErrReadFilesMsg());
        }
    }
}
