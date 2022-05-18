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
package org.sleuthkit.autopsy.modules.hashdatabase.installer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.modules.ModuleInstall;
import org.python.icu.text.MessageFormat;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.modules.hashdatabase.HashLookupSettings;

/**
 * Installer for hash databases that copies legacy settings to new location.
 */
public class Installer extends ModuleInstall {

    private static final String LEGACY_SERIALIZATION_FILE_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), "hashLookup.settings").toString(); //NON-NLS
    private static final String LEGACY_HASH_DATABASE_DEFAULT_PATH = Paths.get(PlatformUtil.getUserConfigDirectory(), "HashDatabases").toString();

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;
    private static Installer instance;

    /**
     * Gets the singleton "package installer" used by the registered Installer
     * for the Autopsy-Core module located in the org.sleuthkit.autopsy.core
     * package.
     *
     * @return The "package installer" singleton for the
     *         org.sleuthkit.autopsy.centralrepository.eventlisteners package.
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
        // copy user dir hash dbs from legacy to new if old path exists and new does not.
        File legacyDbPath = new File(LEGACY_HASH_DATABASE_DEFAULT_PATH);
        File dbPath = new File(HashLookupSettings.getDefaultDbPath());
        if (legacyDbPath.exists() && !dbPath.exists()) {
            try {
                FileUtils.copyDirectory(legacyDbPath, dbPath);
            } catch (IOException ex) {
                logger.log(Level.WARNING, MessageFormat.format("There was an error copying legacy path hash dbs from {0} to {1}", legacyDbPath, dbPath), ex);
            }

        }

        // copy hash db settings to new location.
        File legacySettingsFile = new File(LEGACY_SERIALIZATION_FILE_PATH);
        File settingsFile = new File(HashLookupSettings.getSettingsPath());
        if (legacySettingsFile.exists() && !settingsFile.exists()) {
            try {
                FileUtils.copyFile(legacySettingsFile, settingsFile);
            } catch (IOException ex) {
                logger.log(Level.WARNING, MessageFormat.format("There was an error copying legacy hash db settings from {0} to {1}", legacySettingsFile, settingsFile), ex);
            }
        }
    }

}
