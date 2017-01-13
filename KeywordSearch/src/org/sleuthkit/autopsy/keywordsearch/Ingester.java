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

import com.google.common.base.Utf8;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.logging.Level;
import javax.annotation.concurrent.NotThreadSafe;
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
    private static final int SINGLE_READ_CHARS = 512;

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
        indexChunk("", file.getName(), getContentFields(file));
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
        indexChunk("", new ArtifactTextExtractor().getName(artifact), getContentFields(artifact));
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
     * Use the given TextExtractor to extract text from the given source. The
     * text will be chunked and each chunk passed to Solr to add to the index.
     *
     *
     * @param <A>       The type of the Appendix provider that provides
     *                  additional text to append to the final chunk.
     * @param <T>       A subclass of SleuthkitVisibleItem.
     * @param extractor The TextExtractor that will be used to extract text from
     *                  the given source.
     * @param source    The source from which text will be extracted, chunked,
     *                  and indexed.
     * @param context   The ingest job context that can be used to cancel this
     *                  process.
     *
     * @return True if this method executed normally. or False if there was an
     *         unexpected exception. //JMTODO: This policy needs to be reviewed.
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    < T extends SleuthkitVisitableItem> boolean indexText(TextExtractor< T> extractor, T source, IngestJobContext context) throws Ingester.IngesterException {
        final long sourceID = extractor.getID(source);
        final String sourceName = extractor.getName(source);

        int numChunks = 0; //unknown until chunking is done

        if (extractor.isDisabled()) {
            /* some Extrctors, notable the strings extractor, have options which
             * can be configured such that no extraction should be done */
            return true;
        }

        Map<String, String> fields = getContentFields(source);
        //Get a reader for the content of the given source
        try (BufferedReader reader = new BufferedReader(extractor.getReader(source));) {
            Chunker chunker = new Chunker(reader);
            for (Chunk chunk : chunker) {
                String chunkId = Server.getChunkIdString(sourceID, numChunks + 1);
                fields.put(Server.Schema.ID.toString(), chunkId);
                fields.put(Server.Schema.CHUNK_SIZE.toString(), String.valueOf(chunk.getBaseChunkLength()));
                try {
                    //add the chunk text to Solr index
                    indexChunk(chunk.toString(), sourceName, fields);
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
            //after all chunks, index just the meta data, including the  numChunks, of the parent file
            fields.put(Server.Schema.NUM_CHUNKS.toString(), Integer.toString(numChunks));
            fields.put(Server.Schema.ID.toString(), Long.toString(sourceID)); //reset id field to base document id
            indexChunk(null, sourceName, fields);
        }

        return true;
    }

    /**
     * Add one chunk as to the Solr index as a seperate sold document.
     *
     * TODO see if can use a byte or string streaming way to add content to
     * /update handler e.g. with XMLUpdateRequestHandler (deprecated in SOlr
     * 4.0.0), see if possible to stream with UpdateRequestHandler
     *
     * @param chunk  The chunk content as a string
     * @param fields
     * @param size
     *
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException
     */
    private void indexChunk(String chunk, String sourceName, Map<String, String> fields) throws IngesterException {
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

        //Make a SolrInputDocument out of the field map
        SolrInputDocument updateDoc = new SolrInputDocument();
        for (String key : fields.keySet()) {
            updateDoc.addField(key, fields.get(key));
        }
        //add the content to the SolrInputDocument
        //JMTODO: can we just add it to the field map before passing that in?
        updateDoc.addField(Server.Schema.CONTENT.toString(), chunk);

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
            params.put(Server.Schema.CTIME.toString(), ContentUtils.getStringTimeISO8601(file.getCtime(), file));
            params.put(Server.Schema.ATIME.toString(), ContentUtils.getStringTimeISO8601(file.getAtime(), file));
            params.put(Server.Schema.MTIME.toString(), ContentUtils.getStringTimeISO8601(file.getMtime(), file));
            params.put(Server.Schema.CRTIME.toString(), ContentUtils.getStringTimeISO8601(file.getCrtime(), file));
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
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(ArtifactTextExtractor.getDataSource(artifact).getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the artifact " + artifact.getArtifactID(), ex); //NON-NLS
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

/**
 * Encapsulates the content chunking algorithm in an implementation of the
 * Iterator interface. Also implements Iterable so it can be used directly in a
 * for loop. The base chunk is the part of the chunk before the overlapping
 * window. The window will be included at the end of the current chunk as well
 * as at the beginning of the next chunk.
 */
@NotThreadSafe
class Chunker implements Iterator<Chunk>, Iterable<Chunk> {

    //Chunking algorithm paramaters-------------------------------------//
    /** the maximum size of a chunk, including the window. */
    private static final int MAX_TOTAL_CHUNK_SIZE = 32766; //bytes
    /** the minimum to read before we start the process of looking for
     * whitespace to break at and creating an overlapping window. */
    private static final int MINIMUM_BASE_CHUNK_SIZE = 30 * 1024; //bytes
    /** The maximum size of the chunk, before the overlapping window, even if we
     * couldn't find whitespace to break at. */
    private static final int MAXIMUM_BASE_CHUNK_SIZE = 31 * 1024; //bytes
    /** The amount of text we will read through before we give up on finding
     * whitespace to break the chunk/window at. */
    private static final int WHITE_SPACE_BUFFER_SIZE = 512; //bytes
    /** The number of characters to read in one go from the Reader. */
    private static final int READ_CHARS_BUFFER_SIZE = 512; //chars

    ////chunker state--------------------------------------------///
    /** The Reader that this chunk reads from, and divides into chunks. It must
     * be a buffered reader to ensure that mark/reset are supported. */
    private final BufferedReader reader;
    /** The local buffer of characters read from the Reader. */
    private final char[] tempChunkBuf = new char[READ_CHARS_BUFFER_SIZE];
    /** number of chars read in the most recent read operation. */
    private int charsRead = 0;

    /** The text of the current chunk (so far). */
    private StringBuilder currentChunk;
    /** the size in bytes of the chunk (so far). */
    private int chunkSizeBytes = 0;
    /** the size in chars of the (base) chunk (so far). */
    private int baseChunkSizeChars;

    /** has the chunker found whitespace to break on? */
    private boolean whitespaceFound = false;
    /** has the chunker reached the end of the Reader? If so, there are no more
     * chunks, and the current chunk does not need a window. */
    private boolean endOfReaderReached = false;

    /**
     * Create a Chunker that will chunk the content of the given Reader.
     *
     * @param reader The content to chunk.
     */
    Chunker(BufferedReader reader) {
        this.reader = reader;
    }

    @Override
    public Iterator<Chunk> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return endOfReaderReached == false;
    }

    /**
     * Sanitize the given StringBuilder by replacing non-UTF-8 characters with
     * caret '^'
     *
     * @param sb the StringBuilder to sanitize
     *
     * //JMTODO: use Charsequence.chars() or codePoints() and then a mapping
     * function?
     */
    private static StringBuilder sanitizeToUTF8(StringBuilder sb) {
        final int length = sb.length();
        for (int i = 0; i < length; i++) {
            if (TextUtil.isValidSolrUTF8(sb.charAt(i)) == false) {
                sb.replace(i, i + 1, "^");
            }
        }
        return sb;
    }

    @Override
    public Chunk next() {
        if (endOfReaderReached) {
            throw new NoSuchElementException("There are no more chunks.");
        }
        //reset state for the next chunk
        currentChunk = new StringBuilder();
        chunkSizeBytes = 0;
        baseChunkSizeChars = 0;

        try {
            readBaseChunk();
            baseChunkSizeChars = currentChunk.length();
            reader.mark(2048); //mark the reader so we can rewind the reader here to begin the next chunk
            readWindow();
        } catch (IOException ioEx) {
            throw new RuntimeException("IOException while reading chunk.", ioEx);
        }
        try {
            reader.reset(); //reset the reader the so the next chunk can begin at the position marked above
        } catch (IOException ex) {
            throw new RuntimeException("IOException while resetting chunk reader.", ex);
        }

        if (endOfReaderReached) {
            /* if we have reached the end of the content,we won't make another
             * overlapping chunk, so the base chunk can be extended to the end. */
            baseChunkSizeChars = currentChunk.length();
        }
        //sanitize the text and return a Chunk object, that includes the base chunk length.
        return new Chunk(sanitizeToUTF8(currentChunk), baseChunkSizeChars);
    }

    /**
     * Read the base chunk from the reader, and attempt to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readBaseChunk() throws IOException {
        //read the chunk until the minimum base chunk size
        readHelper(MINIMUM_BASE_CHUNK_SIZE, false);
        //keep reading until the maximum base chunk size or white space is reached.
        whitespaceFound = false;
        readHelper(MAXIMUM_BASE_CHUNK_SIZE, true);

    }

    /**
     * Read the window from the reader, and attempt to break at whitespace.
     *
     * @throws IOException if there is a problem reading from the reader.
     */
    private void readWindow() throws IOException {
        //read the window, leaving some room to look for white space to break at.
        int windowEnd = Math.min(MAX_TOTAL_CHUNK_SIZE - WHITE_SPACE_BUFFER_SIZE, chunkSizeBytes + 1024);
        readHelper(windowEnd, false);
        whitespaceFound = false;
        //keep reading until the max chunk size, or until whitespace is reached.
        windowEnd = Math.min(MAX_TOTAL_CHUNK_SIZE, chunkSizeBytes + 1024);
        readHelper(windowEnd, true);
    }

    /** Helper method that implements reading in a loop.
     *
     * @param maxBytes           The max cummulative length of the content,in
     *                           bytes, to read from the Reader. That is, when
     *                           chunkSizeBytes >= maxBytes stop reading.
     * @param inWhiteSpaceBuffer Should the current read stop once whitespace is
     *                           found?
     *
     * @throws IOException If there is a problem reading from the Reader.
     */
    private void readHelper(int maxBytes, boolean inWhiteSpaceBuffer) throws IOException {
        //only read one character at a time if we are looking for whitespace.
        final int readSize = inWhiteSpaceBuffer ? 1 : READ_CHARS_BUFFER_SIZE;

        //read chars up to maxBytes, whitespaceFound if also inWhiteSpaceBuffer, or we reach the end of the reader.
        while ((chunkSizeBytes < maxBytes)
                && (false == (inWhiteSpaceBuffer && whitespaceFound))
                && (endOfReaderReached == false)) {
            charsRead = reader.read(tempChunkBuf, 0, readSize);
            if (-1 == charsRead) {
                //this is the last chunk
                endOfReaderReached = true;
            } else {
                if (inWhiteSpaceBuffer) {
                    //chec for whitespace.
                    whitespaceFound = Character.isWhitespace(tempChunkBuf[0]);
                }

                //add read chars to the chunk and update the length.
                String chunkSegment = new String(tempChunkBuf, 0, charsRead);
                chunkSizeBytes += Utf8.encodedLength(chunkSegment);
                currentChunk.append(chunkSegment);
            }
        }
    }
}

/**
 * Represents one chunk as the text in it and the length of the base chunk, in
 * chars.
 */
class Chunk {

    private final StringBuilder sb;
    private final int chunksize;

    Chunk(StringBuilder sb, int baseChunkLength) {
        this.sb = sb;
        this.chunksize = baseChunkLength;
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    int getBaseChunkLength() {
        return chunksize;
    }
}
