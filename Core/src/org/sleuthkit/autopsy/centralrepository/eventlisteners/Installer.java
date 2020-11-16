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
import org.openide.modules.ModuleInstall;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbChoice;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;

/**
 * Adds/removes application event listeners responsible for adding data to the
 * central repository, sets up a default, single-user SQLite central repository
 * if no central repository is configured, and updates the central repository
 * schema as required.
 */
public class Installer extends ModuleInstall {

    private static final Logger logger = Logger.getLogger(Installer.class.getName());
    private static final long serialVersionUID = 1L;
    private static Installer instance;
    private final CaseEventListener caseEventListener = new CaseEventListener();
    private final IngestEventsListener ingestEventListener = new IngestEventsListener();

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
     * Adds/removes application event listeners responsible for adding data to
     * the central repository and sets up a default, single-user SQLite central
     * repository if no central repository is configured.
     *
     * Called by the registered Installer for the Autopsy-Core module located in
     * the org.sleuthkit.autopsy.core package when the already installed
     * Autopsy-Core module is restored (during application startup).
     */
    @Override
    public void restored() {
        addApplicationEventListeners();
        setupDefaultCentralRepository();
    }

    /**
     * Adds the application event listeners responsible for adding data to the
     * central repository.
     */
    private void addApplicationEventListeners() {
        caseEventListener.installListeners();
        ingestEventListener.installListeners();
    }

    /**
     * Checks if the central repository has been set up and configured. If not,
     * does the set up unconditionally. If the application is running with a
     * GUI, a notification will be displayed to the user if the mode is RELEASE
     * (in other words, developers are exempt from seeing the notification).
     */
    private void setupDefaultCentralRepository() {
        Map<String, String> centralRepoSettings = ModuleSettings.getConfigSettings("CentralRepository");
        String initializedStr = centralRepoSettings.get("initialized");

        // check to see if the repo has been initialized asking to setup cr
        boolean initialized = Boolean.parseBoolean(initializedStr);

        // if it hasn't received that flag, check for a previous install where cr is already setup
        if (!initialized) {
            boolean prevRepo = Boolean.parseBoolean(centralRepoSettings.get("db.useCentralRepo"));
            // if it has been previously set up and is in use, mark as previously initialized and save the settings
            if (prevRepo) {
                initialized = true;
                ModuleSettings.setConfigSetting("CentralRepository", "initialized", "true");
            }
        }
        
        if(initialized) {
            return; // Nothing to do
        }

        if (CentralRepositoryNotificationDialog.shouldDisplay()) {
            CentralRepositoryNotificationDialog.display();
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

        ModuleSettings.setConfigSetting("CentralRepository", "initialized", "true");
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
        caseEventListener.uninstallListeners();
        caseEventListener.shutdown();
        ingestEventListener.shutdown();
        ingestEventListener.uninstallListeners();
    }
}
