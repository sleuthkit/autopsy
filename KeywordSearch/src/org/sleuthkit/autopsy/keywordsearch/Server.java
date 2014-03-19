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
import java.lang.Long;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.common.util.NamedList;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.datamodel.Content;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.client.solrj.impl.XMLResponseParser;

/**
 * Handles for keeping track of a Solr server and its cores
 */
public class Server {

    // field names that are used in SOLR schema
    public static enum Schema {
        ID {
            @Override
            public String toString() {
                return "id";
            }
        },
        IMAGE_ID {
            @Override
            public String toString() {
                return "image_id";
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
        // note that we no longer index this field
        CTIME {
            @Override
            public String toString() {
                return "ctime";
            }
        },
        // note that we no longer index this field
        ATIME {
            @Override
            public String toString() {
                return "atime";
            }
        },
        // note that we no longer index this field
        MTIME {
            @Override
            public String toString() {
                return "mtime";
            }
        },
        // note that we no longer index this field
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
        },
    };
    public static final String HL_ANALYZE_CHARS_UNLIMITED = "500000"; //max 1MB in a chunk. use -1 for unlimited, but -1 option may not be supported (not documented)
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
    static final String PROPERTIES_FILE = KeywordSearchSettings.MODULE_NAME;
    static final String PROPERTIES_CURRENT_SERVER_PORT = "IndexingServerPort";
    static final String PROPERTIES_CURRENT_STOP_PORT = "IndexingServerStopPort";
    private static final String KEY = "jjk#09s";
    static final int DEFAULT_SOLR_SERVER_PORT = 23232;
    static final int DEFAULT_SOLR_STOP_PORT = 34343;
    private int currentSolrServerPort = 0;
    private int currentSolrStopPort = 0;
    private static final boolean DEBUG = false;//(Version.getBuildType() == Version.Type.DEVELOPMENT);

    public enum CORE_EVT_STATES {

        STOPPED, STARTED
    };
    private SolrServer solrServer;
    private String instanceDir;
    private File solrFolder;
    private ServerAction serverAction;
    private InputStreamPrinterThread errorRedirectThread;
    private String solrUrl;

    /**
     * New instance for the server at the given URL
     *
     * @param url should be something like "http://localhost:23232/solr/"
     */
    Server() {
        initSettings();

        this.solrUrl = "http://localhost:" + currentSolrServerPort + "/solr";
        this.solrServer = new HttpSolrServer(solrUrl);
        serverAction = new ServerAction();
        solrFolder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false);
        instanceDir = solrFolder.getAbsolutePath() + File.separator + "solr";
        javaPath = PlatformUtil.getJavaPath();

        logger.log(Level.INFO, "Created Server instance");
    }

    private void initSettings() {
        if (ModuleSettings.settingExists(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT)) {
            try {
                currentSolrServerPort = Integer.decode(ModuleSettings.getConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT));
            } catch (NumberFormatException nfe) {
                logger.log(Level.WARNING, "Could not decode indexing server port, value was not a valid port number, using the default. ", nfe);
                currentSolrServerPort = DEFAULT_SOLR_SERVER_PORT;
            }
        } else {
            currentSolrServerPort = DEFAULT_SOLR_SERVER_PORT;
            ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT, String.valueOf(currentSolrServerPort));
        }

        if (ModuleSettings.settingExists(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT)) {
            try {
                currentSolrStopPort = Integer.decode(ModuleSettings.getConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT));
            } catch (NumberFormatException nfe) {
                logger.log(Level.WARNING, "Could not decode indexing server stop port, value was not a valid port number, using default", nfe);
                currentSolrStopPort = DEFAULT_SOLR_STOP_PORT;
            }
        } else {
            currentSolrStopPort = DEFAULT_SOLR_STOP_PORT;
            ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT, String.valueOf(currentSolrStopPort));
        }
    }

    @Override
    public void finalize() throws java.lang.Throwable {
        stop();
        super.finalize();
    }

    public void addServerActionListener(PropertyChangeListener l) {
        serverAction.addPropertyChangeListener(l);
    }

    int getCurrentSolrServerPort() {
        return currentSolrServerPort;
    }

    int getCurrentSolrStopPort() {
        return currentSolrStopPort;
    }

    /**
     * Helper threads to handle stderr/stdout from Solr process
     */
    private static class InputStreamPrinterThread extends Thread {

        InputStream stream;
        OutputStream out;
        volatile boolean doRun = true;

        InputStreamPrinterThread(InputStream stream, String type) {
            this.stream = stream;
            try {
                final String log = Places.getUserDirectory().getAbsolutePath()
                        + File.separator + "var" + File.separator + "log"
                        + File.separator + "solr.log." + type;
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
            OutputStreamWriter osw = null;
            BufferedWriter bw = null;
            try {
                osw = new OutputStreamWriter(out, PlatformUtil.getDefaultPlatformCharset());
                bw = new BufferedWriter(osw);
                String line = null;
                while (doRun && (line = br.readLine()) != null) {
                    bw.write(line);
                    bw.newLine();
                    if (DEBUG) {
                        //flush buffers if dev version for debugging
                        bw.flush();
                    }
                }
                bw.flush();
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Error redirecting Solr output stream");
            } finally {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Error closing Solr output stream writer");
                    }
                }
                 if (br != null) {
                    try {
                        br.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Error closing Solr output stream reader");
                    }
                }
            }
        }
    }

    /**
     * Get list of PIDs of currently running Solr processes
     *
     * @return
     */
    List<Long> getSolrPIDs() {
        List<Long> pids = new ArrayList<Long>();

        //NOTE: these needs to be in sync with process start string in start()
        final String pidsQuery = "Args.4.eq=-DSTOP.KEY=" + KEY + ",Args.7.eq=start.jar";

        long[] pidsArr = PlatformUtil.getJavaPIDs(pidsQuery);
        if (pidsArr != null) {
            for (int i = 0; i < pidsArr.length; ++i) {
                pids.add(pidsArr[i]);
            }
        }

        return pids;
    }

    /**
     * Kill residual Solr processes. Note, this method should be used only if
     * Solr could not be stopped in a graceful manner.
     */
    void killSolr() {
        List<Long> solrPids = getSolrPIDs();
        for (long pid : solrPids) {
            logger.log(Level.INFO, "Trying to kill old Solr process, PID: " + pid);
            PlatformUtil.killProcess(pid);
        }
    }

    /**
     * Tries to start a Solr instance in a separate process. Returns immediately
     * (probably before the server is ready) and doesn't check whether it was
     * successful.
     */
    void start() throws KeywordSearchModuleException, SolrServerNoPortException {
        logger.log(Level.INFO, "Starting Solr server from: " + solrFolder.getAbsolutePath());
        if (isPortAvailable(currentSolrServerPort)) {
            logger.log(Level.INFO, "Port [" + currentSolrServerPort + "] available, starting Solr");
            try {
                final String MAX_SOLR_MEM_MB_PAR = "-Xmx" + Integer.toString(MAX_SOLR_MEM_MB) + "m";

                String loggingPropertiesOpt = "-Djava.util.logging.config.file=";
                String loggingPropertiesFilePath = instanceDir + File.separator + "conf" + File.separator;

                if (DEBUG) {
                    loggingPropertiesFilePath += "logging-development.properties";
                } else {
                    loggingPropertiesFilePath += "logging-release.properties";
                }

                final String loggingProperties = loggingPropertiesOpt + loggingPropertiesFilePath;

                final String [] SOLR_START_CMD = {
                    javaPath,
                    MAX_SOLR_MEM_MB_PAR,
                    "-DSTOP.PORT=" + currentSolrStopPort,
                    "-Djetty.port=" + currentSolrServerPort,
                    "-DSTOP.KEY=" + KEY,
                    loggingProperties,
                    "-jar",
                    "start.jar"};
                
                StringBuilder cmdSb = new StringBuilder();
                for (int i = 0; i<SOLR_START_CMD.length; ++i ) {
                    cmdSb.append(SOLR_START_CMD[i]).append(" ");
                }
                
                logger.log(Level.INFO, "Starting Solr using: " + cmdSb.toString());
                curSolrProcess = Runtime.getRuntime().exec(SOLR_START_CMD, null, solrFolder);
                logger.log(Level.INFO, "Finished starting Solr");

                try {
                    //block, give time to fully start the process
                    //so if it's restarted solr operations can be resumed seamlessly
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    logger.log(Level.WARNING, "Timer interrupted");
                }
                // Handle output to prevent process from blocking

                errorRedirectThread = new InputStreamPrinterThread(curSolrProcess.getErrorStream(), "stderr");
                errorRedirectThread.start();

                final List<Long> pids = this.getSolrPIDs();
                logger.log(Level.INFO, "New Solr process PID: " + pids);


            } catch (SecurityException ex) {
                logger.log(Level.WARNING, "Could not start Solr process!", ex);
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg"), ex);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not start Solr server process!", ex);
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg2"), ex);
            }
        } else {
            logger.log(Level.WARNING, "Could not start Solr server process, port [" + currentSolrServerPort + "] not available!");
            throw new SolrServerNoPortException(currentSolrServerPort);
        }
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port the port to check for availability
     */
    static boolean isPortAvailable(int port) {
        ServerSocket ss = null;
        try {

            ss = new ServerSocket(port, 0, java.net.Inet4Address.getByName("localhost"));
            if (ss.isBound()) {
                ss.setReuseAddress(true);
                ss.close();
                return true;
            }

        } catch (IOException e) {
        } finally {
            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }
        return false;
    }

    /**
     * Changes the current solr server port. Only call this after available.
     *
     * @param port Port to change to
     */
    void changeSolrServerPort(int port) {
        currentSolrServerPort = port;
        ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT, String.valueOf(port));
    }

    /**
     * Changes the current solr stop port. Only call this after available.
     *
     * @param port Port to change to
     */
    void changeSolrStopPort(int port) {
        currentSolrStopPort = port;
        ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT, String.valueOf(port));
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
            final String [] SOLR_STOP_CMD = {
              javaPath,
              "-DSTOP.PORT=" + currentSolrStopPort,
              "-DSTOP.KEY=" + KEY,
              "-jar",
              "start.jar",
              "--stop",
            };
            Process stop = Runtime.getRuntime().exec(SOLR_STOP_CMD, null, solrFolder);
            logger.log(Level.INFO, "Waiting for stopping Solr server");
            stop.waitFor();

            //if still running, forcefully stop it
            if (curSolrProcess != null) {
                curSolrProcess.destroy();
                curSolrProcess = null;
            }

        } catch (InterruptedException ex) {
        } catch (IOException ex) {
        } finally {
            //stop Solr stream -> log redirect threads
            try {
                if (errorRedirectThread != null) {
                    errorRedirectThread.stopRun();
                    errorRedirectThread = null;
                }
            } finally {
                //if still running, kill it
                killSolr();
            }

            logger.log(Level.INFO, "Finished stopping Solr server");
        }
    }

    /**
     * Tests if there's a Solr server running by sending it a core-status
     * request.
     *
     * @return false if the request failed with a connection error, otherwise
     * true
     */
    synchronized boolean isRunning() throws KeywordSearchModuleException {
        try {
            // making a status request here instead of just doing solrServer.ping(), because
            // that doesn't work when there are no cores

            //TODO check if port avail and return false if it is

            //TODO handle timeout in cases when some other type of server on that port
            CoreAdminRequest.getStatus(null, solrServer);
            
            logger.log(Level.INFO, "Solr server is running");
        } catch (SolrServerException ex) {

            Throwable cause = ex.getRootCause();

            // TODO: check if SocketExceptions should actually happen (is
            // probably caused by starting a connection as the server finishes
            // shutting down)
            if (cause instanceof ConnectException || cause instanceof SocketException) { //|| cause instanceof NoHttpResponseException) {
                logger.log(Level.INFO, "Solr server is not running, cause: " + cause.getMessage());
                return false;
            } else {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.isRunning.exception.errCheckSolrRunning.msg"), ex);
            }
        } catch (IOException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.isRunning.exception.errCheckSolrRunning.msg2"), ex);
        }

        return true;
    }
    /**
     * ** Convenience methods for use while we only open one case at a time ***
     */
    private volatile Core currentCore = null;

    synchronized void openCore() throws KeywordSearchModuleException {
        if (currentCore != null) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.openCore.exception.alreadyOpen.msg"));
        }

        Case currentCase = Case.getCurrentCase();

        validateIndexLocation(currentCase);

        currentCore = openCore(currentCase);
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STARTED);
    }

    /**
     * Checks if index dir exists, and moves it to new location if needed (for
     * backwards compatibility with older cases)
     */
    private void validateIndexLocation(Case theCase) {
        logger.log(Level.INFO, "Validating keyword search index location");
        String properIndexPath = getIndexDirPath(theCase);

        String legacyIndexPath = theCase.getCaseDirectory()
                + File.separator + "keywordsearch" + File.separator + "data";


        File properIndexDir = new File(properIndexPath);
        File legacyIndexDir = new File(legacyIndexPath);
        if (!properIndexDir.exists()
                && legacyIndexDir.exists() && legacyIndexDir.isDirectory()) {
            logger.log(Level.INFO, "Moving keyword search index location from: "
                    + legacyIndexPath + " to: " + properIndexPath);
            try {
                Files.move(Paths.get(legacyIndexDir.getParent()), Paths.get(properIndexDir.getParent()));
            } catch (IOException | SecurityException ex) {
                logger.log(Level.WARNING, "Error moving keyword search index folder from: "
                        + legacyIndexPath + " to: " + properIndexPath
                        + " will recreate a new index.", ex);
            }
        }
    }

    synchronized void closeCore() throws KeywordSearchModuleException {
        if (currentCore == null) {
            return;
        }
        currentCore.close();
        currentCore = null;
        serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STOPPED);
    }

    void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException {
        currentCore.addDocument(doc);
    }

    /**
     * Get index dir location for the case
     *
     * @param theCase the case to get index dir for
     * @return absolute path to index dir
     */
    String getIndexDirPath(Case theCase) {
        String indexDir = theCase.getModulesOutputDirAbsPath()
                + File.separator + "keywordsearch" + File.separator + "data";
        return indexDir;
    }

    /**
     * ** end single-case specific methods ***
     */
    /**
     * Open a core for the given case
     *
     * @param theCase the case to open core for
     * @return
     */
    private synchronized Core openCore(Case theCase) throws KeywordSearchModuleException {
        String dataDir = getIndexDirPath(theCase);
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
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreExceptionn
     */
    public int queryNumIndexedFiles() throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryNumIndexedFiles();
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryNumIdxFiles.exception.msg"), ex);
        }


    }

    /**
     * Execute query that gets only number of all Solr file chunks (not logical
     * files) indexed without actually returning the content.
     *
     * @return int representing number of indexed chunks
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumIndexedChunks() throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryNumIndexedChunks();
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryNumIdxChunks.exception.msg"), ex);
        }
    }

    /**
     * Execute query that gets only number of all Solr documents indexed (files
     * and chunks) without actually returning the documents
     *
     * @return int representing number of indexed files (files and chunks)
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumIndexedDocuments() throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryNumIndexedDocuments();
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryNumIdxDocs.exception.msg"), ex);
        }
    }

    /**
     * Return true if the file is indexed (either as a whole as a chunk)
     *
     * @param contentID
     * @return true if it is indexed
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public boolean queryIsIndexed(long contentID) throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryIsIndexed(contentID);
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryIsIdxd.exception.msg"), ex);
        }
    }

    /**
     * Execute query that gets number of indexed file chunks for a file
     *
     * @param fileID file id of the original file broken into chunks and indexed
     * @return int representing number of indexed file chunks, 0 if there is no
     * chunks
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumFileChunks(long fileID) throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryNumFileChunks(fileID);
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryNumFileChunks.exception.msg"), ex);
        }
    }

    /**
     * Execute solr query
     *
     * @param sq query
     * @return query response
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq) throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.query(sq);
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.query.exception.msg", sq.getQuery()), ex);
        }
    }

    /**
     * Execute solr query
     *
     * @param sq the query
     * @param method http method to use
     * @return query response
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq, SolrRequest.METHOD method) throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.query(sq, method);
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.query2.exception.msg", sq.getQuery()), ex);
        }
    }

    /**
     * Execute Solr terms query
     *
     * @param sq the query
     * @return terms response
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public TermsResponse queryTerms(SolrQuery sq) throws KeywordSearchModuleException, NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        try {
            return currentCore.queryTerms(sq);
        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.queryTerms.exception.msg", sq.getQuery()), ex);
        }
    }

    /**
     * Get the text contents of the given file as stored in SOLR.
     *
     * @param content to get the text for
     * @return content text string or null on error
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content) throws NoOpenCoreException {
        if (currentCore == null) {
            throw new NoOpenCoreException();
        }
        return currentCore.getSolrContent(content.getId(), 0);
    }

    /**
     * Get the text contents of a single chunk for the given file as stored in SOLR.
     *
     * @param content to get the text for
     * @param chunkID chunk number to query (starting at 1), or 0 if there is no
     * chunks for that content
     * @return content text string or null if error quering
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content, int chunkID) throws NoOpenCoreException {
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
    private Core openCore(String coreName, File dataDir) throws KeywordSearchModuleException {
        try {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            //handle a possible scenario when server process might not be fully started
            if (!this.isRunning()) {
                logger.log(Level.WARNING, "Core open requested, but server not yet running");
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.openCore.exception.msg"));
            }

            CoreAdminRequest.Create createCore = new CoreAdminRequest.Create();
            createCore.setDataDir(dataDir.getAbsolutePath());
            createCore.setInstanceDir(instanceDir);
            createCore.setCoreName(coreName);

            this.solrServer.request(createCore);

            final Core newCore = new Core(coreName);

            return newCore;

        } catch (SolrServerException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg"), ex);
        } catch (IOException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg2"), ex);
        }
    }

    class Core {

        // handle to the core in Solr
        private String name;
        // the server to access a core needs to be built from a URL with the
        // core in it, and is only good for core-specific operations
        private HttpSolrServer solrCore;

        private Core(String name) {
            this.name = name;

            this.solrCore = new HttpSolrServer(solrUrl + "/" + name);

            //TODO test these settings
            //solrCore.setSoTimeout(1000 * 60);  // socket read timeout, make large enough so can index larger files
            //solrCore.setConnectionTimeout(1000);
            solrCore.setDefaultMaxConnectionsPerHost(2);
            solrCore.setMaxTotalConnections(5);
            solrCore.setFollowRedirects(false);  // defaults to false
            // allowCompression defaults to false.
            // Server side must support gzip or deflate for this to have any effect.
            solrCore.setAllowCompression(true);
            solrCore.setMaxRetries(1); // defaults to 0.  > 1 not recommended.
            solrCore.setParser(new XMLResponseParser()); // binary parser is used by default


        }

        private QueryResponse query(SolrQuery sq) throws SolrServerException {
            return solrCore.query(sq);
        }

        private NamedList<Object> request(SolrRequest request) throws SolrServerException {
            try {
                return solrCore.request(request);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Could not issue Solr request. ", e);
                throw new SolrServerException(
                        NbBundle.getMessage(this.getClass(), "Server.request.exception.exception.msg"), e);
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
                throw new SolrServerException(NbBundle.getMessage(this.getClass(), "Server.commit.exception.msg"), e);
            }
        }

        void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException {
            try {
                solrCore.add(doc);
            } catch (SolrServerException ex) {
                logger.log(Level.SEVERE, "Could not add document to index via update handler: " + doc.getField("id"), ex);
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.addDoc.exception.msg", doc.getField("id")), ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not add document to index via update handler: " + doc.getField("id"), ex);
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.addDoc.exception.msg2", doc.getField("id")), ex);
            }
        }

        /**
         * get the text from the content field for the given file
         * @param contentID
         * @param chunkID
         * @return 
         */
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

        synchronized void close() throws KeywordSearchModuleException {
            try {
                CoreAdminRequest.unloadCore(this.name, solrServer);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.close.exception.msg"), ex);
            } catch (IOException ex) {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.close.exception.msg2"), ex);
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

    /**
     * Exception thrown if solr port not available
     */
    class SolrServerNoPortException extends SocketException {

        /**
         * the port number that is not available
         */
        private int port;

        SolrServerNoPortException(int port) {
            super(NbBundle.getMessage(Server.class, "Server.solrServerNoPortException.msg", port,
                                      Server.PROPERTIES_CURRENT_SERVER_PORT));
            this.port = port;
        }

        int getPortNumber() {
            return port;
        }
    }
}
