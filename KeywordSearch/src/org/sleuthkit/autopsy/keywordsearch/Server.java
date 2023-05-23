/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2021 Basis Technology Corp.
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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.apache.commons.io.FileUtils;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toList;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.ConcurrentUpdateSolrClient;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient.RemoteSolrException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.TermsResponse;
import org.apache.solr.client.solrj.response.TermsResponse.Term;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Places;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.Case.CaseType;
import org.sleuthkit.autopsy.casemodule.CaseMetadata;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.ThreadUtils;
import org.sleuthkit.autopsy.healthmonitor.HealthMonitor;
import org.sleuthkit.autopsy.healthmonitor.TimingMetric;
import org.sleuthkit.autopsy.keywordsearchservice.KeywordSearchServiceException;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.datamodel.Content;

/**
 * Handles management of a either a local or centralized Solr server and its
 * collections or cores.
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
        // This is not stored or indexed. it is copied to text by the schema
        CONTENT {
            @Override
            public String toString() {
                return "content"; //NON-NLS
            }
        },
        // String representation for regular expression searching
        CONTENT_STR {
            @Override
            public String toString() {
                return "content_str"; //NON-NLS
            }
        },
        // default search field.  Populated by schema
        TEXT {
            @Override
            public String toString() {
                return "text"; //NON-NLS
            }
        },
        // no longer populated.  Was used for regular expression searching.
        // Should not be used. 
        CONTENT_WS {
            @Override
            public String toString() {
                return "content_ws"; //NON-NLS
            }
        },
        CONTENT_JA {
            @Override
            public String toString() {
                return "content_ja"; //NON-NLS
            }
        },
        LANGUAGE {
            @Override
            public String toString() {
                return "language"; //NON-NLS
            }
        },
        FILE_NAME {
            @Override
            public String toString() {
                return "file_name"; //NON-NLS
            }
        },
        // note that we no longer store or index this field
        CTIME {
            @Override
            public String toString() {
                return "ctime"; //NON-NLS
            }
        },
        // note that we no longer store or index this field
        ATIME {
            @Override
            public String toString() {
                return "atime"; //NON-NLS
            }
        },
        // note that we no longer store or index this field
        MTIME {
            @Override
            public String toString() {
                return "mtime"; //NON-NLS
            }
        },
        // note that we no longer store or index this field
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
        CHUNK_SIZE {
            @Override
            public String toString() {
                return "chunk_size"; //NON-NLS
            }
        },
        /**
         * termfreq is a function which returns the number of times the term
         * appears. This is not an actual field defined in schema.xml, but can
         * be gotten from returned documents in the same way as fields.
         */
        TERMFREQ {
            @Override
            public String toString() {
                return "termfreq"; //NON-NLS
            }
        }
    };

    public static final String HL_ANALYZE_CHARS_UNLIMITED = "500000"; //max 1MB in a chunk. use -1 for unlimited, but -1 option may not be supported (not documented)
    //max content size we can send to Solr
    public static final long MAX_CONTENT_SIZE = 1L * 31 * 1024 * 1024;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    public static final String CORE_EVT = "CORE_EVT"; //NON-NLS
    @Deprecated
    public static final char ID_CHUNK_SEP = '_';
    public static final String CHUNK_ID_SEPARATOR = "_";
    private String javaPath = "java";
    public static final Charset DEFAULT_INDEXED_TEXT_CHARSET = Charset.forName("UTF-8"); ///< default Charset to index text as
    private Process curSolrProcess = null;
    static final String PROPERTIES_FILE = KeywordSearchSettings.MODULE_NAME;
    static final String PROPERTIES_CURRENT_SERVER_PORT = "IndexingServerPort"; //NON-NLS
    static final String PROPERTIES_CURRENT_STOP_PORT = "IndexingServerStopPort"; //NON-NLS
    private static final String KEY = "jjk#09s"; //NON-NLS
    static final String DEFAULT_SOLR_SERVER_HOST = "localhost"; //NON-NLS
    static final int DEFAULT_SOLR_SERVER_PORT = 23232;
    static final int DEFAULT_SOLR_STOP_PORT = 34343;
    private int localSolrServerPort = 0;
    private int localSolrStopPort = 0;
    private File localSolrFolder;
    private static final String SOLR = "solr";
    private static final String CORE_PROPERTIES = "core.properties";
    private static final boolean DEBUG = false;//(Version.getBuildType() == Version.Type.DEVELOPMENT);
    private static final int NUM_COLLECTION_CREATION_RETRIES = 5;
    private static final int NUM_EMBEDDED_SERVER_RETRIES = 12;  // attempt to connect to embedded Solr server for 1 minute
    private static final int EMBEDDED_SERVER_RETRY_WAIT_SEC = 5;

    public enum CORE_EVT_STATES {

        STOPPED, STARTED
    };
    
    private enum SOLR_VERSION {

        SOLR8, SOLR4
    };

    // A reference to the locally running Solr instance.
    private HttpSolrClient localSolrServer = null;
    private SOLR_VERSION localServerVersion = SOLR_VERSION.SOLR8; // start local Solr 8 by default

    // A reference to the remote/network running Solr instance.
    private HttpSolrClient remoteSolrServer;

    private Collection currentCollection;
    private final ReentrantReadWriteLock currentCoreLock;

    private final ServerAction serverAction;
    private InputStreamPrinterThread errorRedirectThread;

    /**
     * New instance for the server at the given URL
     *
     */
    Server() {
        initSettings();
        
        localSolrServer = getSolrClient("http://localhost:" + localSolrServerPort + "/solr");

        serverAction = new ServerAction();
        File solr8Folder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false); //NON-NLS
        File solr4Folder = InstalledFileLocator.getDefault().locate("solr4", Server.class.getPackage().getName(), false); //NON-NLS
        
        // Figure out where Java is located. The Java home location 
        // will be passed as the SOLR_JAVA_HOME environment
        // variable to the Solr script but it can be overridden by the user in
        // either autopsy-solr.cmd or autopsy-solr-in.cmd.
        javaPath = PlatformUtil.getJavaPath();

        Path solr8Home = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "solr"); //NON-NLS
        try {
            // Always copy the config files, as they may have changed. Otherwise potentially stale Solr configuration is being used.
            if (!solr8Home.toFile().exists()) {
                Files.createDirectory(solr8Home);
            } else {
                // delete the configsets directory as the Autopsy configset could have changed
                FileUtil.deleteDir(solr8Home.resolve("configsets").toFile());
            }
            Files.copy(Paths.get(solr8Folder.getAbsolutePath(), "server", "solr", "solr.xml"), solr8Home.resolve("solr.xml"), REPLACE_EXISTING); //NON-NLS
            Files.copy(Paths.get(solr8Folder.getAbsolutePath(), "server", "solr", "zoo.cfg"), solr8Home.resolve("zoo.cfg"), REPLACE_EXISTING); //NON-NLS
            FileUtils.copyDirectory(Paths.get(solr8Folder.getAbsolutePath(), "server", "solr", "configsets").toFile(), solr8Home.resolve("configsets").toFile()); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create Solr 8 home folder:", ex); //NON-NLS
        }
        
        Path solr4Home = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "solr4"); //NON-NLS
        try {
            // Always copy the config files, as they may have changed. Otherwise potentially stale Solr configuration is being used.
            if (!solr4Home.toFile().exists()) {
                Files.createDirectory(solr4Home);
            }          
            Files.copy(Paths.get(solr4Folder.getAbsolutePath(), "solr", "solr.xml"), solr4Home.resolve("solr.xml"), REPLACE_EXISTING); //NON-NLS
            Files.copy(Paths.get(solr4Folder.getAbsolutePath(), "solr", "zoo.cfg"), solr4Home.resolve("zoo.cfg"), REPLACE_EXISTING); //NON-NLS
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to create Solr 4 home folder:", ex); //NON-NLS
        }

        currentCoreLock = new ReentrantReadWriteLock(true);

        logger.log(Level.INFO, "Created Server instance using Java at {0}", javaPath); //NON-NLS
    }

    private void initSettings() {

        if (ModuleSettings.settingExists(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT)) {
            try {
                localSolrServerPort = Integer.decode(ModuleSettings.getConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT));
            } catch (NumberFormatException nfe) {
                logger.log(Level.WARNING, "Could not decode indexing server port, value was not a valid port number, using the default. ", nfe); //NON-NLS
                localSolrServerPort = DEFAULT_SOLR_SERVER_PORT;
            }
        } else {
            localSolrServerPort = DEFAULT_SOLR_SERVER_PORT;
            ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT, String.valueOf(localSolrServerPort));
        }

        if (ModuleSettings.settingExists(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT)) {
            try {
                localSolrStopPort = Integer.decode(ModuleSettings.getConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT));
            } catch (NumberFormatException nfe) {
                logger.log(Level.WARNING, "Could not decode indexing server stop port, value was not a valid port number, using default", nfe); //NON-NLS
                localSolrStopPort = DEFAULT_SOLR_STOP_PORT;
            }
        } else {
            localSolrStopPort = DEFAULT_SOLR_STOP_PORT;
            ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT, String.valueOf(localSolrStopPort));
        }
    }
    
    private HttpSolrClient getSolrClient(String solrUrl) {
        int connectionTimeoutMs = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getConnectionTimeout();
        return new HttpSolrClient.Builder(solrUrl)
                .withSocketTimeout(connectionTimeoutMs)
                .withConnectionTimeout(connectionTimeoutMs)
                .withResponseParser(new XMLResponseParser())
                .build();
    }

    private ConcurrentUpdateSolrClient getConcurrentClient(String solrUrl) {
        int numThreads = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getNumThreads();
        int numDocs = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getDocumentsQueueSize();
        int connectionTimeoutMs = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getConnectionTimeout();
        logger.log(Level.INFO, "Creating new ConcurrentUpdateSolrClient: {0}", solrUrl); //NON-NLS
        logger.log(Level.INFO, "Queue size = {0}, Number of threads = {1}, Connection Timeout (ms) = {2}", new Object[]{numDocs, numThreads, connectionTimeoutMs}); //NON-NLS
        ConcurrentUpdateSolrClient client = new ConcurrentUpdateSolrClient.Builder(solrUrl)
                .withQueueSize(numDocs)
                .withThreadCount(numThreads)
                .withSocketTimeout(connectionTimeoutMs)
                .withConnectionTimeout(connectionTimeoutMs)
                .withResponseParser(new XMLResponseParser())
                .build();

        return client;
    }
    
    private CloudSolrClient getCloudSolrClient(String host, String port, String defaultCollectionName) throws KeywordSearchModuleException {
        List<String> solrServerList = getSolrServerList(host, port);
        List<String> solrUrls = new ArrayList<>();
        for (String server : solrServerList) {
            solrUrls.add("http://" + server + "/solr");
            logger.log(Level.INFO, "Using Solr server: {0}", server);
        }
        
        logger.log(Level.INFO, "Creating new CloudSolrClient"); //NON-NLS
        int connectionTimeoutMs = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getConnectionTimeout();
        CloudSolrClient client = new CloudSolrClient.Builder(solrUrls)
                .withConnectionTimeout(connectionTimeoutMs)
                .withSocketTimeout(connectionTimeoutMs)
                .withResponseParser(new XMLResponseParser())
                .build();
        if (!defaultCollectionName.isEmpty()) {
            client.setDefaultCollection(defaultCollectionName);
        }
        client.connect();
        return client;
    }

    @Override
    public void finalize() throws java.lang.Throwable {
        stop();
        super.finalize();
    }

    public void addServerActionListener(PropertyChangeListener l) {
        serverAction.addPropertyChangeListener(l);
    }

    int getLocalSolrServerPort() {
        return localSolrServerPort;
    }

    int getLocalSolrStopPort() {
        return localSolrStopPort;
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
     * Run a Solr command with the given arguments.
     *
     * @param solrArguments Command line arguments to pass to the Solr command.
     *
     * @return
     *
     * @throws IOException
     */
    private Process runLocalSolr8ControlCommand(List<String> solrArguments) throws IOException {
        final String MAX_SOLR_MEM_MB_PAR = "-Xmx" + UserPreferences.getMaxSolrVMSize() + "m"; //NON-NLS
        
        // This is our customized version of the Solr batch script to start/stop Solr.
        File solr8Folder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false); //NON-NLS
        Path solr8CmdPath;
        if(PlatformUtil.isWindowsOS()){
            solr8CmdPath = Paths.get(solr8Folder.getAbsolutePath(), "bin", "autopsy-solr.cmd"); //NON-NLS
        } else {
            solr8CmdPath = Paths.get(solr8Folder.getAbsolutePath(), "bin", "autopsy-solr"); //NON-NLS
        }
        Path solr8Home = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), "solr"); //NON-NLS

        List<String> commandLine = new ArrayList<>();
	commandLine.add(solr8CmdPath.toString());
        commandLine.addAll(solrArguments);

        ProcessBuilder solrProcessBuilder = new ProcessBuilder(commandLine);
        solrProcessBuilder.directory(solr8Folder);

        // Redirect stdout and stderr to files to prevent blocking.
        Path solrStdoutPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stdout"); //NON-NLS
        solrProcessBuilder.redirectOutput(solrStdoutPath.toFile());

        Path solrStderrPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stderr"); //NON-NLS
        solrProcessBuilder.redirectError(solrStderrPath.toFile());
        
        // get the path to the JRE folder. That's what Solr needs as SOLR_JAVA_HOME
        String jreFolderPath = new File(javaPath).getParentFile().getParentFile().getAbsolutePath();
        
        solrProcessBuilder.environment().put("SOLR_JAVA_HOME", jreFolderPath); // NON-NLS
        solrProcessBuilder.environment().put("SOLR_HOME", solr8Home.toString()); // NON-NLS
        solrProcessBuilder.environment().put("STOP_KEY", KEY); // NON-NLS 
        solrProcessBuilder.environment().put("SOLR_JAVA_MEM", MAX_SOLR_MEM_MB_PAR); // NON-NLS 
        logger.log(Level.INFO, "Setting Solr 8 directory: {0}", solr8Folder.toString()); //NON-NLS
        logger.log(Level.INFO, "Running Solr 8 command: {0} from {1}", new Object[]{solrProcessBuilder.command(), solr8Folder.toString()}); //NON-NLS
        Process process = solrProcessBuilder.start();
        logger.log(Level.INFO, "Finished running Solr 8 command"); //NON-NLS
        return process;
    }
    
    /**
     * Run a Solr command with the given arguments.
     *
     * @param solrArguments Command line arguments to pass to the Solr command.
     *
     * @return
     *
     * @throws IOException
     */
    private Process runLocalSolr4ControlCommand(List<String> solrArguments) throws IOException {
        final String MAX_SOLR_MEM_MB_PAR = "-Xmx" + UserPreferences.getMaxSolrVMSize() + "m"; //NON-NLS
        File solr4Folder = InstalledFileLocator.getDefault().locate("solr4", Server.class.getPackage().getName(), false); //NON-NLS

        List<String> commandLine = new ArrayList<>();
        commandLine.add(javaPath);
        commandLine.add(MAX_SOLR_MEM_MB_PAR);
        commandLine.add("-DSTOP.PORT=" + localSolrStopPort); //NON-NLS
        commandLine.add("-Djetty.port=" + localSolrServerPort); //NON-NLS
        commandLine.add("-DSTOP.KEY=" + KEY); //NON-NLS
        commandLine.add("-jar"); //NON-NLS
        commandLine.add("start.jar"); //NON-NLS

        commandLine.addAll(solrArguments);

        ProcessBuilder solrProcessBuilder = new ProcessBuilder(commandLine);
        solrProcessBuilder.directory(solr4Folder);

        // Redirect stdout and stderr to files to prevent blocking.
        Path solrStdoutPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stdout"); //NON-NLS
        solrProcessBuilder.redirectOutput(solrStdoutPath.toFile());

        Path solrStderrPath = Paths.get(Places.getUserDirectory().getAbsolutePath(), "var", "log", "solr.log.stderr"); //NON-NLS
        solrProcessBuilder.redirectError(solrStderrPath.toFile());

        logger.log(Level.INFO, "Running Solr 4 command: {0}", solrProcessBuilder.command()); //NON-NLS
        Process process = solrProcessBuilder.start();
        logger.log(Level.INFO, "Finished running Solr 4 command"); //NON-NLS
        return process;
    }    

    /**
     * Get list of PIDs of currently running Solr processes
     *
     * @return
     */
    List<Long> getSolrPIDs() {
        List<Long> pids = new ArrayList<>();

        //NOTE: these needs to be in sync with process start string in start()
        final String pidsQuery = "-DSTOP.KEY=" + KEY + "%start.jar"; //NON-NLS

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
    
    void start() throws KeywordSearchModuleException, SolrServerNoPortException, SolrServerException {        
        startLocalSolr(SOLR_VERSION.SOLR8);
    }
    
    private void configureSolrConnection(Case theCase, Index index) throws KeywordSearchModuleException, SolrServerNoPortException {
        
        try {
            if (theCase.getCaseType() == CaseType.SINGLE_USER_CASE) {

                // makes sure the proper local Solr server is running
                if (IndexFinder.getCurrentSolrVersion().equals(index.getSolrVersion())) {
                    startLocalSolr(SOLR_VERSION.SOLR8);
                } else {
                    startLocalSolr(SOLR_VERSION.SOLR4);
                }

                // check if the local Solr server is running
                if (!this.isLocalSolrRunning()) {
                    logger.log(Level.SEVERE, "Local Solr server is not running"); //NON-NLS
                    throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.msg")); 
                }
            } else {
                // create SolrJ client to connect to remore Solr server
                remoteSolrServer = configureMultiUserConnection(theCase, index, "");

                // test the connection
                connectToSolrServer(remoteSolrServer);
            }
        } catch (SolrServerException | IOException ex) {
            throw new KeywordSearchModuleException(NbBundle.getMessage(Server.class, "Server.connect.exception.msg", ex.getLocalizedMessage()), ex);
        }
    }
    
    /**
     * Returns a fully configured Solr client to be used for the current case.
     * Checks the version of Solr index for the current case (Solr 4 or 8), gets
     * connection info for the appropriate Solr server, and configures the Solr
     * client.
     *
     * @param theCase Current case
     * @param index   Index object for the current case
     * @param name    Name of the Solr collection
     *
     * @return Fully configured Solr client.
     *
     * @throws KeywordSearchModuleException
     */
    private HttpSolrClient configureMultiUserConnection(Case theCase, Index index, String name) throws KeywordSearchModuleException {

        // read Solr connection info from user preferences, unless "solrserver.txt" is present
        IndexingServerProperties properties = getMultiUserServerProperties(theCase.getCaseDirectory());
        if (properties.host.isEmpty() || properties.port.isEmpty()) {
            throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.connectionInfoMissing.exception.msg", index.getSolrVersion()));
        }
        String solrUrl = "http://" + properties.host + ":" + properties.port + "/solr";

        if (!name.isEmpty()) {
            solrUrl = solrUrl + "/" + name;
        }

        // create SolrJ client to connect to remore Solr server
        return getSolrClient(solrUrl);
    }

    /**
     * Tries to start a local Solr instance in a separate process. Returns
     * immediately (probably before the server is ready) and doesn't check
     * whether it was successful.
     */
    @NbBundle.Messages({
        "Server.status.failed.msg=Local Solr server did not respond to status request. This may be because the server failed to start or is taking too long to initialize.",})
    synchronized void startLocalSolr(SOLR_VERSION version) throws KeywordSearchModuleException, SolrServerNoPortException, SolrServerException {
        
        logger.log(Level.INFO, "Starting local Solr " + version + " server"); //NON-NLS
        if (version == SOLR_VERSION.SOLR8) {
            localSolrFolder = InstalledFileLocator.getDefault().locate("solr", Server.class.getPackage().getName(), false); //NON-NLS
        } else {
            // solr4
            localSolrFolder = InstalledFileLocator.getDefault().locate("solr4", Server.class.getPackage().getName(), false); //NON-NLS
        }

        if (isLocalSolrRunning()) {
            if (localServerVersion.equals(version)) {
                // this version of local server is already running
                logger.log(Level.INFO, "Local Solr " + version + " server is already running"); //NON-NLS
                return;
            } else {
                // wrong version of local server is running, stop it
                stop();
            }
        }
        
        // set which version of local server is currently running
        localServerVersion = version;

        if (!isPortAvailable(localSolrServerPort)) {
            // There is something already listening on our port. Let's see if
            // this is from an earlier run that didn't successfully shut down
            // and if so kill it.
            final List<Long> pids = this.getSolrPIDs();

            // If the culprit listening on the port is not a Solr process
            // we refuse to start.
            if (pids.isEmpty()) {
                throw new SolrServerNoPortException(localSolrServerPort);
            }

            // Ok, we've tried to stop it above but there still appears to be
            // a Solr process listening on our port so we forcefully kill it.
            killSolr();

            // If either of the ports are still in use after our attempt to kill 
            // previously running processes we give up and throw an exception.
            if (!isPortAvailable(localSolrServerPort)) {
                throw new SolrServerNoPortException(localSolrServerPort);
            }
            if (!isPortAvailable(localSolrStopPort)) {
                throw new SolrServerNoPortException(localSolrStopPort);
            }
        }        

        if (isPortAvailable(localSolrServerPort)) {
            logger.log(Level.INFO, "Port [{0}] available, starting Solr", localSolrServerPort); //NON-NLS
            try {
                if (version == SOLR_VERSION.SOLR8) {
                    logger.log(Level.INFO, "Starting Solr 8 server"); //NON-NLS
                    curSolrProcess = runLocalSolr8ControlCommand(new ArrayList<>(Arrays.asList("start", "-p", //NON-NLS
                        Integer.toString(localSolrServerPort)))); //NON-NLS
                } else {
                    // solr4
                    logger.log(Level.INFO, "Starting Solr 4 server"); //NON-NLS
                    curSolrProcess = runLocalSolr4ControlCommand(new ArrayList<>(
                        Arrays.asList("-Dbootstrap_confdir=../solr/configsets/AutopsyConfig/conf", //NON-NLS
                                "-Dcollection.configName=AutopsyConfig"))); //NON-NLS
                }
               
                // Wait for the Solr server to start and respond to a statusRequest request.
                for (int numRetries = 0; numRetries < NUM_EMBEDDED_SERVER_RETRIES; numRetries++) {
                    if (isLocalSolrRunning()) {
                        final List<Long> pids = this.getSolrPIDs();
                        logger.log(Level.INFO, "New Solr process PID: {0}", pids); //NON-NLS
                        return;
                    }

                    // Local Solr server did not respond so we sleep for
                    // 5 seconds before trying again.
                    try {
                        TimeUnit.SECONDS.sleep(EMBEDDED_SERVER_RETRY_WAIT_SEC);
                    } catch (InterruptedException ex) {
                        logger.log(Level.WARNING, "Timer interrupted"); //NON-NLS
                    }
                }

                // If we get here the Solr server has not responded to connection
                // attempts in a timely fashion.
                logger.log(Level.WARNING, "Local Solr server failed to respond to status requests.");
                WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
                    @Override
                    public void run() {
                        MessageNotifyUtil.Notify.error(
                                NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"),
                                Bundle.Server_status_failed_msg());
                    }
                });
            } catch (SecurityException ex) {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg"), ex);
            } catch (IOException ex) {
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.start.exception.cantStartSolr.msg2"), ex);
            }
        }
    }

    /**
     * Checks to see if a specific port is available.
     *
     * @param port The port to check for availability.
     * @return True if the port is available and false if not.
     */
    static boolean isPortAvailable(int port) {
        // implementation taken from https://stackoverflow.com/a/435579
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }

        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
        } finally {
            if (ds != null) {
                ds.close();
            }

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
        localSolrServerPort = port;
        ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_SERVER_PORT, String.valueOf(port));
    }

    /**
     * Changes the current solr stop port. Only call this after available.
     *
     * @param port Port to change to
     */
    void changeSolrStopPort(int port) {
        localSolrStopPort = port;
        ModuleSettings.setConfigSetting(PROPERTIES_FILE, PROPERTIES_CURRENT_STOP_PORT, String.valueOf(port));
    }

    /**
     * Closes current collection and tries to stop the local Solr instance.
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
        
        stopLocalSolr();
    }
    
    /**
     * Stops local Solr server instance.
     */
    private void stopLocalSolr() {
        try {
            //try graceful shutdown
            Process process;
            if (localServerVersion == SOLR_VERSION.SOLR8) {
                logger.log(Level.INFO, "Stopping Solr 8 server"); //NON-NLS
                process = runLocalSolr8ControlCommand(new ArrayList<>(Arrays.asList("stop", "-k", KEY, "-p", Integer.toString(localSolrServerPort)))); //NON-NLS
            } else {
                // solr 4
                logger.log(Level.INFO, "Stopping Solr 4 server"); //NON-NLS
                process = runLocalSolr4ControlCommand(new ArrayList<>(Arrays.asList("--stop"))); //NON-NLS
            }

            logger.log(Level.INFO, "Waiting for Solr server to stop"); //NON-NLS
            process.waitFor();

            //if still running, forcefully stop it
            if (curSolrProcess != null) {
                curSolrProcess.destroy();
                curSolrProcess = null;
            }

        } catch (IOException | InterruptedException ex) {
            logger.log(Level.WARNING, "Error while attempting to stop Solr server", ex);
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
     * Tests if there's a local Solr server running by sending it a core-statusRequest
 request.
     *
     * @return false if the request failed with a connection error, otherwise
     * true
     */
    synchronized boolean isLocalSolrRunning() throws KeywordSearchModuleException {
        try {

            if (isPortAvailable(localSolrServerPort)) {
                return false;
            }

            // making a statusRequest request here instead of just doing solrServer.ping(), because
            // that doesn't work when there are no cores
            //TODO handle timeout in cases when some other type of server on that port
            connectToEmbeddedSolrServer();

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
     * @param index The text index that the Solr core should be using.
     *
     * @throws KeywordSearchModuleException If an error occurs while
     * creating/opening the core.
     */
    void openCoreForCase(Case theCase, Index index) throws KeywordSearchModuleException {
        currentCoreLock.writeLock().lock();
        try {
            currentCollection = openCore(theCase, index);

            try {
                // execute a test query. if it fails, an exception will be thrown
                queryNumIndexedFiles();
            } catch (NoOpenCoreException ex) {
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg"), ex);
            }

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
            return (null != currentCollection);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    Index getIndexInfo() throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.getIndexInfo();
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    void closeCore() throws KeywordSearchModuleException {
        currentCoreLock.writeLock().lock();
        try {
            if (null != currentCollection) {
                currentCollection.close();
                serverAction.putValue(CORE_EVT, CORE_EVT_STATES.STOPPED);
            }
        } finally {
            currentCollection = null;
            currentCoreLock.writeLock().unlock();
        }
    }

    void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Index chunk");
            currentCollection.addDocument(doc);
            HealthMonitor.submitTimingMetric(metric);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * ** end single-case specific methods ***
     */
    /**
     * Deletes the keyword search collection for a case.
     *
     * @param coreName The core name.
     */
    @NbBundle.Messages({
        "# {0} - colelction name", "Server.deleteCore.exception.msg=Failed to delete Solr colelction {0}",})
    void deleteCollection(String coreName, CaseMetadata metadata) throws KeywordSearchServiceException, KeywordSearchModuleException {
        try {
            HttpSolrClient solrServer;
            if (metadata.getCaseType() == CaseType.SINGLE_USER_CASE) {
                solrServer = getSolrClient("http://localhost:" + localSolrServerPort + "/solr"); //NON-NLS
                CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, solrServer);
                if (null != response.getCoreStatus(coreName).get("instanceDir")) {             //NON-NLS
                    /*
                     * Send a core unload request to the Solr server, with the
                     * parameter set that request deleting the index and the
                     * instance directory (deleteInstanceDir = true). Note that
                     * this removes everything related to the core on the server
                     * (the index directory, the configuration files, etc.), but
                     * does not delete the actual Solr text index because it is
                     * currently stored in the case directory.
                     */
                    org.apache.solr.client.solrj.request.CoreAdminRequest.unloadCore(coreName, true, true, solrServer);
                }
            } else {
                IndexingServerProperties properties = getMultiUserServerProperties(metadata.getCaseDirectory());
                solrServer = getSolrClient("http://" + properties.getHost() + ":" + properties.getPort() + "/solr");
                connectToSolrServer(solrServer);

                CollectionAdminRequest.Delete deleteCollectionRequest = CollectionAdminRequest.deleteCollection(coreName);
                CollectionAdminResponse response = deleteCollectionRequest.process(solrServer);
                if (response.isSuccess()) {
                    logger.log(Level.INFO, "Deleted collection {0}", coreName); //NON-NLS
                } else {
                    logger.log(Level.WARNING, "Unable to delete collection {0}", coreName); //NON-NLS
                }
            }
        } catch (SolrServerException | IOException ex) {
            // We will get a RemoteSolrException with cause == null and detailsMessage
            // == "Already closed" if the core is not loaded. This is not an error in this scenario.
            if (!ex.getMessage().equals("Already closed")) { // NON-NLS
                throw new KeywordSearchServiceException(Bundle.Server_deleteCore_exception_msg(coreName), ex);
            }
        }
    }

    /**
     * Creates/opens a Solr core (index) for a case.
     *
     * @param theCase The case for which the core is to be created/opened.
     * @param index The text index that the Solr core should be using.
     *
     * @return An object representing the created/opened core.
     *
     * @throws KeywordSearchModuleException If an error occurs while
     * creating/opening the core.
     */
    @NbBundle.Messages({
        "Server.exceptionMessage.unableToCreateCollection=Unable to create Solr collection",
        "Server.exceptionMessage.unableToBackupCollection=Unable to backup Solr collection",
        "Server.exceptionMessage.unableToRestoreCollection=Unable to restore Solr collection",
    })
    private Collection openCore(Case theCase, Index index) throws KeywordSearchModuleException {

        int numShardsToUse = 1;
        try {
            // connect to proper Solr server
            configureSolrConnection(theCase, index);

            if (theCase.getCaseType() == CaseType.MULTI_USER_CASE) {
                // select number of shards to use
                numShardsToUse = getNumShardsToUse();
            }
        } catch (Exception ex) {
            // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
            throw new KeywordSearchModuleException(NbBundle.getMessage(Server.class, "Server.connect.exception.msg", ex.getLocalizedMessage()), ex);
        }

        try {
            String collectionName = index.getIndexName();
            
            if (theCase.getCaseType() == CaseType.MULTI_USER_CASE) {
                if (!collectionExists(collectionName)) {
                    /*
                    * The collection does not exist. Make a request that will cause the colelction to be created.
                    */
                    boolean doRetry = false;
                    for (int reTryAttempt = 0; reTryAttempt < NUM_COLLECTION_CREATION_RETRIES; reTryAttempt++) {
                        try {
                            doRetry = false;
                            createMultiUserCollection(collectionName, numShardsToUse);
                        } catch (Exception ex) {
                            if (reTryAttempt >= NUM_COLLECTION_CREATION_RETRIES) {
                                logger.log(Level.SEVERE, "Unable to create Solr collection " + collectionName, ex); //NON-NLS
                                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg"), ex);
                            } else {
                                logger.log(Level.SEVERE, "Unable to create Solr collection " + collectionName + ". Re-trying...", ex); //NON-NLS
                                Thread.sleep(1000L);
                                doRetry = true;
                            }
                        }
                        if (!doRetry) {
                            break;
                        }
                    }
                }
            } else {
                if (!coreIsLoaded(collectionName)) {
                    // In single user mode, the index is stored in case output directory
                    File dataDir = new File(new File(index.getIndexPath()).getParent()); // "data dir" is the parent of the index directory
                    if (!dataDir.exists()) {
                        dataDir.mkdirs();
                    }
                    
                    // In single user mode, if there is a core.properties file already,
                    // we've hit a solr bug. Compensate by deleting it.
                    if (theCase.getCaseType() == CaseType.SINGLE_USER_CASE) {
                        Path corePropertiesFile = Paths.get(localSolrFolder.toString(), SOLR, collectionName, CORE_PROPERTIES);
                        if (corePropertiesFile.toFile().exists()) {
                            try {
                                corePropertiesFile.toFile().delete();
                            } catch (Exception ex) {
                                logger.log(Level.INFO, "Could not delete pre-existing core.properties prior to opening the core."); //NON-NLS
                            }
                        }
                    }

                    // for single user cases, we unload the core when we close the case. So we have to load the core again. 
                    CoreAdminRequest.Create createCoreRequest = new CoreAdminRequest.Create();
                    createCoreRequest.setDataDir(dataDir.getAbsolutePath());
                    createCoreRequest.setCoreName(collectionName);
                    createCoreRequest.setConfigSet("AutopsyConfig"); //NON-NLS
                    createCoreRequest.setIsLoadOnStartup(false);
                    createCoreRequest.setIsTransient(true);
                    localSolrServer.request(createCoreRequest);

                    if (!coreIndexFolderExists(collectionName)) {
                        throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.noIndexDir.msg"));
                    }
                }
            }

            return new Collection(collectionName, theCase, index);

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Exception during Solr collection creation.", ex); //NON-NLS
            throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.cantOpen.msg"), ex);
        }
    }
    
    private int getNumShardsToUse() throws KeywordSearchModuleException {

        // if we want to use a specific sharding strategy, use that
        if (org.sleuthkit.autopsy.keywordsearch.UserPreferences.getMaxNumShards() > 0) {
            return org.sleuthkit.autopsy.keywordsearch.UserPreferences.getMaxNumShards();
        }

        // otherwise get list of all live Solr servers in the cluster
        List<String> solrServerList = getSolrServerList(remoteSolrServer);
        // shard across all available servers
        return solrServerList.size();
    }

    /*
     * Poll the remote Solr server for list of existing collections, and check if 
     * the collection of interest exists.
     * 
     * @param collectionName The name of the collection.
     *
     * @return True if the collection exists, false otherwise.
     *
     * @throws SolrServerException If there is a problem communicating with the
     * Solr server.
     * @throws IOException If there is a problem communicating with the Solr
     * server.
     */
    private boolean collectionExists(String collectionName) throws SolrServerException, IOException {
        CollectionAdminRequest.List req = new CollectionAdminRequest.List();
        CollectionAdminResponse response = req.process(remoteSolrServer);
        List<?> existingCollections = (List<?>) response.getResponse().get("collections");
        if (existingCollections == null) {
            existingCollections = new ArrayList<>();
        }
        return existingCollections.contains(collectionName);
    }

    /* NOTE: Keeping this code for reference, since it works.
    private boolean collectionExists(String collectionName) throws SolrServerException, IOException {

        // TODO we could potentially use this API. Currently set exception "Solr instance is not running in SolrCloud mode"
        // List<String> list = CollectionAdminRequest.listCollections(localSolrServer);
        
        CollectionAdminRequest.ClusterStatus statusRequest = CollectionAdminRequest.getClusterStatus().setCollectionName(collectionName);
        CollectionAdminResponse statusResponse;
        try {
            statusResponse = statusRequest.process(remoteSolrServer);
        } catch (RemoteSolrException ex) {
            // collection doesn't exist
            return false;
        }
        
        if (statusResponse == null) {
            return false;
        }
        
        NamedList error = (NamedList) statusResponse.getResponse().get("error");
        if (error != null) {
            return false;
        }        
        
        // For some reason this returns info about all collections even though it's supposed to only return about the one we are requesting
        NamedList cluster = (NamedList) statusResponse.getResponse().get("cluster");
        NamedList collections = (NamedList) cluster.get("collections");
        if (collections != null) {
            Object collection = collections.get(collectionName);
            return (collection != null);
        } else {
            return false;
        }
    }*/
    
    private void createMultiUserCollection(String collectionName, int numShardsToUse) throws KeywordSearchModuleException, SolrServerException, IOException {
        /*
        * The core either does not exist or it is not loaded. Make a
        * request that will cause the core to be created if it does not
        * exist or loaded if it already exists.
        */

        Integer numShards = numShardsToUse;
        logger.log(Level.INFO, "numShardsToUse: {0}", numShardsToUse);
        Integer numNrtReplicas = 1;
        Integer numTlogReplicas = 0;
        Integer numPullReplicas = 0;
        CollectionAdminRequest.Create createCollectionRequest = CollectionAdminRequest.createCollection(collectionName, "AutopsyConfig", numShards, numNrtReplicas, numTlogReplicas, numPullReplicas);

        CollectionAdminResponse createResponse = createCollectionRequest.process(remoteSolrServer);
        if (createResponse.isSuccess()) {
            logger.log(Level.INFO, "Collection {0} successfully created.", collectionName);
        } else {
            logger.log(Level.SEVERE, "Unable to create Solr collection {0}", collectionName); //NON-NLS
            throw new KeywordSearchModuleException(Bundle.Server_exceptionMessage_unableToCreateCollection());
        }

        /* If we need core name:
        Map<String, NamedList<Integer>> status = createResponse.getCollectionCoresStatus();
        existingCoreName = status.keySet().iterator().next();*/
        if (!collectionExists(collectionName)) {
            throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.openCore.exception.noIndexDir.msg"));
        }
    }
    
    private void backupCollection(String collectionName, String backupName, String pathToBackupLocation) throws SolrServerException, IOException, KeywordSearchModuleException {
        CollectionAdminRequest.Backup backup = CollectionAdminRequest.backupCollection(collectionName, backupName)
                .setLocation(pathToBackupLocation);
        
        CollectionAdminResponse backupResponse = backup.process(remoteSolrServer);
        if (backupResponse.isSuccess()) {
            logger.log(Level.INFO, "Collection {0} successfully backep up.", collectionName);
        } else {
            logger.log(Level.SEVERE, "Unable to back up Solr collection {0}", collectionName); //NON-NLS
            throw new KeywordSearchModuleException(Bundle.Server_exceptionMessage_unableToBackupCollection());
        }
    }
    
    private void restoreCollection(String backupName, String restoreCollectionName, String pathToBackupLocation) throws SolrServerException, IOException, KeywordSearchModuleException {
        
        CollectionAdminRequest.Restore restore = CollectionAdminRequest.restoreCollection(restoreCollectionName, backupName)
                .setLocation(pathToBackupLocation);
        
        CollectionAdminResponse restoreResponse = restore.process(remoteSolrServer);
        if (restoreResponse.isSuccess()) {
            logger.log(Level.INFO, "Collection {0} successfully resored.", restoreCollectionName);
        } else {
            logger.log(Level.SEVERE, "Unable to restore Solr collection {0}", restoreCollectionName); //NON-NLS
            throw new KeywordSearchModuleException(Bundle.Server_exceptionMessage_unableToRestoreCollection());
        }
    }
    
    /**
     * Determines whether or not a particular Solr core exists and is loaded.  
     * This is used only with embedded Solr server running in non-cloud mode.
     *
     * @param coreName The name of the core.
     *
     * @return True if the core exists and is loaded, false if the core does not
     * exist or is not loaded
     *
     * @throws SolrServerException If there is a problem communicating with the
     * Solr server.
     * @throws IOException If there is a problem communicating with the Solr
     * server.
     */
    private boolean coreIsLoaded(String coreName) throws SolrServerException, IOException {
        CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, localSolrServer);
        return response.getCoreStatus(coreName).get("instanceDir") != null; //NON-NLS
    }
    
    /**
     * Determines whether or not the index files folder for a Solr core
     * exists. This is used only with embedded Solr server running in non-cloud
     * mode.
     *
     * @param coreName the name of the core.
     *
     * @return true or false
     *
     * @throws SolrServerException
     * @throws IOException
     */
    private boolean coreIndexFolderExists(String coreName) throws SolrServerException, IOException {
        CoreAdminResponse response = CoreAdminRequest.getStatus(coreName, localSolrServer);
        Object dataDirPath = response.getCoreStatus(coreName).get("dataDir"); //NON-NLS
        if (null != dataDirPath) {
            File indexDir = Paths.get((String) dataDirPath, "index").toFile();  //NON-NLS
            return indexDir.exists();
        } else {
            return false;
        }
    }    

    /**
     * Get the host and port for a multi-user case. If the file solrserver.txt
     * exists, then use the values from that file. Otherwise use the settings
     * from the properties file, depending on which version of Solr was used to 
     * create the current index. Defaults to using latest Solr version info if
     * an error occurred.
     *
     * @param caseDirectory Current case directory
     * @return IndexingServerProperties containing the solr host/port for this
     * case
     */
    public static IndexingServerProperties getMultiUserServerProperties(String caseDirectory) {

        // if "solrserver.txt" is present, use those connection settings
        Path serverFilePath = Paths.get(caseDirectory, "solrserver.txt"); //NON-NLS
        if (serverFilePath.toFile().exists()) {
            try {
                List<String> lines = Files.readAllLines(serverFilePath);
                if (lines.isEmpty()) {
                    logger.log(Level.SEVERE, "solrserver.txt file does not contain any data"); //NON-NLS
                } else if (!lines.get(0).contains(",")) {
                    logger.log(Level.SEVERE, "solrserver.txt file is corrupt - could not read host/port from " + lines.get(0)); //NON-NLS
                } else {
                    String[] parts = lines.get(0).split(",");
                    if (parts.length != 2) {
                        logger.log(Level.SEVERE, "solrserver.txt file is corrupt - could not read host/port from " + lines.get(0)); //NON-NLS
                    } else {
                        return new IndexingServerProperties(parts[0], parts[1]);
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, "solrserver.txt file could not be read", ex); //NON-NLS
            }
        }
        
        // otherwise (or if an error occurred) determine Solr version of the current case
        List<Index> indexes = new ArrayList<>();
        try {
            IndexMetadata indexMetadata = new IndexMetadata(caseDirectory);
            indexes = indexMetadata.getIndexes();
        } catch (IndexMetadata.TextIndexMetadataException ex) {
            logger.log(Level.SEVERE, "Unable to read text index metadata file: " + caseDirectory, ex);
            
            // default to using the latest Solr version settings
            String host = UserPreferences.getIndexingServerHost();
            String port = UserPreferences.getIndexingServerPort();
            return new IndexingServerProperties(host, port);
        }

        // select which index to use. In practice, all cases always have only one
        // index but there is support for having multiple indexes.
        Index indexToUse = IndexFinder.identifyIndexToUse(indexes);
        if (indexToUse == null) {
            // unable to find index that can be used
            logger.log(Level.SEVERE, "Unable to find index that can be used for case: {0}", caseDirectory);
            
            // default to using the latest Solr version settings
            String host = UserPreferences.getIndexingServerHost();
            String port = UserPreferences.getIndexingServerPort();
            return new IndexingServerProperties(host, port);
        }

        // return connection info for the Solr version of the current index
        if (IndexFinder.getCurrentSolrVersion().equals(indexToUse.getSolrVersion())) {
            // Solr 8
            String host = UserPreferences.getIndexingServerHost();
            String port = UserPreferences.getIndexingServerPort();
            return new IndexingServerProperties(host, port);
        } else {
            // Solr 4
            String host = UserPreferences.getSolr4ServerHost().trim();
            String port = UserPreferences.getSolr4ServerPort().trim();
            return new IndexingServerProperties(host, port);
        }
    }

    /**
     * Pick a solr server to use for this case and record it in the case
     * directory. Looks for a file named "solrServerList.txt" in the root output
     * directory - if this does not exist then no server is recorded.
     *
     * Format of solrServerList.txt: (host),(port) Ex: 10.1.2.34,8983
     *
     * @param rootOutputDirectory
     * @param caseDirectoryPath
     * @throws KeywordSearchModuleException
     */
    public static void selectSolrServerForCase(Path rootOutputDirectory, Path caseDirectoryPath) throws KeywordSearchModuleException {
        // Look for the solr server list file
        String serverListName = "solrServerList.txt"; //NON-NLS
        Path serverListPath = Paths.get(rootOutputDirectory.toString(), serverListName);
        if (serverListPath.toFile().exists()) {

            // Read the list of solr servers
            List<String> lines;
            try {
                lines = Files.readAllLines(serverListPath);
            } catch (IOException ex) {
                throw new KeywordSearchModuleException(serverListName + " could not be read", ex); //NON-NLS
            }

            // Remove any lines that don't contain a comma (these are likely just whitespace)
            for (Iterator<String> iterator = lines.iterator(); iterator.hasNext();) {
                String line = iterator.next();
                if (!line.contains(",")) {
                    // Remove the current element from the iterator and the list.
                    iterator.remove();
                }
            }
            if (lines.isEmpty()) {
                throw new KeywordSearchModuleException(serverListName + " had no valid server information"); //NON-NLS
            }

            // Choose which server to use
            int rnd = new Random().nextInt(lines.size());
            String[] parts = lines.get(rnd).split(",");
            if (parts.length != 2) {
                throw new KeywordSearchModuleException("Invalid server data: " + lines.get(rnd)); //NON-NLS
            }

            // Split it up just to do a sanity check on the data
            String host = parts[0];
            String port = parts[1];
            if (host.isEmpty() || port.isEmpty()) {
                throw new KeywordSearchModuleException("Invalid server data: " + lines.get(rnd)); //NON-NLS
            }

            // Write the server data to a file
            Path serverFile = Paths.get(caseDirectoryPath.toString(), "solrserver.txt"); //NON-NLS
            try {
                caseDirectoryPath.toFile().mkdirs();
                if (!caseDirectoryPath.toFile().exists()) {
                    throw new KeywordSearchModuleException("Case directory " + caseDirectoryPath.toString() + " does not exist"); //NON-NLS
                }
                Files.write(serverFile, lines.get(rnd).getBytes());
            } catch (IOException ex) {
                throw new KeywordSearchModuleException(serverFile.toString() + " could not be written", ex); //NON-NLS
            }
        }
    }
    
    /**
     * Helper class to store the current server properties
     */
    public static class IndexingServerProperties {

        private final String host;
        private final String port;

        IndexingServerProperties(String host, String port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Get the host
         *
         * @return host
         */
        public String getHost() {
            return host;
        }

        /**
         * Get the port
         *
         * @return port
         */
        public String getPort() {
            return port;
        }
    }

    /**
     * Commits current core if it exists
     *
     * @throws SolrServerException, NoOpenCoreException
     */
    void commit() throws SolrServerException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            currentCollection.commit();
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    NamedList<Object> request(SolrRequest<?> request) throws SolrServerException, RemoteSolrException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.request(request);
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryNumIndexedFiles();
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryNumIndexedChunks();
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryNumIndexedDocuments();
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryIsIndexed(contentID);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
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
     * chunks
     *
     * @throws KeywordSearchModuleException
     * @throws NoOpenCoreException
     */
    public int queryNumFileChunks(long fileID) throws KeywordSearchModuleException, NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryNumFileChunks(fileID);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
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
    public QueryResponse query(SolrQuery sq) throws KeywordSearchModuleException, NoOpenCoreException, IOException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.query(sq);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.SEVERE, "Solr query failed: " + sq.getQuery(), ex); //NON-NLS
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.query.exception.msg", sq.getQuery()), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Execute solr query
     *
     * @param sq the query
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.query(sq, method);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.SEVERE, "Solr query failed: " + sq.getQuery(), ex); //NON-NLS
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                return currentCollection.queryTerms(sq);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.SEVERE, "Solr terms query failed: " + sq.getQuery(), ex); //NON-NLS
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.queryTerms.exception.msg", sq.getQuery()), ex);
            }
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Delete a data source from SOLR.
     *
     * @param dataSourceId to delete
     *
     * @throws NoOpenCoreException
     */
    void deleteDataSource(Long dataSourceId) throws IOException, KeywordSearchModuleException, NoOpenCoreException, SolrServerException {
        try {
            currentCoreLock.writeLock().lock();
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            currentCollection.deleteDataSource(dataSourceId);
            currentCollection.commit();
        } finally {
            currentCoreLock.writeLock().unlock();
        }
    }
        
    /**
     * Extract all unique terms/words from current index.
     *
     * @param outputFile Absolute path to the output file
     * @param progressPanel ReportProgressPanel to update
     *
     * @throws NoOpenCoreException
     */
    @NbBundle.Messages({
        "Server.getAllTerms.error=Extraction of all unique Solr terms failed:"})
    void extractAllTermsForDataSource(Path outputFile, ReportProgressPanel progressPanel) throws KeywordSearchModuleException, NoOpenCoreException {
        try {
            currentCoreLock.writeLock().lock();
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            try {
                currentCollection.extractAllTermsForDataSource(outputFile, progressPanel);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.SEVERE, "Extraction of all unique Solr terms failed: ", ex); //NON-NLS
                throw new KeywordSearchModuleException(Bundle.Server_getAllTerms_error(), ex);
            }
        } finally {
            currentCoreLock.writeLock().unlock();
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.getSolrContent(content.getId(), 0);
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
     * chunks for that content
     *
     * @return content text string or null if error quering
     *
     * @throws NoOpenCoreException
     */
    public String getSolrContent(final Content content, int chunkID) throws NoOpenCoreException {
        currentCoreLock.readLock().lock();
        try {
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.getSolrContent(content.getId(), chunkID);
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.getSolrContent(objectID, 0);
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
            if (null == currentCollection) {
                throw new NoOpenCoreException();
            }
            return currentCollection.getSolrContent(objectID, chunkID);
        } finally {
            currentCoreLock.readLock().unlock();
        }
    }

    /**
     * Given file parent id and child chunk ID, return the ID string of the
     * chunk as stored in Solr, e.g. FILEID_CHUNKID
     *
     * @param parentID the parent file id (id of the source content)
     * @param childID the child chunk id
     *
     * @return formatted string id
     */
    public static String getChunkIdString(long parentID, int childID) {
        return Long.toString(parentID) + Server.CHUNK_ID_SEPARATOR + Integer.toString(childID);
    }
    
    /**
     * Attempts to connect to the local Solr server, which is NOT running in SolrCloud mode.
     *
     * @throws SolrServerException
     * @throws IOException
     */
    private void connectToEmbeddedSolrServer() throws SolrServerException, IOException {
        TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Connectivity check");
        CoreAdminRequest.getStatus(null, localSolrServer);
        HealthMonitor.submitTimingMetric(metric);
    }
    
    /**
     * Attempts to connect to the given Solr server, which is running in
     * SoulrCloud mode. This API does not work for the local Solr which is NOT
     * running in SolrCloud mode.
     *
     * @param host Host name of the remote Solr server
     * @param port Port of the remote Solr server
     *
     * @throws SolrServerException
     * @throws IOException
     */
    void connectToSolrServer(String host, String port) throws SolrServerException, IOException {
        try (HttpSolrClient solrServer = getSolrClient("http://" + host + ":" + port + "/solr")) {
            connectToSolrServer(solrServer);
        }
    }

    /**
     * Attempts to connect to the given Solr server, which is running in SoulrCloud mode. This API does not work
     * for the local Solr which is NOT running in SolrCloud mode.
     *
     * @param solrServer
     *
     * @throws SolrServerException
     * @throws IOException
     */
    private void connectToSolrServer(HttpSolrClient solrServer) throws SolrServerException, IOException {
        TimingMetric metric = HealthMonitor.getTimingMetric("Solr: Connectivity check");
        CollectionAdminRequest.ClusterStatus statusRequest = CollectionAdminRequest.getClusterStatus();
        CollectionAdminResponse statusResponse = statusRequest.process(solrServer);
        int statusCode = Integer.valueOf(((NamedList) statusResponse.getResponse().get("responseHeader")).get("status").toString());
        if (statusCode != 0) {
            logger.log(Level.WARNING, "Could not connect to Solr server "); //NON-NLS
        } else {
            logger.log(Level.INFO, "Connected to Solr server "); //NON-NLS
        }
        HealthMonitor.submitTimingMetric(metric);
    }
    
    private List<String> getSolrServerList(String host, String port) throws KeywordSearchModuleException {
        HttpSolrClient solrServer = getSolrClient("http://" + host + ":" + port + "/solr");
        return getSolrServerList(solrServer);
    }
    
    private List<String> getSolrServerList(HttpSolrClient solrServer) throws KeywordSearchModuleException {
        
        try {
            CollectionAdminRequest.ClusterStatus statusRequest = CollectionAdminRequest.getClusterStatus();
            CollectionAdminResponse statusResponse;
            try {
                statusResponse = statusRequest.process(solrServer);
            } catch (RemoteSolrException ex) {
                // collection doesn't exist
                return Collections.emptyList();
            }

            if (statusResponse == null) {
                return Collections.emptyList();
            }

            NamedList<?> error = (NamedList) statusResponse.getResponse().get("error");
            if (error != null) {
                return Collections.emptyList();
            }

            NamedList<?> cluster = (NamedList) statusResponse.getResponse().get("cluster");
            @SuppressWarnings("unchecked")
            List<String> liveNodes = (ArrayList) cluster.get("live_nodes");
            
            if (liveNodes != null) {
                liveNodes = liveNodes.stream()
                        .map(serverStr -> serverStr.endsWith("_solr") 
                                ? serverStr.substring(0, serverStr.length() - "_solr".length())
                                : serverStr)
                        .collect(Collectors.toList());
            }
            return liveNodes;
        } catch (Exception ex) {
            // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
            throw new KeywordSearchModuleException(
                    NbBundle.getMessage(this.getClass(), "Server.serverList.exception.msg", solrServer.getBaseURL()));
        }
    }       

    class Collection {

        // handle to the collection in Solr
        private final String name;

        private final CaseType caseType;

        private final Index textIndex;

        // We use different Solr clients for different operations. HttpSolrClient is geared towards query performance.
        // ConcurrentUpdateSolrClient is geared towards batching solr documents for better indexing throughput. We
        // have implemented our own batching algorithm so we will probably not use ConcurrentUpdateSolrClient.
        // CloudSolrClient is geared towards SolrCloud deployments. These are only good for collection-specific operations.
        private HttpSolrClient queryClient;        
        private SolrClient indexingClient;
        
        private final int maxBufferSize;
        private final List<SolrInputDocument> buffer;
        private final Object bufferLock;
        
        /* (JIRA-7521) Sometimes we get into a situation where Solr server is no longer able to index new data. 
        * Typically main reason for this is Solr running out of memory. In this case we will stop trying to send new 
        * data to Solr (for this collection) after certain number of consecutive batches have failed. */
        private static final int MAX_NUM_CONSECUTIVE_FAILURES = 5;
        private AtomicInteger numConsecutiveFailures = new AtomicInteger(0);
        private AtomicBoolean skipIndexing = new AtomicBoolean(false);
        
        private final ScheduledThreadPoolExecutor periodicTasksExecutor;
        private static final long PERIODIC_BATCH_SEND_INTERVAL_MINUTES = 10;
        private static final int NUM_BATCH_UPDATE_RETRIES = 10;
        private static final long SLEEP_BETWEEN_RETRIES_MS = 10000; // 10 seconds

        private Collection(String name, Case theCase, Index index) throws TimeoutException, InterruptedException, KeywordSearchModuleException {
            this.name = name;
            this.caseType = theCase.getCaseType();
            this.textIndex = index;
            bufferLock = new Object();
            
            if (caseType == CaseType.SINGLE_USER_CASE) {
                // get SolrJ client
                queryClient = getSolrClient("http://localhost:" + localSolrServerPort + "/solr/" + name); // HttpClient
                indexingClient = getSolrClient("http://localhost:" + localSolrServerPort + "/solr/" + name); // HttpClient
            } else {
                // read Solr connection info from user preferences, unless "solrserver.txt" is present
                queryClient = configureMultiUserConnection(theCase, index, name);
                
                // for MU cases, use CloudSolrClient for indexing. Indexing is only supported for Solr 8.
                if (IndexFinder.getCurrentSolrVersion().equals(index.getSolrVersion())) {
                    IndexingServerProperties properties = getMultiUserServerProperties(theCase.getCaseDirectory());
                    indexingClient = getCloudSolrClient(properties.getHost(), properties.getPort(), name); // CloudClient
                } else {
                    indexingClient = configureMultiUserConnection(theCase, index, name); // HttpClient
                }
            }
            
            // document batching
            maxBufferSize = org.sleuthkit.autopsy.keywordsearch.UserPreferences.getDocumentsQueueSize();
            logger.log(Level.INFO, "Using Solr document queue size = {0}", maxBufferSize); //NON-NLS
            buffer = new ArrayList<>(maxBufferSize);
            periodicTasksExecutor = new ScheduledThreadPoolExecutor(1, new ThreadFactoryBuilder().setNameFormat("periodic-batched-document-task-%d").build()); //NON-NLS
            periodicTasksExecutor.scheduleWithFixedDelay(new SendBatchedDocumentsTask(), PERIODIC_BATCH_SEND_INTERVAL_MINUTES, PERIODIC_BATCH_SEND_INTERVAL_MINUTES, TimeUnit.MINUTES);
        }

        /**
         * A task that periodically sends batched documents to Solr. Batched documents
         * get sent automatically as soon as the batching buffer is gets full. However,
         * if the buffer is not full, we want to periodically send the batched documents
         * so that users are able to see them in their keyword searches.
         */
        private final class SendBatchedDocumentsTask implements Runnable {

            @Override
            public void run() {
                
                if (skipIndexing.get()) {
                    return;
                }
                
                List<SolrInputDocument> clone;
                synchronized (bufferLock) {
                    
                    if (buffer.isEmpty()) {
                        return;
                    }
                    
                    // Buffer is full. Make a clone and release the lock, so that we don't
                    // hold other ingest threads
                    clone = buffer.stream().collect(toList());
                    buffer.clear();
                }

                try {
                    // send the cloned list to Solr
                    sendBufferedDocs(clone);
                } catch (KeywordSearchModuleException ex) {
                    logger.log(Level.SEVERE, "Periodic  batched document update failed", ex); //NON-NLS
                }
            }
        }    

        /**
         * Get the name of the collection
         *
         * @return the String name of the collection
         */
        String getName() {
            return name;
        }

        private Index getIndexInfo() {
            return this.textIndex;
        }

        private QueryResponse query(SolrQuery sq) throws SolrServerException, IOException {
            return queryClient.query(sq);
        }

        private NamedList<Object> request(SolrRequest<?> request) throws SolrServerException, RemoteSolrException {
            try {
                return queryClient.request(request);
            } catch (Exception e) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.WARNING, "Could not issue Solr request. ", e); //NON-NLS
                throw new SolrServerException(
                        NbBundle.getMessage(this.getClass(), "Server.request.exception.exception.msg"), e);
            }

        }

        private QueryResponse query(SolrQuery sq, SolrRequest.METHOD method) throws SolrServerException, IOException {
            return queryClient.query(sq, method);
        }

        private TermsResponse queryTerms(SolrQuery sq) throws SolrServerException, IOException {
            QueryResponse qres = queryClient.query(sq);
            return qres.getTermsResponse();
        }

        private void commit() throws SolrServerException {
            List<SolrInputDocument> clone;
            synchronized (bufferLock) {
                // Make a clone and release the lock, so that we don't
                // hold other ingest threads
                clone = buffer.stream().collect(toList());
                buffer.clear();
            }

            try {
                sendBufferedDocs(clone);
            } catch (KeywordSearchModuleException ex) {
                throw new SolrServerException(NbBundle.getMessage(this.getClass(), "Server.commit.exception.msg"), ex);
            }

            try {
                //commit and block
                indexingClient.commit(true, true);
            } catch (Exception e) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.WARNING, "Could not commit index. ", e); //NON-NLS
                throw new SolrServerException(NbBundle.getMessage(this.getClass(), "Server.commit.exception.msg"), e);
            }
        }

        private void deleteDataSource(Long dsObjId) throws IOException, SolrServerException {
            String dataSourceId = Long.toString(dsObjId);
            String deleteQuery = "image_id:" + dataSourceId;

            queryClient.deleteByQuery(deleteQuery);
        }
        
        /**
         * Extract all unique terms/words from current index. Gets 1,000 terms at a time and
         * writes them to output file. Updates ReportProgressPanel status.
         * 
         * @param outputFile Absolute path to the output file
         * @param progressPanel ReportProgressPanel to update
         * @throws IOException 
         * @throws SolrServerException
         * @throws NoCurrentCaseException
         * @throws KeywordSearchModuleException 
         */
        @NbBundle.Messages({
            "# {0} - Number of extracted terms",
            "ExtractAllTermsReport.numberExtractedTerms=Extracted {0} terms..."
        })
        private void extractAllTermsForDataSource(Path outputFile, ReportProgressPanel progressPanel) throws IOException, SolrServerException, NoCurrentCaseException, KeywordSearchModuleException {
            
            Files.deleteIfExists(outputFile);
            OpenOption[] options = new OpenOption[] { java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND };
            
            // step through the terms 
            int termStep = 1000;
            long numExtractedTerms = 0;
            String firstTerm = "";
            while (true) {
                SolrQuery query = new SolrQuery();
                query.setRequestHandler("/terms");
                query.setTerms(true);
                query.setTermsLimit(termStep);
                query.setTermsLower(firstTerm);
                query.setTermsLowerInclusive(false);
                
                // Returned terms sorted by "index" order, which is the fastest way. Per Solr documentation:
                // "Retrieving terms in index order is very fast since the implementation directly uses Lucene’s TermEnum to iterate over the term dictionary."
                // All other sort criteria return very inconsistent and overlapping resuts.
                query.setTermsSortString("index");
                
                // "text" field is the schema field that we populate with (lowercased) terms
                query.addTermsField(Server.Schema.TEXT.toString());
                query.setTermsMinCount(0);

                // Unfortunatelly Solr "terms queries" do not support any filtering so we can't filter by data source this way.
                // query.addFilterQuery(Server.Schema.IMAGE_ID.toString() + ":" + dataSourceId);

                QueryRequest request = new QueryRequest(query);
                TermsResponse response = request.process(queryClient).getTermsResponse();
                List<Term> terms = response.getTerms(Server.Schema.TEXT.toString());

                if (terms == null || terms.isEmpty()) {
                    numExtractedTerms += terms.size();
                    progressPanel.updateStatusLabel(Bundle.ExtractAllTermsReport_numberExtractedTerms(numExtractedTerms));
                    break;
                }
                
                // set the first term for the next query
                firstTerm = terms.get(terms.size()-1).getTerm();

                List<String> listTerms = terms.stream().map(Term::getTerm).collect(Collectors.toList());
                Files.write(outputFile, listTerms, options);
                
                numExtractedTerms += termStep;
                progressPanel.updateStatusLabel(Bundle.ExtractAllTermsReport_numberExtractedTerms(numExtractedTerms));
            }
        }

        /**
         * Add a Solr document for indexing. Documents get batched instead of
         * being immediately sent to Solr (unless batch size = 1).
         *
         * @param doc Solr document to be indexed.
         *
         * @throws KeywordSearchModuleException
         */
        void addDocument(SolrInputDocument doc) throws KeywordSearchModuleException {
            
            if (skipIndexing.get()) {
                return;
            }

            List<SolrInputDocument> clone;
            synchronized (bufferLock) {
                buffer.add(doc);
                // buffer documents if the buffer is not full
                if (buffer.size() < maxBufferSize) {
                    return;
                }

                // Buffer is full. Make a clone and release the lock, so that we don't
                // hold other ingest threads
                clone = buffer.stream().collect(toList());
                buffer.clear();
            }
            
            // send the cloned list to Solr
            sendBufferedDocs(clone);
        }
        
        /**
         * Send a list of buffered documents to Solr.
         *
         * @param docBuffer List of buffered Solr documents
         *
         * @throws KeywordSearchModuleException
         */
        @NbBundle.Messages({
            "Collection.unableToIndexData.error=Unable to add data to text index. All future text indexing for the current case will be skipped.",
            
        })
        private void sendBufferedDocs(List<SolrInputDocument> docBuffer) throws KeywordSearchModuleException {
            
            if (docBuffer.isEmpty()) {
                return;
            }

            try {
                boolean success = true;
                for (int reTryAttempt = 0; reTryAttempt < NUM_BATCH_UPDATE_RETRIES; reTryAttempt++) {
                    try {
                        success = true;
                        indexingClient.add(docBuffer);
                    } catch (Exception ex) {
                        success = false;
                        if (reTryAttempt < NUM_BATCH_UPDATE_RETRIES - 1) {
                            logger.log(Level.WARNING, "Unable to send document batch to Solr. Re-trying...", ex); //NON-NLS
                            try {
                                Thread.sleep(SLEEP_BETWEEN_RETRIES_MS);
                            } catch (InterruptedException ignore) {
                                throw new KeywordSearchModuleException(
                                        NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg"), ex); //NON-NLS
                            }
                        }                        
                    }
                    if (success) {
                        numConsecutiveFailures.set(0);
                        if (reTryAttempt > 0) {
                            logger.log(Level.INFO, "Batch update suceeded after {0} re-try", reTryAttempt); //NON-NLS
                        }
                        return;
                    }
                }
                // if we are here, it means all re-try attempts failed
                logger.log(Level.SEVERE, "Unable to send document batch to Solr. All re-try attempts failed!"); //NON-NLS
                throw new KeywordSearchModuleException(NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg")); //NON-NLS
            } catch (Exception ex) {
                // Solr throws a lot of unexpected exception types
                numConsecutiveFailures.incrementAndGet();
                logger.log(Level.SEVERE, "Could not add batched documents to index", ex); //NON-NLS
                
                // display message to user that that a document batch is missing from the index
                MessageNotifyUtil.Notify.error(
                        NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg"),
                        NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg"));
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg"), ex); //NON-NLS
            } finally {
                if (numConsecutiveFailures.get() >= MAX_NUM_CONSECUTIVE_FAILURES) {
                    // skip all future indexing
                    skipIndexing.set(true);
                    logger.log(Level.SEVERE, "Unable to add data to text index. All future text indexing for the current case will be skipped!"); //NON-NLS

                    // display message to user that no more data will be added to the index
                    MessageNotifyUtil.Notify.error(
                            NbBundle.getMessage(this.getClass(), "Server.addDocBatch.exception.msg"),
                            Bundle.Collection_unableToIndexData_error());
                    if (RuntimeProperties.runningWithGUI()) {
                        MessageNotifyUtil.Message.error(Bundle.Collection_unableToIndexData_error());
                    }
                }
                docBuffer.clear();
            }
        }

        /**
         * get the text from the content field for the given file
         *
         * @param contentID Solr document ID
         * @param chunkID Chunk ID of the Solr document
         *
         * @return Text from matching Solr document (as String). Null if no
         *         matching Solr document found or error while getting content
         *         from Solr
         */
        private String getSolrContent(long contentID, int chunkID) {
            final SolrQuery q = new SolrQuery();
            q.setQuery("*:*");
            String filterQuery = Schema.ID.toString() + ":" + KeywordSearchUtil.escapeLuceneQuery(Long.toString(contentID));
            if (chunkID != 0) {
                filterQuery = filterQuery + Server.CHUNK_ID_SEPARATOR + chunkID;
            }
            q.addFilterQuery(filterQuery);
            q.setFields(Schema.TEXT.toString());
            try {
                // Get the first result. 
                SolrDocumentList solrDocuments = queryClient.query(q).getResults();

                if (!solrDocuments.isEmpty()) {
                    SolrDocument solrDocument = solrDocuments.get(0);
                    if (solrDocument != null) {
                        java.util.Collection<Object> fieldValues = solrDocument.getFieldValues(Schema.TEXT.toString());
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
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                logger.log(Level.SEVERE, "Error getting content from Solr. Solr document id " + contentID + ", chunk id " + chunkID + ", query: " + filterQuery, ex); //NON-NLS
                return null;
            }

            return null;
        }

        synchronized void close() throws KeywordSearchModuleException {
            try {

                // stop the periodic batch update task. If the task is already running, 
                // allow it to finish.
                ThreadUtils.shutDownTaskExecutor(periodicTasksExecutor);

                // We only unload cores for "single-user" cases.
                if (this.caseType == CaseType.MULTI_USER_CASE) {
                    return;
                }
                
                CoreAdminRequest.unloadCore(this.name, localSolrServer);
            } catch (Exception ex) {
                // intentional "catch all" as Solr is known to throw all kinds of Runtime exceptions
                throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.close.exception.msg"), ex);
            } finally {
                try {
                    queryClient.close();
                    queryClient = null;
                    indexingClient.close();
                    indexingClient = null;
                } catch (IOException ex) {
                    throw new KeywordSearchModuleException(
                        NbBundle.getMessage(this.getClass(), "Server.close.exception.msg2"), ex);
                }
            }
        }

        /**
         * Execute query that gets only number of all Solr files (not chunks)
         * indexed without actually returning the files
         *
         * @return int representing number of indexed files (entire files, not
         * chunks)
         *
         * @throws SolrServerException
         */
        private int queryNumIndexedFiles() throws SolrServerException, IOException {
            return queryNumIndexedDocuments() - queryNumIndexedChunks();
        }

        /**
         * Execute query that gets only number of all chunks (not logical
         * folders, or all documents) indexed without actually returning the
         * content
         *
         * @return int representing number of indexed chunks
         *
         * @throws SolrServerException
         */
        private int queryNumIndexedChunks() throws SolrServerException, IOException {
            SolrQuery q = new SolrQuery(Server.Schema.ID + ":*" + Server.CHUNK_ID_SEPARATOR + "*");
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
         *
         * @throws SolrServerException
         */
        private int queryNumIndexedDocuments() throws SolrServerException, IOException {
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
        private boolean queryIsIndexed(long contentID) throws SolrServerException, IOException {
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
         * indexed
         *
         * @return int representing number of indexed file chunks, 0 if there is
         * no chunks
         *
         * @throws SolrServerException
         */
        private int queryNumFileChunks(long contentID) throws SolrServerException, IOException {
            String id = KeywordSearchUtil.escapeLuceneQuery(Long.toString(contentID));
            final SolrQuery q
                    = new SolrQuery(Server.Schema.ID + ":" + id + Server.CHUNK_ID_SEPARATOR + "*");
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
