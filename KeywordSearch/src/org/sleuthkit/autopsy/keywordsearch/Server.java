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

import java.awt.event.ActionEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Handles for keeping track of a Solr server and its cores
 */
class Server {
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    
    private static final String DEFAULT_CORE_NAME = "coreCase";
    // TODO: DEFAULT_CORE_NAME needs to be replaced with unique names to support multiple open cases
    
    public static final String CORE_EVT = "CORE_EVT"; 
    public enum CORE_EVT_STATES { STOPPED, STARTED };

    private CommonsHttpSolrServer solrServer;
    private String instanceDir;
    private File solrFolder;
    private ServerAction serverAction;

    /**
     * New instance for the server at the given URL
     * @param url should be something like "http://localhost:8983/solr/"
     */
    Server(String url) {
        try {
            this.solrServer = new CommonsHttpSolrServer(url);
        } catch (MalformedURLException ex) {
            throw new RuntimeException(ex);
        }
        
        serverAction = new ServerAction();
        solrFolder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false);
        instanceDir = solrFolder.getAbsolutePath() + File.separator + "solr";
    }
    
    @Override
    public void finalize() throws java.lang.Throwable {
        stop();
        super.finalize();
    }
 
    public void addServerActionListener(PropertyChangeListener l) {
        serverAction.addPropertyChangeListener(l);
    }
    
    /**
     * Helper threads to handle stderr/stdout from Solr process
     */
    private static class InputStreamPrinterThread extends Thread {

        InputStream stream;

        InputStreamPrinterThread(InputStream stream) {
            this.stream = stream;
        }

        @Override
        public void run() {
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            String line = null;
            try {
                while ((line = br.readLine()) != null) {
                    logger.log(Level.INFO, "SOLR OUTPUT: " + line.trim());
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }
    
    
    /**
     * Tries to start a Solr instance in a separate process. Returns immediately
     * (probably before the server is ready) and doesn't check whether it was
     * successful.
     */
    void start() {
        logger.log(Level.INFO, "Starting Solr server from: " + solrFolder.getAbsolutePath());
        try {
            Process start = Runtime.getRuntime().exec("java -DSTOP.PORT=8079 -DSTOP.KEY=mysecret -jar start.jar", null, solrFolder);
            
            // Handle output to prevent process from blocking
            (new InputStreamPrinterThread(start.getInputStream())).start();
            (new InputStreamPrinterThread(start.getErrorStream())).start();
            
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Tries to stop a Solr instance.
     * 
     * Waits for the stop command to finish
     * before returning.
     * @return  true if the stop command finished successfully, else false
     */
    boolean stop() {
        try {
            logger.log(Level.INFO, "Stopping Solr server from: " + solrFolder.getAbsolutePath());
            Process stop = Runtime.getRuntime().exec("java -DSTOP.PORT=8079 -DSTOP.KEY=mysecret -jar start.jar --stop", null, solrFolder);
            return stop.waitFor() == 0;
            
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
  
    
    /**
     * Tests if there's a Solr server running by sending it a core-status request.
     * @return false if the request failed with a connection error, otherwise true
     */
    boolean isRunning() {

        try {
            // making a status request here instead of just doing solrServer.ping(), because
            // that doesn't work when there are no cores

            CoreAdminRequest.getStatus(null, solrServer);
        } catch (SolrServerException ex) {
            
            Throwable cause = ex.getRootCause();
            
            // TODO: check if SocketExceptions should actually happen (is
            // probably caused by starting a connection as the server finishes
            // shutting down)
            if (cause instanceof ConnectException || cause instanceof SocketException || cause instanceof NoHttpResponseException) {
                return false;
            } else {
                throw new RuntimeException("Error checking if server is running", ex);
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error checking if server is running", ex);
        }

        return true;
    }

    /**** Convenience methods for use while we only open one case at a time ****/
    
    private Core currentCore = null;
    
    void openCore() {
        if (currentCore != null) {
            throw new RuntimeException("Already an open Core!");
        }
        currentCore = openCore(Case.getCurrentCase());
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STARTED);
    }
    
    void closeCore() {
        if (currentCore == null) {
            throw new RuntimeException("No currently open Core!");
        }
        currentCore.close();
        currentCore = null;
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STOPPED);
    }
    
    Core getCore() {
        if (currentCore == null) {
            throw new RuntimeException("No currently open Core!");
        }
        return currentCore;
    }
        
    
    /**** end single-case specific methods ****/ 

    
    /**
     * Open a core for the given case
     * @param c
     * @return 
     */
    Core openCore(Case c) {
        String sep = File.separator;
        String dataDir = c.getCaseDirectory() + sep + "keywordsearch" + sep + "data";
        return this.openCore(DEFAULT_CORE_NAME, new File(dataDir));
    }

    /**
     * Open a new core
     * @param coreName name to refer to the core by in Solr
     * @param dataDir directory to load/store the core data from/to
     * @return new core
     */
    Core openCore(String coreName, File dataDir) {
        try {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            CoreAdminRequest.Create createCore = new CoreAdminRequest.Create();
            createCore.setDataDir(dataDir.getAbsolutePath());
            createCore.setInstanceDir(instanceDir);
            createCore.setCoreName(coreName);

            this.solrServer.request(createCore);

            return new Core(coreName);

        } catch (SolrServerException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    class Core {

        // handle to the core in Solr
        private String name;
        
        // the server to access a core needs to be built from a URL with the
        // core in it, and is only good for core-specific operations
        private SolrServer solrCore;

        private Core(String name) {
            this.name = name;
            try {
                this.solrCore = new CommonsHttpSolrServer(solrServer.getBaseURL() + "/" + name);
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
        
        TermsResponse queryTerms(SolrQuery sq) throws SolrServerException {
            QueryResponse qres = solrCore.query(sq);
            return qres.getTermsResponse();
        }
        
        void close() {
            try {
                CoreAdminRequest.unloadCore(this.name, solrServer);
            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        
        /**
         * Execute query that gets only number of all Solr documents indexed
         * without actually returning the documents
         * @return int representing number of indexed files
         * @throws SolrServerException 
         */
        public int queryNumIndexedFiles() throws SolrServerException {
             SolrQuery q = new SolrQuery("*:*");
             q.setRows(0); 
             return (int)query(q).getResults().getNumFound();
    }
}
    
    class ServerAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            logger.log(Level.INFO, e.paramString().trim());
        }   
    }
}
