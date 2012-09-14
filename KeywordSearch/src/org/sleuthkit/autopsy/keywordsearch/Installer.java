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
import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Starts up the Solr server when the module is loaded, and stops it when the
 * application is closed.
 */
public class Installer extends ModuleInstall {

    @Override
    public void restored() {

        Logger logger = Logger.getLogger(Installer.class.getName());

        Case.addPropertyChangeListener(new KeywordSearch.CaseChangeListener());

        Server server = KeywordSearch.getServer();

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

        server.start();
        try {
            Thread.sleep(1000); // give it a sec
            //TODO: idle loop while waiting for it to start
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

    }

    @Override
    public boolean closing() {
        KeywordSearch.getServer().stop();
        return true;
    }
}
