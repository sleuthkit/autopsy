/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDb;
import org.sleuthkit.datamodel.TskData.FileKnown;

/**
 * Thread to send info to remote DB that tags a file as known, unknown, or notable.
 */
public class KnownStatusChangeRunner implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(KnownStatusChangeRunner.class.getName());
    private static final long serialVersionUID = 1L;

    private final CorrelationAttribute artifact;
    private final FileKnown knownStatus;

    public KnownStatusChangeRunner(CorrelationAttribute artifact, FileKnown knownStatus) {
        this.artifact = artifact;
        this.knownStatus = knownStatus;
    }

    @Override
    public void run() {
        if (!EamDb.isEnabled()) {
            LOGGER.log(Level.WARNING, "Central Repository database not configured"); // NON-NLS
            return;
        }

        try {
            EamDb dbManager = EamDb.getInstance();
            dbManager.setArtifactInstanceKnownStatus(this.artifact, this.knownStatus);
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error connecting to Central Repository database.", ex); //NON-NLS
        }
    }
}
