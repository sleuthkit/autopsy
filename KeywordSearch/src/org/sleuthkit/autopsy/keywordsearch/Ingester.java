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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
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

    private static final int MAX_EXTR_TEXT_CHARS = 512 * 1024; //chars
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace

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
        indexChunk(null, file.getName(), getContentFields(file), 0);
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
    void indexMetaDataOnly(BlackboardArtifact artifact) throws IngesterException {
        indexChunk(null, new ArtifactTextExtractor().getName(artifact), getContentFields(artifact), 0);
    }

    /**
     * Creates a field map from FsContent, that is later sent to Solr
     *
     * @param item SleuthkitVisitableItem to get fields from
     *
     * @return the map
     */
    Map<String, String> getContentFields(SleuthkitVisitableItem item) {
        return item.accept(SOLR_FIELDS_VISITOR);
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
            return getFileFields(f);
        }

        @Override
        public Map<String, String> visit(DerivedFile df) {
            return getFileFields(df);
        }

        @Override
        public Map<String, String> visit(Directory d) {
            return getFileFields(d);
        }

        @Override
        public Map<String, String> visit(LayoutFile lf) {
            // layout files do not have times
            return getCommonFields(lf);
        }

        @Override
        public Map<String, String> visit(LocalFile lf) {
            return getFileFields(lf);
        }

        @Override
        public Map<String, String> visit(SlackFile f) {
            return getFileFields(f);
        }

        private Map<String, String> getFileFields(AbstractFile file) {
            Map<String, String> params = getCommonFields(file);
            params.put(Server.Schema.CTIME.toString(), ContentUtils.getStringTimeISO8601(file.getCtime(), file));
            params.put(Server.Schema.ATIME.toString(), ContentUtils.getStringTimeISO8601(file.getAtime(), file));
            params.put(Server.Schema.MTIME.toString(), ContentUtils.getStringTimeISO8601(file.getMtime(), file));
            params.put(Server.Schema.CRTIME.toString(), ContentUtils.getStringTimeISO8601(file.getCrtime(), file));
            return params;
        }

        private Map<String, String> getCommonFields(AbstractFile af) {
            Map<String, String> params = new HashMap<>();
            params.put(Server.Schema.ID.toString(), Long.toString(af.getId()));
            try {
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(af.getDataSource().getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the file " + af.getId(), ex); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }
            params.put(Server.Schema.FILE_NAME.toString(), af.getName());
            return params;
        }

        @Override
        public Map<String, String> visit(BlackboardArtifact artifact) {
            Map<String, String> params = new HashMap<>();
            params.put(Server.Schema.ID.toString(), Long.toString(artifact.getArtifactID()));
            try {
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(ArtifactTextExtractor.getDataSource(artifact).getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the artifact " + artifact.getArtifactID(), ex); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }
            return params;
        }
    }


    public <A, T extends SleuthkitVisitableItem> boolean indexText(TextExtractor<A, T> extractor, T source, IngestJobContext context) throws Ingester.IngesterException {
        final long sourceID = extractor.getID(source);
        final String sourceName = extractor.getName(source);

        int numChunks = 0; //unknown until chunking is done

        if (extractor.noExtractionOptionsAreEnabled()) {
            return true;
        }

        Map<String, String> fields = getContentFields(source);

        A appendix = extractor.newAppendixProvider();
        try (final InputStream stream = extractor.getInputStream(source);
                Reader reader = extractor.getReader(stream, source, appendix);) {

            //we read max 1024 chars at time, this seems to max what this Reader would return
            char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];

            boolean eof = false;
            while (!eof) {
                int chunkSizeInChars = 0;
                if (context != null && context.fileIngestIsCancelled()) {
                    return true;
                }
                long charsRead = 0;
                //consume bytes to fill entire chunk (but leave EXTRA_CHARS to end the word)
                while ((chunkSizeInChars < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (charsRead = reader.read(textChunkBuf, chunkSizeInChars, SINGLE_READ_CHARS)) != -1) {
                    chunkSizeInChars += charsRead;
                }

                if (charsRead == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    chunkSizeInChars += charsRead;

                    //try to read char-by-char until whitespace to not break words
                    while ((chunkSizeInChars < MAX_EXTR_TEXT_CHARS - 1)
                            && (Character.isWhitespace(textChunkBuf[chunkSizeInChars - 1]) == false)
                            && (charsRead = reader.read(textChunkBuf, chunkSizeInChars, 1)) != -1) {
                        chunkSizeInChars += charsRead;
                    }
                    if (charsRead == -1) {
                        //this is the last chunk
                        eof = true;
                    }
                }

                StringBuilder sb;
                if (eof) {
                    //1000 char buffer is to allow for appendix data with out needing to resize the string builder.
                    sb = new StringBuilder(chunkSizeInChars + 1000)
                            .append(textChunkBuf, 0, chunkSizeInChars);
                    extractor.appendDataToFinalChunk(sb, appendix);
                } else {
                    sb = new StringBuilder(chunkSizeInChars)
                            .append(textChunkBuf, 0, chunkSizeInChars);

                }

                sanitizeToUTF8(sb);

                String chunkId = Server.getChunkIdString(sourceID, numChunks + 1);
                fields.put(Server.Schema.ID.toString(), chunkId);
                try {
                    indexChunk(sb.toString(), sourceName, fields, sb.length());
                    numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    extractor.logWarning("Ingester had a problem with extracted string from file '" //NON-NLS
                            + sourceName + "' (id: " + sourceID + ").", ingEx);//NON-NLS

                    throw ingEx; //need to rethrow to signal error and move on
                } catch (Exception ex) {
                    throw new IngesterException(String.format("Error ingesting (indexing) file chunk: %s", chunkId), ex);
                }
            }
        } catch (IOException ex) {
            extractor.logWarning("Unable to read content stream from " + sourceID + ": " + sourceName, ex);//NON-NLS
            return false;
        } catch (Exception ex) {
            extractor.logWarning("Unexpected error, can't read content stream from " + sourceID + ": " + sourceName, ex);//NON-NLS
            return false;
        } finally {
            //after all chunks, ingest the parent file without content itself, and store numChunks
            fields.put(Server.Schema.NUM_CHUNKS.toString(), Integer.toString(numChunks));
            fields.put(Server.Schema.ID.toString(), Long.toString(sourceID));
            indexChunk(null, sourceName, fields, 0);
        }
        return true;
    }

    /**
     * Sanitize the given chars by replacing non-UTF-8 characters with caret '^'
     *
     * @param totalRead    the number of chars in textChunkBuf
     * @param textChunkBuf the characters to sanitize
     *
     * //JMTODO: use Charsequence.chars() or codePoints() and then a mapping
     * function?
     */
    private static void sanitizeToUTF8(StringBuilder sb) {
        final int length = sb.length();

        // Sanitize by replacing non-UTF-8 characters with caret '^'
        for (int i = 0; i < length; i++) {
            if (!TextUtil.isValidSolrUTF8(sb.charAt(i))) {
                sb.replace(i, i + 1, "^'");
            }
        }
    }

    /**
     * Indexing method that bypasses Tika, assumes pure text It reads and
     * converts the entire content stream to string, assuming UTF8 since we
     * can't use streaming approach for Solr /update handler. This should be
     * safe, since all content is now in max 1MB chunks.
     *
     * TODO see if can use a byte or string streaming way to add content to
     * /update handler e.g. with XMLUpdateRequestHandler (deprecated in SOlr
     * 4.0.0), see if possible to stream with UpdateRequestHandler
     *
     * @param cs
     * @param fields
     * @param size
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    void indexChunk(String chunk, String sourceName, Map<String, String> fields, int size) throws IngesterException {
        if (fields.get(Server.Schema.IMAGE_ID.toString()) == null) {
            //JMTODO: actually if the we couldn't get the image id it is set to -1,
            // but does this really mean we don't want to index it?

            //skip the file, image id unknown
            //JMTODO: does this need to ne internationalized?
            String msg = NbBundle.getMessage(Ingester.class,
                    "Ingester.ingest.exception.unknownImgId.msg", sourceName); //JMTODO: does this need to ne internationalized?
            logger.log(Level.SEVERE, msg);
            throw new IngesterException(msg);
        }

        SolrInputDocument updateDoc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            updateDoc.addField(key, fields.get(key));
        }

        //using size here, but we are no longer ingesting entire files
        //size is normally a chunk size, up to 1MB
        updateDoc.addField(Server.Schema.CONTENT.toString(), (size > 0) ? chunk : "");

        try {
            //TODO: consider timeout thread, or vary socket timeout based on size of indexed content
            solrServer.addDocument(updateDoc);
            uncommitedIngests = true;
        } catch (KeywordSearchModuleException ex) {
            //JMTODO: does this need to ne internationalized?
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
