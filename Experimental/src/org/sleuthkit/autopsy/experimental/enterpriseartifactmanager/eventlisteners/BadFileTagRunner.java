/*
 * Enterprise Artifact Manager
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.eventlisteners;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamArtifact;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactmanager.datamodel.EamDb;

/**
 * Thread to send info to remote DB that tags a file as known bad.
 */
public class BadFileTagRunner implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(BadFileTagRunner.class.getName());
    private static final long serialVersionUID = 1L;

    private final EamArtifact artifact;
    private final EamDb dbManager;

    public BadFileTagRunner(EamArtifact artifact) {
        this.artifact = artifact;
        this.dbManager = EamDb.getInstance();
    }

    @Override
    public void run() {
        if (!dbManager.isEnabled()) {
            LOGGER.log(Level.WARNING, "Enterprise artifact manager database not configured"); // NON-NLS
            return;
        }

        try {
            dbManager.setArtifactInstanceKnownBad(this.artifact);
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error connecting to enterprise artifact manager database.", ex); //NON-NLS
        }
    }
}
