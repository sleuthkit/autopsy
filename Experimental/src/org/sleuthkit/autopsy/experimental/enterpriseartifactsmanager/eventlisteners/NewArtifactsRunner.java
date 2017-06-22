/*
 * Enterprise Artifacts Manager
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
package org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.eventlisteners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamArtifact;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDbException;
import org.sleuthkit.autopsy.experimental.enterpriseartifactsmanager.datamodel.EamDb;

/**
 * Thread to insert a new artifact into remote DB.
 */
public class NewArtifactsRunner implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(NewArtifactsRunner.class.getName());
    private static final long serialVersionUID = 1L;

    private final EamDb dbManager;
    private final Collection<EamArtifact> eamArtifacts;

    @SuppressWarnings(value = {"unchecked", "rawtypes"})
    public NewArtifactsRunner(Collection<EamArtifact> eamArtifacts) {
        this.dbManager = EamDb.getInstance();
        this.eamArtifacts = new ArrayList(eamArtifacts);
    }

    @Override
    public void run() {
        if (!EamDb.isEnabled()) {
            LOGGER.log(Level.WARNING, "Enterprise artifacts manager database not configured"); // NON-NLS
            return;
        }

        try {
            for (EamArtifact eamArtifact : eamArtifacts) {
                dbManager.addArtifact(eamArtifact);
            }
        } catch (EamDbException ex) {
            LOGGER.log(Level.SEVERE, "Error connecting to enterprise artifacts manager database.", ex); //NON-NLS
        }

    }

}
