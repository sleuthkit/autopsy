/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2020 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.centralrepository.eventlisteners;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;
import org.apache.commons.io.FileUtils;
import org.openide.modules.ModuleInstall;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

/**
 * Sets up a default, single-user SQLite central repository
 * if no central repository is configured, and updates the central repository
 * schema as required.
 */
public class Installer extends ModuleInstall {

    private static final String LEGACY_DEFAULT_FOLDER = "central_repository";
    private static final String LEGACY_DEFAULT_DB_PARENT_PATH = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), LEGACY_DEFAULT_FOLDER).toAbsolutePath().toString();
    private static final String LEGACY_MODULE_SETTINGS_KEY = "CentralRepository";
    
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

    /*
     * Sets up a default, single-user SQLite central
     * repository if no central repository is configured.
     *
     * Called by the registered Installer for the Autopsy-Core module located in
     * the org.sleuthkit.autopsy.core package when the already installed
     * Autopsy-Core module is restored (during application startup).
     */
    @Override
    public void restored() {
        // must happen first to move any legacy settings that exist.
        upgradeSettingsPath();
    }
    
    
    
    /**
    * Path to module settings path.
    *
    * @param moduleName The full name of the module provided to ModuleSettings.
    *
    * @return The path on disk for that object. NOTE: This must be in sync with
    *         ModuleSettings.
    */
    private String getSettingsFilePath(String moduleName) {
        return Paths.get(PlatformUtil.getUserConfigDirectory(), moduleName + ".properties").toString();
    }
            
    /**
     * Copies settings to new path location.
     */
    private void upgradeSettingsPath() {
        File newSettingsFile = new File(getSettingsFilePath(CentralRepoSettings.getInstance().getModuleSettingsKey()));
        File legacySettingsFile = new File(getSettingsFilePath(LEGACY_MODULE_SETTINGS_KEY));
        // new config has not been created, but legacy has, copy it.
        if (!newSettingsFile.exists() && legacySettingsFile.exists()) {
            Map<String, String> prevSettings = ModuleSettings.getConfigSettings(LEGACY_MODULE_SETTINGS_KEY);
            String prevPath = prevSettings.get(CentralRepoSettings.getInstance().getDatabasePathKey());
            File prevDirCheck = new File(prevPath);
            // if a relative directory, make sure it is relative to user config.
            if (!prevDirCheck.isAbsolute()) {
                prevPath = Paths.get(PlatformUtil.getUserDirectory().getAbsolutePath(), prevPath).toAbsolutePath().toString();
            }
            
            // if old path is default path for sqlite db, copy it over to new location and update setting.
            if (prevPath != null 
                    && Paths.get(LEGACY_DEFAULT_DB_PARENT_PATH).toAbsolutePath().toString().equals(Paths.get(prevPath).toAbsolutePath().toString())) {
                String prevDbName = prevSettings.get(CentralRepoSettings.getInstance().getDatabaseNameKey());
                File prevDir = new File(prevPath);
                // copy all files starting with prevDbName in prevPath to new path location.
                if (prevDir.exists() && prevDir.isDirectory()) {
                    new File(CentralRepoSettings.getInstance().getDefaultDbPath()).mkdirs();
                    try {
                        for (File childFile : prevDir.listFiles((dir, name) -> name.startsWith(prevDbName))) {
                            FileUtils.copyFile(childFile, new File(CentralRepoSettings.getInstance().getDefaultDbPath(), childFile.getName()));
                        }    
                    } catch (IOException ex) {
                        logger.log(Level.SEVERE, "There was an error upgrading settings.", ex);
                    }
                }
                
                // get the new relative path to store
                String newRelPath = PlatformUtil.getUserDirectory().toPath().relativize(Paths.get(CentralRepoSettings.getInstance().getDefaultDbPath())).toString();
                // update path settings accordingly
                prevSettings.put(CentralRepoSettings.getInstance().getDatabasePathKey(), newRelPath);
            }
            
            // copy settings
            ModuleSettings.setConfigSettings(CentralRepoSettings.getInstance().getModuleSettingsKey(), prevSettings);
        }
    }

    @Override
    public void uninstalled() {
        /*
         * TODO (Jira-6108): This code is erronoeous. As documented at
         * http://bits.netbeans.org/dev/javadoc/org-openide-modules/org/openide/modules/ModuleInstall.html#uninstalled--
         *
         * "Called when the module is disabled while the application is still
         * running. Should remove whatever functionality that it had registered
         * in ModuleInstall.restored(). 
         * 
         * Beware: in practice there is no way to
         * ensure that this method will really be called. The module might
         * simply be deleted or disabled while the application is not running.
         * In fact this is always the case in NetBeans 6.0; the Plugin Manager
         * only uninstalls or disables modules between restarts. This method
         * will still be called if you reload a module during development."
         * 
         * THIS CODE IS NEVER EXECUTED.
         */
    }
}
