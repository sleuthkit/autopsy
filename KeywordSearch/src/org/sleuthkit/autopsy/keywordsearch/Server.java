/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.net.MalformedURLException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;

/**
 *
 * @author pmartel
 */
public class Server {
    private static final String url = "http://localhost:8983/solr";
    private static final Server S = new Server(url);
            
    static Server getServer() {
        return S;
    }
    
    private SolrServer solr;
    
    Server(String url) {
        try {
            this.solr = new CommonsHttpSolrServer(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    Ingester getIngester() {
        return new Ingester(this.solr);
    }
    
    SolrServer getSolr() {
        return this.solr;
    } 
    
}
