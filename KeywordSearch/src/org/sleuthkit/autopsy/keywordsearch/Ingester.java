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
import java.nio.charset.StandardCharsets;
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
import org.sleuthkit.datamodel.Content;
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
class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private volatile boolean uncommitedIngests = false;
    private final Server solrServer = KeywordSearch.getServer();
    private static final GetContentFieldsV getContentFieldsV = new GetContentFieldsV();
    private static Ingester instance;

    //for ingesting chunk as SolrInputDocument (non-content-streaming, by-pass tika)
    //TODO use a streaming way to add content to /update handler
    private static final int MAX_DOC_CHUNK_SIZE = 32 * 1024;

    private Ingester() {
    }

    public static synchronized Ingester getDefault() {
        if (instance == null) {
            instance = new Ingester();
        }
        return instance;
    }

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
     * Sends a file to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files. If the
     * file is a directory or ingestContent is set to false, the file name is
     * indexed only.
     *
     * @param file          File to ingest
     * @param ingestContent if true, index the file and the content, otherwise
     *                      index metadata only
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void indexMetaDataOnly(AbstractFile file) throws IngesterException {
        indexChunk(null, file.getName(), getContentFields(file), 0);
    }

    void indexMetaDataOnly(BlackboardArtifact artifact) throws IngesterException {
        indexChunk(null, artifact.getDisplayName() + "_" + artifact.getArtifactID(), getContentFields(artifact), 0);
    }

    /**
     * Creates a field map from FsContent, that is later sent to Solr
     *
     * @param fsc FsContent to get fields from
     *
     * @return the map
     */
    Map<String, String> getContentFields(SleuthkitVisitableItem fsc) {
        return fsc.accept(getContentFieldsV);
    }

    /**
     * Visitor used to create param list to send to SOLR index.
     */
    static private class GetContentFieldsV extends SleuthkitItemVisitor.Default<Map<String, String>> {

        @Override
        protected Map<String, String> defaultVisit(SleuthkitVisitableItem svi) {
            return new HashMap<>();
        }

        @Override
        public Map<String, String> visit(File f) {
            return getCommonFileContentFields(f);
        }

        @Override
        public Map<String, String> visit(DerivedFile df) {
            return getCommonFileContentFields(df);
        }

        @Override
        public Map<String, String> visit(Directory d) {
            return getCommonFileContentFields(d);
        }

        @Override
        public Map<String, String> visit(LayoutFile lf) {
            // layout files do not have times
            return getCommonFields(lf);
        }

        @Override
        public Map<String, String> visit(LocalFile lf) {
            return getCommonFileContentFields(lf);
        }

        @Override
        public Map<String, String> visit(SlackFile f) {
            return getCommonFileContentFields(f);
        }

        private Map<String, String> getCommonFileContentFields(AbstractFile file) {
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
                long dataSourceId = af.getDataSource().getId();
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSourceId));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the file {0}", af.getId()); //NON-NLS
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
                Content dataSource = ArtifactExtractor.getDataSource(artifact);
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(dataSource.getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get data source id to properly index the artifact {0}", artifact.getArtifactID()); //NON-NLS
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(-1));
            }

            return params;
        }
    }

    private static final int MAX_EXTR_TEXT_CHARS = 512 * 1024; //chars
    private static final int SINGLE_READ_CHARS = 1024;
    private static final int EXTRA_CHARS = 128; //for whitespace

    public <A, T extends SleuthkitVisitableItem> boolean indexText(TextExtractor<A, T> extractor, T source, IngestJobContext context) throws Ingester.IngesterException {
        int numChunks = 0; //unknown until chunking is done

        if (extractor.noExtractionOptionsAreEnabled()) {
            return true;
        }
        final long sourceID = extractor.getID(source);
        final String sourceName = extractor.getName(source);
        Map<String, String> fields = getContentFields(source);

        A appendix = extractor.newAppendixProvider();
        try (final InputStream stream = extractor.getInputStream(source);
                Reader reader = extractor.getReader(stream, source, appendix);) {

            //we read max 1024 chars at time, this seems to max what this Reader would return
            char[] textChunkBuf = new char[MAX_EXTR_TEXT_CHARS];
            long readSize;
            boolean eof = false;
            while (!eof) {
                int totalRead = 0;
                if (context != null && context.fileIngestIsCancelled()) {
                    return true;
                }
                if ((readSize = reader.read(textChunkBuf, 0, SINGLE_READ_CHARS)) == -1) {
                    eof = true;
                } else {
                    totalRead += readSize;
                }

                //consume more bytes to fill entire chunk (leave EXTRA_CHARS to end the word)
                while ((totalRead < MAX_EXTR_TEXT_CHARS - SINGLE_READ_CHARS - EXTRA_CHARS)
                        && (readSize = reader.read(textChunkBuf, totalRead, SINGLE_READ_CHARS)) != -1) {
                    totalRead += readSize;
                }
                if (readSize == -1) {
                    //this is the last chunk
                    eof = true;
                } else {
                    //try to read char-by-char until whitespace to not break words
                    while ((totalRead < MAX_EXTR_TEXT_CHARS - 1)
                            && !Character.isWhitespace(textChunkBuf[totalRead - 1])
                            && (readSize = reader.read(textChunkBuf, totalRead, 1)) != -1) {
                        totalRead += readSize;
                    }
                    if (readSize == -1) {
                        //this is the last chunk
                        eof = true;
                    }
                }

                StringBuilder sb = new StringBuilder(totalRead + 1000)
                        .append(textChunkBuf, 0, totalRead);

                if (eof) {
                    extractor.appendDataToFinalChunk(sb, appendix);
                }
                sanitizeToUTF8(sb);

                final String chunkString = sb.toString();

                //encode to bytes as UTF-8 to index as byte stream
                byte[] encodedBytes = chunkString.getBytes(Server.DEFAULT_INDEXED_TEXT_CHARSET);

                String chunkId = Server.getChunkIdString(sourceID, numChunks + 1);
                fields.put(Server.Schema.ID.toString(), chunkId);
                try {
                    try {
                        indexChunk(encodedBytes, sourceName, fields, encodedBytes.length);
                    } catch (Exception ex) {
                        throw new IngesterException(String.format("Error ingesting (indexing) file chunk: %s", chunkId), ex);
                    }
                    numChunks++;
                } catch (Ingester.IngesterException ingEx) {
                    extractor.logWarning("Ingester had a problem with extracted string from file '" //NON-NLS
                            + sourceName + "' (id: " + sourceID + ").", ingEx);//NON-NLS

                    throw ingEx; //need to rethrow to signal error and move on
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
    void indexChunk(byte[] docChunkContentBuf, String name, Map<String, String> fields, int size) throws IngesterException {
        if (fields.get(Server.Schema.IMAGE_ID.toString()) == null) {
            //skip the file, image id unknown
            String msg = NbBundle.getMessage(this.getClass(),
                    "Ingester.ingest.exception.unknownImgId.msg", name);
            logger.log(Level.SEVERE, msg);
            throw new IngesterException(msg);
        }

        SolrInputDocument updateDoc = new SolrInputDocument();

        for (String key : fields.keySet()) {
            updateDoc.addField(key, fields.get(key));
        }

        //using size here, but we are no longer ingesting entire files
        //size is normally a chunk size, up to 1MB
        if (size > 0) {
            String s = new String(docChunkContentBuf, 0, size, StandardCharsets.UTF_8);
//                char[] chars = null;
//                for (int i = 0; i < s.length(); i++) {
//                    if (!TextUtil.isValidSolrUTF8(s.charAt(i))) {
//                        // only convert string to char[] if there is a non-UTF8 character
//                        if (chars == null) {
//                            chars = s.toCharArray();
//                        }
//                        chars[i] = '^';
//                    }
//                }
//                if (chars != null) {
//                    s = new String(chars);
//                }
                updateDoc.addField(Server.Schema.CONTENT.toString(), s);

        } else {
            //no content, such as case when 0th chunk indexed
            updateDoc.addField(Server.Schema.CONTENT.toString(), "");
        }

        try {
            //TODO consider timeout thread, or vary socket timeout based on size of indexed content
            solrServer.addDocument(updateDoc);
            uncommitedIngests = true;
        } catch (KeywordSearchModuleException ex) {
            throw new IngesterException(
                    NbBundle.getMessage(this.getClass(), "Ingester.ingest.exception.err.msg", name), ex);
        }
    }

    /**
     * return timeout that should be used to index the content
     *
     * @param size size of the content
     *
     * @return time in seconds to use a timeout
     */
    static int getTimeout(long size) {
        if (size < 1024 * 1024L) //1MB
        {
            return 60;
        } else if (size < 10 * 1024 * 1024L) //10MB
        {
            return 1200;
        } else if (size < 100 * 1024 * 1024L) //100MB
        {
            return 3600;
        } else {
            return 3 * 3600;
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
