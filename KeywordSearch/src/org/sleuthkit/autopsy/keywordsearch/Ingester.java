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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.SolrInputDocument;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.keywordsearch.Server.SolrServerNoPortException;
import org.sleuthkit.datamodel.AbstractContent;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentVisitor;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Handles indexing files on a Solr core.
 */
public class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private boolean uncommitedIngests = false;
    private final ExecutorService upRequestExecutor = Executors.newSingleThreadExecutor();
    private final Server solrServer = KeywordSearch.getServer();
    private final GetContentFieldsV getContentFieldsV = new GetContentFieldsV();
    private static Ingester instance;
   
    //for ingesting chunk as SolrInputDocument (non-content-streaming, by-pass tika)
    //TODO use a streaming way to add content to /update handler
    private final static int MAX_DOC_CHUNK_SIZE = 1024*1024;
    private final byte[] docChunkContentBuf = new byte[MAX_DOC_CHUNK_SIZE];
    private static final String docContentEncoding = "UTF-8";


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
            logger.warning("Ingester was used to add files that it never committed.");
        }
    }

    /**
     * Sends a stream to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files.
     *
     * @param afscs File AbstractFileStringContentStream to ingest
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
     */
    void ingest(AbstractFileStringContentStream afscs) throws IngesterException {
        Map<String, String> params = getContentFields(afscs.getSourceContent());
        ingest(afscs, params, afscs.getSourceContent().getSize());
    }

    /**
     * Sends a AbstractFileExtract to Solr to have its content extracted and
     * added to the index. commit() should be called once you're done ingesting
     * files. FileExtract represents a parent of extracted file with actual
     * content. The parent itself has no content, only meta data and is used to
     * associate the extracted AbstractFileChunk
     *
     * @param fe AbstractFileExtract to ingest
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
     */
    void ingest(AbstractFileExtract fe) throws IngesterException {
        Map<String, String> params = getContentFields(fe.getSourceFile());

        params.put(Server.Schema.NUM_CHUNKS.toString(), Integer.toString(fe.getNumChunks()));

        ingest(new NullContentStream(fe.getSourceFile()), params, 0);
    }

    /**
     * Sends a AbstractFileChunk to Solr and its extracted content stream to be
     * added to the index. commit() should be called once you're done ingesting
     * files. AbstractFileChunk represents a file chunk and its chunk content.
     *
     * @param fec AbstractFileChunk to ingest
     * @param size approx. size of the stream in bytes, used for timeout
     * estimation
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
     */
    void ingest(AbstractFileChunk fec, ByteContentStream bcs, int size) throws IngesterException {
        AbstractContent sourceContent = bcs.getSourceContent();
        Map<String, String> params = getContentFields(sourceContent);

        //overwrite id with the chunk id
        params.put(Server.Schema.ID.toString(),
                Server.getChunkIdString(sourceContent.getId(), fec.getChunkId()));

        ingest(bcs, params, size);
    }

    /**
     * Sends a file to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files. If the
     * file is a directory or ingestContent is set to false, the file name is
     * indexed only.
     *
     * @param file File to ingest
     * @param ingestContent if true, index the file and the content, otherwise
     * indesx metadata only
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
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
     * @return the map
     */
    private Map<String, String> getContentFields(AbstractContent fsc) {
        return fsc.accept(getContentFieldsV);
    }

    private class GetContentFieldsV extends ContentVisitor.Default<Map<String, String>> {

        @Override
        protected Map<String, String> defaultVisit(Content cntnt) {
            return new HashMap<String, String>();
        }

        @Override
        public Map<String, String> visit(File f) {
            Map<String, String> params = getCommonFields(f);
            getCommonFsContentFields(params, f);
            return params;
        }
        
        @Override
        public Map<String, String> visit(DerivedFile df) {
            Map<String, String> params = getCommonFields(df);
            //TODO mactimes after data model refactor
            return params;
        }

        @Override
        public Map<String, String> visit(Directory d) {
            Map<String, String> params = getCommonFields(d);
            getCommonFsContentFields(params, d);
            return params;
        }

        @Override
        public Map<String, String> visit(LayoutFile lf) {
            return getCommonFields(lf);
        }

        private Map<String, String> getCommonFsContentFields(Map<String, String> params, FsContent fsContent) {
            //aaa
            params.put(Server.Schema.CTIME.toString(), ContentUtils.getStringTimeISO8601(fsContent.getCtime(), fsContent));
            params.put(Server.Schema.ATIME.toString(), ContentUtils.getStringTimeISO8601(fsContent.getAtime(), fsContent));
            params.put(Server.Schema.MTIME.toString(), ContentUtils.getStringTimeISO8601(fsContent.getMtime(), fsContent));
            params.put(Server.Schema.CRTIME.toString(), ContentUtils.getStringTimeISO8601(fsContent.getCrtime(), fsContent));
            return params;
        }

        private Map<String, String> getCommonFields(AbstractFile af) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(Server.Schema.ID.toString(), Long.toString(af.getId()));
            try {
                params.put(Server.Schema.IMAGE_ID.toString(), Long.toString(af.getImage().getId()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not get image id to properly index the file " + af.getId());
            }

            params.put(Server.Schema.FILE_NAME.toString(), af.getName());
            return params;
        }
    }

    
    /**
     * Indexing method that bypasses Tika, assumes pure text
     * It reads and converts the entire content stream to string, assuming UTF8
     * since we can't use streaming approach for Solr /update handler.
     * This should be safe, since all content is now in max 1MB chunks.
     * 
     * TODO see if can use a byte or string streaming way to add content to /update handler
     * e.g. with XMLUpdateRequestHandler (deprecated in SOlr 4.0.0), see if possible 
     * to stream with UpdateRequestHandler
     * 
     * @param cs
     * @param fields
     * @param size
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException 
     */
    private void ingest(ContentStream cs, Map<String, String> fields, final long size) throws IngesterException {
        
        if (fields.get(Server.Schema.IMAGE_ID.toString()) == null) {
            //skip the file, image id unknown
            String msg = "Skipping indexing the file, unknown image id, for file: " + cs.getName();
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
 
            InputStream is = null;
            int read = 0;
            try {
                is = cs.getStream();
                read = is.read(docChunkContentBuf);
            } catch (IOException ex) {
                throw new IngesterException("Could not read content stream: " + cs.getName());
            } finally {
                try {
                    is.close();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not close input stream after reading content, " + cs.getName(), ex);
                }
            }

            if (read != 0) {
                String s = "";
                try {
                    s = new String(docChunkContentBuf, 0, read, docContentEncoding);
                } catch (UnsupportedEncodingException ex) {
                    Exceptions.printStackTrace(ex);
                }
                updateDoc.addField(Server.Schema.CONTENT.toString(), s);
            } else {
                updateDoc.addField(Server.Schema.CONTENT.toString(), "");
            }
        }
        else {
            //no content, such as case when 0th chunk indexed
            updateDoc.addField(Server.Schema.CONTENT.toString(), "");
        }
        

        try {
            //TODO consider timeout thread, or vary socket timeout based on size of indexed content
            solrServer.addDocument(updateDoc);
            uncommitedIngests = true;
        } catch (KeywordSearchModuleException ex) {
            throw new IngesterException("Error ingestint document: " + cs.getName(), ex);
        }


    }

    /**
     * Delegate method actually performing the indexing work for objects
     * implementing ContentStream
     *
     * @param cs ContentStream to ingest
     * @param fields content specific fields
     * @param size size of the content - used to determine the Solr timeout, not
     * used to populate meta-data
     *
     * @throws IngesterException if there was an error processing a specific
     * content, but the Solr server is probably fine.
     */
    private void ingestExtract(ContentStream cs, Map<String, String> fields, final long size) throws IngesterException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addContentStream(cs);
        setFields(up, fields);
        up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);

        final String contentType = cs.getContentType();
        if (contentType != null && !contentType.trim().equals("")) {
            up.setParam("stream.contentType", contentType);
        }

        //logger.log(Level.INFO, "Ingesting " + fields.get("file_name"));
        up.setParam("commit", "false");

        final Future<?> f = upRequestExecutor.submit(new UpRequestTask(up));

        try {
            f.get(getTimeout(size), TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            logger.log(Level.WARNING, "Solr timeout encountered, trying to restart Solr");
            //restart may be needed to recover from some error conditions
            hardSolrRestart();
            throw new IngesterException("Solr index request time out for id: " + fields.get("id") + ", name: " + fields.get("file_name"));
        } catch (Exception e) {
            throw new IngesterException("Problem posting content to Solr, id: " + fields.get("id") + ", name: " + fields.get("file_name"), e);
        }
        uncommitedIngests = true;
    }

    /**
     * attempt to restart Solr and recover from its internal error
     */
    private void hardSolrRestart() {
        try {
            solrServer.closeCore();
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Cannot close core while restating", ex);
        }
        try {
            solrServer.stop();
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Cannot stop while restating", ex);
        }
        try {
            solrServer.start();
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Cannot start while restating", ex);
        } catch (SolrServerNoPortException ex) {
            logger.log(Level.WARNING, "Cannot start server with this port", ex);
        }

        try {
            solrServer.openCore();
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.WARNING, "Cannot open core while restating", ex);
        }
    }

    /**
     * return timeout that should be used to index the content
     *
     * @param size size of the content
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

    private class UpRequestTask implements Runnable {

        ContentStreamUpdateRequest up;

        UpRequestTask(ContentStreamUpdateRequest up) {
            this.up = up;
        }

        @Override
        public void run() {
            try {
                up.setMethod(METHOD.POST);
                solrServer.request(up);
            } catch (NoOpenCoreException ex) {
                throw new RuntimeException("No Solr core available, cannot index the content", ex);
            } catch (IllegalStateException ex) {
                // problems with content
                throw new RuntimeException("Problem reading file.", ex);
            } catch (SolrServerException ex) {
                // If there's a problem talking to Solr, something is fundamentally
                // wrong with ingest
                throw new RuntimeException("Problem with Solr", ex);
            } catch (SolrException ex) {
                // Tika problems result in an unchecked SolrException
                ErrorCode ec = ErrorCode.getErrorCode(ex.code());

                // When Tika has problems with a document, it throws a server error
                // but it's okay to continue with other documents
                if (ec.equals(ErrorCode.SERVER_ERROR)) {
                    throw new RuntimeException("Problem posting file contents to Solr. SolrException error code: " + ec, ex);
                } else {
                    // shouldn't get any other error codes
                    throw ex;
                }
            }

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
        } catch (NoOpenCoreException ex) {
            logger.log(Level.WARNING, "Error commiting index", ex);
        } catch (SolrServerException ex) {
            logger.log(Level.WARNING, "Error commiting index", ex);
        }
    }

    /**
     * Helper to set document fields
     *
     * @param up request with document
     * @param fields map of field-names->values
     */
    private static void setFields(ContentStreamUpdateRequest up, Map<String, String> fields) {
        for (Entry<String, String> field : fields.entrySet()) {
            up.setParam("literal." + field.getKey(), field.getValue());
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
            return "File:" + f.getId();
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
            throw new UnsupportedOperationException("Not supported yet.");
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
            return "File:" + aContent.getId();
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
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    /**
     * Indicates that there was an error with the specific ingest operation, but
     * it's still okay to continue ingesting files.
     */
    static class IngesterException extends Exception {

        IngesterException(String message, Throwable ex) {
            super(message, ex);
        }

        IngesterException(String message) {
            super(message);
        }
    }
}
