/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2022 Basis Technology Corp.
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
package org.sleuthkit.autopsy.report.infrastructure;

import java.io.IOException;
import java.util.logging.Level;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * Installer for hash databases that copies legacy settings to new location.
 */
public class Installer extends ModuleInstall {
    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;
    private static Installer instance;

    /**
     * Gets the singleton "package installer" used by the registered Installer
     * for the Autopsy-Core module located in the org.sleuthkit.autopsy.core
     * package.
     *
     * @return The singleton instance of this class.
     */
    public synchronized static Installer getDefault() {
        if (instance == null) {
            instance = new Installer();
        }
        return instance;
    }

    /**
     * Constructs the singleton "package installer" used by the registered
     * Installer for the Autopsy-Core module located in the
     * org.sleuthkit.autopsy.core package.
     */
    private Installer() {
        super();
    }

    @Override
    public void restored() {
        try {
            ReportingConfigLoader.upgradeConfig();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "There was an error while upgrading config paths.", ex);
        }
        
    }

}
