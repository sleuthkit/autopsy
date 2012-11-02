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
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.modules.ModuleInstall;
import org.openide.util.Exceptions;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.keywordsearch.Server.SolrServerNoPortException;

/**
 * Starts up the Solr server when the module is loaded, and stops it when the
 * application is closed.
 *
 * In addition, the default KeywordSearch config files (NSRL, Options, Scripts)
 * are generated here, if they config files do not already exist.
 */
public class Installer extends ModuleInstall {

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private final static int SERVER_START_RETRIES = 5;

    @Override
    public void restored() {

        //Setup the default KeywordSearch configuration files
        KeywordSearchSettings.setDefaults();

        Case.addPropertyChangeListener(new KeywordSearch.CaseChangeListener());

        final Server server = KeywordSearch.getServer();

        try {
            if (server.isRunning()) {

                logger.log(Level.WARNING, "Already a Solr server running, maybe leftover from a previous run. Trying to shut it down...");

                // Send the stop message in case there's a solr server lingering from
                // a previous run of Autopsy that didn't exit cleanly
                server.stop();

                if (server.isRunning()) {
                    throw new IllegalStateException("There's already a server running on our port that can't be shutdown.");
                } else {
                    logger.log(Level.INFO, "Old Solr server shutdown successfully.");
                }
            }
           try{
            server.start();
           } catch(SolrServerNoPortException npe){
               logger.log(Level.WARNING, "Solr server could not bind to expected port. Please refer to jetty.xml and change the port", npe);
            }
        } catch (KeywordSearchModuleException e) {
            logger.log(Level.WARNING, "Could not start Solr server while loading the module.");
        }

        //retry if needed
        int retries = SERVER_START_RETRIES;
        while (retries-- > 0) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                logger.log(Level.WARNING, "Timer interrupted.");
            }

            try {
                if (!server.isRunning()) {
                    logger.log(Level.WARNING, "Server still not running, retries remaining: " + retries);
                    try {
                        server.start();
                    } catch (SolrServerNoPortException npe) {
                        logger.log(Level.WARNING, "Solr server could not bind to expected port. Please refer to jetty.xml and change the port");
                    }
                } else {
                    break;
                }
            } catch (KeywordSearchModuleException ex) {
                logger.log(Level.WARNING, "Was unable to start the keyword search server");
                //retry if has retries
            }

        } //end of retry while loop


        //check if still not running
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            logger.log(Level.WARNING, "Timer interrupted.");
        }
        try {
            if (!server.isRunning()) {
                logger.log(Level.SEVERE, "Was unable to start the keyword search server!");
                reportInitError();
            }
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.SEVERE, "Was unable to start the keyword search server!");
            reportInitError();
        }


    }

    @Override
    public boolean closing() {
        try {
            KeywordSearch.getServer().stop();
        } catch (KeywordSearchModuleException ex) {
            logger.log(Level.INFO, "Could not stop server while unloading the module");
        }
        return true;
    }
    
     private void reportInitError() {
         WindowManager.getDefault().invokeWhenUIReady(new Runnable() {

            @Override
            public void run() {
                final String msg = "<html>Error initializing Keyword Search module.<br />"
                        + "File indexing and search will not be functional.<br />"
                        + "Please try to restart your computer and the application.</html>";
                KeywordSearchUtil.displayDialog("Error initializing Keyword Search module", msg, KeywordSearchUtil.DIALOG_MESSAGE_TYPE.ERROR);
            }
        });
    }
}
