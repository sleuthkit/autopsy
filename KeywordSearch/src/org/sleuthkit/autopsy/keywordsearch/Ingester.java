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

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.keywordsearch.Chunker.Chunk;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.SleuthkitItemVisitor;
import org.sleuthkit.datamodel.SleuthkitVisitableItem;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Handles indexing files on a Solr core.
 */
//JMTODO: Should this class really be a singleton?
class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private volatile boolean uncommitedIngests = false;
    private final Server solrServer = KeywordSearch.getServer();
    private static final SolrFieldsVisitor SOLR_FIELDS_VISITOR = new SolrFieldsVisitor();
    private static Ingester instance;
    private final LanguageSpecificContentIndexingHelper languageSpecificContentIndexingHelper
        = new LanguageSpecificContentIndexingHelper();
    private static final int LANGUAGE_DETECTION_STRING_SIZE = 4096;

    private Ingester() {
    }

    public static synchronized Ingester getDefault() {
        if (instance == null) {
            instance = new Ingester();
        }
        return instance;
    }

    //JMTODO: this is probably useless
    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        super.finalize();

        // Warn if files might have been left uncommited.
        if (uncommitedIngests) {
            logger.warning("Ingester was used to add files that it never committed."); //NON-NLS
        }
    }

    /**
     * Sends the metadata (name, MAC times, image id, etc) for the given file to
     * Solr to be added to the index. commit() should be called once you're done
     * indexing.
     *
     * @param file File to index.
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void indexMetaDataOnly(AbstractFile file) throws IngesterException {
        indexChunk("", "", file.getName().toLowerCase(), new HashMap<>(getContentFields(file)));
    }

    /**
     * Sends the metadata (artifact id, image id, etc) for the given artifact to
     * Solr to be added to the index. commit() should be called once you're done
     * indexing.
     *
     * @param artifact The artifact to index.
     *
     * @throws IngesterException if there was an error processing a specific
     *                           artifact, but the Solr server is probably fine.
     */
    void indexMetaDataOnly(BlackboardArtifact artifact, String sourceName) throws IngesterException {
        indexChunk("", "", sourceName, new HashMap<>(getContentFields(artifact)));
    }

    /**
     * Creates a field map from a SleuthkitVisitableItem, that is later sent to
     * Solr.
     *
     * @param item SleuthkitVisitableItem to get fields from
     *
     * @return the map from field name to value (as a string)
     */
    private Map<String, String> getContentFields(SleuthkitVisitableItem item) {
        return item.accept(SOLR_FIELDS_VISITOR);
    }
    
    /**
     * Read and chunk the source text for indexing in Solr. Also performs
     * language detection on the input text.
     *
     * @param <A>     The type of the Appendix provider that provides additional
     *                text to append to the final chunk.
     * @param <T>     A subclass of SleuthkitVisibleItem.
     * @param Reader  The reader containing extracted text.
     * @param source  The source from which text will be extracted, chunked, and
     *                indexed.
     * @param context The ingest job context that can be used to cancel this
     *                process.
     *
     * @return True if indexing was completed, false otherwise.
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    // TODO (JIRA-3118): Cancelled text indexing does not propagate cancellation to clients 
    < T extends SleuthkitVisitableItem> boolean indexText(Reader sourceReader, long sourceID, String sourceName, T source, IngestJobContext context) throws Ingester.IngesterException {
        boolean doLanguageDetection = true;
        return indexText(sourceReader, sourceID, sourceName, source, context, doLanguageDetection);
    }
    
    /**
     * Read and chunk the source text for indexing in Solr. Does NOT perform
     * language detection on the input strings. Per JIRA-7100, it was determined 
     * that language detection on extracted strings can take a really long time.
     *
     * @param <A>     The type of the Appendix provider that provides additional
     *                text to append to the final chunk.
     * @param <T>     A subclass of SleuthkitVisibleItem.
     * @param Reader  The reader containing extracted text.
     * @param source  The source from which text will be extracted, chunked, and
     *                indexed.
     * @param context The ingest job context that can be used to cancel this
     *                process.
     *
     * @return True if indexing was completed, false otherwise.
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    // TODO (JIRA-3118): Cancelled text indexing does not propagate cancellation to clients 
    < T extends SleuthkitVisitableItem> boolean indexStrings(Reader sourceReader, long sourceID, String sourceName, T source, IngestJobContext context) throws Ingester.IngesterException {
        // Per JIRA-7100, it was determined that language detection on extracted strings can take a really long time.
        boolean doLanguageDetection = false;
        return indexText(sourceReader, sourceID, sourceName, source, context, doLanguageDetection);
    }
    
    /**
     * Read and chunk the source text for indexing in Solr.
     *
     *
     * @param <A>       The type of the Appendix provider that provides
     *                  additional text to append to the final chunk.
     * @param <T>       A subclass of SleuthkitVisibleItem.
     * @param Reader    The reader containing extracted text.
     * @param source    The source from which text will be extracted, chunked,
     *                  and indexed.
     * @param context   The ingest job context that can be used to cancel this
     *                  process.
     * @param doLanguageDetection A flag whether to perform language detection on the input text/strings.
     *
     * @return True if indexing was completed, false otherwise.
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    // TODO (JIRA-3118): Cancelled text indexing does not propagate cancellation to clients 
    private < T extends SleuthkitVisitableItem> boolean indexText(Reader sourceReader, long sourceID, String sourceName, T source, IngestJobContext context, boolean doLanguageDetection) throws Ingester.IngesterException {
        int numChunks = 0; //unknown until chunking is done
        
        Map<String, String> contentFields = Collections.unmodifiableMap(getContentFields(source));
        Optional<Language> language = Optional.empty();
        //Get a reader for the content of the given source
        try (BufferedReader reader = new BufferedReader(sourceReader)) {
            Chunker chunker = new Chunker(reader);
            while (chunker.hasNext()) {
                if (context != null && context.fileIngestIsCancelled()) {
                    logger.log(Level.INFO, "File ingest cancelled. Cancelling keyword search indexing of {0}", sourceName);
                    return false;
                }
                
                Chunk chunk = chunker.next();
                Map<String, Object> fields = new HashMap<>(contentFields);
                String chunkId = Server.getChunkIdString(sourceID, numChunks + 1);
                fields.put(Server.Schema.ID.toString(), chunkId);
                fields.put(Server.Schema.CHUNK_SIZE.toString(), String.valueOf(chunk.getBaseChunkLength()));

                if (doLanguageDetection) {
                    int size = Math.min(chunk.getBaseChunkLength(), LANGUAGE_DETECTION_STRING_SIZE);
                    language = languageSpecificContentIndexingHelper.detectLanguageIfNeeded(chunk.toString().substring(0, size));
                    
                    // only do language detection on the first chunk of the document
                    doLanguageDetection = false;
                }
                language.ifPresent(lang -> languageSpecificContentIndexingHelper.updateLanguageSpecificFields(fields, chunk, lang));
                try {
                    //add the chunk text to Solr index
                    indexChunk(chunk.toString(), chunk.geLowerCasedChunk(), sourceName, fields);
                    // add mini chunk when there's a language specific field
                    if (chunker.hasNext() && language.isPresent()) {
                        languageSpecificContentIndexingHelper.indexMiniChunk(chunk, sourceName, new HashMap<>(contentFields), chunkId, language.get());
                    }
                    numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    logger.log(Level.WARNING, "Ingester had a problem with extracted string from file '" //NON-NLS
                            + sourceName + "' (id: " + sourceID + ").", ingEx);//NON-NLS

                    throw ingEx; //need to rethrow to signal error and move on
                }
            }
            if (chunker.hasException()) {
                logger.log(Level.WARNING, "Error chunking content from " + sourceID + ": " + sourceName, chunker.getException());
                return false;
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Unexpected error, can't read content stream from " + sourceID + ": " + sourceName, ex);//NON-NLS
            return false;
        } finally {
            if (context != null && context.fileIngestIsCancelled()) {
                return false;
            } else {
                Map<String, Object> fields = new HashMap<>(contentFields);
                //after all chunks, index just the meta data, including the  numChunks, of the parent file
                fields.put(Server.Schema.NUM_CHUNKS.toString(), Integer.toString(numChunks));
                //reset id field to base document id
                fields.put(Server.Schema.ID.toString(), Long.toString(sourceID));
                //"parent" docs don't have chunk_size
                fields.remove(Server.Schema.CHUNK_SIZE.toString());
                indexChunk(null, null, sourceName, fields);
            }
        }
        return true;
    }

    /**
     * Add one chunk as to the Solr index as a separate Solr document.
     *
     * TODO see if can use a byte or string streaming way to add content to
     * /update handler e.g. with XMLUpdateRequestHandler (deprecated in SOlr
     * 4.0.0), see if possible to stream with UpdateRequestHandler
     *
     * @param chunk  The chunk content as a string, or null for metadata only
     * @param lowerCasedChunk The lower cased chunk content as a string, or null for metadata only
     * @param fields
     * @param size
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    private void indexChunk(String chunk, String lowerCasedChunk, String sourceName, Map<String, Object> fields) throws IngesterException {
        if (fields.get(Server.Schema.IMAGE_ID.toString()) == null) {
            //JMTODO: actually if the we couldn't get the image id it is set to -1,
            // but does this really mean we don't want to index it?

            //skip the file, image id unknown
            String msg = NbBundle.getMessage(Ingester.class,
                    "Ingester.ingest.exception.unknownImgId.msg", sourceName); //JMTODO: does this need to ne internationalized?
            logger.log(Level.SEVERE, msg);
            throw new IngesterException(msg);
        }

        //Make a SolrInputDocument out of the field map
        SolrInputDocument updateDoc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            if (fields.get(key).getClass() == String.class) {
                updateDoc.addField(key, Chunker.sanitize((String)fields.get(key)).toString());
            } else {
                updateDoc.addField(key, fields.get(key));
            }
        }

        try {
            //TODO: consider timeout thread, or vary socket timeout based on size of indexed content

            //add the content to the SolrInputDocument
            //JMTODO: can we just add it to the field map before passing that in?
            updateDoc.addField(Server.Schema.CONTENT.toString(), chunk);

            // We also add the content (if present) in lowercase form to facilitate case
            // insensitive substring/regular expression search.
            double indexSchemaVersion = NumberUtils.toDouble(solrServer.getIndexInfo().getSchemaVersion());
            if (indexSchemaVersion >= 2.1) {
                updateDoc.addField(Server.Schema.CONTENT_STR.toString(), ((chunk == null) ? "" : lowerCasedChunk));
            }

            TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Index chunk");

            solrServer.addDocument(updateDoc);
            HealthMonitor.submitTimingMetric(metric);
            uncommitedIngests = true;

        } catch (KeywordSearchModuleException | NoOpenCoreException ex) {
            //JMTODO: does this need to be internationalized?
            throw new IngesterException(
                    NbBundle.getMessage(Ingester.class, "Ingester.ingest.exception.err.msg", sourceName), ex);
        }
    }

    /**
     * Tells Solr to commit (necessary before ingested files will appear in
     * searches)
     */
    void commit() {
        try {
            solrServer.commit();
            uncommitedIngests = false;
        } catch (NoOpenCoreException | SolrServerException ex) {
            logger.log(Level.WARNING, "Error commiting index", ex); //NON-NLS

        }
    }

    /**
     * Visitor used to create fields to send to SOLR index.
     */
    static private class SolrFieldsVisitor extends SleuthkitItemVisitor.Default<Map<String, String>> {

        @Override
        protected Map<String, String> defaultVisit(SleuthkitVisitableItem svi) {
            return new HashMap<>();
        }

        @Override
        public Map<String, String> visit(File f) {
            return getCommonAndMACTimeFields(f);
        }

        @Override
        public Map<String, String> visit(DerivedFile df) {
            return getCommonAndMACTimeFields(df);
        }

        @Override
        public Map<String, String> visit(Directory d) {
            return getCommonAndMACTimeFields(d);
        }

        @Override
        public Map<String, String> visit(LocalDirectory ld) {
            return getCommonAndMACTimeFields(ld);
        }

        @Override
        public Map<String, String> visit(LayoutFile lf) {
            // layout files do not have times
            return getCommonFields(lf);
        }

        @Override
        public Map<String, String> visit(LocalFile lf) {
            return getCommonAndMACTimeFields(lf);
        }

        @Override
        public Map<String, String> visit(SlackFile f) {
            return getCommonAndMACTimeFields(f);
        }

        /**
         * Get the field map for AbstractFiles that includes MAC times and the
         * fields that are common to all file classes.
         *
         * @param file The file to get fields for
         *
         * @return The field map, including MAC times and common fields, for the
         *         give file.
         */
        private Map<String, String> getCommonAndMACTimeFields(AbstractFile file) {
            Map<String, String> params = getCommonFields(file);
            params.put(Server.Schema.CTIME.toString(), TimeZoneUtils.getFormattedTimeISO8601(file.getCtime()));
            params.put(Server.Schema.ATIME.toString(), TimeZoneUtils.getFormattedTimeISO8601(file.getAtime()));
            params.put(Server.Schema.MTIME.toString(), TimeZoneUtils.getFormattedTimeISO8601(file.getMtime()));
            params.put(Server.Schema.CRTIME.toString(), TimeZoneUtils.getFormattedTimeISO8601(file.getCrtime()));
            return params;
        }

        /**
         * Get the field map for AbstractFiles that is common to all file
         * classes
         *
         * @param file The file to get fields for
         *
         * @return The field map of fields that are common to all file classes.
         */
        private Map<String, String> getCommonFields(AbstractFile file) {
            Map<String, String> params = new HashMap<>();
            params.put(Server.Schema.ID.toString(), Long.toString(file.getId()));
            try {
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(file.getDataSource().getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the file " + file.getId(), ex); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }
            params.put(Server.Schema.FILE_NAME.toString(), file.getName().toLowerCase());
            return params;
        }

        /**
         * Get the field map for artifacts.
         *
         * @param artifact The artifact to get fields for.
         *
         * @return The field map for the given artifact.
         */
        @Override
        public Map<String, String> visit(BlackboardArtifact artifact) {
            Map<String, String> params = new HashMap<>();
            params.put(Server.Schema.ID.toString(), Long.toString(artifact.getArtifactID()));
            try {
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(artifact.getDataSource().getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the artifact " + artifact.getArtifactID(), ex); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }
            return params;
        }

        /**
         * Get the field map for artifacts.
         *
         * @param report The report to get fields for.
         *
         * @return The field map for the given report.
         */
        @Override
        public Map<String, String> visit(Report report) {
            Map<String, String> params = new HashMap<>();
            params.put(Server.Schema.ID.toString(), Long.toString(report.getId()));
            try {
                Content dataSource = report.getDataSource();
                if (null == dataSource) {
                    params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
                } else {
                    params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));
                }
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the report, using default value. Id: " + report.getId(), ex); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }
            return params;
        }
    }

    /**
     * Indicates that there was an error with the specific ingest operation, but
     * it's still okay to continue ingesting files.
     */
    static class IngesterException extends Exception {

        private static final long serialVersionUID = 1L;

        IngesterException(String message, Throwable ex) {
            super(message, ex);
        }

        IngesterException(String message) {
            super(message);
        }
    }
}
