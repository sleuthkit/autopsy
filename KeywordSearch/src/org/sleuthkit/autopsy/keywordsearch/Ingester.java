package org.sleuthkit.autopsy.keywordsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.TskException;

/**
 * Handles ingesting files to a Solr server, given the url string for it
 */
class Ingester {
    private static final Logger logger = Logger.getLogger(Ingester.class.getName());
    

    private SolrServer solr;
    private boolean uncommitedIngests = false;
    
    /**
     * New Ingester connected to the server at given url
     * @param url Should be something like "http://localhost:8983/solr"
     */
    Ingester(String url) {
        try {
            this.solr = new CommonsHttpSolrServer(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    Ingester(SolrServer solr) {
        this.solr = solr;
    }
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        
        // Warn if files might have been left uncommited.
        if (uncommitedIngests) {
            logger.warning("Ingester was used to add files that it never committed!");
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
            solr.request(up);
            // should't get any checked exceptions, but Tika problems result in
            // an unchecked SolrException
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        } catch (SolrException ex) {
            ErrorCode ec = ErrorCode.getErrorCode(ex.code());
            
            // When Tika has problems with a document, it throws a server error
            // but it's okay to continue with other documents
            if (ec.equals(ErrorCode.SERVER_ERROR)) { 
                throw new IngesterException("Problem posting file contents to Solr. SolrException error code: " + ec , ex);
            } else {
                throw ex;
            }
        }
        uncommitedIngests = true;
    }
    
    void commit() {
        uncommitedIngests = false;
        try {
            solr.commit();
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
            try {
                long size = f.getSize();
                if (size > 0) {
                    return new ByteArrayInputStream(f.read(0, f.getSize()));
                } else {
                    // can't read files with size 0
                    return new ByteArrayInputStream(new byte[0]);
                }
            } catch (TskException ex) {
                throw new IOException("Error reading file '" + f.getName() + "' (id: " + f.getId() + ")", ex);
            }
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
