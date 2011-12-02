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
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.datamodel.FsContent;

/**
 * Handles ingesting files to a Solr server, given the url string for it
 */
class Ingester {

    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    private SolrServer solrCore;
    private boolean uncommitedIngests = false;

    Ingester(SolrServer solrCore) {
        this.solrCore = solrCore;
    }

    @Override
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
     * @param f File to ingest
     * @throws org.sleuthkit.autopsy.keywordsearch.Ingester.IngesterException if
     * there was an error processing the given file, but the Solr server is
     * probably fine.
     */
    void ingest(FsContent f) throws IngesterException {
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("id", Long.toString(f.getId()));
        fields.put("file_name", f.getName());
        fields.put("ctime", f.getCtimeAsDate());
        fields.put("atime", f.getAtimeAsDate());
        fields.put("mtime", f.getMtimeAsDate());
        fields.put("crtime", f.getMtimeAsDate());

        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addContentStream(new FscContentStream(f));
        setFields(up, fields);
        up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);

        up.setParam("commit", "false");

        try {
            solrCore.request(up);
            // should't get any checked exceptions, 
        } catch (IOException ex) {
            // It's possible that we will have IO errors 
            throw new IngesterException("Problem reading file.", ex);
        } catch (SolrServerException ex) {
            // If there's a problem talking to Solr, something is fundamentally
            // wrong with ingest
            throw new RuntimeException(ex);
        } catch (SolrException ex) {
            // Tika problems result in an unchecked SolrException
            ErrorCode ec = ErrorCode.getErrorCode(ex.code());

            // When Tika has problems with a document, it throws a server error
            // but it's okay to continue with other documents
            if (ec.equals(ErrorCode.SERVER_ERROR)) {
                throw new IngesterException("Problem posting file contents to Solr. SolrException error code: " + ec, ex);
            } else {
                // shouldn't get any other error codes
                throw ex;
            }
        }
        
        uncommitedIngests = true;
    }

    void commit() {
        uncommitedIngests = false;
        try {
            solrCore.commit();
            // if commit doesn't work, something's broken
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void setFields(ContentStreamUpdateRequest up, Map<String, String> fields) {
        for (Entry<String, String> field : fields.entrySet()) {
            up.setParam("literal." + field.getKey(), field.getValue());
        }
    }

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

    static class IngesterException extends Exception {

        IngesterException(String message, Throwable ex) {
            super(message, ex);
        }
    }
}
