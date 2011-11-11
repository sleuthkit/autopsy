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
import org.apache.solr.common.util.ContentStream;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.TskException;

/**
 * Handles ingesting files to a Solr server, given the url string for it
 */
class Ingester {
    

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
    
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (uncommitedIngests) {
            Logger.getLogger(Ingester.class.getName()).warning("Ingester was used to add files that it never committed!");
        }
    }

    void ingest(File f) throws IngesterException {
        Map<String, String> fields = new HashMap<String, String>();
        fields.put("id", Long.toString(f.getId()));
        fields.put("file_name", f.getName());
        fields.put("ctime", f.getCtimeAsDate());
        fields.put("atime", f.getAtimeAsDate());
        fields.put("mtime", f.getMtimeAsDate());
        fields.put("crtime", f.getMtimeAsDate());

        ContentStreamUpdateRequest up = new ContentStreamUpdateRequest("/update/extract");
        up.addContentStream(new FileContentStream(f));
        setFields(up, fields);
        up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);
        
        try {
            solr.request(up);
        } catch (IOException ex) {
            throw new IngesterException("Problem posting file contents to Solr", ex);
        } catch (SolrServerException ex) {
            throw new IngesterException("Problem posting file contents to Solr", ex);
        }
        uncommitedIngests = true;
    }
    
    void commit() throws IngesterException {
        uncommitedIngests = false;
        try {
            solr.commit();
        } catch (IOException ex) {
            throw new IngesterException("Problem making Solr commit", ex);
        } catch (SolrServerException ex) {
            throw new IngesterException("Problem making Solr commit", ex);
        }
    }

    private static void setFields(ContentStreamUpdateRequest up, Map<String, String> fields) {
        for (Entry<String, String> field : fields.entrySet()) {
            up.setParam("literal." + field.getKey(), field.getValue());
        }
    }

    private static class FileContentStream implements ContentStream {

        File f;

        FileContentStream(File f) {
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
                return new ByteArrayInputStream(f.read(0, f.getSize()));
            } catch (TskException ex) {
                throw new IOException(ex);
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
