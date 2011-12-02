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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.sleuthkit.autopsy.casemodule.Case;

class Server {

    private static final String DEFAULT_CORE_NAME = "coreCase";
    // TODO: DEFAULT_CORE_NAME needs to be replaced with unique names to support multiple open cases
    

    private CommonsHttpSolrServer solr;
    private String instanceDir = "C:/Users/pmartel/solr-test/maytag/solr";

    Server(String url) {
        try {
            this.solr = new CommonsHttpSolrServer(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
    }

    void start() {
    }

    void stop() {
    }
    
    
    
    /**** Convenience methods for use while we only open one case at a time ****/
    
    private Core currentCore = null;
    
    void openCore() {
        if (currentCore != null) {
            throw new RuntimeException("Already an open Core!");
        }
        currentCore = openCore(Case.getCurrentCase());
    }
    
    void closeCore() {
        if (currentCore == null) {
            throw new RuntimeException("No currently open Core!");
        }
        currentCore.close();
        currentCore = null;
    }
    
    Core getCore() {
        if (currentCore == null) {
            throw new RuntimeException("No currently open Core!");
        }
        return currentCore;
    }
        
    
    /**** end single-case specific methods ****/ 

    
    
    Core openCore(Case c) {
        String sep = File.separator;
        String dataDir = c.getCaseDirectory() + sep + "keywordsearch" + sep + "data";
        return this.openCore(DEFAULT_CORE_NAME, new File(dataDir));
    }

    Core openCore(String coreName, File dataDir) {
        try {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            CoreAdminRequest.Create createCore = new CoreAdminRequest.Create();
            createCore.setDataDir(dataDir.getAbsolutePath());
            createCore.setInstanceDir(instanceDir);
            createCore.setCoreName(coreName);

            this.solr.request(createCore);

            return new Core(coreName);

        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    class Core {

        private String name;
        // server to access a core needs to be built from a URL with the
        // core in it, and is only good for core-specific operations
        private SolrServer solrCore;

        private Core(String name) {
            this.name = name;
            try {
                this.solrCore = new CommonsHttpSolrServer(solr.getBaseURL() + "/" + name);
            } catch (MalformedURLException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        Ingester getIngester() {
            return new Ingester(this.solrCore);
        }
        
        QueryResponse query(SolrQuery sq) throws SolrServerException {
            return solrCore.query(sq);
        }
        
        void close () {
            try {
                CoreAdminRequest.unloadCore(this.name, solr);
            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
