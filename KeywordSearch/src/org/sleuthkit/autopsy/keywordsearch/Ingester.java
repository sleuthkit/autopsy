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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.SolrInputDocument;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.TextUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Handles indexing files on a Solr core.
 */
class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private volatile boolean uncommitedIngests = false;
    private final Server solrServer = KeywordSearch.getServer();
    private final GetContentFieldsV getContentFieldsV = new GetContentFieldsV();
    private static Ingester instance;

    //for ingesting chunk as SolrInputDocument (non-content-streaming, by-pass tika)
    //TODO use a streaming way to add content to /update handler
    private static final int MAX_DOC_CHUNK_SIZE = 1024 * 1024;
    private static final String ENCODING = "UTF-8"; //NON-NLS

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
     * Sends a stream to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files.
     *
     * @param afscs File AbstractFileStringContentStream to ingest
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void ingest(AbstractFileStringContentStream afscs) throws IngesterException {
        Map<String, String> params = getContentFields(afscs.getSourceContent());
        ingest(afscs, params, afscs.getSourceContent().getSize());
    }

    /**
     * Sends a TextExtractor to Solr to have its content extracted and added to
     * the index. commit() should be called once you're done ingesting files.
     * FileExtract represents a parent of extracted file with actual content.
     * The parent itself has no content, only meta data and is used to associate
     * the extracted AbstractFileChunk
     *
     * @param fe TextExtractor to ingest
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void ingest(TextExtractor fe) throws IngesterException {
        Map<String, String> params = getContentFields(fe.getSourceFile());

        params.put(Server.Schema.NUM_CHUNKS.toString(), Integer.toString(fe.getNumChunks()));

        ingest(new NullContentStream(fe.getSourceFile()), params, 0);
    }

    /**
     * Sends a AbstractFileChunk to Solr and its extracted content stream to be
     * added to the index. commit() should be called once you're done ingesting
     * files. AbstractFileChunk represents a file chunk and its chunk content.
     *
     * @param fec  AbstractFileChunk to ingest
     * @param size approx. size of the stream in bytes, used for timeout
     *             estimation
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void ingest(AbstractFileChunk fec, ByteContentStream bcs, int size) throws IngesterException {
        AbstractContent sourceContent = bcs.getSourceContent();
        Map<String, String> params = getContentFields(sourceContent);

        //overwrite id with the chunk id
        params.put(Server.Schema.ID.toString(),
                Server.getChunkIdString(sourceContent.getId(), fec.getChunkNumber()));

        ingest(bcs, params, size);
    }

    /**
     * Sends a file to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files. If the
     * file is a directory or ingestContent is set to false, the file name is
     * indexed only.
     *
     * @param file          File to ingest
     * @param ingestContent if true, index the file and the content, otherwise
     *                      indesx metadata only
     *
     * @throws IngesterException if there was an error processing a specific
     *                           file, but the Solr server is probably fine.
     */
    void ingest(AbstractFile file, boolean ingestContent) throws IngesterException {
        if (ingestContent == false || file.isDir()) {
            ingest(new NullContentStream(file), getContentFields(file), 0);
        } else {
            ingest(new FscContentStream(file), getContentFields(file), file.getSize());
        }
    }

    /**
     * Creates a field map from FsContent, that is later sent to Solr
     *
     * @param fsc FsContent to get fields from
     *
     * @return the map
     */
    private Map<String, String> getContentFields(AbstractContent fsc) {
        return fsc.accept(getContentFieldsV);
    }

    /**
     * Visitor used to create param list to send to SOLR index.
     */
    private class GetContentFieldsV extends ContentVisitor.Default<Map<String, String>> {

        @Override
        protected Map<String, String> defaultVisit(Content cntnt) {
            return new HashMap<>();
        }

        @Override
        public Map<String, String> visit(File f) {
            Map<String, String> params = getCommonFields(f);
            getCommonFileContentFields(params, f);
            return params;
        }

        @Override
        public Map<String, String> visit(DerivedFile df) {
            Map<String, String> params = getCommonFields(df);
            getCommonFileContentFields(params, df);
            return params;
        }

        @Override
        public Map<String, String> visit(Directory d) {
            Map<String, String> params = getCommonFields(d);
            getCommonFileContentFields(params, d);
            return params;
        }

        @Override
        public Map<String, String> visit(LayoutFile lf) {
            // layout files do not have times
            return getCommonFields(lf);
        }

        @Override
        public Map<String, String> visit(LocalFile lf) {
            Map<String, String> params = getCommonFields(lf);
            getCommonFileContentFields(params, lf);
            return params;
        }

        @Override
        public Map<String, String> visit(SlackFile f) {
            Map<String, String> params = getCommonFields(f);
            getCommonFileContentFields(params, f);
            return params;
        }

        private Map<String, String> getCommonFileContentFields(Map<String, String> params, AbstractFile file) {
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
    void ingest(ContentStream cs, Map<String, String> fields, final long size) throws IngesterException {
        if (fields.get(Server.Schema.IMAGE_ID.toString()) == null) {
            //skip the file, image id unknown
            String msg = NbBundle.getMessage(this.getClass(),
                    "Ingester.ingest.exception.unknownImgId.msg", cs.getName());
            logger.log(Level.SEVERE, msg);
            throw new IngesterException(msg);
        }

        final byte[] docChunkContentBuf = new byte[MAX_DOC_CHUNK_SIZE];
        SolrInputDocument updateDoc = new SolrInputDocument();

        for (String key : fields.keySet()) {
            updateDoc.addField(key, fields.get(key));
        }

        //using size here, but we are no longer ingesting entire files
        //size is normally a chunk size, up to 1MB
        if (size > 0) {
            // TODO (RC): Use try with resources, adjust exception messages
            InputStream is = null;
            int read = 0;
            try {
                is = cs.getStream();
                read = is.read(docChunkContentBuf);
            } catch (IOException ex) {
                throw new IngesterException(
                        NbBundle.getMessage(this.getClass(), "Ingester.ingest.exception.cantReadStream.msg",
                                cs.getName()));
            } finally {
                if (null != is) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not close input stream after reading content, " + cs.getName(), ex); //NON-NLS
                    }
                }
            }

            if (read != 0) {
                String s = "";
                try {
                    s = new String(docChunkContentBuf, 0, read, ENCODING);
                    // Sanitize by replacing non-UTF-8 characters with caret '^' before adding to index
                    char[] chars = null;
                    for (int i = 0; i < s.length(); i++) {
                        if (!TextUtil.isValidSolrUTF8(s.charAt(i))) {
                            // only convert string to char[] if there is a non-UTF8 character
                            if (chars == null) {
                                chars = s.toCharArray();
                            }
                            chars[i] = '^';
                        }
                    }
                    // check if the string was modified (i.e. there was a non-UTF8 character found)
                    if (chars != null) {
                        s = new String(chars);
                    }
                } catch (UnsupportedEncodingException ex) {
                    logger.log(Level.SEVERE, "Unsupported encoding", ex); //NON-NLS
                }
                updateDoc.addField(Server.Schema.CONTENT.toString(), s);
            } else {
                updateDoc.addField(Server.Schema.CONTENT.toString(), "");
            }
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
                    NbBundle.getMessage(this.getClass(), "Ingester.ingest.exception.err.msg", cs.getName()), ex);
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
     * ContentStream to read() the data from a FsContent object
     */
    private static class FscContentStream implements ContentStream {

        private AbstractFile f;

        FscContentStream(AbstractFile f) {
            this.f = f;
        }

        @Override
        public String getName() {
            return f.getName();
        }

        @Override
        public String getSourceInfo() {
            return NbBundle.getMessage(this.getClass(), "Ingester.FscContentStream.getSrcInfo", f.getId());
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public Long getSize() {
            return f.getSize();
        }

        @Override
        public InputStream getStream() throws IOException {
            return new ReadContentInputStream(f);
        }

        @Override
        public Reader getReader() throws IOException {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "Ingester.FscContentStream.getReader"));
        }
    }

    /**
     * ContentStream associated with FsContent, but forced with no content
     */
    private static class NullContentStream implements ContentStream {

        AbstractContent aContent;

        NullContentStream(AbstractContent aContent) {
            this.aContent = aContent;
        }

        @Override
        public String getName() {
            return aContent.getName();
        }

        @Override
        public String getSourceInfo() {
            return NbBundle.getMessage(this.getClass(), "Ingester.NullContentStream.getSrcInfo.text", aContent.getId());
        }

        @Override
        public String getContentType() {
            return null;
        }

        @Override
        public Long getSize() {
            return 0L;
        }

        @Override
        public InputStream getStream() throws IOException {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Reader getReader() throws IOException {
            throw new UnsupportedOperationException(
                    NbBundle.getMessage(this.getClass(), "Ingester.NullContentStream.getReader"));
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
