/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2015 Basis Technology Corp.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import org.apache.solr.client.solrj.SolrServerException;
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
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
    private static final long serialVersionUID = 1L;
    private static final String KWS_START_THREAD_NAME = "KWS-server-start-%d";

    @Override
    public void restored() {
        //Setup the default KeywordSearch configuration files
        KeywordSearchSettings.setDefaults();

        final Server server = KeywordSearch.getServer();
        
        ExecutorService jobProcessingExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(KWS_START_THREAD_NAME).build());
        Runnable kwsStartTask = new Runnable() {
            public void run() {
                try {
                    server.start();
                } catch (SolrServerNoPortException ex) {
                    logger.log(Level.SEVERE, "Failed to start Keyword Search server: ", ex); //NON-NLS
                    if (ex.getPortNumber() == server.getLocalSolrServerPort()) {
                        reportPortError(ex.getPortNumber());
                    } else {
                        reportStopPortError(ex.getPortNumber());
                    }
                } catch (KeywordSearchModuleException | SolrServerException ex) {
                    logger.log(Level.SEVERE, "Failed to start Keyword Search server: ", ex); //NON-NLS
                    reportInitError(ex.getMessage());
                }
            }
        };

        // start KWS service on the background thread. Currently all it does is start the embedded Solr server.
        jobProcessingExecutor.submit(kwsStartTask);
        jobProcessingExecutor.shutdown(); // tell executor no more work is coming
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

    private void reportInitError(final String msg) {
        WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
            @Override
            public void run() {
                MessageNotifyUtil.Notify.error(NbBundle.getMessage(this.getClass(), "Installer.errorInitKsmMsg"), msg);
            }
        });
    }
}
