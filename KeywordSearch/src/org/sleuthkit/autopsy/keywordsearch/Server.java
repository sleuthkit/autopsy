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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.common.util.NamedList;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.datamodel.Content;

/**
 * Handles for keeping track of a Solr server and its cores
 */
class Server {

    public static enum Schema {

        ID {
            @Override
            public String toString() {
                return "id";
            }
        },
        CONTENT {
            @Override
            public String toString() {
                return "content";
            }
        },
        CONTENT_WS {
            @Override
            public String toString() {
                return "content_ws";
            }
        },
        FILE_NAME {
            @Override
            public String toString() {
                return "file_name";
            }
        },
        CTIME {
            @Override
            public String toString() {
                return "ctime";
            }
        },
        ATIME {
            @Override
            public String toString() {
                return "atime";
            }
        },
        MTIME {
            @Override
            public String toString() {
                return "mtime";
            }
        },
        CRTIME {
            @Override
            public String toString() {
                return "crtime";
            }
        },
        NUM_CHUNKS {
            @Override
            public String toString() {
                return "num_chunks";
            }
        },};
    public static final String HL_ANALYZE_CHARS_UNLIMITED = "-1";
    //max content size we can send to Solr
    public static final long MAX_CONTENT_SIZE = 1L * 1024 * 1024 * 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final String DEFAULT_CORE_NAME = "coreCase";
    // TODO: DEFAULT_CORE_NAME needs to be replaced with unique names to support multiple open cases
    public static final String CORE_EVT = "CORE_EVT";
    public static final char ID_CHUNK_SEP = '_';
    private String javaPath = "java";
    public static final Charset DEFAULT_INDEXED_TEXT_CHARSET = Charset.forName("UTF-8"); ///< default Charset to index text as
    private static final int MAX_SOLR_MEM_MB = 512; //TODO set dynamically based on avail. system resources
    private Process curSolrProcess = null;
    private static Ingester ingester = null;

    public enum CORE_EVT_STATES {

        STOPPED, STARTED
    };
    private CommonsHttpSolrServer solrServer;
    private String instanceDir;
    private File solrFolder;
    private ServerAction serverAction;
    private InputStreamPrinterThread inputRedirectThread;
    private InputStreamPrinterThread errorRedirectThread;

    /**
     * New instance for the server at the given URL
     *
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

        javaPath = PlatformUtil.getJavaPath();
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
        OutputStream out;
        boolean doRun = true;

        InputStreamPrinterThread(InputStream stream, String type) {
            this.stream = stream;
            try {
                final String log = Places.getUserDirectory().getAbsolutePath() + "/var/log/solr.log." + type;
                File outputFile = new File(log.concat(".0"));
                File first = new File(log.concat(".1"));
                File second = new File(log.concat(".2"));
                if (second.exists()) {
                    second.delete();
                }
                if (first.exists()) {
                    first.renameTo(second);
                }
                if (outputFile.exists()) {
                    outputFile.renameTo(first);
                } else {
                    outputFile.createNewFile();
                }
                out = new FileOutputStream(outputFile);

            } catch (Exception ex) {
                logger.log(Level.WARNING, "Failed to create solr log file", ex);
            }
        }

        void stopRun() {
            doRun = false;
        }

        @Override
        public void run() {
            InputStreamReader isr = new InputStreamReader(stream);
            BufferedReader br = new BufferedReader(isr);
            final Version.Type builtType = Version.getBuildType();
            try {
                OutputStreamWriter osw = new OutputStreamWriter(out, PlatformUtil.getDefaultPlatformCharset());
                BufferedWriter bw = new BufferedWriter(osw);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    if (builtType == Version.Type.DEVELOPMENT) {
                        bw.write(line);
                        bw.newLine();
                    //if (builtType == Version.Type.DEVELOPMENT) {
                        //flush buffers if dev version for debugging
                        bw.flush();
                    }
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
            final String MAX_SOLR_MEM_MB_PAR = " -Xmx" + Integer.toString(MAX_SOLR_MEM_MB) + "m";
            final String SOLR_START_CMD = javaPath + MAX_SOLR_MEM_MB_PAR + " -DSTOP.PORT=8079 -DSTOP.KEY=mysecret -jar start.jar";
            logger.log(Level.INFO, "Starting Solr using: " + SOLR_START_CMD);
            curSolrProcess = Runtime.getRuntime().exec(SOLR_START_CMD, null, solrFolder);
            try {
                //block, give time to fully stary the process
                //so if it's restarted solr operations can be resumed seamlessly
                Thread.sleep(3000);
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            }
            // Handle output to prevent process from blocking
            inputRedirectThread = new InputStreamPrinterThread(curSolrProcess.getInputStream(), "input");
            inputRedirectThread.start();

            errorRedirectThread = new InputStreamPrinterThread(curSolrProcess.getErrorStream(), "error");
            errorRedirectThread.start();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Tries to stop a Solr instance.
     *
     * Waits for the stop command to finish before returning.
     */
    synchronized void stop() {
        try {
            logger.log(Level.INFO, "Stopping Solr server from: " + solrFolder.getAbsolutePath());
            //try graceful shutdown
            Process stop = Runtime.getRuntime().exec(javaPath + " -DSTOP.PORT=8079 -DSTOP.KEY=mysecret -jar start.jar --stop", null, solrFolder);
            stop.waitFor();
            //if still running, forcefully stop it
            if (curSolrProcess != null) {
                curSolrProcess.destroy();
                curSolrProcess = null;
            }

        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            //stop Solr stream -> log redirect threads
            if (inputRedirectThread != null) {
                inputRedirectThread.stopRun();
                inputRedirectThread = null;
            }
            if (errorRedirectThread != null) {
                errorRedirectThread.stopRun();
                errorRedirectThread = null;
            }
        }
    }

    /**
     * Tests if there's a Solr server running by sending it a core-status
     * request.
     *
     * @return false if the request failed with a connection error, otherwise
     * true
     */
    synchronized boolean isRunning() {

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
    /**
     * ** Convenience methods for use while we only open one case at a time ***
     */
    private volatile Core currentCore = null;

    synchronized void openCore() {
        if (currentCore != null) {
            throw new RuntimeException("Already an open Core!");
        }
        currentCore = openCore(Case.getCurrentCase());
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STARTED);
    }

    synchronized void closeCore() {
        if (currentCore == null) {
            return;
        }
        currentCore.close();
        currentCore = null;
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STOPPED);
    }

    /**
     * ** end single-case specific methods ***
     */
    /**
     * Open a core for the given case
     *
     * @param c
     * @return
     */
    synchronized Core openCore(Case c) {
        String sep = File.separator;
        String dataDir = c.getCaseDirectory() + sep + "keywordsearch" + sep + "data";
        return this.openCore(DEFAULT_CORE_NAME, new File(dataDir));
    }

    /**
     * commit current core if it exists
     *
     * @throws SolrServerException, NoOpenCoreException
     */
    synchronized void commit() throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        currentCore.commit();
    }

    NamedList<Object> request(SolrRequest request) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.request(request);
    }

    /**
     * Execute query that gets only number of all Solr files indexed without
     * actually returning the files. The result does not include chunks, only
     * number of actual files.
     *
     * @return int representing number of indexed files
     * @throws SolrServerException
     */
    public int queryNumIndexedFiles() throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }

        return currentCore.queryNumIndexedFiles();
    }

    /**
     * Execute query that gets only number of all Solr file chunks (not logical
     * files) indexed without actually returning the content.
     *
     * @return int representing number of indexed chunks
     * @throws SolrServerException
     */
    public int queryNumIndexedChunks() throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }

        return currentCore.queryNumIndexedChunks();
    }

    /**
     * Execute query that gets only number of all Solr documents indexed (files
     * and chunks) without actually returning the documents
     *
     * @return int representing number of indexed files (files and chunks)
     * @throws SolrServerException
     */
    public int queryNumIndexedDocuments() throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }

        return currentCore.queryNumIndexedDocuments();
    }

    /**
     * Return true if the file is indexed (either as a whole as a chunk)
     *
     * @param contentID
     * @return true if it is indexed
     * @throws SolrServerException, NoOpenCoreException
     */
    public boolean queryIsIndexed(long contentID) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }

        return currentCore.queryIsIndexed(contentID);
    }

    /**
     * Execute query that gets number of indexed file chunks for a file
     *
     * @param fileID file id of the original file broken into chunks and indexed
     * @return int representing number of indexed file chunks, 0 if there is no
     * chunks
     * @throws SolrServerException
     */
    public int queryNumFileChunks(long fileID) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }

        return currentCore.queryNumFileChunks(fileID);
    }

    /**
     * Execute solr query
     *
     * @param sq query
     * @return query response
     * @throws SolrServerException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.query(sq);
    }

    /**
     * Execute solr query
     *
     * @param sq the query
     * @param method http method to use
     * @return query response
     * @throws SolrServerException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq, SolrRequest.METHOD method) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.query(sq, method);
    }

    /**
     * Execute Solr terms query
     *
     * @param sq the query
     * @return terms response
     * @throws SolrServerException
     * @throws NoOpenCoreException
     */
    public TermsResponse queryTerms(SolrQuery sq) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.queryTerms(sq);
    }

    /**
     * Execute Solr query to get content text
     *
     * @param content to get the text for
     * @return content text string
     * @throws SolrServerException
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.getSolrContent(content.getId(), 0);
    }

    /**
     * Execute Solr query to get content text from content chunk
     *
     * @param content to get the text for
     * @param chunkID chunk number to query (starting at 1), or 0 if there is no
     * chunks for that content
     * @return content text string
     * @throws SolrServerException
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content, int chunkID) throws SolrServerException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.getSolrContent(content.getId(), chunkID);
    }

    /**
     * Method to return ingester instance
     *
     * @return ingester instance
     */
    public static Ingester getIngester() {
        return Ingester.getDefault();
    }

    /**
     * Given file parent id and child chunk ID, return the ID string of the
     * chunk as stored in Solr, e.g. FILEID_CHUNKID
     *
     * @param parentID the parent file id (id of the source content)
     * @param childID the child chunk id
     * @return formatted string id
     */
    public static String getChunkIdString(long parentID, int childID) {
        return Long.toString(parentID) + Server.ID_CHUNK_SEP + Integer.toString(childID);
    }

    /**
     * Open a new core
     *
     * @param coreName name to refer to the core by in Solr
     * @param dataDir directory to load/store the core data from/to
     * @return new core
     */
    private Core openCore(String coreName, File dataDir) {
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

        private QueryResponse query(SolrQuery sq) throws SolrServerException {
            return solrCore.query(sq);
        }

        private NamedList<Object> request(SolrRequest request) throws SolrServerException {
            try {
                return solrCore.request(request);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not issue Solr request. ", e);
                throw new SolrServerException("Could not issue Solr request", e);
            }

        }

        private QueryResponse query(SolrQuery sq, SolrRequest.METHOD method) throws SolrServerException {
            return solrCore.query(sq, method);
        }

        private TermsResponse queryTerms(SolrQuery sq) throws SolrServerException {
            QueryResponse qres = solrCore.query(sq);
            return qres.getTermsResponse();
        }

        private void commit() throws SolrServerException {
            try {
                //commit and block
                solrCore.commit(true, true);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not commit index. ", e);
                throw new SolrServerException("Could not commit index", e);
            }
        }

        private String getSolrContent(long contentID, int chunkID) {
            final SolrQuery q = new SolrQuery();
            q.setQuery("*:*");
            String filterQuery = Schema.ID.toString() + ":" + contentID;
            if (chunkID != 0) {
                filterQuery = filterQuery + Server.ID_CHUNK_SEP + chunkID;
            }
            q.addFilterQuery(filterQuery);
            q.setFields(Schema.CONTENT.toString());
            try {
                return (String) solrCore.query(q).getResults().get(0).getFieldValue(Schema.CONTENT.toString());
            } catch (SolrServerException ex) {
                logger.log(Level.WARNING, "Error getting content from Solr", ex);
                return null;
            }
        }

        synchronized void close() {
            try {
                CoreAdminRequest.unloadCore(this.name, solrServer);
            } catch (SolrServerException ex) {
                throw new RuntimeException(ex);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        /**
         * Execute query that gets only number of all Solr files (not chunks)
         * indexed without actually returning the files
         *
         * @return int representing number of indexed files (entire files, not
         * chunks)
         * @throws SolrServerException
         */
        private int queryNumIndexedFiles() throws SolrServerException {
            return queryNumIndexedDocuments() - queryNumIndexedChunks();
        }

        /**
         * Execute query that gets only number of all chunks (not logical files,
         * or all documents) indexed without actually returning the content
         *
         * @return int representing number of indexed chunks
         * @throws SolrServerException
         */
        private int queryNumIndexedChunks() throws SolrServerException {
            SolrQuery q = new SolrQuery(Server.Schema.ID + ":*" + Server.ID_CHUNK_SEP + "*");
            q.setRows(0);
            int numChunks = (int) query(q).getResults().getNumFound();
            return numChunks;
        }

        /**
         * Execute query that gets only number of all Solr documents indexed
         * without actually returning the documents. Documents include entire
         * indexed files as well as chunks, which are treated as documents.
         *
         * @return int representing number of indexed documents (entire files
         * and chunks)
         * @throws SolrServerException
         */
        private int queryNumIndexedDocuments() throws SolrServerException {
            SolrQuery q = new SolrQuery("*:*");
            q.setRows(0);
            return (int) query(q).getResults().getNumFound();
        }

        /**
         * Return true if the file is indexed (either as a whole as a chunk)
         *
         * @param contentID
         * @return true if it is indexed
         * @throws SolrServerException
         */
        private boolean queryIsIndexed(long contentID) throws SolrServerException {
            SolrQuery q = new SolrQuery("*:*");
            q.addFilterQuery(Server.Schema.ID.toString() + ":" + Long.toString(contentID));
            //q.setFields(Server.Schema.ID.toString());
            q.setRows(0);
            return (int) query(q).getResults().getNumFound() != 0;
        }

        /**
         * Execute query that gets number of indexed file chunks for a file
         *
         * @param contentID file id of the original file broken into chunks and
         * indexed
         * @return int representing number of indexed file chunks, 0 if there is
         * no chunks
         * @throws SolrServerException
         */
        private int queryNumFileChunks(long contentID) throws SolrServerException {
            final SolrQuery q =
                    new SolrQuery(Server.Schema.ID + ":" + Long.toString(contentID) + Server.ID_CHUNK_SEP + "*");
            q.setRows(0);
            return (int) query(q).getResults().getNumFound();
        }
    }

    class ServerAction extends AbstractAction {

        @Override
        public void actionPerformed(ActionEvent e) {
            logger.log(Level.INFO, e.paramString().trim());
        }
    }
}
