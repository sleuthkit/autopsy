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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.ReadContentInputStream;

/**
 * Handles indexing files on a Solr core.
 */
class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private boolean uncommitedIngests = false;
    private final ExecutorService upRequestExecutor = Executors.newSingleThreadExecutor();
    static final int UP_REQUEST_TIMEOUT_SECS = 30 * 60; //30 min TODO use variable time depending on file size
    private final Server solrServer = KeywordSearch.getServer();

    Ingester() {
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
     * Sends a file to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files.
     * 
     * @param fcs File FsContentStringStream to ingest
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
     */
    public void ingest(FsContentStringContentStream fcs) throws IngesterException {
        ingest(fcs, getFsContentFields(fcs.getFsContent()));
    }

    /**
     * Sends a file to Solr to have its content extracted and added to the
     * index. commit() should be called once you're done ingesting files.
     * 
     * @param f File to ingest
     * @throws IngesterException if there was an error processing a specific
     * file, but the Solr server is probably fine.
     */
    public void ingest(FsContent f) throws IngesterException {
        ingest(new FscContentStream(f), getFsContentFields(f));
    }

    /**
     * Creates a field map from FsContent, that is later sent to Solr
     * @param fsc FsContent to get fields from
     * @return the map
     */
    private Map<String, String> getFsContentFields(FsContent fsc) {
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("id", Long.toString(fsc.getId()));
        fields.put("file_name", fsc.getName());
        fields.put("ctime", fsc.getCtimeAsDate());
        fields.put("atime", fsc.getAtimeAsDate());
        fields.put("mtime", fsc.getMtimeAsDate());
        fields.put("crtime", fsc.getMtimeAsDate());
        return fields;
    }

    /**
     * Common delegate method actually doing the work for objects implementing ContentStream
     * 
     * @param ContentStream to ingest
     * @param fields content specific fields
     * @throws IngesterException if there was an error processing a specific
     * content, but the Solr server is probably fine.
     */
    private void ingest(ContentStream cs, Map<String, String> fields) throws IngesterException {
        final ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addContentStream(cs);
        setFields(up, fields);
        up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);

        //logger.log(Level.INFO, "Ingesting " + fields.get("file_name"));

        up.setParam("commit", "false");


        final Future f = upRequestExecutor.submit(new UpRequestTask(up));
        try {
            //TODO use timeout proportional to content size
            f.get(UP_REQUEST_TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            logger.log(Level.WARNING, "Solr timeout encountered, trying to restart Solr");
            //TODO restart solr might be needed to recover from some error conditions
            hardSolrRestart();
            throw new IngesterException("Solr index request time out for id: " + fields.get("id") + ", name: " + fields.get("file_name"));
        } catch (Exception e) {
            throw new IngesterException("Problem posting content to Solr, id: " + fields.get("id") + ", name: " + fields.get("file_name"), e);
        }
        uncommitedIngests = true;
    }
    
    
    //attempt to restart Solr and recover from its internal error
    private void hardSolrRestart() {
        solrServer.closeCore();
        solrServer.stop();

        solrServer.start();
        solrServer.openCore();


    }

    private class UpRequestTask implements Runnable {

        ContentStreamUpdateRequest up;

        UpRequestTask(ContentStreamUpdateRequest up) {
            this.up = up;
        }

        @Override
        public void run() {
            try {
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

        FsContent f;

        FscContentStream(FsContent f) {
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
     * Indicates that there was an error with the specific ingest operation,
     * but it's still okay to continue ingesting files.
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
