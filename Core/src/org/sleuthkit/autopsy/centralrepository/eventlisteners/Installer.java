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
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.Version;

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
    @NbBundle.Messages({
        "Installer.initialCreateSqlite.title=Enable Central Repository?",
        "Installer.initialCreateSqlite.messageHeader=The Central Repository is not enabled. Would you like to enable it?",
        "Installer.initialCreateSqlite.messageDesc=It will store information about all hashes and identifiers that you process. "
        + "You can use this to ignore previously seen files and make connections between cases."
    })
    @Override
    public void restored() {
        addApplicationEventListeners();

        if (Version.getBuildType() == Version.Type.RELEASE) {
            setupDefaultCentralRepository();
        }
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
     * either offers to perform set up (running with a GUI) or does the set up
     * unconditionally (not running with a GUI, e.g., in an automated ingest
     * node).
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

        // if central repository hasn't been previously initialized, initialize it
        if (!initialized) {
            // if running with a GUI, prompt the user
            if (RuntimeProperties.runningWithGUI()) {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            String dialogText
                                    = "<html><body>"
                                    + "<div style='width: 400px;'>"
                                    + "<p>" + NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.messageHeader") + "</p>"
                                    + "<p style='margin-top: 10px'>" + NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.messageDesc") + "</p>"
                                    + "</div>"
                                    + "</body></html>";

                            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(WindowManager.getDefault().getMainWindow(),
                                    dialogText,
                                    NbBundle.getMessage(this.getClass(), "Installer.initialCreateSqlite.title"),
                                    JOptionPane.YES_NO_OPTION)) {

                                setupDefaultSqliteCentralRepo();
                            }
                        } catch (CentralRepoException ex) {
                            logger.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

                            doMessageBoxIfRunningInGUI(ex);
                        }
                    });
                } catch (InterruptedException | InvocationTargetException ex) {
                    logger.log(Level.SEVERE, "There was an error while running the swing utility invoke later while creating the central repository database", ex);
                }
            } // if no GUI, just initialize
            else {
                try {
                    setupDefaultSqliteCentralRepo();
                } catch (CentralRepoException ex) {
                    logger.log(Level.SEVERE, "There was an error while initializing the central repository database", ex);

                    doMessageBoxIfRunningInGUI(ex);
                }
            }

            ModuleSettings.setConfigSetting("CentralRepository", "initialized", "true");
        }
    }

    /**
     * Sets up a default single-user SQLite central repository.
     *
     * @throws CentralRepoException If there is an error setting up teh central
     *                              repository.
     */
    private void setupDefaultSqliteCentralRepo() throws CentralRepoException {
        CentralRepoDbManager manager = new CentralRepoDbManager();
        manager.setupDefaultSqliteDb();
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
