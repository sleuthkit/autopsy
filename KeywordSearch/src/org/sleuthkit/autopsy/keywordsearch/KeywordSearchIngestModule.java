/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import static java.util.Locale.US;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.tika.mime.MimeTypes;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.ExecUtil.ProcessTerminator;
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
import org.sleuthkit.autopsy.textextractors.TextExtractor;
import org.sleuthkit.autopsy.textextractors.TextExtractorFactory;
import org.sleuthkit.autopsy.textextractors.TextFileExtractor;
import org.sleuthkit.autopsy.textextractors.configs.ImageConfig;
import org.sleuthkit.autopsy.textextractors.configs.StringsConfig;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Blackboard;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
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
@NbBundle.Messages({
    "# {0} - Reason for not starting Solr", "KeywordSearchIngestModule.init.tryStopSolrMsg={0}<br />Please try stopping Java Solr processes if any exist and restart the application.",
    "KeywordSearchIngestModule.init.badInitMsg=Keyword search server was not properly initialized, cannot run keyword search ingest.",
    "SolrConnectionCheck.Port=Invalid port number.",
    "# {0} - Reason for not connecting to Solr", "KeywordSearchIngestModule.init.exception.errConnToSolr.msg=Error connecting to SOLR server: {0}.",
    "KeywordSearchIngestModule.startUp.noOpenCore.msg=The index could not be opened or does not exist.",
    "CannotRunFileTypeDetection=Unable to run file type detection."
})
public final class KeywordSearchIngestModule implements FileIngestModule {

    private static final int LIMITED_OCR_SIZE_MIN = 100 * 1024;

    /**
     * generally text extractors should ignore archives and let unpacking
     * modules take care of them
     */
    private static final List<String> ARCHIVE_MIME_TYPES
            = ImmutableList.of(
                    //ignore unstructured binary and compressed data, for which string extraction or unzipper works better
                    "application/x-7z-compressed", //NON-NLS
                    "application/x-ace-compressed", //NON-NLS
                    "application/x-alz-compressed", //NON-NLS
                    "application/x-arj", //NON-NLS
                    "application/vnd.ms-cab-compressed", //NON-NLS
                    "application/x-cfs-compressed", //NON-NLS
                    "application/x-dgc-compressed", //NON-NLS
                    "application/x-apple-diskimage", //NON-NLS
                    "application/x-gca-compressed", //NON-NLS
                    "application/x-dar", //NON-NLS
                    "application/x-lzx", //NON-NLS
                    "application/x-lzh", //NON-NLS
                    "application/x-rar-compressed", //NON-NLS
                    "application/x-stuffit", //NON-NLS
                    "application/x-stuffitx", //NON-NLS
                    "application/x-gtar", //NON-NLS
                    "application/x-archive", //NON-NLS
                    "application/x-executable", //NON-NLS
                    "application/x-gzip", //NON-NLS
                    "application/zip", //NON-NLS
                    "application/x-zoo", //NON-NLS
                    "application/x-cpio", //NON-NLS
                    "application/x-shar", //NON-NLS
                    "application/x-tar", //NON-NLS
                    "application/x-bzip", //NON-NLS
                    "application/x-bzip2", //NON-NLS
                    "application/x-lzip", //NON-NLS
                    "application/x-lzma", //NON-NLS
                    "application/x-lzop", //NON-NLS
                    "application/x-z", //NON-NLS
                    "application/x-compress"); //NON-NLS

    private static final List<String> METADATA_DATE_TYPES
            = ImmutableList.of(
                    "Last-Save-Date", //NON-NLS
                    "Last-Printed", //NON-NLS
                    "Creation-Date"); //NON-NLS

    private static final Map<String, BlackboardAttribute.ATTRIBUTE_TYPE> METADATA_TYPES_MAP = ImmutableMap.<String, BlackboardAttribute.ATTRIBUTE_TYPE>builder()
            .put("Last-Save-Date", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_MODIFIED)
            .put("Last-Author", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_USER_ID)
            .put("Creation-Date", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_CREATED)
            .put("Company", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_ORGANIZATION)
            .put("Author", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_OWNER)
            .put("Application-Name", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)
            .put("Last-Printed", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_LAST_PRINTED_DATETIME)
            .put("Producer", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PROG_NAME)
            .put("Title", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DESCRIPTION)
            .put("pdf:PDFVersion", BlackboardAttribute.ATTRIBUTE_TYPE.TSK_VERSION)
            .build();

    private static final String IMAGE_MIME_TYPE_PREFIX = "image/";
    
    // documents where OCR is performed
    private static final ImmutableSet<String> OCR_DOCUMENTS = ImmutableSet.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );
    
    /**
     * Options for this extractor
     */
    enum StringsExtractOptions {
        EXTRACT_UTF16, ///< extract UTF16 text, true/false
        EXTRACT_UTF8, ///< extract UTF8 text, true/false
    };

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
    private Lookup stringsExtractionContext;
    private final KeywordSearchJobSettings settings;
    private boolean initialized = false;
    private long jobId;
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

        Server server = KeywordSearch.getServer();
        if (server.coreIsOpen() == false) {
            throw new IngestModuleException(Bundle.KeywordSearchIngestModule_startUp_noOpenCore_msg());
        }

        try {
            Index indexInfo = server.getIndexInfo();
            if (!indexInfo.isCompatible(IndexFinder.getCurrentSchemaVersion())) {
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
            openCase = Case.getCurrentCaseThrows();
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
                    if (!server.isLocalSolrRunning()) {
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

        StringsConfig stringsConfig = new StringsConfig();
        Map<String, String> stringsOptions = KeywordSearchSettings.getStringExtractOptions();
        stringsConfig.setExtractUTF8(Boolean.parseBoolean(stringsOptions.get(StringsExtractOptions.EXTRACT_UTF8.toString())));
        stringsConfig.setExtractUTF16(Boolean.parseBoolean(stringsOptions.get(StringsExtractOptions.EXTRACT_UTF16.toString())));
        stringsConfig.setLanguageScripts(KeywordSearchSettings.getStringExtractScripts());

        stringsExtractionContext = Lookups.fixed(stringsConfig);

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

        // if ocr only is enabled and not an ocr file, return
        Optional<TextExtractor> extractorOpt = getExtractor(abstractFile);

        String mimeType = fileTypeDetector.getMIMEType(abstractFile).trim().toLowerCase();

        if (settings.isOCREnabled()) {
            // if ocr only and the extractor is not present or will not perform ocr on this file, continue
            if (settings.isOCROnly() && (!extractorOpt.isPresent() || !extractorOpt.get().willUseOCR())) {
                return ProcessResult.OK;
            }

            // if limited ocr is enabled, the extractor will use ocr, and 
            // the file would not be subject to limited ocr reading, continue
            if (settings.isLimitedOCREnabled() && extractorOpt.isPresent()
                    && extractorOpt.get().willUseOCR() && !isLimitedOCRFile(abstractFile, mimeType)) {
                return ProcessResult.OK;
            }
        }

        if (KeywordSearchSettings.getSkipKnown() && abstractFile.getKnown().equals(FileKnown.KNOWN)) {
            //index meta-data only
            if (context.fileIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            indexer.indexFile(extractorOpt, abstractFile, mimeType, false);
            return ProcessResult.OK;
        }

        //index the file and content (if the content is supported)
        if (context.fileIngestIsCancelled()) {
            return ProcessResult.OK;
        }
        indexer.indexFile(extractorOpt, abstractFile, mimeType, true);

        // Start searching if it hasn't started already
        if (!startedSearching) {
            if (context.fileIngestIsCancelled()) {
                return ProcessResult.OK;
            }
            List<String> keywordListNames = settings.getNamesOfEnabledKeyWordLists();
            IngestSearchRunner.getInstance().startJob(context, keywordListNames);
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
            IngestSearchRunner.getInstance().stopJob(jobId);
            cleanup();
            return;
        }

        // Remove from the search list and trigger final commit and final search
        IngestSearchRunner.getInstance().endJob(jobId);

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
        stringsExtractionContext = null;
        initialized = false;
    }

    /**
     * Returns true if file should have OCR performed on it when limited OCR 
     * setting is specified.
     *
     * @param aFile    The abstract file.
     * @param mimeType The file mime type.
     *
     * @return True if file should have text extracted when limited OCR setting
     *         is on.
     */
    private boolean isLimitedOCRFile(AbstractFile aFile, String mimeType) {
        if (OCR_DOCUMENTS.contains(mimeType)) {
            return true;
        }
        
        if (mimeType.startsWith(IMAGE_MIME_TYPE_PREFIX)) {
            return aFile.getSize() > LIMITED_OCR_SIZE_MIN
                || aFile.getType() == TskData.TSK_DB_FILES_TYPE_ENUM.DERIVED;
        }
        
        return false;
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

    private Optional<TextExtractor> getExtractor(AbstractFile abstractFile) {
        ImageConfig imageConfig = new ImageConfig();
        imageConfig.setOCREnabled(settings.isOCREnabled());
        ProcessTerminator terminator = () -> context.fileIngestIsCancelled();
        Lookup extractionContext = Lookups.fixed(imageConfig, terminator);
        try {
            return Optional.ofNullable(TextExtractorFactory.getExtractor(abstractFile, extractionContext));
        } catch (TextExtractorFactory.NoTextExtractorFound ex) {
            return Optional.empty();
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
         * @param extractorOptional The textExtractor to use with this file or
         *                          empty.
         * @param aFile             file to extract strings from, divide into
         *                          chunks and index
         * @param extractedMetadata Map that will be populated with the file's
         *                          metadata.
         *
         * @return true if the file was text_ingested, false otherwise
         *
         * @throws IngesterException exception thrown if indexing failed
         */
        private boolean extractTextAndIndex(Optional<TextExtractor> extractorOptional, AbstractFile aFile,
                Map<String, String> extractedMetadata) throws IngesterException {

            try {
                if (!extractorOptional.isPresent()) {
                    return false;
                }
                TextExtractor extractor = extractorOptional.get();
                Reader fileText = extractor.getReader();
                Reader finalReader;
                try {
                    Map<String, String> metadata = extractor.getMetadata();
                    if (!metadata.isEmpty()) {
                        // Creating the metadata artifact here causes occasional problems
                        // when indexing the text, so we save the metadata map to 
                        // use after this method is complete.
                        extractedMetadata.putAll(metadata);
                    }
                    CharSource formattedMetadata = getMetaDataCharSource(metadata);
                    //Append the metadata to end of the file text
                    finalReader = CharSource.concat(new CharSource() {
                        //Wrap fileText reader for concatenation
                        @Override
                        public Reader openStream() throws IOException {
                            return fileText;
                        }
                    }, formattedMetadata).openStream();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, String.format("Could not format extracted metadata for file %s [id=%d]",
                            aFile.getName(), aFile.getId()), ex);
                    //Just send file text.
                    finalReader = fileText;
                }
                //divide into chunks and index
                return Ingester.getDefault().indexText(finalReader, aFile.getId(), aFile.getName(), aFile, context);
            } catch (TextExtractor.InitReaderException ex) {
                // Text extractor could not be initialized.  No text will be extracted.
                return false;
            }
        }

        private void createMetadataArtifact(AbstractFile aFile, Map<String, String> metadata) {

            String moduleName = KeywordSearchIngestModule.class.getName();

            Collection<BlackboardAttribute> attributes = new ArrayList<>();
            Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                if (METADATA_TYPES_MAP.containsKey(entry.getKey())) {
                    BlackboardAttribute bba = checkAttribute(entry.getKey(), entry.getValue());
                    if (bba != null) {
                        attributes.add(bba);
                    }
                }
            }
            if (!attributes.isEmpty()) {
                try {
                    BlackboardArtifact bbart = aFile.newDataArtifact(new BlackboardArtifact.Type(BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA), attributes);
                    bbartifacts.add(bbart);
                } catch (TskCoreException ex) {
                    // Log error and return to continue processing
                    logger.log(Level.WARNING, String.format("Error creating or adding metadata artifact for file %s.", aFile.getParentPath() + aFile.getName()), ex); //NON-NLS
                    return;
                }
                if (!bbartifacts.isEmpty()) {
                    try {
                        Case.getCurrentCaseThrows().getSleuthkitCase().getBlackboard().postArtifacts(bbartifacts, moduleName, jobId);
                    } catch (NoCurrentCaseException | Blackboard.BlackboardException ex) {
                        // Log error and return to continue processing
                        logger.log(Level.WARNING, String.format("Unable to post blackboard artifacts for file $s.", aFile.getParentPath() + aFile.getName()), ex); //NON-NLS
                        return;
                    }
                }
            }
        }

        private BlackboardAttribute checkAttribute(String key, String value) {
            String moduleName = KeywordSearchIngestModule.class.getName();
            if (!value.isEmpty() && value.charAt(0) != ' ') {
                if (METADATA_DATE_TYPES.contains(key)) {
                    SimpleDateFormat metadataDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", US);
                    Long metadataDateTime = Long.valueOf(0);
                    try {
                        String metadataDate = value.replaceAll("T", " ").replaceAll("Z", "");
                        Date usedDate = metadataDateFormat.parse(metadataDate);
                        metadataDateTime = usedDate.getTime() / 1000;
                        return new BlackboardAttribute(METADATA_TYPES_MAP.get(key), moduleName, metadataDateTime);
                    } catch (ParseException ex) {
                        // catching error and displaying date that could not be parsed then will continue on.
                        logger.log(Level.WARNING, String.format("Failed to parse date/time %s for metadata attribute %s.", value, key), ex); //NON-NLS
                        return null;
                    }
                } else {
                    return new BlackboardAttribute(METADATA_TYPES_MAP.get(key), moduleName, value);
                }
            }

            return null;

        }

        /**
         * Pretty print the text extractor metadata.
         *
         * @param metadata The Metadata map to wrap as a CharSource
         *
         * @return A CharSource for the given Metadata
         */
        @NbBundle.Messages({
            "KeywordSearchIngestModule.metadataTitle=METADATA"
        })
        private CharSource getMetaDataCharSource(Map<String, String> metadata) {
            return CharSource.wrap(new StringBuilder(
                    String.format("\n\n------------------------------%s------------------------------\n\n",
                            Bundle.KeywordSearchIngestModule_metadataTitle()))
                    .append(metadata.entrySet().stream().sorted(Map.Entry.comparingByKey())
                            .map(entry -> entry.getKey() + ": " + entry.getValue())
                            .collect(Collectors.joining("\n"))
                    ));
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
                TextExtractor stringsExtractor = TextExtractorFactory.getStringsExtractor(aFile, stringsExtractionContext);
                Reader extractedTextReader = stringsExtractor.getReader();
                if (Ingester.getDefault().indexStrings(extractedTextReader, aFile.getId(), aFile.getName(), aFile, KeywordSearchIngestModule.this.context)) {
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.STRINGS_INGESTED);
                    return true;
                } else {
                    logger.log(Level.WARNING, "Failed to extract strings and ingest, file ''{0}'' (id: {1}).", new Object[]{aFile.getName(), aFile.getId()});  //NON-NLS
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_TEXTEXTRACT);
                    return false;
                }
            } catch (IngesterException | TextExtractor.InitReaderException ex) {
                logger.log(Level.WARNING, "Failed to extract strings and ingest, file '" + aFile.getName() + "' (id: " + aFile.getId() + ").", ex);  //NON-NLS
                putIngestStatus(jobId, aFile.getId(), IngestStatus.SKIPPED_ERROR_INDEXING);
                return false;
            }
        }

        /**
         * Adds the file to the index. Detects file type, calls extractors, etc.
         *
         * @param extractor    The textExtractor to use with this file or empty
         *                     if no extractor found.
         * @param aFile        File to analyze.
         * @param mimeType     The file mime type.
         * @param indexContent False if only metadata should be text_ingested.
         *                     True if content and metadata should be index.
         */
        private void indexFile(Optional<TextExtractor> extractor, AbstractFile aFile, String mimeType, boolean indexContent) {
            //logger.log(Level.INFO, "Processing AbstractFile: " + abstractFile.getName());

            TskData.TSK_DB_FILES_TYPE_ENUM aType = aFile.getType();

            /**
             * Extract unicode strings from unallocated and unused blocks and
             * carved text files. The reason for performing string extraction on
             * these is because they all may contain multiple encodings which
             * can cause text to be missed by the more specialized text
             * extractors used below.
             */
            if ((aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNALLOC_BLOCKS)
                    || aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.UNUSED_BLOCKS))
                    || (aType.equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED) && aFile.getNameExtension().equalsIgnoreCase("txt"))) {
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

            // we skip archive formats that are opened by the archive module. 
            // @@@ We could have a check here to see if the archive module was enabled though...
            if (ARCHIVE_MIME_TYPES.contains(mimeType)) {
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
            Map<String, String> extractedMetadata = new HashMap<>();

            //extract text with one of the extractors, divide into chunks and index with Solr
            try {
                //logger.log(Level.INFO, "indexing: " + aFile.getName());
                if (context.fileIngestIsCancelled()) {
                    return;
                }
                if (MimeTypes.OCTET_STREAM.equals(mimeType)) {
                    extractStringsAndIndex(aFile);
                    return;
                }
                if (!extractTextAndIndex(extractor, aFile, extractedMetadata)) {
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

            if ((wasTextAdded == false) && (aFile.getNameExtension().equalsIgnoreCase("txt") && !(aFile.getType().equals(TskData.TSK_DB_FILES_TYPE_ENUM.CARVED)))) {
                //Carved Files should be the only type of unallocated files capable of a txt extension and 
                //should be ignored by the TextFileExtractor because they may contain more than one text encoding
                wasTextAdded = indexTextFile(aFile);
            }

            // if it wasn't supported or had an error, default to strings
            if (wasTextAdded == false) {
                extractStringsAndIndex(aFile);
            }

            // Now that the indexing is complete, create the metadata artifact (if applicable).
            // It is unclear why calling this from extractTextAndIndex() generates
            // errors.
            if (!extractedMetadata.isEmpty()) {
                createMetadataArtifact(aFile, extractedMetadata);
            }
        }

        /**
         * Adds the text file to the index given an encoding. Returns true if
         * indexing was successful and false otherwise.
         *
         * @param aFile Text file to analyze
         */
        private boolean indexTextFile(AbstractFile aFile) {
            try {
                TextFileExtractor textFileExtractor = new TextFileExtractor(aFile);
                Reader textReader = textFileExtractor.getReader();
                if (textReader == null) {
                    logger.log(Level.INFO, "Unable to extract with TextFileExtractor, Reader was null for file: {0}", aFile.getName());
                } else if (Ingester.getDefault().indexText(textReader, aFile.getId(), aFile.getName(), aFile, context)) {
                    textReader.close();
                    putIngestStatus(jobId, aFile.getId(), IngestStatus.TEXT_INGESTED);
                    return true;
                }
            } catch (IngesterException | IOException | TextExtractor.InitReaderException ex) {
                logger.log(Level.WARNING, "Unable to index " + aFile.getName(), ex);
            }
            return false;
        }
    }
}
