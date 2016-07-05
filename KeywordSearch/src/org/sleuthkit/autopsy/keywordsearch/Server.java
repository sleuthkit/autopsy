/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import org.apache.solr.client.solrj.SolrQuery;
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
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.coreutils.UNCPathUtilities;
import org.sleuthkit.autopsy.core.UserPreferences;

/**
 * Handles management of a either a local or centralized Solr server and its
 * cores.
 */
public class Server {

    /**
     * Solr document field names.
     */
    public static enum Schema {

        ID {
            @Override
            public String toString() {
                return "id"; //NON-NLS
            }
        },
        IMAGE_ID {
            @Override
            public String toString() {
                return "image_id"; //NON-NLS
            }
        },
        // This is not stored or index . it is copied to Text and Content_Ws
        CONTENT {
            @Override
            public String toString() {
                return "content"; //NON-NLS
            }
        },
        TEXT {
            @Override
            public String toString() {
                return "text"; //NON-NLS
            }
        },
        CONTENT_WS {
            @Override
            public String toString() {
                return "content_ws"; //NON-NLS
            }
        },
        FILE_NAME {
            @Override
            public String toString() {
                return "file_name"; //NON-NLS
            }
        },
        // note that we no longer index this field
        CTIME {
            @Override
            public String toString() {
                return "ctime"; //NON-NLS
            }
        },
        // note that we no longer index this field
        ATIME {
            @Override
            public String toString() {
                return "atime"; //NON-NLS
            }
        },
        // note that we no longer index this field
        MTIME {
            @Override
            public String toString() {
                return "mtime"; //NON-NLS
            }
        },
        // note that we no longer index this field
        CRTIME {
            @Override
            public String toString() {
                return "crtime"; //NON-NLS
            }
        },
        NUM_CHUNKS {
            @Override
            public String toString() {
                return "num_chunks"; //NON-NLS
            }
        },
    };

    public static final String HL_ANALYZE_CHARS_UNLIMITED = "500000"; //max 1MB in a chunk. use -1 for unlimited, but -1 option may not be supported (not documented)
    //max content size we can send to Solr
    public static final long MAX_CONTENT_SIZE = 1L * 1024 * 1024 * 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    private static final String DEFAULT_CORE_NAME = "coreCase"; //NON-NLS
    public static final String CORE_EVT = "CORE_EVT"; //NON-NLS
    public static final char ID_CHUNK_SEP = '_';
    private String javaPath = "java"; //NON-NLS
    public static final Charset DEFAULT_INDEXED_TEXT_CHARSET = Charset.forName("UTF-8"); ///< default Charset to index text as
    private static final int MAX_SOLR_MEM_MB = 512; //TODO set dynamically based on avail. system resources
    private Process curSolrProcess = null;
    static final String PROPERTIES_FILE = KeywordSearchSettings.MODULE_NAME;
    static final String PROPERTIES_CURRENT_SERVER_PORT = "IndexingServerPort"; //NON-NLS
    static final String PROPERTIES_CURRENT_STOP_PORT = "IndexingServerStopPort"; //NON-NLS
    private static final String KEY = "jjk#09s"; //NON-NLS
    static final String DEFAULT_SOLR_SERVER_HOST = "localhost"; //NON-NLS
    static final int DEFAULT_SOLR_SERVER_PORT = 23232;
    static final int DEFAULT_SOLR_STOP_PORT = 34343;
    private int currentSolrServerPort = 0;
    private int currentSolrStopPort = 0;
    private static final boolean DEBUG = false;//(Version.getBuildType() == Version.Type.DEVELOPMENT);
    private UNCPathUtilities uncPathUtilities = null;
    private static final String SOLR = "solr";
    private static final String CORE_PROPERTIES = "core.properties";

    public enum CORE_EVT_STATES {

        STOPPED, STARTED
    };

    // A reference to the locally running Solr instance.
    private final HttpSolrServer localSolrServer;

    // A reference to the Solr server we are currently connected to for the Case.
    // This could be a local or remote server.
    private HttpSolrServer currentSolrServer;

    private Core currentCore;
    private final ReentrantReadWriteLock currentCoreLock;

    private final File solrFolder;
    private final ServerAction serverAction;
    private InputStreamPrinterThread errorRedirectThread;

    /**
     * New instance for the server at the given URL
     *
     */
    Server() {
        initSettings();

        this.localSolrServer = new HttpSolrServer("http://localhost:" + currentSolrServerPort + "/solr"); //NON-NLS
        serverAction = new ServerAction();
        solrFolder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false); //NON-NLS
        javaPath = PlatformUtil.getJavaPath();

        currentCoreLock = new ReentrantReadWriteLock(true);
        uncPathUtilities = new UNCPathUtilities();

        logger.log(Level.INFO, "Created Server instance"); //NON-NLS
    }

    private void initSettings() {

        if (ModuleSettings.settingExists(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT)) {
            try {
                currentSolrServerPort = Integer.decode(ModuleSettings.getConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT));
            } catch (NumberFormatException nfe) {
                logger.log(Level.WARNING, "Could not decode indexing server port, value was not a valid port number, using the default. ", nfe); //NON-NLS
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
                logger.log(Level.WARNING, "Could not decode indexing server stop port, value was not a valid port number, using default", nfe); //NON-NLS
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
                        + File.separator + "var" + File.separator + "log" //NON-NLS
                        + File.separator + "solr.log." + type; //NON-NLS
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
                logger.log(Level.WARNING, "Failed to create solr log file", ex); //NON-NLS
            }
        }

        void stopRun() {
            doRun = false;
        }

        @Override
        public void run() {

            try (InputStreamReader isr = new InputStreamReader(stream);
                    BufferedReader br = new BufferedReader(isr);
                    OutputStreamWriter osw = new OutputStreamWriter(out, PlatformUtil.getDefaultPlatformCharset());
                    BufferedWriter bw = new BufferedWriter(osw);) {

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
                logger.log(Level.SEVERE, "Error redirecting Solr output stream", ex); //NON-NLS
            }
        }
    }

    /**
     * Get list of PIDs of currently running Solr processes
     *
     * @return
     */
    List<Long> getSolrPIDs() {
        List<Long> pids = new ArrayList<>();

        //NOTE: these needs to be in sync with process start string in start()
        final String pidsQuery = "Args.4.eq=-DSTOP.KEY=" + KEY + ",Args.6.eq=start.jar"; //NON-NLS

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
            logger.log(Level.INFO, "Trying to kill old Solr process, PID: {0}", pid); //NON-NLS
            PlatformUtil.killProcess(pid);
        }
    }

    /**
     * Tries to start a local Solr instance in a separate process. Returns
     * immediately (probably before the server is ready) and doesn't check
     * whether it was successful.
     */
    void start() throws KeywordSearchModuleException, SolrServerNoPortException {
        if (isRunning()) {
            // If a Solr server is running we stop it.
            stop();
        }

        if (!isPortAvailable(currentSolrServerPort)) {
            // There is something already listening on our port. Let's see if
            // this is from an earlier run that didn't successfully shut down
            // and if so kill it.
            final List<Long> pids = this.getSolrPIDs();

            // If the culprit listening on the port is not a Solr process
            // we refuse to start.
            if (pids.isEmpty()) {
                throw new SolrServerNoPortException(currentSolrServerPort);
            }

            // Ok, we've tried to stop it above but there still appears to be
            // a Solr process listening on our port so we forcefully kill it.
            killSolr();

            // If either of the ports are still in use after our attempt to kill 
            // previously running processes we give up and throw an exception.
            if (!isPortAvailable(currentSolrServerPort)) {
                throw new SolrServerNoPortException(currentSolrServerPort);
            }
            if (!isPortAvailable(currentSolrStopPort)) {
                throw new SolrServerNoPortException(currentSolrStopPort);
            }
        }

        logger.log(Level.INFO, "Starting Solr server from: {0}", solrFolder.getAbsolutePath()); //NON-NLS

        if (isPortAvailable(currentSolrServerPort)) {
            logger.log(Level.INFO, "Port [{0}] available, starting Solr", currentSolrServerPort); //NON-NLS
            try {
                final String MAX_SOLR_MEM_MB_PAR = "-Xmx" + Integer.toString(MAX_SOLR_MEM_MB) + "m"; //NON-NLS
                List<String> commandLine = new ArrayList<>();
                commandLine.add(javaPath);
                commandLine.add(MAX_SOLR_MEM_MB_PAR);
                commandLine.add("-DSTOP.PORT=" + currentSolrStopPort); //NON-NLS
                commandLine.add("-Djetty.port=" + currentSolrServerPort); //NON-NLS
                commandLine.add("-DSTOP.KEY=" + KEY); //NON-NLS
                commandLine.add("-jar"); //NON-NLS
                commandLine.add("start.jar"); //NON-NLS

                ProcessBuilder solrProcessBuilder = new ProcessBuilder(commandLine);
                solrProcessBuilder.directory(solrFolder);

                // Redirect stdout and stderr to files to prevent blocking.
                Path solrStdoutPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stdout"); //NON-NLS
                solrProcessBuilder.redirectOutput(solrStdoutPath.toFile());

                Path solrStderrPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stderr"); //NON-NLS
                solrProcessBuilder.redirectError(solrStderrPath.toFile());

                logger.log(Level.INFO, "Starting Solr using: {0}", solrProcessBuilder.command()); //NON-NLS
                curSolrProcess = solrProcessBuilder.start();
                logger.log(Level.INFO, "Finished starting Solr"); //NON-NLS

                try {
                    //block for 10 seconds, give time to fully start the process
                    //so if it's restarted solr operations can be resumed seamlessly
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException ex) {
                    logger.log(Level.WARNING, "Timer interrupted"); //NON-NLS
                }

                final List<Long> pids = this.getSolrPIDs();
                logger.log(Level.INFO, "New Solr process PID: {0}", pids); //NON-NLS
            } catch (SecurityException ex) {
                logger.log(Level.SEVERE, "Could not start Solr process!", ex); //NON-NLS
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg"), ex);
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not start Solr server process!", ex); //NON-NLS
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg2"), ex);
            }
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

            ss = new ServerSocket(port, 0, java.net.Inet4Address.getByName("localhost")); //NON-NLS
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
                    /*
                     * should not be thrown
                     */
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
     * Tries to stop the local Solr instance.
     *
     * Waits for the stop command to finish before returning.
     */
    synchronized void stop() {

        try {
            // Close any open core before stopping server
            closeCore();
        } catch (KeywordSearchModuleException e) {
            logger.log(Level.WARNING, "Failed to close core: ", e); //NON-NLS
        }

        try {
            logger.log(Level.INFO, "Stopping Solr server from: {0}", solrFolder.getAbsolutePath()); //NON-NLS

            //try graceful shutdown
            final String[] SOLR_STOP_CMD = {
                javaPath,
                "-DSTOP.PORT=" + currentSolrStopPort, //NON-NLS
                "-DSTOP.KEY=" + KEY, //NON-NLS
                "-jar", //NON-NLS
                "start.jar", //NON-NLS
                "--stop", //NON-NLS
            };
            Process stop = Runtime.getRuntime().exec(SOLR_STOP_CMD, null, solrFolder);
            logger.log(Level.INFO, "Waiting for stopping Solr server"); //NON-NLS
            stop.waitFor();

            //if still running, forcefully stop it
            if (curSolrProcess != null) {
                curSolrProcess.destroy();
                curSolrProcess = null;
            }

        } catch (InterruptedException | IOException ex) {
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

            logger.log(Level.INFO, "Finished stopping Solr server"); //NON-NLS
        }
    }

    /**
     * Tests if there's a local Solr server running by sending it a core-status
     * request.
     *
     * @return false if the request failed with a connection error, otherwise
     *         true
     */
    synchronized boolean isRunning() throws KeywordSearchModuleException {
        try {

            if (isPortAvailable(currentSolrServerPort)) {
                return false;
            }

            if (curSolrProcess != null && !curSolrProcess.isAlive()) {
                return false;
            }

            // making a status request here instead of just doing solrServer.ping(), because
            // that doesn't work when there are no cores
            //TODO handle timeout in cases when some other type of server on that port
            CoreAdminRequest.getStatus(null, localSolrServer);

            logger.log(Level.INFO, "Solr server is running"); //NON-NLS
        } catch (SolrServerException ex) {

            Throwable cause = ex.getRootCause();

            // TODO: check if SocketExceptions should actually happen (is
            // probably caused by starting a connection as the server finishes
            // shutting down)
            if (cause instanceof ConnectException || cause instanceof SocketException) { //|| cause instanceof NoHttpResponseException) {
                logger.log(Level.INFO, "Solr server is not running, cause: {0}", cause.getMessage()); //NON-NLS
                return false;
            } else {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.isRunning.exception.errCheckSolrRunning.msg"), ex);
            }
        } catch (SolrException ex) {
            // Just log 404 errors for now...
            logger.log(Level.INFO, "Solr server is not running", ex); //NON-NLS
            return false;
        } catch (IOException ex) {
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.isRunning.exception.errCheckSolrRunning.msg2"), ex);
        }

        return true;
    }

    /*
     * ** Convenience methods for use while we only open one case at a time ***
     */
    /**
     * Creates/opens a Solr core (index) for a case.
     *
     * @param theCase The case for which the core is to be created/opened.
     *
     *
     * @throws KeywordSearchModuleException If an error occurs while
     *                                      creating/opening the core.
     */
    void openCoreForCase(Case theCase) throws KeywordSearchModuleException {
        currentCoreLock.writeLock().lock();
        try {
            currentCore = openCore(theCase);
            serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STARTED);
        } finally {
            currentCoreLock.writeLock().unlock();
        }
    }

    /**
     * Determines whether or not there is a currently open core (index).
     *
     * @return true or false
     */
    boolean coreIsOpen() {
        currentCoreLock.readLock().lock();
        try {
            return (null != currentCore);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    void closeCore() throws KeywordSearchModuleException {
        currentCoreLock.writeLock().lock();
        try {
            if (null != currentCore) {
                currentCore.close();
                currentCore = null;
                serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STOPPED);
            }
        } finally {
            currentCoreLock.writeLock().unlock();
        }
    }

    void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException {
        currentCoreLock.readLock().lock();
        try {
            currentCore.addDocument(doc);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Get index dir location for the case
     *
     * @param theCase the case to get index dir for
     *
     * @return absolute path to index dir
     */
    String geCoreDataDirPath(Case theCase) {
        String indexDir = theCase.getModuleDirectory() + File.separator + "keywordsearch" + File.separator + "data"; //NON-NLS
        if (uncPathUtilities != null) {
            // if we can check for UNC paths, do so, otherwise just return the indexDir
            String result = uncPathUtilities.mappedDriveToUNC(indexDir);
            if (result == null) {
                uncPathUtilities.rescanDrives();
                result = uncPathUtilities.mappedDriveToUNC(indexDir);
            }
            if (result == null) {
                return indexDir;
            }
            return result;
        }
        return indexDir;
    }

    /**
     * ** end single-case specific methods ***
     */
    /**
     * Creates/opens a Solr core (index) for a case.
     *
     * @param theCase The case for which the core is to be created/opened.
     *
     * @return An object representing the created/opened core.
     *
     * @throws KeywordSearchModuleException If an error occurs while
     *                                      creating/opening the core.
     */
    private Core openCore(Case theCase) throws KeywordSearchModuleException {
        try {
            if (theCase.getCaseType() == CaseType.SINGLE_USER_CASE) {
                currentSolrServer = this.localSolrServer;
            } else {
                String host = UserPreferences.getIndexingServerHost();
                String port = UserPreferences.getIndexingServerPort();
                currentSolrServer = new HttpSolrServer("http://" + host + ":" + port + "/solr"); //NON-NLS
            }
            connectToSolrServer(currentSolrServer);

        } catch (SolrServerException | IOException ex) {
            throw new KeywordSearchModuleException(NbBundle.getMessage(Server.class, "Server.connect.exception.msg"), ex);
        }

        String dataDir = geCoreDataDirPath(theCase);
        String coreName = theCase.getTextIndexName();
        return this.openCore(coreName.isEmpty() ? DEFAULT_CORE_NAME : coreName, new File(dataDir), theCase.getCaseType());
    }

    /**
     * Commits current core if it exists
     *
     * @throws SolrServerException, NoOpenCoreException
     */
    void commit() throws SolrServerException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            currentCore.commit();
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    NamedList<Object> request(SolrRequest request) throws SolrServerException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            return currentCore.request(request);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute query that gets only number of all Solr files indexed without
     * actually returning the files. The result does not include chunks, only
     * number of actual files.
     *
     * @return int representing number of indexed files
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumIndexedFiles() throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryNumIndexedFiles();
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryNumIdxFiles.exception.msg"), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute query that gets only number of all Solr file chunks (not logical
     * files) indexed without actually returning the content.
     *
     * @return int representing number of indexed chunks
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumIndexedChunks() throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryNumIndexedChunks();
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryNumIdxChunks.exception.msg"), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute query that gets only number of all Solr documents indexed (files
     * and chunks) without actually returning the documents
     *
     * @return int representing number of indexed files (files and chunks)
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumIndexedDocuments() throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryNumIndexedDocuments();
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryNumIdxDocs.exception.msg"), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Return true if the file is indexed (either as a whole as a chunk)
     *
     * @param contentID
     *
     * @return true if it is indexed
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public boolean queryIsIndexed(long contentID) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryIsIndexed(contentID);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryIsIdxd.exception.msg"), ex);
            }

        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute query that gets number of indexed file chunks for a file
     *
     * @param fileID file id of the original file broken into chunks and indexed
     *
     * @return int representing number of indexed file chunks, 0 if there is no
     *         chunks
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumFileChunks(long fileID) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryNumFileChunks(fileID);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryNumFileChunks.exception.msg"), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute solr query
     *
     * @param sq query
     *
     * @return query response
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.query(sq);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.query.exception.msg", sq.getQuery()), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute solr query
     *
     * @param sq     the query
     * @param method http method to use
     *
     * @return query response
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public QueryResponse query(SolrQuery sq, SolrRequest.METHOD method) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.query(sq, method);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.query2.exception.msg", sq.getQuery()), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute Solr terms query
     *
     * @param sq the query
     *
     * @return terms response
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public TermsResponse queryTerms(SolrQuery sq) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCore.queryTerms(sq);
            } catch (SolrServerException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryTerms.exception.msg", sq.getQuery()), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Get the text contents of the given file as stored in SOLR.
     *
     * @param content to get the text for
     *
     * @return content text string or null on error
     *
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content) throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            return currentCore.getSolrContent(content.getId(), 0);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Get the text contents of a single chunk for the given file as stored in
     * SOLR.
     *
     * @param content to get the text for
     * @param chunkID chunk number to query (starting at 1), or 0 if there is no
     *                chunks for that content
     *
     * @return content text string or null if error quering
     *
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content, int chunkID) throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            return currentCore.getSolrContent(content.getId(), chunkID);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Get the text contents for the given object id.
     *
     * @param objectID
     *
     * @return
     *
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final long objectID) throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            return currentCore.getSolrContent(objectID, 0);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Get the text contents for the given object id and chunk id.
     *
     * @param objectID
     * @param chunkID
     *
     * @return
     *
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final long objectID, final int chunkID) throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCore) {
                throw new NoOpenCoreException();
            }
            return currentCore.getSolrContent(objectID, chunkID);
        } finally {
            currentCoreLock.readLock().unlock();
        }
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
     * @param childID  the child chunk id
     *
     * @return formatted string id
     */
    public static String getChunkIdString(long parentID, int childID) {
        return Long.toString(parentID) + Server.ID_CHUNK_SEP + Integer.toString(childID);
    }

    /**
     * Creates/opens a Solr core (index) for a case.
     *
     * @param coreName The core name.
     * @param dataDir  The data directory for the core.
     * @param caseType The type of the case (single-user or multi-user) for
     *                 which the core is being created/opened.
     *
     * @return An object representing the created/opened core.
     *
     * @throws KeywordSearchModuleException If an error occurs while
     *                                      creating/opening the core.
     */
    private Core openCore(String coreName, File dataDir, CaseType caseType) throws KeywordSearchModuleException {

        try {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            if (!this.isRunning()) {
                logger.log(Level.SEVERE, "Core create/open requested, but server not yet running"); //NON-NLS
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.msg"));
            }

            if (!coreIsLoaded(coreName)) {
                /*
                 * The core either does not exist or it is not loaded. Make a
                 * request that will cause the core to be created if it does not
                 * exist or loaded if it already exists.
                 */

                // In single user mode, if there is a core.properties file already,
                // we've hit a solr bug. Compensate by deleting it.
                if (caseType == CaseType.SINGLE_USER_CASE) {
                    Path corePropertiesFile = Paths.get(solrFolder.toString(), SOLR, coreName, CORE_PROPERTIES);
                    if (corePropertiesFile.toFile().exists()) {
                        try {
                            corePropertiesFile.toFile().delete();
                        } catch (Exception ex) {
                            logger.log(Level.INFO, "Could not delete pre-existing core.properties prior to opening the core."); //NON-NLS
                        }
                    }
                }

                CoreAdminRequest.Create createCoreRequest = new CoreAdminRequest.Create();
                createCoreRequest.setDataDir(dataDir.getAbsolutePath());
                createCoreRequest.setCoreName(coreName);
                createCoreRequest.setConfigSet("AutopsyConfig"); //NON-NLS
                createCoreRequest.setIsLoadOnStartup(false);
                createCoreRequest.setIsTransient(true);
                currentSolrServer.request(createCoreRequest);
            }

            if (!coreIndexFolderExists(coreName)) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.noIndexDir.msg"));
            }

            return new Core(coreName, caseType);

        } catch (SolrServerException | SolrException | IOException ex) {
            throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg"), ex);
        }
    }

    /**
     * Attempts to connect to the given Solr server.
     *
     * @param solrServer
     *
     * @throws SolrServerException
     * @throws IOException
     */
    void connectToSolrServer(HttpSolrServer solrServer) throws SolrServerException, IOException {
        CoreAdminRequest.getStatus(null, solrServer);
    }

    /**
     * Determines whether or not a particular Solr core exists and is loaded.
     *
     * @param coreName The name of the core.
     *
     * @return True if the core exists and is loaded, false if the core does not
     *         exist or is not loaded
     *
     * @throws SolrServerException If there is a problem communicating with the
     *                             Solr server.
     * @throws IOException         If there is a problem communicating with the
     *                             Solr server.
     */
    private boolean coreIsLoaded(String coreName) throws SolrServerException, IOException {
        CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, currentSolrServer);
        return response.getCoreStatus(coreName).get("instanceDir") != null; //NON-NLS
    }

    /**
     * Determines whether or not the index files folder for a Solr core exists.
     *
     * @param coreName the name of the core.
     *
     * @return true or false
     *
     * @throws SolrServerException
     * @throws IOException
     */
    private boolean coreIndexFolderExists(String coreName) throws SolrServerException, IOException {
        CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, currentSolrServer);
        Object dataDirPath = response.getCoreStatus(coreName).get("dataDir"); //NON-NLS
        if (null != dataDirPath) {
            File indexDir = Paths.get((String) dataDirPath, "index").toFile();  //NON-NLS
            return indexDir.exists();
        } else {
            return false;
        }
    }

    class Core {

        // handle to the core in Solr
        private final String name;

        private final CaseType caseType;

        // the server to access a core needs to be built from a URL with the
        // core in it, and is only good for core-specific operations
        private final HttpSolrServer solrCore;

        private Core(String name, CaseType caseType) {
            this.name = name;
            this.caseType = caseType;

            this.solrCore = new HttpSolrServer(currentSolrServer.getBaseURL() + "/" + name);

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
                logger.log(Level.WARNING, "Could not issue Solr request. ", e); //NON-NLS
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
                logger.log(Level.WARNING, "Could not commit index. ", e); //NON-NLS
                throw new SolrServerException(NbBundle.getMessage(this.getClass(), "Server.commit.exception.msg"), e);
            }
        }

        void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException {
            try {
                solrCore.add(doc);
            } catch (SolrServerException ex) {
                logger.log(Level.SEVERE, "Could not add document to index via update handler: " + doc.getField("id"), ex); //NON-NLS
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.addDoc.exception.msg", doc.getField("id")), ex); //NON-NLS
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "Could not add document to index via update handler: " + doc.getField("id"), ex); //NON-NLS
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.addDoc.exception.msg2", doc.getField("id")), ex); //NON-NLS
            }
        }

        /**
         * get the text from the content field for the given file
         *
         * @param contentID
         * @param chunkID
         *
         * @return
         */
        private String getSolrContent(long contentID, int chunkID) {
            final SolrQuery q = new SolrQuery();
            q.setQuery("*:*");
            String filterQuery = Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(Long.toString(contentID));
            if (chunkID != 0) {
                filterQuery = filterQuery + Server.ID_CHUNK_SEP + chunkID;
            }
            q.addFilterQuery(filterQuery);
            q.setFields(Schema.TEXT.toString());
            try {
                // Get the first result. 
                SolrDocumentList solrDocuments = solrCore.query(q).getResults();

                if (!solrDocuments.isEmpty()) {
                    SolrDocument solrDocument = solrDocuments.get(0);
                    if (solrDocument != null) {
                        Collection<Object> fieldValues = solrDocument.getFieldValues(Schema.TEXT.toString());
                        if (fieldValues.size() == 1) // The indexed text field for artifacts will only have a single value.
                        {
                            return fieldValues.toArray(new String[0])[0];
                        } else // The indexed text for files has 2 values, the file name and the file content.
                        // We return the file content value.
                        {
                            return fieldValues.toArray(new String[0])[1];
                        }
                    }
                }
            } catch (SolrServerException ex) {
                logger.log(Level.WARNING, "Error getting content from Solr", ex); //NON-NLS
                return null;
            }

            return null;
        }

        synchronized void close() throws KeywordSearchModuleException {
            // We only unload cores for "single-user" cases.
            if (this.caseType == CaseType.MULTI_USER_CASE) {
                return;
            }

            try {
                CoreAdminRequest.unloadCore(this.name, currentSolrServer);
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
         *         chunks)
         *
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
         *
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
         *         and chunks)
         *
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
         *
         * @return true if it is indexed
         *
         * @throws SolrServerException
         */
        private boolean queryIsIndexed(long contentID) throws SolrServerException {
            String id = KeywordSearchUtil.escapeLuceneQuery(Long.toString(contentID));
            SolrQuery q = new SolrQuery("*:*");
            q.addFilterQuery(Server.Schema.ID.toString() + ":" + id);
            //q.setFields(Server.Schema.ID.toString());
            q.setRows(0);
            return (int) query(q).getResults().getNumFound() != 0;
        }

        /**
         * Execute query that gets number of indexed file chunks for a file
         *
         * @param contentID file id of the original file broken into chunks and
         *                  indexed
         *
         * @return int representing number of indexed file chunks, 0 if there is
         *         no chunks
         *
         * @throws SolrServerException
         */
        private int queryNumFileChunks(long contentID) throws SolrServerException {
            String id = KeywordSearchUtil.escapeLuceneQuery(Long.toString(contentID));
            final SolrQuery q
                    = new SolrQuery(Server.Schema.ID + ":" + id + Server.ID_CHUNK_SEP + "*");
            q.setRows(0);
            return (int) query(q).getResults().getNumFound();
        }
    }

    class ServerAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        @Override
        public void actionPerformed(ActionEvent e) {
            logger.log(Level.INFO, e.paramString().trim());
        }
    }

    /**
     * Exception thrown if solr port not available
     */
    class SolrServerNoPortException extends SocketException {

        private static final long serialVersionUID = 1L;

        /**
         * the port number that is not available
         */
        private final int port;

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
