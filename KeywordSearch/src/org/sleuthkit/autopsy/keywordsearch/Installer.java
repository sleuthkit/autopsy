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

import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearch.Server.SolrServerNoPortException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.Version;

/**
 * Starts up the Solr server when the module is loaded, and stops it when the
 * application is closed.
 *
 * In addition, the default KeywordSearch config files (NSRL, Options, Scripts)
 * are generated here, if they config files do not already exist.
 */
class Installer extends ModuleInstall {

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private final static int SERVER_START_RETRIES = 5;

    @Override
    public void restored() {
        //Setup the default KeywordSearch configuration files
        KeywordSearchSettings.setDefaults();

        Case.addPropertyChangeListener(new KeywordSearch.CaseChangeListener());

        final Server server = KeywordSearch.getServer();
        int retries = SERVER_START_RETRIES;

        //TODO revise this logic, handle other server types, move some logic to Server class
        try {
            //check if running from previous application instance and try to shut down
            logger.log(Level.INFO, "Checking if server is running");
            if (server.isRunning()) {
                //TODO this could hang if other type of server is running 
                logger.log(Level.WARNING, "Already a server running on " + server.getCurrentSolrServerPort()
                        + " port, maybe leftover from a previous run. Trying to shut it down.");
                //stop gracefully
                server.stop();
                logger.log(Level.INFO, "Re-checking if server is running");
                if (server.isRunning()) {
                    int serverPort = server.getCurrentSolrServerPort();
                    int serverStopPort = server.getCurrentSolrStopPort();
                    logger.log(Level.SEVERE, "There's already a server running on "
                            + serverPort + " port that can't be shutdown.");
                    if (!Server.isPortAvailable(serverPort)) {
                        reportPortError(serverPort);
                    } else if (!Server.isPortAvailable(serverStopPort)) {
                        reportStopPortError(serverStopPort);
                    } else {
                        //some other reason
                        reportInitError();
                    }

                    //in this case give up

                } else {
                    logger.log(Level.INFO, "Old Solr server shutdown successfully.");
                    //make sure there really isn't a hang Solr process, in case isRunning() reported false
                    server.killSolr();
                }
            }
        } catch (KeywordSearchModuleException e) {
            logger.log(Level.SEVERE, "Starting server failed, will try to kill. ", e);
            server.killSolr();
        }


        try {
            //Ensure no other process is still bound to that port, even if we think solr is not running
            //Try to bind to the port 4 times at 1 second intervals. 
            //TODO move some of this logic to Server class
            for (int i = 0; i <= 3; i++) {
                logger.log(Level.INFO, "Checking if port available.");
                if (Server.isPortAvailable(server.getCurrentSolrServerPort())) {
                    logger.log(Level.INFO, "Port available, trying to start server.");
                    server.start();
                    break;
                } else if (i == 3) {
                    logger.log(Level.INFO, "No port available, done retrying.");
                    reportPortError(server.getCurrentSolrServerPort());
                    retries = 0;
                    break;
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException iex) {
                        logger.log(Level.WARNING, "Timer interrupted");
                    }
                }
            }
        } catch (SolrServerNoPortException npe) {
            logger.log(Level.SEVERE, "Starting server failed due to no port available. ", npe);
            //try to kill it

        } catch (KeywordSearchModuleException e) {
            logger.log(Level.SEVERE, "Starting server failed. ", e);
        }


        //retry if needed
        //TODO this loop may be now redundant
        while (retries-- > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Timer interrupted.");
            }

            try {
                logger.log(Level.INFO, "Ensuring the server is running, retries remaining: " + retries);
                if (!server.isRunning()) {
                    logger.log(Level.WARNING, "Server still not running");
                    try {
                        logger.log(Level.WARNING, "Trying to start the server. ");
                        server.start();
                    } catch (SolrServerNoPortException npe) {
                        logger.log(Level.SEVERE, "Starting server failed due to no port available. ", npe);
                    }
                } else {
                    logger.log(Level.INFO, "Server appears now running. ");
                    break;
                }
            } catch (KeywordSearchModuleException ex) {
                logger.log(Level.SEVERE, "Starting server failed. ", ex);
                //retry if has retries
            }

        } //end of retry while loop


        //last check if still not running to report errors
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, "Timer interrupted.");
        }
        try {
            logger.log(Level.INFO, "Last check if server is running. ");
            if (!server.isRunning()) {
                logger.log(Level.SEVERE, "Server is still not running. ");
                //check if port is taken or some other reason
                int serverPort = server.getCurrentSolrServerPort();
                int serverStopPort = server.getCurrentSolrStopPort();
                if (!Server.isPortAvailable(serverPort)) {
                    reportPortError(serverPort);
                } else if (!Server.isPortAvailable(serverStopPort)) {
                    reportStopPortError(serverStopPort);
                } else {
                    //some other reason
                    reportInitError();
                }
            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Starting server failed. ", ex);
            reportInitError();
        }


    }

    @Override
    public boolean closing() {
        //platform about to close

        KeywordSearch.getServer().stop();

        return true;
    }

    @Override
    public void uninstalled() {
        //module is being unloaded
        KeywordSearch.getServer().stop();

    }

    private void reportPortError(final int curFailPort) {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                final String msg = NbBundle.getMessage(this.getClass(), "Installer.reportPortError", curFailPort, Version.getName(), Server.PROPERTIES_CURRENT_SERVER_PORT, Server.PROPERTIES_FILE);
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"), msg);
            }
        });
    }

    private void reportStopPortError(final int curFailPort) {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                final String msg = NbBundle.getMessage(this.getClass(), "Installer.reportStopPortError", curFailPort, Server.PROPERTIES_CURRENT_STOP_PORT, Server.PROPERTIES_FILE);
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"), msg);
            }
        });
    }

    private void reportInitError() {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                final String msg = NbBundle.getMessage(this.getClass(), "Installer.reportInitError", KeywordSearch.getServer().getCurrentSolrServerPort(), Version.getName(), Server.PROPERTIES_CURRENT_SERVER_PORT, Server.PROPERTIES_FILE);
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"), msg);

                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"), msg);
            }
        });
    }
}
