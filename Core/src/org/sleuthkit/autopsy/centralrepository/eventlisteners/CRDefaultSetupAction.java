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

import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.CentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbChoice;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Runs the default setup for central repository and notifies the user if a)
 * running with GUI and b) first launch.
 */
public class CRDefaultSetupAction {

    private static final Logger logger = Logger.getLogger(CRDefaultSetupAction.class.getName());
    private static final CRDefaultSetupAction INSTANCE = new CRDefaultSetupAction();
    
    public static CRDefaultSetupAction getInstance() {
        return INSTANCE;
    }

    private CRDefaultSetupAction() {
    }

    /**
     * Checks if the central repository has been set up and configured. If not,
     * does the set up unconditionally. 
     * 
     * @return Returns true if first run and a default CR was setup.
     */
    public boolean setupDefaultCentralRepository() {
        Map<String, String> centralRepoSettings = ModuleSettings.getConfigSettings(CentralRepoSettings.getInstance().getModuleSettingsKey());
        String initializedStr = centralRepoSettings.get("initialized");

        // check to see if the repo has been initialized asking to setup cr
        boolean initialized = Boolean.parseBoolean(initializedStr);

        // if it hasn't received that flag, check for a previous install where cr is already setup
        if (!initialized) {
            boolean prevRepo = Boolean.parseBoolean(centralRepoSettings.get("db.useCentralRepo"));
            // if it has been previously set up and is in use, mark as previously initialized and save the settings
            if (prevRepo) {
                initialized = true;
                ModuleSettings.setConfigSetting(CentralRepoSettings.getInstance().getModuleSettingsKey(), "initialized", "true");
            }
        }

        if (initialized) {
            return false; // Nothing to do
        }

        try {
            CentralRepoDbManager manager = new CentralRepoDbManager();
            if (UserPreferences.getIsMultiUserModeEnabled()) {
                // Set up using existing multi-user settings.
                manager.setupPostgresDb(CentralRepoDbChoice.POSTGRESQL_MULTIUSER);
            } else {
                manager.setupDefaultSqliteDb();
            }
        } catch (CentralRepoException ex) {
            logger.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

            doMessageBoxIfRunningInGUI(ex);
        }

        ModuleSettings.setConfigSetting(CentralRepoSettings.getInstance().getModuleSettingsKey(), "initialized", "true");
        return true;
    }

    /**
     * Display a central repository exception in a message box if running with a
     * GUI.
     *
     * @param ex The exception.
     */
    @NbBundle.Messages({"Installer.centralRepoUpgradeFailed.title=Central repository disabled"})
    private void doMessageBoxIfRunningInGUI(CentralRepoException ex) {
        if (RuntimeProperties.runningWithGUI()) {
            try {
                SwingUtilities.invokeAndWait(() -> {
                    JOptionPane.showMessageDialog(null,
                            ex.getUserMessage(),
                            NbBundle.getMessage(this.getClass(), "Installer.centralRepoUpgradeFailed.title"),
                            JOptionPane.ERROR_MESSAGE);
                });
            } catch (InterruptedException | InvocationTargetException e) {
                logger.log(Level.WARNING, e.getMessage(), e);
            }
        }
    }

}
